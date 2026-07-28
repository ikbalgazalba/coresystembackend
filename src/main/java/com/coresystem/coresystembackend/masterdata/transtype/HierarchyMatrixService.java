package com.coresystem.coresystembackend.masterdata.transtype;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest;
import com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest.Action;
import com.coresystem.coresystembackend.masterdata.makercheck.MakerCheckerService;
import com.coresystem.coresystembackend.masterdata.user.EmployeeMirror;
import com.coresystem.coresystembackend.masterdata.user.EmployeeMirrorRepository;

/**
 * Service for the approval-hierarchy level config E26-E28 (BE-07 §3.3, §4 flows F-U-005).
 *
 * <p>Implements:
 * <ul>
 *   <li><strong>E26 list per type</strong> — list active hierarchy levels for a transaction type
 *       (paginated).</li>
 *   <li><strong>E27 insert/update</strong> — upsert a hierarchy level with server-side validation
 *       (BR-BE07-15/16/17). The write goes through {@link MakerCheckerService#submit} as a
 *       change-request with status {@code pending_approval} (BR-BE07-05).</li>
 *   <li><strong>E28 PIC picker</strong> — search {@link EmployeeMirror} by name/NIK (stub filter;
 *       job-title filter deferred).</li>
 * </ul>
 *
 * <h2>Server-side validation (OQ-MASTERDATA-02 V4/V6 fix)</h2>
 * The legacy system accepted NIKs without validating them against the employee mirror, leading to
 * orphan hierarchy rows pointing at non-existent or resigned employees. This service enforces:
 * <ul>
 *   <li><strong>BR-BE07-15</strong> — {@code level==1 && isApprover==true} → 422
 *       {@code HIERARCHY_RULE_VIOLATION} (level 1 is always a requester).</li>
 *   <li><strong>BR-BE07-16</strong> — {@code isApprover==false && nextPicNik==null} → 422
 *       {@code NEXT_PIC_REQUIRED}; {@code isApprover==true && nextPicNik!=null} → 422
 *       {@code NEXT_PIC_MUST_BE_EMPTY}.</li>
 *   <li><strong>BR-BE07-17</strong> — {@code picNik} and {@code nextPicNik} must exist in
 *       {@link EmployeeMirrorRepository} and {@code !isResigned} → 422 {@code PIC_NOT_FOUND} /
 *       {@code PIC_RESIGNED}.</li>
 * </ul>
 *
 * <p>All validation happens BEFORE the maker-checker submit — invalid data never enters the
 * change-request pipeline.
 *
 * <p>Guarded by {@link ConditionalOnBean @ConditionalOnBean(JpaRepository.class)} so the service
 * only activates when JPA is available (same pattern as {@link MakerCheckerService}).
 */
@ConditionalOnBean(JpaRepository.class)
@Service
public class HierarchyMatrixService {

	private final HierarchyMatrixRepository hierarchyMatrixRepository;
	private final EmployeeMirrorRepository employeeMirrorRepository;
	private final MakerCheckerService makerCheckerService;

	/**
	 * Constructs the service with its repositories and the maker-checker service.
	 *
	 * @param hierarchyMatrixRepository access to {@code cfg_hierarchy_matrix}
	 * @param employeeMirrorRepository access to {@code mst_employee_mirror} (for PIC validation)
	 * @param makerCheckerService the maker-checker envelope engine (E37)
	 */
	public HierarchyMatrixService(
			HierarchyMatrixRepository hierarchyMatrixRepository,
			EmployeeMirrorRepository employeeMirrorRepository,
			MakerCheckerService makerCheckerService) {
		this.hierarchyMatrixRepository = hierarchyMatrixRepository;
		this.employeeMirrorRepository = employeeMirrorRepository;
		this.makerCheckerService = makerCheckerService;
	}

	// --- E26: list hierarchy levels per transaction type ---

	/**
	 * E26 — list active hierarchy levels for a transaction type, paginated.
	 *
	 * @param transactionTypeCode the transaction-type routing code
	 * @param pageable the pagination request
	 * @return a page of active hierarchy levels for the transaction type
	 */
	@Transactional(readOnly = true)
	public Page<HierarchyMatrix> listHierarchyLevels(String transactionTypeCode, Pageable pageable) {
		return hierarchyMatrixRepository
				.findByTransactionTypeCodeAndIsActiveTrue(transactionTypeCode, pageable);
	}

	// --- E27: insert/update hierarchy level (via maker-checker) ---

