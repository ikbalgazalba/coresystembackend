# 06 — Constraints

> **TL;DR**: Technical + business + non-functional constraints. Regulated gates = **fail-closed** (OQ-REG-06 ✅). Financial integrity = idempotent + compensating reversal + atomik. Audit trail mandatory. Security anti-pattern legacy WAJIB dihapus. Do-not-replicate bug legacy (GOTCHA-1..18) WAJIB diperbaiki dengan regression test.

## Technical constraints

- **BE language = Java** `[LOCKED]` (D-12); framework USULAN Spring Boot 3.x + Java 21 LTS (menunggu ITEC D-11, OQ-ARCH-STACK). Repo existing: Spring Boot 4.1.1-SNAPSHOT + Java 21 (`codebase-map.md`).
- **RDBMS = PostgreSQL** USULAN (final ITEC A-2); satu schema + prefix kelas (`docs/DB-CONVENTIONS.md`, ADR-14).
- **Transport** USULAN REST/JSON (OpenAPI 3) sinkron + outbox/message-relay async; envelope seragam `{ code, message, details?, correlation_id }` via `@RestControllerAdvice` + `ProblemDetail` (RFC 9457).
- **Workflow engine** = Flowable embedded (ADR-13); `ACT_*` engine-owned, JANGAN disentuh manual.
- **Zero stored procedure** (ADR-02) — logika bisnis 100% application layer; DB = penyimpanan + constraint integritas.
- **Cross-module** = event/outbox atau API publik modul; NO direct repository/tabel access modul lain (ADR-03); NO cross-module JOIN di write path; cross-module ref via business key `credit_id`.
- **AuthN** = LDAP corporate directory `[LOCKED]` (BR-SHELL-1, no password store lokal); branch WAJIB di-bind session/token + re-verify server-side (BR-SHELL-3, OQ-SHELL-02).
- **Migrasi** = simulation-first + reconciliation gate (ADR-15); no-data-left-behind (112/112 arsip permanen); code-evidence ≠ data-evidence.

## Business / domain constraints (regulatori & financial integrity)

### Regulated gates — FAIL-CLOSED (OQ-REG-06 ✅ RESOLVED 2026-07-07)
Setiap regulated gate (AML/blacklist, SLIK, DSR, verification, chassis/BAST) yang dependency-nya gagal/error/throw mid-check = **BLOCK**, tanpa kecuali. Default §7.3 BE-00 = kebijakan final; berlaku pre-phase global.

### Financial integrity (BE-00 §8.4)
- GL posting + jurnal/AR Card NPP (D-06): **idempotent** (`idempotency_key`), **compensating reversal**, **fail-closed** (fix silent no-op + commit-on-error legacy).
- BPKB custody guard di-enforce **dalam transaksi mutasi** (bukan endpoint terpisah dengan `RAISERROR` di-comment).
- Aktivasi kontrak STEP 15 **atomik** (D-01 S15): TrNpp aktif + dokumen PK + jurnal/AR Card + master loan + upsert `tr_CIF` konsisten dalam satu unit-of-work (outbox untuk efek eksternal Passnet/email). Error = ROLLBACK penuh.
- GL bank-ID crosswalk `[LOCKED]` verbatim (`'000001'→'00001'`); balance zero-sum `[LOCKED]` (BR-DISB-1); amortization formula `[LOCKED]` (BR-DISB-6 — method change = financial/regulatory change).
- NPL aging buckets 14/30/60/90/180 `[LOCKED]` regulatory convention.

### Field `[LOCKED]` (dipertahankan 1:1 additive-only)
- `credit_id` format `branch(5)+YY(2)+MM(2)+SEQ(5)` 14-char (OQ-GT-02 ✅); `trans_type_id` external-FK char-for-char (routing committee); `agreement_no` legal; `po_number` non-NULL at mint; `passnet_id` `'5'`+9-digit verbatim; `national_id` NIK VARCHAR(16) + NPWP 15/16 (regulatori); `chassis_no`/`engine_no` unique; OJK collectibility scale `1..5` (0→1, 1-90→2, 91-120→3, 121-180→4, >180→5); `ojk_economic_sector`/`debtor_group` codes; `application_type_id` 6 codes (01–06, hanya 02/03 exercised); photo-type vocabulary; KTP/NPWP dealer identity zero-diff; `mst_dealer_bank_reference.account_number`/`account_name` payout zero-diff; `map_transaction_type_gl` CoA zero-diff; `cfg_number_format` `CREDIT_ID` code_type.

