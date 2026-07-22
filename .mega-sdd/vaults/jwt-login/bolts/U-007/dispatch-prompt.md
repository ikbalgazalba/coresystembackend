# Dispatch Prompt — U-007 (LdapUcsService)

## Mission
Implement ONE mega-sdd unit: **U-007 — Create LdapUcsService** replicating newmojf `LDAP_UCS_Utils.authLDAPNew` behavior, adapted to constructor injection + Spring 7.x `RestClient`, WITHOUT the `disableSslVerification()` trust-all. Write `LdapUcsService.java` + `LdapUcsServiceTest.java` (mocked HTTP), run test, commit. Report DONE or HALT.

## Environment facts
- **Working dir:** `/home/ikbalgazalba/AI/Project/coresystembackend`
- **`./mvnw` is BROKEN** — use system `mvn`.
- Git branch: `main`. No hooks. gpgsign off.
- Base package: `com.coresystem.coresystembackend`. Service package: `com.coresystem.coresystembackend.service`.
- Deps on classpath (U-001): spring-boot-starter-web (RestClient, Jackson), spring-boot-starter-security.
- Test deps: spring-boot-starter-test (JUnit 5 + Mockito + MockMvc). NOTE: `MockRestServiceServer` requires `spring-test` (in starter-test) — available.

## Unit spec (authoritative — `.mega-sdd/vaults/jwt-login/units/U-007.md`)
- task_type: create, depends_on: [U-001] (DONE), module: M-integration, complexity: medium, **risk: high**
- P1 OQs RESOLVED v1.2 (OQ-AR-1, OQ-AR-6, OQ-FL-1) — unit NOT blocked.
- target_files: `src/main/java/com/coresystem/coresystembackend/service/LdapUcsService.java` (create)
  - ALSO create `src/test/java/com/coresystem/coresystembackend/service/LdapUcsServiceTest.java` (required by acceptance_test).
- acceptance_test: `mvn -q test -Dtest=LdapUcsServiceTest` → passes
- binding_refs: [C-009, OQ-AR-1, OQ-AR-6, OQ-FL-1, OQ-FL-3, OQ-CN-1]

## Reference source (replicate BEHAVIOR)
`/home/ikbalgazalba/AI/Project/newmojf/src/main/java/com/bankmega/newmojf/utils/LDAP_UCS_Utils.java`

READ it. Key behaviors to replicate (adapted):
- `authLDAPNew(uname, pass)` → returns `{responseCode, responseDescription}`.
- Flow: `getToken()` (OAuth2 password grant) → `getLdapProcess(uname, pass, token)` (GET verify-password).
- `getToken`: POST to `urlToken`, body `grant_type=password&username=...&password=...&client_id=...&client_secret=...`, Content-Type `application/x-www-form-urlencoded`, Host header = host. Extract `access_token` from JSON, return `"Bearer " + accessToken`.
- `getLdapProcess`: AES/ECB-encrypt password → `encryptPassword(pass)` (Base64). URL = `urlVerifyPassword + "userid/" + uname + "/password/" + hashedPassword`. Headers: Authorization=token, X-SIGNATURE, X-TIMESTAMP (ISO8601), X-PARTNER-ID, X-EXTERNAL-ID, CHANNEL-ID, Content-Type=application/json, Host=host. GET method. Parse JSON response → return it.
- `encryptPassword`: `AES/ECB/PKCS5Padding` with `aesKey`, Base64-encode result.
- `calculateSignature`: HMAC-SHA512 of `method:path:token:bodyHash(SHA-256 of "{}"):timestamp`, Base64. (stringToSign format `"%s:%s:%s:%s:%s"`, method, path, token(without "Bearer "), bodyHash 64-hex, timestamp).
- `generateIso8601Timestamp`: `yyyy-MM-dd'T'HH:mm:ssXXX`.

## ✅ Resolved config (OQ-AR-1, OQ-AR-6) — use these via @Value placeholders
- `urlToken` = `https://openapidev2.bankmega.local:15000/realms/quarkus/protocol/openid-connect/token`
- `urlVerifyPassword` = `https://openapidev2.bankmega.local:15000/openapi/v1.0/ldap/verifypassword/`
- Config fields (ALL externalized via `@Value("${coresystem.ldap.*}")`, NEVER hardcoded): `urlToken`, `urlVerifyPassword`, `clientId`, `clientSecret`, `username`, `password`, `partnerId`, `channelId`, `host`, `aesKey`.
- Endpoint `.bankmega.local` is internal-only. The TEST does NOT hit the real endpoint — it uses MockRestServiceServer.

## ⚠️ CRITICAL anti-patterns (MUST honor — these are constitution-grade)

