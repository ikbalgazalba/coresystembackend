package com.coresystem.coresystembackend.masterdata.dealer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresystem.coresystembackend.masterdata.common.PageResponse;
import com.coresystem.coresystembackend.masterdata.dealer.DealerController.BranchAccessRequest;
import com.coresystem.coresystembackend.masterdata.dealer.DealerController.DealerCreateRequest;
import com.coresystem.coresystembackend.masterdata.dealer.DealerController.DealerUpdateRequest;
import com.coresystem.coresystembackend.masterdata.dealer.DealerController.PaymentEligibleContact;
import com.coresystem.coresystembackend.masterdata.dealer.DealerController.PersonnelRequest;
import com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest;
import com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest.Action;
import com.coresystem.coresystembackend.masterdata.makercheck.MakerCheckerService;

/**
 * Service layer for the dealer master family — endpoints E12-E21 (BE-07 §5, §6, BR-BE07-05/07/08/09/10).
 *
 * <p>Owns the business logic for dealer CRUD, lifecycle, personnel, job-title, bank-reference,
 * branch-access, documents, and payment-eligible-contacts resolution. All bank-reference writes
 * (E18) are routed through {@link MakerCheckerService#submit} → {@code 202 pending_approval}
 * (BR-BE07-05, constitution §I-002 — payout target {@code [LOCKED]} maker-checker WAJIB). Legal-
 * identity-field updates on a dealer (E14 — KTP/NPWP) are also routed through maker-checker.
 *
 * <h2>Key business rules</h2>
 * <ul>
 *   <li><strong>BR-BE07-07</strong> — E12 {@code branch_id} filter: only dealers with an active
 *       {@code DEALER_BRANCH_ACCESS} row for that branch appear in the picker.</li>
 *   <li><strong>EC7 fix</strong> — E13 {@code PARENT_DEALER_NOT_FOUND} (422): parent dealer FK is
 *       validated explicitly against {@code mst_dealer.dealer_code}, NOT resolved via {@code notes}
 *       free-text.</li>
 *   <li><strong>BR-BE07-05</strong> — E18 bank-ref writes → {@code 202 pending_approval}; record
 *       is NOT eligible for payout until checker approves.</li>
 *   <li><strong>BR-BE07-10 / BR-DLRPTN-1</strong> — E21 payment-eligible-contacts: a contact is
 *       eligible only if ALL three links (job-title, personnel, bank-reference) are active
 *       simultaneously. If any link is inactive, the contact drops out of the eligible set.</li>
 *   <li><strong>BR-BE07-03</strong> — deactivate-only lifecycle (no hard delete).</li>
 * </ul>
 *
 * <p>Actor NIK is passed explicitly from the controller (stub until JWT auth is wired for
 * master-data endpoints, OQ-ARCH-STACK). Audit columns are set from this actor.
 */
@ConditionalOnBean(JpaRepository.class)
@Service
public class DealerService {

	private static final String STATUS_ACTIVE = "active";
	private static final String STATUS_INACTIVE = "inactive";
	private static final String PERSONNEL_STATUS_A = "A";
	private static final String RESOURCE_DEALER = "dealer";
	private static final String RESOURCE_DEALER_BANK_REFERENCE = "dealer-bank-reference";

	private final DealerRepository dealerRepository;
	private final DealerDocumentRepository documentRepository;
	private final DealerPersonnelRepository personnelRepository;
	private final DealerJobTitleRepository jobTitleRepository;
	private final DealerBranchAccessRepository branchAccessRepository;
	private final DealerBankReferenceRepository bankReferenceRepository;
	private final MakerCheckerService makerCheckerService;

	/**
	 * Constructs the service with all dealer-family repositories + the maker-checker service
	 * (constructor injection per project convention).
	 *
	 * @param dealerRepository       repo for {@code mst_dealer}
	 * @param documentRepository     repo for {@code mst_dealer_document}
	 * @param personnelRepository    repo for {@code mst_dealer_personnel}
	 * @param jobTitleRepository     repo for {@code mst_dealer_job_title}
	 * @param branchAccessRepository repo for {@code mst_dealer_branch_access}
	 * @param bankReferenceRepository repo for {@code mst_dealer_bank_reference}
	 * @param makerCheckerService    the maker-checker envelope engine (U-010)
	 */
	public DealerService(
			DealerRepository dealerRepository,
			DealerDocumentRepository documentRepository,
			DealerPersonnelRepository personnelRepository,
			DealerJobTitleRepository jobTitleRepository,
			DealerBranchAccessRepository branchAccessRepository,
			DealerBankReferenceRepository bankReferenceRepository,
			MakerCheckerService makerCheckerService) {
		this.dealerRepository = dealerRepository;
		this.documentRepository = documentRepository;
		this.personnelRepository = personnelRepository;
		this.jobTitleRepository = jobTitleRepository;
		this.branchAccessRepository = branchAccessRepository;
		this.bankReferenceRepository = bankReferenceRepository;
		this.makerCheckerService = makerCheckerService;
	}

