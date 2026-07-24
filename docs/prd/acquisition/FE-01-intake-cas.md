# PRD — Intake & CAS (Credit Application System) [FE]

> **Audience**: Tim Frontend (FE).
> **Target stack**: **Next.js** `[LOCKED]` (D-12, SoW user directive 2026-07-14). Konvensi shell/shared-component mengikuti **FE-00 OVERVIEW** (dokumen payung FE) — komponen lintas modul TIDAK didefinisikan ulang di sini.
> **Tanggal**: 2026-07-14.
> **Pasangan BE**: `docs/prd/acquisition/BE-01-intake-cas.md` — seluruh kontrak API di §8 merujuk endpoint E1–E13 dokumen tersebut (§4/§5-nya). Kebutuhan layar yang TIDAK punya endpoint di BE-01 dicatat sebagai **GAP** di §11, tidak dikarang.
> **Sumber otoritatif**: `.mega-sdd/knowledge-base/.sp-manifests/_ACQUISITION-GROUND-TRUTH.md` (v2, 16 STEP PDF 08072026), `.mega-sdd/knowledge-base/.sp-manifests/_MEETING-DECISIONS-2026-07.md` (D-01..D-12), KB FE `60-frontend/61-intake-cas-screens.md` (sumber utama field census & staging), `60-frontend/60-app-shell-auth-navigation.md`, `60-frontend/67-client-side-behavior.md`, KB BE `10-domains/20-acquisition-cas-intake.md`, `10-domains/10-customer-applicant-master.md`, `10-domains/11-dealer-partner-master.md`, `10-domains/12-product-asset-master.md`, `20-workflows/credit-origination-lifecycle.md`.

Modul FE ini memiliki layar-layar tempat cabang mengubah calon debitur menjadi aplikasi kredit yang siap diajukan (RFA): wizard capture applicant + related-person + profil finansial (legacy: layar CAS accordion tunggal — `61-intake-cas-screens.md §1,§3a`), form asset & struktur finansial draft, layar upload dokumen + narasi analisa karakter (car), aksi RFA submit, re-entry Correction, serta tool standalone Customer Check (Passnet ID). Layar legacy adalah **EVIDENCE, bukan mandat desain**: OUTCOME (field, aturan, staging, role-gating) dipertahankan; bentuk UX Next.js yang bersih diusulkan eksplisit sebagai **USULAN**.

---

## 1. Ruang Lingkup & Kepemilikan

### 1.1 Screens yang dimiliki modul ini

| ID | Screen (rebuild) | Padanan legacy (evidence) | Sumber KB |
|---|---|---|---|
| SCR-INT-01 | **Wizard Intake CAS** (create/edit/correction) — stepper multi-step menggantikan layar accordion tunggal | `CAS/Index.cshtml` (car) + `CAS/IndexMotor.cshtml` (motor), `CASController.cs` | `61-...§3a S1`, `[VERIFIED][ARTIFACT]` accordion single-form (`61-...§3a` "S1 field inventory") |
| SCR-INT-02 | **Asset & Struktur Finansial Draft** (stage S2 — level draft; sensus field mendalam milik FE-04) | Hand-off `GotoCMCar`/`GotoCMMotorCycle` → `CMCar`/`CMMotorCycle` | `61-...§3a S2` (capability + call site); depth: `64-contract-cm-po-screens.md §3a S1` |
| SCR-INT-03 | **Upload Dokumen + Narasi Analisa Karakter (car) + RFA Submit** (stage S3+S4) | `CACarController.cs`/`CACar.cshtml` (car, full depth di KB 61); `CMController.cs` `CMMotor_UploadDoc` (motor, cross-ref 64) | `61-...§3a S3/S4` |
| SCR-INT-04 | **Customer Check (Passnet ID)** — tool lookup standalone | `CustomerCheckController.cs`, `CustomerCheck/Index.cshtml` | `61-...§5.8`, BR-CASFE-20 |

### 1.2 Yang BUKAN milik modul ini (cross-ref, jangan duplikasi)

- **Panel disposisi komite (Approve/Reject/Review/Verify)** yang di legacy menempel di layar yang sama dengan S3/S4 (session `Flag=Approval`, `61-...§9 Edge Case 9`) → milik **FE-03 (approval/inbox)**. Rebuild memisahkan panel maker (SCR-INT-03) dari panel disposisi — split yang diizinkan eksplisit oleh KB ("a rebuild can legitimately... split them", `61-...§9 EC9`). Termasuk **disposisi Pengecekan Cabang STEP 9** (Verify/Correction/Reject via menu Inbox Approval, GT v2 STEP 9) → layar FE-03; endpoint-nya E11/E12 BE-01.
- **Sensus field mendalam struktur finansial + mekanika RFA channel motor** → milik **FE-04** (KB `64-contract-cm-po-screens.md`); SCR-INT-02/03 di sini mengikat pada kontrak BE-01 E4/E5/E8 dan merujuk FE-04 untuk detail yang KB 61 nyatakan sebagai cross-ref (OQ-CASFE-01 dicatat di §11).
- **App shell, login, branch binding, guard sesi, komponen shared** (grid, lookup-dialog, currency input, confirm dialog, upload, alert) → milik **FE-00** (dari KB 60 + 67). Modul ini hanya MENGONSUMSI.
- **Layar SLIK/CA dashboard** (STEP 11) → FE-02; **layar master data** (D-08) → modul master-data FE.
- **Dead/disabled UI legacy — TIDAK diresurect** `[ARTIFACT]`: section "Data Pembatalan" (car), blok "Analisa & Kesimpulan (AO)" (motor), `ReferencesPartialView` data-driven, blok upload "loan calculator" RO — semuanya source-commented-out (`61-...§9 Edge Case 2`). Kapabilitas cancel di rebuild adalah **kapabilitas baru** via BE-01 E9 (`[KEPUTUSAN DESAIN BARU]` BE-01 §4), bukan restorasi UI dead.

### 1.3 Prinsip rebuild modul ini

- **Stateless context** `[INTENT]` (fix `61-...§9 Edge Case 11`; BE-01 BR-38): `credit_id` + mode (create/edit/correction) dibawa eksplisit di URL/route — DILARANG meniru session-affinity legacy (`Flag`/`IsEdit` server-side).
- **Satu wizard config-driven per `product_line`** `[INTENT]` (selaras BE-01 BR-01/E1 "satu resource, `product_line` sebagai discriminator"; legacy hard-split dua layar car/motor = BR-CASFE-1 `[LOCKED]` sebagai fakta bisnis, direalisasikan sebagai konfigurasi, bukan dua codebase layar). Divergensi capture korporasi motor (BR-CASFE-19) tetap `[OPEN]` OQ-CASFE-07 → §11.
- **Responsive** mobile + desktop (NFR wajib penugasan rebuild); layar data-entry padat legacy didesain ulang agar tetap operasional di viewport sempit (USULAN layout §4).

---

## 2. Aktor & Peran (akses per screen)

Sensus role cabang `[LOCKED]` D-10: **CMO, Marketing Head, Credit Analyst, Kepala Cabang, Credit (Admin)**. **Super-user TIDAK ADA** `[LOCKED]` D-09 — tidak boleh ada bypass role di routing/guard FE. Catatan evidence: legacy TIDAK punya role-attribute yang menggate layar CAS (`61-...§2` — "no distinct session role/permission attribute gates this screen"); role-gating di bawah adalah pemetaan target-state ke sensus D-10 (mengikuti BE-01 §2/§4), bukan perilaku legacy.

