# PRD — Vertel: Verifikasi Telepon Konsumen (STEP 14) [FE]

> **Audience**: Tim Frontend (FE). **Target stack**: **Next.js** `[LOCKED per D-12]`. **Tanggal**: 2026-07-14.
> **Pasangan BE**: `docs/prd/acquisition/BE-06-vertel-verification.md` — kontrak API di §8 dokumen ini WAJIB konsisten dengan §4/§5 file itu (endpoint E1..E13); endpoint yang dibutuhkan layar tetapi TIDAK ada di BE PRD dicatat sebagai **GAP** di §11 (tidak dikarang).
> **Sumber otoritatif**: `.mega-sdd/knowledge-base/.sp-manifests/_ACQUISITION-GROUND-TRUTH.md` v2 (16-STEP, PDF 08072026 — STEP 14 `:63-65`) + `.mega-sdd/knowledge-base/.sp-manifests/_MEETING-DECISIONS-2026-07.md` (D-01..D-12, terutama **D-02**) + KB FE `60-frontend/65-npp-vertel-screens.md` (field census layar legacy Vertel — VTL-S1/S2, BR-NPPVTL-12..18, EC1/EC4/EC6/EC7/EC8) + KB FE `60-frontend/60-app-shell-auth-navigation.md`, `60-frontend/67-client-side-behavior.md` (konvensi shell & client-side) + KB BE `10-domains/30-verification-external-checks.md`, `50-integrations/dukcapil.md`.
> **Status**: **MODUL BARU** dalam dekomposisi FE (menjawab GAP-FE05-04 dari FE-05: kepemilikan layar Vertel → file ini). Layar legacy (FINCORE.WEB `Views/Acquisition/Vertel/*`) = **EVIDENCE, bukan mandat desain** — OUTCOME (field, aturan, staging, role-gating) dipertahankan; UX Next.js baru ditandai **USULAN**. NFR: **responsive mobile + desktop**. **Super-user TIDAK ADA** (D-09 `[LOCKED]`).

Modul FE **06-vertel-verification** adalah presentation layer **STEP 14 — Verifikasi Telepon (Vertel)** (GT `:63-65`; D-02: "step verifikasi telepon di antara step 13–14"): setelah PO terbit (STEP 13), **Admin Cabang** menelepon konsumen dan merekam hasil wawancara (pasangan *confirmed-vs-actual*, penerima barang, checklist dokumen wajib), submit **RFA Vertel**; **Kepala Cabang** me-review dan merekam keputusan (Approve → status `verified` + expiry strict 30 hari per D-01 Step 14 → membuka gate NPP STEP 15). Layar approval legacy **RUSAK** — tombol keputusan tidak pernah render (`65-npp-vertel §9` Edge Case 1 `[ARTIFACT]`); rebuild ini mengimplementasikan gate yang DIMAKSUD (BR-NPPVTL-17 `[INTENT]`) dengan benar via flag `is_pending_approver` dari BE (BE-06 BR-06-24). Semua guard otoritatif di **BE dalam satu transaksi** (BE-06 BR-06-23); FE hanya me-mirror sebagai UX preventif.

---

## 1. Ruang Lingkup & Kepemilikan

### 1.1 Screens yang DIMILIKI modul ini

| Screen | Fungsi | Evidence legacy | Sumber |
|---|---|---|---|
| **SCR-VTL-01 — Vertel Queue (antrean kerja)** | Daftar aplikasi *eligible* utk verifikasi telepon (PO terbit + CM approved + tanpa verifikasi aktif, ATAU re-work `rejected`/`recheck`); badge `queue_reason`; entry point maker. | Lookup popup `Lookup/PartialViewVertel.cshtml` (legacy: antrean disembunyikan di popup Add) | BE-06 §1.1 "Antrean kerja"; `65-npp-vertel §5` item 12; D-02 |
| **SCR-VTL-02 — Vertel List/Monitoring** | Listing record verifikasi semua status per branch; aksi Detail/Edit/Print status-gated. | `Views/Acquisition/Vertel/ListedIndex.cshtml` (working listing — BUKAN `Index.cshtml` yang orphan, EC6 `[ARTIFACT]`) | `65-npp-vertel §5` item 19; BR-NPPVTL-18; §9 EC6 |
| **SCR-VTL-03 — Interview Form (Create)** | Form hasil wawancara telepon: contact status, 7 pasangan confirmed-vs-actual, penerima barang, checklist dokumen, panel rekening tujuan (conditional); Simpan Draft / Submit RFA. | `Views/Acquisition/Vertel/Add.cshtml` + `_DocForm.cshtml` | `65-npp-vertel §3a` VTL-S1 |
| **SCR-VTL-04 — Interview Form (Edit)** | Field set sama dgn Create, pre-filled; dipakai saat `draft` dan re-work setelah `correction`. | `Views/Acquisition/Vertel/Edit.cshtml` | `65-npp-vertel §3a` VTL-S1; BR-NPPVTL-15 |
| **SCR-VTL-05 — Detail / Approval / Riwayat** | View read-only seluruh field; **panel keputusan Kepala Cabang** (Approve/Reject/Correction + reason) — implementasi BENAR dari layar legacy yang rusak (EC1); timeline riwayat approval; print form verifikasi (setelah `verified`); panel Dukcapil read-only (informational). | `Views/Acquisition/Vertel/View.cshtml` + `Lookup/ApprovalVetrtelView.cshtml` (reason-picker) | `65-npp-vertel §3a` VTL-S2, §9 EC1; BE-06 E5/E7/E8/E12/E13 |

Dialog/komponen milik modul: **DecisionDialog** (pengganti reason-picker `ApprovalVetrtelView.cshtml`), **ConfirmedVsActualFieldPair** (komponen pasangan konfirmasi — USULAN), **DocumentChecklistPanel** (pengganti `_DocForm.cshtml`), **DestinationBankPanel** (panel rekening tujuan conditional), **ApprovalHistoryTimeline**, **DukcapilResultPanel** (read-only), **VertelStatusBadge**. `[VERIFIED]` padanan legacy (`65-npp-vertel §11`); bentuk komponen Next.js = USULAN.

### 1.2 BUKAN milik modul ini (non-goal)

| BUKAN dimiliki | Pemilik | Catatan |
|---|---|---|
| **Layar NPP (STEP 15)** — BAST, chassis/engine, aktivasi kontrak | **FE-05** | KB `65-npp-vertel` mendokumentasikan dua keluarga layar dalam satu file; PRD ini hanya keluarga layar Vertel. FE-05 hanya **menampilkan** status Vertel sebagai prasyarat (read-only, via BE-05 preflight). |
| **Enforcement gate "verified sebelum NPP"** | BE **05-npp** (in-transaction) | 06 memproduksi status; FE-06 tidak merender gate NPP. Umbrella seam (BE-06 §1.2). |
| **Approval Inbox shell** (antrian keputusan lintas-modul) | FE-03 approval-inbox (KB `63-approval-inbox-screens.md`) | Legacy me-redirect ke inbox setelah keputusan; FE-06 hanya mendefinisikan deep-link masuk/keluar. Apakah inbox punya decision UI sendiri utk transaksi VK = **OQ-NPPVTL-01** (P1). |
| **App shell, login, navigasi, session/branch context** | FE-00 OVERVIEW (KB `60-app-shell-auth-navigation.md`) | Identitas session dipakai stamp `acting_employee` (BR-SHELL-1 `[LOCKED]`, BR-SHELL-3); jangan duplikasi. |
| **Shared components** (DataTable, LookupDialog, ConfirmDialog, CurrencyField, DateField, FileUploadField, Toast/Alert) | FE-00 OVERVIEW (KB `67-client-side-behavior.md`) | FE-06 mengkonsumsi; §10. |
| **Inisiasi request Dukcapil** (write path) | `[OPEN]` — belum terlokasi (BE-06 §1.2; `dukcapil.md §2`) | FE-06 hanya merender hasil read-only (E13); TIDAK boleh mengasumsikan tombol "request Dukcapil" ada sebelum OQ-DUKCAPIL-01 dijawab. |
| **Layar FCL/SLIK & KTP viewer** | FE modul 02-credit-analysis | FE-06 hanya menautkan (link) dari layar detail (BE-06 §1.2). |
| **Master data (bank, reason codes, katalog document-field)** | modul master-data (D-08; KB `66-master-data-screens.md`) | FE-06 mengkonsumsi lookup-nya (GAP-FE06-01/02 §11). |
| **Rendering/generate PDF report verifikasi** | BE-06 (dataset via E12) | FE hanya memicu unduh/print on-demand. |
| **Orphaned static listing** `Vertel/Index.cshtml` + dead grid-init | — | `[ARTIFACT]` discard (EC6/EC7 `65-npp-vertel §9`) — tidak dibawa ke rebuild. |

### 1.3 Reengineering mandate FE (bukan mirror legacy)

