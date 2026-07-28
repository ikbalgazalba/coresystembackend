package com.coresystem.coresystembackend.masterdata.common;

import jakarta.persistence.Column;
import jakarta.persistence.Version;

/**
 * Shared optimistic-locking base for master-data entities on concurrent-edit tables
 * (DB-CONVENTIONS §4).
 *
 * <p>Extends {@link AuditableEntity} (so it inherits the four audit columns) and adds a
 * {@code version INTEGER} column annotated with {@link Version @Version}. JPA's optimistic
 * locking uses this column: every UPDATE increments {@code version}, and if a row's version at
 * flush time differs from the version read at load time, Hibernate throws
 * {@code OptimisticLockException} — preventing a stale write from clobbering a concurrent
 * update.
 *
 * <p>Concrete entities on tables that participate in concurrent edits (per the
 * {@code version INTEGER} rule in DB-CONVENTIONS §4) extend this class; entities on tables that
 * do not need optimistic locking extend {@link AuditableEntity} directly.
 *
 * <p>The {@code version} field is managed by JPA — application code should NOT set it manually
 * (doing so would corrupt the optimistic-lock contract). There is intentionally no setter.
 */
public abstract class VersionedEntity extends AuditableEntity {

	@Version
	@Column(name = "version")
	protected Integer version;

	public Integer getVersion() {
		return version;
	}

}
// SDD-PROVENANCE: U-001 | vault: .mega-sdd/vaults/acquisition-master-data | VersionedEntity extends AuditableEntity + @Version Integer version (optimistic locking, DB-CONVENTIONS §4); no setter (JPA-managed)
