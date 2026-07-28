package com.coresystem.coresystembackend.masterdata.transtype;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coresystem.coresystembackend.masterdata.common.PageResponse;
import com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest;
import com.coresystem.coresystembackend.masterdata.transtype.TransactionTypeService.ImmutableFieldViolationException;
import com.coresystem.coresystembackend.masterdata.transtype.TransactionTypeService.TransactionCodeNotFoundException;
import com.coresystem.coresystembackend.masterdata.transtype.TransactionTypeService.TransactionTypeAlreadyExistsException;
import com.coresystem.coresystembackend.masterdata.transtype.TransactionTypeService.TransactionTypeNotFoundException;
import com.coresystem.coresystembackend.masterdata.transtype.TransactionTypeService.TransactionTypePatchRequest;

/**
 * REST controller for the Transaction-Type hierarchy config E22-E25 (BE-07 §4 flows F-U-005).
 *
 * <p>Exposes:
 * <ul>
 *   <li>E22 {@code GET /transaction-codes?branch_id=} — list transaction-codes per branch
 *       (paginated, {@link PageResponse} per BR-BE07-20).</li>
 *   <li>E23 {@code PUT /transaction-codes/{branchId}/{code}} — upsert a transaction-code
 *       (BR-BE07-19: satu aksi Save, upper-case normalize server-side).</li>
 *   <li>E24 {@code GET /transaction-types?transaction_code=} — list transaction-types by their
 *       mapping to a transaction code.</li>
 *   <li>E25 {@code POST /transaction-types} — create a new transaction-type via maker-checker
 *       (BR-BE07-05); {@code mapping} must reference an existing TransactionCode.</li>
 *   <li>E25 {@code PATCH /transaction-types/{code}} — patch {@code is_active} ONLY
 *       (BR-BE07-18 / AC-11); any other field → {@code 422}.</li>
 * </ul>
 *
 * <p>Exception mapping:
 * <ul>
 *   <li>{@link ImmutableFieldViolationException} → {@code 422 IMMUTABLE_FIELD}</li>
 *   <li>{@link TransactionCodeNotFoundException} → {@code 422 TRANSACTION_CODE_NOT_FOUND}</li>
 *   <li>{@link TransactionTypeAlreadyExistsException} → {@code 422 TRANSACTION_TYPE_ALREADY_EXISTS}</li>
 *   <li>{@link TransactionTypeNotFoundException} → {@code 404 TRANSACTION_TYPE_NOT_FOUND}</li>
 * </ul>
 *
 * <p>The actual maker NIK resolution from the authenticated JWT principal is a stub until JWT auth
 * is wired for master-data endpoints (OQ-ARCH-STACK — same pattern as
 * {@code MasterChangeRequestController}). The controller accepts the NIK explicitly in the request
 * body for now.
 *
 * <p>Guarded by {@link ConditionalOnBean @ConditionalOnBean(JpaRepository.class)} so the controller
 * only activates when JPA is available (same pattern as {@code MasterChangeRequestController}).
 */
@ConditionalOnBean(JpaRepository.class)
@RestController
@RequestMapping("/transaction-types")
public class TransactionTypeController {

	private final TransactionTypeService service;

	/**
	 * Constructs the controller with its service (constructor injection).
	 *
	 * @param service the {@link TransactionTypeService}
	 */
	public TransactionTypeController(TransactionTypeService service) {
		this.service = service;
	}

	// --- E22: list transaction-codes per branch ---

	/**
	 * E22 — list transaction-codes for a branch, paginated.
	 *
	 * @param branchId the branch scope
	 * @param page the 0-based page number (default 0)
	 * @param size the page size (default 20)
	 * @return a {@link PageResponse} of transaction-code DTOs
	 */
	@GetMapping("/transaction-codes")
	public PageResponse<TransactionCodeResponse> listTransactionCodes(
			@RequestParam(name = "branch_id") String branchId,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size) {
		Pageable pageable = PageRequest.of(page, size);
		Page<TransactionCode> result = service.listTransactionCodes(branchId, pageable);
		Page<TransactionCodeResponse> dtoPage = result.map(TransactionCodeResponse::from);
		return PageResponse.of(dtoPage, page, size);
	}

