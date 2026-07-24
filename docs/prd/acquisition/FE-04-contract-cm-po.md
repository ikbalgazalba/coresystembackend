# PRD — Contract (CM) Finalization & Purchase Order [FE]

> **Kapabilitas 04 — contract-cm-po** dari bounded context **Acquisition** (MCF/FINCORE) — **dokumen frontend**.
> **Audience**: tim Frontend Engineering. **Target stack**: **FE = Next.js** `[LOCKED]` (D-12). **Tanggal**: 2026-07-14.
> **Pasangan BE**: `docs/prd/acquisition/BE-04-contract-cm-po.md` — seluruh kontrak API §8 dokumen ini merujuk
> endpoint yang terdefinisi di BE-04 §4/§5; kebutuhan layar yang TIDAK punya endpoint di BE-04 dicatat sebagai
> **GAP** di §11 (tidak dikarang).
> **Posisi alur**: **STEP 12–13** dari alur final 16-STEP (PDF 08072026) — layar finalisasi Credit Memo (2nd data
> entry: nilai finansial, opsi pembayaran, asuransi jiwa/kendaraan), layar listing acquisition (cetak/preview PO +
> email PDF ke dealer), dan UX koreksi **Open CM**.
> **Bahasa**: Bahasa Indonesia; identifier/SP/tabel/field/enum/OQ-ID/D-ID legacy dipertahankan apa adanya.
> **Sumber otoritatif**:
> `.mega-sdd/knowledge-base/.sp-manifests/_ACQUISITION-GROUND-TRUTH.md` (v2 — alur final 16 STEP, PDF 08072026),
> `.mega-sdd/knowledge-base/.sp-manifests/_MEETING-DECISIONS-2026-07.md` (decision register D-01..D-12),
> `.mega-sdd/knowledge-base/60-frontend/64-contract-cm-po-screens.md` (KB FE primary — sensus field, staging, gating),
> `.mega-sdd/knowledge-base/10-domains/23-contract-cm-po.md` (KB BE counterpart),
> `.mega-sdd/knowledge-base/60-frontend/60-app-shell-auth-navigation.md` + `67-client-side-behavior.md`
> (konvensi shell & client-side — dipayungi **FE-00-OVERVIEW**),
> `docs/prd/acquisition/BE-04-contract-cm-po.md` (kontrak API pasangan).
> **Keputusan meeting terintegrasi**: D-01 (S12, S13), D-02, D-03 (disambiguasi), D-08, D-09, D-10, D-11, D-12.

### Disiplin penanda (dari umbrella)

| Penanda | Arti |
|---|---|
| `[LOCKED]` | **WAJIB dipertahankan** (regulatori / kontrak eksternal / keputusan governance). Additive only. |
| `[INTENT]` | Outcome bisnis wajib dipenuhi; layout/mekanisme UI bebas didesain ulang. |
| `[ARTIFACT]` | Kecelakaan legacy — dibuang / do-not-replicate. |
| `[OPEN]` | Belum terjawab → masuk §11 (jangan diselesaikan diam-diam). |
| `[USULAN]` | Usulan desain penulis PRD (UX Next.js baru) — bukan turunan KB/keputusan; butuh review. |

> **Catatan rebuild**: layar legacy (Razor `.cshtml` + jQuery) adalah **EVIDENCE, bukan mandat desain**. Yang
> dipertahankan adalah OUTCOME: field yang di-capture, aturan validasi, urutan staging, role-gating. Layout,
> komponen, dan interaksi Next.js diusulkan baru dan ditandai `[USULAN]`. NFR: **responsive mobile + desktop**
> untuk semua screen (instruksi umbrella FE).

---

## 1. Ruang Lingkup & Kepemilikan

### 1.0 Posisi dalam alur final 16-STEP — WAJIB dibaca dulu

Ground-truth v2 (PDF 08072026) me-restrukturisasi alur (`_ACQUISITION-GROUND-TRUTH.md` STEP 12/13):

- **STEP 12** — Hierarki Persetujuan: pada approve (`sp_approve_cm_moofi`, status `'A'`), figur `OP`/`ULI`/`LCR`
  **plus binding asuransi jiwa (`TrCmLifeInsuranceCredit`) + kendaraan (`TrCmInsurance`) di-LOCK** (D-01 S12).
  **Eksekutor tulis freeze di rebuild = modul 04 sendiri, sebagai konsumen segera event `MemoApproved` dari 03**
  (**OQ-BE03-02 RESOLVED — opsi b**; BE-04 §5.3; BE-03 §5.2 note) — eventually-consistent dalam transaksi
  konsumsi event; ada jendela singkat pasca-approve sebelum layar modul ini menampilkan nilai frozen + badge lock.
  Entry 2nd-data CM pada alur final **dilipat ke jalur moofi upstream (STEP 1–8)**; surface finalisasi FINCORE
  tetap dispesifikasikan di sini untuk **jalur koreksi (memo `corrected`)** dan **jalur non-moofi** (= jalur
  manual web; OQ-GT-01 ✅ RESOLVED — evidence 2026-07-14, scope port = keputusan desain — BE-04 §1.0/§1.1/§11).
- **STEP 13** — Penerbitan PO & Koreksi: Admin Cabang mencetak PO dan **PDF di-email ke dealer** sebagai bagian
  standar alur cetak (GT STEP 13); bila unit fisik beda (warna/tipe), cabang boleh **Open CM** — return-target
  v2 = proses Moofi Step 1–12, granularitas `[OPEN]` OQ-GT-03.
- **STEP 14 (Vertel, D-02)** adalah downstream langsung setelah PO — **bukan milik modul ini** (FE-05).

### 1.1 Screens yang DIMILIKI modul FE ini (owns)

| ID | Screen | Evidence legacy | Catatan |
|---|---|---|---|
| **FE04-SCR-01** | **Form Finalisasi CM (2nd data entry)** — struktur finansial: Data Asset + Data Price (payment option, OTR, DP, fee, asuransi kendaraan/jiwa/kesehatan, skema UMC/third-party) | `CMMotorCycle.cshtml:1-923` (motor), `CMCar.cshtml:1-80,879-1404` (car) — `64 §3a S1`, `64 §4` | Dua layar legacy per lini → rebuild: **satu form ber-varian per `product_line`** `[USULAN]` (lihat §4.1); divergensi field car/motor adalah [INTENT] (`64 BR-CMPOFE-6`), bukan artefak. |
| **FE04-SCR-02** | **Upload Dokumen Pendukung + Save Draft / RFA** | `CMMotorCycle_UploadDoc.cshtml:1-314` — `64 §3a S2` | Panel approval embedded di layar legacy ini **bukan milik FE-04** (milik FE-03 — lihat §1.2). Aksi RFA memanggil boundary **BE-01** (BE-04 §1.2) → GAP-01 §11. |
| **FE04-SCR-03** | **Listing Acquisition / PO** — cetak PO, download File-PO, status email, trigger Open CM | `Index.cshtml:1-243` (4-wheel) + `IndexMotor.cshtml:1-288` (2-wheel) — `64 §11` | Dua layar legacy → rebuild: **satu listing + filter `product_line`** `[USULAN]`; asimetri gate per lini = `[OPEN]` OQ-CMPOFE-02/03, jangan disamakan diam-diam (§6.3). |
| **FE04-SCR-04** | **PO Detail / Preview** — preview PDF PO, audit cetak (`print_count`, first/last print), status email dealer, aksi print & re-send email | Kebutuhan diturunkan dari aksi print/email/download di listing legacy (`AcquisitionController.cs:241-364`; `IndexMotor.cshtml` File-PO — `64 §11`) | `[USULAN]` screen baru — legacy tidak punya layar detail PO; outcome (preview + audit + email status) [INTENT] dari GT STEP 13 + `23 BR-CMPO-9`. |
| **FE04-DLG-01** | **Dialog konfirmasi Open CM** (koreksi unit mismatch) | `acquisition.js:24-66`; `AcquisitionController.cs:122-137` — `64 §3a S5` | Dialog dua-tombol + alasan; routing pasca-sukses per `return_target` (BE-04 §5.5). |

### 1.2 Yang BUKAN milik modul FE ini (non-goal)

- **Panel aksi approval (Approve/Verify/Review/Reject) + input risk-scale** — legacy menempelkannya di layar
  upload (`CMMotorCycle_PhotoDetail.js:13-60,348-416` — `64 §2, §5.7`); mekanika hierarki milik
  **FE-03-approval-inbox** (KB `63-approval-inbox-screens.md`). Rebuild: disposisi komite dilakukan di layar
  Inbox Approval (FE-03), **tidak** di-embed ulang di FE04-SCR-02 `[USULAN]` — konsisten boundary BE (03 =
  keputusan, 04 = reaksi; BE-04 §1.2).
