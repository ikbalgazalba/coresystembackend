package com.coresystem.coresystembackend.masterdata.transtype;

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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.coresystem.coresystembackend.masterdata.common.VersionedEntity;
import com.coresystem.coresystembackend.masterdata.makercheck.MasterChangeRequest;
import com.coresystem.coresystembackend.masterdata.makercheck.MakerCheckerService;
import com.coresystem.coresystembackend.masterdata.transtype.HierarchyMatrixService.HierarchyRuleViolationException;
import com.coresystem.coresystembackend.masterdata.transtype.HierarchyMatrixService.NextPicMustBeEmptyException;
import com.coresystem.coresystembackend.masterdata.transtype.HierarchyMatrixService.NextPicRequiredException;
import com.coresystem.coresystembackend.masterdata.transtype.HierarchyMatrixService.PicNotFoundException;
import com.coresystem.coresystembackend.masterdata.transtype.HierarchyMatrixService.PicResignedException;
import com.coresystem.coresystembackend.masterdata.user.EmployeeMirror;
import com.coresystem.coresystembackend.masterdata.user.EmployeeMirrorRepository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

/**
 * TDD test for U-008 — Approval-hierarchy level (cfg_hierarchy_matrix) + PIC picker E26-E28.
 *
 * <p>Verifies the acceptance criteria from the unit spec:
 * <ul>
 *   <li><strong>Entity mapping</strong> — {@link HierarchyMatrix} maps to
 *       {@code cfg_hierarchy_matrix}, extends {@link VersionedEntity}.</li>
 *   <li><strong>Unique key</strong> — {@code (transactionTypeCode, level, picNik)} via
 *       {@link UniqueConstraint}.</li>
 *   <li><strong>AC-10 / BR-BE07-15</strong> — {@code level==1 && isApprover==true} → 422
 *       HIERARCHY_RULE_VIOLATION.</li>
 *   <li><strong>BR-BE07-16</strong> — {@code isApprover==false && nextPicNik==null} → 422
 *       NEXT_PIC_REQUIRED; {@code isApprover==true && nextPicNik!=null} → 422
 *       NEXT_PIC_MUST_BE_EMPTY.</li>
 *   <li><strong>BR-BE07-17</strong> — {@code picNik}/{@code nextPicNik} must exist in
 *       {@link EmployeeMirrorRepository} and {@code !isResigned} → 422 PIC_NOT_FOUND /
 *       PIC_RESIGNED (fix OQ-MASTERDATA-02 V4/V6 — NIK tanpa guard).</li>
 *   <li><strong>Maker-checker</strong> (BR-BE07-05) — APPROVAL_HIERARCHY_LEVEL write goes through
 *       {@link MakerCheckerService#submit}.</li>
 *   <li><strong>E28 PIC picker</strong> — search EmployeeMirror by name/NIK.</li>
 * </ul>
 *
 * <p>Test structure follows the existing project patterns (TransactionTypeConfigTest):
 * Mockito-mocked repositories + mocked MakerCheckerService, verifying business logic in isolation.
 * All validation is server-side (AC-10).
 */
class HierarchyMatrixValidationTest {

	// ----------------------------------------------------------------------------------------------
	// Entity reflection tests — HierarchyMatrix
	// ----------------------------------------------------------------------------------------------

	@Test
	void hierarchyMatrixIsEntityWithCfgHierarchyMatrixTable() {
		assertThat(HierarchyMatrix.class).hasAnnotation(Entity.class);
		Table table = HierarchyMatrix.class.getAnnotation(Table.class);
		assertThat(table).as("HierarchyMatrix must carry @Table").isNotNull();
		assertThat(table.name()).isEqualTo("cfg_hierarchy_matrix");
	}

	@Test
	void hierarchyMatrixExtendsVersionedEntity() {
		assertThat(VersionedEntity.class).isAssignableFrom(HierarchyMatrix.class);
	}