| Screen | CMO / Marketing Head (Maker) | Credit (Admin) | Credit Analyst / Kepala Cabang | Marker |
|---|---|---|---|---|
| SCR-INT-01 Wizard CAS | create/edit/correction (full) | read | read | `[INTENT]` pemetaan D-10 (BE-01 E1/E2 role) |
| SCR-INT-02 Asset & Financial Draft | edit | read | read | `[INTENT]` (BE-01 E4 role) |
| SCR-INT-03 Dokumen + Narasi + RFA | upload dokumen + narasi (car) | upload dokumen; **tombol RFA hanya Credit (Admin)** | read | `[INTENT]`; legacy tidak membedakan aktor narasi vs RFA (`61-...§2` `[OPEN]` OQ-CASFE-08) → gating final `[OPEN]` §11 |
| SCR-INT-04 Customer Check | — | — | — (lihat catatan) | Legacy: restriksi HO-only DISABLED (`[ARTIFACT]` `61-...§9 EC7`); rebuild JANGAN diam-diam drop ATAU enforce — keputusan `[OPEN]` §11 |

Aksi disposisi STEP 9 (Verify/Correction/Reject — Credit (Admin), GT v2 STEP 9) dan komite (STEP 12) berada di layar FE-03; modul ini hanya menampilkan konsekuensi statusnya (§7).

---

## 3. Peta Screen & Route (USULAN route Next.js — App Router)

Seluruh route di bawah **USULAN** (tidak ada padanan legacy yang mengikat; legacy memakai MVC route + session). Prinsip pengikat: `credit_id` eksplisit di path (BE-01 BR-38 `[INTENT]`), step wizard deep-linkable, guard auth/role dari FE-00.

| Screen | Route (USULAN) | Mode |
|---|---|---|
| Daftar aplikasi intake (entry point / worklist) | `/acquisition/intake` | list + filter status — **endpoint list TIDAK ada di BE-01 → GAP-FE01-01 (§11)** |
| SCR-INT-01 create | `/acquisition/intake/new` | wizard step 1..6 (client-side step state; lihat §6) |
| SCR-INT-01 edit / correction | `/acquisition/intake/[creditId]?step=<n>` | mode diturunkan dari `status` server (E3), BUKAN flag sesi |
| SCR-INT-02 | `/acquisition/intake/[creditId]/asset-financials` | stage S2 |
| SCR-INT-03 | `/acquisition/intake/[creditId]/documents` | stage S3 + aksi RFA (S4) |
| Review & submit RFA (sub-view SCR-INT-03) | `/acquisition/intake/[creditId]/rfa` | ringkasan read-only + confirm |
| SCR-INT-04 | `/tools/customer-check` | standalone |

Navigasi antar stage mengikuti transisi §6; tombol "Back to List" legacy (`AcquisitionController`, `61-...§11`) dipetakan ke `/acquisition/intake`.

---

## 4. Komposisi Layar & Komponen

Semua komponen shared (Stepper, FormSection, CurrencyInput, DatePicker, LookupDialog, DataGrid, ConfirmDialog, FileUpload, AlertBanner, BusyButton) berasal dari **FE-00** — modul ini hanya mendeklarasikan pemakaian. Kontrak perilaku komponen mengikuti KB 67 (satu implementasi per kontrak; jangan ulangi divergensi legacy BR-CSB-6/11/13/19).

### 4.1 SCR-INT-01 — Wizard Intake CAS

- **Layout**: header konteks (nomor CAS, `product_line`, `status` badge, `customer_dedup` indicator) + **Stepper** vertikal (desktop sidebar kiri) / horizontal collapsible (mobile) — USULAN, menggantikan accordion single-form legacy (`[ARTIFACT]` `61-...§3a` — "single page, single form, accordion-style"; bentuk accordion BOLEH diganti, set section & aturannya TIDAK).
- **Form step** (pemetaan section → step di §6.1): field group per section KB 61; conditional rendering per `customer_type` / toggle (BR-CASFE-2).
- **Repeatable row list** Pengurus/Pemilik: operasi tambah/hapus baris **murni client-side** `[INTENT]` — fix `61-...§9 Edge Case 10` (legacy full-page server round-trip yang membahayakan field yang belum tersimpan; "implement this as a pure client-side list operation").
- **LookupDialog** (satu komponen FE-00, fix BR-CSB-13): location, bank, old-agreement (per `product_line`), NIK-Konsumen, order-id (paged search — padanan legacy `CASController.cs:1064-1990`).
- **Panel screening**: tombol "Cek Blacklist" pada 3 titik (applicant/spouse/guarantor — BR-CASFE-7) + "Cek APU-PPT"; hasil hit → AlertBanner warning + reveal panel kuesioner AML (lihat §6.4).
- **Action bar** sticky: `Simpan Draft` / `Simpan Perubahan`, `Lanjut` (Next-gate §6.2), `Batalkan Aplikasi` (E9 — kapabilitas BARU, confirm dialog dua tombol per BR-CSB-18).

### 4.2 SCR-INT-02 — Asset & Struktur Finansial Draft

- Form dua panel: **Asset** (chassis/engine/brand/type/year/ownership-proof + lookup master asset) dan **Struktur Finansial** (OTR, DP, tenor, fee, rate, subsidi — CurrencyInput satu konvensi `[INTENT]` fix BR-CSB-6). Field census level kontrak = BE-01 E4 (§5.2); kedalaman aturan per field milik FE-04/KB 64.
- Simpan = upsert idempotent (E4); sukses → lanjut ke SCR-INT-03.

### 4.3 SCR-INT-03 — Dokumen + Narasi + RFA

- **Uploader dokumen**: pilih `photo_type` dari vocabulary `[LOCKED]` (BR-CASFE-17), file picker `.jpg/.jpeg/.png/.pdf`; pre-check ekstensi+ukuran client-side yang **memblokir** (USULAN — legacy warning tidak memblokir, BR-CSB-16/17 `[ARTIFACT]` do-not-replicate); otoritatif tetap server (BE-01 BR-37). Kegagalan upload WAJIB tampil (fix `61-...§9 EC5` silent catch).
- **Daftar dokumen terunggah** + preview/download — **endpoint list/download TIDAK ada di BE-01 → GAP-FE01-03 (§11)**.
- **Panel Narasi Analisa Karakter (car only)**: textarea Capacity, Capital, Character, Condition, Collateral, CMO additional analysis, advantage/deficiency notes — savable independen sebelum RFA (BR-CASFE-16 `[INTENT]`). Motor: TIDAK dirender (padanan legacy dead — `61-...§3a S3`, Edge Case 2); menyamakan car/motor = keputusan `[OPEN]` produk (D-07/OQ-MEET-06).
- **Field NPWP kondisional**: tampil bila jumlah pembiayaan ≥ Rp 50.000.000; wajib ≥15 karakter (`61-...§3a S3` — rule dimiliki `64-...§5 step 6`, cross-ref).
- **Aksi RFA** (S4): tombol `Submit RFA` (role Credit (Admin)) → route `/rfa` ringkasan read-only → ConfirmDialog dua tombol → panggil E8 dengan `Idempotency-Key` uuid yang digenerate client dan dipertahankan selama retry (BE-01 E8 header WAJIB; D-01 Step 6 idempotent). BusyButton anti double-submit (BR-CSB-20 diperluas jadi default semua tombol submit, bukan opt-in).
- **Outcome terpisah dipertahankan** `[INTENT]` BR-CASFE-15: "simpan narasi/dokumen" (persist-only) vs "Submit RFA" (lock) adalah dua aksi UI berbeda — implementasi single-endpoint-flagAction legacy TIDAK ditiru (BE-01 memisahkan E5 vs E8).