	// ==================== E12 — List dealers ====================

	/**
	 * E12 — List/search dealers with optional filters (BR-BE07-07 branch-scoped picker).
	 *
	 * <p>When {@code branchId} is provided, only dealers with an active
	 * {@code DEALER_BRANCH_ACCESS} row for that branch are returned. The {@code isUsedCar} and
	 * {@code status} filters are applied in-memory on the result set (the dealer table is small
	 * enough that a two-step filter is acceptable; a later unit may push these to a JPA
	 * Specification if performance requires).
	 *
	 * @param branchId  optional branch filter (BR-BE07-07)
	 * @param isUsedCar optional used-car filter
	 * @param status    optional status filter ({@code active}/{@code inactive})
	 * @return filtered list of dealers
	 */
	@Transactional(readOnly = true)
	public List<Dealer> listDealers(String branchId, Boolean isUsedCar, String status) {
		List<Dealer> dealers;
		if (branchId != null && !branchId.isBlank()) {
			// BR-BE07-07: only dealers with active branch-access for this branch.
			Set<String> dealerCodes = branchAccessRepository
					.findByBranchIdAndIsActiveTrue(branchId).stream()
					.map(DealerBranchAccess::getDealerCode)
					.collect(Collectors.toSet());
			dealers = dealerCodes.stream()
					.map(dealerRepository::findByDealerCode)
					.filter(Optional::isPresent)
					.map(Optional::get)
					.collect(Collectors.toList());
		} else {
			dealers = dealerRepository.findAll();
		}
		// Apply optional in-memory filters.
		if (isUsedCar != null) {
			dealers = dealers.stream().filter(Dealer::isUsedCar).collect(Collectors.toList());
		}
		if (status != null && !status.isBlank()) {
			dealers = dealers.stream()
					.filter(d -> status.equalsIgnoreCase(d.getStatus()))
					.collect(Collectors.toList());
		}
		return dealers;
	}

	/**
	 * Convenience overload returning a {@link PageResponse} for a page slice of the filtered list
	 * (BR-BE07-20 pagination envelope).
	 *
	 * @param branchId  optional branch filter
	 * @param isUsedCar optional used-car filter
	 * @param status    optional status filter
	 * @param page      0-based page number
	 * @param size      page size
	 * @return a {@link PageResponse} of dealers
	 */
	@Transactional(readOnly = true)
	public PageResponse<Dealer> listDealersPaged(
			String branchId, Boolean isUsedCar, String status, int page, int size) {
		List<Dealer> all = listDealers(branchId, isUsedCar, status);
		int total = all.size();
		int from = Math.min(page * size, total);
		int to = Math.min(from + size, total);
		List<Dealer> slice = all.subList(from, to);
		int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
		return new PageResponse<>(slice, page, totalPages, total);
	}

	/**
	 * E14 — Get a single dealer by its business key.
	 *
	 * @param dealerCode the dealer business key
	 * @return the dealer
	 * @throws DealerServiceException with code {@code DEALER_NOT_FOUND} (404)
	 */
	@Transactional(readOnly = true)
	public Dealer getDealerByCode(String dealerCode) {
		return requireDealer(dealerCode);
	}

	// ==================== E13 — Create dealer ====================

