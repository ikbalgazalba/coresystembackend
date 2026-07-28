package com.coresystem.coresystembackend.masterdata.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * TDD test for U-003 — APP_USER entity + role enum D-10 + user provisioning (E1-E7).
 *
 * <p>Verifies the acceptance criteria from the unit spec:
 * <ul>
 *   <li><strong>AC-1</strong> — Happy path: provisioning a valid user returns 201 with the
 *       created user DTO.</li>
 *   <li><strong>AC-2</strong> — {@code SUPER_USER} role is rejected with {@code 422 UNKNOWN_ROLE}
 *       (D-09 — no super-user in entity/endpoint/grant).</li>
 *   <li><strong>AC-3</strong> — Resigned employee is rejected with {@code 422 EMPLOYEE_RESIGNED},
 *       and the HR resign event auto-deactivates the linked user (BR-BE07-27).</li>
 *   <li><strong>409 USER_ALREADY_EXISTS</strong> — Duplicate NIK provisioning is rejected.</li>
 *   <li><strong>E7</strong> — {@code GET /roles} returns the static D-10 catalog.</li>
 *   <li><strong>Entity rules</strong> — AppUser has NO password field (BR-SHELL-1), NO
 *       isSuperUser (D-09), role is D-10 enum, extends VersionedEntity.</li>
 *   <li><strong>E5 reactivate</strong> — Rejected with 409 if mirror resigned.</li>
 * </ul>
 *
 * <p>Test structure follows {@code EmployeeMirrorTest}:
 * <ul>
 *   <li>Entity reflection tests (no Spring context) — verify JPA mapping, field presence,
 *       no password/isSuperUser.</li>
 *   <li>Role enum tests — D-10 values, no SUPER_USER, fromName validation.</li>
 *   <li>Controller HTTP tests — focused Spring context ({@code classes = AppUserController.class}),
 *     mocked repositories via {@code @MockitoBean}, excluding DataSource/JPA autoconfiguration.</li>
 *   <li>Service event-listener test — direct instantiation with mocked repos.</li>
 * </ul>
 */
class AppUserProvisioningTest {

	// ----------------------------------------------------------------------------------------------
	// Role enum tests — D-10 closed catalog, NO SUPER_USER (D-09)
	// ----------------------------------------------------------------------------------------------

	@Test
	void roleEnumHasExactlyFiveD10Values() {
		assertThat(Role.values())
				.as("Role enum must have exactly 5 D-10 values")
				.hasSize(5);
	}

	@Test
	void roleEnumContainsAllD10Values() {
		assertThat(Role.values())
				.as("Role enum must contain exactly CMO, MARKETING_HEAD, CREDIT_ANALYST, KEPALA_CABANG, CREDIT_ADMIN")
				.containsExactlyInAnyOrder(
						Role.CMO,
						Role.MARKETING_HEAD,
						Role.CREDIT_ANALYST,
						Role.KEPALA_CABANG,
						Role.CREDIT_ADMIN);
	}

	@Test
	void roleEnumDoesNotContainSuperUser() {
		// D-09: NO SUPER_USER anywhere — entity, endpoint, or grant.
		assertThat(Role.values())
				.as("D-09: Role enum must NOT contain SUPER_USER")
				.noneMatch(r -> r.name().equals("SUPER_USER"));
	}

	@Test
	void roleFromNameRejectsSuperUser() {
		// AC-2: SUPER_USER is not a valid D-10 role.
		assertThat(Role.fromName("SUPER_USER"))
				.as("Role.fromName('SUPER_USER') must return empty (D-09)")
				.isEmpty();
	}

	@Test
	void roleFromNameAcceptsD10Value() {
		assertThat(Role.fromName("CREDIT_ANALYST"))
				.as("Role.fromName('CREDIT_ANALYST') must return the enum value")
				.contains(Role.CREDIT_ANALYST);
	}

	@Test
	void roleFromNameRejectsNull() {
		assertThat(Role.fromName(null)).isEmpty();
	}

	@Test
	void roleFromNameRejectsUnknownValue() {
		assertThat(Role.fromName("ADMIN")).isEmpty();
		assertThat(Role.fromName("")).isEmpty();
	}

