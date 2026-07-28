package com.coresystem.coresystembackend.masterdata.transtype;

import java.time.Instant;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresystem.coresystembackend.masterdata.makercheck.MakerCheckerService;
import com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest.Action;

/**
 * Service for the Transaction-Type hierarchy config E22-E25 (BE-07 §3.3, §4 flows F-U-005).
 *
 * <p>Implements:
 * <ul>
 *   <li><strong>E22 list per branch</strong> — list transaction-codes for a branch (paginated).</li>
 *   <li><strong>E23 upsert</strong> — upsert a transaction-code (BR-BE07-19: satu aksi Save,
 *       upper-case normalize server-side).</li>
 *   <li><strong>E24 list by code</strong> — list transaction-types by their mapping to a
 *       transaction code.</li>
 *   <li><strong>E25 POST create + PATCH</strong> — create a new transaction-type, and PATCH
 *       {@code is_active} ONLY (BR-BE07-18 / AC-11). Any attempt to PATCH other fields
 *       ({@code description}, {@code transactionTypeCode}, {@code mapping}) returns
 *       {@code 422}. The {@code mapping} must reference an existing TransactionCode or the
 *       create returns {@code 422 TRANSACTION_CODE_NOT_FOUND}.</li>
 * </ul>
 *
 * <p><strong>Maker-checker (BR-BE07-05)</strong> — TRANSACTION_TYPE writes (create + PATCH) go
 * through {@link MakerCheckerService#submit} as a change-request with status
 * {@code pending_approval}. The mutation is NOT applied directly; it is applied when the checker
 * approves (the apply-callback registration is a later concern — this unit submits the envelope).
 *
 * <p><strong>Mapping disimpan eksplisit (BR-BE07-18)</strong> — the {@code mapping} field on
 * TransactionType is stored as an explicit String reference to a TransactionCode, NOT derived
 * from {@code substring(0,2)} of the type code. This is a deliberate fix: the legacy system
 * derived the mapping implicitly, which broke when codes were reorganized.
 *
 * <p>Guarded by {@link ConditionalOnBean @ConditionalOnBean(JpaRepository.class)} so the service
 * only activates when JPA is available (same pattern as {@link MakerCheckerService}).
 */
@ConditionalOnBean(JpaRepository.class)
@Service
public class TransactionTypeService {

	private final TransactionCodeRepository transactionCodeRepository;
	private final TransactionTypeRepository transactionTypeRepository;
	private final MakerCheckerService makerCheckerService;

	/**
	 * Constructs the service with its repositories and the maker-checker service.
	 *
	 * @param transactionCodeRepository access to {@code cfg_transaction_code}
	 * @param transactionTypeRepository access to {@code cfg_transaction_type}
	 * @param makerCheckerService the maker-checker envelope engine (E37)
	 */
	public TransactionTypeService(
			TransactionCodeRepository transactionCodeRepository,
			TransactionTypeRepository transactionTypeRepository,
			MakerCheckerService makerCheckerService) {
		this.transactionCodeRepository = transactionCodeRepository;
		this.transactionTypeRepository = transactionTypeRepository;
		this.makerCheckerService = makerCheckerService;
	}

	// --- E22: list transaction-codes per branch ---

	/**
	 * E22 — list transaction-codes for a branch, paginated.
	 *
	 * @param branchId the branch scope
	 * @param pageable the pagination request
	 * @return a page of transaction-codes for the branch
	 */
	@Transactional(readOnly = true)
	public Page<TransactionCode> listTransactionCodes(String branchId, Pageable pageable) {
		return transactionCodeRepository.findByBranchId(branchId, pageable);
	}

	// --- E23: upsert transaction-code (BR-BE07-19) ---

