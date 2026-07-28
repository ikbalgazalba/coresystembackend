package com.coresystem.coresystembackend.masterdata.user;

import java.time.Instant;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for APP_USER provisioning and lifecycle (F-U-001, E1-E5).
 *
 * <p>Encapsulates the business rules for creating, activating, deactivating, and
 * reactivating application users. The service is the sole write path for {@code mst_user}
 * — controllers delegate to it, and the HR-resign event listener is wired here
 * (BR-BE07-27 auto-deactivate).
 *
 * <h2>Provisioning (E2)</h2>
 * Validates the employee NIK against the {@link EmployeeMirror} mirror:
 * <ol>
 *   <li>Reject if role is not a D-10 value (including {@code SUPER_USER} →
 *       {@code 422 UNKNOWN_ROLE}, D-09).</li>
 *   <li>Reject if NIK not in mirror → {@code 422 EMPLOYEE_NOT_FOUND}.</li>
 *   <li>Reject if NIK in mirror but resigned → {@code 422 EMPLOYEE_RESIGNED}
 *       (BR-BE07-02, BR-BE07-22 — explicit, not silent-success).</li>
 *   <li>Reject if user already exists → {@code 409 USER_ALREADY_EXISTS}.</li>
 *   <li>Create user with {@code isActive=true}, set audit columns.</li>
 * </ol>
 *
 * <h2>Deactivation / Reactivation (E5)</h2>
 * <ul>
 *   <li>Deactivate: set {@code isActive=false}, {@code deactivationReason=manual},
 *       {@code deactivationDate=now}. If already inactive, returns {@code 409}.</li>
 *   <li>Reactivate: check the mirror — if the employee is resigned, reject with
 *       {@code 409 EMPLOYEE_RESIGNED} (BR-BE07-27 — users deactivated by HR resign
 *       cannot be reactivated). Otherwise set {@code isActive=true},
 *       {@code deactivationReason=null}, {@code activationDate=now}.</li>
 * </ul>
 *
 * <h2>HR resign auto-deactivate (BR-BE07-27)</h2>
 * The {@link #onEmployeeResigned(EmployeeResignedEvent)} listener fires when the HR sync
 * job detects a resignation. It finds the linked {@link AppUser} and auto-deactivates it
 * with {@code deactivationReason=hr_resigned}. This is non-reactivatable.
 *
 * <p>Guarded by {@link ConditionalOnBean @ConditionalOnBean(JpaRepository.class)} to avoid
 * context-load regression in tests that exclude JPA autoconfiguration (same pattern as
 * {@code MasterDataConfig.JpaAuditingConfig}).
 */
@Service
@ConditionalOnBean(JpaRepository.class)
public class AppUserService {

	private final AppUserRepository appUserRepository;
	private final EmployeeMirrorRepository employeeMirrorRepository;

	public AppUserService(AppUserRepository appUserRepository,
			EmployeeMirrorRepository employeeMirrorRepository) {
		this.appUserRepository = appUserRepository;
		this.employeeMirrorRepository = employeeMirrorRepository;
	}

	/**
	 * E2 — Provision a new application user.
	 *
	 * <p>Validation ladder (each rejection is explicit, not silent-success — BR-BE07-22):
	 * <ol>
	 *   <li>Role must be a D-10 value → else {@code 422 UNKNOWN_ROLE} (D-09).</li>
	 *   <li>NIK must exist in the employee mirror → else {@code 422 EMPLOYEE_NOT_FOUND}.</li>
	 *   <li>NIK must not be resigned → else {@code 422 EMPLOYEE_RESIGNED} (BR-BE07-02).</li>
	 *   <li>NIK must not already have a user → else {@code 409 USER_ALREADY_EXISTS}.</li>
	 * </ol>
	 *
	 * @param request the provisioning request (NIK, role, companyId, branchId)
	 * @return the created {@link AppUser}
	 * @throws ProvisioningException if any validation fails (with the appropriate error code)
	 */
	@Transactional
	public AppUser provision(ProvisionRequest request) {
		// 1. Role validation — reject SUPER_USER and any non-D-10 value (D-09, AC-2).
		Role role = Role.fromName(request.role())
				.orElseThrow(() -> new ProvisioningException(
						ErrorCode.UNKNOWN_ROLE, "Role '" + request.role() + "' is not a D-10 role"));

		// 2. NIK must exist in the mirror (BR-BE07-02, BR-BE07-22).
		EmployeeMirror employee = employeeMirrorRepository.findByNik(request.employeeNik())
				.orElseThrow(() -> new ProvisioningException(
						ErrorCode.EMPLOYEE_NOT_FOUND,
						"NIK '" + request.employeeNik() + "' not found in employee mirror"));

		// 3. NIK must not be resigned (BR-BE07-02).
		if (employee.isResigned()) {
			throw new ProvisioningException(
					ErrorCode.EMPLOYEE_RESIGNED,
					"NIK '" + request.employeeNik() + "' is resigned in the employee mirror");
		}

		// 4. Unique check — no duplicate user for the same NIK.
		if (appUserRepository.existsByEmployeeNik(request.employeeNik())) {
			throw new ProvisioningException(
					ErrorCode.USER_ALREADY_EXISTS,
					"User with NIK '" + request.employeeNik() + "' already exists");
		}

		// 5. Create the user.
		Instant now = Instant.now();
		AppUser user = new AppUser();
		user.setEmployeeNik(request.employeeNik());
		user.setRole(role);
		user.setCompanyId(request.companyId());
		user.setActive(true);
		user.setActivationDate(now);
		user.setCreatedAt(now);
		user.setCreatedBy("SYSTEM");
		user.setUpdatedAt(now);
		user.setUpdatedBy("SYSTEM");

		// Branch scope — default single-branch (OQ-BE07-04).
		if (request.branchId() != null && !request.branchId().isBlank()) {
			AppUserBranchScope scope = new AppUserBranchScope();
			scope.setBranchId(request.branchId());
			scope.setCreatedAt(now);
			scope.setCreatedBy("SYSTEM");
			scope.setUpdatedAt(now);
			scope.setUpdatedBy("SYSTEM");
			user.setBranchScopes(new java.util.ArrayList<>(java.util.List.of(scope)));
			// Set the FK on the child — done after save so we have the user ID.
		}

		user = appUserRepository.save(user);

		// Set userId FK on branch scope children (they need the generated user ID).
		if (user.getBranchScopes() != null) {
			for (AppUserBranchScope scope : user.getBranchScopes()) {
				scope.setUserId(user.getId());
			}
		}

		return user;
	}

	/**
	 * E5 — Deactivate a user (admin-initiated).
	 *
	 * <p>Sets {@code isActive=false}, {@code deactivationReason=manual},
	 * {@code deactivationDate=now}. If the user is already inactive, throws
	 * {@code 409 USER_ALREADY_INACTIVE}.
	 *
	 * @param id the user ID
	 * @return the updated {@link AppUser}
	 * @throws ProvisioningException if the user is not found or already inactive
	 */
	@Transactional
	public AppUser deactivate(Long id) {
		AppUser user = appUserRepository.findById(id)
				.orElseThrow(() -> new ProvisioningException(
						ErrorCode.USER_NOT_FOUND, "User " + id + " not found"));

		if (!user.isActive()) {
			throw new ProvisioningException(
					ErrorCode.USER_ALREADY_INACTIVE,
					"User " + id + " is already inactive");
		}

		Instant now = Instant.now();
		user.setActive(false);
		user.setDeactivationReason(AppUser.DeactivationReason.manual);
		user.setDeactivationDate(now);
		user.setUpdatedAt(now);
		user.setUpdatedBy("SYSTEM");
		return appUserRepository.save(user);
	}

	/**
	 * E5 — Reactivate a user.
	 *
	 * <p>Checks the employee mirror — if the employee is resigned, rejects with
	 * {@code 409 EMPLOYEE_RESIGNED} (BR-BE07-27 — users deactivated by HR resign cannot be
	 * reactivated). Otherwise sets {@code isActive=true}, clears the deactivation fields,
	 * sets {@code activationDate=now}.
	 *
	 * @param id the user ID
	 * @return the updated {@link AppUser}
	 * @throws ProvisioningException if the user is not found, already active, or the
	 *         employee is resigned in the mirror
	 */
	@Transactional
	public AppUser reactivate(Long id) {
		AppUser user = appUserRepository.findById(id)
				.orElseThrow(() -> new ProvisioningException(
						ErrorCode.USER_NOT_FOUND, "User " + id + " not found"));

		if (user.isActive()) {
			throw new ProvisioningException(
					ErrorCode.USER_ALREADY_ACTIVE,
					"User " + id + " is already active");
		}

		// BR-BE07-27: cannot reactivate if the employee is resigned in the mirror.
		EmployeeMirror employee = employeeMirrorRepository.findByNik(user.getEmployeeNik())
				.orElseThrow(() -> new ProvisioningException(
						ErrorCode.EMPLOYEE_NOT_FOUND,
						"NIK '" + user.getEmployeeNik() + "' not found in employee mirror"));

		if (employee.isResigned()) {
			throw new ProvisioningException(
					ErrorCode.REACTIVATE_FORBIDDEN,
					"Cannot reactivate — employee NIK '" + user.getEmployeeNik()
							+ "' is resigned in the employee mirror");
		}

		Instant now = Instant.now();
		user.setActive(true);
		user.setDeactivationReason(null);
		user.setDeactivationDate(null);
		user.setActivationDate(now);
		user.setUpdatedAt(now);
		user.setUpdatedBy("SYSTEM");
		return appUserRepository.save(user);
	}

	/**
	 * BR-BE07-27 — Auto-deactivate on HR resign event.
	 *
	 * <p>When the HR sync job detects that an employee has resigned, it publishes an
	 * {@link EmployeeResignedEvent}. This listener finds the linked {@link AppUser} (if one
	 * exists) and auto-deactivates it with {@code deactivationReason=hr_resigned}. This is
	 * non-reactivatable — the E5 reactivate path checks the mirror and rejects if the
	 * employee is still resigned.
	 *
	 * <p>If no {@link AppUser} exists for the resigned NIK, the event is a no-op (not every
	 * employee has an app user).
	 *
	 * @param event the HR resign event carrying the employee NIK
	 */
	@EventListener
	@Transactional
	public void onEmployeeResigned(EmployeeResignedEvent event) {
		Optional<AppUser> maybeUser = appUserRepository.findByEmployeeNik(event.employeeNik());
		if (maybeUser.isEmpty()) {
			// No app user for this NIK — nothing to deactivate.
			return;
		}

		AppUser user = maybeUser.get();
		if (!user.isActive()) {
			// Already inactive — no-op (avoid overwriting the existing deactivation reason).
			return;
		}

		Instant now = Instant.now();
		user.setActive(false);
		user.setDeactivationReason(AppUser.DeactivationReason.hr_resigned);
		user.setDeactivationDate(now);
		user.setUpdatedAt(now);
		user.setUpdatedBy("SYSTEM");
		appUserRepository.save(user);
	}

	// ----------------------------------------------------------------------------------------------
	// Request / error / event types
	// ----------------------------------------------------------------------------------------------

	/**
	 * Provisioning request DTO (E2 POST /users body). Carries the fields needed to create
	 * a new user. The {@code role} is a plain String (not a {@link Role}) so that
	 * {@code SUPER_USER} and other invalid values reach the service for explicit rejection
	 * ({@code 422 UNKNOWN_ROLE}) rather than being deserialization-rejected at the
	 * controller layer (which would yield a less informative 400).
	 *
	 * @param employeeNik the HR employee NIK (must exist in mirror, not resigned)
	 * @param role the D-10 role name (rejected if not a valid D-10 value)
	 * @param companyId the company identifier
	 * @param branchId the default single-branch scope (OQ-BE07-04)
	 */
	public record ProvisionRequest(
			String employeeNik,
			String role,
			String companyId,
			String branchId) {
	}

	/** Error codes for provisioning/lifecycle failures. Each maps to a distinct HTTP status. */
	public enum ErrorCode {
		UNKNOWN_ROLE(422),
		EMPLOYEE_NOT_FOUND(422),
		EMPLOYEE_RESIGNED(422),
		USER_ALREADY_EXISTS(409),
		USER_NOT_FOUND(404),
		USER_ALREADY_INACTIVE(409),
		USER_ALREADY_ACTIVE(409),
		REACTIVATE_FORBIDDEN(409);

		private final int httpStatus;

		ErrorCode(int httpStatus) {
			this.httpStatus = httpStatus;
		}

		public int httpStatus() {
			return httpStatus;
		}
	}

	/**
	 * Exception thrown when provisioning or lifecycle validation fails. Carries an
	 * {@link ErrorCode} that the controller translates to the correct HTTP status.
	 */
	public static class ProvisioningException extends RuntimeException {

		private final ErrorCode errorCode;

		public ProvisioningException(ErrorCode errorCode, String message) {
			super(message);
			this.errorCode = errorCode;
		}

		public ErrorCode getErrorCode() {
			return errorCode;
		}
	}

	/**
	 * Event published when the HR sync detects an employee resignation (BR-BE07-27). The
	 * {@link #onEmployeeResigned(EmployeeResignedEvent)} listener consumes it to
	 * auto-deactivate the linked {@link AppUser}.
	 *
	 * @param employeeNik the NIK of the employee who resigned
	 */
	public record EmployeeResignedEvent(String employeeNik) {
	}

}
// SDD-PROVENANCE: U-003 | vault: .mega-sdd/vaults/acquisition-master-data | AppUserService @Service @ConditionalOnBean(JpaRepository) — E2 provision (Role.fromName reject UNKNOWN_ROLE D-09; NIK exists in mirror !resigned BR-BE07-02; 409 duplicate); E5 deactivate/reactivate (409 if mirror resigned BR-BE07-27); @EventListener EmployeeResignedEvent → auto-deactivate hr_resigned; ProvisioningException+ErrorCode for explicit HTTP status mapping
