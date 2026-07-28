package com.coresystem.coresystembackend.masterdata.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Field;
import java.time.Instant;
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

import com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest;
import com.coresystem.coresystembackend.masterdata.makercheck.MakerCheckerService;
import com.coresystem.coresystembackend.masterdata.user.Role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

class MenuGrantTest {

	@Test
	void menuIsJpaEntityMappedToCfgMenuTable() {
		assertThat(Menu.class).hasAnnotation(Entity.class);
		Table table = Menu.class.getAnnotation(Table.class);
		assertThat(table).isNotNull();
		assertThat(table.name()).isEqualTo("cfg_menu");
	}

	@Test
	void menuExtendsVersionedEntity() {
		assertThat(com.coresystem.coresystembackend.masterdata.common.VersionedEntity.class).isAssignableFrom(Menu.class);
	}

	@Test
	void menuHasIdPrimaryKeyWithIdentityGeneration() {
		Field idField = getDeclaredField(Menu.class, "id");
		assertThat(idField.getType()).isEqualTo(Long.class);
		assertThat(idField.isAnnotationPresent(Id.class)).isTrue();
		GeneratedValue gv = idField.getAnnotation(GeneratedValue.class);
		assertThat(gv).isNotNull();
		assertThat(gv.strategy()).isEqualTo(GenerationType.IDENTITY);
	}

	@Test
	void menuHasParentIdSelfFk() {
		Field parentField = getDeclaredField(Menu.class, "parentId");
		assertThat(parentField.getType()).isEqualTo(Long.class);
		Column col = parentField.getAnnotation(Column.class);
		assertThat(col).isNotNull();
		assertThat(col.name()).isEqualTo("parent_id");
	}

	@Test
	void menuTransTypeIdPrefixColumnIsLockedVerbatim() {
		Field prefixField = getDeclaredField(Menu.class, "transTypeIdPrefix");
		Column col = prefixField.getAnnotation(Column.class);
		assertThat(col).isNotNull();
		assertThat(col.name()).isEqualTo("trans_type_id_prefix");
	}

	@Test
	void menuHasDisplayOrderIntField() {
		Field orderField = getDeclaredField(Menu.class, "displayOrder");
		assertThat(orderField.getType()).isEqualTo(int.class);
		Column col = orderField.getAnnotation(Column.class);
		assertThat(col).isNotNull();
		assertThat(col.name()).isEqualTo("display_order");
	}

	@Test
	void menuHasIsActiveBooleanField() {
		Field activeField = getDeclaredField(Menu.class, "isActive");
		assertThat(activeField.getType()).isEqualTo(boolean.class);
		Column col = activeField.getAnnotation(Column.class);
		assertThat(col).isNotNull();
		assertThat(col.name()).isEqualTo("is_active");
	}

	@Test
	void menuHasModuleAndNameAndRouteFields() {
		assertThat(fieldExists(Menu.class, "module")).isTrue();
		assertThat(fieldExists(Menu.class, "name")).isTrue();
		assertThat(fieldExists(Menu.class, "route")).isTrue();
	}

	@Test
	void menuRoleGrantIsJpaEntityMappedToCfgMenuRoleGrantTable() {
		assertThat(MenuRoleGrant.class).hasAnnotation(Entity.class);
		Table table = MenuRoleGrant.class.getAnnotation(Table.class);
		assertThat(table).isNotNull();
		assertThat(table.name()).isEqualTo("cfg_menu_role_grant");
	}

	@Test
	void menuRoleGrantExtendsVersionedEntity() {
		assertThat(com.coresystem.coresystembackend.masterdata.common.VersionedEntity.class).isAssignableFrom(MenuRoleGrant.class);
	}

	@Test
	void menuRoleGrantHasUniqueConstraintOnRoleAndMenuId() {
		Table table = MenuRoleGrant.class.getAnnotation(Table.class);
		UniqueConstraint[] ucs = table.uniqueConstraints();
		assertThat(ucs).isNotEmpty();
		boolean found = false;
		for (UniqueConstraint uc : ucs) {
			List<String> cols = List.of(uc.columnNames());
			if (cols.contains("role") && cols.contains("menu_id")) { found = true; break; }
		}
		assertThat(found).isTrue();
	}