### 4.4 SCR-INT-04 — Customer Check

Form satu field (Passnet ID) + tombol cari + kartu hasil read-only (credit id, kode cabang, nama cabang) — `61-...§5.8` `[VERIFIED][INTENT]`. Tidak ada state yang dipersist. Endpoint backend belum ada di BE-01 (legacy: Reports-domain `customer-check/getData`, BR-CASFE-20 `[ARTIFACT]` — penempatan bebas) → **GAP-FE01-05 (§11)**.

---

## 5. Field & Validasi (census per form)

> Basis census: tabel "S1 field inventory by accordion section" + `input_fields` §3a KB `61-intake-cas-screens.md` (car `CAS/Index.cshtml`, motor `CAS/IndexMotor.cshtml`). KB mencatat **jumlah field per section** dan **field ter-evidensi**; baris di bawah mengenumerasi field yang ter-evidensi eksplisit di KB — bila jumlah KB > jumlah baris, sisanya WAJIB dienumerasi dari view legacy pada rentang baris yang disitir (kolom "Evidence") saat detail engineering, JANGAN dikarang. Kolom "Sumber options" merujuk reference-data dari modul masters (D-08) — lihat GAP-FE01-02.
> Validasi client-side di bawah = UX layer; **source of truth validasi = BE** (BE-01 BR-35/36/37; fix OQ-CASFE-05). Render error server (`422 details[]`) per field adalah kontrak wajib (§7).

### Form 1 — CAS Header (10 field car / ~7 motor — `61-...§3a` sec.1; evidence `CAS/Index.cshtml:52-171`)

| Field | Tipe input | Required | Format / Validasi | Sumber options | Marker |
|---|---|---|---|---|---|
| `cas_number` | text read-only | sistem | system-generated | — | `[VERIFIED][INTENT]` |
| `credit_source` (CAS Source) | select | ya | Nilai `1` → lock customer-type+source; `5` → lock source; `3` → enable lookup Order ID (BR-CASFE-11) | lookup CreditSource (masters D-08) | `[VERIFIED][INTENT]` |
| `order_id` | text + LookupDialog | kondisional | enabled HANYA saat source=3; selain itu cleared+locked (BR-CASFE-11) | paged pooling-order lookup (`CASController.cs:1064-1990`) | `[VERIFIED][INTENT]` |
| `customer_type` | select (`P` Individual / `C` Corporate) | ya | **Aplikasi baru: forced `Corporate` + locked** (BR-CASFE-3 `[VERIFIED]`; jangan direlaks diam-diam — `[OPEN]` OQ-CASFE-04 §11). Driver conditional-rendering hampir semua step (BR-CASFE-2) | konstanta `CustomerTypes` (`Collection.cs:89-93`) | `[VERIFIED]` behavior / `[OPEN]` kebijakan |
| `is_repeat_order` | toggle yes/no | ya | yes → wajib `ro_reason` + old-agreement + RO decision sebelum save (BR-CASFE-4) | — | `[VERIFIED][INTENT]` |
| `ro_reason` | select/text | bila RO=yes | — | — | `[VERIFIED][INTENT]` |

### Form 2 — Identitas (39 field car — `61-...§3a` sec.2; evidence `CAS/Index.cshtml:176-556`)

Conditional: field set Individual vs substitusi company-level untuk Corporate (BR-CASFE-2).

| Field | Tipe input | Required | Format / Validasi | Sumber options | Marker |
|---|---|---|---|---|---|
| `national_id` (NIK) | text | ya (Individual) | 16 digit; saat mencapai 16 char → prefill gender dari pola digit, radio tetap editable (BR-CASFE-10); NIK `[LOCKED]` format (BE-01 §3.2) | — | `[VERIFIED][INTENT]` |
| `identity_validity_date`, `identity_issue_date` | date | ya | DatePicker format konsisten (date-picker legacy di CAS justru DISABLED — BR-CSB-7 `[OPEN]` OQ-CSB-03; rebuild default: picker aktif seragam, USULAN) | — | `[VERIFIED]`/`[OPEN]` |
| `address` + `location` | text + LookupDialog | ya | location dari paged lookup | paged location lookup (masters) | `[VERIFIED][INTENT]` |
| `full_name` | text | ya | — | — | `[VERIFIED][INTENT]` |
| `birth_place`, `birth_date` | text + date | ya | — | — | `[VERIFIED][INTENT]` |
| `gender` | radio | ya | prefilled dari NIK (BR-CASFE-10) | kode OJK | `[VERIFIED][INTENT]` |
| `mother_name` | text | ya | — | — | `[VERIFIED][INTENT]` |
| `email` | text | — | format email | — | `[VERIFIED][INTENT]` |
| `phone` | text | ya | numerik | — | `[VERIFIED][INTENT]` |
| `npwp` (tax id) | text | — | cek panjang minimum ADVISORY legacy (warning non-blocking, `67-...§5 item 3` BR-CSB-3) — dipertahankan sebagai advisory | — | `[VERIFIED][INTENT]` |
| `residence_distance` | number | — | — | — | `[VERIFIED][INTENT]` |
| `customer_source` | select | — | — | masters | `[VERIFIED][INTENT]` |
| `marital_status` | select | ya (Individual) | driver conditional step Pasangan | lookup Marital (masters) | `[VERIFIED][INTENT]` |
| Blok jawaban kuesioner AML | question set | kondisional | revealed HANYA setelah cek APU-PPT hit (`61-...§3a` `aml_questionnaire_answers`) | — | `[VERIFIED][INTENT]` |

### Form 3 — Badan Usaha / Corporate Profile (~55 field — sec.3; evidence `CAS/Index.cshtml:556-834`; conditional `customer_type=Corporate`)

Field ter-evidensi: nama komisaris/direktur, industri, akta pendirian (no/tanggal), akta penyesuaian/perubahan, notaris, no+tanggal SIUP, no+tanggal TDP, NPWP badan, surat kelengkapan. `[VERIFIED][INTENT]`. Motor channel: hanya field dokumen "management deed" statis yang sama (BR-CASFE-19). Enumerasi penuh 55 field → view legacy rentang di atas (detail engineering).

### Form 4 — Data Pengurus / Pemilik (repeatable, 16 field/baris — sec.4; evidence `CAS/Index.cshtml:834-1071`; **car only** `[LOCKED]` fakta legacy / `[OPEN]` unifikasi OQ-CASFE-07)

| Field | Tipe input | Required | Format / Validasi | Sumber options | Marker |
|---|---|---|---|---|---|
| `name` | text | ya | — | — | `[VERIFIED]` |
| `identity_type` | select | ya | — | lookup IdentityTypeByCustomer (masters) | `[VERIFIED]` |
| `identity_number` | text | ya | KTP (kode `2`) = tepat 16 char; NPWP (kode `4`) = 15–16 char (BR-CASFE-6; server-side BE-01 BR-35) | — | `[VERIFIED][INTENT]` |
| `gender` | select | ya | kode OJK | — | `[VERIFIED]` |
| `position` | select/text | ya | — | — | `[VERIFIED]` |
| `address` + `location` | text + LookupDialog | ya | — | paged location lookup | `[VERIFIED]` |
| `ownership_share_pct` | number | ya | **Σ seluruh baris = tepat 100%** — gate transisi Next, bukan Save (BR-CASFE-5; server-side BE-01 BR-35/AC-15) | — | `[VERIFIED][INTENT]` |

