package com.coresystem.coresystembackend.masterdata.dealer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coresystem.coresystembackend.masterdata.common.PageResponse;
import com.coresystem.coresystembackend.masterdata.dealer.DealerService.DealerServiceException;
import com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest;
import com.coresystem.coresystembackend.masterdata.makercheck.MakerCheckerService;

/**
 * REST controller for the dealer master family — endpoints E12-E21 (BE-07 §5, BR-BE07-05/07/08/09/10).
 *
 * <p>Exposes the dealer aggregate endpoints:
 * <ul>
 *   <li><strong>E12</strong> {@code GET /dealers} — list/search with {@code branch_id} filter
 *       (BR-BE07-07 branch-scoped picker), {@code is_used_car}, {@code status}; returns
 *       {@link PageResponse} per BR-BE07-20.</li>
 *   <li><strong>E13</strong> {@code POST /dealers} — create dealer (KTP/NPWP validate → 422;
 *       409 DEALER_CODE_EXISTS; 422 PARENT_DEALER_NOT_FOUND EC7 fix).</li>
 *   <li><strong>E14</strong> {@code GET / PATCH /dealers/{code}} — detail / update; legal-identity
 *       fields (KTP/NPWP) → maker-checker {@code 202 pending_approval}.</li>
 *   <li><strong>E15</strong> {@code POST /dealers/{code}/deactivate} / {@code /reactivate} —
 *       lifecycle (deactivate-only BR-BE07-03).</li>
 *   <li><strong>E16</strong> {@code GET/POST /dealers/{code}/personnel},
 *       {@code PATCH /dealers/{code}/personnel/{id}} — personnel CRUD.</li>
 *   <li><strong>E17</strong> {@code GET/POST /dealer-job-titles},
 *       {@code PATCH /dealer-job-titles/{id}} — job-title CRUD.</li>
 *   <li><strong>E18</strong> {@code GET/POST/PATCH /dealers/{code}/bank-references} — bank-ref
 *       CRUD; <strong>ALL writes via maker-checker → 202 pending_approval</strong>
 *       (BR-BE07-05 payout {@code [LOCKED]}). Self-approve → 403 SELF_APPROVAL_BLOCKED.</li>
 *   <li><strong>E19</strong> {@code GET/PUT /dealers/{code}/branch-access} — replace-set
 *       atomik.</li>
 *   <li><strong>E20</strong> {@code GET/POST /dealers/{code}/documents} — document list/upload.</li>
 *   <li><strong>E21</strong> {@code GET /dealers/{code}/payment-eligible-contacts} — join
 *       job-title→personnel→bank-ref ALL active simultan (BR-BE07-10/BR-DLRPTN-1).</li>
 * </ul>
 *
 * <p>Exception mapping:
 * <ul>
 *   <li>{@link DealerServiceException} with code prefix {@code DEALER_CODE_EXISTS} → 409</li>
 *   <li>{@link DealerServiceException} with code prefix {@code PARENT_DEALER_NOT_FOUND} → 422</li>
 *   <li>{@link DealerServiceException} with code prefix {@code INVALID_KTP_NPWP} → 422</li>
 *   <li>{@link DealerServiceException} with code suffix {@code _NOT_FOUND} → 404</li>
 *   <li>{@link MakerCheckerService.SelfApprovalBlockedException} → 403 SELF_APPROVAL_BLOCKED</li>
 * </ul>
 *
 * <p>Auth: the actor NIK is accepted explicitly in request bodies for now (stub until JWT auth is
 * wired for master-data endpoints, OQ-ARCH-STACK).
 *
 * <p>Guarded by {@link ConditionalOnBean @ConditionalOnBean(JpaRepository.class)} so the controller
 * only activates when JPA is auto-configured (consistent with the maker-checker controller pattern).
 */
@ConditionalOnBean(JpaRepository.class)
@RestController
@RequestMapping("/dealers")
public class DealerController {

	private final DealerService dealerService;

	/**
	 * Constructs the controller with the dealer service (constructor injection).
	 *
	 * @param dealerService the {@link DealerService}
	 */
	public DealerController(DealerService dealerService) {
		this.dealerService = dealerService;
	}

	// ==================== E12 — List dealers ====================

