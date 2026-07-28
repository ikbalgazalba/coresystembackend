package com.coresystem.coresystembackend.masterdata.common;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/**
 * Shared audit base for every master-data entity (DB-CONVENTIONS §4).
 *
 * <p>Carries the four audit columns that every {@code mst_/cfg_/map_} table must have:
 * <ul>
 *   <li>{@code created_at} ({@link Instant}) — row creation timestamp</li>
 *   <li>{@code created_by} ({@link String}) — actor NIK that created the row</li>
 *   <li>{@code updated_at} ({@link Instant}) — last-update timestamp</li>
 *   <li>{@code updated_by} ({@link String}) — actor NIK that last updated the row</li>
 * </ul>
 *
 * <p>This is a {@link MappedSuperclass @MappedSuperclass} — it contributes columns to each
 * concrete entity's table but has no table of its own. Concrete entities extend it and add their
 * own business columns; they do NOT redeclare these fields.
 *
 * <h2>Why no {@code @PrePersist}/@PreUpdate</h2>
 * The audit values are set explicitly by the service layer from the {@code AuthenticatedActor}
 * (the resolved employee NIK), NOT by a JPA lifecycle callback. This keeps the audit source
 * single-truth (the actor resolved per-request, including the {@code SYSTEM} placeholder from
 * {@link com.coresystem.coresystembackend.masterdata.config.MasterDataConfig#auditorAware()}) and
 * avoids the silent-fallback-on-empty-context trap that callback-based auditing falls into when
 * the security context is absent (e.g. during bootstrap/migration). A later unit may wire
 * {@code @CreatedDate}/@LastModifiedDate + {@code AuditorAware} if a callback path is desired;
 * until then the service layer owns the writes.
 *
 * <p>Field access is protected (not private) so concrete entities can read the audit state in
 * their domain logic without forcing a public getter contract.
 */
@MappedSuperclass
public abstract class AuditableEntity {

	@Column(name = "created_at", nullable = false, updatable = false)
	protected Instant createdAt;

	@Column(name = "created_by", nullable = false, updatable = false, length = 32)
	protected String createdBy;

	@Column(name = "updated_at")
	protected Instant updatedAt;

	@Column(name = "updated_by", length = 32)
	protected String updatedBy;

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(String createdBy) {
		this.createdBy = createdBy;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	public String getUpdatedBy() {
		return updatedBy;
	}

	public void setUpdatedBy(String updatedBy) {
		this.updatedBy = updatedBy;
	}

}
// SDD-PROVENANCE: U-001 | vault: .mega-sdd/vaults/acquisition-master-data | AuditableEntity @MappedSuperclass — 4 audit columns (created_at/created_by/updated_at/updated_by) per DB-CONVENTIONS §4; service-layer-set (no @PrePersist)
