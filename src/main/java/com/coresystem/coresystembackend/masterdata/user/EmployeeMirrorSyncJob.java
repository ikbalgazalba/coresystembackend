package com.coresystem.coresystembackend.masterdata.user;

import org.springframework.stereotype.Component;

/**
 * HR sync job stub for the {@link EmployeeMirror} Tier B mirror (BE-07 §3.1, BR-EMPLOYEE-1).
 *
 * <p>This is a <strong>stub</strong> — the real HR sync transport (batch extract, CDC, or HR API)
 * is undecided and tracked by <strong>OQ-BE07-02</strong>. The {@link #syncFromHr()} method throws
 * {@link UnsupportedOperationException} to make the TBD state explicit and prevent any silent fake
 * implementation. When OQ-BE07-02 is resolved, replace the stub body with the real sync logic.
 *
 * <h2>Sync contract (for the future implementer)</h2>
 * When the transport is decided, the sync job will:
 * <ul>
 *   <li>Pull employee data from the HR source (batch/CDC/API — TBD OQ-BE07-02).</li>
 *   <li>Upsert into {@code mst_employee_mirror}: set {@code isResigned} from HR {@code Fkeluar}
 *       (the Edge Case 12 fix — never comment-out the active filter like the legacy
 *       {@code vw_HREmployeeData} did with {@code IsActive}).</li>
 *   <li>Populate audit columns ({@code created_at/created_by/updated_at/updated_by}) per
 *       DB-CONVENTIONS §4; the sync job is the sole writer (BR-EMPLOYEE-1).</li>
 *   <li>Fire an event when an employee transitions to resigned, so U-003's listener can
 *       auto-deactivate the linked {@code APP_USER} (BR-BE07-27:
 *       {@code inactive(hr_resigned)} — non-reactivatable).</li>
 * </ul>
 *
 * <p>The event-publishing and auto-deactivate wiring is out of scope for U-002 (it lands in U-003);
 * this stub only establishes the interface and the explicit TBD marker.
 */
@Component
public class EmployeeMirrorSyncJob {

	/**
	 * Sync employee data from the HR system into {@code mst_employee_mirror}.
	 *
	 * <p><strong>STUB — TBD OQ-BE07-02.</strong> The HR sync transport (batch/CDC/API) has not been
	 * decided yet. This method throws {@link UnsupportedOperationException} to make the undecided
	 * state explicit — it does NOT silently fake a sync. When OQ-BE07-02 is resolved, replace this
	 * body with the real sync implementation.
	 *
	 * @throws UnsupportedOperationException always — OQ-BE07-02 HR sync transport not yet decided
	 */
	public void syncFromHr() {
		// TBD OQ-BE07-02: HR sync transport (batch/CDC/API) not yet decided.
		// Do NOT implement a silent fake — the stub must throw until the transport is chosen.
		throw new UnsupportedOperationException("OQ-BE07-02 HR sync transport not yet decided");
	}

}
// SDD-PROVENANCE: U-002 | vault: .mega-sdd/vaults/acquisition-master-data | EmployeeMirrorSyncJob @Component stub — syncFromHr() throws UnsupportedOperationException (OQ-BE07-02 HR sync transport undecided); future: upsert isResigned from Fkeluar + fire resigned event for U-003 auto-deactivate (BR-BE07-27)
