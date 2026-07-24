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
acceptance_test_result: PASS — docker build SUCCESS + runtime-image verification + secret-scan (post-network-restoration)
bolt_self_report:
  confidence: 0.96
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
- **`docker build` VERIFIED (post-network-restoration):** `docker build -t coresystembackend:u003-verify .` → BUILD SUCCESS (multi-stage: eclipse-temurin:21-jdk builder runs `./mvnw -B clean package -DskipTests`; 21-jre runtime copies JAR). First attempts hit Docker Hub `auth.docker.io` gateway timeouts (intermittent outage); succeeded on retry once BuildKit's internal retry + cached base images resolved.
- **Runtime-image verification PASS:** `/app/app.jar` present (65MB executable); ABSENT: `src/`, `.mvn/`, `pom.xml`, `.env`, `mvnw`, `target/` (image is lean — no source/build-artifacts/secret). Runtime base = Ubuntu 26.04 + OpenJDK 21.0.11 **JRE** (not JDK — stage 2 correct); `mvn` absent (builder stage did not leak into runtime).
- **Secret-scan PASS:** `docker history --no-trunc` shows no secret literal (no password/jwtSecret/LDAP_secret/datasource_password); image `ENV` only base-image defaults (PATH, JAVA_HOME, LANG) — zero app secrets baked (HR-1: secrets only from `env_file` at run, trust store only via volume mount).

## Post-flight Hard rules
All 5 PASS (postflight.json): Dockerfile exists; pom unchanged; SecurityConfig unchanged; HR-1 (no baked secrets); HR-2 (runtime 21-jre + builder ./mvnw).

## Concerns
- **docker build** — resolved. `docker build` + runtime-image verification + secret-scan all PASS (post-network-restoration). The earlier in-session failures were Docker Hub `auth.docker.io` gateway timeouts (intermittent outage) + container(default bridge)→Maven Central networking — both environmental, neither a Dockerfile defect. Build succeeds on retry once auth resolves.
- The runtime image base is Ubuntu 26.04 (eclipse-temurin:21-jre on Ubuntu), which ships `wget` — see U-004 healthcheck update (readiness probe via `wget /actuator/health`).

## Out of scope (verified untouched)
compose/.dockerignore (U-004). profiles (U-005). SecurityConfig (U-002). springdoc (U-001).