	/**
	 * E23 — upsert a transaction-code (BR-BE07-19: satu aksi Save, upper-case normalize
	 * server-side).
	 *
	 * <p>If a transaction-code with the same {@code (branchId, transactionCode)} already exists,
	 * it is updated in place (formRequester/formApproval overwritten). Otherwise a new row is
	 * inserted. The {@code transactionCode} is normalized to upper-case before the lookup and
	 * before the save, so callers can pass lower-case and still match/insert consistently.
	 *
	 * @param branchId the branch scope
	 * @param transactionCode the transaction code (will be upper-cased)
	 * @param formRequester the form-requester reference
	 * @param formApproval the form-approval reference
	 * @param actorNik the NIK of the actor performing the upsert
	 * @return the saved (upserted) transaction-code
	 */
	@Transactional
	public TransactionCode upsertTransactionCode(
			String branchId, String transactionCode,
			String formRequester, String formApproval, String actorNik) {
		String normalizedCode = normalizeUpperCase(transactionCode);
		Instant now = Instant.now();

		Optional<TransactionCode> existing = transactionCodeRepository
				.findByBranchIdAndTransactionCode(branchId, normalizedCode);

		TransactionCode entity;
		if (existing.isPresent()) {
			entity = existing.get();
			entity.setFormRequester(formRequester);
			entity.setFormApproval(formApproval);
			entity.setUpdatedAt(now);
			entity.setUpdatedBy(actorNik);
		} else {
			entity = new TransactionCode();
			entity.setBranchId(branchId);
			entity.setTransactionCode(normalizedCode);
			entity.setFormRequester(formRequester);
			entity.setFormApproval(formApproval);
			entity.setCreatedAt(now);
			entity.setCreatedBy(actorNik);
		}
		return transactionCodeRepository.save(entity);
	}

	// --- E24: list transaction-types by code ---

	/**
	 * E24 — list transaction-types whose mapping references a given transaction code, paginated.
	 *
	 * @param transactionCode the referenced transaction code
	 * @param pageable the pagination request
	 * @return a page of matching transaction-types
	 */
	@Transactional(readOnly = true)
	public Page<TransactionType> listTransactionTypes(String transactionCode, Pageable pageable) {
		return transactionTypeRepository.findByMapping(transactionCode, pageable);
	}

	// --- E25 POST: create transaction-type (via maker-checker) ---

	/**
	 * E25 POST — create a new transaction-type via maker-checker (BR-BE07-05).
	 *
	 * <p>The {@code mapping} must reference an existing TransactionCode — if no
	 * TransactionCode row exists with a matching {@code transactionCode} value, a
	 * {@link TransactionCodeNotFoundException} is thrown (mapped to {@code 422
	 * TRANSACTION_CODE_NOT_FOUND} by the controller).
	 *
	 * <p>The change-request is submitted to {@link MakerCheckerService} with status
	 * {@code pending_approval}. The actual mutation is applied when the checker approves.
	 *
	 * @param transactionTypeCode the [LOCKED] external-FK routing code
	 * @param description the description (immutable pasca-create)
	 * @param mapping the explicit FK to a TransactionCode (NOT derived from substring)
	 * @param makerNik the NIK of the maker submitting the request
	 * @return the submitted maker-checker change-request (status pending_approval)
	 * @throws TransactionCodeNotFoundException if the mapping references a non-existent
	 *     TransactionCode
	 */
	@Transactional
	public com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest createTransactionType(
			String transactionTypeCode, String description, String mapping, String makerNik) {
		// Validate that the mapping references an existing TransactionCode.
		requireTransactionCodeExists(mapping);

		// Validate that the transaction-type code is not already taken.
		if (transactionTypeRepository.findByTransactionTypeCode(transactionTypeCode).isPresent()) {
			throw new TransactionTypeAlreadyExistsException(transactionTypeCode);
		}

		String payload = buildCreatePayload(transactionTypeCode, description, mapping);
		return makerCheckerService.submit("TRANSACTION_TYPE", Action.create, payload, makerNik);
	}

	// --- E25 PATCH: patch is_active ONLY (BR-BE07-18 / AC-11) ---