- **RFA lock ownership** — endpoint RFA milik **BE-01** (BE-04 §1.2: "04 tidak memiliki endpoint RFA").
  FE04-SCR-02 menampilkan tombol RFA tetapi memanggil kontrak BE-01 → dicatat GAP-01 §11 (konfirmasi silang
  dengan FE-01/BE-01).
- **Layar insurance-cover binding batch (IC1–IC6)** — grouping kontrak ter-legalisasi (keyed nomor NPP) ke satu
  cover request insurer (`InsuranceCoverController.cs:41-880` — `64 §3a IC1-IC6`). **Di-luar-scope 04** sampai
  `[OPEN]` OQ-CMPOFE-08 terjawab (BE-04 §1.2 mengeluarkannya juga; kemungkinan konteks INSURANCE downstream).
- **Vertel (STEP 14, D-02) & NPP (STEP 15)** — milik FE-05 (`65-npp-vertel-screens.md`).
- **Email blast dealer pasca STEP 15 (D-03)** — milik 05; **JANGAN dirancukan** dengan email PDF PO di STEP 13
  yang tampil di FE04-SCR-03/04 (BE-04 §1.2).
- **Popup FCL/SLIK result & popup placeholder** — popup placeholder "Wowowowowow" (`PartialPopup.cshtml:1-8`,
  `64 Edge Case 5`) = `[ARTIFACT]` discard; FCL/SLIK dashboard milik FE-02 (`62-credit-analysis-screens.md`).

### 1.3 Catatan arsitektur target (Next.js) — [USULAN kecuali dinyatakan lain]

- **Framework: Next.js** `[LOCKED]` (D-12). Versi/router: **USULAN App Router + React Server Components** untuk
  listing (data fetch server-side) + Client Components untuk form interaktif; keputusan final ikut konvensi
  **FE-00-OVERVIEW** dan dokumen arsitektur ITEC (D-11) — jangan dianggap keputusan (§11 OQ-ARCH-STACK).
- **Semua enforcement otoritatif ada di BE** — role-gating FE hanya *presentational* (hide/disable); legacy
  membuktikan check FE bisa jadi dead code (`64 BR-CMPOFE-14` `[ARTIFACT]`) dan tombol approval di-gate murni
  client-side (`64 BR-CMPOFE-10`). FE WAJIB tetap menangani `403`/`409` dari BE dengan pesan jelas.
- **Angka moneter**: input numeric typed + layer display-formatting terpisah, SATU konvensi format (id-ID) —
  do-not-replicate string-manipulation per keystroke (`64 Edge Case 9`; `67 §5 item 5-6` + Edge Case 3).
  Pakai `CurrencyInput` shared dari FE-00.
- **Setiap request punya failure-path yang terlihat user** — non-negotiable (do-not-replicate `67 BR-CSB-10`
  silent-swallow `[ARTIFACT]`).

---

## 2. Aktor & Peran

Sensus peran cabang final per **D-10** `[LOCKED]`: **CMO, Marketing Head, Credit Analyst, Kepala Cabang,
Credit (Admin)**. **Super-user TIDAK ADA** per **D-09** `[LOCKED]` — FE tidak boleh menyediakan menu/route/
bypass ekuivalen super-user. Menu per user di-resolve dinamis dari BE (pola legacy `60 BR-SHELL-4` [INTENT];
mekanisme baru per FE-00).

Akses per screen (aksi otoritatif di-enforce BE; kolom ini menentukan **visibilitas** UI):

| Screen / aksi | CMO | Credit (Admin) | Admin Cabang (Credit Admin ber-gate posisi cetak) | Kepala Cabang | Sumber |
|---|---|---|---|---|---|
| FE04-SCR-01 form finalisasi (edit) | ✔ | ✔ | — | view-only | `64 §2` (operational/data-entry staff); BE-04 §2 (CMO/Credit Admin) |
| FE04-SCR-01 (view/read-only saat locked) | ✔ | ✔ | ✔ | ✔ | `64 BR-CMPOFE-1` |
| FE04-SCR-02 upload dokumen + Save Draft | ✔ | ✔ | — | — | `64 §3a S2` |
| FE04-SCR-02 tombol RFA | — | ✔ (branch admin) | ✔ | — | `64 §2` (branch admin submit RFA); boundary BE-01 |
| FE04-SCR-03 listing (baca) | — | ✔ | ✔ | ✔ | BE-04 §4 (`GET /purchase-orders`: Credit (Admin)/Kepala Cabang) |
| FE04-SCR-03/04 **Print PO** (+ email otomatis) | — | — | ✔ **position-gated** | — | GT STEP 13 (Admin Cabang); BE-04 §2/§5.4; gate `[LOCKED]`, katalog posisi `[OPEN]` OQ-CMPOFE-05 |
| FE04-SCR-04 re-send email PO | — | ✔ | ✔ | — | BE-04 §4 (`POST /email`: Admin Cabang / Credit (Admin)) |
| FE04-DLG-01 Open CM | — | ✔ | ✔ | — | `64 §2` (branch admin); BE-04 §4 (`POST /correction`) |
| **Dealer (eksternal)** | penerima email PDF PO; tidak menyentuh UI | | | | `64 §2` [INFERRED] |

> **Penting (fix legacy)**: gate "siapa boleh cetak PO" di web layer legacy adalah **dead code**
> (`AcquisitionController.cs:441-454`, `64 BR-CMPOFE-14` `[ARTIFACT]`) — FE rebuild TIDAK mengimplementasikan
> allow-list posisi sendiri; tombol Print di-render berdasarkan **capability flag dari BE** dan error `403
> PO_PRINT_NOT_AUTHORIZED` tetap ditangani (BE-04 AC-10).

---

## 3. Peta Screen & Route

Inventori screen + usulan route Next.js (App Router, segmen `acquisition`) — semua route `[USULAN]`; mapping
legacy → target:

| Screen | Legacy (evidence) | Route Next.js [USULAN] | Jenis |
|---|---|---|---|
| FE04-SCR-03 Listing Acquisition/PO | `/Acquisition/Index` (4-wheel) + `/Acquisition/IndexMotor` (2-wheel) | `/acquisition/purchase-orders` (query: `?product_line=CAR\|MOTOR&status=&branch=&from=&to=`) | List (RSC + client filter) |
| FE04-SCR-01 Form Finalisasi CM | `/Acquisition/CM/CMMotorCycle` + `/Acquisition/CM/CMCar` | `/acquisition/credit-memos/[memoId]/finalization` | Form multi-section (client) |
| FE04-SCR-02 Upload Dokumen + RFA | `/Acquisition/CM/CMMotorCycle_UploadDoc` (motor); car: layar modul CA (`CACarController.CACar`) | `/acquisition/credit-memos/[memoId]/documents` | Form upload (client) |
| FE04-SCR-04 PO Detail/Preview | — (aksi tersebar di listing legacy) | `/acquisition/purchase-orders/[poNumber]` | Detail + PDF preview |
| FE04-DLG-01 Open CM | dialog di listing (`acquisition.js:24-66`) | dialog modal dari SCR-03/SCR-04 (tanpa route; state URL `?dialog=open-cm` opsional) | Dialog |

Catatan konsolidasi:

- **Dua listing legacy → satu route** `[USULAN]`: perbedaan kolom/gate antar lini (`64 BR-CMPOFE-12/13`)
  ditangani lewat field status **kanonik** yang diekspos BE (BE-04 §5.7), bukan dua halaman; sampai
  OQ-CMPOFE-02/03 resolve, FE membaca flag gate dari response BE (lihat §6.3) — FE **tidak** menghitung gate
  sendiri dari dua kolom berbeda.
- **Dua form CM legacy → satu route ber-varian `product_line`** `[USULAN]`: field-set car ⊃ motor
  (`64 BR-CMPOFE-6` [INTENT] — produk car memang lebih kaya). Varian dirender dari metadata `product_line`
  memo (`GET /credit-memos/{memoId}` — BE-04 §4). Paritas section "Data Asset" car = `[OPEN]` OQ-CMPOFE-12.
- **Layar upload car-line legacy tinggal di modul CA** (`CACarController.cs:70-170,721-1059` — `64 §5.5`) —
  artefak organisasi kode, bukan aturan bisnis; rebuild menyatukan ke satu route documents `[USULAN]`.
  Paritas validasi car vs motor di layar upload = `[OPEN]` OQ-CMPOFE-13.
- Navigasi masuk: dari menu shell (FE-00) dan dari Inbox Approval FE-03 (view-only). Guard auth/branch ikut
  konvensi shell FE-00 (`60 BR-SHELL-1/3` — login LDAP `[LOCKED]`, branch binding [INTENT]; guard sentral
  WAJIB satu mekanisme — do-not-replicate `60 Edge Case 3` `[ARTIFACT]`).

