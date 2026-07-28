# Project Constitution — acquisition

**Status**: Active
**Version**: 1.2.0 (extends api-platform constitution v1.1.1 with acquisition clauses §H; §A–§G inherited verbatim)
**Last reviewed**: 2026-07-27
**Sign-off**: Tech Lead / Security / Compliance (when relevant) — pending

> This constitution extends the project-wide rules established in `../api-platform/constitution.md` (§A–§G, which itself extends `../jwt-login/constitution.md` §A–§F). §A–§G are inherited verbatim and re-asserted below (same project, same codebase — Spring Boot 4.1.1-SNAPSHOT + Java 21). §H adds acquisition-epic-specific clauses (credit-origination MCF/FINCORE rebuild).

## §A. Coding standards (Non-negotiable) — inherited

- A-001: Class PascalCase + suffix layer (`Controller`/`Service`/`Repository`/`Config`); method/field camelCase.
- A-002: Paket layer standar: `controller/`, `service/`, `repository/`, `entity/`, `dto/`, `config/`. Untuk acquisition modulith: package boundary per modul (Spring Modulith) — `acquisition.intakecas`, `acquisition.creditanalysis`, dst.
- A-003: Test files di `src/test/java/<pkg>/` mencerminkan struktur main. Modulith slice test per boundary.
- A-004: Tidak ada `System.out.println()` — pakai SLF4J `LoggerFactory.getLogger()` + correlation id per `credit_id` (ADR-11).

## §B. Security baselines — inherited + extended

- B-001..B-009: inherited dari api-platform (jakarta.persistence, SecurityFilterChain, jwtSecret externalize, CSRF, LDAP password, no trust-all, no echo exception, actuator health permitAll, Swagger permitAll v1).
- **B-010 (NEW)**: Regulated gates (AML/blacklist, SLIK, DSR, verification, chassis/BAST) = **FAIL-CLOSED** (OQ-REG-06 ✅). Dependency gagal/error/throw mid-check = BLOCK, tanpa kecuali. `BE-00 §7.3, §8.2`.
- **B-011 (NEW)**: No-self-approval (D-01 S11) enforced **dua lapis**: Flowable task assignment + Java service guard (`403 SELF_APPROVAL_BLOCKED`). `ADR-07, ADR-13`.
- **B-012 (NEW)**: Super-user DIHAPUS (D-09) — tidak ada role/grant/bypass setara; audit override historis WAJIB survive migrasi (`legacy_super_user_override`). `BE-00 §2.2, §8.1`.
- **B-013 (NEW)**: Branch pick WAJIB di-bind session/token + **re-verify server-side** terhadap authorized-branch list (tutup OQ-SHELL-02). `ADR-07, BE-00 §7.5, §8.2`.
- **B-014 (NEW)**: Hapus anti-pattern legacy — `EXECUTE AS 'sa'`, `sp_OACreate` HTTP hardcoded-IP, cross-DB linked-server DML, plaintext DB credential, `PasscodeBiBca` secret. Semua integrasi eksternal via ACL app-tier (ADR-05). `BE-00 §8.2`.

## §C. Architecture invariants — inherited + extended

- C-001..C-005: inherited (controller↔controller via service, entity not exposed, logic in @Service, constructor injection, @Transactional multi-step).
- **C-006 (NEW)**: Cross-module communication HANYA via domain event (outbox) atau API publik modul — NO direct repository/tabel access modul lain; NO cross-module JOIN di write path; cross-module ref via business key `credit_id`. ArchUnit menolak pelanggaran (ADR-03). `BE-00 §5, ARCH-PROP §5`.
- **C-007 (NEW)**: Zero stored procedure (ADR-02) — logika bisnis 100% application layer; DB = penyimpanan + constraint integritas.
- **C-008 (NEW)**: PO minting dimiliki **04-contract** (exactly one PO per approval, trigger `MemoApproved`); JANGAN mint PO dari modul credit-analyst (fix GOTCHA-8). `BE-04 §5.3, D-01 S13`.
- **C-009 (NEW)**: Downstream STEP 16 = **PULL** (read-API + event feed); Acquisition tidak pernah menulis ke sistem downstream (ADR-09). `BE-00 §1.1, GT v2 STEP 16`.

## §D. Anti-patterns (from legacy) — inherited + extended

- D-001..D-005: inherited (no verbatim newmojf, no hardcoded secret, no new dep tanpa review, no bake secret/trust-store/.env ke image, no host-install Maven).
- **D-006 (NEW)**: JANGAN replika bug legacy do-not-replicate (GOTCHA-1..18): narrow screening, fail-open gate, car/motor code split, destructive RAC delete, no-dedup NIK, positional related-person, dead `*_cas` SP, GL silent-commit, BPKB guard disabled, IA string-hack. Setiap bug WAJIB diperbaiki + regression test. `BE-00 §1.3, BE-0x gotchas`.
- **D-007 (NEW)**: JANGAN hardcode routing/hierarki/matriks per-produk di BPMN — baca delegate dari `cfg_` tables (data change, NOT deploy). `ADR-13, DB-CONVENTIONS §8`.
- **D-008 (NEW)**: JANGAN sentuh Flowable `ACT_*` runtime tables manual — engine-owned; data bisnis TIDAK di variabel proses kecuali key (`credit_id`, task ref). `ADR-13, DB-CONVENTIONS §8`.

