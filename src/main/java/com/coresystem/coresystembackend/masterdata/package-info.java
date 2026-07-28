/**
 * Master-data domain module (acquisition sub-vault).
 *
 * <p>Shared base classes for all master-data entities ({@link com.coresystem.coresystembackend.masterdata.common.AuditableEntity},
 * {@link com.coresystem.coresystembackend.masterdata.common.VersionedEntity}), the standardized
 * pagination response ({@link com.coresystem/coresystembackend.masterdata.common.PageResponse}),
 * and the JPA auditing configuration ({@link com.coresystem.coresystembackend.masterdata.config.MasterDataConfig}).
 *
 * <p>Conventions:
 * <ul>
 *   <li>DB-CONVENTIONS §4 — every {@code mst_/cfg_/map_} table carries the four audit columns
 *       {@code created_at/created_by/updated_at/updated_by}; concurrent-edit tables additionally
 *       carry a {@code version INTEGER} optimistic-lock column.</li>
 *   <li>BR-BE07-20 — list endpoints return the standardized pagination shape
 *       {@code {items[], page, total_pages, record_count}} via {@link PageResponse}.</li>
 * </ul>
 *
 * <p>Package kept as a plain package-info (no Spring Modulith {@code @ApplicationModule} yet) per
 * the U-001 "keep simple" directive; module formalization lands when a later unit needs it.
 */
package com.coresystem.coresystembackend.masterdata;

// SDD-PROVENANCE: U-001 | vault: .mega-sdd/vaults/acquisition-master-data | masterdata module package-info (acquisition sub-vault; shared base classes + config)