1. **§B-006 (advisor ADV-006):** DO NOT replicate `disableSslVerification()` — NO trust-all X509TrustManager, NO empty `checkServerTrusted`, NO `allHostsValid` HostnameVerifier, NO `HttpsURLConnection.setDefaultSSLSocketFactory`. **If your code contains ANY of these → halt `hard_rule_violated`.** If the endpoint needs custom SSL, you'd raise an OQ — but for this unit, do NOT install any SSL bypass. Use a plain `RestClient` (default JVM SSL). The test mocks HTTP so SSL is irrelevant to the test.
2. **§B-007 (OQ-FL-3, ADV-005):** On exception, the `responseDescription` returned to the caller MUST be a GENERIC message (e.g. "Authentication failed" / "LDAP service error"), NOT `e.getMessage()`. Log the raw `e.getMessage()` server-side via SLF4J only. The TEST asserts the response body does NOT contain raw exception text.
3. **§D-002:** DO NOT hardcode AES key / client credentials / URLs in source. All via `@Value` placeholders. (The TEST setting values via test config / constructor is fine.)
4. **§C-003:** Business logic in `@Service`, not controller (this IS the service — good).
5. **§C-004:** Constructor injection (final fields), no field @Autowired.
6. **§A-004:** SLF4J LoggerFactory, no System.out.
7. No Lombok.

## Required implementation

Create `LdapUcsService.java`, `@Service`, package `com.coresystem.coresystembackend.service`:

- Define a result record: `public record LdapAuthResult(String responseCode, String responseDescription) {}` (or a static nested class — record is cleaner, Java 21 OK).
- Constructor-inject config via @Value + a `RestClient`:
  ```java
  @Service
  public class LdapUcsService {
      private static final Logger logger = LoggerFactory.getLogger(LdapUcsService.class);
      private final RestClient restClient;
      private final String urlToken, urlVerifyPassword, clientId, clientSecret, username, password, partnerId, channelId, host, aesKey;

      public LdapUcsService(
          @Value("${coresystem.ldap.urlToken}") String urlToken,
          @Value("${coresystem.ldap.urlVerifyPassword}") String urlVerifyPassword,
          @Value("${coresystem.ldap.clientId}") String clientId,
          @Value("${coresystem.ldap.clientSecret}") String clientSecret,
          @Value("${coresystem.ldap.username}") String username,
          @Value("${coresystem.ldap.password}") String password,
          @Value("${coresystem.ldap.partnerId}") String partnerId,
          @Value("${coresystem.ldap.channelId}") String channelId,
          @Value("${coresystem.ldap.host}") String host,
          @Value("${coresystem.ldap.aesKey}") String aesKey,
          RestClient.Builder restClientBuilder) {
          // assign all to final fields; this.restClient = restClientBuilder.build();
      }
  ```
  **For testability:** also provide a package-private/protected constructor OR a setter that accepts a pre-built `RestClient` so the test can inject a RestClient bound to MockRestServiceServer. Simplest: make `restClient` injectable — add a second constructor `LdapUcsService(...all @Value..., RestClient restClient)` for tests, OR use `@MockBean`/`RestClient.Builder` with MockRestServiceServer. **Recommended:** inject `RestClient.Builder` and build in the primary constructor; in the test, create the service via the primary constructor passing a `RestClient.Builder` configured with `MockRestServiceServer.bindTo(builder)`.

- `public LdapAuthResult authLDAPNew(String uname, String pass)`:
  - try: `String token = getToken();` if token null/empty → return `new LdapAuthResult("502", "LDAP token error")` (generic; log raw).
  - try: `LdapAuthResult result = getLdapProcess(uname, pass, token);` return it.
  - catch (Exception e): log raw `logger.error("LDAP auth failed: {}", e.getMessage())`; return `new LdapAuthResult("401", "Authentication failed")` (GENERIC — §B-007).

