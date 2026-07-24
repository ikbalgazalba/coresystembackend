# 06 — Constraints

## Technical

- Spring Boot 4.1.1-SNAPSHOT, Java 21, Maven (mvnw committed). *(scan-codebase §7)*
- `jakarta.*` namespace (Boot 4.x); Spring Security 7.x (`SecurityFilterChain` bean). *(jwt-login §B-001/§B-002)*
- Builder stage requires network to `repo.spring.io/snapshot` (Boot 4 parent snapshot). *(scan-codebase §7)*
- `application.yaml` `${VAR}` placeholders have NO defaults (fail-fast); must not regress. *(scan-codebase §6)*
- springdoc-openapi version must be Boot-4-compatible (unresolved → OQ-AP-1). *(scan-codebase §7 snapshot caveat)*
- Constructor injection; no field `@Autowired`; layered packages. *(jwt-login §C-004/§A-002)*

## Business / non-functional

- §D-002: NO secret in image / env-literals; `env_file: .env` (gitignored) only. *(jwt-login §D-002; Q&A env & secrets)*
- §B-006: NO trust-all SSL bypass; the bankmega trust store is mounted as a volume, never replaced with an empty `X509TrustManager`. *(jwt-login §B-006)*
- §B-007: error responses stay generic (no raw exception echo) — unchanged by this epic but must not be regressed by actuator/health error exposure. *(jwt-login §B-007)*
- Quantitative success metrics (RPS, cold-start, image size target) — `(unspecified)` (OQ-AP-7). *(brief silent)*
- JVM memory/CPU limits — `(unspecified)` (OQ-AP-6). *(brief silent)*

## Hard rules (non-negotiable — bind + bolts enforce)

- **HR-1**: The Docker image MUST contain no `.env`, no secret string, no trust store bytes. `.dockerignore` excludes `.env`; secrets come only from `env_file` at run; trust store only via volume mount. *(§D-002; ADR-AP-3)*
- **HR-2**: The runtime image MUST be `eclipse-temurin:21-jre` (or compatible JRE 21); the Maven builder stage MUST use `./mvnw` (committed wrapper), NOT a host/installed Maven. *(ADR-AP-2)*
- **HR-3**: `/actuator/health/**` MUST be permitAll in `SecurityConfig`; no other actuator endpoint exposed unless OQ-AP-8 resolves otherwise. *(ADR-AP-4; ADR-AP-5)*
- **HR-4**: Base `application.yaml` fail-fast behavior (no defaults on `${VAR}`) MUST be preserved; profile YAMLs override only profile-specific keys. *(scan-codebase §6; ADR-AP-4)*
- **HR-5**: The existing `POST /api/auth/dologin` endpoint + `SecurityConfig` authz rules MUST NOT be weakened; only ADDITIVE permitAll matchers for doc + health paths are allowed. *(jwt-login §B-002; 02-architecture)*
- **HR-6**: No new business logic / no fix of pre-existing jwt-login gaps in this epic (bearer filter, role enforcement, `@Valid`). Those are out of scope. *(seed-PRD §F)*
- **HR-7**: `springdoc-openapi` (and actuator) are the ONLY new dependencies; no Lombok / extra libs without OQ resolution (cf. jwt-login §D-003). *(jwt-login §D-003; seed-PRD §G)*
- **HR-8**: CORS allowed origins MUST be externalized to a `${CORS_ALLOWED_ORIGINS}` env placeholder (profile-driven). The existing `SecurityConfig.corsConfigurationSource()` hardcodes `localhost:3000/8080` (`SecurityConfig.java:89`) — container/prod deployment MUST override this via the prod profile, else browser clients hit CORS rejection. *(binding-advisor APB-003; OQ-AP-9; constitution §G-007)*

## Sources

- `source/seed-PRD.md` §G, §F
- `.mega-sdd/codebase/codebase-map.md` §6, §7
- `.mega-sdd/vaults/jwt-login/constitution.md` §B-006, §B-007, §C-004, §D-002, §D-003
