package com.coresystem.coresystembackend.masterdata.operational;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.coresystem.coresystembackend.masterdata.common.VersionedEntity;
import com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest;
import com.coresystem.coresystembackend.masterdata.makercheck.MakerCheckerService;
import com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest.Action;
import com.coresystem.coresystembackend.masterdata.operational.NumberFormat.ResetPeriod;
import com.coresystem.coresystembackend.masterdata.operational.NumberFormatService.NumberFormatNotFoundException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

class GlLinkAndNumberFormatTest {

	@Test
	void glTransactionTypeLinkIsEntityWithMapTable() {
		assertThat(GlTransactionTypeLink.class).hasAnnotation(Entity.class);
		Table table = GlTransactionTypeLink.class.getAnnotation(Table.class);
		assertThat(table).as("GlTransactionTypeLink must carry @Table").isNotNull();
		assertThat(table.name()).isEqualTo("map_transaction_type_gl");
	}

	@Test
	void glTransactionTypeLinkExtendsVersionedEntity() {
		assertThat(VersionedEntity.class).isAssignableFrom(GlTransactionTypeLink.class);
	}

	@Test
	void glTransactionTypeLinkHasIdentityIdPk() {
		Field idField = declaredField(GlTransactionTypeLink.class, "id");
		assertThat(idField.getType()).isEqualTo(Long.class);
		assertThat(idField.isAnnotationPresent(Id.class)).isTrue();
		GeneratedValue gv = idField.getAnnotation(GeneratedValue.class);
		assertThat(gv).isNotNull();
		assertThat(gv.strategy()).isEqualTo(GenerationType.IDENTITY);
	}

	@Test
	void glTransactionTypeLinkUniqueConstraintOnTrxIdClassId() {
		Table table = GlTransactionTypeLink.class.getAnnotation(Table.class);
		UniqueConstraint[] ucs = table.uniqueConstraints();
		assertThat(ucs).hasSize(1);
		assertThat(ucs[0].name()).isEqualTo("ux_map_transaction_type_gl_trx_id_class_id");
		assertThat(ucs[0].columnNames()).containsExactly("trx_id", "class_id");
	}

	@Test
	void glTransactionTypeLinkColumnsMapped() {
		assertThat(columnName(GlTransactionTypeLink.class, "trxId")).isEqualTo("trx_id");
		assertThat(columnName(GlTransactionTypeLink.class, "classId")).isEqualTo("class_id");
		assertThat(columnName(GlTransactionTypeLink.class, "glAccountNo")).isEqualTo("gl_account_no");
	}

	@Test
	void glTransactionTypeLinkFields() {
		assertThat(fieldType(GlTransactionTypeLink.class, "trxId")).isEqualTo(String.class);
		assertThat(fieldType(GlTransactionTypeLink.class, "classId")).isEqualTo(String.class);
		assertThat(fieldType(GlTransactionTypeLink.class, "glAccountNo")).isEqualTo(String.class);
	}

	@Test
	void numberFormatIsEntityWithCfgTable() {
		assertThat(NumberFormat.class).hasAnnotation(Entity.class);
		Table table = NumberFormat.class.getAnnotation(Table.class);
		assertThat(table).as("NumberFormat must carry @Table").isNotNull();
		assertThat(table.name()).isEqualTo("cfg_number_format");
	}

	@Test
	void numberFormatExtendsVersionedEntity() {
		assertThat(VersionedEntity.class).isAssignableFrom(NumberFormat.class);
	}

	@Test
	void numberFormatHasIdentityIdPk() {
		Field idField = declaredField(NumberFormat.class, "id");
		assertThat(idField.getType()).isEqualTo(Long.class);
		assertThat(idField.isAnnotationPresent(Id.class)).isTrue();
		GeneratedValue gv = idField.getAnnotation(GeneratedValue.class);
		assertThat(gv).isNotNull();
		assertThat(gv.strategy()).isEqualTo(GenerationType.IDENTITY);
	}

	@Test
	void numberFormatColumnsMapped() {
		assertThat(columnName(NumberFormat.class, "codeType")).isEqualTo("code_type");
		assertThat(columnName(NumberFormat.class, "companyId")).isEqualTo("company_id");
		assertThat(columnName(NumberFormat.class, "branchId")).isEqualTo("branch_id");
		assertThat(columnName(NumberFormat.class, "formatTemplate")).isEqualTo("format_template");
		assertThat(columnName(NumberFormat.class, "sequenceName")).isEqualTo("sequence_name");
		assertThat(columnName(NumberFormat.class, "effectiveFrom")).isEqualTo("effective_from");
		assertThat(columnName(NumberFormat.class, "effectiveTo")).isEqualTo("effective_to");
		assertThat(columnName(NumberFormat.class, "isActive")).isEqualTo("is_active");
	}