## §E. Performance constraints — inherited + extended

- E-001..E-002: inherited (response time / image size = unspecified, no claim without source).
- **E-003 (NEW)**: KPI baseline (A-13) = prasyarat Phase 2 — ukur dari data legacy (SLA per step, lead time intake→NPP, latency RAC, throughput approval) sebelum target ditetapkan. PRD TIDAK memfabrikasi angka. `BE-00 §8.7(c)`.

## §F. Compliance — inherited + extended

- F-001..F-002: inherited (PDP-Indonesia regime, audit `last_login`).
- **F-003 (NEW)**: Audit trail approval WAJIB `log_approval_history` append-only (actor, timestamp, level, aksi, alasan) — independent dari Flowable engine (kebutuhan regulatori). `BE-00 §8.1, ADR-13`.
- **F-004 (NEW)**: Data identitas + laporan OJK (kolektibilitas, sektor ekonomi, NIK/NPWP) residen Indonesia; kepatuhan OJK/APU-PPT; retensi audit trail sesuai regulasi multifinance. `BE-00 §8.5`.
- **F-005 (NEW)**: Migrasi = no-data-left-behind (112/112 tabel legacy di-extract 1:1 + arsip permanen); DISCARD hanya schema target, TIDAK PERNAH data; final drop di-gate profiling data prod (OQ-MIG-05). `ADR-15, DATA-MIGRATION-PLAN`.

## §G. Containerization clauses — inherited (from api-platform)

- G-001..G-008: inherited (multi-stage Dockerfile eclipse-temurin:21, .dockerignore, compose env_file+trust-store volume, healthcheck /actuator/health, spring profiles dev/prod, additive permitAll, CORS externalize).

## §H. Acquisition clauses (NEW — this epic)

- **H-001**: Schema WAJIB `docs/DB-CONVENTIONS.md` (ADR-14) — satu schema, prefix `mst_/trx_/cfg_/log_/map_/stg_/out_`, singular snake_case English, PK `id BIGINT IDENTITY` + business key `credit_id` terpisah, declared FK wajib, audit columns wajib, satu kolom `status`. `BE-00 §7, DB-CONVENTIONS`.
- **H-002**: `credit_id` = business key `[LOCKED]` (format `branch(5)+YY+MM+SEQ(5)` 14-char, OQ-GT-02 ✅) — bukan PK teknis; mint-once idempotent di modul intake (D-01 S8); generator tunggal (BR-33). `BE-01 §3.1.13, ADR-08`.
- **H-003**: `trans_type_id` = external-FK `[LOCKED]` char-for-char match `FC_MSTAPP_MCF`; composition owned ONLY modul 02 (consumed 03); ladder `AA00000001` distinct — jangan dicampur. `BE-02, BE-03 BR-AC-1/BR-AC-6`.
- **H-004**: Field `[LOCKED]` (regulatori/external-FK/governance) dipertahankan 1:1 additive-only; `[ARTIFACT]` dibuang setelah sign-off stakeholder; `[OPEN]` → OQ (jangan diselesaikan diam-diam); **USULAN** = desain baru belum diputuskan. `BE-00 §Disiplin penanda`.
- **H-005**: NPP activation STEP 15 = **atomik** (D-01 S15) — agreement active + PK + jurnal/AR Card + master loan + upsert `mst_customer` + outbox (Passnet/email) dalam satu unit-of-work; error = ROLLBACK penuh (fix silent-commit legacy). `BE-05 §5.3, ARCH-PROP §7`.
- **H-006**: Role census D-10 `[LOCKED]`: CMO · Marketing Head · Credit Analyst · Kepala Cabang · Credit (Admin); hierarki approval by skala risiko. Tidak ada super-user (D-09). `BE-00 §2.1, §2.2`.
- **H-007**: OQ-ID PRD DIPRESERVE sebagai tag stabil (OQ-GT-*, OQ-MEET-*, OQ-CRSCORE-*, OQ-CMPO-*, OQ-NPP-*, dst.) — jangan renumber ke `OQ-{DOC}-{N}`; `doc` field vault.json = vault file tempat roll-up. `vault-contract §id-stability`.
- **H-008**: Migrasi = simulation-first + reconciliation gate (ADR-15); tiap sprint run simulasi dengan report (row count, financial sums, checksum `[LOCKED]` zero-diff, FK orphan=0); acceptance cutover = 2 run berturut-turut zero-diff. `DATA-MIGRATION-PLAN`.
