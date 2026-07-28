package com.coresystem.coresystembackend.masterdata.operational;

import java.time.LocalDate;

import com.coresystem.coresystembackend.masterdata.common.VersionedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "cfg_number_format")
public class NumberFormat extends VersionedEntity {

	public enum ResetPeriod { NONE, MONTHLY, YEARLY }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "code_type", nullable = false, length = 50)
	private String codeType;

	@Column(name = "company_id")
	private String companyId;

	@Column(name = "branch_id")
	private String branchId;

	@Column(name = "format_template", length = 100)
	private String formatTemplate;

	@Enumerated(EnumType.STRING)
	@Column(name = "reset_period", length = 10)
	private ResetPeriod resetPeriod;

	@Column(name = "sequence_name")
	private String sequenceName;

	@Column(name = "effective_from")
	private LocalDate effectiveFrom;

	@Column(name = "effective_to")
	private LocalDate effectiveTo;

	@Column(name = "is_active", nullable = false)
	private boolean isActive;

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public String getCodeType() { return codeType; }
	public void setCodeType(String codeType) { this.codeType = codeType; }
	public String getCompanyId() { return companyId; }
	public void setCompanyId(String companyId) { this.companyId = companyId; }
	public String getBranchId() { return branchId; }
	public void setBranchId(String branchId) { this.branchId = branchId; }
	public String getFormatTemplate() { return formatTemplate; }
	public void setFormatTemplate(String formatTemplate) { this.formatTemplate = formatTemplate; }
	public ResetPeriod getResetPeriod() { return resetPeriod; }
	public void setResetPeriod(ResetPeriod resetPeriod) { this.resetPeriod = resetPeriod; }
	public String getSequenceName() { return sequenceName; }
	public void setSequenceName(String sequenceName) { this.sequenceName = sequenceName; }
	public LocalDate getEffectiveFrom() { return effectiveFrom; }
	public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
	public LocalDate getEffectiveTo() { return effectiveTo; }
	public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
	public boolean isActive() { return isActive; }
	public void setActive(boolean isActive) { this.isActive = isActive; }
}
// SDD-PROVENANCE: U-012 | vault: .mega-sdd/vaults/acquisition-master-data | NumberFormat @Entity cfg_number_format extends VersionedEntity; codeType VARCHAR(50) target CREDIT_ID BR-33; formatTemplate VARCHAR(100); resetPeriod enum; sequenceName DB sequence DB-CONVENTIONS 6.5; OQ-GT-02
