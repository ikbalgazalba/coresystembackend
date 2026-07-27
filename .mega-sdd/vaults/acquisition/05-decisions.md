# 05 — Decisions

> **TL;DR**: ADR-01..15 dari `docs/ARCHITECTURE-PROPOSAL.md §4` (status *Proposed* — menunggu rekonsiliasi ITEC D-11) + keputusan meeting D-01..D-12 (BE-00 §11.0). Format: Keputusan → Konteks → Konsekuensi → Sumber.

## ADR register

### ADR-01 — Modular Monolith, bukan microservices penuh
- **Keputusan**: satu deployable Spring Boot; modul = bounded context PRD (7+1); boundary ter-enforce (Spring Modulith + ArchUnit).
- **Konteks**: alur sekuensial 16-STEP; atomisitas STEP 15; satu tim BE. KB membolehkan "one modular service or several".
- **Konsekuensi**: (+) transaksi lokal titik kritis; (+) refactor boundary murah; (−) satu blast-radius deploy — dimitigasi modul + test per modul; jalur evolusi ke service terpisah tersedia (§9 ARCH-PROP).

### ADR-02 — Business logic 100% application layer; zero stored procedure
- **Keputusan**: tidak ada logika bisnis di DB. DB = penyimpanan + constraint integritas.
- **Konteks**: 473 SP legacy = lokus unmaintainability + bug (HTTP T-SQL, `EXECUTE AS 'sa'`, linked-server DML).
- **Konsekuensi**: (+) testable, versionable, satu bahasa; (−) migrasi perilaku SP → service perlu disiplin paritas (setiap SP acquisition hidup dipetakan ke service method di BE-0x §6).

### ADR-03 — Satu DB PostgreSQL, satu schema + prefix kelas tabel, ownership registry per modul *(REVISI 2026-07-14)*
- **Keputusan**: 1 instance/1 DB/1 schema; klasifikasi via prefix `mst_/trx_/cfg_/log_/map_/stg_/out_`; boundary di-enforce application-layer (Spring Modulith + ArchUnit) + table-ownership registry (BE-00 §6.3). Konvensi lengkap = `docs/DB-CONVENTIONS.md`.
- **Konteks**: butuh transaksi lintas modul STEP 15 + disiplin ownership. User pilih prefix-satu-schema (sederhana operasional) di atas schema-per-module.
- **Konsekuensi**: (+) atomisitas; (+) operasional DB sederhana; (−) boundary tidak ter-enforce di DB — mitigasi ArchUnit test (repository menulis tabel milik modul lain = tolak) + review schema rujuk DB-CONVENTIONS.

### ADR-04 — Transactional Outbox untuk SEMUA efek eksternal + idempotency per flow
- **Keputusan**: setiap efek keluar (Passnet, email blast D-03, notifikasi, event downstream) ditulis ke outbox **dalam transaksi yang sama** dengan mutasi domain; dispatcher terpisah retry + DLQ; consumer idempotent.
- **Konteks**: legacy fire-and-forget tanpa ack (Passnet), GL double-posting, email dari `sa`.
- **Konsekuensi**: idempotency-key per flow (RFA lock, RAC callback, committee approve, PO minting, NPP activation, GL posting).

### ADR-05 — Anti-Corruption Layer untuk 10 integrasi eksternal
- **Keputusan**: satu package `acl` adapter per sistem eksternal; kontrak payload `[LOCKED]`; transport bebas didesain ulang. (RAC async, biro orchestrator, DOKU app-tier HTTP, Passnet outbox+reconciliation, email least-privilege).
- **Sumber**: `50-integrations/*.md`, BE-00 §9.

### ADR-06 — State-machine engine tunggal, config-driven per produk
- **Keputusan**: satu engine status aplikasi (Draft→RFA→RAC→CA→Committee→CM/PO→Vertel→NPP-active) dengan konfigurasi per produk MACF: step aktif, gate, hierarki, varian car/motor & CF/syariah.
- **Konteks**: matriks per-product belum final (OQ-MEET-06) — justru karena itu harus konfigurasi, bukan kode; legacy duplikasi path car/motor dengan formula drift.
- **Konsekuensi**: (+) OQ-MEET-06 selesai tanpa perubahan kode; (+) IA = policy flag auditable; (−) engine harus dites property-based terhadap konfigurasi.

