# 01 — Overview

## What

Add an **OpenAPI/Swagger documentation layer** and **Docker containerization** to the existing coresystembackend Spring Boot service. The service already exposes `POST /api/auth/dologin` (jwt-login vault); this epic makes its API self-documenting and deployable as a container. *(brief; Q&A Swagger scope + Docker strategy)*

## Who

- **API consumers / frontend devs** — read the OpenAPI spec / Swagger UI to integrate against the login endpoint and future endpoints. *(Q&A Swagger scope, inferred)*
- **Platform / DevOps engineer** — builds and runs the container image, wires env + SSL trust store, probes health. *(Q&A Docker/env/health, inferred)*

## Why

The service has no API documentation layer (no springdoc/swagger dependency) and no container artifacts (no Dockerfile/compose/.dockerignore) — it runs only via host `mvn spring-boot:run` with a host-specific trust store path. This blocks reproducible deployment and API discoverability. *(brief; scan-codebase §7 observations)*

## Success criteria

- OpenAPI 3 spec auto-generated from code; Swagger UI reachable at `/swagger-ui.html`. *(Q&A Swagger scope)*
- App builds and runs inside a Docker container (multi-stage, JRE runtime image). *(Q&A Docker strategy)*
- `docker compose up` starts the app with env from `.env` + trust store mounted as volume. *(Q&A env & secrets)*
- `/actuator/health` reachable for container readiness/liveness probes. *(Q&A health & profiles)*
- No secret baked into the image (§D-002 preserved). *(Q&A env & secrets; jwt-login constitution §D-002)*
- Quantitative acceptance thresholds — `(unspecified)` (OQ-AP-7). *(brief silent)*

## Sources

- `source/seed-PRD.md` §A, §B, §C, §D
- `.mega-sdd/codebase/codebase-map.md` §7 (no swagger/docker deps present)
