package com.coresystem.coresystembackend.masterdata.lookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.coresystem.coresystembackend.masterdata.lookup.LookupAclClient.LookupSourceUnavailableException;
import com.coresystem.coresystembackend.masterdata.lookup.LookupController.LookupResponse;
import com.coresystem.coresystembackend.masterdata.lookup.LookupService.LookupItem;

/**
 * TDD test for U-011 — Lookup Tier C ACL layer + generic E30 endpoint.
 *
 * <p>Verifies the three acceptance criteria that are in scope for this unit:
 * <ul>
 *   <li><strong>AC-12</strong> — {@code applicant_type} filter = set-membership eksplisit (BR-BE07-12,
 *       fix EC5). KITAS ter-map only P; {@code ?applicant_type=C} → KITAS NOT returned. No
 *       partial-text/substring match (filter "PP" does not match {@code applicantTypes=["P"]}).</li>
 *   <li><strong>AC-13</strong> — backing unreachable → {@code 503 LOOKUP_SOURCE_UNAVAILABLE} +
 *       {@code correlation_id} (BR-BE07-22, fix EC1/EC12/fuel silent-success). NOT {@code 200} empty.</li>
 *   <li><strong>Key-not-in-registry</strong> → {@code 404 LOOKUP_KEY_UNKNOWN}.</li>
 * </ul>
 *
 * <p>Also verifies default active-only (BR-BE07-21), {@code include_inactive=true} admin toggle,
 * the {@code source_tier:"C_EXTERNAL_READONLY"} boundary-transparency field, and the stub ACL
 * client that always throws (OQ-ARCH-STACK — real adapter TBD ITEC D-11).
 *
 * <p>Pure unit test — no Spring context (Boot 4.1.1 does not ship {@code @WebMvcTest}).
 * Controller and service are instantiated directly; {@link LookupAclClient} is mocked via Mockito.
 */
class LookupTierCTest {

	private LookupKeyRegistry registry;
	private LookupAclClient aclClient;
	private LookupService service;
	private LookupController controller;

	@BeforeEach
	void setUp() {
		registry = new LookupKeyRegistry();
		aclClient = mock(LookupAclClient.class);
		service = new LookupService(registry, aclClient);
		controller = new LookupController(service);
	}

	// -------------------------------------------------------------------------
	// AC-12: applicant_type filter = set-membership eksplisit (NOT substring)
	// -------------------------------------------------------------------------

	/**
	 * AC-12: identity-type KITAS ter-map only P. Filter P → KITAS returned.
	 * Filter C → KITAS NOT returned (C not in ["P"]). KTP (mapped P,C) returned for both.
	 */
	@Test
	void ac12_applicantTypeFilter_setMembership_kitasOnlyMappedToP() {
		LookupItem kitas = new LookupItem("KITAS", "Kartu Izin Tinggal Terbatas", List.of("P"), true);
		LookupItem ktp = new LookupItem("KTP", "Kartu Tanda Penduduk", List.of("P", "C"), true);
		when(aclClient.fetch(eq("identity-type"), any())).thenReturn(List.of(kitas, ktp));

		// filter applicant_type=P → both returned (KITAS has P, KTP has P)
		var resultP = service.getLookup("identity-type", Map.of("applicant_type", "P"));
		assertThat(resultP.items()).extracting(LookupItem::id)
				.containsExactlyInAnyOrder("KITAS", "KTP");

		// filter applicant_type=C → only KTP returned (KITAS does NOT have C in its set)
		var resultC = service.getLookup("identity-type", Map.of("applicant_type", "C"));
		assertThat(resultC.items()).extracting(LookupItem::id)
				.containsOnly("KTP");
	}

	/**
	 * AC-12: no partial-text match. Filter "PP" does NOT match an item with
	 * {@code applicantTypes=["P"]} — set-membership requires exact element equality, not substring.
	 */
	@Test
	void ac12_noPartialTextMatch_filterPpDoesNotMatchApplicantTypeP() {
		LookupItem kitas = new LookupItem("KITAS", "Kitas Name", List.of("P"), true);
		when(aclClient.fetch(eq("identity-type"), any())).thenReturn(List.of(kitas));

		var result = service.getLookup("identity-type", Map.of("applicant_type", "PP"));
		assertThat(result.items()).isEmpty();
	}

