---
type: index
doc_id: 00-index
vault_version: "1.2"
project_slug: jwt-login
tags: ["vault/jwt-login", "doc/index"]
---

# Vault: coresystembackend — JWT Login

**Project**: coresystembackend (Spring Boot 4.1.1-SNAPSHOT, Java 21) — fitur login berbasis JWT, replikasi pola autentikasi aplikasi referensi newmojf (LDAP UCS → JWT).

## Phase context

**Phase:** 1 of 1
**Project type:** single-phase (greenfield — Mode B `--from-prompt`, no legacy-rebuild phasing)

---

## Executive Summary

Vault ini menspec fitur login JWT untuk coresystembackend: endpoint `POST /api/auth/dologin` menerima `{uname, pass}`, mengautentikasi via LDAP UCS (pola newmojf `LDAP_UCS_Utils.authLDAPNew`), menerbitkan JWT dari uname, lalu lookup data user dari tabel `users` dan mengembalikan `JwtResponse` field verbatim newmojf `{token, type="Bearer ", id, uname, mitKode, urole}` (nama field final → OQ-AR-7). Replikasi pola newmojf dengan adaptasi wajib ke stack Spring Boot 4.x: `jakarta.persistence` (bukan `javax`), Spring Security 7.x `SecurityFilterChain` bean (bukan `WebSecurityConfigurerAdapter`), constructor injection (bukan field `@Autowired`), paket `entity/` (bukan `model/`). Saat ini coresystembackend skeleton tanpa HTTP layer — perlu penambahan starter `-web`, `-security`, `-data-jpa`, driver PostgreSQL, dan lib JWT (`jjwt`).

## Project Readiness Status

- [x] PRD — seed-PRD draft (`source/seed-PRD.md`), bukan stakeholder-signed
- [ ] Figma — tidak dikonsumsi (api-only, tidak ada UI)
- [x] Tech stack — defined (Spring Boot 4.x snapshot, Java 21, Maven, pack=spring.md)
- [ ] Sign-off — 0/Y stakeholders
- Open Questions: **17** total — **P1: 4** (all resolved) · **P2: 6** (all resolved) · **P3: 7** (5 resolved, 2 deferred) → **0 open** (15 resolved, 2 deferred post-v1)

## Vault Lock Status

- **PROJECT_SHAPE**: `api-only` (backend REST login API; tidak ada layer FE/mobile — `HAS_UI_COMPONENTS=false`)
- **IMPLEMENTATION_MODE**: `existing` (migrated from `new` 2026-07-23 — jwt-login vault fully implemented: 9/9 units, 18/18 tests green, login verified live HTTP 200 for orisys06, commit de3f828)
- **mode_migrate_after**: COMPLETED — code landed on main branch; migration new→existing done (v1.3)
- **PRD_STATUS**: `draft`
- **OUTPUT_MODE**: `compact`
- **design_system_flags**: `{HAS_UI_COMPONENTS: false, HAS_TOKENS: false, HAS_A11Y: false, HAS_VOICE_BRAND: false}` — api-only, tidak ada design-system section (sesuai anti-halu rail: no shape-based defaulting)

## Reading paths by role

- **Architect**: `02-architecture` → `03-data-model` → `05-decisions` → `06-constraints`
- **Dev (BE)**: `02-architecture` → `03-data-model` → `04-flows`
- **QA**: `04-flows` (fokus Definition of Done per flow)
- **PM / Business Owner**: `00-index` → `01-overview` → `05-decisions`

## Reading order

1. `00-index` — navigasi + OQ roll-up (doc ini)
2. `01-overview` — apa, siapa, kenapa, success criteria
3. `02-architecture` — komponen per layer + API contract + starterkit binding
4. `03-data-model` — entity users (DBML) + adaptasi namespace
5. `04-flows` — flow login + Mermaid + DoD
6. `05-decisions` — ADR (LDAP, JWT, jakarta migration, security 7.x)
7. `06-constraints` — technical/business/NFR + pack Hard Rules

## Anti-hallucination rules for dev / dev-AI consumers

- Setiap claim menelusuri ke `seed-PRD.md`, file referensi newmojf (`AuthUserController.java`, `mojf_users_Model.java`, `application-test.properties`), atau `codebase-map.md`. Tidak ada yang diarang.
- newmojf adalah **referensi pola**, BUKAN target rebuild. Konvensi legacy newmojf (`javax.persistence`, field `@Autowired`, `model/`, `.properties`) **HARUS diadaptasi** ke pack Spring Boot 4.x — jangan salin verbatim.
- Yang tidak explicit di brief/source → Open Question, bukan tebakan.
- LDAP/DB/secret adalah P1 business OQ — blocking untuk runtime, tapi tidak memblok pembuatan spec.

