# DRIFT-REPORT — jwt-login vault

**Generated:** 2026-07-23
**VAULT_DIR:** `.mega-sdd/vaults/jwt-login`
**CODE_DIR:** `/home/ikbalgazalba/AI/Project/coresystembackend`
**DRIFT_SCOPE:** full
**Vault Mode:** `implementation_mode: new` (OUTDATED — code is implemented)

---

## Executive Summary

**DRIFT STATUS: LOW** — The codebase closely aligns with vault documentation. All core components specified in the vault are implemented. Minor drifts detected in:

1. **Undocumented bean**: `RestClient.Builder` bean in SecurityConfig not in U-006 spec
2. **Method name mismatch**: `login()` vs spec's `authenticateUserUCS()` (cosmetic)
3. **Table name decision made**: Code uses `users` (pack standard), resolving OQ-DM-1 implicitly
4. **Test modifications**: `contextLoads` smoke test has exclusions/mocks not specified in units

---

## Findings by Category

### 1. VAULT-ONLY (documented but not in code)

| Entity | Vault Reference | Confidence | Notes |
|--------|-----------------|------------|-------|
| None | — | — | All vault-documented components are implemented |

**Resolution:** No vault-only drift detected. The vault accurately describes what exists in code.

---

### 2. CODE-ONLY (implemented but not documented)

| Entity | Code Location | Vault Impact | Confidence | Action |
|--------|---------------|--------------|------------|--------|
| `RestClient.Builder` bean | `SecurityConfig.java:107-110` | U-006 spec incomplete | **HIGH** | Add to U-006 spec |
| `LdapAuthResult` record | `LdapUcsService.java:96` | Minor gap | **MEDIUM** | Document in vault |
| Helper methods in LdapUcsService | `LdapUcsService.java:246-303` | Implementation detail | **LOW** | No action needed |

#### 2.1 RestClient.Builder Bean [HIGH]

**Finding:** SecurityConfig defines a `@Bean RestClient.Builder restClientBuilder()` that was not specified in U-006.

**Code:**
```java
// SecurityConfig.java:107-110
@Bean
public RestClient.Builder restClientBuilder() {
    return RestClient.builder();
}
```