	@Test
	void menuRoleGrantRoleIsEnumeratedString() {
		Field roleField = getDeclaredField(MenuRoleGrant.class, "role");
		assertThat(roleField.getType()).isEqualTo(Role.class);
		Enumerated enumerated = roleField.getAnnotation(Enumerated.class);
		assertThat(enumerated).isNotNull();
		assertThat(enumerated.value()).isEqualTo(EnumType.STRING);
	}

	@Test
	void menuRoleGrantHasIsViewOnlyAndIsActiveFields() {
		assertThat(fieldExists(MenuRoleGrant.class, "isViewOnly")).isTrue();
		assertThat(fieldExists(MenuRoleGrant.class, "isActive")).isTrue();
	}

	@Test
	void menuUserGrantSpecialIsJpaEntityMappedToCfgMenuUserGrantSpecialTable() {
		assertThat(MenuUserGrantSpecial.class).hasAnnotation(Entity.class);
		Table table = MenuUserGrantSpecial.class.getAnnotation(Table.class);
		assertThat(table).isNotNull();
		assertThat(table.name()).isEqualTo("cfg_menu_user_grant_special");
	}

	@Test
	void menuUserGrantSpecialHasGrantedReasonField() {
		assertThat(fieldExists(MenuUserGrantSpecial.class, "grantedReason")).isTrue();
		Field reasonField = getDeclaredField(MenuUserGrantSpecial.class, "grantedReason");
		Column col = reasonField.getAnnotation(Column.class);
		assertThat(col).isNotNull();
		assertThat(col.name()).isEqualTo("granted_reason");
	}

	@Test
	void menuUserGrantSpecialHasUserIdAndMenuIdFields() {
		assertThat(fieldExists(MenuUserGrantSpecial.class, "userId")).isTrue();
		assertThat(fieldExists(MenuUserGrantSpecial.class, "menuId")).isTrue();
	}

	@Nested
	class ServiceLogicTest {
		private MenuRepository menuRepository;
		private MenuRoleGrantRepository roleGrantRepository;
		private MenuUserGrantSpecialRepository userGrantRepository;
		private MakerCheckerService makerCheckerService;
		private MenuService menuService;

		@BeforeEach
		void setUp() {
			menuRepository = org.mockito.Mockito.mock(MenuRepository.class);
			roleGrantRepository = org.mockito.Mockito.mock(MenuRoleGrantRepository.class);
			userGrantRepository = org.mockito.Mockito.mock(MenuUserGrantSpecialRepository.class);
			makerCheckerService = org.mockito.Mockito.mock(MakerCheckerService.class);
			menuService = new MenuService(menuRepository, roleGrantRepository, userGrantRepository, makerCheckerService);
		}

		@Test
		void e6MenuEfektifReturnsOnlyActiveMenusWithActiveGrants() {
			Menu parent = menu(1L, null, "ACQ", "Acquisition", "/acq", "PREFIX_AQ", 1, true);
			Menu child = menu(2L, 1L, "ACQ", "Application", "/acq/app", "PREFIX_AP", 2, true);
			Menu inactive = menu(3L, null, "ACQ", "Disabled", "/disabled", "PREFIX_DIS", 3, false);
			when(menuRepository.findByIsActiveTrueOrderByDisplayOrder()).thenReturn(List.of(parent, child, inactive));
			MenuRoleGrant parentGrant = grant(1L, Role.CREDIT_ANALYST, 1L, false, true);
			MenuRoleGrant childGrant = grant(2L, Role.CREDIT_ANALYST, 2L, true, true);
			MenuRoleGrant inactiveGrant = grant(3L, Role.CREDIT_ANALYST, 3L, false, false);
			when(roleGrantRepository.findByRoleAndIsActiveTrue(Role.CREDIT_ANALYST)).thenReturn(List.of(parentGrant, childGrant, inactiveGrant));
			List<MenuService.MenuTreeNode> tree = menuService.getEffectiveMenuTree(Role.CREDIT_ANALYST, null);
			assertThat(tree).hasSize(1);
			MenuService.MenuTreeNode node = tree.get(0);
			assertThat(node.name()).isEqualTo("Acquisition");
			assertThat(node.route()).isEqualTo("/acq");
			assertThat(node.isViewOnly()).isFalse();
			assertThat(node.children()).hasSize(1);
			assertThat(node.children().get(0).name()).isEqualTo("Application");
			assertThat(node.children().get(0).isViewOnly()).isTrue();
			assertThat(tree).noneMatch(n -> n.name().equals("Disabled"));
		}