	@Test
	void roleCatalogReturnsDefensiveCopy() {
		Role[] first = Role.catalog();
		Role[] second = Role.catalog();
		assertThat(first).isNotSameAs(second);
		assertThat(first).containsExactlyElementsOf(List.of(second));
	}

	// ----------------------------------------------------------------------------------------------
	// AppUser entity reflection tests — no password, no isSuperUser, D-10 role
	// ----------------------------------------------------------------------------------------------

	@Test
	void appUserIsJpaEntityMappedToMstUserTable() {
		assertThat(AppUser.class).hasAnnotation(Entity.class);
		Table table = AppUser.class.getAnnotation(Table.class);
		assertThat(table).as("AppUser must carry @Table").isNotNull();
		assertThat(table.name()).isEqualTo("mst_user");
	}

	@Test
	void appUserExtendsVersionedEntity() {
		assertThat(com.coresystem.coresystembackend.masterdata.common.VersionedEntity.class)
				.isAssignableFrom(AppUser.class);
	}

	@Test
	void appUserHasIdPrimaryKeyWithIdentityGeneration() {
		Field idField = getDeclaredField(AppUser.class, "id");
		assertThat(idField.getType()).isEqualTo(Long.class);
		assertThat(idField.isAnnotationPresent(Id.class)).isTrue();
		GeneratedValue gv = idField.getAnnotation(GeneratedValue.class);
		assertThat(gv).isNotNull();
		assertThat(gv.strategy()).isEqualTo(GenerationType.IDENTITY);
	}

	@Test
	void appUserEmployeeNikIsUniqueColumn() {
		Field nikField = getDeclaredField(AppUser.class, "employeeNik");
		Column col = nikField.getAnnotation(Column.class);
		assertThat(col).isNotNull();
		assertThat(col.name()).isEqualTo("employee_nik");
		assertThat(col.unique())
				.as("employeeNik must be unique (ux_mst_user_employee_nik)")
				.isTrue();
	}

	@Test
	void appUserRoleIsEnumeratedString() {
		Field roleField = getDeclaredField(AppUser.class, "role");
		assertThat(roleField.getType()).isEqualTo(Role.class);
		Enumerated enumerated = roleField.getAnnotation(Enumerated.class);
		assertThat(enumerated)
				.as("role must carry @Enumerated(STRING) for VARCHAR persistence")
				.isNotNull();
		assertThat(enumerated.value()).isEqualTo(jakarta.persistence.EnumType.STRING);
	}

	@Test
	void appUserDoesNotHavePasswordField() {
		// BR-SHELL-1: NO password field — auth delegated to LDAP.
		assertThat(AppUser.class.getDeclaredFields())
				.as("AppUser must NOT declare a 'password' field (BR-SHELL-1)")
				.noneMatch(f -> f.getName().toLowerCase().contains("password"));
	}

	@Test
	void appUserDoesNotHaveIsSuperUserField() {
		// D-09: NO isSuperUser field.
		assertThat(AppUser.class.getDeclaredFields())
				.as("AppUser must NOT declare an 'isSuperUser' or 'superUser' field (D-09)")
				.noneMatch(f -> f.getName().toLowerCase().contains("superuser") || f.getName().toLowerCase().contains("super_user"));
	}

	@Test
	void appUserHasIsActiveBooleanField() {
		Field activeField = getDeclaredField(AppUser.class, "isActive");
		assertThat(activeField.getType()).isEqualTo(boolean.class);
		Column col = activeField.getAnnotation(Column.class);
		assertThat(col).isNotNull();
		assertThat(col.name()).isEqualTo("is_active");
	}

	@Test
	void appUserHasDeactivationReasonEnum() {
		Field reasonField = getDeclaredField(AppUser.class, "deactivationReason");
		assertThat(reasonField.getType()).isEqualTo(AppUser.DeactivationReason.class);
	}

	@Test
	void appUserDeactivationReasonHasCorrectValues() {
		assertThat(AppUser.DeactivationReason.values())
				.containsExactlyInAnyOrder(
						AppUser.DeactivationReason.manual,
						AppUser.DeactivationReason.hr_resigned);
	}

