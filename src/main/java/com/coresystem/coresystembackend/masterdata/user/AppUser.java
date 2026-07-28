package com.coresystem.coresystembackend.masterdata.user;

import java.time.Instant;

import com.coresystem.coresystembackend.masterdata.common.VersionedEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * Tier A application-user entity (BE-07 §3.1, constitution §I-004/§I-005, F-U-001 E1-E5).
 *
 * <p>Maps {@code mst_user} — the RBAC user table for the acquisition module. This is
 * <strong>distinct</strong> from the jwt-login vault's {@code Users}/{@code mojf_users}
 * (binding coexistence note): {@code AppUser} carries the D-10 role enum and the
 * branch-scope list, while {@code Users} is the legacy auth table. The two coexist without
 * conflict.
 *
 * <h2>Key design rules</h2>
 * <ul>
 *   <li><strong>NO {@code password} field</strong> (BR-SHELL-1 [LOCKED]) — authentication is
 *       delegated to LDAP via the corporate directory. The app never stores or compares a
 *       password hash.</li>
 *   <li><strong>NO {@code isSuperUser}</strong> (D-09) — the super-user concept is eliminated.
 *       The role enum D-10 ({@link Role}) is the only authorization mechanism, and it has no
 *       super-user value.</li>
 *   <li><strong>{@code employeeNik}</strong> — FK to {@link EmployeeMirror#nik}; unique
 *       ({@code ux_mst_user_employee_nik}). Provisioning validates the NIK exists in the
 *       mirror and is not resigned (BR-BE07-02).</li>
 *   <li><strong>{@code role}</strong> — {@link Role} enum, persisted as {@code VARCHAR} via
 *       {@link EnumType#STRING @Enumerated(STRING)}. The D-10 {@code @Check} constraint lives
 *       at the DB level; the Java enum is the application-layer gate.</li>
 *   <li><strong>{@code deactivationReason}</strong> — enum {@code manual|hr_resigned}. When
 *       the HR mirror sync detects a resignation, the listener sets this to
 *       {@code hr_resigned} and the user cannot be reactivated (BR-BE07-27).</li>
 * </ul>
 *
 * <h2>Lifecycle state machine</h2>
 * <pre>
 *   active → inactive(manual)      [E5 deactivate]
 *   active → inactive(hr_resigned) [BR-BE07-27 auto-deactivate]
 *   inactive(manual) → active      [E5 reactivate, if mirror !isResigned]
 *   inactive(hr_resigned) → ✗      [cannot reactivate — 409 if mirror resigned]
 * </pre>
 *
 * <p>Extends {@link VersionedEntity} — audit columns ({@code created_at/created_by/
 * updated_at/updated_by}) + {@code @Version} optimistic lock (DB-CONVENTIONS §4). The
 * {@code mst_user} table participates in concurrent edits (role/scope changes by multiple
 * admins), so optimistic locking is required.
 *
 * <p>Per constitution §C-002, controllers return DTOs, never the raw entity. The entity is
 * the persistence layer's concern; the controller maps it to a response DTO.
 */
@Entity
@Table(name = "mst_user")
public class AppUser extends VersionedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/**
	 * Employee NIK — business key ({@code ux_mst_user_employee_nik}), unique. Logical FK to
	 * {@link EmployeeMirror#nik}. Provisioning validates existence + non-resigned
	 * (BR-BE07-02).
	 */
	@Column(name = "employee_nik", nullable = false, unique = true)
	private String employeeNik;

	/** D-10 role enum (constitution §I-004). Persisted as VARCHAR via @Enumerated(STRING). */
	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false)
	private Role role;

	/** Company identifier — scopes the user to a company context. */
	@Column(name = "company_id")
	private String companyId;

	/** Whether the user is active. Lifecycle toggle (BR-BE07-03 deactivate-only). */
	@Column(name = "is_active", nullable = false)
	private boolean isActive;

	/**
	 * Why the user was deactivated: {@code manual} (E5) or {@code hr_resigned} (BR-BE07-27
	 * auto-deactivate). {@code null} while active.
	 */
	@Enumerated(EnumType.STRING)
	@Column(name = "deactivation_reason")
	private DeactivationReason deactivationReason;

	/** When the user was activated (initial provisioning or last reactivation). */
	@Column(name = "activation_date")
	private Instant activationDate;

	/** When the user was deactivated. {@code null} while active. */
	@Column(name = "deactivation_date")
	private Instant deactivationDate;

	/**
	 * Multi-branch scope (OQ-BE07-04 resolved — list supports both single and multi-branch).
	 * Default is a single branch; the list can grow to multiple. Managed via E4 PATCH.
	 */
	@OneToMany(mappedBy = "userId", cascade = CascadeType.ALL, orphanRemoval = true)
	private java.util.List<AppUserBranchScope> branchScopes;

	/**
	 * Deactivation reason enum (BR-BE07-27). {@code manual} = admin-initiated E5;
	 * {@code hr_resigned} = auto-deactivate from HR sync event. Users deactivated with
	 * {@code hr_resigned} cannot be reactivated.
	 */
	public enum DeactivationReason {
		manual,
		hr_resigned
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getEmployeeNik() {
		return employeeNik;
	}

	public void setEmployeeNik(String employeeNik) {
		this.employeeNik = employeeNik;
	}

	public Role getRole() {
		return role;
	}

	public void setRole(Role role) {
		this.role = role;
	}

	public String getCompanyId() {
		return companyId;
	}

	public void setCompanyId(String companyId) {
		this.companyId = companyId;
	}

	public boolean isActive() {
		return isActive;
	}

	public void setActive(boolean isActive) {
		this.isActive = isActive;
	}

	public DeactivationReason getDeactivationReason() {
		return deactivationReason;
	}

	public void setDeactivationReason(DeactivationReason deactivationReason) {
		this.deactivationReason = deactivationReason;
	}

	public Instant getActivationDate() {
		return activationDate;
	}

	public void setActivationDate(Instant activationDate) {
		this.activationDate = activationDate;
	}

	public Instant getDeactivationDate() {
		return deactivationDate;
	}

	public void setDeactivationDate(Instant deactivationDate) {
		this.deactivationDate = deactivationDate;
	}

	public java.util.List<AppUserBranchScope> getBranchScopes() {
		return branchScopes;
	}

	public void setBranchScopes(java.util.List<AppUserBranchScope> branchScopes) {
		this.branchScopes = branchScopes;
	}

}
// SDD-PROVENANCE: U-003 | vault: .mega-sdd/vaults/acquisition-master-data | AppUser @Entity mst_user extends VersionedEntity; employeeNik unique FK→EmployeeMirror.nik; role Role enum D-10 @Enumerated STRING; NO password (BR-SHELL-1); NO isSuperUser (D-09); deactivationReason manual|hr_resigned (BR-BE07-27); branchScopes OneToMany (OQ-BE07-04 multi-branch); isActive lifecycle toggle