### Idempotency points (BE-00 §7.4)
| Langkah | Kunci idempotensi |
|---|---|
| Minting `credit_id` (STEP 8) | mint-once per aplikasi MOOFI (dedup by source app id) |
| RFA lock (STEP 9) | idempotent; re-lock re-screen (D-01 S6) |
| RAC callback ingest (STEP 10) | by `application_id` + `decision_id` |
| Committee approve (STEP 12) | actor-identity enforced; no self-approval |
| PO minting (STEP 13) | trigger deterministik tunggal; exactly one PO per approval |
| Vertel submit (STEP 14) | re-submit saat chain "VK" open melanjutkan chain (BR-VERIF-4) |
| NPP activation (STEP 15) | hard BAST + chassis gate; aktivasi atomik; idempotency key `{credit_id}:JOURNAL_DISBURSEMENT:v1` |
| GL posting / jurnal + AR Card | `idempotency_key` + compensating reversal; fail-closed |
| Email blast dealer (D-03) | outbox + dedup per (agreement, template) — policy OQ-MEET-01 |
| Downstream (payment/BPKB/insurance) | kontrak eligibility eksplisit ATAU outbox event |
| Passnet sync (STEP 15) | outbox + reconciliation (ack/write-back) |

## Audit trail (BE-00 §8.1 — maker-checker)
- Setiap transisi approval (submit/approve/reject/correction) WAJIB tercatat ke audit approval — target `log_approval_history` (append-only, INSERT-only) dengan actor, timestamp, level, aksi, alasan. Engine (Flowable) BUKAN satu-satunya sumber audit (kebutuhan regulatori).
- Enforce identitas approver (no self-approval) di application layer (D-01 S11).
- Super-user & override: DIHAPUS (D-09) — tidak ada jalur bypass; audit override historis WAJIB survive migrasi (`legacy_super_user_override` di `log_approval_history`).

## Security — hapus anti-pattern legacy (BE-00 §8.2 — WAJIB, bukan port)
- `EXECUTE AS LOGIN='sa'` untuk email → least-privilege mail service (`[ARTIFACT]`).
- HTTP dari dalam T-SQL (`sp_OACreate` OLE Automation ke hardcoded IP `10.90.7.3:81`, DOKU) → HTTP client app-tier + owned response handling.
- Cross-DB/cross-company linked-server DML (`DELETE` ke DB Bank Mega) → kontrak API yang dimiliki; tanpa DML lintas-DB.
- Hapus plaintext DB credential (MINIAPI `appsettings.json`, OQ-EXTMASTERS-05); `PasscodeBiBca` = secret + security review (OQ-REF-05/OQ-DLRPTN-05).
- FE-berimplikasi-BE (jangan replika `60-frontend/60-app-shell §9`): (a) branch trust gap — re-verify server-side (OQ-SHELL-02); (b) auth cookie hard-coded role tak di-enforce `[ARTIFACT]` → discard, token/session actively-enforced; (c) tiga login-guard mati/rusak `[ARTIFACT]` → satu guard terpusat BE; (d) presence check kredensial client-side → validasi server-side.

## Ketahanan async (BE-00 §8.3)
- RAC callback + Passnet sync + email blast dealer (D-03): outbox transaksional + reconciliation; retry idempotent; dead-letter gagal permanen; scheduler terdokumentasi (legacy scheduler tak ter-lokasi — OQ-RAC-02, OQ-NOTIF-01). USULAN: transactional outbox table + relay (Spring scheduling / message relay), bukan side-effect request thread.

## Data residency / regulator (BE-00 §8.5)
- Data identitas + laporan OJK (kolektibilitas, sektor ekonomi, NIK/NPWP) **residen Indonesia**; kepatuhan OJK/APU-PPT; retensi audit trail sesuai regulasi multifinance.