	/**
	 * E12 — List/search dealers with optional filters (BR-BE07-07 branch-scoped picker).
	 *
	 * @param branchId  optional branch filter — only dealers with active branch-access
	 * @param isUsedCar optional used-car filter
	 * @param status    optional status filter
	 * @param page      0-based page number (default 0)
	 * @param size      page size (default 20)
	 * @return a {@link PageResponse} of dealers
	 */
	@GetMapping
	public PageResponse<Dealer> listDealers(
			@RequestParam(name = "branch_id", required = false) String branchId,
			@RequestParam(name = "is_used_car", required = false) Boolean isUsedCar,
			@RequestParam(name = "status", required = false) String status,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size) {
		return dealerService.listDealersPaged(branchId, isUsedCar, status, page, size);
	}

	// ==================== E13 — Create dealer ====================

	/**
	 * E13 — Create a new dealer.
	 *
	 * @param request the create request (includes {@code actor_nik} for audit)
	 * @return 201 with the created dealer, or 422/409 on validation error
	 */
	@PostMapping
	public ResponseEntity<?> createDealer(@RequestBody DealerCreateRequest request) {
		try {
			Dealer created = dealerService.createDealer(request, request.actorNik());
			return ResponseEntity.status(HttpStatus.CREATED).body(created);
		} catch (DealerServiceException e) {
			return toErrorResponse(e);
		}
	}

	// ==================== E14 — Detail / Update dealer ====================

	/**
	 * E14 — Get dealer detail by code.
	 *
	 * @param code the dealer business key
	 * @return 200 with the dealer, or 404
	 */
	@GetMapping("/{code}")
	public ResponseEntity<?> getDealer(@PathVariable String code) {
		try {
			return ResponseEntity.ok(dealerService.getDealerByCode(code));
		} catch (DealerServiceException e) {
			return toErrorResponse(e);
		}
	}

	/**
	 * E14 — Update a dealer. Legal-identity fields (KTP/NPWP/is_sub_dealer_enabled) → maker-checker
	 * {@code 202 pending_approval}; non-sensitive fields → 200 with updated dealer.
	 *
	 * @param code the dealer business key
	 * @param request the update request
	 * @return 200 with the dealer (non-sensitive), or 202 with the change-request (sensitive)
	 */
	@PatchMapping("/{code}")
	public ResponseEntity<?> updateDealer(@PathVariable String code,
			@RequestBody DealerUpdateRequest request) {
		try {
			Object result = dealerService.updateDealer(code, request, request.actorNik());
			if (result instanceof MasterChangeRequest mcr) {
				return ResponseEntity.status(HttpStatus.ACCEPTED)
						.body(ChangeRequestSummary.from(mcr));
			}
			return ResponseEntity.ok(result);
		} catch (DealerServiceException e) {
			return toErrorResponse(e);
		}
	}

	// ==================== E15 — Lifecycle ====================

	/**
	 * E15 — Deactivate a dealer (BR-BE07-03 deactivate-only).
	 *
	 * @param code the dealer business key
	 * @param body the request body (carries {@code actor_nik})
	 * @return 200 with the deactivated dealer, or 404
	 */
	@PostMapping("/{code}/deactivate")
	public ResponseEntity<?> deactivateDealer(@PathVariable String code,
			@RequestBody ActorRequest body) {
		try {
			return ResponseEntity.ok(dealerService.deactivateDealer(code, body.actorNik()));
		} catch (DealerServiceException e) {
			return toErrorResponse(e);
		}
	}

	/**
	 * E15 — Reactivate a previously-deactivated dealer.
	 *
	 * @param code the dealer business key
	 * @param body the request body (carries {@code actor_nik})
	 * @return 200 with the reactivated dealer, or 404
	 */
	@PostMapping("/{code}/reactivate")
	public ResponseEntity<?> reactivateDealer(@PathVariable String code,
			@RequestBody ActorRequest body) {
		try {
			return ResponseEntity.ok(dealerService.reactivateDealer(code, body.actorNik()));
		} catch (DealerServiceException e) {
			return toErrorResponse(e);
		}
	}

	// ==================== E16 — Personnel CRUD ====================

