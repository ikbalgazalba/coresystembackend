package com.coresystem.coresystembackend.masterdata.menu;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link MenuUserGrantSpecial}
 * (table {@code cfg_menu_user_grant_special}).
 *
 * <p>Exposes finders for the E6 menu-efektif query and E11 special grant management:
 * <ul>
 *   <li>{@link #findByUserIdAndIsActiveTrue(Long)} — active special grants for a user (E6).</li>
 *   <li>{@link #findByUserId(Long)} — all special grants for a user (E11 GET, including inactive).</li>
 * </ul>
 *
 * <p>No custom delete methods — deactivate-only (BR-BE07-03). Special grants carry a mandatory
 * {@code granted_reason} for governance (OQ-BE07-05 resolved).
 */
public interface MenuUserGrantSpecialRepository extends JpaRepository<MenuUserGrantSpecial, Long> {

	/**
	 * Find active special grants for the given user.
	 *
	 * <p>Used by E6 menu-efektif to include per-user special grants alongside role grants.
	 *
	 * @param userId the {@code mst_user.id}
	 * @return list of active special grants for the user
	 */
	List<MenuUserGrantSpecial> findByUserIdAndIsActiveTrue(Long userId);

	/**
	 * Find all special grants (active and inactive) for the given user.
	 *
	 * <p>Used by E11 GET to display the full special grant set for admin management.
	 *
	 * @param userId the {@code mst_user.id}
	 * @return list of all special grants for the user
	 */
	List<MenuUserGrantSpecial> findByUserId(Long userId);

}
// SDD-PROVENANCE: U-004 | vault: .mega-sdd/vaults/acquisition-master-data | MenuUserGrantSpecialRepository extends JpaRepository — findByUserIdAndIsActiveTrue (E6), findByUserId (E11 GET all); no custom delete (BR-BE07-03); granted_reason governance OQ-BE07-05