		@Test
		void e6MenuEfektifDoesNotExposeTransTypeIdPrefix() {
			Menu parent = menu(1L, null, "ACQ", "Acquisition", "/acq", "SECRET_PREFIX", 1, true);
			when(menuRepository.findByIsActiveTrueOrderByDisplayOrder()).thenReturn(List.of(parent));
			MenuRoleGrant g = grant(1L, Role.CMO, 1L, false, true);
			when(roleGrantRepository.findByRoleAndIsActiveTrue(Role.CMO)).thenReturn(List.of(g));
			List<MenuService.MenuTreeNode> tree = menuService.getEffectiveMenuTree(Role.CMO, null);
			assertThat(tree).hasSize(1);
			assertThat(MenuService.MenuTreeNode.class.getRecordComponents()).noneMatch(c -> c.getName().equals("transTypeIdPrefix"));
		}

		@Test
		void e6MenuEfektifIncludesSpecialGrants() {
			Menu specialMenu = menu(1L, null, "ACQ", "Special Access", "/special", "PREFIX_SP", 1, true);
			when(menuRepository.findByIsActiveTrueOrderByDisplayOrder()).thenReturn(List.of(specialMenu));
			when(roleGrantRepository.findByRoleAndIsActiveTrue(Role.CREDIT_ANALYST)).thenReturn(List.of());
			MenuUserGrantSpecial userG = userGrant(1L, 100L, 1L, true, "Temp project access");
			when(userGrantRepository.findByUserIdAndIsActiveTrue(100L)).thenReturn(List.of(userG));
			List<MenuService.MenuTreeNode> tree = menuService.getEffectiveMenuTree(Role.CREDIT_ANALYST, 100L);
			assertThat(tree).hasSize(1);
			assertThat(tree.get(0).name()).isEqualTo("Special Access");
		}

		@Test
		void e6MenuEfektifExcludesMenuWithNoActiveGrantOfAnyKind() {
			Menu ungranted = menu(1L, null, "ACQ", "No Access", "/noaccess", "PREFIX_NA", 1, true);
			when(menuRepository.findByIsActiveTrueOrderByDisplayOrder()).thenReturn(List.of(ungranted));
			when(roleGrantRepository.findByRoleAndIsActiveTrue(Role.CREDIT_ANALYST)).thenReturn(List.of());
			when(userGrantRepository.findByUserIdAndIsActiveTrue(100L)).thenReturn(List.of());
			List<MenuService.MenuTreeNode> tree = menuService.getEffectiveMenuTree(Role.CREDIT_ANALYST, 100L);
			assertThat(tree).isEmpty();
		}

		@Test
		void prefixChangeSubmitsMakerCheckerAndReturnsPendingApproval() {
			Menu existing = menu(1L, null, "ACQ", "Acquisition", "/acq", "OLD_PREFIX", 1, true);
			when(menuRepository.findById(1L)).thenReturn(Optional.of(existing));
			MasterChangeRequest mockRequest = new MasterChangeRequest();
			mockRequest.setId(1L);
			mockRequest.setStatus(MasterChangeRequest.Status.pending_approval);
			mockRequest.setResource("menu.trans_type_id_prefix");
			mockRequest.setAction(MasterChangeRequest.Action.update);
			when(makerCheckerService.submit(eq("menu.trans_type_id_prefix"), eq(MasterChangeRequest.Action.update), anyString(), eq("SYSTEM"))).thenReturn(mockRequest);
			MenuService.PrefixChangeResult result = menuService.submitPrefixChange(1L, "NEW_PREFIX", "SYSTEM");
			assertThat(result.status()).isEqualTo("pending_approval");
			assertThat(result.affectedTransactionTypes()).isNotNull();
			assertThat(existing.getTransTypeIdPrefix()).isEqualTo("OLD_PREFIX");
			verify(menuRepository, never()).save(any(Menu.class));
		}

