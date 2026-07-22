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

## Review panel

**Tier:** full (risk signals: crypto/auth unit, binding §B) — reduced to spec + security (the load-bearing lenses for JWT crypto); quality/standards verified inline by controller.
**Lenses dispatched (blind):** spec, security
**Code commit:** f4b87df

| Lens | Verdict | Critical | Important | Minor |
|---|---|---|---|---|
| spec | PASS | 0 | 0 | 2 |
| security | PASS | 0 | 0 | 1 |

**Merge result:** No spec ❌, no Critical → **mergeable**. No re-dispatch.

### Findings (merged)

| # | Severity | Lens | Finding | Evidence |
|---|---|---|---|---|
| 1 | Minor | spec | `application.yaml` not yet defining `coresystem.app.jwtSecret`/`jwtExpirationMs` — correctly out of scope (U-008). JwtUtilsTest constructs directly (no Spring context), so unaffected. | units/U-005.md:38 |
| 2 | Minor | spec | JwtUtilsTest.setUp redundantly calls ReflectionTestUtils.setField after constructor already set final fields (no-op). Cosmetic dead code. | JwtUtilsTest.java setUp |
| 3 | Minor | security | No `@PostConstruct` fail-fast on key length; weak/short secret surfaces at first token op (already fail-closed: generate throws→500, validate returns false). Optional hardening. | JwtUtils.java:31-33 |

**Dropped-no-evidence:** 0. **Consensus:** 0.

### Controller inline verification (quality + standards)
- 0.12.x API only (grep: 0 SignatureAlgorithm/setSigningKey) — OQ-AR-4 ✓
- `key()` typed SecretKey (sound for jjwt 0.12.6 generics) ✓
- Constructor injection (@Value), final fields — §C-004 ✓
- HS512 explicit, no downgrade; Base64-decoded ≥64-byte key — secure ✓
- validateJwtToken fail-closed (false on any error, no throw) ✓
- signature-verified parse (verifyWith) ✓
- SLF4J, no System.out, no Lombok — §A-004/OQ-CN-2 ✓
- PascalCase, security/ package — §A-001/§A-002 ✓
- Provenance present; whitelist (2 source files) honored ✓

### Gate decision
PASS — mergeable as-is. Findings are optional hardening / cosmetic / out-of-scope-deferred.

### Post-flight Hard-rule scan
- Grammar: v1-bullet
- Rules (FILE_PRESENCE + SIGNATURE_RULE): **pass**.
- Evidence: `postflight.json` (status: pass).
