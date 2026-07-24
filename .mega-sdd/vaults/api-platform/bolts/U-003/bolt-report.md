---
unit: U-003
vault: .mega-sdd/vaults/api-platform
status: completed
commit_sha: 8c6b06b
branch: feat/api-platform-swagger-docker
target_files:
  - path: Dockerfile
    operation: create
postflight: pass
acceptance_test_result: PARTIAL — build command + JAR name PROVEN (host build SUCCESS); docker build NOT VERIFIED (environmental network block)
bolt_self_report:
  confidence: 0.90
  certain_decisions:
    - "multi-stage: eclipse-temurin:21-jdk builder + 21-jre runtime (HR-2)"
    - "RUN ./mvnw -B clean package -DskipTests (HR-2/§D-005 — committed wrapper, not host Maven)"
    - "OQ-AP-2 port 8080; OQ-AP-3 trust-store mount /opt/bankmega-truststore/bankmega-truststore.p12"
    - "ENTRYPOINT java $JAVA_OPTS -jar app.jar (U-004 injects trust-store flags via JAVA_OPTS)"
    - "no HEALTHCHECK shipped (curl caveat — JRE image lacks curl; mechanism is U-004's call)"
  uncertain_decisions:
    - "docker build not verified end-to-end: Docker Hub auth.docker.io outage + container(default bridge)→Maven Central networking issue. Host build (./mvnw) SUCCESS in 3.6s producing exact JAR name → build command + JAR name proven correct; full image build deferred to network restoration."
  retry_history: ["docker build x4 (Docker Hub auth flaky; container→Maven Central deterministic timeout ~268s)"]
---

# Bolt Report — U-003

## Summary
Created multi-stage `Dockerfile` (54 lines) per spec: `eclipse-temurin:21-jdk` builder runs `./mvnw -B clean package -DskipTests`; `eclipse-temurin:21-jre` runtime copies only the JAR; `EXPOSE 8080`; `ENTRYPOINT java $JAVA_OPTS -jar app.jar`. Committed `8c6b06b`.

## Acceptance tests
- **Host build (proves build command + JAR name):** `./mvnw -B clean package -DskipTests` → BUILD SUCCESS (3.6s), produced `target/coresystembackend-0.0.1-SNAPSHOT.jar` — the exact filename the Dockerfile's `COPY --from=builder` references; spring-boot:repackage made it executable.
- **`docker build`:** NOT VERIFIED — two independent environmental network blocks (not Dockerfile defects):
  1. Docker Hub `auth.docker.io` intermittent gateway errors/timeouts (BuildKit must hit it for manifest resolution).
  2. Container (default bridge) → Maven Central broken: `wget: Failed to fetch ... apache-maven-3.9.9-bin.tar.gz` at ~268s, while host reaches Maven Central fine (host build succeeded). Docker bridge networking issue (IPv6/MTU to CDN).
  - Mitigations tried: retries, `--network=host` (blocked by Docker Hub auth at that moment), `docker build --check` (also hits Docker Hub). None altered the Dockerfile.
- **Runtime-image verification (criterion 2/3):** not run — depends on a successful image build.

## Post-flight Hard rules
All 5 PASS (postflight.json): Dockerfile exists; pom unchanged; SecurityConfig unchanged; HR-1 (no baked secrets); HR-2 (runtime 21-jre + builder ./mvnw).

## Concerns
- **docker build unverified** — environmental, not a defect. The Dockerfile is syntactically valid (BuildKit parsed it + began resolving FROM). High confidence it builds once network is restored. Suggested follow-up (U-004/re-run): `docker build -t coresystembackend:u003 .` then criterion-2/3 checks; if container→Maven-Central persists, use `--network=host` (host reaches Maven Central) — no Dockerfile change needed.
- The `--network=host` / `--check` attempts were build-time diagnostics only; the committed Dockerfile is the spec-exact version.

## Out of scope (verified untouched)
compose/.dockerignore (U-004). profiles (U-005). SecurityConfig (U-002). springdoc (U-001).