### ADR-07 — Security & AuthZ: RBAC 5 peran + maker-checker, server-side authoritative
- **Keputusan**: role model persis census D-10; **tidak ada super-user** (D-09); semua validasi/gating otoritatif server; no-self-approval di service (checker ≠ maker, `403 SELF_APPROVAL_BLOCKED`); branch scoping diverifikasi ulang server-side (tutup OQ-SHELL-02). Mekanisme auth/session final menunggu ITEC (abstraksi `AuthenticatedActor` + `RoleResolver`).
- **Sumber**: D-09, D-10, `60-frontend/60-app-shell-auth-navigation.md §9`, BE-07/FE-00.

### ADR-08 — Kontrak MOOFI→FINCORE (STEP 8): sync idempotent + credit_id minting
- **Keputusan**: endpoint ingest (a) mint `credit_id` (PK) via sequence/generator intake (format 14-char `branch(5)+YY+MM+SEQ(5)`, OQ-GT-02 ✅), (b) bentuk draft `Status RFA='0'` idempotent by referensi aplikasi mobile, (c) validasi payload (padanan `sp_validation_mobile_to_fincore` app-layer).
- **Sumber**: GT v2 STEP 8, BE-01.

### ADR-09 — Downstream delivery: PULL via read-API + event feed (bukan push)
- **Keputusan**: modul NPP ekspos read-API kontrak eligibility + event feed (`AgreementActivated` dst. dari outbox) yang di-poll/subscribe downstream. Acquisition tidak pernah menulis ke sistem downstream.
- **Sumber**: GT v2 STEP 16 `[LOCKED]`.

### ADR-10 — Next.js App Router + BFF-lite (FE — context only, PRD FE-*)
- **Keputusan**: App Router; server components read path; Route Handlers session/token + agregasi ringan; bukan BFF service berdiri sendiri.
- **Sumber**: PRD FE-00.

### ADR-11 — Observability & audit first-class
- **Keputusan**: (a) audit trail approval ke `log_approval_history` append-only; (b) structured logging + correlation id per `credit_id` menembus modul & ACL; (c) metric per gate (RFA, RAC latency, committee SLA, Vertel aging, NPP 30-day expiry); (d) event log outbox = sumber replay/rekonsiliasi.

### ADR-12 — Testing & quality gates
- **Keputusan**: unit + module test per boundary (Modulith slice); contract test ACL adapter (wiremock); property-based test state-machine vs konfigurasi produk; acceptance test = Given/When/Then BE-0x §9; arsitektur test `ApplicationModules.verify()` + ArchUnit menolak dependensi lintas modul ilegal & write tabel milik modul lain (input: registry BE-00 §6.3); BPMN process test (Flowable harness).

### ADR-13 — Workflow engine: Flowable embedded untuk approval/human-task *(BARU 2026-07-14)*
- **Keputusan**: approval/human-task layer (inbox, hierarki komite dinamis, maker-checker, RFA berlapis, deviasi, Vertel RFA, SLA aging, IA lane) dijalankan Flowable embedded di modulith. Lifecycle status aplikasi TETAP config-driven in-app (ADR-06) — engine mengorkestrasi *human task*, BUKAN menggantikan state machine domain.
- **Konsekuensi/rel** (`DB-CONVENTIONS.md §8`): BPMN versioned repo; matriks per-produk + hierarki dibaca delegate dari `cfg_` (data change, NOT deploy); variabel proses hanya key (`credit_id`); **`log_approval_history` TETAP audit otoritatif regulatori** (engine bukan satu-satunya sumber audit); `ACT_*` engine-owned JANGAN disentuh manual; no-self-approval + role census D-10 enforced dua lapis; kurva belajar BPMN = risiko diterima sadar (dimitigasi BPMN process test).
- **Sumber**: keputusan user 2026-07-14, D-01 S10-11, gap-entities (deviasi/IA), D-07.

### ADR-14 — Standarisasi schema via `DB-CONVENTIONS.md` *(BARU 2026-07-14)*
- **Keputusan**: seluruh schema target mengikuti `docs/DB-CONVENTIONS.md` (prefix kelas, snake_case English singular, PK `id` identity + business key terpisah, declared FK wajib, mapping tipe MSSQL→PostgreSQL, kolom audit wajib, satu kolom `status`, larangan shadow/print-counter/denormalisasi-identitas/temp-permanen). PRD BE-0x §3 = ground truth schema.
- **Sumber**: keputusan user 2026-07-14, do-not-replicate `hidden-gotchas.md`.