	/**
	 * E27 — upsert a hierarchy level with server-side validation (BR-BE07-15/16/17).
	 *
	 * <p>Validates the hierarchy rules and PIC existence, then submits the change via
	 * {@link MakerCheckerService#submit} as an {@code APPROVAL_HIERARCHY_LEVEL} change-request
	 * (BR-BE07-05). The actual mutation is applied when the checker approves.
	 *
	 * <p>Validation order:
	 * <ol>
	 *   <li>BR-BE07-15: level==1 && isApprover==true → 422 HIERARCHY_RULE_VIOLATION</li>
	 *   <li>BR-BE07-16: next_pic required/must_be_empty consistency</li>
	 *   <li>BR-BE07-17: picNik exists & not resigned; nextPicNik (if present) exists & not resigned</li>
	 * </ol>
	 *
	 * @param transactionTypeCode the transaction-type routing code
	 * @param level the hierarchy level (1-based)
	 * @param picNik the NIK of the PIC at this level
	 * @param isApprover whether this level's PIC is an approver (chain terminator)
	 * @param nextPicNik the NIK of the next PIC (required if !isApprover, must be null if isApprover)
	 * @param makerNik the NIK of the maker submitting the request
	 * @return the submitted maker-checker change-request (status pending_approval)
	 * @throws HierarchyRuleViolationException if level==1 && isApprover==true (BR-BE07-15)
	 * @throws NextPicRequiredException if isApprover==false && nextPicNik==null (BR-BE07-16)
	 * @throws NextPicMustBeEmptyException if isApprover==true && nextPicNik!=null (BR-BE07-16)
	 * @throws PicNotFoundException if picNik or nextPicNik does not exist in EmployeeMirror
	 *     (BR-BE07-17)
	 * @throws PicResignedException if picNik or nextPicNik exists but isResigned (BR-BE07-17)
	 */
	@Transactional
	public MasterChangeRequest upsertHierarchyLevel(
			String transactionTypeCode, int level, String picNik,
			boolean isApprover, String nextPicNik, String makerNik) {

		// BR-BE07-15: level==1 && isApprover==true → 422 HIERARCHY_RULE_VIOLATION
		if (level == 1 && isApprover) {
			throw new HierarchyRuleViolationException(level);
		}

		// BR-BE07-16: next_pic required / must_be_empty consistency
		if (!isApprover && (nextPicNik == null || nextPicNik.isBlank())) {
			throw new NextPicRequiredException();
		}
		if (isApprover && nextPicNik != null && !nextPicNik.isBlank()) {
			throw new NextPicMustBeEmptyException();
		}

		// BR-BE07-17: picNik must exist & not be resigned (OQ-MASTERDATA-02 V4/V6 fix)
		EmployeeMirror pic = requireValidPic(picNik);

		// BR-BE07-17: nextPicNik (if present) must exist & not be resigned
		EmployeeMirror nextPic = null;
		if (nextPicNik != null && !nextPicNik.isBlank()) {
			nextPic = requireValidPic(nextPicNik);
		}

		String payload = buildPayload(transactionTypeCode, level, picNik, pic.getName(),
				isApprover, nextPicNik, nextPic != null ? nextPic.getName() : null);

		return makerCheckerService.submit("APPROVAL_HIERARCHY_LEVEL", Action.create, payload, makerNik);
	}

	// --- E28: PIC picker ---

	/**
	 * E28 — search {@link EmployeeMirror} by name/NIK for the PIC picker.
	 *
	 * <p>Case-insensitive name OR NIK substring match, filtered to active (non-resigned) employees
	 * only. Job-title filter is deferred (stub) — the current implementation searches by name/NIK
	 * only.
	 *
	 * @param search the name or NIK substring to search for (case-insensitive)
	 * @param pageable the pagination request
	 * @return a page of active employees matching the search
	 */
	@Transactional(readOnly = true)
	public Page<EmployeeMirror> searchPicCandidates(String search, Pageable pageable) {
		return employeeMirrorRepository
				.findByNameContainingIgnoreCaseOrNikContainingIgnoreCaseAndIsResignedFalse(
						search, search, pageable);
	}

	// --- helpers ---

	/**
	 * Validate that a NIK exists in {@code mst_employee_mirror} and is not resigned (BR-BE07-17).
	 * This is the OQ-MASTERDATA-02 V4/V6 fix — the legacy system had no guard.
	 *
	 * @param nik the NIK to validate
	 * @return the validated employee
	 * @throws PicNotFoundException if the NIK does not exist in the mirror
	 * @throws PicResignedException if the employee exists but isResigned
	 */
	private EmployeeMirror requireValidPic(String nik) {
		Optional<EmployeeMirror> employee = employeeMirrorRepository.findByNik(nik);
		if (employee.isEmpty()) {
			throw new PicNotFoundException(nik);
		}
		if (employee.get().isResigned()) {
			throw new PicResignedException(nik);
		}
		return employee.get();
	}

