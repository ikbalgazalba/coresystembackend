# PRD — Vertel: Verifikasi Telepon Konsumen (Customer Phone Verification) [BE]

> **Audience**: Tim Backend (BE). **Target stack**: **Java** `[LOCKED per D-12]` — framework belum ditetapkan; **rekomendasi: Spring Boot** (USULAN — lihat §11 [OPEN] OQ-ARCH-STACK). **Tanggal**: 2026-07-14.
> **Sumber otoritatif**: `_ACQUISITION-GROUND-TRUTH.md` v2 (PDF "ALUR TRANSAKSI ACQUISITION 08072026", 16-STEP final — STEP 14 `:63-65`) + `_MEETING-DECISIONS-2026-07.md` (register D-01..D-12, terutama **D-02**) + KB `10-domains/30-verification-external-checks.md` (BR-VERIF-1..12, state machine, edge cases) + KB `50-integrations/dukcapil.md` + KB FE `60-frontend/65-npp-vertel-screens.md` (VTL-S1/S2, BR-NPPVTL-12..18).
> **Status**: **MODUL BARU** dalam dekomposisi PRD (D-02: "Perlu ditambahkan step verifikasi telepon di antara step 13–14", diformalkan sebagai **STEP 14** di PDF 08072026). Bukan kapabilitas baru dari nol — legacy sudah punya sub-workflow Vertel penuh (`FINCORE.SERVICE.VERTEL`, `tr_verification_customer`); yang BARU adalah **posisi formal di alur** (antara PO dan NPP), **30-day expiry strict** (D-01 Step 14), dan pembersihan seluruh *do-not-replicate* bug legacy. Arsitektur infra final = deliverable ITEC Bank Mega (D-11) — PRD ini menyatakan asumsi & menandai keputusan yang menunggu dokumen itu.

Kapabilitas **06-vertel-verification** adalah **produsen status verifikasi konsumen** pada alur final 16-STEP — **STEP 14** (GT `:63-65`): setelah PO terbit (STEP 13), **Admin Cabang** memvalidasi data langsung ke konsumen via telepon (legacy **`TrVerificationCustomer`** / `tr_verification_customer` — kanonik per OQ-DATA-05 resolved), melakukan **RFA Vertel**, lalu disetujui oleh **Kepala Cabang**. Output modul ini — status `verified` (legacy `verification_status='A'`) + timestamp freshness — adalah **syarat wajib (hard-gate)** aplikasi masuk antrean **NPP legalization (STEP 15)** dan **BPKB candidate queue** (BR-VERIF-6; umbrella §5 seam "Verification hard-gate sebelum NPP"). Modul ini **memproduksi** gate; **eksekusi/enforcement** gate berada di **05-npp** (in-transaction, block + rollback bila gagal) — dua peran distinct, satu eksekutor (umbrella `00-OVERVIEW.md §5`). Requirement meeting yang mengubah semantik legacy: (a) **posisi**: legacy meng-antre Vertel segera setelah CM committee-approved; target-state menempatkannya **setelah PO terbit** (D-02); (b) **expiry**: status verifikasi konsumen **kedaluwarsa strict 30 hari** (D-01 Step 14; konsekuensi & titik mulai clock = OQ-MEET-05); (c) **approval**: checker target-state bernama **Kepala Cabang** (GT `:64-65`; D-10), **super user dihapus** (D-09), **self-approval diblokir** (D-01 Step 11).

- **STEP dicakup**: STEP 14 (Vertel) — baris deltas GT: "— (MoM addition) → STEP 14 Vertel — NEW mandatory step before NPP" (GT `:90`).
- **Kepemilikan otoritatif**: entity `VERIFICATION` (legacy `tr_verification_customer`) + checklist dokumen (`tr_verification_customer_application_in`) + rantai approval "VK" untuk Vertel + emisi event `CustomerVerificationApproved` (USULAN) + surface read-only hasil Dukcapil (informational).
- **Downstream langsung**: **05-npp** (STEP 15) meng-enforce `status='verified'` + freshness 30-hari sebagai hard-gate aktivasi (BE-05 §1.1); BPKB candidate queue (post-acq) membaca gate yang sama (BR-VERIF-6; OQ-COLL-01 resolved → `verification_status='A'`).
- **Sumber KB utama**: `10-domains/30-verification-external-checks.md`; `_ACQUISITION-GROUND-TRUTH.md:63-65,75-79,90`; `_MEETING-DECISIONS-2026-07.md` (D-02, D-01 Step 14, D-09, D-10); `50-integrations/dukcapil.md`; `60-frontend/65-npp-vertel-screens.md`.

---

## 1. Ruang Lingkup & Kepemilikan

### 1.1 Dimiliki service ini (owned)

| Kepemilikan | Deskripsi | Sumber |
|---|---|---|
| **Antrean kerja Vertel (work queue)** | Daftar aplikasi *eligible* untuk verifikasi telepon. Target-state: eligible = **PO sudah terbit (STEP 13)** + CM committee-approved + belum punya verifikasi aktif — ATAU verifikasi berstatus `rejected`/`recheck` yang boleh dikerjakan ulang (fix BR-VERIF-12). **SATU implementasi query** (konsolidasi triplikasi legacy — §1.4). | D-02; GT `:59-65`; `30-verif §7 BR-VERIF-1/12`; `§9 EC1/EC2/EC7` |
| **Capture wawancara telepon (maker, S1)** | Form interview: pasangan *confirmed-vs-actual* (delivery date, item type, installment, tenor, DP, nilai pencairan UMC, admin fee, email, HP/WhatsApp), penerima barang + relasi, checklist dokumen wajib per asset-kind/disbursement-method, catatan; save Draft vs Submit-for-approval. Persisten ke `trx_customer_verification` + `trx_customer_verification_document` (§3.1). | `30-verif §3a S1, §4, §6`; `TrVerificationConsumenModels.cs:1-46`; `65-npp-vertel §3a VTL-S1` |
| **RFA Vertel (submit maker→checker)** | Transisi ke `rfa` + pembukaan/penerusan rantai approval "VK". Re-submit saat chain masih open = **resume**, bukan duplikasi (BR-VERIF-4). Aktor RFA target-state = **Admin Cabang** (GT `:64`). | GT `:63-65`; `30-verif §5.3-4, §7 BR-VERIF-2/4` |
| **Keputusan approval Vertel (checker, S2)** | Approve / Reject / Correction (+ Verify interim bila chain multi-level dipertahankan — OQ-VTL-03), keputusan di-*type* oleh reason-code master (BR-VERIF-5). Checker target-state = **Kepala Cabang** (GT `:64-65`; D-10). **No self-approval** (D-01 Step 11); **tanpa super user** (D-09 `[LOCKED]`). | GT `:64-65`; D-09/D-10; `30-verif §5.5-6, §7 BR-VERIF-5`; `sp_approve_vertel.sql:61-256` |
| **Produksi sinyal gate + freshness** | Menulis `status='approved'` (proyeksi kanonik `verified` — §3.3), `verified_at`, `expires_at = verified_at + 30 hari` (**strict** — D-01 Step 14; parameter clock/konsekuensi = OQ-MEET-05). Menyediakan **read API status** yang dikonsumsi 05-npp in-transaction dan BPKB queue (PULL — D-01 Step 15). | D-01 Step 14; `30-verif §7 BR-VERIF-6`; OQ-MEET-05 |
| **Expiry lifecycle 30-hari** | Deteksi kedaluwarsa (`verified` → `recheck` setelah `expires_at` lewat — USULAN default, menunggu OQ-MEET-05) + membuat record eligible dikerjakan ulang di antrean. | D-01 Step 14; OQ-MEET-05 |
| **Re-verifikasi setelah Reject/Expiry** | Implementasi BENAR dari intent legacy yang rusak: aplikasi `rejected` (dan `recheck`) **kembali muncul** di antrean untuk dikerjakan ulang (legacy: dead code — Edge Case 1). | `30-verif §7 BR-VERIF-12 [ARTIFACT]`, `§9 EC1`; OQ-VERIF-04 |
| **Checklist dokumen per asset-kind** | Definisi field dokumen wajib (asset-kind- & disbursement-method-dependent, legacy `sp_get_DocumentField`) + persist per-dokumen "present + file upload". | `30-verif §7 BR-VERIF-9`; `65-npp-vertel §3a` (checklist), `§9 EC4` |
| **Guard disbursement-account (anti-fraud)** | Untuk aplikasi car retail non-UMC: rekening tujuan pencairan wajib diisi + **nama pemilik rekening token-match nama applicant** (`[LOCKED]` kontrol; algoritma bebas) + minimal 2 dokumen pendukung. Enforcement **server-side otoritatif** (legacy: hanya kuat di FE + 1 guard server). | `65-npp-vertel §7 BR-NPPVTL-12/13/16 [LOCKED]`; `VertelController.cs:297-377` |
| **Audit & history approval** | Rantai keputusan VK + arsip history (legacy `tr_hierarchy_transaction` + `tr_hierarchy_approval_transaction`) di-model per ADR-13: in-flight = human task Flowable, audit append-only = **`log_approval_history` terpusat (milik 03)** dengan diskriminator `module_context`/`entity_type` slice VK (§3.1 — **OQ-VTL-06 RESOLVED by convention**, dasar: DB-CONVENTIONS §8 + ADR-13d). | `30-verif §6`; `sp_get_history_vertel.sql:16-46`; ADR-13; DB-CONVENTIONS §8 |
| **Cetak/report Vertel (dataset)** | Dataset print form verifikasi + checklist dokumen (report-only, tersedia setelah `verified`). | `30-verif §6`; `VertelReportController.cs:1-30`; `65-npp-vertel §5.19` |
| **Surface read-only hasil Dukcapil** | Endpoint read-only hasil match civil-registry by NIK + paginated list (informational untuk staf; **tidak ada gate coded** — BR-VERIF-8/OQ-VERIF-01). Request-initiation Dukcapil BUKAN di sini ([OPEN] OQ-DUKCAPIL-01). | `dukcapil.md §2-§6`; `30-verif §7 BR-VERIF-8` |
| **Emisi event `CustomerVerificationApproved`** (USULAN) | Sinyal ke downstream saat verifikasi final-approved; downstream tetap **PULL/re-read otoritatif in-transaction** (05), event hanya notifikasi. | D-01 Step 15 (PULL, never push); umbrella §5 |

### 1.2 BUKAN milik service ini (non-goal)

| BUKAN dimiliki | Pemilik | Catatan |
|---|---|---|
| **Enforcement gate verifikasi sebelum NPP** | **05-npp** (eksekutor) | 06 hanya **memproduksi** status; 05 meng-enforce in-transaction (block + rollback). Umbrella §5 seam. |
| **Gate freshness FCL/SLIK 30-hari di NPP-save** | Data: **02-credit-analysis** (BUREAU_RESULT); enforcement: **05-npp** | **JANGAN dikonflasi** dengan expiry Vertel 30-hari (D-01 Step 14) — dua cek berbeda, stage berbeda, data berbeda (BR-VERIF-7 `[INTENT]`; umbrella §4.1 "dua gate berbeda"). Lihat §6 BR-06-14. |
| **Validasi BAST + chassis/engine (`sp_validation_chasis_number`)** | **05-npp** (STEP 15) | D-01 Step 14 menyebut "BAST + chassis/engine validation" dalam satu kalimat dengan expiry — pemetaan modul: BAST/chassis = STEP 15 milik 05 (GT `:66-69`); expiry verifikasi konsumen = milik 06. |
| **Field survey mobile 2W sebagai intake** (`spNewZoomInsertSurvey_2w`) | **01-intake-cas** (channel mobile, P2) | Survey-as-intake membangun `tr_CAS`/`tr_CM` (`credit_source_id='5'`) — itu kapabilitas intake, bukan verifikasi post-PO. Caller eksternal `[OPEN]` OQ-VERIF-02 (register di 01/OQ-ACQCAS-06). `30-verif §5.8, §7 BR-VERIF-10/11`. |
| **Surveyor assignment desk-side + print survey** | 01-intake / references | Lookup surveyor by branch/asset-kind/position (`sp_get_pagination_lookup_surveyor`) — bukan bagian gate STEP 14. `30-verif §5.9`. |
| **Data biro FCL/SLIK & KTP/FCL viewer** | **02-credit-analysis** (BUREAU_RESULT via ACL) | View KTP photo & FCL batch = read surface milik 02; 06 hanya menautkan (link) dari layar verifikasi. `30-verif §1.3`. |
| **Inisiasi request Dukcapil** | `[OPEN]` — mekanisme upstream belum terlokasi | Tidak ada write path di codebase (Edge Case 1 dukcapil.md); rebuild TIDAK boleh mengasumsikan read-only cukup sebelum OQ-DUKCAPIL-01 dijawab. `dukcapil.md §2, §10`. |
| **PO minting / Open CM correction** | **04-contract-cm-po** (STEP 13) | 06 hanya membaca keberadaan PO sebagai prasyarat antrean. Interaksi Open CM ↔ verifikasi existing = OQ-VTL-02. |
| **Aktivasi kontrak / TrNpp / AR Card / master loan** | **05-npp** (STEP 15) | D-04/D-05/D-06 — lihat BE-05. |
| **Konsolidasi "Customer Check" (satu verdict gabungan)** | Net-new — belum diputuskan | Legacy TIDAK punya konsolidasi (Edge Case 6 `30-verif §9`); bila target flow menghendakinya = fitur baru, bukan migrasi (OQ-VERIF-05). |