---

## 4. Komposisi Layar & Komponen

Semua komponen bersama (ConfirmDialog, CurrencyInput, DataTable, FileUpload, LookupModal, StatusBadge,
LoadingOverlay/BusyButton, Alert/Toast envelope) dirujuk dari **FE-00** — jangan duplikasi spesifikasinya di
sini. Komposisi per screen (layout `[USULAN]`, isi field [INTENT] dari KB):

### 4.1 FE04-SCR-01 — Form Finalisasi CM

- **Header context bar**: `credit_id`, nama applicant, `product_line` badge (CAR/MOTOR), status memo
  (StatusBadge: `draft`/`corrected` = editable; `finalized`/`approved`/`rejected` = read-only) — pengganti
  session-flag mode Add/Edit/View/Approval legacy (`64 BR-CMPOFE-1`; mekanisme session-flag = bebas diganti,
  outcome guard editability [INTENT]).
- **Section A — Data Asset** (card/accordion): identifikasi aplikasi, aset, dealer, surveyor, marketing head
  (field census §5.1). Lookup dealer/surveyor memakai `LookupModal` shared (konsolidasi 3 implementasi modal
  legacy — `67 BR-CSB-13` `[ARTIFACT]` fragmentasinya, [INTENT] interaksinya).
- **Section B — Data Price / Struktur Finansial**: payment option, OTR, DP, fee, tenor + **panel ringkasan
  kalkulasi live** (net DP, financed amount, installment, rate) yang nilainya datang dari
  `POST /calculation-preview` BE (BE-04 §4) — **bukan** dihitung di browser (fix `64 BR-CMPOFE-3` +
  Edge Case 9) `[USULAN mekanisme, INTENT outcome preview-akurat]`. Debounce on-change; skeleton saat fetch.
- **Section C — Asuransi**: sub-panel Asuransi Kendaraan (kedua lini) + sub-panel Asuransi Jiwa & Kesehatan
  (car line only, conditional — §5.1.4). Premi auto-refetch via `GET /insurance-quote` saat OTR/tenor/asset
  berubah (`64 BR-CMPOFE-5` [INTENT]).
- **Section D — Skema UMC / Third-Party Ownership** (conditional render bila application type = skema
  alternatif): perantara, bank tujuan + tombol **Cek Rekening** (DOKU validate — BE-04 §5.2), no rangka/mesin/
  plat, status invoice/STNK (§5.1.5).
- **Action bar sticky** (bawah): `Simpan Draft` (PUT finalization), `Lanjut ke Dokumen` (save + route ke
  SCR-02), `Kembali` — **navigasi Kembali TIDAK auto-save** `[USULAN]`; legacy menyimpan penuh saat back
  (`64 Edge Case 7`) — rebuild memakai dirty-check + prompt "simpan perubahan?" — butuh konfirmasi PO (§11
  OQ-FE04-01).
- Responsive: grid 2 kolom desktop → 1 kolom mobile; section jadi accordion di mobile `[USULAN]`.

### 4.2 FE04-SCR-02 — Upload Dokumen + Save Draft / RFA

- **Echo read-only** ringkasan S1 (angka final memo) — legacy juga menampilkan echo (`64 §3a S2
  hidden_fields_vs_prior`).
- **Uploader dokumen pendukung** (FileUpload shared): multi-file, validasi extension + size **blocking**
  client-side DAN otoritatif server-side — legacy hanya warning advisory yang tidak memblokir
  (`67 §5 item 15/16` `[ARTIFACT]` mekanismenya); daftar tipe `.jpg/.jpeg/.png/.pdf` (§5.2).
- **Field NPWP conditional** — muncul & required bila financing amount ≥ 50.000.000 (§5.2, `[LOCKED]`).
- **Dua tombol terpisah**: `Simpan Draft` vs `Request For Approval (RFA)` — legacy memakai satu action + hidden
  flag (`64 BR-CMPOFE-9` `[ARTIFACT]` mekanisme; distingsi RFA-vs-draft [INTENT]). RFA memunculkan
  ConfirmDialog dua-tombol (konvensi `67 BR-CSB-18`).
- **TIDAK ada panel Approve/Verify/Review/Reject** di layar ini pada rebuild (milik FE-03; §1.2) `[USULAN]`.

### 4.3 FE04-SCR-03 — Listing Acquisition/PO

- **Filter bar**: branch, status, `product_line`, rentang tanggal (param `GET /purchase-orders` BE-04 §5.7).
- **DataTable shared** (satu kontrak grid — konsolidasi dua implementasi legacy, `67 BR-CSB-11`): kolom §5.3;
  empty-state WAJIB membedakan "gagal ambil data" vs "data memang kosong" (kontrak varian (b) legacy yang lebih
  lengkap — `67 §5 item 11` [INTENT]).
- **Row actions** (render kondisional per flag dari BE — §6.3): `Print PO`, `File-PO` (download PDF),
  `Kirim Ulang Email`, `Open CM`, `Lihat Detail`. Aksi row memakai event binding terstruktur — bukan string
  onclick ter-interpolasi (`67 BR-CSB-25` `[ARTIFACT]`).
- Kolom audit `print_count` / "Jumlah Send Mail" / "Status PO" tampil selalu — `[LOCKED]` audit trail
  dealer/compliance-facing (`64 §6`; `23 BR-CMPO-9`).
- Responsive: tabel scroll horizontal dalam container sendiri di mobile, atau card-list `[USULAN]`.

### 4.4 FE04-SCR-04 — PO Detail / Preview

- **Metadata panel**: `po_number`, `credit_id`, dealer, status PO (`issued`/`corrected`/`fulfilled` — BE-04
  §7.2), `print_count`, first/last print (user + waktu), status email (`is_send`, `sent_to`, `sent_at` —
  BE-04 §3.6 `PO_EMAIL_LOG` = `out_notification` + `log_po_email`).
- **PDF preview** dari `GET /purchase-orders/{poNumber}/document` (BE-04 §4) — inline viewer + tombol download.
- **Aksi**: `Print PO` (POST /print — menampilkan hasil `email_dispatch` di response), `Kirim Ulang Email`
  (POST /email — tampilkan `already_sent` bila no-op idempotent), `Open CM` (buka FE04-DLG-01).

### 4.5 FE04-DLG-01 — Dialog Open CM

- ConfirmDialog dua-tombol dengan ikon warning (`67 BR-CSB-18` [INTENT]; kosakata tombol konsisten satu
  konvensi lokalitas — perbaiki fragmentasi `67 §5 item 18`). Field **alasan koreksi (required)** — kontrak
  BE `POST /correction` memuat `reason` (BE-04 §5.5).
- Context tersembunyi yang dikirim: `po_number` (path), `credit_id` (body) — legacy juga membawa `item_id`
  (`64 §3a S5`); `item_id` TIDAK ada di kontrak BE-04 §5.5 → GAP-03 §11 (jangan dikarang).
- Pasca-sukses: tampilkan `return_target` dari response dan route sesuai nilainya — nilai final enum menunggu
  `[OPEN]` OQ-GT-03 (BE-04 §5.5); sementara: tampilkan pesan "koreksi dibuka — proses kembali ke jalur Moofi"
  + kembali ke listing `[USULAN]`.

---

## 5. Field & Validasi (sensus per form)

> Konvensi: kolom **Val. client** = mirror UX (instan); **otoritatif selalu server** (envelope error BE-04 §5).
> Sensus diturunkan dari `64 §3a/§4` dengan sitasi cshtml/controller; nama field target mengikuti kontrak
> BE-04 §5.1. Semua baris [VERIFIED] dari KB kecuali ditandai lain.

### 5.1 FE04-SCR-01 — Form Finalisasi CM

#### 5.1.1 Section Data Asset (kedua lini)

| Field | Tipe kontrol | Req | Val. client / format | Sumber options | Sumber KB |
|---|---|---|---|---|---|
| `application_type` (jenis aplikasi / skema) | select | ✔ | pilihan valid; menentukan render Section D (UMC) | master (BE) | `64 §3a S1` (`application_type_and_finance_type`); `CMMotorCycle.cshtml:134-269` |
| `finance_type` | select | ✔ | — | master (BE) | idem |
| Aset: `asset_kind` / `brand` / `model` / `year` | cascading select | ✔ | cascade kind→brand→model; year numerik 4 digit | master aset (D-08, read-only) | `64 §4` ("asset kind/brand/model/year") |
| `dealer` | LookupModal | ✔ | wajib terpilih dari master (bukan free-text) | dealer master (D-08) — juga sumber email dealer terverifikasi (BE-04 BR-CMPO-20) | `64 §4`; `CMMotorCycle.cshtml:134-269` |
| `surveyor` | LookupModal | ✔ | dari master employee | master | idem |
| `marketing_head` | LookupModal | ✔ | dari master employee | master | idem |
| Identifikasi product-marketing | select/lookup | ✔ | — | master | `64 §4` ("product-marketing identification") |

