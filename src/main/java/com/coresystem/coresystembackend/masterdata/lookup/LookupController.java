package com.coresystem.coresystembackend.masterdata.lookup;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coresystem.coresystembackend.masterdata.lookup.LookupAclClient.LookupSourceUnavailableException;
import com.coresystem.coresystembackend.masterdata.lookup.LookupService.LookupItem;
import com.coresystem.coresystembackend.masterdata.lookup.LookupService.LookupResult;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * E30 generic lookup controller (Tier C via ACL, F-U-006).
 *
 * <p>Exposes a single endpoint: {@code GET /lookups/{lookup_key}}. This is the generic read-only
 * lookup over the {@code FC_MSTAPP_MCF} external master catalog (BE-07 §3.5), accessed via the
 * ACL service boundary (BR-BE07-26 — NO cross-DB three-part-name / linked-server four-part-name).
 * There is NO local {@code mst_} table for Tier C lookups.
 *
 * <h2>Response shape (BR-BE07-20)</h2>
 * <pre>
 * {
 *   "lookup_key": "identity-type",
 *   "source_tier": "C_EXTERNAL_READONLY",
 *   "items": [ { "id":"KTP", "name":"...", "applicant_types":["P","C"], "is_active":true } ],
 *   "page": 0,
 *   "total_pages": 1,
 *   "record_count": 2
 * }
 * </pre>
 *
 * <h2>Query parameters</h2>
 * <ul>
 *   <li>{@code applicant_type} — set-membership eksplisit filter (BR-BE07-12, fix EC5: an item is
 *       returned only if its {@code applicant_types} list contains the value as an exact element;
 *       NOT substring match).</li>
 *   <li>{@code parent_id} — hierarchical parent filter (economic-sector, location).</li>
 *   <li>{@code branch_id} — branch-scope filter.</li>
 *   <li>{@code include_inactive} — default {@code false} (BR-BE07-21 active-only); {@code true}
 *       for admin screens.</li>
 *   <li>{@code page}/{@code size} — pagination (0-based page, default size 20).</li>
 * </ul>
 *
 * <h2>Error handling (BR-BE07-22 — three distinct signals)</h2>
 * <ul>
 *   <li><strong>Key unknown</strong> → {@code 404 LOOKUP_KEY_UNKNOWN} + {@code lookup_key}.</li>
 *   <li><strong>Source unreachable</strong> → {@code 503 LOOKUP_SOURCE_UNAVAILABLE} +
 *       {@code correlation_id} (fix EC1/EC12/fuel silent-success — NEVER {@code 200} empty).</li>
 * </ul>
 *
 * <p>Authentication is enforced by the security filter chain ({@code SecurityConfig}); the
 * {@code /lookups} path is not in the {@code permitAll} list, so it requires an authenticated
 * request. No role-based authorization is applied here — the admin-only
 * {@code include_inactive=true} toggle is a caller-responsibility contract (the caller's UI
 * decides whether to show the toggle), not a server-enforced role gate in this unit.
 */
@ConditionalOnBean(JpaRepository.class)
@RestController
@RequestMapping("/lookups")
public class LookupController {

	private static final Logger logger = LoggerFactory.getLogger(LookupController.class);

	private final LookupService lookupService;

	/**
	 * Construct the controller with its lookup service.
	 *
	 * @param lookupService the E30 lookup service
	 */
	public LookupController(LookupService lookupService) {
		this.lookupService = lookupService;
	}

