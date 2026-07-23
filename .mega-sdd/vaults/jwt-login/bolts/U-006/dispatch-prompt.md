# Dispatch Prompt — U-006 (SecurityConfig, Spring Security 7.x)

## Mission
Implement ONE mega-sdd unit: **U-006 — Create SecurityConfig (Spring Security 7.x SecurityFilterChain bean)**. Write `SecurityConfig.java` + `SecurityConfigTest.java`, run the test, commit. Report DONE or HALT.

## Environment facts
- **Working dir:** `/home/ikbalgazalba/AI/Project/coresystembackend`
- **`./mvnw` is BROKEN** (`.mvn/wrapper/maven-wrapper.properties` missing) — use system `mvn` (Maven 3.9.16, Java 21 via SDKMAN).
- Git branch: `main`. No client-side hooks. `gpgsign` off.
- Base package: `com.coresystem.coresystembackend`. Config package: `com.coresystem.coresystembackend.config` (does NOT exist yet — this bolt creates it).
- **Spring Security version resolved: 7.1.0** (spring-boot-starter-parent 4.1.1-SNAPSHOT BOM). Confirmed jars in `~/.m2`: `spring-security-config-7.1.0.jar`, `spring-security-web-7.1.0.jar`. This is the modern component-based DSL.
- Deps on classpath (from U-001): `spring-boot-starter-security`, `spring-boot-starter-web`, `spring-boot-starter-test` (JUnit 5 + Mockito + MockMvc).
- `JwtUtils` exists (U-005) at `com.coresystem.coresystembackend.security.JwtUtils` — a `@Component`. U-006 does NOT inject/wire JwtUtils as a filter (out of scope — D-002 resource-server deferred; see Out of scope).

## Unit spec (authoritative — `.mega-sdd/vaults/jwt-login/units/U-006.md`)
- task_type: **create**, depends_on: [U-001 (DONE), U-005 (DONE)], module: M-security, complexity: medium, squad: default
- target_files: `src/main/java/com/coresystem/coresystembackend/config/SecurityConfig.java` (create)
  - NOTE: the unit ALSO requires `SecurityConfigTest` (step 6) — create it at `src/test/java/com/coresystem/coresystembackend/config/SecurityConfigTest.java` (test mirrors main per §A-003). The test is part of this bolt's deliverable (acceptance_test runs it).
- acceptance_test: `mvn -q test -Dtest=SecurityConfigTest` → passes
- binding_refs: [C-007, OQ-AR-5]

## Goal
`SecurityConfig` with a `@Bean SecurityFilterChain` (NOT `WebSecurityConfigurerAdapter`) — permitAll `/api/auth/**`, stateless sessions, CSRF disabled for the stateless token API (WITH explanatory comment), a `PasswordEncoder` bean, and CORS via `SecurityFilterChain` (not wildcard `@CrossOrigin`).

## Required implementation (Spring Security 7.1.0 DSL — from unit spec steps 1-5)

Create `SecurityConfig.java`, `@Configuration @EnableWebSecurity`, package `com.coresystem.coresystembackend.config`:

1. **`@Bean SecurityFilterChain filterChain(HttpSecurity http)`** — use the LAMBDA DSL (7.x). Throws `Exception`.
   ```java
   http
       // CSRF disabled: this is a stateless JWT API — there is no session/cookie to forge, so CSRF protection is not applicable.
       .csrf(csrf -> csrf.disable())
       .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
       .authorizeHttpRequests(auth -> auth
           .requestMatchers("/api/auth/**").permitAll()
           .anyRequest().authenticated())
       .cors(Customizer.withDefaults());
   return http.build();
   ```
   - The explanatory comment on the CSRF line is REQUIRED (constitution §B-004 / pack §CSRF — anti-pattern: "DO NOT disable CSRF without an explanatory comment").
   - Use `authorizeHttpRequests` (NOT legacy `authorizeRequests`), `requestMatchers` (NOT legacy `antMatchers` — removed in 6.x+).

2. **`@Bean PasswordEncoder passwordEncoder()`** — delegating encoder (constitution §B-005; bean present even though auth is via LDAP):
   ```java
   return PasswordEncoderFactories.createDelegatingPasswordEncoder();
   ```
   - NOT `NoOpPasswordEncoder` (forbidden anti-pattern).

3. **`@Bean CorsConfigurationSource corsConfigurationSource()`** — EXPLICIT origins, NOT wildcard (OQ-AR-5 ACCEPTED; advisor ADV-004):
   ```java
   CorsConfiguration configuration = new CorsConfiguration();
   configuration.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:8080")); // explicit dev origins — NOT "*" (OQ-AR-5)
   configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
   configuration.setAllowedHeaders(List.of("*"));
   UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
   source.registerCorsConfiguration("/**", configuration);
   return source;
   ```
   - `setAllowedOrigins(List.of(...))` with an explicit list — NOT `addAllowedOrigin("*")`, NOT `setAllowedOriginPatterns("*")`.
   - `setAllowedHeaders(List.of("*"))` is the only permitted wildcard (headers, not origins) — OQ-AR-5 scopes the explicit-origin rule to ORIGINS.

