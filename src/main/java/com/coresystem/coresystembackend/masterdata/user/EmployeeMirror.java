package com.coresystem.coresystembackend.masterdata.user;

import java.time.LocalDate;

import com.coresystem.coresystembackend.masterdata.common.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Tier B synced-mirror entity for HR employee data (BE-07 §3.1, F-U-003).
 *
 * <p>Mirrors the HR system's employee record into the acquisition database as a read-only copy.
 * The HR system is the system-of-record (BR-EMPLOYEE-1); the only writes to this table come from
 * {@link EmployeeMirrorSyncJob} (one-way sync). There are no application-layer write endpoints —
 * {@link EmployeeMirrorController} is GET-only.
 *
 * <h2>Why a mirror, not a direct HR query</h2>
 * The acquisition engine needs employee data (NIK, name, branch, position, resign status) for
 * user provisioning (U-003) and the PIC picker (U-008) without coupling to the HR system's
 * availability. A local mirror lets these flows work even when HR is unreachable, and the sync
 * job is the single write path that reconciles drift.
 *
 * <h2>{@code isResigned} — the Edge Case 12 fix</h2>
 * The legacy {@code vw_HREmployeeData} view commented out the {@code IsActive} filter, silently
 * returning resigned employees as if they were active. This entity makes {@code is_resigned}
 * ({@code Fkeluar}) a first-class boolean column that is ALWAYS present in API responses, so the
 * caller can never confuse a resigned employee with an active one (BR-BE07-22: "resigned",
 * "not found", and "source error" are three distinct signals).
 *
 * <h2>No {@code @Version}</h2>
 * This is a Tier B read-only table — there are no concurrent application-layer edits to this
 * entity, so optimistic locking ({@code @Version}) is not needed. The sync job is the sole writer
 * and runs serially. {@link AuditableEntity} provides the four audit columns
 * ({@code created_at/created_by/updated_at/updated_by}) that every {@code mst_} table must carry
 * (DB-CONVENTIONS §4); the sync job populates them.
 *
 * <h2>Legacy mapping (BE-07 §3.1 census)</h2>
 * <table>
 *   <caption>Legacy → target column mapping</caption>
 *   <tr><th>Field</th><th>Legacy source</th><th>Column</th></tr>
 *   <tr><td>{@code nik}</td><td>HR NIK</td><td>{@code nik} (unique, business key {@code ux_mst_employee_mirror_nik})</td></tr>
 *   <tr><td>{@code name}</td><td>HR employee name</td><td>{@code name}</td></tr>
 *   <tr><td>{@code branchId}</td><td>{@code KdCabang}</td><td>{@code branch_id}</td></tr>
 *   <tr><td>{@code positionId}</td><td>{@code KdJabat}</td><td>{@code position_id}</td></tr>
 *   <tr><td>{@code nationalId}</td><td>{@code NoKtp}</td><td>{@code national_id} VARCHAR(16) [LOCKED]</td></tr>
 *   <tr><td>{@code isResigned}</td><td>{@code Fkeluar}</td><td>{@code is_resigned} BOOLEAN</td></tr>
 *   <tr><td>{@code employeeStatus}</td><td>{@code Stpegawai}</td><td>{@code employee_status}</td></tr>
 *   <tr><td>{@code joinDate}</td><td>{@code Tglmasuk}</td><td>{@code join_date}</td></tr>
 *   <tr><td>{@code exitDate}</td><td>{@code Tglkeluar}</td><td>{@code exit_date}</td></tr>
 * </table>
 *
 * <p>{@code national_id} is {@code VARCHAR(16)} and {@code [LOCKED]} per DB-CONVENTIONS §3 (NIK —
 * national identity number; zero-diff, never reformatted).
 */
@Entity
@Table(name = "mst_employee_mirror")
public class EmployeeMirror extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** HR employee NIK — business key ({@code ux_mst_employee_mirror_nik}), unique. */
	@Column(name = "nik", nullable = false, unique = true)
	private String nik;

	/** Employee full name from HR. */
	@Column(name = "name", nullable = false)
	private String name;

	/** Legacy branch code ({@code KdCabang}) — the employee's assigned branch. */
	@Column(name = "branch_id")
	private String branchId;

	/** Legacy position code ({@code KdJabat}) — the employee's job position. */
	@Column(name = "position_id")
	private String positionId;

	/**
	 * National identity number ({@code NoKtp}) — {@code VARCHAR(16)} {@code [LOCKED]} per
	 * DB-CONVENTIONS §3. Never reformatted; zero-diff from HR source.
	 */
	@Column(name = "national_id", length = 16)
	private String nationalId;

	/**
	 * Whether the employee has resigned ({@code Fkeluar}). Explicit boolean — the Edge Case 12 fix:
	 * the legacy {@code vw_HREmployeeData} view commented out the {@code IsActive} filter, silently
	 * surfacing resigned employees as active. This field is ALWAYS present in API responses so the
	 * caller can distinguish active from resigned (BR-BE07-22).
	 */
	@Column(name = "is_resigned", nullable = false)
	private boolean isResigned;

	/** Legacy employee status code ({@code Stpegawai}). */
	@Column(name = "employee_status")
	private String employeeStatus;

	/** Employee join date ({@code Tglmasuk}). */
	@Column(name = "join_date")
	private LocalDate joinDate;

	/** Employee exit/resignation date ({@code Tglkeluar}); null if not resigned. */
	@Column(name = "exit_date")
	private LocalDate exitDate;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getNik() {
		return nik;
	}

	public void setNik(String nik) {
		this.nik = nik;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getBranchId() {
		return branchId;
	}

	public void setBranchId(String branchId) {
		this.branchId = branchId;
	}

	public String getPositionId() {
		return positionId;
	}

	public void setPositionId(String positionId) {
		this.positionId = positionId;
	}

	public String getNationalId() {
		return nationalId;
	}

	public void setNationalId(String nationalId) {
		this.nationalId = nationalId;
	}

	public boolean isResigned() {
		return isResigned;
	}

	public void setResigned(boolean isResigned) {
		this.isResigned = isResigned;
	}

	public String getEmployeeStatus() {
		return employeeStatus;
	}

	public void setEmployeeStatus(String employeeStatus) {
		this.employeeStatus = employeeStatus;
	}

	public LocalDate getJoinDate() {
		return joinDate;
	}

	public void setJoinDate(LocalDate joinDate) {
		this.joinDate = joinDate;
	}

	public LocalDate getExitDate() {
		return exitDate;
	}

	public void setExitDate(LocalDate exitDate) {
		this.exitDate = exitDate;
	}

}
// SDD-PROVENANCE: U-002 | vault: .mega-sdd/vaults/acquisition-master-data | EmployeeMirror @Entity (mst_employee_mirror, Tier B read-only) extends AuditableEntity (no @Version); isResigned boolean explicit (Edge Case 12 fix — Fkeluar, not comment-out IsActive); nationalId VARCHAR(16) [LOCKED]; legacy mapping KdCabang/KdJabat/NoKtp/Stpegawai/Tglmasuk/Tglkeluar