- **Layar approval HARUS berfungsi** — legacy: seluruh blok tombol Approve/Reject/Correction/Verify di-gate pada label status layar yang tidak pernah di-assign controller, sehingga **tidak pernah render** utk viewer/state mana pun; hanya link Cancel yang tampil (`65-npp-vertel §9` EC1 `[ARTIFACT]`, `View.cshtml:669-793`). Rebuild: gating dari **`is_pending_approver`** per-record yang disediakan BE (E5 — BE-06 BR-06-24), meniru pola NPP yang bekerja (BR-NPPVTL-8), BUKAN perbandingan label string.
- **Client-side guard = UX preventif, BUKAN enforcement** — semua guard submit/decision otoritatif server-side atomik (BE-06 BR-06-23). Guard email/phone legacy hanya hidup di layar Edit (BR-NPPVTL-15 — Add screen tanpa guard); rebuild: guard di FE utk Create & Edit, enforcement di BE (BR-06-09).
- **Kondisi panel auto-debit TIDAK di-reimplementasi di FE** — legacy menduplikasi kondisi trigger (car retail non-UMC) di 3 situs: script on-load, script lookup-selection, dan server (`65-npp-vertel §9` EC8). Rebuild: **server-computed visibility metadata** (BE-06 BR-06-10); FE hanya membaca flag, tidak menghitung ulang `item_id/customer_type/application_type_id`.
- **Antrean sebagai layar first-class** — legacy menyembunyikan antrean di lookup popup pada Add screen; rebuild mengangkatnya jadi SCR-VTL-01 dgn `queue_reason` eksplisit (`never_verified`/`rejected_rework`/`recheck_expired` — E1), termasuk re-work `rejected` yang di legacy mustahil (dead filter — BE-06 BR-06-13). **USULAN** bentuk layar; outcome antrean `[INTENT]`.
- **Do-not-replicate FE**: silent AJAX failure swallowing (BR-CSB-10 `[ARTIFACT]`), size-check upload yang rusak di Edit screen (BR-CSB-17 `[ARTIFACT]` — membaca properti size dari objek yang salah), dead grid-init (EC7), orphaned `Index.cshtml` (EC6), tiga implementasi lookup-dialog tak-interoperable (BR-CSB-13 → satu LookupDialog FE-00), date-picker enforcement setengah-jalan (BR-CSB-7 — justru di `vertel.js:3-7` picker dimatikan).
- **Super-user dihapus** (D-09 `[LOCKED]`): tidak ada affordance UI bypass approval dalam bentuk apa pun; **self-approval diblokir** (D-01 Step 11) — FE menyembunyikan panel keputusan utk submitter, BE tetap menolak `403 SELF_APPROVAL_BLOCKED`.

---

## 2. Aktor & Peran (akses per screen, role-gating)

Role census cabang target-state (D-10 `[LOCKED]`): **CMO, Marketing Head, Credit Analyst, Kepala Cabang, Credit (Admin)**. Legacy FE tidak punya role-gating client-side eksplisit — akses via menu per-employee (`60-app-shell` BR-SHELL-4) + flag generik "pending approver" (`65-npp-vertel §2`). Rebuild memodelkan **role eksplisit** pada session claim (mekanisme final menunggu arsitektur ITEC D-11 — [OPEN] OQ-ARCH-STACK; mapping role `KEPALA_CABANG` = OQ-NPPVTL-02).

| Aktor | SCR-VTL-01 Queue | SCR-VTL-02 List | SCR-VTL-03/04 Form | SCR-VTL-05 Detail/Approval |
|---|---|---|---|---|
| **Admin Cabang / Credit (Admin)** (maker — GT `:64`: "Admin Cabang memvalidasi data langsung … melakukan RFA Vertel") | **Full**: lihat antrean, mulai "Kerjakan" | Lihat + Edit (status editable) + Print (`verified`) | **Full access**: create, edit, upload, Simpan Draft, Submit RFA | View read-only + riwayat; TIDAK melihat panel keputusan |
| **Kepala Cabang** (checker — GT `:64-65`: "disetujui oleh Kepala Cabang"; D-10) | Lihat (read) | Lihat (filter "menunggu keputusan saya" — USULAN) | Tidak ada akses tulis | View + **panel keputusan** (Approve/Reject/Correction) — hanya bila `is_pending_approver=true` utk record itu **dan** viewer ≠ submitter (D-01 Step 11; BE BR-06-05) |
| **Back-office viewer** | — | Lihat (read) | — | View + DukcapilResultPanel (read-only informational — BE-06 §2, BR-06-20) |
| **CMO / Marketing Head / Credit Analyst** | [OPEN] — read-only atau tanpa akses; matrix menu per role final = keputusan (§11 GAP-FE06-06, mirror GAP-FE05-05) | — | — | — |

Aturan gating FE:
- **Panel keputusan** dirender hanya bila: `workflow_state ∈ {rfa, verified_interim}` **dan** `is_pending_approver=true` dari E5 **dan** viewer ≠ `submitted_by` (UX preventif; enforcement BE `403 SELF_APPROVAL_BLOCKED`/`403 NOT_PENDING_APPROVER` — BE-06 §5 E7). Ini implementasi BENAR dari intent BR-NPPVTL-17 `[INTENT]`; mekanisme label-string legacy `[ARTIFACT]` DILARANG ditiru (§1.3).
- **Edit/Print di listing** legacy murni status-gated, bukan identity-gated (BR-NPPVTL-18 `[INTENT]`) — rebuild menambahkan role-gating (maker utk Edit) di atas status-gating; **USULAN** (memperketat, tidak melonggarkan outcome).
- Identitas `acting_employee_id` diambil dari session shell (FE-00; BR-SHELL-1/3), tidak pernah di-input manual — konsisten pola stamp "created by" legacy (`65-npp-vertel §2`).
- **Tidak ada super user** (D-09 `[LOCKED]`; BE BR-06-19): tidak ada tombol/route/mode tersembunyi yang mem-bypass gating di atas.

---

## 3. Peta Screen & Route (inventori + usulan route Next.js)

Prefix route group acquisition mengikuti konvensi FE-00. Seluruh route = **USULAN** (App Router).

| Screen | Route (USULAN) | Jenis render | Guard akses |
|---|---|---|---|
| SCR-VTL-01 Queue | `/acquisition/vertel/queue` | List + server-side pagination/search (`GET /vertel/queue` E1) | Admin Cabang (maker); role lain read [OPEN] |
| SCR-VTL-02 List/Monitoring | `/acquisition/vertel` | List + pagination/filter (`GET /vertel/verifications` E2) | Branch staff (read); aksi role+status-gated |
| SCR-VTL-03 Create | `/acquisition/vertel/new?applicationId=` | Form client-heavy (conditional panel, upload, live warning) | Admin Cabang |
| SCR-VTL-04 Edit | `/acquisition/vertel/[id]/edit` | Form pre-filled; hanya reachable bila `workflow_state ∈ {draft, correction}` (mirror guard E4 BE) | Admin Cabang |
| SCR-VTL-05 Detail/Approval/Riwayat | `/acquisition/vertel/[id]` | Detail read-only; panel keputusan muncul role+state-driven (bukan varian URL) | Semua role modul; panel keputusan: Kepala Cabang assigned |

Navigasi & deep-link:
- Entry point maker: menu modul → `/acquisition/vertel/queue` → baris eligible → tombol "Kerjakan" → `/acquisition/vertel/new?applicationId=` (aplikasi terkunci dari antrean — pengganti lookup popup legacy `PartialViewVertel.cshtml`; free-typing `application_id` DILARANG, outcome lookup legacy `65-npp-vertel §4`).
- Entry point checker: dari **Approval Inbox** (FE-03) → deep-link `/acquisition/vertel/[id]`; setelah keputusan sukses, kembali ke inbox (outcome redirect legacy — `65-npp-vertel §9` EC1 "reason-picker's own success handler point at the shared approval inbox"; mekanik back-nav = USULAN). Pembagian kerja decision UI inbox vs layar ini = **OQ-NPPVTL-01** (P1).
- Mode approval TIDAK memakai query `?mode=approval` — panel keputusan dirender dari kombinasi (state `rfa`/`verified_interim` + `is_pending_approver`). **USULAN** — menghindari URL yang bisa dibagikan utk memaksa mode (konsisten FE-05 §3).
- Re-work `rejected`/`recheck`: dari SCR-VTL-01 baris ber-badge `rejected_rework`/`recheck_expired` → "Kerjakan Ulang" → `/acquisition/vertel/new?applicationId=` (membuat **versi baru** — BE-06 BR-06-13; row lama tetap read-only di SCR-VTL-02).

---

## 4. Komposisi Layar & Komponen

Referensi shared components dari FE-00 (KB `67-client-side-behavior.md`): DataTable satu grid contract dgn pembedaan empty-vs-fetch-failed (BR-CSB-11), LookupDialog tunggal (BR-CSB-13), ConfirmDialog dua-tombol vocabulary tunggal (BR-CSB-18/19), CurrencyField satu formatter id-ID (BR-CSB-5/6), DateField picker konsisten SEMUA field tanggal (BR-CSB-7 — legacy Vertel justru sisi yang picker-nya dimatikan, `vertel.js:3-7`), FileUploadField multipart + busy (BR-CSB-15), Toast/Alert envelope-aware (BR-CSB-9), button-busy anti double-submit (BR-CSB-20). Jangan duplikasi di modul.