	/**
	 * AC-12 supplementary: no applicant_type filter → all items returned (no filtering).
	 */
	@Test
	void ac12_noApplicantTypeFilter_returnsAllItems() {
		LookupItem kitas = new LookupItem("KITAS", "Kitas", List.of("P"), true);
		LookupItem ktp = new LookupItem("KTP", "Ktp", List.of("P", "C"), true);
		when(aclClient.fetch(eq("identity-type"), any())).thenReturn(List.of(kitas, ktp));

		var result = service.getLookup("identity-type", Map.of());
		assertThat(result.items()).extracting(LookupItem::id)
				.containsExactlyInAnyOrder("KITAS", "KTP");
	}

	// -------------------------------------------------------------------------
	// AC-13: backing unreachable → 503 + correlation_id (NOT 200 empty)
	// -------------------------------------------------------------------------

	/**
	 * AC-13: ACL backing store unreachable → 503 LOOKUP_SOURCE_UNAVAILABLE + correlation_id.
	 * This is the fix for EC1/EC12/fuel silent-success (BR-BE07-22) — NEVER return 200 empty.
	 */
	@Test
	void ac13_backingUnavailable_returns503WithCorrelationId() {
		when(aclClient.fetch(anyString(), any()))
				.thenThrow(new LookupSourceUnavailableException(
						"OQ-ARCH-STACK ACL transport not yet implemented"));

		ResponseEntity<?> response = controller.getLookup(
				"identity-type", null, null, null, false, 0, 20);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(response.getBody()).isInstanceOf(Map.class);
		@SuppressWarnings("unchecked")
		Map<String, Object> body = (Map<String, Object>) response.getBody();
		assertThat(body).containsEntry("error", "LOOKUP_SOURCE_UNAVAILABLE");
		assertThat(body).containsKey("correlation_id");
		assertThat(body.get("correlation_id")).isNotNull();
		// Guard: NOT 200 empty (EC1/EC12/fuel do-not-replicate)
		assertThat(response.getStatusCode().value()).isNotEqualTo(200);
	}

	// -------------------------------------------------------------------------
	// Key-not-in-registry → 404 LOOKUP_KEY_UNKNOWN
	// -------------------------------------------------------------------------