	/**
	 * E13 — Create a new dealer (BE-07 §5 E13).
	 *
	 * <p>Validates:
	 * <ul>
	 *   <li>KTP/NPWP format ({@code [LOCKED]} — 422 on bad format).</li>
	 *   <li>{@code dealer_code} uniqueness → 409 {@code DEALER_CODE_EXISTS}.</li>
	 *   <li>Parent dealer FK (EC7 fix — 422 {@code PARENT_DEALER_NOT_FOUND} if
	 *       {@code parent_dealer_code} is set but does not match any {@code mst_dealer.dealer_code};
	 *       NOT resolved via {@code notes}).</li>
	 * </ul>
	 *
	 * @param request the create request
	 * @param actorNik the NIK of the creator (audit)
	 * @return the created dealer with {@code status="active"}
	 * @throws DealerServiceException with code {@code INVALID_KTP_NPWP} (422),
	 *         {@code DEALER_CODE_EXISTS} (409), or {@code PARENT_DEALER_NOT_FOUND} (422)
	 */
	@Transactional
	public Dealer createDealer(DealerCreateRequest request, String actorNik) {
		// KTP/NPWP format validation [LOCKED].
		validateKtpNpwp(request.ktpNo(), request.npwpNo());

		// dealer_code uniqueness → 409 DEALER_CODE_EXISTS.
		if (dealerRepository.findByDealerCode(request.dealerCode()).isPresent()) {
			throw new DealerServiceException("DEALER_CODE_EXISTS",
					"Dealer code '" + request.dealerCode() + "' already exists");
		}

		// EC7 fix: parent_dealer_code FK eksplisit (NOT via notes).
		if (request.parentDealerCode() != null && !request.parentDealerCode().isBlank()) {
			if (dealerRepository.findByDealerCode(request.parentDealerCode()).isEmpty()) {
				throw new DealerServiceException("PARENT_DEALER_NOT_FOUND",
						"Parent dealer '" + request.parentDealerCode() + "' not found");
			}
		}

		Instant now = Instant.now();
		Dealer dealer = new Dealer();
		dealer.setDealerCode(request.dealerCode());
		dealer.setDealerName(request.dealerName());
		dealer.setAuthorizedDealer(request.isAuthorizedDealer());
		dealer.setSellingNewProductOnly(request.isSellingNewProductOnly());
		dealer.setUsedCar(request.isUsedCar());
		dealer.setSubDealerEnabled(request.isSubDealerEnabled());
		dealer.setParentDealerCode(request.parentDealerCode());
		dealer.setGroupCode(request.groupCode());
		dealer.setMainDealerCode(request.mainDealerCode());
		dealer.setKtpNo(request.ktpNo());
		dealer.setKtpName(request.ktpName());
		dealer.setNpwpNo(request.npwpNo());
		dealer.setStatus(STATUS_ACTIVE);
		dealer.setActivationDate(request.activationDate() != null ? request.activationDate() : LocalDate.now());
		dealer.setNotes(request.notes());
		dealer.setCreatedAt(now);
		dealer.setCreatedBy(actorNik);
		return dealerRepository.save(dealer);
	}

	// ==================== E14 — Update dealer ====================

	/**
	 * E14 — Update a dealer. Legal-identity fields (KTP/NPWP) and {@code is_sub_dealer_enabled}
	 * changes are routed through maker-checker (BR-BE07-05 sensitive-field guard). Non-sensitive
	 * fields are applied directly.
	 *
	 * @param dealerCode the dealer business key
	 * @param request the update request
	 * @param actorNik the NIK of the maker
	 * @return the updated dealer (for non-sensitive fields), or a {@link MasterChangeRequest} with
	 *         {@code 202 pending_approval} (for sensitive fields)
	 * @throws DealerServiceException with code {@code DEALER_NOT_FOUND} (404) if the dealer does
	 *         not exist
	 */
	@Transactional
	public Object updateDealer(String dealerCode, DealerUpdateRequest request, String actorNik) {
		Dealer dealer = dealerRepository.findByDealerCode(dealerCode)
				.orElseThrow(() -> new DealerServiceException("DEALER_NOT_FOUND",
						"Dealer '" + dealerCode + "' not found"));

		boolean hasSensitiveChange = false;
		StringBuilder payloadBuilder = new StringBuilder("{");

		// KTP/NPWP/is_sub_dealer_enabled → maker-checker (sensitive).
		if (request.ktpNo() != null || request.ktpName() != null
				|| request.npwpNo() != null || request.isSubDealerEnabled() != null) {
			hasSensitiveChange = true;
			appendJsonField(payloadBuilder, "dealer_code", dealerCode, false);
			if (request.ktpNo() != null) {
				appendJsonField(payloadBuilder, "ktp_no", request.ktpNo(), true);
			}
			if (request.ktpName() != null) {
				appendJsonField(payloadBuilder, "ktp_name", request.ktpName(), true);
			}
			if (request.npwpNo() != null) {
				appendJsonField(payloadBuilder, "npwp_no", request.npwpNo(), true);
			}
			if (request.isSubDealerEnabled() != null) {
				appendJsonField(payloadBuilder, "is_sub_dealer_enabled",
						request.isSubDealerEnabled().toString(), true);
			}
		}

		// Non-sensitive fields applied directly.
		if (request.dealerName() != null) {
			dealer.setDealerName(request.dealerName());
		}
		if (request.notes() != null) {
			dealer.setNotes(request.notes());
		}
		dealer.setUpdatedAt(Instant.now());
		dealer.setUpdatedBy(actorNik);

		if (hasSensitiveChange) {
			// Route sensitive fields through maker-checker → 202 pending_approval.
			payloadBuilder.append("}");
			return makerCheckerService.submit(
					RESOURCE_DEALER, Action.update, payloadBuilder.toString(), actorNik);
		}
		return dealerRepository.save(dealer);
	}

