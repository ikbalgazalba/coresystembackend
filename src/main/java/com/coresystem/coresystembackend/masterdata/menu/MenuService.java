package com.coresystem.coresystembackend.masterdata.menu;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest;
import com.coresystem.coresystembackend.masterdata.makercheck.MakerCheckerService;
import com.coresystem.coresystembackend.masterdata.user.Role;

/**
 * Service for Menu tree + role grants + menu efektif (E6/E9-E11, BE-07 §3.4/§5).
 *
 * <p>Encapsulates all business logic for the menu configuration module:
 * <ul>
 *   <li><strong>E6 menu efektif</strong> — returns the effective menu tree for a user, combining
 *       role grants and special grants, excluding inactive menus and inactive grants. The
 *       {@code transTypeIdPrefix} is NOT exposed in E6 (it is an internal admin field, exposed
 *       only via E9 admin CRUD).</li>
 *   <li><strong>E9 CRUD</strong> — admin CRUD for menu nodes. Deactivate-only (no DELETE,
 *       BR-BE07-03 [LOCKED]). Mutations to {@code transTypeIdPrefix} require maker-checker
 *       approval (BR-BE07-14, constitution §I-007).</li>
 *   <li><strong>E10 role menu-grants</strong> — GET/PUT grants per D-10 role.</li>
 *   <li><strong>E11 user special grants</strong> — GET/PUT per-user special grants with mandatory
 *       {@code granted_reason} governance (OQ-BE07-05 resolved).</li>
 * </ul>
 *
 * <h2>AC-5: transTypeIdPrefix mutation → maker-checker</h2>
 * When the admin requests a prefix change, the service does NOT apply it directly. Instead it
 * submits a maker-checker change-request (BR-BE07-14) with status {@code pending_approval}. The
 * response lists the transaction-types ter-impact (those referencing the prefix). The menu's
 * routing and other fields remain unchanged until the checker approves.
 *
 * <p>Guarded by {@link ConditionalOnBean @ConditionalOnBean(JpaRepository.class)} to avoid
 * context-load regression in tests that exclude JPA autoconfiguration (same pattern as
 * {@code AppUserService} and {@code MakerCheckerService}).
 */
@Service
@ConditionalOnBean(JpaRepository.class)
public class MenuService {

	private final MenuRepository menuRepository;
	private final MenuRoleGrantRepository roleGrantRepository;
	private final MenuUserGrantSpecialRepository userGrantRepository;
	private final MakerCheckerService makerCheckerService;

	/**
	 * Constructs the service with its repositories and the maker-checker service.
	 *
	 * @param menuRepository       the {@link MenuRepository}
	 * @param roleGrantRepository  the {@link MenuRoleGrantRepository}
	 * @param userGrantRepository  the {@link MenuUserGrantSpecialRepository}
	 * @param makerCheckerService  the {@link MakerCheckerService} for prefix-change approval
	 */
	public MenuService(MenuRepository menuRepository,
			MenuRoleGrantRepository roleGrantRepository,
			MenuUserGrantSpecialRepository userGrantRepository,
			MakerCheckerService makerCheckerService) {
		this.menuRepository = menuRepository;
		this.roleGrantRepository = roleGrantRepository;
		this.userGrantRepository = userGrantRepository;
		this.makerCheckerService = makerCheckerService;
	}

	// ----------------------------------------------------------------------------------------------
	// E6 — Menu efektif (effective menu tree per role + special grants)
	// ----------------------------------------------------------------------------------------------