### 4.1 SCR-VTL-01 — Vertel Queue

- **Layout**: page header (judul "Antrean Verifikasi Telepon") → search bar (nama/credit_id — param E1) → DataTable → pagination. Mobile: tabel → card list per baris (USULAN; NFR responsive).
- **Kolom** (dari kontrak E1 — BE-06 §5): credit_id, customer_name, po_number, po_issued_at, cm_approved_at, **QueueReasonBadge** (`never_verified` → "Baru"; `rejected_rework` → "Re-work (Ditolak)"; `recheck_expired` → "Kadaluarsa"), aksi "Kerjakan"/"Kerjakan Ulang".
- Baris `rejected_rework`/`recheck_expired` menampilkan link "lihat verifikasi sebelumnya" (`previous_verification_id` dari E1 → SCR-VTL-05). USULAN.
- Branch default = branch session (BR-SHELL-3); lintas-branch = kebijakan FE-00 [OPEN] (OQ-SHELL-02).

### 4.2 SCR-VTL-02 — Vertel List/Monitoring

- **Layout**: header → filter bar (status kanonik + pencarian) → DataTable → pagination; mobile card list.
- **Kolom** (USULAN — field census response E2 belum dirinci BE → GAP-FE06-03): credit_id, customer_name, **VertelStatusBadge** (§7), contact_status, submitted_at, verified_at + indikator umur/expiry (USULAN), kolom aksi.
- **Aksi per baris** (status-gated — outcome BR-NPPVTL-18; role-gating tambahan USULAN §2): Detail (selalu), Edit (hanya `draft`/`correction` + role maker — padanan legacy "Edit icon utk Draft/Correction rows", `65-npp-vertel §5` item 19), Print (hanya `verified` — padanan "Print icon utk terminal/approved", E12).

### 4.3 SCR-VTL-03/04 — Interview Form (Create/Edit)

Satu form (Create/Edit berbagi komposisi — legacy Add/Edit "same field set", `65-npp-vertel §11`), disusun sebagai section berurutan pada satu halaman (staging maker→checker lintas-screen ada di §6; BUKAN wizard multi-halaman — USULAN):

1. **Section A — Aplikasi (read-only)**: pre-fill dari antrean/detail: credit_id, nama customer, PO number, data CM final sisi "confirmed" (tenor, angsuran, DP, item type, admin fee — sumber: `CREDIT_APPLICATION`+`CREDIT_MEMO` read-only, BE-06 §3.2). Kontrak endpoint pre-fill utk layar Create = **GAP-FE06-03** §11.
2. **Section B — Kontak & Wawancara**: `contact_status` (required), `confirmation_datetime`, `billing_date`; pasangan `email` dan `mobile_phone` confirmed/actual (guard non-empty §5). Model BE menyimpan riwayat attempt kontak per panggilan (`trx_customer_verification_contact_attempt`, BE-06 §3.1) tetapi kontrak capture-nya belum ada di E3/E4/E5 → **GAP-FE06-07** (§11); Section B hanya membawa status/waktu kontak final.
3. **Section C — Konfirmasi Data Pembiayaan** (komponen **ConfirmedVsActualFieldPair** ×5 — USULAN): delivery_date, item_type, installment_amount, tenor, down_payment; tiap pasangan menampilkan nilai "confirmed" (pre-fill CM) berdampingan dgn input "actual" hasil wawancara; visual diff highlight bila berbeda (USULAN).
4. **Section D — Penerima Barang & Lainnya**: `item_receiver_name` + `item_receiver_relation`, `requested_due_date`, toggle `other_admin_fee_flag` → `other_admin_fee_amount` (conditional), `other_notes`.
5. **Section E — DestinationBankPanel (conditional)**: dirender HANYA bila metadata BE `requires_destination_bank_account=true` (car retail non-UMC — BE-06 BR-06-10; FE tidak menghitung kondisi sendiri, §1.3): bank (lookup master — GAP-FE06-02), `destination_account_no`, `destination_account_name` + **live name-match warning** terhadap nama applicant (BR-06-11 `[LOCKED]` — FE warning, BE hard-block), upload **2 dokumen pendukung** (BR-06-12).
6. **Section F — DocumentChecklistPanel**: daftar dokumen wajib dari `GET /vertel/document-fields?application_id=` (E9 — set bervariasi per asset-kind + disbursement method, BR-06-08); per baris: nama dokumen + status kehadiran + FileUploadField. Perilaku opsi "Tidak Ada" = **OQ-NPPVTL-03** ([OPEN] — legacy permanently disabled, EC4; default FE: hanya "present + upload", tanpa opsi explicit-missing, sampai diputuskan).
7. **Action bar** (sticky bottom di mobile): Batal · **Simpan Draft** · **Submit RFA** (ConfirmDialog dua-tombol sebelum kirim — BR-CSB-18). Draft vs RFA = outcome legacy `save_mode` (`65-npp-vertel §3a` VTL-S1) → target: draft via E3/E4, RFA via E6 (§6.1).

### 4.4 SCR-VTL-05 — Detail / Approval / Riwayat

- **Header**: credit_id + nama customer + **VertelStatusBadge** besar + (bila `verified`) umur verifikasi & `expires_at` + breadcrumb.
- **Body**: seluruh field wawancara dirender read-only, grup section sama dgn 4.3 (konsistensi mental model); panel rekening tujuan read-only (outcome `hidden_fields_vs_prior` `65-npp-vertel §3a` VTL-S2: "destination-bank fields shown read-only, not re-editable").
- **Panel Keputusan** (role+state-gated §2): tombol **Approve / Reject / Correction** → **DecisionDialog**: pilihan aksi mem-filter opsi `reason_id` berdasarkan `type` reason master (decision di-derive dari reason, BUKAN status bebas — BE-06 BR-06-07; payload E7 hanya membawa `reason_id` + `reason_description`); ConfirmDialog sebelum submit. Sumber options reason = **GAP-FE06-01** §11. Keputusan `Verify` interim HANYA dirender bila multi-level chain aktif ([OPEN] OQ-VTL-03; default 1 level Kepala Cabang → tombol Verify TIDAK ada).
- **ApprovalHistoryTimeline**: riwayat keputusan chain VK dari `GET /vertel/verifications/{id}/history` (E8): actor, role, aksi, reason, timestamp — urut kronologis (BE-06 BR-06-22). Sumber data sisi BE = **`log_approval_history` terpusat milik 03** (filter `module_context='vertel'`/`entity_type='VK'` — **OQ-VTL-06 RESOLVED**, BE-06 §3.1; BUKAN tabel per-modul, BUKAN query `ACT_HI_*` engine) — transparan bagi FE selama kontrak E8 stabil.
- **Print** (hanya `verified` — gate outcome BR-NPPVTL-18): tombol unduh/print form verifikasi + checklist dari E12.
- **DukcapilResultPanel** (read-only informational — BE-06 BR-06-20): hasil match by NIK dari E13: submitted-vs-registry per field + **per-field match scores** + threshold + result (scores WAJIB tampil utuh, jangan direduksi ke boolean tunggal — `dukcapil.md §10 EC4` semantics `[LOCKED]`). TIDAK ada aksi approve/reject pada panel ini (gate prosedural = [OPEN] OQ-VERIF-01). NIK applicant utk query = bagian field census E5 (GAP-FE06-03).
- Link silang read-only ke layar FCL/KTP viewer modul 02 (non-goal §1.2).

---

## 5. Field & Validasi (census per form)

> Kolom **Marker** mengikuti KB: `[LOCKED]` = wajib verbatim; `[INTENT]` = outcome dipertahankan, bentuk bebas; `[ARTIFACT]` = legacy dibuang/diganti; **USULAN** = desain baru FE. Validasi FE = preventif; otoritatif di BE (BE-06 BR-06-23 — gagal = `422`/`409` atomik). Semua field date memakai DateField FE-00 (picker aktif konsisten — perbaiki enforcement setengah-jalan BR-CSB-7 yang justru mati di `vertel.js:3-7`); semua field uang memakai CurrencyField id-ID (BR-CSB-5/6).
> Legenda required: **Ya** = wajib sejak entry; **Ya†** = wajib-saat-submit (boleh kosong di Draft, divalidasi saat RFA — konvensi BE-06 §5 E3).

### 5.1 Interview Form (SCR-VTL-03/04) → payload `POST /vertel/verifications` (E3) / `PATCH /vertel/verifications/{id}` (E4) — BE-06 §5