**Vault Spec (U-006):** Only specified three beans:
- `SecurityFilterChain` (spec'd)
- `PasswordEncoder` (spec'd)
- `CorsConfigurationSource` (spec'd)

**Rationale in code comment:** "Spring Boot 4.x no longer auto-configures a `RestClient.Builder` bean (the auto-config was removed from the starter), so it is provided explicitly here for `LdapUcsService` (U-007) to consume via constructor injection."

**Recommendation:** Update U-006 spec to document this bean. This is a legitimate Boot 4.x adaptation, not a deviation.

---

### 3. NAME/SHAPE MISMATCH

| Entity | Vault Name | Code Name | Confidence | Impact |
|--------|------------|-----------|------------|--------|
| Controller method | `authenticateUserUCS` | `login` | **HIGH** | Cosmetic |
| Entity table | `mojf_users` (OQ-DM-1 pending) | `users` | **HIGH** | Decision resolved |
| Test contextLoads | Basic smoke test | With exclusions/mocks | **MEDIUM** | Behavioral |

#### 3.1 Controller Method Name Mismatch [HIGH]

**Vault (U-008.md:50):**
```java
@PostMapping("/dologin") authenticateUserUCS(@RequestBody LoginRequest req)
```

**Code (AuthUserController.java:50):**
```java
@PostMapping("/dologin")
public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest)
```

**Analysis:** Method named `login()` instead of spec's `authenticateUserUCS()`. This is documented in `bolts/U-008/bolt-report.md:59` as a cosmetic deviation with no functional impact.

**Recommendation:** Either update vault to use `login()` (preferred for brevity) or rename method to match spec. No urgent action required.

---

#### 3.2 Table Name Decision [HIGH]

**Vault (OQ-DM-1):** Open question whether to use `users` (pack standard) or `mojf_users` (newmojf reference).

**Code (Users.java:14):**
```java
@Table(name = "users")
public class Users { ... }
```

**Analysis:** Code chose `users` (pack standard). This is a valid resolution of OQ-DM-1, but the vault OQ remains marked as pending.

**Recommendation:** Mark OQ-DM-1 as RESOLVED with decision "users (pack standard)".

---

#### 3.3 contextLoads Test Modifications [MEDIUM]

**Vault expectation (bolts/U-006/dispatch-prompt.md:73):** The contextLoads smoke test would fail without datasource config.

**Code (CoresystembackendApplicationTests.java):**
```java
@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
})
class CoresystembackendApplicationTests {
    @MockitoBean
    private LdapUcsService ldapUcsService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void contextLoads() {
    }
}
```

**Analysis:** The smoke test has DataSource/Hibernate auto-config exclusions and mocks external collaborators. This is NOT specified in any unit spec but is a sensible adaptation for test isolation.

**Recommendation:** Document this test configuration pattern in vault for future reference.

---

## 4. Cross-Reference Verification

### 4.1 Component Presence Matrix

| Component | Vault Doc | Unit Spec | Code | Status |
|-----------|-----------|-----------|------|--------|
| `Users` entity | 03-data-model.md | U-002 | `entity/Users.java` | MATCH |
| `UserRepository` | 02-architecture.md | U-003 | `repository/UserRepository.java` | MATCH |
| `JwtUtils` | 02-architecture.md | U-005 | `security/JwtUtils.java` | MATCH |
| `SecurityConfig` | 02-architecture.md | U-006 | `config/SecurityConfig.java` | MATCH (+ 1 bean) |
| `LdapUcsService` | 02-architecture.md | U-007 | `service/LdapUcsService.java` | MATCH |
| `AuthUserController` | 02-architecture.md | U-008 | `controller/AuthUserController.java` | MATCH (method name diff) |
| DTOs (LoginRequest, JwtResponse, MessageResponse) | 02-architecture.md | U-004 | `dto/*.java` | MATCH |
| `application.yaml` config | 06-constraints.md | U-008 | `application.yaml` | MATCH |
| `pom.xml` deps | 06-constraints.md | U-001 | `pom.xml` | MATCH |

### 4.2 API Contract Verification

| Endpoint | Vault Spec | Code Implementation | Status |
|----------|------------|---------------------|--------|
| `POST /api/auth/dologin` | 02-architecture.md:73-108 | `AuthUserController.java:49-102` | MATCH |

**Request/Response shape verification:**
- Request: `{uname, pass}` — MATCH
- Response success: `{token, type, id, uname, mitKode, urole}` — MATCH (verbatim field names per ADV-001)
- Error 400: `MessageResponse` — MATCH
- Error 401: `MessageResponse("Authentication failed")` — MATCH

### 4.3 Dependency Verification (pom.xml vs U-001)

| Dependency | U-001 Spec | pom.xml | Version | Status |
|------------|------------|---------|---------|--------|
| spring-boot-starter-web | required | present | managed | MATCH |
| spring-boot-starter-security | required | present | managed | MATCH |
| spring-boot-starter-data-jpa | required | present | managed | MATCH |
| postgresql | required | present | runtime scope | MATCH |
| jjwt-api | required | present | 0.12.6 | MATCH |
| jjwt-impl | required | present | 0.12.6 (runtime) | MATCH |
| jjwt-jackson | required | present | 0.12.6 (runtime) | MATCH |

---

## 5. Configuration Verification

### 5.1 application.yaml vs Vault Constraints

| Config Key | Vault Expectation | Code Value | Status |
|------------|-------------------|------------|--------|
| `spring.datasource.url` | `${SPRING_DATASOURCE_URL}` placeholder | `${SPRING_DATASOURCE_URL:jdbc:postgresql://...}` | MATCH |
| `spring.datasource.username` | env var placeholder | `${SPRING_DATASOURCE_USERNAME:}` | MATCH |
| `spring.datasource.password` | env var placeholder | `${SPRING_DATASOURCE_PASSWORD:}` | MATCH |
| `spring.jpa.hibernate.ddl-auto` | `none` (OQ-DM-3) | `none` | MATCH |
| `coresystem.app.jwtSecret` | env var placeholder (OQ-AR-3) | `${JWT_SECRET:dev-only-secret-change-me}` | MATCH |
| `coresystem.app.jwtExpirationMs` | 86400000 | 86400000 | MATCH |
| LDAP config placeholders | Required by OQ-AR-6 | Present (10 keys) | MATCH |

---

## 6. Test Coverage Verification

| Unit | Specified Test | Implemented | Status |
|------|----------------|-------------|--------|
| U-002 | — | (entity, no dedicated test) | N/A |
| U-003 | — | (interface, no dedicated test) | N/A |
| U-005 | — | `JwtUtilsTest.java` | EXTRA (good) |
| U-006 | `SecurityConfigTest` | `config/SecurityConfigTest.java` | MATCH |
| U-007 | — | `service/LdapUcsServiceTest.java` | EXTRA (good) |
| U-008 | `AuthUserControllerTest` | `controller/AuthUserControllerTest.java` | MATCH |
| U-009 | `AuthLoginIntegrationTest` | `AuthLoginIntegrationTest.java` | MATCH |

---

## 7. Open Questions Status Review

| OQ ID | Vault Status | Code Decision | Drift? |
|-------|--------------|---------------|--------|
| OQ-AR-1 | RESOLVED v1.2 | LDAP UCS endpoint used | NO |
| OQ-AR-2 | RESOLVED v1.2 | newmojf DB used | NO |
| OQ-AR-3 | pending (recommend: env var) | env var placeholder | IMPLICIT RESOLUTION |
| OQ-AR-4 | pending (recommend: jjwt 0.12.x) | jjwt 0.12.6 used | IMPLICIT RESOLUTION |
| OQ-AR-5 | pending (recommend: explicit CORS) | explicit origins in SecurityConfig | IMPLICIT RESOLUTION |
| OQ-AR-6 | RESOLVED v1.2 | LDAP config externalized | NO |
| OQ-AR-7 | pending | verbatim `mitKode`/`urole` used | IMPLICIT RESOLUTION |
| OQ-DM-1 | pending | `users` table used | IMPLICIT RESOLUTION |
| OQ-DM-3 | pending (recommend: none) | `ddl-auto: none` | IMPLICIT RESOLUTION |
| OQ-FL-3 | pending | generic error messages | IMPLICIT RESOLUTION |

---

## 8. Vault Mode Status

**Current vault mode:** `implementation_mode: new`

**Actual status:** Code is fully implemented. All units executed successfully (bolt-reports exist for U-001 through U-009).

**Recommendation:** Update vault to `implementation_mode: existing` and `mode: existing`. The `mode_migrate_after` condition ("first commit lands on main branch") has been met — multiple feature commits exist.

---

## 9. Recommendations Summary

| Priority | Finding | Action |
|----------|---------|--------|
| P1 | Vault mode outdated | Flip `implementation_mode: existing` |
| P2 | `RestClient.Builder` bean undocumented | Add to U-006 spec |
| P2 | OQ-DM-1 pending but decided | Mark RESOLVED |
| P3 | Method name `login` vs `authenticateUserUCS` | Update vault OR code (cosmetic) |
| P3 | Multiple OQs implicitly resolved | Batch-resolve via `resolve-oq` |
| LOW | Test exclusions in contextLoads | Document pattern in vault |

---

## 10. Confidence Ratings

| Category | Confidence | Rationale |
|----------|------------|-----------|
| Component presence | **HIGH** | Direct file verification |
| API contract compliance | **HIGH** | Exact field name matching verified |
| Configuration compliance | **HIGH** | YAML structure matches spec |
| Dependency compliance | **HIGH** | pom.xml matches U-001 exactly |
| OQ resolution status | **MEDIUM** | Implicit resolutions need formal closure |
| Vault mode status | **HIGH** | Clear evidence of implementation |

---

## Appendix A: Files Analyzed

### Vault Files
- `.mega-sdd/vaults/jwt-login/02-architecture.md`
- `.mega-sdd/vaults/jwt-login/03-data-model.md`
- `.mega-sdd/vaults/jwt-login/04-flows.md`
- `.mega-sdd/vaults/jwt-login/05-decisions.md`
- `.mega-sdd/vaults/jwt-login/06-constraints.md`
- `.mega-sdd/vaults/jwt-login/units/U-001.md` through `U-009.md`

### Source Files
- `src/main/java/com/coresystem/coresystembackend/CoresystembackendApplication.java`
- `src/main/java/com/coresystem/coresystembackend/controller/AuthUserController.java`
- `src/main/java/com/coresystem/coresystembackend/security/JwtUtils.java`
- `src/main/java/com/coresystem/coresystembackend/config/SecurityConfig.java`
- `src/main/java/com/coresystem/coresystembackend/entity/Users.java`
- `src/main/java/com/coresystem/coresystembackend/repository/UserRepository.java`
- `src/main/java/com/coresystem/coresystembackend/service/LdapUcsService.java`
- `src/main/java/com/coresystem/coresystembackend/dto/JwtResponse.java`
- `src/main/java/com/coresystem/coresystembackend/dto/MessageResponse.java`
- `src/main/java/com/coresystem/coresystembackend/dto/LoginRequest.java`
- `src/main/resources/application.yaml`
- `pom.xml`

### Test Files
- `src/test/java/com/coresystem/coresystembackend/CoresystembackendApplicationTests.java`
- `src/test/java/com/coresystem/coresystembackend/AuthLoginIntegrationTest.java`
- `src/test/java/com/coresystem/coresystembackend/security/JwtUtilsTest.java`
- `src/test/java/com/coresystem/coresystembackend/config/SecurityConfigTest.java`
- `src/test/java/com/coresystem/coresystembackend/controller/AuthUserControllerTest.java`
- `src/test/java/com/coresystem/coresystembackend/service/LdapUcsServiceTest.java`

---

**END OF DRIFT REPORT**
