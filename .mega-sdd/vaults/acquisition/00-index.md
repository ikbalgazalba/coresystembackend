# Vault Index — acquisition

> **Vault**: Acquisition (MCF/FINCORE) — Backend · **Project**: coresystembackend · **Epic**: credit-origination
> **Source**: `docs/prd/acquisition/BE-00..BE-07` (8 docs, v2.1, 2026-07-14/22) · **Companion**: `docs/ARCHITECTURE-PROPOSAL.md`, `docs/DB-CONVENTIONS.md`, `docs/DATA-MIGRATION-PLAN.md`
> **Generated**: 2026-07-27 · **Multi-PRD**: epic baru di atas vault `jwt-login` + `api-platform` (sudah ship)

## Vault Lock

| Field | Value |
|---|---|
| `vault_version` | 1.2 |
| `project_shape` | api-only |
| `implementation_mode` | existing |
| `prd_status` | draft |
| `output_mode` | compact |
| `constitution_version` | 1.2.0 (extends api-platform v1.1.1; §A–§G inherited + §H acquisition) |
| `source_documents` | BE-00-OVERVIEW · BE-01-intake-cas · BE-02-credit-analysis · BE-03-approval-committee · BE-04-contract-cm-po · BE-05-npp-legalization · BE-06-vertel-verification · BE-07-master-data-menus |
| `design_system_flags` | HAS_UI_COMPONENTS=false · HAS_TOKENS=false · HAS_A11Y=false · HAS_VOICE_BRAND=false (BE-only vault; FE di PRD `FE-*` terpisah) |

## Implementation Notes for AI Consumers

Vault ini adalah **START POINT** backend untuk membangun microservice Acquisition — credit-origination pembiayaan kendaraan (motor/mobil) dari STEP 8 (sync MOOFI→FINCORE) sampai STEP 15 (legalisasi NPP). Rentang dimiliki: **STEP 8–15** dari alur final 16-STEP; STEP 1–7 dimiliki upstream MOOFI (kontrak input binding), STEP 16 = downstream PULL.

**Yang WAJIB dijaga AI consumer:**
- **Jangan fabrikasi.** Setiap klaim sitasi ke PRD `BE-0x §N`. Bila PRD `[OPEN]` → OQ, bukan tebakan. OQ-ID PRD (OQ-GT-*, OQ-MEET-*, OQ-CRSCORE-*, dst.) DIPRESERVE sebagai tag stabil — jangan renumber ke `OQ-{DOC}-{N}`.
- **Marker mutability wajib dihormati:** `[LOCKED]` (regulatori/external-FK/governance) = pertahankan 1:1 additive-only; `[INTENT]` = outcome wajib, mekanisme bebas; `[ARTIFACT]` = buang setelah sign-off; `[OPEN]` → OQ; **USULAN** = desain baru belum diputuskan.
- **Konvensi schema** otoritatif = `docs/DB-CONVENTIONS.md` (ADR-14): satu schema, prefix `mst_/trx_/cfg_/log_/map_/stg_/out_`, singular snake_case English, PK `id BIGINT IDENTITY` + business key `credit_id` terpisah, declared FK wajib, audit columns wajib, satu kolom `status`. Flowable `ACT_*` = engine-owned, JANGAN disentuh manual.
- **Do-not-replicate** bug legacy (GOTCHA-1..18) WAJIB diperbaiki, bukan di-port: narrow screening, fail-open regulated gate, car/motor code split, destructive RAC delete, no-dedup NIK, positional related-person, dead `*_cas` SP, GL silent-commit, `EXECUTE AS 'sa'`, cross-DB linked-server DML, sp_OACreate HTTP, IA string-hack.
- **Super-user DIHAPUS** (D-09 `[LOCKED]`) — tidak ada role/grant/bypass setara. No-self-approval (D-01 S11) enforced dua lapis (Flowable task + service guard). Role census D-10 `[LOCKED]`: CMO · Marketing Head · Credit Analyst · Kepala Cabang · Credit (Admin).