| # | Field (payload BE) | Label (ID) | Tipe input | Required | Format & validasi FE | Sumber options/nilai | Marker | Sumber |
|---|---|---|---|---|---|---|---|---|
| 1 | `application_id` | Aplikasi / Credit ID | terkunci dari antrean (read-only) | **Ya** | Harus berasal dari baris antrean E1 — free-typing DILARANG (outcome lookup legacy) | SCR-VTL-01 (E1) | [INTENT] | `65-npp-vertel §3a` VTL-S1 (acquisition_record_lookup required), §4 |
| 2 | — (pre-fill display) | Nama Customer, No PO, data CM (tenor/angsuran/DP/item/admin fee sisi confirmed) | read-only text | — | Non-editable | payload antrean + CM read-only (BE-06 §3.2); kontrak = GAP-FE06-03 | [INTENT] | BE-06 §3.2; `65-npp-vertel §5` item 12 |
| 3 | `contact_status` | Status Kontak | select | **Ya** | satu pilihan wajib | options: sumber katalog TIDAK ter-ekstrak KB ("contacted_option") → **GAP-FE06-04** | [INTENT] | `65-npp-vertel §3a` VTL-S1 (required, always); BE-06 §3.1 |
| 4 | `confirmation_datetime` | Waktu Konfirmasi | datetime | Tidak | datetime valid; default = now (USULAN) | picker | [INTENT] | BE-06 §3.1 |
| 5 | `billing_date` | Tanggal Penagihan | DateField | Tidak | tanggal valid | picker | [INTENT] | BE-06 §3.1 |
| 6 | `email.confirmed` / `email.actual` | Email (Konfirmasi / Aktual) | text ×2 | **Ya†** (pasangan) | format email; **minimal SATU dari dua non-empty saat RFA** (BR-06-09) — di Create DAN Edit (perluasan: legacy hanya guard di Edit screen, BR-NPPVTL-15) | manual | [INTENT] | BR-NPPVTL-15; BE-06 BR-06-09 |
| 7 | `mobile_phone.confirmed` / `mobile_phone.actual` | No. HP/WhatsApp (Konfirmasi / Aktual) | text ×2 | **Ya†** (pasangan) | digits; minimal SATU non-empty saat RFA (BR-06-09) | manual | [INTENT] | BR-NPPVTL-15; BE-06 BR-06-09 |
| 8 | `delivery.confirmed` / `delivery.actual` | Tgl Pengiriman (Konfirmasi / Aktual) | DateField ×2 | Tidak | tanggal valid | confirmed pre-fill; actual manual | [INTENT] | `65-npp-vertel §3a` VTL-S1 (optional) |
| 9 | `item_type.confirmed` / `item_type.actual` | Tipe Unit (Konfirmasi / Aktual) | text ×2 | Tidak | — | confirmed pre-fill CM | [INTENT] | `65-npp-vertel §3a` VTL-S1 ("vehicle_type") |
| 10 | `installment_amount.confirmed` / `.actual` | Angsuran (Konfirmasi / Aktual) | CurrencyField ×2 | Tidak | numeric ≥ 0; format id-ID (BR-CSB-5) | confirmed pre-fill CM | [INTENT] | `65-npp-vertel §3a` VTL-S1; BE-06 §3.1 (decimal) |
| 11 | `tenor.confirmed` / `tenor.actual` | Tenor (Konfirmasi / Aktual) | number ×2 | Tidak | integer > 0 | confirmed pre-fill CM | [INTENT] | `65-npp-vertel §3a` VTL-S1 |
| 12 | `down_payment.confirmed` / `.actual` | DP (Konfirmasi / Aktual) | CurrencyField ×2 | Tidak | numeric ≥ 0 | confirmed pre-fill CM | [INTENT] | `65-npp-vertel §3a` VTL-S1 |
| 13 | `item_receiver.name` | Nama Penerima Barang | text | **Ya†** | non-empty saat RFA | manual | [INTENT] | `65-npp-vertel §3a` VTL-S1 (required); BE-06 §5 E3 († ) |
| 14 | `item_receiver.relation` | Relasi Penerima ke Pemohon | select/text | **Ya†** | non-empty saat RFA | options: sumber katalog tidak ter-ekstrak → GAP-FE06-04 | [INTENT] | `65-npp-vertel §3a` VTL-S1 ("name_and_relation" satu paket) |
| 15 | `requested_due_date` | Tanggal Jatuh Tempo Diminta | DateField | Tidak | tanggal valid | picker | [INTENT] | `65-npp-vertel §3a` VTL-S1 (optional) |
| 16 | `other_admin_fee.flag` | Ada Biaya Admin Lain? | toggle | Tidak | toggle yes → field #17 tampil + required (runtime toggle — pola BR-CSB-2) | — | [INTENT] | `65-npp-vertel §3a` VTL-S1 (conditional "toggle set to yes") |
| 17 | `other_admin_fee.amount` | Nominal Biaya Admin Lain | CurrencyField | **Conditional** (flag=yes) | numeric > 0 | manual | [INTENT] | `65-npp-vertel §3a` VTL-S1; BE-06 §3.1 |
| 18 | `other_notes` | Catatan Lain | textarea | **Ya†** | non-empty saat RFA; max-length ikut BE ([OPEN] tidak dispesifikasi) | manual | [INTENT] | `65-npp-vertel §3a` VTL-S1 (required); BE-06 §5 E3 († ) |
| 19 | `destination_bank_account.bank_id` | Bank Tujuan Pencairan | LookupDialog/select | **Conditional** — wajib bila panel aktif (metadata BE `requires_destination_bank_account=true`) | harus pilihan valid master bank | master bank — endpoint lookup = **GAP-FE06-02** | [INTENT] (BR-06-10) | BR-NPPVTL-12; BE-06 BR-06-10 |
| 20 | `destination_bank_account.account_no` | No. Rekening Tujuan | text | **Conditional** (panel aktif) | digits, non-empty | manual | [INTENT] | BR-NPPVTL-12 |
| 21 | `destination_bank_account.account_name` | Nama Pemilik Rekening | text | **Conditional** (panel aktif) | **live token-match warning** (first/middle/last) vs nama applicant; mismatch = warning FE + hard-block BE `422 DEST_ACCOUNT_NAME_MISMATCH` | manual; nama applicant dari data pre-fill (GAP-FE06-03) | **[LOCKED]** kontrol name-match (anti-fraud destinasi pencairan; algoritma bebas, kontrol WAJIB) | BR-NPPVTL-13 `[LOCKED]`; BE-06 BR-06-11 |
| 22 | dokumen pendukung rekening (×2) | Dokumen Pendukung Rekening | FileUploadField ×2 | **Conditional** — minimal **2 file** bila destination bank dipilih | tipe/ukuran file ikut BE ([OPEN] — legacy warning ~500KB advisory-only & rusak di Edit, BR-CSB-16/17 → GAP-FE06-05); kurang dari 2 → blok submit + BE `422 DEST_ACCOUNT_DOCS_INSUFFICIENT` | upload via E10 | [INTENT] | BR-NPPVTL-16; BE-06 BR-06-12 |
| 23 | `document_checks[]` (`document_field_code`, `presence`, `file_id`) | Checklist Dokumen Wajib | DocumentChecklistPanel (baris per dokumen: status + FileUploadField) | **Ya†** (set wajib per katalog) | set baris dari E9 (per asset-kind + disbursement — BR-06-08); "present" terikat file upload; opsi "Tidak Ada" TIDAK dirender (default) sampai OQ-NPPVTL-03 diputuskan (legacy: permanently disabled — EC4) | `GET /vertel/document-fields?application_id=` (E9); file via E10 | [INTENT]; three-state = [OPEN] OQ-NPPVTL-03 | `65-npp-vertel §3a` VTL-S1, §9 EC4; BE-06 §3.1 `VERIFICATION_DOCUMENT_CHECK` |
| 24 | `save_mode` | — (tombol Simpan Draft vs Submit RFA) | dua tombol action bar | **Ya** | Draft = E3(`save_mode:"draft"`)/E4; RFA = E6 (record existing) atau E3(`save_mode:"rfa"`) utk create-langsung-submit | — | [INTENT] | `65-npp-vertel §3a` VTL-S1 (save_mode required); BE-06 §4 E3/E6 |

**Guard submit RFA (client-side preventif)** — tombol RFA disabled + daftar kekurangan inline bila belum terpenuhi: #3 terisi; pasangan #6 dan #7 masing-masing minimal satu non-empty; #13/#14/#18 terisi; checklist #23 set wajib lengkap; bila panel aktif: #19–21 terisi + name-match tidak mismatch + #22 ≥ 2 file (mirror kondisi transisi VTL-S1→VTL-S2 `65-npp-vertel §3a` + BE-06 BR-06-09..12). Error BE (`422 RFA_GATE_FAILED` + daftar `rule`) tetap dipetakan balik per-field (§7) — FE guard bukan pengganti enforcement (BR-06-23).

### 5.2 Form Keputusan Checker (DecisionDialog, SCR-VTL-05) → `POST /vertel/verifications/{id}/decision` (E7 — BE-06 §5)