	@Test
	void hierarchyMatrixHasIdentityIdPk() {
		Field idField = declaredField(HierarchyMatrix.class, "id");
		assertThat(idField.getType()).isEqualTo(Long.class);
		assertThat(idField.isAnnotationPresent(Id.class)).isTrue();
		GeneratedValue gv = idField.getAnnotation(GeneratedValue.class);
		assertThat(gv).isNotNull();
		assertThat(gv.strategy()).isEqualTo(GenerationType.IDENTITY);
	}

	@Test
	void hierarchyMatrixFields() {
		assertThat(fieldType(HierarchyMatrix.class, "transactionTypeCode")).isEqualTo(String.class);
		assertThat(fieldType(HierarchyMatrix.class, "level")).isEqualTo(int.class);
		assertThat(fieldType(HierarchyMatrix.class, "picNik")).isEqualTo(String.class);
		assertThat(fieldType(HierarchyMatrix.class, "picName")).isEqualTo(String.class);
		assertThat(fieldType(HierarchyMatrix.class, "nextPicNik")).isEqualTo(String.class);
		assertThat(fieldType(HierarchyMatrix.class, "nextPicName")).isEqualTo(String.class);
		assertThat(fieldType(HierarchyMatrix.class, "isApprover")).isEqualTo(boolean.class);
		assertThat(fieldType(HierarchyMatrix.class, "statusApprover")).isEqualTo(String.class);
		assertThat(fieldType(HierarchyMatrix.class, "escalationDays")).isEqualTo(int.class);
		assertThat(fieldType(HierarchyMatrix.class, "isActive")).isEqualTo(boolean.class);
	}

	@Test
	void hierarchyMatrixColumnsMapped() {
		assertThat(columnName(HierarchyMatrix.class, "transactionTypeCode"))
				.isEqualTo("transaction_type_code");
		assertThat(columnName(HierarchyMatrix.class, "level")).isEqualTo("level");
		assertThat(columnName(HierarchyMatrix.class, "picNik")).isEqualTo("pic_nik");
		assertThat(columnName(HierarchyMatrix.class, "picName")).isEqualTo("pic_name");
		assertThat(columnName(HierarchyMatrix.class, "nextPicNik")).isEqualTo("next_pic_nik");
		assertThat(columnName(HierarchyMatrix.class, "nextPicName")).isEqualTo("next_pic_name");
		assertThat(columnName(HierarchyMatrix.class, "isApprover")).isEqualTo("is_approver");
		assertThat(columnName(HierarchyMatrix.class, "statusApprover")).isEqualTo("status_approver");
		assertThat(columnName(HierarchyMatrix.class, "escalationDays")).isEqualTo("escalation_days");
		assertThat(columnName(HierarchyMatrix.class, "isActive")).isEqualTo("is_active");
	}

	@Test
	void hierarchyMatrixHasUniqueConstraintOnTransactionTypeCodeLevelPicNik() {
		Table table = HierarchyMatrix.class.getAnnotation(Table.class);
		assertThat(table.uniqueConstraints()).hasSize(1);
		UniqueConstraint uc = table.uniqueConstraints()[0];
		assertThat(uc.columnNames()).containsExactly("transaction_type_code", "level", "pic_nik");
	}

	@Test
	void hierarchyMatrixInheritsVersionFromVersionedEntity() {
		Field versionField;
		try {
			versionField = VersionedEntity.class.getDeclaredField("version");
		} catch (NoSuchFieldException e) {
			throw new AssertionError("VersionedEntity must declare a 'version' field", e);
		}
		assertThat(versionField.isAnnotationPresent(Version.class)).isTrue();
	}

	// ----------------------------------------------------------------------------------------------
	// Repository tests
	// ----------------------------------------------------------------------------------------------

	@Test
	void hierarchyMatrixRepositoryExtendsJpaRepository() {
		assertThat(JpaRepository.class).isAssignableFrom(HierarchyMatrixRepository.class);
	}