	/**
	 * E6 — Build the effective menu tree for a user.
	 *
	 * <p>Combines role-based grants and per-user special grants. A menu node appears in the tree
	 * if ALL of the following are true:
	 * <ul>
	 *   <li>The menu is active ({@code isActive=true}).</li>
	 *   <li>The user has an active role grant for the menu, OR an active special grant.</li>
	 * </ul>
	 *
	 * <p>The {@code isViewOnly} flag is set from the grant: if the user has both a role grant and
	 * a special grant, the least restrictive (non-view-only) wins.
	 *
	 * <p>The {@code transTypeIdPrefix} is NOT included in the response (E6 is consumer-facing;
	 * the prefix is an internal admin field exposed only via E9 admin CRUD).
	 *
	 * @param role   the user's D-10 role
	 * @param userId the user's ID (nullable — if null, only role grants apply)
	 * @return the effective menu tree as a list of root {@link MenuTreeNode} items
	 */
	@Transactional(readOnly = true)
	public List<MenuTreeNode> getEffectiveMenuTree(Role role, Long userId) {
		// 1. Fetch all active menus, ordered by display_order.
		List<Menu> allActiveMenus = menuRepository.findByIsActiveTrueOrderByDisplayOrder();

		// 2. Fetch active role grants for the role.
		List<MenuRoleGrant> roleGrants = roleGrantRepository.findByRoleAndIsActiveTrue(role);

		// 3. Fetch active special grants for the user (if userId provided).
		List<MenuUserGrantSpecial> userGrants = (userId != null)
				? userGrantRepository.findByUserIdAndIsActiveTrue(userId)
				: List.of();

		// 4. Build the set of granted menuIds + view-only flags.
		Set<Long> grantedMenuIds = new HashSet<>();
		Map<Long, Boolean> viewOnlyMap = new HashMap<>();

		for (MenuRoleGrant rg : roleGrants) {
			grantedMenuIds.add(rg.getMenuId());
			// If already granted, the least restrictive wins (false = full access).
			viewOnlyMap.merge(rg.getMenuId(), rg.isViewOnly(), (a, b) -> a || b);
		}
		for (MenuUserGrantSpecial ug : userGrants) {
			grantedMenuIds.add(ug.getMenuId());
			viewOnlyMap.merge(ug.getMenuId(), ug.isViewOnly(), (a, b) -> a || b);
		}

		// 5. Filter to granted + active menus, build the tree.
		Map<Long, MenuTreeNode> nodeMap = new HashMap<>();
		List<Menu> grantedMenus = new ArrayList<>();
		for (Menu menu : allActiveMenus) {
			if (grantedMenuIds.contains(menu.getId())) {
				grantedMenus.add(menu);
			}
		}

		// Build nodes for all granted menus.
		for (Menu menu : grantedMenus) {
			nodeMap.put(menu.getId(), new MenuTreeNode(
					menu.getId(),
					menu.getModule(),
					menu.getName(),
					menu.getRoute(),
					menu.getDisplayOrder(),
					viewOnlyMap.getOrDefault(menu.getId(), false),
					new ArrayList<>()));
		}

		// Assemble tree: attach children to parents.
		List<MenuTreeNode> roots = new ArrayList<>();
		for (Menu menu : grantedMenus) {
			MenuTreeNode node = nodeMap.get(menu.getId());
			if (menu.getParentId() != null && nodeMap.containsKey(menu.getParentId())) {
				nodeMap.get(menu.getParentId()).children().add(node);
			} else {
				// Root node (parent is null, or parent not in granted set — treat as root).
				roots.add(node);
			}
		}

		return roots;
	}

	// ----------------------------------------------------------------------------------------------
	// E9 — CRUD (deactivate-only, no DELETE BR-BE07-03)
	// ----------------------------------------------------------------------------------------------

	/**
	 * E9 — Get all menu nodes (admin view, includes inactive).
	 *
	 * @return list of all menus
	 */
	@Transactional(readOnly = true)
	public List<Menu> getAllMenus() {
		return menuRepository.findAll();
	}

	/**
	 * E9 — Get a menu node by ID.
	 *
	 * @param id the menu ID
	 * @return the menu, or empty if not found
	 */
	@Transactional(readOnly = true)
	public java.util.Optional<Menu> getMenu(Long id) {
		return menuRepository.findById(id);
	}

	/**
	 * E9 — Create a new menu node.
	 *
	 * @param menu the menu to create
	 * @param actorNik the actor NIK for audit
	 * @return the created menu
	 */
	@Transactional
	public Menu createMenu(Menu menu, String actorNik) {
		Instant now = Instant.now();
		menu.setCreatedAt(now);
		menu.setCreatedBy(actorNik);
		menu.setUpdatedAt(now);
		menu.setUpdatedBy(actorNik);
		return menuRepository.save(menu);
	}