4. **AuthenticationManager** — OMIT (spec step 5: "Prefer OMIT unless U-008 needs it"). newmojf's active `/dologin` path does not use it. Do NOT create the bean. (If you are tempted to add it, don't — it's explicitly optional/unused.)

### Imports needed
`org.springframework.context.annotation.Bean`, `org.springframework.context.annotation.Configuration`, `org.springframework.security.config.Customizer`, `org.springframework.security.config.annotation.web.builders.HttpSecurity`, `org.springframework.security.config.annotation.web.configuration.EnableWebSecurity`, `org.springframework.security.crypto.password.PasswordEncoder`, `org.springframework.security.crypto.factory.PasswordEncoderFactories`, `org.springframework.security.web.SecurityFilterChain`, `org.springframework.security.config.http.SessionCreationPolicy`, `org.springframework.web.cors.CorsConfiguration`, `org.springframework.web.cors.CorsConfigurationSource`, `org.springframework.web.cors.UrlBasedCorsConfigurationSource`, `java.util.List`.

## SecurityConfigTest (step 6 — REQUIRED)
`src/test/java/com/coresystem/coresystembackend/config/SecurityConfigTest.java`, JUnit 5.

**Approach:** Use `@SpringBootTest` + `@AutoConfigureMockMvc` to spin up the security context, OR (lighter, recommended) a standalone `@WebMvcTest` slice is NOT ideal here because there is no controller yet. **Recommended: `@SpringBootTest` with `@AutoConfigureMockMvc`** — it loads the real `SecurityConfig` (via component scan of `com.coresystem.coresystembackend`) and exercises the filter chain with MockMvc.

⚠️ **`contextLoads` note:** U-008 has NOT yet added `spring.datasource` config. `@SpringBootTest` will try to auto-configure the DataSource (data-jpa is on the classpath from U-001) and FAIL to start the context without a DB URL. To keep `SecurityConfigTest` standalone-green, **disable the problematic auto-configurations in the test** so it does not depend on U-008:
```java
@SpringBootTest
@AutoConfigureMockMvc
@ImportAutoConfiguration(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class
})
```
(add the necessary imports: `org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration`, `org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration`). This is a legitimate test-scoped narrowing, not a production bypass. (Alternatively use `@MockBean`-style slicing, but the exclude approach is simplest and keeps the security chain real.)

Test cases (spec acceptance — permitAll vs authenticated):
- `permitAllForAuthEndpoint()` — `mockMvc.perform(get("/api/auth/dologin")).andExpect(status().isNotFound())` — NOTE: `/api/auth/dologin` does not yet exist (no controller until U-008), so the expected status is **404 Not Found** (the request passed the security filter and reached the dispatcher with no handler), NOT 401/403. Asserting `not Forbidden()` / `isNotFound()` proves the path is permitAll (it got past security). Use `andExpect(status().isNotFound())` OR `andExpect(status().is4xxClientError())` — do NOT assert 200 (no endpoint yet). The spec's manual acceptance ("returns 200") is deferred to U-008.
- `protectedPathRequiresAuth()` — `mockMvc.perform(get("/api/some-protected-path")).andExpect(status().isUnauthorized())` — a path NOT under `/api/auth/**` must be rejected by security → **401 Unauthorized** (default Spring Security entry point for a stateless, permitAll-rest, authenticated-any-request config). This proves `anyRequest().authenticated()` is in force.

(Use static imports for `get`/`status` from `MockMvcRequestBuilders` / `MockMvcResultMatchers`.)

## Hard rules (v1 bullet — preflight taken)
- **FILE_PRESENCE:** `src/main/java/com/coresystem/coresystembackend/config/SecurityConfig.java` MUST exist after bolt. (pre-state: absent.)

## Anti-patterns (MUST honor — these are the post-flight + panel checks)
- **§B-002:** NO `extends WebSecurityConfigurerAdapter` (removed in Spring 6). Use `@Bean SecurityFilterChain`.
- NO `@EnableGlobalMethodSecurity` (deprecated → removed; spec Out of scope: no method security).
- NO legacy DSL verbs: `authorizeRequests(...)`, `antMatchers(...)`, `http.csrf().disable()` (chained-string form). Use the lambda/customizer DSL only.
- **§B-004 / §CSRF:** DO NOT disable CSRF without an explanatory comment (one inline comment is present above).
- **OQ-AR-5 / ADV-004:** NO `@CrossOrigin(origins = "*")`, NO `addAllowedOrigin("*")`/`setAllowedOriginPatterns("*")`. CORS via `SecurityConfig` `CorsConfigurationSource` with EXPLICIT origins.
- NO `NoOpPasswordEncoder` (use delegating `PasswordEncoderFactories.createDelegatingPasswordEncoder()`).
- **§C-004:** constructor injection where deps are injected (none here beyond `HttpSecurity` param — fine). No field injection of services.
- **§A-004:** SLF4J for any logging, no `System.out`. (You likely need no logging in a config class — fine to omit a logger entirely.)

