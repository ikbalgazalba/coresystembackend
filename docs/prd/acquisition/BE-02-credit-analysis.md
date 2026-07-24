# PRD — Credit Analysis & Scoring [BE]

> **Audience**: Tim Backend (BE). **Target stack**: **Java** (D-12 [LOCKED]; SoW user directive 2026-07-14). Framework: **USULAN Spring Boot** (belum ditetapkan — [OPEN] OQ-ARCH-STACK; topologi infra final menunggu deliverable arsitektur ITEC Bank Mega, D-11).
> **Tanggal**: 2026-07-14. **Versi**: 2.1 (2026-07-14: §3 Model Data dilengkapi menjadi **GROUND TRUTH schema** per `docs/DB-CONVENTIONS.md` §9 — census kolom penuh 16 tabel legacy modul + set RAC, disposisi migrasi per `docs/DATA-MIGRATION-PLAN.md`; +OQ-CRSCORE-11, OQ-GAP-03/06, OQ-CORE-08 di §11). Supersedes v2.0 (revisi post-meeting) & baseline `02-credit-analysis.md` pre-meeting.
> **Sumber otoritatif**: `_ACQUISITION-GROUND-TRUTH.md` v2 (PDF "ALUR TRANSAKSI ACQUISITION 08072026", 16-STEP final) + `_MEETING-DECISIONS-2026-07.md` (D-01…D-12) + KB backend (`10-domains/21-credit-analysis-scoring.md`, `50-integrations/rac-bank-mega-risk-engine.md`, `50-integrations/slik-ojk.md`, `50-integrations/pefindo.md`, `50-integrations/neoscore.md`, `50-integrations/dukcapil.md`) + KB frontend (`60-frontend/62-credit-analysis-screens.md`).
> **Keputusan meeting terintegrasi**: D-01 (Step 7/8/10), D-07, D-09, D-10, D-11, D-12.

Kapabilitas ini adalah inti pengambilan-keputusan risiko yang duduk di antara intake aplikasi (01) dan komite approval (03). Ia mengorkestrasi gate risiko otomatis **RAC Bank Mega** — **STEP 10** pada alur final 16-STEP PDF 08072026 (dispatcher CF-konvensional vs US-syariah via ACL per D-01 Step 7, request async + **ingest callback async** per D-01 Step 8, idempotent), menjalankan **Credit Analysis** — **STEP 11** (validasi dokumen granular `TrCaDocuments`, dissection history pembayaran SLIK per bulan, kolektibilitas SLIK/FCL, scoring internal LKK, DSR, rekomendasi *Recommended / Not Recommended* dengan justifikasi), dan menyusun komposisi risk-category yang mengkualifikasi `trans_type_id` untuk routing komite (D-01 Step 8: "risk-category composes trans_type_id"; D-01 Step 10: routing komite dinamis by `trans_type_id` + Plafond Hutang Pokok (OP) + risk level).

**Cakupan & pemetaan penomoran**: modul 02 = **PDF v2 STEP 10 (RAC) + STEP 11 (CA)** = umbrella **PRD FASE 9** (credit decisioning) pada `00-OVERVIEW.md §3` (label FASE umbrella dipertahankan untuk traceability; penomoran STEP PDF 08072026 adalah yang final — deltas v1→v2 di `_ACQUISITION-GROUND-TRUTH.md`: "FASE 9 RAC → STEP 10 (unchanged, external seam)"; "FASE 10 CA → STEP 11 (unchanged)"). STEP 12 (Hierarki/komite) milik 03; STEP 13 (PO) milik 04; STEP 14 (Vertel, step BARU per D-02) milik verification/05; STEP 15 (NPP) milik 05. Kepemilikan 02: seam RAC (request + callback ingest), SLIK/OJK, Pefindo, NeoScore (lihat §8 — kepemilikan call outbound berubah vs legacy), konsumsi read Dukcapil; emit event `AnalysisComplete` + memasok decision RAC & risk-tier ke 03.

> Catatan identifier: `application_id` pada PRD ini ≙ `credit_id` legacy — nomor kontrak nasional-unik yang di-mint pada STEP 8 (sync MOOFI→FINCORE) dan menjadi Primary Key (`_ACQUISITION-GROUND-TRUTH.md` STEP 8). Format/sequence penomoran = RESOLVED — evidence (OQ-GT-02, 2026-07-14): `branch_id(5)+YY(2)+MM(2)+SEQ(5)` = 14 char (spec BE-01 §3.1.13).

---

## 1. Ruang Lingkup & Kepemilikan

### Yang DIMILIKI service ini

| # | Milik | Sumber KB |
|---|---|---|
| 1 | **Dispatcher RAC (STEP 10)** rute **CF (Conventional Finance)** vs **US (Unit Syariah)** berdasarkan financing-model code pada record kontrak, via Anti-Corruption Layer (ACL) — D-01 Step 7 mewajibkan ACL. Legacy SP tercantum PDF: `sp_insert_rac_processing_noreturn` / `sp_insert_rac_processing` (CF), `sp_insert_rac_processing_syariah` (US). | `21-credit-analysis-scoring.md §1,§5.1`; `rac-bank-mega-risk-engine.md §1`; ground-truth v2 STEP 10; D-01 |
| 2 | **Adapter request RAC** (submit profil risiko ke Bank Mega) + **ingester callback/poll** yang menerima decision async dan mendorong status lokal maju; idempotent by `application_id + decision_id`. **Ingest callback ASYNC adalah keputusan meeting** (D-01 Step 8), bukan lagi sekadar rekonstruksi dari kode. | `rac-bank-mega-risk-engine.md §3,§9,§10 EC-1`; boundary_ownership "RAC async callback ingest"; D-01 Step 8 |
| 3 | **Review kolektibilitas SLIK/FCL** (grid per-bank, sampai 24 bulan; dissection per bulan per STEP 11) + **fallback Pefindo**; reduksi days-past-due ke skala kolektibilitas OJK 1–5. | `21-...md §5.9-5.10`; `slik-ojk.md §1,§6`; `pefindo.md §1,§6`; ground-truth v2 STEP 11 (`sp_get_fcl_result_history_kol_slik` → TrCaSlik) |
| 4 | **Internal scoring engine (LKK)**: grade applicant → weight → risk-category 9-parameter → bucket low/medium/high/very-high. | `21-...md §5.4-5.6, BR-CRSCORE-7,8,11` |
| 5 | **Checklist validasi dokumen granular** (~40 field, `TrCaDocuments` — dinamai eksplisit di PDF v2 STEP 11), **bank-account detail/mutation** (+ rekap rekening 3 bulan), **DSR 40%**, **freshness bureau 30-hari (advisory di STEP 11)**. | `21-...md §5.12-5.14, BR-CRSCORE-5,6`; `TrCaDocuments.cs`; ground-truth v2 STEP 11; `62-credit-analysis-screens.md §4-5` |
| 6 | **Rekomendasi analis** (*Recommended / Not Recommended* dengan justifikasi — vocabulary final per PDF v2 STEP 11), narasi positif/negatif, **debtor-group & ojk_economic_sector** (di-assign saat analisis, bukan intake). | `21-...md §5.15, BR-CRSCORE-12`; ground-truth v2 STEP 11 (`sp_insert_analisa_cmo_ca` → TrCa) |
| 7 | **Komposisi risk-category penyusun `trans_type_id`** yang mengkualifikasi risk-tier untuk routing komite (disusun di SATU tempat, dikonsumsi 03; D-01 Step 10: routing komite = `trans_type_id` + OP + risk level; D-10: hierarki approval tergantung **skala risiko**). | `21-...md BR-CRSCORE-9`; umbrella conventions `id_format`; D-01 Step 8/10; D-10 |
| 8 | **Provider parameter NeoScore** (3 channel: default/car/mobile) + **ingester hasil NeoScore** + **[USULAN — target] pemilik call outbound NeoScore via ACL BE** (legacy: call di FE tier — resolved, lihat §8); **konsumsi read Dukcapil**; **microflow direct-checking SLIK/OJK** (request+approval terpisah). | `neoscore.md §1,§2`; `62-credit-analysis-screens.md §9 EC-1`; `dukcapil.md §2,§3`; `slik-ojk.md §3, BR-CRSCORE-14` |
| 9 | Emit event **`AnalysisComplete`**; memasok decision RAC + risk-tier + komposisi `trans_type_id` ke 03. | umbrella `bounded_contexts.02` |
| 10 | **API baca/tulis untuk layar CA FE (Next.js)**: worklist scoped branch+analyst, entry multi-panel, grid transkripsi SLIK, checklist dokumen, viewer dokumen intake (read), print/export SLIK. | `62-credit-analysis-screens.md §3a,§4`; D-12 (FE = Next.js, konsumen API BE) |

### Yang BUKAN milik service ini

| Bukan milik | Pemilik | Sumber |
|---|---|---|
| Keputusan komite approve/reject/correction (**STEP 12**: routing hierarki `sp_get_next_approval_scheme` by Plafond + Risiko per PDF v2, aksi approver, lock OP/ULI/LCR + insurance saat approve, audit `tr_hierarchy_transaction`) | 03-approval-committee | ground-truth v2 STEP 12; umbrella; `22-approval-committee.md` |
| **Processing/decisioning RAC** (logika accept/reject) — jalan di sisi Bank Mega (JFinMega), eksternal | Bank Mega (eksternal) | `rac-bank-mega-risk-engine.md §1,§4` |
| **PO minting** (TrPo, **STEP 13**) — di-mint 04 (single deterministic PO per approval, D-01 Step 13); **service ini TIDAK memicu PO** (bug legacy `CreditAnalystRepositoryEF.cs:692-708` JANGAN direplika; FE legacy juga me-render PO PDF sinkron dari layar approval CA `CreditAnalystController.cs:1166-1266` — JANGAN direplika) | 04-contract-cm-po | GOTCHA-8; boundary_ownership "PO minting"; D-01 Step 13; `62-...screens.md §5.11` |
| **Vertel (STEP 14, BARU)** — verifikasi telepon konsumen `TrVerificationCustomer`, RFA Vertel, approve Kepala Cabang | verification / 05 | D-02; ground-truth v2 STEP 14 |
| **Hard-gate verifikasi & freshness STEP 15 (NPP)** (403+rollback; BAST + `sp_validation_chasis_number`; expiry verifikasi konsumen 30-hari strict per D-01 Step 14) — freshness bureau STEP 11 di sini hanya **advisory**, JANGAN dikonflasi (lihat catatan tiga jendela 30-hari di §6 BR-02-14) | 05-npp / verification | BR-VERIF-7; boundary_ownership "Verification hard-gate"; D-01 Step 14 |
| RFA lock & transisi status RFA='0' (**STEP 9**; SP moofi-path `sp_approve_cm_moofi` per PDF v2 — dual path vs legacy `sp_rfa_cm`/`sp_approve_cm` = OQ-GT-01 ✅ RESOLVED — evidence, §11: keduanya LIVE, pemisah = trigger) | 01-intake-cas | ground-truth v2 STEP 9; boundary_ownership "RFA" |
| Ladder credit-analyst (`trans_type_id='AA00000001'`) sebagai router — distinct dari router komite; **catatan rekonsiliasi**: PDF v2 STEP 12 menyebut `sp_get_next_approval_scheme` sebagai router KOMITE, sedangkan KB code-evidence (GOTCHA-5) memetakannya ke ladder credit-analyst distinct — discrepancy doc-vs-code ini milik 03 untuk direkonsiliasi; 02 hanya menjaga komposisi `trans_type_id` | (distinct; rekonsiliasi milik 03) | GOTCHA-5; ground-truth v2 STEP 12 |
| **Rapindo (registri kendaraan curian/BPKB)** — check di-trigger dari layar CA legacy dan meng-gate Approve final-level; gate final-approve milik 03/kolateral domain; 02 hanya perlu menyediakan konteks aplikasi | 03 / `31-collateral-bpkb-fidusia` | `62-...screens.md §5.7, BR-CAUI-7`; [OPEN] OQ-CAUI-02/04 |

---

## 2. Aktor & Peran

> Catatan [KEPUTUSAN DESAIN BARU]: RBAC tidak ada di kode legacy (`neoscore.md §7`: "no RBAC scheme exists in code generally"). Peran di bawah adalah desain target; enforcement identitas aktor per endpoint dilacak di OQ-MCP-01.
>
> **Sensus peran cabang (D-10 [LOCKED])**: peran existing di cabang = **CMO, Marketing Head, Credit Analyst, Kepala Cabang, Credit (Admin)**; hierarki approval tergantung **skala risiko**. **Super user DIHAPUS dari rebuild (D-09 [LOCKED])** — tidak boleh ada role super-user pada endpoint modul ini.
>
> Catatan [OPEN] OQ-CRSCORE-10 (P1): Framing actor-of-record & time-ordering peran "credit analyst" — apakah jalur capture preliminary-note CMO dan analyst workstation penuh adalah **langkah sekuensial satu lifecycle** ATAU **kanal alternatif untuk lini produk berbeda** — BELUM final dan bersifat **provisional**. **Bukti baru dari KB FE** (`62-credit-analysis-screens.md §1`): dua implementasi layar CA yang berevolusi independen memang eksis per lini produk — **motor** ("CreditAnalyst", form multi-panel analis) vs **mobil** ("CACar", dispatcher 1-halaman 5C free-text + upload foto oleh CMO + action bar maker-checker) — mendukung framing "kanal alternatif per produk", tapi TETAP butuh konfirmasi stakeholder; terkait D-07/OQ-MEET-06 (matriks step per produk MACF). JANGAN diasumsikan salah satu framing.

| Aktor | Peran | Sumber |
|---|---|---|
| **Credit Marketing Officer (CMO) / preparer** | Menyusun preliminary 5C-note (di produk mobil: field Capacity/Capital/Character/Condition/Collateral + upload dokumen foto); dapat me-(re)trigger gate RAC manual (standalone). Peran tercantum di sensus cabang D-10. | `21-...md §2, §11 CACarController`; `62-...screens.md §2, CAR-S1`; D-10 |
| **Credit Analyst (branch/back-office)** | Aktor utama STEP 11: review kolektibilitas bureau, transkripsi grid history SLIK, validasi checklist dokumen, catat bank-account analysis + rekap 3 bulan, stamp *Recommended / Not Recommended* + justifikasi. Peran tercantum di sensus D-10. | `21-...md §2`; ground-truth v2 STEP 11; D-10 |
| **Marketing Head / Kepala Cabang / Credit (Admin)** | Peran cabang lain per sensus D-10; keterlibatan spesifik pada walk approval CA-level = milik 03; identitas "CA approver" (flag `IsApprover`/`IsLastApprover` di layar mobil) belum ter-resolve → [OPEN] OQ-CAUI-02. | D-10; `62-...screens.md §2, BR-CAUI-5` |
| **System / RAC dispatcher** | Trigger otomatis RAC saat resubmission-after-correction; assemble profil risiko; route CF/US; deteksi branch code / product type untuk pilih rute (PDF v2 STEP 10). | `21-...md §5.1-5.2`; ground-truth v2 STEP 10 |
| **RAC callback ingester (scheduled/async)** | Menerima decision async Bank Mega, idempotent, dorong status lokal. **ASYNC per D-01 Step 8 [INTENT]**. Legacy: SQL Agent poll `sp_agent_rac_to_cm_bulk` (scheduler [OPEN] OQ-RAC-02). Target Java: [USULAN] scheduled poller (Spring `@Scheduled`/Quartz) ATAU webhook endpoint — mekanisme final tergantung kontrak Bank Mega (OQ-RAC-01) + arsitektur ITEC (D-11). | `rac-...md §3(e),§8,§10 EC-1`; D-01 Step 8 |
| **Reminder job (scheduled)** | Mengingatkan approver pending. Legacy hardcode 2 nama — JANGAN direplika (BR-02-21). | `21-...md §5.22, BR-CRSCORE-19` |
| **SLIK/OJK direct-check requester + approver** | Microflow request pengecekan registri langsung dengan hierarki approval dari department requester. | `21-...md BR-CRSCORE-14`; `slik-ojk.md §3` |
| **Bank Mega Risk Engine (JFinMega)** — eksternal | Menjalankan decisioning RAC; mengembalikan accept/reject async. Resolusi PDF v2 STEP 10: **Rejected → stop; Approved → CA queue**. | `rac-...md §1`; ground-truth v2 STEP 10 |
| **NeoScore** — eksternal | Consumer-scoring service. **Legacy caller = FE tier (RESOLVED)**: `FINCORE.Services/NeoScoreServices.cs` HTTP POST langsung ke vendor (plain HTTP + PII) — JANGAN direplika; target: call BE-owned via ACL [USULAN]. | `neoscore.md §1`; `62-...screens.md §9 EC-1` |
| **SLIK / Pefindo / Dukcapil** — eksternal | Supply bureau collectibility & civil-registry match (read-only feed ter-staging). | `slik-ojk.md`, `pefindo.md`, `dukcapil.md` |

---

## 3. Model Data — GROUND TRUTH SCHEMA