		@Test
		void prefixChangeOnNonExistentMenuThrowsNotFound() {
			when(menuRepository.findById(99L)).thenReturn(Optional.empty());
			assertThatThrownBy(() -> menuService.submitPrefixChange(99L, "NEW", "SYSTEM")).isInstanceOf(MenuService.MenuNotFoundException.class);
		}

		@Test
		void prefixChangeDoesNotModifyMenuRouteOrOtherFields() {
			Menu existing = menu(1L, null, "ACQ", "Acquisition", "/acq", "OLD_PREFIX", 1, true);
			String originalRoute = existing.getRoute();
			String originalName = existing.getName();
			when(menuRepository.findById(1L)).thenReturn(Optional.of(existing));
			MasterChangeRequest mockRequest = new MasterChangeRequest();
			mockRequest.setId(1L);
			mockRequest.setStatus(MasterChangeRequest.Status.pending_approval);
			when(makerCheckerService.submit(anyString(), any(), anyString(), anyString())).thenReturn(mockRequest);
			menuService.submitPrefixChange(1L, "NEW_PREFIX", "SYSTEM");
			assertThat(existing.getRoute()).isEqualTo(originalRoute);
			assertThat(existing.getName()).isEqualTo(originalName);
			assertThat(existing.getTransTypeIdPrefix()).isEqualTo("OLD_PREFIX");
		}

		@Test
		void e9DeactivateSetsIsActiveFalse() {
			Menu active = menu(1L, null, "ACQ", "Acquisition", "/acq", "PREFIX", 1, true);
			when(menuRepository.findById(1L)).thenReturn(Optional.of(active));
			when(menuRepository.save(any(Menu.class))).thenAnswer(inv -> inv.getArgument(0));
			Menu result = menuService.deactivate(1L, "SYSTEM");
			assertThat(result.isActive()).isFalse();
		}

		@Test
		void e9DeactivateAlreadyInactiveThrowsConflict() {
			Menu inactive = menu(1L, null, "ACQ", "Acquisition", "/acq", "PREFIX", 1, false);
			when(menuRepository.findById(1L)).thenReturn(Optional.of(inactive));
			assertThatThrownBy(() -> menuService.deactivate(1L, "SYSTEM")).isInstanceOf(MenuService.MenuConflictException.class);
		}

		@Test
		void e10GetRoleMenuGrantsReturnsGrants() {
			MenuRoleGrant g1 = grant(1L, Role.CREDIT_ANALYST, 10L, false, true);
			MenuRoleGrant g2 = grant(2L, Role.CREDIT_ANALYST, 20L, true, true);
			when(roleGrantRepository.findByRole(Role.CREDIT_ANALYST)).thenReturn(List.of(g1, g2));
			assertThat(menuService.getRoleGrants(Role.CREDIT_ANALYST)).hasSize(2);
		}

		@Test
		void e10PutRoleMenuGrantsReplacesAll() {
			MenuRoleGrant existing = grant(1L, Role.CREDIT_ANALYST, 10L, false, true);
			when(roleGrantRepository.findByRole(Role.CREDIT_ANALYST)).thenReturn(List.of(existing));
			when(roleGrantRepository.save(any(MenuRoleGrant.class))).thenAnswer(inv -> inv.getArgument(0));
			menuService.replaceRoleGrants(Role.CREDIT_ANALYST, List.of(new MenuService.RoleGrantRequest(10L, true, true)), "SYSTEM");
			assertThat(existing.isActive()).isFalse();
			verify(roleGrantRepository, org.mockito.Mockito.atLeast(1)).save(any(MenuRoleGrant.class));
		}

