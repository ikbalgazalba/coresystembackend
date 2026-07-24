# PostgreSQL MCP Server — Design Spec

**Date:** 2026-07-24
**Topic:** A Model Context Protocol (MCP) server that connects to the same PostgreSQL the `coresystembackend` app uses, exposing tools Claude can call later to perform DDL and DML migration work safely.

## 1. Background & Motivation

The project's `application.yaml` configures Spring Data JPA against an external PostgreSQL (newmojf UAT at `10.95.1.43:5432/newmojf`) with `jpa.hibernate.ddl-auto: none`. There is **no Flyway or Liquibase** in the project — all schema changes are currently manual. This leaves a gap: no structured, reviewable, auditable way to perform schema (DDL) and data (DML) migrations.

This spec designs an MCP server that closes that gap. It points at the same database the app uses and gives Claude a tool surface for introspecting the schema and performing gated, previewed, audited write operations. The phrase "agar suatu hari nanti kamu bantu migrasi database untuk operasi DML dan DDL" (so that someday you can help me migrate the database for DML and DDL operations) defines the goal: a durable, safe migration assistant available on demand.

### Project facts (verified at design time)

- **DB engine:** PostgreSQL, driver `org.postgresql.Driver`.
- **Connection:** `jdbc:postgresql://10.95.1.43:5432/newmojf` (external; no postgres service in `docker-compose.yml`).
- **Secrets externalized:** `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` come from env vars (`§D-002` of the project constitution). No defaults; missing env fails fast.
- **Schema management:** `ddl-auto: none`, no migration framework present.
- **Runtime availability on the host:** Python 3.10.12, Node 24, Java 21. (No `mcp` SDK or `psycopg` installed yet — both are pip-installable.)

## 2. Decisions (locked during brainstorming)

| Decision | Choice |
|---|---|
| Autonomy model | **Dry-run + confirm gate** — reads run freely; every write is previewed then gated by an approval token. |
| Runtime | **Python + MCP SDK**, psycopg3 driver. |
| DB connection | **Reuse app env vars** `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` — zero drift from `application.yaml`, honors `§D-002`. |
| Tool scope | Schema introspection (read) + Query/EXPLAIN (read) + DDL execution (write) + DML execution (write). |
| Code location | Top-level `mcp/` directory. |
| Build approach | **A — preview + confirm + audit**, plus a migration-script generator tool. |

## 3. Architecture & Components

### Layout

```
mcp/pg-mcp-server/
├── pyproject.toml          # deps: mcp, psycopg[binary]; packaging metadata
├── .gitignore              # .venv/, __pycache__/, audit.log
├── README.md               # install + register in .mcp.json
├── src/pg_mcp_server/
│   ├── __main__.py         # entrypoint: reads env, builds pool, runs stdio server
│   ├── config.py           # env parsing (SPRING_DATASOURCE_*), kill-switch, limits
│   ├── db.py               # psycopg pool; read-only vs read-write connection factory
│   ├── safety.py           # SQL classifier, token bind (sha256+nonce), non-tx detector
│   ├── audit.py            # JSONL audit appender (timestamp, tool, sql, status, rows)
│   ├── migrations.py       # generate_migration_script → V<ts>__<desc>.sql
│   └── server.py           # MCP @mcp.tool() definitions + handlers
└── tests/
    ├── test_safety.py      # classifier + token-bind unit tests (no DB)
    └── test_db_smoke.py    # introspection/SELECT round-trip; @pytest.mark.integration
```

### Component responsibilities

- **`config.py`** — single source for env. Parse `SPRING_DATASOURCE_URL` (jdbc→psycopg dsn conversion), creds, `MCP_PG_WRITE_MODE` (default `confirm`; `off` disables all writes), row cap (500), time cap (10000 ms). Fail fast on missing env, mirroring the app's `§D-002` "Could not resolve placeholder" pattern.
- **`db.py`** — two connection sources from one psycopg `ConnectionPool`: `_read_conn()` sets `default_transaction_read_only = on`; `_write_conn()` is read-write. Transactions are per-call; no global mutable state. Per-call `statement_timeout`.
- **`safety.py`** — pure functions, fully unit-testable: `classify(sql)` (DDL/DML/SELECT/OTHER), `detect_non_transactional(sql)` (CONCURRENTLY, VACUUM, ALTER SYSTEM, CLUSTER, REINDEX), `assert_single_select(sql)`, token produce/verify (`sha256(normalized_sql + nonce)`).
- **`audit.py`** — append-only JSONL to `mcp/pg-mcp-server/audit.log` (gitignored). Every write-path tool call logs `{ts, tool, sql, status, rows_affected, decision}` regardless of outcome.
- **`migrations.py`** — given confirmed statements + a description, emit a timestamped SQL file under `src/main/resources/db/migration/V<YYYYMMDDHHMMSS>__<slug>.sql`. Durable, reviewable, Flyway-ready for later adoption.
- **`server.py`** — the MCP `@mcp.tool()` surface (Section 4). Thin handlers delegating to db/safety/audit.