	// ==================== E15 — Lifecycle ====================

	/**
	 * E15 — Deactivate a dealer (BR-BE07-03 deactivate-only, no hard delete).
	 *
	 * @param dealerCode the dealer business key
	 * @param actorNik the NIK of the actor
	 * @return the deactivated dealer
	 * @throws DealerServiceException with code {@code DEALER_NOT_FOUND} (404)
	 */
	@Transactional
	public Dealer deactivateDealer(String dealerCode, String actorNik) {
		Dealer dealer = requireDealer(dealerCode);
		dealer.setStatus(STATUS_INACTIVE);
		dealer.setDeactivationDate(LocalDate.now());
		dealer.setUpdatedAt(Instant.now());
		dealer.setUpdatedBy(actorNik);
		return dealerRepository.save(dealer);
	}

	/**
	 * E15 — Reactivate a previously-deactivated dealer.
	 *
	 * @param dealerCode the dealer business key
	 * @param actorNik the NIK of the actor
	 * @return the reactivated dealer
	 * @throws DealerServiceException with code {@code DEALER_NOT_FOUND} (404)
	 */
	@Transactional
	public Dealer reactivateDealer(String dealerCode, String actorNik) {
		Dealer dealer = requireDealer(dealerCode);
		dealer.setStatus(STATUS_ACTIVE);
		dealer.setDeactivationDate(null);
		dealer.setActivationDate(LocalDate.now());
		dealer.setUpdatedAt(Instant.now());
		dealer.setUpdatedBy(actorNik);
		return dealerRepository.save(dealer);
	}

	// ==================== E16 — Personnel CRUD ====================

	/**
	 * E16 — List personnel for a dealer. By default only active ({@code status='A'}) personnel are
	 * returned (BR-DLRPTN-1 eligibility filter).
	 *
	 * @param dealerCode the dealer business key
	 * @param includeInactive if true, include inactive personnel
	 * @return list of personnel
	 */
	@Transactional(readOnly = true)
	public List<DealerPersonnel> listPersonnel(String dealerCode, boolean includeInactive) {
		if (includeInactive) {
			return personnelRepository.findByDealerCode(dealerCode);
		}
		return personnelRepository.findByDealerCodeAndStatus(dealerCode, PERSONNEL_STATUS_A);
	}

	/**
	 * E16 — Create a new personnel record for a dealer.
	 *
	 * @param dealerCode the dealer business key
	 * @param request the personnel request
	 * @param actorNik the NIK of the creator
	 * @return the created personnel
	 * @throws DealerServiceException with code {@code DEALER_NOT_FOUND} (404)
	 */
	@Transactional
	public DealerPersonnel createPersonnel(String dealerCode, PersonnelRequest request, String actorNik) {
		requireDealer(dealerCode);
		Instant now = Instant.now();
		DealerPersonnel personnel = new DealerPersonnel();
		personnel.setPersonnelId(request.personnelId());
		personnel.setDealerCode(dealerCode);
		personnel.setName(request.name());
		personnel.setJobTitleId(request.jobTitleId());
		personnel.setStatus(PERSONNEL_STATUS_A);
		personnel.setBankReferenceId(request.bankReferenceId());
		personnel.setCreatedAt(now);
		personnel.setCreatedBy(actorNik);
		return personnelRepository.save(personnel);
	}

