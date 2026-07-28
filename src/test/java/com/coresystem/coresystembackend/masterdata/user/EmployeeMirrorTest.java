package com.coresystem.coresystembackend.masterdata.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.coresystem.coresystembackend.masterdata.common.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * TDD test for U-002 — Employee mirror entity + repository + HR sync job + E8 controller (Tier B).
 *
 * <p>Verifies the five acceptance criteria from the unit spec:
 * <ol>
 *   <li>{@link EmployeeMirror} has an explicit {@code isResigned} boolean field (fix Edge Case 12 —
 *       the legacy {@code vw_HREmployeeData} comment-out filter {@code IsActive} is NOT replicated;
 *       {@code is_resigned} is a first-class column, not a hidden filter).</li>
 *   <li>E8 {@code GET /employees} returns {@code is_resigned} explicitly in the response — the
 *       picker surfaces resign status so the caller can distinguish active vs resigned employees
 *       (three distinct signals per BR-BE07-22: resigned, not-found, source-error).</li>
 *   <li>Not-found NIK lookup returns {@code 404} (NOT an empty 200 — BR-BE07-22 forbids
 *       silent-success: "not found" is a distinct signal from "empty result set").</li>
 *   <li>No write endpoints ({@code POST}/{@code PUT}/{@code DELETE}) exist on
 *       {@link EmployeeMirrorController} — HR is the system-of-record (BR-EMPLOYEE-1); the mirror
 *       is Tier B read-only, writable only by the sync job.</li>
 *   <li>{@link EmployeeMirrorSyncJob#syncFromHr()} stub throws
 *       {@link UnsupportedOperationException} — OQ-BE07-02 (HR sync transport undecided) is NOT
 *       silently faked; the stub is an explicit TBD marker.</li>
 * </ol>
 *
 * <p>Test structure follows the existing project patterns:
 * <ul>
 *   <li>Entity reflection tests (isResigned field, @Entity, @Table, extends AuditableEntity, no
 *       @Version) mirror {@code MasterDataScaffoldTest} — pure reflection, no Spring context.</li>
 *   <li>The E8 controller test uses {@code @SpringBootTest(classes = EmployeeMirrorController.class)}
 *       with a mocked {@link EmployeeMirrorRepository} (via {@code @MockitoBean}) and manual MockMvc
 *       setup, excluding DataSource/JPA autoconfiguration (same pattern as
 *       {@code AuthLoginIntegrationTest}). Only the controller class is loaded — NOT
 *       {@code SecurityConfig} — so the security filter chain does not interfere with the endpoint
 *       assertions (the security posture for {@code /employees} is a separate concern owned by a
 *       later unit).</li>
 *   <li>The sync job stub is tested by direct instantiation (no Spring context needed — the stub
 *       has no dependencies).</li>
 * </ul>
 */
class EmployeeMirrorTest {

	// ----------------------------------------------------------------------------------------------
	// Entity reflection tests — verify the JPA mapping without a Spring context.
	// ----------------------------------------------------------------------------------------------

	@Test
	void employeeMirrorIsJpaEntityMappedToMstEmployeeMirrorTable() {
		assertThat(EmployeeMirror.class).hasAnnotation(Entity.class);
		Table table = EmployeeMirror.class.getAnnotation(Table.class);
		assertThat(table).as("EmployeeMirror must carry @Table").isNotNull();
		assertThat(table.name()).isEqualTo("mst_employee_mirror");
	}

	@Test
	void employeeMirrorExtendsAuditableEntity() {
		assertThat(AuditableEntity.class).isAssignableFrom(EmployeeMirror.class);
	}

	@Test
	void employeeMirrorHasIsResignedBooleanField() {
		// Fix Edge Case 12: is_resigned (Fkeluar) MUST be an explicit boolean column — NOT a
		// comment-out filter like the legacy vw_HREmployeeData IsActive.
		Field isResignedField;
		try {
			isResignedField = EmployeeMirror.class.getDeclaredField("isResigned");
		} catch (NoSuchFieldException e) {
			throw new AssertionError("EmployeeMirror must declare an 'isResigned' field (Edge Case 12 fix)", e);
		}
		assertThat(isResignedField.getType())
				.as("isResigned must be boolean (maps to is_resigned BOOLEAN)")
				.isEqualTo(boolean.class);
		Column column = isResignedField.getAnnotation(Column.class);
		assertThat(column).as("isResigned must carry @Column").isNotNull();
		assertThat(column.name()).isEqualTo("is_resigned");
	}

	@Test
	void employeeMirrorHasNoVersionField() {
		// Tier B read-only — NO @Version (only concurrent-edit Tier A tables get optimistic locking).
		assertThat(EmployeeMirror.class.getDeclaredFields())
				.as("EmployeeMirror is Tier B read-only — must NOT declare a 'version' field")
				.noneMatch(f -> f.getName().equals("version"));
	}

	@Test
	void employeeMirrorHasIdPrimaryKeyWithIdentityGeneration() {
		Field idField;
		try {
			idField = EmployeeMirror.class.getDeclaredField("id");
		} catch (NoSuchFieldException e) {
			throw new AssertionError("EmployeeMirror must declare an 'id' PK field", e);
		}
		assertThat(idField.getType()).isEqualTo(Long.class);
		assertThat(idField.isAnnotationPresent(Id.class))
				.as("id must carry @Id").isTrue();
		GeneratedValue generatedValue = idField.getAnnotation(GeneratedValue.class);
		assertThat(generatedValue)
				.as("id must carry @GeneratedValue").isNotNull();
		assertThat(generatedValue.strategy()).isEqualTo(GenerationType.IDENTITY);
	}

	@Test
	void employeeMirrorNikIsUniqueColumn() {
		Field nikField;
		try {
			nikField = EmployeeMirror.class.getDeclaredField("nik");
		} catch (NoSuchFieldException e) {
			throw new AssertionError("EmployeeMirror must declare a 'nik' field", e);
		}
		Column column = nikField.getAnnotation(Column.class);
		assertThat(column).as("nik must carry @Column").isNotNull();
		assertThat(column.name()).isEqualTo("nik");
		assertThat(column.unique())
				.as("nik must be unique (business key ux_mst_employee_mirror_nik)")
				.isTrue();
	}

	@Test
	void employeeMirrorNationalIdIsLocked16CharVarchar() {
		// DB-CONVENTIONS §3: national_id (NoKtp) NIK VARCHAR(16) [LOCKED]
		Field nationalIdField;
		try {
			nationalIdField = EmployeeMirror.class.getDeclaredField("nationalId");
		} catch (NoSuchFieldException e) {
			throw new AssertionError("EmployeeMirror must declare a 'nationalId' field", e);
		}
		Column column = nationalIdField.getAnnotation(Column.class);
		assertThat(column).as("nationalId must carry @Column").isNotNull();
		assertThat(column.name()).isEqualTo("national_id");
		assertThat(column.length())
				.as("national_id must be VARCHAR(16) per DB-CONVENTIONS §3 NIK [LOCKED]")
				.isEqualTo(16);
	}

	@Test
	void employeeMirrorDeclaresAllBusinessFields() {
		// BE-07 §3.1 census: nik, name, branchId (KdCabang), positionId (KdJabat),
		// nationalId (NoKtp), isResigned (Fkeluar), employeeStatus (Stpegawai),
		// joinDate (Tglmasuk), exitDate (Tglkeluar)
		assertThat(fieldExists(EmployeeMirror.class, "nik")).isTrue();
		assertThat(fieldExists(EmployeeMirror.class, "name")).isTrue();
		assertThat(fieldExists(EmployeeMirror.class, "branchId")).isTrue();
		assertThat(fieldExists(EmployeeMirror.class, "positionId")).isTrue();
		assertThat(fieldExists(EmployeeMirror.class, "nationalId")).isTrue();
		assertThat(fieldExists(EmployeeMirror.class, "isResigned")).isTrue();
		assertThat(fieldExists(EmployeeMirror.class, "employeeStatus")).isTrue();
		assertThat(fieldExists(EmployeeMirror.class, "joinDate")).isTrue();
		assertThat(fieldExists(EmployeeMirror.class, "exitDate")).isTrue();
	}

	// ----------------------------------------------------------------------------------------------
	// Sync job stub test — OQ-BE07-02 not yet decided; stub must throw, NOT fake.
	// ----------------------------------------------------------------------------------------------

	@Test
	void syncFromHrStubThrowsUnsupportedOperationException() {
		// OQ-BE07-02: HR sync transport (batch/CDC/API) not yet decided.
		// The stub MUST throw — it is an explicit TBD marker, not a silent fake implementation.
		EmployeeMirrorSyncJob syncJob = new EmployeeMirrorSyncJob();
		assertThatThrownBy(syncJob::syncFromHr)
				.isInstanceOf(UnsupportedOperationException.class)
				.hasMessageContaining("OQ-BE07-02")
				.hasMessageContaining("HR sync transport not yet decided");
	}

	// ----------------------------------------------------------------------------------------------
	// Controller reflection test — NO write endpoints (BR-EMPLOYEE-1 HR system-of-record).
	// ----------------------------------------------------------------------------------------------

	@Test
	void controllerHasNoWriteEndpoints() {
		// BR-EMPLOYEE-1: HR is the system-of-record. The mirror is Tier B read-only.
		// POST/PUT/DELETE must NOT exist on EmployeeMirrorController.
		assertThat(EmployeeMirrorController.class.getDeclaredMethods())
				.as("EmployeeMirrorController must NOT declare @PostMapping methods (HR system-of-record BR-EMPLOYEE-1)")
				.noneMatch(m -> m.isAnnotationPresent(
						org.springframework.web.bind.annotation.PostMapping.class));
		assertThat(EmployeeMirrorController.class.getDeclaredMethods())
				.as("EmployeeMirrorController must NOT declare @PutMapping methods (HR system-of-record BR-EMPLOYEE-1)")
				.noneMatch(m -> m.isAnnotationPresent(
						org.springframework.web.bind.annotation.PutMapping.class));
		assertThat(EmployeeMirrorController.class.getDeclaredMethods())
				.as("EmployeeMirrorController must NOT declare @DeleteMapping methods (HR system-of-record BR-EMPLOYEE-1)")
				.noneMatch(m -> m.isAnnotationPresent(
						org.springframework.web.bind.annotation.DeleteMapping.class));
	}

	// ----------------------------------------------------------------------------------------------
	// E8 controller HTTP tests — uses a focused Spring context (controller only, repo mocked).
	// ----------------------------------------------------------------------------------------------

	/**
	 * Focused context test for the E8 controller. Loads ONLY {@link EmployeeMirrorController}
	 * (not the full application, not {@code SecurityConfig}) with a mocked
	 * {@link EmployeeMirrorRepository}, excluding DataSource/JPA autoconfiguration. The web MVC
	 * and Jackson auto-configurations are imported so the dispatcher servlet and JSON message
	 * converters are registered (otherwise the controller returns 406 — no converter for JSON).
	 * This follows the same pattern as {@code AuthLoginIntegrationTest} and
	 * {@code SecurityConfigTest}: manual MockMvc setup against the
	 * {@link WebApplicationContext}, mocked collaborators via {@code @MockitoBean}.
	 */
	@SpringBootTest(classes = EmployeeMirrorController.class)
	@Import(E8ControllerTest.TestConfig.class)
	@Nested
	class E8ControllerTest {

		@Autowired
		private WebApplicationContext context;

		@MockitoBean
		private EmployeeMirrorRepository employeeMirrorRepository;

		private MockMvc mockMvc;

		@BeforeEach
		void setUp() {
			mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		}

		@Test
		void e8SearchReturnsIsResignedExplicitly() throws Exception {
			// E8 GET /employees returns is_resigned explicitly in the response (fix Edge Case 12).
			EmployeeMirror active = employee("NIK001", "Alice", "BR01", "POS01", false);
			EmployeeMirror resigned = employee("NIK002", "Bob", "BR02", "POS02", true);
			Page<EmployeeMirror> page = new PageImpl<>(List.of(active, resigned),
					PageRequest.of(0, 20), 2);
			when(employeeMirrorRepository.findByNikContainingIgnoreCaseAndIsResignedFalse(
					eq("NIK"), any(Pageable.class)))
					.thenReturn(page);

			mockMvc.perform(get("/employees")
					.param("search", "NIK")
					.param("page", "0")
					.param("size", "20")
					.accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk())
					// is_resigned must be present and explicit on every item
					.andExpect(jsonPath("$.items[0].is_resigned").value(false))
					.andExpect(jsonPath("$.items[1].is_resigned").value(true))
					// PageResponse envelope shape (camelCase — no global PropertyNamingStrategy
					// is configured; the DTO fields use @JsonProperty for snake_case, but the
					// PageResponse envelope itself is camelCase per its U-001 design)
					.andExpect(jsonPath("$.recordCount").value(2))
					.andExpect(jsonPath("$.page").value(0));
		}

		@Test
		void e8LookupByNikNotFoundReturns404() throws Exception {
			// BR-BE07-22: not-found NIK → 404 EMPLOYEE_NOT_FOUND (NOT empty 200 — silent-success
			// is forbidden; "not found" is a distinct signal from "empty result set").
			when(employeeMirrorRepository.findByNik("UNKNOWN_NIK"))
					.thenReturn(Optional.empty());

			mockMvc.perform(get("/employees/{nik}", "UNKNOWN_NIK")
					.accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.is_resigned").doesNotExist())
					.andExpect(jsonPath("$.error").exists());
		}

		@Test
		void e8LookupByNikFoundReturnsIsResigned() throws Exception {
			// Single-employee lookup by NIK returns the employee with is_resigned explicit.
			EmployeeMirror resigned = employee("NIK003", "Charlie", "BR03", "POS03", true);
			when(employeeMirrorRepository.findByNik("NIK003"))
					.thenReturn(Optional.of(resigned));

			mockMvc.perform(get("/employees/{nik}", "NIK003")
					.accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.nik").value("NIK003"))
					.andExpect(jsonPath("$.is_resigned").value(true));
		}

		@Test
		void e8SearchReturnsPageResponseEnvelope() throws Exception {
			// The search endpoint returns a PageResponse envelope {items, page, total_pages,
			// record_count} per BR-BE07-20.
			EmployeeMirror emp = employee("NIK004", "Diana", "BR04", "POS04", false);
			Page<EmployeeMirror> page = new PageImpl<>(List.of(emp),
					PageRequest.of(0, 20), 1);
			when(employeeMirrorRepository.findByNikContainingIgnoreCaseAndIsResignedFalse(
					eq("Diana"), any(Pageable.class)))
					.thenReturn(page);

			mockMvc.perform(get("/employees")
					.param("search", "Diana")
					.accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.items").isArray())
					.andExpect(jsonPath("$.items[0].nik").value("NIK004"))
					.andExpect(jsonPath("$.items[0].name").value("Diana"))
					.andExpect(jsonPath("$.items[0].branch_id").value("BR04"))
					.andExpect(jsonPath("$.items[0].position_id").value("POS04"))
					.andExpect(jsonPath("$.items[0].is_resigned").value(false));
		}

		@Test
		void postEmployeesReturns404Or405() throws Exception {
			// BR-EMPLOYEE-1: NO write endpoints. POST /employees must not be handled by the
			// controller. Since only the controller is loaded (no other handlers), an unmapped
			// POST returns 404 (dispatcher has no handler). Either 404 or 405 is acceptable —
			// both prove the endpoint does NOT exist.
			int status = mockMvc.perform(post("/employees")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{}"))
					.andReturn().getResponse().getStatus();
			assertThat(status == 404 || status == 405)
					.as("POST /employees must not be a mapped endpoint (BR-EMPLOYEE-1), got " + status)
					.isTrue();
		}

		@Test
		void putEmployeesReturns404Or405() throws Exception {
			int status = mockMvc.perform(put("/employees/1")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{}"))
					.andReturn().getResponse().getStatus();
			assertThat(status == 404 || status == 405)
					.as("PUT /employees must not be a mapped endpoint (BR-EMPLOYEE-1), got " + status)
					.isTrue();
		}

		@Test
		void deleteEmployeesReturns404Or405() throws Exception {
			int status = mockMvc.perform(delete("/employees/1"))
					.andReturn().getResponse().getStatus();
			assertThat(status == 404 || status == 405)
					.as("DELETE /employees must not be a mapped endpoint (BR-EMPLOYEE-1), got " + status)
					.isTrue();
		}

		/**
		 * Test configuration: exclude DataSource/JPA autoconfiguration (same pattern as
		 * {@code AuthLoginIntegrationTest.TestConfig} — the repository is mocked via
		 * {@code @MockitoBean}, so no real datasource/JPA is needed) and import the WebMVC +
		 * Jackson auto-configurations so the dispatcher servlet and JSON message converters are
		 * registered (the focused {@code classes = EmployeeMirrorController.class} context does
		 * not auto-discover them).
		 */
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
			// Empty — annotation-driven configuration only.
		}

		private static EmployeeMirror employee(String nik, String name, String branchId,
				String positionId, boolean isResigned) {
			EmployeeMirror emp = new EmployeeMirror();
			emp.setId(1L);
			emp.setNik(nik);
			emp.setName(name);
			emp.setBranchId(branchId);
			emp.setPositionId(positionId);
			emp.setResigned(isResigned);
			emp.setNationalId("1234567890123456");
			emp.setEmployeeStatus("A");
			emp.setJoinDate(LocalDate.of(2020, 1, 1));
			emp.setCreatedAt(Instant.now());
			emp.setCreatedBy("SYSTEM");
			return emp;
		}
	}

	// ----------------------------------------------------------------------------------------------
	// Helpers
	// ----------------------------------------------------------------------------------------------

	private static boolean fieldExists(Class<?> type, String fieldName) {
		try {
			type.getDeclaredField(fieldName);
			return true;
		} catch (NoSuchFieldException e) {
			return false;
		}
	}

}
// SDD-PROVENANCE: U-002 | vault: .mega-sdd/vaults/acquisition-master-data | TDD test for EmployeeMirror entity (isResigned explicit Edge Case 12 fix) + E8 GET /employees (is_resigned in response, 404 not-found BR-BE07-22) + no write endpoints (BR-EMPLOYEE-1) + sync stub throws (OQ-BE07-02)
