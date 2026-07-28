package com.coresystem.coresystembackend.masterdata.dealer;

import com.coresystem.coresystembackend.masterdata.common.VersionedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Dealer personnel entity — a contact person at a dealer (BE-07 §3.2, table
 * {@code mst_dealer_personnel}).
 *
 * <p>Maps the legacy {@code ms_dealer_personel} table (27 columns). The {@link #status} field
 * drives payment eligibility (BR-DLRPTN-1): only personnel with {@code status = 'A'} and active
 * job-title + active bank-reference are eligible for disbursement contact resolution
 * (BR-BE07-10).
 *
 * <p>{@link #status} is modeled as a {@code String} (not a Java enum) with DB-layer
 * {@code CHECK(status IN ('A','inactive'))} — the {@code 'A'} value is retained verbatim from
 * legacy for migration parity. {@link #bankReferenceId} is a nullable FK to
 * {@code mst_dealer_bank_reference} (owned by U-006).
 *
 * <p>Extends {@link VersionedEntity} (audit columns + {@code @Version} optimistic lock).
 */
@Entity
@Table(name = "mst_dealer_personnel")
public class DealerPersonnel extends VersionedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** Business key (legacy {@code personnel_id}); the {@code ux_..._personnel_id} unique index. */
	@Column(name = "personnel_id", unique = true, nullable = false)
	private String personnelId;

	/** FK to {@code mst_dealer.dealer_code}. */
	@Column(name = "dealer_code", nullable = false)
	private String dealerCode;

	@Column(name = "name")
	private String name;

	/** FK to {@code mst_dealer_job_title.job_title_id}. */
	@Column(name = "job_title_id")
	private String jobTitleId;

	/** BR-DLRPTN-1: {@code 'A'} = active/eligible, {@code 'inactive'} = not eligible. */
	@Column(name = "status")
	private String status;

	/** Nullable FK to {@code mst_dealer_bank_reference} (U-006). */
	@Column(name = "bank_reference_id")
	private String bankReferenceId;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getPersonnelId() {
		return personnelId;
	}

	public void setPersonnelId(String personnelId) {
		this.personnelId = personnelId;
	}

	public String getDealerCode() {
		return dealerCode;
	}

	public void setDealerCode(String dealerCode) {
		this.dealerCode = dealerCode;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getJobTitleId() {
		return jobTitleId;
	}

	public void setJobTitleId(String jobTitleId) {
		this.jobTitleId = jobTitleId;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getBankReferenceId() {
		return bankReferenceId;
	}

	public void setBankReferenceId(String bankReferenceId) {
		this.bankReferenceId = bankReferenceId;
	}

}
// SDD-PROVENANCE: U-005 | vault: .mega-sdd/vaults/acquisition-master-data | DealerPersonnel @Entity mst_dealer_personnel extends VersionedEntity; status enum 'A'|'inactive' (BR-DLRPTN-1); jobTitleId FK; bankReferenceId nullable FK (U-006)
