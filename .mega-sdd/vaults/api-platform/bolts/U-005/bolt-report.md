---
unit: U-005
vault: .mega-sdd/vaults/api-platform
status: completed
commit_sha: f0b518b
branch: feat/api-platform-swagger-docker
target_files:
  - path: src/main/resources/application-dev.yaml
    operation: create
  - path: src/main/resources/application-prod.yaml
    operation: create
postflight: pass
acceptance_test_result: PASS (contextLoads x3: no-profile, dev, prod)
bolt_self_report:
  confidence: 0.96
  certain_decisions:
    - "CORS key coresystem.cors.allowed-origins matches U-002's @Value reader"
    - "prod swagger-ui.enabled=false (OQ-AP-5 ACCEPT); dev=true (intent clarity)"
    - "both profiles management.endpoints.web.exposure.include=health (OQ-AP-8)"
    - "prod server.port ${SERVER_PORT:8080} (OQ-AP-2); CORS ${CORS_ALLOWED_ORIGINS:localhost-default}"
  uncertain_decisions:
    - "prod CORS uses localhost fallback default (not fail-fast) per prompt directive — non-secret config key, acceptable; aligns with SecurityConfig @Value already having the same default"
  retry_history: []
---

# Bolt Report — U-005

## Summary
Created `application-dev.yaml` + `application-prod.yaml` overriding ONLY profile-specific keys (CORS origins, swagger-ui toggle, actuator exposure, prod port). Base `application.yaml` untouched (HR-4). Committed `f0b518b`.

## Acceptance tests (all PASS)
- No profile: contextLoads exit 0 (base behavior preserved; actuator endpoint exposed).
- `SPRING_PROFILES_ACTIVE=dev`: contextLoads exit 0 (dev profile active).
- `SPRING_PROFILES_ACTIVE=prod` (CORS_ALLOWED_ORIGINS unset): contextLoads exit 0 (default kicked in, no crash).

## Post-flight Hard rules
All 7 PASS: both YAMLs exist; application.yaml + pom.xml untouched; HR-4 (base fail-fast preserved); OQ-AP-5 (prod UI off); OQ-AP-8 (health-only both profiles).

## Concerns
- prod CORS localhost fallback (not fail-fast) — non-secret, aligns with SecurityConfig @Value default. Acceptable.
- dev `swagger-ui.enabled: true` is springdoc's default (no-op) but kept per unit for intent clarity.