| Field | Label | Tipe | Required | Validasi FE | Sumber options | Marker | Sumber |
|---|---|---|---|---|---|---|---|
| (pemilih aksi) | Keputusan | tombol Approve / Reject / Correction (+ Verify hanya bila multi-level — OQ-VTL-03) | **Ya** | satu pilihan; aksi mem-**filter** opsi reason by `type` (1→approve, 2→reject, 3→correction, 4→verify-interim); payload TIDAK membawa field decision — di-derive BE dari reason (BR-06-07) | enum kanonik BE-06 §3.1 `action` `[LOCKED]` | [INTENT] mekanik; enum [LOCKED] | `65-npp-vertel §3a` VTL-S2; BE-06 BR-06-07, §3.1 |
| `reason_id` | Alasan | select | **Ya** (semua aksi) | wajib pilih; invalid → BE `422 REASON_REQUIRED` | reason-code master — **endpoint lookup TIDAK ada di BE-06 §4 → GAP-FE06-01** | [INTENT] | `65-npp-vertel §3a` VTL-S2 (reason_code required); BE-06 §5 E7 |
| `reason_description` | Keterangan | textarea | Tidak | max-length ikut BE ([OPEN]) | manual | [INTENT] | `65-npp-vertel §3a` VTL-S2 (optional) |
| `acting_employee_id` | — | dari session | **Ya** | tidak editable; ≠ submitter (UX; BE `403 SELF_APPROVAL_BLOCKED`) | session shell FE-00 | [INTENT]; no-self-approval **[LOCKED]** D-01 S11 | BE-06 §5 E7; BR-06-05 |
| (header) `Idempotency-Key` | — | auto-generated FE (UUID per intent keputusan) | **Ya** | satu key per sesi dialog; re-klik/retry memakai key sama (konvensi semua mutasi BE-06 §4) | generated | [KEPUTUSAN DESAIN BARU] | BE-06 §4 |

### 5.3 Submit RFA (action bar SCR-VTL-03/04) → `POST /vertel/verifications/{id}/rfa` (E6 — BE-06 §5)

| Field | Tipe | Catatan | Sumber |
|---|---|---|---|
| (header) `Idempotency-Key` | UUID, **WAJIB** | satu key per intent submit; retry memakai key sama → `200` identik tanpa efek ganda | BE-06 §5 E6 |
| `acting_employee_id` | dari session | stamp maker (`submitted_by` — dipakai guard no-self-approval) | BE-06 §5 E6, §3.1 |
| (respon) `approval_chain` | display | tampilkan `expected_role: KEPALA_CABANG` + `resumed_existing` sebagai info "diteruskan ke Kepala Cabang" / "melanjutkan chain sebelumnya" (BR-06-06) | BE-06 §5 E6 |

### 5.4 Filter List (SCR-VTL-02) → `GET /vertel/verifications` (E2)

| Field | Tipe | Default | Catatan | Sumber |
|---|---|---|---|---|
| `status` / `workflow_state` | select | semua | label kanonik §7; parameter filter final ikut kontrak E2 ([OPEN] — response/param E2 belum dirinci BE → GAP-FE06-03) | BE-06 §4 E2 |
| pencarian teks | text | — | nama/credit_id (mirror E1); param final = GAP-FE06-03 | BE-06 §4 E2 |
| `branch` | select | branch session | lintas-branch = kebijakan FE-00 [OPEN] (OQ-SHELL-02) | BR-SHELL-3; BE-06 §5 E1 |

---

## 6. Aturan Interaksi & Staging

### 6.1 Staging maker→checker (dari KB §3a — outcome WAJIB dipertahankan)

Dua stage lintas-screen (`65-npp-vertel §3a` — "genuine maker→checker, two-stage hand-offs"):

| Stage | Screen | Aktor | Transisi |
|---|---|---|---|
| **VTL-S1** — capture wawancara & Save/RFA | SCR-VTL-03/04 | Admin Cabang | Simpan Draft → E3/E4 → tetap `draft` (editable, tanpa efek workflow — BE BR-06-03); Submit RFA → E6 → `rfa` (masuk chain VK); guard gagal → `422 RFA_GATE_FAILED`, tetap di form dgn error per-field |
| **VTL-S2** — review & keputusan | SCR-VTL-05 | Kepala Cabang | Approve (final) → `approved`/`verified` + `verified_at` + `expires_at` (D-01 S14); Reject → `rejected`/`failed` (re-queue BR-06-13); Correction → `correction`, kembali editable oleh maker asal (BR-06-16) |

**Urutan create-upload-submit (menyelesaikan dependency file)**: E10 (upload) membutuhkan `{id}` verifikasi, sedangkan `document_checks[].file_id` di E3 merujuk file ter-upload → alur baku FE: (1) **Simpan Draft** (E3) → (2) upload file checklist/pendukung (E10) → (3) lengkapi & **Submit RFA** (E6). Create-langsung-RFA (E3 `save_mode:"rfa"`) hanya utk kasus tanpa kebutuhan upload baru. **USULAN** (sequencing); outcome field `[INTENT]`.

Field baru di S2 vs S1: keputusan + reason (`new_fields_vs_prior` — `65-npp-vertel §3a` VTL-S2); field rekening tujuan di S2 read-only, tidak re-editable (`hidden_fields_vs_prior`).

### 6.2 Conditional rendering & disable/enable

| # | Trigger | Efek UI | Marker | Sumber |
|---|---|---|---|---|
| 1 | Metadata BE `requires_destination_bank_account=true` (car retail non-UMC — dihitung server, BUKAN FE) | **DestinationBankPanel** (Section E) dirender + field #19–22 required; false → panel tidak dirender sama sekali | [INTENT] (BR-06-10); konsolidasi EC8 `[ARTIFACT]` 3-situs | BR-NPPVTL-12; `65-npp-vertel §9` EC8; BE-06 BR-06-10; bentuk metadata = GAP-FE06-03 |
| 2 | Ketik/blur `destination_account_name` | Live token-match vs nama applicant → warning inline non-blocking ("nama tidak cocok dgn pemohon"); submit tetap dicoba → BE hard-block `422 DEST_ACCOUNT_NAME_MISMATCH` | **[LOCKED]** kontrol; posisi warning = USULAN | BR-NPPVTL-13; BE-06 BR-06-11 |
| 3 | `other_admin_fee.flag` = yes | Field amount tampil + required (runtime required-toggle — shared primitive BR-CSB-2) | [INTENT] | `65-npp-vertel §3a` VTL-S1 |
| 4 | `application_id` terkunci dari antrean | Section A pre-fill; ganti aplikasi = kembali ke antrean (tidak ada swap in-form — USULAN, mencegah state dependen basi) | USULAN | pola lookup `65-npp-vertel §4` |
| 5 | Load form (per `application_id`) | `GET /vertel/document-fields` (E9) → render baris checklist per katalog (motor vs car vs auto-debit berbeda set — BR-06-08); JANGAN hardcode set dokumen | [INTENT] | BE-06 BR-06-08; `65-npp-vertel §3a` VTL-S1 |
| 6 | Baris checklist: file ter-upload | Status baris = "Ada" (present) otomatis; hapus file → kembali unchecked; opsi eksplisit "Tidak Ada" TIDAK dirender (default menunggu OQ-NPPVTL-03) | [INTENT] + [OPEN] | `65-npp-vertel §9` EC4; BE-06 §3.1 `presence` |
| 7 | `workflow_state ∉ {draft, correction}` | Route Edit tidak ditawarkan; akses langsung → redirect detail + notice (mirror guard E4 BE); baris `rejected` TIDAK dapat di-edit — re-work = versi baru via antrean (BR-06-13) | [INTENT] | BR-NPPVTL-18; BE-06 §7 |
| 8 | Viewer = pending approver (`is_pending_approver` dari E5) & state `rfa`/`verified_interim` & viewer ≠ submitter | Panel Keputusan dirender; selain itu view-only — **fix EC1**: gating dari flag BE, BUKAN label string | [INTENT] (BR-NPPVTL-17); mekanisme legacy `[ARTIFACT]` | `65-npp-vertel §9` EC1; BE-06 BR-06-24 |
| 9 | Pilih aksi di DecisionDialog | Opsi `reason_id` ter-filter by `type` sesuai aksi; reason kosong → submit dialog diblok | [INTENT] | BE-06 BR-06-07 |
| 10 | Keputusan sukses (E7) | Approve final → render status `verified` + `verified_at`/`expires_at` dari body response (tanpa re-fetch wajib); Reject/Correction → status baru + navigasi kembali inbox (deep-link FE-03) | [INTENT] | BE-06 §5 E7; `65-npp-vertel §9` EC1 (redirect target inbox) |
| 11 | Submit RFA sukses (E6) | Tampilkan info chain (`expected_role`, `resumed_existing`) + record menjadi read-only utk maker | [INTENT] (BR-06-06) | BE-06 §5 E6 |
| 12 | Double-click submit/keputusan/upload | Button-busy + disabled selama request (BR-CSB-20); mutasi idempotent via `Idempotency-Key` (BE-06 §4) | [INTENT] | `67-client-side` BR-CSB-20 |
| 13 | Semua aksi state-changing (Simpan Draft final, RFA, keputusan) | ConfirmDialog dua-tombol proceed/cancel, vocabulary tunggal Bahasa Indonesia (konsolidasi BR-CSB-18/19) | [INTENT] | `67-client-side` BR-CSB-18/19 |
| 14 | Status `verified` + `expires_at` mendekat/lewat | Badge umur verifikasi (mis. "sisa 12 hari") di list & detail; saat `recheck`: CTA "Verifikasi Ulang" → antrean. Konsekuensi & clock final = **OQ-MEET-05** — jangan diputuskan diam-diam | [INTENT] D-01 S14; UX = USULAN | BE-06 BR-06-14; OQ-MEET-05 |

