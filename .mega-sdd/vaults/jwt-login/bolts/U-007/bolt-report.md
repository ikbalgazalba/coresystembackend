# Bolt Report — U-007 (LdapUcsService)

## bolt_self_report
- **confidence:** 0.9
- **status:** DONE

## target_hashes (sha256)
- `src/main/java/com/coresystem/coresystembackend/service/LdapUcsService.java`: `16b498e22c77870a7c5954f5de8080fe449496961313336efa49228b41057433`
- `src/test/java/com/coresystem/coresystembackend/service/LdapUcsServiceTest.java`: `74c2631df73a096b7f934fa1ee0beeae5fbccf0d20b671690c4a261a531cbd44`

## acceptance_test
`mvn -q test -Dtest=LdapUcsServiceTest` (run via system `mvn` — `./mvnw` is broken)
```
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.394 s -- in com.coresystem.coresystembackend.service.LdapUcsServiceTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## what was implemented
- `LdapUcsService` @Service in `com.coresystem.coresystembackend.service`, constructor-injected
  (final fields, no field @Autowired — §C-004). All config externalized via
  `@Value("${coresystem.ldap.*}")` placeholders: `urlToken`, `urlVerifyPassword`, `clientId`,
  `clientSecret`, `username`, `password`, `partnerId`, `channelId`, `host`, `aesKey` (§D-002).
  A `RestClient.Builder` is also constructor-injected and `.build()`-ed into the `RestClient`
  field, so `MockRestServiceServer.bindTo(builder)` in the test drives the real HTTP code path.
- `public record LdapAuthResult(String responseCode, String responseDescription) {}` — mirrors
  the upstream `{responseCode, responseDescription}` contract (OQ-FL-1).
- `public LdapAuthResult authLDAPNew(String uname, String pass)`:
  - `getToken()` (OAuth2 password grant, POST `urlToken`, form-urlencoded body, `Host` header).
    On failure returns null (raw logged). Jackson `ObjectMapper.readTree` extracts `access_token`;
    returns `"Bearer " + accessToken`.
  - If token null/empty → returns generic `LdapAuthResult("401", "Authentication failed")`
    (raw logged server-side — §B-007; uniform 401 avoids leaking token-endpoint state).
  - Otherwise `getLdapProcess(uname, pass, token)`: AES/ECB/PKCS5Padding-encrypt password
    (`encryptPassword` replicates reference :267-279), build URL
    `urlVerifyPassword + "userid/" + uname + "/password/" + hashedPassword`, compute
    HMAC-SHA512 `X-SIGNATURE` (`calculateSignature` replicates :286-319:
    `method:path:token:sha256(body):timestamp`, Base64), set headers (Authorization,
    X-SIGNATURE, X-TIMESTAMP ISO-8601, X-PARTNER-ID, X-EXTERNAL-ID, CHANNEL-ID, Content-Type,
    Host), GET via RestClient, parse JSON → pass through upstream responseCode/description.
  - On any exception → `LdapAuthResult("401", "Authentication failed")` GENERIC (§B-007);
    raw `e.getMessage()` logged via SLF4J only.
- `parseLdapResponse`: Jackson parse; falls back to generic 401 on empty body / missing fields
  / parse error (never echoes raw upstream content).
- SLF4J `LoggerFactory` only (§A-004), no System.out, no Lombok.
- NO `disableSslVerification()` / trust-all / X509TrustManager / HostnameVerifier / SSLContext
  / setDefaultSSLSocketFactory anywhere (§B-006). Plain RestClient, default JVM SSL.
- Provenance trailer present in `LdapUcsService.java`.

## LdapUcsServiceTest (3 cases, JUnit 5 + MockRestServiceServer)
1. `authLDAPNew_success_passesThroughUpstreamResponseCode` — token endpoint → fake-token;
   verify endpoint → `{"responseCode":"00","responseDescription":"Success"}` → asserts result
   `responseCode="00"`, `responseDescription="Success"`; `mockServer.verify()` confirms both
   calls fired (POST token + GET verify).
2. `authLDAPNew_badCredentials_passesThroughUpstreamFailureCode` — verify endpoint returns
   `responseCode="99"` + description → asserts passthrough (does NOT assume "00" only success,
   per OQ-FL-1 [INFERRED]).
3. `authLDAPNew_tokenEndpointError_returnsGeneric401AndDoesNotLeakRawException` — token endpoint
   returns 500 → asserts `responseCode="401"` AND `responseDescription` does NOT contain the
   raw endpoint host fragment, "Exception", or "Connection" (§B-007 no-leak assertion).

## hard rules honored
- FILE_PRESENCE: `service/LdapUcsService.java` exists after bolt.
- NAMING_RULE: `service/LdapUcsService.java` PascalCase.
- §B-006: NO `disableSslVerification` / trust-all X509TrustManager / empty `checkServerTrusted`
  / `allHostsValid` HostnameVerifier / `setDefaultSSLSocketFactory` / `SSLContext` / `TrustManager`
  imports or usage. Verified by grep — only the Javadoc + provenance comment mention the name
  (describing its absence).
- §B-007 / OQ-FL-3: every exception path returns the GENERIC literal "Authentication failed";
  raw `e.getMessage()` is logged via SLF4J only. Test asserts no raw exception text
  (host fragment, "Exception", "Connection") in responseDescription.
- §D-002: all URLs / creds / aesKey via `@Value` placeholders; none hardcoded. Test sets values
  via constructor args (test config, not production hardcoding).
- §C-003: business logic in @Service.
- §C-004: constructor injection, final fields, no field @Autowired.
- §A-004: SLF4J LoggerFactory, no System.out.
- No Lombok.
- Whitelist: only `LdapUcsService.java` + `LdapUcsServiceTest.java` created in src. No
  application.yaml (U-008), no pom.xml changes, no json-simple added.
- D-001: did NOT add json-simple; used Jackson (already on classpath via spring-boot-starter-jackson).

## reuse_decisions
- candidate: newmojf `LDAP_UCS_Utils.java` — decision: reimplemented — reason: reference uses
  `HttpURLConnection` + `org.json.simple` (json-simple NOT in our pom, D-001 forbids adding it)
  + a `disableSslVerification()` trust-all (§B-006 forbids replicating) + echoes
  `e.getMessage()` to the client (§B-007 forbids). Spec mandates RestClient (Spring 7.x) +
  Jackson + constructor injection + generic errors. BEHAVIOR replicated (OAuth2 password-grant
  token, AES/ECB/PKCS5Padding password encrypt, HMAC-SHA512 sig over
  `method:path:token:sha256(body):timestamp`, GET verify-password path-param layout, ISO-8601
  timestamp `yyyy-MM-dd'T'HH:mm:ssXXX`), API/anti-patterns NOT replicated.
- candidate: reuse-index.yaml — decision: not_applicable — reason: no reuse-index.yaml exists in
  this repo (confirmed via find under .mega-sdd); no pre-existing HTTP/crypto/JSON helper in
  `src/main/java` (codebase-map §2 confirms only the bootstrap class + U-001/U-005 artifacts
  exist; the `service` package did not exist before this bolt). AES/HMAC use JDK
  `javax.crypto` (standard library — reuse-first rung #2); JSON parse uses Jackson
  `ObjectMapper` (already-installed dep — rung #4); HTTP uses Spring `RestClient` (native
  framework feature — rung #3). No new dependency added.

## notes / deviation from dispatch skeleton
- **Jackson 3.x (`tools.jackson.databind`) instead of `com.fasterxml.jackson.databind`.**
  Spring Boot 4.1.1-SNAPSHOT ships `spring-boot-starter-jackson` → `tools.jackson.core:jackson-databind`
  3.1.5 at **compile** scope (Jackson 3.x migrated to the `tools.jackson` package). The legacy
  `com.fasterxml.jackson.core:jackson-databind` 2.21.5 is pulled in only at **runtime** scope
  (transitively via jjwt-jackson), so `com.fasterxml.jackson.databind.ObjectMapper` does NOT
  compile. Used `tools.jackson.databind.ObjectMapper` / `JsonNode` instead — identical API
  (`readTree(String)`, `get(String)`, `asText()`, `isNull()`). `JacksonException` is unchecked
  (extends RuntimeException) in 3.x, so the existing `catch (Exception)` wrappers are sufficient.
  This is a package-only substitution forced by the Spring Boot 4.1 / Jackson 3 classpath; the
  dispatch prompt's intent (Jackson, NOT json-simple) is honored.
- **Token-failure responseCode = "401" (not "502").** The dispatch skeleton suggested
  `502` for a null token, but the acceptance test case 3 (token endpoint 500) explicitly
  asserts `responseCode="401"`. Aligned the token-failure path to return the generic
  `401 "Authentication failed"` — this also strengthens §B-007: a uniform `401` reveals nothing
  about internal token-endpoint state (a distinct `502` would be an information signal). Raw
  detail still logged server-side. The dispatch skeleton's `502` would have failed the
  acceptance test; the test is the binding criterion.
- `ObjectMapper` is a static final field (thread-safe per Jackson spec). `SimpleDateFormat` is
  constructed per-call (not shared across threads — SimpleDateFormat is not thread-safe),
  matching the reference pattern.
- `X-EXTERNAL-ID` uses `System.currentTimeMillis()` substring(0,12) replicating the reference
  refNum (reference :210-211).

## retry_history
1. First compile failed: `package com.fasterxml.jackson.databind does not exist` — root cause:
   Spring Boot 4.1.1 brings Jackson 3.x (`tools.jackson.databind`) at compile scope; the legacy
   `com.fasterxml` artifact is runtime-only. Fix: switched imports to `tools.jackson.databind`.
2. Second run: 2/3 tests passed; case 3 failed — `expected "401" but was "502"` (token endpoint
   500 → getToken() returned null → authLDAPNew returned the skeleton's `502`). Fix: aligned
   token-failure path to generic `401` (see notes). Re-ran → 3/3 pass, BUILD SUCCESS.

## security self-check (§B-006 / §B-007)
- grep for `disableSslVerification|X509TrustManager|checkServerTrusted|allHostsValid|HostnameVerifier|setDefaultSSLSocketFactory|SSLContext|TrustManager`
  in LdapUcsService.java → matches ONLY in Javadoc/provenance comment text (describing absence);
  zero imports of `javax.net.ssl` / `java.security.cert.X509Certificate` / `TrustManager`.
- grep for `responseDescription.*e.getMessage` / `e.getMessage.*responseDescription` / `put("responseDescription"...)`
  → no matches. Every exception branch returns the literal "Authentication failed".
- grep for `bankmega|openapidev2|"https?://` in source → no matches (all via @Value).
