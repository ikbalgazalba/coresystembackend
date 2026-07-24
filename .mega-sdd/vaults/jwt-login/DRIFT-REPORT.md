# DRIFT-REPORT — jwt-login vault

**Generated:** 2026-07-24 (P2+P3 fixes applied)
**VAULT_DIR:** `.mega-sdd/vaults/jwt-login`
**CODE_DIR:** `/home/ikbalgazalba/AI/Project/coresystembackend`
**DRIFT_SCOPE:** full
**Vault Mode:** `implementation_mode: existing` (migrated from `new` 2026-07-23, v1.3)
**Code HEAD:** `3fc32f5` (2026-07-24); P2+P3 drift fixes in `bffab3c` + follow-up

---

## Executive Summary

**DRIFT STATUS: NONE (all findings closed).**

The codebase is functionally aligned with the vault and the vault docs now reflect the actual code. Login is verified live (`orisys06` → HTTP 200 + JWT). All 17 OQs are resolved/deferred (0 open). `implementation_mode` is correctly `existing`.

All P2 and P3 drift findings from the 2026-07-24 report have been closed via vault-doc edits + one `git mv` — **zero code changes**. The original drift was spec-body staleness (the v1.3 OQ-resolution pass updated OQ blocks + `vault.json` + `00-index.md` but not the surrounding prose/DBML) plus one filename regression. Both are now resolved.

### Findings at a glance (all CLOSED)

| # | Category | Severity | One-line | Status |
|---|----------|----------|----------|--------|
| 1 | MIGRATION-READINESS (spec body stale) | P2 | `03-data-model.md` DBML + prose said table `users`; code uses `mojf_users` | ✅ CLOSED (`bffab3c`) |
| 2 | MIGRATION-READINESS (spec body stale) | P2 | `02-architecture.md:40` said "Entity tabel `users`"; code = `mojf_users` | ✅ CLOSED (`bffab3c`) |
| 3 | MIGRATION-READINESS (spec body stale) | P2 | `02-architecture.md:103` said non-success echoes `responseDescription`; code returns generic | ✅ CLOSED (`bffab3c`) |
| 4 | MIGRATION-READINESS (spec body stale) | P3 | `02-architecture.md:129` said OQ-DM-1 "tetap pending"; resolved | ✅ CLOSED (`bffab3c`) |
| 5 | CODE-ONLY / repo hygiene | P2 | Env template renamed `.env.example` → `.env copy.example`; breaks run recipe | ✅ CLOSED (`bffab3c`, `git mv` back) |
| 6 | CODE-ONLY (documented gap) | P3 | `RestClient.Builder` bean in SecurityConfig not in spec | ✅ CLOSED (02-architecture row updated) |
| 7 | NAME/SHAPE (cosmetic) | P3 | Controller method `login()` vs spec's `authenticateUserUCS()` | ✅ CLOSED (U-008 spec synced to `login`) |
| 8 | CODE-ONLY (doc gap) | P3 | F-U-001 success code marked `[INFERRED]`; verified live | ✅ CLOSED (04-flows DoD lifted to `[VERIFIED]`) |

---

## 1. VAULT-ONLY (documented in vault but NOT in code)

| Entity | Vault Reference | Confidence | Notes |
|--------|-----------------|------------|-------|
| None | — | — | Every vault-documented component is implemented. No missing code. |

---

## 2. CODE-ONLY (in code but NOT documented in vault)

| Entity | Code Location | Vault Impact | Confidence | Action |
|--------|---------------|--------------|------------|--------|
| `RestClient.Builder` bean | `SecurityConfig.java:107-110` | U-006 spec incomplete | HIGH | Document in U-006 (Boot 4.x adaptation) |
| Diagnostic logging | `LdapUcsService.java:121,123-124,157-162`; `AuthUserController.java:42,104` | Spec flow text silent on logging | LOW | Optional: note in 04-flows / 06-constraints |
| `LdapAuthResult` record | `LdapUcsService.java:96` | Minor impl detail | LOW | Optional doc |

### 2.1 `.env copy.example` filename regression [P2] — REPO HYGIENE

**Finding:** Commit `3fc32f5` (2026-07-24 "fix(login): resolve .env integration") renamed the committed env template `.env.example` → **`.env copy.example`** (a filename containing a space). This is almost certainly an accidental file-manager rename, not an intentional change — the file content is unchanged and still clean.