## Glossary

- **JWT** — JSON Web Token, token stateless yang membawa klaim (di sini: uname).
- **LDAP UCS** — layanan autentikasi UCS yang dipakai newmojf (`LDAP_UCS_Utils.authLDAPNew`); endpoint OpenAPI bankmega (OAuth2 password-grant token + `verifypassword` GET, UAT: `openapidev2.bankmega.local:15000`). ⚠️ Kontrak `responseCode` sukses `[INFERRED]` — kode "00"/"01" tidak terverifikasi di newmojf (OQ-FL-1 v1.2); failure codes verified 502/503/401.
- **jakarta.persistence** — namespace JPA untuk Jakarta EE 10 (Spring Boot 4.x); menggantikan `javax.persistence` lama.
- **SecurityFilterChain** — bean Spring Security 7.x yang menggantikan `WebSecurityConfigurerAdapter` (dihapus di Spring 6).
- **pack** — file konvensi framework (`framework-conventions/spring.md`) yang jadi acuan struktur/naming/idiom.
- **DoD** — Definition of Done, checklist observable per flow (kontrak QA).

## Auto-Classification Review

> Total classified: 17 OQs. Auto-resolution active: 0 (skeleton kosong, tidak ada tech high-confidence yang bisa auto-resolve via scan). Manual review recommended: 9 (tech medium/low — butuh keputusan runtime/konfirmasi user). Business P1 blocking: 4.

| OQ-ID | Question | Auto-tagged | Confidence | Action |
|---|---|---|---|---|
| OQ-AR-1 | LDAP UCS infra aktif untuk coresystembackend? | business / blocking | high | **RESOLVED v1.2** — pakai infra existing newmojf (`openapidev2.bankmega.local:15000`) |
| OQ-AR-2 | Target DB: PostgreSQL newmojf existing atau DB baru? | business / blocking | high | **RESOLVED v1.2** — pakai DB newmojf existing; migrate to new DB post-deploy |
| OQ-AR-3 | jwtSecret: hardcode vs env var? | tech / recommend | medium | review — rekomendasi env var |
| OQ-AR-4 | lib JWT jjwt 0.12.x kompatibel Boot 4.x? (legacy API hazard) | tech / recommend | high | review — jjwt VERIFIED + rewrite API legacy |
| OQ-AR-5 | CORS: wildcard @CrossOrigin vs SecurityFilterChain config? | tech / recommend | medium | review — rekomendasi CORS config eksplisit |
| OQ-AR-6 | Prasyarat LDAP UCS (AES key, cred, URLs) tersedia? | business / blocking | high | **RESOLVED v1.2** — tersedia via `DB_Connection.properties` newmojf; externalize secret |
| OQ-AR-7 | Nama field DTO response: verbatim mitKode/urole vs rename? | business / blocking | medium | blocking — PO/architect (kontrak API) |
| OQ-DM-1 | Tabel entity `users` vs `mojf_users`? | tech / recommend | medium | review — rekomendasi `users` |
| OQ-DM-2 | Update last_login saat login? | tech / recommend | low | review — rekomendasi tidak (v1 read-only) |
| OQ-DM-3 | DDL: ddl-auto=none vs Flyway? | tech / recommend | medium | review — rekomendasi none |
| OQ-FL-1 | responseCode "00"/"01" LDAP UCS contract valid? | business / blocking | high | **RESOLVED v1.2 (correction)** — endpoint verified; "00"/"01" `[INFERRED]` (tidak terverifikasi di newmojf) |
| OQ-FL-2 | Rate-limiting/lockout setelah N gagal? | business / blocking | low | review — konfirmasi PO/security |
| OQ-FL-3 | Sanitasi error body LDAP (info leakage)? | business / blocking | medium | blocking — PO/security |
| OQ-CN-1 | Compliance regime (PDP-Indonesia)? | business / blocking | low | review — konfirmasi PO |
| OQ-CN-2 | Lombok @Slf4j vs SLF4J manual? | tech / recommend | medium | review — rekomendasi SLF4J manual |
| OQ-DC-1 | JWT bawa uname-only vs role claim? | tech / recommend | medium | review — rekomendasi uname-only v1 |
| OQ-OV-1 | Success criteria kuantitatif (latency/RPS)? | business / blocking | low | review — konfirmasi PO |

## Open Questions roll-up

> Total: **17 Open Questions** across 6 docs. Sorted by category, then P1 → P2 → P3 within each.

