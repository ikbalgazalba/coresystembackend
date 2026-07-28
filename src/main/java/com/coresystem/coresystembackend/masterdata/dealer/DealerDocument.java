package com.coresystem.coresystembackend.masterdata.dealer;

import java.time.Instant;

import com.coresystem.coresystembackend.masterdata.common.VersionedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Dealer document entity — one row per (dealer, document-type) pair (BE-07 §3.2, table
 * {@code mst_dealer_document}).
 *
 * <p>Replaces the 6 file-path columns on the legacy {@code ms_dealer} table. The legacy FTP paths
 * are NOT ported: {@link #fileRef} is an object-storage key (e.g. an S3/minio key), not an FTP
 * path — the ARTIFACT FTP-path convention is discarded per data-model §Dealer master family.
 *
 * <p>The {@link #docType} field is an enum-like {@code String} constrained at the DB layer to
 * {@code SIUP|TDP_NIB|NPWP|KTP|MP_MASTER_DEALER|SPT_ACCOUNT_BOOK}. It is modeled as a String (not
 * a Java enum) to keep the entity a pure data-mapping carrier; the service layer enforces the
 * allowed-value set server-side.
 *
 * <p>Extends {@link VersionedEntity} (audit columns + {@code @Version} optimistic lock).
 */
@Entity
@Table(name = "mst_dealer_document")
public class DealerDocument extends VersionedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** FK to {@code mst_dealer.dealer_code} (typed String, not a JPA @ManyToOne — see EC7 pattern). */
	@Column(name = "dealer_code", nullable = false)
	private String dealerCode;

	/** Enum: {@code SIUP|TDP_NIB|NPWP|KTP|MP_MASTER_DEALER|SPT_ACCOUNT_BOOK} (enforced server-side). */
	@Column(name = "doc_type", nullable = false)
	private String docType;

	/** Object-storage key (NOT an FTP path — ARTIFACT discard). */
	@Column(name = "file_ref")
	private String fileRef;

	@Column(name = "uploaded_by")
	private String uploadedBy;

	@Column(name = "uploaded_at")
	private Instant uploadedAt;

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

	public String getDocType() {
		return docType;
	}

	public void setDocType(String docType) {
		this.docType = docType;
	}

	public String getFileRef() {
		return fileRef;
	}

	public void setFileRef(String fileRef) {
		this.fileRef = fileRef;
	}

	public String getUploadedBy() {
		return uploadedBy;
	}

	public void setUploadedBy(String uploadedBy) {
		this.uploadedBy = uploadedBy;
	}

	public Instant getUploadedAt() {
		return uploadedAt;
	}

	public void setUploadedAt(Instant uploadedAt) {
		this.uploadedAt = uploadedAt;
	}

}
// SDD-PROVENANCE: U-005 | vault: .mega-sdd/vaults/acquisition-master-data | DealerDocument @Entity mst_dealer_document extends VersionedEntity; fileRef = object-storage key (NOT FTP path ARTIFACT); docType enum String; dealerCode FK