## Constitution clauses in force (vault `.mega-sdd/vaults/jwt-login/constitution.md`)
- §B-002 SecurityFilterChain bean (not WebSecurityConfigurerAdapter)
- §B-004 CSRF disabled w/ comment
- §B-005 PasswordEncoder bean (delegating)
- §C-004 constructor injection
- §A-004 SLF4J not sysout
- OQ-AR-5 CORS explicit origins (ACCEPTED recommendation)
- §D-002 no hardcoded secrets/URLs — the dev origins `http://localhost:3000`/`http://localhost:8080` are NOT secrets; they are dev defaults pending OQ-AR-5 finalization (allowed — the unit spec step 4 says "default to a dev origin").

## Target file whitelist
- `src/main/java/com/coresystem/coresystembackend/config/SecurityConfig.java` (create)
- `src/test/java/com/coresystem/coresystembackend/config/SecurityConfigTest.java` (create — required by acceptance_test)
No other files. Do NOT touch pom.xml, application.yaml, any existing source, or JwtUtils. CORS dev-origins are inline constants in SecurityConfig (not config-file properties) per unit spec step 4.

## Provenance trailer (MANDATORY in SecurityConfig.java)
```java
// SDD-PROVENANCE: U-006 | vault: .mega-sdd/vaults/jwt-login | SecurityFilterChain bean (Spring Security 7.x); permitAll /api/auth/**, STATELESS, CSRF-disabled w/ comment, delegating PasswordEncoder, explicit-origin CORS
```
(Add the same trailer to SecurityConfigTest.java for consistency.)

## Execution protocol
1. Create the `config/` package dir (the file write will create it).
2. Write `SecurityConfig.java` (4 beans: filterChain, passwordEncoder, corsConfigurationSource; NO authManager) with provenance trailer.
3. Write `SecurityConfigTest.java` (2 tests, `@SpringBootTest` + MockMvc + exclude DataSource/JPA autoconfig).
4. Run `mvn -q test -Dtest=SecurityConfigTest` — must pass (BUILD SUCCESS, Tests run: 2, Failures: 0).
5. If it fails: diagnose within whitelist. Common pitfalls: (a) the protected-path test expecting 403 when 401 is returned (Spring Security 7 default → 401; fix assertion), (b) `@SpringBootTest` failing to start due to missing DataSource (fix via the exclude block above), (c) a DSL method renamed in 7.1. Fix + retry. Max 3.
6. Commit BOTH files in ONE commit:
   ```
   feat(U-006): add SecurityConfig (Spring Security 7.x SecurityFilterChain) + test

   SecurityFilterChain bean (no WebSecurityConfigurerAdapter): permitAll
   /api/auth/**, anyRequest authenticated, STATELESS sessions, CSRF disabled
   with explanatory comment (§B-004). Delegating PasswordEncoder bean (§B-005).
   Explicit-origin CorsConfigurationSource (OQ-AR-5, no wildcard). AuthManager
   omitted (unused on active /dologin path).

   SDD-PROVENANCE: U-006 vault=.mega-sdd/vaults/jwt-login
   ```
   (no `--no-verify`, no push)
7. Report: commit SHA, sha256 of both files, verbatim last test-output lines, confidence 0.0–1.0, and a `bolt_self_report` (certain/uncertain decisions + retry_history).

## Halt conditions
- `mvn test -Dtest=SecurityConfigTest` fails after 3 retries → halt `test_fail` with verbatim error.
- Any forbidden pattern present (`extends WebSecurityConfigurerAdapter`, `NoOpPasswordEncoder`, `addAllowedOrigin("*")`, `antMatchers`, `authorizeRequests`) → halt `hard_rule_violated`.
- CSRF disable without explanatory comment → halt `hard_rule_violated` (§B-004).
- Out-of-whitelist write (pom.xml/application.yaml/existing source) → halt (whitelist violation).

## Self-assessment (REQUIRED in your report)
`bolt_self_report` YAML block: numeric `confidence` (0.0–1.0), certain/uncertain decisions, retry_history.

Begin now. Spec complete — no questions. The Spring Security version is 7.1.0 (verified); use the lambda DSL exactly as specified.
