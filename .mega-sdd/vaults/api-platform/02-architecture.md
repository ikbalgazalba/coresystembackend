# 02 — Architecture

> Cross-cutting platform epic on the **existing** coresystembackend Spring Boot service. No new business endpoints; new components are: an OpenAPI/Swagger config, an actuator health exposure, a multi-stage Dockerfile, a docker-compose, and Spring profile config files. All new files follow the existing package layering (`config/`) and the spring.md pack conventions (scan-codebase §5, starterkit-context.yaml patterns).

## Components by layer

### API documentation layer (new)

| Component | Target file | Responsibility | Source |
|---|---|---|---|
| OpenAPI/Swagger config | `src/main/java/com/coresystem/coresystembackend/config/OpenApiConfig.java` (NEW) | `@OpenAPIDefinition` / `OpenAPI` `@Bean`: API title, description, version, contact, server URL. Configures springdoc paths (`/v3/api-docs`, `/swagger-ui.html`). | Q&A Swagger scope; spring.md §config |
| springdoc-openapi dependency | `pom.xml` (EDIT — add dependency) | Brings auto OpenAPI 3 generation from `@RestController` + DTOs. Version = OQ-AP-1 (Boot 4 compat). | Q&A Swagger scope; scan-codebase §7 |
| Documented endpoints (existing, no edit) | `AuthUserController` (`/api/auth/dologin`), `LoginRequest`, `JwtResponse`, `MessageResponse` | Auto-documented by springdoc from annotations + field names. No code change required for basic docs. | codebase-map §3, §2 |

### Health / observability layer (new)

| Component | Target file | Responsibility | Source |
|---|---|---|---|
| Actuator dependency | `pom.xml` (EDIT — add `spring-boot-starter-actuator`) | Provides `/actuator/health` (liveness + readiness groups) for container probes. | Q&A health & profiles |
| SecurityConfig permit rule (EDIT) | `config/SecurityConfig.java` (EDIT — add `.requestMatchers("/actuator/health/**").permitAll()`) | Health probe must be reachable without auth (container orchestrator probes). Exposure surface = OQ-AP-8. | Q&A health & profiles; jwt-login §B-002 |
| Profile config | `src/main/resources/application-dev.yaml` (NEW), `application-prod.yaml` (NEW) | dev: local defaults (local trust store path). prod: container trust store path via env (`${TRUSTSTORE_PATH}`), actuator probe tuning. | Q&A health & profiles |

### Containerization layer (new)

| Component | Target file | Responsibility | Source |
|---|---|---|---|
| Multi-stage Dockerfile | `Dockerfile` (NEW, repo root) | Stage 1 `builder`: `eclipse-temurin:21-jdk`, copy src + mvnw, `./mvnw -B clean package -DskipTests` (tests run separately). Stage 2 `runtime`: `eclipse-temurin:21-jre`, copy the built JAR, `ENTRYPOINT java -jar`. EXPOSE port (OQ-AP-2). | Q&A Docker strategy |
| docker-compose.yml | `docker-compose.yml` (NEW, repo root) | `app` service: `build: .`, `env_file: .env`, trust store `volume` mount (OQ-AP-3 mount target), `ports:`, `healthcheck:` against `/actuator/health`. Optional `postgres` service (OQ-AP-4). | Q&A Docker strategy, env & secrets |

> **Healthcheck probe caveat (advisor finding):** the runtime image is `eclipse-temurin:21-jre`, which does NOT ship `curl`/`wget` by default. A compose `healthcheck: CMD curl ...` would fail unless curl is installed in the runtime stage. Options: (a) install curl in the runtime stage (`RUN apt-get` — enlarges image), (b) use a Java-based probe (`CMD-SHELL java -cp app.jar ...`), or (c) use Spring Boot's built-in actuator probe via `CMD-SHELL` with `wget` if added. Implementer picks one; note the choice in the Dockerfile.
| .dockerignore | `.dockerignore` (NEW, repo root) | Exclude `target/`, `.git/`, `.mega-sdd/`, `.env`, `*.log`, `.idea/`, `.vscode/`, `preview.webp`, build caches. Keeps `.env.example` (template, not secret). | Q&A Docker strategy; §D-002 |

## API contracts (new doc/health paths only)

| Method | Path | Auth | Purpose | Source |
|---|---|---|---|---|
| GET | `/v3/api-docs` | permitAll (new doc path) | OpenAPI 3 JSON spec | Q&A Swagger scope |
| GET | `/swagger-ui.html` | permitAll (new doc path) | Swagger UI browser | Q&A Swagger scope |
| GET | `/actuator/health` | permitAll (NEW rule) | Container liveness/readiness probe | Q&A health & profiles |

> Existing `POST /api/auth/dologin` (permitAll) is unchanged. The new doc + health paths must be added to the `SecurityConfig` permitAll matchers (or a dedicated matcher group), BEFORE the `anyRequest().authenticated()` rule (Spring Security evaluates matchers in order). Prod gating of Swagger UI = OQ-AP-5.

**CORS coupling note (advisor finding):** The existing `SecurityConfig.corsConfigurationSource()` bean hardcodes allowed origins to `http://localhost:3000` and `http://localhost:8080` (`SecurityConfig.java:89`). In a containerized/prod scenario the browser origin calling the API will likely differ from these localhost literals → CORS would reject browser clients. This is an environment-coupled, non-additive concern. HR-5 (additive-only permitAll) is NOT weakened — but the implementer MUST make CORS origins profile-driven (e.g. `${CORS_ALLOWED_ORIGINS}` placeholder in `application-prod.yaml`) or raise an OQ for the prod CORS origin policy. Tracked as implementation guidance (not a new OQ, since the prod-origin value is a deployment decision covered by the profile mechanism).

## Tech stack (confirmed via scan)

- Spring Boot 4.1.1-SNAPSHOT, Java 21, Maven (mvnw committed). *(scan-codebase §7)*
- New deps: `springdoc-openapi-starter-webmvc-ui` (version OQ-AP-1), `spring-boot-starter-actuator`. *(Q&A; scan-codebase §7 — both absent)*
- Runtime image base: `eclipse-temurin:21-jre` (JDK only in builder stage). *(Q&A Docker strategy)*

## Sources

- `source/seed-PRD.md` §E, §G
- `.mega-sdd/codebase/codebase-map.md` §1, §2, §3, §7
- `.mega-sdd/codebase/starterkit-context.yaml` patterns (controller/config locations)
- `.mega-sdd/vaults/jwt-login/constitution.md` §B-002 (SecurityFilterChain)
