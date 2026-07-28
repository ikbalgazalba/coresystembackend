package com.coresystem.coresystembackend.masterdata.operational;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.coresystem.coresystembackend.masterdata.makercheck.MakerCheckerService;
import com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest;
import com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest.Action;
import com.coresystem.coresystembackend.masterdata.operational.MasterOperationalEntities.ApprovalReason;
import com.coresystem.coresystembackend.masterdata.operational.MasterOperationalEntities.BlacklistOverride;
import com.coresystem.coresystembackend.masterdata.operational.MasterOperationalEntities.BranchCreditSource;
import com.coresystem.coresystembackend.masterdata.operational.MasterOperationalEntities.CreditSource;
import com.coresystem.coresystembackend.masterdata.operational.MasterOperationalEntities.GeneralParameter;
import com.coresystem.coresystembackend.masterdata.operational.MasterOperationalEntities.PublicHoliday;
import com.coresystem.coresystembackend.masterdata.operational.MasterOperationalEntities.PromotionLineText;

@Service
public class MasterOperationalService {
	private final ApprovalReasonRepository approvalReasonRepo;
	private final CreditSourceRepository creditSourceRepo;
	private final BranchCreditSourceRepository branchCreditSourceRepo;
	private final BlacklistOverrideRepository blacklistOverrideRepo;
	private final PublicHolidayRepository publicHolidayRepo;
	private final GeneralParameterRepository generalParameterRepo;
	private final PromotionLineTextRepository promotionLineTextRepo;
	private final MakerCheckerService makerCheckerService;

	public MasterOperationalService(ApprovalReasonRepository approvalReasonRepo, CreditSourceRepository creditSourceRepo, BranchCreditSourceRepository branchCreditSourceRepo, BlacklistOverrideRepository blacklistOverrideRepo, PublicHolidayRepository publicHolidayRepo, GeneralParameterRepository generalParameterRepo, PromotionLineTextRepository promotionLineTextRepo, MakerCheckerService makerCheckerService) {
		this.approvalReasonRepo = approvalReasonRepo; this.creditSourceRepo = creditSourceRepo;
		this.branchCreditSourceRepo = branchCreditSourceRepo; this.blacklistOverrideRepo = blacklistOverrideRepo;
		this.publicHolidayRepo = publicHolidayRepo; this.generalParameterRepo = generalParameterRepo;
		this.promotionLineTextRepo = promotionLineTextRepo; this.makerCheckerService = makerCheckerService;
	}

	@Transactional(readOnly = true)
	public Page<ApprovalReason> listApprovalReasons(String type, Pageable pageable) {
		if (type != null && !type.isBlank()) return approvalReasonRepo.findByTypeAndIsActiveTrue(type, pageable);
		return approvalReasonRepo.findByIsActiveTrue(pageable);
	}
	@Transactional(readOnly = true) public Optional<ApprovalReason> getApprovalReason(Long id) { return approvalReasonRepo.findById(id); }
	@Transactional public ApprovalReason createApprovalReason(ApprovalReason reason, String actorNik) { reason.setCreatedAt(Instant.now()); reason.setCreatedBy(actorNik); reason.setActive(true); return approvalReasonRepo.save(reason); }
	@Transactional public ApprovalReason updateApprovalReason(Long id, ApprovalReason update, String actorNik) {
		ApprovalReason existing = approvalReasonRepo.findById(id).orElseThrow(() -> new NotFoundException("approval-reason", id));
		if (update.getDescription() != null) existing.setDescription(update.getDescription());
		if (update.getType() != null) existing.setType(update.getType());
		existing.setUpdatedAt(Instant.now()); existing.setUpdatedBy(actorNik); return approvalReasonRepo.save(existing);
	}
	@Transactional public ApprovalReason deactivateApprovalReason(Long id, String actorNik) {
		ApprovalReason existing = approvalReasonRepo.findById(id).orElseThrow(() -> new NotFoundException("approval-reason", id));
		existing.setActive(false); existing.setUpdatedAt(Instant.now()); existing.setUpdatedBy(actorNik); return approvalReasonRepo.save(existing);
	}