### Data flow (a write)

`propose_write(sql)` → `safety.classify` + `db.preview` (read-only EXPLAIN + estimate, **no mutation**) → return preview + `preview_token` → (human approval in chat) → `confirm_write(token, sql)` → `safety.verify_token` → `db.execute_write` (transactional commit) → `audit.log` → return result.

### Isolation guarantees (defense in depth)

1. Reads run on a server-enforced read-only connection — a classifier bug cannot mutate the DB.
2. The confirm token is bound to the exact SQL string; the confirm step cannot silently execute something different from what was previewed.

## 4. Tool Surface

### Read tools (ungated, read-only connection)

| Tool | Inputs | Returns |
|---|---|---|
| `list_schemas` | `include_system?` (default false) | schema names (excludes pg_catalog/information_schema unless flag set) |
| `list_tables` | `schema?` (default `public`) | table names + row-count estimates + sizes |
| `describe_table` | `schema`, `table` | columns (name/type/nullable/default), PK, FKs, indexes, unique constraints |
| `list_views` | `schema?` | view names + definitions |
| `run_select` | `sql`, `limit?` (cap 500), `timeout_ms?` (cap 10000) | rows (capped); rejects non-SELECT |
| `explain` | `sql` | `EXPLAIN (FORMAT TEXT)` plan |

### Write tools (gated, two-phase)

| Tool | Inputs | Returns |
|---|---|---|
| `propose_write` | `sql` | classification (DDL/DML), non-tx warnings, EXPLAIN preview, affected-row estimate, `preview_token` |
| `confirm_write` | `preview_token`, `sql` | executes iff token matches `sha256(sql.strip().lower() + nonce)` (same normalization as `propose_write`); returns affected rows / `RETURNING` rows; writes audit line |
| `generate_migration_script` | `description`, `sql[]` (confirmed only) | writes `src/main/resources/db/migration/V<ts>__<slug>.sql`; returns path + content |

### Guardrails (enforced in handlers)

- **`run_select`** rejects anything that isn't a single SELECT: no `;`, no `INSERT/UPDATE/DELETE`, no CTE-with-INSERT. Postgres-level defense: read-only session regardless.
- **`propose_write`** never executes the target statement — only EXPLAIN plus a separate count query. It cannot mutate even if the classifier mislabels.
- **`confirm_write`** refuses when: `MCP_PG_WRITE_MODE == off`, the token does not recompute for the exact `sql` passed, or the statement is on the non-transactional list.
- **Row/time caps** enforced on every read; write previews cannot run unbounded.
- **Non-transactional commands** (`VACUUM`, `CREATE INDEX CONCURRENTLY`, `ALTER SYSTEM`, `CLUSTER`, `REINDEX`) are detected and refused by the write path — they cannot run inside the preview→confirm transaction model. `propose_write` explains and suggests manual execution. This is a real Postgres constraint, surfaced honestly.

## 5. Data Flow, Error Handling, Security

### Read flow (`run_select`)

1. `config` resolves connection from `SPRING_DATASOURCE_*` (jdbc→dsn in `db.py`).
2. `db._read_conn()` → connection with `default_transaction_read_only = on`.
3. `safety.assert_single_select(sql)` — reject if not exactly one SELECT.
4. `cur.execute(sql)` with per-call `statement_timeout = min(timeout_ms, cap)`. Fetch `min(limit, 500)`.
5. Return structured rows; rollback (read-only txn discarded).
6. On timeout → return a clear "Query timed out after X ms" error, not a raw stack.

### Write flow (`propose_write` → `confirm_write`)

1. `propose_write(sql)`:
   - `safety.classify(sql)` → DDL/DML/OTHER; extract statement verb.
   - `safety.detect_non_transactional(sql)` → if hit, return refusal-with-explanation (no preview, no token).
   - Read-only preview: `EXPLAIN` for the statement. For UPDATE/DELETE, run a **separate matching** `SELECT count(*)` using the same WHERE clause — **never executes the write**. The WHERE is extracted by regex on the statement text (`WHERE` … to end, stripping trailing `RETURNING`); if extraction fails, the estimate is omitted and the preview returns an explicit `"row_estimate: unavailable (could not extract WHERE)"` rather than a silent guess. INSERT is not estimated (no reliable count without executing).
   - Compute `preview_token = sha256(sql.strip().lower() + nonce)`. `nonce` is a per-server-start random value held in memory.
   - Return classification + warnings + EXPLAIN + estimate + token. Audit as `PROPOSED`.
2. Human approves in chat.
3. `confirm_write(token, sql)`:
   - If `MCP_PG_WRITE_MODE == off` → refuse "writes disabled".
   - Recompute token from the passed `sql`; if ≠ `token` → refuse "SQL does not match previewed statement".
   - `db._write_conn()` → BEGIN; `cur.execute(sql)`; commit.
   - For DML with `RETURNING`, return the rows; else return `rowcount`.
   - Audit as `EXECUTED` with rows, or `FAILED` on error (no commit).
   - On any DB error → rollback, log `FAILED` + error message, surface a readable error.

