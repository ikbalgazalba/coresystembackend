# 04 — Flows

> System (not end-user) flows. This epic is infrastructure; flows describe build/deploy/run and doc-generation sequences. Each flow has a Definition of Done.

## Flow 1 — Build container image (multi-stage)

**Actors**: Platform/DevOps engineer, Docker daemon.
**Trigger**: `docker build` or `docker compose build`.

1. Builder stage (`eclipse-temurin:21-jdk`) copies `pom.xml` + `mvnw` + `.mvn` + `src/`.
2. Builder runs `./mvnw -B clean package` (resolver reaches `repo.spring.io/snapshot` for the Boot 4 parent — network required in builder). *(scan-codebase §7 snapshot_repo)*
3. Builder produces `target/coresystembackend-0.0.1-SNAPSHOT.jar` (layered Spring Boot jar via spring-boot-maven-plugin).
4. Runtime stage (`eclipse-temurin:21-jre`) copies ONLY the JAR (no Maven, no source, no target/).
5. `.dockerignore` ensures `.env`, `.git`, `.mega-sdd/`, `target/` (host) are not sent to the builder context. *(§D-002 — no secret in context)*
6. Image tagged; EXPOSE port (OQ-AP-2).

**Definition of Done**:
- `docker build -t coresystembackend .` succeeds.
- Image runs and binds the app port.
- Image contains NO `.env`, NO source tree, NO Maven — only the JRE + JAR (verify via `docker run --rm coresystembackend ls /`).
- No secret string appears in any image layer (`docker history` + secret-scan). *(§D-002)*

## Flow 2 — Run container via compose (env + trust store)

**Actors**: Platform/DevOps engineer, Docker Compose.
**Trigger**: `docker compose up`.

1. Compose `app` service builds (or pulls) the image.
2. `env_file: .env` injects all 14 env vars (SPRING_DATASOURCE_URL/USERNAME/PASSWORD, JWT_SECRET, LDAP_URL_TOKEN, LDAP_URL_VERIFY_PASSWORD, LDAP_HOST, LDAP_CLIENT_ID, LDAP_CLIENT_SECRET, LDAP_USERNAME, LDAP_PASSWORD, LDAP_PARTNER_ID, LDAP_CHANNEL_ID, LDAP_AES_KEY) into the container env → Spring `${VAR}` placeholders resolve. *(scan-codebase §6 secrets; .env.example contract — 14 vars verified)*
3. Trust store volume mount: host `.ssl-truststores/bankmega-truststore.p12` → container path (OQ-AP-3 mount target); JVM flags `-Djavax.net.ssl.trustStore=...` passed via `JAVA_OPTS` env. *(run-app.sh logic; §B-006 — mounted, not bypassed)*
4. Container port published to host (OQ-AP-2).
5. Healthcheck: compose polls `/actuator/health` until `UP`.

**Definition of Done**:
- `docker compose up` starts the app; `POST /api/auth/dologin` reachable on the published port.
- `.env` is NOT in the image (only mounted/injected at run). *(§D-002)*
- Removing `.env` before `up` causes the app to fail-fast on missing placeholder (preserves existing behavior). *(scan-codebase §6)*
- Trust store is mounted (volume), not baked into the image.
- `/actuator/health` returns `{"status":"UP"}`.

## Flow 3 — Generate & serve OpenAPI docs

**Actors**: springdoc-openapi (runtime), API consumer (browser).
**Trigger**: app startup + GET `/swagger-ui.html` / `/v3/api-docs`.

1. springdoc scans `@RestController` beans (`AuthUserController`) + DTOs (`LoginRequest`, `JwtResponse`, `MessageResponse`) at startup.
2. OpenAPI 3 spec assembled: path `POST /api/auth/dologin`, request body schema = `LoginRequest`, response schemas = `JwtResponse` / `MessageResponse`.
3. Spec served at `/v3/api-docs` (JSON); Swagger UI served at `/swagger-ui.html`.
4. SecurityConfig permits these doc paths (new matcher). *(02-architecture API contracts)*
5. Prod profile may disable Swagger UI (OQ-AP-5 gating).

**Definition of Done**:
- `GET /v3/api-docs` returns valid OpenAPI 3 JSON containing the `/api/auth/dologin` path + the 3 DTO schemas.
- `GET /swagger-ui.html` loads the UI and renders the login endpoint.
- Doc paths are reachable without auth (permitAll matcher in SecurityConfig).

## Flow 4 — Health probe (actuator)

**Actors**: Container orchestrator / compose healthcheck, Spring Boot actuator.
**Trigger**: periodic probe.

1. `spring-boot-starter-actuator` exposes `/actuator/health`.
2. Liveness + readiness groups available (`/actuator/health/liveness`, `/actuator/health/readiness`) — exposure surface OQ-AP-8.
3. SecurityConfig permits `/actuator/health/**`.
4. Compose healthcheck: polls `/actuator/health` until `UP`. **Probe caveat (advisor):** the `eclipse-temurin:21-jre` runtime image lacks `curl`/`wget` by default — use a Java-based/`CMD-SHELL` probe or install a client in the runtime stage (see 02-architecture healthcheck caveat).

**Definition of Done**:
- `/actuator/health` returns `{"status":"UP"}` when the context is healthy.
- `/actuator/health/**` is permitAll (no auth wall on probes).
- No other actuator endpoints exposed beyond health unless OQ-AP-8 resolves otherwise (default: health only).

## Flow 5 — Profile selection (dev vs prod)

**Actors**: Spring Boot, operator.
**Trigger**: `SPRING_PROFILES_ACTIVE` env var.

1. `SPRING_PROFILES_ACTIVE=dev` → loads `application-dev.yaml` (local trust store path, local DB).
2. `SPRING_PROFILES_ACTIVE=prod` → loads `application-prod.yaml` (trust store path via `${TRUSTSTORE_PATH}`, container-tuned actuator, Swagger UI gating per OQ-AP-5).
3. Default (no profile) → existing `application.yaml` behavior preserved (fail-fast on missing env). *(scan-codebase §6)*

**Definition of Done**:
- `application-dev.yaml` and `application-prod.yaml` exist and override only profile-specific keys.
- Base `application.yaml` env-placeholder + fail-fast behavior unchanged.
- Compose sets `SPRING_PROFILES_ACTIVE=prod` by default.

## Sources

- `source/seed-PRD.md` §E (in-scope features), §G (constraints)
- `.mega-sdd/codebase/codebase-map.md` §6 (secrets/run-app.sh), §7 (snapshot repo)
- `.mega-sdd/vaults/jwt-login/constitution.md` §B-006, §D-002