Interaksi baris: add/remove client-side murni (fix EC10). Ringkasan Σ% live di footer list (USULAN).

### Form 5 — Existing Customer / Repeat Order (18 field — sec.5; evidence `CAS/Index.cshtml:1071-1304`; conditional `is_repeat_order=yes`)

| Field | Tipe input | Required | Format / Validasi | Sumber options | Marker |
|---|---|---|---|---|---|
| `old_agreement_no` | LookupDialog | ya (RO) | wajib terpilih sebelum Save (BR-CASFE-4); lookup paged by identity number, partial per channel (`AgreementOldPartialView`/`AgreementOldCarPartialView`) | paged agreement-old lookup | `[VERIFIED][INTENT]` |
| `ro_category`, `ro_decision`, `reference_source` | select / lookup | ya (RO) | `reference_source` diisi via lookup "NIK Konsumen" — semantik lookup `[OPEN]` OQ-CASFE-09 (kolom hasil terbaca sebagai lookup internal employee/marketing-source) | NIK-Konsumen paged lookup | `[VERIFIED]`/`[OPEN]` |
| `linked_bank_account` | select/lookup | — | — | bank lookup | `[VERIFIED]` |
| `topup_type` | select | — | — | — | `[VERIFIED]` |
| `instant_approval_flag` | display/toggle | — | Lane Instant-Approval = policy path auditable (D-01 Step 11); eligibility `[OPEN]` OQ-MEET-04 — FE render sebagai indikator, BUKAN kontrol keputusan | — | `[VERIFIED — doc]`/`[OPEN]` |

Catatan: fitur upload "Dokumen Loan Calculator" RO legacy = dead (`61-...§9 EC2(d)`) — tidak dirender.

### Form 6 — Data Rekening (6 field — sec.6; evidence `CAS/Index.cshtml:1304-1344`; **car only** `[LOCKED]` fakta / `[OPEN]` OQ-CASFE-07)

`bank` (paged bank lookup), `account_number` (numerik), `account_name` (text) — semua required saat section aktif. `[VERIFIED]`.

### Form 7 — Data Karakter (4 field — sec.7; evidence `CAS/Index.cshtml:1344-1384`; selalu tampil, optional — `61-...§3a` `character_source` mutability optional)

`informant_name`, `informant_address`, `credit_evaluation`. `[VERIFIED][INTENT]`.

### Form 8 — Data Penghasilan (15 field — sec.8; evidence `CAS/Index.cshtml:1384-1518`)

| Field | Tipe input | Required | Format / Validasi | Sumber options | Marker |
|---|---|---|---|---|---|
| `primary_income` | CurrencyInput | ya | **non-zero — gate transisi Next** (BR-CASFE-13) | — | `[VERIFIED][INTENT]` |
| `other_income` | CurrencyInput | — | — | — | `[VERIFIED]` |
| `employer_name/address/location/phone/fax` | text + lookup | ya (sesuai tipe) | — | location lookup | `[VERIFIED]` |
| `position`, `industry`, `commodity_type`, `work_tenure` | select/text/number | — | — | masters | `[VERIFIED]` |
| `profession` | select | ya | — | lookup Profession (masters) | `[VERIFIED]` |

### Form 9 — Data Biaya & Tanggungan (17 field — sec.9; evidence `CAS/Index.cshtml:1518-1778`)

`household_expense`, `education_expense`, `health_expense`, `other_installment_expense` (CurrencyInput), `dependent_count` (integer), `residence_status`, `residence_ownership_proof` (select). Aliasing field Individual vs Corporate (BR-CASFE-2). `[VERIFIED][INTENT]`.

### Form 10 — Data Pasangan (18 field — sec.10; evidence `CAS/Index.cshtml:1778-1933`; conditional `marital_status=married`; Corporate men-suppress section ini — BR-CASFE-2)

`married_flag`, `name`, `identity_type`+`identity_number`, `birth_place`/`birth_date`, `address`+`location`, `phone`, `occupation`, `income` (CurrencyInput) + tombol **Cek Blacklist (spouse)**. Required saat aktif. `[VERIFIED][INTENT]`. Kardinalitas spouse maks 1 — server-enforced (BE-01 `RELATED_PERSON`, AC-8).

### Form 11 — Data Penjamin (9 field — sec.11; evidence `CAS/Index.cshtml:1933-2058`; conditional guarantor toggle=yes)

`name`, `identity_type`+`identity_number`, `birth_place`/`birth_date`, `address`+`location` + tombol **Cek Blacklist (guarantor)**. `[VERIFIED][INTENT]`. Guarantor maks 1 (BE-01 §3.1); taxonomy relasi guarantor ≠ taxonomy reference (BE-01 §3.1 via BR-CUSTMASTER-8 — dua dropdown relationship BERBEDA sumbernya).

### Form 12 — Data Referensi (blok dinamis 2 ATAU 3 — sec.12; evidence `CAS/Index.cshtml:2058-2469`)

Per blok: `name`, `relationship` (taxonomy family/acquaintance), `address`, `phone_home`/`phone_office`/`phone_mobile`, `location` (lookup). `[VERIFIED][INTENT]`.
**Jumlah blok wajib dinamis**: MaxKol (kedalaman kolektibilitas biro by NIK) ≤ 3 → 2 blok; > 3 → 3 blok (BR-CASFE-9 `[VERIFIED]`; server-enforced BE-01 BR-36). FE butuh nilai MaxKol/required-count untuk merender jumlah blok — **endpoint read TIDAK ada di BE-01 → GAP-FE01-04 (§11)**. Kelengkapan blok divalidasi saat Save bila `is_references=true` (`61-...§3a` S1 transitions).

### Form 13 — Data Pembayaran Tagihan (6 field — sec.13; evidence `CAS/Index.cshtml:2469-2672`; selalu tampil)

`mail_to_source`, `mail_to_address`, `mail_to_location` (lookup), `mail_to_phone`, `payment_point_plan`. Required (`61-...§3a` `billing_address_and_payment_point` = required/shown/always). `[VERIFIED][INTENT]`.

### Form 14 — Asset & Struktur Finansial Draft (SCR-INT-02; kontrak = BE-01 E4 §5.2; depth milik FE-04/KB 64)

| Field | Tipe input | Required | Format / Validasi | Sumber options |
|---|---|---|---|---|
| `asset.chassis_no`, `asset.engine_no` | text | ya | `[LOCKED]` unik (BE-01 §3.1 `ASSET`; validasi hard-gate final milik 05/NPP) | — |
| `asset.brand/type/model_year/ownership_proof` | select/number | ya | — | masters product/asset (`12-product-asset-master.md`, D-08) |
| `dealer_code`, `subdealer_code` | LookupDialog | ya | — | masters dealer (`11-dealer-partner-master.md`, D-08) |
| `otr_price` | CurrencyInput | ya | `[LOCKED]` source (BE-01 §3.1) | — |
| `down_payment` | CurrencyInput | ya | nett DP; DP ≤ 0 akan gagal gate RFA (BE-01 BR-15) — tampilkan peringatan dini (USULAN) | — |
| `tenor_months` | number/select | ya | — | — |
| `admin_fee`, `insurance_fee` | CurrencyInput | — | admin fee: aturan minimum by application_type (BE-01 BR-22, server) | — |
| `effective_rate`, `flat_rate`, `amount_installment` | number/CurrencyInput | — | — | — |
| `umc_fields` | group | kondisional | hanya bila `application_type_id='03'` (BE-01 E4) | — |
| `subsidies.{finance,dealer,atpm,third_party,interest}` | CurrencyInput | — | — | — |