### Error handling principles

- **Never swallow** — every error path returns a structured, readable message to the tool caller and logs to audit. No silent failures.
- **Connection failures** (DB unreachable, auth failed) → fail fast at startup with the env-var names to check.
- **Token expiry** — nonce is per-process; if the server restarts, old tokens are invalid → `confirm_write` tells you to re-`propose`. No stale execution.
- **psycopg `DatabaseError`** → catch, extract `pgcode`/`pgerror`, return the Postgres message without Python traceback noise.

### Security (aligned with `§D-002`)

- **Zero secrets in code or repo.** Connection comes only from `SPRING_DATASOURCE_*` env vars (already in `.env`, gitignored). `.mcp.json` references the server command + venv, never creds.
- **`.gitignore` additions:** `mcp/pg-mcp-server/.venv/`, `mcp/pg-mcp-server/audit.log`, `mcp/pg-mcp-server/__pycache__/`.
- **Read-only connection** is a second layer of defense independent of the SQL classifier.
- **Kill-switch** `MCP_PG_WRITE_MODE=off` is a hard gate checked before every write path — useful to point the server at a read-only copy or freeze during an incident.
- **Audit log** is the accountability record: every propose and confirm is timestamped with the SQL and outcome. Gitignored (may contain schema/data shape) but present on disk.

### Honest limitation (stated in README)

An MCP server cannot truly verify *who* called `confirm_write` — Claude invokes the tools. The real guarantees are: (1) writes never execute without a matching preview surfacing EXPLAIN/affected-rows first, (2) the token binds the executed SQL to exactly what was previewed (no bait-and-switch), (3) a kill-switch backstop, (4) a full audit log. The human's "yes" in chat is the actual approval — same as all tool use. This is **not** row-level auth, not a sandboxed DB, not multi-tenant isolation. It is the same DB user the app uses, with preview/confirm/audit discipline layered on.

## 6. Testing & Wiring

### Testing strategy

1. **Unit tests (no DB):** `test_safety.py` covers `classify()`, `detect_non_transactional()`, token produce/verify (correct binding, mismatched-SQL refusal, wrong-token refusal), and `assert_single_select()` (rejects `INSERT; SELECT`, CTE-with-INSERT, `SELECT … INTO`).
2. **Integration tests (optional, requires PG):** `test_db_smoke.py` — verify `list_schemas`, `describe_table`, `run_select`, the full propose→confirm cycle, and migration-script generation. Marked `@pytest.mark.integration`; skipped unless `MCP_PG_TEST_URL` is set.
3. **Manual smoke test:** After `.mcp.json` registration, call `list_schemas` + `describe_table` on a known table to verify connection and data return.

### Wiring into Claude Code (project-level `.mcp.json`)

```json
{
  "mcpServers": {
    "pg-mcp-server": {
      "command": "mcp/pg-mcp-server/.venv/bin/python",
      "args": ["-m", "pg_mcp_server"],
      "env": {
        "SPRING_DATASOURCE_URL": "",
        "SPRING_DATASOURCE_USERNAME": "",
        "SPRING_DATASOURCE_PASSWORD": "",
        "MCP_PG_WRITE_MODE": "confirm"
      }
    }
  }
}
```

The `SPRING_DATASOURCE_*` values are **empty strings** — Claude Code inherits them from the shell environment (the sourced `.env`), not from this file. `§D-002` honored. `MCP_PG_WRITE_MODE` defaults to `confirm` as the safe baseline; override to `off` for a read-only session.

### README content

- Prerequisites: Python 3.10+; venv + `pip install -e .`.
- Env vars needed (same as app + `MCP_PG_WRITE_MODE`).
- How to register in `.mcp.json`.
- Quick verification: `list_schemas` call.
- Limitations stated: same DB user as app, no row-level isolation, non-tx commands must be manual, token expires on server restart.

## 7. Out of Scope

- Row-level authorization, multi-tenant isolation, or a sandboxed copy of the DB.
- Execution of non-transactional commands (`VACUUM`, `CONCURRENTLY` index builds, `ALTER SYSTEM`) — these are detected, refused, and pointed to manual execution.
- A full migration-framework integration (Flyway/Liquibase). The server emits Flyway-style `V__*.sql` files as durable artifacts, but does not itself run a migration framework.
- Authentication of the MCP transport (stdio is local-only by default).

## 8. Open Items (none blocking)

- Whether `generate_migration_script` should also emit a Flyway-style down/rollback script. Deferred — the audit log already captures what executed for manual rollback; a down-script generator can be a later enhancement.
- Whether to point the server at a DB clone rather than live UAT. Left to operator choice via env vars at run time; the design is connection-agnostic.
