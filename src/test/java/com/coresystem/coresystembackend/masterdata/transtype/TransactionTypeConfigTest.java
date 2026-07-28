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
import com.coresystem.coresystembackend.masterdata.transtype.TransactionTypeService.ImmutableFieldViolationException;
import com.coresystem.coresystembackend.masterdata.transtype.TransactionTypeService.TransactionCodeNotFoundException;
import com.coresystem.coresystembackend.masterdata.transtype.TransactionTypeService.TransactionTypeAlreadyExistsException;
import com.coresystem.coresystembackend.masterdata.transtype.TransactionTypeService.TransactionTypeNotFoundException;
import com.coresystem.coresystembackend.masterdata.transtype.TransactionTypeService.TransactionTypePatchRequest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * TDD test for U-007 — Transaction-Code + Transaction-Type config (E22-E25).
 *
 * <p>Verifies the acceptance criteria from the unit spec:
 * <ul>
 *   <li><strong>Entity mapping</strong> — {@link TransactionCode} maps to
 *       {@code cfg_transaction_code}, {@link TransactionType} maps to {@code cfg_transaction_type};
 *       both extend {@link VersionedEntity}.</li>
 *   <li><strong>[LOCKED] external-FK</strong> (BR-PRODASSET-7) —
 *       {@code TransactionType.transactionTypeCode} carries {@code @Column(name="transaction_type_code")}
 *       char-for-char.</li>
 *   <li><strong>AC-11 / BR-BE07-18</strong> — PATCH only accepts {@code is_active}; PATCHing
 *       {@code description} or {@code mapping} → {@link ImmutableFieldViolationException}.</li>
 *   <li><strong>E23 upsert</strong> (BR-BE07-19) — upper-case normalize server-side.</li>
 *   <li><strong>Mapping disimpan eksplisit</strong> (BR-BE07-18) — mapping is stored as-is, NOT
 *       derived from {@code substring(0,2)}.</li>
 *   <li><strong>Mapping must reference existing TransactionCode</strong> →
 *       {@link TransactionCodeNotFoundException} (422) if not found.</li>
 *   <li><strong>Maker-checker</strong> (BR-BE07-05) — TRANSACTION_TYPE write goes through
 *       {@link MakerCheckerService#submit}.</li>
 * </ul>
 *
 * <p>Test structure follows the existing project patterns:
 * <ul>
 *   <li>Entity reflection tests ( DealerEntityTest pattern) — pure reflection, no Spring context.</li>
 *   <li>Service logic tests ( MakerCheckerEnvelopeTest pattern) — Mockito-mocked repositories +
 *       mocked MakerCheckerService, verifying business logic in isolation.</li>
 * </ul>
 */
class TransactionTypeConfigTest {

	// ----------------------------------------------------------------------------------------------
	// Entity reflection tests — TransactionCode
	// ----------------------------------------------------------------------------------------------

	@Test
	void transactionCodeIsEntityWithCfgTransactionCodeTable() {
		assertThat(TransactionCode.class).hasAnnotation(Entity.class);
		Table table = TransactionCode.class.getAnnotation(Table.class);
		assertThat(table).as("TransactionCode must carry @Table").isNotNull();
		assertThat(table.name()).isEqualTo("cfg_transaction_code");
	}

	@Test
	void transactionCodeExtendsVersionedEntity() {
		assertThat(VersionedEntity.class).isAssignableFrom(TransactionCode.class);
	}

	@Test
	void transactionCodeHasIdentityIdPk() {
		Field idField = declaredField(TransactionCode.class, "id");
		assertThat(idField.getType()).isEqualTo(Long.class);
		assertThat(idField.isAnnotationPresent(Id.class)).isTrue();
		GeneratedValue gv = idField.getAnnotation(GeneratedValue.class);
		assertThat(gv).isNotNull();
		assertThat(gv.strategy()).isEqualTo(GenerationType.IDENTITY);
	}

	@Test
	void transactionCodeFields() {
		assertThat(fieldType(TransactionCode.class, "branchId")).isEqualTo(String.class);
		assertThat(fieldType(TransactionCode.class, "transactionCode")).isEqualTo(String.class);
		assertThat(fieldType(TransactionCode.class, "formRequester")).isEqualTo(String.class);
		assertThat(fieldType(TransactionCode.class, "formApproval")).isEqualTo(String.class);
	}

	@Test
	void transactionCodeColumnsMapped() {
		assertThat(columnName(TransactionCode.class, "branchId")).isEqualTo("branch_id");
		assertThat(columnName(TransactionCode.class, "transactionCode")).isEqualTo("transaction_code");
		assertThat(columnName(TransactionCode.class, "formRequester")).isEqualTo("form_requester");
		assertThat(columnName(TransactionCode.class, "formApproval")).isEqualTo("form_approval");
	}

	// ----------------------------------------------------------------------------------------------
	// Entity reflection tests — TransactionType
	// ----------------------------------------------------------------------------------------------

	@Test
	void transactionTypeIsEntityWithCfgTransactionTypeTable() {
		assertThat(TransactionType.class).hasAnnotation(Entity.class);
		Table table = TransactionType.class.getAnnotation(Table.class);
		assertThat(table).as("TransactionType must carry @Table").isNotNull();
		assertThat(table.name()).isEqualTo("cfg_transaction_type");
	}

	@Test
	void transactionTypeExtendsVersionedEntity() {
		assertThat(VersionedEntity.class).isAssignableFrom(TransactionType.class);
	}

	@Test
	void transactionTypeHasIdentityIdPk() {
		Field idField = declaredField(TransactionType.class, "id");
		assertThat(idField.getType()).isEqualTo(Long.class);
		assertThat(idField.isAnnotationPresent(Id.class)).isTrue();
		GeneratedValue gv = idField.getAnnotation(GeneratedValue.class);
		assertThat(gv).isNotNull();
		assertThat(gv.strategy()).isEqualTo(GenerationType.IDENTITY);
	}

	@Test
	void transactionTypeCodeIsLockedExternalFkCharForChar() {
		// BR-PRODASSET-7 [LOCKED]: @Column(name="transaction_type_code") char-for-char
		Field field = declaredField(TransactionType.class, "transactionTypeCode");
		assertThat(field.isAnnotationPresent(Column.class)).isTrue();
		Column column = field.getAnnotation(Column.class);
		assertThat(column.name()).isEqualTo("transaction_type_code");
	}

	@Test
	void transactionTypeFields() {
		assertThat(fieldType(TransactionType.class, "transactionTypeCode")).isEqualTo(String.class);
		assertThat(fieldType(TransactionType.class, "description")).isEqualTo(String.class);
		assertThat(fieldType(TransactionType.class, "mapping")).isEqualTo(String.class);
		assertThat(fieldType(TransactionType.class, "isActive")).isEqualTo(boolean.class);
	}

	@Test
	void transactionTypeColumnsMapped() {
		assertThat(columnName(TransactionType.class, "transactionTypeCode"))
				.isEqualTo("transaction_type_code");
		assertThat(columnName(TransactionType.class, "description")).isEqualTo("description");
		assertThat(columnName(TransactionType.class, "mapping")).isEqualTo("mapping");
		assertThat(columnName(TransactionType.class, "isActive")).isEqualTo("is_active");
	}

	@Test
	void transactionTypeInheritsVersionFromVersionedEntity() {
		// VersionedEntity declares @Version Integer version; TransactionType inherits it.
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
	void transactionCodeRepositoryExtendsJpaRepository() {
		assertThat(JpaRepository.class).isAssignableFrom(TransactionCodeRepository.class);
	}

	@Test
	void transactionTypeRepositoryExtendsJpaRepository() {
		assertThat(JpaRepository.class).isAssignableFrom(TransactionTypeRepository.class);
	}

	// ----------------------------------------------------------------------------------------------
	// Service logic tests — E23 upsert (BR-BE07-19 upper-case normalize)
	// ----------------------------------------------------------------------------------------------

	private TransactionCodeRepository codeRepo;
	private TransactionTypeRepository typeRepo;
	private MakerCheckerService makerCheckerService;
	private TransactionTypeService service;
	private final AtomicLong idGenerator = new AtomicLong(1);
	private final java.util.Map<Long, TransactionCode> codeStore =
			new java.util.concurrent.ConcurrentHashMap<>();

	@BeforeEach
	void setUp() {
		codeRepo = Mockito.mock(TransactionCodeRepository.class);
		typeRepo = Mockito.mock(TransactionTypeRepository.class);
		makerCheckerService = Mockito.mock(MakerCheckerService.class);
		codeStore.clear();
		idGenerator.set(1);

		// Mock save: assign ID if null, store in map, return the entity.
		when(codeRepo.save(any(TransactionCode.class))).thenAnswer(inv -> {
			TransactionCode entity = inv.getArgument(0);
			if (entity.getId() == null) {
				reflectiveSetId(entity, idGenerator.getAndIncrement());
			}
			codeStore.put(entity.getId(), entity);
			return entity;
		});

		// Mock findByBranchIdAndTransactionCode: look up in the in-memory map.
		when(codeRepo.findByBranchIdAndTransactionCode(anyString(), anyString()))
				.thenAnswer(inv -> {
					String branchId = inv.getArgument(0);
					String code = inv.getArgument(1);
					return codeStore.values().stream()
							.filter(tc -> branchId.equals(tc.getBranchId())
									&& code.equals(tc.getTransactionCode()))
							.findFirst();
				});

		// Mock findAll (used by requireTransactionCodeExists) — return the current store values.
		when(codeRepo.findAll()).thenAnswer(inv -> List.copyOf(codeStore.values()));

		service = new TransactionTypeService(codeRepo, typeRepo, makerCheckerService);
	}

	@Test
	void upsert_lowerCaseCode_isNormalizedToUpperCase() {
		service.upsertTransactionCode("BR01", "ab01", "FRM1", "FAP1", "NIK001");

		// The saved entity must have upper-case transaction code.
		TransactionCode saved = codeStore.values().iterator().next();
		assertThat(saved.getTransactionCode()).isEqualTo("AB01");
		assertThat(saved.getBranchId()).isEqualTo("BR01");
		assertThat(saved.getFormRequester()).isEqualTo("FRM1");
		assertThat(saved.getFormApproval()).isEqualTo("FAP1");
		assertThat(saved.getCreatedBy()).isEqualTo("NIK001");
		assertThat(saved.getCreatedAt()).isNotNull();
	}

	@Test
	void upsert_existingCode_updatesInPlace() {
		// First upsert (insert).
		service.upsertTransactionCode("BR01", "AB01", "FRM1", "FAP1", "NIK001");
		Long originalId = codeStore.values().iterator().next().getId();
		Instant originalCreatedAt = codeStore.values().iterator().next().getCreatedAt();

		// Second upsert (update — same branchId + code).
		service.upsertTransactionCode("BR01", "ab01", "FRM2", "FAP2", "NIK002");

		assertThat(codeStore).hasSize(1);
		TransactionCode updated = codeStore.values().iterator().next();
		assertThat(updated.getId()).isEqualTo(originalId);
		assertThat(updated.getFormRequester()).isEqualTo("FRM2");
		assertThat(updated.getFormApproval()).isEqualTo("FAP2");
		assertThat(updated.getUpdatedBy()).isEqualTo("NIK002");
		assertThat(updated.getUpdatedAt()).isNotNull();
		// created_at/created_by must not change on update.
		assertThat(updated.getCreatedAt()).isEqualTo(originalCreatedAt);
		assertThat(updated.getCreatedBy()).isEqualTo("NIK001");
	}

	// ----------------------------------------------------------------------------------------------
	// Service logic tests — E25 POST create (maker-checker + mapping validation)
	// ----------------------------------------------------------------------------------------------

	@Test
	void create_withExistingTransactionCode_submitsToMakerChecker() {
		// Seed a TransactionCode so the mapping reference is valid.
		service.upsertTransactionCode("BR01", "AB01", "FRM1", "FAP1", "NIK001");

		when(typeRepo.findByTransactionTypeCode("AB0101")).thenReturn(Optional.empty());

		MasterChangeRequest stubRequest = new MasterChangeRequest();
		stubRequest.setId(1L);
		when(makerCheckerService.submit(eq("TRANSACTION_TYPE"), any(), anyString(), eq("NIK001")))
				.thenReturn(stubRequest);

		MasterChangeRequest result = service.createTransactionType(
				"AB0101", "Pembiayaan Mobil Baru", "AB01", "NIK001");

		assertThat(result).isNotNull();
		verify(makerCheckerService).submit(eq("TRANSACTION_TYPE"), any(), anyString(), eq("NIK001"));
	}

	@Test
	void create_withNonExistentTransactionCode_throws422() {
		// No TransactionCode seeded — mapping "XX99" does not exist.
		assertThatThrownBy(() -> service.createTransactionType(
				"AB0101", "desc", "XX99", "NIK001"))
				.isInstanceOf(TransactionCodeNotFoundException.class)
				.hasMessageContaining("TRANSACTION_CODE_NOT_FOUND");

		// Maker-checker must NOT be called when validation fails.
		verify(makerCheckerService, never()).submit(anyString(), any(), anyString(), anyString());
	}

	@Test
	void create_mappingStoredEksplisitNotDerivedFromSubstring() {
		// Seed a TransactionCode with code "AB01".
		service.upsertTransactionCode("BR01", "AB01", "FRM1", "FAP1", "NIK001");

		when(typeRepo.findByTransactionTypeCode("AB0101")).thenReturn(Optional.empty());

		// Create a transaction-type whose mapping is "AB01" (explicit), while the type code is
		// "AB0101". If the system derived mapping from substring(0,2), it would produce "AB"
		// — which does NOT match any TransactionCode. By passing "AB01" explicitly, we prove
		// the mapping is stored as-is.
		service.createTransactionType("AB0101", "desc", "AB01", "NIK001");

		// Verify the payload sent to maker-checker contains the explicit mapping "AB01".
		org.mockito.ArgumentCaptor<String> payloadCaptor =
				org.mockito.ArgumentCaptor.forClass(String.class);
		verify(makerCheckerService).submit(
				eq("TRANSACTION_TYPE"), any(), payloadCaptor.capture(), eq("NIK001"));
		String payload = payloadCaptor.getValue();
		assertThat(payload).contains("\"mapping\":\"AB01\"");
		// It must NOT contain a derived substring like "AB".
		assertThat(payload).doesNotContain("\"mapping\":\"AB\"");
	}

	@Test
	void create_duplicateTypeCode_throws422() {
		// Seed an existing TransactionType.
		TransactionType existing = new TransactionType();
		existing.setTransactionTypeCode("AB0101");
		when(typeRepo.findByTransactionTypeCode("AB0101")).thenReturn(Optional.of(existing));

		// Also seed the TransactionCode so the mapping validation passes (otherwise we'd get
		// TransactionCodeNotFoundException first).
		service.upsertTransactionCode("BR01", "AB01", "FRM1", "FAP1", "NIK001");

		assertThatThrownBy(() -> service.createTransactionType(
				"AB0101", "desc", "AB01", "NIK001"))
				.isInstanceOf(TransactionTypeAlreadyExistsException.class);
	}

	// ----------------------------------------------------------------------------------------------
	// Service logic tests — E25 PATCH is_active ONLY (AC-11 / BR-BE07-18)
	// ----------------------------------------------------------------------------------------------

	@Test
	void patch_isActiveOnly_submitsToMakerChecker() {
		TransactionType existing = transactionType("AB0101", "desc", "AB01", true);
		when(typeRepo.findByTransactionTypeCode("AB0101")).thenReturn(Optional.of(existing));

		MasterChangeRequest stubRequest = new MasterChangeRequest();
		stubRequest.setId(1L);
		when(makerCheckerService.submit(eq("TRANSACTION_TYPE"), any(), anyString(), eq("NIK001")))
				.thenReturn(stubRequest);

		TransactionTypePatchRequest patch = new TransactionTypePatchRequest(
				null, null, null, false, "NIK001");
		MasterChangeRequest result = service.patchTransactionType("AB0101", patch, "NIK001");

		assertThat(result).isNotNull();
		verify(makerCheckerService).submit(
				eq("TRANSACTION_TYPE"), any(), anyString(), eq("NIK001"));
	}

	@Test
	void patch_description_throws422() {
		// AC-11: PATCH description → 422 (only is_active accepted)
		TransactionType existing = transactionType("AB0101", "desc", "AB01", true);
		when(typeRepo.findByTransactionTypeCode("AB0101")).thenReturn(Optional.of(existing));

		TransactionTypePatchRequest patch = new TransactionTypePatchRequest(
				null, "new description", null, null, "NIK001");

		assertThatThrownBy(() -> service.patchTransactionType("AB0101", patch, "NIK001"))
				.isInstanceOf(ImmutableFieldViolationException.class)
				.hasMessageContaining("IMMUTABLE_FIELD");

		verify(makerCheckerService, never()).submit(anyString(), any(), anyString(), anyString());
	}

	@Test
	void patch_mapping_throws422() {
		// AC-11: PATCH mapping → 422 (only is_active accepted; mapping immutable pasca-create)
		TransactionType existing = transactionType("AB0101", "desc", "AB01", true);
		when(typeRepo.findByTransactionTypeCode("AB0101")).thenReturn(Optional.of(existing));

		TransactionTypePatchRequest patch = new TransactionTypePatchRequest(
				null, null, "NEW_MAP", null, "NIK001");

		assertThatThrownBy(() -> service.patchTransactionType("AB0101", patch, "NIK001"))
				.isInstanceOf(ImmutableFieldViolationException.class)
				.hasMessageContaining("IMMUTABLE_FIELD");

		verify(makerCheckerService, never()).submit(anyString(), any(), anyString(), anyString());
	}

	@Test
	void patch_transactionTypeCode_throws422() {
		// PATCH transactionTypeCode (the [LOCKED] routing key) → 422
		TransactionType existing = transactionType("AB0101", "desc", "AB01", true);
		when(typeRepo.findByTransactionTypeCode("AB0101")).thenReturn(Optional.of(existing));

		TransactionTypePatchRequest patch = new TransactionTypePatchRequest(
				"CD0202", null, null, null, "NIK001");

		// transactionTypeCode in the body is not checked against the path here — the service
		// uses the path variable. But since isActive is null and description/mapping are null,
		// the service will throw ImmutableFieldViolationException because isActive is null
		// (no patchable field present).
		assertThatThrownBy(() -> service.patchTransactionType("AB0101", patch, "NIK001"))
				.isInstanceOf(ImmutableFieldViolationException.class);
	}

	@Test
	void patch_nonExistentTypeCode_throws404() {
		when(typeRepo.findByTransactionTypeCode("UNKNOWN")).thenReturn(Optional.empty());

		TransactionTypePatchRequest patch = new TransactionTypePatchRequest(
				null, null, null, false, "NIK001");

		assertThatThrownBy(() -> service.patchTransactionType("UNKNOWN", patch, "NIK001"))
				.isInstanceOf(TransactionTypeNotFoundException.class);
	}

	@Test
	void patch_isActiveNull_throws422() {
		// PATCH with no is_active (only null fields) → 422 (no patchable field present)
		TransactionType existing = transactionType("AB0101", "desc", "AB01", true);
		when(typeRepo.findByTransactionTypeCode("AB0101")).thenReturn(Optional.of(existing));

		TransactionTypePatchRequest patch = new TransactionTypePatchRequest(
				null, null, null, null, "NIK001");

		assertThatThrownBy(() -> service.patchTransactionType("AB0101", patch, "NIK001"))
				.isInstanceOf(ImmutableFieldViolationException.class);
	}

	@Test
	void patch_isActiveFalse_payloadContainsFalse() {
		TransactionType existing = transactionType("AB0101", "desc", "AB01", true);
		when(typeRepo.findByTransactionTypeCode("AB0101")).thenReturn(Optional.of(existing));

		MasterChangeRequest stubRequest = new MasterChangeRequest();
		stubRequest.setId(1L);
		when(makerCheckerService.submit(eq("TRANSACTION_TYPE"), any(), anyString(), eq("NIK001")))
				.thenReturn(stubRequest);

		TransactionTypePatchRequest patch = new TransactionTypePatchRequest(
				null, null, null, false, "NIK001");
		service.patchTransactionType("AB0101", patch, "NIK001");

		org.mockito.ArgumentCaptor<String> payloadCaptor =
				org.mockito.ArgumentCaptor.forClass(String.class);
		verify(makerCheckerService).submit(
				eq("TRANSACTION_TYPE"), any(), payloadCaptor.capture(), eq("NIK001"));
		String payload = payloadCaptor.getValue();
		assertThat(payload).contains("\"is_active\":false");
	}

	// ----------------------------------------------------------------------------------------------
	// Service logic tests — E22/E24 list
	// ----------------------------------------------------------------------------------------------

	@Test
	void listTransactionCodes_returnsPageForBranch() {
		TransactionCode tc1 = transactionCode(1L, "BR01", "AB01", "FRM1", "FAP1");
		TransactionCode tc2 = transactionCode(2L, "BR01", "CD02", "FRM2", "FAP2");
		Pageable pageable = PageRequest.of(0, 20);
		when(codeRepo.findByBranchId("BR01", pageable))
				.thenReturn(new PageImpl<>(List.of(tc1, tc2), pageable, 2));

		Page<TransactionCode> result = service.listTransactionCodes("BR01", pageable);

		assertThat(result.getContent()).hasSize(2);
		assertThat(result.getContent().get(0).getTransactionCode()).isEqualTo("AB01");
	}

	@Test
	void listTransactionTypes_returnsPageByMapping() {
		TransactionType tt1 = transactionType("AB0101", "desc1", "AB01", true);
		TransactionType tt2 = transactionType("AB0102", "desc2", "AB01", false);
		Pageable pageable = PageRequest.of(0, 20);
		when(typeRepo.findByMapping("AB01", pageable))
				.thenReturn(new PageImpl<>(List.of(tt1, tt2), pageable, 2));

		Page<TransactionType> result = service.listTransactionTypes("AB01", pageable);

		assertThat(result.getContent()).hasSize(2);
		assertThat(result.getContent().get(0).getTransactionTypeCode()).isEqualTo("AB0101");
	}

	// ----------------------------------------------------------------------------------------------
	// Helper methods
	// ----------------------------------------------------------------------------------------------

	private static TransactionType transactionType(String code, String desc, String mapping,
			boolean active) {
		TransactionType tt = new TransactionType();
		reflectiveSetId(tt, 1L);
		tt.setTransactionTypeCode(code);
		tt.setDescription(desc);
		tt.setMapping(mapping);
		tt.setActive(active);
		tt.setCreatedAt(Instant.now());
		tt.setCreatedBy("SYSTEM");
		return tt;
	}

	private static TransactionCode transactionCode(Long id, String branchId, String code,
			String formRequester, String formApproval) {
		TransactionCode tc = new TransactionCode();
		reflectiveSetId(tc, id);
		tc.setBranchId(branchId);
		tc.setTransactionCode(code);
		tc.setFormRequester(formRequester);
		tc.setFormApproval(formApproval);
		tc.setCreatedAt(Instant.now());
		tc.setCreatedBy("SYSTEM");
		return tc;
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
// SDD-PROVENANCE: U-007 | vault: .mega-sdd/vaults/acquisition-master-data | TDD test for TransactionType config E22-E25 — entity mapping (cfg_transaction_code/cfg_transaction_type extends VersionedEntity; [LOCKED] @Column transaction_type_code BR-PRODASSET-7); AC-11/BR-BE07-18 PATCH is_active ONLY (description/mapping→422 ImmutableFieldViolation); E23 upsert upper-case BR-BE07-19; mapping disimpan eksplisit NOT substring; mapping must reference existing TransactionCode→422; TRANSACTION_TYPE write→MakerCheckerService.submit BR-BE07-05