**Impact:**
- The documented run recipe `cp .env.example .env` (in `.env.example` header comment, `run-app.sh`, and memory) no longer matches the tracked filename.
- A space in the filename complicates shell usage (`cp ".env copy.example" .env`).

**Evidence:**
```
git show --stat 3fc32f5 → ".env.example => .env copy.example | 0"  (pure rename, 0 content change)
git ls-files | grep '.env' → ".env copy.example"
```

**Verification (positive):**
- The user's real `.env` (with secrets) is correctly gitignored (`git check-ignore .env` → ignored). No secret leak.
- `.env copy.example` content has NO secrets (only blank `export VAR=` + non-secret UAT URLs). Clean.

**Recommendation:** Rename back: `git mv ".env copy.example" .env.example`. (Read-only report — not auto-applied.)

### 2.2 RestClient.Builder Bean [P3]

**Finding:** `SecurityConfig` defines `@Bean RestClient.Builder restClientBuilder()` returning `RestClient.builder()` — not specified in U-006.

**Rationale (code comment):** Spring Boot 4.x removed the `RestClient.Builder` auto-config from the starter, so it is provided explicitly for `LdapUcsService` (U-007) to consume via constructor injection. Legitimate Boot 4.x adaptation, not a deviation.

**Recommendation:** Add a one-line note to the U-006 spec / `02-architecture.md` SecurityConfig row.

---

## 3. NAME/SHAPE MISMATCH

| Entity | Vault Name | Code Name | Confidence | Impact |
|--------|------------|-----------|------------|--------|
| Controller method | `authenticateUserUCS` (U-008) | `login` (`AuthUserController.java:55`) | HIGH | Cosmetic — URL `/api/auth/dologin` unchanged |
| Entity table (spec body) | `users` (DBML) | `mojf_users` (`Users.java:14`) | HIGH | Doc-only — see §4.1 |

### 3.1 Controller Method Name [P3, cosmetic]

`@PostMapping("/dologin") public ResponseEntity<?> login(...)` vs spec's `authenticateUserUCS`. The HTTP contract (`POST /api/auth/dologin`, request/response shapes) is identical; only the Java method name differs. Documented in `bolts/U-008/bolt-report.md` as an intentional cosmetic deviation.

**Recommendation:** Update U-008 spec method name to `login`, or leave as-is (no functional impact).

---

## 4. MIGRATION-READINESS GAPS (existing-mode vault)

> For an `IMPLEMENTATION_MODE=existing` vault (past `mode_migrate_after`), the spec docs should reflect the **actual implemented code**, not the pre-implementation design. The v1.3 OQ-resolution pass updated OQ blocks + `vault.json` + `00-index.md`, but did **not** propagate decisions into the surrounding spec prose/DBML. These are the migration-readiness gaps.

### 4.1 Table name `users` → `mojf_users` in spec body [P2]

**Vault (stale):**
- `03-data-model.md:16` — DBML `Table users { ... }`
- `03-data-model.md:36` — Note: "Nama tabel users (pack standard) vs mojf_users (referensi)"
- `03-data-model.md:55` — "Tabel snake_case plural (`users`) per pack"
- `02-architecture.md:40` — "Entity tabel `users`"
- `02-architecture.md:61` — "Baca user terdaftar di tabel `users`"

**Code (current):** `Users.java:14` → `@Table(name = "mojf_users")`. Verified live: `findByUname("orisys06")` resolves against `mojf_users` (user id=1561 found).

**Resolution already recorded:** OQ-DM-1 (resolved v1.3, commit `810fd54`) chose `mojf_users` per the OQ-AR-2 branch (use existing newmojf DB). The OQ block says this correctly; the body DBML/prose does not.

**Recommendation:** Update the DBML block + the 4 prose mentions from `users` → `mojf_users`. (This is the single most material doc-staleness item — a reader of `03-data-model.md` alone would build a `users` table and break login.)

### 4.2 Non-success error body still says `responseDescription` [P2]

**Vault (stale):** `02-architecture.md:103` —
> `400` / `MessageResponse` — LDAP `responseCode` bukan "00"/"01" (kredensial salah) → `{message: responseDescription}`

**Code (current):** `AuthUserController.java:97-99` — non-success code returns `MessageResponse("Authentication failed")` (generic), NOT `result.responseDescription()`. Raw `responseDescription` is never echoed to the client (§B-007 / OQ-FL-3).

**Resolution already recorded:** OQ-FL-3 (resolved v1.3) chose "map to generic, do not echo". The OQ block says this; the API-contract Errors section does not.

