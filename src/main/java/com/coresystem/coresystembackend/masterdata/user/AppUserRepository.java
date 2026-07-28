package com.coresystem.coresystembackend.masterdata.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;


/**
 * Spring Data JPA repository for {@link AppUser} (Tier A owned, table {@code mst_user}).
 *
 * <p>Exposes finders for user provisioning (E2) and lifecycle management (E1-E5):
 * <ul>
 *   <li>{@link #findByEmployeeNik(String)} — single-user lookup by NIK business key. Used by
 *       E2 provisioning (duplicate check → {@code 409 USER_ALREADY_EXISTS}) and E3 detail.</li>
 *   <li>{@link #existsByEmployeeNik(String)} — existence check for provisioning uniqueness
 *       validation. More efficient than {@code findByEmployeeNik} when only a boolean is
 *       needed.</li>
 * </ul>
 *
 * <p>No custom delete methods are declared — master-data is deactivate-only
 * (BR-BE07-03, constitution §I-003). The inherited {@code JpaRepository.deleteById}/
 * {@code delete} methods exist at the framework level but are never invoked by the
 * service layer; lifecycle is managed via {@code isActive} toggles.
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

	/**
	 * Find a user by their employee NIK (business key).
	 *
	 * @param employeeNik the HR employee NIK (unique business key)
	 * @return the user, or {@link Optional#empty()} if not found
	 */
	Optional<AppUser> findByEmployeeNik(String employeeNik);

	/**
	 * Check whether a user exists for the given employee NIK.
	 *
	 * <p>Used by E2 provisioning to reject duplicates ({@code 409 USER_ALREADY_EXISTS}).
	 *
	 * @param employeeNik the HR employee NIK to check
	 * @return {@code true} if a user with this NIK already exists
	 */
	boolean existsByEmployeeNik(String employeeNik);

}
// SDD-PROVENANCE: U-003 | vault: .mega-sdd/vaults/acquisition-master-data | AppUserRepository extends JpaRepository — findByEmployeeNik (business key lookup) + existsByEmployeeNik (provisioning uniqueness check); no custom delete (BR-BE07-03 deactivate-only)
