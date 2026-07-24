# PRD — Intake & CAS (Credit Application System) [BE]

> **Audience**: Tim Backend (BE).
> **Target stack**: **Java** `[LOCKED]` (D-12, SoW user directive 2026-07-14). Framework belum ditetapkan — **rekomendasi: Spring Boot** (USULAN, `[OPEN]` menunggu keputusan arsitektur ITEC Bank Mega per D-11, deadline arsitektur 10 Juli 2026). Transport (REST vs gRPC vs message-bus) `[OPEN]` — kontrak di dokumen ini ditulis level resource+field, path/verb ilustratif.
> **Tanggal**: 2026-07-14.
> **Sumber otoritatif**: `.mega-sdd/knowledge-base/.sp-manifests/_ACQUISITION-GROUND-TRUTH.md` (v2, alur final 16 STEP PDF 08072026), `.mega-sdd/knowledge-base/.sp-manifests/_MEETING-DECISIONS-2026-07.md` (D-01..D-12 + OQ-MEET), KB `10-domains/20-acquisition-cas-intake.md`, `10-domains/10-customer-applicant-master.md`, `10-domains/11-dealer-partner-master.md`, `10-domains/12-product-asset-master.md`, `20-workflows/credit-origination-lifecycle.md`, KB FE `60-frontend/61-intake-cas-screens.md`.
> **Status**: Revisi post-meeting; **menggantikan** baseline `docs/prd/acquisition/01-intake-cas.md` (pre-meeting). Renumbering FASE (v1) → STEP (v2) diterapkan penuh.

Kapabilitas ini adalah **front of funnel** origination pembiayaan kendaraan: menangkap identitas applicant (+ related-person), memilih product/asset dan menyusun struktur finansial draft, mengunggah dokumen, menjalankan screening AML/blacklist entry-time, **menerima sinkronisasi data MOOFI → FINCORE (STEP 8, GT v2)** termasuk **minting `credit_id`** dan pembentukan draft kontrak **Status RFA = '0'**, dan berakhir pada aksi terminal **RFA lock idempotent** yang mengunci draft ke **status `rfa_locked` (legacy `status_approval='0'`)** lalu mengemit event **`ApplicationLocked`** (D-01 Step 6). Cakupan pada alur final 16 STEP: **kapabilitas intake target-state D-01 Step 1–6** (capture applicant + NIK dedup lock, related-person typed roles, asset & financial draft, document upload, entry-time screening, RFA lock) **+ STEP 8 sinkronisasi MOOFI→FINCORE + disposisi Pengecekan Cabang pada STEP 9 (Verify-lock / Correction / Reject)**. **Kepemilikan RFA berada di kapabilitas ini** — legacy anchor gate battery: `sp_rfa_cm` / `sp_rfa_cm_car`; PDF 08072026 STEP 9 mengutip `sp_approve_cm_moofi` sebagai SP lock jalur moofi → dual path ini **RESOLVED — evidence** (OQ-GT-01, 2026-07-14: pemisah = trigger manual-web vs agent otomatis; kedua SP live — §11). Kapabilitas ini menyuplai sinyal risk-escalation, tetapi **tidak** memiliki komposisi `trans_type_id`, keputusan RAC/komite, PO minting, aktivasi kontrak, maupun seed/routing hierarki komite (yang di-key `trans_type_id`).

Grounding: `10-domains/20-acquisition-cas-intake.md`, `.sp-manifests/_ACQUISITION-GROUND-TRUTH.md` (STEP 1–9), `.sp-manifests/_MEETING-DECISIONS-2026-07.md` (D-01, D-09, D-10, D-11, D-12), `40-business-rules/regulatory-rules.md` (AML/blacklist), `40-business-rules/hidden-gotchas.md` (do-not-replicate), `99-rebuild-architecture/data-mutation-policy.md` (marker [LOCKED]), `99-rebuild-architecture/suggested-erd.md` (entity target shape), `60-frontend/61-intake-cas-screens.md` (cross-check kebutuhan API dari layar). Khusus §3 Model Data (GROUND TRUTH schema): `docs/DB-CONVENTIONS.md` (konvensi WAJIB), `docs/DATA-MIGRATION-PLAN.md` (kelas disposisi), KB `30-data-model/core-entities.md` + `30-data-model/gap-entities.md` (census legacy per kolom, DDL `FC_ACQ_MCF 2.sql`). Konform ke SHARED CONTRACT DIGEST umbrella (`docs/prd/acquisition/00-OVERVIEW.md`).

---

## 1. Ruang Lingkup & Kepemilikan

### 1.1 Yang dimiliki (in-scope — D-01 Step 1–6 + GT STEP 8 + STEP 9 lock)

- **CAS application header + child records** — `CREDIT_APPLICATION` (owner) beserta financial profile, corporate profile/owners, bank account, repeat-order detail, dan jawaban kuesioner AML. Sumber: `20-acquisition-cas-intake.md §1,§6`.
- **Capture applicant + NIK deduplication lock** — dedup-by-NIK **wajib pada first capture** (D-01 Step 1, `[INTENT]` decision register). Semantik lock detail `[OPEN]` OQ-PRD01-01 (lihat §3.3, §11).
- **Related-person typed roles** — `RELATED_PERSON` (owner) sebagai typed rows (`spouse|guarantor|reference`) dengan **strict role structure & validation** (D-01 Step 2). Sumber: umbrella shared-entity `RELATED_PERSON`; taxonomy identitas & relasi tetap milik `10-customer-applicant-master.md` (cross-link, tidak di-re-derive).
- **Product/asset & struktur finansial draft** — capture `ASSET` (chassis_no/engine_no) + seed `CREDIT_MEMO` draft (OTR, DP, tenor, fee, rate). Sumber: `20-...§3a S2`, BR-ACQCAS-4; D-01 Step 3.
- **Upload dokumen** — set dokumen/foto keyed by application + photo-type; vocabulary photo-type `[LOCKED]` (BR-CASFE-17). Sumber: `20-...§3a S3`; D-01 Step 4.
- **Screening AML/blacklist entry-time (otomatis pada first data entry, D-01 Step 5)** — cek identitas terhadap register blacklist internal + watchlist nasional (APU-PPT/DTTOT) + red-zone geografi + high-risk-profession; hitung AML risk score Low/Medium/High; audit-log setiap hit. Sumber: `regulatory-rules.md §7 BR-REG-1..13`, `20-...§7 BR-ACQCAS-5..9`.
- **STEP 8 — Sinkronisasi MOOFI → FINCORE (BARU vs baseline; formal di GT v2)** — (a) **Pembentukan Identitas & Penomoran Otomatis**: nomor kontrak unik secara nasional di-mint dan menjadi **`credit_id` (Primary Key)** — format/sequence **RESOLVED — evidence** (OQ-GT-02; 14-char `branch_id(5)+YY(2)+MM(2)+SEQ(5)`, spec §3.1.13); (b) **Pembentukan Draft Kontrak**: data pribadi applicant + guarantor, kapasitas bayar, struktur pinjaman, file foto dipindahkan Mobile→Fincore; skeleton draft kontrak auto-created **Status RFA = '0'**; (c) **Validasi**: kontrak SP legacy `sp_validation_mobile_to_fincore` menjadi acuan aturan validasi ingestion. Sumber: GT v2 STEP 8 `[VERIFIED — doc]`.
- **RFA lock idempotent (KEPEMILIKAN DI SINI)** — gate underwriting penuh atomik + status write `rfa_locked` (legacy `tr_cm.status_approval='0'`) + **emit `ApplicationLocked`** (D-01 Step 6 — "idempotent RFA lock emitting an ApplicationLocked event"; payload membawa risk-escalation signals + `product_line` + `finance_scheme`). Seed/routing hierarki komite **BUKAN** di sini: di-key `trans_type_id` yang baru disusun 02 (RAC callback ASYNC, D-01 Step 8), dibangun downstream (di 02/03). Sumber: GT v2 STEP 9 delta ("FASE 8 RFA `sp_rfa_cm` → STEP 9 RFA `sp_approve_cm_moofi`"); `20-...§5.9,§7 BR-ACQCAS-10..21`.
- **Disposisi Pengecekan Cabang (GT STEP 9)** — Admin Cabang (role **Credit (Admin)** per D-10) memeriksa kelengkapan dokumen pendukung via menu Inbox Approval; **Verify** mengunci data; **Correction** mengembalikan file ke Step 1–7 (perbaikan CMO); **Reject** menghentikan proses. Sumber: GT v2 STEP 9 `[VERIFIED — doc]`. Boundary: aksi disposisi komite (STEP 12, Approve/Reject/Correction level komite) milik 03.

### 1.2 Yang BUKAN miliknya (non-goal)

- **Komposisi `trans_type_id`** — disusun di SATU tempat, milik **02-credit-analysis** (dipakai 03); risk-category dari RAC callback ASYNC meng-compose `trans_type_id` (D-01 Step 8). RFA hanya **menyuplai sinyal risk-escalation** (eff-rate scale-up BR-ACQCAS-19, aggregate-exposure OP BR-ACQCAS-20) yang dibawa sebagai **konteks** pada payload `ApplicationLocked` (di-re-qualify otoritatif di 03). `[KEPUTUSAN DESAIN BARU]` — departure dari legacy yang meng-eskalasi digit di `sp_rfa_cm`. Sumber: umbrella §7.1; `20-...§7 BR-ACQCAS-20`.
- **RAC request/decision / biro scoring** — RAC request via **ACL** dan routing CF-vs-syariah (D-01 Step 7, GT STEP 10) milik **02** (processing jalan di sisi Bank Mega — seam eksternal, GT v2 STEP 10 CRITICAL SEAM). RFA hanya menyerahkan hand-off event.
- **Routing komite dinamis (`trans_type_id` + Plafond OP + risk level, D-01 Step 10), self-approval block & Instant-Approval lane (D-01 Step 11), PO minting deterministik (D-01 Step 13), freeze OP/ULI/LCR + insurance binding (D-01 Step 12 / GT STEP 12), Vertel (D-02 / GT STEP 14), NPP activation atomik + upsert customer master + downstream PULL (D-01 Step 15 / GT STEP 15)** — milik 02/03/04/05.
- **5C credit-analysis narrative (CMO)** — hand-off pra-RFA ke **02**, BUKAN endpoint milik 01; RFA tidak menjadikannya hard-gate. Sumber: `20-...§2,§3a S4,§9`.
- **Menutup aplikasi saat reject** — legacy menutup `TrCas`/`TrCm` saat reject; rebuild TIDAK auto-close (OQ-AC-01). State `rejected`/`corrected` bisa ditulis oleh aksi 03 (level komite) maupun aksi STEP 9 milik 01 (level cabang), keduanya mendarat pada entity milik 01 (lihat §7).
- **Masters customer/dealer/product/user** — dikonsumsi read-only via ACL; bukan ditulis di sini. CRUD Menu Master (User, Dealer, dst.) masuk SoW rebuild (D-08) tetapi dimiliki modul master-data, bukan 01. Penulisan otoritatif `CUSTOMER` (`tr_CIF` upsert) tetap milik **05-npp** (D-01 Step 15; lihat §3.3).
- **Origination mobile MOOFI Step 1–7 itu sendiri** — MOOFI = upstream context terpisah { FCL, ACQ, Credit Scoring, RFA } (flow.png, GT v2 boundary map). 01 memiliki **sisi penerima** (STEP 8 ingestion), bukan aplikasi mobile-nya.

### 1.3 Catatan arsitektur (D-11, D-12)

Arsitektur infra final disiapkan tim **ITEC Bank Mega** (D-11) — PRD ini menyatakan asumsi (service boundary per kapabilitas, event-driven hand-off, ACL di semua seam eksternal) dan menunda topologi final ke deliverable ITEC. Bahasa BE = **Java** `[LOCKED]` (D-12); pola implementasi yang disebut di dokumen ini (mis. `@Transactional` semantics, idempotency store) ditulis framework-agnostic — Spring Boot hanyalah rekomendasi (USULAN).

---

## 2. Aktor & Peran

Sensus role cabang **[LOCKED]** per D-10: **CMO, Marketing Head, Credit Analyst, Kepala Cabang, Credit (Admin)** — hierarki approval tergantung **skala risiko**. **Super user DIHAPUS** dari rebuild `[LOCKED]` (D-09) — tidak boleh ada role super-user di authz modul ini.

| Aktor | Peran di 01 | Marker |
|---|---|---|
| **Applicant / Customer** (individual `perorangan` / corporate `badan usaha`) | Subjek identitas, financial profile, ownership structure; subjek semua screening. | `[VERIFIED][INTENT]` |
| **Spouse / Guarantor / Personal References** | Related-person, disimpan sebagai typed rows child `CREDIT_APPLICATION` (D-01 S2 strict role). Taxonomy dimiliki `10-customer-applicant-master.md`. | `[VERIFIED][INTENT]` |
| **CMO / Marketing Head** (Maker, web channel — pemetaan D-10 dari legacy "Branch Data-Entry / Marketing Staff") | Key aplikasi, pilih asset/dealer/terms, upload dokumen. Legacy tidak punya role-attribute eksplisit (`[INFERRED]` KB §2); rebuild WAJIB memetakan ke sensus D-10. | `[INFERRED][INTENT]` + D-10 `[LOCKED]` |
| **Mobile Field Surveyor (MOOFI upstream)** | Capture survey FCL + foto di MOOFI (Step 1–7); masuk FINCORE via STEP 8 sync — kini **first-class** di GT v2, bukan lagi channel "possibly dead". Caller legacy sync SP tetap `[OPEN]` (OQ-ACQCAS-06, kini soal pemetaan legacy, bukan kelayakan channel). | `[VERIFIED — doc][INTENT]` |
| **CMO (Credit Marketing Officer)** | Entry 5C narrative (car channel) pra-RFA — hand-off ke 02, bukan aksi milik 01. Target Correction STEP 9 ("back to Step 1–7 for CMO fix"). | `[VERIFIED][INTENT]` |
| **Dealer / Dealer Personnel** | Sumber order (channel TAC / Third-Party) untuk atribusi insentif/refund. | `[VERIFIED][INTENT]` |
| **Credit (Admin)** — legacy "Branch Admin" | **Menekan RFA / Verify-lock** (aksi terminal domain) + disposisi STEP 9 Correction/Reject. | `[VERIFIED][LOCKED]` (D-10) |
| **Kepala Cabang** | Approver hierarki (dipakai downstream 03; Vertel/NPP di 05). Disebut di sini hanya sebagai konteks sensus D-10. | `[VERIFIED — doc]` (D-10) |
| **Compliance / AML Reviewer** | Visibilitas flag AML "High" dibatasi ke allow-list position-code. Role ini TIDAK ada di sensus cabang D-10 → kemungkinan role HO; konfirmasi pemetaan `[OPEN]` (BR-REG-13-adjacent). | `[INFERRED][LOCKED]` (BR-REG-13) |

---

## 3. Model Data

> **GROUND TRUTH schema modul 01-intake.** Skema fisik WAJIB mengikuti `docs/DB-CONVENTIONS.md` (disepakati 2026-07-14): prefix kelas tabel (`trx_/mst_/cfg_/log_/map_/stg_/out_`), `snake_case` Inggris singular, PK teknis `id BIGINT GENERATED ALWAYS AS IDENTITY`, business key terpisah + `ux_` unique (**`credit_id` = business key `[LOCKED]`, BUKAN PK teknis** — dirujuk lintas modul & eksternal), declared FK nyata, kolom audit wajib (`created_at/created_by/updated_at/updated_by`; tabel `log_` hanya `created_*`, append-only), SATU kolom `status` per spine, tipe per DB-CONVENTIONS §3 (uang `NUMERIC(18,2)`, rate `NUMERIC(9,6)`, `TIMESTAMPTZ` UTC, tanggal murni `DATE`, `BOOLEAN NOT NULL DEFAULT false`, enum kecil `VARCHAR`+`CHECK`, file = object-key `VARCHAR`, bukan BLOB). Tipe census di bawah ditulis sebagai tipe target PostgreSQL (USULAN — final ITEC A-2, portable); pemetaan Java (`BigDecimal`, `Instant`) mengikuti konvensi tim BE. Nama entity konseptual umbrella (`CREDIT_APPLICATION`, `RELATED_PERSON`, `MOOFI_SYNC_RECORD`, dst.) tetap dipakai section lain — §3.1 memetakan entity → tabel target fisik.

