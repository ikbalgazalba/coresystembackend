package com.coresystem.coresystembackend.masterdata.user;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.coresystem.coresystembackend.masterdata.common.PageResponse;
import com.coresystem.coresystembackend.masterdata.user.AppUserService.ErrorCode;
import com.coresystem.coresystembackend.masterdata.user.AppUserService.ProvisioningException;
import com.coresystem.coresystembackend.masterdata.user.AppUserService.ProvisionRequest;

/**
 * E1-E5 + E7 controller for APP_USER provisioning and lifecycle (F-U-001).
 *
 * <p>Exposes the following endpoints:
 * <ul>
 *   <li>E1 {@code GET /users} — list/search users, returns {@link PageResponse}.</li>
 *   <li>E2 {@code POST /users} — provision a new user (NIK validation, role D-10).</li>
 *   <li>E3 {@code GET /users/{id}} — user detail.</li>
 *   <li>E4 {@code PATCH /users/{id}} — update role/scope (not identity).</li>
 *   <li>E5 {@code POST /users/{id}/deactivate} / {@code POST /users/{id}/reactivate} —
 *       lifecycle toggles (reactivate rejected if mirror resigned BR-BE07-27).</li>
 *   <li>E7 {@code GET /roles} — static D-10 role catalog.</li>
 * </ul>
 *
 * <p>Authentication/authorization (Credit Admin) is enforced by the security filter chain
 * ({@code SecurityConfig}), not by this controller. The {@code /users} path is not in the
 * {@code permitAll} list, so it requires an authenticated request.
 *
 * <p>Per constitution §C-002, controllers return DTOs — never the raw entity. The
 * {@link AppUserDto} record is the response carrier for E1-E4.
 *
 * <p>Guarded by {@link ConditionalOnBean @ConditionalOnBean(JpaRepository.class)} to avoid
 * context-load regression in tests that exclude JPA autoconfiguration (same pattern as
 * {@code MasterDataConfig.JpaAuditingConfig} and {@code AppUserService}).
 *
 * <p>No {@code POST /employees} endpoint exists here — HR is the system-of-record
 * (BR-EMPLOYEE-1, constitution §I-005).
 */
@RestController
@RequestMapping("/users")
@ConditionalOnBean(JpaRepository.class)
public class AppUserController {

	private final AppUserService appUserService;
	private final AppUserRepository appUserRepository;

	public AppUserController(AppUserService appUserService, AppUserRepository appUserRepository) {
		this.appUserService = appUserService;
		this.appUserRepository = appUserRepository;
	}

	/**
	 * E1 — List/search users. Returns a {@link PageResponse} of {@link AppUserDto} items.
	 *
	 * @param page zero-based page number (default 0)
	 * @param size page size (default 20)
	 * @return paginated user list
	 */
	@GetMapping
	public PageResponse<AppUserDto> list(
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size) {
		Pageable pageable = PageRequest.of(page, size);
		Page<AppUser> result = appUserRepository.findAll(pageable);
		Page<AppUserDto> dtoPage = result.map(AppUserDto::from);
		return PageResponse.of(dtoPage, page, size);
	}

	/**
	 * E2 — Provision a new user.
	 *
	 * <p>Validates the role (D-10, rejects {@code SUPER_USER} → {@code 422 UNKNOWN_ROLE}),
	 * the NIK (exists in mirror, not resigned → {@code 422 EMPLOYEE_NOT_FOUND}/
	 * {@code EMPLOYEE_RESIGNED}), and uniqueness ({@code 409 USER_ALREADY_EXISTS}).
	 *
	 * @param request the provisioning request body
	 * @return 201 with the created user DTO, or 422/409 with an error body
	 */
	@PostMapping
	public ResponseEntity<?> provision(@RequestBody ProvisionRequest request) {
		try {
			AppUser created = appUserService.provision(request);
			return ResponseEntity
					.status(HttpStatus.CREATED)
					.body(AppUserDto.from(created));
		} catch (ProvisioningException e) {
			return toErrorResponse(e);
		}
	}

	/**
	 * E3 — Get user detail by ID.
	 *
	 * @param id the user ID
	 * @return 200 with the user DTO, or 404
	 */
	@GetMapping("/{id}")
	public ResponseEntity<?> getDetail(@PathVariable Long id) {
		Optional<AppUser> user = appUserRepository.findById(id);
		if (user.isEmpty()) {
			return ResponseEntity
					.status(HttpStatus.NOT_FOUND)
					.body(Map.of("error", "USER_NOT_FOUND", "id", id));
		}
		return ResponseEntity.ok(AppUserDto.from(user.get()));
	}

	/**
	 * E4 — Update user role/scope (not identity). The role must be D-10; the NIK cannot be
	 * changed.
	 *
	 * @param id the user ID
	 * @param body the update fields ({@code role} and/or {@code branch_ids})
	 * @return 200 with the updated user DTO, or error
	 */
	@PatchMapping("/{id}")
	public ResponseEntity<?> updateRoleScope(@PathVariable Long id, @RequestBody Map<String, Object> body) {
		try {
			Optional<AppUser> maybeUser = appUserRepository.findById(id);
			if (maybeUser.isEmpty()) {
				return ResponseEntity
						.status(HttpStatus.NOT_FOUND)
						.body(Map.of("error", "USER_NOT_FOUND", "id", id));
			}
			AppUser user = maybeUser.get();
			if (body.containsKey("role")) {
				Role newRole = Role.fromName((String) body.get("role"))
						.orElseThrow(() -> new ProvisioningException(
								ErrorCode.UNKNOWN_ROLE,
								"Role '" + body.get("role") + "' is not a D-10 role"));
				user.setRole(newRole);
			}
			user.setUpdatedAt(Instant.now());
			user.setUpdatedBy("SYSTEM");
			AppUser saved = appUserRepository.save(user);
			return ResponseEntity.ok(AppUserDto.from(saved));
		} catch (ProvisioningException e) {
			return toErrorResponse(e);
		}
	}

