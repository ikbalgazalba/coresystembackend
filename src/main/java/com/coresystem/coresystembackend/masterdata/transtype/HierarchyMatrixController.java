package com.coresystem.coresystembackend.masterdata.transtype;

import org.springframework.data.jpa.repository.JpaRepository;
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
import org.springframework.web.bind.annotation.RestController;

import com.coresystem.coresystembackend.masterdata.common.PageResponse;
import com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest;
import com.coresystem.coresystembackend.masterdata.transtype.HierarchyMatrixService.HierarchyRuleViolationException;
import com.coresystem.coresystembackend.masterdata.transtype.HierarchyMatrixService.NextPicMustBeEmptyException;
import com.coresystem.coresystembackend.masterdata.transtype.HierarchyMatrixService.NextPicRequiredException;
import com.coresystem.coresystembackend.masterdata.transtype.HierarchyMatrixService.PicNotFoundException;
import com.coresystem.coresystembackend.masterdata.transtype.HierarchyMatrixService.PicResignedException;
import com.coresystem.coresystembackend.masterdata.user.EmployeeMirror;

/**
 * REST controller for the approval-hierarchy level config E26-E28 (BE-07 §4 flows F-U-005).
 *
 * <p>Exposes:
 * <ul>
 *   <li>E26 {@code GET /hierarchy-matrix?transaction_type_code=} — list active hierarchy levels
 *       for a transaction type (paginated, {@link PageResponse} per BR-BE07-20).</li>
 *   <li>E27 {@code POST /hierarchy-matrix} — upsert a hierarchy level via maker-checker
 *       (BR-BE07-05) with server-side validation (BR-BE07-15/16/17).</li>
 *   <li>E28 {@code GET /hierarchy-matrix/pic-candidates?search=} — search EmployeeMirror by
 *       name/NIK for the PIC picker (stub filter; job-title filter deferred).</li>
 * </ul>
 *
 * <p>Exception mapping:
 * <ul>
 *   <li>{@link HierarchyRuleViolationException} → {@code 422 HIERARCHY_RULE_VIOLATION}</li>
 *   <li>{@link NextPicRequiredException} → {@code 422 NEXT_PIC_REQUIRED}</li>
 *   <li>{@link NextPicMustBeEmptyException} → {@code 422 NEXT_PIC_MUST_BE_EMPTY}</li>
 *   <li>{@link PicNotFoundException} → {@code 422 PIC_NOT_FOUND}</li>
 *   <li>{@link PicResignedException} → {@code 422 PIC_RESIGNED}</li>
 * </ul>
 *
 * <p>The actual maker NIK resolution from the authenticated JWT principal is a stub until JWT auth
 * is wired for master-data endpoints (OQ-ARCH-STACK — same pattern as
 * {@code TransactionTypeController}). The controller accepts the NIK explicitly in the request
 * body for now.
 *
 * only activates when JPA is available (same pattern as {@code TransactionTypeController}).
 */
@RestController
@RequestMapping("/hierarchy-matrix")
public class HierarchyMatrixController {

	private final HierarchyMatrixService service;

	/**
	 * Constructs the controller with its service (constructor injection).
	 *
	 * @param service the {@link HierarchyMatrixService}
	 */
	public HierarchyMatrixController(HierarchyMatrixService service) {
		this.service = service;
	}

	// --- E26: list hierarchy levels per transaction type ---

	/**
	 * E26 — list active hierarchy levels for a transaction type, paginated.
	 *
	 * @param transactionTypeCode the transaction-type routing code
	 * @param page the 0-based page number (default 0)
	 * @param size the page size (default 20)
	 * @return a {@link PageResponse} of hierarchy-level DTOs
	 */
	@GetMapping("/hierarchy-matrix")
	public PageResponse<HierarchyMatrixResponse> listHierarchyLevels(
			@RequestParam(name = "transaction_type_code") String transactionTypeCode,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size) {
		Pageable pageable = PageRequest.of(page, size);
		Page<HierarchyMatrix> result = service.listHierarchyLevels(transactionTypeCode, pageable);
		Page<HierarchyMatrixResponse> dtoPage = result.map(HierarchyMatrixResponse::from);
		return PageResponse.of(dtoPage, page, size);
	}

	// --- E27: upsert hierarchy level (via maker-checker) ---

	/**
	 * E27 POST — upsert a hierarchy level via maker-checker (BR-BE07-05) with server-side
	 * validation (BR-BE07-15/16/17).
	 *
	 * @param body the upsert request body
	 * @return {@code 202 Accepted} with the submitted change-request DTO
	 */
	@PostMapping("/hierarchy-matrix")
	public ResponseEntity<MasterChangeRequest> upsertHierarchyLevel(
			@RequestBody UpsertHierarchyLevelRequest body) {
		MasterChangeRequest request = service.upsertHierarchyLevel(
				body.transactionTypeCode(),
				body.level(),
				body.picNik(),
				body.isApprover(),
				body.nextPicNik(),
				body.makerNik());
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(request);
	}

