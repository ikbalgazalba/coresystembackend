# Dispatch Prompt — U-005 (JwtUtils, jjwt 0.12.x)

## Mission
Implement ONE mega-sdd unit: **U-005 — Create JwtUtils (jjwt 0.12.x API)**. Write `JwtUtils.java` + `JwtUtilsTest.java`, run the test, commit. Report DONE or HALT.

## Environment facts
- **Working dir:** `/home/ikbalgazalba/AI/Project/coresystembackend`
- **`./mvnw` is BROKEN** — use system `mvn`.
- Git branch: `main`. No hooks. gpgsign off.
- Base package: `com.coresystem.coresystembackend`. Security package: `com.coresystem.coresystembackend.security`.
- Deps on classpath (U-001): jjwt-api 0.12.6 (compile), jjwt-impl/jjwt-jackson 0.12.6 (runtime), spring-boot-starter-security, spring-boot-starter.
- Test deps: spring-boot-starter-test (JUnit 5 + Mockito).

## Unit spec (authoritative — `.mega-sdd/vaults/jwt-login/units/U-005.md`)
- task_type: create, depends_on: [U-001] (DONE), module: M-security, complexity: medium
- target_files: `src/main/java/com/coresystem/coresystembackend/security/JwtUtils.java` (create)
  - NOTE: the unit ALSO requires `JwtUtilsTest` (step 7) — create it at `src/test/java/com/coresystem/coresystembackend/security/JwtUtilsTest.java` (test files mirror main structure per §A-003). The test file is part of this bolt's deliverable (acceptance_test runs it).
- acceptance_test: `mvn -q test -Dtest=JwtUtilsTest` → passes
- binding_refs: [C-006, OQ-AR-3, OQ-AR-4, OQ-DC-1]

## Reference behavior (replicate BEHAVIOR, NOT the API)
`/home/ikbalgazalba/AI/Project/newmojf/src/main/java/com/bankmega/newmojf/security/jwt/JwtUtils.java`

newmojf uses the LEGACY jjwt API (removed in 0.11+):
- `signWith(SignatureAlgorithm.HS512, jwtSecret)` ← REMOVED
- `Jwts.parser().setSigningKey(jwtSecret).parseClaimsJws(token).getBody().getSubject()` ← REMOVED

You MUST rewrite to the 0.12.x API (below). Replicate the BEHAVIOR: HS512-signed JWT, subject=uname, exp from config, validate returning false on bad token.

## Required implementation (0.12.x API — from unit spec steps 3-6)

Create `JwtUtils.java`, `@Component`, package `com.coresystem.coresystembackend.security`:

1. SLF4J logger: `private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);` (no System.out, no Lombok).

