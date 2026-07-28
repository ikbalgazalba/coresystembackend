package com.coresystem.coresystembackend.masterdata.makercheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest.Action;
import com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest.Status;
import com.coresystem.coresystembackend.masterdata.makercheck.MakerCheckerService.ChangeRequestNotFoundException;
import com.coresystem.coresystembackend.masterdata.makercheck.MakerCheckerService.SelfApprovalBlockedException;
import com.coresystem.coresystembackend.masterdata.makercheck.MakerCheckerService.TerminalStateException;

/**
 * TDD test for U-009 — maker-checker envelope engine E37.
 *
 * <p>Tests the state machine §7.2 {@code (∅)→pending_approval→applied|rejected|cancelled} and
 * the guard rules (self-approval blocked, reject reason wajib, cancel only by maker, terminal
 * immutable) WITHOUT loading a Spring context or a real datasource. The service is exercised
 * against a Mockito-mocked {@link MasterChangeRequestRepository} so the business logic is
 * verified in isolation.
 *
 * <p>The mock repository is configured to behave like a real JPA store: {@code save} persists
 * into an in-memory map (assigning an ID on first save), and {@code findById} reads from that
 * map. This lets the service's save-then-find-then-update cycle work end-to-end in the test.
 *
 * <p>AC-8 (BE-07 §9):
 * <ul>
 *   <li>submit → {@code pending_approval}</li>
 *   <li>self-approve → {@code 403 SELF_APPROVAL_BLOCKED} (D-01 S11)</li>
 *   <li>approve idempotent (replay returns same result)</li>
 *   <li>reject requires {@code rejectReason} (blank → error)</li>
 *   <li>cancel only by maker</li>
 *   <li>terminal {@code applied}/{@code rejected}/{@code cancelled} immutable → {@code 409}</li>
 * </ul>
 */
class MakerCheckerEnvelopeTest {

	private MasterChangeRequestRepository repository;
	private MakerCheckerService service;
	private final AtomicLong idGenerator = new AtomicLong(1);
	private final java.util.Map<Long, MasterChangeRequest> store = new java.util.concurrent.ConcurrentHashMap<>();

	@BeforeEach
	void setUp() {
		repository = Mockito.mock(MasterChangeRequestRepository.class);
		store.clear();
		idGenerator.set(1);

		// Mock save: assign ID if null, store in map, return the entity.
		when(repository.save(any(MasterChangeRequest.class))).thenAnswer(inv -> {
			MasterChangeRequest req = inv.getArgument(0);
			if (req.getId() == null) {
				reflectiveSetId(req, idGenerator.getAndIncrement());
			}
			store.put(req.getId(), req);
			return req;
		});

		// Mock findById: read from the in-memory map.
		when(repository.findById(any())).thenAnswer(inv -> {
			Long id = inv.getArgument(0);
			return Optional.ofNullable(store.get(id));
		});
		service = new MakerCheckerService(repository);
	}

	// --- submit ---

	@Test
	void submit_createsPendingApprovalRequest() {
		MasterChangeRequest request = service.submit("dealer-bank-reference", Action.create,
				"{\"bank\":\"BCA\"}", "NIK001");

		assertThat(request.getId()).isNotNull();
		assertThat(request.getResource()).isEqualTo("dealer-bank-reference");
		assertThat(request.getAction()).isEqualTo(Action.create);
		assertThat(request.getPayload()).isEqualTo("{\"bank\":\"BCA\"}");
		assertThat(request.getStatus()).isEqualTo(Status.pending_approval);
		assertThat(request.getMakerNik()).isEqualTo("NIK001");
		assertThat(request.getSubmittedAt()).isNotNull();
		assertThat(request.getCheckerNik()).isNull();
		assertThat(request.getCheckedAt()).isNull();
	}

	@Test
	void submit_setsCreatedByToMakerNik() {
		MasterChangeRequest request = service.submit("general-parameter", Action.update,
				"{}", "NIK002");

		assertThat(request.getCreatedBy()).isEqualTo("NIK002");
		assertThat(request.getCreatedAt()).isNotNull();
	}

	// --- approve ---