	@Transactional(readOnly = true) public Page<CreditSource> listCreditSources(String branchId, Pageable pageable) { return creditSourceRepo.findByIsActiveTrue(pageable); }
	@Transactional(readOnly = true) public Optional<CreditSource> getCreditSource(Long id) { return creditSourceRepo.findById(id); }
	@Transactional public CreditSource createCreditSource(CreditSource source, String actorNik) { source.setCreatedAt(Instant.now()); source.setCreatedBy(actorNik); source.setActive(true); return creditSourceRepo.save(source); }
	@Transactional public CreditSource updateCreditSource(Long id, CreditSource update, String actorNik) {
		CreditSource existing = creditSourceRepo.findById(id).orElseThrow(() -> new NotFoundException("credit-source", id));
		if (update.getDescription() != null) existing.setDescription(update.getDescription());
		existing.setUpdatedAt(Instant.now()); existing.setUpdatedBy(actorNik); return creditSourceRepo.save(existing);
	}
	@Transactional public CreditSource deactivateCreditSource(Long id, String actorNik) {
		CreditSource existing = creditSourceRepo.findById(id).orElseThrow(() -> new NotFoundException("credit-source", id));
		existing.setActive(false); existing.setUpdatedAt(Instant.now()); existing.setUpdatedBy(actorNik); return creditSourceRepo.save(existing);
	}
	@Transactional(readOnly = true) public Page<BranchCreditSource> listBranchCreditSources(String branchId, Pageable pageable) {
		if (branchId != null && !branchId.isBlank()) return branchCreditSourceRepo.findByBranchIdAndIsActiveTrue(branchId, pageable);
		return branchCreditSourceRepo.findByIsActiveTrue(pageable);
	}
	@Transactional public BranchCreditSource createBranchCreditSource(BranchCreditSource mapping, String actorNik) { mapping.setCreatedAt(Instant.now()); mapping.setCreatedBy(actorNik); mapping.setActive(true); return branchCreditSourceRepo.save(mapping); }
	@Transactional public BranchCreditSource deactivateBranchCreditSource(Long id, String actorNik) {
		BranchCreditSource existing = branchCreditSourceRepo.findById(id).orElseThrow(() -> new NotFoundException("branch-credit-source", id));
		existing.setActive(false); existing.setUpdatedAt(Instant.now()); existing.setUpdatedBy(actorNik); return branchCreditSourceRepo.save(existing);
	}

	@Transactional(readOnly = true) public Page<BlacklistOverride> listBlacklistOverrides(String nationalId, Pageable pageable) {
		if (nationalId != null && !nationalId.isBlank()) return blacklistOverrideRepo.findByNationalIdAndIsActiveTrue(nationalId, pageable);
		return blacklistOverrideRepo.findByIsActiveTrue(pageable);
	}
	@Transactional(readOnly = true) public Optional<BlacklistOverride> getBlacklistOverride(Long id) { return blacklistOverrideRepo.findById(id); }
	@Transactional public MasterChangeRequest createBlacklistOverride(BlacklistOverride override, String makerNik) {
		if (override.getJustification() == null || override.getJustification().isBlank()) throw new MissingJustificationException();
		String payload = "nationalId=" + override.getNationalId() + ",reasonCode=" + override.getReasonCode() + ",justification=" + override.getJustification();
		return makerCheckerService.submit("blacklist-override", Action.create, payload, makerNik);
	}
	@Transactional public MasterChangeRequest updateBlacklistOverride(Long id, BlacklistOverride update, String makerNik) {
		if (update.getJustification() == null || update.getJustification().isBlank()) throw new MissingJustificationException();
		String payload = "id=" + id + ",justification=" + update.getJustification();
		return makerCheckerService.submit("blacklist-override", Action.update, payload, makerNik);
	}

	@Transactional(readOnly = true) public Page<PublicHoliday> listPublicHolidays(Integer year, Pageable pageable) { return publicHolidayRepo.findAll(pageable); }
	@Transactional(readOnly = true) public Optional<PublicHoliday> getPublicHoliday(Long id) { return publicHolidayRepo.findById(id); }
	@Transactional public PublicHoliday createPublicHoliday(PublicHoliday holiday, String actorNik) { holiday.setCreatedAt(Instant.now()); holiday.setCreatedBy(actorNik); return publicHolidayRepo.save(holiday); }
	@Transactional public PublicHoliday updatePublicHoliday(Long id, PublicHoliday update, String actorNik) {
		PublicHoliday existing = publicHolidayRepo.findById(id).orElseThrow(() -> new NotFoundException("public-holiday", id));
		if (update.getHolidayName() != null) existing.setHolidayName(update.getHolidayName());
		if (update.getHolidayDate() != null) existing.setHolidayDate(update.getHolidayDate());
		existing.setUpdatedAt(Instant.now()); existing.setUpdatedBy(actorNik); return publicHolidayRepo.save(existing);
	}
	@Transactional public PublicHoliday deactivatePublicHoliday(Long id, String actorNik) {
		PublicHoliday existing = publicHolidayRepo.findById(id).orElseThrow(() -> new NotFoundException("public-holiday", id));
		existing.setUpdatedAt(Instant.now()); existing.setUpdatedBy(actorNik); return publicHolidayRepo.save(existing);
	}
	@Transactional public void deletePublicHoliday(Long id) { PublicHoliday existing = publicHolidayRepo.findById(id).orElseThrow(() -> new NotFoundException("public-holiday", id)); publicHolidayRepo.delete(existing); }

