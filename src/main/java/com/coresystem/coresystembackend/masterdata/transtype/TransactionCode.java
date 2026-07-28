package com.coresystem.coresystembackend.masterdata.transtype;

import com.coresystem.coresystembackend.masterdata.common.VersionedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Transaction-Code config entity — the parent level of the Transaction-Type hierarchy
 * (BE-07 §3.3, table {@code cfg_transaction_code}).
 *
 * <p>Maps the legacy {@code ms_trans_type} + {@code ms_module_menu} tables. Each row defines a
 * transaction code scoped to a branch, with the associated form-requester and form-approval
 * references. Transaction codes are upsert-able (BR-BE07-19 — satu aksi Save) and normalized to
 * upper-case server-side.
 *
 * <p>Extends {@link VersionedEntity} to inherit the four audit columns plus the {@code @Version}
 * optimistic-lock column (DB-CONVENTIONS §4 — {@code cfg_} tables participate in concurrent edits).
 *
 * <h2>Unique key</h2>
 * {@code ux_cfg_transaction_code_branch_id_transaction_code} on {@code (branch_id, transaction_code)}
 * — the same code can exist in different branches, but the pair is unique.
 */
@Entity
@Table(name = "cfg_transaction_code", uniqueConstraints = @UniqueConstraint(
		name = "ux_cfg_transaction_code_branch_id_transaction_code",
		columnNames = {"branch_id", "transaction_code"}))
public class TransactionCode extends VersionedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** Branch scope FK — the code is scoped per branch (legacy {@code ms_trans_type} branch). */
	@Column(name = "branch_id", nullable = false)
	private String branchId;

	/**
	 * Transaction code — upper-case normalized server-side (BR-BE07-19). Part of the composite
	 * unique key {@code (branch_id, transaction_code)}.
	 */
	@Column(name = "transaction_code", nullable = false)
	private String transactionCode;

	/** Form requester reference (legacy {@code ms_module_menu} form mapping). */
	@Column(name = "form_requester")
	private String formRequester;

	/** Form approval reference (legacy {@code ms_module_menu} form mapping). */
	@Column(name = "form_approval")
	private String formApproval;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getBranchId() {
		return branchId;
	}

	public void setBranchId(String branchId) {
		this.branchId = branchId;
	}

	public String getTransactionCode() {
		return transactionCode;
	}

	public void setTransactionCode(String transactionCode) {
		this.transactionCode = transactionCode;
	}

	public String getFormRequester() {
		return formRequester;
	}

	public void setFormRequester(String formRequester) {
		this.formRequester = formRequester;
	}

	public String getFormApproval() {
		return formApproval;
	}

	public void setFormApproval(String formApproval) {
		this.formApproval = formApproval;
	}

}
// SDD-PROVENANCE: U-007 | vault: .mega-sdd/vaults/acquisition-master-data | TransactionCode @Entity cfg_transaction_code extends VersionedEntity; branchId+transactionCode composite unique key; formRequester/formApproval; upsert BR-BE07-19 upper-case server-side