	@Test
	void numberFormatCodeTypeIsVarchar50() {
		Field field = declaredField(NumberFormat.class, "codeType");
		Column column = field.getAnnotation(Column.class);
		assertThat(column.length()).isEqualTo(50);
	}

	@Test
	void numberFormatFormatTemplateIsVarchar100() {
		Field field = declaredField(NumberFormat.class, "formatTemplate");
		Column column = field.getAnnotation(Column.class);
		assertThat(column.length()).isEqualTo(100);
	}

	@Test
	void numberFormatResetPeriodIsEnumWithStringPersistence() {
		Field field = declaredField(NumberFormat.class, "resetPeriod");
		assertThat(field.isAnnotationPresent(Enumerated.class)).isTrue();
		Enumerated enumerated = field.getAnnotation(Enumerated.class);
		assertThat(enumerated.value()).isEqualTo(EnumType.STRING);
	}

	@Test
	void numberFormatResetPeriodEnumValues() {
		assertThat(ResetPeriod.values())
				.containsExactlyInAnyOrder(ResetPeriod.NONE, ResetPeriod.MONTHLY, ResetPeriod.YEARLY);
	}

	@Test
	void numberFormatFields() {
		assertThat(fieldType(NumberFormat.class, "codeType")).isEqualTo(String.class);
		assertThat(fieldType(NumberFormat.class, "companyId")).isEqualTo(String.class);
		assertThat(fieldType(NumberFormat.class, "branchId")).isEqualTo(String.class);
		assertThat(fieldType(NumberFormat.class, "formatTemplate")).isEqualTo(String.class);
		assertThat(fieldType(NumberFormat.class, "resetPeriod")).isEqualTo(ResetPeriod.class);
		assertThat(fieldType(NumberFormat.class, "sequenceName")).isEqualTo(String.class);
		assertThat(fieldType(NumberFormat.class, "effectiveFrom")).isEqualTo(LocalDate.class);
		assertThat(fieldType(NumberFormat.class, "effectiveTo")).isEqualTo(LocalDate.class);
		assertThat(fieldType(NumberFormat.class, "isActive")).isEqualTo(boolean.class);
	}

	@Test
	void numberFormatInheritsVersionFromVersionedEntity() throws NoSuchFieldException {
		Field versionField = VersionedEntity.class.getDeclaredField("version");
		assertThat(versionField.isAnnotationPresent(Version.class)).isTrue();
	}

	@Test
	void glTransactionTypeLinkRepositoryExtendsJpaRepository() {
		assertThat(JpaRepository.class).isAssignableFrom(GlTransactionTypeLinkRepository.class);
	}

	@Test
	void numberFormatRepositoryExtendsJpaRepository() {
		assertThat(JpaRepository.class).isAssignableFrom(NumberFormatRepository.class);
	}

	@Test
	void e36_deleteReturns405NotAllowed() {
		GlLinkController controller = new GlLinkController(null);
		ResponseEntity<?> response = controller.deleteGlLink(1L);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
		assertThat(response.getBody()).isNotNull();
	}

	private GlTransactionTypeLinkRepository glLinkRepo;
	private MakerCheckerService makerCheckerService;
	private NumberFormatRepository numberFormatRepo;
	private NumberFormatService numberFormatService;

	@BeforeEach
	void setUp() {
		glLinkRepo = Mockito.mock(GlTransactionTypeLinkRepository.class);
		makerCheckerService = Mockito.mock(MakerCheckerService.class);
		numberFormatRepo = Mockito.mock(NumberFormatRepository.class);
		numberFormatService = new NumberFormatService(numberFormatRepo, makerCheckerService);
	}