	@Transactional(readOnly = true) public Page<GeneralParameter> listGeneralParameters(Boolean includeHidden, Pageable pageable) {
		if (Boolean.TRUE.equals(includeHidden)) return generalParameterRepo.findAll(pageable);
		return generalParameterRepo.findByIsVisibleTrue(pageable);
	}
	@Transactional(readOnly = true) public Optional<GeneralParameter> getGeneralParameter(String parameterName) { return generalParameterRepo.findByParameter(parameterName); }
	@Transactional public GeneralParameter updateGeneralParameter(Long id, String newValue, String actorNik) {
		GeneralParameter param = generalParameterRepo.findById(id).orElseThrow(() -> new NotFoundException("general-parameter", id));
		if (!param.isUpdateable()) throw new NotUpdateableException(param.getParameter());
		param.setValue(newValue); param.setUpdatedAt(Instant.now()); param.setUpdatedBy(actorNik); return generalParameterRepo.save(param);
	}
	@Transactional public MasterChangeRequest createGeneralParameterChangeRequest(Long id, String newValue, String makerNik) {
		GeneralParameter param = generalParameterRepo.findById(id).orElseThrow(() -> new NotFoundException("general-parameter", id));
		if (!param.isUpdateable()) throw new NotUpdateableException(param.getParameter());
		String payload = "parameter=" + param.getParameter() + ",newValue=" + newValue;
		return makerCheckerService.submit("general-parameter", Action.update, payload, makerNik);
	}

	@Transactional(readOnly = true) public Page<PromotionLineText> listPromotionLineTexts(Pageable pageable) { return promotionLineTextRepo.findByIsActiveTrue(pageable); }
	@Transactional(readOnly = true) public Optional<PromotionLineText> getPromotionLineText(Long id) { return promotionLineTextRepo.findById(id); }
	@Transactional public PromotionLineText createPromotionLineText(PromotionLineText text, String actorNik) { text.setCreatedAt(Instant.now()); text.setCreatedBy(actorNik); text.setActive(true); return promotionLineTextRepo.save(text); }
	@Transactional public PromotionLineText updatePromotionLineText(Long id, PromotionLineText update, String actorNik) {
		PromotionLineText existing = promotionLineTextRepo.findById(id).orElseThrow(() -> new NotFoundException("promotion-line-text", id));
		if (update.getText() != null) existing.setText(update.getText());
		if (update.getDisplayColor() != null) existing.setDisplayColor(update.getDisplayColor());
		existing.setUpdatedAt(Instant.now()); existing.setUpdatedBy(actorNik); return promotionLineTextRepo.save(existing);
	}
	@Transactional public PromotionLineText deactivatePromotionLineText(Long id, String actorNik) {
		PromotionLineText existing = promotionLineTextRepo.findById(id).orElseThrow(() -> new NotFoundException("promotion-line-text", id));
		existing.setActive(false); existing.setUpdatedAt(Instant.now()); existing.setUpdatedBy(actorNik); return promotionLineTextRepo.save(existing);
	}

	public static class MissingJustificationException extends RuntimeException { public MissingJustificationException() { super("justification is required for blacklist-override (BR-BE07-06)"); } }
	public static class NotUpdateableException extends RuntimeException { private final String parameterName; public NotUpdateableException(String parameterName) { super("Parameter '" + parameterName + "' is not updateable (is_updateable=false, BR-BE07-23)"); this.parameterName = parameterName; } public String getParameterName() { return parameterName; } }
	public static class NotFoundException extends RuntimeException { public NotFoundException(String resource, Long id) { super(resource + " " + id + " not found"); } }

}
// SDD-PROVENANCE: U-010 | vault: .mega-sdd/vaults/acquisition-master-data | MasterOperationalService E29-E35 + 7 repos