	@Test
	void appUserHasActivationAndDeactivationDates() {
		assertThat(fieldExists(AppUser.class, "activationDate")).isTrue();
		assertThat(fieldExists(AppUser.class, "deactivationDate")).isTrue();
	}

	// ----------------------------------------------------------------------------------------------
	// AppUserBranchScope entity reflection tests
	// ----------------------------------------------------------------------------------------------

	@Test
	void appUserBranchScopeIsJpaEntityMappedToMstUserBranchScopeTable() {
		assertThat(AppUserBranchScope.class).hasAnnotation(Entity.class);
		Table table = AppUserBranchScope.class.getAnnotation(Table.class);
		assertThat(table).as("AppUserBranchScope must carry @Table").isNotNull();
		assertThat(table.name()).isEqualTo("mst_user_branch_scope");
	}

	@Test
	void appUserBranchScopeHasUserIdAndBranchIdFields() {
		assertThat(fieldExists(AppUserBranchScope.class, "userId")).isTrue();
		assertThat(fieldExists(AppUserBranchScope.class, "branchId")).isTrue();
	}

	// ----------------------------------------------------------------------------------------------
	// Controller HTTP tests — focused Spring context with mocked repos
	// ----------------------------------------------------------------------------------------------

	/**
	 * Focused context test for the user provisioning controller. Loads ONLY
	 * {@link AppUserController} (not the full application, not {@code SecurityConfig}) with
	 * mocked {@link AppUserRepository} and {@link EmployeeMirrorRepository}, excluding
	 * DataSource/JPA autoconfiguration. The web MVC and Jackson auto-configurations are
	 * imported so the dispatcher servlet and JSON message converters are registered.
	 * This follows the same pattern as {@code EmployeeMirrorTest.E8ControllerTest}.
	 */
	@SpringBootTest(classes = AppUserController.class)
	@Import(ProvisioningControllerTest.TestConfig.class)
	@Nested
	class ProvisioningControllerTest {

		@Autowired
		private WebApplicationContext context;

		@Autowired
		private AppUserRepository appUserRepository;

		@Autowired
		private EmployeeMirrorRepository employeeMirrorRepository;

		@Autowired
		private AppUserService appUserService;

		private MockMvc mockMvc;

		@BeforeEach
		void setUp() {
			mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		}

