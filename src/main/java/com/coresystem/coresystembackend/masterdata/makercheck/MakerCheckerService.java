package com.coresystem.coresystembackend.masterdata.makercheck;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest.Action;
import com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest.Status;

/**
 * Maker-checker envelope engine E37 — generic change-request lifecycle service (BE-07 §5 E37,
 * §7.2, BR-BE07-05, D-MD-02, constitution §I-002).
 *
 * <p>Implements the state machine {@code (∅)→pending_approval→applied|rejected|cancelled} with
 * the following guards:
 * <ul>
 *   <li><b>submit</b> — any maker can submit a change-request for a BR-BE07-05 resource. The
 *       request is persisted with status {@code pending_approval}.</li>
 *   <li><b>approve</b> — checker NIK must differ from maker NIK
 *       ({@code 403 SELF_APPROVAL_BLOCKED}, D-01 S11). On approve the status becomes
 *       {@code applied}. Idempotent: the same checker replaying their approval returns the
 *       same request without error.</li>
 *   <li><b>reject</b> — checker NIK must differ from maker NIK. {@code rejectReason} is mandatory
 *       (blank → {@link IllegalArgumentException}). Status becomes {@code rejected}.</li>
 *   <li><b>cancel</b> — only the maker can cancel their own request. Status becomes
 *       {@code cancelled}.</li>
 *   <li><b>terminal immutability</b> — {@code applied}/{@code rejected}/{@code cancelled} are
 *       terminal; any further action throws {@link TerminalStateException} (mapped to
 *       {@code 409}).</li>
 * </ul>
 *
 * <p>Checker role-per-resource check is a stub (OQ-BE07-01 RESOLVED v1.3.1 — checker = Kepala
 * Cabang for all resources; the actual role verification against the authenticated principal
 * lands when JWT auth is wired for master-data endpoints, OQ-ARCH-STACK). The stub is marked
 * with a TODO comment, not silent.
 *
 * <p>Maker-checker is a NEW control — legacy does not have it ({@code 12-...§3a}); do not claim
 * parity (D-MD-02).
 *
 * <p>The actual mutation application (calling the resource service to apply the proposed change)
 * is OUT OF SCOPE for this unit — U-006/007/008/012 register apply-callbacks. This service owns
 * only the envelope lifecycle (status transitions + audit fields).
 *
 * <p>Nested exception types ({@link SelfApprovalBlockedException},
 * {@link TerminalStateException}, {@link ChangeRequestNotFoundException}) are declared here
 * rather than as separate files to keep the maker-checker module's surface within the unit's
 * target files. The controller maps each to its HTTP status.
 */
@Service
public class MakerCheckerService {

	private final MasterChangeRequestRepository repository;

	/**
	 * Constructs the service with the change-request repository (constructor injection per
	 * project convention).
	 *
	 * @param repository the {@link MasterChangeRequestRepository}
	 */
	public MakerCheckerService(MasterChangeRequestRepository repository) {
		this.repository = repository;
	}

	/**
	 * Submit a new change-request for a BR-BE07-05 resource.
	 *
	 * <p>The request is persisted with status {@code pending_approval}. Audit columns
	 * {@code created_at}/{@code created_by} are set to the submission timestamp and maker NIK.
	 * The {@code updated_at}/{@code updated_by} columns are intentionally NOT set (append-only
	 * {@code log_} table per DB-CONVENTIONS §4).
	 *
	 * @param resource the target resource name (e.g. {@code "dealer-bank-reference"})
	 * @param action the proposed mutation action
	 * @param payload the proposed mutation payload (JSON string)
	 * @param makerNik the NIK of the maker submitting the request
	 * @return the persisted change-request with ID and {@code pending_approval} status
	 */
	@Transactional
	public MasterChangeRequest submit(String resource, Action action, String payload, String makerNik) {
		Instant now = Instant.now();
		MasterChangeRequest request = new MasterChangeRequest();
		request.setResource(resource);
		request.setAction(action);
		request.setPayload(payload);
		request.setStatus(Status.pending_approval);
		request.setMakerNik(makerNik);
		request.setSubmittedAt(now);
		// Audit columns — created_at/created_by only (log_ table, DB-CONVENTIONS §4).
		request.setCreatedAt(now);
		request.setCreatedBy(makerNik);
		return repository.save(request);
	}