	/**
	 * E30 GET {@code /lookups/{lookup_key}} — generic Tier C read-only lookup.
	 *
	 * @param lookupKey the registered lookup key (path variable)
	 * @param applicantType optional set-membership filter (BR-BE07-12)
	 * @param parentId optional hierarchical parent filter
	 * @param branchId optional branch-scope filter
	 * @param includeInactive default false (BR-BE07-21); true for admin
	 * @param page zero-based page number (default 0)
	 * @param size page size (default 20)
	 * @return 200 with {@link LookupResponse}, 404 if key unknown, 503 if source unreachable
	 */
	@GetMapping("/{lookup_key}")
	public ResponseEntity<?> getLookup(
			@PathVariable("lookup_key") String lookupKey,
			@RequestParam(name = "applicant_type", required = false) String applicantType,
			@RequestParam(name = "parent_id", required = false) String parentId,
			@RequestParam(name = "branch_id", required = false) String branchId,
			@RequestParam(name = "include_inactive", defaultValue = "false") boolean includeInactive,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size) {

		// Build the filter map for the service (nulls excluded so the service sees only present
		// filters; the include_inactive flag is passed as a string for uniform parseBoolean handling).
		Map<String, String> filters = new java.util.LinkedHashMap<>();
		if (applicantType != null && !applicantType.isBlank()) {
			filters.put("applicant_type", applicantType);
		}
		if (parentId != null && !parentId.isBlank()) {
			filters.put("parent_id", parentId);
		}
		if (branchId != null && !branchId.isBlank()) {
			filters.put("branch_id", branchId);
		}
		filters.put("include_inactive", String.valueOf(includeInactive));

		try {
			LookupResult result = lookupService.getLookup(lookupKey, filters);

			// In-memory pagination over the (already post-filtered) items. The ACL stub returns
			// small lists; when the real adapter lands it may push pagination server-side. For
			// now we paginate the in-memory list so the response shape matches BR-BE07-20.
			List<LookupItem> allItems = result.items();
			int total = allItems.size();
			int safeSize = size > 0 ? size : 20;
			int safePage = page >= 0 ? page : 0;
			int from = Math.min(safePage * safeSize, total);
			int to = Math.min(from + safeSize, total);
			List<LookupItem> pageItems = allItems.subList(from, to);
			int totalPages = safeSize > 0 ? (int) Math.ceil((double) total / safeSize) : 0;

			return ResponseEntity.ok(new LookupResponse(
					result.lookupKey(),
					result.sourceTier(),
					pageItems,
					safePage,
					totalPages,
					total));

		} catch (LookupService.LookupKeyUnknownException e) {
			// BR-BE07-22: key not in registry → 404 LOOKUP_KEY_UNKNOWN (distinct signal).
			return ResponseEntity
					.status(HttpStatus.NOT_FOUND)
					.body(Map.of(
							"error", "LOOKUP_KEY_UNKNOWN",
							"lookup_key", lookupKey));

		} catch (LookupSourceUnavailableException e) {
			// BR-BE07-22: backing unreachable → 503 LOOKUP_SOURCE_UNAVAILABLE + correlation_id.
			// Fix EC1/EC12/fuel silent-success — NEVER return 200 empty.
			String correlationId = UUID.randomUUID().toString();
			logger.error("Lookup source unavailable for key={}: {} [correlation_id={}]",
					lookupKey, e.getMessage(), correlationId);
			return ResponseEntity
					.status(HttpStatus.SERVICE_UNAVAILABLE)
					.body(Map.of(
							"error", "LOOKUP_SOURCE_UNAVAILABLE",
							"lookup_key", lookupKey,
							"correlation_id", correlationId));
		}
	}

	/**
	 * Response body for E30 GET {@code /lookups/{lookup_key}}.
	 *
	 * <p>Field names are snake_case via {@link JsonProperty @JsonProperty} to match the BR-BE07-20
	 * response convention without relying on a global Jackson naming strategy (the app does not
	 * configure one — same approach as U-002 {@code EmployeeMirrorDto}).
	 *
	 * @param lookupKey the resolved lookup key
	 * @param sourceTier always {@code "C_EXTERNAL_READONLY"} (boundary transparency, BE-07 §1.3)
	 * @param items the page slice of lookup items
	 * @param page the requested page (0-based)
	 * @param totalPages total page count
	 * @param recordCount total record count across all pages
	 */
	public record LookupResponse(
			@JsonProperty("lookup_key") String lookupKey,
			@JsonProperty("source_tier") String sourceTier,
			List<LookupItem> items,
			int page,
			@JsonProperty("total_pages") int totalPages,
			@JsonProperty("record_count") int recordCount) {
	}

}
// SDD-PROVENANCE: U-011 | vault: .mega-sdd/vaults/acquisition-master-data | LookupController @RestController (/lookups) — E30 GET /{lookup_key} (Tier C ACL read-only); applicant_type/parent_id/branch_id/include_inactive filters; 404 LOOKUP_KEY_UNKNOWN, 503 LOOKUP_SOURCE_UNAVAILABLE+correlation_id (BR-BE07-22); source_tier C_EXTERNAL_READONLY; nested LookupResponse record @JsonProperty snake_case (BR-BE07-20); BR-BE07-26 ACL boundary
