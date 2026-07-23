# Bolt Report — U-006 (SecurityConfig, Spring Security 7.x)

## bolt_self_report
- **confidence:** 0.90
- **status:** DONE

## target_hashes (sha256)
- `src/main/java/com/coresystem/coresystembackend/config/SecurityConfig.java`: `87affd94b25d147408eca82ac959d7d67d4014c467c19ecfea3902360001c149`
- `src/test/java/com/coresystem/coresystembackend/config/SecurityConfigTest.java`: `12d131db6b1f6add81af5dabde6ceccf104e4d6e5dd1b9a735e2c78b83aade50`

## acceptance_test
`mvn -q test -Dtest=SecurityConfigTest`
```
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.525 s -- in com.coresystem.coresystembackend.config.SecurityConfigTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## what was implemented
- `SecurityConfig` @Configuration @EnableWebSecurity in `com.coresystem.coresystembackend.config`.
- `filterChain(HttpSecurity http)` — lambda DSL (7.1.0): `csrf(csrf -> csrf.disable())` WITH explanatory comment (§B-004), `SessionCreationPolicy.STATELESS`, `authorizeHttpRequests` with `requestMatchers("/api/auth/**").permitAll().anyRequest().authenticated()`, `cors(Customizer.withDefaults())`. No legacy `authorizeRequests`/`antMatchers`/`WebSecurityConfigurerAdapter`.
- `passwordEncoder()` — `PasswordEncoderFactories.createDelegatingPasswordEncoder()` (§B-005, no NoOp).
- `corsConfigurationSource()` — explicit origins `http://localhost:3000` + `http://localhost:8080` (OQ-AR-5, no wildcard origin; headers wildcard permitted per OQ-AR-5 scoping). Methods: GET/POST/PUT/DELETE/OPTIONS.
- `AuthenticationManager` bean omitted (spec step 5: "Prefer OMIT").
- `SecurityConfigTest` — 2 JUnit 5 tests via `@SpringBootTest(classes = SecurityConfig.class)` + manual MockMvc (Boot 4.1.1 repackaged `@AutoConfigureMockMvc` out of starter-test). `permitAllForAuthEndpoint` asserts 404 (past security, no handler). `protectedPathRequiresAuth` asserts 401-or-403 (security rejects).

## hard rules honored
- FILE_PRESENCE: `config/SecurityConfig.java` exists after bolt.
- Whitelist: only the two target files (SecurityConfig.java + SecurityConfigTest.java) created; no pom.xml, no application.yaml, no existing source touched.
- §B-002: no WebSecurityConfigurerAdapter — SecurityFilterChain bean only.
- §B-004 / §CSRF: CSRF disabled with explanatory comment.
- §B-005: delegating PasswordEncoder, no NoOp.
- OQ-AR-5 / ADV-004: explicit CORS origins, no wildcard `*`.
- §A-004: no System.out, no Lombok.

## reuse_decisions
- `{candidate: reuse-index.yaml, decision: not_applicable, reason: "No reuse-index.yaml exists; config package did not exist before this bolt."}`
- `{candidate: JwtUtils as filter (U-005), decision: not_applicable, reason: "JWT filter wiring is out of scope (D-002 deferred). SecurityConfig does not inject JwtUtils."}`

## notes / deviation from dispatch skeleton
- Test approach deviated from dispatch recipe: `@AutoConfigureMockMvc` not on classpath in Boot 4.1.1 (moved to separate artifact); `DataSourceAutoConfiguration`/`HibernateJpaAutoConfiguration` import paths changed in Boot 4.x. Used `@SpringBootTest(classes = SecurityConfig.class)` + manual `MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain)` instead. This is a tighter, more focused test.
- Protected-path test accepts 401-or-403 (spec step 6 says "returns 401/403"); actual observed value is 403 (no auth entry point configured yet).

## retry_history
1. Test compile failed — `@AutoConfigureMockMvc` missing, `DataSourceAutoConfiguration` import path wrong for Boot 4.1.1.
2. Switched to manual MockMvc, still full `@SpringBootTest` — context load failed (LdapUcsService needs RestClient.Builder).
3. Focused `@SpringBootTest(classes = SecurityConfig.class)` — context loads, permitAll PASS, protected-path got 403 not 401.
4. Accept 401-or-403 per spec — both PASS. BUILD SUCCESS.

## Review panel

**Tier:** full (risk signals: auth/security unit, binding §B) — spec + security (load-bearing lenses); quality/standards verified inline by controller.
**Lenses dispatched (blind):** spec, security
**Code commit:** 574fce88

| Lens | Verdict | Critical | Important | Minor |
|---|---|---|---|---|
| spec | PASS | 0 | 0 | 1 |
| security | PASS | 0 | 0 | 2 |

**Merge result:** No spec ❌, no Critical → **mergeable**. No re-dispatch.

### Findings (merged)

| # | Severity | Lens | Finding | Evidence |
|---|---|---|---|---|
| 1 | Minor | spec | SecurityConfigTest.java not listed in target_files (only SecurityConfig.java declared); test required by acceptance_test entry | U-006.md:7-9 |
| 2 | Minor | security | CORS origins hardcoded as localhost literals in source; needs externalization before non-local deploy | SecurityConfig.java:85 |
| 3 | Minor | security | Test uses @Autowired field injection, not constructor injection (§C-004 style drift, no security impact) | SecurityConfigTest.java:32-35 |

**Dropped-no-evidence:** 0. **Consensus:** 0.

### Controller inline verification (quality + standards)
- Lambda DSL only (no chained-string `http.csrf().disable()`, no `authorizeRequests`/`antMatchers`) — §B-002 ✓
- `requestMatchers("/api/auth/**").permitAll()` + `anyRequest().authenticated()` — spec step 2 ✓
- STATELESS session — spec step 2 ✓
- CSRF disabled with explanatory comment — §B-004/§CSRF ✓
- Delegating PasswordEncoder — §B-005 ✓
- Explicit CORS origins, no wildcard — OQ-AR-5/ADV-004 ✓
- No `setAllowCredentials(true)` — correct for stateless JWT ✓
- AuthenticationManager omitted — spec step 5 ✓
- No JWT filter wired (out of scope) — D-002 ✓
- Provenance present in SecurityConfig.java ✓
- Whitelist (2 files) honored ✓
- `setAllowedHeaders(List.of("*"))` is headers-whitelist (request headers), not origins — safe per OQ-AR-5 scoping ✓

### Gate decision
PASS — mergeable as-is. Findings are optional hardening / spec-hygiene / style-only / known-dev-deferred.

### Post-flight Hard-rule scan
- Grammar: v1-bullet
- Rules (FILE_PRESENCE): **pass**.
- Evidence: `postflight.json` (status: pass).
