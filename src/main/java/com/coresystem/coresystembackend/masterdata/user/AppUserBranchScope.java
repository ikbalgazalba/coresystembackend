package com.coresystem.coresystembackend.masterdata.user;

import com.coresystem.coresystembackend.masterdata.common.VersionedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * User-branch scope bridge entity (BE-07 §3.1, table {@code mst_user_branch_scope}).
 *
 * <p>Controls which branches a user has visibility into. A user may have one or more branch
 * scopes (OQ-BE07-04 resolved — the list supports both single-branch and multi-branch
 * configurations; the default is single-branch, set during E2 provisioning).
 *
 * <p>The composite unique key is {@code (user_id, branch_id)} — enforced at the DB level.
 * A user cannot have duplicate entries for the same branch.
 *
 * <p>Extends {@link VersionedEntity} (audit columns + {@code @Version} optimistic lock) per
 * DB-CONVENTIONS §4 — this table participates in concurrent edits when admins adjust branch
 * scopes via E4 PATCH.
 *
 * <p>Lifecycle follows the parent {@link AppUser} — when a user is deactivated, their branch
 * scopes remain in the table (for audit/history) but are effectively inert because the user
 * is inactive. The scopes are managed through E4 PATCH, not independently.
 */
@Entity
@Table(name = "mst_user_branch_scope")
public class AppUserBranchScope extends VersionedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** FK to {@code mst_user.id}. The user this scope belongs to. */
	@Column(name = "user_id", nullable = false)
	private Long userId;

	/** FK to the branch master. The branch this user has scope over. */
	@Column(name = "branch_id", nullable = false)
	private String branchId;

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

	public String getBranchId() {
		return branchId;
	}

	public void setBranchId(String branchId) {
		this.branchId = branchId;
	}

}
// SDD-PROVENANCE: U-003 | vault: .mega-sdd/vaults/acquisition-master-data | AppUserBranchScope @Entity mst_user_branch_scope extends VersionedEntity; userId FK→mst_user.id + branchId; multi-branch (OQ-BE07-04 resolved); composite unique (user_id, branch_id)