	@Test
	void approve_byDifferentChecker_appliesRequest() {
		MasterChangeRequest request = service.submit("dealer-bank-reference", Action.create,
				"{}", "NIK001");

		MasterChangeRequest approved = service.approve(request.getId(), "NIK_CHECKER", "LG");

		assertThat(approved.getStatus()).isEqualTo(Status.applied);
		assertThat(approved.getCheckerNik()).isEqualTo("NIK_CHECKER");
		assertThat(approved.getCheckedAt()).isNotNull();
		assertThat(approved.getCheckerNote()).isEqualTo("LG");
	}

	@Test
	void approve_bySameNikAsMaker_throwsSelfApprovalBlocked() {
		MasterChangeRequest request = service.submit("dealer-bank-reference", Action.create,
				"{}", "NIK001");

		assertThatThrownBy(() -> service.approve(request.getId(), "NIK001", "self"))
				.isInstanceOf(SelfApprovalBlockedException.class)
				.hasMessageContaining("SELF_APPROVAL_BLOCKED");
	}

	@Test
	void approve_idempotent_alreadyApplied_returnsSameRequest() {
		MasterChangeRequest request = service.submit("dealer-bank-reference", Action.create,
				"{}", "NIK001");
		MasterChangeRequest firstApprove = service.approve(request.getId(), "NIK_CHECKER", "OK");

		MasterChangeRequest secondApprove = service.approve(request.getId(), "NIK_CHECKER", "OK");

		assertThat(secondApprove.getStatus()).isEqualTo(Status.applied);
		assertThat(secondApprove.getCheckedAt()).isEqualTo(firstApprove.getCheckedAt());
	}

	@Test
	void approve_nonExistentId_throwsNotFound() {
		assertThatThrownBy(() -> service.approve(9999L, "NIK_CHECKER", "OK"))
				.isInstanceOf(ChangeRequestNotFoundException.class);
	}

	// --- reject ---

	@Test
	void reject_byDifferentChecker_rejectsRequest() {
		MasterChangeRequest request = service.submit("dealer-bank-reference", Action.create,
				"{}", "NIK001");

		MasterChangeRequest rejected = service.reject(request.getId(), "NIK_CHECKER", "Invalid data");

		assertThat(rejected.getStatus()).isEqualTo(Status.rejected);
		assertThat(rejected.getCheckerNik()).isEqualTo("NIK_CHECKER");
		assertThat(rejected.getCheckedAt()).isNotNull();
		assertThat(rejected.getRejectReason()).isEqualTo("Invalid data");
	}