---

## 7. State Tampilan

| State | Perilaku | Sumber |
|---|---|---|
| **Loading** | Skeleton per-section (list: skeleton rows; form: skeleton panel); overlay hanya utk aksi mutasi (modernisasi pola overlay page-wide legacy BR-CSB-8 → inline/skeleton = USULAN) | `67-client-side` BR-CSB-8 |
| **Empty vs fetch-failed** | WAJIB dibedakan: "Tidak ada aplikasi di antrean" / "Data tidak tersedia" vs "Gagal memuat data" + tombol Retry (adopsi varian failure-vs-empty legacy yang lebih lengkap — BR-CSB-11) | `67-client-side` BR-CSB-11 |
| **Error request** | SETIAP kegagalan request punya jalur feedback user-visible (toast/alert + `correlation_id` dari error envelope BE `{code,message,details?,correlation_id}`); DILARANG silent swallow (do-not-replicate BR-CSB-10 `[ARTIFACT]`; termasuk pola "service unavailable" generik legacy `65-npp-vertel §6`) | `67-client-side` BR-CSB-10; BE-06 §4 |
| **Status-driven display** (VertelStatusBadge — mapping `workflow_state`/`status` kanonik BE-06 §3.3) | `draft` → "Draft" (aksi Edit); `rfa` → "Menunggu Persetujuan" (read-only maker; panel keputusan utk checker); `verified_interim` → "Disetujui — menunggu level berikut" (hanya bila multi-level, OQ-VTL-03); `approved`/`verified` → "Terverifikasi" (+ umur & `expires_at`, Print unlocked); `correction` → "Perlu Perbaikan" (CTA Edit utk maker); `rejected`/`failed` → "Ditolak" (terminal utk row; re-work via antrean); `recheck` → "Kadaluarsa — Perlu Verifikasi Ulang" (CTA ke antrean). **Satu label kanonik per status** — representasi kode 1-char legacy (`D/0/V/A/C/R`) tidak dibawa ke UI | BE-06 §3.3/§7; `65-npp-vertel §5` item 19 |
| **Error mapping (submit/RFA)** | `422 RFA_GATE_FAILED` + daftar `rule` → dipetakan per-field/section (BR-06-09..12); `422 DEST_ACCOUNT_NAME_MISMATCH` → error field #21; `422 DEST_ACCOUNT_DOCS_INSUFFICIENT` → error Section E; `409 VERIFICATION_ALREADY_ACTIVE` → notice + link ke record aktif; `409 APPLICATION_NOT_ELIGIBLE` → notice "PO belum terbit" (BR-06-01/D-02); `409 RFA_INVALID_STATE` → refresh state + notice | BE-06 §5 E3/E6 |
| **Error mapping (keputusan)** | `403 SELF_APPROVAL_BLOCKED` → notice "Anda submitter record ini" (D-01 S11); `403 NOT_PENDING_APPROVER` → panel keputusan disembunyikan + notice (tanpa jalur bypass — D-09); `409 DECISION_INVALID_STATE` → refresh; `422 REASON_REQUIRED` → error field reason | BE-06 §5 E7 |
| **Idempotent replay** | Retry RFA/keputusan dgn `Idempotency-Key` sama setelah timeout/refresh → BE kembalikan hasil pertama; FE render hasil normal tanpa error ganda | BE-06 §5 E6 |
| **Dukcapil panel** | Loading/failed state terpisah dari halaman (panel informational — kegagalan E13 TIDAK memblok layar); scores per-field selalu utuh | BE-06 §5 E13; `dukcapil.md §10 EC4` |
| **Print/unduh** | Aksi print (E12) hanya saat `verified`; busy-state per tombol; kegagalan → error jelas (bukan silent) | BE-06 §4 E12; `67-client-side` BR-CSB-12 |
| **Responsive** | Semua screen operable di mobile & desktop (NFR); form panjang: section anchor nav + sticky action bar; tabel → card list | Catatan rebuild (header) |

---

## 8. Kontrak Konsumsi API (per screen — konsisten BE-06 §4/§5)

Semua request memakai konvensi FE-00 (auth header/session, error envelope `{code, message, details?, correlation_id}`; semua mutasi ber-header `Idempotency-Key` — BE-06 §4). Transport REST diasumsikan; final menunggu arsitektur ITEC (D-11) — [OPEN] OQ-ARCH-STACK.

| Screen | Interaksi | Endpoint (BE-06 §4) | Catatan konsumsi |
|---|---|---|---|
| SCR-VTL-01 | Muat antrean + search + pagination | **E1** `GET /vertel/queue` | Render `queue_reason` badge + `previous_verification_id` link |
| SCR-VTL-02 | Muat list semua status + filter | **E2** `GET /vertel/verifications` | Field census response/param = GAP-FE06-03 |
| SCR-VTL-03/04 | Muat definisi checklist dokumen | **E9** `GET /vertel/document-fields?application_id=` | Set per asset-kind + disbursement (BR-06-08) |
| SCR-VTL-03 | Buat record (Draft / langsung RFA) | **E3** `POST /vertel/verifications` | Handle `409 VERIFICATION_ALREADY_ACTIVE` / `409 APPLICATION_NOT_ELIGIBLE` / `422` per-field (§7) |
| SCR-VTL-04 | Update record (`draft`/`correction`) | **E4** `PATCH /vertel/verifications/{id}` | Guard status di BE; `409/422` → notice |
| SCR-VTL-03/04 | Upload file checklist & dokumen pendukung | **E10** `POST /vertel/verifications/{id}/documents` (multipart) | Urutan draft-first §6.1; busy per field (BR-CSB-15/20) |
| SCR-VTL-03/04 | Submit RFA | **E6** `POST /vertel/verifications/{id}/rfa` + header `Idempotency-Key` | `422 RFA_GATE_FAILED` → map per-field; `resumed_existing` → info resume chain (BR-06-06) |
| SCR-VTL-05 | Muat detail + flags approval | **E5** `GET /vertel/verifications/{id}` | `is_pending_approver`, `has_next_level` = kunci gating panel keputusan (fix EC1 — BE BR-06-24); field census lengkap = GAP-FE06-03 |
| SCR-VTL-05 | Rekam keputusan | **E7** `POST /vertel/verifications/{id}/decision` (+ `Idempotency-Key` per konvensi mutasi BE-06 §4) | Payload `reason_id` + `reason_description` + `acting_employee_id`; decision di-derive BE (BR-06-07); error mapping §7 |
| SCR-VTL-05 | Riwayat approval | **E8** `GET /vertel/verifications/{id}/history` | Timeline chain VK (BR-06-22); backing store = `log_approval_history` terpusat, `module_context='vertel'` (OQ-VTL-06 RESOLVED — BE-06 §3.1) |
| SCR-VTL-05 / SCR-VTL-02 | Print form verifikasi + checklist | **E12** `GET /vertel/verifications/{id}/print` | Hanya `verified`; FE menyembunyikan aksi utk status lain |
| SCR-VTL-05 | Panel Dukcapil read-only | **E13** `GET /dukcapil/results/{nik}` (detail) · `GET /dukcapil/results?nik=` (list) | Informational; per-field scores utuh (`[LOCKED]` semantics); NIK dari data applicant (GAP-FE06-03) |
| — (bukan FE-06) | Gate status utk 05/BPKB | E11 `GET .../gate-status` | Service-to-service; FE-06 TIDAK memanggil (konsumen = BE-05/BPKB) |

**Endpoint yang DIBUTUHKAN layar tetapi TIDAK ada di BE-06 §4** (jangan dikarang — dicatat sebagai GAP §11): lookup master **reason codes** utk DecisionDialog (GAP-FE06-01); lookup master **bank** utk `destination_bank_id` (GAP-FE06-02); field census response **E2/E5** + data pre-fill layar Create (nama applicant utk name-match, NIK utk Dukcapil, CM confirmed-side, metadata `requires_destination_bank_account`) (GAP-FE06-03); katalog options `contact_status` & `item_receiver_relation` (GAP-FE06-04); batas tipe/ukuran file E10 (GAP-FE06-05).

---

## 9. Acceptance Criteria (Given/When/Then)

**AC-FE-1 — Antrean eligible + badge alasan (posisi STEP 14)**
- **Given** aplikasi A (CM approved + PO terbit, belum ada verifikasi), aplikasi B (CM approved TANPA PO), aplikasi C (verifikasi `rejected`);
- **When** Admin Cabang membuka SCR-VTL-01;
- **Then** A tampil ber-badge "Baru" (`never_verified`), C tampil ber-badge "Re-work (Ditolak)" (`rejected_rework`) dgn link verifikasi sebelumnya, B TIDAK tampil (BR-06-01; D-02; BE AC-1/AC-5).