	/**
	 * E16 — Update a personnel record.
	 *
	 * @param personnelId the personnel business key
	 * @param request the update request
	 * @param actorNik the NIK of the actor
	 * @return the updated personnel
	 * @throws DealerServiceException with code {@code PERSONNEL_NOT_FOUND} (404)
	 */
	@Transactional
	public DealerPersonnel updatePersonnel(String personnelId, PersonnelRequest request, String actorNik) {
		DealerPersonnel personnel = personnelRepository.findByPersonnelId(personnelId)
				.orElseThrow(() -> new DealerServiceException("PERSONNEL_NOT_FOUND",
						"Personnel '" + personnelId + "' not found"));
		if (request.name() != null) {
			personnel.setName(request.name());
		}
		if (request.jobTitleId() != null) {
			personnel.setJobTitleId(request.jobTitleId());
		}
		if (request.bankReferenceId() != null) {
			personnel.setBankReferenceId(request.bankReferenceId());
		}
		if (request.status() != null) {
			personnel.setStatus(request.status());
		}
		personnel.setUpdatedAt(Instant.now());
		personnel.setUpdatedBy(actorNik);
		return personnelRepository.save(personnel);
	}

	// ==================== E17 — Job-title CRUD ====================

	/**
	 * E17 — List all job titles.
	 *
	 * @return list of all job titles
	 */
	@Transactional(readOnly = true)
	public List<DealerJobTitle> listJobTitles() {
		return jobTitleRepository.findAll();
	}

	/**
	 * E17 — Create a new job title.
	 *
	 * @param jobTitleId the business key
	 * @param description the description
	 * @param dealerPaymentCode the payment-scheme code
	 * @param actorNik the NIK of the creator
	 * @return the created job title
	 */
	@Transactional
	public DealerJobTitle createJobTitle(
			String jobTitleId, String description, String dealerPaymentCode, String actorNik) {
		Instant now = Instant.now();
		DealerJobTitle jobTitle = new DealerJobTitle();
		jobTitle.setJobTitleId(jobTitleId);
		jobTitle.setDescription(description);
		jobTitle.setDealerPaymentCode(dealerPaymentCode);
		jobTitle.setActive(true);
		jobTitle.setCreatedAt(now);
		jobTitle.setCreatedBy(actorNik);
		return jobTitleRepository.save(jobTitle);
	}

	/**
	 * E17 — Update a job title.
	 *
	 * @param jobTitleId the business key
	 * @param description the new description (nullable)
	 * @param dealerPaymentCode the new payment code (nullable)
	 * @param isActive the new active flag (nullable)
	 * @param actorNik the NIK of the actor
	 * @return the updated job title
	 * @throws DealerServiceException with code {@code JOB_TITLE_NOT_FOUND} (404)
	 */
	@Transactional
	public DealerJobTitle updateJobTitle(
			String jobTitleId, String description, String dealerPaymentCode,
			Boolean isActive, String actorNik) {
		DealerJobTitle jobTitle = jobTitleRepository.findByJobTitleId(jobTitleId)
				.orElseThrow(() -> new DealerServiceException("JOB_TITLE_NOT_FOUND",
						"Job title '" + jobTitleId + "' not found"));
		if (description != null) {
			jobTitle.setDescription(description);
		}
		if (dealerPaymentCode != null) {
			jobTitle.setDealerPaymentCode(dealerPaymentCode);
		}
		if (isActive != null) {
			jobTitle.setActive(isActive);
		}
		jobTitle.setUpdatedAt(Instant.now());
		jobTitle.setUpdatedBy(actorNik);
		return jobTitleRepository.save(jobTitle);
	}

	// ==================== E18 — Bank-reference (maker-checker WAJIB) ====================

	/**
	 * E18 — List bank references for a dealer.
	 *
	 * @param dealerCode the dealer business key
	 * @return list of bank references
	 */
	@Transactional(readOnly = true)
	public List<DealerBankReference> listBankReferences(String dealerCode) {
		return bankReferenceRepository.findByDealerCode(dealerCode);
	}