	/**
	 * E25 PATCH — patch a transaction-type's {@code is_active} flag ONLY (BR-BE07-18 / AC-11).
	 *
	 * <p>Any attempt to modify other fields ({@code description}, {@code transactionTypeCode},
	 * {@code mapping}) results in {@link ImmutableFieldViolationException} (mapped to {@code 422}
	 * by the controller). The patch is submitted via maker-checker (BR-BE07-05).
	 *
	 * @param transactionTypeCode the [LOCKED] external-FK routing code identifying the record
	 * @param patchRequest the patch fields (only {@code is_active} is accepted; other fields
	 *     must be null/absent)
	 * @param makerNik the NIK of the maker submitting the request
	 * @return the submitted maker-checker change-request (status pending_approval)
	 * @throws TransactionTypeNotFoundException if the transaction-type code does not exist
	 * @throws ImmutableFieldViolationException if any field other than {@code is_active} is
	 *     present in the patch request
	 */
	@Transactional
	public com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest patchTransactionType(
			String transactionTypeCode, TransactionTypePatchRequest patchRequest, String makerNik) {
		TransactionType existing = transactionTypeRepository
				.findByTransactionTypeCode(transactionTypeCode)
				.orElseThrow(() -> new TransactionTypeNotFoundException(transactionTypeCode));

		// BR-BE07-18 / AC-11: only is_active can be PATCHed. Any other field present → 422.
		if (patchRequest.hasImmutableFieldChanges()) {
			throw new ImmutableFieldViolationException();
		}

		// is_active must be present (it is the only patchable field).
		if (patchRequest.isActive() == null) {
			throw new ImmutableFieldViolationException();
		}

		String payload = buildPatchPayload(existing, patchRequest.isActive());
		return makerCheckerService.submit("TRANSACTION_TYPE", Action.update, payload, makerNik);
	}

	// --- helpers ---

	/**
	 * Normalize a transaction code to upper-case (BR-BE07-19). Null-safe.
	 *
	 * @param value the raw transaction code
	 * @return the upper-case value, or null if input is null
	 */
	static String normalizeUpperCase(String value) {
		return value != null ? value.toUpperCase() : null;
	}

	/**
	 * Verify that a TransactionCode row exists with the given transaction code value.
	 * Used to validate the {@code mapping} FK reference.
	 *
	 * @param transactionCode the referenced transaction code
	 * @throws TransactionCodeNotFoundException if no matching TransactionCode exists
	 */
	private void requireTransactionCodeExists(String transactionCode) {
		// The mapping can reference a transaction-code in any branch. We check if ANY row with
		// this transaction_code value exists. Since the unique key is (branch_id, transaction_code),
		// the same code can exist in multiple branches — we just need at least one.
		boolean exists = transactionCodeRepository.findAll().stream()
				.anyMatch(tc -> transactionCode != null
						&& transactionCode.equals(tc.getTransactionCode()));
		if (!exists) {
			throw new TransactionCodeNotFoundException(transactionCode);
		}
	}

	private static String buildCreatePayload(String code, String description, String mapping) {
		return "{\"transaction_type_code\":\"" + escape(code) + "\","
				+ "\"description\":\"" + escape(description) + "\","
				+ "\"mapping\":\"" + escape(mapping) + "\"}";
	}

	private static String buildPatchPayload(TransactionType existing, boolean newActive) {
		return "{\"transaction_type_code\":\"" + escape(existing.getTransactionTypeCode()) + "\","
				+ "\"is_active\":" + newActive + "}";
	}

	private static String escape(String value) {
		return value != null ? value.replace("\\", "\\\\").replace("\"", "\\\"") : "";
	}

	// --- nested exceptions ---

	/**
	 * Thrown when a PATCH attempts to modify a field other than {@code is_active}
	 * (BR-BE07-18 / AC-11). Mapped to HTTP {@code 422 IMMUTABLE_FIELD} by the controller.
	 */
	public static class ImmutableFieldViolationException extends RuntimeException {

		/** Constructs the exception. */
		public ImmutableFieldViolationException() {
			super("IMMUTABLE_FIELD: only is_active can be modified via PATCH "
					+ "(BR-BE07-18 / AC-11)");
		}

	}

	/**
	 * Thrown when the {@code mapping} references a TransactionCode that does not exist.
	 * Mapped to HTTP {@code 422 TRANSACTION_CODE_NOT_FOUND} by the controller.
	 */
	public static class TransactionCodeNotFoundException extends RuntimeException {

