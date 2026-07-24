# 03 — Data Model

> This epic adds **no new persistent entities**. It documents the existing ones via OpenAPI and adds no DB schema change. JPA `ddl-auto: none` is unchanged (schema pre-exists in the newmojf PostgreSQL DB). *(scan-codebase §4)*

## Entities (existing — documented, not modified)

| Entity | Table | Documented via | Source |
|---|---|---|---|
| `Users` | `mojf_users` | NOT exposed in REST (correctly); appears only as internal lookup. OpenAPI docs do NOT surface it (it is a `@Entity`, not a DTO). | codebase-map §4; jwt-login §C-002 |

## DTOs (existing — auto-documented by springdoc)

| DTO | Fields | OpenAPI role | Source |
|---|---|---|---|
| `LoginRequest` | `uname`, `pass` | Request body schema for `POST /api/auth/dologin` | codebase-map §2; jwt-login vault |
| `JwtResponse` | `token`, `type`, `id`, `uname`, `urole`, `mitKode` | Response body schema (success) | codebase-map §2 |
| `MessageResponse` | `message` | Response body schema (error/info) | codebase-map §2 |

> springdoc-openapi infers schemas from DTO field names/types automatically. No DBML change. No relations added. The `pass` field in `LoginRequest` will appear in the spec — flag: ensure prod Swagger UI gating (OQ-AP-5) so credential field docs are not publicly exposed in prod.

## Sources

- `.mega-sdd/codebase/codebase-map.md` §2, §4
- `.mega-sdd/vaults/jwt-login/03-data-model.md` (entity/DTO provenance)