	/**
	 * E18 — Submit a bank-reference create via maker-checker (BR-BE07-05 — ALL writes via
	 * maker-checker). Returns a {@link MasterChangeRequest} with {@code pending_approval} status;
	 * the record is NOT created until a checker approves.
	 *
	 * @param dealerCode the dealer business key
	 * @param payload the bank-reference field payload (JSON string)
	 * @param makerNik the NIK of the maker
	 * @return the change-request with {@code 202 pending_approval}
	 * @throws DealerServiceException with code {@code DEALER_NOT_FOUND} (404)
	 */
	@Transactional
	public MasterChangeRequest submitBankReferenceCreate(
			String dealerCode, String payload, String makerNik) {
		requireDealer(dealerCode);
		return makerCheckerService.submit(
				RESOURCE_DEALER_BANK_REFERENCE, Action.create, payload, makerNik);
	}

	/**
	 * E18 — Submit a bank-reference update via maker-checker.
	 *
	 * @param dealerCode the dealer business key
	 * @param bankReferenceId the bank-reference business key
	 * @param payload the update payload (JSON string)
	 * @param makerNik the NIK of the maker
	 * @return the change-request with {@code 202 pending_approval}
	 * @throws DealerServiceException with code {@code DEALER_NOT_FOUND} or
	 *         {@code BANK_REFERENCE_NOT_FOUND} (404)
	 */
	@Transactional
	public MasterChangeRequest submitBankReferenceUpdate(
			String dealerCode, String bankReferenceId, String payload, String makerNik) {
		requireDealer(dealerCode);
		bankReferenceRepository
				.findByDealerCodeAndBankReferenceId(dealerCode, bankReferenceId)
				.orElseThrow(() -> new DealerServiceException("BANK_REFERENCE_NOT_FOUND",
						"Bank reference '" + bankReferenceId + "' not found for dealer '"
								+ dealerCode + "'"));
		return makerCheckerService.submit(
				RESOURCE_DEALER_BANK_REFERENCE, Action.update, payload, makerNik);
	}

	/**
	 * E18 — Submit a bank-reference deactivate via maker-checker.
	 *
	 * @param dealerCode the dealer business key
	 * @param bankReferenceId the bank-reference business key
	 * @param makerNik the NIK of the maker
	 * @return the change-request with {@code 202 pending_approval}
	 * @throws DealerServiceException with code {@code BANK_REFERENCE_NOT_FOUND} (404)
	 */
	@Transactional
	public MasterChangeRequest submitBankReferenceDeactivate(
			String dealerCode, String bankReferenceId, String makerNik) {
		requireDealer(dealerCode);
		bankReferenceRepository
				.findByDealerCodeAndBankReferenceId(dealerCode, bankReferenceId)
				.orElseThrow(() -> new DealerServiceException("BANK_REFERENCE_NOT_FOUND",
						"Bank reference '" + bankReferenceId + "' not found for dealer '"
								+ dealerCode + "'"));
		return makerCheckerService.submit(
				RESOURCE_DEALER_BANK_REFERENCE, Action.deactivate, "{}", makerNik);
	}

	// ==================== E19 — Branch-access (replace-set) ====================

	/**
	 * E19 — Get the branch-access set for a dealer.
	 *
	 * @param dealerCode the dealer business key
	 * @return list of branch-access rows
	 */
	@Transactional(readOnly = true)
	public List<DealerBranchAccess> listBranchAccess(String dealerCode) {
		return branchAccessRepository.findByDealerCode(dealerCode);
	}

	/**
	 * E19 — Replace the branch-access set atomically (BR-BE07-07). All existing active rows are
	 * deactivated, and new active rows are created for the requested branch IDs. This is a
	 * replace-set operation: the final set of active branches equals exactly the request.
	 *
	 * @param dealerCode the dealer business key
	 * @param request the branch-access request (set of branch IDs)
	 * @param actorNik the NIK of the actor
	 * @return the new set of active branch-access rows
	 * @throws DealerServiceException with code {@code DEALER_NOT_FOUND} (404)
	 */
	@Transactional
	public List<DealerBranchAccess> replaceBranchAccess(
			String dealerCode, BranchAccessRequest request, String actorNik) {
		requireDealer(dealerCode);
		Instant now = Instant.now();

		// Deactivate all existing active rows for this dealer.
		List<DealerBranchAccess> existing = branchAccessRepository
				.findByDealerCodeAndIsActiveTrue(dealerCode);
		for (DealerBranchAccess access : existing) {
			access.setActive(false);
			access.setUpdatedAt(now);
			access.setUpdatedBy(actorNik);
			branchAccessRepository.save(access);
		}

		// Create new active rows for the requested branch IDs.
		List<DealerBranchAccess> newRows = new ArrayList<>();
		if (request != null && request.branchIds() != null) {
			for (String branchId : request.branchIds()) {
				DealerBranchAccess access = new DealerBranchAccess();
				access.setDealerCode(dealerCode);
				access.setBranchId(branchId);
				access.setActive(true);
				access.setCreatedAt(now);
				access.setCreatedBy(actorNik);
				newRows.add(branchAccessRepository.save(access));
			}
		}
		return newRows;
	}

