package com.coresystem.coresystembackend.masterdata.dealer;

import com.coresystem.coresystembackend.masterdata.common.VersionedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Dealer job-title entity — defines the role a dealer personnel holds (BE-07 §3.2, table
 * {@code mst_dealer_job_title}).
 *
 * <p>Maps the legacy {@code ms_dealer_job_title} table (8 columns). The {@link #dealerPaymentCode}
 * field is {@code [INTENT]} (per data-model) — it carries the payment-scheme code used to resolve
 * eligible payment contacts (BR-BE07-10 join chain: job-title → personnel → bank-reference).
 *
 * <p>Lifecycle is deactivate-only via the {@link #isActive} boolean (BR-BE07-03).
 *
 * <p>Extends {@link VersionedEntity} (audit columns + {@code @Version} optimistic lock).
 */
@Entity
@Table(name = "mst_dealer_job_title")
public class DealerJobTitle extends VersionedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** Business key (legacy {@code job_title_id}); the {@code ux_..._job_title_id} unique index. */
	@Column(name = "job_title_id", unique = true, nullable = false)
	private String jobTitleId;

	@Column(name = "description")
	private String description;

	/** [INTENT] payment-scheme code for BR-BE07-10 contact resolution. */
	@Column(name = "dealer_payment_code")
	private String dealerPaymentCode;

	/** Deactivate-only lifecycle toggle (BR-BE07-03). */
	@Column(name = "is_active", nullable = false)
	private boolean isActive;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getJobTitleId() {
		return jobTitleId;
	}

	public void setJobTitleId(String jobTitleId) {
		this.jobTitleId = jobTitleId;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getDealerPaymentCode() {
		return dealerPaymentCode;
	}

	public void setDealerPaymentCode(String dealerPaymentCode) {
		this.dealerPaymentCode = dealerPaymentCode;
	}

	public boolean isActive() {
		return isActive;
	}

	public void setActive(boolean isActive) {
		this.isActive = isActive;
	}

}
// SDD-PROVENANCE: U-005 | vault: .mega-sdd/vaults/acquisition-master-data | DealerJobTitle @Entity mst_dealer_job_title extends VersionedEntity; jobTitleId business key; dealerPaymentCode [INTENT]; isActive deactivate-only (BR-BE07-03)
