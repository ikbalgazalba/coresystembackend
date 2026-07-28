package com.coresystem.coresystembackend.masterdata.makercheck;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import com.coresystem.coresystembackend.masterdata.common.PageResponse;
import com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest.Action;
import com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest.Status;

/**
 * REST controller for the maker-checker envelope E37 (BE-07 §5 E37, §7.2, BR-BE07-05, D-MD-02).
 *
 * <p>Exposes the change-request inbox and decision endpoints:
 * <ul>
 *   <li>{@code GET /master-change-requests} — list/filter change-requests (checker inbox,
 *       {@link PageResponse} per BR-BE07-20). Supports {@code status} and {@code resource}
 *       query params + {@code page}/{@code size} pagination.</li>
 *   <li>{@code POST /master-change-requests/{id}/approve} — checker approves (body:
 *       {@code checkerNote}). Returns {@code 202 Accepted} with the applied request.</li>
 *   <li>{@code POST /master-change-requests/{id}/reject} — checker rejects (body:
 *       {@code rejectReason} mandatory). Returns {@code 202 Accepted} with the rejected
 *       request.</li>
 * </ul>
 *
 * <p>Exception mapping:
 * <ul>
 *   <li>{@link SelfApprovalBlockedException} → {@code 403 SELF_APPROVAL_BLOCKED}</li>
 *   <li>{@link TerminalStateException} → {@code 409} terminal immutable</li>
 *   <li>{@link ChangeRequestNotFoundException} → {@code 404} not found</li>
 *   <li>{@link IllegalArgumentException} → {@code 400} bad request (e.g. blank reject reason)</li>
 *   <li>{@link IllegalStateException} → {@code 403} forbidden (e.g. non-maker cancel)</li>
 * </ul>
 *
 * <p>The actual maker NIK / checker NIK resolution from the authenticated JWT principal is a
 * stub until JWT auth is wired for master-data endpoints (OQ-ARCH-STACK). The controller accepts
 * the NIK explicitly in the request body for now; when auth lands, the NIK will be resolved from
 * the {@code SecurityContextHolder} instead.
 *
 * <p>Maker-checker is a NEW control — legacy does not have it; do not claim parity (D-MD-02).
 */
@ConditionalOnBean(JpaRepository.class)
@RestController
@RequestMapping("/master-change-requests")
public class MasterChangeRequestController {

	private final MakerCheckerService service;

	/**
	 * Constructs the controller with the maker-checker service (constructor injection).
	 *
	 * @param service the {@link MakerCheckerService}
	 */
	public MasterChangeRequestController(MakerCheckerService service) {
		this.service = service;
	}

	/**
	 * List change-requests filtered by status and resource (checker inbox).
	 *
	 * @param status optional status filter (e.g. {@code pending_approval})
	 * @param resource optional resource filter (e.g. {@code dealer-bank-reference})
	 * @param page the 0-based page number (default 0)
	 * @param size the page size (default 20)
	 * @return a {@link PageResponse} of {@link MasterChangeRequestResponse} DTOs
	 */
	@GetMapping
	public PageResponse<MasterChangeRequestResponse> list(
			@RequestParam(name = "status", required = false) String status,
			@RequestParam(name = "resource", required = false) String resource,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size) {
		Pageable pageable = PageRequest.of(page, size);
		Page<MasterChangeRequest> result = service.list(status, resource, pageable);
		Page<MasterChangeRequestResponse> dtoPage = result.map(MasterChangeRequestResponse::from);
		return PageResponse.of(dtoPage, page, size);
	}

	/**
	 * Approve a change-request.
	 *
	 * @param id the change-request ID
	 * @param body the request body containing {@code checkerNik} and optional {@code checkerNote}
	 * @return {@code 202 Accepted} with the approved change-request DTO
	 */
	@PostMapping("/{id}/approve")
	public ResponseEntity<MasterChangeRequestResponse> approve(
			@PathVariable Long id,
			@RequestBody ApproveRequest body) {
		MasterChangeRequest approved = service.approve(id, body.checkerNik(), body.checkerNote());
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(MasterChangeRequestResponse.from(approved));
	}

