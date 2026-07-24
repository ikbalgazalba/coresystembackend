package com.coresystem.coresystembackend.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security 7.x configuration for the JWT login API.
 *
 * <p>Establishes the security posture every endpoint inherits (binding C-007):
 * <ul>
 *   <li>{@code /api/auth/**} is {@code permitAll} (login/token endpoints must be reachable
 *       without credentials); every other request requires authentication.</li>
 *   <li>Sessions are {@link SessionCreationPolicy#STATELESS STATELESS} — authentication is
 *       per-request via a bearer token, so no HTTP session is created or used.</li>
 *   <li>CSRF protection is disabled (see the comment on the {@link #filterChain} bean) because
 *       there is no session/cookie to forge for a stateless token API.</li>
 *   <li>A delegating {@link PasswordEncoder} bean is exposed even though authentication is
 *       delegated to LDAP UCS (constitution §B-005 — never {@code NoOpPasswordEncoder}).</li>
 *   <li>CORS is configured through the {@link SecurityFilterChain} with an explicit list of
 *       allowed origins (recommendation OQ-AR-5 / advisor ADV-004 — no wildcard origin).</li>
 * </ul>
 *
 * <p>This is the modern component-based DSL ({@code SecurityFilterChain} {@code @Bean}); the
 * legacy {@code WebSecurityConfigurerAdapter} was removed in Spring Security 6 and is not used
 * here (constitution §B-002).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	/**
	 * Comma-separated list of allowed CORS origins, externalized to the
	 * {@code coresystem.cors.allowed-origins} property (HR-8 — no hardcoded origin in prod).
	 * The default keeps local dev / context-load tests working without the env var; U-005
	 * overrides this per profile (dev/prod) so the production origin is never baked in.
	 */
	private final String corsAllowedOrigins;

	public SecurityConfig(
			@Value("${coresystem.cors.allowed-origins:http://localhost:3000,http://localhost:8080}")
			String corsAllowedOrigins) {
		this.corsAllowedOrigins = corsAllowedOrigins;
	}

	/**
	 * Main request-filter chain. Defines the authorization rules, session policy, CSRF policy
	 * and CORS integration for the whole application.
	 *
	 * @param http the {@link HttpSecurity} to configure
	 * @return the built {@link SecurityFilterChain}
	 * @throws Exception if configuration fails (declared by the {@link HttpSecurity} DSL)
	 */
	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				// CSRF disabled: this is a stateless JWT API — there is no session/cookie to
				// forge, so CSRF protection is not applicable (pack §CSRF / constitution §B-004).
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/api/auth/**").permitAll()
						// U-002 additive permits — placed BEFORE anyRequest().authenticated() (matcher
						// order matters): actuator health probe + OpenAPI/Swagger docs reachable without
						// auth so k8s/readiness probes and API docs render unauthenticated. Health-only
						// exposure scope is enforced via management.endpoints.web.exposure.include (U-005).
						.requestMatchers("/actuator/health/**").permitAll()
						.requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
						.anyRequest().authenticated())
				.cors(Customizer.withDefaults());
		return http.build();
	}

	/**
	 * Delegating password encoder. Encodes/validates with the id-prefixed delegating encoder
	 * (BCrypt by default). Present even though authentication is via LDAP UCS so the bean is
	 * available for any local credential handling (constitution §B-005).
	 *
	 * @return a delegating {@link PasswordEncoder}
	 */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	/**
	 * CORS configuration source with an explicit list of allowed origins — NOT a wildcard
	 * (recommendation OQ-AR-5 ACCEPTED; advisor ADV-004). Registered for all paths ({@code /**})
	 * and consumed by {@code http.cors(Customizer.withDefaults())} in {@link #filterChain}.
	 *
	 * @return the {@link CorsConfigurationSource} used by the CORS filter
	 */
	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		// Allowed origins are externalized to ${coresystem.cors.allowed-origins} (HR-8) — comma
		// separated, split here. Default fallback (localhost dev) is a non-secret dev convenience
		// so the context-loads test passes without the env var; U-005 sets the real origin per
		// profile. Explicit origins only — NOT "*" (OQ-AR-5 scopes the explicit-origin rule to
		// ORIGINS).
		configuration.setAllowedOrigins(
				Arrays.stream(corsAllowedOrigins.split(","))
						.map(String::trim)
						.filter(s -> !s.isEmpty())
						.toList());
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		// Headers are the only permitted wildcard (OQ-AR-5 scopes the rule to origins, not headers).
		configuration.setAllowedHeaders(List.of("*"));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	/**
	 * {@link RestClient.Builder} bean. Spring Boot 4.x no longer auto-configures a
	 * {@code RestClient.Builder} bean (the auto-config was removed from the starter), so it is
	 * provided explicitly here for {@code LdapUcsService} (U-007) to consume via constructor
	 * injection. Default client with default converters; TLS uses the JVM default trust store
	 * (constitution §B-006 — no trust-all bypass).
	 *
	 * @return a {@link RestClient.Builder}
	 */
	@Bean
	public RestClient.Builder restClientBuilder() {
		return RestClient.builder();
	}

}

// SDD-PROVENANCE: U-006 | vault: .mega-sdd/vaults/jwt-login | SecurityFilterChain bean (Spring Security 7.x); permitAll /api/auth/**, STATELESS, CSRF-disabled w/ comment, delegating PasswordEncoder, explicit-origin CORS
// SDD-PROVENANCE: U-002 | vault: .mega-sdd/vaults/api-platform | actuator + additive SecurityConfig permitAll (health + doc paths) + CORS externalize ${coresystem.cors.allowed-origins}
