package com.coresystem.coresystembackend.masterdata.user;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.coresystem.coresystembackend.masterdata.common.PageResponse;

/**
 * E8 controller for the HR employee mirror picker (F-U-003, Tier B read-only).
 *
 * <p>Exposes a single endpoint shape: {@code GET /employees}. This is the HR picker used by
 * master-data admins to look up employees for user provisioning (U-003) and by hierarchy admins
 * for the PIC picker (U-008). The endpoint is <strong>read-only</strong> — there are no
 * {@code POST}/{@code PUT}/{@code DELETE} methods because HR is the system-of-record
 * (BR-EMPLOYEE-1); the only writes to {@code mst_employee_mirror} come from
 * {@link EmployeeMirrorSyncJob}.
 *
 * <h2>Two query modes</h2>
 * <ul>
 *   <li><strong>Search</strong> — {@code GET /employees?search=NIK00&page=0&size=20}: case-insensitive
 *       NIK substring search, filtered to active (non-resigned) employees. Returns a
 *       {@link PageResponse} of {@link EmployeeMirrorDto} items with {@code is_resigned} explicit
 *       on every item (fix Edge Case 12 — the legacy {@code vw_HREmployeeData} commented out the
 *       {@code IsActive} filter; here {@code is_resigned} is always present in the response).</li>
 *   <li><strong>Single lookup by NIK</strong> — {@code GET /employees/{nik}}: returns the employee
 *       (including resigned ones) or {@code 404 EMPLOYEE_NOT_FOUND} when the NIK is not in the
 *       mirror. BR-BE07-22: "not found" is a distinct signal — never silent-success-empty.</li>
 * </ul>
 *
 * <h2>BR-BE07-22 — three distinct signals</h2>
 * The controller never conflates these:
 * <ul>
 *   <li><strong>Resigned</strong> — the employee exists with {@code is_resigned=true} (returned in
 *       the body, not filtered out for single-lookup).</li>
 *   <li><strong>Not found</strong> — {@code 404 EMPLOYEE_NOT_FOUND} (the NIK is absent from the
 *       mirror).</li>
 *   <li><strong>Source error</strong> — {@code 503 LOOKUP_SOURCE_UNAVAILABLE} (HR source
 *       unreachable; handled by a future integration layer when the sync transport OQ-BE07-02
 *       lands).</li>
 * </ul>
 *
 * <p>Authentication/authorization (Credit Admin / Hierarchy Admin) is enforced by the security
 * filter chain ({@code SecurityConfig}), not by this controller. The {@code /employees} path is
 * not in the {@code permitAll} list, so it requires an authenticated request.
 */
@RestController
@RequestMapping("/employees")
public class EmployeeMirrorController {

	private final EmployeeMirrorRepository employeeMirrorRepository;

	public EmployeeMirrorController(EmployeeMirrorRepository employeeMirrorRepository) {
		this.employeeMirrorRepository = employeeMirrorRepository;
	}

	/**
	 * E8 search: list/search employees by NIK substring (active only by default).
	 *
	 * <p>Returns a {@link PageResponse} of {@link EmployeeMirrorDto} with {@code is_resigned}
	 * explicit on every item. The search filters to non-resigned employees (the
	 * {@code isResignedFalse} qualifier in the repository query name), but {@code is_resigned} is
	 * still surfaced in the response so the caller sees the explicit value (always {@code false}
	 * for this query).
	 *
	 * @param search NIK substring to search for (case-insensitive); if blank, returns all active
	 *               employees (the repository query matches any NIK containing the empty string)
	 * @param page   zero-based page number (default 0)
	 * @param size   page size (default 20)
	 * @return a {@link PageResponse} of {@link EmployeeMirrorDto}
	 */
	@GetMapping
	public PageResponse<EmployeeMirrorDto> search(
			@RequestParam(name = "search", defaultValue = "") String search,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size) {

		Pageable pageable = PageRequest.of(page, size);
		Page<EmployeeMirror> result = employeeMirrorRepository
				.findByNikContainingIgnoreCaseAndIsResignedFalse(search, pageable);

		Page<EmployeeMirrorDto> dtoPage = result.map(EmployeeMirrorDto::from);
		return PageResponse.of(dtoPage, page, size);
	}

