package com.coresystem.coresystembackend.masterdata.dealer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.coresystem.coresystembackend.masterdata.common.PageResponse;
import com.coresystem.coresystembackend.masterdata.dealer.DealerController.BankReferenceRequest;
import com.coresystem.coresystembackend.masterdata.dealer.DealerController.BranchAccessRequest;
import com.coresystem.coresystembackend.masterdata.dealer.DealerController.ChangeRequestSummary;
import com.coresystem.coresystembackend.masterdata.dealer.DealerController.DealerCreateRequest;
import com.coresystem.coresystembackend.masterdata.dealer.DealerController.DealerUpdateRequest;
import com.coresystem.coresystembackend.masterdata.dealer.DealerController.ErrorResponse;
import com.coresystem.coresystembackend.masterdata.dealer.DealerController.PaymentEligibleContact;
import com.coresystem.coresystembackend.masterdata.dealer.DealerService.DealerServiceException;
import com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest;
import com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest.Action;
import com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest.Status;
import com.coresystem.coresystembackend.masterdata.makercheck.MakerCheckerService;

/**
 * TDD test for U-006 — Dealer bank-reference + controller E12-E21 + payment-eligible-contacts.
 *
 * <p>This is a pure unit test (no Spring context, no datasource) that verifies the acceptance
 * criteria AC-6 through AC-9:
 * <ul>
 *   <li><strong>AC-6</strong> — E12 branch-scoped picker: only dealers with active
 *       {@code DEALER_BRANCH_ACCESS} for the given {@code branch_id} appear (BR-BE07-07).</li>
 *   <li><strong>AC-7</strong> — E13 {@code 422 PARENT_DEALER_NOT_FOUND}: FK eksplisit validated
 *       against {@code mst_dealer.dealer_code}, NOT via {@code notes} (EC7 fix).</li>
 *   <li><strong>AC-8</strong> — E18 bank-ref write → {@code 202 pending_approval} (NOT 201);
 *       self-approve → {@code 403 SELF_APPROVAL_BLOCKED} (D-01 S11).</li>
 *   <li><strong>AC-9</strong> — E21 payment-eligible-contacts: a contact is eligible only if ALL
 *       three links (job-title, personnel, bank-ref) are active simultaneously (BR-BE07-10).</li>
 * </ul>
 *
 * <p>The controller is instantiated directly with mocked repositories + a real
 * {@link DealerService} + a real {@link MakerCheckerService} (backed by a mock repository), so the
 * full business-logic chain is exercised end-to-end without a Spring context or a real database.
 *
 * <p>Entity-mapping contracts for {@link DealerBankReference} are also verified via reflection
 * ([LOCKED] payout zero-diff {@code @Column} mapping).
 */
class DealerControllerTest {

	private DealerRepository dealerRepository;
	private DealerDocumentRepository documentRepository;
	private DealerPersonnelRepository personnelRepository;
	private DealerJobTitleRepository jobTitleRepository;
	private DealerBranchAccessRepository branchAccessRepository;
	private DealerBankReferenceRepository bankReferenceRepository;
	private com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequestRepository mcrRepository;

	private DealerService dealerService;
	private MakerCheckerService makerCheckerService;
	private DealerController controller;

	private final AtomicLong mcrIdGenerator = new AtomicLong(1);
	private final java.util.Map<Long, MasterChangeRequest> mcrStore =
			new java.util.concurrent.ConcurrentHashMap<>();

	@BeforeEach
	void setUp() {
		dealerRepository = Mockito.mock(DealerRepository.class);
		documentRepository = Mockito.mock(DealerDocumentRepository.class);
		personnelRepository = Mockito.mock(DealerPersonnelRepository.class);
		jobTitleRepository = Mockito.mock(DealerJobTitleRepository.class);
		branchAccessRepository = Mockito.mock(DealerBranchAccessRepository.class);
		bankReferenceRepository = Mockito.mock(DealerBankReferenceRepository.class);
		mcrRepository = Mockito.mock(
				com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequestRepository.class);

		mcrStore.clear();
		mcrIdGenerator.set(1);

		// Mock MCR repository: save assigns ID, stores in map.
		when(mcrRepository.save(any(MasterChangeRequest.class))).thenAnswer(inv -> {
			MasterChangeRequest req = inv.getArgument(0);
			if (req.getId() == null) {
				reflectiveSetId(req, mcrIdGenerator.getAndIncrement());
			}
			mcrStore.put(req.getId(), req);
			return req;
		});
		when(mcrRepository.findById(any())).thenAnswer(inv -> {
			Long id = inv.getArgument(0);
			return Optional.ofNullable(mcrStore.get(id));
		});

		makerCheckerService = new MakerCheckerService(mcrRepository);
		dealerService = new DealerService(
				dealerRepository, documentRepository, personnelRepository,
				jobTitleRepository, branchAccessRepository, bankReferenceRepository,
				makerCheckerService);
		controller = new DealerController(dealerService);
	}