## Do-not-replicate (bug legacy WAJIB diperbaiki — BE-00 §1.3 + BE-0x gotchas)
- **GOTCHA-1**: narrow blacklist screening → broad-match (OQ-ACQCAS-01 ✅)
- **GOTCHA-2**: fail-open regulated gate → fail-closed (OQ-REG-06 ✅)
- **GOTCHA-3**: LKK grade→weight must strictly decrease
- **GOTCHA-4**: OP threshold hardcoded → config
- **GOTCHA-5**: ladder credit-analyst (`AA00000001`) vs committee router confusion
- **GOTCHA-6**: `po_no` selalu NULL → assigned at mint
- **GOTCHA-7**: filter reject by reason text not status
- **GOTCHA-8**: PO dipicu dari modul credit-analyst (`CreditAnalystRepositoryEF.cs:692-708`) → milik 04
- **GOTCHA-9**: IA string-hack → policy flag auditable
- **GOTCHA-10**: car/motor code split → single engine config-driven
- **GOTCHA-11**: RAC destructive delete on re-open → idempotent re-screen
- **GOTCHA-12**: downstream push dari NPP → PULL via read-API + event feed (ADR-09)
- **GOTCHA-13**: Passnet fire-and-forget tanpa ack/write-back → outbox + reconciliation (ADR-04)
- **GOTCHA-15**: BAST gate legacy prosedural-only → hard in-transaction gate (D-01 S14, OQ-NPP-14 ✅)
- **GOTCHA-16**: no dedup NIK → dedup-at-intake lock (D-01 S1)
- **GOTCHA-17**: positional related-person → typed rows + role enum (D-01 S2)
- **GOTCHA-18**: dead `*_cas` SPs → discard
- **GL**: silent no-op + commit-on-error → idempotent + reversal + fail-closed
- **BPKB**: guard dinonaktifkan → enforce in-transaction
- **FE-berimplikasi-BE**: login guard mati, auth-cookie tak enforced, branch trust gap, client-side-only validation, sync-over-async, silent upload failure, session-affinity context

## Non-functional (menunggu baseline A-13)
- **KPI baseline** (A-13, prasyarat Phase 2): ukur dari data legacy — SLA per step approval, lead time intake→NPP, latency RAC, throughput approval. Target angka ditetapkan bareng bisnis di atas baseline. PRD TIDAK memfabrikasi angka.
- **Dependensi arsitektur eksternal** (D-11): dokumen arsitektur rebuild disiapkan tim ITEC Bank Mega. Semua USULAN §7.0 wajib di-reconcile sebelum Phase 1.
- Performance quantitative targets = `(unspecified)` sampai baseline + ITEC (sesuai constitution §E pattern: no claim without source).

## Sources

- `docs/prd/acquisition/BE-00 §8` (NFR), `§7.3` (error pattern + fail-closed), `§7.4` (idempotency), `§1.3` (reengineering mandate), `§11.1` (RESOLVED OQ)
- `docs/prd/acquisition/BE-0x §6` (aturan bisnis), `§8` (integrasi), `§9` (acceptance criteria)
- `docs/ARCHITECTURE-PROPOSAL.md §2.2` (do-not-replicate), `§10` (assumption register)
- `docs/DB-CONVENTIONS.md` (schema constraints)

## Open Questions

- **OQ-REG-02** [P1] — intake-only AML = signed-off control atau second screening di CA?
- **OQ-SLIK-05** [P1] — freshness 30-hari SLIK hard-block atau informational?
- **OQ-MEET-01/05** [P2] — email blast trigger/template/policy; expiry verifikasi konsekuensi
- **OQ-EXTMASTERS-05 / OQ-REF-05 / OQ-DLRPTN-05** [P1] — `PasscodeBiBca` + plaintext credential security action
- **OQ-ARCH-STACK** [P1] — framework/transport/topologi (ITEC D-11) — memengaruhi NFR konkret
- Lengkap di `00-index.md` roll-up
