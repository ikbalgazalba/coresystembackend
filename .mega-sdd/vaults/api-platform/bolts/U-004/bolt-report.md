---
unit: U-004
vault: .mega-sdd/vaults/api-platform
status: completed
commit_sha: 0a3d772
branch: feat/api-platform-swagger-docker
target_files:
  - path: docker-compose.yml
    operation: create
  - path: .dockerignore
    operation: create
postflight: pass
acceptance_test_result: PASS (docker compose config -q exit 0); manual `compose up` deferred to operator (needs .env secrets + network)
bolt_self_report:
  confidence: 0.94
  certain_decisions:
    - "env_file: .env + trust-store :ro volume mount (HR-1, §D-002, §B-006)"
    - "no postgres service (OQ-AP-4 env-only to existing newmojf UAT 10.95.1.43)"
    - "no resource limits (OQ-AP-6 fallback)"
    - "SPRING_PROFILES_ACTIVE=prod; CORS_ALLOWED_ORIGINS + JAVA_OPTS trust-store flags wired"
    - "trust-store mount target /opt/bankmega-truststore/bankmega-truststore.p12 (OQ-AP-3, from U-003)"
  uncertain_decisions:
    - "healthcheck uses CMD-SHELL java -version (liveness-only baseline) because eclipse-temurin:21-jre lacks curl/wget (U-003 shipped none). java -version ≠ app/actuator health. Follow-up: replace with curl /actuator/health once curl is added to the runtime image. Documented in compose comment + commit."
  retry_history: []
---

# Bolt Report — U-004

## Summary
Created `docker-compose.yml` (app service: build from U-003 Dockerfile, env_file, trust-store volume mount, prod profile, CORS+JAVA_OPTS env, port 8080, java-probe healthcheck) + `.dockerignore` (excludes secrets/build-noise, keeps build-relevant files). Committed `0a3d772`.

## Acceptance tests
- `docker compose config -q` → exit 0 (valid syntax; no image pull, so Docker Hub auth flakiness not a factor).
- `.dockerignore` excludes `.env`/`target/`/`.git/`/`.mega-sdd/`. Compose has env_file + `:ro` volume + no postgres. All structural checks PASS.
- Manual `docker compose up -d --build && curl /actuator/health` — out of scope (operator-run; needs real .env secrets + network).

## Post-flight Hard rules
All 7 PASS: both files exist; pom + Dockerfile untouched; HR-1 (env_file + volume, no baked secret); OQ-AP-4 (no postgres); .dockerignore exclusions.

## Concerns
- **Healthcheck liveness-only:** `java -version` proves JVM alive, not app health. Trade-off forced by U-003's curl-less JRE image. Documented; follow-up to add curl/wget to the runtime image for a real `/actuator/health` readiness probe.
- `docker compose up` end-to-end not run (operator/manual) — the unit's manual acceptance is operator-run with real secrets.