	private static String buildPayload(String transactionTypeCode, int level, String picNik,
			String picName, boolean isApprover, String nextPicNik, String nextPicName) {
		return "{\"transaction_type_code\":\"" + escape(transactionTypeCode) + "\","
				+ "\"level\":" + level + ","
				+ "\"pic_nik\":\"" + escape(picNik) + "\","
				+ "\"pic_name\":\"" + escape(picName) + "\","
				+ "\"is_approver\":" + isApprover + ","
				+ "\"next_pic_nik\":" + (nextPicNik != null ? "\"" + escape(nextPicNik) + "\"" : "null") + ","
				+ "\"next_pic_name\":" + (nextPicName != null ? "\"" + escape(nextPicName) + "\"" : "null") + "}";
	}

	private static String escape(String value) {
		return value != null ? value.replace("\\", "\\\\").replace("\"", "\\\"") : "";
	}

	// --- nested exceptions ---

	/**
	 * Thrown when {@code level==1 && isApprover==true} (BR-BE07-15). Level 1 is always a requester,
	 * never an approver. Mapped to HTTP {@code 422 HIERARCHY_RULE_VIOLATION} by the controller.
	 */
	public static class HierarchyRuleViolationException extends RuntimeException {

		private final int level;

		/**
		 * Constructs the exception for the given level.
		 *
		 * @param level the level that violated the rule
		 */
		public HierarchyRuleViolationException(int level) {
			super("HIERARCHY_RULE_VIOLATION: level " + level
					+ " cannot be an approver (BR-BE07-15: level 1 is always a requester)");
			this.level = level;
		}

		/** @return the level that violated the rule */
		public int getLevel() {
			return level;
		}

	}

	/**
	 * Thrown when {@code isApprover==false && nextPicNik==null} (BR-BE07-16). A non-approver must
	 * have a next PIC — the chain must continue. Mapped to HTTP {@code 422 NEXT_PIC_REQUIRED}.
	 */
	public static class NextPicRequiredException extends RuntimeException {

		/** Constructs the exception. */
		public NextPicRequiredException() {
			super("NEXT_PIC_REQUIRED: a non-approver level must have a next_pic_nik "
					+ "(BR-BE07-16: the chain must continue)");
		}

	}

	/**
	 * Thrown when {@code isApprover==true && nextPicNik!=null} (BR-BE07-16). An approver is the
	 * chain terminator — there must be no next PIC. Mapped to HTTP
	 * {@code 422 NEXT_PIC_MUST_BE_EMPTY}.
	 */
	public static class NextPicMustBeEmptyException extends RuntimeException {

		/** Constructs the exception. */
		public NextPicMustBeEmptyException() {
			super("NEXT_PIC_MUST_BE_EMPTY: an approver level must not have a next_pic_nik "
					+ "(BR-BE07-16: the approver terminates the chain)");
		}

	}

	/**
	 * Thrown when a PIC NIK does not exist in {@code mst_employee_mirror} (BR-BE07-17). This is the
	 * OQ-MASTERDATA-02 V4/V6 fix — the legacy system had no guard against orphan NIKs. Mapped to
	 * HTTP {@code 422 PIC_NOT_FOUND}.
	 */
	public static class PicNotFoundException extends RuntimeException {

		private final String nik;

		/**
		 * Constructs the exception for the given NIK.
		 *
		 * @param nik the NIK that was not found
		 */
		public PicNotFoundException(String nik) {
			super("PIC_NOT_FOUND: NIK '" + nik
					+ "' does not exist in mst_employee_mirror (BR-BE07-17)");
			this.nik = nik;
		}

		/** @return the NIK that was not found */
		public String getNik() {
			return nik;
		}

	}

	/**
	 * Thrown when a PIC NIK exists in {@code mst_employee_mirror} but the employee is resigned
	 * (BR-BE07-17). A resigned employee cannot be a PIC. Mapped to HTTP {@code 422 PIC_RESIGNED}.
	 */
	public static class PicResignedException extends RuntimeException {

		private final String nik;

		/**
		 * Constructs the exception for the given NIK.
		 *
		 * @param nik the NIK of the resigned employee
		 */
		public PicResignedException(String nik) {
			super("PIC_RESIGNED: NIK '" + nik
					+ "' is resigned and cannot be a PIC (BR-BE07-17)");
			this.nik = nik;
		}

		/** @return the NIK of the resigned employee */
		public String getNik() {
			return nik;
		}

	}

}
// SDD-PROVENANCE: U-008 | vault: .mega-sdd/vaults/acquisition-master-data | HierarchyMatrixService @ConditionalOnBean(JpaRepository) — E26 list per type; E27 upsert with server-side validation (BR-BE07-15 level==1&&isApprover→422 HIERARCHY_RULE_VIOLATION; BR-BE07-16 next_pic required/empty; BR-BE07-17 PIC not found/resigned OQ-MASTERDATA-02 V4/V6 fix); APPROVAL_HIERARCHY_LEVEL write → MakerCheckerService.submit (BR-BE07-05); E28 PIC picker name/NIK search (stub filter, job-title deferred); nested exceptions (HierarchyRuleViolation/NextPicRequired/NextPicMustBeEmpty/PicNotFound/PicResigned)