> **Status: GROUND TRUTH** (dilengkapi 2026-07-14). Semua tabel target mengikuti `docs/DB-CONVENTIONS.md` (prefix kelas §1, snake_case Inggris, PK/FK §2, tipe §3, kolom audit wajib §4, SATU kolom `status` §5, larangan §6, format kepatuhan §9). Mapping asal = DDL legacy `FC_ACQ_MCF` (census kolom penuh) + KB `30-data-model/core-entities.md §3, §11, §16` + `gap-entities.md §2.3, §3`. Disposisi migrasi (**MIGRATE / MIGRATE-READONLY / DISCARD / REBUILD**) per `docs/DATA-MIGRATION-PLAN.md` §1; **hasil biro historis = MIGRATE-READONLY** per DATA-MIGRATION-PLAN §4 butir 3. Penyimpangan dari schema ini = change-request PRD, bukan keputusan implementasi.
>
> Konvensi implementasi Java [USULAN]: tabel dipetakan ke JPA entity + Flyway migration; enum → `enum` Java + kolom `VARCHAR` ber-`CHECK`; field `[LOCKED]` diberi Bean Validation + test kontrak. Kolom audit wajib (`created_at/created_by/updated_at/updated_by`; `log_` hanya `created_*`) berlaku untuk SEMUA tabel di bawah dan tidak diulang per census. Marker ditulis `[confidence×mutability]`.

### 3.0 Peta disposisi tabel legacy → target (coverage modul: 16/16 tabel FC_ACQ_MCF + set RAC eksternal + titipan `tr_CAS_AC_header` dari register 01)

