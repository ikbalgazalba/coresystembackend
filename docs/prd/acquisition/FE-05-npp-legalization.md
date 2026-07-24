# PRD — NPP Legalization (STEP 15) [FE]

> **Audience**: Tim Frontend (FE). **Target stack**: **Next.js** `[LOCKED per D-12]`. **Tanggal**: 2026-07-14.
> **Pasangan BE**: `docs/prd/acquisition/BE-05-npp-legalization.md` — kontrak API di §8 dokumen ini WAJIB konsisten dengan §4/§5 file itu; endpoint yang dibutuhkan layar tetapi TIDAK ada di BE PRD dicatat sebagai **GAP** di §11 (tidak dikarang).
> **Sumber otoritatif**: `.mega-sdd/knowledge-base/.sp-manifests/_ACQUISITION-GROUND-TRUTH.md` v2 (16-STEP, PDF 08072026) + `.mega-sdd/knowledge-base/.sp-manifests/_MEETING-DECISIONS-2026-07.md` (D-01..D-12) + KB FE `60-frontend/65-npp-vertel-screens.md` (field census layar legacy) + KB FE `60-frontend/60-app-shell-auth-navigation.md`, `60-frontend/67-client-side-behavior.md` (konvensi shell & client-side) + KB BE `10-domains/24-npp-legalization-downstream.md`, `10-domains/32-disbursement-subledger.md`, `50-integrations/passnet-mf-payment-sync.md`, `50-integrations/email-sms-notifications.md`.
> **Status**: Revisi post-meeting. Layar legacy (FINCORE.WEB) = **EVIDENCE, bukan mandat desain** — OUTCOME (field, aturan, staging, role-gating) dipertahankan; UX Next.js baru ditandai **USULAN**. NFR: **responsive mobile + desktop**. **Super-user TIDAK ADA** (D-09 `[LOCKED]`).

