# 05 — Decisions (ADR-style)

## ADR-AP-1: springdoc-openapi for OpenAPI 3 generation

**Context**: Need an API documentation layer that auto-generates from the existing Spring Boot controller + DTOs with minimal code. Options: springdoc-openapi (modern, Boot-native), springfox (unmaintained, Boot 3+ issues), hand-written spec. *(Q&A Swagger scope)*

**Decision**: Use `springdoc-openapi-starter-webmvc-ui` — the maintained, Boot-3/4-aligned generator. Auto-discovers `@RestController` + DTOs; serves UI + spec.

**Consequences**:
- One dependency + one config class (`OpenApiConfig`) for basic docs.
- Version compatibility with Boot 4.1.1-SNAPSHOT is unresolved → OQ-AP-1 (a snapshot Boot may need a specific springdoc milestone/snapshot).
- `LoginRequest.pass` field appears in the spec → prod gating needed (OQ-AP-5).
- springfox explicitly rejected (unmaintained, breaks on Boot 3+).

## ADR-AP-2: Multi-stage Dockerfile (builder JDK → runtime JRE)

**Context**: Need a reproducible container build that does not require Maven on the host and produces a lean runtime image. Options: single-stage (build on host, copy JAR), multi-stage (build inside container), buildpacks. *(Q&A Docker strategy)*

**Decision**: Multi-stage Dockerfile. Stage 1 `eclipse-temurin:21-jdk` runs `./mvnw -B clean package`; stage 2 `eclipse-temurin:21-jre` copies only the JAR.

**Consequences**:
- Reproducible (mvnw pinned, no host Maven version drift).
- Runtime image is small (JRE only, no Maven, no source).
- Builder needs network to `repo.spring.io/snapshot` (Boot 4 parent) — offline builds fail unless cached. *(scan-codebase §7)*
- Tests are skipped in the image build (`-DskipTests`); tests run as a separate CI/gate step (out of scope here).

## ADR-AP-3: env_file + volume mount for secrets & trust store (NOT baked-in)

**Context**: Existing app externalizes all secrets to env vars (§D-002) and uses a host-specific bankmega SSL trust store. In a container, secrets must not be baked into the image and the trust store must be portable. Options: bake config into image, env_file + volume mount, Docker secrets/secret manager. *(Q&A env & secrets)*

**Decision**: `env_file: .env` (gitignored) for env vars; trust store mounted as a volume to a container path (OQ-AP-3) with JVM flags via `JAVA_OPTS`.

**Consequences**:
- §D-002 preserved: image contains no secret, no trust store bytes.
- Portable across hosts (trust store path is env-configurable, not hardcoded).
- `run-app.sh` host logic is superseded for container runs (kept for local non-container runs) — OQ-AP-3 reconciles.
- Docker secrets / external manager deferred to a future hardening epic.

## ADR-AP-4: Actuator health + two Spring profiles (dev/prod)

**Context**: Container orchestrators need a health probe; local dev and container prod have different trust-store/DB/tuning needs. Options: no actuator (TCP probe only), actuator only, actuator + profiles. *(Q&A health & profiles)*

**Decision**: Add `spring-boot-starter-actuator`; expose `/actuator/health` (permitAll in SecurityConfig). Two profiles: `application-dev.yaml` (local) and `application-prod.yaml` (container).

**Consequences**:
- Compose healthcheck uses actuator (real app-health, not just TCP).
- `/actuator/health/**` added to permitAll — widens the unauthenticated surface by one path (acceptable for a health probe; exposure scope = OQ-AP-8).
- Profile config files must override ONLY profile-specific keys; base `application.yaml` fail-fast behavior preserved.
- Default profile in compose = `prod`.

## ADR-AP-5: Swagger UI path under permitAll (v1), prod gating deferred

**Context**: `/api/auth/**` is already permitAll; Swagger UI/docs paths are new unauthenticated surfaces. Exposing API docs (incl. a credential field) in prod is a mild security smell. *(Q&A Swagger scope; OQ-AP-5)*

**Decision (v1)**: Permit doc paths in all profiles for simplicity. Prod gating (disable Swagger UI in prod, keep spec internal) is deferred to OQ-AP-5 resolution.

**Consequences**:
- v1 ships docs reachable without auth in all environments.
- OQ-AP-5 (recommend) will propose a prod profile flag (`springdoc.swagger-ui.enabled=false`) — implementer should leave a clean seam for it.
- The `LoginRequest.pass` field is documented — acceptable for an internal UAT-context API but flagged.

## Sources

- `source/seed-PRD.md` §E, §G, §I
- `.mega-sdd/codebase/codebase-map.md` §6, §7
- `.mega-sdd/vaults/jwt-login/constitution.md` §B-006, §D-002

## OQ-AP-1 resolution — springdoc-openapi version (RESOLVED 2026-07-24)

**Decision:** use `springdoc-openapi-starter-webmvc-ui` **3.0.3**.

**Evidence:** springdoc-openapi maintains two parallel release tracks — v2.x.x targets Spring Boot 3.5.x; **v3.x.x targets Spring Boot 4.x** (v3.0.0 "Upgrade to Spring Boot 4.0.0"; v3.0.3 targets Boot 4.0.5, released 2026-04-11). Source: github.com/springdoc/springdoc-openapi/releases.

**Rationale:** this project pins Spring Boot 4.1.1-SNAPSHOT. The v3.x line is the only springdoc track targeting Boot 4; v3.0.3 is the latest v3 release (Boot 4.0.5). The 4.1.1-SNAPSHOT is one minor ahead of v3.0.3's targeted 4.0.5, but the v3 line is the correct Boot-4-compatible track (v2.x is Boot-3-only and will not resolve against Boot 4 starters).

**Risk:** snapshot Boot may shift behavior; if v3.0.3 fails to resolve against 4.1.1-SNAPSHOT, fall back to the latest v3.0.x snapshot/milestone or pin Boot to 4.0.5. Recorded as the accepted recommendation; PO/architect may override.

## OQ-AP-2 resolution — container port 7001 (RESOLVED 2026-07-24)

**Decision:** default app/container port changed from 8080 → **7001**.

**Rationale:** operator preference to free 8080 for other uses; 7001 chosen (free on host, verified `ss -tlnp`). Spring Boot `server.port` remains env-driven (`${SERVER_PORT:7001}`) so it is overridable per environment — the change only shifts the DEFAULT, not the mechanism. Port 7001 is set in BOTH profiles (`application-prod.yaml` + `application-dev.yaml`) so dev runs (no explicit profile key) listen on 7001 too (previously dev fell back to Spring's 8080 default).

**Files changed (8080 → 7001, 8 points):**
- `docker-compose.yml`: ports `"7001:7001"`, healthcheck `${SERVER_PORT:-7001}`, CORS fallback origin.
- `application-prod.yaml` + `application-dev.yaml`: `server.port: ${SERVER_PORT:7001}` + CORS default origin `localhost:7001`.
- `SecurityConfig.java`: `@Value` CORS default `localhost:7001`.
- `Dockerfile`: `EXPOSE 7001` + OQ-AP-2 comment + provenance trailer.

**Risk:** low — port lives only in config + EXPOSE (no business code bound to 8080). Verified live: Tomcat `started on port 7001`, `/actuator/health` + `/v3/api-docs` + `/swagger-ui` + `/v3/api-docs/swagger-config` all 200/302 on :7001. `run-app.sh` (host non-container launcher) still uses Spring default 8080 — set `SERVER_PORT=7001` if host-run also needs 7001.
