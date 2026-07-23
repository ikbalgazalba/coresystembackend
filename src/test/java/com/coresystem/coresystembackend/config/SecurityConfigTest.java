package com.coresystem.coresystembackend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import jakarta.servlet.Filter;

/**
 * Focused security slice test for {@link SecurityConfig}.
 *
 * <p>Loads a minimal application context containing ONLY {@link SecurityConfig} (via
 * {@link SpringBootTest#classes()}) — not the full {@code @SpringBootApplication}. This keeps
 * the test scoped to the security posture (the {@code SecurityFilterChain}, {@code PasswordEncoder}
 * and {@code CorsConfigurationSource} beans) and avoids pulling unrelated services that are not
 * yet self-sufficient at this stage of the build (e.g. {@code LdapUcsService}, which needs a
 * {@code RestClient.Builder}, and the JPA/datasource layer that U-008 configures later).
 *
 * <p>Because {@link SecurityConfig} is annotated {@code @EnableWebSecurity}, the
 * {@code springSecurityFilterChain} bean (type {@link Filter}) is registered and drives request
 * authorization. MockMvc is wired manually via
 * {@link MockMvcBuilders#webAppContextSetup(WebApplicationContext)} with that filter added —
 * the dependency-free equivalent of {@code @AutoConfigureMockMvc}, which in Spring Boot 4.x moved
 * out of {@code spring-boot-starter-test} into a separate artifact that is not on this
 * project's classpath (and likewise {@code spring-security-test}'s {@code springSecurity()}
 * configurer is absent).
 */
@SpringBootTest(classes = SecurityConfig.class)
class SecurityConfigTest {

	@Autowired
	private WebApplicationContext context;

	/** The Spring Security filter chain bean registered by {@code @EnableWebSecurity}. */
	@Autowired
	private Filter springSecurityFilterChain;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.addFilters(springSecurityFilterChain)
				.build();
	}

	@Test
	void permitAllForAuthEndpoint() throws Exception {
		// /api/auth/** is permitAll — the request must pass through Spring Security and reach
		// the dispatcher. With no controller mapped to /api/auth/dologin yet (U-008), the
		// dispatcher answers 404, which proves security did NOT short-circuit to 401/403.
		mockMvc.perform(get("/api/auth/dologin"))
				.andExpect(status().isNotFound());
	}

	@Test
	void protectedPathRequiresAuth() throws Exception {
		// anyRequest().authenticated() — an unauthenticated request to a non-permitAll path
		// must be rejected by Spring Security. The exact rejection code is 401 when an
		// authentication entry point (challenge) is configured and 403 otherwise; the unit
		// spec accepts either ("401/403"). Either way it must NOT be 200/404 (which would mean
		// the path was reachable).
		int status = mockMvc.perform(get("/api/some-protected-path"))
				.andReturn()
				.getResponse()
				.getStatus();
		assertThat(status == 401 || status == 403)
				.as("protected path must be rejected with 401 or 403, but was " + status)
				.isTrue();
	}

}

// SDD-PROVENANCE: U-006 | vault: .mega-sdd/vaults/jwt-login | SecurityConfig slice test (focused context: classes=SecurityConfig only; manual MockMvc w/ springSecurityFilterChain; permitAll + authenticated assertions)