		@Test
		void e11GetUserSpecialGrantsReturnsGrants() {
			MenuUserGrantSpecial ug = userGrant(1L, 100L, 10L, true, "Project X access");
			when(userGrantRepository.findByUserId(100L)).thenReturn(List.of(ug));
			List<MenuUserGrantSpecial> result = menuService.getUserSpecialGrants(100L);
			assertThat(result).hasSize(1);
			assertThat(result.get(0).getGrantedReason()).isEqualTo("Project X access");
		}

		@Test
		void e11PutUserSpecialGrantRequiresGrantedReason() {
			assertThatThrownBy(() -> menuService.replaceUserSpecialGrants(100L, List.of(new MenuService.SpecialGrantRequest(10L, true, null)), "SYSTEM"))
					.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("granted_reason");
		}

		@Test
		void e11PutUserSpecialGrantWithBlankReasonRejected() {
			assertThatThrownBy(() -> menuService.replaceUserSpecialGrants(100L, List.of(new MenuService.SpecialGrantRequest(10L, true, "   ")), "SYSTEM"))
					.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("granted_reason");
		}

		@Test
		void e11PutUserSpecialGrantWithValidReasonSucceeds() {
			MenuUserGrantSpecial existing = userGrant(1L, 100L, 20L, true, "old reason");
			when(userGrantRepository.findByUserId(100L)).thenReturn(List.of(existing));
			when(userGrantRepository.save(any(MenuUserGrantSpecial.class))).thenAnswer(inv -> inv.getArgument(0));
			menuService.replaceUserSpecialGrants(100L, List.of(new MenuService.SpecialGrantRequest(10L, true, "Valid reason")), "SYSTEM");
			assertThat(existing.isActive()).isFalse();
			verify(userGrantRepository, org.mockito.Mockito.atLeast(1)).save(any(MenuUserGrantSpecial.class));
		}
	}

	@SpringBootTest(classes = MenuController.class)
	@Import(ControllerTest.ControllerTestConfig.class)
	@Nested
	class ControllerTest {
		@Autowired private WebApplicationContext context;
		@Autowired private MenuService menuService;
		private MockMvc mockMvc;
		@BeforeEach void setUp() { mockMvc = MockMvcBuilders.webAppContextSetup(context).build(); }

		@Test
		void e6GetEffectiveMenuReturns200() throws Exception {
			MenuService.MenuTreeNode childNode = new MenuService.MenuTreeNode(2L, "ACQ", "Application", "/acq/app", 2, true, List.of());
			MenuService.MenuTreeNode parentNode = new MenuService.MenuTreeNode(1L, "ACQ", "Acquisition", "/acq", 1, false, List.of(childNode));
			when(menuService.getEffectiveMenuTree(eq(Role.CREDIT_ANALYST), eq(100L))).thenReturn(List.of(parentNode));
			mockMvc.perform(get("/menus/users/{id}/menus", 100L).param("role", "CREDIT_ANALYST"))
					.andExpect(status().isOk()).andExpect(jsonPath("$").isArray())
					.andExpect(jsonPath("$[0].name").value("Acquisition"))
					.andExpect(jsonPath("$[0].route").value("/acq"))
					.andExpect(jsonPath("$[0].is_view_only").value(false))
					.andExpect(jsonPath("$[0].children[0].name").value("Application"))
					.andExpect(jsonPath("$[0].children[0].is_view_only").value(true));
		}

		@Test
		void e6ResponseDoesNotContainTransTypeIdPrefix() throws Exception {
			MenuService.MenuTreeNode node = new MenuService.MenuTreeNode(1L, "ACQ", "Acquisition", "/acq", 1, false, List.of());
			when(menuService.getEffectiveMenuTree(any(Role.class), any())).thenReturn(List.of(node));
			String response = mockMvc.perform(get("/menus/users/{id}/menus", 100L).param("role", "CMO"))
					.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
			assertThat(response).doesNotContain("trans_type_id_prefix");
			assertThat(response).doesNotContain("transTypeIdPrefix");
		}