	// ==================== AC-6 — Branch-scoped picker (BR-BE07-07) ====================

	@Test
	void ac6_branchFilter_onlyDealersWithActiveBranchAccess() {
		// Two dealers: DLR-0451 has access to branch 0101, DLR-0999 has access to 0102.
		Dealer d1 = makeDealer("DLR-0451", "Dealer 1", true);
		Dealer d2 = makeDealer("DLR-0999", "Dealer 2", false);

		DealerBranchAccess access1 = makeBranchAccess("DLR-0451", "0101", true);
		DealerBranchAccess access2 = makeBranchAccess("DLR-0999", "0102", true);

		// branch_id=0101 → only DLR-0451
		when(branchAccessRepository.findByBranchIdAndIsActiveTrue("0101"))
				.thenReturn(List.of(access1));
		when(dealerRepository.findByDealerCode("DLR-0451")).thenReturn(Optional.of(d1));

		PageResponse<Dealer> result1 = controller.listDealers("0101", null, null, 0, 20);

		assertThat(result1.items()).hasSize(1);
		assertThat(result1.items().get(0).getDealerCode()).isEqualTo("DLR-0451");

		// branch_id=0102 → only DLR-0999
		when(branchAccessRepository.findByBranchIdAndIsActiveTrue("0102"))
				.thenReturn(List.of(access2));
		when(dealerRepository.findByDealerCode("DLR-0999")).thenReturn(Optional.of(d2));

		PageResponse<Dealer> result2 = controller.listDealers("0102", null, null, 0, 20);

		assertThat(result2.items()).hasSize(1);
		assertThat(result2.items().get(0).getDealerCode()).isEqualTo("DLR-0999");
	}

	@Test
	void ac6_branchFilter_excludesDealerWithInactiveBranchAccess() {
		// DLR-0451 has an INACTIVE access row for branch 0101 — should NOT appear.
		when(branchAccessRepository.findByBranchIdAndIsActiveTrue("0101"))
				.thenReturn(List.of()); // no active access rows for 0101

		PageResponse<Dealer> result = controller.listDealers("0101", null, null, 0, 20);

		assertThat(result.items()).isEmpty();
	}

	// ==================== AC-7 — PARENT_DEALER_NOT_FOUND (EC7 fix) ====================

