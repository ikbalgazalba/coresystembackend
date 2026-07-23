# Bolt Report — U-009 (AuthLoginIntegrationTest)

## bolt_self_report
- **confidence:** 0.90
- **status:** DONE

## target_hashes (sha256)
- `src/test/java/com/coresystem/coresystembackend/AuthLoginIntegrationTest.java`: `662dbb1cc3cab0711b1e58b7c7f3bcc29ad70fdfec315ad867635a2dd2e8911e`

## acceptance_test
`mvn -q test -Dtest=AuthLoginIntegrationTest`
```
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.458 s -- in com.coresystem.coresystembackend.AuthLoginIntegrationTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## what was implemented
- `AuthLoginIntegrationTest.java` — `@SpringBootTest` + manual MockMvc + `@MockitoBean` for LdapUcsService/JwtUtils/UserRepository.
- DataSource/JPA auto-configuration excluded via `@ImportAutoConfiguration` (Boot 4.1.1 repackaged paths).
- 4 test cases:
  - `loginSuccess_returnsJwtResponse` — 200 + JwtResponse with verbatim `mitKode`/`urole` (ADV-001 guard)
  - `loginBadCredentials_returns400` — 400 + `.message` exists
  - `loginLdapNull_returns400FailedToConnect` — 400 "Failed to connect to LDAP service"
  - `loginException_returns401` — 401 "Authentication failed" + no raw exception text leak (§B-007 guard)

## hard rules honored
- FILE_PRESENCE: AuthLoginIntegrationTest.java exists after bolt.
- Whitelist: only the test file created; no source modifications.
- §B-007: exception test explicitly asserts no raw leak (`content().string(not(containsString("internal detail")))`).
- ADV-001: success test asserts `$.mitKode` and `$.urole` (verbatim, not `kodeMitra`/`role`).

## notes / deviation from dispatch skeleton
- Test uses `@SpringBootTest` + manual MockMvc instead of `@WebMvcTest` + `@AutoConfigureMockMvc` (Boot 4.1.1 removed both from spring-boot-test-autoconfigure).
- DataSource/Hibernate exclusion uses Boot 4.1.1 repackaged packages (`org.springframework.boot.jdbc.autoconfigure` / `org.springframework.boot.hibernate.autoconfigure`) — not the Boot 3.x paths.

## retry_history
1. Compile failed: Boot 3.x auto-config import paths (`org.springframework.boot.autoconfigure.jdbc` / `org.springframework.boot.autoconfigure.orm.jpa`) don't exist in 4.1.1.
2. Fix: changed to Boot 4.1.1 paths. BUILD SUCCESS, 4/4 pass.

## Review panel
(U-009 is a test-only unit with low risk — no review panel dispatched. Controller verified by U-008 panel.)
