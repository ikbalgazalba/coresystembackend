package com.coresystem.coresystembackend.masterdata.menu;

import com.coresystem.coresystembackend.masterdata.common.VersionedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * User-specific special menu grant entity (BE-07 §3.4, table
 * {@code cfg_menu_user_grant_special}).
 *
 * <p>Maps per-user menu grants that supplement the role-based grants. This entity is the
 * resolution of OQ-BE07-05: role-based + special grant retained WITH governance.
 *
 * <h2>Governance (OQ-BE07-05 resolved)</h2>
 * <ul>
 *   <li><strong>{@code grantedReason} is mandatory</strong> — every special grant must carry a
 *       non-blank reason explaining why the user needs access beyond their role. The service
 *       layer rejects requests with blank/null reason ({@code IllegalArgumentException} → 400).</li>
 *   <li><strong>Audit append-only</strong> — the table is append-only in spirit; old grants are
 *       deactivated ({@code isActive=false}) rather than deleted (BR-BE07-03). The
 *       {@code granted_reason} survives deactivation for audit traceability.</li>
 *   <li><strong>NOT super-user</strong> (D-09) — special grants give access to specific menu
 *       nodes, not a blanket bypass. They cannot set the user equal to a super-user.</li>
 * </ul>
 *
 * <p>The special grant is included in the E6 menu-efektif tree: a menu node appears if the user
 * has EITHER an active role grant OR an active special grant (union, not intersection).
 *
 * <p>Extends {@link VersionedEntity} — audit columns + {@code @Version} optimistic lock
 * (DB-CONVENTIONS §4).
 */
@Entity
@Table(name = "cfg_menu_user_grant_special", uniqueConstraints = @UniqueConstraint(
		name = "ux_cfg_menu_user_grant_special_user_menu",
		columnNames = { "user_id", "menu_id" }))
public class MenuUserGrantSpecial extends VersionedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** FK to {@code mst_user.id} — the user this special grant applies to. */
	@Column(name = "user_id", nullable = false)
	private Long userId;

	/** FK to {@code cfg_menu.id} — the menu node this grant applies to. */
	@Column(name = "menu_id", nullable = false)
	private Long menuId;

	/** Whether the grant is view-only (read access, no write). */
	@Column(name = "is_view_only", nullable = false)
	private boolean isViewOnly;

	/**
	 * Mandatory governance reason (OQ-BE07-05 resolved). Every special grant must explain why the
	 * user needs access beyond their role. Blank/null is rejected by the service layer.
	 */
	@Column(name = "granted_reason", nullable = false, length = 500)
	private String grantedReason;

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

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
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

	public String getGrantedReason() {
		return grantedReason;
	}

	public void setGrantedReason(String grantedReason) {
		this.grantedReason = grantedReason;
	}

	public boolean isActive() {
		return isActive;
	}

	public void setActive(boolean isActive) {
		this.isActive = isActive;
	}

}
// SDD-PROVENANCE: U-004 | vault: .mega-sdd/vaults/acquisition-master-data | MenuUserGrantSpecial @Entity cfg_menu_user_grant_special extends VersionedEntity; userId (FK mst_user), menuId (FK cfg_menu), isViewOnly, grantedReason (mandatory OQ-BE07-05 governance), isActive; unique on (user_id, menu_id); NOT super-user D-09; deactivate-only BR-BE07-03
