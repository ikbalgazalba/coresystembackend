package com.coresystem.coresystembackend.masterdata.operational;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresystem.coresystembackend.masterdata.makercheck.MakerCheckerService;
import com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest;
import com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest.Action;

@ConditionalOnBean(JpaRepository.class)
@Service
public class NumberFormatService {

	static final String CREDIT_ID = "CREDIT_ID";
	static final String STUB_SEQUENCE = "00001";

	private final NumberFormatRepository numberFormatRepository;
	private final MakerCheckerService makerCheckerService;

	public NumberFormatService(
			NumberFormatRepository numberFormatRepository,
			MakerCheckerService makerCheckerService) {
		this.numberFormatRepository = numberFormatRepository;
		this.makerCheckerService = makerCheckerService;
	}

	@Transactional(readOnly = true)
	public Page<NumberFormat> listNumberFormats(String codeType, Pageable pageable) {
		if (codeType != null && !codeType.isBlank()) {
			return numberFormatRepository.findByCodeTypeAndIsActiveTrue(codeType, pageable);
		}
		return numberFormatRepository.findByIsActiveTrue(pageable);
	}

	@Transactional(readOnly = true)
	public Optional<NumberFormat> getNumberFormat(Long id) {
		return numberFormatRepository.findById(id);
	}

	@Transactional
	public MasterChangeRequest createNumberFormat(
			String codeType, String companyId, String branchId,
			String formatTemplate, NumberFormat.ResetPeriod resetPeriod,
			String sequenceName, LocalDate effectiveFrom, LocalDate effectiveTo,
			String actorNik) {
		if (CREDIT_ID.equals(codeType)) {
			String payload = buildCreatePayload(codeType, companyId, branchId, formatTemplate,
					resetPeriod, sequenceName, effectiveFrom, effectiveTo);
			return makerCheckerService.submit("NUMBER_FORMAT", Action.create, payload, actorNik);
		}
		NumberFormat nf = new NumberFormat();
		nf.setCodeType(codeType);
		nf.setCompanyId(companyId);
		nf.setBranchId(branchId);
		nf.setFormatTemplate(formatTemplate);
		nf.setResetPeriod(resetPeriod);
		nf.setSequenceName(sequenceName);
		nf.setEffectiveFrom(effectiveFrom);
		nf.setEffectiveTo(effectiveTo);
		nf.setActive(true);
		nf.setCreatedAt(java.time.Instant.now());
		nf.setCreatedBy(actorNik);
		numberFormatRepository.save(nf);
		MasterChangeRequest direct = new MasterChangeRequest();
		direct.setResource("NUMBER_FORMAT");
		direct.setAction(Action.create);
		direct.setStatus(MasterChangeRequest.Status.applied);
		return direct;
	}

	@Transactional
	public MasterChangeRequest updateNumberFormat(
			Long id, String codeType, String companyId, String branchId,
			String formatTemplate, NumberFormat.ResetPeriod resetPeriod,
			String sequenceName, LocalDate effectiveFrom, LocalDate effectiveTo,
			String actorNik) {
		NumberFormat existing = numberFormatRepository.findById(id)
				.orElseThrow(() -> new NumberFormatNotFoundException(id));
		if (CREDIT_ID.equals(codeType)) {
			String payload = buildUpdatePayload(id, codeType, companyId, branchId, formatTemplate,
					resetPeriod, sequenceName, effectiveFrom, effectiveTo);
			return makerCheckerService.submit("NUMBER_FORMAT", Action.update, payload, actorNik);
		}
		if (codeType != null) existing.setCodeType(codeType);
		if (companyId != null) existing.setCompanyId(companyId);
		if (branchId != null) existing.setBranchId(branchId);
		if (formatTemplate != null) existing.setFormatTemplate(formatTemplate);
		if (resetPeriod != null) existing.setResetPeriod(resetPeriod);
		if (sequenceName != null) existing.setSequenceName(sequenceName);
		if (effectiveFrom != null) existing.setEffectiveFrom(effectiveFrom);
		if (effectiveTo != null) existing.setEffectiveTo(effectiveTo);
		existing.setUpdatedAt(java.time.Instant.now());
		existing.setUpdatedBy(actorNik);
		numberFormatRepository.save(existing);
		MasterChangeRequest direct = new MasterChangeRequest();
		direct.setResource("NUMBER_FORMAT");
		direct.setAction(Action.update);
		direct.setStatus(MasterChangeRequest.Status.applied);
		return direct;
	}

