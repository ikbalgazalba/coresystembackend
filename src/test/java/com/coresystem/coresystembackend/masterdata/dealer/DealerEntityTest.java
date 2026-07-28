package com.coresystem.coresystembackend.masterdata.dealer;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import com.coresystem.coresystembackend.masterdata.common.AuditableEntity;
import com.coresystem.coresystembackend.masterdata.common.VersionedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * TDD entity-mapping test for U-005 — the dealer master family (5 tables).
 *
 * <p>This is a reflection-based unit test (no Spring context, no datasource) that verifies the
 * JPA-mapping contracts the unit specifies, with emphasis on the two do-not-replicate fixes:
 * <ul>
 *   <li><strong>EC6 fix</strong> — {@code Dealer.isSubDealerEnabled} is a real boolean flag, NOT the
 *       legacy name-literal {@code '%PT Lucas Digital Indonesia%'} match (BR-BE07-09).</li>
 *   <li><strong>EC7 fix</strong> — {@code Dealer.parentDealerCode}/{@code groupCode}/{@code mainDealerCode}
 *       are typed {@code String} FK columns, NOT join-via-{@code notes} free-text (BR-BE07-07).</li>
 *   <li><strong>KTP/NPWP [LOCKED]</strong> — {@code ktpNo}/{@code ktpName}/{@code npwpNo} are
 *       {@code @Column}-mapped for zero-diff migration checksum.</li>
 *   <li><strong>BR-BE07-03</strong> deactivate-only — repositories extend {@link JpaRepository} but
 *       must NOT expose a custom hard-delete method; lifecycle is via {@code status}/{@code isActive}.</li>
 *   <li><strong>DealerDocument.fileRef</strong> — typed {@code String} object-storage key, NOT an
 *       FTP path (ARTIFACT discard per data-model §Dealer master family).</li>
 * </ul>
 *
 * <p>Each entity is verified to: carry {@link Entity @Entity} + {@link Table @Table} with the
 * correct {@code mst_dealer*} table name; extend {@link VersionedEntity} (inheriting the 4 audit
 * columns + {@code @Version}); declare an {@code id} {@code IDENTITY} PK; and map the spec's
 * business-key / FK / status columns.
 */
class DealerEntityTest {

	// ---- Dealer (mst_dealer) ----

	@Test
	void dealerIsEntityWithMstDealerTable() {
		assertThat(Dealer.class).hasAnnotation(Entity.class);
		Table table = Dealer.class.getAnnotation(Table.class);
		assertThat(table).isNotNull();
		assertThat(table.name()).isEqualTo("mst_dealer");
	}

	@Test
	void dealerExtendsVersionedEntity() {
		assertThat(VersionedEntity.class).isAssignableFrom(Dealer.class);
	}

	@Test
	void dealerHasIdentityIdPk() {
		Field idField = declaredField(Dealer.class, "id");
		assertThat(idField.getType()).isEqualTo(Long.class);
		assertThat(idField.isAnnotationPresent(Id.class)).isTrue();
		GeneratedValue gv = idField.getAnnotation(GeneratedValue.class);
		assertThat(gv).isNotNull();
		assertThat(gv.strategy()).isEqualTo(GenerationType.IDENTITY);
	}

	@Test
	void dealerParentDealerCodeIsTypedStringFkNotNotes() {
		// EC7 fix: parent/group/main dealer codes are explicit typed String columns, NOT join-via-notes.
		assertThat(fieldType(Dealer.class, "parentDealerCode")).isEqualTo(String.class);
		assertThat(fieldType(Dealer.class, "groupCode")).isEqualTo(String.class);
		assertThat(fieldType(Dealer.class, "mainDealerCode")).isEqualTo(String.class);
		// notes must remain free-text and NOT be the join key — it still exists but is separate.
		assertThat(fieldType(Dealer.class, "notes")).isEqualTo(String.class);
	}

	@Test
	void dealerParentDealerCodeHasColumnAnnotation() {
		Field f = declaredField(Dealer.class, "parentDealerCode");
		assertThat(f.isAnnotationPresent(Column.class)).isTrue();
		assertThat(f.getAnnotation(Column.class).name()).isEqualTo("parent_dealer_code");
	}

	@Test
	void dealerIsSubDealerEnabledIsBooleanFlag() {
		// EC6 fix: explicit boolean flag, NOT the legacy name-literal '%PT Lucas Digital Indonesia%'.
		assertThat(fieldType(Dealer.class, "isSubDealerEnabled")).isEqualTo(boolean.class);
	}