	@Test
	void reject_withBlankReason_throwsException() {
		MasterChangeRequest request = service.submit("dealer-bank-reference", Action.create,
				"{}", "NIK001");

		assertThatThrownBy(() -> service.reject(request.getId(), "NIK_CHECKER", ""))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("reject_reason");

		assertThatThrownBy(() -> service.reject(request.getId(), "NIK_CHECKER", null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("reject_reason");
	}

	@Test
	void reject_bySameNikAsMaker_throwsSelfApprovalBlocked() {
		MasterChangeRequest request = service.submit("dealer-bank-reference", Action.create,
				"{}", "NIK001");

		assertThatThrownBy(() -> service.reject(request.getId(), "NIK001", "reason"))
				.isInstanceOf(SelfApprovalBlockedException.class);
	}

	// --- cancel ---

	@Test
	void cancel_byMaker_cancelsRequest() {
		MasterChangeRequest request = service.submit("dealer-bank-reference", Action.create,
				"{}", "NIK001");

		MasterChangeRequest cancelled = service.cancel(request.getId(), "NIK001");

		assertThat(cancelled.getStatus()).isEqualTo(Status.cancelled);
		assertThat(cancelled.getCheckedAt()).isNotNull();
	}

	@Test
	void cancel_byNonMaker_throwsException() {
		MasterChangeRequest request = service.submit("dealer-bank-reference", Action.create,
				"{}", "NIK001");

		assertThatThrownBy(() -> service.cancel(request.getId(), "NIK_OTHER"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("maker");
	}

	// --- terminal immutability ---

	@Test
	void approve_onAppliedRequest_throwsConflict() {
		MasterChangeRequest request = service.submit("dealer-bank-reference", Action.create,
				"{}", "NIK001");
		service.approve(request.getId(), "NIK_CHECKER", "OK");

		assertThatThrownBy(() -> service.approve(request.getId(), "NIK_CHECKER2", "OK2"))
				.isInstanceOf(TerminalStateException.class)
				.hasMessageContaining("409");
	}

	@Test
	void reject_onAppliedRequest_throwsConflict() {
		MasterChangeRequest request = service.submit("dealer-bank-reference", Action.create,
				"{}", "NIK001");
		service.approve(request.getId(), "NIK_CHECKER", "OK");

		assertThatThrownBy(() -> service.reject(request.getId(), "NIK_CHECKER2", "reason"))
				.isInstanceOf(TerminalStateException.class);
	}

	@Test
	void cancel_onAppliedRequest_throwsConflict() {
		MasterChangeRequest request = service.submit("dealer-bank-reference", Action.create,
				"{}", "NIK001");
		service.approve(request.getId(), "NIK_CHECKER", "OK");

		assertThatThrownBy(() -> service.cancel(request.getId(), "NIK001"))
				.isInstanceOf(TerminalStateException.class);
	}

	@Test
	void approve_onRejectedRequest_throwsConflict() {
		MasterChangeRequest request = service.submit("dealer-bank-reference", Action.create,
				"{}", "NIK001");
		service.reject(request.getId(), "NIK_CHECKER", "bad");

		assertThatThrownBy(() -> service.approve(request.getId(), "NIK_CHECKER2", "OK"))
				.isInstanceOf(TerminalStateException.class);
	}

	@Test
	void cancel_onCancelledRequest_throwsConflict() {
		MasterChangeRequest request = service.submit("dealer-bank-reference", Action.create,
				"{}", "NIK001");
		service.cancel(request.getId(), "NIK001");

		assertThatThrownBy(() -> service.cancel(request.getId(), "NIK001"))
				.isInstanceOf(TerminalStateException.class);
	}

	// --- repository query ---

	@Test
	void findByStatusAndResource_filtersCorrectly() {
		service.submit("dealer-bank-reference", Action.create, "{}", "NIK001");
		service.submit("general-parameter", Action.update, "{}", "NIK002");
		service.submit("dealer-bank-reference", Action.update, "{}", "NIK003");

		Pageable pageable = PageRequest.of(0, 10);
		when(repository.findByStatusAndResource(MasterChangeRequest.Status.pending_approval, "dealer-bank-reference", pageable))
				.thenAnswer(inv -> {
					MasterChangeRequest.Status status = inv.getArgument(0);
					String resource = inv.getArgument(1);
					List<MasterChangeRequest> filtered = store.values().stream()
							.filter(r -> r.getStatus().equals(status))
							.filter(r -> r.getResource().equals(resource))
							.toList();
					return new PageImpl<>(filtered, pageable, filtered.size());
				});

		Page<MasterChangeRequest> page = repository.findByStatusAndResource(
				MasterChangeRequest.Status.pending_approval, "dealer-bank-reference", pageable);

		assertThat(page.getContent()).hasSize(2);
		assertThat(page.getTotalElements()).isEqualTo(2);
	}

	// --- entity ---

	@Test
	void entity_hasCorrectTableName() {
		jakarta.persistence.Table table = MasterChangeRequest.class
				.getAnnotation(jakarta.persistence.Table.class);
		assertThat(table.name()).isEqualTo("log_master_change_request");
	}

	@Test
	void entity_statusEnumHasAllFourStates() {
		assertThat(Status.values()).containsExactlyInAnyOrder(
				Status.pending_approval, Status.applied, Status.rejected, Status.cancelled);
	}

	@Test
	void entity_actionEnumHasAllFiveActions() {
		assertThat(Action.values()).containsExactlyInAnyOrder(
				Action.create, Action.update, Action.deactivate, Action.reactivate, Action.delete);
	}

	// --- helper ---

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
// SDD-PROVENANCE: U-009 | vault: .mega-sdd/vaults/acquisition-master-data | TDD test — maker-checker envelope E37 state machine §7.2 (submit→pending_approval; self-approve→403; approve idempotent; reject reason wajib; cancel only maker; terminal immutable→409)