	public String generateCreditId(String branchId) {
		String branch = padRight(branchId, 5);
		LocalDate now = LocalDate.now();
		String yy = String.format("%02d", now.getYear() % 100);
		String mm = String.format("%02d", now.getMonthValue());
		String seq = STUB_SEQUENCE;
		return branch + yy + mm + seq;
	}

	private static String padRight(String value, int length) {
		if (value == null) return "0".repeat(length);
		if (value.length() >= length) return value.substring(0, length);
		return value + "0".repeat(length - value.length());
	}

	private static String buildCreatePayload(String codeType, String companyId, String branchId,
			String formatTemplate, NumberFormat.ResetPeriod resetPeriod, String sequenceName,
			LocalDate effectiveFrom, LocalDate effectiveTo) {
		return "{\"code_type\":\"" + escape(codeType) + "\","
				+ "\"company_id\":\"" + escape(companyId) + "\","
				+ "\"branch_id\":\"" + escape(branchId) + "\","
				+ "\"format_template\":\"" + escape(formatTemplate) + "\","
				+ "\"reset_period\":\"" + (resetPeriod != null ? resetPeriod.name() : "") + "\","
				+ "\"sequence_name\":\"" + escape(sequenceName) + "\","
				+ "\"effective_from\":\"" + (effectiveFrom != null ? effectiveFrom.toString() : "") + "\","
				+ "\"effective_to\":\"" + (effectiveTo != null ? effectiveTo.toString() : "") + "\"}";
	}

	private static String buildUpdatePayload(Long id, String codeType, String companyId,
			String branchId, String formatTemplate, NumberFormat.ResetPeriod resetPeriod,
			String sequenceName, LocalDate effectiveFrom, LocalDate effectiveTo) {
		return "{\"id\":" + id + ","
				+ "\"code_type\":\"" + escape(codeType) + "\","
				+ "\"company_id\":\"" + escape(companyId) + "\","
				+ "\"branch_id\":\"" + escape(branchId) + "\","
				+ "\"format_template\":\"" + escape(formatTemplate) + "\","
				+ "\"reset_period\":\"" + (resetPeriod != null ? resetPeriod.name() : "") + "\","
				+ "\"sequence_name\":\"" + escape(sequenceName) + "\","
				+ "\"effective_from\":\"" + (effectiveFrom != null ? effectiveFrom.toString() : "") + "\","
				+ "\"effective_to\":\"" + (effectiveTo != null ? effectiveTo.toString() : "") + "\"}";
	}

	private static String escape(String value) {
		return value != null ? value.replace("\\\\", "\\\\\\\\").replace("\"", "\\\\\"") : "";
	}

	public static class NumberFormatNotFoundException extends RuntimeException {
		public NumberFormatNotFoundException(Long id) {
			super("NumberFormat " + id + " not found");
		}
	}
}

interface NumberFormatRepository extends JpaRepository<NumberFormat, Long> {
	Page<NumberFormat> findByIsActiveTrue(Pageable pageable);
	Page<NumberFormat> findByCodeTypeAndIsActiveTrue(String codeType, Pageable pageable);
}
// SDD-PROVENANCE: U-012 | vault: .mega-sdd/vaults/acquisition-master-data | NumberFormatService - E38 CRUD; CREDIT_ID maker-checker BR-BE07-05; generateCreditId OQ-GT-02; bundled NumberFormatRepository