### Auth & infra (PRIORITY-1)
- [x] **OQ-AR-1** [P1] [business] [conf: high]: coresystembackend belum punya infra LDAP UCS aktif — pakai LDAP UCS newmojf yang sama, atau perlu mock/fallback auth DB untuk dev? `[02-architecture.md]` → Resolved v1.2: pakai infra LDAP UCS existing newmojf (`openapidev2.bankmega.local:15000` UAT); dev tanpa akses jaringan internal → mock via `@Profile("dev")` stub.
- [x] **OQ-AR-2** [P1] [business] [conf: high]: target database — sambung ke PostgreSQL newmojf existing (10.95.1.43:5432/newmojf) atau DB baru khusus coresystembackend? Tabel users sudah berisi data user terdaftar? `[02-architecture.md]` → Resolved v1.2: pakai DB newmojf existing (`jdbc:postgresql://10.95.1.43:5432/newmojf`); migrasi ke DB baru = fase pasca-deploy.
- [x] **OQ-AR-6** [P1] [business] [conf: high]: prasyarat integrasi LDAP UCS — AES key, client credentials, partnerId/channelId, urlToken/urlVerifyPassword harus tersedia; dari mana? `[02-architecture.md]` → Resolved v1.2: tersedia via `DB_Connection.properties` newmojf (di-load per-env oleh `DB_Connection.java`); secret WAJIB externalize (B-003/D-002).
- [x] **OQ-FL-1** [P1] [business] [conf: high]: kontrak response LDAP UCS (`responseCode`/`responseDescription`, kode sukses "00"/"01") — valid? Endpoint LDAP UCS apa yang dipakai? `[04-flows.md]` → Resolved v1.2 (with correction): endpoint verified; ⚠️ kode sukses "00"/"01" `[INFERRED]` (tidak terverifikasi di newmojf — `authLDAPNew` return JSON mentah UCS); konfirmasi via integration-test; failure codes verified 502/503/401; B-007 apply.

### Config, naming & API contract (PRIORITY-2)
- [x] **OQ-AR-3** [P2] [tech / recommend] [conf: medium]: `jwtSecret` — hardcode (referensi) atau externalize via env var? `[02-architecture.md]` → Resolved v1.3: externalize via `${JWT_SECRET:base64-dev-default}` (commit de3f828); default sebelumnya invalid Base64 → fixed.
- [x] **OQ-AR-4** [P2] [tech / recommend] [conf: high]: lib JWT jjwt 0.12.x kompatibel Boot 4.x? (newmojf VERIFIED jjwt tapi API legacy — butuh rewrite). `[02-architecture.md]` → Resolved v1.3: jjwt 0.12.6 kompatibel, API rewrite (signWith(Key)+verifyWith) verified live (commit f4b87df U-005).
- [x] **OQ-AR-5** [P2] [tech / recommend] [conf: medium]: CORS — replikasi `@CrossOrigin(origins="*")` wildcard atau SecurityFilterChain config eksplisit? `[02-architecture.md]` → Resolved v1.3: CORS eksplisit via SecurityFilterChain + CorsConfigurationSource (origins localhost:3000/8080, bukan wildcard) (commit 574fce8 U-006).
- [x] **OQ-AR-7** [P2] [business] [conf: medium]: nama field DTO response — verbatim newmojf (`mitKode`/`urole`) atau rename (`kodeMitra`/`role`)? `[02-architecture.md]` → Resolved v1.3: verbatim newmojf (`mitKode`/`urole`); live response konfirmasi (commit 6783558 U-008 / de3f828).
- [x] **OQ-DM-1** [P2] [tech / recommend] [conf: medium]: nama tabel entity — `users` (pack) atau `mojf_users` (referensi)? `[03-data-model.md]` → Resolved v1.3: `@Table(name="mojf_users")` (DB existing newmojf); fixed dari drift (commit 810fd54 — P1).
- [x] **OQ-FL-3** [P2] [business] [conf: medium]: sanitasi error body LDAP — echo verbatim `responseDescription` atau map generic (anti info-leakage)? `[04-flows.md]` → Resolved v1.3: map ke generic "Authentication failed", raw detail log server-side only (§B-007) (commit 6783558 U-008 + de3f828).