	/**
	 * Lookup key not in registry → 404 LOOKUP_KEY_UNKNOWN (BR-BE07-22 distinct signal).
	 */
	@Test
	void unknownKey_returns404LookupKeyUnknown() {
		ResponseEntity<?> response = controller.getLookup(
				"nonexistent-key", null, null, null, false, 0, 20);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).isInstanceOf(Map.class);
		@SuppressWarnings("unchecked")
		Map<String, Object> body = (Map<String, Object>) response.getBody();
		assertThat(body).containsEntry("error", "LOOKUP_KEY_UNKNOWN");
		assertThat(body).containsEntry("lookup_key", "nonexistent-key");
	}

	// -------------------------------------------------------------------------
	// BR-BE07-21: default active-only; include_inactive=true for admin
	// -------------------------------------------------------------------------

	/**
	 * Default include_inactive=false → only active items returned (BR-BE07-21).
	 */
	@Test
	void defaultActiveOnly_filtersInactive() {
		LookupItem active = new LookupItem("A", "Active", List.of("P"), true);
		LookupItem inactive = new LookupItem("B", "Inactive", List.of("P"), false);
		when(aclClient.fetch(eq("marital-status"), any())).thenReturn(List.of(active, inactive));

		var result = service.getLookup("marital-status", Map.of());
		assertThat(result.items()).extracting(LookupItem::id).containsOnly("A");
	}

	/**
	 * include_inactive=true → all items returned including inactive (BR-BE07-21 admin toggle).
	 */
	@Test
	void includeInactiveTrue_returnsAll() {
		LookupItem active = new LookupItem("A", "Active", List.of("P"), true);
		LookupItem inactive = new LookupItem("B", "Inactive", List.of("P"), false);
		when(aclClient.fetch(eq("marital-status"), any())).thenReturn(List.of(active, inactive));

		var result = service.getLookup("marital-status", Map.of("include_inactive", "true"));
		assertThat(result.items()).extracting(LookupItem::id)
				.containsExactlyInAnyOrder("A", "B");
	}

	// -------------------------------------------------------------------------
	// Response shape: source_tier, lookup_key, items, pagination
	// -------------------------------------------------------------------------

	/**
	 * Success response carries source_tier:"C_EXTERNAL_READONLY", lookup_key, items, and
	 * the BR-BE07-20 pagination fields (page, total_pages, record_count).
	 */
	@Test
	void responseHasSourceTierAndLookupKey() {
		LookupItem item = new LookupItem("KTP", "Kartu Tanda Penduduk", List.of("P", "C"), true);
		when(aclClient.fetch(eq("identity-type"), any())).thenReturn(List.of(item));

		ResponseEntity<?> response = controller.getLookup(
				"identity-type", null, null, null, false, 0, 20);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isInstanceOf(LookupResponse.class);
		LookupResponse body = (LookupResponse) response.getBody();
		assertThat(body.lookupKey()).isEqualTo("identity-type");
		assertThat(body.sourceTier()).isEqualTo("C_EXTERNAL_READONLY");
		assertThat(body.items()).hasSize(1);
		assertThat(body.items().get(0).id()).isEqualTo("KTP");
		assertThat(body.page()).isZero();
		assertThat(body.totalPages()).isEqualTo(1);
		assertThat(body.recordCount()).isEqualTo(1);
	}

	/**
	 * Applicant_type filter applied through the controller endpoint (end-to-end via controller).
	 */
	@Test
	void controllerAppliesApplicantTypeFilter() {
		LookupItem kitas = new LookupItem("KITAS", "Kitas", List.of("P"), true);
		LookupItem ktp = new LookupItem("KTP", "Ktp", List.of("P", "C"), true);
		when(aclClient.fetch(eq("identity-type"), any())).thenReturn(List.of(kitas, ktp));

		ResponseEntity<?> response = controller.getLookup(
				"identity-type", "C", null, null, false, 0, 20);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		LookupResponse body = (LookupResponse) response.getBody();
		assertThat(body.items()).extracting(LookupItem::id).containsOnly("KTP");
	}

	// -------------------------------------------------------------------------
	// Stub ACL client (OQ-ARCH-STACK)
	// -------------------------------------------------------------------------

	/**
	 * The stub ACL client always throws LookupSourceUnavailableException (OQ-ARCH-STACK —
	 * real adapter TBD ITEC D-11). This ensures every registered key returns 503 until the
	 * real transport lands — NEVER silent-success-empty (BR-BE07-22).
	 */
	@Test
	void stubAclClientAlwaysThrows() {
		LookupAclClient stub = new LookupAclClient.StubLookupAclClient();
		assertThatThrownBy(() -> stub.fetch("identity-type", Map.of()))
				.isInstanceOf(LookupSourceUnavailableException.class)
				.hasMessageContaining("OQ-ARCH-STACK");
	}

	// -------------------------------------------------------------------------
	// Registry initial keys (BE-07 §3.5)
	// -------------------------------------------------------------------------

	/**
	 * Registry has the six initial lookup keys from BE-07 §3.5.
	 */
	@Test
	void registryHasInitialKeys() {
		assertThat(registry.isRegistered("marital-status")).isTrue();
		assertThat(registry.isRegistered("identity-type")).isTrue();
		assertThat(registry.isRegistered("economic-sector")).isTrue();
		assertThat(registry.isRegistered("bank")).isTrue();
		assertThat(registry.isRegistered("location")).isTrue();
		assertThat(registry.isRegistered("asset-brand")).isTrue();
		assertThat(registry.isRegistered("nonexistent")).isFalse();
	}

	/**
	 * Registry.get returns Optional with config for registered keys, empty for unknown.
	 */
	@Test
	void registryGetReturnsConfigForRegisteredKeys() {
		assertThat(registry.get("identity-type")).isPresent();
		assertThat(registry.get("identity-type").get().key()).isEqualTo("identity-type");
		assertThat(registry.get("identity-type").get().backingStoreId()).isEqualTo("FC_MSTAPP_MCF");
		assertThat(registry.get("nonexistent")).isEmpty();
	}

}
// SDD-PROVENANCE: U-011 | vault: .mega-sdd/vaults/acquisition-master-data | TDD test — AC-12 (set-membership applicant_type, KITAS P-only, no partial-text), AC-13 (503+correlation_id, not 200 empty), 404 unknown key, active-only default, include_inactive toggle, response shape, stub ACL throws
