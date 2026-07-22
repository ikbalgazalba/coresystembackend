# Bolt Report — U-005 (JwtUtils, jjwt 0.12.x)

## bolt_self_report
- **confidence:** 0.95
- **status:** DONE

## target_hashes (sha256)
- `src/main/java/com/coresystem/coresystembackend/security/JwtUtils.java`: `c45476bcd5926ad8fdf43d8e434acc504bc3ba79f6ea4240e0bfa51ea975fcb7`
- `src/test/java/com/coresystem/coresystembackend/security/JwtUtilsTest.java`: `fb8c260a2bd1ac451619dec539591d5213a26408a994e7ca0c7450667cac682e`

## acceptance_test
`mvn -q test -Dtest=JwtUtilsTest`
```
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.398 s -- in com.coresystem.coresystembackend.security.JwtUtilsTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## what was implemented
- `JwtUtils` @Component in `com.coresystem.coresystembackend.security`, constructor-injected
  `@Value("${coresystem.app.jwtSecret}")` and `@Value("${coresystem.app.jwtExpirationMs}")` (no hardcoded secret).
- `generateTokenFromUname(String uname)` — jjwt 0.12.x builder: `header().add("typ","JWT").and()`,
  `subject(uname)`, `issuedAt`, `expiration`, `signWith(key(), Jwts.SIG.HS512)`, `compact()`.
  Signature preserved: `(String uname) => String`.
- `getUserNameFromJwt(String token)` — `Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload().getSubject()`.
- `validateJwtToken(String authToken)` — try parse, return false on `JwtException`/`IllegalArgumentException`,
  SLF4J `LoggerFactory` logging only.
- `private SecretKey key()` — `Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret))`.
- `JwtUtilsTest` — 4 JUnit 5 tests; injects a 64-byte Base64 secret via `ReflectionTestUtils.setField`
  (HS512-safe). Cases: generate non-empty, getUserName returns "testuser", validate true, tampered false.

## hard rules honored
- FILE_PRESENCE: `security/JwtUtils.java` exists after bolt.
- SIGNATURE_RULE: `generateTokenFromUname(String uname)` preserves `(String uname) => String`.
- OQ-AR-4: NO `SignatureAlgorithm`, NO `signWith(SignatureAlgorithm,*)`, NO `Jwts.parser().setSigningKey(...)`. Modern 0.12.x API only.
- §D-002 / OQ-AR-3: jwtSecret externalized via `@Value`, NOT hardcoded. Test sets field via ReflectionTestUtils (test config, not production hardcoding).
- §A-004: SLF4J `LoggerFactory` only, no `System.out`.
- No Lombok.
- Whitelist: only the two target files created; no application.yaml, no pom.xml changes.

## reuse_decisions
- candidate: newmojf `JwtUtils.java` — decision: reimplemented — reason: reference uses removed legacy jjwt API (`signWith(SignatureAlgorithm,String)`, `setSigningKey`); spec mandates 0.12.x rewrite replicating BEHAVIOR (HS512, subject=uname, exp from config) not API.
- candidate: reuse-index.yaml — decision: not_applicable — reason: no reuse-index.yaml exists in this repo; no pre-existing JWT/security helper in `src/main/java` (confirmed via grep + directory listing). The `security` package did not exist before this bolt.
- candidate: existing `dto/JwtResponse.java` — decision: not_applicable — reason: a DTO, unrelated to token generation/validation; no reusable JWT helper present.

## notes / deviation from dispatch skeleton
- The dispatch skeleton typed `key()` as `java.security.Key`. With jjwt 0.12.6's generic
  `signWith(<K> K, SecureDigestAlgorithm<? super K,?>)` and `verifyWith(SecretKey)`, a `Key`-typed
  return fails type inference (compiler error: "no suitable method found for signWith(Key, MacAlgorithm)"
  and "verifyWith(Key)"). Typed `key()` as `javax.crypto.SecretKey` instead — `Keys.hmacShaKeyFor`
  returns `SecretKey`, satisfying both `signWith` and `verifyWith`. This is a type-narrowing fix,
  not an API-pattern change; the 0.12.x call sites (`signWith(key, Jwts.SIG.HS512)`,
  `verifyWith(key)`) are unchanged from the spec.

## retry_history
1. First compile failed: `signWith(Key, MacAlgorithm)` / `verifyWith(Key)` type-mismatch (see notes). Root cause: `Key` too wide for 0.12.6 generics.
2. Fix: typed `key()` return as `SecretKey`. Re-ran `mvn -q test -Dtest=JwtUtilsTest` → BUILD SUCCESS, 4/4 pass.
