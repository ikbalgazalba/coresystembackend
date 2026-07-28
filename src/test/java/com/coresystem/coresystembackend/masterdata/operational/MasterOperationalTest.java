package com.coresystem.coresystembackend.masterdata.operational;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import com.coresystem.coresystembackend.masterdata.common.VersionedEntity;
import com.coresystem.coresystembackend.masterdata.makercheck.MakerCheckerService;
import com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest;
import com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest.Action;
import com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest.Status;
import com.coresystem.coresystembackend.masterdata.operational.MasterOperationalService.NotUpdateableException;
import com.coresystem.coresystembackend.masterdata.operational.MasterOperationalEntities.ApprovalReason;
import com.coresystem.coresystembackend.masterdata.operational.MasterOperationalEntities.BlacklistOverride;
import com.coresystem.coresystembackend.masterdata.operational.MasterOperationalEntities.BranchCreditSource;
import com.coresystem.coresystembackend.masterdata.operational.MasterOperationalEntities.CreditSource;
import com.coresystem.coresystembackend.masterdata.operational.MasterOperationalEntities.GeneralParameter;
import com.coresystem.coresystembackend.masterdata.operational.MasterOperationalEntities.PublicHoliday;
import com.coresystem.coresystembackend.masterdata.operational.MasterOperationalEntities.PromotionLineText;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

class MasterOperationalTest {
	@Test void approvalReasonIsEntityWithMstApprovalReasonTable() { assertThat(ApprovalReason.class).hasAnnotation(Entity.class); assertThat(ApprovalReason.class.getAnnotation(Table.class).name()).isEqualTo("mst_approval_reason"); }
	@Test void approvalReasonExtendsVersionedEntity() { assertThat(VersionedEntity.class).isAssignableFrom(ApprovalReason.class); }
	@Test void approvalReasonHasReasonIdUniqueColumn() { Field f = declaredField(ApprovalReason.class, "reasonId"); assertThat(f.getAnnotation(Column.class).name()).isEqualTo("reason_id"); assertThat(f.getAnnotation(Column.class).unique()).isTrue(); }
	@Test void approvalReasonTypeIsStringNotHardcodedSubset() { assertThat(fieldType(ApprovalReason.class, "type")).isEqualTo(String.class); }
	@Test void creditSourceIsEntityWithMstCreditSourceTable() { assertThat(CreditSource.class).hasAnnotation(Entity.class); assertThat(CreditSource.class.getAnnotation(Table.class).name()).isEqualTo("mst_credit_source"); }
	@Test void creditSourceExtendsVersionedEntity() { assertThat(VersionedEntity.class).isAssignableFrom(CreditSource.class); }
	@Test void creditSourceHasCreditSourceIdUnique() { assertThat(declaredField(CreditSource.class, "creditSourceId").getAnnotation(Column.class).unique()).isTrue(); }
	@Test void branchCreditSourceIsEntityWithMstBranchCreditSourceTable() { assertThat(BranchCreditSource.class).hasAnnotation(Entity.class); assertThat(BranchCreditSource.class.getAnnotation(Table.class).name()).isEqualTo("mst_branch_credit_source"); }
	@Test void branchCreditSourceHasPhotoRequiredAndPrintSurveyReport() { assertThat(fieldType(BranchCreditSource.class, "photoRequired")).isEqualTo(boolean.class); assertThat(fieldType(BranchCreditSource.class, "printSurveyReport")).isEqualTo(boolean.class); }
	@Test void blacklistOverrideIsEntityWithMstBlacklistOverrideTable() { assertThat(BlacklistOverride.class).hasAnnotation(Entity.class); assertThat(BlacklistOverride.class.getAnnotation(Table.class).name()).isEqualTo("mst_blacklist_override"); }
	@Test void blacklistOverrideNationalIdIsLocked16CharVarchar() { Column col = declaredField(BlacklistOverride.class, "nationalId").getAnnotation(Column.class); assertThat(col.name()).isEqualTo("national_id"); assertThat(col.length()).isEqualTo(16); }
	@Test void blacklistOverrideJustificationField() { assertThat(fieldType(BlacklistOverride.class, "justification")).isEqualTo(String.class); }
	@Test void blacklistOverrideValidFromAndValidUntil() { assertThat(fieldType(BlacklistOverride.class, "validFrom")).isEqualTo(LocalDate.class); assertThat(fieldType(BlacklistOverride.class, "validUntil")).isEqualTo(LocalDate.class); }
	@Test void publicHolidayIsEntityWithMstPublicHolidayTable() { assertThat(PublicHoliday.class).hasAnnotation(Entity.class); assertThat(PublicHoliday.class.getAnnotation(Table.class).name()).isEqualTo("mst_public_holiday"); }
	@Test void publicHolidayHolidayDateIsNotNullUnique() { Column col = declaredField(PublicHoliday.class, "holidayDate").getAnnotation(Column.class); assertThat(col.nullable()).isFalse(); assertThat(col.unique()).isTrue(); }
	@Test void publicHolidayHolidayNameIsNotNull() { assertThat(declaredField(PublicHoliday.class, "holidayName").getAnnotation(Column.class).nullable()).isFalse(); }
	@Test void generalParameterIsEntityWithMstGeneralParameterTable() { assertThat(GeneralParameter.class).hasAnnotation(Entity.class); assertThat(GeneralParameter.class.getAnnotation(Table.class).name()).isEqualTo("mst_general_parameter"); }
	@Test void generalParameterHasParameterUniqueColumn() { assertThat(declaredField(GeneralParameter.class, "parameter").getAnnotation(Column.class).unique()).isTrue(); }
	@Test void generalParameterIsUpdateableAndIsVisibleFields() { assertThat(fieldType(GeneralParameter.class, "isUpdateable")).isEqualTo(boolean.class); assertThat(fieldType(GeneralParameter.class, "isVisible")).isEqualTo(boolean.class); }
	@Test void promotionLineTextIsEntityWithMstPromotionLineTextTable() { assertThat(PromotionLineText.class).hasAnnotation(Entity.class); assertThat(PromotionLineText.class.getAnnotation(Table.class).name()).isEqualTo("mst_promotion_line_text"); }
	@Test void promotionLineTextDisplayColorAndIsActive() { assertThat(fieldType(PromotionLineText.class, "text")).isEqualTo(String.class); assertThat(fieldType(PromotionLineText.class, "displayColor")).isEqualTo(String.class); assertThat(fieldType(PromotionLineText.class, "isActive")).isEqualTo(boolean.class); }
	@Test void allEntitiesHaveIdentityIdPk() { for (Class<?> entity : new Class<?>[] { ApprovalReason.class, CreditSource.class, BranchCreditSource.class, BlacklistOverride.class, PublicHoliday.class, GeneralParameter.class, PromotionLineText.class }) { Field idField = declaredField(entity, "id"); assertThat(idField.getType()).isEqualTo(Long.class); assertThat(idField.isAnnotationPresent(Id.class)).isTrue(); GeneratedValue gv = idField.getAnnotation(GeneratedValue.class); assertThat(gv).isNotNull(); assertThat(gv.strategy()).isEqualTo(GenerationType.IDENTITY); } }

