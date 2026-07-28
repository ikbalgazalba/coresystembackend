package com.coresystem.coresystembackend.masterdata.transtype;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link HierarchyMatrix} ({@code cfg_hierarchy_matrix}).
 *
 * <p>Exposes read-only finders for the E26 list-per-type endpoint. No custom delete methods are
 * declared — hard delete is prohibited for all master-data (BR-BE07-03 deactivate-only,
 * constitution §I-003); lifecycle is managed via {@code isActive} toggles.
 *
 * <p>Finders:
 * <ul>
 *   <li>{@link #findByTransactionTypeCodeAndIsActiveTrue(String, Pageable)} — E26 list: active
 *       hierarchy levels for a transaction type, paginated.</li>
 * </ul>
 */
public interface HierarchyMatrixRepository extends JpaRepository<HierarchyMatrix, Long> {

	/**
	 * E26 — list active hierarchy levels for a transaction type, paginated.
	 *
	 * <p>Filters to {@code is_active=true} rows only — deactivated levels are excluded from the
	 * default list view (BR-BE07-03 deactivate-only).
	 *
	 * @param transactionTypeCode the transaction-type routing code
	 * @param pageable pagination information
	 * @return a page of active hierarchy levels for the transaction type
	 */
	Page<HierarchyMatrix> findByTransactionTypeCodeAndIsActiveTrue(
			String transactionTypeCode, Pageable pageable);

}
// SDD-PROVENANCE: U-008 | vault: .mega-sdd/vaults/acquisition-master-data | HierarchyMatrixRepository extends JpaRepository — findByTransactionTypeCodeAndIsActiveTrue (E26 list per type); no custom delete (BR-BE07-03 deactivate-only)