### Form 15 — Upload Dokumen (SCR-INT-03; kontrak = BE-01 E5)

| Field | Tipe input | Required | Format / Validasi | Sumber options |
|---|---|---|---|---|
| `photo_type` | select | ya | vocabulary `[LOCKED]` BR-CASFE-17: KTP, Kartu Keluarga, Slip Gaji, KTP Pasangan, KTP Penjamin, Dokumen Kepemilikan Rumah (Dokumen Loan Calculator = terikat fitur dead, evaluasi retire — BE-01 §3.1) | konstanta |
| `file` | FileUpload | ya | ekstensi `.jpg/.jpeg/.png/.pdf` + size limit (BR-CASFE-14; server otoritatif BR-37). Client pre-check MEMBLOKIR (USULAN — fix BR-CSB-16/17). Nilai limit `[OPEN]` OQ-CSB-02 | — |
| `npwp_no` | text | kondisional | tampil+wajib bila pembiayaan ≥ Rp 50jt; ≥15 char (`61-...§3a S3`) | — |

### Form 16 — Narasi Analisa Karakter (SCR-INT-03, car only — evidence `CACarController.cs:258-303`)

`capacity`, `capital`, `character`, `condition`, `collateral`, `cmo_additional_analysis`, `advantage_notes`, `deficiency_notes` — textarea, required sebelum RFA (`61-...§3a S3` mutability required), savable independen (BR-CASFE-16). **Endpoint save narasi TIDAK ada di BE-01** (BE-01 §1.2 menyatakan 5C narrative = hand-off ke 02, bukan endpoint 01) → **GAP-FE01-06 (§11)**.

### Form 17 — Customer Check (SCR-INT-04)

`passnet_id` (text, required) → hasil read-only: `credit_id`, `branch_code`, `branch_name` (`61-...§5.8`). Endpoint → GAP-FE01-05.

---

## 6. Aturan Interaksi & Staging

### 6.1 Stepper (WAJIB mempertahankan staging KB 61 §3a)

Stage top-level S1→S4 dipertahankan verbatim dari KB (S5 milik FE-03). Di dalam S1, 13 section live dikelompokkan menjadi step wizard (pengelompokan = USULAN; set section, conditional, dan gate = `[VERIFIED]` KB):

| Stepper (USULAN) | Stage KB | Section KB 61 | Conditional tampil |
|---|---|---|---|
| 1. Header & Identitas | S1 | sec.1 CAS Header, sec.2 Identitas | selalu |
| 2. Profil Korporasi | S1 | sec.3 Badan Usaha; sec.4 Pengurus/Pemilik (car); sec.6 Rekening (car) | `customer_type=Corporate` (BR-CASFE-2); sec.4/6 per `product_line` (BR-CASFE-19 `[OPEN]`) |
| 3. Repeat Order | S1 | sec.5 Existing Customer | `is_repeat_order=yes` (BR-CASFE-4) |
| 4. Profil Finansial | S1 | sec.7 Karakter, sec.8 Penghasilan, sec.9 Biaya & Tanggungan | selalu; aliasing per `customer_type` |
| 5. Pihak Terkait | S1 | sec.10 Pasangan (`married`), sec.11 Penjamin (toggle), sec.12 Referensi (blok 2/3 by MaxKol) | conditional per toggle (BR-CASFE-2/9) |
| 6. Penagihan | S1 | sec.13 Billing | selalu |
| 7. Asset & Finansial | **S2** | SCR-INT-02 | setelah Next-gate S1 lulus |
| 8. Dokumen & Narasi | **S3** | SCR-INT-03 | — |
| 9. Review & Submit RFA | **S4** | sub-view `/rfa` | tombol hanya role Credit (Admin) |

Step S1 (1–6) berbagi SATU payload save (legacy: satu form satu POST — `61-...§3a`); `Simpan Draft`/`Simpan Perubahan` bisa dipanggil dari step mana pun (E1 create pertama kali; E2 selanjutnya). Wizard TIDAK memaksa urutan linear di dalam S1 (accordion legacy membolehkan akses bebas) — stepper membolehkan lompat antar step 1–6 setelah create; transisi ke step 7 dikunci Next-gate.

### 6.2 Gate transisi (dipertahankan dari KB — `61-...§3a S1 transitions`, §5)

- **Save (S1→S1)**: referensi lengkap bila `is_references=true`; `old_agreement_no` terpilih bila RO=yes (BR-CASFE-4).
- **Next (S1→S2)**: `primary_income` non-zero (BR-CASFE-13); Corporate: Σ `ownership_share_pct` = 100% (BR-CASFE-5) + format identitas pengurus valid (BR-CASFE-6). Ketiganya juga divalidasi server (BE-01 BR-35) — FE menampilkan kegagalan server per field. Catatan legacy: Next juga me-re-save (upsert) dan me-re-check blacklist guarantor pada edit-mode Individual (`61-...§5.6`, parameter pairing aneh → `[OPEN]` OQ-CASFE-03; rebuild: re-screen dipicu server-side, FE cukup memanggil E2 lalu navigate).
- **S3→S4 (RFA)**: seluruh file valid ekstensi/size; `npwp_no` bila threshold (§5 Form 15). Gate underwriting penuh = server (E8) — FE TIDAK menduplikasi gate battery, hanya merender hasilnya.

### 6.3 Conditional rendering & disable/enable (dipertahankan)

- `customer_type` men-drive show/hide + required-ness lintas hampir semua step (BR-CASFE-2) — implementasi via SATU primitive "conditional required" shared FE-00 (rekomendasi KB 67 item 2), bukan logika per layar.
- Lock kombinasi `credit_source`/`customer_type` (BR-CASFE-3/11): new → forced Corporate + locked; edit → re-lock bila source 1 (keduanya) / 5 (source saja). **Perilaku dipertahankan sampai OQ-CASFE-04 diputuskan** — render sebagai field disabled dengan tooltip penjelasan (USULAN), bukan `pointer-events:none` diam-diam.
- `order_id` enabled hanya source=3 (BR-CASFE-11).
- Section Pengurus/Pemilik + Rekening hanya `product_line=car` (BR-CASFE-19) — dirender config-driven per product_line menunggu OQ-CASFE-07.

### 6.4 Dedup NIK UX (D-01 Step 1 `[INTENT]` — dimandatkan meeting)

- Saat create (E1) response `customer_dedup="matched_existing"` → banner info "Data nasabah ditautkan ke master existing (CUST-xxx)" + opsi lihat data master (read-only). `matched_existing`/`created_new` = kontrak E1 BE-01 §5.1.
- Response `409 DUPLICATE_INFLIGHT_APPLICATION` (default USULAN BE-01, `[OPEN]` OQ-PRD01-01) → dialog blocking: NIK memiliki draft aktif; tampilkan `credit_id` konflik bila disediakan; aksi: buka aplikasi existing / batal. Copy final menunggu resolusi OQ-PRD01-01 (§11).

### 6.5 Screening entry-time UX (BR-CASFE-7/8; D-01 Step 5)

