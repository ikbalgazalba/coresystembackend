package com.coresystem.coresystembackend.masterdata.lookup;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.coresystem.coresystembackend.masterdata.lookup.LookupAclClient.LookupSourceUnavailableException;

/**
 * E30 lookup service — generic read-only lookup over Tier C external masters via ACL.
 *
 * <p>Dispatches by {@code lookup_key} (validated against {@link LookupKeyRegistry}), calls
 * {@link LookupAclClient#fetch(String, Map)} to retrieve items from the ACL backing store, and
 * applies in-memory post-filters:
 * <ul>
 *   <li><strong>{@code applicant_type}</strong> — set-membership eksplisit (BR-BE07-12, fix EC5):
 *       an item is included only if its {@code applicantTypes} list <em>contains</em> the filter
 *       value as an exact element. This is NOT a substring/contains-text match — filtering
 *       {@code "PP"} does not match an item with {@code applicantTypes=["P"]}.</li>
 *   <li><strong>{@code include_inactive}</strong> — default {@code false} (BR-BE07-21): inactive
 *       items are filtered out unless the caller passes {@code include_inactive=true} (admin
 *       toggle).</li>
 * </ul>
 *
 * <h2>Error handling (BR-BE07-22)</h2>
 * Three distinct signals — never conflated, never silent-success-empty (EC1/EC12/fuel):
 * <ul>
 *   <li><strong>Key unknown</strong> — {@link LookupKeyUnknownException} → controller maps to
 *       {@code 404 LOOKUP_KEY_UNKNOWN}.</li>
 *   <li><strong>Source unreachable</strong> — {@link LookupSourceUnavailableException} from the
 *       ACL client propagates to the controller → {@code 503 LOOKUP_SOURCE_UNAVAILABLE}.</li>
 *   <li><strong>Empty result</strong> — a genuine zero-row result from the backing store returns
 *       an empty items list with HTTP 200 (distinct from a source error).</li>
 * </ul>
 *
 * <p>The {@link LookupSourceUnavailableException} is allowed to propagate unchanged so the
 * controller can attach a {@code correlation_id} and set the HTTP status.
 */
@Service
public class LookupService {

	private static final Logger logger = LoggerFactory.getLogger(LookupService.class);

	private final LookupKeyRegistry registry;
	private final LookupAclClient aclClient;

	/**
	 * Construct the service with its registry and ACL client.
	 *
	 * @param registry the lookup-key registry
	 * @param aclClient the ACL adapter (stub until OQ-ARCH-STACK resolved)
	 */
	public LookupService(LookupKeyRegistry registry, LookupAclClient aclClient) {
		this.registry = registry;
		this.aclClient = aclClient;
	}

	/**
	 * Resolve a lookup key to its items, applying the {@code applicant_type} set-membership and
	 * {@code include_inactive} post-filters.
	 *
	 * @param key the registered lookup key
	 * @param filters the raw query filters (may be empty; keys are query-param names)
	 * @return the lookup result (items, source tier)
	 * @throws LookupKeyUnknownException if the key is not in the registry (→ 404)
	 * @throws LookupSourceUnavailableException if the ACL backing store is unreachable (→ 503)
	 */
	public LookupResult getLookup(String key, Map<String, String> filters) {
		Objects.requireNonNull(key, "lookup key must not be null");
		Map<String, String> safeFilters = filters != null ? filters : Map.of();

		// Validate key in registry — 404 LOOKUP_KEY_UNKNOWN if absent (BR-BE07-22).
		if (!registry.isRegistered(key)) {
			throw new LookupKeyUnknownException(key);
		}

		// Fetch from ACL — may throw LookupSourceUnavailableException (propagates → 503).
		List<LookupItem> items = aclClient.fetch(key, safeFilters);

		// Post-filter: applicant_type = set-membership eksplisit (BR-BE07-12, fix EC5).
		String applicantType = safeFilters.get("applicant_type");
		if (applicantType != null && !applicantType.isBlank()) {
			items = items.stream()
					.filter(item -> item.applicantTypes() != null
							&& item.applicantTypes().contains(applicantType))
					.toList();
		}

		// Post-filter: default active-only (BR-BE07-21). include_inactive=true for admin.
		boolean includeInactive = parseBoolean(safeFilters.get("include_inactive"));
		if (!includeInactive) {
			items = items.stream()
					.filter(LookupItem::isActive)
					.toList();
		}

		return new LookupResult(key, "C_EXTERNAL_READONLY", items);
	}

	/**
	 * Parse a boolean query-param value. Treats {@code "true"} (case-insensitive) as true;
	 * everything else (including null/blank) as false. This matches the BR-BE07-21 admin-toggle
	 * contract: the default is active-only, and only an explicit {@code include_inactive=true}
	 * opts in.
	 */
	private static boolean parseBoolean(String value) {
		return value != null && "true".equalsIgnoreCase(value.trim());
	}

	/**
	 * A single lookup item from the ACL backing store.
	 *
	 * @param id the item's business identifier (e.g. {@code KTP}, {@code KITAS})
	 * @param name the human-readable label
	 * @param applicantTypes the set of applicant-types this item is mapped to (may be empty for
	 *        keys that don't carry applicant-type mapping); set-membership is checked against
	 *        this list (BR-BE07-12)
	 * @param active whether the item is active (BR-BE07-21 default active-only)
	 */
	public record LookupItem(String id, String name, List<String> applicantTypes, boolean active) {

		/**
		 * Canonical accessor matching the boolean record-component naming convention.
		 *
		 * @return true if the item is active
		 */
		public boolean isActive() {
			return active;
		}
	}

	/**
	 * The result of a lookup query.
	 *
	 * @param lookupKey the resolved lookup key
	 * @param sourceTier always {@code "C_EXTERNAL_READONLY"} for Tier C (boundary transparency,
	 *        BE-07 §1.3)
	 * @param items the (post-filtered) lookup items
	 */
	public record LookupResult(String lookupKey, String sourceTier, List<LookupItem> items) {
	}

	/**
	 * Thrown when a lookup key is not in the registry. Mapped to
	 * {@code 404 LOOKUP_KEY_UNKNOWN} by the controller (BR-BE07-22 distinct signal).
	 */
	public static class LookupKeyUnknownException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		private final String lookupKey;

		/**
		 * Construct with the unknown key.
		 *
		 * @param lookupKey the key that was not found in the registry
		 */
		public LookupKeyUnknownException(String lookupKey) {
			super("LOOKUP_KEY_UNKNOWN: " + lookupKey);
			this.lookupKey = lookupKey;
		}

		/**
		 * @return the unknown lookup key
		 */
		public String getLookupKey() {
			return lookupKey;
		}
	}

	/**
	 * Convenience accessor for the set of supported filter names for a key (delegates to the
	 * registry). Used by the controller to validate incoming filter params.
	 *
	 * @param key the lookup key
	 * @return the supported filter names, or empty if the key is not registered
	 */
	public Set<String> supportedFilters(String key) {
		return registry.get(key)
				.map(LookupKeyRegistry.LookupKeyConfig::supportedFilters)
				.orElse(Set.of());
	}

}
// SDD-PROVENANCE: U-011 | vault: .mega-sdd/vaults/acquisition-master-data | LookupService @Service — E30 getLookup: validate key (404 LOOKUP_KEY_UNKNOWN), applicant_type set-membership eksplisit (BR-BE07-12 fix EC5), default active-only (BR-BE07-21), LookupSourceUnavailableException propagates (503 BR-BE07-22); nested LookupItem + LookupResult records; LookupKeyUnknownException