### 1.3 Delta posisi alur: legacy vs target-state (mengikat)

| Aspek | Legacy (kode) | Target-state (GT v2 + D-02) | Marker |
|---|---|---|---|
| Trigger masuk antrean | CM committee-approved (`status_approval='A'`) + belum ada baris verifikasi (BR-VERIF-1) — **sebelum/paralel PO** | **Setelah PO terbit (STEP 13)**: "step verifikasi telepon di antara step 13–14" (D-02); STEP 14 di PDF 08072026 | `[INTENT]` — outcome posisi WAJIB per D-02 |
| Aktor maker | "Verifier/Preparer" generik (`VertelController.cs:57-61`) | **Admin Cabang** (GT `:64`; sensus D-10: Credit (Admin)) | `[INTENT]` role naming per D-10 |
| Aktor checker | Hierarki VK multi-level, routing car (`_R4`) vs motor (BR-VERIF-3); super-user bypass mechanism-reuse | **Kepala Cabang** (GT `:64-65`); super user DIHAPUS (D-09 `[LOCKED]`); kedalaman chain = OQ-VTL-03 | `[LOCKED]` D-09; `[OPEN]` kedalaman |
| Umur status verified | Tidak ada expiry pada `verification_status` | **Strict 30-day expiry** pada consumer-verification status (D-01 Step 14) | `[INTENT]` — konsekuensi & clock = OQ-MEET-05 |
| Rejected → re-work | Intent ada, **tidak pernah jalan** (dead filter — EC1) | Re-queue BENAR: `rejected`/`recheck` bisa dikerjakan ulang | `[ARTIFACT]` fix wajib (BR-VERIF-12) |

### 1.4 Reengineering mandate (bukan mirror legacy)

Semua *do-not-replicate* diperbaiki, bukan ditiru (detail per-item §6/§9-AC):