### ADR-15 — Migrasi legacy→baru: simulation-first dengan reconciliation gate *(BARU 2026-07-14)*
- **Keputusan**: migrasi = deliverable Phase 1 (bukan cutover): pipeline extract (`stg_legacy_*` 1:1 + `migration_batch_id`) → transform (mapping matrix PRD §3) → load, **dijalankan berulang sebagai simulasi** tiap sprint dengan reconciliation report otomatis (row count, financial sums, checksum `[LOCKED]` zero-diff, FK orphan=0, status-vocabulary, dedup NIK, prod-data profiling DISCARD, archive completeness 112/112). Acceptance cutover: 2 run berturut-turut zero-diff. Strategi cutover = OQ-MIG-01 [P1] (2 skenario `DATA-MIGRATION-PLAN.md §5`).
- **Dua prinsip tambahan (user directive 2026-07-14)**: (a) **no-data-left-behind** — 100% data legacy (112 tabel termasuk DISCARD) di-extract + diarsip permanen; DISCARD hanya schema target, bukan data; (b) **code-evidence ≠ data-evidence** — klasifikasi dead/DISCARD = asumsi arkeologi kode; final drop di-gate profiling data prod (OQ-MIG-05).

## Keputusan meeting D-01..D-12 (BE-00 §11.0 — TARGET-STATE, mengikat di atas legacy)

| ID | Keputusan | Marker | Dipakai di |
|---|---|---|---|
| D-01 | Target flow 15-step UREQ (dedup-lock, typed related-person, screening entry-time, RFA idempotent + event, ACL RAC, async ingest, routing OP+risk, self-approval blocked + IA lane, freeze finansial, PO tunggal, gate 30-hari, aktivasi atomik + PULL) | `[INTENT]` | §1.1, §4, §5, §7.4, §8, §10 |
| D-02 | Vertel step wajib antara PO dan NPP (STEP 14) | `[INTENT]` | §1.1, §3, §4, §4.1 |
| D-03 | Email blast ke dealer pasca STEP 15 | `[INTENT]` | §4, §9 #10, §10 P3 |
| D-04 | Dokumen PK terbentuk pasca STEP 15 | `[INTENT]` | §1.1, §10 P3 |
| D-05 | Master loan terbentuk saat aktivasi | `[INTENT]` | §1.2, §6.1, §10 P3 |
| D-06 | AR Card + jurnal terbentuk (output NPP wajib) | `[INTENT]` | §4, §8.4, §10 P3 |
| D-07 | Definisi step per-product MACF diperlukan | `[OPEN]` | §4, §10 (OQ-MEET-06) |
| D-08 | Menu Master (User, Dealer) masuk SoW | `[INTENT]` | §1.1, §9 #9, §10 P1 |
| D-09 | Super user DIHAPUS | `[LOCKED]` | §2.2, §6.2 #8, §8.1 |
| D-10 | Role census cabang: CMO, Marketing Head, Credit Analyst, Kepala Cabang, Credit (Admin); hierarki by skala risiko | `[LOCKED]` | §2.1, §3.1, §4.1 |
| D-11 | Arsitektur disiapkan ITEC Bank Mega (deadline 10 Jul 2026) | dependensi eksternal | §7.0, §8.6 |
| D-12 | Target stack: BE=Java, FE=Next.js; PRD split per audience | `[LOCKED]` | header, §7.0 |

## Sources

- `docs/ARCHITECTURE-PROPOSAL.md §4` (ADR-01..15), `§9` (jalur evolusi), `§10` (assumption register A-1..A-13)
- `docs/prd/acquisition/BE-00 §11.0` (D-01..D-12), `§11.1` (OQ register + RESOLVED)
- `docs/DB-CONVENTIONS.md` (ADR-14), `docs/DATA-MIGRATION-PLAN.md` (ADR-15)

## Open Questions

- **OQ-ARCH-STACK** [P1] — framework/transport/topologi (ITEC D-11); semua ADR status *Proposed* menunggu rekonsiliasi
- **OQ-MIG-01** [P1] — strategi cutover (drain vs in-flight)
- **OQ-MEET-06** [P1] — matrix per-product (D-07) — ter-absorb config engine (ADR-06)
- Lengkap di `00-index.md` roll-up
