package com.coresystem.coresystembackend.masterdata.dealer;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository interfaces for the dealer master family (5 tables).
 *
 * <p>All five interfaces are bundled in this single file to keep the dealer package compact (one
 * repository file per aggregate family, not per entity). Each extends
 * {@link JpaRepository} providing standard CRUD + derived-query access.
 *
 * <p><strong>BR-BE07-03 deactivate-only</strong> — these repositories MUST NOT declare any custom
 * delete/remove method. Hard delete is prohibited for all master-data (constitution §I-003,
 * BR-BE07-03); lifecycle is managed exclusively via {@code status}/{@code isActive} toggles in the
 * service layer. The inherited {@code JpaRepository.deleteById}/{@code delete} methods are
 * available at the framework level but are never invoked by the master-data service layer; a
 * later unit may add a guard (e.g. a {@code @PreRemove} listener that throws) if defense-in-depth
 * is required.
 *
 * <p>No custom finders are declared yet — the service layer (a later unit) will add derived queries
 * (e.g. {@code findByDealerCode}, {@code findByStatusAndIsActive}) as needed. Declaring them
 * speculatively now would violate YAGNI.
 */

/** Access to {@code mst_dealer} (Dealer master). */
public interface DealerRepository extends JpaRepository<Dealer, Long> {
}

/** Access to {@code mst_dealer_document} (Dealer documents). */
interface DealerDocumentRepository extends JpaRepository<DealerDocument, Long> {
}

/** Access to {@code mst_dealer_personnel} (Dealer personnel contacts). */
interface DealerPersonnelRepository extends JpaRepository<DealerPersonnel, Long> {
}

/** Access to {@code mst_dealer_job_title} (Dealer job titles). */
interface DealerJobTitleRepository extends JpaRepository<DealerJobTitle, Long> {
}

/** Access to {@code mst_dealer_branch_access} (Dealer-branch visibility bridge). */
interface DealerBranchAccessRepository extends JpaRepository<DealerBranchAccess, Long> {
}

// SDD-PROVENANCE: U-005 | vault: .mega-sdd/vaults/acquisition-master-data | DealerRepository — 5 bundled JpaRepository interfaces (Dealer/DealerDocument/DealerPersonnel/DealerJobTitle/DealerBranchAccess); no custom delete (BR-BE07-03 deactivate-only)