	@Test
	void e36_patchSubmitsToMakerChecker() {
		GlTransactionTypeLink existing = glLink(1L, "AB0101", "01", "1001-0100");
		when(glLinkRepo.findById(1L)).thenReturn(Optional.of(existing));
		MasterChangeRequest stubRequest = new MasterChangeRequest();
		stubRequest.setId(99L);
		when(makerCheckerService.submit(eq("GL_TRANSACTION_TYPE_LINK"), eq(Action.update),
				anyString(), eq("NIK001"))).thenReturn(stubRequest);
		GlLinkController controller = new GlLinkController(
				new GlLinkController.GlLinkService(glLinkRepo, makerCheckerService));
		GlTransactionTypeLink patch = new GlTransactionTypeLink();
		patch.setGlAccountNo("2001-0200");
		ResponseEntity<MasterChangeRequest> response = controller.updateGlLink(1L, patch, "NIK001");
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(response.getBody().getId()).isEqualTo(99L);
		verify(makerCheckerService).submit(eq("GL_TRANSACTION_TYPE_LINK"), eq(Action.update), anyString(), eq("NIK001"));
	}

	@Test
	void e36_patchNonExistentReturns404() {
		when(glLinkRepo.findById(999L)).thenReturn(Optional.empty());
		GlLinkController controller = new GlLinkController(
				new GlLinkController.GlLinkService(glLinkRepo, makerCheckerService));
		GlTransactionTypeLink patch = new GlTransactionTypeLink();
		patch.setGlAccountNo("2001-0200");
		assertThatThrownBy(() -> controller.updateGlLink(999L, patch, "NIK001"))
				.isInstanceOf(GlLinkController.GlLinkService.GlLinkNotFoundException.class);
	}