- **Konsolidasi query antrean** — legacy 3 copy (`spGetListNewVertelLookUpPaging`, `sp_get_pagination_lookup_vertel`, `sp_get_pagination_lookup_vertel_r4` — copy R4 **tanpa caller** sama sekali) masing-masing membawa bug yang sama → **SATU** implementasi (`30-verif §9 EC2 [ARTIFACT]`).
- **Fix dead-filter rejected** — `(vk IS NULL OR vk.status='R')` di inner query dianulir `NOT IN` outer → rejected tak pernah re-queue (`§9 EC1`); plus **operator-precedence bug** `AND`/`OR` tanpa kurung yang mem-bypass filter saat search term kosong (`§9 EC7`) → predicate ter-parameterisasi eksplisit, bukan string-concat.
- **Fix double-write Correction top-level** — blok `IF` non-eksklusif `sp_approve_vertel` menyebabkan Correction di level puncak menulis 2x + insert row hand-off ekstra (`§9 EC3`); rebuild memodelkan **satu transisi eksplisit** "Correction → kembali ke maker" (aturan final = OQ-VERIF-06).
- **Buang tabel mati** — `CFVerifikasiKonsumen`/`sp_insert_VerifikasiKonsumen` **zero caller** (`§9 EC4 [ARTIFACT]`; OQ-DATA-05 resolved: kanonik = `tr_verification_customer`); jangan drop fisik saat migrasi sebelum audit penulis eksternal (OQ-VERIF-03).
- **Fix layar approval FE yang mati** — tombol keputusan Vertel **tidak pernah render** di legacy (label-mismatch — `65-npp-vertel §9 EC1 [ARTIFACT]`); BE menyediakan endpoint decision yang benar + flag `is_pending_approver` per-record sehingga FE Next.js baru meng-gate dengan benar (BR-NPPVTL-17 `[INTENT]`).
- **Satu engine config-driven car & motor** — routing VK car (`sp_get_hierarchy_transaction_R4`) vs motor (`sp_get_hierarchy_transaction`) dikolapskan jadi satu engine dengan konfigurasi per product-line (umbrella §6.2 departure #3; BR-VERIF-3 outcome dijaga).
- **Super user DIHAPUS** (D-09 `[LOCKED]`); **self-approval diblokir di application layer** (D-01 Step 11).
- **Konsolidasi guard auto-debit 3-situs** — kondisi panel rekening tujuan di-reimplementasi 3x di legacy (2 script FE + 1 server) → server-computed otoritatif + metadata visibility utk FE (`65-npp-vertel §9 EC8`).

---

## 2. Aktor & Peran

Sensus role cabang target-state (D-10 `[LOCKED]`): **CMO, Marketing Head, Credit Analyst, Kepala Cabang, Credit (Admin)**; hierarki approval tergantung **skala risiko**. **Super user DIHAPUS** (D-09 `[LOCKED]`; PDF PRD Notes 2.1, GT `:77-79`).

| Aktor | Peran di modul ini | Sumber |
|---|---|---|
| **Admin Cabang / Credit (Admin)** (maker) | Melakukan wawancara telepon dengan konsumen; capture confirmed-vs-actual + checklist dokumen; save Draft; **submit RFA Vertel**. | GT `:63-65` ("Admin Cabang memvalidasi data langsung ... Admin Cabang melakukan RFA Vertel"); D-10; `30-verif §2` (Preparer/Verifier) |
| **Kepala Cabang** (checker) | Membuka record dalam mode approval; merekam **Approve / Reject / Correction** dengan reason code; keputusan Approve final membuka gate NPP/BPKB. | GT `:64-65` ("disetujui oleh Kepala Cabang"); D-10; `30-verif §2` (Approver/checker) |
| **System / Expiry scheduler** | Memicu notifikasi/re-queue eventing saat `expires_at` lewat (USULAN default — OQ-MEET-05); proyeksi `verified` → `recheck` = read-time dari (`status`, `expires_at`) TANPA UPDATE baris (§3.3); memunculkan kembali record di antrean. | D-01 Step 14; OQ-MEET-05 |
| **Modul 05-npp** (konsumen, read-only) | Membaca status + freshness via read API **in-transaction** sebagai hard-gate aktivasi (eksekutor gate). | Umbrella §5; BE-05 §1.1; `30-verif §7 BR-VERIF-6` |
| **BPKB candidate queue** (konsumen post-acq, read-only) | PULL record dengan `status='verified'` (OQ-COLL-01 resolved). | `30-verif §7 BR-VERIF-6`; umbrella OQ-COLL-01 |
| **Konsumen / Applicant** | Subjek wawancara — bukan aktor sistem; hanya data yang direferensikan. | `30-verif §2` |
| **Back-office viewer** | Melihat hasil Dukcapil (read-only, informational — tanpa aksi approval coded). | `30-verif §2`; `dukcapil.md §3` |

> **Enforcement identitas**: (a) **no self-approval** — `decision.actor` ≠ `rfa.submitted_by` divalidasi di application layer (D-01 Step 11; legacy SQL tidak meng-enforce); (b) **super-user bypass** legacy (mechanism-reuse `[INFERRED]` di `30-verif §2`) **TIDAK dibawa** — OQ-VERIF-09 ("apakah super-user berlaku utk transaksi VK?") **DITUTUP oleh D-09**: role-nya sendiri dihapus dari rebuild.
> **Catatan role "Kepala Cabang"**: literal role string tidak ditemukan di kode legacy — layar meng-gate pakai flag generik "pending approver" (`65-npp-vertel §2`; OQ-NPPVTL-02; OQ-ACTORS-01). D-10 menjadikannya **role target-state bernama** → rebuild memodelkan role eksplisit `KEPALA_CABANG` pada langkah approve Vertel (konsisten dgn BE-05 §2).

---

## 3. Model Data

> **GROUND TRUTH SCHEMA modul vertel** — format per `docs/DB-CONVENTIONS.md` §9 (ADR-14): nama target ber-prefix kelas, mapping asal legacy per kolom, field census ber-marker (confidence × mutability), dan disposisi eksplisit tabel yang TIDAK dibawa. RDBMS asumsi PostgreSQL (USULAN — final by ITEC, A-2). Konform Shared ERD umbrella (`00-OVERVIEW.md §6`): entity `VERIFICATION` ≙ **`trx_customer_verification`** (PK teknis `id` + business key `credit_id`), pemilik = modul ini, konsumen 05/BPKB. Field `[LOCKED]` = additive only. Basis `DATA-MIGRATION-PLAN.md` (ADR-15): tiap tabel legacy di bawah mendapat lane extract `stg_legacy_*` 1:1 sebelum transform; rekonsiliasi row-count + checksum field `[LOCKED]`.

### 3.0 Disposisi tabel legacy milik modul (coverage 4/4)

| Tabel legacy (`FC_ACQ_MCF.dbo`) | Kolom | Disposisi | Target |
|---|---|---|---|
| `tr_verification_customer` | **66** | **PORT + normalisasi** — census penuh §3.1 (58 kolom → header, 6 kolom posisi `_2`/`_3` → child attempt, 2 kolom discard beralasan) | `trx_customer_verification` + `trx_customer_verification_contact_attempt` |
| `tr_verification_customer_application_in` | 11 | **PORT** — census penuh §3.1 (11/11) | `trx_customer_verification_document` |
| `CFVerifikasiKonsumen` | 36 | **`[ARTIFACT — discard]`** varian generasi-1 mati (analisis relasi §3.4; BR-06-21) | tidak di-port; drop fisik menunggu OQ-VERIF-03 |
| `CFVerifikasiKonsumen_AplikasiIN` | 9 | **`[ARTIFACT — discard]`** (zero referensi di seluruh SP corpus — §3.4) | tidak di-port; idem |

Slice tabel shared yang disentuh alur VK (bukan milik 06, disposisi penuh di BE-00/BE-03): `tr_hierarchy_transaction` (type `VK`) + `tr_hierarchy_approval_transaction` → **Flowable runtime (`ACT_*`) untuk in-flight + `log_approval_history` terpusat (milik 03) untuk audit** (ADR-13d; DB-CONVENTIONS §7-§8; **OQ-VTL-06 RESOLVED by convention** — lihat §3.1; migrasi slice VK satu pintu di lane BE-03, §3.4 registry-note).

### 3.1 Tabel yang dimiliki service ini

**`trx_customer_verification`** — record verifikasi konsumen per aplikasi (entity umbrella `VERIFICATION`).

- **Kelas**: `trx_` (spine bisnis). **Mapping asal**: `tr_verification_customer` (PK legacy = `credit_id` → 1 baris per aplikasi selamanya = akar EC1; target = **versioned rows**).
- **Kunci**: PK `id BIGINT GENERATED ALWAYS AS IDENTITY`; business key `credit_id` **[LOCKED]**; FK `application_id` → `trx_application`; FK self `superseded_by_id`.
- **Unik/index**: `ux_trx_customer_verification_credit_id_current` — partial unique `(credit_id) WHERE superseded_at IS NULL` (maksimal SATU baris current per aplikasi; re-work BR-06-13 menstempel `superseded_at` baris lama lalu insert versi baru — history utuh, BUKAN menimpa); `ix_trx_customer_verification_application_id`; `ix_trx_customer_verification_status` (antrean E1/E2); `ck_trx_customer_verification_status`.
- **Status**: SATU kolom `status` (DB-CONVENTIONS §5) = vocabulary state machine internal (§7). Status kanonik cross-service (pending|verified|failed|recheck) **BUKAN kolom kedua** — proyeksi read-time E11 dari (`status`, `expires_at`) — §3.3. Diskriminator umbrella `check_type` juga bukan kolom fisik: baris tabel ini selalu `vertel`; `dukcapil`/`ktp_fcl`/`survey` tidak pernah ditulis modul ini (direalisasikan di API/read-model).
- **Pola pair legacy dipertahankan**: `option_X` (bit "jawaban konsumen SESUAI data CM") + `option_X_real` (nilai sebenarnya bila beda) → `x_matches BOOLEAN` + `x_actual`. Sisi "confirmed" TIDAK disimpan ulang (derive dari `trx_credit_memo` — larangan denormalisasi DB-CONVENTIONS §6.3), kecuali email/mobile yang legacy memang menyimpan snapshot `_original` (dipertahankan, dipakai guard BR-06-09).

Field census (66/66 kolom legacy ter-disposisi; `— (BARU)` = kolom target tanpa asal legacy):

| Kolom target | Tipe target | Null | Asal legacy (`tr_verification_customer`) | Marker | Catatan |
|---|---|---|---|---|---|
| `id` | BIGINT identity (PK) | NOT NULL | — (BARU) | — | Menggantikan PK legacy `credit_id` (fix EC1: memungkinkan versi baru per re-work). |
| `application_id` | BIGINT (FK `trx_application`) | NOT NULL | — (BARU; resolve dari `credit_id` saat migrasi) | [INTENT] | Key umbrella; declared FK wajib (DB-CONVENTIONS §2). |
| `credit_id` | VARCHAR(20) | NOT NULL | `credit_id` varchar(20) — PK legacy | [VERIFIED]**[LOCKED]** | Kunci lintas-modul (gate NPP/BPKB, history VK); format = OQ-GT-02. |
| `status` | VARCHAR(20) CHECK (`draft\|rfa\|verified_interim\|approved\|correction\|rejected`) | NOT NULL | `verification_status` char(1) `D/0/V/A/C/R` | [VERIFIED] nilai; [INTENT] representasi | Mapping migrasi §3.3; `verified_interim` hanya bila multi-level (OQ-VTL-03). Transisi via service + Flowable (§7). |
| `submitted_by` / `submitted_at` | VARCHAR(50) / TIMESTAMPTZ | NULL | — (BARU) | [INTENT] | Stamp maker saat RFA; dipakai guard no-self-approval BR-06-05 **[LOCKED]**. |
| `verified_at` | TIMESTAMPTZ | NULL | — (BARU) | [INTENT] | Diisi saat final-approve Kepala Cabang. |
| `expires_at` | TIMESTAMPTZ | NULL | — (**BARU per D-01 Step 14**) | [INTENT]; parameter **[OPEN]** OQ-MEET-05 | `verified_at + 30 hari` **strict** (USULAN clock start = `verified_at`). Dibaca 05 in-transaction (E11); basis proyeksi `recheck` §3.3 — hard-gate STEP 14. |
| `superseded_at` / `superseded_by_id` | TIMESTAMPTZ / BIGINT (FK self) | NULL | — (BARU) | [KEPUTUSAN DESAIN BARU] | Versioning re-work (BR-06-13, fix EC1): baris current = `superseded_at IS NULL` (partial unique di atas). |
| `contact_status` | VARCHAR(20) CHECK | NULL (wajib saat submit) | `contacted_option` int | [VERIFIED][INTENT] | Kode int legacy → enum; hasil kontak final. Attempt per panggilan dinormalisasi ke child table (bawah). |
| `confirmation_at` | TIMESTAMPTZ | NULL | `confirmation_date` datetime + `confirmation_time` varchar(10) | [VERIFIED][INTENT] | **MERGE 2→1**; transform parse jam; simpan UTC (DB-CONVENTIONS §3). |
| `billing_date` | DATE | NULL | `billing_date` date | [VERIFIED][INTENT] | |
| `delivery_date_matches` | BOOLEAN NOT NULL DEFAULT false | NOT NULL | `option_item_received_date` bit | [VERIFIED][INTENT] | Boolean nullable dilarang (§3): NULL legacy → false. |
| `delivery_date_actual` | DATE | NULL | `option_item_received_date_real` datetime | [VERIFIED][INTENT] | Tanggal murni → DATE. |
| `item_type_matches` / `item_type_actual` | BOOLEAN / VARCHAR(50) | NOT NULL / NULL | `option_item_type` bit / `option_item_type_real` varchar(50) | [VERIFIED][INTENT] | |
| `installment_matches` / `installment_actual` | BOOLEAN / NUMERIC(18,2) | NOT NULL / NULL | `option_installment` bit / `option_installment_real` numeric(9,0) | [VERIFIED][INTENT] | Uang → NUMERIC(18,2), whole-rupiah legacy simpan `.00` (§3). |
| `installment_actual_note` | VARCHAR(255) | NULL | `option_installment_real_others` varchar(255) | [VERIFIED][INTENT] | Jawaban free-text varian "lainnya". |
| `tenor_matches` / `tenor_actual` | BOOLEAN / INTEGER | NOT NULL / NULL | `option_tenor` bit / `option_tenor_real` int | [VERIFIED][INTENT] | |
| `tenor_actual_note` | VARCHAR(255) | NULL | `option_tenor_real_others` varchar(255) | [VERIFIED][INTENT] | |
| `umc_disbursement_matches` / `umc_disbursement_actual` | BOOLEAN / NUMERIC(18,2) | NOT NULL / NULL | `option_disbursement_UMC` bit / `option_disbursement_UMC_real` numeric(9,0) | [VERIFIED][INTENT] | Nilai pencairan via UMC/financing-house. |
| `umc_disbursement_actual_note` | VARCHAR(255) | NULL | `option_disbursement_UMC_real_others` varchar(255) | [VERIFIED][INTENT] | |
| `down_payment_matches` / `down_payment_actual` | BOOLEAN / NUMERIC(18,2) | NOT NULL / NULL | `option_consumen_payment` bit / `option_consumen_payment_real` numeric(9,0) | [VERIFIED][INTENT] | "consumen_payment" legacy = DP setor konsumen (padanan CFV `OptDPSetor` — §3.4). |
| `down_payment_actual_note` | VARCHAR(255) | NULL | `option_consumen_payment_real_others` varchar(255) | [VERIFIED][INTENT] | |
| `admin_fee_other` / `admin_fee_other_amount` | BOOLEAN / NUMERIC(18,2) | NOT NULL / NULL | `admin_fee_other` bit / `admin_fee_other_amount` numeric(9,0) | [VERIFIED][INTENT] | Conditional: applicant menyanggah admin fee standar. |
| `email_matches` / `email_actual` / `email_original` | BOOLEAN / VARCHAR(60) / VARCHAR(60) | NOT NULL / NULL / NULL | `option_email` bit / `email_real` / `email_original` | [VERIFIED][INTENT] | `_original` = snapshot sisi confirmed (legacy simpan — dipertahankan); guard submit BR-06-09. |
| `mobile_phone_matches` / `mobile_phone_actual` / `mobile_phone_original` | BOOLEAN / VARCHAR(20) / VARCHAR(20) | NOT NULL / NULL / NULL | `option_mobile_phone` bit / `mobile_phone_real` / `mobile_phone_original` | [VERIFIED][INTENT] | Guard submit BR-06-09. |
| `item_receiver_name` / `item_receiver_relation` | VARCHAR(100) / VARCHAR(100) | NULL | `item_receiver_name` / `item_receiver_relation` | [VERIFIED][INTENT] | Wajib saat submit (FE `65 §3a`). |
| `item_receiver_relation_note` | VARCHAR(255) | NULL | `item_receiver_name_relation_others` varchar(255) | [VERIFIED][INTENT] | |
| `stnk_receiver_relation` / `stnk_receiver_relation_note` | VARCHAR(5) / VARCHAR(255) | NULL | `STNK_receiver_relation` / `STNK_receiver_relation_others` | [VERIFIED][INFERRED semantik kode] | Kode relasi pemilik STNK; katalog nilai kode diverifikasi saat migrasi. |
| `item_usage_relation` / `item_usage_relation_note` | VARCHAR(5) / VARCHAR(255) | NULL | `item_usage_relation` / `item_usage_relation_others` | [VERIFIED][INFERRED semantik kode] | Relasi pemakai unit. |
| `item_received_origin` | VARCHAR(5) | NULL | `item_received_origin` varchar(5) | [VERIFIED][INFERRED semantik kode] | Kode lokasi terima unit (padanan CFV `LokasiMotorDiTerima` — §3.4). |
| `dealer_origin` | VARCHAR(255) | NULL | `dealer_origin` varchar(255) | [VERIFIED][INTENT] | |
| `asset_type_description_note` | VARCHAR(255) | NULL | `asset_type_description_answer_others` varchar(255) | [VERIFIED][INTENT] | |
| `changed_phone_number` / `changed_phone_number_relation` / `changed_phone_number_pic` | VARCHAR(20) / VARCHAR(20) / VARCHAR(100) | NULL | `changed_phone_number` / `changed_phone_number_relation` / `PIC_changed_phone_number` | [VERIFIED][INTENT] | Capture perubahan nomor kontak saat wawancara. |
| `mother_name` | VARCHAR(100) | NULL | `mother_name` varchar(100) | [VERIFIED][INFERRED semantik] | Probe identitas wawancara (bandingkan Dukcapil `nama_ibu` — read-only, §8). |
| `destination_account_matches` / `destination_account_no` | BOOLEAN / VARCHAR(20) | NOT NULL / NULL | `bank_account_no` bit / `bank_account_no_real` varchar(20) | [VERIFIED][INTENT] | Panel rekening tujuan pencairan (conditional BR-06-10). |
| `destination_account_name_matches` / `destination_account_name` | BOOLEAN / VARCHAR(100) | NOT NULL / NULL | `bank_disbursement_name` bit / `bank_disbursement_name_real` varchar(100) | [VERIFIED]; kontrol name-match **[LOCKED]** | WAJIB token-match nama applicant (BR-06-11) — enforce application layer (bukan CHECK). |
| `destination_bank_name_matches` / `destination_bank_name` | BOOLEAN / VARCHAR(100) | NOT NULL / NULL | `bank_source_name` bit / `bank_source_name_real` varchar(100) | [VERIFIED][INTENT] | Nama bank free-text legacy — nilai asli dipertahankan utk rekonsiliasi migrasi. |
| `destination_bank_id` | BIGINT (FK `mst_bank`) | NULL | — (BARU) | [KEPUTUSAN DESAIN BARU] | Normalisasi free-text → FK (transform lookup dari `destination_bank_name`; unmatched → NULL + baris laporan rekonsiliasi, BUKAN tebak-tebakan). |
| `requested_due_date` | VARCHAR(100) | NULL | `request_of_due_date` varchar(100) | [VERIFIED][INTENT] | Legacy free-text ("PermintaanJT"); normalisasi ke DATE = USULAN utk input FE baru — migrasi membawa nilai apa adanya. |
| `reference_source` | VARCHAR(255) | NULL | `ms_references_source` varchar(255) | [VERIFIED][INFERRED semantik] | Sumber data referensi yang dikonfirmasi. |
| `verification_result` | VARCHAR(2) | NULL | `verification_result` varchar(2) | [VERIFIED] dipakai; semantik nilai **[OPEN]** | Dibaca layar detail (`sp_get_data_VerifikasiKonsumen:199`); katalog nilai 2-char didaftarkan saat migrasi. |
| `other_notes` | TEXT | NULL (wajib saat submit) | `other_notes` varchar(300) | [VERIFIED][INTENT] | |
| `created_at` / `created_by` | TIMESTAMPTZ NOT NULL / VARCHAR(50) NOT NULL | NOT NULL | `created_on` / `created_by` varchar(60) | [VERIFIED][INTENT] | Kolom wajib §4; legacy varchar(60) → 50: rekonsiliasi migrasi cek panjang aktual (>50 → keputusan eksplisit, bukan truncate diam-diam). |
| `updated_at` / `updated_by` | TIMESTAMPTZ NOT NULL / VARCHAR(50) NOT NULL | NOT NULL | `last_updated_on` / `last_updated_by` varchar(60) | [VERIFIED][INTENT] | NULL legacy → copy dari created. |
| `version` | INTEGER NOT NULL DEFAULT 0 | NOT NULL | — (BARU) | [KEPUTUSAN DESAIN BARU] | Optimistic locking §4 (draft/correction diedit user). |

Kolom legacy yang TIDAK dibawa ke header (disposisi eksplisit — melengkapi 66/66):

| Kolom legacy | Disposisi | Alasan |
|---|---|---|
| `confirmation_date_2`, `confirmation_time_2`, `contacted_option_2`, `confirmation_date_3`, `confirmation_time_3`, `contacted_option_3` (6) | **Normalisasi** → `trx_customer_verification_contact_attempt` | Kolom polimorfik posisi (attempt `_2`/`_3`) dilarang DB-CONVENTIONS §6.4 → typed rows. |
| `approved_date_npp` | **[ARTIFACT — discard]** | Denormalisasi lintas modul (tanggal approve NPP = data milik 05 — §6.3) DAN zero referensi di seluruh SP corpus (negative grep) — kolom mati; derive via 05 bila dibutuhkan report. |
| `contacted_count` | **discard (derived)** | = COUNT baris attempt child; disimpan ganda = larangan §6.3. Direkonsiliasi saat migrasi (count legacy vs COUNT(*) child). |

**`trx_customer_verification_contact_attempt`** — riwayat percobaan kontak telepon per verifikasi (normalisasi kolom posisi di atas).

| Kolom target | Tipe target | Null | Asal legacy | Marker | Catatan |
|---|---|---|---|---|---|
| `id` | BIGINT identity (PK) | NOT NULL | — | — | |
| `verification_id` | BIGINT (FK `trx_customer_verification`) | NOT NULL | `credit_id` (join) | [INTENT] | `ux_trx_customer_verification_contact_attempt_verification_id_attempt_no`. |
| `attempt_no` | SMALLINT | NOT NULL | posisi kolom (1 / `_2` / `_3`) | [VERIFIED][INTENT] | Target tidak dibatasi 3 (legacy hard-cap 3 attempt = artefak lebar tabel). |
| `contact_status` | VARCHAR(20) CHECK | NULL | `contacted_option[_2/_3]` int | [VERIFIED][INTENT] | Enum sama dgn header. |
| `contacted_at` | TIMESTAMPTZ | NULL | `confirmation_date[_2/_3]` + `confirmation_time[_2/_3]` | [VERIFIED][INTENT] | Merge date+time per attempt. |
| `created_at/by`, `updated_at/by` | per §4 | NOT NULL | audit header | [INTENT] | |

Migrasi: tiap triplet non-null → satu baris (attempt 1..3); header `contact_status`/`confirmation_at` = attempt terakhir non-null. **Write-path target [OPEN]**: kontrak API utk mencatat attempt baru per panggilan BELUM didefinisikan di §4/§5 (payload E3/E4 hanya membawa contact status/waktu final) — lihat FE-06 GAP-FE06-07; jangan diselesaikan diam-diam.

**`trx_customer_verification_document`** — checklist dokumen per verifikasi (entity `VERIFICATION_DOCUMENT_CHECK`).

- **Kelas**: `trx_`. **Mapping asal**: `tr_verification_customer_application_in` (PK legacy komposit `credit_id`+`field_id`; `VertelRepositoryEF.cs:278-326,700-767`). Census 11/11.
- **Kunci**: PK `id`; `ux_trx_customer_verification_document_verification_id_document_field_id`.

| Kolom target | Tipe target | Null | Asal legacy | Marker | Catatan |
|---|---|---|---|---|---|
| `id` | BIGINT identity (PK) | NOT NULL | — (BARU) | — | |
| `verification_id` | BIGINT (FK) | NOT NULL | `credit_id` varchar(20) (PK part) | [VERIFIED][INTENT] | Re-key ke versi verifikasi; `credit_id` TIDAK diduplikasi (derive via header — §6.3). |
| `document_field_id` | INTEGER (FK `mst_document_field`) | NOT NULL | `field_id` int (PK part) | [VERIFIED][INTENT] | Katalog per asset-kind + disbursement (legacy `sp_get_DocumentField`; BR-06-08). |
| `presence` | VARCHAR(20) CHECK (`present\|confirmed_missing\|unchecked`) NOT NULL DEFAULT `unchecked` | NOT NULL | `is_true` bit NULL | [VERIFIED] nilai; 3-state **[OPEN]** OQ-NPPVTL-03 | Boolean nullable dilarang (§3) → enum; migrasi: 1→`present`, 0/NULL→`unchecked`; `confirmed_missing` = USULAN (legacy opsi "Tidak Ada" permanently disabled — `65 §9 EC4`). |
| `file_name` | VARCHAR(255) | NULL | `application_file_name` varchar(max) | [VERIFIED][INTENT] | |
| `file_object_key` | VARCHAR(500) | NULL | `application_file_path` varchar(max) | [VERIFIED][INTENT] | File di object storage, bukan BLOB/path OS (§3); path legacy dimigrasi apa adanya sebagai key transisi. |
| `photo_id` / `photo_type_id` | VARCHAR(10) / VARCHAR(10) | NULL | `photo_id` / `photo_type_id` varchar(10) | [VERIFIED][INFERRED semantik] | Referensi photo-store legacy (`sp_get_file_verification_customer`); dipertahankan utk interop retrieval. |
| `created_at/by`, `updated_at/by` | per §4 | NOT NULL | `created_on/by`, `last_updated_on/by` | [VERIFIED][INTENT] | |

**`trx_customer_verification_support_document`** — dokumen pendukung rekening tujuan (min. 2 saat destination bank dipilih — BR-06-12).

- **Kelas**: `trx_`. **Mapping asal**: `[KEPUTUSAN DESAIN BARU]` — TANPA padanan tabel legacy langsung: legacy meng-upload di save action (`VertelController.cs:335-377`) ke file server; metadata-nya tidak terlokasi di DDL acquisition. Bila tabel file legacy ditemukan saat migrasi → tambahkan lane `stg_` (ADR-15).

| Kolom target | Tipe target | Null | Marker | Catatan |
|---|---|---|---|---|
| `id` (PK), `verification_id` (FK) | BIGINT | NOT NULL | — | |
| `doc_label` | VARCHAR(100) | NOT NULL | [INTENT] | |
| `file_name` / `file_object_key` | VARCHAR(255) / VARCHAR(500) | NOT NULL | [INTENT] | Object storage (§3). |
| `created_at/by`, `updated_at/by` | per §4 | NOT NULL | [INTENT] | Guard "min. 2 dokumen" = application layer (BR-06-12), bukan constraint DB. |

**Audit keputusan chain VK → `log_approval_history` TERPUSAT (milik 03)** — **RESOLVED by convention (2026-07-14, OQ-VTL-06 §11)**; dasar: **DB-CONVENTIONS §8** ("keputusan/riwayat approval tetap ditulis ke `log_approval_history`"), **ADR-13d**, konsistensi **BE-05 §3.1.12** (lane NPP memakai log terpusat yang sama) + **registry BE-00 §6.3** (`tr_hierarchy_transaction` di-target-kan tanpa slice `VK`). Tabel per-modul `log_verification_approval` **TIDAK dibuat**; keputusan RFA Vertel + approve Kepala Cabang ditulis ke `log_approval_history` dengan diskriminator **`module_context='vertel'` / `entity_type='VK'`** (menggantikan pemodelan 2-tabel legacy `tr_hierarchy_transaction` type `VK` + arsip `tr_hierarchy_approval_transaction` — `sp_get_history_vertel.sql:16-46`).

- **Kelas**: `log_` — INSERT-only, tidak pernah UPDATE/DELETE (§1); hanya `created_at`/`created_by` (§4). Per **ADR-13 + DB-CONVENTIONS §8**: state in-flight (siapa pending di level berapa) hidup di **Flowable** (`ACT_RU_TASK` dkk — milik engine, JANGAN disentuh manual), TIDAK dimodelkan sebagai tabel bisnis; audit regulatori independen dari engine WAJIB ditulis ke `log_approval_history` **dalam transaksi yang sama** dengan keputusan (engine BUKAN satu-satunya sumber audit). **Catatan seam — RESOLVED by convention (OQ-VTL-06, 2026-07-14)**: dipilih log **terpusat** `log_approval_history` (milik 03), BUKAN log per-modul — dasar: DB-CONVENTIONS §8, ADR-13d, sejalan BE-05 §3.1.12 + registry BE-00 §6.3; menghapus risiko double-migrate slice VK (lihat catatan migrasi di bawah). Penulisan slice VK oleh 06 mengikuti pola BE-05 §3.1.12 — mekanisme write (port/API internal modul 03 vs kontrak cross-cutting) per ADR-03 diserahkan ke definisi tabel otoritatif di BE-03 §3.1; outcome append-only independen dari engine tidak berubah.

Field census di bawah = **kontribusi slice VK** yang WAJIB terwakili di `log_approval_history` (definisi tabel otoritatif: BE-03 §3.1; `module_context`/`entity_type` = kolom diskriminator):

| Kolom target | Tipe target | Null | Asal legacy | Marker | Catatan |
|---|---|---|---|---|---|
| `id` | BIGINT identity (PK) | NOT NULL | — | — | Fix larangan §6.5 (increment manual). |
| `verification_id` | BIGINT (FK) | NOT NULL | join via `trans_id`/`credit_id` | [INTENT] | |
| `credit_id` | VARCHAR(20) | NOT NULL | `tr_hierarchy_transaction.credit_id` | [VERIFIED]**[LOCKED]** | Kunci korelasi lintas modul + history legacy. |
| `level` | INTEGER | NOT NULL | level hierarki VK | [INTENT] + **[OPEN]** OQ-VTL-03 | Default target = 1 (Kepala Cabang); multi-level per skala risiko (D-10) via `cfg_hierarchy_matrix`. |
| `actor` / `actor_role` | VARCHAR(50) / VARCHAR(30) | NOT NULL | `employee_id` (+ resolusi role) | [INTENT] | Checker target = `KEPALA_CABANG`; maker RFA juga dicatat (action `submitted`). |
| `action` | VARCHAR(20) CHECK (`submitted\|approved\|rejected\|correction`) | NOT NULL | status `0/A/R/C/V` | **[LOCKED]** enum kanonik umbrella APPROVAL_STEP (subset) | `pending` TIDAK dicatat (in-flight = Flowable task); approve interim legacy `V` = baris `approved` dgn `is_final=false` — bukan enum baru (BR-06-17). |
| `is_final` | BOOLEAN NOT NULL | NOT NULL | `is_approver = 1` | [INTENT] | Membedakan approve terminal vs eskalasi level berikut. |
| `reason_id` / `reason_description` | BIGINT (FK `mst_approval_reason`) / TEXT | `reason_id` NOT NULL utk decision | reason master `ms_CAS_approval_reason` | [INTENT] | Decision type di-derive dari reason master (BR-06-07). |
| `process_instance_id` / `task_id` | VARCHAR(64) | NULL | — (BARU) | [KEPUTUSAN DESAIN BARU] | Korelasi Flowable — key-only, payload tetap di `trx_` (ADR-13c). |
| `created_at` / `created_by` | TIMESTAMPTZ / VARCHAR(50) | NOT NULL | `trans_date` dkk | [INTENT] | = `acted_at`; log_ append-only (§4). |

Migrasi — **satu pintu di lane BE-03 (RESOLVED by convention, OQ-VTL-06)**: baris `tr_hierarchy_transaction` type `VK` + arsip approval ikut MIGRATE bersama lane BE-03 → `log_approval_history` **tanpa exclusion** (satu baris per keputusan), di-stamp `module_context='vertel'`/`entity_type='VK'`. 06 **TIDAK** menjalankan lane migrasi VK sendiri — risiko double-migrate hilang; rekonsiliasi ADR-15 memverifikasi coverage + stamping baris `VK` di lane 03. Chain in-flight saat cutover = strategi OQ-MIG-01 (ADR-15).

**Footprint Flowable modul ini (ADR-13; DB-CONVENTIONS §8)** — RFA Vertel + approve Kepala Cabang = human-task flow:

- Satu process definition BPMN `vertel_approval` (versioned di repo): E6 RFA start process → user task "Putusan Vertel" (candidate group dari `cfg_hierarchy_matrix`, trans-type `VK`, per product-line — BR-06-18: SATU definisi + variabel product-line, BUKAN dua BPMN car/motor) → service task menulis hasil ke `trx_customer_verification` + `log_approval_history` (terpusat, `module_context='vertel'` — OQ-VTL-06 RESOLVED by convention).
- Variabel proses hanya key (`credit_id`, `verification_id`) — payload wawancara tetap di `trx_` (ADR-13c).
- **No self-approval dua lapis**: task assignment meng-exclude `submitted_by` + service guard BR-06-05 (ADR-13e); tanpa super user (D-09 **[LOCKED]**).
- Resume chain (BR-06-06) = korelasi ke process instance aktif per `verification_id`; TIDAK start instance duplikat.
- Kedalaman chain (1 level default vs multi-level) = data `cfg_hierarchy_matrix`, bukan hardcode BPMN (OQ-VTL-03; D-10).
- Tabel `ACT_*` = milik engine, di luar konvensi schema; layar history E8 membaca `log_approval_history` (filter `module_context='vertel'`), BUKAN query `ACT_HI_*`.

### 3.2 Shared entities / references yang dikonsumsi (read-only — nama target konvensi)

| Target | Legacy | Pemilik | Peran di 06 | Sumber |
|---|---|---|---|---|
| `trx_application` + `trx_credit_memo` | `tr_CAS` / `tr_CM` | 01 / 04 | Pre-fill sisi "confirmed" wawancara (tenor, DP, angsuran, admin fee, item type dari CM final); prasyarat CM `approved`. | `30-verif §5.1`; `65 §4` |
| `trx_purchase_order` | (PO — 04) | 04 | **Prasyarat antrean** target-state: PO exists (STEP 13) — D-02. | GT `:59-65`; D-02 |
| `mst_approval_reason` | `ms_CAS_approval_reason` | shared references | Katalog reason; kolom `type` men-derive decision (1→approved, 2→rejected, 3→correction, 4→verify-interim). `[INTENT]` governance terpusat (BR-VERIF-5). | `sp_approve_vertel.sql:61-68` |
| `mst_document_field` | katalog di balik `sp_get_DocumentField` | references (master) | Definisi dokumen wajib per asset-kind + `application_type_id` (mis. `'03'` auto-debit → bukti rekening). | `30-verif §7 BR-VERIF-9` |
| `mst_bank` | master bank | references | Target FK `destination_bank_id` (normalisasi §3.1). | `65 §7 BR-NPPVTL-12` |
| `cfg_hierarchy_matrix` | `ms_hierarchy_transaction` (+ routing SP `_R4`) | config engine (BE-00) | Resolusi level/role chain VK per product-line — dibaca delegate Flowable (ADR-13b; BR-06-18). | `30-verif §7 BR-VERIF-3` |
| `CUSTOMER` | `tr_CIF` | penulisan otoritatif 05 | Nama applicant utk token-match rekening tujuan (read-only); NIK utk lookup Dukcapil. | `65 §7 BR-NPPVTL-13` |
| **Dukcapil result replica** (`FCL_Dukcapil_Hdr`/`_Dtl`) | linked-server `[MACF-DBMCF].MACFDB` | eksternal (read-only replica) | Display informational by NIK via ACL — §8. **TIDAK ada tabel target milik 06** sebelum OQ-DUKCAPIL-01 menjawab mekanisme populate; field identitas + per-field match scores `[LOCKED]` (regulated). | `dukcapil.md §4-§6 [LOCKED]` |

### 3.3 Mapping status: legacy → kolom `status` → proyeksi kanonik

Patuh DB-CONVENTIONS §5 (**SATU kolom status**): yang TERSIMPAN hanya `trx_customer_verification.status` (vocabulary state machine §7). Status kanonik cross-service umbrella §7.2 (`pending|verified|failed|recheck`) = **proyeksi read-time** (E11 / read-model) — fungsi deterministik dari (`status`, `expires_at`), BUKAN kolom kedua:

| Legacy `verification_status` | Kolom `status` (tersimpan) | Proyeksi kanonik (E11) | Catatan |
|---|---|---|---|
| `D` (Draft) | `draft` | `pending` | Editable, di luar chain formal. |
| `0` (RFA) | `rfa` | `pending` | Process instance Flowable aktif. |
| `V` (Verified interim) | `verified_interim` | `pending` | HANYA bila chain multi-level (OQ-VTL-03). **Jangan dikonflasi** dgn `verified` kanonik. |
| `A` (Approved), `now ≤ expires_at` | `approved` | **`verified`** | Membuka gate NPP/BPKB; `verified_at` + `expires_at` terisi. |
| `A` (Approved), `now > expires_at` | `approved` (baris TIDAK berubah) | **`recheck`** | **BARU per D-01 Step 14** (USULAN default; OQ-MEET-05). Proyeksi murni — tanpa UPDATE scheduler pada baris; scheduler expiry (§4) hanya utk notifikasi/re-queue eventing, bukan sumber kebenaran. |
| `C` (Correction) | `correction` | `pending` | Kembali ke maker (S1). |
| `R` (Rejected) | `rejected` | `failed` | Terminal utk baris; aplikasi **re-workable** via versi baru (BR-06-13 — fix EC1; `superseded_at` §3.1). |

> **Kontrak API**: field `workflow_state` di §5/§7 = kolom `status` tersimpan; field `status` di §5/§7 = proyeksi kanonik — dua field API, SATU kolom DB. Konsumen cross-service (05, BPKB) HANYA membaca proyeksi kanonik + `expires_at` via E11 — bukan kolom internal. Mekanisme kode 1-karakter legacy bebas didesain ulang; **outcome** "verifikasi mendahului legalisasi/kolateral" WAJIB (BR-VERIF-6 nota mutability `[INTENT]`).

### 3.4 Tabel legacy TIDAK dibawa + temuan relasi `tr_verification_customer` × `CFVerifikasiKonsumen`

**Disposisi**: `CFVerifikasiKonsumen` (36 kolom) dan `CFVerifikasiKonsumen_AplikasiIN` (9 kolom) = **`[ARTIFACT — discard]`** (BR-06-21; OQ-DATA-05 resolved: kanonik = `tr_verification_customer`). JANGAN drop fisik sebelum audit penulis eksternal (**[OPEN]** OQ-VERIF-03); migrasi tetap meng-extract keduanya ke `stg_legacy_cfverifikasikonsumen` / `stg_legacy_cfverifikasikonsumen_aplikasi_in` untuk arsip + rekonsiliasi (ADR-15) — TIDAK ditransform ke `trx_`.

**Hubungan dua tabel** (analisis DDL + grep SP corpus `SP/FC_ACQ_MCF/`) — kesimpulan: **bukan duplikat paralel yang sama-sama hidup, melainkan varian generasi-1 (predecessor) yang digantikan `tr_verification_customer`** `[INFERRED]` dari tiga bukti konvergen:

1. **Struktural** — ±28 dari 36 kolom `CFVerifikasiKonsumen` punya padanan 1:1 di `tr_verification_customer`, hanya berganti bahasa (ID→EN) dan kunci: `StatusVerifikasi`→`verification_status`, `OptBisaDiHubungi`→`contacted_option`, `TglTerimaTagihan`→`billing_date`, `TglKonfirmasi`/`JamKonfirmasi`→`confirmation_date`/`_time`, `TglApproveNPP`→`approved_date_npp`, `OptTglTerimaMotor(+Sebenarnya)`→`option_item_received_date(_real)`, `OptTipeMotor`→`option_item_type`, `OptAngsuran`→`option_installment`, `OptTenor`→`option_tenor`, `OptDPSetor`→`option_consumen_payment`, `OptPencairanMB`→`option_disbursement_UMC`, `NamaPenerimaMotor`/`HubunganPenerimaMotor`→`item_receiver_name`/`_relation`, `PermintaanJT`→`request_of_due_date`, `CatatanLain`→`other_notes`, `BiayaAdminLainnya`/`JumlahNominalAdmin`→`admin_fee_other(_amount)`, audit 4 kolom. `tr_verification_customer` = rename EN + **ekstensi ±30 kolom** (attempt 2/3, changed-phone, kode STNK/usage, trio rekening, email/mobile, `mother_name`, dst.) + **re-key**: CFV ber-PK `VerifikasiNo` (generated `fnEPGenVerifikasiNo(101)`) dgn kunci bisnis `CMNo`/`AgreementNumber`; `tr_verification_customer` ber-PK `credit_id`. Kolom khas "Motor" (`NamaSTNK`/`NamaPasanganSTNK`, `LokasiMotor*`) menandai asal era motorcycle-only.
2. **Caller** — `CFVerifikasiKonsumen` hanya disentuh 2 objek di seluruh SP corpus: `sp_insert_VerifikasiKonsumen` (INSERT; **zero live caller** — hanya EF scaffolding, `30-verif §9 EC4 [VERIFIED]`) dan `spGetListNewVerteltest` (SP ber-suffix "test"); pasangan produksinya `spGetListNewVerteltestPaging` justru SUDAH membaca `tr_verification_customer`. `CFVerifikasiKonsumen_AplikasiIN`: **ZERO referensi** di seluruh SP corpus (negative grep) — lebih mati dari induknya; padanan strukturalnya (`CMNo`+`IDBindField`+`IsTrue`+file) = `tr_verification_customer_application_in` (`credit_id`+`field_id`+`is_true`+file, + `photo_id`/`photo_type_id` ekstensi).
3. **Nama SP menipu** — keluarga SP produksi ber-nama Indonesia (`sp_get_data_VerifikasiKonsumen`, `_motor`, `_Stg`) faktanya membaca `tr_verification_customer` (`sp_get_data_VerifikasiKonsumen:199` `FROM ... [tr_verification_customer] vk`), BUKAN `CFVerifikasiKonsumen` — nama SP dipertahankan tetapi di-repoint ke tabel generasi-2. Jangan memakai nama SP sebagai sinyal kepemilikan tabel saat menyusun mapping migrasi.

Yang TIDAK terjawab dari DDL + SP corpus (tetap **[OPEN]** → OQ-VERIF-03): (a) apakah data historis CFV pernah dimigrasi ke `tr_verification_customer` atau baris era-CFV hanya tertinggal; (b) apakah ada tool/report **eksternal** yang menulis/membaca CFV langsung (di luar codebase ini); (c) apakah `sp_insert_VerifikasiKonsumen`/`spGetListNewVerteltest` masih dipanggil ad-hoc oleh operasi. Konsekuensi migrasi: data era-CFV diarsip via `stg_` tanpa transform (tidak ada gate aktif yang bergantung padanya — status verified era itu by definition sudah lewat 30-day expiry D-01 S14).

**Registry-note (slice VK — OQ-VTL-06 RESOLVED by convention, 2026-07-14)**: migrasi `tr_hierarchy_transaction` type `VK` + `tr_hierarchy_approval_transaction` = **satu pintu di lane BE-03** → `log_approval_history` tanpa exclusion, di-stamp `module_context='vertel'`/`entity_type='VK'` (§3.1). 06 TIDAK punya lane migrasi VK sendiri; registry BE-00 §6.3 tetap men-target-kan tabel hierarchy ke 03. Dasar: DB-CONVENTIONS §8 + ADR-13d; rekonsiliasi ADR-15.

---

## 4. API Endpoint

Kontrak level resource+field. Transport final (REST/gRPC/message-bus) = keputusan arsitektur ITEC (D-11) → [OPEN] OQ-ARCH-STACK; path/verb di bawah ilustratif resource-shape (konsisten BE-01..BE-05). Semua endpoint mutasi menerima header `Idempotency-Key` (umbrella §7.4); error envelope `{ code, message, details?, correlation_id }`.

| # | Method | Path | Deskripsi | Auth/Role | Sumber legacy |
|---|---|---|---|---|---|
| E1 | GET | `/vertel/queue` | Antrean kerja Vertel: aplikasi eligible (PO terbit + CM approved + tanpa verifikasi aktif, ATAU `rejected`/`recheck` re-workable). Paginated + search. **Satu implementasi** (fix EC1/EC2/EC7). | Admin Cabang | `sp_get_pagination_lookup_vertel` / `spGetListNewVertelLookUpPaging` (konsolidasi) |
| E2 | GET | `/vertel/verifications` | Listing record verifikasi (semua status) + label status — utk layar list/monitoring. | Branch staff (read) | `sp_get_pagination_vertel` |
| E3 | POST | `/vertel/verifications` | Create record wawancara utk satu `application_id` (Draft, atau langsung RFA via `save_mode`). Guard: eligibility antrean; maksimal satu verifikasi aktif. | Admin Cabang | `InsertVertel` (`VertelRepositoryEF.cs:118-441`) |
| E4 | PATCH | `/vertel/verifications/{id}` | Update field wawancara + checklist selama `draft`/`correction`. | Admin Cabang | `UpdateVertel` (`:443-894`) |
| E5 | GET | `/vertel/verifications/{id}` | Detail record + checklist + flags approval (`is_pending_approver`, `has_next_level`) utk gating layar FE (fix `65 §9 EC1`). | Branch staff / Kepala Cabang | `sp_get_data_VerifikasiKonsumen` |
| E6 | POST | `/vertel/verifications/{id}/rfa` | **RFA Vertel** (submit maker→checker). Idempotent; bila chain VK masih open → **resume** (BR-VERIF-4). | **Admin Cabang** | `SaveType='R'` path + `sp_update_status_approval_vertel` |
| E7 | POST | `/vertel/verifications/{id}/decision` | Keputusan checker: `approve` / `reject` / `correction` via `reason_id` (type di-derive dari reason master). No self-approval; final approve → `verified` + `verified_at` + `expires_at`. | **Kepala Cabang** | `sp_approve_vertel.sql:7-385` (fix EC3) |
| E8 | GET | `/vertel/verifications/{id}/history` | Riwayat keputusan chain VK (step + arsip). | Branch staff (read) | `sp_get_history_vertel.sql` |
| E9 | GET | `/vertel/document-fields?application_id=` | Definisi checklist dokumen wajib per asset-kind + disbursement method utk aplikasi tsb. | Admin Cabang | `sp_get_DocumentField` (BR-VERIF-9) |
| E10 | POST | `/vertel/verifications/{id}/documents` | Upload file checklist / dokumen pendukung rekening (multipart). | Admin Cabang | upload path save action (`VertelController.cs:335-377`) |
| E11 | GET | `/vertel/verifications/by-application/{applicationId}/gate-status` | **Read API gate** utk 05-npp & BPKB queue: `{ status, verified_at, expires_at, is_fresh }`. Dibaca 05 **in-transaction**. | service-to-service (05, BPKB) | gate join di `sp_get_pagination_lookup_npp_process_credit.sql:44-85` dkk (BR-VERIF-6) |
| E12 | GET | `/vertel/verifications/{id}/print` | Dataset cetak form verifikasi + checklist (tersedia setelah `verified`). | Branch staff | `VertelReportController.cs:1-30` |
| E13 | GET | `/dukcapil/results?nik=` · `/dukcapil/results/{nik}` | Read-only hasil match Dukcapil (list ringkas + detail per-field scores). **Informational, tanpa gate coded** (BR-VERIF-8; OQ-VERIF-01). | Back-office viewer | `sp_get_ResultDukcapil`, `sp_get_pagination_result_dukcapil`; `DukcapilResultController.cs:35-45` |

> **USULAN (bukan requirement bersumber)**: event `CustomerVerificationApproved` di-publish setelah commit E7-final-approve (notifikasi; konsumen tetap re-read E11 otoritatif). Scheduler internal expiry (bukan endpoint publik) HANYA memicu notifikasi/re-queue eventing saat `expires_at` lewat — proyeksi `verified → recheck` sendiri deterministik read-time dari `expires_at`, tanpa UPDATE baris (§3.3; OQ-MEET-05).

---

## 5. Kontrak Request/Response

### E1 — GET /vertel/queue

Query: `page`, `size`, `search?` (nama/credit_id), `branch_id`.

Response `200 OK`:

```json
{
  "items": [
    {
      "application_id": "APP-2026-0001",
      "credit_id": "300012345678",
      "customer_name": "…",
      "po_number": "PO-2026-0091",
      "po_issued_at": "2026-07-10",
      "cm_approved_at": "2026-07-09",
      "queue_reason": "never_verified",     // never_verified | rejected_rework | recheck_expired
      "previous_verification_id": null       // terisi utk rejected_rework/recheck_expired
    }
  ],
  "page": 1, "size": 20, "total": 57
}
```

Aturan komposisi (server-side, satu implementasi): `po exists` **AND** `cm.status = approved` **AND** (tidak ada verifikasi aktif **OR** verifikasi terakhir `rejected`/`recheck`). Predicate ter-parameterisasi eksplisit — dilarang meniru string-concat precedence-ambiguous legacy (`30-verif §9 EC7 [ARTIFACT]`).

### E3 / E4 — Create / Update wawancara

Request E3 (field wajib `*`; wajib-saat-submit ditandai `†` — boleh kosong di Draft, divalidasi saat RFA):

```json
{
  "application_id": "APP-2026-0001",             // *
  "save_mode": "draft",                          // * draft | rfa
  "contact_status": "contacted",                 // *
  "confirmation_datetime": "2026-07-14T10:30:00Z",
  "billing_date": "2026-08-01",
  "delivery": { "confirmed": "2026-07-16", "actual": "2026-07-16" },
  "item_type": { "confirmed": "…", "actual": "…" },
  "installment_amount": { "confirmed": 7000000, "actual": 7000000 },
  "tenor": { "confirmed": 36, "actual": 36 },
  "down_payment": { "confirmed": 50000000, "actual": 50000000 },
  "email": { "confirmed": "a@b.c", "actual": null },        // † min. satu non-empty
  "mobile_phone": { "confirmed": "08xx", "actual": null },  // † min. satu non-empty
  "item_receiver": { "name": "…", "relation": "spouse" },   // †
  "requested_due_date": "2026-08-05",
  "other_admin_fee": { "flag": false, "amount": null },
  "destination_bank_account": {                  // conditional — car retail non-UMC (BR-06-10)
    "bank_id": "BNK-014", "account_no": "1234567890",
    "account_name": "NAMA APPLICANT"             // WAJIB token-match nama applicant (BR-06-11 [LOCKED])
  },
  "other_notes": "…",                            // †
  "document_checks": [
    { "document_field_code": "KTP_PHOTO", "presence": "present", "file_id": "F-001" }
  ]
}
```

> **Binding kontrak ↔ schema (§3.1)**: pasangan `{confirmed, actual}` di payload adalah bentuk API — sisi `confirmed` divalidasi server terhadap CM lalu di-derive menjadi kolom `x_matches BOOLEAN` + `x_actual` (TIDAK dipersist ulang nilainya — anti-denormalisasi DB-CONVENTIONS §6.3), kecuali `email`/`mobile_phone` yang snapshot `_original`-nya memang disimpan (guard BR-06-09). Contoh payload **non-exhaustive**: field wawancara lain per census §3.1 (a.l. pasangan `umc_disbursement`, varian catatan `_note` "lainnya", `stnk_receiver_relation`, `item_usage_relation`, `item_received_origin`, `dealer_origin`, `asset_type_description_note`, `changed_phone_number*`, `mother_name`, `reference_source`) mengikuti pola field yang sama — kontrak field lengkap = §3.1 (surface capture FE = GAP-FE06-08 di FE-06 §11).

Response `201 Created` (E3) / `200 OK` (E4):

```json
{
  "id": "VTL-2026-0042",
  "application_id": "APP-2026-0001",
  "workflow_state": "draft",
  "status": "pending",
  "created_at": "2026-07-14T10:31:00Z"
}
```

Error: `422` validasi (name-mismatch rekening → `{ "code": "DEST_ACCOUNT_NAME_MISMATCH", "details": [{ "rule": "BR-06-11" }] }`; <2 dokumen pendukung saat destination bank dipilih → `DEST_ACCOUNT_DOCS_INSUFFICIENT`, BR-06-12); `409` sudah ada verifikasi aktif utk aplikasi tsb (`VERIFICATION_ALREADY_ACTIVE`); `409` aplikasi belum eligible (PO belum terbit — `APPLICATION_NOT_ELIGIBLE`, BR-06-01).

### E6 — POST /vertel/verifications/{id}/rfa

Header WAJIB: `Idempotency-Key: <uuid>`. Request: `{ "acting_employee_id": "EMP-0007" }`.

Response `200 OK`:

```json
{
  "id": "VTL-2026-0042",
  "workflow_state": "rfa",
  "status": "pending",
  "approval_chain": { "resumed_existing": false, "current_level": 1, "expected_role": "KEPALA_CABANG" },
  "submitted_by": "EMP-0007",
  "submitted_at": "2026-07-14T11:00:00Z",
  "correlation_id": "…"
}
```

- Guard status: hanya dari `draft`/`correction` → selain itu `409 RFA_INVALID_STATE`.
- Guard kelengkapan submit (†): email & phone min. satu non-empty (BR-06-09), `item_receiver`, `other_notes`, checklist wajib terisi, guard rekening tujuan (BR-06-10..12) — gagal → `422 RFA_GATE_FAILED` + daftar `rule`, **nol perubahan state** (atomik).
- Bila chain VK masih open utk aplikasi ini → **resume** chain (`"resumed_existing": true`), TIDAK membuat chain duplikat (BR-06-06; legacy `sp_update_status_approval_vertel`).
- Retry dgn `Idempotency-Key` sama → `200` identik tanpa efek ganda.

### E7 — POST /vertel/verifications/{id}/decision

Request:

```json
{
  "reason_id": "RSN-VK-01",              // * decision type di-derive dari reason master (BR-06-07)
  "reason_description": "…",
  "acting_employee_id": "EMP-0100"       // * role KEPALA_CABANG
}
```

Response `200 OK` (approve, level final):

```json
{
  "id": "VTL-2026-0042",
  "decision": "approved",
  "workflow_state": "approved",
  "status": "verified",
  "verified_at": "2026-07-14T14:00:00Z",
  "expires_at": "2026-08-13T14:00:00Z",   // strict 30 hari (D-01 Step 14; clock start = OQ-MEET-05)
  "event_emitted": "CustomerVerificationApproved",   // USULAN
  "correlation_id": "…"
}
```

Varian: `decision: "correction"` → `workflow_state: "correction"`, record kembali editable oleh maker asal (SATU transisi eksplisit — fix EC3); `decision: "rejected"` → `workflow_state: "rejected"`, `status: "failed"`, aplikasi kembali eligible di antrean sebagai `rejected_rework` (BR-06-13). Bila multi-level aktif (OQ-VTL-03) dan approver bukan level final → `workflow_state: "verified_interim"`, step baru level berikut dibuat.

Error: `403 SELF_APPROVAL_BLOCKED` bila `acting_employee_id == submitted_by` (D-01 Step 11); `403 NOT_PENDING_APPROVER` bila bukan approver tertunda utk record ini; `409 DECISION_INVALID_STATE` bila `workflow_state` bukan `rfa`/`verified_interim`; `422 REASON_REQUIRED` bila `reason_id` kosong/tak dikenal.

### E11 — GET /vertel/verifications/by-application/{applicationId}/gate-status

Response `200 OK`:

```json
{
  "application_id": "APP-2026-0001",
  "verification_id": "VTL-2026-0042",
  "status": "verified",                   // pending | verified | failed | recheck
  "verified_at": "2026-07-14T14:00:00Z",
  "expires_at": "2026-08-13T14:00:00Z",
  "is_fresh": true                         // now < expires_at (evaluasi server 06)
}
```

Bila tidak ada verifikasi: `200` dgn `status: null` / `"none"` (05 memperlakukan sebagai gate GAGAL — fail-closed, umbrella §7.3 OQ-REG-06 resolved). Endpoint ini read-only, konsisten, dan **05 memanggilnya (atau membaca read-model replikanya) di dalam transaksi aktivasi** — kontrak konsistensi final (sync call vs read-model) menunggu arsitektur ITEC (D-11) → [OPEN] OQ-VTL-05.

### E13 — GET /dukcapil/results/{nik}

Response `200 OK` — bentuk field mengikuti replica `[LOCKED]` (regulated identity match — `dukcapil.md §5-§6`):

```json
{
  "nik": "3275xxxxxxxxxxxx",
  "submitted": { "nama_lengkap": "…", "tempat_lahir": "…", "tgl_lahir": "…", "alamat": "…", "nama_ibu": "…", "…": "…" },
  "registry":  { "nama_lengkap": "…", "nama_lengkap_score": 97.5,
                 "tempat_lahir": "…", "tempat_lahir_score": 100,
                 "alamat": "…", "alamat_score": 88.0,
                 "nama_ibu": "…", "nama_ibu_score": 95.0 },
  "threshold": 90.0,
  "result": "verified"                    // terjemahan 'Terverivikasi'/'Tidak Terverivikasi' legacy
}
```

**Per-field match scores WAJIB dibawa end-to-end** (audit/dispute detail — `dukcapil.md §10 EC4 [LOCKED]` semantics; jangan reduksi ke boolean tunggal seperti list-view legacy).

---

## 6. Aturan Bisnis

| ID | Aturan | Sumber | Marker | Catatan rebuild |
|---|---|---|---|---|
| BR-06-01 | Aplikasi masuk antrean Vertel hanya bila: **PO sudah terbit (STEP 13)** DAN CM committee-approved DAN tidak ada verifikasi aktif. | D-02; GT `:59-65`; delta atas `30-verif BR-VERIF-1` (legacy: CM-approved saja) | `[INTENT]` posisi per D-02 | Delta posisi terdokumentasi §1.3; interaksi Open CM = OQ-VTL-02. |
| BR-06-02 | Vertel adalah **step WAJIB** sebelum NPP untuk alur acquisition (STEP 14 "NEW mandatory step before NPP"). Applicability per product MACF = parameterisasi D-07. | GT `:90` deltas; D-02; D-07 | `[INTENT]`; scope per-product `[OPEN]` OQ-MEET-06 | Umbrella PRD tidak diblokir; annex per-product menunggu OQ-MEET-06. |
| BR-06-03 | Save `draft` tidak berefek workflow; hanya submit RFA yang memasukkan record ke chain approval. | `30-verif §7 BR-VERIF-2` | `[INTENT]` | Pola maker-checker sistem-wide. |
| BR-06-04 | RFA Vertel dilakukan oleh **Admin Cabang**; approval oleh **Kepala Cabang** (role eksplisit target-state). | GT `:63-65`; D-10 | `[INTENT]` role naming; sensus D-10 `[LOCKED]` | Legacy: flag generik pending-approver (OQ-NPPVTL-02); rebuild memodelkan role bernama. |
| BR-06-05 | **Self-approval DIBLOKIR**: `decision.actor ≠ rfa.submitted_by`, dicek application layer. | D-01 Step 11 | `[LOCKED]` (governance meeting) | Legacy SQL tidak meng-enforce utk VK. |
| BR-06-06 | Re-submit RFA saat chain VK masih open → **resume** chain existing, bukan chain duplikat. | `30-verif §7 BR-VERIF-4`; `sp_update_status_approval_vertel` | `[INTENT]` | Idempotensi chain. |
| BR-06-07 | Decision type (approve/reject/correction/verify) di-derive dari **reason-code master** (`type` 1/2/3/4), bukan status bebas dari approver. | `30-verif §7 BR-VERIF-5`; `sp_approve_vertel.sql:61-68` | `[INTENT]` | Katalog reason ter-governance terpusat. |
| BR-06-08 | Checklist dokumen wajib bergantung **asset-kind** + **disbursement method** (`application_type_id='03'` auto-debit → bukti rekening); tiap kehadiran dokumen tercatat sebagai row checklist. | `30-verif §7 BR-VERIF-9`; `sp_get_DocumentField.sql:7-60` | `[INTENT]` | Definisi = data (master), bukan logic hardcode. |
| BR-06-09 | Submit RFA mensyaratkan **email** dan **mobile phone/WhatsApp** masing-masing non-empty pada minimal satu representasi (confirmed atau actual). | `65-npp-vertel §7 BR-NPPVTL-15` | `[INTENT]` | Legacy hanya di FE Edit screen; rebuild = **server-side otoritatif**. |
| BR-06-10 | Car retail yang TIDAK dicairkan via UMC/auto-debit financing-house: panel rekening tujuan (bank, no., nama) WAJIB + 2 dokumen pendukung. Kondisi trigger dihitung **server-side** (satu sumber; FE membaca metadata visibility). | `65 §7 BR-NPPVTL-12`; `30-verif §3a S1` (conditional `item_id='002' AND customer_type='P' AND application_type_id != '03'`) | `[INTENT]` | Fix duplikasi 3-situs (`65 §9 EC8`). |
| BR-06-11 | Nama pemilik rekening tujuan WAJIB **token-match** (first/middle/last) nama identitas applicant; mismatch → save/submit di-block. | `65 §7 BR-NPPVTL-13` | **[LOCKED]** — kontrol anti-fraud destinasi pencairan; algoritma matching bebas, kontrolnya WAJIB | Enforce server-side; FE hanya live-warning. |
| BR-06-12 | Destination bank dipilih saat record masih Draft/initial → minimal **2 dokumen pendukung** ter-upload sebelum save diterima. | `65 §7 BR-NPPVTL-16`; `VertelController.cs:335-344` | `[INTENT]` | |
| BR-06-13 | Verifikasi `rejected` (dan `recheck`) WAJIB **kembali eligible** di antrean untuk dikerjakan ulang — implement BENAR intent legacy yang rusak (dead filter). Re-work membuat versi baru dgn history utuh. | `30-verif §7 BR-VERIF-12 [ARTIFACT]`, `§9 EC1`; OQ-VERIF-04 | `[INTENT]` (fix) | Kapabilitas re-verifikasi dikonfirmasi stakeholder via OQ-VERIF-04; default rebuild = re-workable. |
| BR-06-14 | Status `verified` **kedaluwarsa strict 30 hari** (`expires_at`). 05-npp meng-enforce freshness saat aktivasi. **JANGAN dikonflasi** dgn (a) gate freshness FCL/SLIK 30-hari di NPP-save (BR-VERIF-7 — data biro, hard 403+rollback) dan (b) cek 30-hari advisory di credit-analysis — tiga cek berbeda. | D-01 Step 14; `30-verif §7 BR-VERIF-7` nota mutability; umbrella §4.1 | `[INTENT]` — BARU per meeting; konsekuensi & clock start `[OPEN]` OQ-MEET-05 | USULAN default: clock = `verified_at`; konsekuensi = `recheck` (re-verify), bukan auto-cancel. |
| BR-06-15 | Output gate: `status='verified'` (+ `is_fresh`) bersama CM approved = syarat aplikasi tampil di **NPP queue**, **BPKB candidate queue**, dan detail "process CM". Outcome WAJIB; mekanisme kode 1-char legacy bebas diganti. | `30-verif §7 BR-VERIF-6` (4 SP gate); umbrella §5, OQ-COLL-01 resolved | `[INTENT]` (outcome) | Konsumen membaca E11 / read-model — bukan join langsung ke tabel internal 06 (ownership seam). |
| BR-06-16 | Correction mengembalikan record ke **maker asal** sebagai SATU transisi eksplisit; tidak ada double-write/insert hand-off ganda seperti `sp_approve_vertel` legacy. | `30-verif §9 EC3`; OQ-VERIF-06 | `[INTENT]` (fix); aturan "selalu ke maker asal, level berapa pun" `[OPEN]` OQ-VERIF-06 | Default: correction → maker asal. |
| BR-06-17 | Approve pada level final = terminal (`approved`); pada level non-final (bila multi-level dipertahankan) = interim + eskalasi step berikutnya. Default target-state: **1 level (Kepala Cabang)**. | GT `:64-65`; `30-verif §5.6, §8` | `[INTENT]`; kedalaman chain `[OPEN]` OQ-VTL-03 | Hierarki tergantung skala risiko (D-10) — konfigurasi, bukan hardcode. |
| BR-06-18 | Routing approval car vs motor: legacy 2 SP berbeda (`_R4` vs non-R4) → rebuild **satu engine config-driven per product-line**; outcome routing dipertahankan. | `30-verif §7 BR-VERIF-3`; umbrella §6.2 departure #3 | `[INTENT]` | Varian queue `_r4` = dead (zero caller) — jangan di-port (`§9 EC2 [ARTIFACT]`). |
| BR-06-19 | **Tidak ada role super user** dalam bentuk apa pun pada alur Vertel (bypass approval, self-approve stamp, dsb). | D-09; GT `:77-78` | **[LOCKED]** | Menutup OQ-VERIF-09. |
| BR-06-20 | Hasil match Dukcapil ditampilkan **read-only informational** (submitted-vs-registry + per-field scores); TIDAK ada gate otomatis pada outcome match — kecuali OQ-VERIF-01 memutuskan sebaliknya. | `30-verif §7 BR-VERIF-8`; `dukcapil.md §1-§6` | `[INTENT]`; gate prosedural `[OPEN]` OQ-VERIF-01 | Per-field scores dipertahankan end-to-end (`dukcapil.md §10 EC4`). |
| BR-06-21 | `CFVerifikasiKonsumen`/`CFVerifikasiKonsumen_AplikasiIN` + `sp_insert_VerifikasiKonsumen` TIDAK di-port (zero caller); drop fisik menunggu audit penulis eksternal. | `30-verif §9 EC4`; OQ-VERIF-03; OQ-DATA-05 resolved | `[ARTIFACT]` | Kanonik = `tr_verification_customer` → `VERIFICATION`. |
| BR-06-22 | Setiap submit RFA + keputusan tercatat append-only di **`log_approval_history` terpusat** (milik 03; `module_context='vertel'`/`entity_type='VK'` — §3.1, **OQ-VTL-06 RESOLVED by convention**): actor, role, reason, timestamp — ditulis dalam transaksi keputusan; Flowable `ACT_HI_*` BUKAN satu-satunya sumber audit (ADR-13d). | `30-verif §6`; `sp_get_history_vertel.sql:16-46`; ADR-13; DB-CONVENTIONS §8 | `[INTENT]` | Audit maker-checker wajib (regulatori). |
| BR-06-23 | Semua guard submit/decision dieksekusi **server-side otoritatif dalam satu transaksi**; kegagalan = `422`/`409` tanpa perubahan state (atomik). FE guard = UX saja. | pola umbrella §7.3; `65 §7 BR-NPPVTL-17` (gate FE rusak = bukti FE tak boleh jadi satu-satunya enforcement) | `[INTENT]` | Fail-closed utk regulated gate (OQ-REG-06 resolved). |
| BR-06-24 | Endpoint decision + flag `is_pending_approver` disediakan BE sehingga layar approval FE **berfungsi** (legacy: tombol keputusan tak pernah render — bug label). | `65 §9 EC1 [ARTIFACT]`, `§7 BR-NPPVTL-17` | `[INTENT]` (intended rule dipertahankan; mekanisme rusak tidak) | Cross-check FE: OQ-NPPVTL-01 (inbox shared). |

---

## 7. State Machine

Kolom tersimpan: `trx_customer_verification.status` (state machine internal — di kontrak API §5 muncul sebagai field `workflow_state`) + proyeksi status kanonik cross-service (field API `status`). Mapping penuh di §3.3. Chain approval in-flight diorkestrasi Flowable (ADR-13; §3.1); transisi tetap dienforce service layer + CHECK.

| Dari | Aksi | Ke | Guard / Prasyarat |
|---|---|---|---|
| `(∅)` | E3 create (`save_mode=draft`) | `draft` / `pending` | Eligibility BR-06-01 (PO terbit + CM approved + tanpa verifikasi aktif); maksimal satu aktif per aplikasi (`409` bila ada) |
| `draft` | E4 update | `draft` | Editable penuh |
| `draft` / `correction` | E6 RFA (guard submit lulus) | `rfa` / `pending` | BR-06-09..12 (kelengkapan submit) atomik; chain open → resume (BR-06-06); Idempotency-Key |
| `draft` / `correction` | E6 RFA (guard gagal) | (unchanged) | `422 RFA_GATE_FAILED`, nol perubahan (BR-06-23) |
| status lain | E6 RFA | (unchanged) | `409 RFA_INVALID_STATE` |
| `rfa` / `verified_interim` | E7 decision `approve` (level final) | `approved` / **`verified`** | Actor = pending approver role `KEPALA_CABANG`; no self-approval (BR-06-05); set `verified_at` + `expires_at` (BR-06-14); emit `CustomerVerificationApproved` (USULAN) |
| `rfa` / `verified_interim` | E7 decision `approve` (level non-final — bila multi-level, OQ-VTL-03) | `verified_interim` / `pending` | Step level berikut dibuat (BR-06-17) |
| `rfa` / `verified_interim` | E7 decision `correction` | `correction` / `pending` | SATU transisi eksplisit kembali ke maker asal (BR-06-16; fix EC3) |
| `rfa` / `verified_interim` | E7 decision `reject` | `rejected` / **`failed`** | Terminal untuk row; aplikasi kembali eligible antrean `rejected_rework` (BR-06-13) |
| `correction` | E4 edit → E6 re-submit | `rfa` | Loop rework maker |
| `approved`/`verified` | `now > expires_at` (proyeksi read-time §3.3; scheduler hanya eventing re-queue/notifikasi) | (row tetap `approved`) / **`recheck`** | **BARU D-01 Step 14** — USULAN default; konsekuensi final & clock start = OQ-MEET-05. Aplikasi kembali eligible antrean `recheck_expired` |
| `rejected` / `recheck` | E3 create versi baru (re-work) | `draft` (versi baru) | BR-06-13; history versi lama utuh (append-only) |
| `approved`/`verified` (fresh) | — dibaca 05/BPKB via E11 | (no transition) | Gate keluar: `status='verified'` AND `is_fresh` AND CM approved (BR-06-15) |

Catatan non-happy-path tercakup: guard submit gagal (no-op), status guard (`409`), self-approval (`403`), bukan-pending-approver (`403`), reject→re-queue (fix EC1), resume chain (idempotent), expiry→recheck, konsumen gate saat tidak ada verifikasi (05 fail-closed).

> **Yang TIDAK direplikasi dari state machine legacy** (`30-verif §8-§9`): (a) `Rejected` terminal-selamanya tanpa re-queue (EC1); (b) double-write Correction top-level (EC3); (c) tidak ada transisi langsung `Correction/Draft → Approved/Rejected` — tetap dipertahankan: satu-satunya jalan ke terminal adalah melalui `rfa`/`verified_interim`.

---

## 8. Integrasi Eksternal

Semua akses eksternal via **ACL**; hapus anti-pattern legacy (cross-DB linked-server read utk data regulated, triplikasi query, logika di string-concat SQL) — umbrella §6.2 departure #6.

| Seam | Arah | Sync/Async | Owner | Peran di 06 |
|---|---|---|---|---|
| **Dukcapil result replica** (`[MACF-DBMCF].MACFDB.dbo.FCL_Dukcapil_Hdr/Dtl`) | inbound (read-only) | sync read | eksternal (populated di luar codebase) | E13 display informational by NIK. Field set + per-field scores **[LOCKED]** (regulated KYC identity match — `dukcapil.md §1,§5,§6`). **Request-initiation TIDAK ditemukan di codebase** (`dukcapil.md §2, EC1`) → `[OPEN]` OQ-DUKCAPIL-01 (P1 — jangan asumsikan read-only cukup). Freshness/expiry Dukcapil analog 30-hari = `[OPEN]` OQ-DUKCAPIL-04. |
| **Reason-code master** (`ms_CAS_approval_reason`) | inbound (read) | sync | shared references | Derive decision type (BR-06-07). |
| **Katalog document-field** (`sp_get_DocumentField` → master) | inbound (read) | sync | references (master) | Definisi checklist per asset-kind/disbursement (BR-06-08). |
| **File storage** (upload checklist + dokumen pendukung) | outbound (write) | sync | infra (ITEC — D-11) | E10; legacy menyimpan via save action + `sp_get_file_verification_customer` (retrieval). Mekanisme storage final = arsitektur ITEC. |
| **05-npp (gate consumer)** | inbound (dibaca) | sync in-transaction (kontrak konsistensi = OQ-VTL-05) | 06 memproduksi; 05 meng-enforce | E11 gate-status; fail-closed di sisi 05 (umbrella §7.3). |
| **BPKB candidate queue** | inbound (dibaca) | PULL | post-acq | Membaca gate yang sama (BR-06-15; OQ-COLL-01 resolved). |
| **Event bus (USULAN)** | outbound | async | 06 | `CustomerVerificationApproved` — notifikasi, bukan sumber kebenaran (downstream PULL — D-01 Step 15). Mekanisme broker = ITEC (D-11). |
| **BUKAN seam 06**: FCL/SLIK freshness (02/05), RAC Bank Mega (02), Passnet (05), mobile survey sync (01 — OQ-VERIF-02). | — | — | — | Lihat §1.2. |

> **Catatan Dukcapil `[LOCKED]`**: set field identitas yang di-match (NIK, nama lengkap, tempat/tgl lahir, gender, status kawin, pekerjaan, alamat + kode wilayah prov/kab/kec/kel + RT/RW, nama ibu) + semantik pass/fail dgn threshold dan per-field scores = kontrak regulatory (KYC/AML), WAJIB dipertahankan meskipun penamaan internal berubah (`dukcapil.md §1 [LOCKED], §5 [LOCKED], §6 [LOCKED]`). Bug legacy yang TIDAK direplikasi: unguarded `ex.InnerException.Message` dereference di repository Dukcapil (`dukcapil.md §8 [ARTIFACT]`).

---

## 9. Acceptance Criteria (Given/When/Then)

**AC-1 (eligibility antrean — posisi STEP 14)**
Given aplikasi dengan CM committee-approved dan **PO sudah terbit** dan belum punya verifikasi,
When Admin Cabang memanggil E1 `/vertel/queue`,
Then aplikasi tampil dengan `queue_reason="never_verified"`; dan Given aplikasi CM-approved **tanpa PO**, Then aplikasi TIDAK tampil (BR-06-01, D-02).

**AC-2 (happy path: draft → RFA → approve Kepala Cabang)**
Given record wawancara `draft` lengkap (email+phone non-empty, receiver, notes, checklist wajib),
When Admin Cabang POST E6 RFA lalu Kepala Cabang POST E7 dengan `reason_id` ber-type approve,
Then `workflow_state=approved`, `status=verified`, `verified_at` terisi, `expires_at = verified_at + 30 hari`, baris `log_approval_history` (`module_context='vertel'`; submitted + approved final) tercatat, event `CustomerVerificationApproved` (USULAN) ter-emit sekali.

**AC-3 (gate terbuka utk 05)**
Given verifikasi `verified` dan `now < expires_at`,
When 05-npp memanggil E11,
Then response `status="verified"`, `is_fresh=true` — aplikasi eligible antrean NPP/BPKB (BR-06-15).

**AC-4 (30-day expiry strict)**
Given verifikasi `verified` dengan `expires_at` sudah lewat,
When scheduler expiry berjalan (atau E11 dipanggil setelah lewat),
Then `status="recheck"` / `is_fresh=false`; 05 mem-block aktivasi; aplikasi kembali tampil di E1 dengan `queue_reason="recheck_expired"` (BR-06-14; D-01 Step 14; konsekuensi default menunggu OQ-MEET-05).

**AC-5 (rejected re-queue — fix dead filter legacy)**
Given verifikasi diputus `reject` oleh Kepala Cabang,
When Admin Cabang membuka E1,
Then aplikasi tampil kembali dengan `queue_reason="rejected_rework"` dan E3 membuat **versi baru** tanpa menghapus history versi lama (BR-06-13; kebalikan eksplisit dari `30-verif §9 EC1`).

**AC-6 (no self-approval)**
Given record di-RFA oleh `EMP-0007`,
When `EMP-0007` (meski ber-role Kepala Cabang) POST E7,
Then `403 SELF_APPROVAL_BLOCKED`, tanpa perubahan state (BR-06-05; D-01 Step 11).

**AC-7 (tanpa super user)**
Given user mana pun tanpa posisi pending-approver pada chain record,
When POST E7,
Then `403 NOT_PENDING_APPROVER` — tidak ada jalur bypass apa pun (BR-06-19; D-09).

**AC-8 (resume chain, idempotent)**
Given record `correction` di-edit lalu di-RFA ulang saat chain VK aplikasi masih open,
When POST E6,
Then chain existing di-**resume** (`resumed_existing=true`), tidak ada chain duplikat; retry E6 dengan `Idempotency-Key` sama → `200` identik tanpa efek ganda (BR-06-06).

**AC-9 (guard kelengkapan submit atomik)**
Given record `draft` dengan email dan phone kosong di kedua representasi,
When POST E6,
Then `422 RFA_GATE_FAILED` merujuk BR-06-09, dan record tetap `draft` tanpa perubahan apa pun (BR-06-23).

**AC-10 (guard rekening tujuan — name match [LOCKED])**
Given aplikasi car retail non-UMC dengan `destination_account_name` tidak token-match nama applicant,
When POST E3/E4/E6,
Then `422 DEST_ACCOUNT_NAME_MISMATCH` (BR-06-11) — enforcement server-side meskipun FE sudah warning; dan Given hanya 1 dokumen pendukung ter-upload, Then `422 DEST_ACCOUNT_DOCS_INSUFFICIENT` (BR-06-12).

**AC-11 (checklist per asset-kind)**
Given aplikasi motor vs car dengan `application_type_id` berbeda,
When GET E9,
Then set document-field yang dikembalikan berbeda sesuai katalog master (BR-06-08) — bukan hardcode; dan submit RFA tanpa checklist wajib → `422`.

**AC-12 (decision di-key reason master)**
Given `reason_id` yang tidak terdaftar atau tanpa `type` valid,
When POST E7,
Then `422 REASON_REQUIRED`/invalid — approver TIDAK bisa mengirim status bebas (BR-06-07).

**AC-13 (correction satu transisi)**
Given Kepala Cabang (level final) memutus `correction`,
When E7 diproses,
Then TEPAT satu step history correction tercatat dan record kembali editable oleh maker asal — tidak ada double-write / row hand-off ganda (BR-06-16; kebalikan `30-verif §9 EC3`).

**AC-14 (gate fail-closed di konsumen)**
Given aplikasi tanpa record verifikasi sama sekali,
When 05 memanggil E11,
Then response `status:"none"` dan 05 memblokir aktivasi (fail-closed — umbrella §7.3; OQ-REG-06 resolved). *(AC lintas-modul; assert sisi 06: E11 tidak pernah error-out menjadi "dianggap verified".)*

**AC-15 (Dukcapil read-only + scores utuh)**
Given NIK dengan hasil match tersedia di replica,
When GET E13 detail,
Then response memuat **per-field scores** + threshold + result terjemahan; dan tidak ada endpoint 06 yang menolak/meloloskan aplikasi berdasarkan hasil Dukcapil (BR-06-20 — sampai OQ-VERIF-01 memutuskan lain).

**AC-16 (audit trail lengkap)**
Given siklus penuh draft→rfa→correction→rfa→approve,
When GET E8 history,
Then seluruh keputusan tampil berurutan dengan actor, role, reason, timestamp; tidak ada langkah hilang (BR-06-22).

---

## 10. Dependency

**Upstream (prasyarat / dikonsumsi read-only):**
- **04-contract-cm-po** — PO terbit (STEP 13) = prasyarat antrean (D-02; BR-06-01); data CM final (tenor/DP/angsuran/admin-fee/item) utk sisi "confirmed" wawancara.
- **03-approval-committee** — CM `approved` (STEP 12) — prasyarat transitif.
- **01-intake-cas / CUSTOMER** — identitas applicant (nama utk token-match BR-06-11; NIK utk lookup Dukcapil).
- **Masters/references** — reason-code master (BR-06-07), katalog document-field (BR-06-08), master bank.
- **Dukcapil replica** — read-only (E13); mekanisme populate `[OPEN]` OQ-DUKCAPIL-01.
- **Arsitektur ITEC Bank Mega (D-11)** — topologi service, broker event, storage file, kontrak konsistensi gate (OQ-VTL-05).

**Downstream (mengonsumsi output 06):**
- **05-npp (STEP 15)** — enforcement hard-gate `verified` + freshness in-transaction (BE-05 §1.1; umbrella §5). 05 TIDAK menulis entity 06.
- **BPKB candidate queue (post-acq)** — PULL gate yang sama (OQ-COLL-01 resolved → `verification_status='A'` kanonik).
- **FE Next.js (FE-06)** — layar antrean/wawancara/approval; BE menyediakan `is_pending_approver` + metadata visibility panel rekening (fix `65 §9 EC1/EC8`); cross-check kebutuhan API dari `60-frontend/65-npp-vertel-screens.md` §3a.

**Urutan build (rebuild_phase 2 — `30-verif` front-matter):** membutuhkan 03/04 (CM approved + PO) sudah ada; harus ada sebelum 05 bisa diuji end-to-end (gate). Konsisten dependency KB: `depends_on: [contract-cm-po, approval-committee, acquisition-cas-intake, credit-analysis-scoring]`.

**BUKAN dependency 06:** FCL/SLIK freshness data (02), chassis/BAST validation (05), Passnet (05), survey mobile 2W (01), PO minting (04).

---

## 11. Keputusan Dibutuhkan (Open Questions)

| OQ-ID | Pertanyaan | Menyentuh | Prioritas |
|---|---|---|---|
| **OQ-MEET-05** | 30-day expiry verifikasi konsumen (D-01 Step 14): konsekuensi expiry = **auto-cancel** aplikasi atau **re-verify** (default rebuild: re-verify → `recheck`)? Clock start = `verified_at` (default USULAN), tanggal wawancara, atau lainnya? | 06 (produksi `expires_at`), 05 (enforcement) | **P1** (blocker semantik gate) — resolves: ops stakeholder |
| **OQ-VTL-01** `[BARU]` | Eligibility antrean target = **strict post-PO** (D-02) — bolehkah Vertel dimulai lebih awal (paralel post-CM seperti legacy BR-VERIF-1) untuk efisiensi, atau strict sequential? Default rebuild: strict post-PO per D-02. | 06 | P2 — resolves: business owner (COBS) |
| **OQ-VTL-02** `[BARU]` | **Open CM** (koreksi STEP 13, kembali ke Step 1–12 — GT `:59-62`; OQ-GT-03) terhadap verifikasi existing: apakah verifikasi ter-invalidate (→ `recheck`) saat CM/PO berubah? Default USULAN: ya, invalidate. | 06, 04 | P2 — resolves: bersama OQ-GT-03 |
| **OQ-VTL-03** `[BARU]` | Kedalaman chain approval VK target: **1 level Kepala Cabang** (GT `:64-65`, default) atau multi-level per skala risiko (D-10) seperti mekanisme legacy (`V` interim)? Bila 1 level, state `verified_interim` tidak terpakai. | 06 | P2 — resolves: risk/ops policy owner |
| **OQ-VTL-04** `[BARU]` | Applicability Vertel per product MACF (D-07): wajib utk SEMUA product atau ada pengecualian (mis. Instant-Approval lane — OQ-MEET-04)? | 06; annex per-product | P2 — bagian OQ-MEET-06 (P1 utk annex, bukan umbrella) |
| **OQ-VTL-05** `[BARU]` | Kontrak konsistensi gate E11 utk 05: sync call in-transaction vs read-model/replika ter-subscribe — menunggu arsitektur ITEC (D-11; monolith modular vs microservices — bandingkan BE-05 §1.3 Opsi A/B). | 06, 05 | P1 — resolves: dokumen ITEC (deadline 10 Juli 2026 per D-11) |
| **OQ-VTL-06** `[BARU — audit dokumen]` | Audit keputusan approval VK: log **per-modul** (`log_verification_approval`) vs log **terpusat** `log_approval_history` milik 03? → **RESOLVED by convention (2026-07-14): log TERPUSAT `log_approval_history` (milik 03)** dgn diskriminator `module_context='vertel'`/`entity_type='VK'` (§3.1) — dasar: **DB-CONVENTIONS §8** (keputusan/riwayat approval ditulis ke `log_approval_history`), **ADR-13d**, konsistensi **BE-05 §3.1.12** + **registry BE-00 §6.3** (tanpa slice `VK`). Tabel `log_verification_approval` TIDAK dibuat. Migrasi type `VK` = **satu pintu di lane BE-03** (tanpa exclusion; §3.1 + §3.4 registry-note) — risiko double-migrate hilang; rekonsiliasi ADR-15 memverifikasi stamping. | 06, 03, 05; migrasi | **RESOLVED by convention** — refinement ADR-13 (was P2) |
| **OQ-VERIF-01** | Apakah hasil match Dukcapil (atau checklist KTP) meng-gate secara **prosedural** (sign-off manual sebelum approve Vertel) meski tak ada kode yang meng-enforce? Menentukan perlu-tidaknya coded gate baru. | 06 | P2 — resolves: ops/compliance |
| **OQ-VERIF-03** | `CFVerifikasiKonsumen` benar-benar unused end-to-end, atau ada tool/report eksternal yang menulis/membaca langsung? Menentukan keamanan drop saat migrasi. | migrasi data 06 | P2 |
| **OQ-VERIF-04** | Kapabilitas "rejected re-appears in queue" pernah live lalu rusak, atau tak pernah berfungsi? Default rebuild: **implement** (BR-06-13). | 06 | P2 |
| **OQ-VERIF-05** | Perlu "Customer Check" **consolidated view** (Vertel+Dukcapil+FCL+survey → satu verdict)? Legacy TIDAK punya (EC6) → bila ya = net-new feature. | 06 (+02) | P2 |
| **OQ-VERIF-06** | Correction oleh approver level puncak: "selalu kembali ke maker asal" = aturan intensional (jadikan first-class rule) atau side-effect blok `IF` legacy? Default rebuild: satu transisi correction → maker asal (BR-06-16). | 06 | P3 |
| **OQ-NPPVTL-01** | Inbox approval shared (`63-approval-inbox-screens`) punya decision UI sendiri utk transaksi VK, atau layar Vertel adalah satu-satunya jalur (yang di legacy rusak)? Menentukan surface decision FE + apakah E7 dipanggil dari inbox. | 06 BE↔FE | P1 (utk FE-06; BE tetap expose E7) |
| **OQ-NPPVTL-03** | Checklist dokumen: perlukah state **confirmed-missing** eksplisit (three-state) atau "absence is implicit" (legacy: opsi "Tidak Ada" permanently disabled)? Model §3.1 menyiapkan enum 3-state sebagai USULAN. | 06 | P2 |
| **OQ-DUKCAPIL-01** | Mekanisme apa yang menginisiasi request Dukcapil + mengisi `FCL_Dukcapil_Hdr/_Dtl` (API pemerintah / reseller / RPA)? **Highest-priority integration OQ** — rebuild tidak boleh berasumsi read-only cukup. | 06/02; integrasi | **P1** |
| **OQ-DUKCAPIL-04** | Perlukah freshness/expiry utk hasil Dukcapil analog aturan 30-hari (SLIK & Vertel), mengingat sama-sama feeding NPP? | 06, 05 | P2 |
| **OQ-VERIF-02** | Caller eksternal `spNewZoomInsertSurvey_2w` / `sp_*_OJK_checking_to_FCL` (zero in-codebase caller) — live integration atau dead? *(Register di 01 — dicatat di sini karena berbagi KB domain.)* | 01 (bukan 06) | P1 (di 01) |
| **OQ-ARCH-STACK** | Framework Java (rekomendasi: **Spring Boot** — USULAN), transport (REST/gRPC/bus), broker event — menunggu arsitektur ITEC (D-11). | semua modul BE | — |
| ~~OQ-VERIF-09~~ | ~~Super-user berlaku utk transaksi VK?~~ **DITUTUP oleh D-09** — role super user dihapus dari rebuild sepenuhnya (BR-06-19). | 06 | ✅ closed |

> **Catatan marker-fidelity**: `[LOCKED]` = BR-06-05 (no self-approval — governance D-01), BR-06-11 (name-match anti-fraud), BR-06-19 (no super user — D-09), field `credit_id`, kontrak field/scores Dukcapil, sensus role D-10, stack Java D-12. `[INTENT]` = outcome maker-checker, gate "verifikasi mendahului legalisasi/kolateral" (BR-VERIF-6), checklist per asset-kind, expiry 30-hari (parameter di OQ). `[ARTIFACT]` do-not-replicate = dead filter rejected (EC1), triplikasi query + varian `_r4` (EC2), double-write correction (EC3), `CFVerifikasiKonsumen` (EC4), tombol approval FE yang tak pernah render (`65 §9 EC1`), precedence bug (EC7), unguarded InnerException (dukcapil §8). `[OPEN]`/OQ = JANGAN diselesaikan diam-diam — semua tercantum di tabel §11.
