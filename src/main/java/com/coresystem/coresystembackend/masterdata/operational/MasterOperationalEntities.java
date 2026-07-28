package com.coresystem.coresystembackend.masterdata.operational;

import java.time.LocalDate;
import com.coresystem.coresystembackend.masterdata.common.VersionedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

public final class MasterOperationalEntities {
	private MasterOperationalEntities() {}

	@Entity @Table(name = "mst_approval_reason")
	public static class ApprovalReason extends VersionedEntity {
		@Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id") private Long id;
		@Column(name = "reason_id", unique = true, nullable = false) private String reasonId;
		@Column(name = "description") private String description;
		@Column(name = "type", length = 2) private String type;
		@Column(name = "is_active", nullable = false) private boolean isActive;
		public Long getId() { return id; } public void setId(Long id) { this.id = id; }
		public String getReasonId() { return reasonId; } public void setReasonId(String reasonId) { this.reasonId = reasonId; }
		public String getDescription() { return description; } public void setDescription(String description) { this.description = description; }
		public String getType() { return type; } public void setType(String type) { this.type = type; }
		public boolean isActive() { return isActive; } public void setActive(boolean isActive) { this.isActive = isActive; }
	}

	@Entity @Table(name = "mst_credit_source")
	public static class CreditSource extends VersionedEntity {
		@Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id") private Long id;
		@Column(name = "credit_source_id", unique = true, nullable = false) private String creditSourceId;
		@Column(name = "description") private String description;
		@Column(name = "is_active", nullable = false) private boolean isActive;
		public Long getId() { return id; } public void setId(Long id) { this.id = id; }
		public String getCreditSourceId() { return creditSourceId; } public void setCreditSourceId(String creditSourceId) { this.creditSourceId = creditSourceId; }
		public String getDescription() { return description; } public void setDescription(String description) { this.description = description; }
		public boolean isActive() { return isActive; } public void setActive(boolean isActive) { this.isActive = isActive; }
	}

	@Entity @Table(name = "mst_branch_credit_source")
	public static class BranchCreditSource extends VersionedEntity {
		@Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id") private Long id;
		@Column(name = "branch_id", nullable = false) private String branchId;
		@Column(name = "credit_source_id", nullable = false) private String creditSourceId;
		@Column(name = "photo_required", nullable = false) private boolean photoRequired;
		@Column(name = "print_survey_report", nullable = false) private boolean printSurveyReport;
		@Column(name = "is_active", nullable = false) private boolean isActive;
		public Long getId() { return id; } public void setId(Long id) { this.id = id; }
		public String getBranchId() { return branchId; } public void setBranchId(String branchId) { this.branchId = branchId; }
		public String getCreditSourceId() { return creditSourceId; } public void setCreditSourceId(String creditSourceId) { this.creditSourceId = creditSourceId; }
		public boolean isPhotoRequired() { return photoRequired; } public void setPhotoRequired(boolean photoRequired) { this.photoRequired = photoRequired; }
		public boolean isPrintSurveyReport() { return printSurveyReport; } public void setPrintSurveyReport(boolean printSurveyReport) { this.printSurveyReport = printSurveyReport; }
		public boolean isActive() { return isActive; } public void setActive(boolean isActive) { this.isActive = isActive; }
	}

	@Entity @Table(name = "mst_blacklist_override")
	public static class BlacklistOverride extends VersionedEntity {
		@Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id") private Long id;
		@Column(name = "national_id", length = 16) private String nationalId;
		@Column(name = "reason_code", length = 10) private String reasonCode;
		@Column(name = "justification", nullable = false) private String justification;
		@Column(name = "valid_from") private LocalDate validFrom;
		@Column(name = "valid_until") private LocalDate validUntil;
		@Column(name = "is_active", nullable = false) private boolean isActive;
		public Long getId() { return id; } public void setId(Long id) { this.id = id; }
		public String getNationalId() { return nationalId; } public void setNationalId(String nationalId) { this.nationalId = nationalId; }
		public String getReasonCode() { return reasonCode; } public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
		public String getJustification() { return justification; } public void setJustification(String justification) { this.justification = justification; }
		public LocalDate getValidFrom() { return validFrom; } public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }
		public LocalDate getValidUntil() { return validUntil; } public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }
		public boolean isActive() { return isActive; } public void setActive(boolean isActive) { this.isActive = isActive; }
	}

	@Entity @Table(name = "mst_public_holiday")
	public static class PublicHoliday extends VersionedEntity {
		@Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id") private Long id;
		@Column(name = "holiday_name", nullable = false) private String holidayName;
		@Column(name = "holiday_date", nullable = false, unique = true) private LocalDate holidayDate;
		public Long getId() { return id; } public void setId(Long id) { this.id = id; }
		public String getHolidayName() { return holidayName; } public void setHolidayName(String holidayName) { this.holidayName = holidayName; }
		public LocalDate getHolidayDate() { return holidayDate; } public void setHolidayDate(LocalDate holidayDate) { this.holidayDate = holidayDate; }
	}

	@Entity @Table(name = "mst_general_parameter")
	public static class GeneralParameter extends VersionedEntity {
		@Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id") private Long id;
		@Column(name = "parameter", unique = true, nullable = false) private String parameter;
		@Column(name = "value") private String value;
		@Column(name = "unit") private String unit;
		@Column(name = "description") private String description;
		@Column(name = "is_updateable", nullable = false) private boolean isUpdateable;
		@Column(name = "is_visible", nullable = false) private boolean isVisible;
		public Long getId() { return id; } public void setId(Long id) { this.id = id; }
		public String getParameter() { return parameter; } public void setParameter(String parameter) { this.parameter = parameter; }
		public String getValue() { return value; } public void setValue(String value) { this.value = value; }
		public String getUnit() { return unit; } public void setUnit(String unit) { this.unit = unit; }
		public String getDescription() { return description; } public void setDescription(String description) { this.description = description; }
		public boolean isUpdateable() { return isUpdateable; } public void setUpdateable(boolean isUpdateable) { this.isUpdateable = isUpdateable; }
		public boolean isVisible() { return isVisible; } public void setVisible(boolean isVisible) { this.isVisible = isVisible; }
	}

	@Entity @Table(name = "mst_promotion_line_text")
	public static class PromotionLineText extends VersionedEntity {
		@Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id") private Long id;
		@Column(name = "text") private String text;
		@Column(name = "display_color") private String displayColor;
		@Column(name = "is_active", nullable = false) private boolean isActive;
		public Long getId() { return id; } public void setId(Long id) { this.id = id; }
		public String getText() { return text; } public void setText(String text) { this.text = text; }
		public String getDisplayColor() { return displayColor; } public void setDisplayColor(String displayColor) { this.displayColor = displayColor; }
		public boolean isActive() { return isActive; } public void setActive(boolean isActive) { this.isActive = isActive; }
	}
}
// SDD-PROVENANCE: U-010 | vault: .mega-sdd/vaults/acquisition-master-data | 7 master operasional entities bundled