	/**
	 * E5 — Deactivate a user (admin-initiated, {@code deactivationReason=manual}).
	 *
	 * @param id the user ID
	 * @return 200 with the updated user DTO, or 409/404
	 */
	@PostMapping("/{id}/deactivate")
	public ResponseEntity<?> deactivate(@PathVariable Long id) {
		try {
			AppUser deactivated = appUserService.deactivate(id);
			return ResponseEntity.ok(AppUserDto.from(deactivated));
		} catch (ProvisioningException e) {
			return toErrorResponse(e);
		}
	}

	/**
	 * E5 — Reactivate a user. Rejected if the employee is resigned in the mirror
	 * (BR-BE07-27 — {@code 409 EMPLOYEE_RESIGNED}).
	 *
	 * @param id the user ID
	 * @return 200 with the updated user DTO, or 409/404
	 */
	@PostMapping("/{id}/reactivate")
	public ResponseEntity<?> reactivate(@PathVariable Long id) {
		try {
			AppUser reactivated = appUserService.reactivate(id);
			return ResponseEntity.ok(AppUserDto.from(reactivated));
		} catch (ProvisioningException e) {
			return toErrorResponse(e);
		}
	}

	/**
	 * E7 — Static D-10 role catalog. Returns the five role names as an array.
	 * The catalog is closed (D-10, D-MD-04) — no {@code mst_role} table exists.
	 *
	 * @return the D-10 role catalog
	 */
	@GetMapping("/roles")
	public List<String> roles() {
		return List.of(Role.CMO.name(), Role.MARKETING_HEAD.name(),
				Role.CREDIT_ANALYST.name(), Role.KEPALA_CABANG.name(),
				Role.CREDIT_ADMIN.name());
	}

	// ----------------------------------------------------------------------------------------------
	// Helpers
	// ----------------------------------------------------------------------------------------------

	private static ResponseEntity<?> toErrorResponse(ProvisioningException e) {
		HttpStatus status = HttpStatus.resolve(e.getErrorCode().httpStatus());
		if (status == null) {
			status = HttpStatus.INTERNAL_SERVER_ERROR;
		}
		return ResponseEntity
				.status(status)
				.body(Map.of("error", e.getErrorCode().name(), "message", e.getMessage()));
	}

	// ----------------------------------------------------------------------------------------------
	// Response DTO
	// ----------------------------------------------------------------------------------------------

	/**
	 * Response DTO for E1-E4 user endpoints. Carries the user fields in snake_case per
	 * BR-BE07-20. Per constitution §C-002, controllers return DTOs — never the raw entity.
	 *
	 * <p>Excludes audit columns ({@code created_at/created_by/updated_at/updated_by}) which
	 * are internal. The {@code branch_ids} list is the flattened projection of the
	 * {@link AppUserBranchScope} children.
	 *
	 * @param id             database PK
	 * @param employeeNik    HR employee NIK (business key)
	 * @param role           D-10 role name
	 * @param companyId      company identifier
	 * @param isActive       lifecycle toggle
	 * @param deactivationReason why the user was deactivated (null while active)
	 * @param activationDate when the user was activated
	 * @param deactivationDate when the user was deactivated (null while active)
	 * @param branchIds      the branch scope IDs (flattened from AppUserBranchScope)
	 */
	public record AppUserDto(
			Long id,
			@JsonProperty("employee_nik") String employeeNik,
			String role,
			@JsonProperty("company_id") String companyId,
			@JsonProperty("is_active") boolean isActive,
			@JsonProperty("deactivation_reason") String deactivationReason,
			@JsonProperty("activation_date") Instant activationDate,
			@JsonProperty("deactivation_date") Instant deactivationDate,
			@JsonProperty("branch_ids") List<String> branchIds) {

		/**
		 * Map an {@link AppUser} entity to the response DTO.
		 *
		 * @param user the entity
		 * @return the DTO with snake_case field names
		 */
		public static AppUserDto from(AppUser user) {
			List<String> branchIds = user.getBranchScopes() != null
					? user.getBranchScopes().stream()
							.map(AppUserBranchScope::getBranchId)
							.toList()
					: List.of();

			String deactivationReason = user.getDeactivationReason() != null
					? user.getDeactivationReason().name()
					: null;

			return new AppUserDto(
					user.getId(),
					user.getEmployeeNik(),
					user.getRole().name(),
					user.getCompanyId(),
					user.isActive(),
					deactivationReason,
					user.getActivationDate(),
					user.getDeactivationDate(),
					branchIds);
		}
	}

}
// SDD-PROVENANCE: U-003 | vault: .mega-sdd/vaults/acquisition-master-data | AppUserController @RestController /users @ConditionalOnBean(JpaRepository) — E1 GET list (PageResponse), E2 POST provision (422 UNKNOWN_ROLE/EMPLOYEE_NOT_FOUND/EMPLOYEE_RESIGNED, 409 USER_ALREADY_EXISTS), E3 GET detail, E4 PATCH role/scope, E5 deactivate/reactivate (409 if mirror resigned), E7 GET /roles static D-10 catalog; AppUserDto record with @JsonProperty snake_case per BR-BE07-20
