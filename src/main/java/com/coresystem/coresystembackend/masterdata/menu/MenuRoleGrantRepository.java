package com.coresystem.coresystembackend.masterdata.menu;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;


import com.coresystem.coresystembackend.masterdata.user.Role;

/**
 * Spring Data JPA repository for {@link MenuRoleGrant} (table {@code cfg_menu_role_grant}).
 *
 * <p>Exposes finders for the E6 menu-efektif query and E10 role grant management:
 * <ul>
 *   <li>{@link #findByRoleAndIsActiveTrue(Role)} — active grants for a given D-10 role (E6).</li>
 *   <li>{@link #findByRole(Role)} — all grants for a role (E10 GET, including inactive).</li>
 * </ul>
 *
 * <p>No custom delete methods — deactivate-only (BR-BE07-03).
 */
public interface MenuRoleGrantRepository extends JpaRepository<MenuRoleGrant, Long> {

	/**
	 * Find active role grants for the given D-10 role.
	 *
	 * <p>Used by E6 menu-efektif to determine which menus the role has access to.
	 *
	 * @param role the D-10 role
	 * @return list of active grants for the role
	 */
	List<MenuRoleGrant> findByRoleAndIsActiveTrue(Role role);

	/**
	 * Find all grants (active and inactive) for the given role.
	 *
	 * <p>Used by E10 GET to display the full grant set for admin management.
	 *
	 * @param role the D-10 role
	 * @return list of all grants for the role
	 */
	List<MenuRoleGrant> findByRole(Role role);

}
// SDD-PROVENANCE: U-004 | vault: .mega-sdd/vaults/acquisition-master-data | MenuRoleGrantRepository extends JpaRepository — findByRoleAndIsActiveTrue (E6), findByRole (E10 GET all); no custom delete (BR-BE07-03)
