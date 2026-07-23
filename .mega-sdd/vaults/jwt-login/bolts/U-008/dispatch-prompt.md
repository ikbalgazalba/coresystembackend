# Dispatch Prompt — U-008 (AuthUserController + application.yaml)

## Mission
Implement ONE mega-sdd unit: **U-008 — Create AuthUserController + application.yaml config (login endpoint)**. Wire `POST /api/auth/dologin` → LdapUcsService → JwtUtils → UserRepository → JwtResponse. Configure application.yaml with JWT + datasource + LDAP placeholders. Report DONE or HALT.

## Environment facts
- **Working dir:** `/home/ikbalgazalba/AI/Project/coresystembackend`
- **`./mvnw` is BROKEN** — use system `mvn` (Maven 3.9.16, Java 21 via SDKMAN).
- Git branch: `main`. No client-side hooks. `gpgsign` off.
- Base package: `com.coresystem.coresystembackend`. Controller package: `com.coresystem.coresystembackend.controller` (does NOT exist yet — this bolt creates it).
- **Spring Boot 4.1.1-SNAPSHOT** with Spring Security 7.1.0, Jackson 3.x (`tools.jackson.databind.ObjectMapper` — not that controller uses Jackson directly; Spring MVC handles serialization).
- Deps on classpath: `spring-boot-starter-web`, `spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `jjwt-api 0.12.6`, `spring-boot-starter-test`.

## Unit spec (authoritative — `.mega-sdd/vaults/jwt-login/units/U-008.md`)
- task_type: **create**, depends_on: [U-003 (UserRepository), U-004 (DTOs), U-005 (JwtUtils), U-007 (LdapUcsService)] — ALL DONE
- target_files:
  - `src/main/java/com/coresystem/coresystembackend/controller/AuthUserController.java` (create)
  - `src/main/resources/application.yaml` (modify — add config)
- acceptance_test: `mvn -q test -Dtest=AuthUserControllerTest` → passes
- binding_refs: [C-001, C-002, C-011, OQ-AR-2, OQ-AR-3, OQ-AR-7, OQ-FL-2, OQ-FL-3, OQ-OV-1]
- risk: **high** (integration point)

## Integration contracts (verified from existing code)

### LdapUcsService (U-007)
```java
// Located at: com.coresystem.coresystembackend.service.LdapUcsService
public record LdapAuthResult(String responseCode, String responseDescription) {}
public LdapAuthResult authLDAPNew(String uname, String pass)
```
- Returns `LdapAuthResult` with `responseCode` and `responseDescription`.
- On failure (exception or null token), returns generic `"401"` / `"Authentication failed"`.
- **DO NOT** echo raw `responseDescription` if it may leak internals (§B-007, OQ-FL-3) — map to generic for non-"00"/"01" cases.

### JwtUtils (U-005)
```java
// Located at: com.coresystem.coresystembackend.security.JwtUtils
public String generateTokenFromUname(String uname)
```
- Generates HS512-signed JWT with subject=uname, expiration from config.

### UserRepository (U-003)
```java
// Located at: com.coresystem.coresystembackend.repository.UserRepository
Optional<Users> findByUname(String uname)
```

### Users entity (U-002)
```java
// Located at: com.coresystem.coresystembackend.entity.Users
Long getId()
String getUname()
String getKodeMitra()
Long getUrole()  // ← needs "ROLE_" prefix
```

### JwtResponse (U-004)
```java
// Located at: com.coresystem.coresystembackend.dto.JwtResponse
// Constructor: JwtResponse(String token, Long id, String uname, String mitKode, String urole)
// Fields: token, type="Bearer ", id, uname, mitKode, urole
// IMPORTANT: field names are VERBATIM (mitKode, urole) — do NOT rename to kodeMitra/role per ADV-001.
```

### LoginRequest (U-004)
```java
// Located at: com.coresystem.coresystembackend.dto.LoginRequest
String getUname()
String getPass()
```

### MessageResponse (U-004)
```java
// Located at: com.coresystem.coresystembackend.dto.MessageResponse
// Constructor: MessageResponse(String message)
```

## Required implementation (from unit spec steps 1-7)

### 1. AuthUserController.java
Package: `com.coresystem.coresystembackend.controller`. Annotations: `@RestController @RequestMapping("/api/auth")`.

```java
package com.coresystem.coresystembackend.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.coresystem.coresystembackend.dto.JwtResponse;
import com.coresystem.coresystembackend.dto.LoginRequest;
import com.coresystem.coresystembackend.dto.MessageResponse;
import com.coresystem.coresystembackend.entity.Users;
import com.coresystem.coresystembackend.repository.UserRepository;
import com.coresystem.coresystembackend.security.JwtUtils;
import com.coresystem.coresystembackend.service.LdapUcsService;
import com.coresystem.coresystembackend.service.LdapUcsService.LdapAuthResult;