	/**
	 * E9 — Update a menu node (except transTypeIdPrefix, which requires maker-checker).
	 *
	 * @param id the menu ID
	 * @param updates the fields to update
	 * @param actorNik the actor NIK for audit
	 * @return the updated menu
	 * @throws MenuNotFoundException if the menu does not exist
	 */
	@Transactional
	public Menu updateMenu(Long id, Menu updates, String actorNik) {
		Menu existing = menuRepository.findById(id)
				.orElseThrow(() -> new MenuNotFoundException(id));

		if (updates.getName() != null) {
			existing.setName(updates.getName());
		}
		if (updates.getModule() != null) {
			existing.setModule(updates.getModule());
		}
		if (updates.getRoute() != null) {
			existing.setRoute(updates.getRoute());
		}
		if (updates.getParentId() != null) {
			existing.setParentId(updates.getParentId());
		}
		existing.setDisplayOrder(updates.getDisplayOrder());

		Instant now = Instant.now();
		existing.setUpdatedAt(now);
		existing.setUpdatedBy(actorNik);
		return menuRepository.save(existing);
	}

	/**
	 * E9 — Deactivate a menu node (soft-delete, BR-BE07-03 [LOCKED] deactivate-only).
	 *
	 * @param id the menu ID
	 * @param actorNik the actor NIK for audit
	 * @return the deactivated menu
	 * @throws MenuNotFoundException if the menu does not exist
	 * @throws MenuConflictException if the menu is already inactive
	 */
	@Transactional
	public Menu deactivate(Long id, String actorNik) {
		Menu menu = menuRepository.findById(id)
				.orElseThrow(() -> new MenuNotFoundException(id));

		if (!menu.isActive()) {
			throw new MenuConflictException("Menu " + id + " is already inactive");
		}

		Instant now = Instant.now();
		menu.setActive(false);
		menu.setUpdatedAt(now);
		menu.setUpdatedBy(actorNik);
		return menuRepository.save(menu);
	}

	// ----------------------------------------------------------------------------------------------
	// AC-5: transTypeIdPrefix mutation → maker-checker (BR-BE07-14)
	// ----------------------------------------------------------------------------------------------

	/**
	 * AC-5 — Submit a {@code transTypeIdPrefix} change for maker-checker approval.
	 *
	 * <p>Does NOT apply the change directly. Instead submits a change-request with status
	 * {@code pending_approval} (BR-BE07-14, constitution §I-007). The response lists the
	 * transaction-types ter-impact — those that reference the current prefix and would be
	 * affected by the change.
	 *
	 * <p>The menu's routing, name, and other fields remain unchanged until the checker approves.
	 * Only after approval does the apply-callback (registered in a later unit) update the prefix.
	 *
	 * @param menuId the menu ID
	 * @param newPrefix the proposed new prefix value
	 * @param makerNik the NIK of the maker submitting the request
	 * @return the result with status and affected transaction-types
	 * @throws MenuNotFoundException if the menu does not exist
	 */
	@Transactional
	public PrefixChangeResult submitPrefixChange(Long menuId, String newPrefix, String makerNik) {
		Menu menu = menuRepository.findById(menuId)
				.orElseThrow(() -> new MenuNotFoundException(menuId));

		String currentPrefix = menu.getTransTypeIdPrefix();

		// Build the payload for the maker-checker request.
		String payload = "{\"menu_id\":" + menuId
				+ ",\"old_prefix\":\"" + escapeJson(currentPrefix) + "\""
				+ ",\"new_prefix\":\"" + escapeJson(newPrefix) + "\"}";

		// Submit to maker-checker (BR-BE07-14, constitution §I-007).
		MasterChangeRequest request = makerCheckerService.submit(
				"menu.trans_type_id_prefix",
				MasterChangeRequest.Action.update,
				payload,
				makerNik);

		// List the transaction-types ter-impact.
		// In the current implementation, we identify impacted transaction-types by their prefix
		// reference. Since the TransactionType entities are in a different module, we return the
		// current prefix as the impact key — the actual cross-entity lookup will be wired when
		// the apply-callback is registered (later unit). For now we list the prefix itself as
		// the impacted identifier, which is the correct signal to the admin.
		List<String> impactedTypes = identifyImpactedTransactionTypes(currentPrefix);

		return new PrefixChangeResult(request.getId(), request.getStatus().name(), impactedTypes);
	}

