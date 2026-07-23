# Bolt Report — U-008 (AuthUserController + application.yaml)

## bolt_self_report
- **confidence:** 0.92
- **status:** DONE

## target_hashes (sha256)
- `src/main/java/com/coresystem/coresystembackend/controller/AuthUserController.java`: `98b5696c7e339804de1af06f72ef9141c0f072f392bbdf787be047278759c4c4`
- `src/main/resources/application.yaml`: `5de91278864d695e28c97e57f25fa508079e41c2b3a88efbefbd1c76c0a6ca62`
- `src/test/java/com/coresystem/coresystembackend/controller/AuthUserControllerTest.java`: `c2847754be18a2acbfd0a0f41a9d57c67edff9194ba2e616ed1fa6634afeb488`

## acceptance_test
`mvn -q test -Dtest=AuthUserControllerTest`
```
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.437 s -- in com.coresystem.coresystembackend.controller.AuthUserControllerTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## what was implemented
- `AuthUserController.java` @RestController @RequestMapping("/api/auth"), constructor-injected LdapUcsService + JwtUtils + UserRepository (§C-004).
- `@PostMapping("/dologin") login(@RequestBody LoginRequest)` — wires: LdapUcsService.authLDAPNew → JwtUtils.generateTokenFromUname → UserRepository.findByUname → JwtResponse.
  - Null result → 400 "Failed to connect to LDAP service"
  - responseCode "00"/"01" → 200 JwtResponse (token, id, uname, mitKode, urole — verbatim per ADV-001)
  - Other responseCode → 400 "Authentication failed" (NOT raw responseDescription — OQ-FL-3/§B-007)
  - Exception → 401 "Authentication failed" (§B-007)
  - User not found in local DB → 400 "User not found in local database" (defensive addition)
- `application.yaml` — added:
  - `coresystem.app.jwtSecret: ${JWT_SECRET:dev-only-secret-change-me}` (env placeholder — OQ-AR-3)
  - `coresystem.app.jwtExpirationMs: 86400000`
  - `spring.datasource.*` (PostgreSQL env placeholders — OQ-AR-2)
  - `spring.jpa.hibernate.ddl-auto: none` (OQ-DM-3)
  - `coresystem.ldap.*` (10 env placeholders — U-007 config)
- NO @CrossOrigin (CORS via U-006 SecurityConfig).
- `AuthUserControllerTest.java` — pure unit test (4 cases: success, null LDAP, bad cred, exception). Boot 4.1.1 removed @WebMvcTest; spring-security-test not on classpath — controller instantiated directly with Mockito mocks.

## hard rules honored
- FILE_PRESENCE: `controller/AuthUserController.java` exists after bolt.
- NAMING_RULE: `AuthUserController.java` — PascalCase.
- Whitelist: only the 3 declared files created/modified; no pom.xml, no other source.

## anti-patterns verified
- No field @Autowired (§C-004) ✓
- No @CrossOrigin (OQ-AR-5) ✓
- No Users entity in response (§C-002) ✓
- No hardcoded jwtSecret (§D-002/OQ-AR-3) ✓
- No raw responseDescription echo (§B-007/OQ-FL-3) ✓
- mitKode/urole verbatim (OQ-AR-7/ADV-001) ✓
- Constructor injection only ✓

## reuse_decisions
- `{candidate: JwtResponse/LoginRequest/MessageResponse (U-004), decision: reused, reason: "DTOs created by U-004 are the exact response/request types for this endpoint"}`
- `{candidate: LdapUcsService (U-007), decision: reused, reason: "Delegates LDAP auth to existing service — no duplication"}`
- `{candidate: JwtUtils (U-005), decision: reused, reason: "Delegates JWT generation to existing component"}`
- `{candidate: UserRepository (U-003), decision: reused, reason: "Delegates user lookup to existing repository"}`

## notes / deviation from dispatch skeleton
- Test approach: pure unit test (direct Mockito instantiation) instead of @WebMvcTest. Boot 4.1.1 removed @WebMvcTest from spring-boot-test-autoconfigure (jar shrunk from 229KB to 28KB); spring-security-test not on classpath. Pure unit test is simpler and more robust.
- Method name: `login()` instead of spec's `authenticateUserUCS()` — cosmetic, no functional impact.
- Defensive addition: `userOpt.isEmpty()` returns 400 "User not found in local database" — not in spec step 3 but prevents unhandled orElseThrow 500.

## retry_history
1. Implementer wrote @WebMvcTest-based test — compile failed (package does not exist in Boot 4.1.1).
2. Rewrote to @SpringBootTest + MockMvc — compile failed (spring-security-test not on classpath; SecurityMockMvcConfigurers missing).
3. Rewrote to pure unit test (direct mock injection) — 4/4 pass, BUILD SUCCESS.

## Review panel

**Tier:** full (risk signals: auth/integration unit, high risk, binding refs §B/OQ)
**Lenses dispatched (blind):** spec, security, code-quality, standards
**Code commits:** 6595b3b + 6783558

| Lens | Verdict | Critical | Important | Minor |
|---|---|---|---|---|
| spec | PASS | 0 | 0 | 3 |
| security | PASS | 0 | 0 | 3 |
| code-quality | PASS | 0 | 1 | 0 |
| standards | PASS | 0 | 0 | 2 |

**Merge result:** No spec ❌, no Critical → **mergeable**. No re-dispatch.

### Findings (merged)

| # | Severity | Lens | Finding | Evidence |
|---|---|---|---|---|
| 1 | Important | code-quality | Missing test for 'LDAP success but user not found in local DB' edge case (lines 69-74 untested) | AuthUserController.java:69-74 |
| 2 | Minor | spec | LDAP config placeholders in application.yaml are extra relative to U-008 step 5 (U-007 scope) | application.yaml:19-28 |
| 3 | Minor | spec | Test uses pure unit test instead of spec-prescribed @WebMvcTest (Boot 4.1.1 incompat) | AuthUserControllerTest.java:39-44 |
| 4 | Minor | spec | Method named `login()` instead of spec's `authenticateUserUCS()` | AuthUserController.java:50 |
| 5 | Minor | security | jwtSecret fallback value 'dev-only-secret-change-me' is a production smell | application.yaml:16 |
| 6 | Minor | security | No input validation on LoginRequest uname/pass (null → NPE downstream) | AuthUserController.java:50-52 |
| 7 | Minor | security | Datasource URL default exposes internal IP (10.95.1.43) | application.yaml:5 |
| 8 | Minor | standards | Controller catch block does not log the exception server-side (§B-007 pattern) | AuthUserController.java:96-101 |
| 9 | Minor | standards | AuthUserControllerTest lacks SDD-PROVENANCE trailing comment | AuthUserControllerTest.java (end) |

**Dropped-no-evidence:** 0. **Consensus:** 0.

### Controller inline verification
- Constructor injection with final fields — §C-004 ✓
- JwtResponse fields verbatim (mitKode, urole) — OQ-AR-7/ADV-001 ✓
- No @CrossOrigin — OQ-AR-5 ✓
- Null LDAP → 400 "Failed to connect" ✓
- Bad cred → 400 generic (not raw responseDescription) — OQ-FL-3 ✓
- Exception → 401 generic — §B-007 ✓
- jwtSecret env placeholder (${JWT_SECRET:...}) — OQ-AR-3/§D-002 ✓
- datasource env placeholders — OQ-AR-2 ✓
- ddl-auto: none — OQ-DM-3 ✓
- Provenance trailer present in AuthUserController.java ✓
- Whitelist (3 files) honored ✓

### Gate decision
PASS — mergeable as-is. Important finding is test-coverage gap (edge case logic is correct, just untested). Minor findings are defensive hardening / cosmetic / Boot-4.x adaptation.

### Post-flight Hard-rule scan
- Grammar: v1-bullet
- Rules (FILE_PRESENCE + NAMING_RULE): **pass**.
- Evidence: `postflight.json` (status: pass).
