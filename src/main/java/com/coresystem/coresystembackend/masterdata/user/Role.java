package com.coresystem.coresystembackend.masterdata.user;

/**
 * Closed role enum D-10 (constitution §I-004, BR-BE07-01 [LOCKED]).
 *
 * <p>Exactly five values — no more, no less:
 * <ul>
 *   <li>{@link #CMO}</li>
 *   <li>{@link #MARKETING_HEAD}</li>
 *   <li>{@link #CREDIT_ANALYST}</li>
 *   <li>{@link #KEPALA_CABANG}</li>
 *   <li>{@link #CREDIT_ADMIN}</li>
 * </ul>
 *
 * <p><strong>No {@code SUPER_USER}</strong> (D-09). The legacy {@code ms_trans_super_user}
 * table is read-only for migration audit only; the super-user concept is eliminated from
 * every entity, endpoint, and grant path. Any attempt to provision a user with
 * {@code SUPER_USER} must be rejected with {@code 422 UNKNOWN_ROLE} (AC-2).
 *
 * <p>Persisted as {@code VARCHAR} + {@code @Check} constraint in the database
 * (DB-CONVENTIONS §3 enum kecil pattern). The {@code mst_role} table is NOT created while
 * the enum is closed (D-MD-04). If OQ-BE07-03 decides to expand to HO roles with
 * governance, the enum will be promoted to a {@code mst_role} table — never extended by
 * silently adding a value here.
 *
 * <p>The {@link #catalog()} method returns the immutable static catalog used by E7
 * {@code GET /roles}. It is a defensive copy so callers cannot mutate the canonical array.
 */
public enum Role {

	CMO,
	MARKETING_HEAD,
	CREDIT_ANALYST,
	KEPALA_CABANG,
	CREDIT_ADMIN;

	/** Canonical D-10 catalog. Shared immutable backing array. */
	private static final Role[] CATALOG = values();

	/**
	 * Static D-10 catalog for E7 {@code GET /roles}.
	 *
	 * <p>Returns a defensive copy so callers cannot mutate the canonical array. The catalog
	 * is static and closed (D-10) — it never changes at runtime; there is no {@code mst_role}
	 * table (D-MD-04).
	 *
	 * @return a defensive copy of the D-10 role catalog
	 */
	public static Role[] catalog() {
		return CATALOG.clone();
	}

	/**
	 * Parse a role name, rejecting values outside D-10 (including {@code SUPER_USER}).
	 *
	 * <p>This is the single validation gate for the provisioning endpoint (E2). Any string
	 * that does not match a D-10 enum constant — including {@code SUPER_USER}, typos, or
	 * unknown roles — returns {@link Optional#empty()}, which the controller translates to
	 * {@code 422 UNKNOWN_ROLE} (D-09, AC-2). Using {@link Enum#valueOf} directly would throw
	 * {@link IllegalArgumentException}, which is harder to distinguish from genuine
	 * programming errors; this method returns a clean empty signal.
	 *
	 * @param name the role name to parse (case-sensitive, must match exactly)
	 * @return the matching {@link Role}, or {@link Optional#empty()} if not a D-10 value
	 */
	public static java.util.Optional<Role> fromName(String name) {
		if (name == null) {
			return java.util.Optional.empty();
		}
		for (Role role : CATALOG) {
			if (role.name().equals(name)) {
				return java.util.Optional.of(role);
			}
		}
		return java.util.Optional.empty();
	}

}
// SDD-PROVENANCE: U-003 | vault: .mega-sdd/vaults/acquisition-master-data | Role enum D-10 [LOCKED] — CMO, MARKETING_HEAD, CREDIT_ANALYST, KEPALA_CABANG, CREDIT_ADMIN; NO SUPER_USER (D-09); VARCHAR+CHECK DB-CONVENTIONS §3; no mst_role table (D-MD-04); catalog() for E7 + fromName() validation gate