- 3 titik cek blacklist (applicant/spouse/guarantor) memanggil **E6**; cek kuesioner AML memanggil **E7** — otomatis dipicu saat first data entry lengkap (D-01 S5; USULAN trigger: on-blur set field identitas inti) DAN tersedia manual via tombol.
- Semantik matching = milik BE: screening rebuild memakai **broad match** (BE-01 BR-04; **RESOLVED — OQ-ACQCAS-01/02**, BE-01 §11: web legacy terbukti narrow-only, broad = fix by design) → frekuensi hit dapat LEBIH TINGGI dibanding legacy; FE tetap merender hasil response E6/E7 apa adanya tanpa logika matching sendiri.
- Hit → AlertBanner warning + reveal panel kuesioner (E7); **hasil bersifat informasional — TIDAK men-disable Save/Next/RFA** (Edge Case 1 `61-...§9`: legacy tidak pernah hard-block; hard-block client = **kapabilitas BARU** yang butuh keputusan → §11). Gate otoritatif = re-derive server saat RFA (BE-01 BR-07).
- FE TIDAK mengirim flag `is_blacklist`/`is_apuppt` sebagai data otoritatif — BE me-re-derive (BE-01 BR-07; fix `61-...§3a` `entry_time_blacklist_and_apuppt_results` hidden client-supplied booleans).
- Interpretasi reason-code car vs motor yang divergen (BR-CASFE-8) TIDAK di-port ke FE — presentasi hasil mengikuti response E6/E7 server apa adanya; unifikasi = `[OPEN]` OQ-CASFE-06.
- Screening error → `503 SCREENING_UNAVAILABLE` (fail-closed BE-01 BR-06): tampilkan state error eksplisit "Screening tidak tersedia — aplikasi tidak dapat dinyatakan clean", BUKAN degradasi diam-diam.

### 6.6 RFA submit + Correction re-entry

- **RFA** (S4): confirm dialog → E8 + `Idempotency-Key`; sukses → status `rfa_locked`, seluruh form jadi read-only, banner sukses + event info (`ApplicationLocked`); gagal gate `422 RFA_GATE_FAILED` → kembali ke S3 editable, render `details[]` per rule/field (BE-01 §5.4) sebagai daftar kegagalan yang bisa diklik menuju field terkait (USULAN); `409 RFA_INVALID_STATE` → tampilkan status saat ini.
- **Correction re-entry**: aplikasi berstatus `corrected` (hasil disposisi STEP 9 E11 via FE-03, atau komite STEP 12 via 03) terbuka kembali di wizard mode correction — banner persist "Dikembalikan untuk perbaikan" di semua step. Setelah perbaikan: aksi `Re-open & Submit RFA` = panggil **E10** (reopen; memicu re-screen RAC idempotent, non-destruktif BE-01 BR-26) lalu **E8** (re-submit). Return-target correction = Step 1–7 (GT v2 STEP 9/12) → FE membuka step 1 wizard.
- **Cancel** (E9, kapabilitas BARU): hanya saat `draft`; confirm dialog dua tombol; sukses → status `cancelled`, read-only.

---

## 7. State Tampilan

- **Loading**: skeleton per form section saat fetch E3; overlay/BusyButton saat mutasi (kontrak KB 67 item 8/19 — via komponen FE-00). Setiap request WAJIB punya failure path yang terlihat user — **non-negotiable** (fix BR-CSB-10: 8/24 call site silent di CAS.js legacy; `[ARTIFACT]` do-not-replicate).
- **Empty vs failure dibedakan** pada list (dokumen, hasil lookup): "Data tidak tersedia" ≠ "Gagal memuat data dari server" (carry forward kontrak BR-CSB-11 varian (b)).
- **Status-driven display** (enum kanonik BE-01 §7 — FE TIDAK memakai huruf legacy `D/C/0/V/R/A`; konflik `R`=REJECT vs Review OQ-CASFE-10 resolved-by-design di BE-01):

| `status` | Wizard S1 | S2/S3 | Aksi tersedia |
|---|---|---|---|
| `draft` | editable | editable | Save, Next, screening, Cancel (E9), RFA (bila lengkap) |
| `rfa_locked` | read-only | read-only | — (menunggu disposisi STEP 9/RAC; badge "RFA Terkunci") |
| `corrected` | editable + banner correction | editable | Save, Re-open & Submit RFA (E10+E8) |
| `rejected` | read-only + banner reject | read-only | — (tanpa auto-close — BE-01 BR-28) |
| `cancelled` | read-only | read-only | — |
| `risk_gated`/`analyzing`/`committee`/`approved` | read-only | read-only | ditulis modul 02/03 — FE-01 hanya menampilkan badge |

- **Screening states**: idle / checking (busy) / clean / hit (warning banner + panel kuesioner) / unavailable (error fail-closed 503) — lihat §6.5.
- **Error envelope seragam** `{ code, message, details?, correlation_id }` (BE-01 §5): tampilkan `message` + `correlation_id` (copyable untuk support); `details[]` di-render per field.
- **Responsive**: form multi-kolom desktop → satu kolom mobile; stepper collapse; tabel (list dokumen, list pengurus) menjadi card-list di viewport sempit; LookupDialog full-screen sheet di mobile (USULAN).

---

## 8. Kontrak Konsumsi API (WAJIB konsisten dgn BE-01 §4/§5)

Transport final `[OPEN]` (BE-01: REST diilustrasikan; menunggu arsitektur ITEC D-11). FE layer akses via typed API client; path di bawah = kontrak resource BE-01.

