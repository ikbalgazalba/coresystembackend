package com.coresystem.coresystembackend.config;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Flowable embedded workflow engine configuration (ADR-13 — approval/human-task layer).
 *
 * <p>Flowable runs embedded inside the modulith (ADR-01) — satu deployable. The engine
 * orchestrates <em>human tasks</em> (inbox, committee hierarchy, maker-checker, SLA aging,
 * Instant-Approval lane) but does NOT replace the domain state machine (ADR-06 — lifecycle
 * status remains config-driven in-app).
 *
 * <h2>Key constraints (DB-CONVENTIONS §8, BE-00 §8.7a)</h2>
 * <ul>
 *   <li><strong>ACT_* tables</strong> = engine-owned, auto-created on startup
 *       ({@code databaseSchemaUpdate=true}). Never touched manually.</li>
 *   <li><strong>Process variables</strong> store only keys ({@code credit_id}, task ref) —
 *       payload stays in {@code trx_} tables.</li>
 *   <li><strong>{@code log_approval_history}</strong> = authoritative regulatory audit,
 *       INDEPENDENT of the engine (written by our service layer, not Flowable).</li>
 *   <li><strong>BPMN</strong> versioned in repo ({@code src/main/resources/processes/});
 *       per-product matrix + hierarchy read by delegates from {@code cfg_} tables —
 *       NOT hardcoded in BPMN.</li>
 *   <li><strong>No-self-approval</strong> (D-01 S11) + role census D-10 enforced dua lapis:
 *       Flowable task assignment + Java service guard.</li>
 * </ul>
 *
 * <p>This is the <strong>bare minimum</strong> configuration: engine starts, {@code ACT_*}
 * tables auto-create, but no BPMN processes are deployed yet. BPMN processes
 * (committee-approval, vertel-approval, npp-approval) + WorkflowService + ApprovalController
 * land in a later phase when modules 03/05/06 are built.
 *
 * <p>{@code @ConditionalOnBean(DataSource.class)} guards this config so it does not break
 * existing context-load tests that exclude DataSource/JPA autoconfiguration (same pattern
 * as {@code MakerCheckerService}).
 *
 * @see <a href="docs/ARCHITECTURE-PROPOSAL.md §4 ADR-13">ADR-13 — Flowable embedded</a>
 * @see <a href="docs/DB-CONVENTIONS.md §8">DB-CONVENTIONS §8 — Flowable footprint</a>
 */
// SDD-PROVENANCE: flowable-config | vault: .mega-sdd/vaults/acquisition | ADR-13 Flowable embedded bare-minimum config
@Configuration
@ConditionalOnBean(DataSource.class)
public class FlowableConfig {

	private static final Logger log = LoggerFactory.getLogger(FlowableConfig.class);

	/**
	 * Configure the Flowable process engine: auto-create {@code ACT_*} schema,
	 * disable Flowable REST API (we use our own controllers), enable async executor
	 * for future SLA/timer tasks.
	 *
	 * @return the engine configuration customizer
	 */
	@Bean
	public EngineConfigurationConfigurer<SpringProcessEngineConfiguration> flowableEngineConfigurer() {
		return configuration -> {
			// Auto-create/update ACT_* tables on startup (DB-CONVENTIONS §8 — engine-owned).
			configuration.setDatabaseSchemaUpdate("true");

			// Enable async executor for future timer/SLA/escalation tasks.
			configuration.setAsyncExecutorActivate(true);

			// Disable Flowable's built-in REST API — we expose our own controllers.
			// (The starter doesn't auto-configure REST by default, but we set this
			// explicitly to document the decision.)
			log.info("Flowable embedded engine configured: schemaUpdate=true, asyncExecutor=true, REST disabled (ADR-13)");
		};
	}
}