	// --- E23: upsert transaction-code (BR-BE07-19) ---

	/**
	 * E23 — upsert a transaction-code (BR-BE07-19: satu aksi Save, upper-case normalize).
	 *
	 * @param branchId the branch scope (path variable)
	 * @param code the transaction code (path variable, will be upper-cased)
	 * @param body the request body containing formRequester and formApproval
	 * @return {@code 200 OK} with the upserted transaction-code DTO
	 */
	@PutMapping("/transaction-codes/{branchId}/{code}")
	public ResponseEntity<TransactionCodeResponse> upsertTransactionCode(
			@PathVariable String branchId,
			@PathVariable String code,
			@RequestBody UpsertTransactionCodeRequest body) {
		TransactionCode saved = service.upsertTransactionCode(
				branchId, code, body.formRequester(), body.formApproval(), body.makerNik());
		return ResponseEntity.ok(TransactionCodeResponse.from(saved));
	}

	// --- E24: list transaction-types by code ---

	/**
	 * E24 — list transaction-types whose mapping references a given transaction code.
	 *
	 * @param transactionCode the referenced transaction code
	 * @param page the 0-based page number (default 0)
	 * @param size the page size (default 20)
	 * @return a {@link PageResponse} of transaction-type DTOs
	 */
	@GetMapping("/transaction-types")
	public PageResponse<TransactionTypeResponse> listTransactionTypes(
			@RequestParam(name = "transaction_code") String transactionCode,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size) {
		Pageable pageable = PageRequest.of(page, size);
		Page<TransactionType> result = service.listTransactionTypes(transactionCode, pageable);
		Page<TransactionTypeResponse> dtoPage = result.map(TransactionTypeResponse::from);
		return PageResponse.of(dtoPage, page, size);
	}

	// --- E25 POST: create transaction-type (via maker-checker) ---

	/**
	 * E25 POST — create a new transaction-type via maker-checker (BR-BE07-05).
	 *
	 * @param body the create request body
	 * @return {@code 202 Accepted} with the submitted change-request DTO
	 */
	@PostMapping("/transaction-types")
	public ResponseEntity<MasterChangeRequest> createTransactionType(
			@RequestBody CreateTransactionTypeRequest body) {
		MasterChangeRequest request = service.createTransactionType(
				body.transactionTypeCode(), body.description(), body.mapping(), body.makerNik());
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(request);
	}

	// --- E25 PATCH: patch is_active ONLY (BR-BE07-18 / AC-11) ---

	/**
	 * E25 PATCH — patch a transaction-type's {@code is_active} flag ONLY (BR-BE07-18 / AC-11).
	 *
	 * @param code the [LOCKED] external-FK routing code identifying the record
	 * @param body the patch request body (only {@code is_active} is accepted)
	 * @return {@code 202 Accepted} with the submitted change-request DTO
	 */
	@PatchMapping("/transaction-types/{code}")
	public ResponseEntity<MasterChangeRequest> patchTransactionType(
			@PathVariable String code,
			@RequestBody TransactionTypePatchRequest body) {
		MasterChangeRequest request = service.patchTransactionType(code, body, body.makerNik());
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(request);
	}

	// --- exception handlers ---

	@ExceptionHandler(ImmutableFieldViolationException.class)
	public ResponseEntity<ErrorResponse> handleImmutableField(ImmutableFieldViolationException ex) {
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
				.body(new ErrorResponse("IMMUTABLE_FIELD", ex.getMessage()));
	}