	// ----------------------------------------------------------------------------------------------
	// Service logic tests — AC-10 / BR-BE07-15: level==1 && isApprover==true → 422
	// ----------------------------------------------------------------------------------------------

	private HierarchyMatrixRepository matrixRepo;
	private EmployeeMirrorRepository employeeRepo;
	private MakerCheckerService makerCheckerService;
	private HierarchyMatrixService service;
	private final java.util.Map<Long, EmployeeMirror> employeeStore =
			new java.util.concurrent.ConcurrentHashMap<>();
	private final AtomicLong empIdGen = new AtomicLong(1);

	@BeforeEach
	void setUp() {
		matrixRepo = Mockito.mock(HierarchyMatrixRepository.class);
		employeeRepo = Mockito.mock(EmployeeMirrorRepository.class);
		makerCheckerService = Mockito.mock(MakerCheckerService.class);
		employeeStore.clear();
		empIdGen.set(1);

		// Mock findByNik: look up in the in-memory map.
		when(employeeRepo.findByNik(anyString())).thenAnswer(inv -> {
			String nik = inv.getArgument(0);
			return employeeStore.values().stream()
					.filter(e -> nik.equals(e.getNik()))
					.findFirst();
		});

		service = new HierarchyMatrixService(matrixRepo, employeeRepo, makerCheckerService);
	}

	@Test
	void create_level1IsApproverTrue_throws422HierarchyRuleViolation() {
		// BR-BE07-15: level==1 && isApprover==true → 422 HIERARCHY_RULE_VIOLATION
		seedEmployee("NIK001", "Alice", false);
		seedEmployee("NIK002", "Bob", false);

		assertThatThrownBy(() -> service.upsertHierarchyLevel(
				"AB0101", 1, "NIK001", true, "NIK002", "NIK003"))
				.isInstanceOf(HierarchyRuleViolationException.class)
				.hasMessageContaining("HIERARCHY_RULE_VIOLATION");

		verify(makerCheckerService, never()).submit(anyString(), any(), anyString(), anyString());
	}

	@Test
	void create_level1IsApproverFalse_succeeds() {
		// level==1 && isApprover==false is valid (level 1 is a requester, not an approver)
		seedEmployee("NIK001", "Alice", false);
		seedEmployee("NIK002", "Bob", false);

		MasterChangeRequest stub = new MasterChangeRequest();
		stub.setId(1L);
		when(makerCheckerService.submit(eq("APPROVAL_HIERARCHY_LEVEL"), any(), anyString(), eq("NIK003")))
				.thenReturn(stub);

		MasterChangeRequest result = service.upsertHierarchyLevel(
				"AB0101", 1, "NIK001", false, "NIK002", "NIK003");

		assertThat(result).isNotNull();
		verify(makerCheckerService).submit(eq("APPROVAL_HIERARCHY_LEVEL"), any(), anyString(), eq("NIK003"));
	}

	// ----------------------------------------------------------------------------------------------
	// Service logic tests — BR-BE07-16: next_pic required / must be empty
	// ----------------------------------------------------------------------------------------------

	@Test
	void create_isApproverFalseNextPicNull_throws422NextPicRequired() {
		// BR-BE07-16: isApprover==false && nextPicNik==null → 422 NEXT_PIC_REQUIRED
		seedEmployee("NIK001", "Alice", false);

		assertThatThrownBy(() -> service.upsertHierarchyLevel(
				"AB0101", 2, "NIK001", false, null, "NIK003"))
				.isInstanceOf(NextPicRequiredException.class)
				.hasMessageContaining("NEXT_PIC_REQUIRED");

		verify(makerCheckerService, never()).submit(anyString(), any(), anyString(), anyString());
	}