**Provenance caveat (capture, bukan blocker):** BE-00 menyatakan KB `.mega-sdd/knowledge-base/` = "SUMBER OTORITATIF TEKNIS" tetapi KB **tidak ada di disk** saat vault ini dibuat. Sitasi PRD ke file KB (`_ACQUISITION-GROUND-TRUTH.md`, `gap-entities.md`, `60-frontend/`, dst.) menggantung → OQ-AC-PROVENANCE. Companion docs (`ARCHITECTURE-PROPOSAL`, `DB-CONVENTIONS`, `DATA-MIGRATION-PLAN`) ADA dan ter-sitasi. Jangan fabrikasi konten KB yang tidak ada.

## Reading paths

| Pembaca | Baca dulu |
|---|---|
| Backend architect | 01-overview → 02-architecture → 05-decisions (ADR-01..15) → 06-constraints |
| Dev per modul | 02-architecture (modules + events) → 04-flows (STEP 8–15) → 03-data-model (entitas modul) → BE-0x PRD asli |
| QA | 04-flows (Given/When/Then per flow) → 06-constraints (NFR + regulated gates) |
| PO/Stakeholder | 01-overview → 00-index §Open Questions roll-up (P1 blocker) |

## Auto-Classification Review

> Total classified: lihat `vault.json.open_questions_summary`. Auto-resolution active: OQ `tech / scan / high` (akan auto-resolve di `bind-codebase` bila single match). Manual review: `tech / medium|low` + business OQ.
> OQ di vault ini sebagian besar **business / blocking** (perlu stakeholder/telemetry/ITEC) karena PRD sudah ber-label `[OPEN]` eksplisit. Beberapa `tech / scan` (OQ-ARCH-STACK residual framework, OQ-EXTMASTERS liveness) — tapi tetap menunggu pihak eksternal (ITEC D-11 / DBA).

## Open Questions roll-up

> Roll-up subset paling memblokir dari 247+ OQ PRD. **Tag PRD dipreserve.** Status per-ID otoritatif di baris masing-masing; ✅ = RESOLVED (evidence/convention 2026-07-07/14). Roll-up lengkap di `vault.json.open_questions` + register asli BE-0x §11 + BE-00 §11.1.

