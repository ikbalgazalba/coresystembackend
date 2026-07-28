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
 * Approval-hierarchy level entity — the {@code cfg_hierarchy_matrix} config table (BE-07 §3.3,
 * E26-E28).
 *
 * <p>Each row defines one level in the approval hierarchy for a transaction type: who is the PIC
 * at that level, whether they are an approver or a requester, and who the next PIC in the chain is.
 * The matrix drives the committee approval routing (Phase 2, BE-03).
 *
 * <h2>Business rules (server-side validated in {@link HierarchyMatrixService})</h2>
 * <ul>
 *   <li><strong>BR-BE07-15</strong> — {@code level==1 && isApprover==true} is invalid (level 1 is
 *       always a requester, never an approver). The service rejects this with
 *       {@code 422 HIERARCHY_RULE_VIOLATION}.</li>
 *   <li><strong>BR-BE07-16</strong> — A non-approver ({@code isApprover==false}) MUST have a
 *       {@code nextPicNik} (the chain continues); an approver ({@code isApprover==true}) MUST NOT
 *       have a {@code nextPicNik} (the chain ends). Violations return {@code 422 NEXT_PIC_REQUIRED}
 *       or {@code 422 NEXT_PIC_MUST_BE_EMPTY}.</li>
 *   <li><strong>BR-BE07-17</strong> — {@code picNik} and {@code nextPicNik} must reference existing,
 *       non-resigned employees in {@code mst_employee_mirror}. This is the OQ-MASTERDATA-02 V4/V6
 *       fix: the legacy system accepted NIKs without validating them against the employee mirror,
 *       leading to orphan hierarchy rows pointing at non-existent or resigned employees.</li>
 * </ul>
 *
 * <p>Extends {@link VersionedEntity} to inherit the four audit columns plus the {@code @Version}
 * optimistic-lock column (DB-CONVENTIONS §4 — {@code cfg_} tables participate in concurrent edits).
 *
 * <h2>Unique key</h2>
 * {@code ux_cfg_hierarchy_matrix_type_level_pic} on {@code (transaction_type_code, level, pic_nik)}
 * — a given PIC can appear at most once per level per transaction type.
 *
 * <h2>{@code statusApprover} — vestigial field</h2>
 * The {@code status_approver} column is a vestigial legacy field carried for zero-diff migration
 * (DB-CONVENTIONS §3). It is NOT used by the application logic; the effective approver flag is
 * {@code is_approver}. The field is kept to avoid breaking the migration checksum.
 */
@Entity
@Table(name = "cfg_hierarchy_matrix", uniqueConstraints = @UniqueConstraint(
		name = "ux_cfg_hierarchy_matrix_type_level_pic",
		columnNames = { "transaction_type_code", "level", "pic_nik" }))
public class HierarchyMatrix extends VersionedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/**
	 * FK to the transaction-type code — the routing key whose approval hierarchy this row belongs
	 * to. Matches {@code cfg_transaction_type.transaction_type_code}.
	 */
	@Column(name = "transaction_type_code", nullable = false)
	private String transactionTypeCode;

	/** Hierarchy level (1-based; level 1 is the entry point / requester level). */
	@Column(name = "level", nullable = false)
	private int level;

	/** NIK of the PIC at this level — must exist in {@code mst_employee_mirror} (BR-BE07-17). */
	@Column(name = "pic_nik", nullable = false)
	private String picNik;

	/** Name of the PIC at this level (denormalized from {@code mst_employee_mirror} for display). */
	@Column(name = "pic_name", nullable = false)
	private String picName;

	/**
	 * NIK of the next PIC in the chain — required when {@code isApprover==false} (BR-BE07-16),
	 * must be null when {@code isApprover==true}. Must exist in {@code mst_employee_mirror}
	 * (BR-BE07-17).
	 */
	@Column(name = "next_pic_nik")
	private String nextPicNik;

	/** Name of the next PIC (denormalized from {@code mst_employee_mirror} for display). */
	@Column(name = "next_pic_name")
	private String nextPicName;

	/**
	 * Whether this level's PIC is an approver (chain terminator) or a requester (chain continues).
	 * Level 1 must never be an approver (BR-BE07-15).
	 */
	@Column(name = "is_approver", nullable = false)
	private boolean isApprover;

	/**
	 * Vestigial legacy field ({@code status_approver}) — carried for zero-diff migration
	 * (DB-CONVENTIONS §3). NOT used by application logic; the effective approver flag is
	 * {@code is_approver}.
	 */
	@Column(name = "status_approver")
	private String statusApprover;

	/** Escalation timeout in days — if the PIC does not act within this period, escalation fires. */
	@Column(name = "escalation_days", nullable = false)
	private int escalationDays;

	/** Active flag — soft-delete toggle (BR-BE07-03 deactivate-only, no hard delete). */
	@Column(name = "is_active", nullable = false)
	private boolean isActive;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getTransactionTypeCode() {
		return transactionTypeCode;
	}

	public void setTransactionTypeCode(String transactionTypeCode) {
		this.transactionTypeCode = transactionTypeCode;
	}

	public int getLevel() {
		return level;
	}

	public void setLevel(int level) {
		this.level = level;
	}

	public String getPicNik() {
		return picNik;
	}

	public void setPicNik(String picNik) {
		this.picNik = picNik;
	}

	public String getPicName() {
		return picName;
	}

	public void setPicName(String picName) {
		this.picName = picName;
	}

	public String getNextPicNik() {
		return nextPicNik;
	}

	public void setNextPicNik(String nextPicNik) {
		this.nextPicNik = nextPicNik;
	}

	public String getNextPicName() {
		return nextPicName;
	}

	public void setNextPicName(String nextPicName) {
		this.nextPicName = nextPicName;
	}

	public boolean isIsApprover() {
		return isApprover;
	}

	public void setIsApprover(boolean isApprover) {
		this.isApprover = isApprover;
	}

	public String getStatusApprover() {
		return statusApprover;
	}

	public void setStatusApprover(String statusApprover) {
		this.statusApprover = statusApprover;
	}

	public int getEscalationDays() {
		return escalationDays;
	}

	public void setEscalationDays(int escalationDays) {
		this.escalationDays = escalationDays;
	}

	public boolean isActive() {
		return isActive;
	}

	public void setActive(boolean isActive) {
		this.isActive = isActive;
	}

}
// SDD-PROVENANCE: U-008 | vault: .mega-sdd/vaults/acquisition-master-data | HierarchyMatrix @Entity cfg_hierarchy_matrix extends VersionedEntity; fields: transactionTypeCode (FK), level (int), picNik, picName, nextPicNik (nullable), nextPicName (nullable), isApprover (boolean), statusApprover (vestigial), escalationDays (int), isActive; unique key (transaction_type_code, level, pic_nik); BR-BE07-15/16/17 server-side validated in service; OQ-MASTERDATA-02 V4/V6 fix (NIK guard)
