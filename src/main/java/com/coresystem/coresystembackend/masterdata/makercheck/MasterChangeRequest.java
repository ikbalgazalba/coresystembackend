package com.coresystem.coresystembackend.masterdata.makercheck;

import java.time.Instant;

import com.coresystem.coresystembackend.masterdata.common.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Append-only change-request envelope for the maker-checker control E37 (BE-07 §5 E37, §7.2,
 * BR-BE07-05, D-MD-02).
 *
 * <p>Each row records a proposed sensitive master-data mutation (create / update / deactivate /
 * reactivate / delete on a BR-BE07-05 resource) submitted by a maker, the decision of a checker
 * (approve → {@code applied}, reject → {@code rejected}), or a withdrawal by the maker
 * ({@code cancelled}). The table is {@code log_}-prefixed per DB-CONVENTIONS §4 — INSERT-only,
 * append-only audit log; rows are never UPDATEd or DELETEd.
 *
 * <h2>State machine §7.2</h2>
 * <pre>
 *   (∅) → pending_approval → applied | rejected | cancelled
 * </pre>
 * Terminal states ({@code applied}/{@code rejected}/{@code cancelled}) are immutable — any
 * further action on them is rejected with {@code 409} by {@link MakerCheckerService}.
 *
 * <h2>Inheritance note — log_ table</h2>
 * Extends {@link AuditableEntity} which carries four audit columns
 * ({@code created_at}/{@code created_by}/{@code updated_at}/{@code updated_by}). For a
 * {@code log_} table only {@code created_at}/{@code created_by} are meaningful (DB-CONVENTIONS §4
 * — {@code log_} tables are append-only). The {@code updated_at}/{@code updated_by} columns are
 * inherited but NEVER set by the service layer — they remain {@code null} and JPA does not
 * include unchanged fields in the UPDATE statement (and since this is INSERT-only, no UPDATE
 * is ever issued). This keeps the inheritance clean without requiring {@code @Transient} overrides.
 *
 * <p>Maker-checker is a NEW control — legacy does not have it ({@code 12-...§3a}); do not claim
 * parity (D-MD-02).
 *
 * <p>Checker scope: {@code Kepala Cabang} for all BR-BE07-05 resources (RESOLVED OQ-BE07-01 v1.3.1).
 * Checker ≠ maker enforced app-layer (D-01 S11 — {@code 403 SELF_APPROVAL_BLOCKED}).
 */
@Entity
@Table(name = "log_master_change_request")
public class MasterChangeRequest extends AuditableEntity {

	/** Proposed mutation action on the target resource. */
	public enum Action {
		/** Create a new resource record. */
		create,
		/** Update an existing resource record. */
		update,
		/** Deactivate (soft-delete) an existing resource record. */
		deactivate,
		/** Reactivate a previously-deactivated resource record. */
		reactivate,
		/** Hard-delete a resource record (only {@code PUBLIC_HOLIDAY} per OQ-BE07-06). */
		delete
	}

	/** Change-request lifecycle state per state machine §7.2. */
	public enum Status {
		/** Submitted by maker, awaiting checker decision. Initial state. */
		pending_approval,
		/** Checker approved; mutation applied to the target resource. Terminal. */
		applied,
		/** Checker rejected; no mutation applied. Terminal. */
		rejected,
		/** Maker withdrew the request before a checker decision. Terminal. */
		cancelled
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Target resource name (e.g. {@code "dealer-bank-reference"}, {@code "general-parameter"}). */
	@Column(name = "resource", nullable = false, length = 64)
	private String resource;

	/**
	 * Identifier of the target resource record (for update/deactivate/reactivate/delete actions).
	 * Nullable for {@code create} actions (no target record exists yet).
	 */
	@Column(name = "resource_id", length = 64)
	private String resourceId;

	/** Proposed mutation action. */
	@Enumerated(EnumType.STRING)
	@Column(name = "action", nullable = false, length = 16)
	private Action action;

	/** Proposed mutation payload (JSON). Validated at submit time, not just at approve time. */
	@Column(name = "payload", columnDefinition = "jsonb")
	private String payload;

	/** Current lifecycle state. */
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private Status status;

	/** NIK of the maker who submitted the request. */
	@Column(name = "maker_nik", nullable = false, length = 16)
	private String makerNik;

	/** Timestamp the request was submitted. */
	@Column(name = "submitted_at", nullable = false)
	private Instant submittedAt;

	/** NIK of the checker who approved/rejected the request. Null while pending. */
	@Column(name = "checker_nik", length = 16)
	private String checkerNik;

	/** Timestamp the checker (or maker for cancel) decision was recorded. Null while pending. */
	@Column(name = "checked_at")
	private Instant checkedAt;

	/** Note from the checker on approval. Null for reject/cancel. */
	@Column(name = "checker_note", length = 500)
	private String checkerNote;

	/** Mandatory reason for rejection. Null for approve/cancel. */
	@Column(name = "reject_reason", length = 500)
	private String rejectReason;

	// --- getters/setters ---

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getResource() {
		return resource;
	}

	public void setResource(String resource) {
		this.resource = resource;
	}

	public String getResourceId() {
		return resourceId;
	}

	public void setResourceId(String resourceId) {
		this.resourceId = resourceId;
	}

	public Action getAction() {
		return action;
	}

	public void setAction(Action action) {
		this.action = action;
	}

	public String getPayload() {
		return payload;
	}

	public void setPayload(String payload) {
		this.payload = payload;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public String getMakerNik() {
		return makerNik;
	}

	public void setMakerNik(String makerNik) {
		this.makerNik = makerNik;
	}

	public Instant getSubmittedAt() {
		return submittedAt;
	}

	public void setSubmittedAt(Instant submittedAt) {
		this.submittedAt = submittedAt;
	}

	public String getCheckerNik() {
		return checkerNik;
	}

	public void setCheckerNik(String checkerNik) {
		this.checkerNik = checkerNik;
	}

	public Instant getCheckedAt() {
		return checkedAt;
	}

	public void setCheckedAt(Instant checkedAt) {
		this.checkedAt = checkedAt;
	}

	public String getCheckerNote() {
		return checkerNote;
	}

	public void setCheckerNote(String checkerNote) {
		this.checkerNote = checkerNote;
	}

	public String getRejectReason() {
		return rejectReason;
	}

	public void setRejectReason(String rejectReason) {
		this.rejectReason = rejectReason;
	}

}
// SDD-PROVENANCE: U-009 | vault: .mega-sdd/vaults/acquisition-master-data | MasterChangeRequest @Entity log_master_change_request — maker-checker envelope E37 (state machine §7.2; append-only INSERT-only; extends AuditableEntity, updated_at/updated_by never set)