**Recommendation:** Change `02-architecture.md:103` to `{message: "Authentication failed"}` (generic).

### 4.3 Stale "OQ-DM-1 pending" inside OQ-AR-2 block [P3]

**Vault (stale):** `02-architecture.md:129` —
> OQ-DM-1 sendiri tetap status pending untuk review final, tapi cabang keputusannya sudah ditentukan di sini.

**Current:** OQ-DM-1 is resolved (v1.3, commit `810fd54`). The sentence is self-contradictory with the resolved OQ-DM-1 block in `03-data-model.md`.

**Recommendation:** Drop the "tetap pending" clause; state OQ-DM-1 resolved to `mojf_users`.

### 4.4 Success-contract `[INFERRED]` note not lifted [P3]

`04-flows.md:44` still carries the v1.2 ⚠️ "kode sukses '00'/'01' `[INFERRED]`" warning in the flow DoD, even though OQ-FL-1 v1.3 verified `responseCode=00 SUCCESS` live (commit `de3f828`). The OQ-FL-1 block was amended with the live-verification note, but the flow DoD text was not lifted from `[INFERRED]`.

**Recommendation:** Update the F-U-001 DoD line to mark success code `00` as `[VERIFIED]` (live).

---

## 5. Cross-Reference Verification

### 5.1 Component Presence Matrix

| Component | Vault Doc | Unit | Code | Status |
|-----------|-----------|------|------|--------|
| `Users` entity | 03-data-model | U-002 | `entity/Users.java` | MATCH (table name drift in doc only — §4.1) |
| `UserRepository` | 02-architecture | U-003 | `repository/UserRepository.java` | MATCH |
| `JwtUtils` | 02-architecture | U-005 | `security/JwtUtils.java` | MATCH |
| `SecurityConfig` | 02-architecture | U-006 | `config/SecurityConfig.java` | MATCH (+ RestClient.Builder bean, §2.2) |
| `LdapUcsService` | 02-architecture | U-007 | `service/LdapUcsService.java` | MATCH |
| `AuthUserController` | 02-architecture | U-008 | `controller/AuthUserController.java` | MATCH (method name, §3.1) |
| DTOs (LoginRequest, JwtResponse, MessageResponse) | 02-architecture | U-004 | `dto/*.java` | MATCH |
| `application.yaml` config | 06-constraints | U-008 | `application.yaml` | MATCH (all bare `${VAR}`, §5.2) |
| `pom.xml` deps | 06-constraints | U-001 | `pom.xml` | MATCH |

### 5.2 Configuration Verification (application.yaml vs §D-002)

| Property | Form | Status |
|----------|------|--------|
| `spring.datasource.url/username/password` | bare `${VAR}` (no default) | MATCH — fail-fast if unset |
| `coresystem.app.jwtSecret` | bare `${JWT_SECRET}` | MATCH |
| `coresystem.ldap.*` (10 keys) | bare `${VAR}` | MATCH |
| `jwtExpirationMs`, `driver-class-name`, `ddl-auto`, `show-sql`, `application.name` | literal | MATCH (non-secret) |

**§D-002 compliance:** No property carries a secret/URL as a committed default. All secrets live in the gitignored `.env` (verified `git check-ignore .env` → ignored). The committed template (currently named `.env copy.example`) contains only blank `export VAR=` + non-secret UAT URLs.

### 5.3 API Contract Verification

| Endpoint | Spec | Code | Status |
|----------|------|------|--------|
| `POST /api/auth/dologin` | `02-architecture.md:73-108` | `AuthUserController.java:54-108` | MATCH |

- Request `{uname, pass}` — MATCH
- Success 200 `{token, type:"Bearer ", id, uname, mitKode, urole:"ROLE_<n>"}` — MATCH (verified live: `{id:1561, mitKode:"001", urole:"ROLE_0"}`)
- 400 null-result → `"Failed to connect to LDAP service"` — MATCH
- 400 non-success → spec says `responseDescription`, code says `"Authentication failed"` (generic) — **DRIFT §4.2**
- 401 exception → `"Authentication failed"` — MATCH

### 5.4 Dependency Verification (pom.xml vs U-001)

All 7 required deps present with correct versions/scopes: `spring-boot-starter-web`, `-security`, `-data-jpa`, `org.postgresql:postgresql` (runtime), `jjwt-api/impl/jackson` 0.12.6 (impl+jackson runtime). MATCH.

### 5.5 Test Verification