		private final String transactionCode;

		/**
		 * Constructs the exception for the given mapping value.
		 *
		 * @param transactionCode the transaction code that was not found
		 */
		public TransactionCodeNotFoundException(String transactionCode) {
			super("TRANSACTION_CODE_NOT_FOUND: no TransactionCode exists with transaction_code='"
					+ transactionCode + "' (mapping must reference an existing TransactionCode)");
			this.transactionCode = transactionCode;
		}

		/** @return the transaction code that was not found */
		public String getTransactionCode() {
			return transactionCode;
		}

	}

	/**
	 * Thrown when a create attempts to use a transaction-type code that already exists.
	 * Mapped to HTTP {@code 422 TRANSACTION_TYPE_ALREADY_EXISTS} by the controller.
	 */
	public static class TransactionTypeAlreadyExistsException extends RuntimeException {

		private final String transactionTypeCode;

		/**
		 * Constructs the exception for the given code.
		 *
		 * @param transactionTypeCode the code that already exists
		 */
		public TransactionTypeAlreadyExistsException(String transactionTypeCode) {
			super("TRANSACTION_TYPE_ALREADY_EXISTS: a TransactionType with transaction_type_code='"
					+ transactionTypeCode + "' already exists");
			this.transactionTypeCode = transactionTypeCode;
		}

		/** @return the code that already exists */
		public String getTransactionTypeCode() {
			return transactionTypeCode;
		}

	}

	/**
	 * Thrown when a transaction-type code is not found. Mapped to HTTP {@code 404} by the
	 * controller.
	 */
	public static class TransactionTypeNotFoundException extends RuntimeException {

		private final String transactionTypeCode;

		/**
		 * Constructs the exception for the given code.
		 *
		 * @param transactionTypeCode the code that was not found
		 */
		public TransactionTypeNotFoundException(String transactionTypeCode) {
			super("TRANSACTION_TYPE_NOT_FOUND: no TransactionType with transaction_type_code='"
					+ transactionTypeCode + "'");
			this.transactionTypeCode = transactionTypeCode;
		}

		/** @return the code that was not found */
		public String getTransactionTypeCode() {
			return transactionTypeCode;
		}

	}

	// --- patch request DTO (used by service + controller) ---

	/**
	 * Patch request for E25 PATCH — only {@code isActive} is accepted; any other field present
	 * triggers {@link ImmutableFieldViolationException}.
	 *
	 * <p>{@code description}, {@code transactionTypeCode}, and {@code mapping} are carried so the
	 * service can detect if the caller attempted to send them (and reject with 422). They are
	 * never applied.
	 *
	 * @param transactionTypeCode the routing code (if present → 422; the path variable is the
	 *     source of truth)
	 * @param description if present → 422 (immutable)
	 * @param mapping if present → 422 (immutable)
	 * @param isActive the only patchable field
	 * @param makerNik the NIK of the maker (stub until JWT auth wired — OQ-ARCH-STACK)
	 */
	public record TransactionTypePatchRequest(
			String transactionTypeCode,
			String description,
			String mapping,
			Boolean isActive,
			String makerNik) {

		/**
		 * Check if any immutable field is present in the patch request (BR-BE07-18 / AC-11).
		 *
		 * @return true if any immutable field (description, mapping, or transactionTypeCode) is
		 *     present
		 */
		public boolean hasImmutableFieldChanges() {
			return description != null || mapping != null;
		}

	}

}
// SDD-PROVENANCE: U-007 | vault: .mega-sdd/vaults/acquisition-master-data | TransactionTypeService @ConditionalOnBean(JpaRepository) — E22 list per branch; E23 upsert (upper-case BR-BE07-19); E24 list by code; E25 POST create + PATCH is_active ONLY (422 on other fields BR-BE07-18/AC-11; mapping must reference existing TransactionCode → 422); TRANSACTION_TYPE write → MakerCheckerService.submit (BR-BE07-05); mapping disimpan eksplisit NOT substring; nested exceptions (ImmutableFieldViolation/TransactionCodeNotFound/TransactionTypeAlreadyExists/TransactionTypeNotFound)