### Persistence, design & refinement (PRIORITY-3)
- [x] **OQ-DM-2** [P3] [tech / recommend] [conf: low]: update `last_login` saat login sukses? `[03-data-model.md]` → Resolved v1.3: tidak update (read-only, replikasi newmojf) (commit 6783558 U-008).
- [x] **OQ-DM-3** [P3] [tech / recommend] [conf: medium]: DDL — `ddl-auto=none` atau Flyway? `[03-data-model.md]` → Resolved v1.3: `ddl-auto=none` (skema existing dikelola eksternal) (commit 6595b3b U-008).
- [~] **OQ-FL-2** [P3] [business] [conf: low]: rate-limiting/lockout setelah N percobaan gagal? `[04-flows.md]` → Deferred v1.3 (PO decision): defer ke post-v1; mitigasi sementara via network-layer rate-limit.
- [x] **OQ-DC-1** [P3] [tech / recommend] [conf: medium]: JWT bawa uname-only atau role claim? `[05-decisions.md]` → Resolved v1.3: subject=uname only; role via DB lookup saat validasi (commit f4b87df U-005).
- [x] **OQ-CN-1** [P3] [business] [conf: low]: regime compliance (PDP-Indonesia/lainnya) untuk kredensial login? `[06-constraints.md]` → Resolved v1.3 (PO/compliance): PDP Law Indonesia (UU PDP); v1 sudah HTTPS+externalize+generic-error+no-log-password.
- [x] **OQ-CN-2** [P3] [tech / recommend] [conf: medium]: Lombok `@Slf4j` atau SLF4J manual? `[06-constraints.md]` → Resolved v1.3: SLF4J manual (no Lombok dep, zero-dependency) (commits U-005/007/008).
- [~] **OQ-OV-1** [P3] [business] [conf: low]: success criteria kuantitatif (latency/RPS)? `[01-overview.md]` → Deferred v1.3 (PO decision): belum ada target; v1 pakai default wajar (live ~1.4s end-to-end).

## Source documents

- `source/seed-PRD.md` — brief verbatim + elaborasi (Mode B)
- `/home/ikbalgazalba/AI/Project/newmojf/.../controller/AuthUserController.java` — pola controller login (referensi)
- `/home/ikbalgazalba/AI/Project/newmojf/.../model/mojf/mojf_users_Model.java` — struktur tabel user (referensi)
- `/home/ikbalgazalba/AI/Project/newmojf/.../model/mojf/response/JwtResponse.java` — DTO response fields (referensi; advisor ADV-001)
- `/home/ikbalgazalba/AI/Project/newmojf/.../security/jwt/JwtUtils.java` — impl JWT jjwt + legacy API (referensi; advisor ADV-002)
- `/home/ikbalgazalba/AI/Project/newmojf/.../utils/LDAP_UCS_Utils.java` — impl LDAP UCS (referensi; advisor ADV-005/006)
- `/home/ikbalgazalba/AI/Project/newmojf/.../configuration/security_Config.java` — legacy SecurityConfig (referensi; advisor ADV-008)
- `/home/ikbalgazalba/AI/Project/newmojf/.../application-test.properties` — config JWT + datasource (referensi)
- `.mega-sdd/codebase/codebase-map.md` — starterkit map coresystembackend (§7 spring pack)

## Changelog

### v1.2 (2026-07-22)

Resolved 4 P1 OQs via `resolve-oq` session (sourced from `newmojf/src/main/resources/DB_Connection.properties` + `DB_Connection.java` + `LDAP_UCS_Utils.java`, cross-source verified + stakeholder decision for OQ-AR-2).

- **Resolved** (4 entries):
  - OQ-AR-1 → coresystembackend pakai infra LDAP UCS existing newmojf (`openapidev2.bankmega.local:15000` UAT); dev tanpa akses jaringan internal → mock via `@Profile("dev")` stub (see `02-architecture.md`).
  - OQ-AR-2 → pakai DB newmojf existing (`jdbc:postgresql://10.95.1.43:5432/newmojf`); kredensial externalize via env var; migrasi ke DB baru = fase pasca-deploy (see `02-architecture.md`).
  - OQ-AR-6 → prasyarat LDAP UCS tersedia via `DB_Connection.properties` newmojf (di-load per-env oleh `DB_Connection.java`); secret WAJIB externalize (B-003/D-002), jangan salin `.properties` verbatim (D-001) (see `02-architecture.md`).
  - OQ-FL-1 → endpoint LDAP UCS verified; **KOREKSI**: kode sukses "00"/"01" `[INFERRED]` (tidak terverifikasi di newmojf — `authLDAPNew` return JSON mentah UCS); failure codes verified 502/503/401; B-007 apply (see `04-flows.md` + glossary).
- **Konsekuensi (dependency unlocked, not auto-resolved)**: OQ-AR-2 mendorong cabang keputusan OQ-DM-1 → `@Table(name="mojf_users")` (DB newmojf existing). OQ-DM-1 tetap pending untuk review final.
- **Still open after this session**: 13 (0 P1; 6 P2; 7 P3).

> No vault lock was active (Vault Lock Status = DRAFT-equivalent; no `🔒 LOCKED` line). No re-sign-off required.

## Last updated

2026-07-22
