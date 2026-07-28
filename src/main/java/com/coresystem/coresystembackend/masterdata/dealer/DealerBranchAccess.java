package com.coresystem.coresystembackend.masterdata.dealer;

import com.coresystem.coresystembackend.masterdata.common.VersionedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Dealer-branch access entity — the bridge that controls which dealers appear in a branch's
 * dealer picker (BE-07 §3.2, table {@code mst_dealer_branch_access}).
 *
 * <p>BR-BE07-07: a dealer only appears in a branch's picker if there is an active row in this
 * table for that (dealer, branch) pair. Dealers are branch-scoped partners, not global.
 *
 * <p>Maps the legacy {@code ms_dealer_branch_access} table (8 columns). Lifecycle is
 * deactivate-only via the {@link #isActive} boolean (BR-BE07-03) — no hard-delete path.
 *
 * <p>Extends {@link VersionedEntity} (audit columns + {@code @Version} optimistic lock).
 */
@Entity
@Table(name = "mst_dealer_branch_access")
public class DealerBranchAccess extends VersionedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** FK to {@code mst_dealer.dealer_code}. */
	@Column(name = "dealer_code", nullable = false)
	private String dealerCode;

	/** FK to the branch master (branch-scoped dealer visibility, BR-BE07-07). */
	@Column(name = "branch_id", nullable = false)
	private String branchId;

	/** Deactivate-only lifecycle toggle (BR-BE07-03). */
	@Column(name = "is_active", nullable = false)
	private boolean isActive;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getDealerCode() {
		return dealerCode;
	}

	public void setDealerCode(String dealerCode) {
		this.dealerCode = dealerCode;
	}

	public String getBranchId() {
		return branchId;
	}

	public void setBranchId(String branchId) {
		this.branchId = branchId;
	}

	public boolean isActive() {
		return isActive;
	}

	public void setActive(boolean isActive) {
		this.isActive = isActive;
	}

}
// SDD-PROVENANCE: U-005 | vault: .mega-sdd/vaults/acquisition-master-data | DealerBranchAccess @Entity mst_dealer_branch_access extends VersionedEntity; dealerCode+branchId bridge (BR-BE07-07 branch-scoped picker); isActive deactivate-only (BR-BE07-03)