	/**
	 * E8 single-employee lookup by NIK.
	 *
	 * <p>Returns the employee (including resigned ones — {@code is_resigned} is explicit in the
	 * response so the caller can distinguish). Returns {@code 404 EMPLOYEE_NOT_FOUND} when the NIK
	 * is absent from the mirror (BR-BE07-22: "not found" is a distinct signal, NOT empty 200).
	 *
	 * @param nik the HR employee NIK (business key)
	 * @return 200 with the employee DTO, or 404 with an error body
	 */
	@GetMapping("/{nik}")
	public ResponseEntity<?> getByNik(@PathVariable String nik) {
		Optional<EmployeeMirror> employee = employeeMirrorRepository.findByNik(nik);
		if (employee.isEmpty()) {
			// BR-BE07-22: not-found → 404 EMPLOYEE_NOT_FOUND (not silent-success-empty).
			return ResponseEntity
					.status(HttpStatus.NOT_FOUND)
					.body(Map.of("error", "EMPLOYEE_NOT_FOUND", "nik", nik));
		}
		return ResponseEntity.ok(EmployeeMirrorDto.from(employee.get()));
	}

	/**
	 * Response DTO for the E8 employee picker. Carries {@code is_resigned} explicitly (Edge Case 12
	 * fix) so the caller can always distinguish active from resigned employees (BR-BE07-22).
	 *
	 * <p>Field names are snake_case via {@link JsonProperty @JsonProperty} to match the BR-BE07-20
	 * response convention without relying on a global Jackson naming strategy (the app does not
	 * configure one). The DTO is a flat projection of {@link EmployeeMirror} — it excludes audit
	 * columns ({@code created_at/created_by/updated_at/updated_by}) which are internal.
	 *
	 * <p>Nested inside the controller because it is tightly coupled to this single endpoint shape
	 * and is not shared by other controllers. If a later unit needs the same projection, it can be
	 * promoted to a top-level record.
	 *
	 * @param id             the database PK
	 * @param nik            the HR employee NIK (business key)
	 * @param name           employee full name
	 * @param branchId       legacy branch code ({@code KdCabang})
	 * @param positionId     legacy position code ({@code KdJabat})
	 * @param nationalId     national identity number ({@code NoKtp}), VARCHAR(16) [LOCKED]
	 * @param isResigned     whether the employee has resigned ({@code Fkeluar}) — ALWAYS present
	 * @param employeeStatus legacy status code ({@code Stpegawai})
	 * @param joinDate       join date ({@code Tglmasuk})
	 * @param exitDate       exit date ({@code Tglkeluar}); null if not resigned
	 */
	public record EmployeeMirrorDto(
			Long id,
			String nik,
			String name,
			@JsonProperty("branch_id") String branchId,
			@JsonProperty("position_id") String positionId,
			@JsonProperty("national_id") String nationalId,
			@JsonProperty("is_resigned") boolean isResigned,
			@JsonProperty("employee_status") String employeeStatus,
			@JsonProperty("join_date") LocalDate joinDate,
			@JsonProperty("exit_date") LocalDate exitDate) {

		/**
		 * Map an {@link EmployeeMirror} entity to the response DTO.
		 *
		 * @param emp the entity
		 * @return the DTO with {@code is_resigned} explicit
		 */
		public static EmployeeMirrorDto from(EmployeeMirror emp) {
			return new EmployeeMirrorDto(
					emp.getId(),
					emp.getNik(),
					emp.getName(),
					emp.getBranchId(),
					emp.getPositionId(),
					emp.getNationalId(),
					emp.isResigned(),
					emp.getEmployeeStatus(),
					emp.getJoinDate(),
					emp.getExitDate());
		}
	}

}
// SDD-PROVENANCE: U-002 | vault: .mega-sdd/vaults/acquisition-master-data | EmployeeMirrorController @RestController (/employees) — E8 GET search (PageResponse<EmployeeMirrorDto>, is_resigned explicit) + GET /{nik} (404 EMPLOYEE_NOT_FOUND BR-BE07-22); read-only (no POST/PUT/DELETE, BR-EMPLOYEE-1 HR system-of-record); nested EmployeeMirrorDto record with @JsonProperty snake_case