Cakupan census: **35 tabel legacy** ter-register di modul ini — 34 owned 01 + `tr_CAS_AC_header` (owner target **02**, §3.4 #12) — register disposisi lengkap §3.4 → **26 tabel target**. Kelas disposisi migrasi per `docs/DATA-MIGRATION-PLAN.md` §1: **MIGRATE** (data dibawa, mapping kolom eksplisit), **MIGRATE-READONLY** (historis dibawa ke arsip/read-model), **DISCARD** (`[ARTIFACT]` — register + konfirmasi stakeholder), **REBUILD** (diturunkan ulang / mulai kosong). Marker per field = `[confidence][mutability]` (confidence dari KB `30-data-model/`; mutability per `data-mutation-policy.md`).

### 3.1 Tabel target dimiliki kapabilitas ini (census penuh)

Ringkasan (detail per tabel di §3.1.1–§3.1.13):

| # | Tabel target | Kelas | Entity umbrella | Mapping asal (legacy) | Disposisi |
|---|---|---|---|---|---|
| 1 | `trx_application` | `trx_` spine | `CREDIT_APPLICATION` | `tr_CAS` (55 kol) + serapan `tr_cas_mobile_flag` | MIGRATE |
| 2 | `trx_application_related_person` | `trx_` | `RELATED_PERSON` | `tr_CAS_references` | MIGRATE (transform positional→typed) |
| 3 | `trx_application_financial_profile` | `trx_` | financial profile | `tr_CAS_financial` | MIGRATE |
| 4 | `trx_application_other_installment` | `trx_` | financial profile child | `tr_CAS_installment` | MIGRATE |
| 5 | `trx_application_bank_account` | `trx_` | bank account | `tr_CAS_bank_account` | MIGRATE |
| 6 | `trx_application_corporate_profile` | `trx_` | corporate profile | `tr_CAS_corporate_document` (minus kolom akta) | MIGRATE |
| 7 | `trx_application_corporate_deed` | `trx_` | corporate deeds (typed) | `tr_CAS_corporate_document` (founders/management deed) + `tr_CAS_corporate_adjustment_deed` + `tr_CAS_corporate_amendment_deed` | MIGRATE (transform → typed rows) |
| 8 | `trx_application_corporate_owner` | `trx_` | corporate owners | `tr_CAS_corporate_owner` | MIGRATE |
| 9 | `trx_application_document` | `trx_` | Document/photo record | `Tr_CAS_photo_detail` + `tr_CAS_AC_detail` | MIGRATE (file → object storage) |
| 10 | `trx_application_repeat_order` | `trx_` | repeat-order detail | `tr_CAS_repeat_order` | MIGRATE |
| 11 | `trx_application_payment_point` | `trx_` | payment point | `tr_CAS_payment_point` | MIGRATE |
| 12 | `trx_application_lkk_score` | `trx_` | LKK score | `tr_CAS_LKK_Score` | MIGRATE-READONLY |
| 13 | `trx_application_aml_answer` | `trx_` | AML questionnaire | `tr_CAS_APUPPT` (+ historis `tr_APUPPT_header`/`tr_APUPPT_detail`, OQ-CORE-09) | MIGRATE |
| 14 | `trx_application_aml_risk` | `trx_` | `AML_SCREENING` | `tr_CAS_APUPPT_risk` | MIGRATE |
| 15 | `log_aml_hit` | `log_` | `AML_HIT_LOG` | `tr_APUPPT_blacklist_log` | MIGRATE-READONLY |
| 16 | `log_ppatk_hit` | `log_` | PPATK hit evidence | `tr_PPATK_hit_log` | MIGRATE-READONLY |
| 17 | `log_blacklist_screening` | `log_` | blacklist screening result | `tr_blacklist_daily` | MIGRATE-READONLY (hasil baru re-derive live, BR-07) |
| 18 | `map_customer_blacklist` | `map_` | blacklist cross-ref | `tr_mapping_customer_blacklist` | MIGRATE |
| 19 | `trx_pooling_order` | `trx_` | Pooling Order/OMA channel | `tr_pooling_orders` (66 kol) | MIGRATE-READONLY + `[OPEN — OQ-GAP-01]` |
| 20 | `trx_dealer_order_source` | `trx_` | dealer order source header | `tr_dealer_order_source_header` | MIGRATE |
| 21 | `trx_dealer_order_source_refund` | `trx_` | TAC/third-party refund (typed) | `tr_dealer_order_source_TAC` + `tr_dealer_order_source_third_party` | MIGRATE (transform → typed rows) |
| 22 | `map_moofi_fincore` | `map_` | `MOOFI_SYNC_RECORD` | `CASMobile_mappingfincore` (36 kol) | REBUILD (operasional via E13) + MIGRATE-READONLY (historis) |
| 23 | `log_moofi_reverse` | `log_` | reverse-to-mobile audit | `tr_reverse_CAS_to_mobile` | MIGRATE-READONLY (OQ-ACQCAS-07) |
| 24 | `map_nik_repeat_order` | `map_` | NIK lama↔baru bridge RO | `tr_mapping_NIK_RO` | MIGRATE (OQ-GAP-07) |
| 25 | `cfg_number_format` (+ DB sequence) | `cfg_` | numbering / minting `credit_id` (ADR-08) | `tr_auto_number` + `tr_generate_code` | REBUILD (seed dari max legacy) |
| 26 | `log_number_generation` | `log_` | numbering audit | `tr_generate_code_history` | MIGRATE-READONLY |

Tabel legacy milik modul ini yang TIDAK menghasilkan tabel target sendiri: `tr_cas_mobile_flag` (diserap kolom `origination_channel` — REBUILD), `tr_CAS_AC_header` (5C narrative → target milik **02**, lihat §3.4), `temppotonganro` (`[ARTIFACT — discard]`, 0 referensi). Detail di §3.4.

#### 3.1.1 `trx_application` — spine intake (`CREDIT_APPLICATION`)

Owner: 01-intake. PK teknis `id`; business key **`credit_id`** `[LOCKED]` (`ux_trx_application_credit_id`) — nomor kontrak unik nasional, di-mint generator ADR-08 (format resolved OQ-GT-02 — 14-char, spec §3.1.13; jalur web di-mint saat create E1, jalur MOOFI saat sync E13). **Mapping asal**: `tr_CAS` (55 kolom, `FC_ACQ_MCF 2.sql:8081-8146`) + serapan `tr_cas_mobile_flag.Is_Mobile` → `origination_channel`. Identitas applicant TIDAK di-denormalisasi ulang di sini (DB-CONVENTIONS §6.3): kolom identitas `tr_CAS` mendarat di `mst_customer` (dedup-by-NIK D-01 S1, §3.3) dan dirujuk via `customer_id`.

Kolom target BARU (tanpa padanan langsung di `tr_CAS`):

| Kolom | Tipe | Null | Marker | Catatan |
|---|---|---|---|---|
| `id` | `BIGINT` identity PK | NO | —[INTENT] | PK teknis (DB-CONVENTIONS §2) |
| `customer_id` | `BIGINT` FK `mst_customer` | NO | [VERIFIED][INTENT] | Hasil dedup-by-NIK (D-01 S1); menggantikan blok identitas denormalized `tr_CAS` |
| `origination_channel` | `VARCHAR(10)` CHECK `moofi\|web\|pooling` | NO | [INFERRED][INTENT] | USULAN; serapan `tr_cas_mobile_flag` + `credit_source_id`; nilai `pooling` hanya bila OQ-GAP-01 mempertahankan channel |
| `product_line` | `VARCHAR(10)` CHECK `car\|motor` | NO | [VERIFIED][LOCKED semantik] | Dari `tr_CM.item_id` legacy (`'001'`=motor,`'002'`=car) — dipindah ke spine aplikasi (BR-01; GOTCHA-10) |
| `finance_scheme` | `VARCHAR(20)` CHECK `conventional_CF\|syariah_US` | NO | [VERIFIED][INTENT] | Rute STEP 10 RAC (dipakai 02); D-01 S7 |
| `application_type_id` | `VARCHAR(2)` FK `mst_` | NO | [VERIFIED][LOCKED kode] | 6 kode BR-CASFE-18 (lihat §3.1.14); legacy hidup di field CAS/CM — di target dipromosikan ke spine aplikasi |
| `status` | `VARCHAR(20)` CHECK enum §7 | NO | [VERIFIED][INTENT] | SATU kolom status (DB-CONVENTIONS §5); `rfa_locked` = legacy `status_approval='0'`; riwayat transisi → `log_` milik 03 |
| `version` | `INTEGER` DEFAULT 0 | NO | —[INTENT] | Optimistic locking (draft di-edit konkuren) |

Census penuh 55 kolom `tr_CAS` → disposisi kolom:

| Kolom legacy `tr_CAS` | Target | Tipe target | Null | Marker | Catatan |
|---|---|---|---|---|---|
| `created_by` `varchar(15)` | `trx_application.created_by` | `VARCHAR(50)` | NO | [VERIFIED][INTENT] | Kolom wajib DB-CONVENTIONS §4 |
| `created_on` `datetime` | `created_at` | `TIMESTAMPTZ` | NO | [VERIFIED][INTENT] | UTC |
| `last_updated_by` `varchar(15)` | `updated_by` | `VARCHAR(50)` | NO | [VERIFIED][INTENT] | |
| `last_updated_on` `datetime` | `updated_at` | `TIMESTAMPTZ` | NO | [VERIFIED][INTENT] | |
| `credit_id` `varchar(20)` PK | `credit_id` (business key) | `VARCHAR(20)` + `ux_` | NO | [VERIFIED][LOCKED] | Hub key seluruh spine origination; checksum migrasi zero-diff |
| `company_id` `varchar(5)` | `company_id` | `VARCHAR(5)` | NO | [VERIFIED][INTENT] | |
| `branch_id` `varchar(5)` | `branch_id` | `VARCHAR(5)` | NO | [VERIFIED][INTENT] | |
| `outlet_code` `varchar(8)` | `outlet_code` | `VARCHAR(8)` | YES | [VERIFIED][INTENT] | |
| `credit_source_id` `varchar(5)` | `credit_source_id` | `VARCHAR(5)` FK `mst_` | YES | [VERIFIED][INTENT] | Forced default legacy `=1` → OQ-CASFE-04 |
| `order_id` `varchar(50)` | `pooling_order_id` | `VARCHAR(50)` FK `trx_pooling_order.order_id` | YES | [VERIFIED][INTENT] | Lebar legacy 50 ≠ 25 di `tr_pooling_orders` [ARTIFACT — inkonsistensi]; FK declared di target |
| `customer_type` `varchar(1)` | `mst_customer.customer_kind` | `VARCHAR(12)` CHECK `individual\|corporate` | NO | [VERIFIED][INTENT] | Pindah ke master customer |
| `is_repeat_order` `bit` | `is_repeat_order` | `BOOLEAN` | NO | [VERIFIED][INTENT] | Default false |
| `is_instant_approval` `bit` | `is_instant_approval` | `BOOLEAN` | NO | [VERIFIED][INTENT] | Sinyal IA lane (OQ-MEET-04); gate milik 03 |
| `repeat_order_reason` `varchar(50)` | `repeat_order_reason` | `VARCHAR(50)` | YES | [VERIFIED][INTENT] | Detail RO di `trx_application_repeat_order` |
| `customer_name` `varchar(50)` | `mst_customer.full_name` | `VARCHAR(100)` | NO | [VERIFIED][INTENT] | Dedup — tidak ditulis ulang per aplikasi (GOTCHA-16) |
| `birth_place` `varchar(255)` | `mst_customer.birth_place` | `VARCHAR(255)` | YES | [VERIFIED][INTENT] | |
| `birth_date` `datetime` | `mst_customer.birth_date` | `DATE` | YES | [VERIFIED][INTENT] | Tanggal murni |
| `gender` `varchar(1)` | `mst_customer.gender` | `VARCHAR(1)` CHECK | YES | [VERIFIED][INTENT] | |
| `mother_name` `varchar(50)` | `mst_customer.mother_name` | `VARCHAR(100)` | YES | [VERIFIED][LOCKED] | PII verifikasi identitas (bank-grade KYC) |
| `email` `varchar(60)` | `mst_customer.email` | `VARCHAR(100)` | YES | [VERIFIED][INTENT] | |
| `identity_type_id` `varchar(2)` | `mst_customer.identity_type_id` | `VARCHAR(2)` FK `mst_` | NO | [VERIFIED][LOCKED] | |
| `identity_number` `varchar(45)` | `mst_customer.national_id` | `VARCHAR(45)`; NIK=16 digit CHECK per type | NO | [VERIFIED][LOCKED] | Regulator-facing (SLIK/OJK, APU-PPT); panjang NIK 16 `[LOCKED]`; unique per §3.3 |
| `valid_thru` `datetime` | `mst_customer.identity_valid_thru` | `DATE` | YES | [VERIFIED][LOCKED] | Compliance KTP; legacy NOT NULL dipaksa dummy — target nullable + validasi |
| `issue_date` `datetime` | `mst_customer.identity_issue_date` | `DATE` | YES | [VERIFIED][LOCKED] | |
| `identity_address` `varchar(255)` | `mst_customer.identity_address` | `VARCHAR(255)` | YES | [VERIFIED][INTENT] | Alamat KTP vs domisili = distingsi bisnis sah |
| `identity_location_id` `int` | `mst_customer.identity_location_id` | `INTEGER` FK lokasi | YES | [VERIFIED][INTENT] | |
| `is_blacklist` `bit` | `is_blacklist` | `BOOLEAN` | NO | [VERIFIED][LOCKED nilai / INTENT peran] | **Display/audit only** — re-derive server-side saat RFA (BR-07); hasil otoritatif di `log_blacklist_screening` |
| `customer_address` `varchar(255)` | `mst_customer.current_address` | `VARCHAR(255)` | YES | [VERIFIED][INTENT] | |
| `customer_location_id` `int` | `mst_customer.current_location_id` | `INTEGER` | YES | [VERIFIED][INTENT] | |
| `NPWP_no` `varchar(16)` | `mst_customer.tax_id` | `VARCHAR(16)` | YES | [VERIFIED][LOCKED] | NPWP 15–16 digit `[LOCKED]` regulatori |
| `telephone_number` `varchar(15)` | `mst_customer.telephone_number` | `VARCHAR(15)` | YES | [VERIFIED][INTENT] | |
| `mobile_phone` `varchar(20)` | `mst_customer.mobile_phone` | `VARCHAR(20)` | YES | [VERIFIED][INTENT] | |
| `residence_distance` `int` | `residence_distance` | `INTEGER` | YES | [VERIFIED][INTENT] | Konteks per-aplikasi (jarak ke cabang, feeds credit policy) — tetap di spine |
| `customer_source_id` `varchar(5)` | `customer_source_id` | `VARCHAR(5)` | YES | [VERIFIED][INTENT] | |
| `is_surveyed` `bit` | `is_surveyed` | `BOOLEAN` | NO | [VERIFIED][INTENT] | |
| `sources_id` `int` | `lead_source_id` | `INTEGER` | YES | [VERIFIED][INTENT] | Sumber lead/referensi order |
| `sources_name` `varchar(255)` | `lead_source_name` | `VARCHAR(255)` | YES | [VERIFIED][INTENT] | |
| `sources_address` `varchar(255)` | `lead_source_address` | `VARCHAR(255)` | YES | [VERIFIED][INTENT] | |
| `evaluation_id` `int` | `evaluation_id` | `INTEGER` | YES | [VERIFIED][INTENT] | |
| `residence_status_id` `varchar(10)` | `residence_status_id` | `VARCHAR(10)` FK `mst_` | YES | [VERIFIED][INTENT] | Snapshot kondisi tinggal saat aplikasi — application-scoped |
| `ownership_proof` `int` | `ownership_proof_id` | `INTEGER` | YES | [VERIFIED][INTENT] | Bukti kepemilikan rumah |
| `ownership_proof_name` `varchar(255)` | `ownership_proof_name` | `VARCHAR(255)` | YES | [VERIFIED][INTENT] | |
| `residence_condition` `varchar(255)` | `residence_condition` | `VARCHAR(255)` | YES | [VERIFIED][INTENT] | |
| `marital_status` `int` | `mst_customer.marital_status` | `INTEGER` FK `mst_` | YES | [VERIFIED][INTENT] | |
| `mail_to_source_id` `int` | `mailing_source_id` | `INTEGER` | YES | [VERIFIED][INTENT] | Blok alamat korespondensi |
| `mail_to_address` `varchar(255)` | `mailing_address` | `VARCHAR(255)` | YES | [VERIFIED][INTENT] | |
| `mail_to_location_id` `int` | `mailing_location_id` | `INTEGER` | YES | [VERIFIED][INTENT] | |
| `mail_to_telephone_number` `varchar(15)` | `mailing_telephone_number` | `VARCHAR(15)` | YES | [VERIFIED][INTENT] | |
| `credit_source_status` `varchar(4)` | `credit_source_status` | `VARCHAR(4)` | YES | [VERIFIED][OPEN semantik] | Kode status sumber order — enumerasi tidak ditemukan di master lokal |
| `analysis` `varchar(255)` | `intake_analysis` | `TEXT` | YES | [VERIFIED][INTENT] | Screening awal free-text (≠ `tr_CA.credit_analysis` milik 02) |
| `conclusion` `varchar(1)` | `intake_conclusion` | `VARCHAR(1)` CHECK | YES | [VERIFIED][OPEN kode] | Kode kesimpulan screening awal |
| `is_APUPPT` `int` | `is_apuppt` | `BOOLEAN` | NO | [VERIFIED][LOCKED nilai / INTENT peran] | Legacy `int` 0/1 → boolean; display/audit only (BR-07); hasil otoritatif di `trx_application_aml_risk` |
| `is_topup_ms` `bit` | `is_topup_ms` | `BOOLEAN` | NO | [VERIFIED][ARTIFACT] | Special-case partner Mega Solusi bolted-on — kandidat generalize; terkait `Tr_TopUpMegaSolusi` disabled (OQ-GAP-08, milik 04) |
| `customer_name_identity` `varchar(50)` | `mst_customer.full_name_identity` | `VARCHAR(100)` | YES | [VERIFIED][INTENT] | Nama persis sesuai KTP (bisa ≠ `full_name`) |
| `is_TopUp_Type` `varchar(3)` | `topup_type` | `VARCHAR(3)` | YES | [VERIFIED][ARTIFACT] | Pasangan `is_topup_ms`; semantik kode `[OPEN]` |

Index tambahan: `ix_trx_application_customer_id`, `ix_trx_application_status`, `ix_trx_application_branch_id`; FK declared ke `mst_customer`, `trx_pooling_order` (nullable).

#### 3.1.2 `trx_application_related_person` — typed roles (`RELATED_PERSON`, D-01 S2)

Owner: 01-intake. **Mapping asal**: `tr_CAS_references` (PK legacy `credit_id`+`reference_id` positional 1=spouse, 2=guarantor, 3–5=refs — GOTCHA-17, do-not-replicate). Unique: `ux_trx_application_related_person_app_role` partial unique `(application_id, role)` untuk `role IN ('spouse','guarantor')`; kardinalitas `reference` 2–3 by MaxKol (BR-36) dienforce service.

| Kolom target | Tipe | Null | Marker | Mapping asal / catatan |
|---|---|---|---|---|
| `id` | `BIGINT` identity PK | NO | —[INTENT] | Menggantikan PK komposit positional |
| `application_id` | `BIGINT` FK `trx_application` | NO | [VERIFIED][INTENT] | ← `credit_id` |
| `role` | `VARCHAR(10)` CHECK `spouse\|guarantor\|reference` | NO | [VERIFIED][INTENT] | ← derive dari `reference_id` (1/2/3–5) saat migrasi; typed rows menggantikan magic number (BR-12) |
| `sequence_no` | `INTEGER` | NO | [INFERRED][INTENT] | Urutan dalam role (reference ke-1/2/3); ← `reference_id` |
| `name` | `VARCHAR(100)` | NO | [VERIFIED][INTENT] | ← `references_name` `varchar(50)` |
| `identity_type_id` | `VARCHAR(2)` | YES | [VERIFIED][LOCKED] | ← `references_identity_type_id` |
| `identity_number` | `VARCHAR(40)` | YES | [VERIFIED][LOCKED] | ← `references_identity_number`; NIK regulator-facing; spouse/guarantor di-screen (BR-CASFE-7) |
| `birth_place` | `VARCHAR(30)` | YES | [VERIFIED][INTENT] | ← `references_birth_place` |
| `birth_date` | `DATE` | YES | [VERIFIED][INTENT] | ← `references_birth_date` `datetime` |
| `address` | `VARCHAR(255)` | YES | [VERIFIED][INTENT] | ← `references_address` |
| `location_id` | `INTEGER` | YES | [VERIFIED][INTENT] | ← `references_location_id` |
| `telephone_number` | `VARCHAR(25)` | YES | [VERIFIED][INTENT] | ← `references_telephone_number` |
| `office_phone_number` | `VARCHAR(15)` | YES | [VERIFIED][INTENT] | ← `references_office_phone_number` |
| `mobile_number` | `VARCHAR(20)` | YES | [VERIFIED][INTENT] | ← `references_mobile_number` |
| `fax` | `VARCHAR(15)` | YES | [VERIFIED][ARTIFACT] | ← `references_fax` — kanal mati; dibawa untuk kontinuitas data, kandidat retire |
| `occupation` | `VARCHAR(255)` | YES | [VERIFIED][INTENT] | ← `references_occupation` |
| `income` | `NUMERIC(18,2)` | YES | [VERIFIED][INTENT] | ← `references_income` `numeric(18,0)` — simpan `.00` (rekonsiliasi bulat) |
| `other_income` | `NUMERIC(18,2)` | YES | [VERIFIED][INTENT] | ← `references_other_income` |
| `other_income_desc` | `VARCHAR(255)` | YES | [VERIFIED][INTENT] | ← `references_other_income_desc` |
| `relationship_id` | `INTEGER` FK `mst_` | YES | [VERIFIED][OPEN-duplikat] | ← `references_relationship` `int`; duplikat paralel dgn kolom berikut — mana otoritatif `[OPEN]` (validasi per-role dua taxonomy BR-CUSTMASTER-8) |
| `applicant_relationship_id` | `INTEGER` FK `mst_` | YES | [VERIFIED][OPEN-duplikat] | ← `references_applicant_relationship_id` |
| audit (`created_at/by`, `updated_at/by`) | per §4 konvensi | NO | [VERIFIED][INTENT] | ← quartet `created_by/on`, `last_updated_by/on` |

#### 3.1.3 `trx_application_financial_profile` + `trx_application_other_installment`

**`trx_application_financial_profile`** ← `tr_CAS_financial` (1:1 by `credit_id`, FK declared legacy). PK `id`; `ux_(application_id)`.

| Kolom target | Tipe | Null | Marker | Mapping asal / catatan |
|---|---|---|---|---|
| `application_id` | `BIGINT` FK | NO | [VERIFIED][INTENT] | ← `credit_id` |
| `primary_income` | `NUMERIC(18,2)` | YES | [VERIFIED][LOCKED nilai] | ← `primary_income` `numeric(20,0)` — kapasitas bayar, input gate underwriting; migrasi lossless |
| `other_income` | `NUMERIC(18,2)` | YES | [VERIFIED][LOCKED nilai] | ← `other_income` `numeric(20,0)` |
| `other_income_desc` | `VARCHAR(255)` | YES | [VERIFIED][INTENT] | |
| `office_name` / `office_address` | `VARCHAR(255)` | YES | [VERIFIED][INTENT] | |
| `office_location_id` | `INTEGER` | YES | [VERIFIED][INTENT] | |
| `office_telephone_number` / `office_fax` | `VARCHAR(20)` | YES | [VERIFIED][INTENT] | `office_fax` [ARTIFACT] kandidat retire |
| `position` | `VARCHAR(50)` | YES | [VERIFIED][INTENT] | |
| `industry_type_id` | `INTEGER` FK `mst_` | YES | [VERIFIED][INTENT] | |
| `commodity_type` | `VARCHAR(50)` | YES | [VERIFIED][INTENT] | |
| `years_of_work_experience` | `NUMERIC(5,1)` | YES | [VERIFIED][INTENT] | ← `numeric(9,0)` |
| `profession_id` | `VARCHAR(5)` FK `mst_` | YES | [VERIFIED][LOCKED] | Input high-risk-profession AML (BR-11) |
| `household_expenses` | `NUMERIC(18,2)` | YES | [VERIFIED][LOCKED nilai] | ← `numeric(20,0)` |
| `education_expenses` / `health_expenses` | `NUMERIC(18,2)` | YES | [VERIFIED][INTENT] | ← `numeric(9,0)` |
| `number_of_dependents` | `INTEGER` | YES | [VERIFIED][INTENT] | |
| `monthly_other_installment` | `NUMERIC(18,2)` | YES | [VERIFIED][LOCKED nilai] | ← `numeric(20,0)` — DSR input |
| audit 4 kolom | per §4 | NO | [VERIFIED][INTENT] | |

**`trx_application_other_installment`** ← `tr_CAS_installment` (PK legacy `credit_id`+`monthly_other_installment_id`): `id` PK; `application_id` FK NO [VERIFIED][INTENT]; `other_installment_type_id` `VARCHAR(5)` FK `mst_` NO [VERIFIED][INTENT] (jenis cicilan lain berjalan); `ux_(application_id, other_installment_type_id)`; audit 4 kolom. Semua kolom legacy (6) ter-cover.

#### 3.1.4 `trx_application_bank_account` ← `tr_CAS_bank_account`

1:1 by `credit_id` (FK declared legacy). `id` PK; `ux_(application_id)`.

| Kolom target | Tipe | Null | Marker | Mapping asal / catatan |
|---|---|---|---|---|
| `application_id` | `BIGINT` FK | NO | [VERIFIED][INTENT] | ← `credit_id` |
| `account_name` | `VARCHAR(100)` | YES | [VERIFIED][LOCKED] | ← `account_name_customer` — rekening customer, fraud-control (cross-check Vertel 06) |
| `account_number` | `VARCHAR(20)` | YES | [VERIFIED][LOCKED] | ← `account_no_customer` |
| `bank_id` | `VARCHAR(6)` FK `mst_bank` | YES | [VERIFIED][INTENT] | ← `bank_id` |
| audit 4 kolom | per §4 | NO | [VERIFIED][INTENT] | |

#### 3.1.5 Profil korporasi — `trx_application_corporate_profile` + `trx_application_corporate_deed` + `trx_application_corporate_owner`

**`trx_application_corporate_profile`** ← `tr_CAS_corporate_document` (1:1 by `credit_id`, FK declared legacy; 41 kolom) MINUS 6 kolom akta founders/management (pindah ke tabel deed typed) MINUS 2 flag `is_adjustment_deed`/`is_amendment_deed` (di-derive dari kehadiran row deed — DISCARD kolom, bukan data). `id` PK; `ux_(application_id)`.

| Kolom target | Tipe | Null | Marker | Mapping asal / catatan |
|---|---|---|---|---|
| `application_id` | `BIGINT` FK | NO | [VERIFIED][INTENT] | ← `credit_id` |
| `commissioner_name` / `director_name` | `VARCHAR(255)` | YES | [VERIFIED][INTENT] | ← `corporate_commisioner_name` (typo legacy diperbaiki), `corporate_director_name` |
| `corporate_status` | `VARCHAR(255)` | YES | [VERIFIED][INTENT] | |
| `industry_id` / `other_industry` | `INTEGER` / `VARCHAR(255)` | YES | [VERIFIED][INTENT] | ← `corporate_industry_id`, `corporate_other_industry` |
| `operating_year_period` / `operating_month_period` | `SMALLINT` | YES | [VERIFIED][INTENT] | ← `corporate_year_period/month_period` `tinyint` |
| `number_of_employee` | `INTEGER` | YES | [VERIFIED][INTENT] | ← `numeric(18,0)` |
| `debtor_type` | `VARCHAR(255)` | YES | [VERIFIED][INTENT] | ← `corporate_debitur_type` |
| `telephone_number` / `fax_number` | `VARCHAR(30)` | YES | [VERIFIED][INTENT] | |
| `website` / `email` | `VARCHAR(255)` | YES | [VERIFIED][INTENT] | ← `corporate_site`, `corporate_email` |
| `director_identity_number` | `VARCHAR(16)` | NO | [VERIFIED][LOCKED] | NIK direktur — KTP=16 digit, validasi server-side (BR-35) |
| `commissioner_identity_number` | `VARCHAR(16)` | NO | [VERIFIED][LOCKED] | NIK komisaris |
| `corporate_tax_id` / `director_tax_id` | `VARCHAR(30)` | YES | [VERIFIED][LOCKED] | ← `corporate_tax_id_number`, `director_tax_id_number` (NPWP) |
| `siup_id` / `siup_issue_date` / `siup_due_date` | `VARCHAR(50)` / `DATE` / `DATE` | YES | [VERIFIED][LOCKED] | Izin usaha; legacy `smalldatetime NOT NULL` dipaksa dummy → target nullable + validasi kondisional |
| `tdp_id` / `tdp_issue_date` / `tdp_due_date` | `VARCHAR(50)` / `DATE` / `DATE` | YES | [VERIFIED][LOCKED] | |
| `is_completeness_letter` / `completeness_letter_issue_date` / `completeness_letter_due_date` | `BOOLEAN` / `DATE` / `DATE` | NO/YES/YES | [VERIFIED][INTENT] | |
| `entity_type_id` | `VARCHAR(2)` FK `mst_` | YES | [VERIFIED][INTENT] | ← `corporate_entity_type_id` |
| `reporter_relationship_id` | `VARCHAR(4)` | YES | [VERIFIED][LOCKED] | ← `corporate_reporter_relationship_id` — kode pelaporan OJK |
| `is_go_public` | `BOOLEAN` | NO | [VERIFIED][INTENT] | ← `varchar(2)` → boolean |
| `founders_regency_id_ojk` | `VARCHAR(5)` | YES | [VERIFIED][LOCKED] | Kode wilayah OJK |
| audit 4 kolom | per §4 | NO | [VERIFIED][INTENT] | |

**`trx_application_corporate_deed`** — typed rows menggantikan kolom-flat + 2 tabel satelit. **Mapping asal**: `tr_CAS_corporate_document.founders_deed_number/date` + `founders_notary_name` + `founders_SKMENKUMHAM_number`; `management_deed_number/date` + `management_notary_name`; `tr_CAS_corporate_adjustment_deed` (4 kolom bisnis); `tr_CAS_corporate_amendment_deed` (4 kolom bisnis). `id` PK; `ux_(application_id, deed_type)`.

| Kolom target | Tipe | Null | Marker | Catatan |
|---|---|---|---|---|
| `application_id` | `BIGINT` FK | NO | [VERIFIED][INTENT] | ← `credit_id` (semua sumber) |
| `deed_type` | `VARCHAR(12)` CHECK `founders\|management\|adjustment\|amendment` | NO | [VERIFIED][INTENT] | Normalisasi typed rows (pola sama D-01 S2); KB gap §2.11–2.12 catat akta melekat aplikasi [ARTIFACT] — jangka panjang kandidat pindah ke legal-entity master |
| `deed_number` | `VARCHAR(255)` | YES | [VERIFIED][LOCKED] | Nomor dokumen legal (adjustment/amendment `varchar(50)`, founders/management `varchar(255)`) |
| `deed_date` | `DATE` | YES | [VERIFIED][LOCKED] | |
| `notary_name` | `VARCHAR(255)` | YES | [VERIFIED][LOCKED] | Hanya founders/management di legacy |
| `sk_menkumham_number` | `VARCHAR(100)` | YES | [VERIFIED][LOCKED] | Pengesahan Kemenkumham — identifier legal-regulator |
| audit 4 kolom | per §4 | NO | [VERIFIED][INTENT] | `created_by/on` NOT NULL di tabel satelit legacy |

**`trx_application_corporate_owner`** ← `tr_CAS_corporate_owner` (1:N, 15 kolom): `id` PK (legacy `bigint` identity dipertahankan polanya); `application_id` FK NO; `owner_seq` `INTEGER` YES [VERIFIED][INTENT]; `owner_name` `VARCHAR(100)` YES [VERIFIED][INTENT]; `owner_identity_type` `VARCHAR(2)` YES [VERIFIED][LOCKED]; `owner_identity_number` `VARCHAR(50)` YES [VERIFIED][LOCKED] (NIK beneficial owner); `owner_gender_ojk_id` `VARCHAR(2)` YES [VERIFIED][LOCKED] (kode OJK); `owner_address` `VARCHAR(255)` YES [VERIFIED][INTENT]; `owner_location_id` `VARCHAR(10)` YES [VERIFIED][INTENT]; `owner_position_id` `VARCHAR(3)` FK `mst_` YES [VERIFIED][INTENT]; `owner_share_percentage` `NUMERIC(5,2)` YES [VERIFIED][INTENT] (← `float` [ARTIFACT — presisi float dilarang]; Σ per aplikasi = 100% dienforce server-side BR-35/AC-15); audit 4 kolom. Sudah ternormalisasi benar di legacy (1 row per owner).

#### 3.1.6 `trx_application_document` ← `Tr_CAS_photo_detail` + `tr_CAS_AC_detail`

Dokumen/foto keyed by application + photo-type; vocabulary photo-type `[LOCKED]` (BR-CASFE-17). File pindah ke object storage — kolom path → `object_key` (DB-CONVENTIONS §3); migrasi menyalin file + rewrite path. `id` PK.

| Kolom target | Tipe | Null | Marker | Mapping asal / catatan |
|---|---|---|---|---|
| `application_id` | `BIGINT` FK | NO | [VERIFIED][INTENT] | ← `credit_id` (kedua sumber) |
| `document_no` | `VARCHAR(20)` | YES | [VERIFIED][INTENT] | ← `tr_CAS_AC_detail.document_no` (NULL utk foto CAS biasa); konteks dokumen AC 5C |
| `photo_type_id` | `VARCHAR(10)` FK `mst_` | NO | [VERIFIED][LOCKED] | ← `photo_type_id` (kedua sumber) — vocabulary BR-CASFE-17 |
| `photo_id` | `VARCHAR(10)` | NO | [VERIFIED][INTENT] | ← `photo_id` (kedua sumber) |
| `file_name` | `VARCHAR(200)` | YES | [VERIFIED][INTENT] | ← `filename` `varchar(100)` / `file_name` `varchar(200)` |
| `object_key` | `VARCHAR(1000)` | NO | [VERIFIED][INTENT] | ← `filePath` `varchar(100)` / `file_path` `varchar(1000)`; kegagalan simpan WAJIB surface (BR-37, fix FE EC5) |
| `status` | `VARCHAR(1)` CHECK | YES | [VERIFIED][OPEN kode] | ← `Tr_CAS_photo_detail.Status` — enumerasi `[OPEN]` |
| `is_new_zoom` (legacy, kedua sumber) | — DISCARD | — | [VERIFIED][ARTIFACT] | Flag versi app mobile lama — diserap `origination_channel`; tidak dibawa |
| audit 4 kolom | per §4 | NO | [VERIFIED][INTENT] | |

#### 3.1.7 Repeat order, payment point, LKK score

**`trx_application_repeat_order`** ← `tr_CAS_repeat_order` (1:1 by `credit_id`, FK declared legacy; 17 kolom): `id` PK; `application_id` FK NO; `agreement_number_old` `VARCHAR(14)` NO [VERIFIED][LOCKED] (kontrak lama — gate BR-19; lookup via `map_nik_repeat_order` §3.1.12); `description` `VARCHAR(255)` YES [VERIFIED][INTENT] (← `repeat_order_description`); `category_id` / `decision_id` / `reference_source_id` / `applicant_relation_id` `VARCHAR(5)` FK `mst_` YES [VERIFIED][INTENT] (← `repeat_order_*` — decision-tier BR-20); `bank_id` `VARCHAR(20)` YES [VERIFIED][INTENT]; `account_name` `VARCHAR(50)` / `account_number` `VARCHAR(30)` YES [VERIFIED][LOCKED] (rekening pencairan RO); `telephone_number` `VARCHAR(30)` YES [VERIFIED][INTENT]; `reference_source_desc` `VARCHAR(20)` YES [VERIFIED][INTENT]; `status_repeat_order` → `status` `VARCHAR(5)` YES [VERIFIED][OPEN kode]; audit 4 kolom.

**`trx_application_payment_point`** ← `tr_CAS_payment_point` (8 kolom): `id` PK (legacy identity); `application_id` FK NO; `payment_type_id` `INTEGER` NO [VERIFIED][INTENT]; `payment_point_id` `INTEGER` FK `mst_` NO [VERIFIED][INTENT]; `ux_(application_id, payment_type_id, payment_point_id)`; audit 4 kolom.

**`trx_application_lkk_score`** ← `tr_CAS_LKK_Score` (8 kolom, heap tanpa PK [ARTIFACT — target diberi PK]): `id` PK; `application_id` FK NO; `parameter_lkk_code` `VARCHAR(50)` NO [VERIFIED][INTENT]; `description` `VARCHAR(100)` NO [VERIFIED][INTENT]; `score` `NUMERIC(9,2)` YES [VERIFIED][INTENT] (← `decimal(21,2)`); audit 4 kolom. Kepanjangan "LKK" tidak ditemukan di source `[OPEN]` (kandidat: Lembar Kerja Kelayakan); writer path perlu konfirmasi saat detail engineering → disposisi **MIGRATE-READONLY** sampai terkonfirmasi dipakai flow rebuild.

#### 3.1.8 Screening AML / blacklist (regulatori — `[LOCKED]` keseluruhan, BR-REG-1..13)

**`trx_application_aml_answer`** ← `tr_CAS_APUPPT` (6 kolom; PK legacy `credit_id`+`question_id`+`question_flag`): `id` PK; `application_id` FK NO [VERIFIED][LOCKED]; `question_id` `INTEGER` FK `mst_apuppt_question` NO [VERIFIED][LOCKED] (teks pertanyaan regulator-defined — tidak boleh diubah diam-diam); `question_flag` `VARCHAR(50)` NO [VERIFIED][LOCKED]; `answer` `TEXT` YES [VERIFIED][LOCKED] (← `varchar(max)`); `ux_(application_id, question_id, question_flag)`; audit `created_at/by` (legacy tanpa update — sekali-tulis). Pasangan legacy kedua `tr_APUPPT_header` (7 kolom: `credit_id`+`APUPPT_question_id` PK, `is_answer bit`) + `tr_APUPPT_detail` (14 kolom: identitas pihak terkait jawaban — `name`, `address`, `residence_address`, `customer_relationship`, `telephone_number`, `industry_type`, `corporate_commisioner_name`, `answer`) = **duplikat/versi kuesioner lain `[OPEN — OQ-CORE-09]`** → disposisi **MIGRATE-READONLY** (arsip compliance), TIDAK menjadi tabel operasional kedua sebelum OQ-CORE-09 resolved; bila terbukti versi aktif, kolom detail dilebur sebagai kolom nullable di `trx_application_aml_answer`.

**`trx_application_aml_risk`** ← `tr_CAS_APUPPT_risk` (1:1 by `credit_id`, FK declared legacy; 22 kolom) — entity `AML_SCREENING`: `id` PK; `application_id` FK NO; dimensi risiko `pep_id`, `family_pep_id`, `product_type_id`, `use_type_id`, `legal_entity_type_id`, `shareholders_id`, `nationality_id`, `funding_source_id`, `party_id`, `job_level_id` semua `VARCHAR(8)` FK `mst_` YES [VERIFIED][LOCKED] (dimensi regulator-defined, bobot dari weight-reference table BR-08); `business_location_id` `INTEGER` YES [VERIFIED][LOCKED] (cross-check red-zone `mst_apuppt_red_zone`, BR-11); `financing_purposes_id` / `financing_scheme_id` / `rab_id` `VARCHAR(5)` YES [VERIFIED][LOCKED] + `rab_others` `VARCHAR(50)` YES [VERIFIED][INTENT] (← `RAB_id`/`RAB_others`; kepanjangan RAB `[OPEN]`); `aml_score` `NUMERIC(9,2)` YES [VERIFIED][LOCKED] (← `score_apuppt numeric(18,2)`); `aml_risk_tier` `VARCHAR(6)` CHECK `low\|medium\|high` NO [VERIFIED][LOCKED metodologi] (← `risk_apuppt int` — mapping tier Low ≤1.5 / Medium ≤2.5 / High >2.5, BR-08); audit 4 kolom; `ux_(application_id)`.

**`log_aml_hit`** ← `tr_APUPPT_blacklist_log` (14 kolom, heap append-only) — entity `AML_HIT_LOG` `[LOCKED]` BR-REG-10/BR-10: `id` PK (← `ID` identity); `matched_name` `VARCHAR(100)` YES [VERIFIED][LOCKED] (← `Name`); `ktp_no` `VARCHAR(16)` YES [VERIFIED][LOCKED] (← `KTPNo`); `birth_date` `DATE` YES [VERIFIED][LOCKED] (← `BirthDate`); `watchlist_ref_code` `VARCHAR(20)` YES [VERIFIED][LOCKED] (← `KodeDensus` — kode referensi DTTOT/densus); `is_level1` / `is_level2` `BOOLEAN` NO [VERIFIED][LOCKED] + `level3_blacklist_desc` `VARCHAR(255)` YES [VERIFIED][LOCKED] (tier match); `credit_id` `VARCHAR(20)` YES [VERIFIED][LOCKED] (← `CasId`); `npp_no` `VARCHAR(10)` YES [VERIFIED][LOCKED] (← `NppNo`); `branch` `VARCHAR(10)` YES [VERIFIED][LOCKED]; `created_by` `VARCHAR(50)` / `created_at` NO [VERIFIED][LOCKED] (officer + waktu — BR-REG-10). INSERT-only; tanpa `updated_*` (DB-CONVENTIONS §4).

**`log_ppatk_hit`** ← `tr_PPATK_hit_log` (11 kolom; PK legacy `credit_id`+`nik`): `id` PK; `credit_id` `VARCHAR(20)` NO [VERIFIED][LOCKED]; `nik` `VARCHAR(20)` NO [VERIFIED][LOCKED]; `nik_type` `VARCHAR(3)` YES [VERIFIED][LOCKED]; `status` `VARCHAR(20)` YES [VERIFIED][LOCKED]; `flag` `BOOLEAN` NO [VERIFIED][LOCKED]; `hit_count` `INTEGER` YES [VERIFIED][LOCKED] (← `numeric(18,0)`); `ppatk_response` `TEXT` YES [VERIFIED][LOCKED] (raw evidence watchlist — retained verbatim); `created_at/by` NO. Legacy punya `last_updated_*` [ARTIFACT] — rebuild INSERT-only: re-screen = row baru, bukan update (jejak regulatori utuh).

**`log_blacklist_screening`** ← `tr_blacklist_daily` (7 kolom): `id` PK; `credit_id` `VARCHAR(20)` NO [VERIFIED][LOCKED] (legacy `varchar(14)` — anomali lebar `[OPEN — OQ-CORE-10]`, migrasi cast lossless); `blacklist_reason_id` `INTEGER` NO [VERIFIED][LOCKED]; `blacklist_reason_desc` `VARCHAR(255)` YES [VERIFIED][LOCKED]; kolom BARU `matched_on` `VARCHAR(50)` YES [INFERRED][INTENT] + `screening_source` `VARCHAR(20)` YES [INFERRED][INTENT] (jejak broad-match BR-04: field yang match + register sumber); `created_at/by` NO. INSERT-only per screening run — setiap re-derive RFA (BR-07) menulis row baru; legacy `last_updated_*` [ARTIFACT] tidak dibawa.

**`map_customer_blacklist`** ← `tr_mapping_customer_blacklist` (7 kolom): `id` PK; `lessee_id` `VARCHAR(45)` NO + `ux_` [VERIFIED][LOCKED] (← `lesseeid` PK legacy); `s_id` `VARCHAR(45)` YES [VERIFIED][LOCKED] (← `sID` — identifier register blacklist eksternal); `pname_id` `VARCHAR(50)` YES [VERIFIED][LOCKED] (← `PNameID`); audit 4 kolom. Bridge ke register eksternal `CFCustomerBlacklist` (linked-server legacy) — di rebuild register diakses via ACL live (BR-05); mapping dipertahankan untuk korelasi historis.

#### 3.1.9 `trx_pooling_order` ← `tr_pooling_orders` (66 kolom — channel Pooling Order/OMA)

Kelangsungan channel `[OPEN — OQ-GAP-01]` (8 SP aktif + EF, tetapi join di `sp_Cek_rac_history` sudah di-comment-out — indikasi transisi). Disposisi: **MIGRATE-READONLY** (data historis wajib dibawa ke arsip/read-model); tabel operasional di bawah HANYA dibuat bila OQ-GAP-01 memutuskan channel dipertahankan — bila dikonsolidasi ke MOOFI, cukup arsip. PK `id`; business key `order_id` + `ux_` [VERIFIED][LOCKED] (dirujuk `tr_CAS.order_id`). Census penuh 66 kolom:

| Kolom legacy | Target | Tipe target | Null | Marker | Catatan |
|---|---|---|---|---|---|
| `created_by`/`created_on`/`last_updated_by`/`last_updated_on` | audit 4 kolom | per §4 | NO | [VERIFIED][INTENT] | |
| `order_id` `varchar(25)` PK | `order_id` | `VARCHAR(25)` + `ux_` | NO | [VERIFIED][LOCKED] | Business key; FK target dari `trx_application.pooling_order_id` |
| `order_date` `datetime` | `order_date` | `TIMESTAMPTZ` | YES | [VERIFIED][INTENT] | |
| `company_id` `varchar(2)` / `branch_id` `varchar(5)` | sama | `VARCHAR(2)`/`VARCHAR(5)` | YES | [VERIFIED][INTENT] | |
| `customer_name` `varchar(200)` | `customer_name` | `VARCHAR(200)` | YES | [VERIFIED][INTENT] | Snapshot pra-aplikasi (order SEBELUM `credit_id` ada — bukan pelanggaran §6.3; belum ada customer master link) |
| `identity_type_id` `varchar(5)` / `identity_number` `varchar(64)` | sama | `VARCHAR(5)`/`VARCHAR(64)` | YES | [VERIFIED][LOCKED] | NIK/KTP regulator-facing |
| `birth_date` `datetime` | `birth_date` | `DATE` | YES | [VERIFIED][INTENT] | |
| `survey_address` `varchar(255)` / `survey_address_desc` `varchar(100)` | sama | `VARCHAR(255)`/`VARCHAR(100)` | YES | [VERIFIED][INTENT] | Alamat lokasi survey (≠ alamat KTP) |
| `province_survey_id`/`regency_survey_id`/`district_survey_id`/`village_survey_id` `int` | sama | `INTEGER` ×4 | YES | [VERIFIED][INTENT] | Hirarki wilayah 4 level `int` ≠ `location_id` string di CAS — normalisasi lokasi saat rebuild |
| `survey_appointment` `datetime` / `survey_time` `varchar(3)` | sama | `TIMESTAMPTZ`/`VARCHAR(3)` | YES | [VERIFIED][INTENT] | Jadwal kunjungan surveyor |
| `phone1` `varchar(16)` / `phone2` `varchar(max)` | `phone1`/`phone2` | `VARCHAR(16)`/`TEXT` | YES | [VERIFIED][INTENT] | `phone2 varchar(max)` anomali (multi-nomor digabung [INFERRED]) |
| `item_merk_type_id` `varchar(10)` | `item_merk_type_id` | `VARCHAR(10)` FK `mst_` | NO | [VERIFIED][INTENT] | Merk/tipe item diminati |
| `tenor` `tinyint` / `gross_down_payment` `numeric(18,0)` | sama | `SMALLINT`/`NUMERIC(18,2)` | YES | [VERIFIED][INTENT] | Simulasi awal pra-CM |
| `status_order` `varchar(5)` | `status` | `VARCHAR(5)` CHECK | NO | [VERIFIED][OPEN kode] | Daftar nilai state machine order tidak ditemukan di master lokal `[OPEN]` |
| `NIK_surveyor_code` `varchar(10)` | `surveyor_nik` | `VARCHAR(10)` | YES | [VERIFIED][INTENT] | Penunjukan surveyor |
| `approve_by` `varchar(max)` / `approve_date` | `approved_by`/`approved_at` | `TEXT`/`TIMESTAMPTZ` | YES | [VERIFIED][INTENT] | `varchar(max)` kemungkinan daftar/JSON approver [INFERRED] — rebuild: riwayat approval → `log_` |
| `reject_by` `varchar(max)` / `reject_date` | `rejected_by`/`rejected_at` | `TEXT`/`TIMESTAMPTZ` | YES | [VERIFIED][INTENT] | Simetris approval |
| `profession_id` `varchar(4)` / `industry_type_id` `int` / `position_id` `varchar(4)` / `lama_bekerja_id` `int` / `income_id` `int` / `kepemilikan_usaha_id` `int` | `profession_id`/`industry_type_id`/`position_id`/`length_of_work_id`/`income_band_id`/`business_ownership_id` | per legacy → `VARCHAR(4)`/`INTEGER` | YES | [VERIFIED][INTENT] | Demografi pekerjaan (pre-screening); nama Indonesia distandarkan Inggris (DB-CONVENTIONS §1) |
| `home_status_id` `varchar(3)` / `home_time_stay_id` `int` | `home_status_id`/`home_time_stay_id` | `VARCHAR(3)`/`INTEGER` | YES | [VERIFIED][INTENT] | |
| `marital_id` `int` / `marital_status` `char(1)` | `marital_id` (+ `marital_status` arsip) | `INTEGER` | YES | [VERIFIED][OPEN-duplikat] | Dua kolom marital paralel — otoritatif `[OPEN]`; target simpan `marital_id`, `marital_status` hanya di arsip readonly |
| `photo_KTP_pemohon`/`photo_KTP_pasangan`/`photo_NPWP` `varchar(500)` | `photo_ktp_applicant_key`/`photo_ktp_spouse_key`/`photo_npwp_key` | `VARCHAR(500)` object-key | YES | [VERIFIED][INTENT] | File → object storage |
| `APPI_result` `varchar(50)` / `APPI_file_name` `varchar(1000)` / `APPI_file_path` `varchar(200)` | `appi_result`/`appi_file_name`/`appi_object_key` | `VARCHAR(50)`/`VARCHAR(1000)`/`VARCHAR(500)` | YES | [VERIFIED][INTENT] | Cek asosiasi pembiayaan (kepanjangan APPI `[OPEN]`) |
| `recommendation` `varchar(50)` | `recommendation` | `VARCHAR(50)` | YES | [VERIFIED][INTENT] | Hasil pre-screening |
| `pefindo_score`/`pefindo_result` `varchar(50)`; `pefindo_file_name` `varchar(1000)` / `pefindo_file_path` `varchar(200)` | `pefindo_score`/`pefindo_result`/`pefindo_file_name`/`pefindo_object_key` | sama pola | YES | [VERIFIED][INTENT] | Skor biro Pefindo tahap order |
| `SLIK_result_pemohon` + file name/path; `SLIK_result_pasangan` + file name/path (6 kolom) | `slik_result_applicant`/`slik_file_name_applicant`/`slik_object_key_applicant` + `_spouse` ×3 | `VARCHAR(50)`/`VARCHAR(1000)`/`VARCHAR(500)` | YES | [VERIFIED][LOCKED] | Hasil SLIK OJK pemohon+pasangan — regulator-facing, retained verbatim |
| `description` `varchar(1000)` | `description` | `VARCHAR(1000)` | YES | [VERIFIED][INTENT] | |
| `application_type_id` `varchar(5)` | `application_type_id` | `VARCHAR(5)` | YES | [VERIFIED][LOCKED kode] | Paralel `tr_CAS.application_type_id` (BR-CASFE-18) |
| `spouse_name` `varchar(150)` / `spouse_identity_number` `varchar(50)` / `spouse_birth_date` `varchar(50)` | `spouse_name`/`spouse_identity_number`/`spouse_birth_date` | `VARCHAR(150)`/`VARCHAR(50)`/`DATE` | YES | [VERIFIED][LOCKED NIK] | `spouse_birth_date` legacy `varchar` [ARTIFACT data-quality] → target `DATE`, nilai unparseable = reject + register migrasi |
| `mobile_FCLID` `varchar(15)` | `moofi_fcl_id` | `VARCHAR(15)` | YES | [VERIFIED][INTENT] | FK logis ke `MOBILE.dbo.FCL` (sistem mobile eksternal) |
| `sync_date` `datetime` / `is_sync_order` `bit` | `synced_at`/`is_synced` | `TIMESTAMPTZ`/`BOOLEAN` | YES/NO | [VERIFIED][INTENT] | Di-set `spSyncPoolOrderToCAS` saat order dipromosikan jadi `tr_CAS` |

#### 3.1.10 `trx_dealer_order_source` + `trx_dealer_order_source_refund`

**`trx_dealer_order_source`** ← `tr_dealer_order_source_header` (11 kolom; FK declared legacy → `tr_NPP`): `id` PK; `order_source_no` `VARCHAR(25)` NO + `ux_` [VERIFIED][INTENT] (business key); `credit_id` `VARCHAR(20)` NO [VERIFIED][LOCKED] (anchor agreement — cross-module ref via business key, ADR-03); `dealer_code` `VARCHAR(10)` FK `mst_dealer` NO [VERIFIED][INTENT]; `status` `VARCHAR(1)` CHECK YES [VERIFIED][INTENT]; `approved_at` `TIMESTAMPTZ` / `approved_by` `VARCHAR(60)` YES [VERIFIED][INTENT]; `changes_desc` `VARCHAR(255)` YES [VERIFIED][INTENT]; audit 4 kolom.

**`trx_dealer_order_source_refund`** — typed rows menggantikan dua tabel kembar. **Mapping asal**: `tr_dealer_order_source_TAC` (13 kolom; `rate_TAC_refund`, `amount_TAC_refund`, `amount_TAC_refund_after_tax`) + `tr_dealer_order_source_third_party` (13 kolom; `rate_provisi_refund`, `amount_provisi_refund`, `amount_provisi_refund_after_tax`) — shape identik, dibedakan `refund_type`. PK `id`; `ux_(order_source_id, refund_type, job_title_id)` (PK legacy `credit_id`+`job_title_id`).

| Kolom target | Tipe | Null | Marker | Catatan |
|---|---|---|---|---|
| `order_source_id` | `BIGINT` FK `trx_dealer_order_source` | NO | [VERIFIED][INTENT] | ← join `credit_id` saat migrasi |
| `refund_type` | `VARCHAR(12)` CHECK `tac\|third_party` | NO | [VERIFIED][INTENT] | Diskriminator; ekspansi "TAC" `[OPEN]` OQ-ACQCAS-11 |
| `job_title_id` / `personel_id` | `INTEGER` | NO | [VERIFIED][INTENT] | |
| `personel_name` | `VARCHAR(250)` | YES | [VERIFIED][INTENT] | |
| `refund_rate` | `NUMERIC(9,6)` | NO | [VERIFIED][LOCKED nilai] | ← `rate_*_refund decimal(21,2)` — rate presisi 6 desimal per konvensi |
| `refund_amount` / `refund_amount_after_tax` | `NUMERIC(18,2)` | NO | [VERIFIED][LOCKED] | Payout finansial + perlakuan pajak — zero-diff migrasi |
| `bank_account_number` `VARCHAR(20)` / `bank_account_name` `VARCHAR(60)` | | YES | [VERIFIED][LOCKED] | Rekening tujuan payout — sensitif |
| audit 4 kolom | per §4 | NO | [VERIFIED][INTENT] | |

#### 3.1.11 Bridge MOOFI — `map_moofi_fincore` + `log_moofi_reverse` (STEP 8 / ADR-08)

**`map_moofi_fincore`** — realisasi fisik entity `MOOFI_SYNC_RECORD`; kunci idempotency E13 (BR-32). **Mapping asal**: `CASMobile_mappingfincore` (36 kolom; EF-only, 0 SP — writer aktual `[OPEN — OQ-GAP-06]`). Disposisi: **REBUILD** untuk operasional (diisi E13 mulai kosong) + **MIGRATE-READONLY** historis (36 kolom apa adanya ke arsip `stg_legacy_casmobile_mappingfincore` → read-model). Tabel operasional:

| Kolom target | Tipe | Null | Marker | Mapping asal / catatan |
|---|---|---|---|---|
| `id` | `BIGINT` identity PK | NO | —[INTENT] | Legacy tanpa PK declared [ARTIFACT — diperbaiki] |
| `moofi_reference_id` | `VARCHAR(50)` + `ux_` | NO | [VERIFIED][INTENT] | ← `MobileCASID numeric(18,0)` (kandidat natural key legacy); kunci idempotency: 1 aplikasi MOOFI = maks 1 `credit_id` (AC-14) |
| `moofi_fcl_id` / `moofi_survey_id` / `moofi_simulasi_id` | `VARCHAR(20)` | YES | [VERIFIED][INTENT] | ← `MobileFCLID`/`MobileSurveyID`/`MobileSimulasiID` — FK logis entitas mobile eksternal |
| `credit_id` | `VARCHAR(20)` | YES | [VERIFIED][LOCKED] | ← `CASID` (lebar 10 legacy → 20 target); terisi setelah minting sukses |
| `validation_status` | `VARCHAR(6)` CHECK `passed\|failed` | NO | [INFERRED][INTENT] | BARU — hasil validasi ingestion (kontrak `sp_validation_mobile_to_fincore`, GT STEP 8) |
| `validation_errors` | `JSONB` | YES | [INFERRED][INTENT] | BARU — alasan gagal per field, dikembalikan ke MOOFI (AC-13) |
| `photos_moved` | `BOOLEAN` | NO | [VERIFIED — doc][INTENT] | BARU — file foto dipindahkan Mobile→Fincore (GT STEP 8b) |
| `synced_at` / `synced_by` | `TIMESTAMPTZ`/`VARCHAR(50)` | YES | [VERIFIED][INTENT] | ← `TanggalMigrasi`/`CreatedBy` (audit) |
| `moofi_status` | `VARCHAR(5)` | YES | [VERIFIED][OPEN kode] | ← `Statuscasmobile` — enumerasi `[OPEN]` (pola reset `'D'` oleh reverse) |
| audit 4 kolom | per §4 | NO | [VERIFIED][INTENT] | ← `CreatedOn/By`, `LastUpdatedOn/By` |

Kolom snapshot simulasi legacy (`TipeAplikasi`, `TipeFinance`, `TipeBaruBekas`, `NamaDealer`, `Merk`, `Type`, `AssetSeriesID`, `HargaKendaraan`, `UangMuka`, `JenisTenor`, `Tenor`, `NominalAngsuran`, `TglJatuhTempo`, `IsPT`, `Analisa`, `IMEI` [OPEN semantik], `PONo`, `TglPONo`, `AgreementNumber`, `AdaRef`, `NamaRef`, `HubRef`, `AlamatRef`, `TelpRmhRef`, `RefLocationID` — 25 kolom) = **TIDAK dibawa ke tabel operasional** [VERIFIED][ARTIFACT — snapshot denormalized]: data setara sudah first-class di `trx_application`+anak hasil E13; historis tetap utuh di arsip MIGRATE-READONLY. Progresi `PONo`/`AgreementNumber` dilacak via spine `credit_id`, bukan disalin ke bridge.

**`log_moofi_reverse`** ← `tr_reverse_CAS_to_mobile` (10 kolom, heap): `id` PK; `credit_id` `VARCHAR(20)` NO [VERIFIED][LOCKED] (← `cas_id`; multi-event per aplikasi didukung — append-only); `is_reverse_to_mobile` / `is_sync_to_fincore` `BOOLEAN` NO [VERIFIED][INTENT] (updater legacy tidak ditemukan `[OPEN]`); `return_message` `VARCHAR(20)` YES [VERIFIED][INTENT]; `moofi_cas_id` / `moofi_survey_id` `VARCHAR(20)` YES [VERIFIED][INTENT]; `created_at/by` NO. Aksi reverse itu sendiri di rebuild `[OPEN]` OQ-ACQCAS-07 (trigger FE legacy pun commented-out) — log dibawa MIGRATE-READONLY; operasional hanya bila OQ-ACQCAS-07 memutuskan fitur hidup.

#### 3.1.12 `map_nik_repeat_order` ← `tr_mapping_NIK_RO`

Bridge NIK lama↔baru untuk lookup agreement lama Repeat Order (join `identity_number_old = lesseeid` legacy lintas-database). Populate path `[OPEN — OQ-GAP-07]` (migrasi satu-kali vs proses berjalan). 8 kolom legacy, heap tanpa PK/unique [ARTIFACT — risiko duplikat, diperbaiki]:

| Kolom target | Tipe | Null | Marker | Mapping asal / catatan |
|---|---|---|---|---|
| `id` | `BIGINT` identity PK | NO | —[INTENT] | |
| `identity_number` | `VARCHAR(40)` | NO | [VERIFIED][LOCKED] | NIK aktif (regulator-facing); legacy nullable → target NOT NULL |
| `identity_number_desc` | `VARCHAR(100)` | YES | [VERIFIED][INTENT] | |
| `identity_number_old` | `VARCHAR(40)` | NO | [VERIFIED][LOCKED] | NIK/lessee-id lama (len=16 di filter SP) |
| `identity_number_old_desc` | `VARCHAR(100)` | YES | [VERIFIED][INTENT] | |
| `ux_(identity_number, identity_number_old)` | unique | — | —[INTENT] | Idempotent upsert (DB-CONVENTIONS §1 kelas `map_`) |
| audit 4 kolom | per §4 | NO | [VERIFIED][INTENT] | |

#### 3.1.13 Numbering — DB sequence + `cfg_number_format` + `log_number_generation` (ADR-08)

Menggantikan `tr_auto_number` (9 kolom: PK `prefix`+`company_id`+`branch_id`+`period`, `last_number`) dan `tr_generate_code` (8 kolom: `code_type`, `period_year/month`, `code_format`, `last_number`, `last_generate_date`, `branch_id`) — dua generator paralel `[OPEN — OQ-CORE-11]` dikonsolidasi jadi SATU minting service (BR-33: jalur web E1 & sync E13 memakai generator sama). Disposisi **REBUILD**: counter `last_number` TIDAK dimigrasi sebagai state — sequence baru di-seed dari `max()` nomor legacy per scope saat cutover (mencegah tabrakan `credit_id`); jaminan yang dipertahankan `[INTENT]` = unik + scope-correct per prefix/company/branch/period.

**`cfg_number_format`** — **census kanonik: BE-07 §3.4** (owner definisi & admin surface = 07 via E38; kolom: `code_type` mis. `CREDIT_ID`, `company_id`, `branch_id`, `format_template` [`[VERIFIED]` — resolved OQ-GT-02, lihat bawah], `reset_period` CHECK `NONE|MONTHLY|YEARLY`, `sequence_name`, `effective_from/to` `DATE`, `is_active`, audit). Modul 01 adalah **konsumen minting** (E1 web / E13 sync memakai generator yang sama, BR-33) — tidak mendefinisikan ulang census di sini. Counter runtime = **DB sequence** per scope (bukan baris tabel ber-lock — menghapus race increment manual, DB-CONVENTIONS §6.5).

**Format legacy `credit_id` `[VERIFIED — resolved OQ-GT-02, 2026-07-14]`**: `branch_id(5) + YY(2) + MM(2) + SEQ(5, zero-pad)` = **14 karakter** (contoh: branch `00545`, Juli 2026, seq 1 → `00545` + `26` + `07` + `00001` = `00545260700001`). Sumber: `sp_get_auto_number` (`SP/FC_ACQ_MCF/dbo.sp_get_auto_number.StoredProcedure.sql:54-60` — `@yourNumber = @branchId + right(@currentYear,2) + @currentMonth + dbo.fc_get_sequence_number(@lastNumber)`); padding 5-digit via `fc_get_sequence_number` (`FC_ACQ_MCF 2.sql:282-299`; CASE tanpa ELSE → seq >99999 menghasilkan NULL = bug kapasitas laten `[ARTIFACT]` do-not-replicate). Counter legacy = baris `tr_generate_code` per (`code_type`,`period_year`,`period_month`,`branch_id`) → `reset_period = MONTHLY` per branch; kolom `code_format` legacy berisi literal `'Branch_YYYY_MM_00001'` (label — implementasi aktual memakai YY 2-digit, bukan YYYY). Keunikan nasional dijamin prefix `branch_id`. **Gotcha legacy yang memperkuat BR-33**: tiga `code_type` berbeda menghasilkan `credit_id` ber-format identik dengan counter independen — `'TrCas'` jalur web .NET (`GeneratedCode.cs:7` via `TrCasRepositoryEF.cs:33` GenerateCreditId), `'CreditId'` jalur SP sync (`sp_Sync_Pool_Order_To_CAS` `FC_ACQ_MCF 2.sql:85803`; `SpSyncMobileToFincoreR2` `:96053`; `spSyncPoolOrderToCAS` SP dump `:21`), `'CreditItid'` (sic) hardcoded `sp_get_auto_number_r4` (mobil; `dbo.sp_get_auto_number_r4...sql:31`) → potensi tabrakan antar jalur pada branch+bulan sama; rebuild WAJIB satu scope sequence per format output (bukan per code_type caller).

**`log_number_generation`** ← `tr_generate_code_history` (7 kolom): `id` PK; `code_type` `VARCHAR(50)` NO [VERIFIED][INTENT]; `actor_employee_id` `VARCHAR(50)` NO [VERIFIED][INTENT] (← `employee_id`); `branch_id` `VARCHAR(50)` NO [VERIFIED][INTENT]; `code_output` `VARCHAR(150)` NO [VERIFIED][LOCKED] (nomor yang diterbitkan — jejak gap detection); `status_transaction` `VARCHAR(25)` YES [VERIFIED][INTENT] (transaksi pemakai selesai/gagal); `created_at` NO (← `create_date`). INSERT-only; disposisi MIGRATE-READONLY (audit historis penomoran).

#### 3.1.14 Peta entity umbrella → tabel target + catatan semantik yang dipertahankan

| Entity umbrella (dipakai §4–§10) | Tabel target | Catatan semantik `[LOCKED]`/kanonik |
|---|---|---|
| `CREDIT_APPLICATION` | `trx_application` (§3.1.1) | `credit_id` **unik secara nasional** `[VERIFIED — doc][LOCKED semantik unik; format VERIFIED — OQ-GT-02 resolved, spec §3.1.13]` GT STEP 8; di-mint saat create (web E1) / sync (moofi E13) oleh generator §3.1.13. `origination_channel` dipakai memilih jalur lock (OQ-GT-01). **`application_type_id`** `VARCHAR(2)` NO — kolom target di `trx_application` (asal legacy: field CAS/CM, set lengkap 6 kode `[VERIFIED]` FE KB BR-CASFE-18 `Collection.cs:156-164`: `'01'`=TUTUP_BUKA, `'02'`=STANDARD, `'03'`=UMC_0, `'04'`=WRITE_OFF, `'05'`=NON_STANDARD, `'06'`=MULTI_PRODUCT — kode `[LOCKED]`; hanya `'02'`/`'03'` ter-exercise; makna `'01'/'04'/'05'/'06'` `[OPEN]` OQ-ACQCAS-10; **orthogonal** dari `finance_scheme`). `status` enum kanonik `draft\|rfa_locked\|risk_gated\|analyzing\|committee\|approved\|rejected\|corrected\|cancelled` (umbrella §7.2) — konflik huruf legacy FE `R`=REJECT vs BE `R`=Review (OQ-CASFE-10) **resolved by design**: huruf legacy TIDAK di-port |
| `RELATED_PERSON` | `trx_application_related_person` (§3.1.2) | Kardinalitas: `spouse` maks 1; `guarantor` maks 1; `reference` **2 atau 3 wajib** by MaxKol biro (≤3 → 2 refs; >3 → 3 refs, BR-CASFE-9 `[VERIFIED]`) — server-side (BR-36). Dua taxonomy relasi berbeda per role (BR-CUSTMASTER-8) |
| `ASSET` (capture) | fisik di `trx_credit_memo` draft + `trx_asset` **milik 04/05** (BE-04 §3) | 01 meng-capture `chassis_no`/`engine_no` `[LOCKED]` unik via E4; legacy `tr_items` keyed ke `tr_NPP` → di-census BE-05; validasi final hard-gate `sp_validation_chasis_number` STEP 15 (milik 05). `otr_price` `[LOCKED]`, `down_payment` (nett/gross), `tenor_months` = kolom `trx_credit_memo` (seed draft di sini, finalize 04 — BR-02) |
| `MOOFI_SYNC_RECORD` | `map_moofi_fincore` (§3.1.11) | Kunci idempotency `moofi_reference_id` unik: 1 aplikasi MOOFI = maks 1 `credit_id` |
| `AML_SCREENING` | `trx_application_aml_risk` + `trx_application_aml_answer` (§3.1.8) | `[LOCKED]` regulasi BR-REG-1..13; tier Low/Medium/High (BR-08) |
| `AML_HIT_LOG` | `log_aml_hit` + `log_ppatk_hit` (§3.1.8) | Append-only `[LOCKED]` BR-REG-10 |
| Document/photo record | `trx_application_document` (§3.1.6) | Vocabulary photo-type `[LOCKED]` BR-CASFE-17: KTP, Kartu Keluarga, Slip Gaji, KTP Pasangan, KTP Penjamin, Dokumen Kepemilikan Rumah, Dokumen Loan Calculator (yang terakhir terikat fitur dead legacy — evaluasi retire) |
| `is_blacklist` / `is_apuppt` | kolom `trx_application` | **Display/audit only** — WAJIB re-derive server-side, TIDAK dipercaya dari client (BR-07 / BR-ACQCAS-9); hasil otoritatif di `log_blacklist_screening` / `trx_application_aml_risk` |

### 3.2 Shared entities yang direferensikan (dari umbrella)

| Entity | Tabel target (owner modul) | Key | Owner (umbrella) | Peran di 01 |
|---|---|---|---|---|
| `CUSTOMER` | `mst_customer` (fisik didefinisikan modul master-data/05; kolom identitas eks-`tr_CAS` di-census §3.1.1) | `national_id` (NIK) unik; `tax_id` (NPWP) | Penulisan otoritatif **05-npp** (`tr_CIF` upsert, D-01 Step 15); target **dedup-at-intake 01** (D-01 Step 1) | Dedup-by-NIK + dedup lock saat capture (§3.3). Field `national_id`/`tax_id`/`ojk_economic_sector` `[LOCKED]` |
| `CREDIT_MEMO` | `trx_credit_memo` (+ normalisasi anak — BE-04 §3) | `id`; `credit_id`; `trans_type_id` | Finalize **04**; `trans_type_id` disusun **02** (dari RAC risk-category, D-01 Step 8) | **Seed draft di sini** (satu invariant lintas channel — BR-02). Status `draft` sampai RFA. OP/ULI/LCR `[LOCKED]` frozen-at-committee-approve (GT v2 STEP 12 — "OP/ULI/LCR, Asuransi Jiwa, Asuransi Kendaraan LOCKED saat approve"; milik 03/04, bukan di sini) |
| `CREDIT_ANALYSIS`, `RAC_SCREENING` | tabel `trx_`/`log_` milik **02** (BE-02 §3) — termasuk target 5C narrative eks-`tr_CAS_AC_header` | `application_id` | **02** | Dipicu setelah `ApplicationLocked` (downstream, §10); 5C narrative CMO = hand-off pra-RFA (§1.2) |

### 3.3 Dedup-by-NIK + deduplication lock di intake vs penulisan otoritatif CUSTOMER

Legacy me-recapture identitas penuh applicant di setiap aplikasi dan hanya mengisi `tr_CIF` saat NPP approval (akhir funnel), tidak pernah dipakai ulang di intake (GOTCHA-16 `[INTENT]`; BR-CUSTMASTER-14: tidak ada CIF master durable di legacy slice). Rebuild — kini **dimandatkan meeting**, bukan sekadar usulan:

- **01 (intake)** = **NIK-based deduplication lock at first capture** `[INTENT]` (D-01 Step 1): saat capture, lookup/link `CUSTOMER` by `national_id` (NIK); reuse master bila sudah ada, bukan re-capture buta. Field identitas `[LOCKED]` (nilai/format/validasi WAJIB dipertahankan; nama field boleh berubah). Semantik "lock" persisnya (link-only vs blokir draft in-flight kedua atas NIK sama tanpa override) tidak dirinci MoM → `[OPEN]` OQ-PRD01-01; default rebuild (USULAN): link-to-master WAJIB + tolak draft aktif duplikat pada NIK sama dengan error `409 DUPLICATE_INFLIGHT_APPLICATION`.
- **05 (NPP activation)** = **penulisan otoritatif** `tr_CIF` (KTP/NIK + NPWP) tetap dipegang 05 ("upserts customer master", D-01 Step 15). 01 hanya **membaca/menautkan**, tidak menjadi source-of-truth penulisan.

Sumber: `data-mutation-policy.md §Customer identity`; umbrella shared-entity `CUSTOMER`; GOTCHA-16; D-01 Step 1/15.

Pendukung dedup lintas-era: **`map_nik_repeat_order`** (§3.1.12) menjembatani NIK aktif ↔ NIK/lessee-id lama agar riwayat agreement Repeat Order tetap ketemu saat dedup/lookup (BR-19); populate path `[OPEN — OQ-GAP-07]`. Duplikat NIK historis yang ketahuan saat migrasi = keputusan bisnis (OQ-MIG-03, `DATA-MIGRATION-PLAN.md` §7), bukan auto-merge.

### 3.4 Register disposisi migrasi — 35 tabel legacy ter-register modul 01 (34 owned + `tr_CAS_AC_header` owner target 02)

Selaras `docs/DATA-MIGRATION-PLAN.md` §1 (mapping matrix per kolom = §3.1 di atas; rekonsiliasi per §3 DATA-MIGRATION-PLAN: row count, financial sums, checksum `[LOCKED]` zero-diff, FK integrity, status vocabulary, dedup).

| # | Tabel legacy | Disposisi | Target / alasan |
|---|---|---|---|
| 1 | `tr_CAS` (55 kol) | **MIGRATE** | → `trx_application` + kolom identitas → `mst_customer` (§3.1.1); dedup NIK saat transform (report duplikat → OQ-MIG-03) |
| 2 | `tr_CAS_references` | **MIGRATE** | → `trx_application_related_person`; transform positional `reference_id` → typed `role` (D-01 S2) |
| 3 | `tr_CAS_financial` | **MIGRATE** | → `trx_application_financial_profile` |
| 4 | `tr_CAS_installment` | **MIGRATE** | → `trx_application_other_installment` |
| 5 | `tr_CAS_bank_account` | **MIGRATE** | → `trx_application_bank_account` |
| 6 | `tr_CAS_corporate_document` | **MIGRATE** | → `trx_application_corporate_profile` + kolom akta → `trx_application_corporate_deed` (typed); flag `is_adjustment_deed`/`is_amendment_deed` di-derive (kolom discard, data tidak) |
| 7 | `tr_CAS_corporate_adjustment_deed` | **MIGRATE** | → `trx_application_corporate_deed` (`deed_type='adjustment'`) |
| 8 | `tr_CAS_corporate_amendment_deed` | **MIGRATE** | → `trx_application_corporate_deed` (`deed_type='amendment'`) |
| 9 | `tr_CAS_corporate_owner` | **MIGRATE** | → `trx_application_corporate_owner`; `float` share → `NUMERIC(5,2)` (cast + report Σ≠100%) |
| 10 | `Tr_CAS_photo_detail` | **MIGRATE** | → `trx_application_document`; file → object storage (path rewrite); `is_new_zoom` discard [ARTIFACT] |
| 11 | `tr_CAS_AC_detail` | **MIGRATE** | → `trx_application_document` (dengan `document_no`) |
| 12 | `tr_CAS_AC_header` | **MIGRATE** (owner **02**) | 5C narrative CMO → tabel target BE-02 §3 (hand-off pra-RFA, §1.2); disposisi dicatat di sini karena tabel keluarga `tr_CAS_*` |
| 13 | `tr_CAS_repeat_order` | **MIGRATE** | → `trx_application_repeat_order` |
| 14 | `tr_CAS_payment_point` | **MIGRATE** | → `trx_application_payment_point` |
| 15 | `tr_CAS_LKK_Score` | **MIGRATE-READONLY** | → `trx_application_lkk_score` (arsip); kepanjangan LKK + writer path `[OPEN]` — aktivasi operasional menunggu konfirmasi |
| 16 | `tr_CAS_APUPPT` | **MIGRATE** | → `trx_application_aml_answer` (`[LOCKED]` regulatori) |
| 17 | `tr_CAS_APUPPT_risk` | **MIGRATE** | → `trx_application_aml_risk` (`[LOCKED]`) |
| 18 | `tr_APUPPT_header` | **MIGRATE-READONLY** | Arsip compliance; duplikat vs `tr_CAS_APUPPT` `[OPEN — OQ-CORE-09]` |
| 19 | `tr_APUPPT_detail` | **MIGRATE-READONLY** | Idem OQ-CORE-09; bila versi aktif → dilebur ke `trx_application_aml_answer` |
| 20 | `tr_APUPPT_blacklist_log` | **MIGRATE-READONLY** | → `log_aml_hit` (append-only, retensi regulatori BR-REG-10; kebijakan retensi PDP = OQ-MIG-02) |
| 21 | `tr_PPATK_hit_log` | **MIGRATE-READONLY** | → `log_ppatk_hit` (evidence verbatim `[LOCKED]`) |
| 22 | `tr_blacklist_daily` | **MIGRATE-READONLY** | → `log_blacklist_screening`; hasil baru selalu re-derive live (BR-05/07); anomali lebar `credit_id` 14 → OQ-CORE-10 |
| 23 | `tr_mapping_customer_blacklist` | **MIGRATE** | → `map_customer_blacklist` |
| 24 | `tr_pooling_orders` (66 kol) | **MIGRATE-READONLY** + `[OPEN — OQ-GAP-01]` | Historis → arsip/read-model; `trx_pooling_order` operasional HANYA bila channel dipertahankan (OQ-GAP-01) |
| 25 | `tr_dealer_order_source_header` | **MIGRATE** | → `trx_dealer_order_source` |
| 26 | `tr_dealer_order_source_TAC` | **MIGRATE** | → `trx_dealer_order_source_refund` (`refund_type='tac'`) |
| 27 | `tr_dealer_order_source_third_party` | **MIGRATE** | → `trx_dealer_order_source_refund` (`refund_type='third_party'`) |
| 28 | `CASMobile_mappingfincore` | **REBUILD** (operasional) + **MIGRATE-READONLY** (historis) | `map_moofi_fincore` diisi E13 mulai kosong; 36 kolom legacy utuh ke arsip; writer legacy `[OPEN — OQ-GAP-06]` |
| 29 | `tr_cas_mobile_flag` | **REBUILD** (diserap) | Flag → nilai `origination_channel='moofi'` pada `trx_application` saat transform; tabel TIDAK dibawa |
| 30 | `tr_reverse_CAS_to_mobile` | **MIGRATE-READONLY** | → `log_moofi_reverse`; kelangsungan fitur reverse `[OPEN]` OQ-ACQCAS-07 |
| 31 | `tr_mapping_NIK_RO` | **MIGRATE** | → `map_nik_repeat_order` (+ dedup ke `ux_`); populate path `[OPEN — OQ-GAP-07]` |
| 32 | `tr_auto_number` | **REBUILD** | → DB sequence + `cfg_number_format`; seed dari `max()` legacy per scope (ADR-08; OQ-CORE-11) |
| 33 | `tr_generate_code` | **REBUILD** | Idem — dikonsolidasi satu minting service (BR-33) |
| 34 | `tr_generate_code_history` | **MIGRATE-READONLY** | → `log_number_generation` |
| 35 | `temppotonganro` | **DISCARD** | `[ARTIFACT — dead/vestigial]` 0 referensi di 473 SP + dump body + kode .NET (gap-entities §3; konfirmasi stakeholder per OQ-GAP-09) |

---

## 4. API Endpoint

Kontrak ditulis level resource+field (transport final `[OPEN]` per D-11/D-12 — REST diilustrasikan; implementasi Java, framework rekomendasi Spring Boot = USULAN). Path/verb bersifat ilustratif resource-shape, bukan penguncian framework.

| # | Method | Path | Deskripsi | Auth/Role (D-10) |
|---|---|---|---|---|
| E1 | POST | `/credit-applications` | Create CAS header + identity + related-persons + financial profile; `product_line` sebagai discriminator (satu resource, bukan insert/motor vs insert/car). Dedup-by-NIK + dedup lock (D-01 S1). Channel web. | CMO / Marketing Head |
| E2 | PATCH | `/credit-applications/{id}` | Update draft (identity/financial profile/related-persons) selama status `draft`/`corrected`. | CMO / Marketing Head |
| E3 | GET | `/credit-applications/{id}` | Baca aplikasi + child records; flag AML ditandai "as declared at intake" bila RFA belum jalan. | Branch roles (read) / Committee (read) |
| E4 | PUT | `/credit-applications/{id}/asset-financials` | Capture/refresh asset + struktur finansial draft (OTR, DP, tenor, dealer, fee, rate). Seed/refresh `CREDIT_MEMO` draft. Idempotent (insert-if-absent-else-update by application). | CMO / Marketing Head |
| E5 | POST | `/credit-applications/{id}/documents` | Upload dokumen/foto keyed photo-type (vocabulary `[LOCKED]` BR-CASFE-17); validasi ekstensi `.jpg/.jpeg/.png/.pdf` + size limit server-side (BR-37). | CMO / Credit (Admin) |
| E6 | POST | `/screening/blacklist` | Screening blacklist entry-time (broad match deterministik; register internal + employee cross-check). Server-authoritative. Dipanggil utk applicant / spouse / guarantor (3 titik cek FE, BR-CASFE-7). | CMO / Marketing Head |
| E7 | POST | `/screening/aml-questionnaire` | Screening APU-PPT/DTTOT entry-time (broad match + red-zone + profession + AML risk score) + audit-log hit. Otomatis pada first data entry (D-01 S5). | CMO / Marketing Head |
| E8 | POST | `/credit-applications/{id}/rfa` | **RFA lock (aksi terminal, KEPEMILIKAN 01; D-01 S6).** Jalankan gate underwriting penuh atomik; sukses → `rfa_locked`, emit `ApplicationLocked`. **Idempotency-Key WAJIB** ("idempotent RFA lock", D-01 S6). | **Credit (Admin)** |
| E9 | POST | `/credit-applications/{id}/cancel` | Batalkan draft (→ `cancelled`). `[KEPUTUSAN DESAIN BARU]` — tidak ada path live di legacy (OQ-ACQCAS-09; FE Edge Case 2 mengonfirmasi section pembatalan di layar pun dead/commented-out). | Credit (Admin) |
| E10 | POST | `/credit-applications/{id}/reopen` | Re-open dari `corrected` untuk re-submit RFA; memicu **re-screen RAC idempotent** (event), BUKAN destructive delete (fix GOTCHA-11). | Credit (Admin) |
| E11 | POST | `/credit-applications/{id}/return-for-correction` | **Disposisi STEP 9 Correction** (BARU vs baseline): dari `rfa_locked` → `corrected`; file kembali ke Step 1–7 untuk perbaikan CMO. Sumber: GT v2 STEP 9 `[VERIFIED — doc]`. | Credit (Admin) |
| E12 | POST | `/credit-applications/{id}/reject` | **Disposisi STEP 9 Reject** (BARU vs baseline): dari `rfa_locked` → `rejected`; proses berhenti. TANPA auto-close entity (BR-28/OQ-AC-01). Sumber: GT v2 STEP 9 `[VERIFIED — doc]`. | Credit (Admin) |
| E13 | POST | `/sync/moofi-applications` | **STEP 8 ingestion MOOFI→FINCORE** (BARU vs baseline): validasi payload (kontrak `sp_validation_mobile_to_fincore`), **mint `credit_id`**, bentuk draft kontrak skeleton **status RFA='0'** (`rfa_locked`), pindahkan foto Mobile→Fincore, emit `ApplicationLocked`. Idempotent by `moofi_reference_id`. Sumber: GT v2 STEP 8 `[VERIFIED — doc]`. | System (MOOFI service account, via ACL) |

> Hand-off (BUKAN owned): entry 5C CMO narrative diteruskan ke **02-credit-analysis**; RFA tidak hard-gate padanya. Kebutuhan lookup FE (location, bank, agreement-old paged lookup, gender-by-NIK, MaxKol, order-id by credit source — `61-intake-cas-screens.md §11`) dilayani sebagai read-only reference endpoints dari modul masters (D-08), bukan didefinisikan ulang di sini.

---

## 5. Kontrak Request/Response

Error envelope seragam di semua boundary: `{ code, message, details?, correlation_id }` (umbrella §7.3). Regulated gate = **fail-closed** default.

### 5.1 E1 — POST /credit-applications (create CAS)

Request (field wajib ditandai `*`):

```json
{
  "product_line": "car",                         // * enum car|motor
  "finance_scheme": "conventional_CF",           // * enum conventional_CF|syariah_US
  "application_type_id": "02",                    // * kode [LOCKED] BR-CASFE-18; hanya 02/03 ter-exercise
  "customer": {
    "customer_kind": "individual",               // * individual|corporate
    "national_id": "3275xxxxxxxxxxxx",           // * NIK [LOCKED] format/validasi
    "tax_id": "09.xxx.xxx.x-xxx.xxx",            //   NPWP [LOCKED]
    "full_name": "…",                            // *
    "birth_date": "1990-01-01", "birth_place": "…",
    "address": "…", "occupation": "…",
    "ojk_economic_sector": "…"                   //   [LOCKED] OJK code
  },
  "related_persons": [
    { "role": "spouse",    "name": "…", "relationship": "…" },
    { "role": "guarantor", "name": "…", "relationship": "…" },
    { "role": "reference", "name": "…", "relationship": "…" },
    { "role": "reference", "name": "…", "relationship": "…" }
  ],
  "corporate_profile": { "...": "wajib bila customer_kind=corporate; owners[] dgn ownership_share_pct" },
  "financial_profile": { "primary_income": 0, "other_income": 0, "household_expenses": 0, "monthly_other_installment": 0 }
}
```

Validasi server-side WAJIB (tidak boleh hanya client-side — fix gap FE OQ-CASFE-05): kardinalitas role (spouse/guarantor maks 1), jumlah reference 2/3 by MaxKol (BR-36), total `ownership_share_pct` corporate owners = **tepat 100%** (BR-35), format identitas officer (KTP=16 digit; NPWP=15–16) (BR-35).

Response `201 Created`:

```json
{
  "id": "…",                                     // credit_id — format legacy branch(5)+YY+MM+SEQ(5) (OQ-GT-02 resolved, §3.1.13)
  "customer_id": "CUST-000123",                  // linked/deduped by NIK (D-01 S1)
  "customer_dedup": "matched_existing",          // matched_existing|created_new
  "status": "draft",
  "created_at": "2026-07-14T02:00:00Z"
}
```

Status: `201` sukses; `422` validasi (role invalid, NIK format, ownership ≠ 100%, refs kurang); `409` konflik dedup non-resolvable / `DUPLICATE_INFLIGHT_APPLICATION` (dedup lock, default USULAN — OQ-PRD01-01).

### 5.2 E4 — PUT /credit-applications/{id}/asset-financials

```json
{
  "asset": { "chassis_no": "MHxxxxxxxxxxxxxxx", "engine_no": "xxxxxxxxxx",
             "brand": "…", "type": "…", "model_year": 2022, "ownership_proof": "…" },
  "dealer_code": "DLR-001", "subdealer_code": "SUB-001",
  "otr_price": 250000000,                         // * [LOCKED] source
  "down_payment": 50000000,                       // * nett/clean DP
  "tenor_months": 36,                             // *
  "admin_fee": 1000000, "insurance_fee": 5000000,
  "effective_rate": 0.18, "flat_rate": 0.10, "amount_installment": 7000000,
  "umc_fields": null,                             // hanya jika application_type_id='03'
  "subsidies": { "finance": 0, "dealer": 0, "atpm": 0, "third_party": 0, "interest": 0 }
}
```

Response `200 OK`: `{ "id":"…","credit_memo_status":"draft" }`. Menjamin **satu row `CREDIT_MEMO` terisi penuh** sebelum RFA (fix asimetri car/motor Edge Case 4).

### 5.3 E6 / E7 — Screening (server-authoritative, broad match)

Request E7:

```json
{ "national_id":"…", "name":"…", "birth_date":"…", "birth_place":"…",
  "address":"…", "occupation":"…", "customer_kind":"individual",
  "subject_role":"applicant" }                    // applicant|spouse|guarantor (3 titik cek, BR-CASFE-7)
```

Response `200 OK`:

```json
{
  "hit": true,
  "matched_on": ["national_id"],                 // broad: any single ID OR (name AND birth_date)
  "reason_code": "DTTOT", "reason_desc": "…",
  "red_zone": false, "high_risk_profession": true,
  "aml_risk_score": 2.7, "aml_risk_tier": "High", // Low<=1.5 | 1.5<Medium<=2.5 | High>2.5
  "audit_log_id": "AML-LOG-88",
  "correlation_id": "…"
}
```

Bila core screening error mid-check → **fail-closed**: `503` + `{ "code":"SCREENING_UNAVAILABLE", ... }`; aplikasi TIDAK boleh lolos sebagai "clean" (OQ-REG-06 mengonfirmasi kebijakan). Narrow exact-match variant **TIDAK di-port** (GOTCHA-1). Catatan FE cross-check: legacy web hanya memanggil endpoint narrow (`validasi/checkblacklistktp`, BR-CASFE-7 `[LOCKED]` sebagai fakta traceability) dan hasil screening TIDAK pernah memblokir tombol apa pun di client (FE Edge Case 1) — rebuild menjadikan screening **server-authoritative**, dan hard-block bila diinginkan adalah kapabilitas BARU yang harus diputuskan, bukan preserved behavior.

### 5.4 E8 — POST /credit-applications/{id}/rfa (RFA lock — aksi terminal, D-01 S6)

Header WAJIB: `Idempotency-Key: <uuid>`. Request:

```json
{ "acting_employee_id": "EMP-0007", "target_status": "rfa_locked" }
```

Response `200 OK` (semua gate lulus, atomik):

```json
{
  "id": "…",
  "status": "rfa_locked",                         // legacy status_approval='0'
  "credit_memo_status": "draft",
  "risk_escalation_signals": {                    // KONTEKS; di-re-qualify OTORITATIF di 03, bukan trans_type_id final
    "effective_rate_below_min": true,
    "aggregate_exposure_op": 40000000,
    "instant_approval_cohort": false              // IA lane = policy flag auditable; eligibility [OPEN] OQ-MEET-04
  },
  "event_emitted": "ApplicationLocked",
  "correlation_id": "…"
}
```

Response gate gagal `422` — **tanpa perubahan state/data** (draft tetap editable):

```json
{
  "code": "RFA_GATE_FAILED",
  "message": "Net down payment must be > 0",
  "details": [ { "rule": "BR-ACQCAS-11", "field": "down_payment" } ],
  "correlation_id": "…"
}
```

Response guard status `409`: `{ "code":"RFA_INVALID_STATE","message":"Current status 'rfa_locked' is not Draft/Correction" }`. Re-lock dengan Idempotency-Key sama → `200` idempotent (tanpa efek ganda).

### 5.5 E13 — POST /sync/moofi-applications (STEP 8 ingestion — BARU)

Idempotent by `moofi_reference_id` (retry MOOFI tidak boleh mem-mint `credit_id` kedua). Request (shape ilustratif; field census final mengikuti kontrak validasi `sp_validation_mobile_to_fincore` — dibaca saat detail engineering):

```json
{
  "moofi_reference_id": "MOOFI-2026-77123",       // * kunci idempotency
  "product_line": "motor", "finance_scheme": "conventional_CF",
  "applicant": { "national_id": "…", "full_name": "…", "birth_date": "…", "...": "identity + kapasitas bayar" },
  "guarantor": { "...": "data pribadi penjamin (GT STEP 8b)" },
  "loan_structure": { "otr_price": 0, "down_payment": 0, "tenor_months": 0, "...": "struktur pinjaman" },
  "photos": [ { "photo_type": "KTP", "file_ref": "moofi://…" } ],
  "moofi_rfa_context": { "locked_at": "…", "locked_by": "…" }   // konteks RFA Lock Initiation MOOFI (D-01 S6)
}
```

Response `201 Created` (validasi lulus):

```json
{
  "credit_id": "…",                               // minted; nationally unique (OQ-GT-02)
  "status": "rfa_locked",                          // draft kontrak skeleton "Status RFA = '0'" (GT STEP 8)
  "customer_dedup": "matched_existing",            // dedup-by-NIK juga berlaku di jalur sync (D-01 S1)
  "photos_moved": true,
  "event_emitted": "ApplicationLocked",            // emitter tunggal di FINCORE 01 — USULAN, lihat OQ-PRD01-02
  "correlation_id": "…"
}
```

Response validasi gagal `422`: `{ "code":"MOOFI_SYNC_VALIDATION_FAILED", "details":[{ "field":"…", "rule":"…" }] }` — **tidak ada** `credit_id` di-mint, tidak ada record terbentuk; `MOOFI_SYNC_RECORD.validation_status=failed` dicatat untuk audit. Retry `moofi_reference_id` sama setelah sukses → `200` idempotent dengan `credit_id` yang sama.

---

## 6. Aturan Bisnis

| ID | Aturan | Sumber KB | Marker | Catatan (perilaku rebuild) |
|---|---|---|---|---|
| BR-01 | `product_line` (car/motor) adalah hard branch external-master-referenced, BUKAN atribut display. | `20-...§7 BR-ACQCAS-1` | `[LOCKED]` | Satu engine config-driven; divergensi gate = konfigurasi per-line (GOTCHA-10), bukan dua code-path. Parameterisasi per-product MACF wajib didefinisikan (D-07; OQ-MEET-06). |
| BR-02 | Tepat SATU row `CREDIT_MEMO` terisi penuh harus ada sebelum RFA. | `20-...§7 BR-ACQCAS-3,4; §9 EC4` | `[INTENT]` | Jangan replikasi asimetri stub-car vs insert-motor; jamin invariant "CM draft lengkap sebelum dokumen/RFA". |
| BR-03 | Struktur finansial (OTR/DP/tenor/dealer/fee/rate) di-capture via step terpisah, insert-if-absent-else-update by application. | `20-...§7 BR-ACQCAS-4`; FE BR-CASFE-12 | `[INTENT]` | Wizard dua-langkah asli di backend; FE mengonfirmasi CAS-save tidak membawa field finansial. |
| BR-04 | Screening blacklist entry-time = **broad match deterministik** (any single ID OR name+birth); narrow exact-match-all-fields TIDAK di-port. | `20-...§7 BR-ACQCAS-5,6; regulatory-rules §7 BR-REG-3,4,5; GOTCHA-1` | `[INTENT]` (fix) | Satu screening service; tutup coverage gap. Keputusan desain **RESOLVED → broad** (BE-00 §11, OQ-ACQCAS-01 ✅); arkeologi varian produksi legacy kini **RESOLVED — evidence** (§11 OQ-ACQCAS-01/02): web produksi terbukti HANYA memanggil varian narrow (`CASController.cs:641` → `TrCasServices.cs:1029` GET `validasi/checkblacklistktp` → `sp_check_blacklist`, single-key `LesseeID`); varian broad `*_test_ilyas` ter-expose tanpa satu pun caller in-repo (eksperimental); mobile sync pakai query inline Pelsus-only (`sp_validation_mobile_to_fincore:1641-1709`). Implikasi migrasi: data legacy hanya pernah di-screen narrow → wajib re-screen broad saat cutover. |
| BR-05 | Screening dibaca dari sumber **live**, bukan staging replica; hindari lag terhadap register terbaru. | `20-...§7 BR-ACQCAS-7` | `[INTENT]` (fix) | Legacy broad variant baca `MACF-DBSTG` replica. |
| BR-06 | Kegagalan mid-screening = **fail-closed** (block), untuk SEMUA regulated gate (AML/blacklist). | `hidden-gotchas GOTCHA-2; regulatory-rules §9 EC3, OQ-REG-06` | `[OPEN]→fail-closed` | Kebijakan default rebuild; konfirmasi OQ-REG-06 (§11). |
| BR-07 | `is_blacklist`/`is_apuppt` di header di-**re-derive server-side** saat RFA (live), tidak dipercaya dari payload client. | `20-...§7 BR-ACQCAS-9; §9 EC7`; FE EC1 | `[INTENT]` (fix) | Stored flag = display/audit "as declared at intake". FE mengonfirmasi client tidak pernah mem-block apa pun atas hasil screening. |
| BR-08 | AML risk score dihitung per aplikasi → tier Low(≤1.5)/Medium(1.5–2.5)/High(>2.5); bobot & threshold dari weight-reference table (tunable). | `regulatory-rules §7 BR-REG-11,12` | `[LOCKED]` metodologi / `[INTENT]` threshold | Jangan port duplicated-conditional individual/corporate (EC7 regulatory). |
| BR-09 | Visibilitas flag AML "High" dibatasi ke allow-list position-code (segregation of duties). | `regulatory-rules §7 BR-REG-13` | `[LOCKED]` | Enforce di authz layer; pemetaan ke sensus role D-10 `[OPEN]` (role HO?). |
| BR-10 | Setiap AML/watchlist hit ditulis append-only ke audit log (name, ID, birth, watchlist ref code, branch, officer). | `regulatory-rules §7 BR-REG-10; 20-...§6` | `[LOCKED]` | Target: `log_aml_hit` (← `tr_APUPPT_blacklist_log`, §3.1.8). |
| BR-11 | Red-zone geografi & high-risk-profession di-flag independen; individual/flagged wajib isi kuesioner EDD. | `regulatory-rules §7 BR-REG-7,8,9` | `[LOCKED]` | Branch tagging `APUPPTListMaster` yang dinonaktifkan HARUS di-restore atau di-retire formal (BR-ACQCAS-8, EC6) — jangan bawa dead branch diam-diam. |
| BR-12 | Related-person = **typed rows** dengan strict role structure & validasi per-role; positional "magic number" dibuang. | `20-...§2; 10-cust §7 BR-CUSTMASTER-6,7; GOTCHA-17`; **D-01 Step 2** | `[INTENT]` (dimandatkan meeting) | Validasi range/uniqueness per role (legacy hanya spouse yang di-force). Kini keputusan meeting, bukan sekadar rekomendasi KB. |
| BR-13 | Dedup `CUSTOMER` by NIK + **deduplication lock** saat first capture; reuse master, bukan re-capture buta. Penulisan otoritatif `tr_CIF` tetap milik 05. | `data-mutation-policy §Customer; GOTCHA-16`; **D-01 Step 1/15** | `[INTENT]` (dimandatkan meeting) | Lihat §3.3. Semantik lock `[OPEN]` OQ-PRD01-01. Berlaku di KEDUA channel (web E1 & sync E13). |
| BR-14 | **RFA hanya diizinkan** bila status memo = Draft (`D`) atau Correction (`C`); status lain ditolak dengan pesan menyebut status saat ini. | `20-...§7 BR-ACQCAS-10` | `[LOCKED]` | Cegah re-submit/duplikasi file in-flight/selesai. |
| BR-15 | RFA mensyaratkan `nett_down_payment` (DP bersih setelah subsidi) **> 0**. | `20-...§7 BR-ACQCAS-11` | `[LOCKED]` | DP ≤ 0 = struktur finansial invalid. |
| BR-16 | (car) Usia kendaraan saat jatuh tempo ≤ 18 th (8 th untuk double-cabin), hanya used/UMC. Motor tak punya cek ini. | `20-...§7 BR-ACQCAS-12` | `[LOCKED]` cap / `[OPEN]` scope | Extend ke motor? OQ-ACQCAS-03 — masuk matriks per-product D-07/OQ-MEET-06. |
| BR-17 | (car) Usia borrower saat jatuh tempo ≤ 65 th. Motor tak punya cek ini. Superseded profession-tiered age (dead) dibuang. | `20-...§7 BR-ACQCAS-13; regulatory-rules BR-REG-23; GOTCHA-20` | `[INTENT]` (outcome 65 dijaga) | Extend ke motor? OQ-ACQCAS-03. |
| BR-18 | (car) DP ≥ 15% OTR (Investment) / 20% (Multi-purpose), kecuali deviasi tercatat. Motor tak punya cek ini. | `20-...§7 BR-ACQCAS-14` | `[INTENT]` | OQ-ACQCAS-03. |
| BR-19 | Repeat-Order/top-up: old contract harus ada & (top-up) active; tak lintas company; ≥50% angsuran terbayar; **(motor only)** identitas old-contract cocok applicant. | `20-...§7 BR-ACQCAS-15` | `[INTENT]` | Asimetri identity-match car vs motor → OQ-ACQCAS-04-adjacent. |
| BR-20 | Repeat-Order decision-tier dihitung dari worst-overdue + riwayat angsuran; **formula car (%tenor) ≠ motor (count)**. | `20-...§7 BR-ACQCAS-16; GOTCHA-10` | `[INTENT]` | Jaga outcome per channel; unify? OQ-ACQCAS-04. |
| BR-21 | RFA re-screen blacklist reason-code: **car 5 reason** (Pelsus/repossession/write-off/90+ overdue/prior CM reject), masing-masing bypass via override table; **motor 1 reason**. | `20-...§7 BR-ACQCAS-17; GOTCHA-10` | `[LOCKED]` semantik / `[INTENT]` scope | Extend ke motor? OQ-ACQCAS-05. |
| BR-22 | Admin fee: UMC (`'03'`) ≥ minimum tenor-banded dari fee-schedule master; standard (`'02'`) cukup non-zero. | `20-...§7 BR-ACQCAS-18` | `[INTENT]` | Schedule = data (master table), bukan logic. |
| BR-23 | Effective rate < market-min → **risk-tier digit di-scale-up** (kecuali sudah top tier), bukan reject langsung. | `20-...§7 BR-ACQCAS-19` | `[INTENT]` | Under-priced deal dirutekan ke tier scrutiny lebih tinggi. **Sinyal dibawa sebagai konteks & di-re-qualify OTORITATIF di 03** saat routing; komposisi `trans_type_id` milik 02 (D-01 S8), bukan 01. |
| BR-24 | Aggregate-exposure "OP" (applicant+spouse) & Instant-Approval trial-cohort dapat memaksa risk tier tertinggi / reshape approver chain. Mekanisme+hierarki **milik 03** (routing dinamis by trans_type_id + Plafond OP + risk level, D-01 S10; self-approval BLOCKED + IA lane auditable, D-01 S11). | `20-...§7 BR-ACQCAS-20; GOTCHA-4,9`; D-01 S10/S11 | `[INTENT]` cross-domain | 01 hanya menyuplai sinyal via `ApplicationLocked`. Threshold OP 35jt vs 30jt `[OPEN]` OQ-AC-02; eligibility IA lane `[OPEN]` OQ-MEET-04. |
| BR-25 | RFA sukses = SATU transaksi atomik (semua cek + status write); gagal → pesan error, **nol perubahan**. | `20-...§7 BR-ACQCAS-21` | `[INTENT]` | Lock all-or-nothing dari sisi caller. Seed hierarki komite BUKAN bagian transaksi RFA (di-key `trans_type_id`, dibangun downstream). |
| BR-26 | Re-lock RFA idempotent (Idempotency-Key, D-01 S6); re-open memicu **re-screen RAC idempotent (event)**, bukan destructive delete. | umbrella §7.4; `GOTCHA-11`; D-01 S6 | `[INTENT]` (dimandatkan meeting) | Legacy `sp_trans_open_cm:53-78` menghapus record RAC Bank Mega. |
| BR-27 | RFA sukses meng-emit `ApplicationLocked` (membawa risk-escalation signals + product_line + finance_scheme). | umbrella §3/§5; GT STEP 9; **D-01 Step 6** | `[INTENT]` (dimandatkan meeting) | Konsumen: 02 (RAC risk-gating via ACL, D-01 S7). Emitter tunggal = FINCORE 01 utk kedua channel (USULAN; OQ-PRD01-02). |
| BR-28 | Reject TIDAK auto-close aplikasi (legacy menutup `TrCas`/`TrCm`). Berlaku utk reject STEP 9 (E12, milik 01) maupun reject komite (aksi 03). | umbrella §3 non-goal; `20-...`; GT STEP 9 | `[OPEN]` OQ-AC-01 | State `rejected` mendarat di entity 01, tanpa side-effect closure. |
| BR-29 | Blacklist override/whitelist table (dipakai reason-gate RFA) tidak punya CRUD di codebase; butuh admin screen. | `20-...§7 BR-ACQCAS-22; §9 EC10` | `[OPEN]` OQ-ACQCAS-08 | Kandidat masuk Menu Master D-08; konfirmasi cara maintain hari ini. |
| BR-30 | Dead SP `sp_insert_cas`/`sp_insert_tr_cas`/`sp_insert_cas_with_table_type`/`sp_update_cas`/`sp_delete_cas` (target `tr_credit_analyst`) **tidak di-port**. | `20-...§7 BR-ACQCAS-24; §9 EC3; GOTCHA-18` | `[ARTIFACT]` | Live path = EF-direct write + `sp_insert_cm`/`_car`. |
| BR-31 | Hindari pola sync-over-async (`.Result[0]` blocking) pada question-flag APU-PPT; gunakan concurrency idiomatik target stack (Java: jangan block reactive/async pipeline). | `20-...§9 EC6` | `[ARTIFACT]` (fix) | Cegah thread-pool starvation. |
| BR-32 | **STEP 8 ingestion**: payload MOOFI divalidasi (kontrak `sp_validation_mobile_to_fincore`) SEBELUM minting; gagal validasi → tolak utuh, **tidak ada** `credit_id`/record terbentuk; hasil dicatat di `MOOFI_SYNC_RECORD` (audit). Ingestion idempotent by `moofi_reference_id`. | GT v2 STEP 8 `[VERIFIED — doc]`; legacy `spSyncPoolOrderToCAS` (`20-...§3a S1-mobile`) | `[INTENT]` | Menggantikan cross-database `INSERT...SELECT` legacy tanpa caller ter-evidensi (EC8) dengan boundary API/event eksplisit via ACL. |
| BR-33 | **`credit_id` = nomor kontrak unik secara nasional**, di-mint sistem (bukan input user); menjadi PK aplikasi/kontrak lintas modul. | GT v2 STEP 8 `[VERIFIED — doc]` | `[LOCKED]` semantik unik / format **RESOLVED — evidence** (OQ-GT-02, spec §3.1.13) | Satu minting service; jalur web (E1) dan sync (E13) memakai generator yang sama. |
| BR-34 | Draft kontrak hasil STEP 8 lahir pada **Status RFA = '0'** (`rfa_locked`) — bukan `draft`. Gate underwriting utk jalur MOOFI berjalan sebagai bagian validasi ingestion + re-derive screening live (BR-07). | GT v2 STEP 8 `[VERIFIED — doc]` | `[INTENT]` | Konsekuensi state machine: entry-edge `(∅)→rfa_locked` utk channel moofi (§7). Kedalaman gate yang diulang di FINCORE vs dipercaya dari MOOFI `[OPEN]` OQ-PRD01-02. |
| BR-35 | Validasi corporate ownership: total `ownership_share_pct` owners = **tepat 100%**; format identitas officer KTP=16 digit / NPWP=15–16 — WAJIB di-enforce **server-side** (legacy hanya client-side, bisa di-bypass direct API call). | FE `61-...§7 BR-CASFE-5,6`; OQ-CASFE-05 | `[INTENT]` (fix gap) | BE = source of truth validasi; FE hanya UX. |
| BR-36 | Jumlah personal-reference wajib = 2 atau 3, diturunkan dari kedalaman kolektibilitas biro (MaxKol ≤3 → 2; >3 → 3) — di-enforce server-side. | FE `61-...§7 BR-CASFE-9` | `[INTENT]` | Data biro dimiliki 02/SLIK domain; 01 hanya mengonsumsi angka MaxKol via read. |
| BR-37 | Upload dokumen: ekstensi hanya `.jpg/.jpeg/.png/.pdf` + size limit terkonfigurasi, divalidasi per file **server-side**; kegagalan penyimpanan file WAJIB surface ke caller (jangan replikasi silent catch legacy). | FE `61-...§7 BR-CASFE-14`; FE EC5 | `[INTENT]` (fix) | Photo-type vocabulary `[LOCKED]` BR-CASFE-17. |
| BR-38 | Authz modul: role census D-10 (`[LOCKED]`), TANPA super-user (D-09 `[LOCKED]`); konteks lintas layar (credit_id, mode) WAJIB dibawa eksplisit per request (stateless), BUKAN session server-side affinity. | D-09; D-10; FE `61-...§9 EC11` | `[LOCKED]` role / `[INTENT]` stateless | Legacy bergantung server session (Flag/IsEdit/credit_id) — tidak kompatibel dgn rebuild API-driven. |

---

## 7. State Machine

Entity yang dimiliki: `CREDIT_APPLICATION.status` (kolom `trx_application.status`, §3.1.1) = `draft | rfa_locked | risk_gated | analyzing | committee | approved | rejected | corrected | cancelled` (kanonik umbrella). `CREDIT_MEMO.status` (`trx_credit_memo`, BE-04; `draft|finalized|approved|corrected`) di-seed di sini tetap `draft`. Riwayat transisi → tabel `log_` (append-only, DB-CONVENTIONS §5), bukan kolom `last_*`.

> **Representasi "Status 0"** `[KEPUTUSAN DESAIN BARU]`: RFA/sync memetakan `CREDIT_APPLICATION.status → rfa_locked`, sementara `CREDIT_MEMO` yang di-seed tetap `draft`. Apakah "Status 0" flow-doc merujuk header parent atau memo sendiri = `[OPEN]` OQ-CMPO-01. GT v2 STEP 8 menyebut "Status RFA = '0'" pada draft kontrak skeleton — konsisten dengan pemetaan header-level di atas.
> **Vocabulary legacy** `D/C/0/V/R/A` (`sp_rfa_cm:40-46`) `[LOCKED]` sebagai referensi semantik; konflik FE `R`=REJECT vs BE `R`=Review (OQ-CASFE-10) diselesaikan dengan TIDAK mem-port huruf legacy — enum kanonik di atas adalah satu-satunya vocabulary rebuild.

01 memiliki state sampai `rfa_locked` + disposisi STEP 9 (`corrected`/`rejected` level cabang). State `risk_gated`/`analyzing`/`committee`/`approved` dan `rejected`/`corrected` level komite ditulis kapabilitas sibling (02/03) tetapi mendarat pada entity milik 01 — ditampilkan sebagai inbound/outbound edge.

| Dari | Aksi | Ke | Guard / Prasyarat |
|---|---|---|---|
| `(∅)` | POST create (E1, channel web) | `draft` | Identity valid; dedup-by-NIK + dedup lock resolved (BR-13) |
| `(∅)` | POST sync MOOFI (E13, channel moofi — **STEP 8**) | `rfa_locked` | Validasi `sp_validation_mobile_to_fincore` lulus; `credit_id` minted (BR-32/33/34); dedup-by-NIK; foto moved; emit `ApplicationLocked` |
| `draft` | PATCH / asset-financials / documents | `draft` | Screening entry-time boleh dipanggil kapan pun (cross-cutting, bukan stage; otomatis pada first data entry per D-01 S5) |
| `draft` | POST rfa (E8) — **semua gate lulus** | `rfa_locked` | BR-14 (status Draft/Correction), BR-15 (DP>0), BR-16..23 gate underwriting, BR-06 fail-closed, BR-07 re-derive; atomik (BR-25); emit `ApplicationLocked` (BR-27; D-01 S6) |
| `draft` | POST rfa — **gate gagal** | `draft` | `422` `RFA_GATE_FAILED`; **nol perubahan** state/data (BR-25) |
| `draft`/`corrected` | POST rfa — status guard gagal | (unchanged) | `409` bila status ≠ Draft/Correction (BR-14) |
| `draft` | POST cancel (E9) | `cancelled` | `[KEPUTUSAN DESAIN BARU]` (OQ-ACQCAS-09; FE EC2 mengonfirmasi tak ada path live legacy) |
| `rfa_locked` | POST rfa (Idempotency-Key sama) | `rfa_locked` | Idempotent, tanpa efek ganda (BR-26; D-01 S6) |
| `rfa_locked` | POST return-for-correction (E11 — **STEP 9 Correction**) | `corrected` | Credit (Admin); file kembali ke Step 1–7 utk perbaikan CMO (GT STEP 9). Non-destruktif (BR-26) |
| `rfa_locked` | POST reject (E12 — **STEP 9 Reject**) | `rejected` | Credit (Admin); proses berhenti; **tanpa** auto-close (BR-28) |
| `rfa_locked` | Hand-off → 02 (out of scope) | `risk_gated` | RAC risk-gating STEP 10 via ACL (downstream; inbound edge dari sisi 01) |
| `committee` | Aksi 03: correction (STEP 12) | `corrected` | Ditulis 03; entity milik 01; return-target Step 1–7 (GT STEP 12) |
| `corrected` | POST reopen (E10) + re-submit RFA | `rfa_locked` | Re-screen RAC idempotent event, bukan destructive delete (BR-26) |
| `committee` | Aksi 03: reject (STEP 12, permanent) | `rejected` | **Tidak** auto-close (BR-28, OQ-AC-01) |
| `committee` | Aksi 03: approve (STEP 12, `'A'`) | `approved` | Ditulis 03; lock finansial OP/ULI/LCR + insurance terjadi di sana (GT STEP 12), bukan di 01 |

Non-happy-path tercakup: gate gagal (no-op), status-guard (`409`), screening fail-closed (`503`), sync validation gagal (no-op + audit), cancel (NEW), reject-tanpa-closure (cabang & komite), re-lock idempotent, re-open re-screen, sync retry idempotent.

---

## 8. Integrasi Eksternal

Semua akses eksternal WAJIB lewat **Anti-Corruption Layer (ACL)** (D-01 S7 menyebut ACL eksplisit untuk RAC; kebijakan diperluas ke semua seam); hapus anti-pattern legacy (cross-DB linked-server DML, outbound HTTP dari dalam T-SQL `sp_OACreate`, baca staging replica untuk regulated gate, cross-database `INSERT...SELECT` sync).

| Seam | Arah | Sync/Async | Owner | Peran di 01 |
|---|---|---|---|---|
| **MOOFI (upstream origination — FCL, ACQ, Credit Scoring, RFA)** | inbound (STEP 8 sync) | async/batch — mode final `[OPEN]` (event vs API vs batch; tergantung arsitektur ITEC D-11) | **01 (sisi penerima)** | E13 ingestion: validasi (`sp_validation_mobile_to_fincore` sbg kontrak aturan), minting `credit_id`, draft RFA='0', pemindahan foto. GT v2 STEP 8 `[VERIFIED — doc]`. Legacy sync SPs tanpa caller (OQ-ACQCAS-06) digantikan boundary eksplisit. |
| **Blacklist / AML screening** (register internal + watchlist nasional DTTOT/APU-PPT + red-zone + high-risk-profession masters + AML score) | outbound (read) + audit-log write | sync | 01 (entry-time) | Gate entry-time (E6/E7), broad match deterministik, fail-closed, live source (BR-04/05/06). |
| **Masters CUSTOMER** (`tr_CIF`/customer master) | inbound (read) | sync | Penulisan otoritatif 05 (D-01 S15) | Dedup-by-NIK + dedup lock di intake (BR-13, §3.3), read-only. |
| **Masters DEALER** (`11-dealer-partner-master.md`) | inbound (read) | sync | references / Menu Master D-08 | Pilih dealer/subdealer/surveyor/CMO di asset step. |
| **Masters PRODUCT/ASSET** (`12-product-asset-master.md`) | inbound (read) | sync | references / Menu Master D-08 | Pilih kind/class/brand/type/year + application-type catalog. |
| **Biro/SLIK (MaxKol)** | inbound (read) | sync | 02 / SLIK domain | Menentukan jumlah reference wajib 2/3 (BR-36); 01 read-only. |
| **RAC Bank Mega (risk engine)** | outbound hand-off | async (callback ingestion ASYNC, D-01 S8) | **02** | 01 hanya **emit `ApplicationLocked`** saat RFA/sync; RAC request via ACL + callback ingest dimiliki 02 (D-01 S7/S8; GT STEP 10 external seam — `sp_insert_rac_processing*` tidak ada di dump lokal). 01 tidak memanggil RAC langsung. |
| **External masters & linked servers** (`FC_MSTAPP_MCF`) | inbound (read) | sync | references (Phase 1) | Kompatibilitas external-FK (`trans_type_id` char-for-char) — komposisi milik 02. `[OPEN]` OQ-EXTMASTERS-01. |
| **Arsitektur infra (ITEC Bank Mega)** | — | — | eksternal (D-11) | Topologi final (message bus, deployment, DB) menunggu deliverable ITEC; kontrak dokumen ini framework/transport-agnostic. |

---

## 9. Acceptance Criteria

**AC-1 (happy path RFA lock — web)**
Given aplikasi car status `draft` dengan CM draft terisi penuh, DP bersih > 0, semua gate underwriting (usia kendaraan/borrower/DP%/repeat-order/blacklist re-screen/admin-fee/eff-rate) lulus, dan screening live tidak error,
When Credit (Admin) POST `/credit-applications/{id}/rfa` dengan `Idempotency-Key`,
Then dalam satu transaksi atomik status → `rfa_locked`, event `ApplicationLocked` (membawa `risk_escalation_signals` + `product_line` + `finance_scheme`) di-emit, response `200`.

**AC-2 (gate gagal = no-op)**
Given aplikasi dengan `down_payment = 0`,
When POST rfa,
Then response `422 RFA_GATE_FAILED` merujuk `BR-ACQCAS-11`, dan **tidak ada** perubahan state/data (draft tetap editable).

**AC-3 (status guard)**
Given aplikasi sudah `rfa_locked` (bukan Idempotency-Key retry),
When POST rfa dengan key baru,
Then response `409 RFA_INVALID_STATE` menyebut status saat ini; tanpa perubahan.

**AC-4 (idempotent re-lock — D-01 S6)**
Given RFA pertama sukses dengan `Idempotency-Key: K`,
When POST rfa diulang dengan `Idempotency-Key: K`,
Then response `200` identik, tanpa efek ganda (tidak double-emit `ApplicationLocked`, tidak double-write state).

**AC-5 (screening fail-closed)**
Given core screening service throw mid-check saat E7/atau re-derive RFA,
When screening dijalankan,
Then aplikasi **di-block** (`503 SCREENING_UNAVAILABLE`), tidak lolos sebagai "clean" (BR-06).

**AC-6 (broad-match hit + audit)**
Given applicant cocok watchlist hanya pada satu field ID (bukan semua field),
When E7 dijalankan,
Then `hit=true` (broad match menutup gap narrow variant), `aml_risk_tier` dihitung, dan satu baris audit-log append-only tertulis.

**AC-7 (server re-derive, abaikan flag client)**
Given client mengirim `is_blacklist=false` padahal register live berisi hit,
When RFA dijalankan,
Then RFA me-re-derive live dan memblokir/menandai sesuai register — flag client diabaikan (BR-07).

**AC-8 (typed related-person validasi — D-01 S2)**
Given payload dengan dua row `role="spouse"`,
When E1 dijalankan,
Then `422` validasi per-role (uniqueness spouse) — bukan diterima diam-diam seperti positional legacy.

**AC-9 (dedup-by-NIK — D-01 S1)**
Given `CUSTOMER` dengan NIK X sudah ada,
When E1 create aplikasi baru dengan NIK X,
Then aplikasi ditautkan ke `customer_id` existing (`customer_dedup="matched_existing"`), bukan re-capture duplikat.

**AC-10 (re-open re-screen non-destruktif)**
Given aplikasi `corrected`,
When POST reopen lalu re-submit RFA,
Then re-screen RAC dimodelkan sebagai event idempotent — record RAC eksternal **tidak** dihapus destruktif (fix GOTCHA-11).

**AC-11 (reject tanpa closure)**
Given reject terjadi (STEP 9 via E12, atau komite via aksi 03),
When state mendarat di 01,
Then `status=rejected` tanpa auto-close `CREDIT_APPLICATION`/`CREDIT_MEMO` (BR-28; OQ-AC-01 masih terbuka).

**AC-12 (STEP 8 sync happy path — BARU)**
Given payload MOOFI valid dengan `moofi_reference_id` baru,
When POST `/sync/moofi-applications` (E13),
Then validasi (kontrak `sp_validation_mobile_to_fincore`) lulus, `credit_id` unik nasional di-mint, draft kontrak skeleton terbentuk pada `rfa_locked` (Status RFA='0'), foto dipindahkan Mobile→Fincore, `ApplicationLocked` di-emit, response `201`.

**AC-13 (STEP 8 sync validation gagal = no-op + audit — BARU)**
Given payload MOOFI dengan field wajib invalid,
When POST E13,
Then response `422 MOOFI_SYNC_VALIDATION_FAILED` dengan detail per field; **tidak ada** `credit_id` di-mint / record aplikasi terbentuk; satu `MOOFI_SYNC_RECORD` `validation_status=failed` tercatat.

**AC-14 (STEP 8 sync idempotent — BARU)**
Given sync sukses sebelumnya untuk `moofi_reference_id: M`,
When POST E13 diulang dengan `M`,
Then response `200` mengembalikan `credit_id` yang sama, tanpa minting kedua / duplikasi record / double-emit event.

**AC-15 (validasi server-side ownership — BARU, fix gap FE)**
Given corporate applicant dengan total `ownership_share_pct` owners = 90% dikirim langsung via API (bypass FE),
When E1/E2 dijalankan,
Then `422` — total wajib tepat 100% (BR-35); legacy hanya memvalidasi client-side (OQ-CASFE-05).

**AC-16 (STEP 9 correction — BARU)**
Given aplikasi `rfa_locked`,
When Credit (Admin) POST `/credit-applications/{id}/return-for-correction` (E11),
Then status → `corrected`, file dapat diperbaiki CMO (kembali ke Step 1–7), tanpa penghapusan data/record eksternal (BR-26).

---

## 10. Dependency

**Upstream dikonsumsi (pull/read via ACL):**
- **MOOFI** (origination Step 1–7) — inbound sync STEP 8 (E13); mode transport final tergantung arsitektur ITEC (D-11).
- Masters `CUSTOMER` (dedup, penulisan otoritatif milik 05 — D-01 S15), `DEALER` (`11-...`), `PRODUCT/ASSET` (`12-...`), external masters `FC_MSTAPP_MCF`; CRUD masters = modul Menu Master (D-08).
- Biro/SLIK MaxKol (read) untuk jumlah reference wajib (BR-36) — data dimiliki 02/SLIK.
- Related-person taxonomy & corporate-owner model dari `10-customer-applicant-master.md` (cross-link, read).

**Downstream dipicu:**
- **Event `ApplicationLocked`** (push) → **02-credit-analysis** memicu RAC risk-gating via ACL, rute CF/US (D-01 S7; GT STEP 10) + queue Credit Analysis. Membawa risk-escalation signals (bukan `trans_type_id` final — komposisi milik 02 dari RAC callback ASYNC, D-01 S8).
- Re-open → event re-screen RAC idempotent (bukan destructive delete).

**Bukan dependency 01:** trans_type_id composition (02), seed/routing hierarki komite (`tr_hierarchy_transaction`, di-key `trans_type_id` — dibangun downstream; routing dinamis milik 03, D-01 S10), PO minting deterministik (04, D-01 S13), Vertel (05-area, D-02/GT STEP 14), verification hard-gate BAST/chassis + NPP activation + master loan/AR Card/PK/email blast (05, D-01 S14–15, D-03..D-06).

---

## 11. Keputusan Dibutuhkan (Open Questions)

| OQ-ID | Pertanyaan | Menyentuh | Prioritas |
|---|---|---|---|
| **OQ-GT-01** | Dual approve/lock paths: `sp_approve_cm` vs `sp_approve_cm_moofi` (PDF STEP 9/12) vs gate battery `sp_rfa_cm`/`sp_rfa_cm_car` (kode) — channel mana dilayani SP mana dalam scope rebuild? Menentukan apakah E8 (web) dan E13 (moofi) memakai gate battery identik. → **RESOLVED — evidence (2026-07-14)**: matriks caller terpetakan — `sp_approve_cm` = hanya approve **manual** web; `sp_approve_cm_moofi` = hanya 2 agent-SP **otomatis** (instant-approval IA + bulk RAC APPROVED); KEDUA LIVE; pemisah = **trigger**, bukan channel murni (detail: BE-00 §11 / BE-03 §11 / `_ACQUISITION-GROUND-TRUTH.md` OQ-GT-01). Keputusan port satu/dua jalur — termasuk penyamaan gate battery E8 vs E13 — = keputusan desain terpisah, tidak lagi mem-block evidence. | 01, 03 | ~~P1~~ **RESOLVED — evidence** |
| **OQ-GT-02** | Aturan minting nomor kontrak STEP 8 ("unik secara nasional" → `credit_id`): format/sequence source exact tidak ada di PDF. Baca numbering SP legacy / konfirmasi stakeholder. → **RESOLVED — evidence (2026-07-14)**: format legacy `credit_id = branch_id(5) + YY(2) + MM(2) + SEQ(5 zero-pad)` = 14 char, generator `sp_get_auto_number` (`SP/FC_ACQ_MCF/dbo.sp_get_auto_number.StoredProcedure.sql:54-60`; padding `fc_get_sequence_number` `FC_ACQ_MCF 2.sql:282-299`, cap 99999). Counter per (`code_type`,`period_year`,`period_month`,`branch_id`) di `tr_generate_code` — reset bulanan per cabang; keunikan nasional = prefix `branch_id`. code_type multi (`'TrCas'` web / `'CreditId'` sync / `'CreditItid'` r4) = counter independen ber-format identik → risiko tabrakan legacy; **memperkuat BR-33** generator tunggal di rebuild. Spec lengkap: §3.1.13. | 01 (BR-33) | ~~P2~~ RESOLVED |
| **OQ-PRD01-01** | Semantik **NIK deduplication lock** (D-01 S1): link-to-master saja, atau juga blokir draft in-flight kedua pada NIK sama? Default rebuild (USULAN): blokir dengan `409 DUPLICATE_INFLIGHT_APPLICATION`. Butuh sign-off. | 01 | P1 |
| **OQ-PRD01-02** | Duality lock MOOFI vs FINCORE: MOOFI Step 6 = "RFA Lock Initiation (emits ApplicationLocked)", GT STEP 8 = draft FINCORE lahir RFA='0'. Siapa emitter kanonik `ApplicationLocked` dan gate underwriting mana yang WAJIB diulang FINCORE-side saat ingestion (vs dipercaya dari MOOFI)? Default rebuild (USULAN): emitter tunggal = FINCORE 01; screening regulated di-re-derive FINCORE-side (BR-07). | 01, 02; MOOFI team | P1 |
| **OQ-REG-06** | Saat core screening SP (tanpa error-handling) throw mid-check, app-layer fail-closed (block) atau fail-open? Kebijakan untuk SEMUA regulated gate. **Highest-impact.** Rebuild default = fail-closed (BR-06), butuh sign-off. | 01, 02; pre-phase global | P1 |
| **OQ-ACQCAS-01** | Blacklist produksi-authoritative: narrow `sp_check_blacklist` vs broad `sp_check_blacklist_test_ilyas`? Rebuild pilih broad; evidence FE: web legacy hanya memanggil narrow (`validasi/checkblacklistktp`, BR-CASFE-7) — konfirmasi final *(kini terjawab; lihat kolom status)*. | 01; Phase 1 blocker | **RESOLVED — evidence** (matriks jalur→varian). (a) **Web CAS entry** = HANYA narrow `sp_check_blacklist`: `CASController.cs:641,815,2821,2979,3225` → `TrCasServices.cs:1029` (GET `validasi/checkblacklistktp`) → `TrCasController.cs:75-79` → `TrCasRepositoryEF.cs:1606`; narrow = single-key `LesseeID`, source LIVE `[MACF-DBMCF].[MACFDB]`, + anotasi karyawan internal `HREmployee`, read-only (`FC_ACQ_MCF 2.sql:16982-17063`). (b) **Broad `sp_check_blacklist_test_ilyas`** (3-key OR `LesseeID/sID/PNameID` + cek DTTOT APU-PPT + side-effect WRITE `update tr_cas set is_APUPPT='1'`, source staging replica — dump :17068-17130): endpoint `validasi/checkblacklistktpapuppt` (`TrCasController.cs:81-85`, repo :1614-1628 "add by Ilyasa") ter-expose tapi **ZERO caller** in-repo (web/JS/services/SP) → eksperimental. (c) **Mobile sync** `sp_validation_mobile_to_fincore` TIDAK memanggil kedua varian; query inline sendiri broad 3-key TETAPI hanya `bl_reasonid='5'` (Pelsus) + whitelist `tr_mapping_customer_blacklist` (SP file :1641-1709). (d) **Batch harian**: tidak ada caller SP untuk kedua varian; `fn_check_blacklist` defined-but-uncalled (dump :6776, 1 ref = definisi). Residu sempit (non-blocking): klien eksternal di luar repo & job msdb SQL Agent tak ter-audit dari dump. Konsekuensi migrasi: data legacy ter-screen narrow-only → WAJIB re-screen broad saat migrasi (BR-04). |
| **OQ-ACQCAS-02** | Kuesioner AML authoritative: `sp_check_APUPPT` vs `sp_check_APUPPT_Test_Ilyas` (+ audit-log side-effect)? | 01 | **RESOLVED — evidence** (pola identik OQ-ACQCAS-01). Web produksi memanggil HANYA `sp_check_APUPPT` — exact-match SEMUA field AND-ed (`KTPNo AND KTPNo1 AND KTPNo2 AND Name AND BirthDate AND BirthPlaceKota AND Address AND Occupation`), TANPA audit-log (`SP/FC_ACQ_MCF/dbo.sp_check_APUPPT.StoredProcedure.sql:30-40`) — via `CASController.cs:3372-3374` → `TrCasServices.cs:1054` (POST `validasi/checkapuppt`) → `TrCasController.cs:107-110` → `TrCasRepositoryEF.cs:1713`. Varian `sp_check_APUPPT_Test_Ilyas` — OR-match multi-key + fallback `CFCustomerBlackList` + audit-log `sp_insert_APUPPT_blacklist_log` pada hit (`FC_ACQ_MCF 2.sql:16920-16977`) — ter-expose via `validasi/checkapupptnew` (`TrCasController.cs:100-103`, repo :1690-1705 "add by Ilyasa") tapi **ZERO caller** in-repo. Konsekuensi rebuild: karena varian produksi TIDAK menulis audit-log, perilaku append-only audit BR-10 `[LOCKED]` harus diadopsi dari varian test (coverage-gap fix terkonfirmasi, bukan port perilaku produksi). |
| **OQ-ACQCAS-06** | Siapa/apa meng-invoke `spSyncPoolOrderToCAS` / `spNewZoomInsertSurvey_2w` / `SpSyncToFincoreR4_Reverse` (tak ada caller C#)? **Konsekuensi berubah pasca GT v2**: channel MOOFI kini first-class (STEP 8 formal) — pertanyaan tersisa = pemetaan mekanisme legacy → kontrak E13, bukan hidup/mati channel. | 01 | P2 (turun dari P1) |
| **OQ-GAP-01** | Channel **Pooling Order/OMA** (`tr_pooling_orders`, 66 kol) masih dipakai produksi? Siapa pengguna layar OMA sekarang? Rebuild mempertahankan channel ini (`trx_pooling_order` operasional + nilai `origination_channel='pooling'`) atau seluruh intake dikonsolidasi ke MOOFI (cukup arsip MIGRATE-READONLY)? Bukti transisi: 8 SP aktif + EF, tetapi join `tr_pooling_orders` di `sp_Cek_rac_history` sudah di-comment-out. Menentukan §3.1.9 + §3.4 #24. | 01 | P1 |
| **OQ-GAP-06** | `CASMobile_mappingfincore` (→ `map_moofi_fincore`) hanya terdaftar di EF `AcquisitionContext` tanpa satu pun SP/kode yang query — service .NET atau job mana yang benar-benar read/write? Masih diperlukan sebagai bridge, atau digantikan penuh kontrak E13? Menentukan scope MIGRATE-READONLY historis §3.1.11. **Evidence update (2026-07-14, tetap OPEN — menyempit ke keputusan retensi)**: (a) `cek_apuppt_log` **RESOLVED** — writer = `TrCasRepositoryEF.CekApupptLogAsync` (`FINCORE SERVICE/FINCORE.SERVICE.CREDITS/Repositories/EF/TrCasRepositoryEF.cs:1654-1689`, EF `AddAsync`+`SaveChangesAsync`, insert sekali per `credit_id` `IsActive='1'`; reader `CheckApupptLogAsync`:1629-1653; endpoint `POST validasi/cekapupptlog` `TrCasController.cs:93-96`) — semantik: idempotency marker cek APU-PPT per aplikasi (call pertama insert + NOT_FOUND, berikutnya SUCCESS); (b) `CASMobile_mappingfincore` **0 writer/reader lokal** [VERIFIED exhaustive — hanya DbSet `AcquisitionContext.cs:22,117` + DDL dump 6929] → writer eksternal sistem CASMobile/MOOFI [INFERRED: kolom `MobileCASID`/`TanggalMigrasi`/`Statuscasmobile` berpola migrasi dari `[MACF-DBKONSOL].MOBILE.dbo.CASMobile`]; residual = keputusan stakeholder bridge-vs-E13. | 01 | P2 |
| **OQ-GAP-07** | `tr_mapping_NIK_RO` (→ `map_nik_repeat_order`): bagaimana tabel di-populate (migrasi satu-kali dari sistem lessee lama vs proses berjalan)? Perlukah rebuild membawa mapping ini hidup (lookup RO BR-19) atau cukup diselesaikan sekali oleh dedup migrasi customer (OQ-MIG-03)? **Evidence update (2026-07-14, tetap OPEN)**: sweep exhaustive `INSERT/UPDATE tr_mapping_NIK_RO` = **0 hit** di full dump + 473 SP + seluruh .NET; tabel tidak terdaftar di EF sama sekali (0 hit `mapping_nik_ro`/`MappingNikRo` di `FINCORE SERVICE/` + `FINCORE.WEB/`). Reader hanya 3 SP lookup RO `sp_get_pagination_lookup_agreement_old(_car)(_stg)` (join `ro.identity_number_old=c.lesseeid`, filter `ro.identity_number=@LesseeId` — `dbo.sp_get_pagination_lookup_agreement_old.StoredProcedure.sql:149-155,180-186`). Kesimpulan: populate path tidak ada di aplikasi FINCORE — load eksternal (migrasi satu-kali / job DBA / sistem lain) [INFERRED]; keputusan bawa-hidup vs sekali-selesai OQ-MIG-03 = stakeholder call. | 01 | P2 |
| **OQ-MEET-06** | Matriks step per product MACF (D-07): product list + step applicability/variance — mem-blok annex per-product, termasuk keputusan gate car-only (BR-16/17/18) & divergensi motor. | 01..05; P1 blocker annex | P1 |
| **OQ-MEET-04** | Instant-Approval lane (D-01 S11): eligibility per product/plafond — 01 hanya membawa flag `instant_approval_cohort` di payload; gate milik 03. | 03 (disuplai 01) | P2 |
| **OQ-REG-01** | Endpoint screening mana yang benar-benar dipanggil front-end produksi (gap narrow live atau dead)? FE KB menjawab utk web (narrow only); channel MOOFI belum ter-evidensi. | 01 | P2 |
| **OQ-REG-02** | Intake-only AML screening = desain kontrol yang di-sign-off, atau titik screening kedua di credit-analysis yang tak pernah dibangun? | 01, 02 | P1 |
| **OQ-ACQCAS-03** | Gate car-only (usia kendaraan / usia borrower 65 / DP% by purpose) = beda kebijakan car-vs-motor intensional, atau gap motor? Extend ke motor? (masuk matriks D-07/OQ-MEET-06) | 01 | P2 |
| **OQ-ACQCAS-04** | Divergensi formula Repeat-Order decision-tier (car %tenor vs motor count) + identity-match (motor only) = intensional atau drift? Unify? | 01 | P2 |
| **OQ-ACQCAS-05** | Re-screen blacklist RFA car 5-reason vs motor 1-reason = intensional atau gap motor? Extend semua reason ke motor? | 01 | P2 |
| **OQ-AC-01** | Reject apakah harus menutup aplikasi? Legacy menutup `TrCas`/`TrCm`; rebuild default TIDAK auto-close (BR-28). Kini juga menyentuh reject STEP 9 (E12). | 01, 03 | P2 |
| **OQ-CMPO-01** | "Status 0" flow-doc = header-level status parent aplikasi atau status memo sendiri? Rebuild pilih `CREDIT_APPLICATION.status=rfa_locked` + `CREDIT_MEMO=draft`; dokumentasikan mapping (GT STEP 8 konsisten dgn header-level). | 01, 04 | P2 |
| **OQ-ACQCAS-07** | Aksi mobile "reverse to mobile" berefek pada `tr_CAS`/`tr_CM` lokal, atau record lokal tetap live dgn data stale? Risiko orphan/duplikat. FE: trigger reverse di layar web pun commented-out (OQ-CASFE-02-adjacent). | 01 | P2 |
| **OQ-ACQCAS-08** | Bagaimana blacklist-override/whitelist table di-maintain hari ini (tak ada CRUD)? Kandidat admin screen di Menu Master D-08. | 01 | P2 |
| **OQ-AC-02** | Threshold eskalasi aggregate-exposure OP: Rp 35.000.000 (kode) vs ~Rp 30.000.000 (komentar)? Sinyal disuplai 01, gate milik 03. | 03 (disuplai 01) | P2 |
| **OQ-CASFE-04** | Forced default Corporate + credit_source=1 (locked) pada setiap aplikasi baru layar legacy: kebijakan bisnis aktual atau restriksi tak disengaja? Menentukan default `customer_kind` pada E1 rebuild. | 01 (BE default rules), FE-01 | P1 |
| **OQ-ACQCAS-09** | Ada path live cancel/delete CAS/CM pra-RFA? Hanya delete-legacy ke tabel dead; FE section pembatalan pun dead (FE EC2). Cancel = `[KEPUTUSAN DESAIN BARU]` (E9). | 01 | P3 |
| **OQ-ACQCAS-10** | **Di-narrow oleh FE KB**: set kode `application_type_id` kini lengkap (BR-CASFE-18: 01/02/03/04/05/06 `[LOCKED]`); tersisa makna bisnis `'01'/'04'/'05'/'06'` + arti singkatan "OP". | 01 | P3 |
| **OQ-ACQCAS-11** | Ekspansi literal "TAC" (channel order-source dealer) tak dinyatakan di kode. | 01 | P3 |
| **OQ-EXTMASTERS-01** | Masters `FC_MSTAPP_MCF` dimiliki rebuild atau read-only; linked server MACF-DBSTG/DBMCF/DBKONSOL/dbrep masih live? | references; Phase 1 blocker | P1 |
| **OQ-ARCH-STACK** | **Sebagian resolved oleh D-12**: BE = Java `[LOCKED]`, FE = Next.js `[LOCKED]`. Tersisa `[OPEN]`: framework BE (rekomendasi Spring Boot — USULAN), transport (REST/gRPC/message-bus), topologi infra — menunggu arsitektur ITEC (D-11). | semua kapabilitas | P1 (ITEC) |

> Catatan marker-fidelity: `[LOCKED]` (BR-01,14,15,16 cap,21 semantik,33 semantik unik,38 role; field NIK/NPWP/ojk_*/chassis/engine/otr_price; AML BR-08/09/10/11; kode `application_type_id`; photo-type vocabulary; D-09/D-10/D-12) = WAJIB dipertahankan (regulasi / external-FK / kontrak eksternal / keputusan governance meeting). `[INTENT]` = desain target boleh redesign, jaga outcome — termasuk mandat meeting D-01 (dedup lock, typed roles, entry-time screening, idempotent RFA lock + event). `[OPEN]`/OQ = jangan diselesaikan diam-diam. USULAN eksplisit di dokumen ini: `origination_channel` enum, `MOOFI_SYNC_RECORD` entity, default dedup-lock 409, emitter tunggal `ApplicationLocked`, rekomendasi Spring Boot. Do-not-replicate: GOTCHA-1 (narrow screen), -2 (fail-open), -10 (car/motor split), -11 (RAC destructive delete), -16 (no dedup), -17 (positional related-person), -18 (dead `*_cas`), EC6 (sync-over-async), FE EC5 (silent upload failure), FE EC11 (session-affinity context).