- `private String getToken()`: POST urlToken, form-urlencoded body, extract access_token, return "Bearer "+token. On exception → return null (log raw). Use RestClient `.post().uri(urlToken).header("Host", host).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(formBody).retrieve().body(String.class)` then parse JSON (use Jackson `ObjectMapper` or Spring's JsonParser — NOT json-simple). Extract `access_token`.

- `private LdapAuthResult getLdapProcess(String uname, String pass, String token)`: encrypt password, build URL, calc signature + timestamp, GET with headers, parse JSON response into LdapAuthResult. Map the upstream responseCode/responseDescription. On exception → return `new LdapAuthResult("401", "Authentication failed")` (generic).

- `private String encryptPassword(String password)`: AES/ECB/PKCS5Padding with aesKey, Base64. Replicate reference `:267-279`.
- `private String calculateSignature(String bearer, String body, String url, String method, String timestamp)`: HMAC-SHA512, replicate `:286-319`.
- `private String generateIso8601Timestamp()`: `yyyy-MM-dd'T'HH:mm:ssXXX`.

**JSON parsing:** use `com.fasterxml.jackson.databind.ObjectMapper` (Jackson, on classpath) — NOT `org.json.simple` (newmojf used json-simple, a legacy dep NOT in our pom — D-001: do NOT add json-simple; use Jackson).

## LdapUcsServiceTest (REQUIRED)
`src/test/java/com/coresystem/coresystembackend/service/LdapUcsServiceTest.java`, JUnit 5 + MockRestServiceServer.

Approach:
- Build `RestClient.Builder` bound to `MockRestServiceServer.createServer(builder)` (MockRestServiceServer.bindTo(builder)).
- Construct LdapUcsService via primary constructor with test @Value strings + the bound builder.
- Mock the token endpoint to return `{"access_token":"fake-token","token_type":"Bearer"}`.
- Mock the verify-password endpoint to return `{"responseCode":"00","responseDescription":"Success"}`.
- Test cases (spec acceptance step 6):
  1. **Success path:** mock verify returns responseCode "00" → `authLDAPNew` returns LdapAuthResult with responseCode "00".
  2. **Bad-cred path:** mock verify returns responseCode "99" → returns result with "99" + description.
  3. **Exception path:** mock token endpoint to throw / 500 → `authLDAPNew` returns responseCode "401" with GENERIC description, AND assert the responseDescription does NOT contain the raw exception text (§B-007). Use `assertThat(result.responseDescription()).doesNotContain(...)` for a known raw fragment.
- Note on success codes "00"/"01": per OQ-FL-1 these are [INFERRED] — the test asserts the SERVICE maps whatever the upstream returns (don't hardcode that "00" is the only success; just verify the mapping passes through the upstream responseCode). The service should pass through the upstream responseCode/responseDescription on success.

## Hard rules (v1 bullet — preflight taken)
- FILE_PRESENCE: `service/LdapUcsService.java` MUST exist after bolt.
- NAMING_RULE: `service/*.java` PascalCase → `LdapUcsService` (✓).

## Target file whitelist
- `src/main/java/com/coresystem/coresystembackend/service/LdapUcsService.java` (create)
- `src/test/java/com/coresystem/coresystembackend/service/LdapUcsServiceTest.java` (create — required by acceptance_test)
No other files. Do NOT create application.yaml (U-008). Do NOT add json-simple to pom.

## Provenance trailer (MANDATORY in LdapUcsService.java)
```java
// SDD-PROVENANCE: U-007 | vault: .mega-sdd/vaults/jwt-login | replicates newmojf LDAP_UCS_Utils.authLDAPNew (RestClient, no disableSslVerification, generic error §B-007)
```

## Execution protocol
1. Read newmojf `LDAP_UCS_Utils.java` (behavior reference).
2. Create LdapUcsService.java (RestClient, constructor injection, AES+HMAC, generic errors, provenance trailer). NO disableSslVerification.
3. Create LdapUcsServiceTest.java (MockRestServiceServer, 3 test cases incl. §B-007 no-raw-exception assertion).
4. Run `mvn -q test -Dtest=LdapUcsServiceTest` — must pass.
5. If fails: fix within whitelist, retry. Max 3.
6. Commit BOTH files:
   ```
   feat(U-007): add LdapUcsService (LDAP UCS auth via RestClient)

   Replicates newmojf LDAP_UCS_Utils.authLDAPNew: OAuth2 token grant +
   AES/ECB password + HMAC-SHA512 sig + GET verify-password. RestClient
   (Spring 7.x). NO disableSslVerification (§B-006). Generic error to
   client, raw logged (§B-007). Config externalized (§D-002). No Lombok.

   SDD-PROVENANCE: U-007 vault=.mega-sdd/vaults/jwt-login
   ```
   (no --no-verify, no push)
7. Report: commit SHA, sha256 of both files, test result (verbatim last lines), confidence.

## Halt conditions
- `mvn test -Dtest=LdapUcsServiceTest` fails after 3 retries → halt `test_fail` with error.
- `disableSslVerification` / trust-all / empty X509TrustManager / allHostsValid present → halt `hard_rule_violated` (§B-006).
- `e.getMessage()` echoed to client responseDescription → halt `hard_rule_violated` (§B-007).
- Hardcoded AES key/creds/URLs → halt `hard_rule_violated` (§D-002).

## Self-assessment
bolt_self_report: confidence, certain/uncertain decisions, retry_history.

Begin now. This is the highest-risk unit — be careful with §B-006 (no SSL bypass) and §B-007 (generic errors). The test MUST assert no raw exception leakage. Spec complete — no questions.