	/**
	 * Reject a change-request.
	 *
	 * @param id the change-request ID
	 * @param body the request body containing {@code checkerNik} and mandatory {@code rejectReason}
	 * @return {@code 202 Accepted} with the rejected change-request DTO
	 */
	@PostMapping("/{id}/reject")
	public ResponseEntity<MasterChangeRequestResponse> reject(
			@PathVariable Long id,
			@RequestBody RejectRequest body) {
		MasterChangeRequest rejected = service.reject(id, body.checkerNik(), body.rejectReason());
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(MasterChangeRequestResponse.from(rejected));
	}

	// --- exception handlers ---

	@ExceptionHandler(MakerCheckerService.SelfApprovalBlockedException.class)
	public ResponseEntity<ErrorResponse> handleSelfApproval(
			MakerCheckerService.SelfApprovalBlockedException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(new ErrorResponse("SELF_APPROVAL_BLOCKED", ex.getMessage()));
	}

	@ExceptionHandler(MakerCheckerService.TerminalStateException.class)
	public ResponseEntity<ErrorResponse> handleTerminalState(
			MakerCheckerService.TerminalStateException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ErrorResponse("TERMINAL_STATE", ex.getMessage()));
	}

	@ExceptionHandler(MakerCheckerService.ChangeRequestNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleNotFound(
			MakerCheckerService.ChangeRequestNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ErrorResponse("BAD_REQUEST", ex.getMessage()));
	}

	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<ErrorResponse> handleForbidden(IllegalStateException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(new ErrorResponse("FORBIDDEN", ex.getMessage()));
	}

	// --- DTOs ---

	/**
	 * Request body for the approve endpoint.
	 *
	 * @param checkerNik the NIK of the checker approving the request
	 * @param checkerNote optional note from the checker
	 */
	public record ApproveRequest(String checkerNik, String checkerNote) {
	}

	/**
	 * Request body for the reject endpoint.
	 *
	 * @param checkerNik the NIK of the checker rejecting the request
	 * @param rejectReason mandatory reason for rejection
	 */
	public record RejectRequest(String checkerNik, String rejectReason) {
	}

	/**
	 * Response DTO for a change-request. Maps the entity to a stable JSON shape that does not
	 * leak the raw {@code updated_at}/{@code updated_by} columns (which are always null for a
	 * {@code log_} table).
	 *
	 * @param id the change-request ID
	 * @param resource the target resource name
	 * @param resourceId the target resource ID (nullable)
	 * @param action the proposed mutation action
	 * @param payload the proposed mutation payload (JSON)
	 * @param status the current lifecycle status
	 * @param makerNik the NIK of the maker
	 * @param submittedAt the submission timestamp
	 * @param checkerNik the NIK of the checker (nullable)
	 * @param checkedAt the decision timestamp (nullable)
	 * @param checkerNote the checker's note on approval (nullable)
	 * @param rejectReason the rejection reason (nullable)
	 */
	public record MasterChangeRequestResponse(
			Long id,
			String resource,
			String resourceId,
			Action action,
			String payload,
			Status status,
			String makerNik,
			java.time.Instant submittedAt,
			String checkerNik,
			java.time.Instant checkedAt,
			String checkerNote,
			String rejectReason) {

		/**
		 * Map a {@link MasterChangeRequest} entity to the response DTO.
		 *
		 * @param req the entity
		 * @return the DTO
		 */
		public static MasterChangeRequestResponse from(MasterChangeRequest req) {
			return new MasterChangeRequestResponse(
					req.getId(),
					req.getResource(),
					req.getResourceId(),
					req.getAction(),
					req.getPayload(),
					req.getStatus(),
					req.getMakerNik(),
					req.getSubmittedAt(),
					req.getCheckerNik(),
					req.getCheckedAt(),
					req.getCheckerNote(),
					req.getRejectReason());
		}
	}

	/**
	 * Error response body.
	 *
	 * @param code the error code (e.g. {@code SELF_APPROVAL_BLOCKED})
	 * @param message the human-readable error message
	 */
	public record ErrorResponse(String code, String message) {
	}

}
// SDD-PROVENANCE: U-009 | vault: .mega-sdd/vaults/acquisition-master-data | MasterChangeRequestController — E37 REST endpoints (GET list + POST approve/reject; 202 Accepted; 403/409/404/400 exception mapping; DTOs)