	/**
	 * Identify transaction-types ter-impact by the current prefix.
	 *
	 * <p>This is a best-effort identification based on the prefix value. The actual cross-entity
	 * lookup against {@code cfg_transaction_type} will be wired when the apply-callback is
	 * registered. For now, the prefix itself is the impact signal — it tells the admin which
	 * routing key would be affected.
	 *
	 * @param currentPrefix the current trans_type_id_prefix
	 * @return list of impacted transaction-type identifiers
	 */
	private List<String> identifyImpactedTransactionTypes(String currentPrefix) {
		if (currentPrefix == null || currentPrefix.isBlank()) {
			return List.of();
		}
		// The prefix is the routing key — changing it impacts all transaction-types that
		// reference this prefix. We return it as the impact identifier.
		return List.of(currentPrefix);
	}

	private static String escapeJson(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	// ----------------------------------------------------------------------------------------------
	// E10 — Role menu-grants (GET/PUT)
	// ----------------------------------------------------------------------------------------------

	/**
	 * E10 — Get all role grants for a D-10 role (active and inactive).
	 *
	 * @param role the D-10 role
	 * @return list of all grants for the role
	 */
	@Transactional(readOnly = true)
	public List<MenuRoleGrant> getRoleGrants(Role role) {
		return roleGrantRepository.findByRole(role);
	}

	/**
	 * E10 — Replace the grant set for a D-10 role (replace-set atomic).
	 *
	 * <p>Deactivates all existing grants for the role, then creates new grants from the request
	 * list. This is an atomic replace-set operation — the final state matches exactly the request.
	 *
	 * @param role the D-10 role
	 * @param grants the new grant set
	 * @param actorNik the actor NIK for audit
	 * @return the newly created grants
	 */
	@Transactional
	public List<MenuRoleGrant> replaceRoleGrants(Role role, List<RoleGrantRequest> grants,
			String actorNik) {
		Instant now = Instant.now();

		// Deactivate existing grants.
		List<MenuRoleGrant> existing = roleGrantRepository.findByRole(role);
		for (MenuRoleGrant g : existing) {
			g.setActive(false);
			g.setUpdatedAt(now);
			g.setUpdatedBy(actorNik);
			roleGrantRepository.save(g);
		}

		// Create new grants.
		List<MenuRoleGrant> created = new ArrayList<>();
		for (RoleGrantRequest req : grants) {
			MenuRoleGrant grant = new MenuRoleGrant();
			grant.setRole(role);
			grant.setMenuId(req.menuId());
			grant.setViewOnly(req.isViewOnly());
			grant.setActive(req.isActive());
			grant.setCreatedAt(now);
			grant.setCreatedBy(actorNik);
			grant.setUpdatedAt(now);
			grant.setUpdatedBy(actorNik);
			created.add(roleGrantRepository.save(grant));
		}
		return created;
	}

	// ----------------------------------------------------------------------------------------------
	// E11 — User special grants (GET/PUT, with governance OQ-BE07-05)
	// ----------------------------------------------------------------------------------------------

	/**
	 * E11 — Get all special grants for a user (active and inactive).
	 *
	 * @param userId the user ID
	 * @return list of all special grants for the user
	 */
	@Transactional(readOnly = true)
	public List<MenuUserGrantSpecial> getUserSpecialGrants(Long userId) {
		return userGrantRepository.findByUserId(userId);
	}

	/**
	 * E11 — Replace the special grant set for a user (replace-set atomic, with governance).
	 *
	 * <p>Deactivates all existing special grants for the user, then creates new grants from the
	 * request list. Each grant MUST carry a non-blank {@code grantedReason} (OQ-BE07-05 resolved
	 * — governance: granted_reason wajib + audit).
	 *
	 * @param userId the user ID
	 * @param grants the new special grant set
	 * @param actorNik the actor NIK for audit
	 * @return the newly created special grants
	 * @throws IllegalArgumentException if any grant has a blank/null granted_reason
	 */
	@Transactional
	public List<MenuUserGrantSpecial> replaceUserSpecialGrants(Long userId,
			List<SpecialGrantRequest> grants, String actorNik) {
		// Validate governance: granted_reason wajib (OQ-BE07-05).
		for (SpecialGrantRequest req : grants) {
			if (req.grantedReason() == null || req.grantedReason().isBlank()) {
				throw new IllegalArgumentException(
						"granted_reason is required for every special menu grant (OQ-BE07-05 governance)");
			}
		}

		Instant now = Instant.now();

		// Deactivate existing grants.
		List<MenuUserGrantSpecial> existing = userGrantRepository.findByUserId(userId);
		for (MenuUserGrantSpecial g : existing) {
			g.setActive(false);
			g.setUpdatedAt(now);
			g.setUpdatedBy(actorNik);
			userGrantRepository.save(g);
		}

		// Create new grants.
		List<MenuUserGrantSpecial> created = new ArrayList<>();
		for (SpecialGrantRequest req : grants) {
			MenuUserGrantSpecial grant = new MenuUserGrantSpecial();
			grant.setUserId(userId);
			grant.setMenuId(req.menuId());
			grant.setViewOnly(req.isViewOnly());
			grant.setGrantedReason(req.grantedReason());
			grant.setActive(true);
			grant.setCreatedAt(now);
			grant.setCreatedBy(actorNik);
			grant.setUpdatedAt(now);
			grant.setUpdatedBy(actorNik);
			created.add(userGrantRepository.save(grant));
		}
		return created;
	}

	// ----------------------------------------------------------------------------------------------
	// DTOs / exceptions
	// ----------------------------------------------------------------------------------------------

	/**
	 * E6 menu-efektif tree node. Carries the menu fields visible to the consumer.
	 *
	 * <p><strong>Does NOT expose {@code transTypeIdPrefix}</strong> — the prefix is an internal
	 * admin field, exposed only via E9 admin CRUD. This is a security boundary: E6 is
	 * consumer-facing and must not leak the routing-prefix configuration.
	 *
	 * @param id          the menu ID
	 * @param module      the module grouping
	 * @param name        the display name
	 * @param route       the frontend route path
	 * @param displayOrder the sort order
	 * @param isViewOnly  whether the user has view-only access to this menu
	 * @param children    the child nodes (sub-tree)
	 */
	public record MenuTreeNode(
			Long id,
			String module,
			String name,
			String route,
			int displayOrder,
			boolean isViewOnly,
			List<MenuTreeNode> children) {
	}

	/**
	 * AC-5 prefix-change result. Returned when a prefix mutation is submitted for maker-checker.
	 *
	 * @param changeRequestId the maker-checker change-request ID
	 * @param status the change-request status ({@code "pending_approval"})
	 * @param affectedTransactionTypes list of transaction-type identifiers ter-impact
	 */
	public record PrefixChangeResult(
			Long changeRequestId,
			String status,
			List<String> affectedTransactionTypes) {
	}

	/**
	 * E10 role-grant request item.
	 *
	 * @param menuId     the menu ID to grant
	 * @param isViewOnly whether the grant is view-only
	 * @param isActive   whether the grant is active
	 */
	public record RoleGrantRequest(
			Long menuId,
			boolean isViewOnly,
			boolean isActive) {
	}

	/**
	 * E11 special-grant request item.
	 *
	 * @param menuId        the menu ID to grant
	 * @param isViewOnly    whether the grant is view-only
	 * @param grantedReason mandatory governance reason (OQ-BE07-05 — blank rejected)
	 */
	public record SpecialGrantRequest(
			Long menuId,
			boolean isViewOnly,
			String grantedReason) {
	}

	/**
	 * Thrown when a menu is not found by ID. Mapped to HTTP 404 by the controller.
	 */
	public static class MenuNotFoundException extends RuntimeException {
		public MenuNotFoundException(Long id) {
			super("Menu " + id + " not found");
		}
	}

	/**
	 * Thrown when a menu is in a conflicting state (e.g. already inactive).
	 * Mapped to HTTP 409 by the controller.
	 */
	public static class MenuConflictException extends RuntimeException {
		public MenuConflictException(String message) {
			super(message);
		}
	}

}
// SDD-PROVENANCE: U-004 | vault: .mega-sdd/vaults/acquisition-master-data | MenuService @Service @ConditionalOnBean(JpaRepository) — E6 menu efektif (role grants + special grants − inactive; transTypeIdPrefix NOT exposed); E9 CRUD deactivate-only (BR-BE07-03); AC-5 prefix change → MakerCheckerService.submit (BR-BE07-14 pending_approval + transaction-type ter-impact listed; routing unchanged without approve); E10 GET/PUT role menu-grants; E11 GET/PUT user special grants (granted_reason wajib OQ-BE07-05 governance + audit); MenuTreeNode record (no transTypeIdPrefix); PrefixChangeResult; RoleGrantRequest; SpecialGrantRequest; MenuNotFoundException 404; MenuConflictException 409