	// ==================== E20 — Documents ====================

	/**
	 * E20 — List documents for a dealer.
	 *
	 * @param dealerCode the dealer business key
	 * @return list of documents ordered by upload time descending
	 */
	@Transactional(readOnly = true)
	public List<DealerDocument> listDocuments(String dealerCode) {
		return documentRepository.findByDealerCodeOrderByUploadedAtDesc(dealerCode);
	}

	/**
	 * E20 — Upload (register) a document for a dealer. The {@code fileRef} is an object-storage
	 * key, NOT an FTP path (ARTIFACT discard per data-model §Dealer master family).
	 *
	 * @param dealerCode the dealer business key
	 * @param docType the document type (e.g. {@code SIUP}, {@code NPWP}, {@code KTP})
	 * @param fileRef the object-storage key
	 * @param actorNik the NIK of the uploader
	 * @return the created document
	 * @throws DealerServiceException with code {@code DEALER_NOT_FOUND} (404)
	 */
	@Transactional
	public DealerDocument createDocument(
			String dealerCode, String docType, String fileRef, String actorNik) {
		requireDealer(dealerCode);
		Instant now = Instant.now();
		DealerDocument doc = new DealerDocument();
		doc.setDealerCode(dealerCode);
		doc.setDocType(docType);
		doc.setFileRef(fileRef);
		doc.setUploadedBy(actorNik);
		doc.setUploadedAt(now);
		doc.setCreatedAt(now);
		doc.setCreatedBy(actorNik);
		return documentRepository.save(doc);
	}

	// ==================== E21 — Payment-eligible-contacts ====================

	/**
	 * E21 — Resolve payment-eligible contacts for a dealer (BR-BE07-10 / BR-DLRPTN-1).
	 *
	 * <p>A contact is eligible for payment ONLY if ALL three links in the chain are active
	 * simultaneously:
	 * <ol>
	 *   <li>The <strong>job-title</strong> is active ({@code is_active=true}).</li>
	 *   <li>The <strong>personnel</strong> is active ({@code status='A'}).</li>
	 *   <li>The <strong>bank-reference</strong> is active ({@code status='A'}).</li>
	 * </ol>
	 * If any link is inactive, the contact drops out of the eligible set (no data is deleted —
	 * the record simply does not appear in the result).
	 *
	 * <p>When {@code jobTitleId} is provided, only personnel holding that job-title are considered.
	 *
	 * @param dealerCode the dealer business key
	 * @param jobTitleId optional job-title filter
	 * @return list of eligible payment contacts
	 * @throws DealerServiceException with code {@code DEALER_NOT_FOUND} (404)
	 */
	@Transactional(readOnly = true)
	public List<PaymentEligibleContact> getPaymentEligibleContacts(String dealerCode, String jobTitleId) {
		requireDealer(dealerCode);

		// Step 1: load all personnel for this dealer (we need both active and inactive to
		// understand the full chain, but only active ones will be eligible).
		List<DealerPersonnel> allPersonnel = personnelRepository.findByDealerCode(dealerCode);

		// Step 2: filter by job-title if specified.
		if (jobTitleId != null && !jobTitleId.isBlank()) {
			allPersonnel = allPersonnel.stream()
					.filter(p -> jobTitleId.equals(p.getJobTitleId()))
					.collect(Collectors.toList());
		}

		List<PaymentEligibleContact> eligible = new ArrayList<>();
		for (DealerPersonnel personnel : allPersonnel) {
			// Personnel must be active (status='A').
			if (!PERSONNEL_STATUS_A.equals(personnel.getStatus())) {
				continue;
			}

			// Job-title must exist and be active.
			if (personnel.getJobTitleId() == null) {
				continue;
			}
			Optional<DealerJobTitle> jobTitle = jobTitleRepository
					.findByJobTitleId(personnel.getJobTitleId());
			if (jobTitle.isEmpty() || !jobTitle.get().isActive()) {
				continue;
			}

			// Bank-reference must exist and be active (status='A').
			if (personnel.getBankReferenceId() == null) {
				continue;
			}
			Optional<DealerBankReference> bankRef = bankReferenceRepository
					.findByDealerCodeAndBankReferenceId(dealerCode, personnel.getBankReferenceId());
			if (bankRef.isEmpty() || !PERSONNEL_STATUS_A.equals(bankRef.get().getStatus())) {
				continue;
			}

			// All three links active → eligible.
			DealerBankReference ref = bankRef.get();
			eligible.add(new PaymentEligibleContact(
					personnel.getPersonnelId(),
					personnel.getName(),
					personnel.getJobTitleId(),
					jobTitle.get().getDescription(),
					jobTitle.get().getDealerPaymentCode(),
					ref.getBankReferenceId(),
					ref.getBankId(),
					ref.getAccountNumber(),
					ref.getAccountName()));
		}
		return eligible;
	}