	// --- E28: PIC picker ---

	/**
	 * E28 — search EmployeeMirror by name/NIK for the PIC picker.
	 *
	 * <p>Returns a {@link PageResponse} of {@link EmployeeMirror} items (active, non-resigned only).
	 * Job-title filter is deferred (stub) — the current implementation searches by name/NIK only.
	 *
	 * @param search the name or NIK substring to search for (case-insensitive)
	 * @param page the 0-based page number (default 0)
	 * @param size the page size (default 20)
	 * @return a {@link PageResponse} of matching active employees
	 */
	@GetMapping("/hierarchy-matrix/pic-candidates")
	public PageResponse<EmployeeMirror> searchPicCandidates(
			@RequestParam(name = "search", defaultValue = "") String search,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size) {
		Pageable pageable = PageRequest.of(page, size);
		Page<EmployeeMirror> result = service.searchPicCandidates(search, pageable);
		return PageResponse.of(result, page, size);
	}

	// --- exception handlers ---

	@ExceptionHandler(HierarchyRuleViolationException.class)
	public ResponseEntity<ErrorResponse> handleHierarchyRuleViolation(
			HierarchyRuleViolationException ex) {
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
				.body(new ErrorResponse("HIERARCHY_RULE_VIOLATION", ex.getMessage()));
	}

	@ExceptionHandler(NextPicRequiredException.class)
	public ResponseEntity<ErrorResponse> handleNextPicRequired(NextPicRequiredException ex) {
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
				.body(new ErrorResponse("NEXT_PIC_REQUIRED", ex.getMessage()));
	}

	@ExceptionHandler(NextPicMustBeEmptyException.class)
	public ResponseEntity<ErrorResponse> handleNextPicMustBeEmpty(NextPicMustBeEmptyException ex) {
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
				.body(new ErrorResponse("NEXT_PIC_MUST_BE_EMPTY", ex.getMessage()));
	}

	@ExceptionHandler(PicNotFoundException.class)
	public ResponseEntity<ErrorResponse> handlePicNotFound(PicNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
				.body(new ErrorResponse("PIC_NOT_FOUND", ex.getMessage()));
	}

	@ExceptionHandler(PicResignedException.class)
	public ResponseEntity<ErrorResponse> handlePicResigned(PicResignedException ex) {
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
				.body(new ErrorResponse("PIC_RESIGNED", ex.getMessage()));
	}

	// --- DTOs ---

	/**
	 * Request body for E27 POST upsert.
	 *
	 * @param transactionTypeCode the transaction-type routing code
	 * @param level the hierarchy level (1-based)
	 * @param picNik the NIK of the PIC at this level
	 * @param isApprover whether this level's PIC is an approver (chain terminator)
	 * @param nextPicNik the NIK of the next PIC (required if !isApprover, must be null if isApprover)
	 * @param makerNik the NIK of the maker (stub until JWT auth wired — OQ-ARCH-STACK)
	 */
	public record UpsertHierarchyLevelRequest(
			String transactionTypeCode,
			int level,
			String picNik,
			boolean isApprover,
			String nextPicNik,
			String makerNik) {
	}

	/**
	 * Response DTO for a hierarchy-level.
	 *
	 * @param id the database ID
	 * @param transactionTypeCode the transaction-type routing code
	 * @param level the hierarchy level (1-based)
	 * @param picNik the NIK of the PIC at this level
	 * @param picName the name of the PIC at this level
	 * @param nextPicNik the NIK of the next PIC (null if approver)
	 * @param nextPicName the name of the next PIC (null if approver)
	 * @param approver whether this level's PIC is an approver
	 * @param statusApprover vestigial legacy field (not used by application logic)
	 * @param escalationDays the escalation timeout in days
	 * @param active whether the hierarchy level is active
	 */
	public record HierarchyMatrixResponse(
			Long id,
			String transactionTypeCode,
			int level,
			String picNik,
			String picName,
			String nextPicNik,
			String nextPicName,
			boolean approver,
			String statusApprover,
			int escalationDays,
			boolean active) {

		/** Map a {@link HierarchyMatrix} entity to the response DTO. */
		public static HierarchyMatrixResponse from(HierarchyMatrix entity) {
			return new HierarchyMatrixResponse(
					entity.getId(),
					entity.getTransactionTypeCode(),
					entity.getLevel(),
					entity.getPicNik(),
					entity.getPicName(),
					entity.getNextPicNik(),
					entity.getNextPicName(),
					entity.isIsApprover(),
					entity.getStatusApprover(),
					entity.getEscalationDays(),
					entity.isActive());
		}
	}

	/**
	 * Error response body.
	 *
	 * @param code the error code (e.g. {@code HIERARCHY_RULE_VIOLATION})
	 * @param message the human-readable error message
	 */
	public record ErrorResponse(String code, String message) {
	}

}
