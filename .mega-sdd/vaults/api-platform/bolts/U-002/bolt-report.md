---
unit: U-002
vault: .mega-sdd/vaults/api-platform
status: completed
commit_sha: 1d65f4c
branch: feat/api-platform-swagger-docker
target_files:
  - path: pom.xml
    operation: modify
  - path: src/main/java/com/coresystem/coresystembackend/config/SecurityConfig.java
    operation: modify
postflight: pass
acceptance_test_result: PASS (SecurityConfigTest 2/2 + contextLoads 1/1)
bolt_self_report:
  confidence: 0.95
  certain_decisions:
    - "constructor-injected @Value corsAllowedOrigins (pack idiom; final field)"
    - "additive permitAll matchers before anyRequest().authenticated() (matcher order)"
    - "CORS default localhost in @Value (non-secret dev convenience) so context-loads passes without env"
  uncertain_decisions:
    - "SecurityConfigTest does not assert the exact matcher set, so new permitAll paths are covered only indirectly by contextLoads + manual /actuator/health — no automated assertion for the new matchers (test file outside target_files; follow-up unit would own explicit coverage)"
    - "health-only actuator exposure (management.endpoints.web.exposure.include=health) deferred to U-005 per unit step 4"
  retry_history: []
---

# Bolt Report — U-002

## Summary
Added `spring-boot-starter-actuator` + additive permitAll matchers (`/actuator/health/**`, `/v3/api-docs`, `/swagger-ui.html`, `/swagger-ui/**`) to SecurityConfig before `anyRequest().authenticated()`, and externalized CORS origins to `${coresystem.cors.allowed-origins}` (constructor-injected `@Value`, dev default). Committed `1d65f4c`.

## What changed
- `pom.xml` (+6): `spring-boot-starter-actuator` (BOM-managed), grouped with starters.
- `SecurityConfig.java` (+35/-2): `@Value` final field + constructor; additive matchers; `corsConfigurationSource` reads split origins; U-002 provenance trailer (kept existing U-006 trailer).

## Acceptance tests
- `./mvnw test -Dtest=SecurityConfigTest` → PASS (2/2). Non-regressing (green at baseline too).
- `./mvnw test -Dtest=CoresystembackendApplicationTests#contextLoads` → PASS (1/1). Actuator on classpath; CORS placeholder resolves with localhost default.

## Post-flight Hard rules
All 8 PASS (postflight.json): 2 DO_NOT_MODIFY; filterChain signature preserved; SecurityConfig exists; 1 actuator dep; HR-5 additive (existing authz intact); HR-8 (no hardcoded origin in cors bean body); new matchers present.

## Concerns
- SecurityConfigTest does not assert exact matcher set → new permitAll paths covered indirectly only (contextLoads + manual). A follow-up unit owning SecurityConfigTest.java could add explicit assertions. Non-blocking.
- Health-only actuator exposure deferred to U-005 (profile YAML sets `management.endpoints.web.exposure.include=health`).