	@Test
	void create_isApproverTrueNextPicNotNull_throws422NextPicMustBeEmpty() {
		// BR-BE07-16: isApprover==true && nextPicNik!=null → 422 NEXT_PIC_MUST_BE_EMPTY
		seedEmployee("NIK001", "Alice", false);
		seedEmployee("NIK002", "Bob", false);

		assertThatThrownBy(() -> service.upsertHierarchyLevel(
				"AB0101", 3, "NIK001", true, "NIK002", "NIK003"))
				.isInstanceOf(NextPicMustBeEmptyException.class)
				.hasMessageContaining("NEXT_PIC_MUST_BE_EMPTY");

		verify(makerCheckerService, never()).submit(anyString(), any(), anyString(), anyString());
	}

	@Test
	void create_isApproverTrueNextPicNull_succeeds() {
		// isApprover==true && nextPicNik==null is valid (approver is the final level)
		seedEmployee("NIK001", "Alice", false);

		MasterChangeRequest stub = new MasterChangeRequest();
		stub.setId(1L);
		when(makerCheckerService.submit(eq("APPROVAL_HIERARCHY_LEVEL"), any(), anyString(), eq("NIK003")))
				.thenReturn(stub);

		MasterChangeRequest result = service.upsertHierarchyLevel(
				"AB0101", 3, "NIK001", true, null, "NIK003");

		assertThat(result).isNotNull();
		verify(makerCheckerService).submit(eq("APPROVAL_HIERARCHY_LEVEL"), any(), anyString(), eq("NIK003"));
	}

	// ----------------------------------------------------------------------------------------------
	// Service logic tests — BR-BE07-17: PIC not found / resigned
	// ----------------------------------------------------------------------------------------------

	@Test
	void create_picNikNotFound_throws422PicNotFound() {
		// BR-BE07-17: picNik must exist in EmployeeMirror → 422 PIC_NOT_FOUND
		// No employee seeded for NIK001
		seedEmployee("NIK002", "Bob", false);

		assertThatThrownBy(() -> service.upsertHierarchyLevel(
				"AB0101", 2, "NIK001", false, "NIK002", "NIK003"))
				.isInstanceOf(PicNotFoundException.class)
				.hasMessageContaining("PIC_NOT_FOUND");

		verify(makerCheckerService, never()).submit(anyString(), any(), anyString(), anyString());
	}

	@Test
	void create_picNikResigned_throws422PicResigned() {
		// BR-BE07-17: picNik exists but isResigned → 422 PIC_RESIGNED
		seedEmployee("NIK001", "Alice", true); // resigned
		seedEmployee("NIK002", "Bob", false);

		assertThatThrownBy(() -> service.upsertHierarchyLevel(
				"AB0101", 2, "NIK001", false, "NIK002", "NIK003"))
				.isInstanceOf(PicResignedException.class)
				.hasMessageContaining("PIC_RESIGNED");

		verify(makerCheckerService, never()).submit(anyString(), any(), anyString(), anyString());
	}

	@Test
	void create_nextPicNikNotFound_throws422PicNotFound() {
		// BR-BE07-17: nextPicNik must also exist → 422 PIC_NOT_FOUND
		seedEmployee("NIK001", "Alice", false);
		// No employee seeded for NIK002 (nextPicNik)

		assertThatThrownBy(() -> service.upsertHierarchyLevel(
				"AB0101", 2, "NIK001", false, "NIK002", "NIK003"))
				.isInstanceOf(PicNotFoundException.class)
				.hasMessageContaining("PIC_NOT_FOUND");

		verify(makerCheckerService, never()).submit(anyString(), any(), anyString(), anyString());
	}

	@Test
	void create_nextPicNikResigned_throws422PicResigned() {
		// BR-BE07-17: nextPicNik exists but isResigned → 422 PIC_RESIGNED
		seedEmployee("NIK001", "Alice", false);
		seedEmployee("NIK002", "Bob", true); // resigned

		assertThatThrownBy(() -> service.upsertHierarchyLevel(
				"AB0101", 2, "NIK001", false, "NIK002", "NIK003"))
				.isInstanceOf(PicResignedException.class)
				.hasMessageContaining("PIC_RESIGNED");

		verify(makerCheckerService, never()).submit(anyString(), any(), anyString(), anyString());
	}

