package com.coresystem.coresystembackend.masterdata.transtype;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Repository interfaces for the Transaction-Type hierarchy config (2 tables).
 *
 * <p>Both interfaces are bundled in this single file to keep the transtype package compact (one
 * repository file per aggregate family, not per entity — same pattern as
 * {@code DealerRepository}). Each extends {@link JpaRepository} providing standard CRUD +
 * derived-query access.
 *
 * <p><strong>BR-BE07-03 deactivate-only</strong> — these repositories MUST NOT declare any custom
 * delete/remove method. Hard delete is prohibited for all master-data (constitution §I-003,
 * BR-BE07-03); lifecycle is managed exclusively via {@code isActive} toggles in the service layer.
 */

/** Access to {@code cfg_transaction_code} (Transaction-Code config). */
interface TransactionCodeRepository extends JpaRepository<TransactionCode, Long> {

	/**
	 * Find a transaction-code by its branch ID and transaction code (composite unique key).
	 *
	 * @param branchId the branch scope
	 * @param transactionCode the upper-case transaction code
	 * @return the matching row, or empty if not found
	 */
	Optional<TransactionCode> findByBranchIdAndTransactionCode(String branchId, String transactionCode);

	/**
	 * List transaction-codes for a branch, paginated.
	 *
	 * @param branchId the branch scope
	 * @param pageable the pagination request
	 * @return a page of matching transaction-codes
	 */
	Page<TransactionCode> findByBranchId(String branchId, Pageable pageable);

}

/** Access to {@code cfg_transaction_type} (Transaction-Type config). */
interface TransactionTypeRepository extends JpaRepository<TransactionType, Long> {

	/**
	 * Find a transaction-type by its routing code.
	 *
	 * @param transactionTypeCode the [LOCKED] external-FK routing code
	 * @return the matching row, or empty if not found
	 */
	Optional<TransactionType> findByTransactionTypeCode(String transactionTypeCode);

	/**
	 * List transaction-types whose mapping references a given transaction code, paginated.
	 *
	 * @param transactionCode the referenced transaction code
	 * @param pageable the pagination request
	 * @return a page of matching transaction-types
	 */
	Page<TransactionType> findByMapping(String transactionCode, Pageable pageable);

}
// SDD-PROVENANCE: U-007 | vault: .mega-sdd/vaults/acquisition-master-data | TransactionTypeRepositories — 2 bundled JpaRepository interfaces (TransactionCode/TransactionType); findByBranchIdAndTransactionCode, findByBranchId, findByTransactionTypeCode, findByMapping; no custom delete (BR-BE07-03 deactivate-only)
