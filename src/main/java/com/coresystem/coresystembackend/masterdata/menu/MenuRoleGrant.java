package com.coresystem.coresystembackend.masterdata.menu;

import com.coresystem.coresystembackend.masterdata.common.VersionedEntity;
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

/**
 * Role-based menu grant entity (BE-07 §3.4, table {@code cfg_menu_role_grant}).
 *
 * <p>Maps the grant of a specific menu node to a D-10 role. This is the primary access-control
 * mechanism for the menu tree (OQ-BE07-05 resolved — role-based + special grant retained).
 *
 * <p>Each row grants menu {@code menuId} to role {@code role} with an optional
 * {@code isViewOnly} flag. The unique constraint on {@code (role, menu_id)} ensures one grant
 * per role-menu pair — no duplicate grants.
 *
 * <h2>Key design rules</h2>
 * <ul>
 *   <li><strong>Re-keyed from legacy</strong> — legacy used position-based grants; the rebuild
 *       re-keys to D-10 role (constitution §I-004). This is NOT a 1:1 copy of the legacy
 *       {@code ms_module_menu} position-based grants.</li>
 *   <li><strong>D-10 role</strong> — persisted as {@code VARCHAR} via {@code @Enumerated(STRING)}.
 *       No {@code SUPER_USER} (D-09).</li>
 *   <li><strong>Deactivate-only</strong> — no DELETE (BR-BE07-03). Grants are toggled via
 *       {@code isActive}; E10 PUT replaces the grant set by deactivating old grants and creating
 *       new ones.</li>
 * </ul>
 *
 * <p>Extends {@link VersionedEntity} — audit columns + {@code @Version} optimistic lock
 * (DB-CONVENTIONS §4).
 */
@Entity
@Table(name = "cfg_menu_role_grant", uniqueConstraints = @UniqueConstraint(
		name = "ux_cfg_menu_role_grant_role_menu",
		columnNames = { "role", "menu_id" }))
public class MenuRoleGrant extends VersionedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** D-10 role enum (constitution §I-004). Persisted as VARCHAR via @Enumerated(STRING). */
	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false)
	private Role role;

	/** FK to {@code cfg_menu.id} — the menu node this grant applies to. */
	@Column(name = "menu_id", nullable = false)
	private Long menuId;

	/** Whether the grant is view-only (read access, no write). */
	@Column(name = "is_view_only", nullable = false)
	private boolean isViewOnly;

	/** Whether the grant is active. Lifecycle toggle (BR-BE07-03 deactivate-only). */
	@Column(name = "is_active", nullable = false)
	private boolean isActive;

	// --- getters/setters ---

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Role getRole() {
		return role;
	}

	public void setRole(Role role) {
		this.role = role;
	}

	public Long getMenuId() {
		return menuId;
	}

	public void setMenuId(Long menuId) {
		this.menuId = menuId;
	}

	public boolean isViewOnly() {
		return isViewOnly;
	}

	public void setViewOnly(boolean isViewOnly) {
		this.isViewOnly = isViewOnly;
	}

	public boolean isActive() {
		return isActive;
	}

	public void setActive(boolean isActive) {
		this.isActive = isActive;
	}

}
// SDD-PROVENANCE: U-004 | vault: .mega-sdd/vaults/acquisition-master-data | MenuRoleGrant @Entity cfg_menu_role_grant extends VersionedEntity; role (Role enum D-10 @Enumerated STRING), menuId (FK), isViewOnly, isActive; Unique on (role, menuId); re-keyed position→role (OQ-BE07-05 resolved); No DELETE (BR-BE07-03 deactivate-only)