	/**
	 * E16 — List personnel for a dealer. By default only active ({@code status='A'}) personnel
	 * are returned.
	 *
	 * @param code the dealer business key
	 * @param includeInactive if true, include inactive personnel
	 * @return 200 with the list of personnel
	 */
	@GetMapping("/{code}/personnel")
	public List<DealerPersonnel> listPersonnel(
			@PathVariable String code,
			@RequestParam(name = "include_inactive", defaultValue = "false") boolean includeInactive) {
		return dealerService.listPersonnel(code, includeInactive);
	}

	/**
	 * E16 — Create a new personnel record for a dealer.
	 *
	 * @param code the dealer business key
	 * @param request the personnel request
	 * @return 201 with the created personnel
	 */
	@PostMapping("/{code}/personnel")
	public ResponseEntity<?> createPersonnel(@PathVariable String code,
			@RequestBody PersonnelRequest request) {
		try {
			DealerPersonnel created = dealerService.createPersonnel(code, request, request.actorNik());
			return ResponseEntity.status(HttpStatus.CREATED).body(created);
		} catch (DealerServiceException e) {
			return toErrorResponse(e);
		}
	}

	/**
	 * E16 — Update a personnel record.
	 *
	 * @param code the dealer business key
	 * @param personnelId the personnel business key
	 * @param request the update request
	 * @return 200 with the updated personnel, or 404
	 */
	@PatchMapping("/{code}/personnel/{personnelId}")
	public ResponseEntity<?> updatePersonnel(@PathVariable String code,
			@PathVariable String personnelId, @RequestBody PersonnelRequest request) {
		try {
			return ResponseEntity.ok(dealerService.updatePersonnel(personnelId, request, request.actorNik()));
		} catch (DealerServiceException e) {
			return toErrorResponse(e);
		}
	}

	// ==================== E17 — Job-title CRUD ====================

	/**
	 * E17 — List all dealer job titles.
	 *
	 * @return 200 with the list of job titles
	 */
	@GetMapping("/job-titles")
	public List<DealerJobTitle> listJobTitles() {
		return dealerService.listJobTitles();
	}