	@Test
	void ac7_createWithNonExistentParent_throws422ParentDealerNotFound() {
		DealerCreateRequest request = new DealerCreateRequest(
				"DLR-NEW", "PT New Dealer", true, false, true, false,
				"DLR-NONEXISTENT", // parent that does not exist
				null, null,
				"3275123456789012", // valid 16-digit KTP
				"Andi Wijaya",
				"09.123.456.7-890.123", // valid NPWP format
				LocalDate.now(), "notes", "NIK001");

		// dealer_code does not exist yet (new), but parent does not exist either.
		when(dealerRepository.findByDealerCode("DLR-NEW")).thenReturn(Optional.empty());
		when(dealerRepository.findByDealerCode("DLR-NONEXISTENT")).thenReturn(Optional.empty());

		ResponseEntity<?> response = controller.createDealer(request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
		ErrorResponse body = (ErrorResponse) response.getBody();
		assertThat(body.code()).isEqualTo("PARENT_DEALER_NOT_FOUND");
	}

	@Test
	void ac7_createWithExistingParent_succeeds() {
		Dealer parent = makeDealer("DLR-PARENT", "Parent Dealer", true);
		when(dealerRepository.findByDealerCode("DLR-PARENT")).thenReturn(Optional.of(parent));
		when(dealerRepository.findByDealerCode("DLR-NEW")).thenReturn(Optional.empty());
		when(dealerRepository.save(any(Dealer.class))).thenAnswer(inv -> inv.getArgument(0));

		DealerCreateRequest request = new DealerCreateRequest(
				"DLR-NEW", "PT New Dealer", true, false, true, false,
				"DLR-PARENT", null, null,
				"3275123456789012", "Andi Wijaya",
				"09.123.456.7-890.123",
				LocalDate.now(), "notes", "NIK001");

		ResponseEntity<?> response = controller.createDealer(request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		Dealer body = (Dealer) response.getBody();
		assertThat(body.getDealerCode()).isEqualTo("DLR-NEW");
		assertThat(body.getStatus()).isEqualTo("active");
		assertThat(body.getParentDealerCode()).isEqualTo("DLR-PARENT");
	}

	@Test
	void ac7_createWithDuplicateCode_throws409DealerCodeExists() {
		Dealer existing = makeDealer("DLR-EXISTING", "Existing", true);
		when(dealerRepository.findByDealerCode("DLR-EXISTING")).thenReturn(Optional.of(existing));

		DealerCreateRequest request = new DealerCreateRequest(
				"DLR-EXISTING", "PT Duplicate", true, false, true, false,
				null, null, null,
				"3275123456789012", "Andi", "09.123.456.7-890.123",
				LocalDate.now(), "", "NIK001");

		ResponseEntity<?> response = controller.createDealer(request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		ErrorResponse body = (ErrorResponse) response.getBody();
		assertThat(body.code()).isEqualTo("DEALER_CODE_EXISTS");
	}

	@Test
	void ac7_createWithInvalidKtp_throws422InvalidKtpNpwp() {
		DealerCreateRequest request = new DealerCreateRequest(
				"DLR-NEW2", "PT Bad KTP", true, false, true, false,
				null, null, null,
				"123", // invalid KTP (not 16 digits)
				"Andi", "09.123.456.7-890.123",
				LocalDate.now(), "", "NIK001");

		ResponseEntity<?> response = controller.createDealer(request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
		ErrorResponse body = (ErrorResponse) response.getBody();
		assertThat(body.code()).isEqualTo("INVALID_KTP_NPWP");
	}

	// ==================== AC-8 — E18 → 202 pending_approval; self-approve → 403 ====================

	@Test
	void ac8_createBankReference_returns202PendingApproval() {
		Dealer dealer = makeDealer("DLR-0451", "PT Maju Motor", true);
		when(dealerRepository.findByDealerCode("DLR-0451")).thenReturn(Optional.of(dealer));

		BankReferenceRequest request = new BankReferenceRequest(
				"BR-001", "OPR", "Operating Account", "000122",
				"1234567890", "PT Maju Motor", false,
				LocalDate.of(2026, 7, 15), "NIK-MAKER");

		ResponseEntity<?> response = controller.createBankReference("DLR-0451", request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED); // 202, NOT 201
		ChangeRequestSummary body = (ChangeRequestSummary) response.getBody();
		assertThat(body.status()).isEqualTo("pending_approval");
		assertThat(body.resource()).isEqualTo("dealer-bank-reference");
		assertThat(body.action()).isEqualTo("create");
		assertThat(body.maker()).isEqualTo("NIK-MAKER");
		assertThat(body.changeRequestId()).isNotNull();
	}

	@Test
	void ac8_bankReferenceNotEligibleUntilCheckerApproves() {
		// The 202 response means the bank-reference is NOT yet created/eligible.
		// Verify via the change-request being in pending_approval state.
		Dealer dealer = makeDealer("DLR-0451", "PT Maju Motor", true);
		when(dealerRepository.findByDealerCode("DLR-0451")).thenReturn(Optional.of(dealer));

		BankReferenceRequest request = new BankReferenceRequest(
				"BR-001", "OPR", null, "000122",
				"1234567890", "PT Maju Motor", false,
				LocalDate.of(2026, 7, 15), "NIK-MAKER");

		ResponseEntity<?> response = controller.createBankReference("DLR-0451", request);
		ChangeRequestSummary body = (ChangeRequestSummary) response.getBody();

		// The change-request is pending — the actual bank reference does NOT exist yet.
		assertThat(body.status()).isEqualTo("pending_approval");

		// Verify the bank-reference repository has NOT been called to save.
		Mockito.verify(bankReferenceRepository, Mockito.never()).save(any(DealerBankReference.class));
	}

	@Test
	void ac8_selfApprove_throws403SelfApprovalBlocked() {
		// Submit a bank-reference create as maker NIK-MAKER.
		Dealer dealer = makeDealer("DLR-0451", "PT Maju Motor", true);
		when(dealerRepository.findByDealerCode("DLR-0451")).thenReturn(Optional.of(dealer));

		BankReferenceRequest request = new BankReferenceRequest(
				"BR-001", "OPR", null, "000122",
				"1234567890", "PT Maju Motor", false,
				LocalDate.of(2026, 7, 15), "NIK-MAKER");

		ResponseEntity<?> response = controller.createBankReference("DLR-0451", request);
		ChangeRequestSummary body = (ChangeRequestSummary) response.getBody();
		Long mcrId = body.changeRequestId();

		// Self-approve: same NIK as maker → 403 SELF_APPROVAL_BLOCKED.
		assertThatThrownBy(() -> makerCheckerService.approve(mcrId, "NIK-MAKER", "self"))
				.isInstanceOf(MakerCheckerService.SelfApprovalBlockedException.class)
				.hasMessageContaining("SELF_APPROVAL_BLOCKED");
	}

	@Test
	void ac8_approveByDifferentChecker_appliesChangeRequest() {
		Dealer dealer = makeDealer("DLR-0451", "PT Maju Motor", true);
		when(dealerRepository.findByDealerCode("DLR-0451")).thenReturn(Optional.of(dealer));

		BankReferenceRequest request = new BankReferenceRequest(
				"BR-001", "OPR", null, "000122",
				"1234567890", "PT Maju Motor", false,
				LocalDate.of(2026, 7, 15), "NIK-MAKER");

		ResponseEntity<?> response = controller.createBankReference("DLR-0451", request);
		ChangeRequestSummary body = (ChangeRequestSummary) response.getBody();
		Long mcrId = body.changeRequestId();

		// Different checker approves → applied.
		MasterChangeRequest approved = makerCheckerService.approve(mcrId, "NIK-CHECKER", "OK");
		assertThat(approved.getStatus()).isEqualTo(Status.applied);
		assertThat(approved.getCheckerNik()).isEqualTo("NIK-CHECKER");
	}

	// ==================== AC-9 — E21 payment-eligible-contacts ====================

	@Test
	void ac9_allActive_contactIsEligible() {
		Dealer dealer = makeDealer("DLR-0451", "PT Maju Motor", true);
		when(dealerRepository.findByDealerCode("DLR-0451")).thenReturn(Optional.of(dealer));

		// Active job-title.
		DealerJobTitle jt = makeJobTitle("JT-02", "Branch Manager", "PMT-01", true);
		when(jobTitleRepository.findByJobTitleId("JT-02")).thenReturn(Optional.of(jt));

		// Active personnel with the job-title and a bank-reference.
		DealerPersonnel personnel = makePersonnel("P-001", "DLR-0451", "Andi", "JT-02",
				"A", "BR-001");
		when(personnelRepository.findByDealerCode("DLR-0451")).thenReturn(List.of(personnel));

		// Active bank-reference.
		DealerBankReference bankRef = makeBankReference("DLR-0451", "BR-001",
				"1234567890", "PT Maju Motor", "A");
		when(bankReferenceRepository.findByDealerCodeAndBankReferenceId("DLR-0451", "BR-001"))
				.thenReturn(Optional.of(bankRef));

		ResponseEntity<?> response = controller.getPaymentEligibleContacts("DLR-0451", "JT-02");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		@SuppressWarnings("unchecked")
		List<PaymentEligibleContact> contacts = (List<PaymentEligibleContact>) response.getBody();
		assertThat(contacts).hasSize(1);
		assertThat(contacts.get(0).personnelId()).isEqualTo("P-001");
		assertThat(contacts.get(0).personnelName()).isEqualTo("Andi");
		assertThat(contacts.get(0).jobTitleId()).isEqualTo("JT-02");
		assertThat(contacts.get(0).bankReferenceId()).isEqualTo("BR-001");
		assertThat(contacts.get(0).accountNumber()).isEqualTo("1234567890");
	}

	@Test
	void ac9_bankRefInactive_contactNotEligible() {
		Dealer dealer = makeDealer("DLR-0451", "PT Maju Motor", true);
		when(dealerRepository.findByDealerCode("DLR-0451")).thenReturn(Optional.of(dealer));

		DealerJobTitle jt = makeJobTitle("JT-02", "Branch Manager", "PMT-01", true);
		when(jobTitleRepository.findByJobTitleId("JT-02")).thenReturn(Optional.of(jt));

		// Active personnel.
		DealerPersonnel personnel = makePersonnel("P-001", "DLR-0451", "Andi", "JT-02",
				"A", "BR-001");
		when(personnelRepository.findByDealerCode("DLR-0451")).thenReturn(List.of(personnel));

		// INACTIVE bank-reference → contact should NOT be eligible.
		DealerBankReference bankRef = makeBankReference("DLR-0451", "BR-001",
				"1234567890", "PT Maju Motor", "inactive");
		when(bankReferenceRepository.findByDealerCodeAndBankReferenceId("DLR-0451", "BR-001"))
				.thenReturn(Optional.of(bankRef));

		ResponseEntity<?> response = controller.getPaymentEligibleContacts("DLR-0451", "JT-02");

		@SuppressWarnings("unchecked")
		List<PaymentEligibleContact> contacts = (List<PaymentEligibleContact>) response.getBody();
		assertThat(contacts).isEmpty();
	}

	@Test
	void ac9_personnelInactive_contactNotEligible() {
		Dealer dealer = makeDealer("DLR-0451", "PT Maju Motor", true);
		when(dealerRepository.findByDealerCode("DLR-0451")).thenReturn(Optional.of(dealer));

		DealerJobTitle jt = makeJobTitle("JT-02", "Branch Manager", "PMT-01", true);
		when(jobTitleRepository.findByJobTitleId("JT-02")).thenReturn(Optional.of(jt));

		// INACTIVE personnel → contact should NOT be eligible.
		DealerPersonnel personnel = makePersonnel("P-001", "DLR-0451", "Andi", "JT-02",
				"inactive", "BR-001");
		when(personnelRepository.findByDealerCode("DLR-0451")).thenReturn(List.of(personnel));

		DealerBankReference bankRef = makeBankReference("DLR-0451", "BR-001",
				"1234567890", "PT Maju Motor", "A");
		when(bankReferenceRepository.findByDealerCodeAndBankReferenceId("DLR-0451", "BR-001"))
				.thenReturn(Optional.of(bankRef));

		ResponseEntity<?> response = controller.getPaymentEligibleContacts("DLR-0451", "JT-02");

		@SuppressWarnings("unchecked")
		List<PaymentEligibleContact> contacts = (List<PaymentEligibleContact>) response.getBody();
		assertThat(contacts).isEmpty();
	}

	@Test
	void ac9_jobTitleInactive_contactNotEligible() {
		Dealer dealer = makeDealer("DLR-0451", "PT Maju Motor", true);
		when(dealerRepository.findByDealerCode("DLR-0451")).thenReturn(Optional.of(dealer));

		// INACTIVE job-title → contact should NOT be eligible.
		DealerJobTitle jt = makeJobTitle("JT-02", "Branch Manager", "PMT-01", false);
		when(jobTitleRepository.findByJobTitleId("JT-02")).thenReturn(Optional.of(jt));

		DealerPersonnel personnel = makePersonnel("P-001", "DLR-0451", "Andi", "JT-02",
				"A", "BR-001");
		when(personnelRepository.findByDealerCode("DLR-0451")).thenReturn(List.of(personnel));

		DealerBankReference bankRef = makeBankReference("DLR-0451", "BR-001",
				"1234567890", "PT Maju Motor", "A");
		when(bankReferenceRepository.findByDealerCodeAndBankReferenceId("DLR-0451", "BR-001"))
				.thenReturn(Optional.of(bankRef));

		ResponseEntity<?> response = controller.getPaymentEligibleContacts("DLR-0451", "JT-02");

		@SuppressWarnings("unchecked")
		List<PaymentEligibleContact> contacts = (List<PaymentEligibleContact>) response.getBody();
		assertThat(contacts).isEmpty();
	}

	@Test
	void ac9_noJobTitleFilter_returnsAllEligibleContacts() {
		Dealer dealer = makeDealer("DLR-0451", "PT Maju Motor", true);
		when(dealerRepository.findByDealerCode("DLR-0451")).thenReturn(Optional.of(dealer));

		DealerJobTitle jt1 = makeJobTitle("JT-02", "Manager", "PMT-01", true);
		DealerJobTitle jt3 = makeJobTitle("JT-03", "Staff", "PMT-02", true);
		when(jobTitleRepository.findByJobTitleId("JT-02")).thenReturn(Optional.of(jt1));
		when(jobTitleRepository.findByJobTitleId("JT-03")).thenReturn(Optional.of(jt3));

		DealerPersonnel p1 = makePersonnel("P-001", "DLR-0451", "Andi", "JT-02", "A", "BR-001");
		DealerPersonnel p2 = makePersonnel("P-002", "DLR-0451", "Budi", "JT-03", "A", "BR-002");
		when(personnelRepository.findByDealerCode("DLR-0451")).thenReturn(List.of(p1, p2));

		DealerBankReference br1 = makeBankReference("DLR-0451", "BR-001", "111", "Andi A", "A");
		DealerBankReference br2 = makeBankReference("DLR-0451", "BR-002", "222", "Budi B", "A");
		when(bankReferenceRepository.findByDealerCodeAndBankReferenceId("DLR-0451", "BR-001"))
				.thenReturn(Optional.of(br1));
		when(bankReferenceRepository.findByDealerCodeAndBankReferenceId("DLR-0451", "BR-002"))
				.thenReturn(Optional.of(br2));

		// No job_title_id filter → all eligible contacts returned.
		ResponseEntity<?> response = controller.getPaymentEligibleContacts("DLR-0451", null);

		@SuppressWarnings("unchecked")
		List<PaymentEligibleContact> contacts = (List<PaymentEligibleContact>) response.getBody();
		assertThat(contacts).hasSize(2);
	}

	// ==================== Entity-mapping contracts for DealerBankReference ====================

	@Test
	void dealerBankReferenceIsEntityWithCorrectTable() {
		assertThat(DealerBankReference.class).hasAnnotation(
				jakarta.persistence.Entity.class);
		jakarta.persistence.Table table = DealerBankReference.class.getAnnotation(
				jakarta.persistence.Table.class);
		assertThat(table).isNotNull();
		assertThat(table.name()).isEqualTo("mst_dealer_bank_reference");
	}

	@Test
	void dealerBankReferenceExtendsVersionedEntity() {
		assertThat(com.coresystem.coresystembackend.masterdata.common.VersionedEntity.class)
				.isAssignableFrom(DealerBankReference.class);
	}

	@Test
	void dealerBankReferenceAccountNumberIsColumnMappedLocked() {
		// [LOCKED] payout zero-diff — @Column name must match legacy.
		java.lang.reflect.Field f = declaredField(DealerBankReference.class, "accountNumber");
		assertThat(f.isAnnotationPresent(jakarta.persistence.Column.class)).isTrue();
		assertThat(f.getAnnotation(jakarta.persistence.Column.class).name())
				.isEqualTo("account_number");
	}

	@Test
	void dealerBankReferenceAccountNameIsColumnMappedLocked() {
		// [LOCKED] payout zero-diff — @Column name must match legacy.
		java.lang.reflect.Field f = declaredField(DealerBankReference.class, "accountName");
		assertThat(f.isAnnotationPresent(jakarta.persistence.Column.class)).isTrue();
		assertThat(f.getAnnotation(jakarta.persistence.Column.class).name())
				.isEqualTo("account_name");
	}

	@Test
	void dealerBankReferenceAllFieldsPresent() {
		assertThat(fieldType(DealerBankReference.class, "dealerCode")).isEqualTo(String.class);
		assertThat(fieldType(DealerBankReference.class, "bankReferenceId")).isEqualTo(String.class);
		assertThat(fieldType(DealerBankReference.class, "accountType")).isEqualTo(String.class);
		assertThat(fieldType(DealerBankReference.class, "accountDescription")).isEqualTo(String.class);
		assertThat(fieldType(DealerBankReference.class, "bankId")).isEqualTo(String.class);
		assertThat(fieldType(DealerBankReference.class, "accountNumber")).isEqualTo(String.class);
		assertThat(fieldType(DealerBankReference.class, "accountName")).isEqualTo(String.class);
		assertThat(fieldType(DealerBankReference.class, "bankChargesFlag")).isEqualTo(boolean.class);
		assertThat(fieldType(DealerBankReference.class, "status")).isEqualTo(String.class);
		assertThat(fieldType(DealerBankReference.class, "activationDate")).isEqualTo(LocalDate.class);
		assertThat(fieldType(DealerBankReference.class, "deactivationDate")).isEqualTo(LocalDate.class);
	}

	@Test
	void dealerBankReferenceStatusIsStringEnum() {
		// BR-DLRPTN-1: status 'A'|'inactive' — String + CHECK at DB layer.
		assertThat(fieldType(DealerBankReference.class, "status")).isEqualTo(String.class);
	}

	// ==================== KTP/NPWP validation ====================

	@Test
	void validateKtpNpwp_validKtp16Digits_doesNotThrow() {
		DealerService.validateKtpNpwp("3275123456789012", "09.123.456.7-890.123");
	}

	@Test
	void validateKtpNpwp_shortKtp_throwsInvalidKtpNpwp() {
		assertThatThrownBy(() -> DealerService.validateKtpNpwp("123", "09.123.456.7-890.123"))
				.isInstanceOf(DealerServiceException.class)
				.hasMessageContaining("KTP");
	}

	@Test
	void validateKtpNpwp_badNpwpFormat_throwsInvalidKtpNpwp() {
		assertThatThrownBy(() -> DealerService.validateKtpNpwp("3275123456789012", "bad-npwp"))
				.isInstanceOf(DealerServiceException.class)
				.hasMessageContaining("NPWP");
	}

	// ==================== Helpers ====================

	private static Dealer makeDealer(String code, String name, boolean isUsedCar) {
		Dealer d = new Dealer();
		d.setDealerCode(code);
		d.setDealerName(name);
		d.setUsedCar(isUsedCar);
		d.setStatus("active");
		return d;
	}

	private static DealerBranchAccess makeBranchAccess(String dealerCode, String branchId,
			boolean isActive) {
		DealerBranchAccess a = new DealerBranchAccess();
		a.setDealerCode(dealerCode);
		a.setBranchId(branchId);
		a.setActive(isActive);
		return a;
	}

	private static DealerJobTitle makeJobTitle(String id, String desc, String paymentCode,
			boolean isActive) {
		DealerJobTitle jt = new DealerJobTitle();
		jt.setJobTitleId(id);
		jt.setDescription(desc);
		jt.setDealerPaymentCode(paymentCode);
		jt.setActive(isActive);
		return jt;
	}

	private static DealerPersonnel makePersonnel(String id, String dealerCode, String name,
			String jobTitleId, String status, String bankRefId) {
		DealerPersonnel p = new DealerPersonnel();
		p.setPersonnelId(id);
		p.setDealerCode(dealerCode);
		p.setName(name);
		p.setJobTitleId(jobTitleId);
		p.setStatus(status);
		p.setBankReferenceId(bankRefId);
		return p;
	}

	private static DealerBankReference makeBankReference(String dealerCode, String bankRefId,
			String accountNumber, String accountName, String status) {
		DealerBankReference br = new DealerBankReference();
		br.setDealerCode(dealerCode);
		br.setBankReferenceId(bankRefId);
		br.setAccountNumber(accountNumber);
		br.setAccountName(accountName);
		br.setStatus(status);
		return br;
	}

	private static Class<?> fieldType(Class<?> type, String fieldName) {
		return declaredField(type, fieldName).getType();
	}

	private static java.lang.reflect.Field declaredField(Class<?> type, String fieldName) {
		try {
			return type.getDeclaredField(fieldName);
		} catch (NoSuchFieldException e) {
			throw new AssertionError(type.getSimpleName() + " must declare field '"
					+ fieldName + "'", e);
		}
	}

	private static void reflectiveSetId(MasterChangeRequest entity, long id) {
		try {
			var field = MasterChangeRequest.class.getDeclaredField("id");
			field.setAccessible(true);
			field.set(entity, id);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Cannot set id on MasterChangeRequest", e);
		}
	}

}
// SDD-PROVENANCE: U-006 | vault: .mega-sdd/vaults/acquisition-master-data | TDD test — AC-6 branch-scoped picker (BR-BE07-07), AC-7 PARENT_DEALER_NOT_FOUND (EC7 fix), AC-8 E18→202+403 self-approve, AC-9 E21 eligible=all active simultan (BR-BE07-10), DealerBankReference [LOCKED] @Column entity contracts, KTP/NPWP validation