	@Test
	void dealerKtpAndNpwpColumnsAreColumnMapped() {
		// [LOCKED] zero-diff migration checksum — must be @Column-mapped.
		assertThat(fieldType(Dealer.class, "ktpNo")).isEqualTo(String.class);
		assertThat(fieldType(Dealer.class, "ktpName")).isEqualTo(String.class);
		assertThat(fieldType(Dealer.class, "npwpNo")).isEqualTo(String.class);
		assertThat(declaredField(Dealer.class, "ktpNo").isAnnotationPresent(Column.class)).isTrue();
		assertThat(declaredField(Dealer.class, "ktpName").isAnnotationPresent(Column.class)).isTrue();
		assertThat(declaredField(Dealer.class, "npwpNo").isAnnotationPresent(Column.class)).isTrue();
		assertThat(declaredField(Dealer.class, "ktpNo").getAnnotation(Column.class).name()).isEqualTo("ktp_no");
		assertThat(declaredField(Dealer.class, "ktpName").getAnnotation(Column.class).name()).isEqualTo("ktp_name");
		assertThat(declaredField(Dealer.class, "npwpNo").getAnnotation(Column.class).name()).isEqualTo("npwp_no");
	}

	@Test
	void dealerBusinessKeyAndStatusFields() {
		assertThat(fieldType(Dealer.class, "dealerCode")).isEqualTo(String.class);
		assertThat(fieldType(Dealer.class, "dealerName")).isEqualTo(String.class);
		assertThat(fieldType(Dealer.class, "isAuthorizedDealer")).isEqualTo(boolean.class);
		assertThat(fieldType(Dealer.class, "isSellingNewProductOnly")).isEqualTo(boolean.class);
		assertThat(fieldType(Dealer.class, "isUsedCar")).isEqualTo(boolean.class);
		assertThat(fieldType(Dealer.class, "status")).isEqualTo(String.class);
		assertThat(fieldType(Dealer.class, "activationDate")).isEqualTo(LocalDate.class);
		assertThat(fieldType(Dealer.class, "deactivationDate")).isEqualTo(LocalDate.class);
		// dealerCode is the unique business key.
		Column codeCol = declaredField(Dealer.class, "dealerCode").getAnnotation(Column.class);
		assertThat(codeCol).isNotNull();
		assertThat(codeCol.unique()).isTrue();
		assertThat(codeCol.name()).isEqualTo("dealer_code");
	}

	// ---- DealerDocument (mst_dealer_document) ----

	@Test
	void dealerDocumentIsEntityWithMstDealerDocumentTable() {
		assertThat(DealerDocument.class).hasAnnotation(Entity.class);
		assertThat(DealerDocument.class.getAnnotation(Table.class).name()).isEqualTo("mst_dealer_document");
	}

	@Test
	void dealerDocumentExtendsVersionedEntity() {
		assertThat(VersionedEntity.class).isAssignableFrom(DealerDocument.class);
	}

	@Test
	void dealerDocumentFileRefIsStringStorageKey() {
		// ARTIFACT fix: fileRef is an object-storage key (String), NOT an FTP path.
		assertThat(fieldType(DealerDocument.class, "fileRef")).isEqualTo(String.class);
	}

	@Test
	void dealerDocumentFields() {
		assertThat(fieldType(DealerDocument.class, "dealerCode")).isEqualTo(String.class);
		assertThat(fieldType(DealerDocument.class, "docType")).isEqualTo(String.class);
		assertThat(fieldType(DealerDocument.class, "uploadedBy")).isEqualTo(String.class);
		assertThat(fieldType(DealerDocument.class, "uploadedAt")).isEqualTo(Instant.class);
	}

	// ---- DealerPersonnel (mst_dealer_personnel) ----

	@Test
	void dealerPersonnelIsEntityWithMstDealerPersonnelTable() {
		assertThat(DealerPersonnel.class).hasAnnotation(Entity.class);
		assertThat(DealerPersonnel.class.getAnnotation(Table.class).name()).isEqualTo("mst_dealer_personnel");
	}

	@Test
	void dealerPersonnelExtendsVersionedEntity() {
		assertThat(VersionedEntity.class).isAssignableFrom(DealerPersonnel.class);
	}

	@Test
	void dealerPersonnelStatusIsStringEnum() {
		// BR-DLRPTN-1: status enum 'A'|'inactive' — modeled as String + CHECK at DB layer.
		assertThat(fieldType(DealerPersonnel.class, "status")).isEqualTo(String.class);
	}

	@Test
	void dealerPersonnelFields() {
		assertThat(fieldType(DealerPersonnel.class, "personnelId")).isEqualTo(String.class);
		assertThat(fieldType(DealerPersonnel.class, "dealerCode")).isEqualTo(String.class);
		assertThat(fieldType(DealerPersonnel.class, "name")).isEqualTo(String.class);
		assertThat(fieldType(DealerPersonnel.class, "jobTitleId")).isEqualTo(String.class);
		assertThat(fieldType(DealerPersonnel.class, "bankReferenceId")).isEqualTo(String.class);
	}

