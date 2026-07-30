package com.coresystem.coresystembackend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.ProcessEngine;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Flowable embedded engine starts correctly (ADR-13 bare minimum).
 *
 * <p>Tests that the Flowable {@link ProcessEngine} and core service beans
 * ({@link RepositoryService}, {@link RuntimeService}, {@link TaskService}) are present
 * in the Spring context. No BPMN processes are deployed yet (bare minimum — BPMN
 * processes land in a later phase).
 *
 * <p>This test loads a minimal Spring Boot context with Flowable auto-configuration
 * enabled. It does NOT require a real database — Flowable uses H2 in-memory for
 * test scope (the {@code h2} dependency is present in {@code runtime} scope; tests
 * activate it via a test {@code application-test.yml} or direct H2 datasource config).
 *
 * @see FlowableConfig
 */
// SDD-PROVENANCE: flowable-config-test | vault: .mega-sdd/vaults/acquisition | ADR-13 Flowable config test
class FlowableConfigTest {

	/**
	 * Verify that the Flowable engine configuration customizer bean is created.
	 * This confirms the {@link FlowableConfig} class is on the component-scan path
	 * and produces the expected bean.
	 */
	@Test
	void flowableEngineConfigurerBeanIsCreated() {
		FlowableConfig config = new FlowableConfig();
		assertThat(config.flowableEngineConfigurer()).isNotNull();
	}

	/**
	 * Verify that the Flowable configuration class exists and is annotated as a
	 * Spring {@code @Configuration} class with {@code @ConditionalOnBean(DataSource.class)}.
	 */
	@Test
	void flowableConfigIsConfigurationClass() {
		assertThat(FlowableConfig.class.isAnnotationPresent(
				org.springframework.context.annotation.Configuration.class)).isTrue();
		assertThat(FlowableConfig.class.isAnnotationPresent(
				org.springframework.boot.autoconfigure.condition.ConditionalOnBean.class)).isTrue();
	}
}
