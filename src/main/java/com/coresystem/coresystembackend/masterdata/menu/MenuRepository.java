package com.coresystem.coresystembackend.masterdata.menu;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;


/**
 * Spring Data JPA repository for {@link Menu} (table {@code cfg_menu}).
 *
 * <p>Exposes finders for the E6 menu-efektif tree and E9 admin CRUD:
 * <ul>
 *   <li>{@link #findByParentIdIsNullAndIsActiveTrueOrderByDisplayOrder()} — root nodes for the
 *       E6 tree (active only, ordered by {@code displayOrder}).</li>
 *   <li>{@link #findByIsActiveTrueOrderByDisplayOrder()} — all active menus ordered by
 *       {@code displayOrder}, used by the E6 tree assembly (flat list → in-memory tree).</li>
 *   <li>{@link #findByIsActiveTrue()} — all active menus (unordered), for admin queries.</li>
 * </ul>
 *
 * <p>No custom delete methods are declared — master-data is deactivate-only
 * (BR-BE07-03, constitution §I-003). The inherited {@code JpaRepository.deleteById}/
 * {@code delete} methods exist at the framework level but are never invoked by the
 * service layer; lifecycle is managed via {@code isActive} toggles.
 */
public interface MenuRepository extends JpaRepository<Menu, Long> {

	/**
	 * Find active root menu nodes (parent_id is null), ordered by display_order.
	 *
	 * <p>Used by E6 to fetch the top level of the menu tree.
	 *
	 * @return list of active root menus ordered by display_order
	 */
	List<Menu> findByParentIdIsNullAndIsActiveTrueOrderByDisplayOrder();

	/**
	 * Find all active menus ordered by display_order.
	 *
	 * <p>Used by E6 tree assembly — the service fetches the flat list and builds the tree
	 * in-memory from the parent_id references.
	 *
	 * @return list of all active menus ordered by display_order
	 */
	List<Menu> findByIsActiveTrueOrderByDisplayOrder();

	/**
	 * Find all active menus (unordered).
	 *
	 * @return list of all active menus
	 */
	List<Menu> findByIsActiveTrue();

}
// SDD-PROVENANCE: U-004 | vault: .mega-sdd/vaults/acquisition-master-data | MenuRepository extends JpaRepository — findByParentIdIsNullAndIsActiveTrueOrderByDisplayOrder (E6 root nodes), findByIsActiveTrueOrderByDisplayOrder (E6 flat→tree), findByIsActiveTrue; no custom delete (BR-BE07-03 deactivate-only)