	/**
	 * Approve a change-request.
	 *
	 * <p>Guard: checker NIK must differ from maker NIK ({@code 403 SELF_APPROVAL_BLOCKED}, D-01
	 * S11). Terminal states are immutable ({@code 409}). Idempotent: the same checker replaying
	 * their own approval returns the same request without error; a different checker trying to
	 * approve an already-applied request gets {@code 409} (terminal violation).
	 *
	 * <p>TODO(OQ-BE07-01): verify the checker has the Kepala Cabang role for the resource's
	 * branch scope. Currently a stub — role verification against the authenticated principal
	 * lands when JWT auth is wired for master-data endpoints (OQ-ARCH-STACK).
	 *
	 * @param id the change-request ID
	 * @param checkerNik the NIK of the checker approving the request
	 * @param note optional note from the checker
	 * @return the approved change-request with status {@code applied}
	 * @throws ChangeRequestNotFoundException if the ID does not exist
	 * @throws SelfApprovalBlockedException if checker NIK equals maker NIK
	 * @throws TerminalStateException if the request is in a terminal state other than applied
	 *     (or applied by a different checker)
	 */
	@Transactional
	public MasterChangeRequest approve(Long id, String checkerNik, String note) {
		MasterChangeRequest request = requireFound(id);

		// Idempotent: the same checker replaying their own approval returns the same request.
		if (request.getStatus() == Status.applied
				&& checkerNik.equals(request.getCheckerNik())) {
			return request;
		}

		// Terminal immutability: rejected/cancelled cannot be approved; a different checker
		// trying to approve an already-applied request is also a terminal violation (409).
		if (isTerminal(request.getStatus())) {
			throw new TerminalStateException(id, request.getStatus());
		}

		// Self-approval guard (D-01 S11).
		if (checkerNik.equals(request.getMakerNik())) {
			throw new SelfApprovalBlockedException(checkerNik);
		}

		// TODO(OQ-BE07-01): verify checker has Kepala Cabang role for the resource branch scope.
		// Currently a stub — role check lands when JWT auth is wired (OQ-ARCH-STACK).

		Instant now = Instant.now();
		request.setStatus(Status.applied);
		request.setCheckerNik(checkerNik);
		request.setCheckedAt(now);
		request.setCheckerNote(note);
		return repository.save(request);
	}

	/**
	 * Reject a change-request.
	 *
	 * <p>Guard: checker NIK must differ from maker NIK ({@code 403 SELF_APPROVAL_BLOCKED}).
	 * {@code rejectReason} is mandatory (blank/null → {@link IllegalArgumentException}). Terminal
	 * states are immutable ({@code 409}). No mutation is applied to the target resource.
	 *
	 * @param id the change-request ID
	 * @param checkerNik the NIK of the checker rejecting the request
	 * @param reason the mandatory rejection reason (must not be blank)
	 * @return the rejected change-request with status {@code rejected}
	 * @throws ChangeRequestNotFoundException if the ID does not exist
	 * @throws SelfApprovalBlockedException if checker NIK equals maker NIK
	 * @throws TerminalStateException if the request is in a terminal state
	 * @throws IllegalArgumentException if {@code reason} is blank
	 */
	@Transactional
	public MasterChangeRequest reject(Long id, String checkerNik, String reason) {
		MasterChangeRequest request = requireFound(id);

		if (isTerminal(request.getStatus())) {
			throw new TerminalStateException(id, request.getStatus());
		}

		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException(
					"reject_reason is required when rejecting a change-request");
		}

		if (checkerNik.equals(request.getMakerNik())) {
			throw new SelfApprovalBlockedException(checkerNik);
		}

