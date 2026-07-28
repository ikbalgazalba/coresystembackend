package com.coresystem.coresystembackend.masterdata.dealer;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;


/**
 * Repository interfaces for the dealer master family (6 tables).
 *
 * <p>All six interfaces are bundled in this single file to keep the dealer package compact (one
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
 * <p>Custom finders are declared only where the service layer (U-006) consumes them — derived
 * queries for business-key lookups and dealer-code-scoped child collections.
 */

/** Access to {@code mst_dealer} (Dealer master). */
public interface DealerRepository extends JpaRepository<Dealer, Long> {

	/** Find a dealer by its unique business key {@code dealer_code}. */
	Optional<Dealer> findByDealerCode(String dealerCode);

}

/** Access to {@code mst_dealer_document} (Dealer documents). */
interface DealerDocumentRepository extends JpaRepository<DealerDocument, Long> {

	/** Find all documents for a dealer, ordered by upload time descending. */
	List<DealerDocument> findByDealerCodeOrderByUploadedAtDesc(String dealerCode);

}

/** Access to {@code mst_dealer_personnel} (Dealer personnel contacts). */
interface DealerPersonnelRepository extends JpaRepository<DealerPersonnel, Long> {

	/** Find all personnel for a dealer. */
	List<DealerPersonnel> findByDealerCode(String dealerCode);

	/** Find all active ({@code status='A'}) personnel for a dealer. */
	List<DealerPersonnel> findByDealerCodeAndStatus(String dealerCode, String status);

	/** Find a personnel by its business key {@code personnel_id}. */
	Optional<DealerPersonnel> findByPersonnelId(String personnelId);

}

/** Access to {@code mst_dealer_job_title} (Dealer job titles). */
interface DealerJobTitleRepository extends JpaRepository<DealerJobTitle, Long> {

	/** Find a job-title by its business key {@code job_title_id}. */
	Optional<DealerJobTitle> findByJobTitleId(String jobTitleId);

}

/** Access to {@code mst_dealer_branch_access} (Dealer-branch visibility bridge). */
interface DealerBranchAccessRepository extends JpaRepository<DealerBranchAccess, Long> {

	/** Find all branch-access rows for a dealer. */
	List<DealerBranchAccess> findByDealerCode(String dealerCode);

	/** Find all active branch-access rows for a dealer (BR-BE07-07 picker filter). */
	List<DealerBranchAccess> findByDealerCodeAndIsActiveTrue(String dealerCode);

	/** Find all dealers (dealer_codes) that have active access to a given branch (BR-BE07-07). */
	List<DealerBranchAccess> findByBranchIdAndIsActiveTrue(String branchId);

}

/** Access to {@code mst_dealer_bank_reference} (Dealer bank references — payout target). */
interface DealerBankReferenceRepository extends JpaRepository<DealerBankReference, Long> {

	/** Find all bank references for a dealer. */
	List<DealerBankReference> findByDealerCode(String dealerCode);

	/** Find a bank reference by its business key (composite: dealer_code + bank_reference_id). */
	Optional<DealerBankReference> findByDealerCodeAndBankReferenceId(
			String dealerCode, String bankReferenceId);

}

// SDD-PROVENANCE: U-005 | vault: .mega-sdd/vaults/acquisition-master-data | DealerRepository — 6 bundled JpaRepository interfaces (Dealer/DealerDocument/DealerPersonnel/DealerJobTitle/DealerBranchAccess/DealerBankReference); no custom delete (BR-BE07-03 deactivate-only); U-006 added DealerBankReferenceRepository + derived finders for service layer