	@ExceptionHandler(TransactionCodeNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleTransactionCodeNotFound(
			TransactionCodeNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
				.body(new ErrorResponse("TRANSACTION_CODE_NOT_FOUND", ex.getMessage()));
	}

	@ExceptionHandler(TransactionTypeAlreadyExistsException.class)
	public ResponseEntity<ErrorResponse> handleAlreadyExists(
			TransactionTypeAlreadyExistsException ex) {
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
				.body(new ErrorResponse("TRANSACTION_TYPE_ALREADY_EXISTS", ex.getMessage()));
	}

	@ExceptionHandler(TransactionTypeNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleTypeNotFound(
			TransactionTypeNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse("TRANSACTION_TYPE_NOT_FOUND", ex.getMessage()));
	}

	// --- DTOs ---

	/**
	 * Request body for E23 PUT upsert.
	 *
	 * @param formRequester the form-requester reference
	 * @param formApproval the form-approval reference
	 * @param makerNik the NIK of the maker (stub until JWT auth wired — OQ-ARCH-STACK)
	 */
	public record UpsertTransactionCodeRequest(String formRequester, String formApproval, String makerNik) {
	}

	/**
	 * Request body for E25 POST create.
	 *
	 * @param transactionTypeCode the [LOCKED] external-FK routing code
	 * @param description the description (immutable pasca-create)
	 * @param mapping the explicit FK to a TransactionCode (NOT derived from substring)
	 * @param makerNik the NIK of the maker (stub until JWT auth wired — OQ-ARCH-STACK)
	 */
	public record CreateTransactionTypeRequest(
			String transactionTypeCode,
			String description,
			String mapping,
			String makerNik) {
	}

	/**
	 * Response DTO for a transaction-code.
	 *
	 * @param id the database ID
	 * @param branchId the branch scope
	 * @param transactionCode the upper-case transaction code
	 * @param formRequester the form-requester reference
	 * @param formApproval the form-approval reference
	 */
	public record TransactionCodeResponse(
			Long id,
			String branchId,
			String transactionCode,
			String formRequester,
			String formApproval) {

		/** Map a {@link TransactionCode} entity to the response DTO. */
		public static TransactionCodeResponse from(TransactionCode entity) {
			return new TransactionCodeResponse(
					entity.getId(),
					entity.getBranchId(),
					entity.getTransactionCode(),
					entity.getFormRequester(),
					entity.getFormApproval());
		}
	}

	/**
	 * Response DTO for a transaction-type.
	 *
	 * @param id the database ID
	 * @param transactionTypeCode the [LOCKED] external-FK routing code
	 * @param description the description
	 * @param mapping the explicit FK to a TransactionCode
	 * @param active whether the transaction-type is active
	 */
	public record TransactionTypeResponse(
			Long id,
			String transactionTypeCode,
			String description,
			String mapping,
			boolean active) {

		/** Map a {@link TransactionType} entity to the response DTO. */
		public static TransactionTypeResponse from(TransactionType entity) {
			return new TransactionTypeResponse(
					entity.getId(),
					entity.getTransactionTypeCode(),
					entity.getDescription(),
					entity.getMapping(),
					entity.isActive());
		}
	}

	/**
	 * Error response body.
	 *
	 * @param code the error code (e.g. {@code IMMUTABLE_FIELD})
	 * @param message the human-readable error message
	 */
	public record ErrorResponse(String code, String message) {
	}

}
// SDD-PROVENANCE: U-007 | vault: .mega-sdd/vaults/acquisition-master-data | TransactionTypeController @ConditionalOnBean(JpaRepository) @RestController — E22 GET /transaction-codes?branch_id=; E23 PUT /transaction-codes/{branchId}/{code} (upsert BR-BE07-19); E24 GET /transaction-types?transaction_code=; E25 POST /transaction-types (create via maker-checker), PATCH /transaction-types/{code} (is_active ONLY 422 BR-BE07-18/AC-11); exception mapping 422/404; DTOs (TransactionCodeResponse, TransactionTypeResponse)
