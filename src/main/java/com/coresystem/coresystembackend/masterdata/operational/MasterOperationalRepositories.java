package com.coresystem.coresystembackend.masterdata.operational;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.coresystem.coresystembackend.masterdata.operational.MasterOperationalEntities.ApprovalReason;
import com.coresystem.coresystembackend.masterdata.operational.MasterOperationalEntities.BlacklistOverride;
import com.coresystem.coresystembackend.masterdata.operational.MasterOperationalEntities.BranchCreditSource;
import com.coresystem.coresystembackend.masterdata.operational.MasterOperationalEntities.CreditSource;
import com.coresystem.coresystembackend.masterdata.operational.MasterOperationalEntities.GeneralParameter;
import com.coresystem.coresystembackend.masterdata.operational.MasterOperationalEntities.PromotionLineText;
import com.coresystem.coresystembackend.masterdata.operational.MasterOperationalEntities.PublicHoliday;

interface ApprovalReasonRepository extends JpaRepository<ApprovalReason, Long> {
    Page<ApprovalReason> findByIsActiveTrue(Pageable pageable);
    Page<ApprovalReason> findByTypeAndIsActiveTrue(String type, Pageable pageable);
}

interface CreditSourceRepository extends JpaRepository<CreditSource, Long> {
    Page<CreditSource> findByIsActiveTrue(Pageable pageable);
}

interface BranchCreditSourceRepository extends JpaRepository<BranchCreditSource, Long> {
    Page<BranchCreditSource> findByIsActiveTrue(Pageable pageable);
    Page<BranchCreditSource> findByBranchIdAndIsActiveTrue(String branchId, Pageable pageable);
}

interface BlacklistOverrideRepository extends JpaRepository<BlacklistOverride, Long> {
    Page<BlacklistOverride> findByIsActiveTrue(Pageable pageable);
    Page<BlacklistOverride> findByNationalIdAndIsActiveTrue(String nationalId, Pageable pageable);
}

interface PublicHolidayRepository extends JpaRepository<PublicHoliday, Long> {}

interface GeneralParameterRepository extends JpaRepository<GeneralParameter, Long> {
    Optional<GeneralParameter> findByParameter(String parameter);
    Page<GeneralParameter> findByIsVisibleTrue(Pageable pageable);
}

interface PromotionLineTextRepository extends JpaRepository<PromotionLineText, Long> {
    Page<PromotionLineText> findByIsActiveTrue(Pageable pageable);
}
// SDD-PROVENANCE: operational | vault: .mega-sdd/vaults/acquisition-master-data | 7 top-level JPA repository interfaces for master operasional entities
