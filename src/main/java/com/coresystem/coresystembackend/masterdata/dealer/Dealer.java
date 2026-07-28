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
 * Dealer master entity — the root of the dealer family (BE-07 §3.2, table {@code mst_dealer}).
 *
 * <p>Maps the legacy {@code ms_dealer} table (51 columns) with two do-not-replicate fixes:
 * <ul>
 *   <li><strong>EC6 fix (BR-BE07-09)</strong> — sub-dealer visibility is the explicit boolean flag
 *       {@link #isSubDealerEnabled}, NOT the legacy name-literal match
 *       {@code '%PT Lucas Digital Indonesia%'} which is discarded as an ARTIFACT.</li>
 *   <li><strong>EC7 fix (BR-BE07-07)</strong> — the dealer hierarchy is modeled as typed String FK
 *       columns {@link #parentDealerCode}/{@code groupCode}/{@code mainDealerCode}, NOT a join via
 *       the {@code notes} free-text column. {@code notes} is retained as free-text only.</li>
 * </ul>
 *
 * <p>KTP/NPWP identity fields are {@code [LOCKED]} — their {@code @Column} names must match the
 * legacy column names char-for-char to satisfy the zero-diff migration checksum constraint
 * (06-constraints.md §Field [LOCKED]).
 *
 * <p>Lifecycle is deactivate-only (BR-BE07-03): no hard-delete path exists on the entity or its
 * repository. Records transition {@code active ⇄ inactive} via the {@link #status} field; the
 * {@link #activationDate}/{@link #deactivationDate} fields track the transition timestamps.
 *
 * <p>Extends {@link VersionedEntity} to inherit the four audit columns
 * ({@code created_at/created_by/updated_at/updated_by}) plus the {@code @Version} optimistic-lock
 * column (DB-CONVENTIONS §4).
 */
@Entity
@Table(name = "mst_dealer")
public class Dealer extends VersionedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** Business key (legacy {@code dealer_code}); the {@code ux_mst_dealer_dealer_code} unique index. */
	@Column(name = "dealer_code", unique = true, nullable = false)
	private String dealerCode;

	@Column(name = "dealer_name")
	private String dealerName;

	/** Whether this dealer is an authorized (main) dealer. */
	@Column(name = "is_authorized_dealer", nullable = false)
	private boolean isAuthorizedDealer;

	/** BR-BE07-08 carve-out: {@code false} = mixed inventory (always included in used-car search). */
	@Column(name = "is_selling_new_product_only", nullable = false)
	private boolean isSellingNewProductOnly;

	@Column(name = "is_used_car", nullable = false)
	private boolean isUsedCar;

	/** EC7 fix: typed FK to parent dealer, NOT join-via-notes. */
	@Column(name = "parent_dealer_code")
	private String parentDealerCode;

	/** EC7 fix: typed dealer group code, NOT join-via-notes. */
	@Column(name = "group_code")
	private String groupCode;

	/** EC7 fix: typed main dealer code, NOT join-via-notes. */
	@Column(name = "main_dealer_code")
	private String mainDealerCode;

	/** EC6 fix: explicit sub-dealer flag, NOT the legacy name-literal '%PT Lucas Digital Indonesia%'. */
	@Column(name = "is_sub_dealer_enabled", nullable = false)
	private boolean isSubDealerEnabled;

	/** [LOCKED] KTP number — zero-diff migration checksum. */
	@Column(name = "ktp_no")
	private String ktpNo;

	/** [LOCKED] KTP name — zero-diff migration checksum. */
	@Column(name = "ktp_name")
	private String ktpName;

	/** [LOCKED] NPWP number — zero-diff migration checksum. */
	@Column(name = "npwp_no")
	private String npwpNo;

	/** Lifecycle: {@code active} or {@code inactive} (BR-BE07-03 deactivate-only). */
	@Column(name = "status")
	private String status;

	@Column(name = "activation_date")
	private LocalDate activationDate;

	@Column(name = "deactivation_date")
	private LocalDate deactivationDate;

	/** Free-text notes — NOT a join key (EC7 fix). */
	@Column(name = "notes")
	private String notes;

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

	public String getDealerName() {
		return dealerName;
	}

	public void setDealerName(String dealerName) {
		this.dealerName = dealerName;
	}

	public boolean isAuthorizedDealer() {
		return isAuthorizedDealer;
	}

	public void setAuthorizedDealer(boolean isAuthorizedDealer) {
		this.isAuthorizedDealer = isAuthorizedDealer;
	}

	public boolean isSellingNewProductOnly() {
		return isSellingNewProductOnly;
	}

	public void setSellingNewProductOnly(boolean isSellingNewProductOnly) {
		this.isSellingNewProductOnly = isSellingNewProductOnly;
	}

	public boolean isUsedCar() {
		return isUsedCar;
	}

	public void setUsedCar(boolean isUsedCar) {
		this.isUsedCar = isUsedCar;
	}

	public String getParentDealerCode() {
		return parentDealerCode;
	}

	public void setParentDealerCode(String parentDealerCode) {
		this.parentDealerCode = parentDealerCode;
	}

	public String getGroupCode() {
		return groupCode;
	}

	public void setGroupCode(String groupCode) {
		this.groupCode = groupCode;
	}

	public String getMainDealerCode() {
		return mainDealerCode;
	}

	public void setMainDealerCode(String mainDealerCode) {
		this.mainDealerCode = mainDealerCode;
	}

	public boolean isSubDealerEnabled() {
		return isSubDealerEnabled;
	}

	public void setSubDealerEnabled(boolean isSubDealerEnabled) {
		this.isSubDealerEnabled = isSubDealerEnabled;
	}

	public String getKtpNo() {
		return ktpNo;
	}

	public void setKtpNo(String ktpNo) {
		this.ktpNo = ktpNo;
	}

	public String getKtpName() {
		return ktpName;
	}

	public void setKtpName(String ktpName) {
		this.ktpName = ktpName;
	}

	public String getNpwpNo() {
		return npwpNo;
	}

	public void setNpwpNo(String npwpNo) {
		this.npwpNo = npwpNo;
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

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

}
// SDD-PROVENANCE: U-005 | vault: .mega-sdd/vaults/acquisition-master-data | Dealer @Entity mst_dealer extends VersionedEntity; EC6 fix (isSubDealerEnabled boolean flag), EC7 fix (parentDealerCode/groupCode/mainDealerCode typed FK String not notes), KTP/NPWP [LOCKED] @Column, deactivate-only (status active|inactive)
