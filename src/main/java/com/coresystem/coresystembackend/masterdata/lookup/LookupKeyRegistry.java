package com.coresystem.coresystembackend.masterdata.lookup;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Registry of lookup keys served by the generic E30 endpoint (Tier C via ACL).
 *
 * <p>Maps a {@code lookup_key} (the path variable on {@code GET /lookups/{lookup_key}}) to its
 * ACL query configuration: the backing-store identifier and the set of query filters the key
 * supports. Adding a new lookup key is a <strong>configuration</strong> change here, not a new
 * endpoint — the E30 controller dispatches generically by key (BE-07 §10 urutan (5)).
 *
 * <h2>Initial keys (BE-07 §3.5)</h2>
 * The six initial keys cover the primary acquisition lookup groups:
 * <ul>
 *   <li>{@code marital-status} — marital-status classification (applicant group (a))</li>
 *   <li>{@code identity-type} — identity-type classification (applicant group (a); carries the
 *       applicant-type set-membership mapping fixed by BR-BE07-12/EC5)</li>
 *   <li>{@code economic-sector} — 2-level economic sector (applicant group (a); hierarchical,
 *       accepts {@code parent_id})</li>
 *   <li>{@code bank} — bank &amp; payment master (group (b); {@code MsBank.PasscodeBiBca} is
 *       REDACTED-SECRET per BR-BE07-25 — the ACL adapter must strip/exclude it)</li>
 *   <li>{@code location} — location + OJK crosswalk (group (c); hierarchical, accepts
 *       {@code parent_id})</li>
 *   <li>{@code asset-brand} — product/asset taxonomy (group (d))</li>
 * </ul>
 *
 * <p>All keys map to the {@code FC_MSTAPP_MCF} backing store (the 310-master ACL catalog,
 * BE-07 §3.5), accessed read-only via the ACL service boundary (BR-BE07-26 — NO cross-DB
 * three-part-name / linked-server four-part-name).
 *
 * <h2>Supported filters</h2>
 * Each key declares the set of query-filter parameters it accepts. The E30 controller validates
 * that a requested filter is in this set before passing it to the ACL client. The common filters
 * are: {@code applicant_type}, {@code parent_id}, {@code branch_id}, {@code include_inactive}.
 * Not all keys support all filters (e.g. {@code marital-status} has no hierarchy, so
 * {@code parent_id} is not in its supported set).
 */
@Component
public class LookupKeyRegistry {

	/**
	 * Configuration for a single lookup key.
	 *
	 * @param key the lookup key (path variable on E30)
	 * @param backingStoreId the ACL backing-store identifier (e.g. {@code FC_MSTAPP_MCF})
	 * @param supportedFilters the set of query-filter parameter names this key accepts
	 */
	public record LookupKeyConfig(String key, String backingStoreId, Set<String> supportedFilters) {
	}

	/** The ACL backing store for all Tier C lookups (BE-07 §3.5). */
	static final String BACKING_STORE_FC_MSTAPP_MCF = "FC_MSTAPP_MCF";

	/** Common filter: applicant-type set-membership (BR-BE07-12). */
	static final String FILTER_APPLICANT_TYPE = "applicant_type";
	/** Common filter: hierarchical parent (economic-sector, location). */
	static final String FILTER_PARENT_ID = "parent_id";
	/** Common filter: branch scope. */
	static final String FILTER_BRANCH_ID = "branch_id";
	/** Common filter: include inactive records (admin toggle, BR-BE07-21). */
	static final String FILTER_INCLUDE_INACTIVE = "include_inactive";

	private final Map<String, LookupKeyConfig> keys;

	/**
	 * Construct the registry with the initial set of lookup keys (BE-07 §3.5).
	 */
	public LookupKeyRegistry() {
		this.keys = Map.of(
				"marital-status", new LookupKeyConfig("marital-status",
						BACKING_STORE_FC_MSTAPP_MCF,
						Set.of(FILTER_APPLICANT_TYPE, FILTER_BRANCH_ID, FILTER_INCLUDE_INACTIVE)),
				"identity-type", new LookupKeyConfig("identity-type",
						BACKING_STORE_FC_MSTAPP_MCF,
						Set.of(FILTER_APPLICANT_TYPE, FILTER_BRANCH_ID, FILTER_INCLUDE_INACTIVE)),
				"economic-sector", new LookupKeyConfig("economic-sector",
						BACKING_STORE_FC_MSTAPP_MCF,
						Set.of(FILTER_APPLICANT_TYPE, FILTER_PARENT_ID, FILTER_BRANCH_ID,
								FILTER_INCLUDE_INACTIVE)),
				"bank", new LookupKeyConfig("bank",
						BACKING_STORE_FC_MSTAPP_MCF,
						Set.of(FILTER_BRANCH_ID, FILTER_INCLUDE_INACTIVE)),
				"location", new LookupKeyConfig("location",
						BACKING_STORE_FC_MSTAPP_MCF,
						Set.of(FILTER_PARENT_ID, FILTER_BRANCH_ID, FILTER_INCLUDE_INACTIVE)),
				"asset-brand", new LookupKeyConfig("asset-brand",
						BACKING_STORE_FC_MSTAPP_MCF,
						Set.of(FILTER_BRANCH_ID, FILTER_INCLUDE_INACTIVE)));
	}

	/**
	 * Look up the configuration for a key.
	 *
	 * @param key the lookup key
	 * @return the config, or empty if the key is not registered
	 */
	public Optional<LookupKeyConfig> get(String key) {
		return Optional.ofNullable(keys.get(key));
	}

	/**
	 * Check whether a key is registered.
	 *
	 * @param key the lookup key
	 * @return true if the key is in the registry
	 */
	public boolean isRegistered(String key) {
		return keys.containsKey(key);
	}

}
// SDD-PROVENANCE: U-011 | vault: .mega-sdd/vaults/acquisition-master-data | LookupKeyRegistry @Component — lookup_key → LookupKeyConfig(key, backingStoreId, supportedFilters); 6 initial keys (BE-07 §3.5); FC_MSTAPP_MCF backing store; BR-BE07-26 ACL boundary