	// ==================== Helpers ====================

	/**
	 * Resolve a dealer by business key or throw 404.
	 *
	 * @param dealerCode the dealer business key
	 * @return the dealer
	 * @throws DealerServiceException with code {@code DEALER_NOT_FOUND} (404)
	 */
	private Dealer requireDealer(String dealerCode) {
		return dealerRepository.findByDealerCode(dealerCode)
				.orElseThrow(() -> new DealerServiceException("DEALER_NOT_FOUND",
						"Dealer '" + dealerCode + "' not found"));
	}

	/**
	 * Validate KTP/NPWP format ({@code [LOCKED]} — 422 on bad format).
	 *
	 * <p>KTP must be a 16-digit numeric string. NPWP must match the Indonesian NPWP format
	 * {@code XX.XXX.XXX.X-XXX.XXX} (15 digits + dots/dashes).
	 *
	 * @param ktpNo the KTP number
	 * @param npwpNo the NPWP number
	 * @throws DealerServiceException with code {@code INVALID_KTP_NPWP} (422)
	 */
	static void validateKtpNpwp(String ktpNo, String npwpNo) {
		if (ktpNo == null || !ktpNo.matches("\\d{16}")) {
			throw new DealerServiceException("INVALID_KTP_NPWP",
					"KTP number must be exactly 16 digits");
		}
		if (npwpNo == null || !npwpNo.matches("\\d{2}\\.\\d{3}\\.\\d{3}\\.\\d{1}-\\d{3}\\.\\d{3}")) {
			throw new DealerServiceException("INVALID_KTP_NPWP",
					"NPWP number must match format XX.XXX.XXX.X-XXX.XXX");
		}
	}

	/**
	 * Append a JSON key-value pair to a {@link StringBuilder} building a JSON object. The value
	 * is always quoted as a string. Handles the comma separator between fields.
	 *
	 * @param sb       the string builder
	 * @param key      the JSON key
	 * @param value    the JSON value (quoted as string)
	 * @param prependComma if true, prepend a comma before this field
	 */
	private static void appendJsonField(StringBuilder sb, String key, String value,
			boolean prependComma) {
		if (prependComma) {
			sb.append(",");
		}
		sb.append("\"").append(key).append("\":\"").append(value).append("\"");
	}

	/**
	 * Domain exception for dealer-service business-rule violations. The controller maps the
	 * {@code code} to the appropriate HTTP status.
	 */
	public static class DealerServiceException extends RuntimeException {

		private final String code;

		/**
		 * Constructs the exception with an error code and message.
		 *
		 * @param code the error code (e.g. {@code DEALER_CODE_EXISTS}, {@code PARENT_DEALER_NOT_FOUND})
		 * @param message the human-readable message
		 */
		public DealerServiceException(String code, String message) {
			super(message);
			this.code = code;
		}

		/**
		 * @return the error code for HTTP status mapping
		 */
		public String getCode() {
			return code;
		}
	}

}
// SDD-PROVENANCE: U-006 | vault: .mega-sdd/vaults/acquisition-master-data | DealerService — E12-E21 business logic; E12 branch-scoped picker (BR-BE07-07); E13 KTP/NPWP validate + PARENT_DEALER_NOT_FOUND FK eksplisit (EC7 fix); E14 sensitive→maker-checker; E18 ALL bank-ref writes via MakerCheckerService.submit→202; E19 replace-set; E21 join job-title→personnel→bank-ref all active simultan (BR-BE07-10/BR-DLRPTN-1)