| # | Tabel legacy (jml kolom) | Target | Disposisi | Ref |
|---|---|---|---|---|
| 1 | `tr_CA` (38) | `trx_credit_analysis` | MIGRATE | §3.1 |
| 2 | `tr_CA_SLIK` (15) | `trx_slik_history_entry` | MIGRATE-READONLY (hasil biro historis) | §3.6 |
| 3 | `tr_CA_account_detail` (10) | `trx_credit_analysis_bank_account` | MIGRATE | §3.4 |
| 4 | `tr_CA_account_mutation` (11) | `trx_credit_analysis_bank_mutation` | MIGRATE | §3.4 |
| 5 | `tr_CA_approval_progress` (23) | Flowable runtime + `log_approval_history` (milik 03) — TIDAK ada tabel 02 baru | MIGRATE-READONLY (riwayat); in-flight TIDAK dimigrasi | §3.12 |
| 6 | `tr_CA_approval_progress_transaction` (23; shadow byte-identik) | idem — shadow pattern DILARANG direplika (DB-CONVENTIONS §6.1) | MIGRATE-READONLY | §3.12 |
| 7 | `tr_CA_documents` (75) | `trx_credit_analysis_document_check` (unpivot typed rows) + `trx_credit_analysis_appi` | MIGRATE (unpivot) | §3.5 |
| 8 | `tr_CA_financial` (18) | `trx_credit_analysis_financial` | MIGRATE | §3.3 |
| 9 | `tr_CA_print_history` (5) | `log_document_print` generik (DB-CONVENTIONS §6.2) | MIGRATE-READONLY | §3.11 |
| 10 | `tr_slik_request` (31) | `trx_slik_request` | MIGRATE | §3.7 |
| 11 | `tr_slik_ojk_application_in` (11) | `trx_slik_request_document` | MIGRATE | §3.7 |
| 12 | `tr_slik_bypass_acquisition` (4) | `log_slik_bypass` **[LOCKED]** (OQ-GAP-03) | MIGRATE-READONLY | §3.8 |
| 13 | `tr_print_log_slikojk` (4) | `log_document_print` (`document_type='slik_report'`) | MIGRATE-READONLY | §3.11 |
| 14 | `neo_score_log` (12) | `trx_neoscore_result` (first-class) + `log_neoscore_call` | MIGRATE (baris terbaru/credit) + MIGRATE-READONLY (riwayat penuh) | §3.9 |
| 15 | `tr_risk_scale_analysis` (6) | `trx_risk_scale_analysis` | MIGRATE | §3.10 |
| 16 | `cek_apuppt_log` (6) | idempotency-marker cek APU-PPT (OQ-GAP-06 porsi ini ✅ RESOLVED — evidence, §3.13) | MIGRATE-READONLY (arsip) | §3.13 |
| 17 | `tr_CAS_AC_header` — **owner target 02** (disposisi teregister di BE-01 §3.4 #12, keluarga `tr_CAS_*`) | 5C narrative CMO → `trx_credit_analysis` (`positive_aspects`/`negative_aspects`/`analysis_narrative`; authority 3-jalur 5C-note = **[OPEN — OQ-CRSCORE-01]**) | MIGRATE | §3.1 |
| R | `rac_processing_header/detail` (CF) + `rac_processing_status_bms/detail_bms` (US/SY) — **eksternal `[macf-dbmega].JFinMega`, BUKAN FC_ACQ_MCF** | `trx_rac_screening` + `log_rac_callback` + `stg_rac_callback` | REBUILD (state terkini direkonstruksi via ACL saat cutover; tabel eksternal TIDAK disalin) | §3.2 |

### 3.1 `trx_credit_analysis` ← `tr_CA` (spine STEP 11; umbrella entity `CREDIT_ANALYSIS`)

Legacy writer: `sp_insert_analisa_cmo_ca` (PDF v2 STEP 11). Business key `ca_no` (`ux_trx_credit_analysis_ca_no`); relasi aplikasi via `application_id` = **declared FK** (legacy: `credit_id` kolom polos nullable, tanpa FK — do-not-replicate). **Perbaikan struktural utama**: 3 kolom status overlap legacy (`CA_decision`, `approval`, `CA_status`) — do-not-replicate per DB-CONVENTIONS §5 — dikonsolidasi menjadi SATU kolom `status` (state machine §7.2) + kolom `recommendation` (keputusan bisnis analis, BUKAN state workflow).

| Kolom target | Tipe | Null | Asal (`tr_CA`) | Marker | Catatan |
|---|---|---|---|---|---|
| `id` | BIGINT identity | NOT NULL | — (baru) | — | PK teknis (DB-CONVENTIONS §2) |
| `ca_no` | VARCHAR(20) | NOT NULL | `CA_no` (PK legacy) | [VERIFIED×LOCKED] | Business key dokumen CA (regulator-facing, tercetak di laporan); `ux_` |
| `application_id` | BIGINT FK → `trx_application` | NOT NULL | `credit_id` varchar(20) NULL(!) | [VERIFIED×LOCKED nilai] | ≙ `credit_id` legacy (minted STEP 8); legacy nullable/orphan → reject + register saat migrasi |
| `company_id` | VARCHAR(2) | NOT NULL | `company_id` | [VERIFIED×INTENT] | |
| `branch_id` | VARCHAR(5) | NOT NULL | `branch_id` | [VERIFIED×INTENT] | Basis scoping worklist (BR-02-36) |
| `house_condition`, `environment_condition`, `length_of_stay`, `road_access`, `business_environment` | VARCHAR(50) | NULL | kolom sama (5) | [VERIFIED×INTENT] | Penilaian kualitatif residensial; kandidat FK lookup `mst_` (belum dikonfirmasi) |
| `corporate_legal_entity`, `company_ownership`, `business_activity`, `corporate_scale`, `beneficial_owner` | VARCHAR(50) | NULL | kolom sama (5) | [VERIFIED×INTENT] | Varian korporat penilaian kualitatif |
| `applicant_validation`, `reference_validation`, `submission_validation` | VARCHAR(50) | NULL | kolom sama (3) | [VERIFIED×INTENT] | Verdict validasi analis |
| `vertel_validation_reason` | TEXT | NULL | `vertel_validation_reason` | [VERIFIED×OPEN] | Vertel = STEP 14 milik 05/06; kolom dipertahankan utk migrasi — kepemilikan go-forward [OPEN, cross-module] |
| `mrp` | NUMERIC(18,2) | NULL | `MRP` numeric(18,0) | [VERIFIED×INTENT] | Market Resale Price kolateral; wajib non-zero sebelum Save (BR-02-28); whole-rupiah → `.00` (DB-CONVENTIONS §3) |
| `spk_name` | VARCHAR(50) | NULL | `SPK_name` | [VERIFIED×INTENT] | Ekspansi "SPK" [OPEN — OQ-CORE-01] |
| `spk_date` | DATE | NULL | `SPK_date` datetime | [VERIFIED×INTENT] | Tanggal murni → DATE |
| `slik_link` | TEXT | NULL | `SLIK_link` | [VERIFIED×LOCKED] | Pointer ke cek SLIK/OJK — linkage regulatori |
| `positive_aspects` | TEXT | NULL | `positive_aspects` | [VERIFIED×INTENT] | = separuh field umbrella `positive_negative_narrative` |
| `negative_aspects` | TEXT | NULL | `negative_aspects` | [VERIFIED×INTENT] | idem |
| `analysis_narrative` | TEXT | NULL | `credit_analysis` | [VERIFIED×INTENT] | = `recommendation_justification` umbrella; wajib saat submit (§5.3); legacy FE maxlength 800 |
| `recommendation` | VARCHAR(20) + CHECK `recommended\|not_recommended` | NULL (sampai submit) | `CA_decision` varchar(20) | [VERIFIED×INTENT] | **Mapping nilai legacy**: motor `Approve`→`recommended`, `Reject`→`not_recommended` [VERIFIED]; mobil **[RESOLVED — OQ-CRSCORE-11]**: vocabulary `CA_decision` mobil = HANYA `A`/`R` (dropdown "Keputusan CA": `A`=Approve, `R`=Reject — `FINCORE.WEB/.../Views/Acquisition/CreditAnalyst/Index.cshtml:2839-2843`; ext-prop DDL "keputusan CA" `FC_ACQ_MCF 2.sql:101572`) → `A`→`recommended`, `R`→`not_recommended`; kode `V/C` yang sebelumnya diduga milik kolom ini ternyata milik `CA_status` (lihat baris `status`); nilai tak ter-map = reject + register (DATA-MIGRATION-PLAN §3) |
| `status` | VARCHAR(20) NOT NULL + CHECK `queued\|under_review\|recommended` | NOT NULL | `CA_status` varchar(5) (satu-satunya kolom status hidup; `approval` varchar(5) = DEAD → DISCARD) | [VERIFIED×INTENT] | SATU kolom status (DB-CONVENTIONS §5). **[RESOLVED — OQ-CRSCORE-11]** disambiguasi 3 kolom: (1) `CA_status` = state machine workflow CA mobil, vocabulary `D`=Draft, `0`=RFA, `V`=Verify/Verified (approve intermediate), `C`=Correction/Reviewed, `''`=Reviewed CM (koreksi Level 0), `E`=Eskalasi (reject non-Level-0), `A`=Approved (final), `R`=Rejected (decode CASE `sp_get_pagination_ca` `FC_ACQ_MCF 2.sql:43056-43063`; `sp_get_pagination_acquisition_mobil` :39681-39686; writers `CreditAnalystRepositoryEF.cs:1449/1410/766,841/583,618/927/1018/710`, `sp_approve_cm_car`:119,124, `sp_trans_open_cm`:18); (2) `CA_decision` = verdict analis `A`/`R` saja (baris `recommendation`); (3) `approval` ("jumlah approval", ext-prop :101576) = TIDAK pernah dibaca/ditulis SP mana pun di dump 3.7MB (hanya DDL :7787) & tanpa binding UI (.NET hanya pass-through `CreditAnalystRepositoryEF.cs:1406,1448`) → DISCARD tanpa mapping (verifikasi NULL-ness saat migrasi). Matriks `CA_status`→`status`+`recommendation` target = deliverable migrasi (kini ter-ground), nilai tak ter-map reject + register |
| `collectibility` | SMALLINT + CHECK 1..5 | NULL | — (baru; legacy TIDAK persist di `tr_CA`) | [INFERRED×**LOCKED** skala] | Ringkasan kolektibilitas terburuk, derived dari §3.6/bureau snapshot; skala OJK `0→1, 1-90→2, 91-120→3, 121-180→4, >180→5` **WAJIB** (BR-02-06) |
| `dsr` | NUMERIC(9,6) | NULL | — (baru; legacy hitung di 4 tempat, tidak dipersist) | [INTENT] | Persist hasil formula tunggal BE saat submit (BR-02-28); inklusi spouse-income [OPEN — OQ-CAUI-07] |
| `risk_tier` | VARCHAR(12) + CHECK `low\|medium\|high\|very_high` (+ kode fast-track) | NULL | — (baru; legacy output `sp_get_scoring`, tidak dipersist di `tr_CA`) | [INTENT] | Feed komposisi `trans_type_id` (BR-02-11); basis hierarki per skala risiko (D-10) |
| `ojk_economic_sector` | VARCHAR(7) | NULL | `economic_sector_code` | [VERIFIED×**LOCKED**] | WAJIB match OJK code list; di-assign saat analisis (BR-02-17) |
| `debtor_group` | VARCHAR(12) | NULL | `debtor_group_id` | [VERIFIED×**LOCKED**] | Kode pelaporan debtor-group BI/OJK (BR-02-17) |

**Kolom legacy `tr_CA` yang TIDAK dibawa (DISCARD kolom, 38/38 ter-disposisi):**

| Kolom legacy | Disposisi | Alasan |
|---|---|---|
| `CA_nik` varchar(15) | DISCARD — derive via JOIN customer | Denormalisasi identitas dilarang (DB-CONVENTIONS §6.3); nilai [LOCKED] diverifikasi checksum vs record customer saat migrasi |
| `last_printed_by`, `last_printed_on`, `sum_of_print` | DISCARD kolom → `log_document_print` (§3.11) | Print-tracking bespoke dilarang (DB-CONVENTIONS §6.2); `sum_of_print` = derivable count |
| `created_by/on`, `last_updated_by/on` | → kolom audit standar §4 | rename konvensi |

### 3.2 Set RAC (STEP 10): `trx_rac_screening` + `log_rac_callback` + `stg_rac_callback` (umbrella entity `RAC_SCREENING`)

Tidak ada tabel legacy di `FC_ACQ_MCF`: state legacy hidup di record set **eksternal** `[macf-dbmega].JFinMega` — CF: `rac_processing_header/detail(/source/_history)`; US/SY: `rac_processing_status_bms/detail_bms` — di-DML langsung via linked server oleh SP legacy = [ARTIFACT] do-not-replicate (akses hanya via ACL, BR-02-03). **Disposisi: REBUILD** — status terkini per aplikasi direkonstruksi via ACL saat cutover; decision historis yang masih dibutuhkan komite masuk `log_rac_callback` sebagai baris migrasi.

**`trx_rac_screening`** (spine screening; SATU kolom `status` per DB-CONVENTIONS §5 — field API `rac_status` di §4/§5 = serialisasi kolom ini):

| Kolom target | Tipe | Null | Asal | Marker | Catatan |
|---|---|---|---|---|---|
| `id` | BIGINT identity | NOT NULL | — | — | PK |
| `application_id` | BIGINT FK → `trx_application` | NOT NULL | key eksternal `credit_id`/ReffId | [VERIFIED×LOCKED nilai] | |
| `financing_model_code` | VARCHAR(5) | NOT NULL | branching key rute CF vs US/SY | [VERIFIED×**LOCKED**] | Allow-list dapat dikonfigurasi (seed `CF`+`US`+`SY`); dipertahankan char-for-char; anggota pasti kode syariah [OPEN — OQ-CRSCORE-07] (§5.1) |
| `decision_id` | VARCHAR(50) | NULL (pre-decision) | `ReffId`/decision eksternal | [VERIFIED×**LOCKED** kontrak] | Kunci idempotency bersama `application_id`; `ux_trx_rac_screening_application_decision` |
| `status` | VARCHAR(15) + CHECK `not_submitted\|pending\|approved\|rejected` | NOT NULL | `rac_get_status`/`STATUS_DESC` eksternal | [INTENT mekanisme ×**LOCKED** nilai kontrak] | `pending` first-class (RAC EC-7); vocabulary penuh feed [OPEN — OQ-RAC-05]; state machine §7.1 |
| `reject_detail` | JSONB | NULL | `rac_get_reject_detail_json` (`$.fullResponse.dataReject[].message`) | [VERIFIED×INTENT] | |
| `decided_at` | TIMESTAMPTZ | NULL | `DtmUpd` sisi eksternal | [VERIFIED×INTENT] | |
| `submitted_at`, `submitted_by` | TIMESTAMPTZ / VARCHAR(50) | NULL | audit request submit | [INTENT] | |

**`log_rac_callback`** (append-only, INSERT-only per DB-CONVENTIONS `log_`; ledger idempotency + audit override BR-02-04): `id`, `application_id`, `decision_id`, `financing_model_code`, `raw_status` (nilai eksternal apa adanya), `payload` JSONB, `applied` BOOLEAN, `apply_result` VARCHAR + CHECK `applied\|already_processed\|held_for_review\|override_applied`, `is_late_override` BOOLEAN, `override_authorized_by` VARCHAR(50) NULL (wajib terisi bila `is_late_override` — late-approval eksplisit + auditable, BR-02-04), `created_at/created_by`. Setiap callback/poll masuk = satu baris, termasuk duplikat (jejak `already_processed` AC-3).

**`stg_rac_callback`** (staging payload mentah ACL; DB-CONVENTIONS §1 `stg_`): boleh di-truncate; TIDAK dikonsumsi logic bisnis langsung; kolom bebas (raw payload + `received_at` + `batch_id`).

> Idempotency ingest: satu `(application_id, decision_id)` di-apply **tepat sekali** (enforced `ux_` di `trx_rac_screening` + ledger `log_rac_callback`); write-back ke RAC **DILARANG** (`no write-back ke RAC`, umbrella). Ingest **ASYNC** = keputusan meeting D-01 Step 8 [INTENT].

### 3.3 `trx_credit_analysis_financial` ← `tr_CA_financial` (18 kolom, semua ter-disposisi)

Detail penghasilan/pekerjaan per sumber income yang divalidasi analis (FK `FK_tr_CA_financial_tr_CA` = satu-satunya FK declared di keluarga CA legacy [VERIFIED]).

| Kolom target | Tipe | Null | Asal (`tr_CA_financial`) | Marker | Catatan |
|---|---|---|---|---|---|
| `id` | BIGINT identity | NOT NULL | `id` bigint identity | [VERIFIED] | PK |
| `credit_analysis_id` | BIGINT FK → `trx_credit_analysis` | NOT NULL | `CA_no` varchar(20) | [VERIFIED×INTENT] | FK nyata (legacy FK by name) |
| `income_type_id` | BIGINT | NULL | `income_type_id` | [VERIFIED×INTENT] | Kandidat FK `mst_` income type |
| `source_of_income` | VARCHAR(50) | NULL | `source_of_income` | [VERIFIED×INTENT] | |
| `corporate_or_business_name` | VARCHAR(255) | NULL | `corporate_or_bussiness_name` (typo legacy distandarkan) | [VERIFIED×INTENT] | |
| `corporate_or_business_address` | VARCHAR(255) | NULL | `corporate_or_bussiness_address` | [VERIFIED×INTENT] | |
| `position` | VARCHAR(50) | NULL | `position` | [VERIFIED×INTENT] | |
| `profession_id` | VARCHAR(4) | NULL | `profession_id` | [VERIFIED×INTENT] | FK kandidat master profesi (`FC_MSTAPP_MCF` — OQ-EXTMASTERS-01) |
| `length_of_work` | VARCHAR(3) | NULL | `length_of_work` | [VERIFIED×OPEN] | Unit (tahun/bulan) tidak terdokumentasi [OPEN] |
| `phone_number` | VARCHAR(20) | NULL | `phone_number` | [VERIFIED×INTENT] | |
| `employee_status` | VARCHAR(50) | NULL | `employee_status` | [VERIFIED×INTENT] | |
| `industry_type_id` | VARCHAR(50) | NULL | `industry_type_id` | [VERIFIED×INTENT] | Dipakai juga param NeoScore (§5.7) |
| `number_of_employees` | INTEGER | NULL | `number_of_employees` varchar(5)(!) | [VERIFIED×INTENT] | Konversi numerik; non-numeric → reject + register |
| `is_other_income` | BOOLEAN NOT NULL DEFAULT false | NOT NULL | `is_other_income` varchar(4)(!) | [VERIFIED×INTENT] | Konversi boolean per DB-CONVENTIONS §3 |
| `income_validation` | VARCHAR(50) | NULL | `income_validation` | [VERIFIED×INTENT] | Verdict validasi analis |

### 3.4 `trx_credit_analysis_bank_account` + `trx_credit_analysis_bank_mutation` ← `tr_CA_account_detail` (10) + `tr_CA_account_mutation` (11)

Umbrella entity `BANK_ACCOUNT_DETAIL` + `MUTATION_ENTRY` (di-key manual analis; rekap rekening 3 bulan `PartialRekening` = read-model dari data ini, bukan tabel).

**`trx_credit_analysis_bank_account`**: `id` BIGINT PK (baru; legacy PK `account_detail_id` varchar(50) → hanya dipakai sebagai kunci mapping di `stg_`, TIDAK dibawa sebagai kolom target), `credit_analysis_id` FK ← `CA_no`, `account_name` VARCHAR(50), `account_number` VARCHAR(50), `bank_id` VARCHAR(10) (FK kandidat `mst_bank`), `previous_month_balance` NUMERIC(18,2) ← numeric(18,0). Semua [VERIFIED×INTENT].

**`trx_credit_analysis_bank_mutation`**: `id` BIGINT ← `id` bigint identity, `bank_account_id` FK ← `account_detail_id` varchar(50), `mutation_period` DATE (hari-1 bulan) ← gabungan `mutation_month` varchar(10) + `mutation_year` varchar(10) (konversi; non-parseable → reject + register), `total_credit_mutation` / `total_debit_mutation` / `end_of_month_balance` NUMERIC(18,2) ← numeric(18,0). Semua [VERIFIED×INTENT].

### 3.5 `trx_credit_analysis_document_check` + `trx_credit_analysis_appi` ← `tr_CA_documents` (75 kolom — census PENUH; `TrCaDocuments` STEP 11)

**Perbaikan struktural**: tabel lebar 75-kolom (pola berulang "statement + check_result" per dokumen) di-**unpivot** menjadi typed rows — pola kolom-per-item dilarang berkembang lagi; item baru = baris `cfg_ca_checklist_item`, bukan ALTER TABLE. Granularity **dua kanal verifikasi independen** (civil-registry/Dukcapil vs "Playstore") **[LOCKED]** (BR-02-29, KYC/AML control record). Row set per `customer_type` P/C [LOCKED]; gap penomoran item 3–8 branch individual [OPEN — OQ-CAUI-05].

**`trx_credit_analysis_document_check`**:

| Kolom target | Tipe | Null | Marker | Catatan |
|---|---|---|---|---|
| `id` | BIGINT identity | NOT NULL | — | PK; legacy `id` bigint identity |
| `credit_analysis_id` | BIGINT FK | NOT NULL | [VERIFIED×INTENT] | ← `CA_no` |
| `item_code` | VARCHAR(40) FK → `cfg_ca_checklist_item` | NOT NULL | [VERIFIED×LOCKED set item] | Katalog item versioned (`cfg_`, per `customer_type` P/C); daftar item = tabel mapping di bawah |
| `channel` | VARCHAR(15) + CHECK `document\|civil_registry\|playstore` | NOT NULL | [VERIFIED×**LOCKED**] | Dua kanal independen WAJIB dipertahankan (BR-02-29) |
| `availability` | VARCHAR(20) | NULL | [VERIFIED×OPEN vocab] | ← kolom `*_statement`/`Ket*`; vocabulary `Terlampir\|Belum Ada\|Tidak Ada` = kandidat enum [OPEN — OQ-CRSCORE-08] |
| `check_result` | VARCHAR(20) | NULL | [VERIFIED×OPEN vocab] | ← kolom `*_check_result`/`HasilCheck*`; vocabulary `Valid\|Tidak Valid\|Tidak Ada` (+ selfie survey) [OPEN — OQ-CRSCORE-08] |
| `detail_value` | VARCHAR(50) | NULL | [VERIFIED×INTENT] | Atribut ekstra per item (lihat mapping: `Bentuk...`, `Situs...`) |
| `valid_until` | DATE | NULL | [VERIFIED×INTENT] | ← `ExpiredDateSTNKBPKB` date |
| — | `ux_(credit_analysis_id, item_code, channel)` | | | Satu verdict per item per kanal |

**Census mapping 75 kolom `tr_CA_documents` → target** (6 kolom non-item: `id`, `CA_no`, 4 audit → PK/FK/audit standar):

| Kolom legacy (pasangan statement + check_result kecuali dicatat) | → `item_code` | `channel` |
|---|---|---|
| `akte_pendirian_statement` + `akte_pendirian_check_result` | `akte_pendirian` | `document` |
| `akte_perubahan_statement` + `akte_perubahan_check_result` | `akte_perubahan` | `document` |
| `SIUP_NIB_statement` + `SIUP_NIB_check_result` | `siup_nib` | `document` |
| `NPWP_statement` + `NPWP_check_result` | `npwp_perusahaan` | `document` |
| `TDP_NIB_statement` + `TDP_NIB_check_result` | `tdp_nib` | `document` |
| `SKMenkeh_statement` + `SKMenkeh_check_result` | `sk_menkeh` | `document` |
| `SKDomisili_statement` + `SKDomisili_check_result` | `sk_domisili` | `document` |
| `checking_account_statement` + `account_check_result` | `checking_account` | `document` |
| `KetKTPPemohon/komisarisDukcapil` + `HasilCheckKTPPemohon/komisarisDukcapil` | `ktp_pemohon_komisaris` | `civil_registry` |
| `KetKTPPemohon/komisarisPlaystore` + `HasilCheckKTPPemohon/komisarisPlaystore` | `ktp_pemohon_komisaris` | `playstore` |
| `KetKTPPasangan/DirutDukcapil` + `HasilCheckKTPPasangan/DirutDukcapil` | `ktp_pasangan_dirut` | `civil_registry` |
| `KetKtppasangan/DirutPlaystore` + `HasilCheckKTPPasangan/DirutPlaystore` | `ktp_pasangan_dirut` | `playstore` |
| `KetKTPPenjamin/DirekturDukcapil` + `HasilCheckKTPPenjamin/DirekturDukcapil` | `ktp_penjamin_direktur` | `civil_registry` |
| `KetKTPPenjamin/DirekturPlaystore` + `HasilCheckKTPPenjamin/DirekturPlaystore` | `ktp_penjamin_direktur` | `playstore` |
| `KeteranganKartuKeluarga` + `HasilCheckKartuKeluarga` | `kartu_keluarga` | `document` |
| `KeteranganNPWPKomisaris` + `HasilCheckNPWPKomisaris` | `npwp_komisaris` | `document` |
| `KeteranganNPWPDirekturUtama` + `HasilCheckNPWPDirekturUtama` | `npwp_direktur_utama` | `document` |
| `KeteranganNPWPDirektur` + `HasilCheckNPWPDirektur` | `npwp_direktur` | `document` |
| `KetBuktiKepemilikanRumah` + `HasilCheckBuktiKepemilikanRumah` + `BentukBuktiKepemilikanRumah` (→ `detail_value`) | `bukti_kepemilikan_rumah` | `document` |
| `KeteranganRekening` + `HasilCheckRekening` | `rekening` | `document` |
| `KetSurveyEmergencyContact` + `HasilCheckSurveyEmergencyContact` | `survey_emergency_contact` | `document` |
| `KeteranganSTNK` (→ `availability`) + `StatusBerlakuSTNK` (→ `check_result`) + `ExpiredDateSTNKBPKB` (→ `valid_until`) | `stnk` | `document` |
| `KeteranganMedsos` + `HasilCheckMedsos` + `SitusSumberInfoMedsos` (→ `detail_value`) | `medsos` | `document` |
| `KeteranganCheckSurveyDomisiliPemohon/perusahaan` + `HasilCheckSurveyDomisiliPemohon/perusahaan` | `survey_domisili_pemohon_perusahaan` | `document` |
| `KeteranganSurveyKantor` + `HasilCheckSurveyKantor` | `survey_kantor` | `document` |
| `KeteranganSurveyUsaha` + `HasilCheckSurveyUsaha` | `survey_usaha` | `document` |
| `KetSurveyLingkunganRTRW/perusahaan` + `HasilCheckSurveyLigkunganRTRW/perusahaan` (typo legacy) | `survey_lingkungan_rtrw_perusahaan` | `document` |
| `KeteranganSurveyDomisiliPenjamin/PenggunaUnit` + `HasilCheckSurveyDomisiliPenjamin/PenggunaUnit` | `survey_domisili_penjamin_pengguna_unit` | `document` |
| `KetSurveyTaksasiUnit` + `HasilCheckSurveyTaksasiUnit` | `survey_taksasi_unit` | `document` |
| `KeteranganRekeningKoran` + `HasilCheckRekeningKoran` | `rekening_koran` | `document` |
| `StatusAPPIPemohon/BadanHukum` + `NominalAPPIPemohon/BadanHukum`; `StatusAPPIPasangan/Komisaris` + `NominalAPPIPasanganKomisaris`; `StatusAPPIPenjamin/Direktur` + `NominalAPPIPenjamin/Direktur` (6 kolom) | → **`trx_credit_analysis_appi`** (di bawah) | — |

**`trx_credit_analysis_appi`** (umbrella entity `APPI_FACILITY_STATUS`): `id` PK, `credit_analysis_id` FK, `party` VARCHAR(30) + CHECK `applicant_or_corporate\|spouse_or_komisaris\|guarantor_or_direktur` (label ganda P/C [LOCKED]), `appi_status` VARCHAR(20) [VERIFIED×**LOCKED** verdict clear/blacklist — compliance control point BR-02-31], `appi_amount` NUMERIC(18,2) ← `Nominal*` varchar(50)(!) (konversi numerik; non-numeric → reject + register). Ekspansi istilah "APPI" [OPEN].

### 3.6 `trx_slik_history_entry` ← `tr_CA_SLIK` (15 kolom; umbrella entity `SLIK_HISTORY_ENTRY`)

Grid transkripsi manual analis atas history fasilitas SLIK/BI (BUKAN auto-populate; gap rekonsiliasi vs FCL viewer [OPEN] BR-02-30). Field set **[LOCKED]** (§5.8). **Disposisi: MIGRATE-READONLY** — hasil biro historis per DATA-MIGRATION-PLAN §4 butir 3; baris baru pasca-cutover ditulis normal ke tabel yang sama.

| Kolom target | Tipe | Null | Asal (`tr_CA_SLIK`) | Marker | Catatan |
|---|---|---|---|---|---|
| `id` | BIGINT identity | NOT NULL | `id` int identity | [VERIFIED] | PK |
| `credit_analysis_id` | BIGINT FK | NOT NULL | `CA_no` varchar(50) | [VERIFIED×INTENT] | FK nyata (legacy tanpa FK) |
| `relation` | VARCHAR(50) | NULL | `relation` | [VERIFIED×**LOCKED**] | applicant/spouse/guarantor/reference |
| `facility_name` | VARCHAR(50) | NULL | `facility_name` | [VERIFIED×**LOCKED**] | |
| `bank_id` | VARCHAR(20) | NULL | `bank_id` | [VERIFIED×**LOCKED**] | |
| `facility_type` | VARCHAR(50) | NULL | `facility_type` | [VERIFIED×**LOCKED**] | |
| `plafon` | NUMERIC(18,2) | NULL | `plafon` varchar(50)(!) | [VERIFIED×**LOCKED** nilai] | Uang disimpan varchar di legacy — konversi numerik; non-numeric → reject + register |
| `outstanding_balance` | NUMERIC(18,2) | NULL | `OS` varchar(50)(!) | [VERIFIED×**LOCKED** nilai] | idem |
| `kol_status` | SMALLINT + CHECK 1..5 | NULL | `KOL_status` varchar(50)(!) | [VERIFIED×**LOCKED**] | Skala kolektibilitas OJK (BR-02-06) |
| `kol_max` | SMALLINT + CHECK 1..5 | NULL | `KOL_max` varchar(50)(!) | [VERIFIED×**LOCKED**] | |
| `lifecycle_status` | VARCHAR(20) | NULL | `status` varchar(50) | [VERIFIED×INTENT] | active/closed dsb — BUKAN kolom status workflow |
| `deleted_at`, `deleted_by` | TIMESTAMPTZ / VARCHAR(50) | NULL | — (baru) | [INTENT] | Soft-delete diizinkan (DB-CONVENTIONS §4): DELETE baris grid wajib meninggalkan audit trail (AC-20) |

### 3.7 `trx_slik_request` (31 kolom) + `trx_slik_request_document` ← `tr_slik_request` + `tr_slik_ojk_application_in` (umbrella entity `SLIK_DIRECT_CHECK_REQUEST`)

Microflow direct-checking SLIK/OJK (BR-02-18; state machine §7.3). PK legacy komposit `(request_id, trans_type_id)` → surrogate `id` + `ux_trx_slik_request_request_id_trans_type_id`.

| Kolom target | Tipe | Null | Asal (`tr_slik_request`) | Marker | Catatan |
|---|---|---|---|---|---|
| `id` | BIGINT identity | NOT NULL | — (baru) | — | PK |
| `request_id` | VARCHAR(20) | NOT NULL | `request_id` (PK-1) | [VERIFIED×**LOCKED**] | Business key request |
| `trans_type_id` | VARCHAR(10) | NOT NULL | `trans_type_id` (PK-2) | [VERIFIED×**LOCKED** external-FK] | Char-for-char (BR-02-11 note) |
| `employee_id` | VARCHAR(20) | NOT NULL | `employee_id` | [VERIFIED×INTENT] | Requester |
| `request_date` | TIMESTAMPTZ | NOT NULL | `request_date` datetime | [VERIFIED×INTENT] | |
| `status` | VARCHAR(20) + CHECK `submitted\|forwarded\|approved\|corrected\|rejected` | NOT NULL | `status` varchar(20) | [VERIFIED×INTENT] | Vocabulary §7.3; nilai legacy di-map, tak ter-map → reject + register |
| `reason` | VARCHAR(255) | NULL | `reason` | [VERIFIED×INTENT] | |
| `hierarchy_trans_id` | BIGINT | NULL | `hierarchy_trans_id` int NOT NULL | [VERIFIED×INTENT] | Tie-in approval hierarki legacy → target: referensi process-instance Flowable / `log_approval_history` (mekanik walk milik 03) |
| `nik` | VARCHAR(20) | NULL | `nik` varchar(20) | [VERIFIED×**LOCKED**] | Payload inquiry — denormalisasi DISENGAJA (INI payload request eksternal, core-entities §16); lebar legacy 20 > NIK-16 regulatori — baris baru divalidasi 16, baris migrasi dibawa apa adanya |
| `nama`, `nama_perusahaan`, `tempat_lahir`, `alamat`, `npwp`, `nppno` | VARCHAR(100)/VARCHAR(255)/VARCHAR(100)/TEXT/VARCHAR(50)/VARCHAR(50) | NULL | kolom sama (6) | [VERIFIED×**LOCKED**] | Identitas payload inquiry SLIK |
| `tanggal_lahir` | DATE | NULL | `tanggal_lahir` date | [VERIFIED×**LOCKED**] | |
| `jenis_debitur` | VARCHAR(50) | NULL | `jenis_debitur` | [VERIFIED×INTENT] | individual/corporate |
| `credit_id_search_source` | VARCHAR(255) | NULL | `sumber_pencarian_credit_id` | [VERIFIED×INTENT] | Distandarkan Inggris |
| `document_ektp`, `document_form_persetujuan`, `document_surat_kuasa`, `document_nib_siup`, `document_akta_pendirian`, `document_npwp`, `document_akta_pengurus` | VARCHAR(255) object-key (7 kolom) | NULL | kolom sama (7) | [VERIFIED×**LOCKED**] | Dokumen consent/pendukung mandat OJK — form persetujuan & surat kuasa = compliance gate, bukan opsional |
| `file_object_key` | VARCHAR(1024) | NULL | `file_path` varchar(max) | [VERIFIED×INTENT] | Object storage, bukan path lokal (DB-CONVENTIONS §3) |
| `check_type` | CHAR(1) | NULL | `type_pengecekan` varchar(1) | [VERIFIED×OPEN] | Makna kode belum terdokumentasi [OPEN] |
| `is_checked` | BOOLEAN NOT NULL DEFAULT false | NOT NULL | `is_checked` varchar(1)(!) | [VERIFIED×INTENT] | Konversi boolean |

**`trx_slik_request_document`** ← `tr_slik_ojk_application_in` (11 kolom; PK legacy komposit `(request_id, field_id)`): `id` PK baru, `slik_request_id` FK ← `request_id` varchar(50), `field_id` INT ← `field_id` (kandidat FK `cfg_` checklist dokumen request), `is_provided` BOOLEAN ← `is_true` bit, `file_name` VARCHAR(255) + `file_object_key` VARCHAR(1024) ← `application_file_name`/`application_file_path` varchar(max), `photo_id` VARCHAR(10), `photo_type_id` VARCHAR(10). Semua [VERIFIED×INTENT]; `ux_(slik_request_id, field_id)`.

### 3.8 `log_slik_bypass` ← `tr_slik_bypass_acquisition` (4 kolom) — **[LOCKED]**, OQ-GAP-03

Audit trail bypass pemeriksaan kolektibilitas SLIK (sensitif regulatori — gap-entities §2.3). Legacy: satu-satunya writer `sp_validation_mobile_to_fincore` cabang ELSE blok "CEK KOL", pesan hard-coded `'by pass slik'`, **TANPA `credit_id`, TANPA aktor, TANPA otorisasi** [VERIFIED]. **Desain target MENUTUP gap itu** — fakta pencatatan [LOCKED] (bentuk boleh berubah, fakta tidak):

| Kolom target | Tipe | Null | Asal | Marker | Catatan |
|---|---|---|---|---|---|
| `id` | BIGINT identity | NOT NULL | `Id` int identity | [VERIFIED] | PK |
| `credit_id` | VARCHAR(20) | NULL (hanya utk baris migrasi) | — (**BARU**; legacy tidak simpan [VERIFIED gap]) | [INTENT] | Baris baru WAJIB terisi; baris migrasi dikorelasi via `map_moofi_fincore` bila resolvable, sisanya NULL + register |
| `acquisition_id` | VARCHAR(40) | NULL | `acquisition_id` | [VERIFIED×**LOCKED**] | ID akuisisi mobile yang di-bypass |
| `reason` | VARCHAR(255) | NOT NULL (baris baru) | `message` (nilai teramati hanya `'by pass slik'`) | [VERIFIED×**LOCKED** nilai historis] | Target: alasan bisnis eksplisit wajib, bukan literal hard-coded |
| `authorized_by` | VARCHAR(50) | NULL (migrasi) / NOT NULL (baru) | — (**BARU**, OQ-GAP-03) | [OPEN — OQ-GAP-03] | Siapa berwenang mengaktifkan bypass; **sampai OQ-GAP-03 diputus, jalur bypass TIDAK diaktifkan di rebuild** (fail-closed BR-02-15) |
| `authorization_ref` | VARCHAR(100) | NULL | — (**BARU**) | [OPEN — OQ-GAP-03] | Nomor persetujuan/memo bila compliance mensyaratkan |
| `created_at`, `created_by` | per `log_` §4 | NOT NULL | `created_on`; aktor baru | [VERIFIED×**LOCKED**] | Baris migrasi: `created_by='legacy_migration'` |

INSERT-only. **Disposisi: MIGRATE-READONLY** (baris historis dibawa apa adanya; kolom baru NULL).

### 3.9 `trx_neoscore_result` + `log_neoscore_call` ← `neo_score_log` (12 kolom)

`neo_score_log` **BUKAN pure log** (gap-entities §3 [INTENT]): (a) `sp_rfa_cm(_car)` **menolak RFA** bila belum ada baris utk `credit_id` (gate `NEOSCORE=1`, error `'View Score harus di klik terlebih dahulu.'`); (b) `sp_get_scoring` membaca `total_score` terbaru sebagai input keputusan [VERIFIED]. Rebuild memisahkan **hasil skor first-class** dari **log panggilan**:

**`trx_neoscore_result`** (satu baris hasil terkini per aplikasi per channel; gate RFA + sumber `total_score`; repeat submission = update dengan hasil segar, JANGAN clobber stale — NeoScore EC-3, AC-14):

| Kolom target | Tipe | Null | Asal (`neo_score_log`) | Marker | Catatan |
|---|---|---|---|---|---|
| `id` | BIGINT identity | NOT NULL | — | — | PK; legacy `id` varchar(15) daily-sequence `YYMMDD`+9-digit = [ARTIFACT] do-not-replicate (DB-CONVENTIONS §6.5) |
| `application_id` | BIGINT FK | NOT NULL | `credit_id` varchar(20) | [VERIFIED×LOCKED nilai] | |
| `acquisition_id` | VARCHAR(40) | NULL | `acquisition_id` | [VERIFIED×INTENT] | ID akuisisi mobile |
| `channel` | VARCHAR(10) + CHECK `default\|car\|mobile` | NOT NULL | derive dari `method`/`menu`/`menu_detail` | [INFERRED×INTENT] | Aturan derive per-baris legacy = deliverable migrasi [OPEN] |
| `total_score` | INTEGER | NULL | `total_score` | [VERIFIED×**LOCKED** gate] | Sumber gate RFA + `sp_get_scoring` |
| `result_structured` | JSONB | NULL | `result` varchar(max) | [VERIFIED×INTENT] | Kontrak terstruktur §5.7 — bukan HTML blob/tag-strip (NeoScore EC-5) |
| `status` | VARCHAR(15) + CHECK `scored\|not_found\|failed` | NOT NULL | `status` varchar(15) | [VERIFIED×INTENT] | Vocabulary legacy `FOUND/NOT FOUND` menyesatkan = [ARTIFACT]; mapping nilai didokumentasi saat migrasi |
| `scored_at` | TIMESTAMPTZ | NULL | `log_date` | [VERIFIED×INTENT] | |
| `version` | INTEGER NOT NULL DEFAULT 0 | NOT NULL | — | — | Optimistic locking (di-update pada re-score) |
| — | `ux_(application_id, channel)` | | | | |

**`log_neoscore_call`** (append-only ledger SEMUA panggilan — idempotency/prior-result check §4 `GET /neoscore-results/{credit_id}` membaca `trx_`; riwayat forensik di sini): `id` BIGINT identity, `application_ref` VARCHAR(20) ← `credit_id`, `acquisition_id`, `user_id` VARCHAR(50), `method` VARCHAR(10), `menu` VARCHAR(20), `menu_detail` VARCHAR(50), `request_parameter` TEXT ← `parameter` varchar(max), `raw_result` TEXT ← `result`, `raw_status` VARCHAR(15) ← `status`, `total_score` INTEGER, `created_at` ← `log_date`, `created_by` ← `user_id`. Semua [VERIFIED×INTENT kecuali dicatat].

**Disposisi**: baris **terbaru per `credit_id`(×channel)** → MIGRATE ke `trx_neoscore_result`; **riwayat penuh** → MIGRATE-READONLY ke `log_neoscore_call`. 12/12 kolom legacy ter-disposisi (kolom `id` legacy [ARTIFACT — discard, diganti identity]).

### 3.10 `trx_risk_scale_analysis` ← `tr_risk_scale_analysis` (6 kolom)

Seleksi manual skala risiko internal per aplikasi per penilai (legacy: `sp_get_risk_scale`/`sp_get_risk_scale_value`/`sp_save_risk_scale`; dikonsumsi modul CM sibling → cross-module read via API, bukan JOIN — ADR-03).

| Kolom target | Tipe | Null | Asal | Marker | Catatan |
|---|---|---|---|---|---|
| `id` | BIGINT identity | NOT NULL | — (baru; legacy PK komposit) | — | PK; `ux_(application_id, employee_id, risk_scale_id)` ← PK komposit legacy |
| `application_id` | BIGINT FK | NOT NULL | `credit_id` varchar(20) | [VERIFIED×LOCKED nilai] | |
| `employee_id` | VARCHAR(10) | NOT NULL | `employee_id` | [VERIFIED×INTENT] | Penilai |
| `risk_scale_id` | INTEGER | NOT NULL | `risk_scale_id` | [VERIFIED×INTENT] | FK kandidat master parameter risiko (`MsRiskParameter`, `FC_MSTAPP_MCF` — OQ-EXTMASTERS-01) |
| `scale_id` | VARCHAR(5) | NULL | `scale_id` | [VERIFIED×INTENT] | Nilai skala terpilih |

Audit: legacy hanya `created_by/on` → kolom `updated_*` diisi = `created_*` saat migrasi. **Disposisi: MIGRATE.**

### 3.11 `log_document_print` (generik; DB-CONVENTIONS §6.2) ← `tr_CA_print_history` (5) + `tr_print_log_slikojk` (4) + kolom print `tr_CA`

Menggantikan SEMUA print-tracking bespoke modul ini: `id` BIGINT identity, `document_type` VARCHAR(30) + CHECK (`credit_analysis_report`, `slik_report`, …), `document_ref` VARCHAR(50) (← `CA_no` / `request_id`), `printed_by` VARCHAR(50) (← `printed_by`), `printed_at` TIMESTAMPTZ (← `printed_date`/`printed_on`), `print_sequence` INTEGER (← `sequence`; NULL utk sumber slikojk), `created_at`/`created_by`. Semua [VERIFIED×ARTIFACT bentuk / INTENT fakta]. INSERT-only.

- Mapping: `tr_CA_print_history(id, CA_no, printed_by, printed_date, sequence)` → `document_type='credit_analysis_report'`; `tr_print_log_slikojk(log_id, request_id, printed_by, printed_on)` → `document_type='slik_report'`; kolom `tr_CA.last_printed_by/on, sum_of_print` → derivable dari log (DISCARD kolom, §3.1).
- Tabel **shared lintas modul** (pola sama berulang di `tr_PO`, `tr_NPP_print`, `tr_BPKB` — core-entities); definisi kanonik di PRD ini; **ownership final = SHARED cross-cutting, ditetapkan di BE-00 §6.3** (catatan setelah registry) — men-supersede status [USULAN] sebelumnya. **Disposisi: MIGRATE-READONLY.**

### 3.12 `tr_CA_approval_progress` + `tr_CA_approval_progress_transaction` → Flowable runtime + `log_approval_history` (milik 03) — TIDAK ada tabel 02 baru

Dua tabel legacy **byte-identik 23 kolom** (core-entities §11 [VERIFIED]) = shadow pattern — DILARANG direplika (DB-CONVENTIONS §6.1); konsolidasi ke SATU model approval-progress kanonik (BR-02-26). Live-vs-history antar keduanya BELUM terkonfirmasi → [OPEN — OQ-CORE-08 / OQ-CRSCORE-03]; scope kunci `CF_No` (CA-only vs shared) → [OPEN — OQ-DATA-03].

Disposisi kolom (berlaku utk kedua tabel):

| Kelompok kolom legacy | Target | Marker |
|---|---|---|
| Kunci komposit `company_id, CF_No, approval_scheme_id, approval_level_id, NIK` | `log_approval_history` (key event) + korelasi process-instance Flowable | [VERIFIED×LOCKED identitas event] |
| Keputusan approver: `user_status_type`, `user_keterangan_approval`, `user_dtm_status_changed`, `user_mandatory` | `log_approval_history` (append-only; audit regulatori independen dari engine — DB-CONVENTIONS §8) | [VERIFIED×**LOCKED**] |
| Konfigurasi walk: `policy_id`, `level_active`, `level_quorum`, `level_escalation_type/day`, `level_request_date/complete_date`, `next_approval_level_id`, `media_approve`, `is_notification` | `cfg_hierarchy_matrix` + definisi BPMN (state runtime = tabel `ACT_*` Flowable) | [VERIFIED×INTENT] |

**Disposisi migrasi**: riwayat keputusan → MIGRATE-READONLY ke `log_approval_history`; walk **in-flight TIDAK dimigrasi** ke Flowable (DATA-MIGRATION-PLAN §4 butir 4 + §5).

### 3.13 `cek_apuppt_log` (6 kolom) — **OQ-GAP-06 (porsi tabel ini) ✅ RESOLVED — evidence (2026-07-14)**

`[ARTIFACT: log]` compliance-adjacent (AML): **idempotency-marker cek APU-PPT per aplikasi** (`credit_id` PK + `is_active` varchar(1)). Writer/reader ketemu (gap-entities §4 OQ-GAP-06): `TrCasRepositoryEF.CekApupptLogAsync` (`FINCORE SERVICE/FINCORE.SERVICE.CREDITS/Repositories/EF/TrCasRepositoryEF.cs:1654-1689`) + reader `CheckApupptLogAsync` (:1629-1653), endpoint `POST validasi/cekapupptlog` — call pertama insert baris (belum pernah dicek), call berikutnya SUCCESS; 0 SP [VERIFIED]. Disposisi: **MIGRATE-READONLY (arsip)**; semantik idempotency-marker di rebuild dilebur sebagai status pada entitas AML/PEP (§3.14) — bukan tabel marker terpisah.

### 3.14 Entitas pendukung lintas-sumber (tanpa tabel legacy milik modul ini)

| Entitas target [INTENT — schema detail menyusul keputusan terkait] | Isi | Sumber legacy & catatan disposisi |
|---|---|---|
| `BUREAU_COLLECTIBILITY_SNAPSHOT` (read-model) | grid per-bank ≤24 bulan (year-month, tier 1-5, days-overdue); SLIK tier-1/mirror → Pefindo fallback | `sp_get_fcl_result_history_kol_slik` atas DB FCL **eksternal** (PDF v2 STEP 11) — bukan tabel FC_ACQ_MCF; hasil biro historis = MIGRATE-READONLY; tier `CLKNAE*` [OPEN — OQ-SLIK-01] |
| `INTERNAL_SCORING_RESULT` | applicant grade → weight → risk-category (9 param); blacklist override | output `sp_get_customer_scoring`/`sp_get_scoring`; persist legacy di `tr_CAS_LKK_Score` (anak `tr_CAS` — disposisi tabelnya milik **BE-01** §3); 02 memiliki hasil scoring go-forward (ringkasan dipersist ke `trx_credit_analysis.risk_tier` §3.1) |
| `AML_PEP_SCORE` | skor PEP/profile/product/geo/channel → bucket low/med/high | `sp_check_APUPPT_score`; persist legacy `tr_CAS_APUPPT(_risk)` (anak `tr_CAS` — disposisi milik **BE-01** §3); reader [OPEN — OQ-CRSCORE-04]; kandidat rumah flag `cek_apuppt_log` (§3.13) |

### 3.15 Shared entities yang DIRUJUK (bukan milik 02 — tabel target didefinisikan di PRD pemiliknya)

- **`CREDIT_APPLICATION`** (owner 01) — `status` enum: `draft|rfa_locked|risk_gated|analyzing|committee|approved|rejected|corrected|cancelled`. `otr_price` **[LOCKED]** source. Service ini menggerakkan transisi `rfa_locked→risk_gated→analyzing→committee`. RFA lock STEP 9 (moofi-path `sp_approve_cm_moofi`; dual path OQ-GT-01 ✅ RESOLVED — evidence, §11: trigger manual vs agent).
- **`CREDIT_MEMO`** (owner 04; finalize) — `trans_type_id` **[LOCKED]** external-FK **disusun dari 02**. Komposisi = (application-type code)+(menu-entry prefix)+(sequence), risk-tier-qualified. **WAJIB dipertahankan char-for-char** (dicocokkan ke tabel referensi approval-hierarchy di `FC_MSTAPP_MCF`). OP/ULI/LCR di-lock saat committee-approve STEP 12 (delta v2: financial lock pindah KE DALAM approve komite via moofi path) — milik 03/04.
- **`CUSTOMER`** (owner 05 tulis; 01 dedup) — `national_id` (NIK) & `tax_id` (NPWP) **[LOCKED]** (identitas OJK/AML); dikonsumsi sebagai key bureau/Dukcapil.

---

## 4. API Endpoint

> [KEPUTUSAN DESAIN BARU] Kontrak ditulis level resource+field. Bahasa BE = **Java** (D-12 [LOCKED]); transport **REST/JSON [USULAN]** dengan Spring Boot (framework & transport final [OPEN] OQ-ARCH-STACK; arsitektur ITEC D-11). `Auth/Role` bersifat desain target (OQ-MCP-01); **tidak ada role super-user (D-09)**. Endpoint bertanda ⊕ = tambahan hasil cross-check kebutuhan layar FE (`62-credit-analysis-screens.md`) — USULAN ter-ground pada layar legacy.

| Method | Path | Deskripsi | Auth/Role |
|---|---|---|---|
| POST | `/applications/{id}/rac-screening` | Submit/trigger gate RAC STEP 10 (route CF/US via ACL). Return **envelope call**, bukan decision. | System, CMO |
| GET | `/applications/{id}/rac-screening` | Baca status/decision RAC terkini (incl. `pending`). | Analyst, System, 03 (read) |
| GET | `/applications/{id}/rac-screening/history` | History-check RAC (helper first-submission branch; legacy `sp_Cek_rac_history`). | Analyst, System |
| POST | `/rac-screening/callbacks` | **Ingest callback/poll decision async** dari Bank Mega ACL (D-01 Step 8); idempotent by `application_id+decision_id`. | RAC ingester (service-to-service) |
| GET ⊕ | `/credit-analysis/worklist` | Worklist CA paginated, **scoped branch + analyst** (outcome scoping [INTENT]; mekanik list bebas). Param: `search_term`, `page`. | Analyst |
| POST | `/applications/{id}/credit-analysis` | Buka/inisiasi record analisis (application → `analyzing`). | Analyst |
| GET | `/applications/{id}/credit-analysis` | Baca record analisis + **ratio ter-komputasi server-side** (`dsr`, `ltv`, `dp_pct`, `dp_net_pct`) sebagai field API kanonik (BR-02-32). | Analyst, 03 (read) |
| PATCH | `/credit-analysis/{id}` | Update review bureau / financial (income utama/tambahan/pasangan, MRP) / narasi — hanya saat `status ∈ {queued, under_review}` (§7.2; vocabulary "Draft" legacy tidak dipakai). | Analyst |
| PUT ⊕ | `/credit-analysis/{id}/document-checklist` | Tulis verdict checklist dokumen granular (availability+result per item per kanal; row set by `customer_type` P/C). | Analyst |
| POST ⊕ | `/credit-analysis/{id}/slik-history-rows` | Tambah baris grid transkripsi history SLIK/BI. | Analyst |
| DELETE ⊕ | `/credit-analysis/{id}/slik-history-rows/{rowId}` | Hapus baris grid transkripsi (audit trail dipertahankan). | Analyst |
| PUT ⊕ | `/credit-analysis/{id}/bank-accounts` | Tulis bank-account detail + mutation entries + rekap 3 bulan. | Analyst |
| GET ⊕ | `/applications/{id}/documents` | Viewer read-only dokumen upload intake (6 kategori foto: mandatory ID, house, customer, financial-analysis, vehicle-document, vehicle) — data milik 01, disajikan utk layar CA. | Analyst |
| GET | `/applications/{id}/bureau/collectibility` | Grid kolektibilitas SLIK/FCL ≤24 bulan (fallback Pefindo transparan). | Analyst |
| GET ⊕ | `/applications/{id}/fcl-result` | Viewer FCL/SLIK read-only multi-tab: `?party=applicant\|spouse\|guarantor\|reference&view=header\|summary\|history\|detail` (legacy `FCLResultController`, `sp_get_fcl_result_*`). | Analyst |
| GET | `/applications/{id}/bureau/pefindo` | Lookup asset/akad Pefindo (standalone; `sp_get_pefindo_data(_plafon)`). | Analyst |
| GET | `/applications/{id}/dukcapil-result` | Read-only civil-registry match (tanpa gate). | Analyst |
| POST | `/credit-analysis/{id}/scoring` | Hitung internal grade + risk-category (engine LKK). | Analyst, System |
| GET | `/applications/{id}/risk-category` | Baca risk-tier + komposisi penyusun `trans_type_id`. | Analyst, 03 (read) |
| GET | `/applications/{id}/neoscore-param` | Assemble parameter NeoScore; `?channel=default\|car\|mobile` (3 kontrak berbeda — lihat §5.7). | System / caller tier |
| POST ⊕ | `/applications/{id}/neoscore/score` | **[USULAN — target]** BE-owned outbound call NeoScore via ACL (TLS, kontrak terstruktur); menggantikan call FE-tier legacy. Idempoten: cek prior-result sebelum call vendor. Pending keputusan §11 (residual OQ-NEOSCORE-01). | Analyst, System |
| POST | `/neoscore-results` | Log hasil NeoScore (write-back; tetap ada utk kompatibilitas masa transisi bila call belum dipindah ke BE). | Caller tier / internal |
| GET | `/neoscore-results/{credit_id}` | Idempotency/prior-result check. | Caller tier / internal |
| POST | `/credit-analysis/{id}/recommendation` | Submit rekomendasi (RFA): jalankan DSR + freshness advisory; emit `AnalysisComplete`; application → `committee`. | Analyst |
| GET | `/credit-analysis/{id}/recommendation/advisories` | Baca pesan advisory (DSR>40%, bureau >30 hari). | Analyst |
| GET ⊕ | `/credit-analysis/{id}/print` | Render laporan CA PDF per customer-type (`Perseorangan`/`BadanUsaha`); konten = artefak regulator-facing, fidelity field [LOCKED] pending mapping (OQ-CAUI-01). | Analyst |
| GET ⊕ | `/credit-analysis/{id}/slik-export` | Export SLIK (Excel) — artefak regulator-facing. | Analyst |
| POST | `/slik-requests` | Buat request direct-checking registri (microflow terpisah). | Requester |
| POST | `/slik-requests/{id}/approval` | Aksi approval microflow SLIK (approve/forward/correct/reject). | SLIK approver |
| GET | `/slik-requests` | List/paginate request direct-checking. | Requester, approver |

---

## 5. Kontrak Request/Response

### 5.1 `POST /applications/{id}/rac-screening` — Submit gate RAC (STEP 10)

Request (wajib: `financing_model_code`, `created_by`):
```json
{
  "financing_model_code": "CF",
  "created_by": "EMP-00123"
}
```
Response `202 Accepted` — **hanya envelope call, BUKAN decision** (decision datang async; RAC EC-4; D-01 Step 8):
```json
{
  "application_id": "APP-2026-0001",
  "rac_status": "pending",
  "submitted_at": "2026-07-07T03:12:00Z",
  "call_envelope": { "error_number": 0, "error_message": null },
  "correlation_id": "corr-8f1a"
}
```
- `400` **HANYA** bila `financing_model_code` tidak ada dalam **allow-list financing-model code yang dapat dikonfigurasi** (seed: `CF` konvensional + kode syariah terkonfirmasi `US`/`SY`). Nilai yang berada di allow-list **dipertahankan char-for-char** (`[LOCKED]`; JANGAN dinormalisasi atau dipersempit diam-diam). Himpunan pasti kode syariah (US vs SY, dipakai bergantian) **[OPEN] OQ-CRSCORE-07** — sampai diputus, seed mencakup CF+US+SY agar nilai legacy sah tidak ditolak; **JANGAN** hard-reject dua-nilai. `502` bila ACL Bank Mega tak reachable (fail-closed, tidak menandai approved). Error envelope seragam `{code,message,details?,correlation_id}`.
- Payload profil risiko field-level yang dibutuhkan Bank Mega di luar `credit_id`/`CreatedBy` = [OPEN] OQ-RAC-07 (legacy hanya 2 parameter; profil dirakit di dalam SP eksternal yang opaque).

### 5.2 `POST /rac-screening/callbacks` — Ingest decision async (idempotent; D-01 Step 8)

Request (wajib: `application_id`, `decision_id`, `rac_status`):
```json
{
  "application_id": "APP-2026-0001",
  "decision_id": "REFF-77219",
  "financing_model_code": "CF",
  "rac_status": "REJECTED",
  "reject_detail": [{ "message": "DSR melebihi ambang RAC" }],
  "decided_at": "2026-07-07T03:20:00Z"
}
```
Response `200 OK`:
```json
{ "application_id": "APP-2026-0001", "decision_id": "REFF-77219",
  "applied": true, "resulting_status": "rejected", "correlation_id": "corr-8f2b" }
```
- **Idempotent**: `(application_id, decision_id)` yang identik → `applied:false, "reason":"already_processed"`, `200` (tidak menggandakan efek). Sumber: umbrella boundary_ownership; `rac-...md §9`.
- **Late-approval override**: bila decision baru (`decision_id` berbeda) `APPROVED` tiba setelah status lokal sudah final berlawanan (rejected/corrected), override HANYA boleh dijalankan bila **eksplisit, ter-log, auditable** — bukan silent flip (perbaikan RAC EC-2; kebijakan window [OPEN] OQ-RAC-03).
- **No write-back ke RAC**; ACL tidak melakukan DML mentah ke DB Bank Mega (perbaikan RAC EC-5).
- Resolusi bisnis (PDF v2 STEP 10): `REJECTED` → stop (mekanisme blocking [OPEN] OQ-CRSCORE-06); `APPROVED` → CA queue.
- **Emisi event `RacDecisionReceived`** — **RESOLVED by convention (2026-07-14; BE-03 §11 OQ-BE03-04 → opsi a)**: pada apply efektif (`applied:true`), 02 meng-emit **`RacDecisionReceived`** (payload minimum: `credit_id`, `decision_id`, `decision`) via **`out_event` dalam transaksi yang sama** dengan tulis `log_rac_callback` + status (ADR-04 outbox). Konsumen: wait-state (receive task) Flowable komite 03 — serapan pola poll legacy `sp_agent_rac_to_cm_bulk` yang membaca hasil RAC untuk melepas keputusan (BE-03 §3.4 #8; agent polling tidak direplika). Replay idempotent (`applied:false`) **TIDAK** meng-emit ulang. Dasar boundary: seam "RAC async callback ingest (STEP 10)" milik 02 (BE-00 §5).

### 5.3 `POST /credit-analysis/{id}/recommendation` — Submit rekomendasi (RFA STEP 11)

Request (wajib: `recommendation`, `recommendation_justification`, `positive_negative_narrative`, `debtor_group`, `ojk_economic_sector`):
```json
{
  "recommendation": "recommended",
  "recommendation_justification": "Kapasitas cukup; agunan memadai.",
  "positive_negative_narrative": "Positif: histori kol 1. Negatif: DSR mendekati ambang.",
  "debtor_group": "GRP-01",
  "ojk_economic_sector": "OJK-45201"
}
```
Response `200 OK` (advisory dikembalikan inline, non-blocking di STEP 11):
```json
{
  "credit_analysis_id": "CA-0001",
  "status": "recommended",
  "advisories": [
    { "code": "DSR_EXCEEDED", "message": "DSR 47% > 40%", "severity": "advisory" },
    { "code": "BUREAU_STALE", "message": "Cek bureau > 30 hari; ulang sebelum NPP", "severity": "advisory" }
  ],
  "event_emitted": "AnalysisComplete",
  "correlation_id": "corr-9c01"
}
```
- `ojk_economic_sector` **[LOCKED]** — nilai WAJIB match OJK code list.
- Advisory DSR/freshness di STEP 11 **advisory** (BR-02-13,14); apakah menjadi hard-block adalah **[OPEN] OQ-CRSCORE-02** (default target regulated gate = fail-closed, [KEPUTUSAN DESAIN BARU], tapi belum diputus untuk kedua check ini — lihat §11). Freshness hard-gate ada di STEP 15/NPP (05), **jangan dikonflasi**.
- Prasyarat submit: `recommendation` + justifikasi terisi (legacy client-side guard `CreditAnalyst.js:118-159`; rebuild: **enforce server-side** [INTENT] BR-CAUI-3).

### 5.4 `GET /applications/{id}/risk-category` — Risk-tier & komposisi trans_type_id

Response `200 OK`:
```json
{
  "application_id": "APP-2026-0001",
  "risk_tier": "high",
  "blacklist_override_applied": false,
  "trans_type_id_composition": {
    "application_type_code": "01",
    "menu_entry_prefix": "CM",
    "risk_tier_qualifier": "H",
    "note": "trans_type_id final [LOCKED] external-FK, char-for-char"
  }
}
```
- `blacklist_override_applied:true` → `risk_tier="very_high"` **[LOCKED]** (BR-02-08).
- Komposisi disusun HANYA di sini, dikonsumsi 03 untuk routing dinamis `trans_type_id` + OP + risk level (D-01 Step 10; D-10 skala risiko). Ladder `AA00000001` distinct.

### 5.5 `POST /neoscore-results` — Log hasil NeoScore (tabel `trx_neoscore_result` + `log_neoscore_call` §3.9)

Request (wajib: `credit_id`, `result`):
```json
{ "credit_id": "APP-2026-0001", "parameter": "default", "result": "{...structured...}", "user_id": "EMP-00123" }
```
Response `201 Created`:
```json
{ "credit_id": "APP-2026-0001", "total_score": 720, "status": "logged", "correlation_id": "corr-a1" }
```
- Catatan: `status:"logged"` pada response = status OPERASI log (envelope), BUKAN kolom `trx_neoscore_result.status` (vocabulary kolom = `scored|not_found|failed`, §3.9) — jangan dikonflasi.
- Parsing hasil **WAJIB** memakai kontrak terstruktur (JSON/schema-validated), BUKAN tag-strip + label-offset (perbaikan NeoScore §6/EC-5).
- Repeat submission `credit_id` sama **WAJIB** menyimpan hasil segar; JANGAN clobber dengan stale placeholder (perbaikan NeoScore EC-3).
- Legacy write-back linked-server `[macf-dbstg] DUMP_MACF.tbl_Submit.score_rimo` = single point of failure [ARTIFACT]; bila masih dibutuhkan (OQ-NEOSCORE-03), pisahkan sebagai step retryable (outbox), JANGAN coupling ke response log utama (NeoScore EC-4).

### 5.6 `GET /applications/{id}/bureau/collectibility` — Grid kolektibilitas

Response `200 OK` (tier 1-5 per bulan; fallback SLIK→Pefindo transparan):
```json
{
  "application_id": "APP-2026-0001",
  "source_tier": "slik_primary",
  "banks": [
    { "bank_name": "Bank X", "months": [
      { "year_month": "2026-06", "collectibility": 1, "days_overdue": 0 },
      { "year_month": "2026-05", "collectibility": 2, "days_overdue": 12 }
    ]}
  ]
}
```
- `collectibility` **[LOCKED]** skala OJK; **satu fungsi tunggal** dipakai lintas source (perbaikan SLIK EC-6 / Pefindo EC-3, dua komputasi inkonsisten).
- Tier fallback: SLIK primary → SLIK mirror → Pefindo → `CLKNAE*` (identitas Tier-3 [OPEN] OQ-SLIK-01; jangan diasumsikan "SLIK tambahan").

### 5.7 NeoScore — kontrak parameter (3 channel) & hasil terstruktur

**Parameter** (`GET /applications/{id}/neoscore-param?channel=...`) — tiga kontrak channel **berbeda field set** (bukan rename; `neoscore.md §5`):
- `default` (legacy `sp_get_param_neo_score`): `Name, Age, HomeOwnershipStatus, Phone, Gender, NIK, MarriageStatus, MonthlyIncome, AdditionalIncome, Profession, IndustryType, ZipCode, HasGuarantor, Installment, AssetCost, Tenor, DownPayment, ItemTypeName, Year, NumberOfDependents`.
- `car` (legacy `sp_get_param_neo_score_car`): `Gender, MarriageStatus, Profession, HomeOwnershipStatus, IndustryType, ProvinceName, InsModelName, MonthlyIncome, AdditionalIncome, CoupleIncome, NumberOfDependents, Tenor, AssetCost, Installment, CoupleAge, YearsOfWorkExperience, DownPayment`.
- `mobile` (legacy `sp_get_param_neo_score_mobile`): field set ≈ default; caller legacy tak terlokasi (OQ-NEOSCORE-02).

Perbaikan WAJIB lintas channel (do-not-replicate): (a) `NIK` = **raw national ID kanonik** di semua channel (legacy default mengirim komposit `identity_number + '-' + credit_id` — NeoScore EC-1); (b) `NumberOfDependents` dipopulasi dari data aktual, BUKAN hardcode 0 (NeoScore EC-2; rasional legacy [OPEN] OQ-NEOSCORE-05).

**Hasil terstruktur** (`POST /applications/{id}/neoscore/score` response / payload `result` pada §5.5) — shape riil terdokumentasi pada sample payload legacy (`CACarController.cs:1096-1098`, `62-...screens.md §9 EC-1`):
```json
{
  "total_score": 720,
  "recommendation": "PASS",
  "factor_validity": [ { "factor": "phone", "valid": true } ],
  "phone_verification_detail": { },
  "health_insurance_enrollment_detail": { },
  "fintech_loan_history_detail": { },
  "ewallet_topup_detail": { }
}
```
Field naming final dinegosiasikan dgn vendor (kontrak terstruktur menggantikan HTML blob; NeoScore EC-5) — [OPEN] residual OQ-NEOSCORE-01.

### 5.8 `POST /credit-analysis/{id}/slik-history-rows` — Baris transkripsi history SLIK/BI (tabel `trx_slik_history_entry` §3.6)

Request (field set **[LOCKED]** per `62-...screens.md §4`):
```json
{
  "relation": "applicant",
  "facility_name": "KPR",
  "bank": "Bank X",
  "facility_type": "installment",
  "plafon": 250000000,
  "outstanding_balance": 120000000,
  "kol_status": 1,
  "kol_max": 2,
  "lifecycle_status": "active"
}
```
Response `201 Created` `{ "row_id": "SLK-ROW-001" }`. Catatan mapping: field API `bank` = serialisasi kolom `trx_slik_history_entry.bank_id` (§3.6; kandidat lookup `mst_bank`) — nilai contoh nama bank mengikuti isi legacy `bank_id` varchar; penamaan final field API mengikuti kolom target. DELETE baris mempertahankan audit trail. Grid ini **tidak** otomatis ter-rekonsiliasi dengan FCL viewer (legacy gap) — kebijakan rekonsiliasi [OPEN], lihat BR-02-30.

---

## 6. Aturan Bisnis

| ID | Aturan | Sumber KB | Marker | Catatan |
|---|---|---|---|---|
| BR-02-01 | Gate RAC (STEP 10) bercabang **CF (konvensional)** vs **US (syariah)** berdasarkan financing-model code (sistem mendeteksi branch code / product type utk pilih rute per PDF v2); tiap rute baca/clear record set eksternal terpisah. | `21-...md BR-CRSCORE-1`; `rac-...md §1`; ground-truth v2 STEP 10 | **[LOCKED]** | Kontrak external-system; **WAJIB dipertahankan**. Arti kode US [OPEN] OQ-CRSCORE-07 |
| BR-02-02 | RAC di-model **async submit + callback/poll ingest** (D-01 Step 8 [INTENT] — keputusan meeting); submit hanya balikkan envelope call, decision (accept/reject) tiba terpisah. State **`pending`** adalah first-class. | `rac-...md §6, §10 EC-4,EC-7`; D-01 Step 8 | [INTENT] | JANGAN model sync call-and-get-decision |
| BR-02-03 | Ingest decision RAC **idempotent** by `application_id+decision_id`; efek di-apply tepat sekali; **no write-back ke RAC**; tanpa DML mentah ke DB Bank Mega; request via **ACL** (D-01 Step 7). | `rac-...md §9, EC-5`; umbrella boundary_ownership; data-mutation-policy anti-patterns; D-01 Step 7/8 | [INTENT] (mekanisme) / **[LOCKED]** (no cross-DB DML) | Perbaikan: owned API contract (submit + cancel/resubmit idempotent) |
| BR-02-04 | Late-approval override (decision `APPROVED` terlambat membalik status final berlawanan) HARUS eksplisit, ter-log, auditable — bukan silent flip. Window legacy 120 menit. | `rac-...md §9, §10 EC-2` | [INTENT] / do-not-replicate | Kebijakan window [OPEN] OQ-RAC-03 |
| BR-02-05 | CM reopen-for-correction memicu **re-screen RAC**; model sebagai event idempotent eksplisit, BUKAN destructive delete record RAC eksternal. Open CM STEP 13 (v2) mengembalikan ke "Step 1–12" — granularity [OPEN] OQ-GT-03. | GOTCHA-11; `rac-...md §3(c)`; ground-truth v2 STEP 13 | [VERIFIED][INTENT] / do-not-replicate | Legacy `sp_trans_open_cm:53-78` hanya cover item_id='001' (EC-6) |
| BR-02-06 | Skala kolektibilitas OJK: `0→1, 1-90→2, 91-120→3, 121-180→4, >180→5`; **satu fungsi tunggal** dipakai konsisten lintas SLIK/Pefindo. | `21-...md BR-CRSCORE-3`; `slik-ojk.md §6, EC-6`; `pefindo.md §6, EC-3`; data-mutation-policy | **[LOCKED]** | Regulasi OJK nasional; **WAJIB dipertahankan** |
| BR-02-07 | Internal scoring: risk-category disusun dari 9 parameter berbobot (grade, indikator historis branch/dealer, tenor, DP-ratio, blacklist, DSR, principal, jarak residensi) via master threshold configurable. | `21-...md BR-CRSCORE-7` | [INTENT] | Bobot/threshold configurable |
| BR-02-08 | **Blacklist flag** memaksa bucket **very-high** terlepas parameter lain. | `21-...md BR-CRSCORE-8` | **[LOCKED]** | Satu-satunya kontrol AML-adjacent di scoring engine |
| BR-02-09 | Mapping grade→weight **WAJIB monotonic**; **weight strictly TURUN dengan risiko** (weight = skor favorability/kualitas — grade terbaik ⇒ weight tertinggi, grade terburuk ⇒ weight terendah; BUKAN penalti yang naik). Perbaiki bug legacy di mana grade kedua-terburuk = weight grade terbaik. Tambah unit test asersi **`weight strictly decreases with risk`**. | GOTCHA-3; `21-...md §9 EC-1, BR-CRSCORE-11` | [VERIFIED][ARTIFACT] / do-not-replicate | `sp_get_customer_scoring:437-444`. **Catatan arah**: hanya GOTCHA-3 memfiksasi tanda (turun); EC-1 & BR-CRSCORE-11 hanya "monotonic" tanpa arah — tim WAJIB verifikasi tanda terhadap SP `sp_get_customer_scoring:437-444` saat implementasi. |
| BR-02-10 | Guard pembagian nol pada parameter persentase (DP-ratio, principal-ratio) yang membagi dengan cost aset — dan **semua** komputasi ratio API (DSR, LTV, DP%, DP-net%): legacy FE punya 3 komputasi server tanpa guard + 1 client dengan guard (FE EC-2). | `21-...md §9 EC-10`; `62-...screens.md §9 EC-2` | [VERIFIED] / do-not-replicate | Tanpa guard → runtime arithmetic error |
| BR-02-11 | Output risk-category dibaca 02 untuk menyusun `trans_type_id` (risk-tier-qualified) yang meng-drive routing komite dinamis **`trans_type_id` + OP (Plafond Hutang Pokok) + risk level** (D-01 Step 10); disusun **di satu tempat**, dikonsumsi 03. Hierarki approval bergantung **skala risiko** (D-10 [LOCKED]). | `21-...md BR-CRSCORE-9`; umbrella conventions; D-01 Step 8/10; D-10 | **[LOCKED]** (external-FK) | Char-for-char match `FC_MSTAPP_MCF`; ladder `AA00000001` distinct (GOTCHA-5); rekonsiliasi `sp_get_next_approval_scheme` doc-vs-code milik 03 |
| BR-02-12 | Fast-track category untuk satu vehicle-category: DP-nol → murni score NeoScore ≥ threshold + repeat-order flag; DP-lain → threshold score + min-DP% keduanya terpenuhi. | `21-...md BR-CRSCORE-10` | [INTENT] | Terkait IA lane (D-01 Step 11) eligibility [OPEN] OQ-MEET-04 |
| BR-02-13 | **DSR cap 40%** (existing+proposed installment / total income) dihitung saat submit rekomendasi; di STEP 11 dikembalikan sebagai pesan advisory. | `21-...md BR-CRSCORE-5, §9 EC-4` | [VERIFIED][OPEN] | Hard-block vs advisory [OPEN] OQ-CRSCORE-02 / OQ-REG-06; formula (spouse income) [OPEN] OQ-CAUI-07 → BR-02-28 |
| BR-02-14 | **Freshness bureau 30-hari** di-flag saat submit rekomendasi sebagai advisory ("ulang sebelum NPP"). **ADVISORY di STEP 11** — distinct dari hard-gate 403+rollback STEP 15/NPP (05). **Tiga jendela 30-hari berbeda, JANGAN dikonflasi**: (1) freshness bureau advisory STEP 11 (aturan ini); (2) expiry SLIK/FCL hard-gate di NPP STEP 15 (05); (3) expiry verifikasi konsumen Vertel 30-hari strict (D-01 Step 14; konsekuensi [OPEN] OQ-MEET-05, milik 05). | `21-...md BR-CRSCORE-6`; BR-VERIF-7; boundary_ownership; D-01 Step 14 | [VERIFIED][OPEN] | Enforcement [OPEN] OQ-CRSCORE-02 / OQ-SLIK-05 |
| BR-02-15 | Regulated gate (AML/SLIK/DSR/verification) default **fail-closed**: kegagalan mid-check memblokir, bukan meloloskan. | GOTCHA-2; umbrella conventions `status_enum_note` | **[KEPUTUSAN DESAIN BARU]** | Global policy [OPEN] OQ-REG-06; belum diputus untuk DSR/freshness STEP 11 |
| BR-02-16 | First-submission vs resubmission RAC = jalur berbeda: resubmission clear record prior; first-submission konsultasi history-check helper. Pertahankan keduanya distinct. | `21-...md §9 EC-9`; `rac-...md §3(a),(d)` | [VERIFIED] | Vocabulary `message` helper [OPEN] OQ-RAC-06 |
| BR-02-17 | debtor-group & ojk_economic_sector di-assign **saat analisis**, bukan intake. | `21-...md BR-CRSCORE-12` | [VERIFIED][INTENT] | `ojk_economic_sector` [LOCKED] value |
| BR-02-18 | Microflow direct-checking SLIK/OJK punya lifecycle+vocabulary sendiri (submitted/forwarded/approved/corrected/rejected) dengan routing dari department requester, terpisah dari status record analisis. | `21-...md BR-CRSCORE-14`; `slik-ojk.md §3` | [VERIFIED][INTENT] | |
| BR-02-19 | Batch bridge OJK-checking mengelompokkan request per NIK, restart grup bila >30 hari sejak request sebelumnya; stage request terbaru per grup; job companion rekonsiliasi hasil. Konsolidasi logika grouping (jangan duplikat). | `21-...md BR-CRSCORE-15`; `slik-ojk.md §9, EC-3` | [VERIFIED][INTENT] | |
| BR-02-20 | **NeoScore**: legacy call outbound = **FE tier** (`NeoScoreServices.cs`, plain HTTP + PII + render `@Html.Raw` — RESOLVED, FE KB EC-1); JANGAN direplika. Target: **[USULAN]** call BE-owned via ACL (TLS, kontrak terstruktur JSON schema-validated — bukan tag-strip/offset); repeat submission simpan hasil segar (jangan clobber stale). NIK kanonik raw + NumberOfDependents populate konsisten lintas 3 channel. | `neoscore.md §1,§5,§6, EC-1,EC-2,EC-3,EC-5`; `62-...screens.md §9 EC-1` | [VERIFIED][LOCKED] (parse=de-facto contract) / do-not-replicate | Residual [OPEN]: kontrak vendor terstruktur + caching policy motor-vs-car (FE EC-7, OQ-CAUI-06) |
| BR-02-21 | Reminder job pending-approver WAJIB derive recipient dari approver-of-record live; JANGAN hardcode nama/email. | `21-...md BR-CRSCORE-19, §9 EC-3` | [VERIFIED][ARTIFACT] / do-not-replicate | |
| BR-02-22 | AML/PEP-adjacent score (PEP/profile/product/geo/channel → low/med/high) dihitung & dipersist per aplikasi. | `21-...md BR-CRSCORE-16` | [VERIFIED][OPEN] | Reader tak ditemukan → OQ-CRSCORE-04 (jangan tandai dead tanpa konfirmasi) |
| BR-02-23 | Aturan "jumlah reference person scaling dengan worst kolektibilitas" (2 utk tier 1-3, 3 utk tier 4-5) hanya komentar SQL, tak ter-enforce. JANGAN diam-diam di-encode sebagai enforced. | `21-...md BR-CRSCORE-4, §9 EC-5` | [VERIFIED][OPEN] | Enforcement [OPEN] OQ-CRSCORE-05 |
| BR-02-24 | Dukcapil match dikonsumsi **read-only, tanpa coded gate** di kapabilitas ini (informational untuk judgment analis). | `dukcapil.md §2`; BR-VERIF-8 | [VERIFIED][INTENT] | Gating (jika ada) prosedural — OQ-VERIF-01 |
| BR-02-25 | Service ini **TIDAK mint PO**; PO di-mint 04 (STEP 13, single deterministic per D-01 Step 13) pada MemoApproved. JANGAN replika trigger PO dari modul credit-analyst legacy (BE `CreditAnalystRepositoryEF.cs:692-708` maupun render PO sinkron dari layar approval FE `CreditAnalystController.cs:1166-1266`). | GOTCHA-8; boundary_ownership "PO minting"; D-01 Step 13; `62-...screens.md §5.11` | [VERIFIED][INTENT] / do-not-replicate | |
| BR-02-26 | Konsolidasi ke **satu** model approval-progress kanonik (legacy punya 2 set bookkeeping paralel; write-target 5C-note ada 3 jalur). Jangan replika duplikasi. | `21-...md §9 EC-6,EC-7, BR-CRSCORE-13` | [VERIFIED][OPEN] | Source-of-truth [OPEN] OQ-CRSCORE-03, OQ-CRSCORE-01, OQ-OVERVIEW-01 |
| BR-02-27 | Instant-Approval (IA) trial-cohort override (paksa low-risk trans-type via string-position editing identifier) diperlakukan sebagai policy flag eksplisit auditable — JANGAN replika string-hack. IA lane utk aplikasi mobile-origin = jalur policy auditable resmi per D-01 Step 11; eligibility per product/plafond [OPEN] OQ-MEET-04. | GOTCHA-9; `21-...md §11 (sp_get_trans_type_id_cm trial-cohort)`; D-01 Step 11 | [OPEN] | Permanen vs pilot [OPEN] OQ-PRODASSET-06 |
| BR-02-28 | **DSR formula tunggal kanonik di BE**: legacy menghitung DSR di **4 tempat** dengan formula tidak konsisten — 3 komputasi server-side (installment ÷ (primary + other income)) dan 1 client-side yang justru dirender ke analis (installment ÷ (primary + other + **spouse** income bila menikah)). Rebuild: SATU komputasi BE, hasil di-serve sebagai field API; inklusi spouse-income = **[OPEN] OQ-CAUI-07 — JANGAN pilih diam-diam**. Prasyarat data: primary income non-zero, additional income non-null, MRP non-zero sebelum Save (enforce server-side). | `62-...screens.md BR-CAUI-1, BR-CAUI-2, §9 EC-2` | [VERIFIED][OPEN] | Menyempurnakan BR-02-13; guard div-zero per BR-02-10 |
| BR-02-29 | **Checklist dokumen**: row set keyed `customer_type` **P/C** dengan set dokumen KYC legally-distinct; tiap item punya **dua kanal verifikasi independen** (civil-registry/Dukcapil vs mobile-app "Playstore" lookup) dengan verdict availability+result terpisah — granularity per-kanal **WAJIB dipertahankan** (KYC/AML control record). Vocabulary verdict teramati FE (`Terlampir/Belum Ada/Tidak Ada`; `Valid/Tidak Valid/Tidak Ada`; selfie survey) = kandidat enum — konfirmasi OQ-CRSCORE-08. Gap penomoran item 3–8 pada branch individual [OPEN] OQ-CAUI-05 (retired vs KYC hilang — jangan ditebak). | `62-...screens.md BR-CAUI-8, §5.5, §9 EC-3`; `21-...md §5.12` | [VERIFIED][LOCKED] (granularity) / [OPEN] (vocab & item set) | |
| BR-02-30 | **Grid transkripsi SLIK** (field set [LOCKED] §3) diinput manual analis dan legacy TIDAK memaksa rekonsiliasi dgn angka FCL viewer. Rebuild WAJIB memutuskan kebijakan rekonsiliasi (validasi silang vs tetap manual) — [OPEN], jangan diasumsikan defect ATAU by-design. | `62-...screens.md §5.4` | [VERIFIED][OPEN] | |
| BR-02-31 | **APPI facility status**: verdict clear/blacklist + nominal per pihak dipersist per aplikasi — compliance control point, verdict **[LOCKED]**; ekspansi istilah "APPI" [OPEN]. | `62-...screens.md §4 (Index.cshtml:2489-2589)` | [VERIFIED][LOCKED] (verdict) | |
| BR-02-32 | **Ratio display kanonik dari BE**: DSR, LTV (= financed ÷ asset cost × 100), DP%, DP-net% dihitung server-side dan diekspos sebagai field API — legacy menghitung di presentation layer (3 controller) + client JS = [ARTIFACT]. FE (Next.js) HANYA menampilkan, tidak menghitung ulang. | `62-...screens.md §5.3, BR-CAUI-1` | [USULAN][INTENT] | Satu sumber angka; mencegah drift server/client |
| BR-02-33 | **Variasi per-produk (motor vs mobil)**: dua workflow CA legacy berevolusi independen (field set, validasi, vocabulary keputusan berbeda). Rebuild memodelkan variasi sebagai **parameterisasi per product matrix** (D-07), BUKAN dua codebase paralel; matriks step per produk MACF [OPEN] OQ-MEET-06 (P1, blocks per-product annex — tidak mem-block PRD umbrella ini). | `62-...screens.md §1, §3a`; D-07 | [VERIFIED][OPEN] | Terkait framing OQ-CRSCORE-10 |
| BR-02-34 | **Validasi upload dokumen CA** (jalur mobil legacy): tipe file `.jpg/.jpeg/.png/.pdf` + max size ter-konfigurasi — enforce server-side seragam lintas produk (legacy motor tidak punya gate setara = inkonsistensi). | `62-...screens.md BR-CAUI-9` | [VERIFIED][OPEN] | Cakupan produk motor [OPEN] |
| BR-02-35 | **Kelengkapan 5C**: field 5C pada jalur mobil legacy bisa disubmit kosong (validasi ter-komentar). JANGAN diam-diam enforce ATAU diam-diam biarkan — kewajiban isi 5C = [OPEN] (butuh keputusan bisnis; FE BR-CAUI-10). | `62-...screens.md BR-CAUI-10` | [VERIFIED][OPEN] | |
| BR-02-36 | **Tidak ada role super-user** pada endpoint modul ini (D-09 [LOCKED]); worklist CA di-scope **branch + analyst** (outcome [INTENT]); aturan approval berbasis label-text (motor legacy: substring "Approve"/"Review"/"Reject" pada teks option) = [ARTIFACT] JANGAN direplika — gunakan `reason_type` code (pola layar mobil BR-CAUI-5, [LOCKED] mapping; mekanik walk milik 03). | D-09; `62-...screens.md §4, BR-CAUI-4, BR-CAUI-5` | **[LOCKED]** (D-09) / [INTENT] (scoping) | |

---

## 7. State Machine

Kapabilitas ini menggerakkan **dua** state machine + transisi `CREDIT_APPLICATION.status` (milik 01, digerakkan 02).

### 7.1 `RAC_SCREENING.rac_status` (STEP 10) — kolom `trx_rac_screening.status` (§3.2)

Status: `not_submitted` → `pending` → `approved` | `rejected` (+ override transition).

| Dari | Aksi | Ke | Guard/Prasyarat |
|---|---|---|---|
| not_submitted | submit RAC (route CF/US) | pending | financing_model_code valid (allow-list); ACL reachable (else fail-closed, tetap not_submitted) |
| pending | ingest decision `APPROVED` | approved | idempotent by `(application_id, decision_id)`; → CA queue (PDF v2 STEP 10) |
| pending | ingest decision `REJECTED` | rejected | idempotent; → stop (mekanisme blocking [OPEN] OQ-CRSCORE-06) |
| pending | ingest duplicate `decision_id` | pending (no-op) | `applied:false` (already_processed) |
| rejected/corrected(final) | late `APPROVED` (decision_id baru, dalam window) | approved (override) | **HARUS eksplisit + ter-log + auditable** (BR-02-04); non-happy-path |
| approved/rejected | CM reopen-for-correction (Open CM STEP 13) | pending (re-screen) | event idempotent, bukan destructive delete (BR-02-05); return-target granularity [OPEN] OQ-GT-03 |

### 7.2 `CREDIT_ANALYSIS.status` (STEP 11) — kolom `trx_credit_analysis.status` (§3.1; konsolidasi 3 kolom status legacy)

Status: `queued` → `under_review` → `recommended` → (handoff ke 03 / STEP 12). Return-path menyentuh record ini; walk multi-level milik 03.

> **[OPEN] OQ-CRSCORE-10 (P1) — ordering & actor-of-record provisional**: Pemodelan sebagai **satu lifecycle linear** (`queued→under_review→recommended`) dengan **Credit Analyst sebagai aktor tunggal** (S2) BELUM dikonfirmasi. Alternatifnya, jalur capture preliminary-note CMO dan analyst workstation penuh bisa merupakan **kanal alternatif untuk lini produk berbeda** — bukti FE baru (dua layar CA independen: motor "CreditAnalyst" vs mobil "CACar") condong ke framing per-produk tapi belum konklusif; terkait D-07/OQ-MEET-06. Kedua dimensi (actor-of-record + time-ordering) menunggu OQ-CRSCORE-10 (§11); JANGAN diasumsikan salah satu framing. Distinct dari OQ-CRSCORE-01 (write-target authority).

| Dari | Aksi | Ke | Guard/Prasyarat |
|---|---|---|---|
| — | RAC approved (STEP 10) | queued | `rac_status='approved'`; application → `risk_gated` |
| queued | analis buka record (STEP 11 entry) | under_review | actor = Credit Analyst; application → `analyzing` |
| under_review | submit rekomendasi (STEP 11 RFA) | recommended | recommendation + justification + narrative + debtor_group + ojk_economic_sector terisi (server-side); emit `AnalysisComplete`; application → `committee` |
| recommended | **03 (STEP 12)**: non-base reviewer kembalikan (correction) | under_review | non-happy-path; return-to-analyst (mekanik walk milik 03; evidence kode legacy) |
| recommended | **03 (STEP 12)**: base-tier reviewer kembalikan | queued | non-happy-path; **re-trigger RAC** pada resubmission; application → `corrected` lalu `risk_gated` |
| recommended | **03 (STEP 12)**: terminal approve | (handoff) | milik 03; 02 memasok decision+risk-tier; **02 TIDAK mint PO** (BR-02-25) |
| recommended | **03 (STEP 12)**: terminal reject | (handoff) | milik 03 (Rejected permanent per PDF v2); application → `rejected` |

> **Delta doc-vs-code pada return-target Correction (flag utk 03)**: PDF v2 STEP 12 menyatakan Correction kembali ke **Step 1–7** (perbaikan CMO di MOOFI), sedangkan kode legacy punya **dua tier return** (non-base → analyst rework; base → queue + re-screen RAC). Discrepancy ini didokumentasikan (per mandat ground-truth §"where code disagrees") dan resolusinya milik 03/01; 02 wajib mendukung **kedua** target return sampai diputus. [OPEN — carry ke 03]
>
> Non-happy-path terkait: RAC `rejected` (STEP 10) → aplikasi berhenti sebelum credit analysis (blocking mechanism [OPEN] OQ-CRSCORE-06). RAC `pending` berkepanjangan → tetap first-class (bukan diasumsikan approved).

### 7.3 `SLIK_DIRECT_CHECK_REQUEST.status` (microflow terpisah) — kolom `trx_slik_request.status` (§3.7)

`submitted` → `forwarded` → `approved`; `submitted` → `corrected` → `submitted`; `submitted` → `rejected`.

| Dari | Aksi | Ke | Guard |
|---|---|---|---|
| submitted | reviewer bukan terminal | forwarded | routing dari department requester |
| forwarded | terminal approver approve | approved | |
| submitted | terminal approver approve | approved | acting = terminal |
| submitted | reviewer kirim balik | corrected | |
| corrected | preparer resubmit | submitted | |
| submitted | reviewer reject | rejected | terminal |

---

## 8. Integrasi Eksternal

> Semua seam **WAJIB lewat Anti-Corruption Layer (ACL)** — D-01 Step 7 eksplisit mewajibkan ACL utk RAC; tanpa cross-DB DML mentah ke DB counterpart (perbaikan atas linked-server pattern legacy; data-mutation-policy anti-patterns). Klasifikasi arah/mode konform umbrella `integration_seams`. Implementasi Java [USULAN]: adapter per seam sebagai komponen terpisah (interface + impl), idempotency key persisted, retry/backoff + circuit breaker (mis. Resilience4j) — pilihan library final ikut OQ-ARCH-STACK/D-11.

| Seam | Arah | Mode | Owner | Catatan ACL |
|---|---|---|---|---|
| **RAC Bank Mega (JFinMega)** — STEP 10 | outbound request + inbound callback | **async** (D-01 Step 8) | 02 | Submit balikkan envelope call saja; decision async via callback/poll ingester; idempotent by `application_id+decision_id`; **no write-back**; owned API contract (submit + cancel/resubmit), bukan `DELETE` ke DB eksternal. `rac-...md §2,§4` |
| **SLIK / OJK** | outbound (pull) | **sync** | 02 | Read grid kolektibilitas via ACL (bukan OPENQUERY string-concat, perbaikan injection SLIK EC-1). Microflow direct-check + RPA-staging batch idempotent. Tier CLKNAE* [OPEN] OQ-SLIK-01 |
| **Pefindo** | outbound (pull) | **sync** | 02 | Fallback bureau saat SLIK kosong; `varchar(16)` NIK [LOCKED]; satu fungsi kolektibilitas tunggal. Request-initiation path [OPEN] OQ-PEFINDO-01 |
| **NeoScore** | outbound | **async** | 02 (target) | **Legacy caller = FE tier (RESOLVED, FE KB EC-1)**: `NeoScoreServices.cs` POST langsung `http://macfto.mcf.co.id/NeoScoreAPI/...` (plain HTTP, PII di body, respons HTML di-inject `@Html.Raw`) — [ARTIFACT] JANGAN direplika. Target [USULAN]: **BE-02 memiliki outbound call** via ACL (TLS, authn, kontrak terstruktur); parsing terstruktur; NIK canonical raw konsisten (EC-1); NumberOfDependents populate konsisten (EC-2); idempotency ledger tetap di BE (§9 KB). Caching policy motor-vs-car [OPEN] (FE EC-7/OQ-CAUI-06) |
| **Dukcapil** | outbound | **async** | 02 / verification | Read-only civil-registry match; **02 tidak gate** atas Dukcapil (informational). Field set [LOCKED] (KYC/AML). `dukcapil.md §2` |

> **Rapindo** (registri kendaraan curian) tampil di layar CA legacy sebagai gate Approve final-level — seam & gate dimiliki domain kolateral/03 (`31-collateral-bpkb-fidusia.md`); dicantumkan di sini hanya sebagai cross-reference kebutuhan layar (OQ-CAUI-02/04).

---

## 9. Acceptance Criteria

**AC-1 (happy: RAC approve → CA queue)**
- **Given** aplikasi `rfa_locked` dengan `financing_model_code='CF'`,
- **When** `POST /applications/{id}/rac-screening` lalu `POST /rac-screening/callbacks` dengan `rac_status='APPROVED'`,
- **Then** `RAC_SCREENING` → `approved`, `CREDIT_APPLICATION.status` → `risk_gated`, `CREDIT_ANALYSIS` → `queued` (resolusi PDF v2 STEP 10: Approved → CA queue).

**AC-2 (RAC async — bukan sync; D-01 Step 8)**
- **Given** submit RAC,
- **When** response diterima,
- **Then** response adalah envelope call (`rac_status='pending'`), **bukan** decision accept/reject; decision hanya datang via callback/poll ingest.

**AC-3 (idempotent ingest)**
- **Given** callback `(application_id, decision_id)` sudah diproses,
- **When** callback identik dikirim ulang,
- **Then** `applied:false, reason:already_processed`, status tidak berubah, tanpa efek ganda.

**AC-4 (late-approval override auditable)**
- **Given** status lokal sudah `rejected`,
- **When** decision `APPROVED` terlambat (decision_id baru, dalam window),
- **Then** override HANYA berlaku bila tercatat sebagai entri audit eksplisit (bukan silent flip); jika kebijakan window belum diputus (OQ-RAC-03), sistem menahan & menandai untuk review.

**AC-5 (dispatch CF vs US)**
- **Given** `financing_model_code='US'`,
- **When** submit RAC,
- **Then** rute US (record set `_bms`) dipakai; `financing_model_code` dipertahankan char-for-char.

**AC-6 (kolektibilitas OJK exact)**
- **Given** days-overdue `95`,
- **When** grid kolektibilitas dihitung (SLIK atau Pefindo),
- **Then** tier = `3` (91-120) — mapping identik lintas source (satu fungsi tunggal).

**AC-7 (blacklist override)**
- **Given** applicant ber-blacklist flag,
- **When** scoring dihitung,
- **Then** `risk_tier='very_high'` terlepas parameter lain.

**AC-8 (grade→weight monotonic — bug fixed)**
- **Given** grade band memburuk (risiko naik),
- **When** weight di-resolve,
- **Then** weight strictly **menurun** dengan risiko — asersi unit test `weight strictly decreases with risk` (per GOTCHA-3); grade kedua-terburuk TIDAK sama dengan grade terbaik. Catatan arah: verifikasi terhadap `sp_get_customer_scoring:437-444` saat implementasi.

**AC-9 (guard pembagian nol)**
- **Given** cost aset = 0 ATAU total income = 0,
- **When** DP-ratio/principal-ratio/DSR/LTV/DP% dihitung (semua entry point),
- **Then** ditangani sebagai validasi (bukan runtime arithmetic error) — cakupan diperluas per FE EC-2 (legacy: 3 komputasi server tanpa guard).

**AC-10 (DSR advisory di STEP 11)**
- **Given** DSR 47% (>40%),
- **When** `POST /credit-analysis/{id}/recommendation`,
- **Then** advisory `DSR_EXCEEDED` dikembalikan inline; submission tetap lanjut (advisory) SELAMA OQ-CRSCORE-02 belum memutuskan hard-block; **tidak** meng-hard-block sebagai gate final (jangan konflasi dengan gate NPP STEP 15).

**AC-11 (freshness advisory ≠ NPP hard gate)**
- **Given** cek bureau > 30 hari,
- **When** submit rekomendasi STEP 11,
- **Then** advisory `BUREAU_STALE` (non-blocking di STEP 11); hard-gate 403+rollback tetap di STEP 15 milik 05; expiry Vertel 30-hari (D-01 Step 14) tidak tersentuh modul ini.

**AC-12 (emit AnalysisComplete + no PO minting)**
- **Given** rekomendasi tersubmit,
- **When** record → `recommended`,
- **Then** event `AnalysisComplete` di-emit, application → `committee`, risk-tier & decision RAC dipasok ke 03, dan **tidak ada PO yang di-mint** oleh kapabilitas ini (PO = STEP 13 milik 04, D-01 Step 13).

**AC-13 (trans_type_id komposisi tunggal)**
- **Given** risk_tier terhitung,
- **When** `GET /applications/{id}/risk-category`,
- **Then** komposisi penyusun `trans_type_id` disediakan (risk-tier-qualified), disusun hanya di 02, `trans_type_id` final char-for-char [LOCKED]; ladder `AA00000001` tidak dicampur; feed routing komite `trans_type_id`+OP+risk (D-01 Step 10).

**AC-14 (NeoScore ingest tanpa clobber)**
- **Given** hasil NeoScore untuk `credit_id` yang sudah punya log,
- **When** `POST /neoscore-results` (atau hasil dari proxy §5.7),
- **Then** hasil segar disimpan (bukan clobber stale placeholder), parsing terstruktur schema-validated (bukan tag-strip/label-offset).

**AC-15 (fail-closed regulated gate — target)**
- **Given** ACL RAC/SLIK gagal mid-check,
- **When** gate regulasi dievaluasi,
- **Then** default fail-closed (blokir), aplikasi tidak lolos diam-diam ([KEPUTUSAN DESAIN BARU]; final policy OQ-REG-06).

**AC-16 (DSR formula tunggal kanonik)**
- **Given** aplikasi dengan primary/additional/spouse income terisi,
- **When** `GET /applications/{id}/credit-analysis`,
- **Then** field `dsr` dihitung TEPAT SATU implementasi BE (tidak ada komputasi paralel FE); inklusi spouse-income mengikuti keputusan OQ-CAUI-07 (sebelum diputus: implementasi di belakang satu flag konfigurasi, JANGAN dua formula liar); Save ditolak bila primary income = 0 / additional income null / MRP = 0 (server-side).

**AC-17 (checklist dokumen per customer-type & kanal)**
- **Given** aplikasi `customer_type='C'` (corporate),
- **When** `PUT /credit-analysis/{id}/document-checklist`,
- **Then** row set yang divalidasi = set corporate (bukan individual); tiap item menerima verdict availability+result per kanal (civil-registry vs mobile-app) secara independen; nilai verdict tervalidasi terhadap vocabulary ter-konfigurasi (kandidat enum FE; final OQ-CRSCORE-08).

**AC-18 (NeoScore call BE-owned — bila USULAN diterima)**
- **Given** analis meminta skor via `POST /applications/{id}/neoscore/score`,
- **When** BE memanggil vendor,
- **Then** call keluar lewat ACL BE ber-TLS (bukan dari FE, bukan plain HTTP), PII tidak transit lewat browser, prior-result dicek dulu (idempotency ledger), respons terstruktur dipersist ke `trx_neoscore_result` (+ jejak panggilan di `log_neoscore_call`, §3.9) — tidak ada HTML mentah yang diteruskan ke FE.

**AC-19 (worklist scoping + no super-user)**
- **Given** Credit Analyst cabang A login,
- **When** `GET /credit-analysis/worklist`,
- **Then** hanya item cabang A yang ter-assign ke analis tsb yang tampil; tidak ada bypass role super-user (D-09).

**AC-20 (grid transkripsi SLIK auditable)**
- **Given** analis menambah lalu menghapus baris history SLIK,
- **When** `POST` / `DELETE /credit-analysis/{id}/slik-history-rows/*`,
- **Then** field set baris sesuai kontrak [LOCKED] §5.8; penghapusan meninggalkan audit trail (baris bureau-history = artefak relevan regulasi).

---

## 10. Dependency

### Upstream yang dikonsumsi
- **01-intake-cas** (event `ApplicationLocked`; status `rfa_locked`; STEP 9 RFA via `sp_approve_cm_moofi` moofi-path — dual path legacy OQ-GT-01 ✅ RESOLVED — evidence, §11): header CAS, applicant, product/asset, financing-model code, blacklist flag, branch/dealer, `customer_type` P/C, dokumen upload (viewer read). RFA lock milik 01; re-open memicu re-screen RAC. — **pull/event**
- **STEP 8 sync MOOFI→FINCORE** (milik 01/upstream): `credit_id` nasional-unik di-mint di sini (≙ `application_id`); format resolved OQ-GT-02 (`branch(5)+YY+MM+SEQ(5)`, BE-01 §3.1.13); validasi `sp_validation_mobile_to_fincore`. — **prasyarat data**
- **Master reference** `FC_MSTAPP_MCF` (trans-type/approval-hierarchy reference) untuk validasi char-for-char `trans_type_id` — **read (linked-server legacy → owned read via ACL)**; reachability [OPEN] OQ-EXTMASTERS-01.
- **CUSTOMER** (NIK/NPWP) untuk key bureau & Dukcapil — konsumsi read.

### Eksternal (seam §8)
- RAC Bank Mega (async, D-01 Step 8), SLIK (sync), Pefindo (sync), NeoScore (async; target call BE-owned [USULAN]), Dukcapil (async, read-only).
- **Arsitektur infra**: deliverable tim ITEC Bank Mega (D-11) — PRD ini menyatakan asumsi (ACL, async ingest, no cross-DB DML) dan menunda topologi final (deployment, messaging, scheduler) ke dokumen ITEC.

### Downstream yang dipicu
- **03-approval-committee (STEP 12)**: menerima event **`AnalysisComplete`** + decision RAC + risk-tier + komposisi `trans_type_id`; routing dinamis by `trans_type_id` + OP + risk level (D-01 Step 10; D-10 skala risiko); 03 MEMBACA decision RAC untuk routing, **tidak menulis balik**. **Plus** event **`RacDecisionReceived`** dari ingest §5.2 (payload minimum `credit_id`, `decision_id`, `decision`; via `out_event` ADR-04) — pelepas wait-state Flowable komite serapan `tr_temp_verify` (BE-03 §3.4 #8) — **RESOLVED by convention** (BE-03 §11 OQ-BE03-04 → opsi a). — **event + pull(read)**
- **04-contract-cm-po (STEP 13)**: mengonsumsi komposisi `trans_type_id` saat finalize CM; PO minting milik 04 (single deterministic, D-01 Step 13; bukan dipicu 02). — **downstream (bukan event dari 02)**
- **05-npp / verification (STEP 14 Vertel + STEP 15 NPP)**: freshness bureau advisory STEP 11 hanya informasi; hard-gate verifikasi + expiry milik 05 (distinct; tiga jendela 30-hari per BR-02-14).
- **FE Next.js (D-12)**: konsumen seluruh endpoint §4; kontrak ratio/enum/vocabulary dikanonikkan di BE (BR-02-32) — FE tidak menghitung ulang dan tidak memanggil vendor eksternal langsung.

---

## 11. Keputusan Dibutuhkan (Open Questions)

> Status: baris **RESOLVED** dipertahankan untuk traceability. OQ meeting (OQ-MEET-xx) & ground-truth (OQ-GT-xx) berasal dari decision register / ground-truth v2.

| OQ-ID | Pertanyaan | Dampak | Status |
|---|---|---|---|
| **OQ-REG-06** | Saat core screening throw mid-check, app-layer fail-closed (blokir) atau fail-open (lolos)? Kebijakan global untuk SEMUA regulated gate. **Highest-impact.** | 01, 02; semua regulated gate (AML/SLIK/DSR/verification) | OPEN [P1] |
| **OQ-CRSCORE-02** | DSR 40% & freshness 30-hari (STEP 11) di-enforce hard-block oleh calling-layer atau advisory? Belum boleh diputus diam-diam. | 02; fail-open/closed | OPEN |
| **OQ-CAUI-07** | Formula DSR authoritative: include spouse-income (formula client legacy yang DITAMPILKAN ke analis) atau exclude (3 formula server legacy)? Butuh domain-expert call; bila ada sitasi regulasi → naik [LOCKED]. | 02; BR-02-28, field API `dsr` | OPEN [P2] — baru (FE KB) |
| **OQ-CRSCORE-06** | Apakah RAC `REJECTED` benar-benar memblokir progres ke credit analysis, di mana & bagaimana? (Propagasi ditemukan; logic decision tetap eksternal. PDF v2: "Rejected → stop".) | 02; ACL RAC | OPEN |
| **OQ-CRSCORE-07** | Arti institusional kode financing-model non-konvensional (US/SY; dua nilai dipakai bergantian). | 02 | OPEN |
| **OQ-CRSCORE-01 / OQ-OVERVIEW-01** | Dari 3 write-target 5C-note paralel, mana authoritative? CA vs CREDITSANALYST source-of-truth. (PDF v2 STEP 11 mengutip `sp_insert_analisa_cmo_ca` → TrCa sebagai jalur valid — belum menutup pertanyaan authority.) | 02 | OPEN [P1] |
| **OQ-CRSCORE-03** | Relasi dua set bookkeeping approval-progress paralel (mana live/legacy/scoped). | 02; konsolidasi model | OPEN |
| **OQ-CRSCORE-04** | Apakah AML/PEP score benar-benar dibaca dari tier manapun (web/mobile/job)? | 02 | OPEN |
| **OQ-CRSCORE-05** | Aturan "reference count scaling dengan kolektibilitas" di-enforce di luar slice ini? | 02 | OPEN |
| **OQ-CRSCORE-08** | Vocabulary valid field "result" checklist dokumen. **Partially answered oleh FE KB**: UI memakai `Terlampir/Belum Ada/Tidak Ada` + `Valid/Tidak Valid/Tidak Ada` (+ selfie survey) — konfirmasi sebagai controlled vocabulary final. | 02 | OPEN (menyempit) |
| **OQ-CRSCORE-10** | Actor-of-record & time-ordering peran "credit analyst": jalur preliminary-note CMO vs analyst workstation penuh — **langkah sekuensial satu lifecycle** ATAU **kanal alternatif untuk lini produk berbeda**? Bukti FE baru (motor vs mobil, dua layar independen) condong per-produk, belum konklusif. JANGAN diasumsikan. | 02; framing aktor §2 + state machine §7.2 (provisional); terkait OQ-MEET-06 | OPEN **[P1]** |
| **OQ-CRSCORE-11** | Disambiguasi 3 kolom status overlap `tr_CA` (`CA_decision` vs `approval` vs `CA_status`): makna & vocabulary nilai masing-masing (per DDL/SP belum terbedakan). Menentukan matriks mapping → `trx_credit_analysis.status` + `recommendation` (§3.1); nilai tak ter-map saat migrasi = reject + register (DATA-MIGRATION-PLAN §3). Termasuk mapping kode mobil `A/V/C/R`. | 02; §3.1, migrasi `tr_CA` | **RESOLVED — evidence** (arkeologi kode). (1) **`CA_status`** ("status CA") = state machine workflow CA mobil: `D`=Draft (create — `CreditAnalystRepositoryEF.cs:1449`; `sp_approve_cm_car`:119), `0`=RFA (EF:1410), `V`=Verify/Verified (approve intermediate — EF:766,841), `C`=Correction/Reviewed (EF:583; `sp_approve_cm_car`:124; `sp_trans_open_cm`:18), `''`=Reviewed CM (koreksi Level 0 — EF:618; `CreditAnalystController.cs:879`), `E`=Eskalasi (reject non-Level-0 — EF:927), `A`=Approved final + generate PO (EF:710), `R`=Rejected di Level 0 (EF:1018); decode label CASE di `sp_get_pagination_ca` (`FC_ACQ_MCF 2.sql:43056-43063`) & `sp_get_pagination_acquisition_mobil` (:39681-39686). (2) **`CA_decision`** ("keputusan CA", ext-prop :101572) = verdict analis, vocabulary HANYA `A`=Approve / `R`=Reject (dropdown "Keputusan CA" `Index.cshtml:2839-2843`; reader hanya print `sp_print_CA_C` :71841 / `sp_print_CA_P` :74535) → map `A`→`recommended`, `R`→`not_recommended`. (3) **`approval`** ("jumlah approval", ext-prop :101576) = DEAD: 0 reader/writer di seluruh dump SP (hanya DDL :7787); .NET pass-through tanpa binding UI (EF:1406,1448) → DISCARD, verifikasi NULL saat migrasi. Anggapan awal "kode mobil A/V/C/R milik `CA_decision`" TERBANTAH — set itu milik `CA_status`. §3.1 sudah diperbarui. |
| **OQ-GAP-03** | Bypass SLIK (`tr_slik_bypass_acquisition` → `log_slik_bypass` §3.8): kondisi bisnis apa yang memilih cabang bypass di `sp_validation_mobile_to_fincore`, SIAPA berwenang mengaktifkan, dan apakah OJK/compliance mensyaratkan approval + audit lebih kaya daripada satu baris `'by pass slik'` tanpa `credit_id`/aktor? Sampai diputus: jalur bypass TIDAK diaktifkan di rebuild (fail-closed BR-02-15). | 02; §3.8; regulatori SLIK | OPEN **[P1]** — dari gap-entities §2.3 |
| **OQ-GAP-06** | `cek_apuppt_log` (compliance-adjacent AML): service/job mana yang benar-benar read/write? Masih diperlukan? → **Porsi `cek_apuppt_log` ✅ RESOLVED — evidence (2026-07-14, gap-entities §4)**: writer = `TrCasRepositoryEF.CekApupptLogAsync` via `POST validasi/cekapupptlog`; semantik = idempotency-marker cek APU-PPT per aplikasi (§3.13). Porsi `CASMobile_mappingfincore` (milik 01) tetap OPEN-menyempit. | 02; §3.13 | ✅ porsi 02 RESOLVED — dari gap-entities |
| **OQ-CORE-08 / OQ-DATA-03** | Pasangan byte-identik `tr_CA_approval_progress`/`_transaction`: mana live vs history (menentukan sumber MIGRATE-READONLY ke `log_approval_history` §3.12)? Plus scope kunci `CF_No` (CA-only vs shared lintas jenis transaksi). Satu tema dengan OQ-CRSCORE-03 (BR-02-26). | 02, 03; §3.12, migrasi approval | OPEN [P2] — dari core-entities §11 |
| **OQ-CAUI-02** | Identitas role "CA approver" (flag `IsApprover`/`IsLastApprover` layar mobil; gate Rapindo layar motor) vs reviewer hierarki generik — role distinct atau sama? | 02, 03; RBAC target | OPEN [P2] — baru (FE KB) |
| **OQ-CAUI-04** | Scoping gate Rapindo legacy (`IsItemNew==0 && ApplicationTypeId=="03"`) — makna kode "03" & apakah gate berlaku lebih luas di rebuild? | 03 / kolateral; cross-ref dari 02 | OPEN [P2] — baru (FE KB) |
| **OQ-CAUI-05** | Gap penomoran checklist dokumen item 3–8 (branch individual): retired sengaja vs cek KYC hilang? Menentukan set dokumen wajib individual final. | 02; BR-02-29 | OPEN [P2] — baru (FE KB) |
| **OQ-CAUI-06** | Varian call NeoScore authoritative: tombol "View Score" layar motor ter-wire ke action controller mobil (varian R4) — unifikasi sengaja vs copy-paste bug; + kebijakan caching motor (tanpa cache) vs mobil (cek log dulu). | 02; ACL NeoScore, idempotency | OPEN [P3] — baru (FE KB) |
| **OQ-CAUI-01** | Mapping nama layar assignment ("CheckingSlikDashboard", "TrCaDocuments") → artefak nyata: PDF v2 STEP 11 mengutip `FINCORE.WEB/Controllers/Slik/CheckingSlikDashboardController.cs`, tapi grep FE = absent; FE KB memetakan ke worklist CA + grid "History BI Check/OJK" + checklist "Validasi Dokumen Mandatory" + `LookupViewDokumen`. Konfirmasi mapping sebelum penamaan layar/API final. | 02 (naming API/laporan), FE-02 | OPEN [P2] — baru (FE KB, discrepancy vs PDF v2) |
| **OQ-RAC-01** | Di mana `sp_insert_rac_processing*` (incl. `_syariah`) & `sp_Cek_rac_history` benar-benar eksekusi (Bank Mega / intermediary)? | 02; ACL RAC; kontrak submit | OPEN [P1] |
| **OQ-RAC-02** | SQL Agent job/scheduler apa yang memanggil poll `sp_agent_rac_to_cm_bulk` & frekuensinya? (Menentukan SLA poller target.) | 02; ketahanan async | OPEN [P1] |
| **OQ-RAC-03** | Late-approval override 120-menit = policy sengaja atau race-mitigation? | 02; idempotency | OPEN |
| **OQ-RAC-05** | Set literal penuh `rac_get_status`/`STATUS_DESC` (adakah nilai pending distinct)? | 02 | OPEN |
| **OQ-RAC-06** | Vocabulary penuh output `message` `sp_Cek_rac_history` (hanya `'0'` diperiksa). | 02 | OPEN |
| **OQ-RAC-07** | Field request-level apa yang dibutuhkan risk engine di luar `credit_id`/`CreatedBy`? | 02; kontrak payload §5.1 | OPEN |
| **OQ-NEOSCORE-01 / OQ-CRSCORE-09** | Tier mana yang melakukan outbound call NeoScore? **RESOLVED (legacy)**: FE tier — `FINCORE.Services/NeoScoreServices.cs` POST langsung ke vendor (plain HTTP + PII; render `@Html.Raw`), per `62-credit-analysis-screens.md §9 EC-1`. **Residual [OPEN] (target)**: ratifikasi USULAN "call pindah ke BE-02 via ACL TLS" + kontrak respons terstruktur dgn vendor + transport/authn. | 02; ACL NeoScore; endpoint §4 ⊕ | RESOLVED (legacy) / OPEN (target) |
| **OQ-NEOSCORE-03** | `DUMP_MACF.tbl_Submit.score_rimo` write-back legacy masih load-bearing? (Menentukan perlunya outbox step §5.5.) | 02 | OPEN |
| **OQ-SLIK-05** | Freshness SLIK 30-hari di-enforce hard-block sebelum NPP atau informational? (satu tema dengan OQ-CRSCORE-02) | 02, 05 | OPEN [P1] |
| **OQ-SLIK-01** | Apa itu family tabel `CLKNAE*` (Tier-3 fallback) & relasinya ke SLIK/Pefindo? | 02; ACL SLIK | OPEN |
| **OQ-SLIK-07 / OQ-PEFINDO-01** | Orkestrasi fan-out multi-bureau (`IsDukcapil`/`IsPefindo`) & mekanisme initiate request Pefindo. | 02; ACL bureau | OPEN |
| **OQ-PRODASSET-06** | IA trial-cohort override (force low-risk trans-type via string-position editing) = policy permanen atau pilot hack stale? | 02, 03; IA policy flag | OPEN |
| **OQ-MEET-04** | IA lane (D-01 Step 11) — eligibility rules per product/plafond. | 02 (fast-track/IA feed), 03 | OPEN [P2] — meeting |
| **OQ-MEET-06** | Matriks step per produk MACF (D-07) — product list + step applicability/variance; menentukan parameterisasi motor-vs-mobil BR-02-33 & framing OQ-CRSCORE-10. **Blocks per-product annex, bukan PRD umbrella ini.** | 02 (varian CA per produk) | OPEN [P1] — meeting |
| **OQ-GT-01** | Dual approve paths `sp_approve_cm` vs `sp_approve_cm_moofi` — channel mana pakai SP mana di rebuild scope? (Menyentuh 02: trigger re-screen RAC legacy embedded di rutinitas RFA-resubmission.) → **RESOLVED — evidence (2026-07-14; detail BE-00 §11 / BE-03 §11)**: pemisah = **trigger** (manual web vs agent otomatis), KEDUA SP LIVE; moofi-path memuat RAC re-hit delete (`FC_ACQ_MCF 2.sql:12321-12368`) — konfirmasi trigger re-screen RAC embedded. Keputusan port satu/dua jalur = keputusan desain terpisah. | 01, 03, 09; trigger RAC 02 | ~~OPEN [P1]~~ **RESOLVED — evidence** — ground-truth v2 |
| **OQ-GT-02** | Aturan minting nomor kontrak STEP 8 ("unik secara nasional" → `credit_id`) — format/sequence source. → **RESOLVED — evidence (2026-07-14)**: 14-char `branch_id(5)+YY(2)+MM(2)+SEQ(5 zero-pad)`, counter reset bulanan per cabang (spec BE-01 §3.1.13; BE-00 §11). | upstream; key `application_id` modul ini | ~~OPEN [P2]~~ **RESOLVED — evidence** — ground-truth v2 |
| **OQ-GT-03** | Open CM (STEP 13 koreksi) return-target "Step 1–12" granularity — full re-entry vs field-scoped; menentukan cakupan re-screen RAC BR-02-05. | 02, 04 | OPEN [P2] — ground-truth v2 |
| **OQ-CORE-03 / OQ-CMPO-02** | Arti bisnis OP/ULI/LCR (dan Ost*) — GL-reconciled & butuh [LOCKED]? (menyentuh komposisi risk/routing; OP = input routing komite D-01 Step 10) | 02, 03, 04 | OPEN |
| **OQ-MCP-01** | Apakah API/session layer meng-enforce "hanya assigned employee boleh act" untuk endpoint credit-analyst (SQL layer legacy tidak; **tanpa super-user per D-09**)? | 02, 03; NFR audit | OPEN |
| **OQ-EXTMASTERS-01** | Apakah master `FC_MSTAPP_MCF` dimiliki rebuild atau read-only; linked server MACF-* masih live/reachable? | references; validasi `trans_type_id` | OPEN |
| **OQ-ARCH-STACK** | Bahasa BE **RESOLVED = Java (D-12 [LOCKED])**. Residual [OPEN]: framework (USULAN Spring Boot), transport (REST/gRPC/message-bus), scheduler/messaging — menunggu arsitektur ITEC (D-11, deadline 10 Jul 2026). | semua kapabilitas; konvensi kontrak API §4 | PARTIAL — D-12 resolved bahasa |