> Paritas section ini pada lini car hanya ter-spot-check (struktur paralel [INFERRED]) — `[OPEN]`
> **OQ-CMPOFE-12**; jangan asumsikan identik saat implementasi varian car.

#### 5.1.2 Section Payment & Financing (kedua lini)

| Field | Tipe kontrol | Req | Val. client / format | Sumber options | Sumber KB |
|---|---|---|---|---|---|
| `payment_option` (opsi pembayaran/AR) | select | ✔ | — | master payment option | `64 §4` (`CMMotorCycle.cshtml:645-677`); BE-04 §5.1 |
| `installment_type` (tipe angsuran / interest-rate type) | select | ✔ | — (catatan mapping: BE-04 §3.1.2 memisahkan `installment_type` ADDM/ADDB — #95 → PAY — dari `interest_rate_type_id` — #92 → CM; bila layar legacy ternyata dua kontrol, JANGAN dilipat satu field — konfirmasi saat implementasi) | master | idem |
| `tenor_months` | select/number | ✔ | integer > 0 | master tenor per produk | idem; BE-04 §3.1 |
| `otr_price` | CurrencyInput | ✔ | numerik > 0; **BE menolak `422 OTR_PRICE_INVALID`** bila 0 / tak ada di referensi (fail-closed) | — | `64 §4` (`CMMotorCycle.cshtml:740-799`); BE-04 §5.1 |
| `down_payment_gross` | CurrencyInput | ✔ | numerik ≥ 0; boundary DP=0 harus eksplisit (BE-04 §3.1) | — | idem |
| `admin_fee` | CurrencyInput | ✔ | numerik ≥ 0 | — | idem |
| `provisi_fee` / `process_fee` | CurrencyInput | ✔ | numerik ≥ 0 | — | idem |
| `insurance_fee` (premi kendaraan) | CurrencyInput **read-only/auto** | auto | diisi dari `GET /insurance-quote` on-change OTR/tenor/asset (`64 BR-CMPOFE-5`) — bukan input bebas `[USULAN]` | kalkulasi BE (rate OJK `[LOCKED]` — BE-04 BR-CMPO-13) | `64 §4`, `CMMotorCycle.js:544-567,2026-2050` |
| `down_payment_net` | display computed | — | dari `calculation-preview` (BE) | — | `64 §4`, `64 BR-CMPOFE-3` |
| `financed_amount` | display computed | — | idem — figur yang sama yang nanti dibekukan jadi `OP`/`ULI`/`LCR` `[LOCKED]` di BE (BE-04 BR-CMPO-4) | — | `64 §4` |
| `installment_amount` | display computed | — | idem | — | `64 §4` |
| `effective_rate` / `flat_rate` | display | — | — | — | `64 §4` |

> **Aturan kalkulasi TIDAK direplikasi di FE.** Legacy menghitung net-DP/financed-amount di browser lalu BE
> membekukan versi server (`64 BR-CMPOFE-3`); rebuild memakai satu sumber formula server-side
> (`POST /calculation-preview` — BE-04 §4 `[USULAN]` di sisi BE) agar preview FE = angka yang dibekukan.
> Guard used-vehicle/UMC (blokir bila disbursement dealer > max & OTR > market price — `64 BR-CMPOFE-4`)
> juga dipindah server-side; FE menampilkan error `422`-nya (field-level bila `details` tersedia).

#### 5.1.3 Section Asuransi Kendaraan (kedua lini)

| Field | Tipe kontrol | Req | Val. client | Sumber options | Sumber KB |
|---|---|---|---|---|---|
| `vehicle_insurance.cover_type` | select | ✔ (bila asuransi kendaraan dipilih) | — | master cover type | `64 §3a S1` (`vehicle_insurance_cover_type_and_fee`); BE-04 §5.1 |
| `vehicle_insurance.insurance_type` | select | ✔ | — | master (mis. `R2_OJK`) | BE-04 §5.1 |
| `vehicle_insurance.tier` (model/tier) | select | ✔ | — | master | BE-04 §5.1; `23 §4` (`TrCmInsurance`) |
| Premi (tampil) | display | — | hasil `insurance-quote`; **setiap lini always-resolving** — jangan tampilkan 0 diam-diam sebagai "tanpa asuransi" (fix silent-zero BE-04 BR-CMPO-12 `[ARTIFACT]`); opt-out = toggle eksplisit | — | BE-04 §4 |

#### 5.1.4 Section Asuransi Jiwa & Kesehatan + field car-line-only (LINI CAR SAJA)

Semua baris bersumber `64 §4` (`CMCar.cshtml:879-1307`) + `64 BR-CMPOFE-6/7`:

| Field | Tipe kontrol | Req | Kondisi render / validasi | Sumber KB |
|---|---|---|---|---|
| `financing_package` (paket pembiayaan) | select | ✔ | car line only | `CMCar.cshtml:879-1307` |
| `process_fee` + toggle **"dibiayakan ke pinjaman"** | CurrencyInput + switch | ✔ | car line only | idem |
| `loss_insurance_fee` | CurrencyInput | opsional | car line only | idem |
| `insurance_provision_fee` | CurrencyInput | opsional | car line only | idem |
| `life_insurance.enabled` (Mega jiwa) + `life_insurance.plan_code` + fee | switch + select + display | opsional | car line only; saat ON → plan wajib; sub-record `TrCmLifeInsuranceCredit` (target `trx_credit_memo_insurance_life`) | idem; BE-04 §3.3/§5.1 |
| `health_insurance` toggle (Mega kesehatan) + fee | switch + display | opsional | **hanya selectable bila customer type = individual DAN flag DOB-eligibility = true** (seeded dari intake) — selain itu toggle disabled ke "No" (`64 BR-CMPOFE-7`); ambang usia persis = `[OPEN]` OQ-CMPOFE-11 | `CMCar.cshtml:9-13,1129-1148` |
| `handset_bundling_fee` | CurrencyInput | opsional | car line only | `CMCar.cshtml:879-1307` |
| `residual_value` | CurrencyInput | opsional | car line only | idem |
| Subsidi: `dealer_subsidy` / `atpm_subsidy` / `third_party_subsidy` | CurrencyInput ×3 | opsional | car line only | idem |

> Kontrak `PUT /finalization` BE-04 §5.1 saat ini baru mengeksplisitkan subset field (payment_option, otr,
> DP, tenor, fee, vehicle/life insurance, vehicle_registration). Field car-line tambahan (financing_package,
> subsidi, handset, residual, health insurance, loss/provision fee) **belum tercantum eksplisit** di contoh
> request BE-04 → **GAP-02 §11** (perluasan payload; jangan dikarang di FE).

#### 5.1.5 Section Skema UMC / Third-Party Ownership (conditional: application type = skema alternatif)

Sumber: `64 §4` (`CMMotorCycle.cshtml:369-563`); `23 §4` (vehicle-registration fields).

| Field | Tipe kontrol | Req (saat section aktif) | Val. client | Sumber KB |
|---|---|---|---|---|
| `cooperation_scheme_type` (tipe skema kerjasama) | select | ✔ | — | `CMMotorCycle.cshtml:369-563` |
| `intermediary_type` + identitas perantara | select + text/lookup | ✔ | — | idem |
| `account_ownership_type` (kepemilikan rekening) | select | ✔ | — | idem |
| `destination_bank` (`bank_id`) | select | ✔ | — (master bank) | idem; BE-04 §5.2 |
| `destination_account_no` | text | ✔ | numerik; + tombol **Cek Rekening** → `GET /bank-account:validate` menampilkan `account_name` (kontrak §8); broker gagal → tampilkan error fail-closed (`502/504`), JANGAN lolos diam-diam | idem; BE-04 §5.2 |
| `chassis_no` / `engine_no` | text ×2 | ✔ | uppercase alfanumerik | idem; BE-04 §5.1 (`vehicle_registration`) |
| `plate_no` (prefix plat) | text | ✔ | — | `23 §4` |
| Status `invoice` / `STNK` | select/checkbox | ✔ | — | `CMMotorCycle.cshtml:369-563` |

### 5.2 FE04-SCR-02 — Upload Dokumen + RFA

| Field | Tipe kontrol | Req | Validasi | Sumber KB |
|---|---|---|---|---|
| `supporting_document_files` | FileUpload multi | ✔ | extension ∈ {`.jpg`,`.jpeg`,`.png`,`.pdf`}; di bawah size limit terkonfigurasi — **blocking** di FE + otoritatif di server (legacy: hanya advisory `67 §5 item 15/16` `[ARTIFACT]`); nilai limit persis belum ter-evidensi → GAP-01 §11 | `64 §3a S2`; `CMController.cs:1355-1412` |
| `npwp_no` | text | conditional ✔ | **muncul & wajib bila computed financing amount ≥ 50.000.000 (Rupiah); minimal 15 karakter** — `[LOCKED]` (regulatory-adjacent, `64 BR-CMPOFE-8`); sumber angka financing = data memo dari BE (bukan hitungan FE) | `64 §3a S2`; `CMController.cs:1355-1412` |
| Aksi `Simpan Draft` | button | — | tidak mengunci memo | `64 BR-CMPOFE-9` |
| Aksi `RFA` | button + ConfirmDialog | — | mengunci memo (→ `finalized`); prasyarat: file valid + NPWP rule lolos; endpoint milik **BE-01** → GAP-01 §11 | `64 §3a S2` transitions; BE-04 §1.2 |

### 5.3 FE04-SCR-03 — Listing (kolom & filter)

Kolom row (grounded `GET /purchase-orders` BE-04 §5.7 + layar legacy `64 §6/§11`):

| Kolom | Sumber field BE | Catatan |
|---|---|---|
| `po_number` | `po_number` | — |
| `credit_id` | `credit_id` | nomor kontrak (PK, GT STEP 8) |
| Dealer | `dealer_name` | — |
| Aset | `asset_desc` | — |
| Status memo | `memo_status` (kanonik) | mapping gate per lini legacy (`status_ca` vs `status_cm`) = `[OPEN]` OQ-CMPOFE-02 — FE HANYA membaca field kanonik BE |
| Status PO | `po_status` | `[LOCKED]` audit-facing (`64 §6` "Status PO") |
| Jumlah cetak | `print_count` | `[LOCKED]` audit (`23 BR-CMPO-9`) |
| Jumlah Send Mail | `send_mail_count` | `[LOCKED]` audit (`64 §6`; `Index.cshtml:99-100`, `IndexMotor.cshtml:115-124`) |
| Tanggal approve | `approved_at` | — |
| Aksi | flag kondisional | lihat §6.3 |

Filter: `branch_id`, `status`, `product_line`, `from`/`to` (tanggal) — paritas query BE-04 §5.7.

### 5.4 FE04-DLG-01 — Open CM

| Field | Tipe | Req | Validasi | Sumber |
|---|---|---|---|---|
| `reason` | textarea | ✔ | non-empty; contoh placeholder "Warna unit fisik berbeda" | BE-04 §5.5 request |
| `credit_id` | hidden (context) | ✔ | dari row terpilih | BE-04 §5.5; `64 §3a S5` |
| `po_number` | path param | ✔ | dari row terpilih | idem |
| (`item_id` legacy) | — | — | ada di request legacy (`64 §3a S5`) tetapi TIDAK di kontrak BE-04 §5.5 → GAP-03 §11 | `acquisition.js:24-66` |

### 5.5 Di-luar-scope

Sensus field layar insurance-cover (IC1–IC6: insurance_source, finance_type, item_type, seleksi kontrak by
nomor NPP, cover-letter/billing upload, payment date/bank) TIDAK disensus di sini — out-of-scope 04 sampai
`[OPEN]` OQ-CMPOFE-08 terjawab (§1.2; BE-04 §1.2). Bila resolusi menempatkannya di acquisition, buat PRD FE
terpisah/annex.

---

## 6. Aturan Interaksi & Staging

### 6.1 Stepper finalisasi (dari KB §3a — S1→S2; S3–S5 lintas modul)

Stage ID selaras `64 §3a` dan `23 §3a`:

| Stage | Screen FE | Aktor | Masuk bila | Keluar ke |
|---|---|---|---|---|
| **S1** Finalisasi struktur finansial | FE04-SCR-01 | CMO / Credit (Admin) | memo `status ∈ {draft, corrected}` (pengganti session-flag Add/Edit/Correction legacy — `64 §3a S1` conditions) | S2 via tombol "Lanjut ke Dokumen" (save dulu) |
| **S2** Upload dokumen + Save Draft / RFA | FE04-SCR-02 | Operational (draft) / Credit (Admin)-branch admin (RFA) | S1 tersimpan | RFA sukses → memo `finalized`, keluar dari jalur edit (S3 milik FE-03) |
| **S3** Disposisi komite | **FE-03** (Inbox) | committee | — | approve → S4; correction/reject → memo `corrected`/`rejected` |
| **S4** Print + email PO | FE04-SCR-03/04 | Admin Cabang | `po_status=issued` & memo `approved` | — |
| **S5** Open CM | FE04-DLG-01 | Credit (Admin)/Admin Cabang | gate §6.3 | memo & PO → `corrected`; return-target per OQ-GT-03 |

Wizard state di FE: stepper 2 langkah (Struktur Finansial → Dokumen & RFA) dengan progress indicator;
deep-link per langkah via route §3 `[USULAN]`. **Delta v2 wajib dipahami tim FE**: pada alur final, entry
penuh S1 normalnya terjadi di jalur moofi upstream; SCR-01 dipakai terutama untuk **koreksi** (memo
`corrected`) dan jalur non-moofi (= jalur manual web; OQ-GT-01 ✅ RESOLVED — evidence, §1.0) — jangan hardcode asumsi "selalu entry
dari nol". Pre-seed nilai awal dari intake = `[OPEN]` OQ-CMPO-08/OQ-CMPOFE-06.

### 6.2 Conditional rendering & disable/enable (form)

| Aturan | Perilaku FE | Sumber |
|---|---|---|
| Mode editable vs read-only | Seluruh kontrol S1/S2 editable hanya saat memo `status ∈ {draft, corrected}`; status lain → render read-only + banner status; submit tetap bisa ditolak BE `409 MEMO_NOT_EDITABLE` | `64 BR-CMPOFE-1` [INTENT]; BE-04 §5.1 |
| Section UMC (D) | render hanya bila `application_type` = skema alternatif/UMC | `64 §3a S1` conditional |
| Panel car-line (§5.1.4) | render hanya bila `product_line=CAR` | `64 BR-CMPOFE-6` |
| Health insurance toggle | enabled hanya bila customer type = individual && DOB-eligibility flag true (dari data memo/BE); selain itu disabled = "No" | `64 BR-CMPOFE-7`; ambang `[OPEN]` OQ-CMPOFE-11 |
| Premi asuransi auto-refetch | on-change `otr_price`/`tenor`/`asset_kind` → `GET /insurance-quote` (debounced), tulis hasil ke field premi; saat request in-flight, field premi & tombol simpan busy | `64 BR-CMPOFE-5`; BE-04 §4 |
| Kalkulasi preview | on-change field finansial → `POST /calculation-preview` (debounced); panel ringkasan menampilkan hasil server | `64 BR-CMPOFE-3` (outcome); BE-04 §4 |
| NPWP conditional | tampil+required bila financing ≥ 50 jt; hilang di bawahnya | `64 BR-CMPOFE-8` `[LOCKED]` |
| Tombol RFA | disabled sampai dokumen wajib ter-upload valid + NPWP rule lolos; klik → ConfirmDialog | `64 §3a S2` transitions |
| Double-submit | semua tombol submit memakai BusyButton (disable + spinner) — konvensi FE-00 | `67 BR-CSB-20` [INTENT] |
| Dirty-navigation | back/route-change dengan perubahan belum tersimpan → prompt simpan/buang `[USULAN]` (pengganti save-on-back legacy `64 Edge Case 7`) | OQ-FE04-01 §11 |

### 6.3 Gating aksi listing/PO (status-driven)

| Aksi | Tampil/enabled bila | Sumber |
|---|---|---|
| `Print PO` | memo approved (legacy: kolom status per lini `== APPROVE`; **field authoritative per lini = `[OPEN]` OQ-CMPOFE-02**) DAN user punya capability cetak (position-gate `[LOCKED]`, enforcement BE) → FE membaca **flag kondisi dari response BE** (mis. `memo_status`/`po_status` kanonik §5.7), bukan menghitung dua kolom sendiri `[USULAN]` | `64 BR-CMPOFE-12`; `Index.cshtml:116-123`; `IndexMotor.cshtml:141-148`; BE-04 §5.7 |
| `File-PO` (download) | `document_uri` tersedia (sudah pernah render/print) | `64 §11 IndexMotor.cshtml`; BE-04 §4 |
| `Kirim Ulang Email` | PO sudah di-print ≥1x (guard BE `409 PO_NOT_PRINTED`) | BE-04 §5.6 |
| `Open CM` | memo approved DAN downstream belum melewati ambang (legacy 2-wheel: NPP status ∈ {Correction, blank}; 4-wheel tanpa check — **asimetri `[OPEN]` OQ-CMPOFE-03**; rebuild menambah pertimbangan Vertel D-02) → FE menampilkan aksi bila BE menandai boleh; tetap tangani `409 PO_CORRECTION_BLOCKED_DOWNSTREAM` | `64 BR-CMPOFE-13`, Edge Case 3; BE-04 BR-CMPO-26 |
| Auto-print pasca approve | **TIDAK diimplementasikan** — legacy auto print+email hanya lini motor (`64 BR-CMPOFE-11`), intensionalitas `[OPEN]` OQ-CMPOFE-01; keputusan BE-04 §5.4: mint tanpa auto-print, print = aksi eksplisit. FE mengikuti: tidak ada trigger print tersembunyi | `64 §5.8`; BE-04 §5.4 |

### 6.4 Interaksi print & email (S4)

1. Klik `Print PO` → ConfirmDialog ringan → `POST /print` → sukses: tampilkan `print_count` baru,
   `document_uri` (buka preview), dan **status dispatch email** dari field `email_dispatch`
   (`requested`/`already_sent`) — email adalah bagian standar alur cetak (GT STEP 13; BE-04 BR-CMPO-23),
   FE menampilkannya sebagai informasi, bukan aksi terpisah yang bisa terlupa.
2. Kegagalan email TIDAK menggagalkan print (advisory — BE-04 §5.4; failure-mode final `[OPEN]` OQ-CMPO-12) —
   FE menampilkan hasil print sukses + warning email bila ada.
3. `Kirim Ulang Email` → `POST /email` → bila `already_sent=true` tampilkan notice idempotent "sudah pernah
   terkirim" (BUKAN error) — BE-04 §5.6/AC-16.
4. Recipient email TIDAK PERNAH diinput dari FE — di-resolve server-side dari dealer terverifikasi
   (BE-04 §5.6 `[ARTIFACT]` fix BR-CMPO-20); UI hanya menampilkan `sent_to_dealer` hasil response.

---

## 7. State Tampilan

| State | Perilaku | Sumber konvensi |
|---|---|---|
| **Loading** | Skeleton untuk listing/detail; LoadingOverlay/BusyButton untuk mutasi — konsisten shared FE-00 (pengganti overlay global legacy) | `67 BR-CSB-8` [INTENT] |
| **Empty vs failure (listing)** | WAJIB dua state berbeda: "Data tidak tersedia" (sukses, kosong) vs "Gagal memuat data dari server" (+retry) — kontrak paling lengkap dari legacy | `67 BR-CSB-11` [INTENT] |
| **Error request** | SETIAP kegagalan request tampil ke user (toast/alert/inline) — dilarang silent swallow (8/24 call site legacy senyap = `[ARTIFACT]`) | `67 BR-CSB-10`, Edge Case 7 |
| **Error tervalidasi server** | Envelope BE `{ code, message, details?, correlation_id }` (BE-04 §5) di-map: `422` → error field-level bila `details` menunjuk field, selainnya banner form; `409` → banner status (mis. `MEMO_NOT_EDITABLE`, `INSURANCE_LOCKED`, `PO_NOT_READY`, `PO_CORRECTION_BLOCKED_DOWNSTREAM`, `PO_NOT_PRINTED`); `403` → notice "tidak berwenang" (`PO_PRINT_NOT_AUTHORIZED`); `502/504` DOKU → banner fail-closed pada blok Cek Rekening | BE-04 §5.1–5.6 |
| **Status-driven display (memo)** | Badge `draft`/`finalized`/`approved`/`corrected`/`rejected` (enum kanonik BE-04 §7.1); `finalized` = "menunggu hierarki" (read-only); `corrected` = editable kembali dengan banner koreksi; `rejected` = terminal read-only | BE-04 §7.1; `64 §8` diagram A |
| **Status-driven display (PO)** | Badge `issued`/`corrected`/`fulfilled`; print-lock/count/email = **atribut** yang ditampilkan, bukan status | BE-04 §7.2 |
| **Status email** | `is_send=false` → "belum terkirim"; `true` → "terkirim ke {sent_to} @ {sent_at}"; hasil idempotent `already_sent` → notice info | BE-04 §3.6/§5.6 |
| **Insurance locked** | Pasca approve, panel asuransi menampilkan badge "terkunci saat approval" (lock D-01 S12); percobaan edit → `409 INSURANCE_LOCKED`. Sumber freeze = **04 mengonsumsi event `MemoApproved`** (OQ-BE03-02 RESOLVED — opsi b; eventually-consistent, BE-04 §5.3) → badge/nilai frozen muncul setelah event terkonsumsi (jendela singkat pasca-approve); copy JANGAN mengklaim "terkunci seketika" | BE-04 §5.3 langkah 2, BR-CMPO-22 `[LOCKED]`; BE-03 §5.2 note |
| **PDF preview** | loading → viewer; gagal → fallback tombol download + pesan | `[USULAN]` |
| **Responsive** | Semua state di atas berlaku mobile & desktop; tabel/preview scroll dalam container sendiri | NFR umbrella FE |

---

## 8. Kontrak Konsumsi API (per screen — WAJIB konsisten dengan BE-04 §4/§5)

Semua endpoint di bawah ADA di `BE-04-contract-cm-po.md` §4 (kontrak detail §5.x). Kebutuhan yang tidak ada
di sana dicantumkan sebagai GAP di §11, bukan di tabel ini.

| Screen | Endpoint (BE-04) | Dipakai untuk | Kontrak detail |
|---|---|---|---|
| FE04-SCR-01 | `GET /credit-memos/{memoId}` | load memo + status + sub-record asuransi + flag editability | BE-04 §4 |
| FE04-SCR-01 | `PUT /credit-memos/{memoId}/finalization` | Simpan Draft / simpan sebelum lanjut; error `409 MEMO_NOT_EDITABLE`, `422 OTR_PRICE_INVALID` | BE-04 §5.1 |
| FE04-SCR-01 | `GET /credit-memos/{memoId}/insurance-quote` | auto-refetch premi on-change OTR/tenor/asset | BE-04 §4 (dipetakan eksplisit ke `64 BR-CMPOFE-5`) |
| FE04-SCR-01 | `POST /credit-memos/{memoId}/calculation-preview` | panel ringkasan net-DP/financed/installment (server-side, pengganti kalkulasi browser) | BE-04 §4 `[USULAN di BE]` |
| FE04-SCR-01 (Section D) | `GET /credit-memos/{memoId}/bank-account:validate?bank_id&account_no` | tombol Cek Rekening (DOKU); `502/504` fail-closed | BE-04 §5.2 |
| FE04-SCR-03 | `GET /purchase-orders?branch_id&status&product_line&from&to&page&size` | listing + filter; row: `po_number, credit_id, memo_status, po_status, print_count, send_mail_count, dealer_name, asset_desc, approved_at` | BE-04 §5.7 `[USULAN di BE]` |
| FE04-SCR-03/04 | `GET /purchase-orders/{poNumber}` | detail PO + audit print + status email | BE-04 §4 |
| FE04-SCR-03 (navigasi dari memo) | `GET /credit-memos/{memoId}/purchase-order` | resolve PO milik sebuah credit | BE-04 §4 |
| FE04-SCR-03/04 | `GET /purchase-orders/{poNumber}/document` | preview/download PDF PO (aksi File-PO) | BE-04 §4 `[USULAN di BE]` |
| FE04-SCR-03/04 | `POST /purchase-orders/{poNumber}/print` | cetak PO; response memuat `print_count`, `document_uri`, `email_dispatch`; error `403 PO_PRINT_NOT_AUTHORIZED`, `409 PO_NOT_READY` | BE-04 §5.4 |
| FE04-SCR-03/04 | `POST /purchase-orders/{poNumber}/email` | re-send email dealer; idempotent `already_sent`; error `409 PO_NOT_PRINTED` | BE-04 §5.6 |
| FE04-DLG-01 | `POST /purchase-orders/{poNumber}/correction` (body `{credit_id, reason}`) | Open CM; response `return_target` (`[OPEN]` OQ-GT-03); error `409 PO_CORRECTION_BLOCKED_DOWNSTREAM` | BE-04 §5.5 |

Endpoint yang DIBUTUHKAN layar tetapi TIDAK ada di BE-04 (jangan dikarang): upload dokumen pendukung memo,
aksi RFA (milik BE-01), sumber options master (dealer/aset/payment-option/paket), field car-line tambahan pada
payload finalisasi, `item_id` pada Open CM → **GAP-01..GAP-04 di §11**.

---

## 9. Acceptance Criteria (Given/When/Then)

**FE04-SCR-01 — Finalisasi**
- **AC-FE-1** *Given* memo `status=draft`, *When* user membuka route finalization, *Then* form editable dengan
  nilai memo ter-load dari `GET /credit-memos/{memoId}`; panel ringkasan menampilkan angka dari
  `calculation-preview` — bukan hasil hitung browser. (`64 BR-CMPOFE-1/3`; BE-04 §4)
- **AC-FE-2** *Given* memo `status=finalized` atau `approved`, *When* route finalization dibuka, *Then* semua
  kontrol read-only + banner status; tidak ada tombol Simpan. (`64 BR-CMPOFE-1`)
- **AC-FE-3** *Given* user mengubah `otr_price`/`tenor`/`asset_kind`, *When* perubahan settle (debounce),
  *Then* `GET /insurance-quote` terpanggil dan field premi ter-update; bila request gagal, error tampil
  (bukan senyap). (`64 BR-CMPOFE-5`; `67 BR-CSB-10`)
- **AC-FE-4** *Given* BE membalas `422 OTR_PRICE_INVALID`, *When* Simpan Draft, *Then* error tampil pada field
  `otr_price` / banner form dengan pesan BE; tidak ada state "sukses palsu". (BE-04 §5.1/AC-3)
- **AC-FE-5** *Given* `product_line=MOTOR`, *When* form dirender, *Then* panel car-line (§5.1.4) TIDAK
  dirender; *Given* `product_line=CAR`, *Then* panel car-line dirender lengkap. (`64 BR-CMPOFE-6`)
- **AC-FE-6** *Given* customer type ≠ individual ATAU DOB-eligibility=false, *When* panel asuransi kesehatan
  dirender, *Then* toggle disabled pada "No". (`64 BR-CMPOFE-7`)
- **AC-FE-7** *Given* application type = skema UMC, *When* form dirender, *Then* Section D tampil dan
  field-nya required; *And* klik Cek Rekening menampilkan `account_name` dari BE atau error fail-closed bila
  broker gagal. (`64 §4`; BE-04 §5.2)

**FE04-SCR-02 — Upload + RFA**
- **AC-FE-8** *Given* file berekstensi di luar {jpg,jpeg,png,pdf} atau melebihi size limit, *When* dipilih,
  *Then* file DITOLAK client-side (blocking, bukan warning advisory) dengan pesan jelas. (`64 BR-CMPOFE-8`;
  fix `67 §5 item 15/16` `[ARTIFACT]`)
- **AC-FE-9** *Given* computed financing amount ≥ 50.000.000, *When* layar dirender, *Then* field NPWP tampil
  + required min 15 karakter; *Given* di bawah ambang, *Then* field tidak wajib. (`64 BR-CMPOFE-8` `[LOCKED]`)
- **AC-FE-10** *Given* dokumen wajib belum lengkap/valid, *When* layar dirender, *Then* tombol RFA disabled;
  *Given* lengkap, *When* RFA diklik, *Then* ConfirmDialog dua-tombol tampil sebelum submit. (`64 §3a S2`;
  `67 BR-CSB-18`)
- **AC-FE-11** *Given* RFA sukses, *Then* memo tampil `finalized` (menunggu hierarki) dan seluruh jalur edit
  tertutup; panel disposisi komite TIDAK tampil di layar ini (milik FE-03). (§1.2; `64 §3a S2→S3`)

**FE04-SCR-03/04 — Listing, Print, Email**
- **AC-FE-12** *Given* listing dimuat dan fetch gagal, *Then* state "gagal memuat" + retry tampil — berbeda
  dari state kosong "data tidak tersedia". (`67 BR-CSB-11`)
- **AC-FE-13** *Given* row dengan memo belum approved, *Then* aksi Print PO tidak tampil/disabled; *Given*
  approved + user ber-capability cetak, *Then* Print tampil. Bila BE tetap membalas `403
  PO_PRINT_NOT_AUTHORIZED`, pesan "posisi tidak berwenang" tampil. (`64 BR-CMPOFE-12/14`; BE-04 AC-10)
- **AC-FE-14** *Given* Print sukses, *Then* UI menampilkan `print_count` ter-update, link/preview
  `document_uri`, dan status dispatch email (`email_dispatch.requested/already_sent`); kegagalan email tampil
  sebagai warning TANPA membatalkan tampilan print sukses. (BE-04 §5.4/AC-12b)
- **AC-FE-15** *Given* PO sudah pernah di-email (`is_send=1`), *When* Kirim Ulang Email diklik, *Then*
  response `already_sent` tampil sebagai notice info idempotent — bukan error, bukan kirim ganda.
  (BE-04 §5.6/AC-16)
- **AC-FE-16** *Given* PO belum pernah di-print, *When* Kirim Ulang Email diklik, *Then* `409 PO_NOT_PRINTED`
  tampil dengan penjelasan "email hanya setelah minimal 1x print". (BE-04 AC-17)
- **AC-FE-17** Kolom `print_count` / Jumlah Send Mail / Status PO SELALU tampil di listing (audit `[LOCKED]`).
  (`64 §6`; `23 BR-CMPO-9`)

**FE04-DLG-01 — Open CM**
- **AC-FE-18** *Given* aksi Open CM diklik, *Then* dialog konfirmasi dua-tombol + field alasan (required)
  tampil; submit tanpa alasan diblokir client-side. (`67 BR-CSB-18`; BE-04 §5.5)
- **AC-FE-19** *Given* konfirmasi dikirim dan sukses, *Then* UI menampilkan status memo & PO → `corrected`,
  info print-history dipertahankan, dan routing/pesan mengikuti `return_target` response (nilai final
  menunggu OQ-GT-03 — sementara pesan generik + kembali ke listing). (BE-04 §5.5/AC-13)
- **AC-FE-20** *Given* BE membalas `409 PO_CORRECTION_BLOCKED_DOWNSTREAM`, *Then* pesan "legalisasi/Vertel/NPP
  sudah berjalan melewati ambang koreksi" tampil dan dialog tertutup tanpa perubahan state. (BE-04 AC-14b)

**Lintas screen**
- **AC-FE-21** *Given* user dengan role di luar sensus D-10 / klaim super-user, *Then* tidak ada route/menu
  modul ini yang memberi jalur bypass — menu di-resolve dari BE dan mutasi tetap ditolak BE. (D-09 `[LOCKED]`;
  BE-04 AC-18)
- **AC-FE-22** Semua screen modul ini lolos uji responsive (viewport mobile ≥360px dan desktop): tidak ada
  horizontal scroll level halaman; tabel/preview scroll di container sendiri. (NFR umbrella FE)
- **AC-FE-23** Tidak ada request dari screen modul ini yang gagal secara senyap — setiap failure path punya
  output UI yang bisa dibedakan dari sukses. (`67 BR-CSB-10` `[ARTIFACT]` fix)

---

## 10. Dependency

### 10.1 Modul BE

| Dependency | Bentuk | Isi |
|---|---|---|
| **BE-04-contract-cm-po** | REST (representasi; transport final `[OPEN]` OQ-ARCH-STACK/D-11) | Seluruh endpoint §8. |
| **BE-01-intake-cas** | boundary | Aksi **RFA** di FE04-SCR-02 (BE-04 §1.2: RFA milik 01) + kemungkinan endpoint upload dokumen — konfirmasi kontrak di BE-01 (GAP-01). |
| **BE-03-approval-committee** | tidak langsung | Disposisi komite mengubah status memo yang dibaca layar-layar modul ini; panel aksi ada di FE-03. |
| **Masters (D-08)** | read-only lookup | Options dealer/aset/payment-option/bank/paket + email dealer terverifikasi (ditampilkan sebagai hasil, bukan input). Endpoint options TIDAK ada di BE-04 → GAP-04. |

### 10.2 Shared components dari FE-00 (jangan duplikasi)

`CurrencyInput` (satu konvensi format id-ID — `67 §5 item 5/6`), `DataTable` (satu kontrak grid + empty-vs-
failure — `67 BR-CSB-11`), `ConfirmDialog` (dua-tombol, satu kosakata — `67 BR-CSB-18/19`), `LookupModal`
(satu kontrak lookup — `67 BR-CSB-13`), `FileUpload` (blocking validation — `67 BR-CSB-15/16`),
`BusyButton`/`LoadingOverlay` (`67 BR-CSB-8/20`), `StatusBadge`, error-envelope handler (`67 BR-CSB-9`),
guard auth/branch shell (`60 BR-SHELL-1..6`; fix `60 Edge Case 3/4` `[ARTIFACT]`).

### 10.3 Upstream/downstream layar

- **FE-03 approval-inbox**: entry point view-only ke SCR-01/02 saat mode approval; menampung panel disposisi.
- **FE-05 npp-vertel**: konsumen downstream pasca-PO (Vertel STEP 14, D-02); status downstream memengaruhi
  gate Open CM (OQ-CMPOFE-03).
- **FE-01 intake-cas**: sumber seed nilai awal S1 (`[OPEN]` OQ-CMPO-08/OQ-CMPOFE-06) dan pemilik flag
  DOB-eligibility (OQ-CMPOFE-11).

---

## 11. Keputusan Dibutuhkan (Open Questions & GAPs)

Jangan diselesaikan diam-diam. OQ-ID mengikuti register KB/GT/BE-04; GAP = kebutuhan layar tanpa kontrak BE.

### 11.1 GAP kontrak API (kebutuhan FE tanpa endpoint di BE-04)

| ID | Kebutuhan layar | Status | Resolusi |
|---|---|---|---|
| **GAP-01** | FE04-SCR-02 butuh endpoint (a) **upload dokumen pendukung memo** dan (b) **submit RFA**. Keduanya TIDAK ada di BE-04 §4 — RFA eksplisit milik BE-01 (BE-04 §1.2); endpoint upload belum ter-map (milik 01 atau 04?). Nilai size-limit upload juga belum ter-evidensi (legacy "configured size limit", `CMController.cs:1355-1412`). | `[OPEN]` | Konfirmasi kontrak di BE-01/FE-01; bila upload ternyata milik 04, tambahkan ke BE-04 §4 — jangan dikarang di FE. |
| **GAP-02** | Payload `PUT /finalization` (BE-04 §5.1) belum memuat field car-line §5.1.4 (financing_package, process-fee-financed toggle, loss/provision fee, health insurance, handset, residual, subsidi ×3) dan field UMC lengkap (skema, perantara, account-ownership, plate, invoice/STNK) — padahal sensus layar `64 §4` mewajibkannya. | `[OPEN]` | Perluas kontrak BE-04 §5.1 (additive) bersama tim BE; FE menahan implementasi field tsb sampai kontrak ada. |
| **GAP-03** | Open CM legacy mengirim `item_id` selain `po_no`+`credit_id` (`64 §3a S5`); kontrak BE-04 §5.5 hanya `{credit_id, reason}`. Apakah `item_id` masih dibutuhkan (multi-item per memo?) | `[OPEN]` | Konfirmasi tim BE + domain: bila satu memo bisa >1 item, kontrak §5.5 perlu `item_id`. |
| **GAP-04** | Sumber options master (dealer, surveyor, marketing head, asset cascade, payment option, tenor, bank, paket, cover/insurance type) — endpoint read TIDAK ada di BE-04 (masters milik modul master, D-08). | `[OPEN]` | Rujuk kontrak modul master (BE/FE master-data); sampai ada, FE memakai kontrak placeholder yang ditandai TODO-blocking. |

### 11.2 Open Questions fungsional (dibawa dari KB/GT/BE-04 — memengaruhi UX modul ini)

| OQ-ID | Prio | Pertanyaan (dampak FE) | Sumber |
|---|---|---|---|
| **OQ-CMPOFE-01** | P1 | Auto print+email saat approve hanya lini motor — intentional? Menentukan apakah FE perlu surface "PO otomatis tercetak" pasca-approve atau selalu manual. Sampai terjawab: **tanpa auto-print** (ikut BE-04 §5.4). | `64 §10`; BE-04 §11 |
| **OQ-CMPOFE-02** | P1 | Field status gate tombol Print berbeda per lini (`status_ca` vs `status_cm`) — mana authoritative? Menentukan flag kanonik yang dibaca FE di listing (§6.3). | `64 BR-CMPOFE-12`; BE-04 §5.7 |
| **OQ-CMPOFE-03** | P2 | Ambang blokir Open CM terhadap downstream (NPP; kini + Vertel D-02) — kapan aksi Open CM disembunyikan vs ditampilkan-lalu-409? | `64 BR-CMPOFE-13`; BE-04 BR-CMPO-26 |
| **OQ-CMPOFE-04 / OQ-CMPO-10** | P1 | FE legacy mengirim disposisi `V`/`C` lewat aksi approval yang sama — persist BE belum terbukti. Menentukan enum status yang boleh dirender StatusBadge (jangan tampilkan state yang tak pernah tercapai). | `64 §10`; BE-04 §7.1 |
| **OQ-CMPOFE-06 / OQ-CMPO-08** | P2 | Kunjungan pertama form finalisasi: pre-seed dari intake/draft STEP 8 atau re-key manual? Menentukan default-value strategy SCR-01. | `64 §10`; BE-04 §11 |
| **OQ-CMPOFE-11** | P3 | Ambang usia/DOB flag eligibility asuransi kesehatan — menentukan tooltip/pesan disable toggle. | `64 §10` |
| **OQ-CMPOFE-12** | P3 | Paritas section Data Asset lini car (CMCar.cshtml:80-878 belum dibaca penuh) — blocking varian car SCR-01 Section A. | `64 §10` |
| **OQ-CMPOFE-13** | P3 | Paritas validasi upload/NPWP layar car (CACar.cshtml) vs motor — blocking varian car SCR-02. | `64 §10` |
| **OQ-CMPOFE-05** | P2 | Katalog posisi berwenang cetak PO (kode legacy = hint tak ter-enforce `[ARTIFACT]`) — menentukan capability flag yang dikirim BE untuk visibilitas tombol Print. | `64 BR-CMPOFE-14`; BE-04 §2 |
| **OQ-CMPOFE-08** | P2 | Relasi layar insurance-cover batch (IC1–IC6) vs insurance binding per-memo D-01 S12 — menentukan apakah perlu PRD FE terpisah. | `64 §10`; BE-04 §1.2 |
| **OQ-GT-01** | ~~P1~~ **RESOLVED** | Dual approve path moofi/non-moofi — menentukan seberapa besar porsi entry S1 yang benar-benar terjadi di FINCORE FE vs hanya jalur koreksi. → **RESOLVED — evidence (2026-07-14)**: non-moofi = jalur manual web, LIVE (pemisah = trigger, bukan channel); porsi entry S1 di FINCORE FE = keputusan desain port jalur + OQ-MEET-06 (BE-04 §1.0/§11). | GT §Open; BE-04 §1.0 |
| **OQ-GT-03** | P2 | Return-target Open CM (full re-entry Moofi Step 1–12 vs field-scoped) — menentukan routing pasca-sukses FE04-DLG-01 dan apakah SCR-01 mode `corrected` cukup. | GT §Open; BE-04 §5.5 |
| **OQ-MEET-06 (D-07)** | P1 | Matriks step per produk MACF — menentukan varian form per produk di luar dikotomi car/motor. | `_MEETING-DECISIONS-2026-07.md` |
| **OQ-ARCH-STACK (D-11/D-12)** | P2 | FE = Next.js `[LOCKED]`; residual: versi/router/pattern data-fetch & auth integration menunggu dokumen ITEC + FE-00. | D-11/D-12; BE-04 §11 |

### 11.3 Keputusan UX baru (diusulkan PRD ini — butuh sign-off PO)

| ID | Usulan | Latar |
|---|---|---|
| **OQ-FE04-01** | Ganti perilaku legacy **save-on-back** (navigasi mundur menyimpan seluruh form secara diam-diam) dengan dirty-check + prompt eksplisit. Konfirmasi ke product owner apakah persistence-on-back legacy load-bearing. | `64 Edge Case 7` (rebuild guidance-nya sendiri meminta konfirmasi) |
| **OQ-FE04-02** | Konsolidasi 2 listing legacy → 1 route + filter `product_line`, dan 2 form CM → 1 route ber-varian. Perlu sign-off bahwa pemisahan per lini bukan kebutuhan operasional (mis. pembagian kerja tim cabang per lini). | `64 §11` (Index vs IndexMotor); `64 BR-CMPOFE-6` |
| **OQ-FE04-03** | Panel disposisi komite dipindah SELURUHNYA ke FE-03 Inbox (tidak di-embed di layar upload seperti legacy). Perlu konfirmasi alur kerja approver: apakah approver butuh melihat dokumen upload dalam konteks yang sama saat memutus (bila ya → FE-03 menyematkan view-only SCR-02, bukan sebaliknya). | `64 §2/§5.7`; boundary BE-04 §1.2 |

> **Sudah RESOLVED (bukan blocker)**: FE = Next.js (D-12 `[LOCKED]`); super-user dihapus (D-09 `[LOCKED]`);
> sensus role cabang (D-10 `[LOCKED]`); email PDF PO = bagian standar STEP 13 milik 04 — berbeda dari email
> blast pasca STEP 15 (D-03, milik 05); Vertel = STEP 14 milik 05 (D-02); enforcement cetak PO server-side
> (dead FE check legacy dibuang — `64 BR-CMPOFE-14` `[ARTIFACT]`).