@RestController
@RequestMapping("/api/auth")
public class AuthUserController {

    private static final Logger logger = LoggerFactory.getLogger(AuthUserController.class);

    private final LdapUcsService ldapUcsService;
    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;

    // Constructor injection (NO field @Autowired — constitution §C-004/D-005)
    public AuthUserController(LdapUcsService ldapUcsService, JwtUtils jwtUtils, UserRepository userRepository) {
        this.ldapUcsService = ldapUcsService;
        this.jwtUtils = jwtUtils;
        this.userRepository = userRepository;
    }

    @PostMapping("/dologin")
    public ResponseEntity<?> authenticateUserUCS(@RequestBody LoginRequest req) {
        try {
            LdapAuthResult result = ldapUcsService.authLDAPNew(req.getUname(), req.getPass());

            if (result == null) {
                // LDAP service unreachable
                return ResponseEntity.badRequest().body(new MessageResponse("Failed to connect to LDAP service"));
            }

            String responseCode = result.responseCode();
            // responseDescription is NOT echoed raw per §B-007 / OQ-FL-3

            if ("00".equals(responseCode) || "01".equals(responseCode)) {
                // LDAP auth success → generate JWT, lookup user
                String jwt = jwtUtils.generateTokenFromUname(req.getUname());

                Users usr = userRepository.findByUname(req.getUname())
                        .orElseThrow(() -> {
                            logger.error("User not found after LDAP auth: {}", req.getUname());
                            return new RuntimeException("User not found");
                        });

                String urole = "ROLE_" + usr.getUrole();

                return ResponseEntity.ok(new JwtResponse(
                        jwt,
                        usr.getId(),
                        usr.getUname(),
                        usr.getKodeMitra(),
                        urole));
            } else {
                // LDAP auth failure → return generic message (do NOT echo raw responseDescription)
                // Log the raw detail server-side only; return generic to client (§B-007)
                logger.warn("LDAP auth failed for user={}: code={}, desc={}",
                        req.getUname(), responseCode, result.responseDescription());
                return ResponseEntity.badRequest().body(new MessageResponse("Authentication failed"));
            }
        } catch (Exception e) {
            logger.error("Authentication error for user={}: {}", req.getUname(), e.getMessage(), e);
            return ResponseEntity.status(401).body(new MessageResponse("Authentication failed"));
        }
    }
}
```

**Key behaviors (matching newmojf `/dologin` lines 89-149):**
- Null `LdapAuthResult` → 400 "Failed to connect to LDAP service"
- `responseCode` "00" or "01" → generate JWT, lookup user, return 200 `JwtResponse`
- Other `responseCode` → 400 with sanitized message (NOT raw `responseDescription`)
- Exception → 401 "Authentication failed"
- NO `@CrossOrigin` (CORS via U-006 SecurityConfig)

### 2. application.yaml modification
**Current content:**
```yaml
spring:
  application:
    name: coresystembackend
```

**Add AFTER `spring.application.name`:**
```yaml
spring:
  application:
    name: coresystembackend
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://10.95.1.43:5432/newmojf}
    username: ${SPRING_DATASOURCE_USERNAME:}
    password: ${SPRING_DATASOURCE_PASSWORD:}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: none
    show-sql: false

