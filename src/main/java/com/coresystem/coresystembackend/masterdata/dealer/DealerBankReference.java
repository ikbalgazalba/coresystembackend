package com.coresystem.coresystembackend.masterdata.dealer;

import java.time.LocalDate;

import com.coresystem.coresystembackend.masterdata.common.VersionedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Dealer bank-reference entity — a payout target bank account for a dealer (BE-07 §3.2, table
 * {@code mst_dealer_bank_reference}).
 *
 * <p>Maps the legacy {@code ms_dealer_bank_reference} table (15 columns). The
 * {@link #accountNumber}/{@link #accountName} fields are {@code [LOCKED]} payout zero-diff — their
 * {@code @Column} names must match the legacy column names char-for-char to satisfy the zero-diff
 * migration checksum constraint (06-constraints.md §Field [LOCKED]). They feed disbursement
 * (BR-DLRPTN-2).
 *
 * <p><strong>Maker-checker WAJIB</strong> (BR-BE07-05, constitution §I-002): ALL write operations on
 * this entity go through the maker-checker envelope (E37 / U-010). No direct create/update/delete
 * is exposed; the service layer routes every write through {@code MakerCheckerService.submit} which
 * returns {@code 202 pending_approval}. The record is NOT eligible for payout until a checker
 * (≠ maker) approves the change-request.
 *
 * <p>{@link #status} is modeled as a {@code String} (not a Java enum) with DB-layer
 * {@code CHECK(status IN ('A','inactive'))} — the {@code 'A'} value is retained verbatim from legacy
 * for migration parity. {@code 'A'} = active/eligible; {@code 'inactive'} = not eligible
 * (BR-DLRPTN-1).
 *
 * <p>Key: {@code ux_mst_dealer_bank_reference_dealer_code_bank_reference_id}
 * ({@link #dealerCode}, {@link #bankReferenceId}).
 *
 * <p>Extends {@link VersionedEntity} (audit columns + {@code @Version} optimistic lock,
 * DB-CONVENTIONS §4).
 */
@Entity
@Table(name = "mst_dealer_bank_reference")
public class DealerBankReference extends VersionedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** FK to {@code mst_dealer.dealer_code}. */
	@Column(name = "dealer_code", nullable = false)
	private String dealerCode;

	/**
	 * Business key (legacy {@code bank_reference_id}); the
	 * {@code ux_..._dealer_code_bank_reference_id} unique index (composite with {@link #dealerCode}).
	 */
	@Column(name = "bank_reference_id", nullable = false)
	private String bankReferenceId;

	/** Account type (e.g. {@code OPR}). */
	@Column(name = "account_type")
	private String accountType;

	/** Account description (free-text). */
	@Column(name = "account_description")
	private String accountDescription;

	/** FK to BANK (Tier C lookup — U-011). */
	@Column(name = "bank_id")
	private String bankId;

	/** [LOCKED] payout zero-diff — account number feeds disbursement (BR-DLRPTN-2). */
	@Column(name = "account_number")
	private String accountNumber;

	/** [LOCKED] payout zero-diff — account name feeds disbursement (BR-DLRPTN-2). */
	@Column(name = "account_name")
	private String accountName;

	/** Whether bank charges apply on this account. */
	@Column(name = "bank_charges_flag", nullable = false)
	private boolean bankChargesFlag;

	/**
	 * Lifecycle: {@code 'A'} = active/eligible, {@code 'inactive'} = not eligible (BR-DLRPTN-1).
	 * Modeled as String + CHECK at DB layer (legacy parity).
	 */
	@Column(name = "status")
	private String status;

	@Column(name = "activation_date")
	private LocalDate activationDate;

	@Column(name = "deactivation_date")
	private LocalDate deactivationDate;

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

	public String getBankReferenceId() {
		return bankReferenceId;
	}

	public void setBankReferenceId(String bankReferenceId) {
		this.bankReferenceId = bankReferenceId;
	}

	public String getAccountType() {
		return accountType;
	}

	public void setAccountType(String accountType) {
		this.accountType = accountType;
	}

	public String getAccountDescription() {
		return accountDescription;
	}

	public void setAccountDescription(String accountDescription) {
		this.accountDescription = accountDescription;
	}

	public String getBankId() {
		return bankId;
	}

	public void setBankId(String bankId) {
		this.bankId = bankId;
	}

	public String getAccountNumber() {
		return accountNumber;
	}

	public void setAccountNumber(String accountNumber) {
		this.accountNumber = accountNumber;
	}

	public String getAccountName() {
		return accountName;
	}

	public void setAccountName(String accountName) {
		this.accountName = accountName;
	}

	public boolean isBankChargesFlag() {
		return bankChargesFlag;
	}

	public void setBankChargesFlag(boolean bankChargesFlag) {
		this.bankChargesFlag = bankChargesFlag;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public LocalDate getActivationDate() {
		return activationDate;
	}

	public void setActivationDate(LocalDate activationDate) {
		this.activationDate = activationDate;
	}

	public LocalDate getDeactivationDate() {
		return deactivationDate;
	}

	public void setDeactivationDate(LocalDate deactivationDate) {
		this.deactivationDate = deactivationDate;
	}

}
// SDD-PROVENANCE: U-006 | vault: .mega-sdd/vaults/acquisition-master-data | DealerBankReference @Entity mst_dealer_bank_reference extends VersionedEntity; accountNumber/accountName [LOCKED] payout zero-diff; maker-checker WAJIB (BR-BE07-05); status 'A'|'inactive' (BR-DLRPTN-1); composite key dealerCode+bankReferenceId