	@Test
	void e36_getByIdReturnsLink() {
		GlTransactionTypeLink link = glLink(1L, "AB0101", "01", "1001-0100");
		when(glLinkRepo.findById(1L)).thenReturn(Optional.of(link));
		GlLinkController controller = new GlLinkController(
				new GlLinkController.GlLinkService(glLinkRepo, makerCheckerService));
		ResponseEntity<GlTransactionTypeLink> response = controller.getGlLink(1L);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().getTrxId()).isEqualTo("AB0101");
		assertThat(response.getBody().getGlAccountNo()).isEqualTo("1001-0100");
	}

	@Test
	void e36_getByIdNotFoundReturns404() {
		when(glLinkRepo.findById(999L)).thenReturn(Optional.empty());
		GlLinkController controller = new GlLinkController(
				new GlLinkController.GlLinkService(glLinkRepo, makerCheckerService));
		ResponseEntity<GlTransactionTypeLink> response = controller.getGlLink(999L);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void e36_listReturnsPage() {
		GlTransactionTypeLink link1 = glLink(1L, "AB0101", "01", "1001-0100");
		GlTransactionTypeLink link2 = glLink(2L, "AB0102", "02", "1001-0200");
		Pageable pageable = PageRequest.of(0, 20);
		when(glLinkRepo.findByIsActiveTrue(pageable))
				.thenReturn(new PageImpl<>(List.of(link1, link2), pageable, 2));
		GlLinkController controller = new GlLinkController(
				new GlLinkController.GlLinkService(glLinkRepo, makerCheckerService));
		var response = controller.listGlLinks(null, 0, 20);
		assertThat(response.items()).hasSize(2);
		assertThat(response.recordCount()).isEqualTo(2);
	}

	@Test
	void e38_creditIdPatchSubmitsToMakerChecker() {
		NumberFormat existing = numberFormat(1L, "CREDIT_ID", "CO01", "BR001",
				"BR001YYMMSEQ", ResetPeriod.MONTHLY, "seq_credit_id_br001");
		when(numberFormatRepo.findById(1L)).thenReturn(Optional.of(existing));
		MasterChangeRequest stubRequest = new MasterChangeRequest();
		stubRequest.setId(77L);
		when(makerCheckerService.submit(eq("NUMBER_FORMAT"), eq(Action.update),
				anyString(), eq("NIK001"))).thenReturn(stubRequest);
		MasterChangeRequest result = numberFormatService.updateNumberFormat(
				1L, "CREDIT_ID", "CO01", "BR001", "BR001YYMMSEQ2", ResetPeriod.YEARLY,
				"seq_credit_id_br001_v2", null, null, "NIK001");
		assertThat(result.getId()).isEqualTo(77L);
		verify(makerCheckerService).submit(eq("NUMBER_FORMAT"), eq(Action.update), anyString(), eq("NIK001"));
	}

	@Test
	void e38_nonCreditIdPatchAppliesDirectly() {
		NumberFormat existing = numberFormat(1L, "AGREEMENT_NO", "CO01", "BR001",
				"AGYYMMSEQ", ResetPeriod.YEARLY, "seq_ag");
		when(numberFormatRepo.findById(1L)).thenReturn(Optional.of(existing));
		when(numberFormatRepo.save(any(NumberFormat.class))).thenAnswer(inv -> inv.getArgument(0));
		MasterChangeRequest result = numberFormatService.updateNumberFormat(
				1L, "AGREEMENT_NO", "CO01", "BR001", "AGYYMMSEQ2", ResetPeriod.MONTHLY,
				"seq_ag_v2", null, null, "NIK001");
		assertThat(result.getStatus()).isEqualTo(MasterChangeRequest.Status.applied);
		assertThat(existing.getFormatTemplate()).isEqualTo("AGYYMMSEQ2");
		verify(makerCheckerService, never()).submit(anyString(), any(), anyString(), anyString());
	}

	@Test
	void e38_creditIdCreateSubmitsToMakerChecker() {
		MasterChangeRequest stubRequest = new MasterChangeRequest();
		stubRequest.setId(55L);
		when(makerCheckerService.submit(eq("NUMBER_FORMAT"), eq(Action.create),
				anyString(), eq("NIK001"))).thenReturn(stubRequest);
		MasterChangeRequest result = numberFormatService.createNumberFormat(
				"CREDIT_ID", "CO01", "BR001", "BR001YYMMSEQ", ResetPeriod.MONTHLY,
				"seq_credit_id_br001", LocalDate.of(2026, 1, 1), null, "NIK001");
		assertThat(result.getId()).isEqualTo(55L);
		verify(makerCheckerService).submit(eq("NUMBER_FORMAT"), eq(Action.create), anyString(), eq("NIK001"));
	}

	@Test
	void e38_nonCreditIdCreateAppliesDirectly() {
		when(numberFormatRepo.save(any(NumberFormat.class))).thenAnswer(inv -> inv.getArgument(0));
		MasterChangeRequest result = numberFormatService.createNumberFormat(
				"AGREEMENT_NO", "CO01", "BR001", "AGYYMMSEQ", ResetPeriod.YEARLY,
				"seq_ag", LocalDate.of(2026, 1, 1), null, "NIK001");
		assertThat(result.getStatus()).isEqualTo(MasterChangeRequest.Status.applied);
		// Verify the entity was saved with correct values
		org.mockito.ArgumentCaptor<NumberFormat> captor =
				org.mockito.ArgumentCaptor.forClass(NumberFormat.class);
		verify(numberFormatRepo).save(captor.capture());
		NumberFormat saved = captor.getValue();
		assertThat(saved.getCodeType()).isEqualTo("AGREEMENT_NO");
		assertThat(saved.isActive()).isTrue();
		assertThat(saved.getCreatedBy()).isEqualTo("NIK001");
		verify(makerCheckerService, never()).submit(anyString(), any(), anyString(), anyString());
	}

	@Test
	void generateCreditIdProduces14CharFormat() {
		String creditId = numberFormatService.generateCreditId("BR001");
		assertThat(creditId).hasSize(14);
		assertThat(creditId.substring(0, 5)).isEqualTo("BR001");
		int currentYear = java.time.Year.now().getValue();
		String expectedYY = String.valueOf(currentYear).substring(2);
		assertThat(creditId.substring(5, 7)).isEqualTo(expectedYY);
		String expectedMM = String.format("%02d", java.time.LocalDate.now().getMonthValue());
		assertThat(creditId.substring(7, 9)).isEqualTo(expectedMM);
		assertThat(creditId.substring(9)).hasSize(5);
		assertThat(creditId.substring(9)).matches("\\d{5}");
	}

	@Test
	void generateCreditIdBranchPaddedTo5() {
		String creditId = numberFormatService.generateCreditId("BR");
		assertThat(creditId).hasSize(14);
		assertThat(creditId.substring(0, 5)).isEqualTo("BR000");
	}

	@Test
	void e38_getByIdReturnsFormat() {
		NumberFormat nf = numberFormat(1L, "CREDIT_ID", "CO01", "BR001",
				"BR001YYMMSEQ", ResetPeriod.MONTHLY, "seq_cr");
		when(numberFormatRepo.findById(1L)).thenReturn(Optional.of(nf));
		NumberFormatController controller = new NumberFormatController(numberFormatService);
		ResponseEntity<NumberFormat> response = controller.getNumberFormat(1L);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().getCodeType()).isEqualTo("CREDIT_ID");
	}

	@Test
	void e38_getByIdNotFoundReturns404() {
		when(numberFormatRepo.findById(999L)).thenReturn(Optional.empty());
		NumberFormatController controller = new NumberFormatController(numberFormatService);
		ResponseEntity<NumberFormat> response = controller.getNumberFormat(999L);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void e38_listReturnsPage() {
		NumberFormat nf1 = numberFormat(1L, "CREDIT_ID", "CO01", "BR001",
				"BR001YYMMSEQ", ResetPeriod.MONTHLY, "seq1");
		NumberFormat nf2 = numberFormat(2L, "AGREEMENT_NO", "CO01", "BR001",
				"AGYYMMSEQ", ResetPeriod.YEARLY, "seq2");
		Pageable pageable = PageRequest.of(0, 20);
		when(numberFormatRepo.findByIsActiveTrue(pageable))
				.thenReturn(new PageImpl<>(List.of(nf1, nf2), pageable, 2));
		NumberFormatController controller = new NumberFormatController(numberFormatService);
		var response = controller.listNumberFormats(null, 0, 20);
		assertThat(response.items()).hasSize(2);
	}

	@Test
	void e38_listByCodeTypeFilters() {
		NumberFormat nf1 = numberFormat(1L, "CREDIT_ID", "CO01", "BR001",
				"BR001YYMMSEQ", ResetPeriod.MONTHLY, "seq1");
		Pageable pageable = PageRequest.of(0, 20);
		when(numberFormatRepo.findByCodeTypeAndIsActiveTrue(eq("CREDIT_ID"), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(nf1), pageable, 1));
		NumberFormatController controller = new NumberFormatController(numberFormatService);
		var response = controller.listNumberFormats("CREDIT_ID", 0, 20);
		assertThat(response.items()).hasSize(1);
		assertThat(response.items().get(0).getCodeType()).isEqualTo("CREDIT_ID");
	}

	@Test
	void e38_patchNonExistentThrowsNotFound() {
		when(numberFormatRepo.findById(999L)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> numberFormatService.updateNumberFormat(
				999L, "CREDIT_ID", "CO01", "BR001", "FMT", ResetPeriod.NONE,
				"seq", null, null, "NIK001"))
				.isInstanceOf(NumberFormatNotFoundException.class);
	}

	private static GlTransactionTypeLink glLink(Long id, String trxId, String classId, String glAccountNo) {
		GlTransactionTypeLink link = new GlTransactionTypeLink();
		reflectiveSetId(link, id);
		link.setTrxId(trxId);
		link.setClassId(classId);
		link.setGlAccountNo(glAccountNo);
		link.setCreatedAt(Instant.now());
		link.setCreatedBy("SYSTEM");
		return link;
	}

	private static NumberFormat numberFormat(Long id, String codeType, String companyId,
			String branchId, String formatTemplate, ResetPeriod resetPeriod, String sequenceName) {
		NumberFormat nf = new NumberFormat();
		reflectiveSetId(nf, id);
		nf.setCodeType(codeType);
		nf.setCompanyId(companyId);
		nf.setBranchId(branchId);
		nf.setFormatTemplate(formatTemplate);
		nf.setResetPeriod(resetPeriod);
		nf.setSequenceName(sequenceName);
		nf.setActive(true);
		nf.setCreatedAt(Instant.now());
		nf.setCreatedBy("SYSTEM");
		return nf;
	}

	private static void reflectiveSetId(Object entity, long id) {
		try {
			var field = entity.getClass().getDeclaredField("id");
			field.setAccessible(true);
			field.set(entity, id);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Cannot set id on " + entity.getClass(), e);
		}
	}

	private static Class<?> fieldType(Class<?> type, String fieldName) {
		return declaredField(type, fieldName).getType();
	}

	private static String columnName(Class<?> type, String fieldName) {
		Column column = declaredField(type, fieldName).getAnnotation(Column.class);
		assertThat(column).as("%s.%s must carry @Column", type.getSimpleName(), fieldName).isNotNull();
		return column.name();
	}

	private static Field declaredField(Class<?> type, String fieldName) {
		try {
			return type.getDeclaredField(fieldName);
		} catch (NoSuchFieldException e) {
			throw new AssertionError(type.getSimpleName() + " must declare field '" + fieldName + "'", e);
		}
	}
}
// SDD-PROVENANCE: U-012 | vault: .mega-sdd/vaults/acquisition-master-data | TDD test E36/E38 - no-delete BR-BE07-24; CoA zero-diff; PATCH maker-checker; CREDIT_ID maker-checker; generateCreditId OQ-GT-02; NumberFormat entity cfg_number_format
