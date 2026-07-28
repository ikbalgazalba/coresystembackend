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
 * Transaction-Type config entity — the child level of the Transaction-Type hierarchy
 * (BE-07 §3.3, table {@code cfg_transaction_type}).
 *
 * <p>Each row defines a transaction-type code that is the routing key for the committee approval
 * flow (Phase 2, BE-03). The {@code transaction_type_code} is an external-FK that must match
 * char-for-char (BR-PRODASSET-7 {@code [LOCKED]}) — its {@code @Column} name must not change.
 *
 * <h2>Field mutability (BR-BE07-18)</h2>
 * <ul>
 *   <li>{@code transactionTypeCode} — immutable pasca-create (it is the routing key).</li>
 *   <li>{@code description} — immutable pasca-create.</li>
 *   <li>{@code mapping} — immutable pasca-create. Stored eksplisit as a String FK to
 *       {@link TransactionCode#getTransactionCode()}, NOT derived from
 *       {@code substring(0,2)} of the code (BR-BE07-18).</li>
 *   <li>{@code isActive} — the ONLY field that can be modified via PATCH (BR-BE07-18 / AC-11).
 *       Any attempt to PATCH other fields returns {@code 422}.</li>
 * </ul>
 *
 * <p>Extends {@link VersionedEntity} to inherit the four audit columns plus the {@code @Version}
 * optimistic-lock column (DB-CONVENTIONS §4 — {@code cfg_} tables participate in concurrent edits).
 *
 * <h2>Unique key</h2>
 * {@code ux_cfg_transaction_type_transaction_type_code} on {@code (transaction_type_code)} — the
 * routing key is globally unique.
 */
@Entity
@Table(name = "cfg_transaction_type", uniqueConstraints = @UniqueConstraint(
		name = "ux_cfg_transaction_type_transaction_type_code",
		columnNames = "transaction_type_code"))
public class TransactionType extends VersionedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/**
	 * [LOCKED] external-FK — must match char-for-char (BR-PRODASSET-7). This is the committee
	 * routing key (Phase 2, BE-03). The {@code @Column} name {@code transaction_type_code} must
	 * not change to satisfy the zero-diff migration checksum constraint.
	 */
	@Column(name = "transaction_type_code", nullable = false)
	private String transactionTypeCode;

	/** Description — immutable pasca-create (BR-BE07-18). */
	@Column(name = "description")
	private String description;

	/**
	 * FK logis ke {@link TransactionCode#getTransactionCode()} — disimpan eksplisit, NOT derived
	 * from {@code substring(0,2)} of the code (BR-BE07-18). Immutable pasca-create.
	 */
	@Column(name = "mapping")
	private String mapping;

	/**
	 * Active flag — the ONLY field that can be modified via PATCH (BR-BE07-18 / AC-11).
	 */
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

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getMapping() {
		return mapping;
	}

	public void setMapping(String mapping) {
		this.mapping = mapping;
	}

	public boolean isActive() {
		return isActive;
	}

	public void setActive(boolean isActive) {
		this.isActive = isActive;
	}

}
// SDD-PROVENANCE: U-007 | vault: .mega-sdd/vaults/acquisition-master-data | TransactionType @Entity cfg_transaction_type extends VersionedEntity; transactionTypeCode [LOCKED] external-FK char-for-char BR-PRODASSET-7; description immutable; mapping disimpan eksplisit NOT substring BR-BE07-18; isActive only PATCHable field