**AC-FE-2 — Panel keputusan RENDER utk pending approver (regression fix EC1)**
- **Given** record `rfa`, viewer Kepala Cabang dgn `is_pending_approver=true` dari E5 dan viewer ≠ submitter;
- **When** SCR-VTL-05 dirender;
- **Then** tombol Approve/Reject/Correction TAMPIL dan berfungsi — kebalikan eksplisit dari legacy yang tidak pernah merender tombol (`65-npp-vertel §9` EC1 `[ARTIFACT]`; BE BR-06-24).

**AC-FE-3 — Panel keputusan disembunyikan utk non-approver & submitter**
- **Given** record `rfa`, viewer = submitter record itu (meski ber-role Kepala Cabang) ATAU `is_pending_approver=false`;
- **When** SCR-VTL-05 dirender;
- **Then** panel keputusan TIDAK dirender (view-only + riwayat); percobaan direct-call tetap ditolak BE `403 SELF_APPROVAL_BLOCKED`/`403 NOT_PENDING_APPROVER` (D-01 S11; D-09 — tanpa jalur bypass).

**AC-FE-4 — Guard RFA client-side (email/phone) di Create DAN Edit**
- **Given** SCR-VTL-03 (Create) dgn `email.confirmed`+`email.actual` kosong keduanya;
- **When** maker menekan Submit RFA;
- **Then** submit diblok client-side dgn daftar kekurangan ("Email wajib minimal satu"), TIDAK ada request E6 terkirim — guard berlaku juga di Create, bukan hanya Edit seperti legacy (BR-NPPVTL-15; BE BR-06-09); bila FE ter-bypass, BE `422 RFA_GATE_FAILED` dipetakan balik per-field.

**AC-FE-5 — Panel rekening tujuan server-driven + name-match**
- **Given** aplikasi car retail non-UMC (metadata BE `requires_destination_bank_account=true`) dan `destination_account_name` tidak token-match nama applicant;
- **When** form dirender lalu user submit;
- **Then** DestinationBankPanel tampil + required; warning live tampil saat ketik; BE menolak `422 DEST_ACCOUNT_NAME_MISMATCH` → dirender sebagai error field (BR-06-10/11 `[LOCKED]`); utk aplikasi motor/UMC panel TIDAK dirender dan FE TIDAK menghitung kondisi sendiri (fix EC8).

**AC-FE-6 — Dua dokumen pendukung rekening**
- **Given** destination bank dipilih dan baru 1 file pendukung ter-upload;
- **When** maker submit (Draft dgn bank terpilih, atau RFA);
- **Then** FE menandai Section E kurang (blok preventif) dan/atau BE `422 DEST_ACCOUNT_DOCS_INSUFFICIENT` dirender di Section E (BR-NPPVTL-16; BE BR-06-12).

**AC-FE-7 — Checklist dokumen per asset-kind**
- **Given** aplikasi motor vs car auto-debit;
- **When** SCR-VTL-03 dimuat utk masing-masing;
- **Then** set baris DocumentChecklistPanel berbeda sesuai E9 (BR-06-08 — bukan hardcode); baris tanpa file = unchecked; TIDAK ada opsi eksplisit "Tidak Ada" (default menunggu OQ-NPPVTL-03 — jangan diputuskan diam-diam).

**AC-FE-8 — Draft-first sequencing upload**
- **Given** maker mengisi form baru dgn dokumen yang harus di-upload;
- **When** menekan Simpan Draft;
- **Then** E3 (`save_mode:"draft"`) terkirim, record `draft` terbentuk, upload E10 tersedia per baris checklist, dan Submit RFA (E6) baru enabled setelah guard §5.1 terpenuhi (BE BR-06-03; §6.1).

**AC-FE-9 — DecisionDialog decision-by-reason**
- **Given** checker memilih aksi Reject di DecisionDialog;
- **When** dialog menampilkan opsi alasan;
- **Then** hanya reason ber-`type` reject yang ditawarkan; tanpa `reason_id` submit diblok; payload E7 hanya membawa `reason_id`+`reason_description` (decision di-derive BE — BR-06-07); `422 REASON_REQUIRED` dirender sebagai error field.

**AC-FE-10 — Approve final men-seed status verified**
- **Given** checker sah konfirmasi Approve dgn `Idempotency-Key` baru;
- **When** E7 sukses `200`;
- **Then** SCR-VTL-05 menampilkan badge "Terverifikasi", `verified_at` dan `expires_at` (= verified_at + 30 hari strict — D-01 S14) langsung dari body response tanpa re-fetch wajib; aksi Print (E12) unlocked.

**AC-FE-11 — Correction → re-work loop**
- **Given** keputusan Correction tercatat;
- **When** maker membuka SCR-VTL-02/05;
- **Then** badge "Perlu Perbaikan" + CTA Edit tampil utk maker; setelah edit + RFA ulang, E6 mengembalikan `resumed_existing=true` dan FE menampilkan info "melanjutkan chain sebelumnya" tanpa chain duplikat (BR-06-06/16).

**AC-FE-12 — Rejected re-work = versi baru via antrean**
- **Given** record diputus Reject;
- **When** maker membuka list & antrean;
- **Then** row lama berstatus "Ditolak" TANPA aksi Edit; aplikasi muncul kembali di SCR-VTL-01 ber-badge `rejected_rework`; "Kerjakan Ulang" membuat record BARU (E3) dgn riwayat lama tetap utuh dan dapat dibuka read-only (BR-06-13 — kebalikan dead-filter legacy).

**AC-FE-13 — Expiry / recheck**
- **Given** verifikasi `verified` dgn `expires_at` lewat (status kanonik `recheck`);
- **When** list/antrean/detail dimuat;
- **Then** badge "Kadaluarsa — Perlu Verifikasi Ulang" + aplikasi tampil di antrean ber-badge `recheck_expired`; UX konsekuensi lanjutan (auto-cancel vs re-verify) TIDAK diputuskan FE — menunggu OQ-MEET-05 (BR-06-14; D-01 S14).

**AC-FE-14 — Riwayat lengkap**
- **Given** siklus draft→rfa→correction→rfa→approve selesai;
- **When** ApprovalHistoryTimeline dimuat (E8);
- **Then** seluruh keputusan tampil berurutan dgn actor, role, reason, timestamp — tidak ada langkah hilang (BE BR-06-22/AC-16).

**AC-FE-15 — Dukcapil read-only + scores utuh**
- **Given** NIK applicant punya hasil match di replica;
- **When** DukcapilResultPanel dimuat (E13);
- **Then** submitted-vs-registry per field + per-field scores + threshold + result tampil utuh (TIDAK direduksi ke satu boolean — `dukcapil.md §10 EC4`); tidak ada tombol approve/reject pada panel (BR-06-20; OQ-VERIF-01); kegagalan E13 tidak memblok layar detail.

**AC-FE-16 — Idempotent retry & anti double-submit**
- **Given** Submit RFA terkirim tetapi koneksi putus sebelum response; FE retry dgn `Idempotency-Key` sama;
- **When** retry sukses;
- **Then** hasil pertama dirender tanpa efek ganda/error ganda (BE-06 §5 E6); selama request tombol disabled + spinner (BR-CSB-20).

**AC-FE-17 — Tanpa silent failure**
- **Given** request apa pun (E1..E13) gagal;
- **When** response error/network failure diterima;
- **Then** feedback user-visible tampil (pesan spesifik per `code` + `correlation_id` dapat disalin) — TIDAK ada jalur request gagal tanpa feedback (do-not-replicate BR-CSB-10 `[ARTIFACT]`).

**AC-FE-18 — Responsive**
- **Given** viewport mobile (≤ ~640px);
- **When** SCR-VTL-03 dibuka;
- **Then** semua section, pasangan confirmed-vs-actual, upload, dan action bar tetap operable (sticky action bar; tabel list/queue menjadi card) — NFR responsive.

---

## 10. Dependency

| Dependency | Jenis | Yang dikonsumsi | Sumber |
|---|---|---|---|
| **BE-06-vertel-verification** | API | Seluruh endpoint §8 (E1..E10, E12, E13); enum status kanonik §3.3; error envelope + `Idempotency-Key`; flag `is_pending_approver`/`has_next_level`; metadata visibility panel rekening | BE-06 §4/§5/§3.3 |
| **FE-00 OVERVIEW (app shell & shared)** | FE | AppShell (nav per-menu BR-SHELL-4, session identity `acting_employee` BR-SHELL-1 `[LOCKED]`, branch context BR-SHELL-3), DataTable, LookupDialog, ConfirmDialog, DateField, CurrencyField, FileUploadField, Toast/Alert envelope-aware, busy-button | `60-app-shell-auth-navigation.md`; `67-client-side-behavior.md` |
| **FE-03 approval-inbox** | FE | Deep-link masuk (inbox → `/acquisition/vertel/[id]`) dan target navigasi setelah keputusan; kontrak transaksi-type VK di inbox (**OQ-NPPVTL-01**) | `63-approval-inbox-screens.md`; `65-npp-vertel §9` EC1 |
| **FE-04 contract-cm-po** | upstream | Aplikasi ber-PO (STEP 13) = populasi antrean; data CM final sisi "confirmed" | D-02; BE-06 §3.2 |
| **FE-05 npp-legalization** | downstream (display) | FE-05 menampilkan status/freshness Vertel di preflight NPP — konsumen data, bukan dependency FE-06 | FE-05 §10 |
| **Master data (D-08)** | API/data | Reason codes (GAP-FE06-01), master bank (GAP-FE06-02), katalog document-field (via E9), katalog contact-status/relation (GAP-FE06-04) | D-08; `66-master-data-screens.md` |
| **Arsitektur ITEC (D-11)** | eksternal | Mekanisme auth/role claim, transport final, storage file (E10) | D-11; OQ-ARCH-STACK |