| OQ-ID | Priority | Category | Resolution mode | Summary |
|---|---|---|---|---|
| OQ-AC-PROVENANCE | P1 | tech | scan | KB `.mega-sdd/knowledge-base/` tidak ada di disk padahal BE-00/ARCHPROP sitasi sebagai sumber otoritatif — restore/move atau ubah sitasi? |
| OQ-ARCH-STACK | P1 | business | blocking | Framework BE (USULAN Spring Boot), transport, topologi — menunggu deliverable ITEC D-11. Java LOCKED (D-12). |
| OQ-MEET-06 | P1 | business | blocking | Matrix step per-product MACF (D-07); mem-block annex per-product, BUKAN PRD payung |
| OQ-MEET-02 | P1 | business | blocking | Owner master loan (D-05): acquisition vs servicing? menunggu ITEC D-11 |
| OQ-EXTMASTERS-01 | P1 | business | blocking | Masters `FC_MSTAPP_MCF` owned vs read-only + liveness linked-server (DDL dump ✅ 2026-07-22, sisa kepemilikan) |
| OQ-EXTMASTERS-07 | P1 | business | blocking | 8 objek dirujuk code acquisition ABSEN dari dump `FC_MSTAPP_MCF` |
| OQ-REG-02 | P1 | business | blocking | Intake-only AML screening = signed-off control atau second screening di CA never built? |
| OQ-GAP-01 | P1 | business | blocking | Channel Pooling Order/OMA masih produksi? |
| OQ-GAP-02 | P1 | business | blocking | Siapa penulis `tr_ia_history` lokal (writer tak ditemukan)? |
| OQ-GAP-03 | P1 | business | blocking | Bypass SLIK: kondisi/otoritas + audit OJK compliance (regulatori) |
| OQ-GAP-04 | P1 | business | blocking | `tr_general_deviation`: makna `param_2`/`param_4`/writer path |
| OQ-MIG-01 | P1 | business | blocking | Strategi cutover: Skenario A (drain-di-legacy) vs B (in-flight per status); sebelum Phase 3 |
| OQ-MIG-05 | P1 | business | blocking | Gate profiling data prod atas semua klaim DISCARD/dead |
| OQ-CMPOFE-01 | P1 | business | blocking | Auto print+email at approve (motor-only legacy): intentional atau gap? |
| OQ-CMPOFE-02 | P1 | business | blocking | Print button gate field: `status_ca` vs `status_cm` — mana authoritative? |
| OQ-DOKU-01 | P1 | business | blocking | Siapa mengisi DOKU response fields (write-back SP tanpa caller)? |
| OQ-NPP-03/OQ-DISB-05 | P1 | business | blocking | OQ-DISB-05 (OPEN): penentu eligibility kontrak masuk batch Dealer Payment + siapa post header PAYMENT DB. *(OQ-NPP-03 ✅ RESOLVED — PULL confirmed, lihat §RESOLVED)* |
| OQ-NPP-13 | P1 | business | blocking | `sp_get_history_npp` baca `tr_hierarchy_approval_transaction` saat `agreement_status='A'` — dead code/archival? |
| OQ-NPP-02/OQ-PASSNET-01/02 | P1 | business | blocking | Siapa drain `tr_synchronize_to_passnet` + write-back; scope Passnet |
| OQ-SHELL-02 | P1 | business | blocking | Branch pick login stage-2 tidak re-verify server-side; rebuild re-verify (USULAN) |
| OQ-DLRPTN-05/OQ-REF-05 | P1 | business | blocking | `[SECURITY]` `MsBank.PasscodeBiBca` kredensial live? review sebelum migrasi |
| OQ-DLRPTN-01 | P1 | business | blocking | `MsDealer` vs `MsDealer1` vs backup — mana live? |
| OQ-CUSTMASTER-04/OQ-DLRPTN-13 | P1 | business | blocking | Bagaimana ~27 applicant lookups + dealer/bank masters di-maintain (no write path)? |
| OQ-BE07-01/02/03 | P1 | business | blocking | Maker-checker scope + who is checker; HR sync mechanism; HO roles (expand enum D-10?) |
| OQ-CRSCORE-01/OQ-OVERVIEW-01 | P1 | business | blocking | Mana 3 write-target 5C-note authoritative? NEEDS TELEMETRY/stakeholder |
| OQ-CRSCORE-10 | P1 | business | blocking | Actor-of-record & time-ordering "credit analyst"; sequential vs per-product |
| OQ-RAC-01/02 | P1 | business | blocking | Di mana `sp_insert_rac_processing*` jalan; scheduler `sp_agent_rac_to_cm_bulk` |
| OQ-SLIK-05 | P1 | business | blocking | Freshness 30-hari SLIK hard-block atau informational? |
| OQ-PRD01-01/02 | P1 | business | blocking | NIK dedup lock semantics; duality lock MOOFI vs FINCORE emitter |
| OQ-CORE-03/OQ-CMPO-02 | P1 | business | blocking | Arti bisnis OP/ULI/LCR (GL-reconciled? dua arti OP) |
| OQ-AC-01 | P1 | business | blocking | ~~Reject komite nutup `tr_cas`?~~ **✅ RESOLVED** (PRD §11.1) → rebuild WAJIB closure eksplisit (sudah hard rule di F-S-001 DoD). Lihat §RESOLVED. |
| OQ-ASM-01/02 | P1 | business | blocking | Semantik non-Level-0 Reject + correction actor (ladder, BUKAN committee) |
| OQ-VTL-05 | P1 | business | blocking | Gate consistency E11 vertel: sync in-transaction vs read-model (ITEC D-11) |
| OQ-DUKCAPIL-01 | P1 | business | blocking | Mekanisme request Dukcapil + populate replica |
| OQ-VERIF-02 | P1 | business | blocking | `spNewZoomInsertSurvey_2w` external caller live/dead? |
| OQ-NPPVTL-01 | P1 (FE-06) | business | blocking | Shared inbox decision UI VK atau Vertel screen only? |
| OQ-MEET-01 | P2 | business | blocking | Email blast dealer (D-03) — trigger point, template, failure policy |
| OQ-MEET-03 | P2 | business | blocking | AR Card & jurnal (D-06) — GL account mapping + posting rules source of truth |
| OQ-MEET-04 | P2 | business | blocking | Instant-Approval lane eligibility per product/plafond |
| OQ-MEET-05 | P2 | business | blocking | Expiry verifikasi 30-hari — auto-cancel vs re-verify + clock start |
| OQ-GT-03 | P2 | business | blocking | Open CM STEP 13 return-target "Step 1–12" granularity |
| OQ-CMPO-11 | P2 | business | blocking | Nama/kontrak committee event correction/reject (03→04 pre-mint) |
| OQ-EXTMASTERS-08 | P2 | business | blocking | `MsPublicHoliday` di DUA database — copy mana otoritatif? |
| OQ-GAP-11 | P2 | business | blocking | PII retention DOKU logs (UU PDP) |