Modul FE **05-npp-legalization** adalah presentation layer **gerbang legalisasi final** (STEP 15 — GT `:66-71`): admin cabang memvalidasi **BAST** fisik & aset, meng-input/memvalidasi **nomor rangka (chassis) & mesin (engine)**, submit **RFA NPP**; **Kepala Cabang** me-review dan merekam keputusan (Approve → aktivasi kontrak atomik di BE); pasca-aktivasi layar menampilkan **status aktivasi kontrak** (jurnal/AR Card/master loan/Passnet/email dealer — D-05/D-06/D-03) dan membuka **cetak dokumen legal** (Perjanjian Pembiayaan/PK + pendamping — D-04). Semua gate keras di-enforce **BE in-transaction** (BE-05 §6); FE hanya me-mirror gate sebagai UX preventif — FE TIDAK boleh menjadi satu-satunya penegak aturan (perbaiki pola client-side-only legacy, `65-npp-vertel §7` BR-NPPVTL-1/2/9; `_screen-inventory.md` seam #4).

---

## 1. Ruang Lingkup & Kepemilikan

### 1.1 Screens yang DIMILIKI modul ini

| Screen | Fungsi | Evidence legacy | Sumber |
|---|---|---|---|
| **SCR-NPP-01 — NPP Queue/List** | Antrian & listing NPP per branch, filter status, aksi Edit/Detail/Print status-gated. | `Views/Acquisition/NPP/Index.cshtml` | `65-npp-vertel §5` item 11,19; BR-NPPVTL-10/11/18 |
| **SCR-NPP-02 — NPP Entry (Create)** | Form validasi BAST + input chassis/engine + billing + dealer-payment; pilih record acquisition via lookup; submit RFA (dan Draft — lihat OQ-NPPVTL-09). | `Views/Acquisition/NPP/Add.cshtml` | `65-npp-vertel §3a` NPP-S1 |
| **SCR-NPP-03 — NPP Entry (Edit)** | Field set sama dgn Create, pre-filled; dipakai re-work setelah Correction. | `Views/Acquisition/NPP/Edit.cshtml` | `65-npp-vertel §3a` NPP-S1; BR-NPPVTL-10 |
| **SCR-NPP-04 — NPP Detail / Approval / Activation Status** | View read-only; mode approval Kepala Cabang (Approve/Reject/Correction + reason); pasca-aktivasi: panel status aktivasi + menu cetak dokumen legal + audit history. | `Views/Acquisition/NPP/View.cshtml` + `Index.cshtml` print menu | `65-npp-vertel §3a` NPP-S2, §5 item 8–11 |

Dialog/komponen milik modul: **CreditLookupDialog** (pengganti `NppProcessCreditPartialView`), **DealerBankRefLookupDialog** (pengganti `DealerBankRefPartialView`), **DecisionDialog** (pengganti `ApprovalActionPartialView` reason-picker), **PreflightChecklistPanel** (visualisasi `GET /npp/{id}/preflight`), **DocumentPrintMenu**. `[VERIFIED]` padanan legacy (`65-npp-vertel §11`); bentuk komponen Next.js = USULAN.

### 1.2 BUKAN milik modul ini (non-goal)

| BUKAN dimiliki | Pemilik | Catatan |
|---|---|---|
| **Layar Vertel (STEP 14)** — interview telepon, RFA Vertel, approval Vertel | modul FE Vertel/verification (D-02) | KB `65-npp-vertel` mendokumentasikan keduanya dalam satu file; PRD ini hanya mengambil keluarga layar NPP. FE-05 hanya **menampilkan** status Vertel sebagai prasyarat (read-only). Kepemilikan file FE PRD utk Vertel = [OPEN], §11 GAP-FE05-04. |
| **Approval Inbox shell** (antrian keputusan lintas-modul) | FE-03 approval-committee / inbox (KB `63-approval-inbox-screens.md`) | Legacy me-redirect ke inbox setelah Approve (`65-npp-vertel §5` item 10); FE-05 hanya mendefinisikan deep-link masuk/keluar. |
| **App shell, login, navigasi, session/branch context** | FE-00 OVERVIEW (KB `60-app-shell-auth-navigation.md`) | Identitas session dipakai stamp `acting_employee`; jangan duplikasi. |
| **Shared components** (DataTable, LookupDialog primitive, ConfirmDialog, CurrencyField, DateField, Toast/Alert) | FE-00 OVERVIEW (KB `67-client-side-behavior.md`) | FE-05 mengkonsumsi; §10. |
| **Rendering/generate PDF dokumen legal** | BE-05 (dataset via endpoint reports, BR-NPP-4/D-04) | FE hanya memicu unduh/print on-demand. |
| **Master data (dealer, reason codes, user)** | modul master-data (D-08; KB `66-master-data-screens.md`) | FE-05 mengkonsumsi lookup-nya. |
| **Layar downstream STEP 16** (Dealer Payment, BPKB, Insurance) | modul/context masing-masing (PULL — D-01 Step 15; GT `:72-73`) | FE-05 tidak menampilkan/mengeksekusi workflow downstream. |

### 1.3 Reengineering mandate FE (bukan mirror legacy)

- **Client-side guard = UX preventif, BUKAN enforcement**: semua gate (chassis/engine, BAST, due-date, verification) otoritatif di BE (BE-05 §6 BR-NPP-N1/N2/N5/N8); FE me-mirror agar user tidak membentur error yang bisa dicegah. Jangan replikasi pola "rule hanya hidup di browser" (`_screen-inventory.md` seam #4).
- **BAST WAJIB di form** — legacy TIDAK memvalidasi BAST di submit guard (Edge Case 2 `65-npp-vertel`); rebuild menjadikan `bast_no`+`bast_date` required di FE, selaras hard-gate D-01 Step 14 (BE BR-NPP-N2).
- **Cek chassis berlaku SEMUA lini** — legacy hanya memicu check di layar motor (Edge Case 5 `65-npp-vertel`); FE baru memicu advisory check utk motor & mobil (selaras BE BR-NPP-N5).
- **Satu form config-driven car/motor** — legacy memakai layar+endpoint terpisah motor vs car (`65-npp-vertel §5` item 7); rebuild: satu screen, asset-type sebagai data (selaras BE BR-NPP-N9).
- **Do-not-replicate FE**: batas tahun hardcoded 2000–2100 (BR-NPPVTL-5 `[ARTIFACT]`), label status drift "Reject/Review" (BR-NPPVTL-11 `[ARTIFACT]`), silent AJAX failure swallowing & broken guards (`67-client-side §9`), dead draft-save controls (Edge Case 9 `65-npp-vertel`), decision-button gating via label-string yang tak pernah ter-assign (Edge Case 1 `65-npp-vertel` — bug Vertel; jangan tiru polanya di NPP).
- **Super-user dihapus** (D-09 `[LOCKED]`): tidak ada affordance UI bypass approval dalam bentuk apa pun.

---

## 2. Aktor & Peran (akses per screen, role-gating)

Role census cabang target-state (D-10 `[LOCKED]`): **CMO, Marketing Head, Credit Analyst, Kepala Cabang, Credit (Admin)**. Legacy FE TIDAK punya role-gating client-side eksplisit — akses via menu per-employee + flag generik "pending approver" (`65-npp-vertel §2`; `60-app-shell` BR-SHELL-4). Rebuild memodelkan **role eksplisit** (klaim role di token/session — mekanisme final tergantung arsitektur ITEC D-11, [OPEN] OQ-ARCH-STACK; mapping role Kepala Cabang = OQ-NPPVTL-02).

| Aktor | SCR-NPP-01 List | SCR-NPP-02/03 Entry | SCR-NPP-04 Detail/Approval |
|---|---|---|---|
| **Credit (Admin) / Admin Cabang** (maker) | Lihat + aksi Edit (status editable) + Print (status `active`) | **Full access**: create, edit, submit RFA | View read-only + history; TIDAK melihat tombol keputusan |
| **Kepala Cabang** (checker) | Lihat (filter "menunggu keputusan saya") | Tidak ada akses tulis | View + **panel keputusan** (Approve/Reject/Correction) — hanya bila ia **assigned pending checker** record itu **dan bukan submitter** (self-approval diblokir — D-01 Step 11; BE BR-NPP-N15) |
| **CMO / Marketing Head / Credit Analyst** | [OPEN] — read-only atau tanpa akses; matrix menu final = keputusan (§11 GAP-FE05-05) | — | — |
| **Audit** (bila ada role read) | Lihat | — | View + history (`GET /npp/{id}/history` — BE-05 §4) |

Aturan gating FE:
- **Tombol keputusan** dirender hanya bila: screen dalam konteks approval **dan** viewer = assigned pending checker (outcome BR-NPPVTL-8 `[INTENT]`, `View.cshtml:690`) **dan** viewer ≠ submitter (tambahkan di FE sebagai UX; enforcement tetap BE `403 SELF_APPROVAL_BLOCKED` — BE-05 §5.3).
- **Edit/Print di listing** legacy hanya status-gated, bukan identity-gated (BR-NPPVTL-18) — rebuild menambahkan role-gating (maker utk Edit) di atas status-gating. Ditandai **USULAN** (memperketat, tidak melonggarkan outcome).
- Identitas `acting_employee` diambil dari session shell (FE-00; `60-app-shell §6` session state), tidak pernah di-input manual.

---

## 3. Peta Screen & Route (inventori + usulan route Next.js)

Prefix route group acquisition mengikuti konvensi FE-00. Seluruh route = **USULAN** (App Router).

| Screen | Route (USULAN) | Jenis render | Guard akses |
|---|---|---|---|
| SCR-NPP-01 List | `/acquisition/npp` | List + server-side pagination/filter (`GET /npp`) | Role modul NPP (maker/checker/read) |
| SCR-NPP-02 Create | `/acquisition/npp/new` | Form client-heavy (lookup, advisory validations) | Credit (Admin) |
| SCR-NPP-03 Edit | `/acquisition/npp/[id]/edit` | Form (pre-filled); hanya reachable bila status editable (`pending`, `held(correction)` — BE BR-NPP-5) | Credit (Admin) |
| SCR-NPP-04 Detail/Approval/Status | `/acquisition/npp/[id]` | Detail; panel keputusan muncul role+state-driven (bukan varian URL) | Semua role modul; panel keputusan: Kepala Cabang assigned |

Navigasi & deep-link:
- Entry point checker: dari **Approval Inbox** (FE-03) → deep-link `/acquisition/npp/[id]`; setelah keputusan sukses, kembali ke inbox (outcome legacy redirect — `65-npp-vertel §5` item 10; mekanik back-nav = USULAN).
- Entry point maker: menu modul → `/acquisition/npp` → tombol "Buat NPP" → `/acquisition/npp/new`.
- Mode approval TIDAK memakai query `?mode=approval` (legacy memakai varian action approval) — panel keputusan dirender dari kombinasi (status `validated` + viewer = pending checker). **USULAN** — menghindari URL yang bisa dibagikan untuk memaksa mode.
- Row list → klik = detail; icon Edit hanya utk baris editable; menu Print hanya baris `active` (BR-NPPVTL-10 → lihat §6).

---

## 4. Komposisi Layar & Komponen

Referensi shared components dari FE-00 (KB `67-client-side-behavior.md`): DataTable (satu grid contract, adopsi pembedaan empty-vs-fetch-failed — BR-CSB-11), LookupDialog primitive (satu implementasi — BR-CSB-13), ConfirmDialog dua-tombol (BR-CSB-18/19), CurrencyField (satu formatter id-ID — BR-CSB-5/6), DateField (picker konsisten semua field tanggal — BR-CSB-7), Toast/AlertDialog envelope-aware (BR-CSB-9), button-busy + anti double-submit (BR-CSB-20). Jangan duplikasi di modul.

### 4.1 SCR-NPP-01 — NPP Queue/List

- **Layout**: page header (judul + tombol "Buat NPP" utk maker) → filter bar → DataTable → pagination. Mobile: filter collapse jadi sheet; tabel → card list per baris (USULAN; NFR responsive).
- **Filter bar**: status (enum kanonik), pencarian (credit_id / agreement_no / nama customer — kolom pencarian final ikut kontrak `GET /npp`, §8), branch (default = branch session; lintas-branch bila diizinkan — [OPEN] ikut kebijakan FE-00).
- **Kolom** (USULAN, diturunkan dari data yang tersedia di list legacy + kontrak BE): credit_id, agreement_no (kosong sebelum aktif), nama customer, dealer, tanggal pengajuan, **StatusBadge**, kolom aksi.
- **Aksi per baris** (status-gated — BR-NPPVTL-10/18): Detail (selalu), Edit (status editable + role maker), **PrintMenu** (hanya `active`).
- **Komponen**: DataTable (FE-00), StatusBadge (§7), DocumentPrintMenu (modul).

### 4.2 SCR-NPP-02/03 — NPP Entry (Create/Edit)

Satu form config-driven (motor & mobil satu layar — §1.3), disusun sebagai section berurutan (bukan wizard multi-halaman; staging maker→checker lintas-screen ada di §6):

1. **Section A — Record Acquisition**: tombol "Pilih Kontrak" → **CreditLookupDialog** (`GET /npp/lookups/credits`); setelah dipilih, panel read-only pre-fill: credit_id, PO number, tanggal acquisition, nama customer, dealer, application/item type (`65-npp-vertel §5` item 1). Ganti kontrak = re-konfirmasi (mengosongkan field dependen).
2. **Section B — Validasi BAST (fisik & aset)**: `bast_no`, `bast_date` — **required** (D-01 Step 14; beda dari legacy, §1.3). Callout kecil menjelaskan BAST = prasyarat aktivasi.
3. **Section C — Identitas Kendaraan**: `chassis_no`, `engine_no`, `item_color`; on-blur chassis/engine → advisory check (`GET /npp/validations/chassis`) dengan hasil inline (§6).
4. **Section D — Billing & Tanggal**: `bill_received_date` (memicu kalkulasi `installment_date` via `GET /npp/validations/installment-date`), `bill_receipt_date`, `down_payment_receipt_no`(+date), `receipt_no`(+date), `bpkb_letter_no`(+date), `installment_date` (auto-lock / editable terbatas — §5/§6).
5. **Section E — Dealer Payment**: tombol "Pilih Rekening Dealer" → **DealerBankRefLookupDialog** (`GET /npp/lookups/dealer-bank-references?dealer_id=`); `disbursement_method` (conditional — UMC/auto-debit application type); ringkasan nilai disbursement vs plafon (guard BR-NPPVTL-6).
6. **Section F — Order Source Dealer (car only)**: baris dealer-personnel TAC & third-party (repeat-rows add/remove) — hanya dirender utk aplikasi mobil (`65-npp-vertel §3a` NPP-S1).
7. **Section G — Asuransi**: `insurance_coverage_period` (nilai di-reset BE saat aktivasi — BR-NPP-12/OQ-NPP-10; tampilkan hint).
8. **Action bar** (sticky bottom di mobile): Batal · Simpan Draft ([OPEN] OQ-NPPVTL-09) · **Submit RFA** (ConfirmDialog dua-tombol sebelum kirim — BR-CSB-18).

Untuk mobil non-top-up: tombol **"Check Asset"** (pengganti Check Rapindo — `GET /npp/validations/asset-check`) di Section C dengan indikator hasil; RFA di-disable sampai lolos/skippable (Edge Case 3 `65-npp-vertel`; OQ-NPPVTL-04).

### 4.3 SCR-NPP-04 — Detail / Approval / Activation Status

- **Header**: credit_id + agreement_no + StatusBadge besar + breadcrumb.
- **Body**: seluruh field entry dirender read-only (grup section sama dgn 4.2 — konsistensi mental model); baris order-source read-only (outcome `hidden_fields_vs_prior` `65-npp-vertel §3a` NPP-S2).
- **PreflightChecklistPanel** (hanya utk viewer checker saat status `validated`): hasil `GET /npp/{id}/preflight` sebagai checklist visual — verifikasi Vertel (`verified` + umur ≤30 hari), chassis/engine, kelengkapan BAST, due-date ≥ approve-date (BE-05 §4). Item gagal = merah + pesan; tombol Approve tetap ada tetapi keputusan final tetap ditolak BE bila gate gagal (§6, §8).
- **Panel Keputusan** (role+state-gated — §2): tombol Approve / Reject / Correction → **DecisionDialog**: pilihan `reason_id` (wajib utk Reject/Correction; sumber options = GAP-FE05-01 §11) + `reason_desc` opsional + ConfirmDialog.
- **Panel Status Aktivasi** (hanya status `active`): agreement_no, activated_at, approver; indikator output STEP 15: dokumen PK tersedia (D-04), jurnal + AR Card + amortisasi (D-06), master loan (D-05), Passnet sync status (`passnet_id`, pending/sent/failed — enum `out_event.status` BE §3.1.8), email dealer status (D-03 pending/sent/failed). Field census panel ini bergantung kontrak `GET /npp/{id}` — GAP-FE05-02 §11. **Catatan [OPEN]**: indikator master loan & ledger dirender sebagai status **provisional** — BE menandai field census/ownership master loan **[OPEN — OQ-MEET-02]** dan GL mapping **[OPEN — OQ-MEET-03]** (BE-05 §3.1.12/§5.3 note; kontrak final menunggu D-11); FE tidak boleh mengklaim bentuk final field ini.
- **DocumentPrintMenu** (hanya `active` — BR-NPP-4 `[LOCKED]` gate availability): Perjanjian Pembiayaan (`GET /npp/{id}/agreement`) + dokumen pendamping (`GET /npp/{id}/documents/{docType}` — `approval-letter`, `statement-letter`, `power-of-attorney-fiducia`, `power-of-attorney-withdrawal`, `important-notice`, `mou`; set dari BE-05 §4).
- **Audit History Panel**: timeline maker-checker dari `GET /npp/{id}/history`.

---

## 5. Field & Validasi (census per form)

> Kolom **Marker** mengikuti KB: `[LOCKED]` = wajib verbatim; `[INTENT]` = outcome dipertahankan, bentuk bebas; `[ARTIFACT]` = legacy dibuang/diganti; **USULAN** = desain baru FE. Validasi FE = preventif; otoritatif di BE (BE-05 §6). Semua field date memakai DateField FE-00 (picker aktif konsisten — jangan tiru enforcement setengah-jalan BR-CSB-7); semua field uang memakai CurrencyField id-ID (BR-CSB-5/6).

### 5.1 Form Entry NPP (SCR-NPP-02/03) → payload `POST /npp` / `PUT /npp/{id}` (BE-05 §5.1)

| # | Field (target) | Label (ID) | Tipe input | Required | Format & validasi FE | Sumber options/nilai | Marker | Sumber |
|---|---|---|---|---|---|---|---|---|
| 1 | `po_id` (via credit lookup) | Kontrak / Credit ID | LookupDialog (read-only setelah pilih) | **Ya** | Harus hasil lookup sukses — free-typing DILARANG (outcome legacy) | `GET /npp/lookups/credits` (hanya record eligible: PO issued + Vertel approved — BE-05 §4/§5.1) | [INTENT] | `65-npp-vertel §3a` NPP-S1; §5 item 1 |
| 2 | — (pre-fill display) | Tgl Acquisition, No PO, Nama Customer, Dealer, Application/Item Type | read-only text | — | Non-editable; ter-reset bila lookup diganti | payload lookup | [INTENT] | `65-npp-vertel §5` item 1 |
| 3 | `bast_no` | No. BAST | text | **Ya (BARU)** | non-empty; trim; max-length ikut kontrak BE ([OPEN] tidak dispesifikasi KB) | manual | **[INTENT — diputuskan meeting]** D-01 S14 | BE BR-NPP-N2; Edge Case 2 `65-npp-vertel` |
| 4 | `bast_date` | Tgl BAST | DateField | **Ya (BARU)** | tanggal valid; range bisnis menggantikan bound 2000–2100 legacy `[ARTIFACT]` (nilai final = OQ-NPPVTL-08) | date picker | **[INTENT — diputuskan meeting]** | BR-NPPVTL-5; D-01 S14 |
| 5 | `chassis_no` | No. Rangka | text (uppercase) | **Ya** | non-empty; on-blur → advisory check §6 (SEMUA lini — beda legacy motor-only); panjang 17-char legacy khusus motor = aturan lini, tampilkan sebagai warning advisory bukan hard block FE ([OPEN] format final per lini ikut BE) | manual + `GET /npp/validations/chassis` | chassis/engine **[LOCKED]** (identitas aset); check-scope [INTENT] | BR-NPPVTL-1/7; BE BR-NPP-1/N5 |
| 6 | `engine_no` | No. Mesin | text (uppercase) | **Ya** | non-empty; ikut advisory check yang sama | manual + endpoint sama | **[LOCKED]** | BR-NPPVTL-1; BE BR-NPP-1 |
| 7 | `item_color` | Warna Unit | text | **Ya** | non-empty | manual | [INTENT] | BR-NPPVTL-1 |
| 8 | `bill_received_date` | Tgl Terima Tagihan | DateField | Tidak | on-change → `GET /npp/validations/installment-date` (kalkulasi/lock installment date) | date picker | [INTENT] | `65-npp-vertel §5` item 4 |
| 9 | `bill_receipt_date` | Tgl Kuitansi Tagihan | DateField | Tidak | tanggal valid | date picker | [INTENT] | `65-npp-vertel §3a` NPP-S1 |
| 10 | `down_payment_receipt_no` (+ `down_payment_receipt_date`) | No./Tgl Kuitansi DP | text + DateField | Tidak | tanggal valid | manual | [INTENT]; nama field date di kontrak BE = GAP-FE05-03 | `65-npp-vertel §3a` NPP-S1; BE §5.1 |
| 11 | `receipt_no` (+ `receipt_date`) | No./Tgl Kuitansi | text + DateField | Tidak | tanggal valid | manual | [INTENT]; GAP-FE05-03 | `65-npp-vertel §3a` NPP-S1 |
| 12 | `bpkb_letter_no` (+ `bpkb_letter_date`) | No./Tgl Surat BPKB | text + DateField | Tidak | tanggal valid | manual | [INTENT]; GAP-FE05-03 | `65-npp-vertel §3a` NPP-S1; BE §5.1 |
| 13 | `installment_date` | Tgl Angsuran Pertama | DateField | **Ya** | **Auto-calculated & LOCKED** bila billing cycle cocok (read-only); bila editable: dibatasi window kecil dari tanggal berjalan (nilai window final ikut BE — kalkulasi di BE, FE display-only) | `GET /npp/validations/installment-date` | [INTENT] (BR-NPP-11) | BR-NPPVTL-4; `65-npp-vertel §5` item 4; BE §4 |
| 14 | `bank_reference_id` | Rekening Bank Dealer | LookupDialog | **Ya** | harus hasil lookup sukses | `GET /npp/lookups/dealer-bank-references?dealer_id=` | [INTENT] | BR-NPPVTL-1; BE §4 |
| 15 | `disbursement_method` | Metode Pencairan | select | **Conditional** — wajib bila application type UMC/auto-debit | required-toggle runtime (pola BR-CSB-2 shared primitive) | options: [OPEN] — sumber options tidak ter-ekstrak KB; kontrak BE = GAP-FE05-03 | [INTENT] | `65-npp-vertel §3a` NPP-S1 |
| 16 | `dealer_order_source_tac[]` / `dealer_order_source_third_party[]` | Order Source Dealer (TAC / Pihak Ketiga) | repeat-rows (personel, nilai) | Tidak (car only) | render hanya utk aplikasi mobil; validasi total subsidi vs max = [OPEN] OQ-NPPVTL-05 (legacy guard di-comment — BR-NPPVTL-19; JANGAN putuskan diam-diam) | manual + master personel dealer | [INTENT] | `65-npp-vertel §3a` NPP-S1; BR-NPPVTL-19 |
| 17 | `insurance_coverage_period` | Periode Cover Asuransi | number (bulan) | Tidak | integer > 0; hint "nilai dapat di-reset sistem saat aktivasi" (BR-NPP-12; OQ-NPP-10) | manual | [INTENT] | BE §3.1/§6.1 |
| 18 | (derived, car) total disbursement | — | computed display | — | **Guard**: requested repayment + disbursement received ≤ payment amount kontrak → blok RFA dgn pesan | computed | [INTENT] | BR-NPPVTL-6 |

**Guard submit RFA (client-side preventif)** — tombol RFA disabled + daftar kekurangan inline bila belum terpenuhi: field #1,3,4,5,6,7,13,14 terisi; #15 bila conditional aktif; guard #18 lolos; car non-top-up: asset-check lolos/skippable (BR-NPPVTL-1/3 + BAST baru §1.3). Error BE (`422 VALIDATION_ERROR` details) tetap dipetakan balik per-field (§7).

### 5.2 Form Keputusan Checker (DecisionDialog, SCR-NPP-04) → `POST /npp/{id}/decision` (BE-05 §5.3)

| Field | Label | Tipe | Required | Validasi FE | Sumber options | Marker | Sumber |
|---|---|---|---|---|---|---|---|
| `action` | Keputusan | tombol Approve / Reject / Correction | **Ya** | satu pilihan; label kanonik ("Rejected", bukan drift "Reject/Review" — `[ARTIFACT]` BR-NPPVTL-11) | enum kanonik BE §7 | [LOCKED] enum action | `65-npp-vertel §3a` NPP-S2; BE §3.1 |
| `reason_id` | Alasan | select | **Wajib bila Reject/Correction** | required-toggle by action | master reason codes — **endpoint lookup TIDAK ada di BE-05 §4 → GAP-FE05-01** | [INTENT] | `65-npp-vertel §5` item 9; BE §5.3 |
| `reason_desc` | Keterangan | textarea | Tidak | max-length ikut BE ([OPEN]) | manual | [INTENT] | `65-npp-vertel §3a` NPP-S2 |
| (header) `Idempotency-Key` | — | auto-generated FE (UUID per intent keputusan) | **Ya (Approve)** | satu key per sesi dialog; re-klik/retry memakai key sama | generated | [KEPUTUSAN DESAIN BARU] | BE §4/§5.3 BR-NPP-N4 |
| (implisit) `acting_employee` | — | dari session | **Ya** | tidak editable | session shell FE-00 | [INTENT] | `60-app-shell §6` |

### 5.3 Filter List (SCR-NPP-01) → `GET /npp`

| Field | Tipe | Default | Catatan | Sumber |
|---|---|---|---|---|
| `status` | select multi/single | semua | enum kanonik `pending·validated·active·held` + disposition; label tunggal (perbaiki drift Edge Case 12 BE) | BE §4 `GET /npp` |
| `branch` | select | branch session | lintas-branch = kebijakan FE-00 [OPEN] | BE §4 |
| pencarian teks | text | — | parameter pencarian final ikut kontrak BE ([OPEN] — BE §4 hanya menyebut filter status/branch → kolom search tambahan = GAP-FE05-02) | BE §4 |

---

## 6. Aturan Interaksi & Staging

### 6.1 Staging maker→checker (dari KB §3a — outcome WAJIB dipertahankan)

Dua stage lintas-screen (bukan wizard satu-halaman) — `65-npp-vertel §3a`:

| Stage | Screen | Aktor | Transisi |
|---|---|---|---|
| **NPP-S1** — data entry & submit RFA | SCR-NPP-02/03 | Credit (Admin) | Submit RFA → `POST /npp` + `POST /npp/{id}/submit` → status `validated`; gagal chassis (`422 CHASSIS_ENGINE_MISMATCH`) → tetap di form dgn error field-level |
| **NPP-S2** — review & keputusan | SCR-NPP-04 | Kepala Cabang | Approve → aktivasi (BE atomik) → panel status aktivasi; Reject/Correction → `held`; Correction re-openable oleh maker via Edit |

Draft-path: BE menyediakan state `pending` (draft) sah (`POST /npp` tanpa submit); web legacy RFA-only (Edge Case 9 `65-npp-vertel`; BR-NPPVTL-20). Keputusan FE baru memakai tombol "Simpan Draft" genuine = **OQ-NPPVTL-09** ([OPEN] — §11; bila diputuskan ya, alur: Simpan Draft = `POST /npp`/`PUT` saja; Submit RFA = lanjut `POST /npp/{id}/submit`).

### 6.2 Conditional rendering & disable/enable

| # | Trigger | Efek UI | Marker | Sumber |
|---|---|---|---|---|
| 1 | Application/item type = **car** | Section F (order source TAC/third-party) dirender; guard #18 §5.1 aktif | [INTENT] | `65-npp-vertel §3a` NPP-S1 |
| 2 | Application type = **UMC/auto-debit** | `disbursement_method` tampil + required (runtime toggle — shared primitive BR-CSB-2) | [INTENT] | `65-npp-vertel §3a` NPP-S1 |
| 3 | **Car non-top-up** | Tombol "Check Asset" tampil; RFA disabled hingga check sukses ATAU flag skippable dari BE (`GET /npp/validations/asset-check`) | [INTENT]; kelanjutan integrasi [OPEN] OQ-NPPVTL-04 | BR-NPPVTL-3; Edge Case 3 `65-npp-vertel` |
| 4 | `bill_received_date` diisi | Panggil `GET /npp/validations/installment-date`; bila match billing cycle → `installment_date` terisi otomatis + **read-only (locked)**; bila tidak → editable dgn window terbatas + helper text | [INTENT] BR-NPP-11 | BR-NPPVTL-4; `65-npp-vertel §5` item 4 |
| 5 | Blur `chassis_no`/`engine_no` (semua lini) | Panggil `GET /npp/validations/chassis` (debounced); hasil inline: OK hijau / mismatch merah + detail; mismatch TIDAK hard-block di FE entry (hard check di submit+approve BE) tetapi tampil menonjol | [KEPUTUSAN DESAIN BARU] (perluasan scope motor-only legacy) | Edge Case 5 `65-npp-vertel`; BE BR-NPP-N5, §4 |
| 6 | Lookup kontrak diganti (Section A) | ConfirmDialog; field dependen (C–G) di-reset | USULAN | pola lookup `65-npp-vertel §5` item 1 |
| 7 | Status record ∉ {`pending`, `held(correction)`} | Route Edit tidak ditawarkan; akses langsung → redirect detail + notice (mirror guard `PUT` BE BR-NPP-5). Catatan: editability saat `validated` = keputusan stakeholder (OQ-CMPO-01 — BE §4 note); FE default TIDAK menawarkan edit saat `validated` | [INTENT] | BR-NPPVTL-10; BE §4 |
| 8 | Viewer = assigned pending checker & status `validated` & viewer ≠ submitter | PreflightChecklistPanel + Panel Keputusan dirender; selain itu view-only | [INTENT] (BR-NPPVTL-8) + D-01 S11 | `View.cshtml:690`; BE BR-NPP-N15 |
| 9 | Klik **Approve** | (a) refresh `GET /npp/{id}/preflight`; (b) bila ada item gagal → tampilkan blocker + keputusan TIDAK dikirim (outcome BR-NPPVTL-9 — pre-check sebelum mutasi); (c) lolos → ConfirmDialog → `POST /decision` dgn Idempotency-Key. Reject/Correction skip preflight (BR-NPPVTL-9) tetapi tetap ConfirmDialog + reason wajib | [INTENT] | BR-NPPVTL-9; BE §4 preflight |
| 10 | Status `active` | Panel Status Aktivasi + DocumentPrintMenu unlocked; Edit hilang permanen | **[LOCKED]** gate print Approved-only | `65-npp-vertel §6`; BE BR-NPP-4 |
| 11 | Double-click submit/decision | Button-busy + disabled selama request (BR-CSB-20); keputusan Approve idempotent via key (BE BR-NPP-N4) | [INTENT] | `67-client-side` item 19 |
| 12 | Semua aksi state-changing (RFA, keputusan, print regenerasi) | ConfirmDialog dua-tombol proceed/cancel, vocabulary tunggal Bahasa Indonesia (konsolidasi BR-CSB-18/19) | [INTENT] | `67-client-side` item 17/18 |

---

## 7. State Tampilan

| State | Perilaku | Sumber |
|---|---|---|
| **Loading** | Skeleton per-section (list: skeleton rows; form: skeleton panel); overlay hanya utk aksi mutasi (pola loading-overlay legacy BR-CSB-8 → modernisasi ke inline/skeleton = USULAN) | `67-client-side` item 8 |
| **Empty vs fetch-failed** | WAJIB dibedakan: "Data tidak tersedia" vs "Gagal memuat data dari server" + tombol Retry (adopsi kontrak varian (b) legacy — BR-CSB-11) | `67-client-side` item 11 |
| **Error request** | SETIAP kegagalan request punya jalur feedback user-visible (toast/alert + korelasi `correlation_id` dari error envelope BE `{code,message,details?,correlation_id}`); DILARANG silent swallow (do-not-replicate BR-CSB-10 `[ARTIFACT]`) | `67-client-side` item 10; BE §4 |
| **Status-driven display** (StatusBadge + layout SCR-NPP-04) | `pending` → badge "Draft" (bila draft dipakai — OQ-NPPVTL-09), aksi Edit; `validated` → "Menunggu Persetujuan", panel keputusan utk checker; `active` → "Aktif", panel aktivasi + print; `held(correction)` → "Perlu Perbaikan", CTA Edit utk maker; `held(rejected)` → "Ditolak" (terminal, read-only). **Satu label kanonik per status** — buang drift "Reject"/"Review" (BR-NPPVTL-11 `[ARTIFACT]`; BE §7 Edge Case 12) | BE §7; BR-NPPVTL-10/11 |
| **Gate-failure mapping (approve)** | `403 VERIFICATION_GATE_FAILED` → panel blocker "Verifikasi Vertel belum verified / kadaluarsa >30 hari" + detail `freshness_days` (D-01 S14); `422 BAST_INCOMPLETE` → arahkan maker lengkapi BAST (CTA Correction); `422 DUE_DATE_BEFORE_APPROVAL` → tampilkan due-date vs approve-date; `403 SELF_APPROVAL_BLOCKED` → notice "Anda submitter record ini"; `422 CHASSIS_ENGINE_MISMATCH` (submit) → error field-level chassis/engine | BE §5.2/§5.3 |
| **Verifikasi kadaluarsa (pre-emptive)** | Bila preflight menandai freshness >30 hari: banner warning di SCR-NPP-04 + (USULAN) indikator umur verifikasi di list; UX konsekuensi expiry (re-verify vs auto-cancel) menunggu **OQ-MEET-05** — jangan diputuskan diam-diam | BE BR-NPP-N1; OQ-MEET-05 |
| **Idempotent replay** | Retry Approve dgn key sama setelah timeout/refresh → BE kembalikan hasil pertama; FE render panel aktivasi normal tanpa pesan error ganda | BE BR-NPP-N4/AC-5 |
| **Aktivasi partial (Opsi B BE §1.3)** | Bila arsitektur final memilih saga: status `active_pending_ledger` dirender sebagai "Aktif — pembukuan diproses" (sub-state flag, bukan status baru); final setelah D-11 | BE §1.3/§7 |
| **Print/unduh** | Aksi print memicu unduhan/preview dokumen dari endpoint reports; busy-state per tombol; kegagalan → error jelas (bukan silent) | BE §4; `67-client-side` item 12 |
| **Responsive** | Semua screen operable di mobile & desktop (NFR); form panjang: section anchor nav; tabel → card list | Catatan rebuild (header) |

---

## 8. Kontrak Konsumsi API (per screen — konsisten BE-05 §4/§5)

Semua request memakai konvensi FE-00 (auth header/session, error envelope `{code, message, details?, correlation_id}` — BE-05 §4). Transport REST diasumsikan; final menunggu arsitektur ITEC (D-11) — [OPEN] OQ-ARCH-STACK.

| Screen | Interaksi | Endpoint (BE-05 §4) | Catatan konsumsi |
|---|---|---|---|
| SCR-NPP-01 | Muat list + filter status/branch + pagination | `GET /npp` | Label status dari enum kanonik (BE §7) |
| SCR-NPP-01/04 | Menu print (baris/detail `active`) | `GET /npp/{id}/agreement`; `GET /npp/{id}/documents/{docType}` (`approval-letter`,`statement-letter`,`power-of-attorney-fiducia`,`power-of-attorney-withdrawal`,`important-notice`,`mou`) | Gate `active` di BE; FE menyembunyikan menu utk status lain |
| SCR-NPP-02 | Lookup kontrak eligible | `GET /npp/lookups/credits` | Eligible = PO issued + Vertel approved (BE §5.1 error `VERTEL_NOT_APPROVED` utk race) |
| SCR-NPP-02/03 | Lookup rekening dealer | `GET /npp/lookups/dealer-bank-references?dealer_id=` | `dealer_id` dari payload lookup kontrak |
| SCR-NPP-02/03 | Advisory chassis/engine on-blur | `GET /npp/validations/chassis?credit_id=&chassis_no=&engine_no=` | Advisory; hard check tetap di submit+approve BE |
| SCR-NPP-02/03 | Kalkulasi installment date | `GET /npp/validations/installment-date?credit_id=&bill_received_date=` | Respon menentukan locked vs editable-window (§6.2 #4) |
| SCR-NPP-02/03 | Check asset (car non-top-up) | `GET /npp/validations/asset-check?credit_id=` | Termasuk flag skippable (Edge Case 3 `65-npp-vertel`) |
| SCR-NPP-02 | Buat record | `POST /npp` | Handle `409` (PO belum issued / NPP aktif ada / `VERTEL_NOT_APPROVED`) dan `422 VALIDATION_ERROR` → map per-field |
| SCR-NPP-03 | Update record | `PUT /npp/{id}` | Hanya status editable (guard BE BR-NPP-5); `409/422` → notice |
| SCR-NPP-02/03 | Submit RFA | `POST /npp/{id}/submit` | Sukses → `validated` + info checker (`assignee_role: KEPALA_CABANG`); `422 CHASSIS_ENGINE_MISMATCH` → error field |
| SCR-NPP-04 | Muat detail | `GET /npp/{id}` | Field census response utk Panel Status Aktivasi belum dirinci BE → GAP-FE05-02 |
| SCR-NPP-04 | Preflight sebelum Approve | `GET /npp/{id}/preflight` | Read-only advisory (pengganti `ValidateNppApprove` legacy — BE §4) |
| SCR-NPP-04 | Rekam keputusan | `POST /npp/{id}/decision` + header **`Idempotency-Key`** | Response happy-path memuat `agreement_no`, `passnet_id`, `ledger.*`, `master_loan_id`, `pk_document`, `dealer_email` (BE §5.3) → seed Panel Status Aktivasi tanpa re-fetch; `master_loan_id`/`ledger.*` provisional — kontrak final mengikuti Opsi A/B + [OPEN] OQ-MEET-02/03 (BE §5.3 note) |
| SCR-NPP-04 | Audit history | `GET /npp/{id}/history` | Timeline `NPP_APPROVAL_HISTORY` |

**Endpoint yang DIBUTUHKAN layar tetapi TIDAK ada di BE-05 §4** (jangan dikarang — dicatat sebagai GAP §11): lookup master **reason codes** utk DecisionDialog (GAP-FE05-01); kontrak field response `GET /npp` (kolom list/search) & `GET /npp/{id}` (status Passnet/email/ledger utk revisit pasca-aktivasi) belum dirinci (GAP-FE05-02); nama field opsional payload entry (`receipt_no/date`, `down_payment_receipt_date`, `bpkb_letter_date`, `disbursement_method`) belum eksplisit di contoh BE §5.1 (GAP-FE05-03).

---

## 9. Acceptance Criteria (Given/When/Then)

**AC-FE-1 — Guard RFA lengkap (termasuk BAST baru)**
- **Given** SCR-NPP-02 dgn kontrak ter-lookup, tetapi `bast_no` kosong;
- **When** maker menekan Submit RFA;
- **Then** submit diblok client-side, daftar kekurangan menampilkan "No. BAST wajib diisi", TIDAK ada request `POST /npp/{id}/submit` terkirim (BR-NPPVTL-1 + perluasan BAST D-01 S14; §5.1 guard).

**AC-FE-2 — Advisory chassis semua lini**
- **Given** form entry aplikasi **mobil** (bukan hanya motor);
- **When** user blur dari `chassis_no`;
- **Then** `GET /npp/validations/chassis` terpanggil dan hasil tampil inline (perbaiki scope motor-only legacy — Edge Case 5 `65-npp-vertel`; BE BR-NPP-N5).

**AC-FE-3 — Installment date auto-lock**
- **Given** `bill_received_date` diisi dan BE mengembalikan match billing cycle;
- **When** respon `GET /npp/validations/installment-date` diterima;
- **Then** `installment_date` terisi otomatis dan menjadi read-only; bila tidak match, field editable dgn helper window terbatas (BR-NPPVTL-4/BR-NPP-11).

**AC-FE-4 — Panel keputusan role+state-gated**
- **Given** SCR-NPP-04 status `validated`, viewer BUKAN assigned pending checker (atau viewer = submitter);
- **When** halaman dirender;
- **Then** panel keputusan TIDAK dirender (view-only); untuk viewer checker sah ≠ submitter, panel dirender (BR-NPPVTL-8; D-01 S11; D-09 — tidak ada jalur bypass).

**AC-FE-5 — Preflight sebelum Approve**
- **Given** checker menekan Approve dan preflight mengembalikan verifikasi Vertel umur 41 hari;
- **When** hasil preflight dirender;
- **Then** blocker "verifikasi kadaluarsa (>30 hari)" tampil, `POST /decision` TIDAK terkirim (outcome BR-NPPVTL-9; BE BR-NPP-N1/D-01 S14).

**AC-FE-6 — Approve happy-path men-seed panel aktivasi**
- **Given** preflight bersih, checker konfirmasi Approve dgn Idempotency-Key baru;
- **When** `POST /npp/{id}/decision` sukses `200`;
- **Then** SCR-NPP-04 menampilkan status "Aktif", `agreement_no`, `passnet_id`, indikator jurnal/AR Card/amortisasi (D-06), master loan (D-05), dokumen PK tersedia (D-04), status email dealer (D-03), dan DocumentPrintMenu unlocked — langsung dari body response BE §5.3 tanpa re-fetch wajib (bentuk final field `master_loan_id`/`ledger.*` provisional — [OPEN] OQ-MEET-02/03, BE §5.3 note).

**AC-FE-7 — Reject/Correction reason wajib**
- **Given** checker memilih Reject (atau Correction) di DecisionDialog tanpa memilih `reason_id`;
- **When** menekan submit dialog;
- **Then** submit diblok dgn error field reason; setelah reason diisi, keputusan terkirim TANPA preflight (BR-NPPVTL-9) dan record berpindah ke `held` dgn label kanonik tunggal (BR-NPPVTL-11 `[ARTIFACT]` dibuang).

**AC-FE-8 — Status-gating aksi list**
- **Given** SCR-NPP-01 memuat baris berstatus `validated`, `held(correction)`, `active`;
- **When** list dirender utk maker;
- **Then** baris `held(correction)` menampilkan Edit; baris `active` menampilkan PrintMenu (dan TIDAK menampilkan Edit); baris `validated` hanya Detail (BR-NPPVTL-10; BE BR-NPP-5).

**AC-FE-9 — Gate-failure approve dirender spesifik + tanpa silent failure**
- **Given** BE mengembalikan `422 BAST_INCOMPLETE` (atau `403 SELF_APPROVAL_BLOCKED` / `422 DUE_DATE_BEFORE_APPROVAL`) saat Approve;
- **When** response diterima;
- **Then** pesan spesifik per `code` tampil (bukan alert generik), `correlation_id` dapat disalin, dan TIDAK ada jalur request yang gagal tanpa feedback (do-not-replicate BR-CSB-10).

**AC-FE-10 — Idempotent retry**
- **Given** Approve terkirim tetapi koneksi putus sebelum response; FE retry dgn Idempotency-Key sama;
- **When** retry sukses;
- **Then** panel aktivasi dirender dari hasil aktivasi pertama tanpa error ganda dan tanpa keputusan kedua (BE BR-NPP-N4/AC-5).

**AC-FE-11 — Print gate**
- **Given** record berstatus `validated`;
- **When** user membuka SCR-NPP-04 / list;
- **Then** DocumentPrintMenu TIDAK tersedia; setelah `active`, menu menawarkan PK + 6 dokumen pendamping sesuai set BE §4 (`[LOCKED]` availability gate — BR-NPP-4).

**AC-FE-12 — Responsive**
- **Given** viewport mobile (≤ ~640px);
- **When** SCR-NPP-02 dibuka;
- **Then** semua section, lookup dialog, dan action bar tetap operable (sticky action bar; tabel list menjadi card) — NFR responsive.

---

## 10. Dependency

| Dependency | Jenis | Yang dikonsumsi | Sumber |
|---|---|---|---|
| **BE-05-npp-legalization** | API | Seluruh endpoint §8; enum status kanonik §7; error envelope; Idempotency-Key semantics | `BE-05 §4/§5/§7` |
| **FE-00 OVERVIEW (app shell & shared)** | FE | AppShell (nav, session identity `acting_employee`, branch context, guard auth terpusat — jangan replikasi 3 guard rusak legacy `60-app-shell §9` Edge Case 3), DataTable, LookupDialog, ConfirmDialog, DateField, CurrencyField, Toast/Alert envelope-aware, busy-button | `60-app-shell-auth-navigation.md`; `67-client-side-behavior.md` |
| **FE-03 approval-inbox** | FE | Deep-link masuk (inbox → `/acquisition/npp/[id]`) dan target navigasi setelah keputusan; kontrak transaksi-type NPP di inbox | `63-approval-inbox-screens.md`; `65-npp-vertel §5` item 10 |
| **Modul FE Vertel (STEP 14)** | FE/data | Status verifikasi + freshness utk banner/preflight display (produsen data: modul Vertel; 05 read-only) | D-02; BE-05 §2 |
| **FE-04 contract-cm-po** | upstream | Record PO issued yang menjadi kandidat lookup kontrak | `64-contract-cm-po-screens.md` |
| **Master data (D-08)** | API/data | Reason codes (GAP-FE05-01), master dealer (email display), personel dealer utk order-source rows | D-08; `66-master-data-screens.md` |
| **Arsitektur ITEC (D-11)** | eksternal | Mekanisme auth/role claim, transport final, konvensi API | D-11; OQ-ARCH-STACK |

---

## 11. Keputusan Dibutuhkan (Open Questions & GAP)

> [OPEN] dari KB + meeting + gap kontrak BE — **jangan diselesaikan diam-diam**. FE memakai default paling aman (fail-closed / fitur tidak dirender) sampai diputuskan.

| ID | Pertanyaan | Prioritas | Dampak FE |
|---|---|---|---|
| **GAP-FE05-01** | DecisionDialog butuh lookup master **reason codes** (`reason_id`), tetapi BE-05 §4 tidak memuat endpoint lookup reason. Tambahkan di BE-05 (mis. `GET /npp/lookups/reasons` — USULAN) atau tunjuk endpoint modul master-data? | P1 | §5.2; tanpa ini Reject/Correction tidak dapat dieksekusi |
| **GAP-FE05-02** | Kontrak field response `GET /npp` (kolom list + parameter search) dan `GET /npp/{id}` (status Passnet/email-dealer/ledger utk Panel Status Aktivasi saat revisit) belum dirinci di BE-05 §5. Perlu field census response. | P1 | §4.1, §4.3, §5.3, §8 |
| **GAP-FE05-03** | Nama field opsional payload entry belum eksplisit di contoh BE §5.1: `receipt_no`/`receipt_date`, `down_payment_receipt_date`, `bpkb_letter_date`, `disbursement_method`. Konfirmasi daftar lengkap field `POST /npp`/`PUT`. | P2 | §5.1 #10–12, #15 |
| **GAP-FE05-04** | Kepemilikan layar **Vertel (STEP 14)** di pemetaan FE-xx: modul FE terpisah atau digabung? (KB `65-npp-vertel` mencakup keduanya; PRD ini hanya NPP.) Termasuk fix Edge Case 1 (decision buttons Vertel tidak pernah render). | P1 | Boundary modul; OQ-NPPVTL-01/07 ikut ke pemilik layar Vertel |
| **GAP-FE05-05** | Matrix akses menu per role D-10 utk modul NPP (apakah CMO/Marketing Head/Credit Analyst punya read-only?). Legacy: menu per-employee tanpa role scheme (`60-app-shell` BR-SHELL-4). | P2 | §2; guard route §3 |
| **OQ-NPPVTL-09** | NPP entry: RFA-only (perilaku legacy web) atau tombol "Simpan Draft" genuine (state `pending` BE sah)? | P2 | §4.2 action bar; §6.1; reachability `pending` |
| **OQ-NPPVTL-02** | "Kepala Cabang" sebagai role bernama: mapping ke struktur hierarki/claim token (legacy hanya flag "pending approver"). | P2 | §2 gating panel keputusan |
| **OQ-NPPVTL-04** | Check asset eksternal (Rapindo) car non-top-up: masih requirement integrasi? (check live vs cascade registrasi dead — jangan dikonflasi.) | P2 | §6.2 #3; tombol Check Asset |
| **OQ-NPPVTL-05** | Validasi total subsidi order-source dealer (guard legacy di-comment — BR-NPPVTL-19): reinstate di FE/BE/keduanya? | P3 | §5.1 #16 |
| **OQ-NPPVTL-06** | Copy pesan error pre-check approve: regulated user-facing copy atau bebas redesign? | P3 | §7 gate-failure mapping |
| **OQ-NPPVTL-08** | Pengganti bound tahun 2000–2100 `[ARTIFACT]`: range tanggal bisnis yang deliberate utk semua DateField NPP. | P3 | §5.1 #4, #8–12 |
| **OQ-MEET-05** | Konsekuensi expiry verifikasi 30-hari (auto-cancel vs wajib re-verify) + titik mulai clock → menentukan UX antrian NPP kadaluarsa (banner, CTA re-verify, auto-remove dari queue?). | P2 | §7 verifikasi kadaluarsa; SCR-NPP-01 indikator umur |
| **OQ-MEET-02 / OQ-MEET-03** *(milik BE-05, disebut krn menyentuh 05)* | Master loan (D-05: ownership + field census) dan GL mapping/posting rules (D-06) masih [OPEN] di BE → bentuk final field `master_loan_id`/`ledger.*` di response `POST /decision` & `GET /npp/{id}` belum terkunci; FE merender indikatornya provisional. | P2 | §4.3 Panel Status Aktivasi; §8 decision row; AC-FE-6 |
| **OQ-NPP-14** *(narrowed)* | Definisi BAST "lengkap": cukup `bast_no`+`bast_date`, atau wajib upload dokumen BAST? Bila wajib upload → tambah komponen upload di Section B (+ kontrak BE baru). | P2 | §4.2 Section B; §5.1 #3–4 |
| **OQ-CMPO-01** | Editability saat `validated` (arti legacy status `0`): bila stakeholder mempertahankan RFA-editability, FE menambah Edit utk `validated` (ikut revisi guard `PUT` BE §4). | P1 | §6.2 #7; SCR-NPP-01 aksi |
| **OQ-ARCH-STACK** *(direvisi D-12)* | FE = Next.js `[LOCKED]`; masih [OPEN]: mekanisme auth/session & role claim (menunggu ITEC D-11), strategi rendering (App Router RSC vs client-heavy per screen = USULAN internal FE-00), transport final. | P1 | Guard route §3; konvensi fetch §8 |
| **OQ-SHELL-02** *(milik FE-00, disebut krn menyentuh 05)* | Branch pick login tidak di-re-verify server-side (legacy) — branch scoping dipakai filter `GET /npp`. | P1 | §5.3 filter branch |

**Tertutup oleh keputusan meeting (tidak lagi open utk modul ini):**
- **Super-user & self-approval UI** — D-09 `[LOCKED]` + D-01 Step 11: tidak ada affordance bypass; FE merender `403 SELF_APPROVAL_BLOCKED` sebagai notice, tanpa jalur alternatif.
- **BAST hard-gate (arah utama OQ-NPP-14)** — D-01 Step 14: FE menjadikan BAST required (§5.1); sisa pertanyaan hanya definisi "lengkap" (narrowed, tabel di atas).
