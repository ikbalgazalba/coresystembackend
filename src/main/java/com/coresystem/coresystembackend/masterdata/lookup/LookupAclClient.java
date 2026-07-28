package com.coresystem.coresystembackend.masterdata.lookup;

import java.util.List;
import java.util.Map;

/**
 * ACL adapter to the {@code FC_MSTAPP_MCF} backing store (Tier C external read-only master).
 *
 * <p>This is the service-boundary adapter mandated by BR-BE07-26: Tier C masters are accessed
 * read-only via an ACL service API, NOT via cross-DB three-part-name / linked-server four-part-name
 * joins. The transport is an open question (OQ-ARCH-STACK — the real adapter topology is decided
 * by ITEC decision D-11); until then, {@link StubLookupAclClient} is wired and always throws
 * {@link LookupSourceUnavailableException} so that every registered lookup key returns
 * {@code 503 LOOKUP_SOURCE_UNAVAILABLE} rather than silent-success-empty (BR-BE07-22,
 * fix EC1/EC12/fuel).
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>{@link #fetch(String, Map)} returns a list of {@link LookupService.LookupItem} for the
 *       given key, filtered by the supplied filters on the ACL side.</li>
 *   <li>If the backing store is unreachable (network, ACL denied, transport error), the
 *       implementation throws {@link LookupSourceUnavailableException} — it NEVER returns an
 *       empty list to signal an error (BR-BE07-22 distinct-signal rule).</li>
 *   <li>The adapter MUST strip/exclude {@code MsBank.PasscodeBiBca} (BR-BE07-25
 *       REDACTED-SECRET — never expose credential-shaped fields on any endpoint).</li>
 * </ul>
 *
 * <p>Filter keys passed to {@code fetch} are the raw query-parameter names
 * ({@code applicant_type}, {@code parent_id}, {@code branch_id}). The service layer applies
 * the {@code applicant_type} set-membership filter and the {@code include_inactive} toggle
 * <em>after</em> the fetch (so the ACL client receives the upstream items and the service
 * filters in-memory); the ACL client is free to push additional server-side filters down to the
 * backing store if the transport supports it.
 */
public interface LookupAclClient {

	/**
	 * Fetch lookup items from the ACL backing store.
	 *
	 * @param key the registered lookup key (e.g. {@code identity-type})
	 * @param filters the query filters to apply (may be empty; never null)
	 * @return a list of lookup items (may be empty if the backing store genuinely has zero rows)
	 * @throws LookupSourceUnavailableException if the backing store is unreachable or the ACL
	 *         denies access (BR-BE07-22 — NEVER return empty to signal an error)
	 */
	List<LookupService.LookupItem> fetch(String key, Map<String, String> filters);

	/**
	 * Unchecked exception thrown when the ACL backing store is unreachable.
	 *
	 * <p>Mapped to HTTP {@code 503 LOOKUP_SOURCE_UNAVAILABLE} by the controller layer
	 * (BR-BE07-22 — fix EC1/EC12/fuel silent-success). Carries no sensitive detail to the
	 * client; the message is for server-side logging only.
	 */
	class LookupSourceUnavailableException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		/**
		 * Construct with a server-side detail message.
		 *
		 * @param message the detail message (server-side only; not exposed to the client)
		 */
		public LookupSourceUnavailableException(String message) {
			super(message);
		}

		/**
		 * Construct with a detail message and cause.
		 *
		 * @param message the detail message (server-side only)
		 * @param cause the underlying transport/ACL error
		 */
		public LookupSourceUnavailableException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	/**
	 * Stub implementation that always throws {@link LookupSourceUnavailableException}.
	 *
	 * <p>Wired as the default {@code @Component} until the real ACL transport lands
	 * (OQ-ARCH-STACK — real adapter TBD ITEC D-11). This guarantees that every registered lookup
	 * key returns {@code 503} rather than silent-success-empty (BR-BE07-22), and that the
	 * 8 absent objects (OQ-EXTMASTERS-07) surface as {@code 503} if referenced.
	 *
	 * <p>This stub does NOT implement any cross-DB three-part-name or linked-server four-part-name
	 * access (BR-BE07-26 — the real adapter will use an ACL service API, not a cross-DB join).
	 */
	@org.springframework.stereotype.Component
	class StubLookupAclClient implements LookupAclClient {

		/**
		 * Always throws — the real ACL transport is not yet implemented.
		 *
		 * @throws LookupSourceUnavailableException always
		 */
		@Override
		public List<LookupService.LookupItem> fetch(String key, Map<String, String> filters) {
			throw new LookupSourceUnavailableException(
					"OQ-ARCH-STACK ACL transport not yet implemented");
		}
	}

}
// SDD-PROVENANCE: U-011 | vault: .mega-sdd/vaults/acquisition-master-data | LookupAclClient interface + LookupSourceUnavailableException (503 BR-BE07-22) + StubLookupAclClient @Component (OQ-ARCH-STACK always throws); ACL boundary BR-BE07-26; MsBank.PasscodeBiBca REDACTED BR-BE07-25