2. Config injection via `@Value` (do NOT hardcode — §B-003, §D-002, OQ-AR-3):
   ```java
   @Value("${coresystem.app.jwtSecret}")
   private String jwtSecret;
   @Value("${coresystem.app.jwtExpirationMs}")
   private int jwtExpirationMs;
   ```
   (These config keys are defined in U-008's application.yaml. For the TEST, set them via @SpringBootTest properties or a test application.yaml so the test runs standalone.)

3. Signing key — jjwt 0.12.x needs a `Key`, not a String:
   ```java
   import io.jsonwebtoken.security.Keys;
   import io.jsonwebtoken.io.Decoders;
   private Key key() {
       // jwtSecret expected to be a Base64-encoded HMAC key.
       // Use BASE64 decode; fall back to raw bytes if not valid Base64.
       return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
   }
   ```
   **IMPORTANT — key length:** HS512 requires ≥64 bytes. The test must use a Base64-encoded secret of ≥64 raw bytes (e.g. generate a 64+ byte key). If `jwtSecret` is too short, `Keys.hmacShaKeyFor` throws. For the test, use a sufficiently long secret (e.g. a 512-bit/64-byte Base64 key). Do NOT downgrade to HS256 silently — if you choose HS256 instead, the spec step 3 says flag it; prefer HS512 with a proper-length key.

4. `generateTokenFromUname(String uname)` (PRESERVE this exact signature — Hard rule SIGNATURE_RULE):
   ```java
   public String generateTokenFromUname(String uname) {
       return Jwts.builder()
               .header().add("typ", "JWT").and()
               .subject(uname)
               .issuedAt(new Date())
               .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
               .signWith(key(), Jwts.SIG.HS512)
               .compact();
   }
   ```

5. `getUserNameFromJwt(String token)`:
   ```java
   public String getUserNameFromJwt(String token) {
       return Jwts.parser()
               .verifyWith(key())
               .build()
               .parseSignedClaims(token)
               .getPayload()
               .getSubject();
   }
   ```

6. `validateJwtToken(String authToken)`:
   ```java
   public boolean validateJwtToken(String authToken) {
       try {
           Jwts.parser().verifyWith(key()).build().parseSignedClaims(authToken);
           return true;
       } catch (JwtException e) {
           logger.error("Invalid JWT: {}", e.getMessage());
       } catch (IllegalArgumentException e) {
           logger.error("JWT claims string is empty: {}", e.getMessage());
       }
       return false;
   }
   ```
   Imports: `io.jsonwebtoken.JwtException`, `io.jsonwebtoken.Jwts`, `io.jsonwebtoken.security.Keys`, `io.jsonwebtoken.io.Decoders`, `java.security.Key`, `java.util.Date`, `org.slf4j.Logger`, `org.slf4j.LoggerFactory`, `org.springframework.beans.factory.annotation.Value`, `org.springframework.stereotype.Component`.

## JwtUtilsTest (step 7 — REQUIRED)
`src/test/java/com/coresystem/coresystembackend/security/JwtUtilsTest.java`, JUnit 5.

Approach: use `@SpringBootTest` with properties, OR construct JwtUtils directly with reflection/constructor-injection of the @Value fields. **Simplest robust approach:** make JwtUtils field-injected via @Value, and in the test use Spring's `ReflectionTestUtils.setField(jwtUtils, "jwtSecret", <base64-64-byte-key>)` + `setField(jwtUtils, "jwtExpirationMs", 900000)` after `new JwtUtils()`. This avoids needing the full Spring context / application.yaml.

Generate a test key: `java.util.Base64.getEncoder().encodeToString(new byte[64])` → a 64-byte zero key Base64-encoded (valid length for HS512). Or use a fixed 88-char Base64 string representing 64 bytes.

Test cases (spec acceptance):
- `generateTokenFromUname("testuser")` → non-empty token.
- `getUserNameFromJwt(token)` → "testuser".
- `validateJwtToken(token)` → true.
- `validateJwtToken(tamperedToken)` → false (tamper: append "x" or flip a char; must NOT throw to caller).

## Hard rules (v1 bullet — preflight taken)
- FILE_PRESENCE: `security/JwtUtils.java` MUST exist after bolt.
- SIGNATURE_RULE: `generateTokenFromUname` MUST preserve signature `(String uname) => String` (✓ — you're using exactly this).

## Anti-patterns (MUST honor)
- **OQ-AR-4:** NO `SignatureAlgorithm.HS512`, NO `signWith(SignatureAlgorithm, String)`, NO `Jwts.parser().setSigningKey(...)`. (0.12.x API only.)
- **§D-002 / OQ-AR-3:** NO hardcoded jwtSecret literal in source — read from config (@Value). (The TEST setting the field via ReflectionTestUtils is fine — that's test config, not production hardcoding.)
- **§A-004:** NO `System.out.println` — SLF4J LoggerFactory only.
- No Lombok.
- **§B-007:** (applies more to U-007/008; here just don't log raw secrets — you log e.getMessage() which is fine, it's a token error not a secret).

## Constitution clauses in force
- §B-003 (jwtSecret externalize), §D-002 (no hardcode), §A-004 (SLF4J not sysout), §C-004 (constructor injection — NOTE: @Value field injection is the newmojf pattern; for a @Component with only @Value strings, field injection is acceptable here, OR use constructor injection with @Value params — constructor injection preferred per §C-004. Use constructor injection: `public JwtUtils(@Value("${coresystem.app.jwtSecret}") String jwtSecret, @Value("${coresystem.app.jwtExpirationMs}") int jwtExpirationMs)` with final fields.).

## Target file whitelist
- `src/main/java/com/coresystem/coresystembackend/security/JwtUtils.java` (create)
- `src/test/java/com/coresystem/coresystembackend/security/JwtUtilsTest.java` (create — required by acceptance_test)
No other files. Do NOT create application.yaml (that's U-008). Do NOT touch pom.xml.

## Provenance trailer (MANDATORY in JwtUtils.java)
```java
// SDD-PROVENANCE: U-005 | vault: .mega-sdd/vaults/jwt-login | jjwt 0.12.x rewrite of newmojf JwtUtils (signWith(Key)+verifyWith)
```
(Test file may also carry a trailer but it's not strictly required; add one for consistency.)

## Execution protocol
1. Read newmojf JwtUtils (behavior reference).
2. Create JwtUtils.java (0.12.x API, constructor injection, provenance trailer).
3. Create JwtUtilsTest.java (ReflectionTestUtils to set fields, 4 test cases).
4. Run `mvn -q test -Dtest=JwtUtilsTest` — must pass (BUILD SUCCESS, tests run ≥4).
5. If fails: fix within whitelist, retry. Max 3.
6. Commit BOTH files:
   ```
   feat(U-005): add JwtUtils (jjwt 0.12.x API) + unit test

   Rewrites newmojf JwtUtils to jjwt 0.12.x (signWith(Key,HS512),
   parser().verifyWith(key)). subject=uname, validate returns false on
   bad token. jwtSecret externalized via @Value (OQ-AR-3). No Lombok.

   SDD-PROVENANCE: U-005 vault=.mega-sdd/vaults/jwt-login
   ```
   (no --no-verify, no push)
7. Report: commit SHA, sha256 of JwtUtils.java + JwtUtilsTest.java, test result (verbatim last lines), confidence.

## Halt conditions
- `mvn test -Dtest=JwtUtilsTest` fails after 3 retries → halt `test_fail` with error.
- Legacy jjwt API used (SignatureAlgorithm / setSigningKey) → halt `hard_rule_violated` (OQ-AR-4).
- Hardcoded jwtSecret literal → halt `hard_rule_violated` (§D-002).

## Self-assessment
bolt_self_report: confidence, certain/uncertain decisions, retry_history.

Begin now. Spec complete — no questions. If the test key length is uncertain, use a 64-byte Base64 key (valid for HS512).
