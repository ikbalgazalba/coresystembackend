package com.coresystem.coresystembackend.masterdata.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Spring Data JPA repository for {@link EmployeeMirror} (Tier B read-only mirror).
 *
 * <p>Exposes read-only finders for the E8 HR picker ({@link EmployeeMirrorController}). No
 * custom write methods are declared — the only write path is {@link EmployeeMirrorSyncJob}
 * (BR-EMPLOYEE-1: HR is the system-of-record; the mirror is written exclusively by the sync job).
 *
 * <p>Finders:
 * <ul>
 *   <li>{@link #findByNik(String)} — single-employee lookup by NIK business key. Used by E8
 *       single-employee detail and by user provisioning (U-003) to validate a NIK exists and is
 *       not resigned. Returns {@link Optional#empty()} when the NIK is not in the mirror — the
 *       controller translates that to {@code 404 EMPLOYEE_NOT_FOUND} (BR-BE07-22: "not found" is
 *       a distinct signal, NOT silent-success-empty).</li>
 *   <li>{@link #findByNikContainingIgnoreCaseAndIsResignedFalse(String, Pageable)} — E8 search:
 *       case-insensitive NIK substring match, filtered to active (non-resigned) employees only.
 *       The default picker view excludes resigned employees (the caller does not need to pass
 *       {@code is_resigned=false}; it is baked into the query). {@code is_resigned} is still
 *       surfaced in the response so the caller sees the explicit value.</li>
 * </ul>
 */
public interface EmployeeMirrorRepository extends JpaRepository<EmployeeMirror, Long> {

	/**
	 * Find a single employee by NIK (business key).
	 *
	 * @param nik the HR employee NIK (unique business key)
	 * @return the employee, or {@link Optional#empty()} if not found in the mirror
	 */
	Optional<EmployeeMirror> findByNik(String nik);

	/**
	 * E8 search: case-insensitive NIK substring match, active (non-resigned) employees only.
	 *
	 * <p>The {@code isResignedFalse} filter is baked into the query name so the default picker view
	 * never surfaces resigned employees. The {@code is_resigned} value is still present in the
	 * response (always {@code false} for this query) so the caller sees the explicit signal.
	 *
	 * @param nik the NIK substring to search for (case-insensitive)
	 * @param pageable pagination information
	 * @return a page of active employees whose NIK contains the search string
	 */
	Page<EmployeeMirror> findByNikContainingIgnoreCaseAndIsResignedFalse(String nik, Pageable pageable);

	/**
	 * E28 PIC picker search: case-insensitive name OR NIK substring match, active (non-resigned)
	 * employees only. Used by {@link com.coresystem.coresystembackend.masterdata.transtype.HierarchyMatrixService#searchPicCandidates}
	 * to populate the PIC picker dropdown when building an approval hierarchy.
	 *
	 * <p>The {@code isResignedFalse} filter is baked into the query name so the default picker view
	 * never surfaces resigned employees (BR-BE07-17 guard — a resigned employee cannot be a PIC).
	 *
	 * <p>Job-title filter is deferred (stub) — the current implementation searches by name/NIK only.
	 * A future unit may add a position-id filter parameter.
	 *
	 * @param name the name substring to search for (case-insensitive)
	 * @param nik the NIK substring to search for (case-insensitive)
	 * @param pageable pagination information
	 * @return a page of active employees whose name or NIK contains the search string
	 */
	Page<EmployeeMirror> findByNameContainingIgnoreCaseOrNikContainingIgnoreCaseAndIsResignedFalse(
			String name, String nik, Pageable pageable);

}
// SDD-PROVENANCE: U-002 | vault: .mega-sdd/vaults/acquisition-master-data | EmployeeMirrorRepository extends JpaRepository — findByNik (business key lookup) + findByNikContainingIgnoreCaseAndIsResignedFalse (E8 active-only search); Tier B read-only (no custom write methods, sync job is sole writer BR-EMPLOYEE-1)