		Instant now = Instant.now();
		request.setStatus(Status.rejected);
		request.setCheckerNik(checkerNik);
		request.setCheckedAt(now);
		request.setRejectReason(reason);
		return repository.save(request);
	}

	/**
	 * Cancel a change-request.
	 *
	 * <p>Guard: only the maker who submitted the request can cancel it
	 * ({@link IllegalStateException} otherwise). Terminal states are immutable ({@code 409}).
	 * No mutation is applied to the target resource.
	 *
	 * @param id the change-request ID
	 * @param makerNik the NIK of the maker requesting cancellation
	 * @return the cancelled change-request with status {@code cancelled}
	 * @throws ChangeRequestNotFoundException if the ID does not exist
	 * @throws TerminalStateException if the request is in a terminal state
	 * @throws IllegalStateException if the caller is not the maker
	 */
	@Transactional
	public MasterChangeRequest cancel(Long id, String makerNik) {
		MasterChangeRequest request = requireFound(id);

		if (isTerminal(request.getStatus())) {
			throw new TerminalStateException(id, request.getStatus());
		}

		if (!makerNik.equals(request.getMakerNik())) {
			throw new IllegalStateException(
					"Only the maker (" + request.getMakerNik()
							+ ") can cancel this change-request");
		}

		Instant now = Instant.now();
		request.setStatus(Status.cancelled);
		request.setCheckedAt(now);
		return repository.save(request);
	}

	/**
	 * List change-requests filtered by status and resource (checker inbox query).
	 *
	 * @param status the lifecycle status to filter by (nullable for no filter)
	 * @param resource the resource name to filter by (nullable for no filter)
	 * @param pageable the pagination request
	 * @return a page of matching change-requests
	 */
	@Transactional(readOnly = true)
	public org.springframework.data.domain.Page<MasterChangeRequest> list(
			String status, String resource, org.springframework.data.domain.Pageable pageable) {
		return repository.findByStatusAndResource(status, resource, pageable);
	}

	// --- helpers ---

	private MasterChangeRequest requireFound(Long id) {
		return repository.findById(id)
				.orElseThrow(() -> new ChangeRequestNotFoundException(id));
	}

	private static boolean isTerminal(Status status) {
		return status == Status.applied || status == Status.rejected || status == Status.cancelled;
	}

	// --- nested exceptions ---

	/**
	 * Thrown when a checker attempts to approve or reject a change-request they themselves
	 * submitted as maker (D-01 S11 — no self-approval; BR-BE07-05; constitution §I-002).
	 *
	 * <p>Mapped to HTTP {@code 403} with body {@code SELF_APPROVAL_BLOCKED} by
	 * {@link MasterChangeRequestController}.
	 */
	public static class SelfApprovalBlockedException extends RuntimeException {

		/**
		 * Constructs the exception for the given checker NIK.
		 *
		 * @param checkerNik the NIK that attempted self-approval
		 */
		public SelfApprovalBlockedException(String checkerNik) {
			super("SELF_APPROVAL_BLOCKED: checker " + checkerNik
					+ " is the same as the maker — self-approval is not allowed (D-01 S11)");
		}

	}

	/**
	 * Thrown when an action is attempted on a change-request that is already in a terminal state
	 * ({@code applied}/{@code rejected}/{@code cancelled}) per state machine §7.2.
	 *
	 * <p>Terminal states are immutable — no further transition is allowed. Mapped to HTTP
	 * {@code 409} by {@link MasterChangeRequestController}.
	 */
	public static class TerminalStateException extends RuntimeException {

		/**
		 * Constructs the exception for the given change-request ID and its current terminal state.
		 *
		 * @param id the change-request ID
		 * @param currentState the terminal state the request is already in
		 */
		public TerminalStateException(Long id, MasterChangeRequest.Status currentState) {
			super("409: change-request " + id + " is in terminal state " + currentState
					+ " — no further action allowed");
		}

	}

	/**
	 * Thrown when a change-request is not found by ID.
	 *
	 * <p>Mapped to HTTP {@code 404} by {@link MasterChangeRequestController}.
	 */
	public static class ChangeRequestNotFoundException extends RuntimeException {

		/**
		 * Constructs the exception for the given change-request ID.
		 *
		 * @param id the change-request ID that was not found
		 */
		public ChangeRequestNotFoundException(Long id) {
			super("Change-request " + id + " not found");
		}

	}

}
// SDD-PROVENANCE: U-009 | vault: .mega-sdd/vaults/acquisition-master-data | MakerCheckerService — E37 envelope state machine §7.2 (submit→pending_approval; approve/reject/cancel guards; self-approval blocked D-01 S11; terminal immutable 409; idempotent approve; checker role stub OQ-BE07-01) + nested exceptions (SelfApprovalBlocked/TerminalState/ChangeRequestNotFound)
