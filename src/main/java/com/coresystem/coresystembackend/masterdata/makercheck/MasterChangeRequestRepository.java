package com.coresystem.coresystembackend.masterdata.makercheck;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Spring Data JPA repository for {@link MasterChangeRequest} (E37 envelope, BR-BE07-05).
 *
 * <p>Provides standard CRUD via {@link JpaRepository} plus a derived finder for the checker
 * inbox/worklist: {@code GET /master-change-requests?status=pending_approval&resource=...}.
 *
 * <p>The underlying {@code log_master_change_request} table is append-only (INSERT-only per
 * DB-CONVENTIONS §4). The repository exposes {@code save} which JPA uses for both INSERT (new
 * entity) and UPDATE (managed entity flush); the service layer never calls {@code delete} or
 * {@code deleteById} — rows are never removed from a {@code log_} table.
 */
public interface MasterChangeRequestRepository extends JpaRepository<MasterChangeRequest, Long> {

	/**
	 * Find change-requests by status and resource, paginated — the checker inbox query
	 * ({@code GET /master-change-requests?status=pending_approval&resource=dealer-bank-reference}).
	 *
	 * <p>Both parameters are exact-match string comparisons. The {@code status} parameter matches
	 * the {@link MasterChangeRequest.Status} enum name (e.g. {@code "pending_approval"}); the
	 * {@code resource} parameter matches the {@code resource} column.
	 *
	 * @param status the lifecycle status to filter by (e.g. {@code "pending_approval"})
	 * @param resource the resource name to filter by (e.g. {@code "dealer-bank-reference"})
	 * @param pageable the pagination request
	 * @return a page of matching change-requests
	 */
	Page<MasterChangeRequest> findByStatusAndResource(MasterChangeRequest.Status status, String resource, Pageable pageable);

	Page<MasterChangeRequest> findByStatus(MasterChangeRequest.Status status, Pageable pageable);

	Page<MasterChangeRequest> findByResource(String resource, Pageable pageable);

}
// SDD-PROVENANCE: U-009 | vault: .mega-sdd/vaults/acquisition-master-data | MasterChangeRequestRepository — JpaRepository + findByStatusAndResource (checker inbox query E37)