	// ----------------------------------------------------------------------------------------------
	// Service logic tests — valid upsert submits to maker-checker
	// ----------------------------------------------------------------------------------------------

	@Test
	void create_validNonApprover_submitsToMakerChecker() {
		seedEmployee("NIK001", "Alice", false);
		seedEmployee("NIK002", "Bob", false);

		MasterChangeRequest stub = new MasterChangeRequest();
		stub.setId(1L);
		when(makerCheckerService.submit(eq("APPROVAL_HIERARCHY_LEVEL"), any(), anyString(), eq("NIK003")))
				.thenReturn(stub);

		MasterChangeRequest result = service.upsertHierarchyLevel(
				"AB0101", 2, "NIK001", false, "NIK002", "NIK003");

		assertThat(result).isNotNull();
		verify(makerCheckerService).submit(
				eq("APPROVAL_HIERARCHY_LEVEL"), any(), anyString(), eq("NIK003"));
	}

	@Test
	void create_validApprover_submitsToMakerChecker() {
		seedEmployee("NIK001", "Alice", false);

		MasterChangeRequest stub = new MasterChangeRequest();
		stub.setId(1L);
		when(makerCheckerService.submit(eq("APPROVAL_HIERARCHY_LEVEL"), any(), anyString(), eq("NIK003")))
				.thenReturn(stub);

		MasterChangeRequest result = service.upsertHierarchyLevel(
				"AB0101", 3, "NIK001", true, null, "NIK003");

		assertThat(result).isNotNull();
		verify(makerCheckerService).submit(
				eq("APPROVAL_HIERARCHY_LEVEL"), any(), anyString(), eq("NIK003"));
	}

	@Test
	void create_payloadContainsAllFields() {
		seedEmployee("NIK001", "Alice", false);
		seedEmployee("NIK002", "Bob", false);

		MasterChangeRequest stub = new MasterChangeRequest();
		stub.setId(1L);
		when(makerCheckerService.submit(eq("APPROVAL_HIERARCHY_LEVEL"), any(), anyString(), eq("NIK003")))
				.thenReturn(stub);

		service.upsertHierarchyLevel("AB0101", 2, "NIK001", false, "NIK002", "NIK003");

		org.mockito.ArgumentCaptor<String> payloadCaptor =
				org.mockito.ArgumentCaptor.forClass(String.class);
		verify(makerCheckerService).submit(
				eq("APPROVAL_HIERARCHY_LEVEL"), any(), payloadCaptor.capture(), eq("NIK003"));
		String payload = payloadCaptor.getValue();
		assertThat(payload).contains("\"transaction_type_code\":\"AB0101\"");
		assertThat(payload).contains("\"level\":2");
		assertThat(payload).contains("\"pic_nik\":\"NIK001\"");
		assertThat(payload).contains("\"pic_name\":\"Alice\"");
		assertThat(payload).contains("\"is_approver\":false");
		assertThat(payload).contains("\"next_pic_nik\":\"NIK002\"");
		assertThat(payload).contains("\"next_pic_name\":\"Bob\"");
	}

	// ----------------------------------------------------------------------------------------------
	// Service logic tests — E26 list per type
	// ----------------------------------------------------------------------------------------------

	@Test
	void listHierarchyLevels_returnsPageForTransactionType() {
		HierarchyMatrix h1 = hierarchyMatrix(1L, "AB0101", 1, "NIK001", "Alice", false, null, null, true);
		HierarchyMatrix h2 = hierarchyMatrix(2L, "AB0101", 2, "NIK002", "Bob", true, null, null, true);
		Pageable pageable = PageRequest.of(0, 20);
		when(matrixRepo.findByTransactionTypeCodeAndIsActiveTrue("AB0101", pageable))
				.thenReturn(new PageImpl<>(List.of(h1, h2), pageable, 2));

		Page<HierarchyMatrix> result = service.listHierarchyLevels("AB0101", pageable);

		assertThat(result.getContent()).hasSize(2);
		assertThat(result.getContent().get(0).getPicNik()).isEqualTo("NIK001");
		assertThat(result.getContent().get(1).getPicNik()).isEqualTo("NIK002");
	}