| Screen / Interaksi | Endpoint BE-01 | Catatan konsumsi |
|---|---|---|
| Wizard create (Simpan Draft pertama) | **E1** `POST /credit-applications` | payload §5.1 BE-01; handle `201`, `422 details[]`, `409 DUPLICATE_INFLIGHT_APPLICATION` (§6.4) |
| Wizard save/edit (draft/corrected) | **E2** `PATCH /credit-applications/{id}` | dipanggil dari step mana pun S1; juga saat Next (upsert) |
| Load aplikasi (semua screen) | **E3** `GET /credit-applications/{id}` | sumber `status` untuk mode & §7; flag AML "as declared at intake" |
| SCR-INT-02 save | **E4** `PUT /credit-applications/{id}/asset-financials` | idempotent upsert; payload §5.2 BE-01 |
| SCR-INT-03 upload | **E5** `POST /credit-applications/{id}/documents` | multipart; render error validasi server per file |
| Cek blacklist (3 titik) | **E6** `POST /screening/blacklist` | `subject_role: applicant\|spouse\|guarantor` |
| Cek APU-PPT / kuesioner | **E7** `POST /screening/aml-questionnaire` | response §5.3 BE-01 (`hit`, `aml_risk_tier`, dst.); `503 SCREENING_UNAVAILABLE` → §6.5 |
| Submit RFA | **E8** `POST /credit-applications/{id}/rfa` | header `Idempotency-Key` WAJIB; response §5.4 BE-01 (`200`/`422 RFA_GATE_FAILED`/`409 RFA_INVALID_STATE`) |
| Cancel draft | **E9** `POST /credit-applications/{id}/cancel` | kapabilitas BARU (`[KEPUTUSAN DESAIN BARU]` BE-01) |
| Re-open dari corrected | **E10** `POST /credit-applications/{id}/reopen` | diikuti E8 (§6.6) |
| Disposisi STEP 9 Correction/Reject | **E11**/**E12** | **TIDAK dipanggil modul ini** — layar Inbox milik FE-03; FE-01 hanya menampilkan status hasilnya |
| Sync MOOFI | **E13** | system-to-system (service account MOOFI) — TIDAK ada layar FE |

Kebutuhan layar TANPA endpoint di BE-01 → GAP §11 (list aplikasi, reference-data lookups, MaxKol/required-reference-count, list+download dokumen, Customer Check, save narasi 5C, print LAHS). BE-01 §4 sendiri menyatakan lookup FE "dilayani sebagai read-only reference endpoints dari modul masters (D-08), bukan didefinisikan ulang di sini" — dependency lintas modul, bukan karangan endpoint baru di sini.

---

## 9. Acceptance Criteria

**AC-FE-1 (stepper mempertahankan staging KB)**
Given user membuka `/acquisition/intake/new`,
When wizard dirender,
Then stepper menampilkan urutan step §6.1 dengan step S1 (1–6) dapat diakses bebas setelah create, step 7 (S2) terkunci sampai Next-gate lulus, dan step 9 (RFA) hanya aktif untuk role Credit (Admin).

**AC-FE-2 (forced Corporate lock — perilaku dipertahankan)**
Given aplikasi BARU dibuka,
When step 1 dirender,
Then `customer_type` = Corporate dan `credit_source` = 1, keduanya disabled dengan tooltip penjelasan; tidak ada cara UI untuk mengubahnya (BR-CASFE-3) sampai OQ-CASFE-04 diputuskan.

**AC-FE-3 (dedup NIK — matched existing)**
Given NIK X sudah ada di master CUSTOMER,
When E1 sukses dengan `customer_dedup="matched_existing"`,
Then banner info penautan master tampil dengan `customer_id`, tanpa menghalangi lanjut mengisi.

**AC-FE-4 (dedup lock 409)**
Given NIK X punya draft in-flight,
When E1 mengembalikan `409 DUPLICATE_INFLIGHT_APPLICATION`,
Then dialog blocking tampil dengan aksi "buka aplikasi existing" / "batal"; tidak ada draft kedua terbentuk di UI.

**AC-FE-5 (Next-gate corporate ownership)**
Given `customer_type=Corporate` (car) dengan Σ `ownership_share_pct` = 90%,
When user menekan Lanjut di step 2,
Then transisi diblokir dengan pesan inline pada list pengurus (Σ harus tepat 100% — BR-CASFE-5), dan bila user mem-bypass ke server, response `422` BE (BR-35) dirender pada field yang sama.

**AC-FE-6 (format identitas pengurus)**
Given baris pengurus dengan `identity_type=KTP` dan `identity_number` 15 karakter,
When user menekan Lanjut,
Then error inline "KTP harus tepat 16 karakter" (BR-CASFE-6) dan transisi diblokir.

**AC-FE-7 (referensi dinamis 2/3)**
Given MaxKol applicant = 4,
When step 5 dirender,
Then 3 blok referensi dirender dan Save diblokir sampai ketiganya lengkap (BR-CASFE-9); bila MaxKol ≤ 3, hanya 2 blok wajib.

**AC-FE-8 (screening informasional, tidak memblokir)**
Given cek blacklist applicant (E6) mengembalikan `hit=true`,
When hasil tampil,
Then AlertBanner warning muncul + (untuk E7 hit) panel kuesioner AML ter-reveal, dan tombol Save/Next/RFA TETAP enabled (Edge Case 1 — hard-block adalah kapabilitas baru yang belum diputuskan, §11).

**AC-FE-9 (screening fail-closed tampil eksplisit)**
Given E7 mengembalikan `503 SCREENING_UNAVAILABLE`,
When panel screening dirender,
Then state error eksplisit tampil (bukan "clean" dan bukan silent) dengan `correlation_id`.

**AC-FE-10 (RFA sukses → locked)**
Given aplikasi `draft` lengkap dan role = Credit (Admin),
When user mengonfirmasi Submit RFA dan E8 mengembalikan `200 status=rfa_locked`,
Then seluruh form modul ini menjadi read-only, badge "RFA Terkunci" tampil, dan tombol RFA tidak dapat memicu submit kedua (Idempotency-Key dipertahankan; BusyButton aktif selama in-flight).

**AC-FE-11 (RFA gate gagal → tetap editable)**
Given E8 mengembalikan `422 RFA_GATE_FAILED` dengan `details:[{rule:"BR-ACQCAS-11",field:"down_payment"}]`,
When response dirender,
Then daftar kegagalan tampil dengan link menuju field `down_payment` di SCR-INT-02, dan tidak ada perubahan state tampilan lain (draft tetap editable — BE-01 BR-25).

**AC-FE-12 (correction re-entry)**
Given aplikasi berstatus `corrected`,
When user membuka `/acquisition/intake/[creditId]`,
Then wizard terbuka editable pada step 1 dengan banner correction persist, dan aksi "Re-open & Submit RFA" memanggil E10 lalu E8 secara berurutan.

**AC-FE-13 (upload gagal harus terlihat)**
Given upload E5 gagal (network/server/validasi),
When request settle,
Then pesan error tampil pada item file terkait (fix `61-...§9 EC5` + BR-CSB-10 — tidak ada failure path yang silent).

**AC-FE-14 (validasi file client memblokir)**
Given user memilih file `.exe` atau melebihi size limit terkonfigurasi,
When file dipilih,
Then seleksi ditolak dengan pesan dan file TIDAK ikut tersubmit (fix BR-CSB-16/17 non-blocking legacy); server tetap memvalidasi (BR-37).

**AC-FE-15 (stateless context)**
Given user membuka URL `/acquisition/intake/ABC123/documents` langsung (deep-link, tanpa navigasi sebelumnya),
When halaman dimuat dan sesi valid,
Then layar memuat aplikasi ABC123 via E3 tanpa bergantung state navigasi sebelumnya (fix EC11), dan tanpa sesi valid → redirect login (guard FE-00).

**AC-FE-16 (responsive)**
Given viewport mobile (≤ 640px),
When wizard/step mana pun dirender,
Then tidak ada horizontal scroll halaman; form satu kolom; stepper collapsible; LookupDialog menjadi full-screen sheet; seluruh aksi tetap dapat dijangkau.

---

## 10. Dependency

**BE (modul 01)** — BE-01-intake-cas.md: E1–E10 (§8); enum status kanonik (§7 BE-01); error envelope; kontrak validasi server (BR-35/36/37).

**FE-00 (payung — WAJIB, jangan duplikasi):** app shell + login/branch binding (KB 60; guard sesi terpusat — fix KB 60 EC3 tiga guard rusak), role-gating routing (D-09/D-10), dan shared components dari kontrak KB 67: satu grid/list (BR-CSB-11), satu LookupDialog (BR-CSB-13), satu CurrencyInput/formatter (BR-CSB-6, konvensi id-ID), satu DatePicker policy (BR-CSB-7/OQ-CSB-03), satu ConfirmDialog + vocabulary lokalisasi (BR-CSB-19/OQ-CSB-08), FileUpload dengan failure surfacing, BusyButton default.

**Modul master-data FE/BE (D-08):** reference-data endpoints untuk seluruh "Sumber options" §5 — CreditSource, Marital, Profession, IdentityTypeByCustomer, paged location/bank/agreement-old/pooling-order/NIK-Konsumen lookups, master dealer (`11-dealer-partner-master.md`), master product/asset (`12-product-asset-master.md`). Padanan legacy: `CASController.cs:905-1990`.

**FE-02:** nilai MaxKol bersumber domain SLIK/biro (BR-CASFE-9; BE-01 BR-36 — 01 read-only). **FE-03:** layar Inbox Approval (disposisi STEP 9 E11/E12 + komite). **FE-04:** sensus mendalam struktur finansial + mekanika upload/RFA channel motor (KB 64; OQ-CASFE-01).

**Eksternal:** arsitektur infra final = deliverable ITEC Bank Mega (D-11) — transport API `[OPEN]`.

---

## 11. Keputusan Dibutuhkan (Open Questions)

Tidak ada `[OPEN]` yang dijawab diam-diam; semuanya tercatat di sini.

### 11.1 GAP kontrak API (layar butuh endpoint yang TIDAK ada di BE-01 — jangan dikarang)

| GAP-ID | Kebutuhan layar | Status di BE-01 | Usulan resolusi |
|---|---|---|---|
| **GAP-FE01-01** | List/worklist aplikasi intake (`/acquisition/intake`) — filter status/cabang, entry point wizard & correction | E3 hanya GET by id; tidak ada `GET /credit-applications` (list) | Tambah list endpoint di BE-01 ATAU tegaskan worklist milik modul inbox (FE-03/BE-03) |
| **GAP-FE01-02** | Reference-data lookups §5 (sumber options) | BE-01 §4 eksplisit mendelegasikan ke modul masters (D-08) tanpa kontrak | PRD modul master-data WAJIB mengenumerasi endpoint lookup ini (census legacy: `CASController.cs:905-1990`) |
| **GAP-FE01-03** | List + preview/download dokumen terunggah (SCR-INT-03); padanan legacy `DownloadDokumen`, `CheckFotoDokumenIsFound` | E5 = upload only | Tambah `GET /credit-applications/{id}/documents` (+ download) di BE-01 |
| **GAP-FE01-04** | Required-reference-count / MaxKol untuk merender 2 vs 3 blok referensi (BR-CASFE-9); padanan legacy `GetMaxKolReference` | BR-36 server-enforced, tanpa endpoint read utk FE | Sertakan `required_reference_count` di response E3, atau endpoint read MaxKol (owner data: 02/SLIK) |
| **GAP-FE01-05** | Customer Check by Passnet ID (SCR-INT-04) | Tidak ada (legacy: Reports-domain, BR-CASFE-20 `[ARTIFACT]` — penempatan bebas) | Tetapkan modul pemilik (01 vs reporting) + definisikan endpoint |
| **GAP-FE01-06** | Save narasi 5C car (Form 16) — savable pra-RFA (BR-CASFE-16) | BE-01 §1.2: 5C narrative = hand-off ke 02, "BUKAN endpoint milik 01" | Konfirmasi kontrak save narasi di BE-02 (credit-analysis) + titik integrasi layar SCR-INT-03 |
| **GAP-FE01-07** | Print LAHS dari layar CAS (padanan legacy `PrintLAHS`, `CASController.cs:3666-3756`) | Tidak disebut | Keputusan produk: pertahankan (endpoint print/report) atau retire |

### 11.2 Open questions fungsional

| OQ-ID | Pertanyaan (dampak FE) | Prioritas |
|---|---|---|
| **OQ-CASFE-04** | Forced Corporate + `credit_source=1` locked pada aplikasi baru: kebijakan aktual atau restriksi tak disengaja? Menentukan default & opsi `customer_type` pada wizard create (§6.3, AC-FE-2). | P1 |
| **OQ-PRD01-01** | Semantik NIK dedup lock (link-only vs blokir draft in-flight): menentukan copy & aksi dialog 409 (§6.4). | P1 |
| **FE-OQ-01 (baru)** | Apakah rebuild menginginkan **hard-block client-side** pada blacklist/AML hit? Legacy TIDAK pernah memblokir (Edge Case 1) — hard-block = kapabilitas BARU; default dokumen ini: informasional + gate otoritatif di server (BR-07). Butuh sign-off compliance. | P1 |
| **OQ-CASFE-07** | Unifikasi capture korporasi motor (roster pengurus + rekening — BR-CASFE-19/EC8): menentukan config per `product_line` step 2. Terkait matriks per-product D-07/OQ-MEET-06. | P2 |
| **OQ-CASFE-06** | Interpretasi reason-code blacklist car vs motor (BR-CASFE-8) — disatukan? FE mengikuti response server; keputusan di BE/bisnis. | P2 |
| **OQ-CASFE-08** | Role gating layar narasi+RFA (CMO vs Credit (Admin)) — legacy tidak membedakan; pemetaan D-10 di §2 butuh konfirmasi. | P2 |
| **OQ-CASFE-09** | Semantik lookup "NIK Konsumen" pada section RO (terbaca sebagai lookup internal employee/marketing-source) — menentukan label & copy UI yang benar. | P3 |
| **OQ-CASFE-03** | Parameter pairing blacklist re-check saat Next (marital_status + guarantor id) — rebuild memindahkan re-screen ke server; konfirmasi input riil check. | P2 |
| **EC7 (KB 61 §9)** | Customer Check: restriksi HO-only yang disabled — restore atau retire formal? Menentukan guard SCR-INT-04 (§2). | P2 |
| **OQ-CSB-02** | Nilai size-limit upload riil (±500KB legacy tidak pernah enforced end-to-end) — menentukan konfigurasi FileUpload (AC-FE-14). | P1 |
| **OQ-CSB-03** | Kebijakan DatePicker seragam (legacy CAS disabled) — default dokumen ini: picker aktif seragam (USULAN). | P2 |
| **OQ-MEET-04** | Eligibility Instant-Approval lane — menentukan apakah `instant_approval_flag` (Form 5) editable atau display-only. | P2 |
| **OQ-MEET-06 / D-07** | Matriks step per product MACF — menentukan konfigurasi wizard per product (step applicability); mem-blok annex per-product, bukan dokumen ini. | P1 |
| **OQ-GT-01** | Dual lock path (`sp_approve_cm` vs `sp_approve_cm_moofi`) — transparan bagi FE selama kontrak E8 stabil; dipantau karena bisa menggeser perilaku gate yang dirender §6.6. → **RESOLVED — evidence (2026-07-14)** di BE: pemisah = trigger manual vs agent, kedua SP live (BE-00 §11.1); kontrak E8 tidak berubah — tidak ada dampak FE. | ~~P1~~ **RESOLVED** (BE) |
| **OQ-CASFE-01** | Otoritas synthesis layar shared upload/RFA (KB 61 vs 64) — pembagian FE-01 vs FE-04 di §1.2 mengikuti KB 61; konfirmasi saat review lintas modul. | P2 |
| **OQ-ARCH-STACK** | Transport API final (REST/gRPC) + topologi — menunggu ITEC (D-11); mempengaruhi API client layer, bukan komposisi layar. | P1 (ITEC) |

> Catatan marker-fidelity: `[LOCKED]` yang dibawa modul ini — vocabulary photo-type (BR-CASFE-17), sensus role D-10, penghapusan super-user D-09, Next.js D-12, fakta hard-branch product_line (BR-CASFE-1, direalisasikan config-driven), kode `application_type_id` (BR-CASFE-18), format NIK/NPWP/chassis/engine (via BE-01). `[INTENT]` — staging S1–S4, gate Next, conditional rendering, dedup-lock UX, typed related-person, stateless context, screening informasional. `[ARTIFACT]` yang TIDAK ditiru — accordion single-form, session-affinity, dead sections (pembatalan/AO/loan-calculator), silent failure, size-check non-blocking, tiga lookup-dialog divergen. `[OPEN]` — seluruh §11.