**✅ RESOLVED (jangan di-blokir lagi):** OQ-REG-06 → fail-closed · OQ-ACQCAS-01 → broad blacklist · OQ-ACQCAS-02 → `sp_check_APUPPT` · OQ-AC-02 → Rp 35M · OQ-MCP-01 → enforce app-layer no-self-approval · OQ-NPP-14 → BAST hard-gate in-transaction · OQ-PRODASSET-06 → IA pilot-hack dibuang → policy flag · OQ-COLL-01 → `verification_status='A'` · OQ-CMPO-01 → status header RFA-locked · OQ-NPP-03 → PULL confirmed · OQ-AC-01 → rebuild closure eksplisit · OQ-GT-01 → dual-path separator=trigger · OQ-GT-02 → `credit_id` format `branch(5)+YY+MM+SEQ(5)` · OQ-BE03-02 → freeze executor=04 (opsi b) · OQ-BE03-04 → `RacDecisionReceived` via outbox (opsi a) · OQ-VTL-06 → centralized `log_approval_history` · OQ-CRSCORE-11 → 3 status cols disambiguated · OQ-PRODASSET-05 → V1–V9 verified · OQ-DATA-02 → `tr_CIF` LIVE · OQ-DATA-05 → `tr_verification_customer` canonical · OQ-REF-04 → dump `FC_MSTAPP_MCF` ✅.

> **⚠️ KOREKSI (phase-advisor sub-vault 2026-07-27):** OQ-MASTERDATA-02 sebelumnya tercantum RESOLVED di sini, tetapi BE-07 §11 menyatakan **"TER-KOREKSI SEBAGIAN"** — backend legacy TERNYATA memvalidasi V1–V3/V7–V8 di SP (bukan hanya JS), tetapi celah nyata **V4 (NIK tanpa guard) + V6 (kontiguitas level) TETAP OPEN**. Cleansing import wajib + profil data prod (OQ-MIG-05). Lihat sub-vault `acquisition-master-data`.

## Sources

- `docs/prd/acquisition/BE-00-OVERVIEW.md` — PRD payung (umbrella): scope, 16-STEP, shared ERD, table-ownership registry 112 tabel, OQ register
- `docs/prd/acquisition/BE-01..BE-07` — PRD per modul (intake-cas, credit-analysis, approval-committee, contract-cm-po, npp-legalization, vertel-verification, master-data-menus)
- `docs/ARCHITECTURE-PROPOSAL.md` — ADR-01..15 (modulith, zero-SP, single-schema, outbox, ACL, state-machine engine, RBAC, MOOFI sync, PULL, Next.js BFF-lite, observability, testing, Flowable, DB-CONVENTIONS, migration)
- `docs/DB-CONVENTIONS.md` — standar schema (prefix kelas, tipe, audit columns, larangan)
- `docs/DATA-MIGRATION-PLAN.md` — ADR-15 simulation-first + reconciliation gate
- `.mega-sdd/vaults/api-platform/constitution.md` — konstitusi di-extend (§A–§G inherited)
- `.mega-sdd/codebase/codebase-map.md` — scan 2026-07-24 (drift vs HEAD; relevan `bind-codebase`/`sync`)

## Out of Scope (vault BE ini)

- **MOOFI mobile origination STEP 1–7** — upstream; semantik target-state mengikat sebagai kontrak input (D-01) tapi tidak diimplement BE Acquisition
- **Post-acquisition eksekusi STEP 16** — Disbursement/GL posting, BPKB custody, Dealer Payment, Insurance = downstream **PULL**; Acquisition hanya sediakan eligibility/event outbox
- **Frontend (`FE-*`)** — PRD terpisah (Next.js); BE vault hanya cross-check kebutuhan API dari layar via KB `60-frontend/`
- **Sibling context** COLLECTION, TERMINATION — bukan scope acquisition
- **Master loan field census** — output wajib STEP 15 (D-05) tapi owner `[OPEN]` OQ-MEET-02 (mungkin servicing)
- **Topologi infra final** — menunggu deliverable ITEC Bank Mega (D-11)