	/**
	 * E17 — Create a new job title.
	 *
	 * @param request the job-title request
	 * @return 201 with the created job title
	 */
	@PostMapping("/job-titles")
	public ResponseEntity<DealerJobTitle> createJobTitle(@RequestBody JobTitleRequest request) {
		DealerJobTitle created = dealerService.createJobTitle(
				request.jobTitleId(), request.description(),
				request.dealerPaymentCode(), request.actorNik());
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	/**
	 * E17 — Update a job title.
	 *
	 * @param jobTitleId the job-title business key
	 * @param request the update request
	 * @return 200 with the updated job title, or 404
	 */
	@PatchMapping("/job-titles/{jobTitleId}")
	public ResponseEntity<?> updateJobTitle(@PathVariable String jobTitleId,
			@RequestBody JobTitleRequest request) {
		try {
			return ResponseEntity.ok(dealerService.updateJobTitle(
					jobTitleId, request.description(),
					request.dealerPaymentCode(), request.isActive(), request.actorNik()));
		} catch (DealerServiceException e) {
			return toErrorResponse(e);
		}
	}

	// ==================== E18 — Bank-reference (maker-checker WAJIB) ====================

	/**
	 * E18 — List bank references for a dealer.
	 *
	 * @param code the dealer business key
	 * @return 200 with the list of bank references
	 */
	@GetMapping("/{code}/bank-references")
	public List<DealerBankReference> listBankReferences(@PathVariable String code) {
		return dealerService.listBankReferences(code);
	}

	/**
	 * E18 — Submit a bank-reference create via maker-checker. Returns {@code 202 pending_approval}
	 * (NOT 201 — the record is NOT created until a checker approves).
	 *
	 * @param code the dealer business key
	 * @param request the bank-reference request
	 * @return 202 with the change-request summary
	 */
	@PostMapping("/{code}/bank-references")
	public ResponseEntity<?> createBankReference(@PathVariable String code,
			@RequestBody BankReferenceRequest request) {
		try {
			StringBuilder payload = new StringBuilder("{");
			payload.append("\"dealer_code\":\"").append(code).append("\"");
			payload.append(",\"bank_reference_id\":\"").append(request.bankReferenceId()).append("\"");
			payload.append(",\"account_type\":\"")
					.append(request.accountType() != null ? request.accountType() : "").append("\"");
			payload.append(",\"bank_id\":\"")
					.append(request.bankId() != null ? request.bankId() : "").append("\"");
			payload.append(",\"account_number\":\"").append(request.accountNumber()).append("\"");
			payload.append(",\"account_name\":\"").append(request.accountName()).append("\"");
			if (request.accountDescription() != null) {
				payload.append(",\"account_description\":\"")
						.append(request.accountDescription()).append("\"");
			}
			payload.append(",\"bank_charges_flag\":").append(request.bankChargesFlag());
			if (request.activationDate() != null) {
				payload.append(",\"activation_date\":\"")
						.append(request.activationDate()).append("\"");
			}
			payload.append("}");
			MasterChangeRequest mcr = dealerService.submitBankReferenceCreate(
					code, payload.toString(), request.actorNik());
			return ResponseEntity.status(HttpStatus.ACCEPTED)
					.body(ChangeRequestSummary.from(mcr));
		} catch (DealerServiceException e) {
			return toErrorResponse(e);
		}
	}

	/**
	 * E18 — Submit a bank-reference update via maker-checker. Returns {@code 202 pending_approval}.
	 *
	 * @param code the dealer business key
	 * @param bankReferenceId the bank-reference business key
	 * @param request the update request
	 * @return 202 with the change-request summary
	 */
	@PatchMapping("/{code}/bank-references/{bankReferenceId}")
	public ResponseEntity<?> updateBankReference(@PathVariable String code,
			@PathVariable String bankReferenceId, @RequestBody BankReferenceRequest request) {
		try {
			StringBuilder payload = new StringBuilder("{");
			payload.append("\"dealer_code\":\"").append(code).append("\"");
			payload.append(",\"bank_reference_id\":\"").append(bankReferenceId).append("\"");
			if (request.accountType() != null) {
				payload.append(",\"account_type\":\"").append(request.accountType()).append("\"");
			}
			if (request.bankId() != null) {
				payload.append(",\"bank_id\":\"").append(request.bankId()).append("\"");
			}
			if (request.accountNumber() != null) {
				payload.append(",\"account_number\":\"").append(request.accountNumber()).append("\"");
			}
			if (request.accountName() != null) {
				payload.append(",\"account_name\":\"").append(request.accountName()).append("\"");
			}
			if (request.accountDescription() != null) {
				payload.append(",\"account_description\":\"")
						.append(request.accountDescription()).append("\"");
			}
			payload.append("}");
			MasterChangeRequest mcr = dealerService.submitBankReferenceUpdate(
					code, bankReferenceId, payload.toString(), request.actorNik());
			return ResponseEntity.status(HttpStatus.ACCEPTED)
					.body(ChangeRequestSummary.from(mcr));
		} catch (DealerServiceException e) {
			return toErrorResponse(e);
		}
	}

	/**
	 * E18 — Submit a bank-reference deactivate via maker-checker. Returns {@code 202 pending_approval}.
	 *
	 * @param code the dealer business key
	 * @param bankReferenceId the bank-reference business key
	 * @param body the request body (carries {@code actor_nik})
	 * @return 202 with the change-request summary
	 */
	@PostMapping("/{code}/bank-references/{bankReferenceId}/deactivate")
	public ResponseEntity<?> deactivateBankReference(@PathVariable String code,
			@PathVariable String bankReferenceId, @RequestBody ActorRequest body) {
		try {
			MasterChangeRequest mcr = dealerService.submitBankReferenceDeactivate(
					code, bankReferenceId, body.actorNik());
			return ResponseEntity.status(HttpStatus.ACCEPTED)
					.body(ChangeRequestSummary.from(mcr));
		} catch (DealerServiceException e) {
			return toErrorResponse(e);
		}
	}

	// ==================== E19 — Branch-access (replace-set) ====================

	/**
	 * E19 — Get the branch-access set for a dealer.
	 *
	 * @param code the dealer business key
	 * @return 200 with the list of branch-access rows
	 */
	@GetMapping("/{code}/branch-access")
	public List<DealerBranchAccess> listBranchAccess(@PathVariable String code) {
		return dealerService.listBranchAccess(code);
	}

	/**
	 * E19 — Replace the branch-access set atomically (BR-BE07-07).
	 *
	 * @param code the dealer business key
	 * @param request the branch-access request (set of branch IDs)
	 * @return 200 with the new set of active branch-access rows
	 */
	@PostMapping("/{code}/branch-access")
	public ResponseEntity<?> replaceBranchAccess(@PathVariable String code,
			@RequestBody BranchAccessRequest request) {
		try {
			return ResponseEntity.ok(dealerService.replaceBranchAccess(code, request, request.actorNik()));
		} catch (DealerServiceException e) {
			return toErrorResponse(e);
		}
	}

	// ==================== E20 — Documents ====================

	/**
	 * E20 — List documents for a dealer.
	 *
	 * @param code the dealer business key
	 * @return 200 with the list of documents
	 */
	@GetMapping("/{code}/documents")
	public List<DealerDocument> listDocuments(@PathVariable String code) {
		return dealerService.listDocuments(code);
	}

	/**
	 * E20 — Upload (register) a document for a dealer.
	 *
	 * @param code the dealer business key
	 * @param request the document request
	 * @return 201 with the created document
	 */
	@PostMapping("/{code}/documents")
	public ResponseEntity<?> createDocument(@PathVariable String code,
			@RequestBody DocumentRequest request) {
		try {
			DealerDocument created = dealerService.createDocument(
					code, request.docType(), request.fileRef(), request.actorNik());
			return ResponseEntity.status(HttpStatus.CREATED).body(created);
		} catch (DealerServiceException e) {
			return toErrorResponse(e);
		}
	}

	// ==================== E21 — Payment-eligible-contacts ====================

	/**
	 * E21 — Resolve payment-eligible contacts for a dealer (BR-BE07-10 / BR-DLRPTN-1).
	 *
	 * <p>A contact is eligible only if ALL three links (job-title, personnel, bank-reference) are
	 * active simultaneously.
	 *
	 * @param code the dealer business key
	 * @param jobTitleId optional job-title filter
	 * @return 200 with the list of eligible contacts
	 */
	@GetMapping("/{code}/payment-eligible-contacts")
	public ResponseEntity<?> getPaymentEligibleContacts(
			@PathVariable String code,
			@RequestParam(name = "job_title_id", required = false) String jobTitleId) {
		try {
			return ResponseEntity.ok(dealerService.getPaymentEligibleContacts(code, jobTitleId));
		} catch (DealerServiceException e) {
			return toErrorResponse(e);
		}
	}

	// ==================== Exception handlers ====================

	@ExceptionHandler(DealerServiceException.class)
	public ResponseEntity<ErrorResponse> handleDealerServiceException(DealerServiceException ex) {
		String code = ex.getCode();
		HttpStatus status;
		if ("DEALER_CODE_EXISTS".equals(code)) {
			status = HttpStatus.CONFLICT; // 409
		} else if ("PARENT_DEALER_NOT_FOUND".equals(code) || "INVALID_KTP_NPWP".equals(code)) {
			status = HttpStatus.UNPROCESSABLE_ENTITY; // 422
		} else if (code.endsWith("_NOT_FOUND")) {
			status = HttpStatus.NOT_FOUND; // 404
		} else {
			status = HttpStatus.BAD_REQUEST; // 400 fallback
		}
		return ResponseEntity.status(status).body(new ErrorResponse(code, ex.getMessage()));
	}

	@ExceptionHandler(MakerCheckerService.SelfApprovalBlockedException.class)
	public ResponseEntity<ErrorResponse> handleSelfApproval(
			MakerCheckerService.SelfApprovalBlockedException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(new ErrorResponse("SELF_APPROVAL_BLOCKED", ex.getMessage()));
	}

	// ==================== DTOs ====================

	/** Request body carrying the actor NIK (for audit). */
	public record ActorRequest(String actorNik) {
	}

	/** E13 — Create dealer request. */
	public record DealerCreateRequest(
			String dealerCode,
			String dealerName,
			boolean isAuthorizedDealer,
			boolean isSellingNewProductOnly,
			boolean isUsedCar,
			boolean isSubDealerEnabled,
			String parentDealerCode,
			String groupCode,
			String mainDealerCode,
			String ktpNo,
			String ktpName,
			String npwpNo,
			LocalDate activationDate,
			String notes,
			String actorNik) {
	}

	/** E14 — Update dealer request. Nullable fields are skipped (partial update). */
	public record DealerUpdateRequest(
			String dealerName,
			String ktpNo,
			String ktpName,
			String npwpNo,
			Boolean isSubDealerEnabled,
			String notes,
			String actorNik) {
	}

	/** E16 — Personnel create/update request. */
	public record PersonnelRequest(
			String personnelId,
			String name,
			String jobTitleId,
			String bankReferenceId,
			String status,
			String actorNik) {
	}

	/** E17 — Job-title create/update request. */
	public record JobTitleRequest(
			String jobTitleId,
			String description,
			String dealerPaymentCode,
			Boolean isActive,
			String actorNik) {
	}

	/** E18 — Bank-reference create/update request. */
	public record BankReferenceRequest(
			String bankReferenceId,
			String accountType,
			String accountDescription,
			String bankId,
			String accountNumber,
			String accountName,
			boolean bankChargesFlag,
			LocalDate activationDate,
			String actorNik) {
	}

	/** E19 — Branch-access replace-set request. */
	public record BranchAccessRequest(
			List<String> branchIds,
			String actorNik) {
	}

	/** E20 — Document upload request. */
	public record DocumentRequest(
			String docType,
			String fileRef,
			String actorNik) {
	}

	/** E21 — Payment-eligible contact response DTO. */
	public record PaymentEligibleContact(
			String personnelId,
			String personnelName,
			String jobTitleId,
			String jobTitleDescription,
			String dealerPaymentCode,
			String bankReferenceId,
			String bankId,
			String accountNumber,
			String accountName) {
	}

	/** Change-request summary (returned for 202 responses). */
	public record ChangeRequestSummary(
			Long changeRequestId,
			String resource,
			String action,
			String status,
			String maker,
			Instant submittedAt) {

		/**
		 * Map a {@link MasterChangeRequest} to the summary DTO.
		 *
		 * @param mcr the change-request entity
		 * @return the summary DTO
		 */
		public static ChangeRequestSummary from(MasterChangeRequest mcr) {
			return new ChangeRequestSummary(
					mcr.getId(),
					mcr.getResource(),
					mcr.getAction() != null ? mcr.getAction().name() : null,
					mcr.getStatus() != null ? mcr.getStatus().name() : null,
					mcr.getMakerNik(),
					mcr.getSubmittedAt());
		}
	}

	/** Error response body. */
	public record ErrorResponse(String code, String message) {
	}

	// ==================== Helpers ====================

	/**
	 * Map a {@link DealerServiceException} to an error response based on its code.
	 *
	 * @param e the exception
	 * @return the error response entity
	 */
	private ResponseEntity<ErrorResponse> toErrorResponse(DealerServiceException e) {
		HttpStatus status;
		if ("DEALER_CODE_EXISTS".equals(e.getCode())) {
			status = HttpStatus.CONFLICT;
		} else if ("PARENT_DEALER_NOT_FOUND".equals(e.getCode())
				|| "INVALID_KTP_NPWP".equals(e.getCode())) {
			status = HttpStatus.UNPROCESSABLE_ENTITY;
		} else if (e.getCode().endsWith("_NOT_FOUND")) {
			status = HttpStatus.NOT_FOUND;
		} else {
			status = HttpStatus.BAD_REQUEST;
		}
		return ResponseEntity.status(status).body(new ErrorResponse(e.getCode(), e.getMessage()));
	}

}
// SDD-PROVENANCE: U-006 | vault: .mega-sdd/vaults/acquisition-master-data | DealerController @RestController /dealers — E12-E21 endpoints; E12 branch_id filter (BR-BE07-07); E13 422/409/422 PARENT_DEALER_NOT_FOUND; E18→202 pending_approval; E21 payment-eligible-contacts; @ConditionalOnBean(JpaRepository); 403 SELF_APPROVAL_BLOCKED handler
