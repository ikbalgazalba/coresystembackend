# Vault Index — api-platform

> **Vault**: `api-platform` — OpenAPI/Swagger documentation layer + Docker containerization for coresystembackend
> **Project shape**: api-only (cross-cutting platform epic on an existing Spring Boot service)
> **Implementation mode**: existing (brownfield; jwt-login vault already landed)
> **PRD status**: draft
> **Output mode**: compact
> **Generated**: 2026-07-24 by `/mega-sdd:generate-intent` (Mode B from-prompt, scan-aware)

## Vault Lock

| Field | Value |
|---|---|
| vault_version | 1.2 |
| project_shape | api-only |
| implementation_mode | existing |
| prd_status | draft |
| output_mode | compact |
| constitution_version | 1.1.0 |
| source_documents | seed-PRD (source/seed-PRD.md), codebase-map (.mega-sdd/codebase/codebase-map.md) |

## Implementation Notes for AI Consumers

This epic adds two cross-cutting concerns to the **existing, feature-bearing** coresystembackend service (which already implements `POST /api/auth/dologin` via the `jwt-login` vault):

1. **API documentation layer** — springdoc-openapi auto-generates an OpenAPI 3 spec from the existing controller + DTOs and serves Swagger UI. No new endpoints beyond the doc paths (`/swagger-ui.html`, `/v3/api-docs`) and the actuator health path.
2. **Containerization** — a multi-stage Dockerfile + docker-compose make the service buildable/runnable in Docker, with env + SSL trust store injected (never baked-in).

**Do NOT** fix the pre-existing jwt-login gaps (bearer filter absent, role-not-enforced, no `@Valid`) in this epic — they are out of scope, tracked as a known codebase gap (codebase-map §6 / starterkit-context.yaml auth.bearer_filter), to be addressed in a separate effort. **Do NOT** add a security scheme to the OpenAPI spec in v1 (deferred per Q&A).

The repo currently runs only via host `mvn spring-boot:run` with a host-specific trust store path (`/home/ikbalgazalba/.ssl-truststores/...`); this epic makes that portable.

## Reading paths

- **Platform / DevOps engineer** → `01-overview.md` (goals) → `02-architecture.md` (container + doc layer components) → `04-flows.md` (build-image-run, doc-generation) → `06-constraints.md` (§D-002 image hygiene, trust store) → `05-decisions.md` (multi-stage, env_file, profiles).
- **API consumer / FE dev** → `02-architecture.md` (OpenAPI/Swagger config) → `03-data-model.md` (DTOs documented) → existing `AuthUserController` (POST /api/auth/dologin).
- **Implementer (bolt)** → `02-architecture.md` (target files per component) → `04-flows.md` (Definition of Done per flow) → `06-constraints.md` (Hard rules) → `constitution.md` (§A–§F).

## Open Questions roll-up

| OQ | Category | Priority | Resolution mode | Summary |
|---|---|---|---|---|
| OQ-AP-1 | tech | P1 | recommend | springdoc-openapi version compatible with Spring Boot 4.1.1-SNAPSHOT |
| OQ-AP-2 | tech | P2 | recommend | Container port (default 8080 vs override) + SERVER_PORT wiring |
| OQ-AP-3 | tech | P1 | blocking | Trust store path inside container (mount target) + reconcile run-app.sh host logic |
| OQ-AP-4 | tech | P2 | recommend | PostgreSQL in compose: real DB service vs env-only wiring to existing newmojf UAT (fallback: env-only, no postgres service — app already wires to 10.95.1.43) |
| OQ-AP-5 | tech | P2 | recommend | Swagger UI prod gating (disable in prod profile? security exposure of docs) |
| OQ-AP-6 | tech | P3 | recommend | JVM memory/CPU resource limits for the container |
| OQ-AP-7 | business | P3 | recommend | Success metrics / acceptance thresholds for "containerized" (quantitative) (fallback: functional DoD only, no quantitative threshold) |
| OQ-AP-8 | tech | P3 | scan | actuator endpoint exposure surface (health only vs full) given SecurityConfig permitAll rules |
| OQ-AP-9 | tech | P2 | recommend | CORS origins externalization — SecurityConfig.java:89 hardcodes localhost:3000/8080; container/prod MUST override via ${CORS_ALLOWED_ORIGINS} (binding-advisor APB-003; now hard rule HR-8) |

## Auto-Classification Review

The following OQs are `low`-confidence **blocking** OQs (cannot be safely auto-resolved downstream — flagged for human review before binding):
- OQ-AP-3 (trust store path) — blocking; the mount target path inside the container must be a human decision (no safe default).

> Advisor note: the remaining medium/low-confidence OQs (OQ-AP-1, OQ-AP-2, OQ-AP-6, OQ-AP-7, OQ-AP-8) are `recommend`/`scan` mode and carry a safe `fallback_if_wrong`, so they CAN auto-resolve downstream and are not blocking-review. OQ-AP-4 was reclassified from `blocking` to `recommend` (advisor) because its fallback is resolvable from the codebase-map (app already wires to the existing UAT DB). OQ-AP-7 was reclassified from `blocking` to `recommend` (advisor) — P3+blocking was internally contradictory; metrics being unspecified does not block finalization.

## Sources

- `source/seed-PRD.md` (user brief + Q&A, verbatim)
- `.mega-sdd/codebase/codebase-map.md` (scan-codebase, HEAD 0682bb5)
- `.mega-sdd/codebase/starterkit-context.yaml` (deep-scan, pack spring.md)
- `.mega-sdd/vaults/jwt-login/constitution.md` (inherited §B/§D clauses)
- `.mega-sdd/vaults/jwt-login/vault.json` (existing entities/endpoints to document)

## Out of Scope (v1)

- OpenAPI security scheme (Bearer JWT) — deferred.
- Distroless / non-root prod image, separate Dockerfile.dev/prod — deferred.
- Docker secrets / external secret manager — deferred (env_file is v1).
- CI/CD pipeline (the brief's "pipeline /mega-sdd" = the SDD pipeline, not CI/CD).
- Fixing pre-existing jwt-login gaps (bearer filter, role enforcement, @Valid) — separate effort.
