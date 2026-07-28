package com.coresystem.coresystembackend.masterdata.config;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * JPA auditing configuration for the master-data module.
 *
 * <p>Provides the {@link AuditorAware<String>} bean that resolves the current actor's employee
 * NIK for the {@code created_by}/{@code updated_by} audit columns (DB-CONVENTIONS §4), and
 * enables JPA auditing application-wide via {@link EnableJpaAuditing @EnableJpaAuditing} when the
 * JPA metamodel is live.
 *
 * <h2>Structure — two concerns, one file</h2>
 * This config is split into two layers to avoid breaking contexts that intentionally exclude the
 * JPA autoconfiguration (e.g. the existing {@code contextLoads} smoke test and the
 * {@code AuthLoginIntegrationTest} exclude {@code HibernateJpaAutoConfiguration} so they can load
 * without a reachable datasource):
 * <ul>
 *   <li>{@link MasterDataConfig} itself is <strong>unconditional</strong> and exposes only the
 *       {@link #auditorAware()} bean. The {@link AuditorAware} contract has no dependency on JPA,
 *       so it is safe to register in every context.</li>
 *   <li>{@link JpaAuditingConfig} is a nested {@code @Configuration} that carries
 *       {@link EnableJpaAuditing @EnableJpaAuditing(auditorAwareRef = "auditorAware")} and is
 *       guarded by {@link ConditionalOnBean @ConditionalOnBean(JpaMetamodelMappingContext.class)}.
 *       It only activates when the JPA metamodel exists (i.e. Hibernate JPA is auto-configured or
 *       otherwise bootstrapped), so tests that exclude the JPA autoconfiguration do not trigger
 *       the {@code jpaAuditingHandler} → {@code jpaMappingContext} wiring that would otherwise
 *       fail with "JPA metamodel must not be empty".</li>
 * </ul>
 *
 * <h2>Why {@code @EnableJpaAuditing} lives here</h2>
 * No existing config class enables JPA auditing (verified at U-001 time: neither
 * {@code SecurityConfig} nor any other {@code @Configuration} carries the annotation).
 * Centralizing the enable + the {@link AuditorAware} bean in the master-data module keeps the
 * audit foundation self-contained for the acquisition epic; if a later unit needs auditing outside
 * master-data, this class can be promoted to the top-level {@code config} package without breaking
 * callers (the bean name {@code auditorAware} is stable).
 *
 * <h2>Auditor resolution — placeholder until real auth lands</h2>
 * The real actor NIK will come from the authenticated principal once the JWT-authenticated
 * security context is populated for master-data endpoints (OQ-ARCH-STACK — the auth stack
 * wiring is not yet finalized). Until then, this bean returns the {@code "SYSTEM"} placeholder so
 * audit columns are always populated (never NULL) and bootstrap/migration writes have a sane
 * actor. When real auth lands, replace the body of {@link #auditorAware()} with:
 * <pre>
 * return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
 * 		.filter(Authentication::isAuthenticated)
 * 		.map(Authentication::getName) // employee NIK from the JWT subject
 * 		.filter(name -> !name.isBlank())
 * 		.or(() -> Optional.of("SYSTEM"));
 * </pre>
 *
 * <p>The placeholder is intentionally NOT driven by {@code @PrePersist}/@PreUpdate callbacks on
 * {@link com.coresystem.coresystembackend.masterdata.common.AuditableEntity} — the service layer
 * sets the audit fields explicitly from the actor, and this {@link AuditorAware} is the fallback
 * for any {@code @CreatedBy}/@LastModifiedBy annotation that a later unit may add.
 */
@Configuration
public class MasterDataConfig {

	/**
	 * Resolves the current actor's employee NIK for JPA audit columns.
	 *
	 * <p>Placeholder implementation: returns {@code "SYSTEM"} until the JWT-authenticated
	 * security context is wired for master-data endpoints (OQ-ARCH-STACK). The real
	 * implementation will read {@link SecurityContextHolder#getContext()} ->
	 * {@link SecurityContext#getAuthentication()} -> {@link Authentication#getName()} (the NIK
	 * carried as the JWT subject) and fall back to {@code "SYSTEM"} when no auth is present
	 * (bootstrap/migration), so audit columns are never NULL.
	 *
	 * <p>This bean is registered unconditionally (no JPA dependency) so it is available in every
	 * context, including ones that exclude the JPA autoconfiguration.
	 *
	 * @return an {@link AuditorAware} that resolves to the actor NIK or {@code "SYSTEM"}
	 */
	@Bean
	public AuditorAware<String> auditorAware() {
		return () -> Optional.of("SYSTEM");
	}

	/**
	 * Nested config that enables JPA auditing only when the JPA metamodel is live.
	 *
	 * <p>Guarded by {@link ConditionalOnBean @ConditionalOnBean(JpaMetamodelMappingContext.class)}
	 * so that {@link EnableJpaAuditing @EnableJpaAuditing} — which wires the
	 * {@code jpaAuditingHandler} against the {@code jpaMappingContext} — does not fire in contexts
	 * that exclude {@code HibernateJpaAutoConfiguration} (where the metamodel would be empty and
	 * the handler creation would fail with "JPA metamodel must not be empty"). In a normal runtime
	 * context (datasource + Hibernate present), the metamodel bean exists and auditing is enabled.
	 */
	@Configuration
	@EnableJpaAuditing(auditorAwareRef = "auditorAware")
	@ConditionalOnBean(JpaMetamodelMappingContext.class)
	public static class JpaAuditingConfig {
		// No beans of its own — the @EnableJpaAuditing import registrar wires the auditing
		// infrastructure against the metamodel + the auditorAware bean declared above.
	}

}
// SDD-PROVENANCE: U-001 | vault: .mega-sdd/vaults/acquisition-master-data | MasterDataConfig — unconditional AuditorAware<String> SYSTEM placeholder (OQ-ARCH-STACK) + nested @EnableJpaAuditing guarded by @ConditionalOnBean(JpaMetamodelMappingContext) to avoid breaking JPA-excluded test contexts