	// ----------------------------------------------------------------------------------------------
	// Service logic tests — E28 PIC picker
	// ----------------------------------------------------------------------------------------------

	@Test
	void searchPicCandidates_byName_returnsMatchingActiveEmployees() {
		EmployeeMirror e1 = seedEmployee("NIK001", "Alice Tan", false);
		EmployeeMirror e2 = seedEmployee("NIK002", "Bob Lee", false);
		Pageable pageable = PageRequest.of(0, 20);
		when(employeeRepo.findByNameContainingIgnoreCaseOrNikContainingIgnoreCaseAndIsResignedFalse(
				"Alice", "Alice", pageable))
				.thenReturn(new PageImpl<>(List.of(e1), pageable, 1));

		Page<EmployeeMirror> result = service.searchPicCandidates("Alice", pageable);

		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().get(0).getName()).isEqualTo("Alice Tan");
	}

	@Test
	void searchPicCandidates_byNik_returnsMatchingActiveEmployees() {
		EmployeeMirror e1 = seedEmployee("NIK001", "Alice", false);
		EmployeeMirror e2 = seedEmployee("NIK002", "Bob", false);
		Pageable pageable = PageRequest.of(0, 20);
		when(employeeRepo.findByNameContainingIgnoreCaseOrNikContainingIgnoreCaseAndIsResignedFalse(
				"NIK00", "NIK00", pageable))
				.thenReturn(new PageImpl<>(List.of(e1, e2), pageable, 2));

		Page<EmployeeMirror> result = service.searchPicCandidates("NIK00", pageable);

		assertThat(result.getContent()).hasSize(2);
	}

	// ----------------------------------------------------------------------------------------------
	// Helper methods
	// ----------------------------------------------------------------------------------------------

	private EmployeeMirror seedEmployee(String nik, String name, boolean resigned) {
		EmployeeMirror emp = new EmployeeMirror();
		reflectiveSetId(emp, empIdGen.getAndIncrement());
		emp.setNik(nik);
		emp.setName(name);
		emp.setResigned(resigned);
		emp.setCreatedAt(Instant.now());
		emp.setCreatedBy("SYSTEM");
		employeeStore.put(emp.getId(), emp);
		return emp;
	}

	private static HierarchyMatrix hierarchyMatrix(Long id, String typeCode, int level,
			String picNik, String picName, boolean isApprover, String nextPicNik,
			String nextPicName, boolean isActive) {
		HierarchyMatrix h = new HierarchyMatrix();
		reflectiveSetId(h, id);
		h.setTransactionTypeCode(typeCode);
		h.setLevel(level);
		h.setPicNik(picNik);
		h.setPicName(picName);
		h.setIsApprover(isApprover);
		h.setNextPicNik(nextPicNik);
		h.setNextPicName(nextPicName);
		h.setActive(isActive);
		h.setCreatedAt(Instant.now());
		h.setCreatedBy("SYSTEM");
		return h;
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
// SDD-PROVENANCE: U-008 | vault: .mega-sdd/vaults/acquisition-master-data | TDD test for HierarchyMatrix E26-E28 — entity mapping (cfg_hierarchy_matrix extends VersionedEntity; unique constraint transaction_type_code/level/pic_nik); AC-10/BR-BE07-15 level==1&&isApprover→422 HIERARCHY_RULE_VIOLATION; BR-BE07-16 next_pic required/empty; BR-BE07-17 PIC not found/resigned (fix OQ-MASTERDATA-02 V4/V6 NIK tanpa guard); APPROVAL_HIERARCHY_LEVEL write→MakerCheckerService.submit BR-BE07-05; E28 PIC picker name/NIK search