coresystem:
  app:
    jwtSecret: ${JWT_SECRET:dev-only-secret-change-me}
    jwtExpirationMs: 86400000
  ldap:
    urlToken: ${LDAP_URL_TOKEN:}
    urlVerifyPassword: ${LDAP_URL_VERIFY_PASSWORD:}
    clientId: ${LDAP_CLIENT_ID:}
    clientSecret: ${LDAP_CLIENT_SECRET:}
    username: ${LDAP_USERNAME:}
    password: ${LDAP_PASSWORD:}
    partnerId: ${LDAP_PARTNER_ID:}
    channelId: ${LDAP_CHANNEL_ID:}
    host: ${LDAP_HOST:}
    aesKey: ${LDAP_AES_KEY:}
```

**Notes:**
- `jwtSecret` uses env placeholder `${JWT_SECRET:dev-only-secret-change-me}` — NOT hardcoded literal (OQ-AR-3, §D-002).
- PostgreSQL datasource URL is the newmojf UAT DB (OQ-AR-2 RESOLVED v1.2). Credentials externalized via `${SPRING_DATASOURCE_*}`.
- `ddl-auto: none` per OQ-DM-3 (do NOT auto-create/drop tables on production schema).
- All 10 LDAP config keys use `${ENV_VAR:}` placeholders (values provided at deploy time).

### 3. AuthUserControllerTest.java
`src/test/java/com/coresystem/coresystembackend/controller/AuthUserControllerTest.java`

Use `@WebMvcTest(AuthUserController.class)` + `@MockBean` for LdapUcsService, JwtUtils, UserRepository.

```java
package com.coresystem.coresystembackend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.coresystem.coresystembackend.dto.JwtResponse;
import com.coresystem.coresystembackend.dto.LoginRequest;
import com.coresystem.coresystembackend.entity.Users;
import com.coresystem.coresystembackend.repository.UserRepository;
import com.coresystem.coresystembackend.security.JwtUtils;
import com.coresystem.coresystembackend.service.LdapUcsService;
import com.coresystem.coresystembackend.service.LdapUcsService.LdapAuthResult;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthUserController.class)
class AuthUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LdapUcsService ldapUcsService;

    @MockBean
    private JwtUtils jwtUtils;

    @MockBean
    private UserRepository userRepository;

    @Test
    void loginSuccess_returns200JwtResponse() throws Exception {
        // Given
        LdapAuthResult successResult = new LdapAuthResult("00", "Success");
        when(ldapUcsService.authLDAPNew("testuser", "testpass")).thenReturn(successResult);
        when(jwtUtils.generateTokenFromUname("testuser")).thenReturn("mock.jwt.token");

        Users user = new Users();
        user.setId(1L);
        user.setUname("testuser");
        user.setKodeMitra("MITRA01");
        user.setUrole(1L);
        when(userRepository.findByUname("testuser")).thenReturn(Optional.of(user));

        // When/Then
        mockMvc.perform(post("/api/auth/dologin")
                .contentType("application/json")
                .content("{\"uname\":\"testuser\",\"pass\":\"testpass\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("mock.jwt.token"))
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.uname").value("testuser"))
            .andExpect(jsonPath("$.mitKode").value("MITRA01"))
            .andExpect(jsonPath("$.urole").value("ROLE_1"));
    }

    @Test
    void ldapNull_returns400FailedToConnect() throws Exception {
        when(ldapUcsService.authLDAPNew(anyString(), anyString())).thenReturn(null);

        mockMvc.perform(post("/api/auth/dologin")
                .contentType("application/json")
                .content("{\"uname\":\"testuser\",\"pass\":\"testpass\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Failed to connect to LDAP service"));
    }

    @Test
    void badCredentials_returns400AuthenticationFailed() throws Exception {
        LdapAuthResult failResult = new LdapAuthResult("99", "Invalid credentials");
        when(ldapUcsService.authLDAPNew(anyString(), anyString())).thenReturn(failResult);

        mockMvc.perform(post("/api/auth/dologin")
                .contentType("application/json")
                .content("{\"uname\":\"testuser\",\"pass\":\"wrongpass\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Authentication failed"));
    }

    @Test
    void exception_returns401AuthenticationFailed() throws Exception {
        when(ldapUcsService.authLDAPNew(anyString(), anyString()))
            .thenThrow(new RuntimeException("Unexpected error"));

        mockMvc.perform(post("/api/auth/dologin")
                .contentType("application/json")
                .content("{\"uname\":\"testuser\",\"pass\":\"testpass\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Authentication failed"));
    }
}
```

## Hard rules (v1 bullet — preflight taken)
- **FILE_PRESENCE:** `src/main/java/com/coresystem/coresystembackend/controller/AuthUserController.java` MUST exist after bolt.
- **NAMING_RULE:** `controller/*.java` MUST follow PascalCase naming (`AuthUserController.java`).

## Anti-patterns (MUST honor)
- **§C-002/D-006:** DO NOT return the `Users` entity from the controller — use `JwtResponse` DTO.
- **§C-003:** DO NOT put LDAP/JWT/lookup logic inline — delegate to services/repository.
- **§C-004/D-005:** DO NOT use field `@Autowired` — constructor injection.
- **OQ-AR-3 / §D-002:** DO NOT hardcode `jwtSecret` literal — use `${JWT_SECRET:...}` placeholder.
- **§B-007 / OQ-FL-3:** DO NOT echo raw LDAP `responseDescription` — return generic "Authentication failed".
- **OQ-AR-7 / ADV-001:** DO NOT rename JwtResponse fields (`mitKode`, `urole`) — keep verbatim.
- NO `@CrossOrigin` (CORS via U-006 SecurityConfig).

## Target file whitelist
- `src/main/java/com/coresystem/coresystembackend/controller/AuthUserController.java` (create)
- `src/main/resources/application.yaml` (modify)
- `src/test/java/com/coresystem/coresystembackend/controller/AuthUserControllerTest.java` (create — required by acceptance_test)

## Provenance trailer (MANDATORY in AuthUserController.java)
```java
// SDD-PROVENANCE: U-008 | vault: .mega-sdd/vaults/jwt-login | POST /api/auth/dologin wiring LdapUcsService+JwtUtils+UserRepository → JwtResponse; application.yaml JWT+datasource+LDAP placeholders
```

## Execution protocol
1. Create `controller/` package (the file write will create it).
2. Write `AuthUserController.java` (constructor injection, `@PostMapping("/dologin")`, error handling per spec).
3. Modify `application.yaml` (add JWT + datasource + LDAP config with env placeholders).
4. Write `AuthUserControllerTest.java` (`@WebMvcTest` + `@MockBean`, 4 tests).
5. Run `mvn -q test -Dtest=AuthUserControllerTest` — must pass (BUILD SUCCESS, Tests run: 4, Failures: 0).
6. If fails: diagnose within whitelist. Common pitfalls: (a) JSON path assertions wrong field names (`mitKode` not `kodeMitra`), (b) Mock setup missing, (c) Spring context fail due to missing config. Fix + retry. Max 3.
7. Commit ALL files:
   ```
   feat(U-008): add AuthUserController (/api/auth/dologin) + application.yaml config

   POST /api/auth/dologin: LdapUcsService.authLDAPNew → JwtUtils.generateTokenFromUname
   → UserRepository.findByUname → JwtResponse. Null LDAP = 400, bad cred = 400,
   exception = 401. Constructor injection (no field @Autowired). No @CrossOrigin.

   application.yaml: coresystem.app.jwtSecret/jwtExpirationMs, spring.datasource
   (PostgreSQL placeholders), coresystem.ldap.* (10 env placeholders), ddl-auto: none.

   SDD-PROVENANCE: U-008 vault=.mega-sdd/vaults/jwt-login
   ```
   (no `--no-verify`, no push)
8. Report: commit SHA, sha256 of all files, verbatim test output, confidence, `bolt_self_report`.

## Halt conditions
- `mvn test -Dtest=AuthUserControllerTest` fails after 3 retries → halt `test_fail`.
- Field `@Autowired` present → halt `hard_rule_violated` (§C-004).
- `@CrossOrigin` present → halt `hard_rule_violated` (OQ-AR-5).
- Hardcoded `jwtSecret` literal → halt `hard_rule_violated` (§D-002).
- Raw `responseDescription` echoed to client → halt `hard_rule_violated` (§B-007).
- Out-of-whitelist write → halt (whitelist violation).

## Self-assessment (REQUIRED in your report)
`bolt_self_report` YAML block: numeric `confidence` (0.0–1.0), certain/uncertain decisions, retry_history.

Begin now. Spec complete — no questions. All integration contracts verified from existing code.