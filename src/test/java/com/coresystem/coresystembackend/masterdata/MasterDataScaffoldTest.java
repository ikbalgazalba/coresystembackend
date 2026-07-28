package com.coresystem.coresystembackend.masterdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import com.coresystem.coresystembackend.masterdata.common.AuditableEntity;
import com.coresystem.coresystembackend.masterdata.common.PageResponse;
import com.coresystem.coresystembackend.masterdata.common.VersionedEntity;
import com.coresystem.coresystembackend.masterdata.config.MasterDataConfig;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

/**
 * TDD scaffold test for U-001 — verifies the master-data module foundation without loading a
 * full Spring context (the JPA auditing infrastructure is wired by {@code @EnableJpaAuditing} on
 * {@link MasterDataConfig} and is exercised end-to-end by a later integration test once a
 * datasource is available; here we test the units directly):
 * <ul>
 *   <li>{@link PageResponse#of(Page, int, int)} builds the BR-BE07-20 shape
 *       {@code {items[], page, total_pages, record_count}} from a Spring Data {@link Page}.</li>
 *   <li>{@link AuditableEntity} is a {@link MappedSuperclass @MappedSuperclass} so the 4 audit
 *       columns are inherited by every master-data entity (DB-CONVENTIONS §4).</li>
 *   <li>{@link VersionedEntity} adds a {@link Version @Version} field for optimistic locking
 *       (DB-CONVENTIONS §4).</li>
 *   <li>{@link MasterDataConfig#auditorAware()} returns an {@link AuditorAware} that resolves to
 *       the {@code SYSTEM} placeholder until real auth lands (OQ-ARCH-STACK).</li>
 * </ul>
 */
class MasterDataScaffoldTest {

	@Test
	void pageResponseOfBuildsFromSpringDataPage() {
		List<String> items = List.of("a", "b", "c");
		Pageable pageable = PageRequest.of(1, 3);
		Page<String> page = PageableExecutionUtils.getPage(items, pageable, () -> 10L);

		PageResponse<String> response = PageResponse.of(page, 1, 3);

		assertThat(response.items()).containsExactly("a", "b", "c");
		assertThat(response.page()).isEqualTo(1);
		assertThat(response.totalPages()).isEqualTo(4);
		assertThat(response.recordCount()).isEqualTo(10L);
	}

	@Test
	void pageResponseOfHandlesEmptyPage() {
		Page<String> page = PageableExecutionUtils.getPage(List.of(), PageRequest.of(0, 5), () -> 0L);

		PageResponse<String> response = PageResponse.of(page, 0, 5);

		assertThat(response.items()).isEmpty();
		assertThat(response.page()).isZero();
		assertThat(response.totalPages()).isZero();
		assertThat(response.recordCount()).isZero();
	}

	@Test
	void pageResponseOfEchoesRequestedPageEvenWhenPageableDiffers() {
		// The factory echoes requestedPage/requestedSize, not page.getPageable(), so the response
		// reflects what the client asked for even if the underlying Pageable was coerced.
		List<String> items = List.of("x");
		Page<String> page = PageableExecutionUtils.getPage(items, PageRequest.of(0, 10), () -> 1L);

		PageResponse<String> response = PageResponse.of(page, 2, 20);

		assertThat(response.page()).isEqualTo(2);
		assertThat(response.totalPages()).isEqualTo(1); // from page.getTotalPages()
		assertThat(response.recordCount()).isEqualTo(1L);
	}

	@Test
	void auditableEntityIsMappedSuperclass() {
		assertThat(AuditableEntity.class)
				.hasAnnotation(MappedSuperclass.class);
	}

	@Test
	void auditableEntityDeclaresFourAuditColumns() {
		// DB-CONVENTIONS §4: created_at / created_by / updated_at / updated_by
		assertThat(fieldType(AuditableEntity.class, "createdAt")).isEqualTo(Instant.class);
		assertThat(fieldType(AuditableEntity.class, "createdBy")).isEqualTo(String.class);
		assertThat(fieldType(AuditableEntity.class, "updatedAt")).isEqualTo(Instant.class);
		assertThat(fieldType(AuditableEntity.class, "updatedBy")).isEqualTo(String.class);
	}

	@Test
	void versionedEntityExtendsAuditableEntity() {
		assertThat(AuditableEntity.class).isAssignableFrom(VersionedEntity.class);
	}

	@Test
	void versionedEntityHasVersionField() {
		Field versionField;
		try {
			versionField = VersionedEntity.class.getDeclaredField("version");
		} catch (NoSuchFieldException e) {
			throw new AssertionError("VersionedEntity must declare a 'version' field", e);
		}
		assertThat(versionField).isNotNull();
		assertThat(versionField.getType()).isEqualTo(Integer.class);
		assertThat(versionField.isAnnotationPresent(Version.class))
				.as("version field must carry @jakarta.persistence.Version for optimistic locking")
				.isTrue();
	}

	@Test
	void auditorAwareBeanReturnsSystemPlaceholder() {
		// OQ-ARCH-STACK: until real auth lands, the auditor resolves to "SYSTEM".
		// The @Bean method is a plain factory; invoke it directly to verify the contract without
		// loading the full JPA auditing infrastructure (which needs a live datasource).
		AuditorAware<String> auditorAware = new MasterDataConfig().auditorAware();
		assertThat(auditorAware).isNotNull();
		Optional<String> current = auditorAware.getCurrentAuditor();
		assertThat(current).contains("SYSTEM");
	}

	@Test
	void auditorAwareBeanIsStableAcrossCalls() {
		AuditorAware<String> auditorAware = new MasterDataConfig().auditorAware();
		Optional<String> first = auditorAware.getCurrentAuditor();
		Optional<String> second = auditorAware.getCurrentAuditor();
		assertThat(first).isEqualTo(second);
		assertThat(first).contains("SYSTEM");
	}

	private static Class<?> fieldType(Class<?> type, String fieldName) {
		try {
			return type.getDeclaredField(fieldName).getType();
		} catch (NoSuchFieldException e) {
			throw new AssertionError(type.getSimpleName() + " must declare field '" + fieldName + "'", e);
		}
	}

}
// SDD-PROVENANCE: U-001 | vault: .mega-sdd/vaults/acquisition-master-data | TDD scaffold test (PageResponse.of + AuditableEntity @MappedSuperclass + VersionedEntity @Version + AuditorAware SYSTEM placeholder)