		@Test
		void ac5PrefixChangeReturns202PendingApproval() throws Exception {
			MenuService.PrefixChangeResult result = new MenuService.PrefixChangeResult(1L, "pending_approval", List.of("LINKED_1", "LINKED_2"));
			when(menuService.submitPrefixChange(eq(1L), eq("NEW_PREFIX"), anyString())).thenReturn(result);
			mockMvc.perform(post("/menus/{id}/trans-type-id-prefix", 1L).contentType(MediaType.APPLICATION_JSON)
					.content("{\"trans_type_id_prefix\":\"NEW_PREFIX\",\"maker_nik\":\"SYSTEM\"}"))
					.andExpect(status().isAccepted())
					.andExpect(jsonPath("$.status").value("pending_approval"))
					.andExpect(jsonPath("$.affected_transaction_types").isArray())
					.andExpect(jsonPath("$.affected_transaction_types[0]").exists());
		}

		@Test
		void ac5PrefixChangeNotFoundReturns404() throws Exception {
			when(menuService.submitPrefixChange(eq(99L), anyString(), anyString())).thenThrow(new MenuService.MenuNotFoundException(99L));
			mockMvc.perform(post("/menus/{id}/trans-type-id-prefix", 99L).contentType(MediaType.APPLICATION_JSON)
					.content("{\"trans_type_id_prefix\":\"NEW\",\"maker_nik\":\"SYSTEM\"}")).andExpect(status().isNotFound());
		}

		@Test
		void e9GetMenuListReturns200() throws Exception {
			when(menuService.getAllMenus()).thenReturn(List.of(menu(1L, null, "ACQ", "Acquisition", "/acq", "PREFIX", 1, true)));
			mockMvc.perform(get("/menus")).andExpect(status().isOk()).andExpect(jsonPath("$").isArray()).andExpect(jsonPath("$[0].name").value("Acquisition"));
		}

		@Test
		void e9DeactivateReturns200() throws Exception {
			when(menuService.deactivate(eq(1L), anyString())).thenReturn(menu(1L, null, "ACQ", "Acquisition", "/acq", "PREFIX", 1, false));
			mockMvc.perform(post("/menus/{id}/deactivate", 1L).param("maker_nik", "SYSTEM")).andExpect(status().isOk()).andExpect(jsonPath("$.is_active").value(false));
		}

		@Test
		void e9NoDeleteEndpoint() throws Exception {
			int status = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/menus/{id}", 1L)).andReturn().getResponse().getStatus();
			assertThat(status == 404 || status == 405).isTrue();
		}

		@Test
		void e10GetRoleMenuGrantsReturns200() throws Exception {
			when(menuService.getRoleGrants(Role.CREDIT_ANALYST)).thenReturn(List.of(grant(1L, Role.CREDIT_ANALYST, 10L, false, true)));
			mockMvc.perform(get("/menus/roles/{role}/menu-grants", "CREDIT_ANALYST")).andExpect(status().isOk()).andExpect(jsonPath("$").isArray()).andExpect(jsonPath("$[0].menu_id").value(10));
		}

		@Test
		void e10PutRoleMenuGrantsReturns200() throws Exception {
			when(menuService.replaceRoleGrants(eq(Role.CREDIT_ANALYST), any(), anyString())).thenReturn(List.of(grant(1L, Role.CREDIT_ANALYST, 10L, false, true)));
			mockMvc.perform(put("/menus/roles/{role}/menu-grants", "CREDIT_ANALYST").contentType(MediaType.APPLICATION_JSON).param("maker_nik", "SYSTEM")
					.content("[{\"menu_id\":10,\"is_view_only\":false,\"is_active\":true}]")).andExpect(status().isOk()).andExpect(jsonPath("$[0].menu_id").value(10));
		}