| Test | Type | Status |
|------|------|--------|
| `CoresystembackendApplicationTests` | `@SpringBootTest` (excl DataSource/Hibernate; mocks LdapUcsService + UserRepository + **JwtUtils**) | MATCH — 18/18 green |
| `AuthLoginIntegrationTest` | `@SpringBootTest` + manual MockMvc (mocks LdapUcsService + JwtUtils + UserRepository) | MATCH |
| `SecurityConfigTest` | `@SpringBootTest(classes=SecurityConfig.class)` scoped | MATCH |
| `JwtUtilsTest` | pure unit | MATCH |
| `LdapUcsServiceTest` | pure unit (MockRestServiceServer) | MATCH |
| `AuthUserControllerTest` | pure unit (Mockito) | MATCH |

Full suite: **18/18 pass** (verified `mvn test` this session).

---

## 6. Open Questions Status (post-v1.3)

All 17 OQs closed: **15 resolved + 2 deferred**, 0 open. No implicit-only resolutions remain (the v1.3 pass formalized all previously-implicit ones). Two deferred (post-v1, by PO decision): OQ-FL-2 (rate-limiting), OQ-OV-1 (quantitative NFR). Neither is a drift — both are documented decisions.

---

## 7. Recommendations Summary

All recommendations below have been **APPLIED** (commits `bffab3c` for P2, this follow-up for P3). Zero code changes were required — the code was always the source of truth and correct; the vault docs caught up.

| Priority | Finding | Action | Type | Status |
|----------|---------|--------|------|--------|
| P2 | `.env copy.example` broken filename | `git mv ".env copy.example" .env.example` | repo hygiene | ✅ DONE |
| P2 | DBML + prose said `users`, code = `mojf_users` | Update `03-data-model.md` + `02-architecture.md` + `01-overview.md` + `04-flows.md` | vault-doc | ✅ DONE |
| P2 | Error body spec said `responseDescription`, code = generic | Update `02-architecture.md:103` + `04-flows.md` DoD | vault-doc | ✅ DONE |
| P3 | Stale "OQ-DM-1 pending" in OQ-AR-2 block | Edit `02-architecture.md:129` | vault-doc | ✅ DONE |
| P3 | F-U-001 DoD `[INFERRED]` for success code | Lift to `[VERIFIED]` in `04-flows.md` | vault-doc | ✅ DONE |
| P3 | `RestClient.Builder` bean undocumented | Note in `02-architecture.md` SecurityConfig row | vault-doc | ✅ DONE |
| P3 | Method name `login` vs `authenticateUserUCS` | Update U-008 spec to `login` | vault-doc | ✅ DONE |

**Remaining drift: NONE.** The only historical artifacts not edited are `bolts/U-008/{bolt-report,dispatch-prompt}.md` — these are immutable execution records (what was requested/delivered at bolt time), not live spec, so `authenticateUserUCS` there is correct history.

---

## 8. Confidence Ratings

| Category | Confidence | Rationale |
|----------|------------|-----------|
| Component presence | HIGH | Direct file verification (agent inventory + manual read) |
| API contract compliance | HIGH | Live HTTP test confirms request/response shapes |
| Configuration compliance (§D-002) | HIGH | Every yaml property verified bare-placeholder; `.env` gitignored |
| OQ resolution status | HIGH | vault.json validated: 15 resolved + 2 deferred, 0 open |
| Spec-body staleness (migration gaps) | HIGH | Direct line-level comparison of DBML/prose vs code |
| Filename regression | HIGH | `git show --stat 3fc32f5` confirms the rename |

---

## Appendix A: Files Analyzed

**Vault:** `vault.json`, `00-index.md`, `01-overview.md`, `02-architecture.md`, `03-data-model.md`, `04-flows.md`, `05-decisions.md`, `06-constraints.md`

**Source:** `entity/Users.java`, `repository/UserRepository.java`, `controller/AuthUserController.java`, `service/LdapUcsService.java`, `security/JwtUtils.java`, `config/SecurityConfig.java`, `dto/{JwtResponse,LoginRequest,MessageResponse}.java`, `application.yaml`, `pom.xml`, `.env copy.example`

**Tests:** all 6 test files under `src/test/java/.../`

**Git:** commits `36a1c7f` (OQ resolve + mode migrate), `2f2a76f` (§D-002 .env hardening), `3fc32f5` (.env integration fix — introduced filename rename)

---

**END OF DRIFT REPORT** — read-only; vault and code were not modified.