		// AC-1: Happy path — provision a valid user returns 201
		@Test
		void provisionHappyPathReturns201() throws Exception {
			AppUser created = appUser("NIK001", Role.CREDIT_ANALYST, true);
			when(appUserService.provision(any(AppUserService.ProvisionRequest.class)))
					.thenReturn(created);

			mockMvc.perform(post("/users")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
								"employeeNik": "NIK001",
								"role": "CREDIT_ANALYST",
								"companyId": "CO01",
								"branchId": "BR01"
							}
							"""))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.employee_nik").value("NIK001"))
					.andExpect(jsonPath("$.role").value("CREDIT_ANALYST"))
					.andExpect(jsonPath("$.is_active").value(true));
		}

		// AC-2: SUPER_USER role → 422 UNKNOWN_ROLE (D-09)
		@Test
		void provisionSuperUserReturns422UnknownRole() throws Exception {
			when(appUserService.provision(any(AppUserService.ProvisionRequest.class)))
					.thenThrow(new AppUserService.ProvisioningException(
							AppUserService.ErrorCode.UNKNOWN_ROLE,
							"Role 'SUPER_USER' is not a D-10 role"));

			mockMvc.perform(post("/users")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
								"employeeNik": "NIK002",
								"role": "SUPER_USER",
								"companyId": "CO01",
								"branchId": "BR01"
							}
							"""))
					.andExpect(status().isUnprocessableEntity())
					.andExpect(jsonPath("$.error").value("UNKNOWN_ROLE"));
		}

		// AC-3: Resigned employee → 422 EMPLOYEE_RESIGNED
		@Test
		void provisionResignedEmployeeReturns422() throws Exception {
			when(appUserService.provision(any(AppUserService.ProvisionRequest.class)))
					.thenThrow(new AppUserService.ProvisioningException(
							AppUserService.ErrorCode.EMPLOYEE_RESIGNED,
							"NIK 'NIK003' is resigned"));

			mockMvc.perform(post("/users")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
								"employeeNik": "NIK003",
								"role": "CMO",
								"companyId": "CO01",
								"branchId": "BR01"
							}
							"""))
					.andExpect(status().isUnprocessableEntity())
					.andExpect(jsonPath("$.error").value("EMPLOYEE_RESIGNED"));
		}

		// NIK not found in mirror → 422 EMPLOYEE_NOT_FOUND
		@Test
		void provisionEmployeeNotFoundReturns422() throws Exception {
			when(appUserService.provision(any(AppUserService.ProvisionRequest.class)))
					.thenThrow(new AppUserService.ProvisioningException(
							AppUserService.ErrorCode.EMPLOYEE_NOT_FOUND,
							"NIK 'UNKNOWN' not found"));

			mockMvc.perform(post("/users")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
								"employeeNik": "UNKNOWN",
								"role": "CMO",
								"companyId": "CO01",
								"branchId": "BR01"
							}
							"""))
					.andExpect(status().isUnprocessableEntity())
					.andExpect(jsonPath("$.error").value("EMPLOYEE_NOT_FOUND"));
		}

		// 409 USER_ALREADY_EXISTS on duplicate NIK
		@Test
		void provisionDuplicateNikReturns409() throws Exception {
			when(appUserService.provision(any(AppUserService.ProvisionRequest.class)))
					.thenThrow(new AppUserService.ProvisioningException(
							AppUserService.ErrorCode.USER_ALREADY_EXISTS,
							"User already exists"));

			mockMvc.perform(post("/users")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
								"employeeNik": "NIK001",
								"role": "CREDIT_ANALYST",
								"companyId": "CO01",
								"branchId": "BR01"
							}
							"""))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error").value("USER_ALREADY_EXISTS"));
		}

		// E5 deactivate
		@Test
		void deactivateReturns200() throws Exception {
			AppUser deactivated = appUser("NIK001", Role.CREDIT_ANALYST, false);
			deactivated.setDeactivationReason(AppUser.DeactivationReason.manual);
			when(appUserService.deactivate(1L)).thenReturn(deactivated);

			mockMvc.perform(post("/users/{id}/deactivate", 1L))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.is_active").value(false))
					.andExpect(jsonPath("$.deactivation_reason").value("manual"));
		}

		// E5 reactivate rejected if mirror resigned → 409
		@Test
		void reactivateResignedReturns409() throws Exception {
			when(appUserService.reactivate(1L))
					.thenThrow(new AppUserService.ProvisioningException(
							AppUserService.ErrorCode.REACTIVATE_FORBIDDEN,
							"Cannot reactivate — employee is resigned"));

			mockMvc.perform(post("/users/{id}/reactivate", 1L))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error").value("REACTIVATE_FORBIDDEN"));
		}

		// E7 GET /users/roles returns D-10 catalog
		@Test
		void getRolesReturnsD10Catalog() throws Exception {
			mockMvc.perform(get("/users/roles"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$").isArray())
					.andExpect(jsonPath("$[0]").value("CMO"))
					.andExpect(jsonPath("$[1]").value("MARKETING_HEAD"))
					.andExpect(jsonPath("$[2]").value("CREDIT_ANALYST"))
					.andExpect(jsonPath("$[3]").value("KEPALA_CABANG"))
					.andExpect(jsonPath("$[4]").value("CREDIT_ADMIN"));
		}

		// E1 GET /users returns PageResponse
		@Test
		void listUsersReturnsPageResponse() throws Exception {
			AppUser user = appUser("NIK001", Role.CREDIT_ADMIN, true);
			when(appUserRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
					.thenReturn(new org.springframework.data.domain.PageImpl<>(
							List.of(user),
							org.springframework.data.domain.PageRequest.of(0, 20),
							1));

			mockMvc.perform(get("/users")
					.param("page", "0")
					.param("size", "20"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.items").isArray())
					.andExpect(jsonPath("$.items[0].employee_nik").value("NIK001"))
					.andExpect(jsonPath("$.recordCount").value(1));
		}

		// E3 GET /users/{id} detail
		@Test
		void getUserDetailReturns200() throws Exception {
			AppUser user = appUser("NIK001", Role.CMO, true);
			user.setId(1L);
			when(appUserRepository.findById(1L)).thenReturn(Optional.of(user));

			mockMvc.perform(get("/users/{id}", 1L))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.employee_nik").value("NIK001"))
					.andExpect(jsonPath("$.role").value("CMO"));
		}

		// E3 GET /users/{id} not found → 404
		@Test
		void getUserDetailNotFoundReturns404() throws Exception {
			when(appUserRepository.findById(99L)).thenReturn(Optional.empty());

			mockMvc.perform(get("/users/{id}", 99L))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error").value("USER_NOT_FOUND"));
		}

		// No POST /employees endpoint on this controller (HR system-of-record)
		@Test
		void postEmployeesReturns404Or405() throws Exception {
			int status = mockMvc.perform(post("/employees")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{}"))
					.andReturn().getResponse().getStatus();
			assertThat(status == 404 || status == 405)
					.as("POST /employees must not be a mapped endpoint on AppUserController (BR-EMPLOYEE-1)")
					.isTrue();
		}

			@TestConfiguration
		@org.springframework.boot.autoconfigure.ImportAutoConfiguration(
				value = {
						org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration.class,
						org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration.class,
						org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration.class
				},
				exclude = {
						DataSourceAutoConfiguration.class,
						HibernateJpaAutoConfiguration.class
				}
		)
		static class TestConfig {

			@Bean
			AppUserRepository appUserRepository() {
				return org.mockito.Mockito.mock(AppUserRepository.class);
			}

			@Bean
			EmployeeMirrorRepository employeeMirrorRepository() {
				return org.mockito.Mockito.mock(EmployeeMirrorRepository.class);
			}

			@Bean
			AppUserService appUserService(AppUserRepository appUserRepository,
					EmployeeMirrorRepository employeeMirrorRepository) {
				return org.mockito.Mockito.mock(AppUserService.class);
			}
		}

		private static AppUser appUser(String nik, Role role, boolean active) {
			AppUser user = new AppUser();
			user.setId(1L);
			user.setEmployeeNik(nik);
			user.setRole(role);
			user.setCompanyId("CO01");
			user.setActive(active);
			user.setActivationDate(Instant.now());
			user.setCreatedAt(Instant.now());
			user.setCreatedBy("SYSTEM");
			return user;
		}
	}

	// ----------------------------------------------------------------------------------------------
	// Service tests — provisioning logic + event listener (direct instantiation, no Spring context)
	// ----------------------------------------------------------------------------------------------

	@Nested
	class ServiceLogicTest {

		@Test
		void provisionRejectsSuperUserRole() {
			AppUserRepository mockUserRepo = org.mockito.Mockito.mock(AppUserRepository.class);
			EmployeeMirrorRepository mockEmpRepo = org.mockito.Mockito.mock(EmployeeMirrorRepository.class);
			AppUserService service = new AppUserService(mockUserRepo, mockEmpRepo);

			AppUserService.ProvisionRequest req = new AppUserService.ProvisionRequest(
					"NIK001", "SUPER_USER", "CO01", "BR01");

			assertThatThrownBy(() -> service.provision(req))
					.isInstanceOf(AppUserService.ProvisioningException.class)
					.satisfies(ex -> {
						AppUserService.ProvisioningException pe = (AppUserService.ProvisioningException) ex;
						assertThat(pe.getErrorCode()).isEqualTo(AppUserService.ErrorCode.UNKNOWN_ROLE);
					});

			// Verify the employee mirror was NEVER queried (role validation is first).
			verify(mockEmpRepo, never()).findByNik(any());
			verify(mockUserRepo, never()).existsByEmployeeNik(any());
		}

		@Test
		void provisionRejectsEmployeeNotFound() {
			AppUserRepository mockUserRepo = org.mockito.Mockito.mock(AppUserRepository.class);
			EmployeeMirrorRepository mockEmpRepo = org.mockito.Mockito.mock(EmployeeMirrorRepository.class);
			when(mockEmpRepo.findByNik("UNKNOWN")).thenReturn(Optional.empty());
			AppUserService service = new AppUserService(mockUserRepo, mockEmpRepo);

			AppUserService.ProvisionRequest req = new AppUserService.ProvisionRequest(
					"UNKNOWN", "CREDIT_ANALYST", "CO01", "BR01");

			assertThatThrownBy(() -> service.provision(req))
					.isInstanceOf(AppUserService.ProvisioningException.class)
					.satisfies(ex -> {
						AppUserService.ProvisioningException pe = (AppUserService.ProvisioningException) ex;
						assertThat(pe.getErrorCode()).isEqualTo(AppUserService.ErrorCode.EMPLOYEE_NOT_FOUND);
					});
		}

		@Test
		void provisionRejectsResignedEmployee() {
			AppUserRepository mockUserRepo = org.mockito.Mockito.mock(AppUserRepository.class);
			EmployeeMirrorRepository mockEmpRepo = org.mockito.Mockito.mock(EmployeeMirrorRepository.class);
			EmployeeMirror resigned = employee("NIK003", true);
			when(mockEmpRepo.findByNik("NIK003")).thenReturn(Optional.of(resigned));
			AppUserService service = new AppUserService(mockUserRepo, mockEmpRepo);

			AppUserService.ProvisionRequest req = new AppUserService.ProvisionRequest(
					"NIK003", "CMO", "CO01", "BR01");

			assertThatThrownBy(() -> service.provision(req))
					.isInstanceOf(AppUserService.ProvisioningException.class)
					.satisfies(ex -> {
						AppUserService.ProvisioningException pe = (AppUserService.ProvisioningException) ex;
						assertThat(pe.getErrorCode()).isEqualTo(AppUserService.ErrorCode.EMPLOYEE_RESIGNED);
					});
		}

		@Test
		void provisionRejectsDuplicateNik() {
			AppUserRepository mockUserRepo = org.mockito.Mockito.mock(AppUserRepository.class);
			EmployeeMirrorRepository mockEmpRepo = org.mockito.Mockito.mock(EmployeeMirrorRepository.class);
			EmployeeMirror active = employee("NIK001", false);
			when(mockEmpRepo.findByNik("NIK001")).thenReturn(Optional.of(active));
			when(mockUserRepo.existsByEmployeeNik("NIK001")).thenReturn(true);
			AppUserService service = new AppUserService(mockUserRepo, mockEmpRepo);

			AppUserService.ProvisionRequest req = new AppUserService.ProvisionRequest(
					"NIK001", "CREDIT_ANALYST", "CO01", "BR01");

			assertThatThrownBy(() -> service.provision(req))
					.isInstanceOf(AppUserService.ProvisioningException.class)
					.satisfies(ex -> {
						AppUserService.ProvisioningException pe = (AppUserService.ProvisioningException) ex;
						assertThat(pe.getErrorCode()).isEqualTo(AppUserService.ErrorCode.USER_ALREADY_EXISTS);
					});
		}

		@Test
		void provisionCreatesActiveUserOnHappyPath() {
			AppUserRepository mockUserRepo = org.mockito.Mockito.mock(AppUserRepository.class);
			EmployeeMirrorRepository mockEmpRepo = org.mockito.Mockito.mock(EmployeeMirrorRepository.class);
			EmployeeMirror active = employee("NIK001", false);
			when(mockEmpRepo.findByNik("NIK001")).thenReturn(Optional.of(active));
			when(mockUserRepo.existsByEmployeeNik("NIK001")).thenReturn(false);
			// Simulate save: set ID and return.
			doAnswer(invocation -> {
				AppUser u = invocation.getArgument(0);
				u.setId(1L);
				return u;
			}).when(mockUserRepo).save(any(AppUser.class));
			AppUserService service = new AppUserService(mockUserRepo, mockEmpRepo);

			AppUserService.ProvisionRequest req = new AppUserService.ProvisionRequest(
					"NIK001", "CREDIT_ANALYST", "CO01", "BR01");

			AppUser created = service.provision(req);

			assertThat(created.getEmployeeNik()).isEqualTo("NIK001");
			assertThat(created.getRole()).isEqualTo(Role.CREDIT_ANALYST);
			assertThat(created.isActive()).isTrue();
			assertThat(created.getActivationDate()).isNotNull();
			assertThat(created.getDeactivationReason()).isNull();
			assertThat(created.getDeactivationDate()).isNull();
			assertThat(created.getBranchScopes()).isNotNull();
			assertThat(created.getBranchScopes()).hasSize(1);
			assertThat(created.getBranchScopes().get(0).getBranchId()).isEqualTo("BR01");
		}

		// AC-3 / BR-BE07-27: HR resign event → auto-deactivate
		@Test
		void hrResignEventAutoDeactivatesUser() {
			AppUserRepository mockUserRepo = org.mockito.Mockito.mock(AppUserRepository.class);
			EmployeeMirrorRepository mockEmpRepo = org.mockito.Mockito.mock(EmployeeMirrorRepository.class);

			AppUser activeUser = appUserForService("NIK001", Role.CREDIT_ANALYST, true);
			when(mockUserRepo.findByEmployeeNik("NIK001")).thenReturn(Optional.of(activeUser));
			when(mockUserRepo.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

			AppUserService service = new AppUserService(mockUserRepo, mockEmpRepo);

			// Fire the HR resign event.
			service.onEmployeeResigned(new AppUserService.EmployeeResignedEvent("NIK001"));

			assertThat(activeUser.isActive()).isFalse();
			assertThat(activeUser.getDeactivationReason())
					.isEqualTo(AppUser.DeactivationReason.hr_resigned);
			assertThat(activeUser.getDeactivationDate()).isNotNull();
		}

		@Test
		void hrResignEventNoOpIfNoUserExists() {
			AppUserRepository mockUserRepo = org.mockito.Mockito.mock(AppUserRepository.class);
			EmployeeMirrorRepository mockEmpRepo = org.mockito.Mockito.mock(EmployeeMirrorRepository.class);
			when(mockUserRepo.findByEmployeeNik("NIK999")).thenReturn(Optional.empty());

			AppUserService service = new AppUserService(mockUserRepo, mockEmpRepo);

			// Fire the event — should be a no-op, no save called.
			service.onEmployeeResigned(new AppUserService.EmployeeResignedEvent("NIK999"));

			verify(mockUserRepo, never()).save(any());
		}

		@Test
		void hrResignEventNoOpIfUserAlreadyInactive() {
			AppUserRepository mockUserRepo = org.mockito.Mockito.mock(AppUserRepository.class);
			EmployeeMirrorRepository mockEmpRepo = org.mockito.Mockito.mock(EmployeeMirrorRepository.class);

			AppUser inactiveUser = appUserForService("NIK001", Role.CREDIT_ANALYST, false);
			inactiveUser.setDeactivationReason(AppUser.DeactivationReason.manual);
			when(mockUserRepo.findByEmployeeNik("NIK001")).thenReturn(Optional.of(inactiveUser));

			AppUserService service = new AppUserService(mockUserRepo, mockEmpRepo);

			service.onEmployeeResigned(new AppUserService.EmployeeResignedEvent("NIK001"));

			// Should not overwrite the existing deactivation reason.
			assertThat(inactiveUser.getDeactivationReason())
					.isEqualTo(AppUser.DeactivationReason.manual);
			verify(mockUserRepo, never()).save(any());
		}

		// E5 reactivate rejected if mirror resigned
		@Test
		void reactivateRejectedIfMirrorResigned() {
			AppUserRepository mockUserRepo = org.mockito.Mockito.mock(AppUserRepository.class);
			EmployeeMirrorRepository mockEmpRepo = org.mockito.Mockito.mock(EmployeeMirrorRepository.class);

			AppUser inactiveUser = appUserForService("NIK001", Role.CREDIT_ANALYST, false);
			inactiveUser.setDeactivationReason(AppUser.DeactivationReason.hr_resigned);
			EmployeeMirror resigned = employee("NIK001", true);

			when(mockUserRepo.findById(1L)).thenReturn(Optional.of(inactiveUser));
			when(mockEmpRepo.findByNik("NIK001")).thenReturn(Optional.of(resigned));

			AppUserService service = new AppUserService(mockUserRepo, mockEmpRepo);

			assertThatThrownBy(() -> service.reactivate(1L))
					.isInstanceOf(AppUserService.ProvisioningException.class)
					.satisfies(ex -> {
						AppUserService.ProvisioningException pe = (AppUserService.ProvisioningException) ex;
						assertThat(pe.getErrorCode()).isEqualTo(AppUserService.ErrorCode.REACTIVATE_FORBIDDEN);
					});
		}

		@Test
		void reactivateSucceedsIfMirrorNotResigned() {
			AppUserRepository mockUserRepo = org.mockito.Mockito.mock(AppUserRepository.class);
			EmployeeMirrorRepository mockEmpRepo = org.mockito.Mockito.mock(EmployeeMirrorRepository.class);

			AppUser inactiveUser = appUserForService("NIK001", Role.CREDIT_ANALYST, false);
			inactiveUser.setDeactivationReason(AppUser.DeactivationReason.manual);
			EmployeeMirror active = employee("NIK001", false);

			when(mockUserRepo.findById(1L)).thenReturn(Optional.of(inactiveUser));
			when(mockEmpRepo.findByNik("NIK001")).thenReturn(Optional.of(active));
			when(mockUserRepo.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

			AppUserService service = new AppUserService(mockUserRepo, mockEmpRepo);

			AppUser reactivated = service.reactivate(1L);

			assertThat(reactivated.isActive()).isTrue();
			assertThat(reactivated.getDeactivationReason()).isNull();
			assertThat(reactivated.getDeactivationDate()).isNull();
			assertThat(reactivated.getActivationDate()).isNotNull();
		}

		private static EmployeeMirror employee(String nik, boolean resigned) {
			EmployeeMirror emp = new EmployeeMirror();
			emp.setId(1L);
			emp.setNik(nik);
			emp.setName("Test Employee");
			emp.setResigned(resigned);
			emp.setBranchId("BR01");
			emp.setPositionId("POS01");
			emp.setNationalId("1234567890123456");
			emp.setEmployeeStatus("A");
			emp.setJoinDate(LocalDate.of(2020, 1, 1));
			emp.setCreatedAt(Instant.now());
			emp.setCreatedBy("SYSTEM");
			return emp;
		}

		private static AppUser appUserForService(String nik, Role role, boolean active) {
			AppUser user = new AppUser();
			user.setId(1L);
			user.setEmployeeNik(nik);
			user.setRole(role);
			user.setCompanyId("CO01");
			user.setActive(active);
			user.setCreatedAt(Instant.now());
			user.setCreatedBy("SYSTEM");
			return user;
		}
	}

	// ----------------------------------------------------------------------------------------------
	// Helpers
	// ----------------------------------------------------------------------------------------------

	private static Field getDeclaredField(Class<?> type, String fieldName) {
		try {
			return type.getDeclaredField(fieldName);
		} catch (NoSuchFieldException e) {
			throw new AssertionError(type.getSimpleName() + " must declare field '" + fieldName + "'", e);
		}
	}

	private static boolean fieldExists(Class<?> type, String fieldName) {
		try {
			type.getDeclaredField(fieldName);
			return true;
		} catch (NoSuchFieldException e) {
			return false;
		}
	}
}
// SDD-PROVENANCE: U-003 | vault: .mega-sdd/vaults/acquisition-master-data | TDD test for AppUser — AC-1 happy path 201, AC-2 SUPER_USER→422 UNKNOWN_ROLE (D-09), AC-3 resigned→422+auto-deactivate (BR-BE07-27), 409 duplicate, E7 /roles D-10 catalog, entity reflection (no password BR-SHELL-1, no isSuperUser D-09), E5 reactivate rejected if mirror resigned