		@Test
		void e11GetUserSpecialGrantsReturns200() throws Exception {
			when(menuService.getUserSpecialGrants(100L)).thenReturn(List.of(userGrant(1L, 100L, 10L, true, "Project access")));
			mockMvc.perform(get("/menus/users/{id}/menu-grants-special", 100L)).andExpect(status().isOk()).andExpect(jsonPath("$").isArray()).andExpect(jsonPath("$[0].granted_reason").value("Project access"));
		}

		@Test
		void e11PutUserSpecialGrantsReturns200() throws Exception {
			when(menuService.replaceUserSpecialGrants(eq(100L), any(), anyString())).thenReturn(List.of(userGrant(1L, 100L, 10L, true, "Valid reason")));
			mockMvc.perform(put("/menus/users/{id}/menu-grants-special", 100L).contentType(MediaType.APPLICATION_JSON).param("maker_nik", "SYSTEM")
					.content("[{\"menu_id\":10,\"is_view_only\":true,\"granted_reason\":\"Valid reason\"}]")).andExpect(status().isOk()).andExpect(jsonPath("$[0].granted_reason").value("Valid reason"));
		}

		@Test
		void e11PutUserSpecialGrantsWithoutReasonReturns400() throws Exception {
			when(menuService.replaceUserSpecialGrants(eq(100L), any(), anyString())).thenThrow(new IllegalArgumentException("granted_reason is required"));
			mockMvc.perform(put("/menus/users/{id}/menu-grants-special", 100L).contentType(MediaType.APPLICATION_JSON).param("maker_nik", "SYSTEM")
					.content("[{\"menu_id\":10,\"is_view_only\":true}]")).andExpect(status().isBadRequest());
		}

		@TestConfiguration
		@org.springframework.boot.autoconfigure.ImportAutoConfiguration(
				value = { org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration.class,
						org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration.class,
						org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration.class },
				exclude = { DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class })
		static class ControllerTestConfig { @Bean MenuService menuService() { return org.mockito.Mockito.mock(MenuService.class); } }
	}

	private static Menu menu(Long id, Long parentId, String module, String name, String route, String prefix, int displayOrder, boolean isActive) {
		Menu m = new Menu(); m.setId(id); m.setParentId(parentId); m.setModule(module); m.setName(name);
		m.setRoute(route); m.setTransTypeIdPrefix(prefix); m.setDisplayOrder(displayOrder); m.setActive(isActive);
		m.setCreatedAt(Instant.now()); m.setCreatedBy("SYSTEM"); return m;
	}
	private static MenuRoleGrant grant(Long id, Role role, Long menuId, boolean isViewOnly, boolean isActive) {
		MenuRoleGrant g = new MenuRoleGrant(); g.setId(id); g.setRole(role); g.setMenuId(menuId);
		g.setViewOnly(isViewOnly); g.setActive(isActive); g.setCreatedAt(Instant.now()); g.setCreatedBy("SYSTEM"); return g;
	}
	private static MenuUserGrantSpecial userGrant(Long id, Long userId, Long menuId, boolean isViewOnly, String reason) {
		MenuUserGrantSpecial ug = new MenuUserGrantSpecial(); ug.setId(id); ug.setUserId(userId); ug.setMenuId(menuId);
		ug.setViewOnly(isViewOnly); ug.setActive(true); ug.setGrantedReason(reason); ug.setCreatedAt(Instant.now()); ug.setCreatedBy("SYSTEM"); return ug;
	}
	private static Field getDeclaredField(Class<?> type, String fieldName) {
		try { return type.getDeclaredField(fieldName); } catch (NoSuchFieldException e) { throw new AssertionError(type.getSimpleName() + " must declare '" + fieldName + "'", e); }
	}
	private static boolean fieldExists(Class<?> type, String fieldName) {
		try { type.getDeclaredField(fieldName); return true; } catch (NoSuchFieldException e) { return false; }
	}
}
// SDD-PROVENANCE: U-004 | vault: .mega-sdd/vaults/acquisition-master-data | TDD test for Menu — AC-4, AC-5
