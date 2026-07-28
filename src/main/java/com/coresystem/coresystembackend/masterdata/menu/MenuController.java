package com.coresystem.coresystembackend.masterdata.menu;

import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coresystem.coresystembackend.masterdata.user.Role;

/**
 * REST controller for the menu tree + role grants + menu efektif (E6/E9-E11, BE-07 §5).
 */
@ConditionalOnBean(JpaRepository.class)
@RestController
@RequestMapping("/menus")
public class MenuController {

	private final MenuService menuService;

	public MenuController(MenuService menuService) {
		this.menuService = menuService;
	}

	@GetMapping
	public List<Menu> list() {
		return menuService.getAllMenus();
	}

	@GetMapping("/{id}")
	public ResponseEntity<?> detail(@PathVariable Long id) {
		java.util.Optional<Menu> menu = menuService.getMenu(id);
		if (menu.isEmpty()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(Map.of("error", "MENU_NOT_FOUND", "id", id));
		}
		return ResponseEntity.ok(menu.get());
	}

	@PostMapping
	public ResponseEntity<?> create(@RequestBody Menu menu,
			@RequestParam(name = "maker_nik", defaultValue = "SYSTEM") String makerNik) {
		Menu created = menuService.createMenu(menu, makerNik);
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@PostMapping("/{id}/deactivate")
	public ResponseEntity<?> deactivate(@PathVariable Long id,
			@RequestParam(name = "maker_nik", defaultValue = "SYSTEM") String makerNik) {
		Menu deactivated = menuService.deactivate(id, makerNik);
		return ResponseEntity.ok(deactivated);
	}

	@PostMapping("/{id}/trans-type-id-prefix")
	public ResponseEntity<?> submitPrefixChange(@PathVariable Long id,
			@RequestBody Map<String, String> body) {
		String newPrefix = body.get("trans_type_id_prefix");
		String makerNik = body.getOrDefault("maker_nik", "SYSTEM");
		MenuService.PrefixChangeResult result = menuService.submitPrefixChange(id, newPrefix, makerNik);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
	}

	@GetMapping("/users/{id}/menus")
	public List<MenuService.MenuTreeNode> getEffectiveMenu(@PathVariable("id") Long userId,
			@RequestParam("role") String role) {
		Role roleEnum = Role.fromName(role).orElseThrow(() ->
				new IllegalArgumentException("Role '" + role + "' is not a D-10 role"));
		return menuService.getEffectiveMenuTree(roleEnum, userId);
	}

	@GetMapping("/roles/{role}/menu-grants")
	public List<MenuRoleGrant> getRoleGrants(@PathVariable("role") String role) {
		Role roleEnum = Role.fromName(role).orElseThrow(() ->
				new IllegalArgumentException("Role '" + role + "' is not a D-10 role"));
		return menuService.getRoleGrants(roleEnum);
	}

	@PutMapping("/roles/{role}/menu-grants")
	public List<MenuRoleGrant> putRoleGrants(@PathVariable("role") String role,
			@RequestBody List<MenuService.RoleGrantRequest> grants,
			@RequestParam(name = "maker_nik", defaultValue = "SYSTEM") String makerNik) {
		Role roleEnum = Role.fromName(role).orElseThrow(() ->
				new IllegalArgumentException("Role '" + role + "' is not a D-10 role"));
		return menuService.replaceRoleGrants(roleEnum, grants, makerNik);
	}

	@GetMapping("/users/{id}/menu-grants-special")
	public List<MenuUserGrantSpecial> getUserSpecialGrants(@PathVariable("id") Long id) {
		return menuService.getUserSpecialGrants(id);
	}

	@PutMapping("/users/{id}/menu-grants-special")
	public List<MenuUserGrantSpecial> putUserSpecialGrants(@PathVariable("id") Long id,
			@RequestBody List<MenuService.SpecialGrantRequest> grants,
			@RequestParam(name = "maker_nik", defaultValue = "SYSTEM") String makerNik) {
		return menuService.replaceUserSpecialGrants(id, grants, makerNik);
	}

	@ExceptionHandler(MenuService.MenuNotFoundException.class)
	public ResponseEntity<?> handleNotFound(MenuService.MenuNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(Map.of("error", "MENU_NOT_FOUND", "message", e.getMessage()));
	}

	@ExceptionHandler(MenuService.MenuConflictException.class)
	public ResponseEntity<?> handleConflict(MenuService.MenuConflictException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(Map.of("error", "MENU_CONFLICT", "message", e.getMessage()));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<?> handleBadRequest(IllegalArgumentException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(Map.of("error", "BAD_REQUEST", "message", e.getMessage()));
	}
}
// SDD-PROVENANCE: U-004 | vault: .mega-sdd/vaults/acquisition-master-data | MenuController @RestController /menus @ConditionalOnBean(JpaRepository) — E9 CRUD, AC-5 prefix change 202, E6 menu efektif, E10 role grants, E11 special grants; exception handlers 404/409/400