---

## 11. Keputusan Dibutuhkan (Open Questions & GAP)

> [OPEN] dari KB + meeting + gap kontrak BE — **jangan diselesaikan diam-diam**. FE memakai default paling aman (fail-closed / fitur tidak dirender) sampai diputuskan.

| ID | Pertanyaan | Prioritas | Dampak FE |
|---|---|---|---|
| **GAP-FE06-01** | DecisionDialog butuh lookup master **reason codes** ter-filter `type` (BR-06-07), tetapi BE-06 §4 tidak memuat endpoint lookup reason. Tambahkan di BE-06 (mis. `GET /vertel/lookups/reasons?type=` — USULAN) atau tunjuk endpoint modul master-data (D-08)? (Gap paralel dgn GAP-FE05-01.) | P1 | §5.2; tanpa ini seluruh keputusan checker tidak dapat dieksekusi |
| **GAP-FE06-02** | Lookup master **bank** utk `destination_bank_id` (§5.1 #19) tidak ada di BE-06 §4 (§3.2 hanya menyebut "master bank" sebagai referensi). Endpoint milik BE-06 atau master-data (D-08)? | P2 | §4.3 Section E |
| **GAP-FE06-03** | Field census response **E2** (kolom list + param filter/search) dan **E5** (detail lengkap: `is_pending_approver`, `has_next_level`, nama applicant utk live name-match, NIK utk Dukcapil, CM confirmed-side pre-fill, metadata `requires_destination_bank_account`) belum dirinci di BE-06 §5; juga kontrak pre-fill utk layar Create SEBELUM record ada (field E1 saja tidak cukup utk Section A/C). | P1 | §4.2–4.4; §5.1 #2/#21; §6.2 #1; §8 |
| **GAP-FE06-04** | Sumber katalog options `contact_status` ("contacted_option") dan `item_receiver_relation` tidak ter-ekstrak dari KB dan tidak ada endpoint-nya di BE-06 §4. Master baru (D-08) atau enum tetap? | P2 | §5.1 #3/#14 |
| **GAP-FE06-05** | Batas tipe & ukuran file upload E10 belum dispesifikasi (legacy: warning ~500KB advisory-only di Add, RUSAK di Edit — BR-CSB-16/17 `[ARTIFACT]`). Limit final server-side + mirror FE. | P3 | §5.1 #22/#23; FileUploadField |
| **GAP-FE06-06** | Matrix akses menu per role D-10 utk modul Vertel (apakah CMO/Marketing Head/Credit Analyst punya read-only?). Legacy: menu per-employee tanpa role scheme (BR-SHELL-4). (Mirror GAP-FE05-05.) | P2 | §2; guard route §3 |
| **GAP-FE06-07** | BE-06 §3.1 memodelkan riwayat attempt kontak (`trx_customer_verification_contact_attempt`, tidak dibatasi 3) tetapi E3/E4/E5 tidak memuat kontrak capture/read attempt per panggilan — Section B hanya membawa contact status/waktu final. Perlu kontrak (array attempt di payload E3/E4, atau endpoint attempt tersendiri), atau keputusan bahwa child table hanya diisi lane migrasi. | P2 | §4.3 Section B |
| **GAP-FE06-08** | Kolom wawancara BE-06 §3.1 TANPA padanan input di census KB VTL-S1 (§5.1): pasangan `umc_disbursement`, varian catatan `_note` "lainnya" (installment/tenor/DP/relation), `stnk_receiver_relation`, `item_usage_relation`, `item_received_origin`, `dealer_origin`, `asset_type_description_note`, `changed_phone_number*`, `mother_name`, `reference_source`. Dirender di form (field & section mana) atau diisi jalur lain? Jangan dikarang — butuh keputusan + rincian kontrak E3 (BE-06 §5 sudah menandai payload contoh non-exhaustive). | P2 | §4.3 Section B/C/D; §5.1 |
| **OQ-NPPVTL-01** | Inbox approval shared (FE-03) punya decision UI sendiri utk transaksi VK, atau SCR-VTL-05 satu-satunya surface keputusan (legacy: dua-duanya tidak jelas karena layar Vertel rusak — EC1)? Menentukan pembagian E7-caller FE-03 vs FE-06. | P1 | §3 deep-link; §4.4 Panel Keputusan |
| **OQ-NPPVTL-03** | Checklist dokumen: perlukah opsi eksplisit "Tidak Ada"/confirmed-missing (three-state — model BE §3.1 `presence` menyiapkannya sebagai USULAN) atau "absence is implicit" (legacy: opsi permanently disabled — EC4)? Default FE: opsi tidak dirender. | P2 | §4.3 Section F; §5.1 #23; §6.2 #6 |
| **OQ-MEET-05** | Konsekuensi expiry 30-hari (auto-cancel vs re-verify) + titik mulai clock (D-01 S14) → menentukan UX badge kadaluarsa, CTA "Verifikasi Ulang", dan apakah antrean `recheck_expired` otomatis muncul. Default FE: re-verify (ikut default BE). | P1 | §6.2 #14; §7 status; SCR-VTL-01 |
| **OQ-VTL-03** | Kedalaman chain approval VK: 1 level Kepala Cabang (default — GT `:64-65`) atau multi-level per skala risiko (D-10)? Menentukan apakah tombol/status "Verify"/`verified_interim` dirender sama sekali. | P2 | §4.4 DecisionDialog; §7 badge `verified_interim` |
| **OQ-VTL-02** | Open CM (koreksi STEP 13) meng-invalidate verifikasi existing (→ `recheck`)? Bila ya: FE butuh banner "verifikasi ter-invalidate karena CM berubah" di detail/list. | P2 | §7 status-driven display |
| **OQ-VTL-01** | Eligibility antrean strict post-PO (default per D-02) vs boleh mulai paralel post-CM — menentukan copy empty-state antrean & filter. | P2 | SCR-VTL-01 |
| **OQ-VERIF-01** | Hasil Dukcapil/checklist KTP meng-gate secara prosedural sebelum approve Vertel? Bila ya: DukcapilResultPanel butuh affordance sign-off + posisi di alur checker. Default: murni informational (BR-06-20). | P2 | §4.4 DukcapilResultPanel |
| **OQ-VERIF-05** | Perlu layar "Customer Check" consolidated (Vertel+Dukcapil+FCL+survey → satu verdict)? Legacy tidak punya (net-new). Bila ya = screen baru (bukan bagian file ini sampai diputuskan). | P2 | inventori §1.1 |
| **OQ-NPPVTL-02** | "Kepala Cabang" sebagai role bernama: mapping ke claim token/hierarki (legacy hanya flag "pending approver"; OQ-ACTORS-01). | P2 | §2 gating panel keputusan |
| **OQ-DUKCAPIL-01** | Mekanisme inisiasi request Dukcapil belum terlokasi — FE TIDAK merender tombol "request Dukcapil" sampai dijawab (hanya read-only E13). | P1 (integrasi; register di BE-06) | §4.4 DukcapilResultPanel |
| **OQ-ARCH-STACK** *(direvisi D-12)* | FE = Next.js `[LOCKED]`; masih [OPEN]: mekanisme auth/session & role claim (menunggu ITEC D-11), strategi rendering (App Router RSC vs client-heavy = USULAN internal FE-00), transport final. | P1 | Guard route §3; konvensi fetch §8 |
| **OQ-SHELL-02** *(milik FE-00, disebut krn menyentuh 06)* | Branch pick login tidak di-re-verify server-side (legacy BR-SHELL-12) — branch scoping dipakai filter E1/E2. | P1 | §5.4 filter branch |

**Tertutup oleh keputusan meeting / dokumen ini (tidak lagi open utk modul ini):**
- **Super-user & self-approval UI** — D-09 `[LOCKED]` + D-01 Step 11: tidak ada affordance bypass; FE merender `403 SELF_APPROVAL_BLOCKED`/`403 NOT_PENDING_APPROVER` sebagai notice, tanpa jalur alternatif (BE BR-06-19; menutup OQ-VERIF-09).
- **GAP-FE05-04 (kepemilikan layar Vertel)** — dijawab: layar Vertel dimiliki **FE-06 (file ini)**; FE-05 hanya menampilkan status Vertel read-only.
- **Layar approval Vertel rusak (EC1)** — arah fix ditetapkan: gating via `is_pending_approver` dari BE (BR-06-24); yang tersisa hanya pembagian surface dgn inbox (OQ-NPPVTL-01).