	@Nested class ServiceTests {
		private ApprovalReasonRepository approvalReasonRepo; private CreditSourceRepository creditSourceRepo; private BranchCreditSourceRepository branchCreditSourceRepo; private BlacklistOverrideRepository blacklistOverrideRepo; private PublicHolidayRepository publicHolidayRepo; private GeneralParameterRepository generalParameterRepo; private PromotionLineTextRepository promotionLineTextRepo; private MakerCheckerService makerCheckerService; private MasterOperationalService service;
		@BeforeEach void setUp() { approvalReasonRepo = mock(ApprovalReasonRepository.class); creditSourceRepo = mock(CreditSourceRepository.class); branchCreditSourceRepo = mock(BranchCreditSourceRepository.class); blacklistOverrideRepo = mock(BlacklistOverrideRepository.class); publicHolidayRepo = mock(PublicHolidayRepository.class); generalParameterRepo = mock(GeneralParameterRepository.class); promotionLineTextRepo = mock(PromotionLineTextRepository.class); makerCheckerService = mock(MakerCheckerService.class); service = new MasterOperationalService(approvalReasonRepo, creditSourceRepo, branchCreditSourceRepo, blacklistOverrideRepo, publicHolidayRepo, generalParameterRepo, promotionLineTextRepo, makerCheckerService); }
		@Test void ac15_blacklistOverrideWithoutJustification_throwsMissingJustification() { BlacklistOverride override = new BlacklistOverride(); override.setNationalId("1234567890123456"); override.setJustification(null); assertThatThrownBy(() -> service.createBlacklistOverride(override, "NIK001")).isInstanceOf(MasterOperationalService.MissingJustificationException.class).hasMessageContaining("justification"); }
		@Test void ac15_blacklistOverrideWithBlankJustification_throwsMissingJustification() { BlacklistOverride override = new BlacklistOverride(); override.setNationalId("1234567890123456"); override.setJustification("   "); assertThatThrownBy(() -> service.createBlacklistOverride(override, "NIK001")).isInstanceOf(MasterOperationalService.MissingJustificationException.class); }
		@Test void ac15_blacklistOverrideWithJustification_submitsMakerChecker() { BlacklistOverride override = new BlacklistOverride(); override.setNationalId("1234567890123456"); override.setJustification("AML override"); override.setReasonCode("1"); override.setValidFrom(LocalDate.of(2026,1,1)); override.setValidUntil(LocalDate.of(2026,12,31)); override.setActive(true); MasterChangeRequest mockRequest = new MasterChangeRequest(); mockRequest.setId(1L); mockRequest.setStatus(Status.pending_approval); when(makerCheckerService.submit(eq("blacklist-override"), eq(Action.create), any(String.class), eq("NIK001"))).thenReturn(mockRequest); assertThat(service.createBlacklistOverride(override, "NIK001").getStatus()).isEqualTo(Status.pending_approval); }
		@Test void ac16_updateGeneralParameterNotUpdateable_throwsNotUpdateableException() { GeneralParameter param = new GeneralParameter(); param.setId(1L); param.setParameter("MAX_DAILY_LIMIT"); param.setUpdateable(false); param.setValue("1000000"); when(generalParameterRepo.findById(1L)).thenReturn(Optional.of(param)); assertThatThrownBy(() -> service.updateGeneralParameter(1L, "5000000", "NIK001")).isInstanceOf(NotUpdateableException.class).hasMessageContaining("is_updateable"); }
		@Test void ac16_updateGeneralParameterUpdateable_succeeds() { GeneralParameter param = new GeneralParameter(); param.setId(1L); param.setParameter("DAILY_RATE"); param.setUpdateable(true); param.setValue("0.05"); param.setCreatedAt(Instant.now()); param.setCreatedBy("SYSTEM"); when(generalParameterRepo.findById(1L)).thenReturn(Optional.of(param)); when(generalParameterRepo.save(any(GeneralParameter.class))).thenAnswer(inv -> inv.getArgument(0)); assertThat(service.updateGeneralParameter(1L, "0.06", "NIK001").getValue()).isEqualTo("0.06"); }
		@Test void ac16_updateGeneralParameter_makerCheckerForWrite() { GeneralParameter param = new GeneralParameter(); param.setId(1L); param.setParameter("KEY_PARAM"); param.setUpdateable(true); param.setValue("old_value"); when(generalParameterRepo.findById(1L)).thenReturn(Optional.of(param)); MasterChangeRequest mockRequest = new MasterChangeRequest(); mockRequest.setId(2L); mockRequest.setStatus(Status.pending_approval); when(makerCheckerService.submit(eq("general-parameter"), eq(Action.update), any(String.class), eq("NIK001"))).thenReturn(mockRequest); assertThat(service.createGeneralParameterChangeRequest(1L, "new_value", "NIK001").getStatus()).isEqualTo(Status.pending_approval); }
		@Test void e29_approvalReasonListWithTypeFilter_returnsFilteredResults() { ApprovalReason t1 = approvalReason("R01","Desc1","1",true); ApprovalReason t9 = approvalReason("R09","Desc9","9",true); when(approvalReasonRepo.findByIsActiveTrue(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(t1,t9))); when(approvalReasonRepo.findByTypeAndIsActiveTrue(eq("9"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(t9))); assertThat(service.listApprovalReasons(null, PageRequest.of(0,20)).getContent()).hasSize(2); Page<ApprovalReason> r = service.listApprovalReasons("9", PageRequest.of(0,20)); assertThat(r.getContent()).hasSize(1); assertThat(r.getContent().get(0).getType()).isEqualTo("9"); }
		@Test void e29_approvalReasonTypeFilterDoesNotRestrictValues() { when(approvalReasonRepo.findByTypeAndIsActiveTrue(eq("99"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of())); assertThat(service.listApprovalReasons("99", PageRequest.of(0,20)).getContent()).isEmpty(); }
		@Test void e33_publicHolidayDeleteAllowed() { PublicHoliday h = new PublicHoliday(); h.setId(1L); h.setHolidayName("Independence Day"); h.setHolidayDate(LocalDate.of(2026,8,17)); when(publicHolidayRepo.findById(1L)).thenReturn(Optional.of(h)); service.deletePublicHoliday(1L); }
		@Test void ac14_approvalReasonDeactivateOnly() { ApprovalReason r = approvalReason("R01","Desc","1",true); when(approvalReasonRepo.findById(1L)).thenReturn(Optional.of(r)); when(approvalReasonRepo.save(any(ApprovalReason.class))).thenAnswer(inv -> inv.getArgument(0)); assertThat(service.deactivateApprovalReason(1L,"NIK001").isActive()).isFalse(); }
		@Test void ac14_creditSourceDeactivateOnly() { CreditSource cs = new CreditSource(); cs.setId(1L); cs.setActive(true); when(creditSourceRepo.findById(1L)).thenReturn(Optional.of(cs)); when(creditSourceRepo.save(any(CreditSource.class))).thenAnswer(inv -> inv.getArgument(0)); assertThat(service.deactivateCreditSource(1L,"NIK001").isActive()).isFalse(); }
	}

	@Nested class ControllerTests {
		private MasterOperationalService service; private MasterOperationalController controller;
		@BeforeEach void setUp() { service = mock(MasterOperationalService.class); controller = new MasterOperationalController(service); }
		@Test void ac14_deleteApprovalReason_returns405() { assertThat(controller.deleteApprovalReason(1L).getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED); }
		@Test void ac14_deleteCreditSource_returns405() { assertThat(controller.deleteCreditSource(1L).getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED); }
		@Test void ac14_deleteBranchCreditSource_returns405() { assertThat(controller.deleteBranchCreditSource(1L).getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED); }
		@Test void ac14_deleteBlacklistOverride_returns405() { assertThat(controller.deleteBlacklistOverride(1L).getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED); }
		@Test void ac14_deleteGeneralParameter_returns405() { assertThat(controller.deleteGeneralParameter("MAX_DAILY_LIMIT").getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED); }
		@Test void ac14_deletePromotionLineText_returns405() { assertThat(controller.deletePromotionLineText(1L).getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED); }
		@Test void e33_deletePublicHoliday_returnsNot405() { assertThat(controller.deletePublicHoliday(1L).getStatusCode().value()).isNotEqualTo(405); }
		@Test void ac15_blacklistOverrideMissingJustification_returns422() { when(service.createBlacklistOverride(any(), any())).thenThrow(new MasterOperationalService.MissingJustificationException()); assertThat(controller.createBlacklistOverride(new BlacklistOverride(), "NIK001").getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY); }
		@Test void ac15_blacklistOverrideWithJustification_returns202() { MasterChangeRequest mockRequest = new MasterChangeRequest(); mockRequest.setId(1L); mockRequest.setStatus(Status.pending_approval); when(service.createBlacklistOverride(any(), any())).thenReturn(mockRequest); BlacklistOverride override = new BlacklistOverride(); override.setJustification("AML override"); assertThat(controller.createBlacklistOverride(override, "NIK001").getStatusCode()).isEqualTo(HttpStatus.ACCEPTED); }
		@Test void ac16_updateNotUpdateableGeneralParameter_returns409() { GeneralParameter param = new GeneralParameter(); param.setId(1L); param.setParameter("MAX_DAILY_LIMIT"); param.setUpdateable(false); when(service.getGeneralParameter("MAX_DAILY_LIMIT")).thenReturn(Optional.of(param)); when(service.updateGeneralParameter(any(), any(), any())).thenThrow(new NotUpdateableException("MAX_DAILY_LIMIT")); assertThat(controller.updateGeneralParameter("MAX_DAILY_LIMIT", new MasterOperationalController.GeneralParameterUpdateRequest("5000000"), "NIK001").getStatusCode()).isEqualTo(HttpStatus.CONFLICT); }
		@Test void ac16_createGeneralParameter_returns405() { assertThat(controller.createGeneralParameter(new GeneralParameter()).getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED); }
		@Test void e29_approvalReasonListAcceptsTypeFilter() { ApprovalReason r1 = approvalReason("R01","Desc1","1",true); when(service.listApprovalReasons(eq("1"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(r1))); var response = controller.listApprovalReasons("1", 0, 20); assertThat(response).isNotNull(); assertThat(response.items()).hasSize(1); }
	}

	private static ApprovalReason approvalReason(String reasonId, String description, String type, boolean isActive) { ApprovalReason r = new ApprovalReason(); r.setId(1L); r.setReasonId(reasonId); r.setDescription(description); r.setType(type); r.setActive(isActive); r.setCreatedAt(Instant.now()); r.setCreatedBy("SYSTEM"); return r; }
	private static Class<?> fieldType(Class<?> type, String fieldName) { return declaredField(type, fieldName).getType(); }
	private static Field declaredField(Class<?> type, String fieldName) { try { return type.getDeclaredField(fieldName); } catch (NoSuchFieldException e) { throw new AssertionError(type.getSimpleName() + " must declare field '" + fieldName + "'", e); } }
}
// SDD-PROVENANCE: U-010 | vault: .mega-sdd/vaults/acquisition-master-data | TDD test AC-14/15/16 + E29