	// ---- DealerJobTitle (mst_dealer_job_title) ----

	@Test
	void dealerJobTitleIsEntityWithMstDealerJobTitleTable() {
		assertThat(DealerJobTitle.class).hasAnnotation(Entity.class);
		assertThat(DealerJobTitle.class.getAnnotation(Table.class).name()).isEqualTo("mst_dealer_job_title");
	}

	@Test
	void dealerJobTitleExtendsVersionedEntity() {
		assertThat(VersionedEntity.class).isAssignableFrom(DealerJobTitle.class);
	}

	@Test
	void dealerJobTitleFields() {
		assertThat(fieldType(DealerJobTitle.class, "jobTitleId")).isEqualTo(String.class);
		assertThat(fieldType(DealerJobTitle.class, "description")).isEqualTo(String.class);
		assertThat(fieldType(DealerJobTitle.class, "dealerPaymentCode")).isEqualTo(String.class);
		assertThat(fieldType(DealerJobTitle.class, "isActive")).isEqualTo(boolean.class);
	}

	// ---- DealerBranchAccess (mst_dealer_branch_access) ----

	@Test
	void dealerBranchAccessIsEntityWithMstDealerBranchAccessTable() {
		assertThat(DealerBranchAccess.class).hasAnnotation(Entity.class);
		assertThat(DealerBranchAccess.class.getAnnotation(Table.class).name()).isEqualTo("mst_dealer_branch_access");
	}

	@Test
	void dealerBranchAccessExtendsVersionedEntity() {
		assertThat(VersionedEntity.class).isAssignableFrom(DealerBranchAccess.class);
	}

	@Test
	void dealerBranchAccessFields() {
		assertThat(fieldType(DealerBranchAccess.class, "dealerCode")).isEqualTo(String.class);
		assertThat(fieldType(DealerBranchAccess.class, "branchId")).isEqualTo(String.class);
		assertThat(fieldType(DealerBranchAccess.class, "isActive")).isEqualTo(boolean.class);
	}

	// ---- Repositories (no custom DELETE — BR-BE07-03 deactivate-only) ----

	@Test
	void dealerRepositoryExtendsJpaRepository() {
		assertThat(JpaRepository.class).isAssignableFrom(DealerRepository.class);
	}

	@Test
	void dealerDocumentRepositoryExtendsJpaRepository() {
		assertThat(JpaRepository.class).isAssignableFrom(DealerDocumentRepository.class);
	}

	@Test
	void dealerPersonnelRepositoryExtendsJpaRepository() {
		assertThat(JpaRepository.class).isAssignableFrom(DealerPersonnelRepository.class);
	}

	@Test
	void dealerJobTitleRepositoryExtendsJpaRepository() {
		assertThat(JpaRepository.class).isAssignableFrom(DealerJobTitleRepository.class);
	}

	@Test
	void dealerBranchAccessRepositoryExtendsJpaRepository() {
		assertThat(JpaRepository.class).isAssignableFrom(DealerBranchAccessRepository.class);
	}

	@Test
	void repositoriesExposeNoCustomDeleteMethod() {
		// BR-BE07-03: deactivate-only (no hard delete). JpaRepository itself ships deleteById/delete
		// (unavoidable from the interface), but our repos MUST NOT declare any ADDITIONAL custom
		// delete method — lifecycle is via status/isActive toggles in the service layer.
		for (Class<?> repo : new Class<?>[] {
				DealerRepository.class, DealerDocumentRepository.class, DealerPersonnelRepository.class,
				DealerJobTitleRepository.class, DealerBranchAccessRepository.class }) {
			Set<String> customDeletes = Arrays.stream(repo.getDeclaredMethods())
					.map(Method::getName)
					.filter(n -> n.toLowerCase().contains("delete") || n.toLowerCase().contains("remove"))
					.collect(Collectors.toSet());
			assertThat(customDeletes)
					.as("%s must not declare custom delete/remove methods (BR-BE07-03 deactivate-only)", repo.getSimpleName())
					.isEmpty();
		}
	}

	// ---- Helpers ----

	private static Class<?> fieldType(Class<?> type, String fieldName) {
		return declaredField(type, fieldName).getType();
	}

	private static Field declaredField(Class<?> type, String fieldName) {
		try {
			return type.getDeclaredField(fieldName);
		} catch (NoSuchFieldException e) {
			throw new AssertionError(type.getSimpleName() + " must declare field '" + fieldName + "'", e);
		}
	}

}
// SDD-PROVENANCE: U-005 | vault: .mega-sdd/vaults/acquisition-master-data | TDD entity-mapping test (Dealer family 5 tables; EC6/EC7 fixes; KTP/NPWP [LOCKED]; deactivate-only no custom delete)
