# PRD — Credit Analysis & Scoring (STEP 10–11) [FE]

> **Audience**: Tim Frontend (FE). **Target stack**: **Next.js** `[LOCKED per D-12]`. **Tanggal**: 2026-07-14.
> **Pasangan BE**: `docs/prd/acquisition/BE-02-credit-analysis.md` — kontrak API di §8 dokumen ini WAJIB konsisten dengan §4/§5 file itu; endpoint yang dibutuhkan layar tetapi TIDAK ada di BE PRD dicatat sebagai **GAP** di §11 (tidak dikarang).
> **Sumber otoritatif**: `.mega-sdd/knowledge-base/.sp-manifests/_ACQUISITION-GROUND-TRUTH.md` v2 (16-STEP, PDF 08072026) + `.mega-sdd/knowledge-base/.sp-manifests/_MEETING-DECISIONS-2026-07.md` (D-01..D-12) + KB FE `60-frontend/62-credit-analysis-screens.md` (field census layar legacy CA) + KB FE `60-frontend/60-app-shell-auth-navigation.md`, `60-frontend/67-client-side-behavior.md` (konvensi shell & client-side) + KB BE `10-domains/21-credit-analysis-scoring.md`, `50-integrations/slik-ojk.md`, `50-integrations/pefindo.md`, `50-integrations/neoscore.md`, `50-integrations/rac-bank-mega-risk-engine.md`.
> **Keputusan meeting terintegrasi**: D-01 (Step 7/8/10/11), D-02 (boundary Vertel), D-07, D-09, D-10, D-11, D-12.
> **Status**: Layar legacy (FINCORE.WEB) = **EVIDENCE, bukan mandat desain** — OUTCOME (field, aturan, staging, role-gating) dipertahankan; UX Next.js baru ditandai **USULAN**. NFR: **responsive mobile + desktop**. **Super-user TIDAK ADA** (D-09 `[LOCKED]`).

Modul FE **02-credit-analysis** adalah presentation layer **STEP 10 (RAC display) + STEP 11 (Credit Analysis)**: Credit Analyst membuka worklist ter-scope branch+analyst, membaca status gate RAC (async, `pending` first-class — D-01 Step 8), men-transkrip & me-review **riwayat kolektibilitas SLIK/FCL per bulan** (grid per-bank ≤24 bulan), memvalidasi **checklist dokumen granular** (~40 field `TrCaDocuments`, per kanal Dukcapil vs mobile-app), membaca **NeoScore** terstruktur, melihat **DSR/LTV/DP% yang dihitung BE** (FE TIDAK menghitung — BE BR-02-32 evidence FE `62-credit-analysis-screens §5.3`), lalu merekam rekomendasi **Recommended / Not Recommended + justifikasi** (vocabulary final PDF v2 STEP 11 — GT `:45-48`). Dua layar legacy per produk (motor "CreditAnalyst" multi-panel vs mobil "CACar" 5C dispatcher — `62-credit-analysis-screens §1` [VERIFIED]) di-rebuild sebagai **satu workstation config-driven per product matrix** (D-07; BE BR-02-33) — framing final [OPEN] OQ-CRSCORE-10/OQ-MEET-06, jangan diputuskan diam-diam.

---

## 1. Ruang Lingkup & Kepemilikan

### 1.1 Screens yang DIMILIKI modul ini

| Screen | Fungsi | Evidence legacy | Sumber |
|---|---|---|---|
| **SCR-CA-01 — CA Worklist** | Antrian aplikasi menunggu analisis, **scoped branch + analyst**; search + pagination. | `Views/Acquisition/CreditAnalyst/List.cshtml`, `CreditAnalystController.cs:266-315` | `62-credit-analysis-screens §3a` MOTOR-S1, §4; BE-02 §4 `GET /credit-analysis/worklist` |
| **SCR-CA-02 — CA Workstation (entry multi-panel)** | Layar kerja utama STEP 11: panel finansial+rasio, grid transkripsi SLIK/BI, checklist dokumen granular, APPI, rekening+mutasi+rekap 3 bulan, viewer dokumen intake, narasi, form rekomendasi (Save / Submit RFA). | `Views/Acquisition/CreditAnalyst/Index.cshtml:1-2995` | `62-credit-analysis-screens §3a` MOTOR-S2, §4-5 |
| **SCR-CA-03 — Bureau Dashboard (SLIK checking)** | Tampilan **riwayat kolektibilitas per bulan** (grid per-bank ≤24 bulan, tier OJK 1–5, fallback Pefindo transparan) + **FCL/SLIK viewer read-only multi-tab** per pihak (applicant/spouse/guarantor/reference) per view (header/summary/history/detail) + panel Dukcapil read-only + export SLIK. Ini padanan target label assignment "CheckingSlikDashboard" — mapping [OPEN] OQ-CAUI-01. | `LookupFCL.cshtml`, `FCLResultController` (`sp_get_fcl_result_*`), `PartialHistoryBI.cshtml` | `62-credit-analysis-screens §5.4`, §11; `slik-ojk.md §6`; BE-02 §4/§5.6 |
| **SCR-CA-04 — NeoScore Result View** | Render **terstruktur** hasil NeoScore (total score, recommendation, factor validity, detail per kategori) — pengganti popup raw-HTML legacy `[ARTIFACT]` (do-not-replicate). | `NeoScore.cshtml`/`NeoScoreCar.cshtml` (`@Html.Raw` — EC-1) | `62-credit-analysis-screens §9 EC-1`; `neoscore.md §6`; BE-02 §5.7 |
| **SCR-CA-05 — CA Record View (read-only)** | Seluruh panel SCR-CA-02 read-only + risk-tier badge + status rekomendasi + print laporan CA; target deep-link konsumsi 03 (komite membaca data CA). | `Views/Acquisition/CreditAnalyst/View.cshtml:1-3190` | `62-credit-analysis-screens §11` |
| **SCR-CA-06 — Preliminary 5C Entry (produk mobil) — PROVISIONAL** | Capture 5C free-text (Capacity/Capital/Character/Condition/Collateral) + CMO additional analysis + upload dokumen foto oleh CMO. **PROVISIONAL**: framing "kanal per-produk vs langkah sekuensial" = [OPEN] OQ-CRSCORE-10 (P1); kontrak write BE = GAP-FE02-03. | `Views/Acquisition/CA/CACar.cshtml:1-730`, `CACarController.cs:69-167` | `62-credit-analysis-screens §3a` CAR-S1; BE-02 §2 catatan OQ-CRSCORE-10 |
| **SCR-CA-07 — SLIK Direct-Check Requests (microflow terpisah)** | List + create request pengecekan registri langsung + aksi approval microflow (approve/forward/correct/reject) — lifecycle sendiri, terpisah dari record analisis. | shape `tr_slik_request` (read via `sp_get_slik_request_list`); layar create legacy tidak ter-ekstrak ([OPEN] write path — `slik-ojk.md §5`) | `slik-ojk.md §3,§5`; BE-02 §4 `/slik-requests*`, §7.3 |

Dialog/komponen milik modul (padanan legacy → target; bentuk komponen Next.js = USULAN): **DocumentViewerDialog** (← `LookupViewDokumen.cshtml`, 6 kategori foto intake; perbaiki dead empty-state `>= 0` — EC-4 `62-credit-analysis-screens §9`), **SlikHistoryRowForm** (baris grid transkripsi), **KolektibilitasGrid** (bulan × bank, tier badge 1–5), **AdvisoryPanel** (DSR/freshness), **RiskTierBadge**, **NeoScorePanel**, **StatusBadge**.

### 1.2 BUKAN milik modul ini (non-goal)

| BUKAN dimiliki | Pemilik | Catatan |
|---|---|---|
| **Aksi keputusan komite** (Approve/Reject/Correction bar, walk hierarki, inbox) — STEP 12 | FE-03 approval-inbox (KB `63-approval-inbox-screens.md`) | Legacy menaruh action bar hierarki di halaman CA yang sama (`Approval.cshtml`, `CACar Flag=Approval`) = **layout artifact**; rebuild MEMISAHKAN display CA (SCR-CA-05) dari UI aksi hierarki (`62-credit-analysis-screens §5.10` [ARTIFACT]). FE-02 hanya menyediakan deep-link data CA read-only. |
| **Rapindo check + gate Approve final-level** | FE-03 / domain kolateral (`31-collateral-bpkb-fidusia`) | Gate fraud [LOCKED] tapi melekat pada aksi approve komite, bukan layar analis (BE-02 §1 "bukan milik"; [OPEN] OQ-CAUI-02/04). `LookupRapindo.cshtml` tidak direplika di FE-02. |
| **Trigger/render PO** dari layar approval CA | FE-04 / BE-04 (STEP 13) | Bug legacy `CreditAnalystController.cs:1166-1266` render PO sinkron — **JANGAN direplika** (BE-02 BR-02-25; D-01 Step 13). |
| **Panel Vertel reason** yang numpang di `Index.cshtml` legacy | modul FE Vertel/verification (STEP 14, D-02) | Cross-ref `62-credit-analysis-screens §11` "(x-ref 65)". |
| **Call vendor NeoScore dari browser/FE tier** | BE-02 (target call BE-owned via ACL — USULAN BE-02 §8) | Legacy FE-tier POST plain HTTP + PII + `@Html.Raw` = `[ARTIFACT]` do-not-replicate (`62-credit-analysis-screens §9 EC-1`; BE BR-02-20). FE hanya memanggil endpoint BE. |
| **Komputasi rasio (DSR/LTV/DP%/DP-net%) & skor** | BE-02 | FE display-only (BE BR-02-32; `62-credit-analysis-screens` BR-CAUI-1 `[OPEN]` formula — FE tidak boleh memilih formula). |
| **App shell, login, navigasi, session/branch context** | FE-00 OVERVIEW (KB `60-app-shell-auth-navigation.md`) | Identitas analis & branch dari session shell; jangan duplikasi guard (3 guard rusak legacy — `60-app-shell §9` Edge Case 3). |
| **Shared components** (DataTable, LookupDialog, ConfirmDialog, CurrencyField, DateField, Toast/Alert, busy-button) | FE-00 (KB `67-client-side-behavior.md`) | FE-02 mengkonsumsi; §10. |
| **Layar intake & upload dokumen aplikasi** | FE-01 intake-cas | FE-02 hanya viewer read-only (`GET /applications/{id}/documents`). |
| **Master data (bank, profession, reason, dsb.)** | modul master-data (D-08; KB `66-master-data-screens.md`) | FE-02 mengkonsumsi lookup-nya (sumber options — lihat GAP-FE02-01). |

### 1.3 Reengineering mandate FE (bukan mirror legacy)

- **Satu workstation config-driven, bukan dua codebase layar**: variasi motor vs mobil dimodelkan sebagai parameterisasi per product matrix (D-07; BE BR-02-33); matriks final [OPEN] OQ-MEET-06 — sampai diputus, SCR-CA-06 berstatus PROVISIONAL dan tidak menghalangi SCR-CA-01..05.
- **FE tidak menghitung angka keputusan**: DSR/LTV/DP%/DP-net% dan skor dibaca dari field API BE (BE BR-02-32/AC-16); menghapus 4 komputasi DSR paralel legacy (3 server + 1 client yang justru dirender — BR-CAUI-1 `[OPEN]`, EC-2 `62-credit-analysis-screens §9`).
- **Guard client-side = UX preventif, BUKAN enforcement**: prasyarat Save/RFA otoritatif di BE (BE BR-02-28, §5.3); FE me-mirror agar user tidak membentur error (perbaiki pola client-side-only legacy `CreditAnalyst.js:118-159` — BR-CAUI-3 [INTENT] server-side).
- **NeoScore terstruktur**: tidak ada HTML vendor yang di-inject; tidak ada PII transit browser→vendor (EC-1 `[ARTIFACT]`).
- **Do-not-replicate FE**: gating tombol via substring label ("Approve"/"Review"/"Reject" pada teks option — BR-CAUI-4 `[ARTIFACT]`; ganti `reason_type` code, milik 03), silent AJAX failure (EC-5 `62-credit-analysis-screens`; BR-CSB-10 `[ARTIFACT]`), dead empty-state viewer dokumen (EC-4), free-text "Link Slik" tanpa validasi (`[ARTIFACT]` — `62-credit-analysis-screens §4`), popup window raw-HTML score (EC-1).
- **Super-user dihapus** (D-09 `[LOCKED]`): tidak ada affordance bypass scoping/approval dalam bentuk apa pun (BE BR-02-36).

---

## 2. Aktor & Peran (akses per screen, role-gating)

Role census cabang target-state (D-10 `[LOCKED]`): **CMO, Marketing Head, Credit Analyst, Kepala Cabang, Credit (Admin)**. Legacy TIDAK punya RBAC client-side (menu per-employee — `60-app-shell` BR-SHELL-4; `neoscore.md §7`); role eksplisit = desain target, enforcement identitas per endpoint = [OPEN] OQ-MCP-01 (BE-02 §2). Mekanisme claim role/token menunggu arsitektur ITEC (D-11).

> **[OPEN] OQ-CRSCORE-10 (P1)**: apakah jalur CMO 5C (mobil) dan analyst workstation (motor) adalah **kanal alternatif per lini produk** atau **langkah sekuensial satu lifecycle** — belum final. Matrix di bawah memakai framing per-produk secara **provisional** (bukti FE: dua layar independen — `62-credit-analysis-screens §1`); JANGAN hardcode asumsi ini di routing/guard — gunakan konfigurasi.

| Aktor | SCR-CA-01 Worklist | SCR-CA-02 Workstation | SCR-CA-03 Bureau | SCR-CA-04 NeoScore | SCR-CA-05 View | SCR-CA-06 5C (prov.) | SCR-CA-07 SLIK Req |
|---|---|---|---|---|---|---|---|
| **Credit Analyst** | **Ya** — hanya item branch sendiri + assigned dirinya (outcome scoping [INTENT] — BE BR-02-36/AC-19) | **Full**: edit + Save + Submit RFA | Ya (read) | Ya (view + trigger score) | Ya | — | Requester (bila dept-nya requester) |
| **CMO** | — ([OPEN] akses read — GAP-FE02-06) | — | — | Ya (view — layar mobil legacy menampilkan skor ke preparer) | Ya (read) | **Full** (isi 5C + upload foto) — provisional | Requester |
| **Marketing Head / Kepala Cabang / Credit (Admin)** | [OPEN] — matrix menu per role belum diputus (GAP-FE02-06) | — | — | — | Ya (read, bila diberi akses) | — | Approver microflow (routing dari department requester — BE BR-02-18) |
| **Reviewer hierarki (via FE-03)** | — | — | Ya (read, deep-link) | Ya (read) | **Ya** (deep-link dari inbox 03) | — | — |

Aturan gating FE:
- **Worklist scoping WAJIB dari server**: FE mengirim identitas session, BE memfilter branch+analyst (`GET /credit-analysis/worklist` — BE-02 §4 ⊕); FE TIDAK menambahkan opsi "lihat semua" (D-09; BE AC-19).
- **Editability SCR-CA-02 status-driven**: hanya saat `CREDIT_ANALYSIS.status ∈ {queued, under_review}` (BE §7.2); setelah `recommended` seluruh panel read-only (SCR-CA-05) sampai correction return.
- **Identitas `acting_employee`** dari session shell FE-00 (`60-app-shell §6`), tidak pernah di-input manual.
- Identitas role "CA approver" (`IsApprover`/`IsLastApprover` legacy mobil) belum ter-resolve ([OPEN] OQ-CAUI-02) — affordance keputusan TIDAK dirender di FE-02 (milik FE-03).

---

## 3. Peta Screen & Route (inventori + usulan route Next.js)

Prefix route group acquisition mengikuti konvensi FE-00. Seluruh route = **USULAN** (App Router).

| Screen | Route (USULAN) | Jenis render | Guard akses |
|---|---|---|---|
| SCR-CA-01 Worklist | `/acquisition/credit-analysis` | List server-side pagination (`GET /credit-analysis/worklist`) | Credit Analyst |
| SCR-CA-02 Workstation | `/acquisition/credit-analysis/[id]` | Multi-panel form client-heavy; editable status-driven | Credit Analyst (assigned) |
| SCR-CA-03 Bureau Dashboard | `/acquisition/credit-analysis/[id]/bureau` | Read-only; tab per pihak + per view; sub-panel kolektibilitas & Pefindo & Dukcapil | Credit Analyst; reviewer 03 (read) |
| SCR-CA-04 NeoScore | `/acquisition/credit-analysis/[id]/neoscore` | Read-only panel terstruktur (pengganti popup window legacy) | Credit Analyst; CMO (read) |
| SCR-CA-05 View (read-only) | `/acquisition/credit-analysis/[id]` (mode read-only status-driven — bukan URL terpisah) | Detail read-only + print | Semua role modul + deep-link FE-03 |
| SCR-CA-06 5C Entry (provisional) | `/acquisition/credit-analysis/[id]/preliminary` — **jangan di-build sebelum OQ-CRSCORE-10/OQ-MEET-06 diputus** | Form 5C + upload | CMO |
| SCR-CA-07 SLIK Direct-Check | `/acquisition/slik-requests` (+ `/new`, `/[id]`) | List + form + aksi approval microflow | Requester dept; SLIK approver |

Navigasi & deep-link:
- Entry point analis: menu modul → worklist → klik baris → workstation. Baris worklist hanya item RAC-approved (queue CA per resolusi PDF v2 STEP 10 "Approved → CA queue" — GT `:34-38`); item RAC `pending`/`rejected` tampil dengan StatusBadge non-actionable ([OPEN] OQ-CRSCORE-06 utk mekanisme blocking; FE default: baris rejected tidak bisa dibuka utk entry).
- Workstation ↔ Bureau ↔ NeoScore = tab/sub-route dalam satu konteks `[id]` (breadcrumb konsisten); mode read-only vs editable ditentukan status record, **bukan varian URL** (menghindari URL yang memaksa mode — pola FE-05 §3).
- Reviewer 03 masuk via deep-link inbox (FE-03) → `/acquisition/credit-analysis/[id]` (render read-only).
- Worklist mobil legacy TIDAK ada (layar dicapai via session `credit_id` dari CMCar — [OPEN] OQ-CAUI-03); target: satu worklist utk semua produk (config-driven §1.3) — USULAN.

---

## 4. Komposisi Layar & Komponen

Referensi shared components FE-00 (KB `67-client-side-behavior.md`): DataTable satu grid contract dgn pembedaan empty-vs-fetch-failed (BR-CSB-11), LookupDialog tunggal (BR-CSB-13 — legacy CA justru contoh terburuk: `CALookup.js` memakai container ID default copy-paste, EC-11), ConfirmDialog dua-tombol (BR-CSB-18/19), CurrencyField formatter id-ID tunggal (BR-CSB-5/6), DateField picker konsisten (BR-CSB-7), Toast/Alert envelope-aware (BR-CSB-9), busy-button anti double-submit (BR-CSB-20). Jangan duplikasi di modul.

### 4.1 SCR-CA-01 — CA Worklist

- **Layout**: page header → search bar (`search_term` = nama customer) → DataTable → pagination. Mobile: card list per baris (USULAN; NFR responsive).
- **Kolom** (USULAN — field census response = GAP-FE02-04): credit_id/application_id, nama customer, produk, tanggal masuk queue, **StatusBadge** RAC/CA, aksi (Buka).
- **Scoping**: branch + analyst dari server (§2). Tidak ada filter "semua cabang".
- **Komponen**: DataTable (FE-00), StatusBadge (§7).

### 4.2 SCR-CA-02 — CA Workstation (entry multi-panel)

Satu halaman panel berurutan dengan anchor nav (bukan wizard — staging lintas-screen di §6); panel sesuai data dependency legacy (`62-credit-analysis-screens §5` item 2, `[ARTIFACT]` shape "load everything in one call" — target: fetch per-panel):

1. **Panel A — Ringkasan Aplikasi & Status Gate**: data intake read-only (customer, produk, plafon — sumber 01); **banner status RAC** (`GET /applications/{id}/rac-screening`): `pending` first-class (D-01 Step 8; BE BR-02-02), `rejected` + reject_detail messages, `approved`; **RiskTierBadge** + indikator blacklist-override (`GET /applications/{id}/risk-category`).
2. **Panel B — Finansial & Rasio**: income Utama/Tambahan/Pasangan + klasifikasi sumber + verdict validasi per sumber + MRP (§5.1); **rasio display-only dari BE** (dsr, ltv, dp_pct, dp_net_pct — BE BR-02-32); AdvisoryPanel inline (DSR>40% advisory — BE BR-02-13).
3. **Panel C — SLIK Checking**: **KolektibilitasGrid** ringkas (embed dari SCR-CA-03) + **grid transkripsi History BI/OJK** (add/delete row — §5.2) + tombol "Buka Bureau Dashboard" (ganti free-text "Link Slik" `[ARTIFACT]`). Catatan rekonsiliasi grid-vs-FCL-viewer = [OPEN] BR-02-30 — FE menampilkan keduanya berdampingan TANPA auto-sinkron sampai kebijakan diputus.
4. **Panel D — Checklist Dokumen Granular** (§5.3): tabel per item × 2 kanal (Dukcapil vs mobile-app), row set by `customer_type` P/C; tombol **DocumentViewerDialog** (lihat upload intake — 6 kategori foto, `GET /applications/{id}/documents`).
5. **Panel E — APPI Facility Status** (§5.4): verdict clear/blacklist + nominal per pihak.
6. **Panel F — Rekening Bank & Mutasi** (§5.5): detail rekening + entri mutasi bulanan + rekap 3 bulan (padanan `PartialRekening`).
7. **Panel G — Dukcapil (read-only)**: hasil match civil-registry informational, **tanpa gate** (BE BR-02-24).
8. **Panel H — NeoScore**: skor ringkas + tombol "Lihat Skor" → SCR-CA-04; trigger `POST /applications/{id}/neoscore/score` (idempoten — cek prior result; BE §4 ⊕).
9. **Panel I — Narasi & Rekomendasi** (§5.6): narasi positif/negatif, recommendation Recommended/Not Recommended, justifikasi, debtor_group, ojk_economic_sector.
10. **Action bar** (sticky bottom di mobile): Batal · **Simpan** (`PATCH /credit-analysis/{id}`) · **Submit Rekomendasi (RFA)** (`POST /credit-analysis/{id}/recommendation`; ConfirmDialog dua-tombol — BR-CSB-18; padanan `confirm()` legacy BR-CAUI-3).

### 4.3 SCR-CA-03 — Bureau Dashboard (SLIK checking + kolektibilitas per bulan)

- **Header**: identitas aplikasi + **badge `source_tier`** (slik_primary / slik_mirror / pefindo — fallback transparan tapi WAJIB terlihat sumbernya; BE §5.6; tier CLKNAE* [OPEN] OQ-SLIK-01).
- **Sub-panel 1 — KolektibilitasGrid**: baris = bank pelapor, kolom = bulan (≤24, `year_month`), sel = tier kolektibilitas 1–5 + `days_overdue` (tooltip/detail) — bentuk data `slik-ojk.md §6` (`TahunBulanNN`/`Kol`/`Ht`) via `GET /applications/{id}/bureau/collectibility`. Skala tier `[LOCKED]` OJK (BE BR-02-06) — FE hanya color-coding (USULAN: 1 hijau → 5 merah), TIDAK memetakan ulang days→tier. Scroll horizontal dalam container sendiri (mobile).
- **Sub-panel 2 — FCL/SLIK Viewer**: tab pihak `applicant|spouse|guarantor|reference` × view `header|summary|history|detail` (`GET /applications/{id}/fcl-result?party=&view=` — BE §4 ⊕; padanan `LookupFCL`/`FCLResultController`).
- **Sub-panel 3 — Pefindo**: lookup asset/akad standalone (`GET /applications/{id}/bureau/pefindo`).
- **Sub-panel 4 — Dukcapil**: hasil match read-only (`GET /applications/{id}/dukcapil-result`).
- **Aksi**: **Export SLIK** (Excel, `GET /credit-analysis/{id}/slik-export` — artefak regulator-facing; busy-state per tombol, kegagalan tidak silent).

### 4.4 SCR-CA-04 — NeoScore Result View

- Render **payload terstruktur** BE §5.7: `total_score` (angka besar + gauge USULAN), `recommendation` (badge PASS/…; vocabulary final = [OPEN] residual OQ-NEOSCORE-01), tabel `factor_validity` (factor × valid), panel detail `phone_verification_detail`, `health_insurance_enrollment_detail`, `fintech_loan_history_detail`, `ewallet_topup_detail`.
- **DILARANG** inject HTML vendor (`@Html.Raw` legacy = EC-1 `[ARTIFACT]`); **DILARANG** call vendor dari browser.
- Tampilkan timestamp hasil + indikator "hasil tersimpan sebelumnya vs fresh" (kebijakan caching motor-vs-mobil [OPEN] OQ-CAUI-06/EC-7 — jangan diputuskan diam-diam; FE menampilkan apa adanya dari BE).

### 4.5 SCR-CA-05 — CA Record View (read-only)

- Seluruh panel 4.2 read-only (konsistensi mental model), + status rekomendasi + advisories terakhir (`GET /credit-analysis/{id}/recommendation/advisories`) + RiskTierBadge.
- **Print laporan CA** per customer-type `Perseorangan`/`BadanUsaha` (`GET /credit-analysis/{id}/print`) — konten artefak regulator-facing, fidelity field `[LOCKED]` pending mapping OQ-CAUI-01.
- **TIDAK ada** tombol keputusan komite/PO di layar ini (§1.2).

### 4.6 SCR-CA-06 — Preliminary 5C Entry (provisional)

- Form 5 textarea 5C + CMO additional analysis + grid upload foto dokumen (tipe file `.jpg/.jpeg/.png/.pdf` + max size ter-konfigurasi — BE BR-02-34) + Save Draft/RFA. Kewajiban isi 5C = [OPEN] BR-02-35/BR-CAUI-10 — default FE: TIDAK memblokir kosong sampai diputus, tampilkan warning advisory.
- Setiap save path WAJIB punya feedback kegagalan (legacy `CACar_PhotoDetail.js:608-693` silent — EC-5 `[ARTIFACT]`).

### 4.7 SCR-CA-07 — SLIK Direct-Check Requests

- List paginated (`GET /slik-requests`) + StatusBadge lifecycle `submitted → forwarded → approved / corrected → submitted / rejected` (BE §7.3) → form create (§5.7) → aksi approval (approve/forward/correct/reject via `POST /slik-requests/{id}/approval`; ConfirmDialog + reason).

---

## 5. Field & Validasi (census per form)

> Kolom **Marker** mengikuti KB: `[LOCKED]` = wajib verbatim; `[INTENT]` = outcome dipertahankan, bentuk bebas; `[ARTIFACT]` = legacy dibuang/diganti; **USULAN** = desain baru FE. Validasi FE = preventif; otoritatif di BE (BE-02 §5/§6). Field uang = CurrencyField id-ID (BR-CSB-5/6); field tanggal = DateField picker aktif (jangan tiru enforcement setengah-jalan BR-CSB-7); conditional-required = shared primitive (BR-CSB-2).

### 5.1 Panel Finansial & Rasio (SCR-CA-02 Panel B) → `PATCH /credit-analysis/{id}` (BE-02 §4)

| # | Field (target) | Label (ID) | Tipe input | Required | Format & validasi FE (preventif) | Sumber options/nilai | Marker | Sumber |
|---|---|---|---|---|---|---|---|---|
| 1 | `primary_income` | Pendapatan Utama | CurrencyField | **Ya** | **> 0** sebelum Save (mirror guard server BE BR-02-28; legacy dua sisi `CreditAnalystController.cs:897-1006` + `CreditAnalyst.js:215-224`) | manual | [INTENT] BR-CAUI-2 | `62-credit-analysis-screens §7` BR-CAUI-2 |
| 2 | `additional_income` | Pendapatan Tambahan | CurrencyField | **Ya (non-null)** | boleh 0, tidak boleh kosong (BE BR-02-28) | manual | [INTENT] | BR-CAUI-2 |
| 3 | `spouse_income` | Pendapatan Pasangan | CurrencyField | **Conditional** — tampil bila status menikah | inklusi ke DSR = **[OPEN] OQ-CAUI-07** — FE hanya input & display, TIDAK ikut menghitung | manual | [INTENT]; formula [OPEN] | BR-CAUI-1; BE BR-02-28 |
| 4 | `income_source_classification` | Sumber Penghasilan | select (employment vs business) | Ya | satu pilihan | master ([OPEN] sumber options — GAP-FE02-01) | [INTENT] | `62-credit-analysis-screens §4` (income sections) |
| 5 | `income_validation_verdict[]` | Hasil Validasi Penghasilan (per sumber) | select per sumber | Tidak | verdict per source-of-income | vocabulary [OPEN] (satu keluarga dgn OQ-CRSCORE-08) | [INTENT] | `62-credit-analysis-screens §4` |
| 6 | `mrp` | MRP (Market Resale Price) | CurrencyField | **Ya** | **non-zero** sebelum Save (BE BR-02-28; basis DP-net) | manual | [INTENT] BR-CAUI-2 | `CreditAnalystController.cs:944-952` |
| 7 | `dsr` / `ltv` / `dp_pct` / `dp_net_pct` | DSR / LTV / DP% / DP-Net% | **display-only** (read-only stat) | — | dari `GET /applications/{id}/credit-analysis` — **FE DILARANG hitung ulang** (satu sumber angka; guard div-zero di BE BR-02-10); DSR>40% → AdvisoryPanel kuning (advisory, BUKAN hard-block — [OPEN] OQ-CRSCORE-02) | field API BE | [INTENT] BE BR-02-32 (USULAN BE diterima FE) | BE-02 §4 `GET .../credit-analysis`; BR-CAUI-1 `[OPEN]` |

### 5.2 Grid Transkripsi History SLIK/BI (SCR-CA-02 Panel C) → `POST /credit-analysis/{id}/slik-history-rows` (BE-02 §5.8)

Field set baris **[LOCKED]** (artefak bureau-history relevan regulasi — `62-credit-analysis-screens §4` `Index.cshtml:2676-2770`); UI transkripsi bebas didesain.

| # | Field | Label (ID) | Tipe input | Required | Format & validasi FE | Sumber options | Marker | Sumber |
|---|---|---|---|---|---|---|---|---|
| 1 | `relation` | Relasi | select | Ya | satu pilihan (applicant/spouse/guarantor/reference — set final ikut kontrak BE §5.8 contoh `"applicant"`) | enum BE | **[LOCKED]** (field) | `62-...screens §4`; BE §5.8 |
| 2 | `facility_name` | Nama Fasilitas | text | Ya | non-empty | manual | **[LOCKED]** | idem |
| 3 | `bank` | Bank | text/lookup | Ya | non-empty; lookup master bank = USULAN (GAP-FE02-01) | manual/master | **[LOCKED]** | idem |
| 4 | `facility_type` | Jenis Fasilitas | select | Ya | satu pilihan (`installment`, dst. — vocabulary ikut BE) | enum BE ([OPEN] set penuh) | **[LOCKED]** | idem |
| 5 | `plafon` | Plafon | CurrencyField | Ya | ≥ 0 | manual | **[LOCKED]** | idem |
| 6 | `outstanding_balance` | Outstanding | CurrencyField | Ya | ≥ 0 | manual | **[LOCKED]** | idem |
| 7 | `kol_status` | Kol (bulan berjalan) | select 1–5 | Ya | integer 1..5 — skala OJK `[LOCKED]` (BE BR-02-06); FE tidak menerima nilai lain | enum 1–5 | **[LOCKED]** | idem; BE §5.8 |
| 8 | `kol_max` | Kol Maks | select 1–5 | Ya | integer 1..5 | enum 1–5 | **[LOCKED]** | idem |
| 9 | `lifecycle_status` | Status Fasilitas | select | Ya | `active`/… (set final ikut BE) | enum BE ([OPEN]) | **[LOCKED]** | idem |

Aturan grid: tambah baris = `POST` (201 → append; `row_id` dari response); hapus = `DELETE /credit-analysis/{id}/slik-history-rows/{rowId}` + ConfirmDialog — **audit trail dipertahankan di BE** (BE AC-20), FE menampilkan konfirmasi bahwa penghapusan ter-audit. Field "Link Slik" free-text legacy (`Index.cshtml:2605-2607`) **DIBUANG** `[ARTIFACT]` — diganti navigasi terstruktur ke SCR-CA-03.

### 5.3 Checklist Dokumen Granular (SCR-CA-02 Panel D) → `PUT /credit-analysis/{id}/document-checklist` (BE-02 §4)

Struktur **[LOCKED]** (KYC/AML control record — BR-CAUI-8; BE BR-02-29): row set keyed **`customer_type` P (individual) vs C (corporate)**; per item **dua kanal verifikasi independen** dengan verdict terpisah.

| # | Field per item per kanal | Label (ID) | Tipe input | Required | Nilai (kandidat enum — konfirmasi **OQ-CRSCORE-08**) | Marker | Sumber |
|---|---|---|---|---|---|---|---|
| 1 | `availability` (kanal Dukcapil/civil-registry) | Keterangan (Dukcapil) | select | per row (guard blank-submit legacy TIDAK ditemukan — [OPEN]; FE default: wajib terisi sebelum RFA = USULAN memperketat) | `Terlampir` \| `Belum Ada` \| `Tidak Ada` | **[LOCKED]** granularity / [OPEN] vocab | `62-...screens §5.5`, BR-CAUI-8; `Index.cshtml:1355-2484` |
| 2 | `result` (kanal Dukcapil) | Hasil (Dukcapil) | select | idem | `Valid` \| `Tidak Valid` \| `Tidak Ada` | idem | idem |
| 3 | `availability` (kanal mobile-app "Playstore") | Keterangan (Aplikasi) | select | idem | idem #1 | idem | idem |
| 4 | `result` (kanal mobile-app) | Hasil (Aplikasi) | select | idem | idem #2 | idem | idem |
| 5 | `surveyor_selfie_verified` (khusus dokumen survey) | Selfie Verifikasi Surveyor | select/checkbox | khusus row survey | ada/tidak | **[LOCKED]** (bagian kontrol survey) | `62-...screens §5.5` |

Item set (row):
- **Individual (P)**: item 1 "KTP Pemohon dan Pasangan", item 2 "KTP Penjamin", … **gap item 3–8** (penomoran legacy loncat 1,2,9,10 — EC-3) = **[OPEN] OQ-CAUI-05**: retired sengaja vs cek KYC hilang — **JANGAN ditebak**; FE merender item set dari **response BE** (data-driven), bukan hardcode.
- **Corporate (C)**: dokumen incorporation, tax, ownership, dst. (~40 field total `TrCaDocuments` — BE-02 §1 item 5).
- Sumber daftar item = kontrak `PUT`/`GET` document-checklist BE (field census response belum dirinci → GAP-FE02-04).

### 5.4 APPI Facility Status (SCR-CA-02 Panel E) → endpoint write **belum eksplisit di BE-02 §4 → GAP-FE02-02**

| # | Field | Label (ID) | Tipe input | Required | Validasi FE | Marker | Sumber |
|---|---|---|---|---|---|---|---|
| 1 | `appi_status` per pihak (applicant/spouse/guarantor) | Status APPI | select `clear` \| `blacklist` | Ya per pihak | satu pilihan; verdict **[LOCKED]** (compliance control point — BE BR-02-31) | **[LOCKED]** (verdict) / [OPEN] (ekspansi "APPI") | `62-...screens §4` `Index.cshtml:2489-2589` |
| 2 | `appi_nominal` per pihak | Nominal | CurrencyField | Tidak | ≥ 0 | [INTENT] | idem |

### 5.5 Rekening Bank & Mutasi (SCR-CA-02 Panel F) → `PUT /credit-analysis/{id}/bank-accounts` (BE-02 §4)

| # | Field | Label (ID) | Tipe input | Required | Catatan | Marker | Sumber |
|---|---|---|---|---|---|---|---|
| 1 | identifikasi rekening (bank, nomor, atas nama) | Rekening | text/lookup | Ya (per rekening) | detail field set payload belum dirinci BE §5 → **GAP-FE02-04** | [INTENT] | `21-credit-analysis-scoring.md §5.9` ("bank account identification…") |
| 2 | `mutation_entries[]` (per bulan: periode, nilai) | Entri Mutasi Bulanan | repeat-rows (DateField bulan + CurrencyField) | Tidak | di-key **manual analis** (bukan auto-feed) | [INTENT] | idem; `62-...screens §11` `PartialRekening.cshtml` |
| 3 | rekap rekening 3 bulan | Rekap 3 Bulan | computed display / entry | — | padanan `PartialRekening`; sumber komputasi (BE vs entry) ikut kontrak BE — GAP-FE02-04 | [INTENT] | BE-02 §1 item 5 |

### 5.6 Form Narasi & Rekomendasi (SCR-CA-02 Panel I) → `POST /credit-analysis/{id}/recommendation` (BE-02 §5.3)

| # | Field | Label (ID) | Tipe input | Required | Format & validasi FE | Sumber options | Marker | Sumber |
|---|---|---|---|---|---|---|---|---|
| 1 | `recommendation` | Rekomendasi | segmented/radio `Recommended` \| `Not Recommended` | **Ya** | satu pilihan; vocabulary bisnis final PDF v2 STEP 11 (GT `:47`); mapping legacy **RESOLVED — OQ-CRSCORE-11** (BE-02 §3.1): motor `CaDecision` `Approve`/`Reject` dan mobil `CA_decision` `A`/`R` (verdict analis) → `recommended`/`not_recommended`; kode mobil `A/V/C/R dst.` yang semula diduga verdict ternyata milik `CA_status` = state workflow (→ kolom `status` target, §7 StatusBadge), BUKAN verdict — FE hanya merender enum target | enum BE §3 | [INTENT] | BE-02 §3.1 `recommendation` (OQ-CRSCORE-11 RESOLVED); `62-...screens §4` |
| 2 | `recommendation_justification` | Justifikasi | textarea | **Ya** | non-empty; max-length ikut BE (legacy `CreditAnalysis` maxlength 800 — referensi, final [OPEN]) | manual | [INTENT] | BE-02 §3; `Index.cshtml:2838-2849` |
| 3 | `positive_negative_narrative` | Aspek Positif / Negatif | textarea (2 kolom USULAN) | **Ya** (wajib di kontrak BE §5.3) | non-empty | manual | [INTENT] (`[ARTIFACT]` bentuk free-text legacy — bebas redesign) | BE-02 §5.3; `Index.cshtml:2795-2808` |
| 4 | `debtor_group` | Debtor Group | select | **Ya** | satu pilihan; di-assign **saat analisis**, bukan intake (BE BR-02-17) | master — endpoint lookup TIDAK ada di BE-02 §4 → **GAP-FE02-01** | [INTENT] | BE-02 §3, BR-02-17 |
| 5 | `ojk_economic_sector` | Sektor Ekonomi OJK | select (searchable USULAN) | **Ya** | nilai WAJIB match OJK code list — FE tidak boleh free-typing | master OJK code — **GAP-FE02-01** | **[LOCKED]** (value) | BE-02 §3 `[LOCKED]`, §5.3 |

**Guard Submit RFA (client-side preventif)** — tombol Submit disabled + daftar kekurangan inline bila #1–#5 belum terisi dan prasyarat Save §5.1 (#1,#2,#6) belum terpenuhi (mirror BE §5.3 "enforce server-side" BR-CAUI-3 [INTENT]); ConfirmDialog dua-tombol sebelum kirim. **Pemilihan approval-scheme manual legacy (`ApprovalSchemeID` lookup modal — `Index.cshtml:2854-2866`) TIDAK direplika**: routing komite target dinamis by `trans_type_id` + OP + risk level (D-01 Step 10, milik 03; komposisi dari BE-02 §5.4) dan kontrak BE §5.3 tidak memiliki field scheme — outcome BR-CAUI-3 ("routed into a named scheme") dipenuhi oleh routing dinamis; konfirmasi penghapusan picker = §11 CONF-FE02-01.

Response sukses menampilkan **AdvisoryPanel** dari `advisories[]` (`DSR_EXCEEDED`, `BUREAU_STALE` — non-blocking di STEP 11; BE BR-02-13/14; hard-block vs advisory [OPEN] OQ-CRSCORE-02 — FE merender sebagai warning, BUKAN error blocking, sampai diputus).

### 5.7 Form 5C Preliminary (SCR-CA-06, provisional) → kontrak write **GAP-FE02-03**

| # | Field | Label (ID) | Tipe input | Required | Validasi FE | Marker | Sumber |
|---|---|---|---|---|---|---|---|
| 1–5 | `capacity` / `capital` / `character` / `condition` / `collateral` | 5C | textarea ×5 | **[OPEN]** BR-02-35 — legacy submitable kosong (validasi ter-komentar `CA.js:13-45`); JANGAN diam-diam enforce ATAU biarkan — default FE: warning advisory | — | [INTENT] (data point) / [OPEN] (requiredness) | `62-...screens §7` BR-CAUI-10; `CACar.cshtml:189-205` |
| 6 | `cmo_additional_analysis` | Analisis Tambahan CMO | textarea | Tidak | — | [INTENT] | `62-...screens §3a` CAR-S1 |
| 7 | `photo_uploads[]` | Upload Dokumen Foto | file per row (set dari photo-type master) | per row bila file belum ada | tipe `.jpg/.jpeg/.png/.pdf` + max size ter-konfigurasi (enforce BE seragam — BE BR-02-34); FE gate blocking (bukan warning-only — jangan tiru BR-CSB-16) | [INTENT] | `62-...screens §3a` CAR-S1, BR-CAUI-9 |

### 5.8 Form Request SLIK Direct-Check (SCR-CA-07) → `POST /slik-requests` (BE-02 §4)

Shape field dari `tr_slik_request` (`slik-ojk.md §5` — [VERIFIED] shape / [OPEN] write path legacy); nama field target ikut kontrak BE (field census request belum dirinci di BE §5 → GAP-FE02-05):

| # | Field (legacy shape) | Label (ID) | Tipe input | Required | Catatan | Marker |
|---|---|---|---|---|---|---|
| 1 | `nik` | NIK | text | **Ya** | 16 digit (format NIK — `[LOCKED]` identitas; BE-02 §3 CUSTOMER) | **[LOCKED]** |
| 2 | `nama` | Nama | text | Ya | non-empty | [INTENT] |
| 3 | `nama_perusahaan` | Nama Perusahaan | text | Conditional — `jenis_debitur` corporate | required-toggle runtime (BR-CSB-2) | [INTENT] |
| 4 | `tempat_lahir` / `tanggal_lahir` | Tempat/Tgl Lahir | text + DateField | Ya (individual) | tanggal valid | [INTENT] |
| 5 | `alamat` | Alamat | textarea | Ya | non-empty | [INTENT] |
| 6 | `npwp` | NPWP | text | Tidak | format NPWP (`[LOCKED]` identitas pajak) | **[LOCKED]** (value) |
| 7 | `jenis_debitur` | Jenis Debitur | select individual/corporate | Ya | menggerakkan #3 | [INTENT] |
| 8 | `type_pengecekan` | Tipe Pengecekan | select | Ya | hanya `'1'` terobservasi legacy — set penuh [OPEN] | [INTENT]/[OPEN] |
| 9 | `document_form_persetujuan` | Form Persetujuan (dokumen) | file/ref | Ya | referensi dokumen persetujuan debitur (compliance) | [INTENT] |
| 10 | `reason` | Alasan Request | textarea | Ya | non-empty | [INTENT] |

Aksi approval (`POST /slik-requests/{id}/approval`): `action` approve/forward/correct/reject (enum BE §7.3) + reason — ConfirmDialog; routing approver dari department requester (BE BR-02-18) dirender sebagai info, bukan pilihan user.

### 5.9 Filter Worklist (SCR-CA-01) → `GET /credit-analysis/worklist`

| Field | Tipe | Default | Catatan | Sumber |
|---|---|---|---|---|
| `search_term` | text | — | pencarian nama customer (param BE §4 ⊕) | `CreditAnalystController.cs:266-315`; BE-02 §4 |
| `page` | pagination | 1 | server-side | BE-02 §4 |
| (implisit) branch + analyst | dari session | — | scoping server-side; FE tidak mengirim override (D-09) | BE BR-02-36/AC-19 |

---

## 6. Aturan Interaksi & Staging

### 6.1 Staging (dari KB §3a — outcome WAJIB dipertahankan)

Legacy = dua stage-set paralel per produk (`62-credit-analysis-screens §3a`: MOTOR-S1→S2→S3; CAR-S1→S2). Target: **satu lifecycle stage-set config-driven** (D-07) dengan stage approval dipindah ke FE-03 (§1.2); framing per-produk provisional [OPEN] OQ-CRSCORE-10.

| Stage target | Screen | Aktor | Transisi | Padanan legacy |
|---|---|---|---|---|
| **CA-T1 — Queue** | SCR-CA-01 | Credit Analyst | pilih record → CA-T2; hanya item RAC-approved actionable (PDF v2 STEP 10) | MOTOR-S1 (`CreditAnalystController.cs:266-315`) |
| **CA-T2 — Analysis entry (editable)** | SCR-CA-02 (+ CA-03/04 read) | Credit Analyst | **Simpan** → tetap CA-T2 (guard §5.1); **Submit RFA** → `POST /recommendation` → status `recommended` → CA-T3; buka record pertama kali: `POST /applications/{id}/credit-analysis` (application → `analyzing` — BE §7.2) | MOTOR-S2 (`Index.cshtml`; `CreditAnalyst.js:118-159`) |
| **CA-T2b — Preliminary 5C (provisional)** | SCR-CA-06 | CMO | Save Draft → tetap; RFA → handoff (kontrak GAP-FE02-03) | CAR-S1 (`CACarController.cs:69-167`) |
| **CA-T3 — Recommended (read-only)** | SCR-CA-05 | Analyst (read); reviewer 03 via deep-link | keputusan komite milik 03 (STEP 12); **correction return** → record kembali editable: non-base → CA-T2 (rework analis); base-tier → CA-T1 + re-screen RAC (BE §7.2 — dual return-target [OPEN], carry ke 03) | MOTOR-S3 / CAR-S2 (action bar hierarki TIDAK direplika di 02) |

### 6.2 Conditional rendering & disable/enable

| # | Trigger | Efek UI | Marker | Sumber |
|---|---|---|---|---|
| 1 | `rac_status = pending` | Banner "Menunggu keputusan RAC Bank Mega" (state first-class, BUKAN diasumsikan approved); panel entry non-actionable | [INTENT] (D-01 Step 8; BE BR-02-02) | BE-02 §7.1; `rac-bank-mega-risk-engine.md §10 EC-7` |
| 2 | `rac_status = rejected` | Banner merah + daftar `reject_detail[].message`; entry diblokir (mekanisme blocking final [OPEN] OQ-CRSCORE-06 — default FE fail-closed: tidak bisa dibuka utk entry) | [INTENT]/[OPEN] | BE-02 §5.2; GT `:38` "Rejected → stop" |
| 3 | `customer_type = 'P'` vs `'C'` | Row set checklist dokumen berbeda (data-driven dari BE, bukan hardcode — §5.3); label print `Perseorangan`/`BadanUsaha` | **[LOCKED]** (row set keying) | BR-CAUI-8; `Index.cshtml:1379-2467` |
| 4 | Status pernikahan = menikah | Field `spouse_income` tampil (§5.1 #3) | [INTENT] | BR-CAUI-1 (dependensi spouse-income) |
| 5 | Produk = mobil (per product matrix D-07) | Section/screen 5C provisional; komponen upload foto | [OPEN] OQ-MEET-06/OQ-CRSCORE-10 | `62-...screens §1, §3a`; BE BR-02-33 |
| 6 | `CREDIT_ANALYSIS.status = recommended` | Seluruh panel read-only; action bar hilang; AdvisoryPanel hasil submit tetap tampil | [INTENT] | BE-02 §7.2 |
| 7 | Save ditekan | Guard preventif: `primary_income > 0`, `additional_income` non-null, `mrp` non-zero → bila gagal, error field-level TANPA request; bila lolos → `PATCH`; `422` BE → map per-field | [INTENT] (BE BR-02-28) | BR-CAUI-2; BE AC-16 |
| 8 | Submit RFA ditekan | Guard §5.6 (5 field wajib) + ConfirmDialog → `POST /recommendation`; sukses → render `advisories[]` + transisi CA-T3 | [INTENT] (BR-CAUI-3 server-side) | BE-02 §5.3 |
| 9 | Blur/aksi "Hitung Skor Internal" | `POST /credit-analysis/{id}/scoring` (busy-button); hasil → refresh RiskTierBadge via `GET /applications/{id}/risk-category`; blacklist-override → badge `very_high` + indikator (BE BR-02-08 `[LOCKED]`) | [INTENT] | BE-02 §4, §5.4 |
| 10 | Klik "Lihat Skor" NeoScore | Cek prior result (`GET /neoscore-results/{credit_id}`); bila kosong/di-refresh → `POST /applications/{id}/neoscore/score`; render SCR-CA-04. Kebijakan re-check vs cache [OPEN] OQ-CAUI-06 — FE menampilkan pilihan hanya bila BE mengeksposnya | [INTENT]/[OPEN] | BE-02 §4 ⊕, §5.7; `62-...screens §9 EC-7/EC-8` |
| 11 | Tambah/hapus baris grid SLIK | `POST`/`DELETE` per baris + ConfirmDialog hapus (audit trail di BE — BE AC-20); optimistic update DILARANG utk hapus (tunggu 200) | [INTENT] | BE-02 §5.8 |
| 12 | Double-click submit apa pun | Busy-button + disabled selama request (BR-CSB-20) | [INTENT] | `67-client-side` item 19 |
| 13 | Semua aksi state-changing (Save besar, RFA, hapus baris, approval microflow) | ConfirmDialog dua-tombol vocabulary tunggal Bahasa Indonesia (BR-CSB-18/19) | [INTENT] | `67-client-side` item 17/18 |

---

## 7. State Tampilan

| State | Perilaku | Sumber |
|---|---|---|
| **Loading** | Skeleton per-panel (workstation multi-panel: tiap panel fetch mandiri + skeleton sendiri — mengganti "load everything in one call" legacy `[ARTIFACT]`); overlay hanya utk aksi mutasi | `62-...screens §5` item 2; `67-client-side` item 8 (BR-CSB-8) |
| **Empty vs fetch-failed** | WAJIB dibedakan per panel: "Data tidak tersedia" vs "Gagal memuat" + Retry (BR-CSB-11); khusus DocumentViewerDialog: kategori kosong → pesan empty-state eksplisit (perbaiki dead branch `>= 0` — EC-4 `[ARTIFACT]`) | `67-client-side` item 11; `62-...screens §9 EC-4` |
| **Error request** | Setiap kegagalan punya jalur feedback (toast/alert + `correlation_id` dari envelope `{code,message,details?,correlation_id}` BE-02 §5.1); DILARANG silent swallow (EC-5 CA-car `[ARTIFACT]`; BR-CSB-10) | `62-...screens §9 EC-5`; `67-client-side` item 10 |
| **Status-driven display (StatusBadge)** | RAC: `pending` → "Menunggu RAC" (abu/kuning), `approved` → "Lolos RAC", `rejected` → "Ditolak RAC" (+ detail); CA: `queued` → "Antri Analisis", `under_review` → "Sedang Dianalisis", `recommended` → "Rekomendasi Terkirim" (read-only) — satu label kanonik per status; vocabulary target final (disambiguasi 3 kolom status legacy `CA_status`/`CA_decision`/`approval` → satu kolom `status` = **RESOLVED — OQ-CRSCORE-11**, BE-02 §3.1) | BE-02 §7.1/§7.2; §3.1 (OQ-CRSCORE-11) |
| **KolektibilitasGrid** | Sel tier 1–5 color-coded (USULAN); `days_overdue` di tooltip; badge `source_tier` selalu tampil (slik_primary/mirror/pefindo); grid scroll horizontal dalam container (mobile) | BE-02 §5.6; `slik-ojk.md §6` |
| **AdvisoryPanel** | `DSR_EXCEEDED` / `BUREAU_STALE` dirender **warning non-blocking** (advisory STEP 11 — BE BR-02-13/14); copy menjelaskan "hard-gate ada di NPP STEP 15, bukan di sini" (tiga jendela 30-hari JANGAN dikonflasi — BE BR-02-14); bila OQ-CRSCORE-02 memutuskan hard-block, panel berubah blocking TANPA redesign | BE-02 §5.3, BR-02-13/14 |
| **NeoScore states** | loading (busy) / hasil terstruktur / gagal (error jelas + retry) / "hasil tersimpan [timestamp]" — TIDAK pernah raw HTML | BE-02 §5.7; `62-...screens §9 EC-1` |
| **RiskTierBadge** | `low/medium/high/very_high`; blacklist-override → `very_high` + ikon kunci/indikator override (BE BR-02-08 `[LOCKED]`) | BE-02 §5.4 |
| **Print/Export** | `GET /credit-analysis/{id}/print` (per customer-type) & `GET /credit-analysis/{id}/slik-export` → unduhan; busy per tombol; gagal → error jelas | BE-02 §4 ⊕; `67-client-side` item 12 |
| **Idempotent/stale guard** | Setelah RFA sukses, re-klik tidak mengirim ulang (busy + transisi CA-T3); refresh halaman merender status terkini dari `GET` | BE-02 §5.3 |
| **Responsive** | Semua screen operable mobile & desktop (NFR); workstation: anchor nav section; grid → scroll container; action bar sticky | Catatan rebuild (header) |

---

## 8. Kontrak Konsumsi API (per screen — konsisten BE-02 §4/§5)

Semua request memakai konvensi FE-00 (session/auth, error envelope `{code, message, details?, correlation_id}` — BE-02 §5.1). Transport REST/JSON diasumsikan; final menunggu arsitektur ITEC (D-11) — [OPEN] OQ-ARCH-STACK.

| Screen | Interaksi | Endpoint (BE-02 §4) | Catatan konsumsi |
|---|---|---|---|
| SCR-CA-01 | Muat worklist + search + paging | `GET /credit-analysis/worklist?search_term=&page=` | Scoped branch+analyst server-side (AC-19); field census response = GAP-FE02-04 |
| SCR-CA-02 Panel A | Status RAC | `GET /applications/{id}/rac-screening` (+ `/history` bila perlu konteks first-submission) | `pending` first-class; `reject_detail[].message` |
| SCR-CA-02 Panel A | Risk tier + komposisi | `GET /applications/{id}/risk-category` | Badge + blacklist_override_applied |
| SCR-CA-02 | Buka/inisiasi record | `POST /applications/{id}/credit-analysis` | application → `analyzing` |
| SCR-CA-02 | Muat record + rasio | `GET /applications/{id}/credit-analysis` | `dsr/ltv/dp_pct/dp_net_pct` display-only (BR-02-32/AC-16) |
| SCR-CA-02 | Simpan finansial/narasi/bureau-review | `PATCH /credit-analysis/{id}` | `422` → map per-field; guard preventif §6.2 #7 |
| SCR-CA-02 Panel C | Tambah/hapus baris SLIK | `POST /credit-analysis/{id}/slik-history-rows`; `DELETE .../slik-history-rows/{rowId}` | Field set [LOCKED] §5.2; hapus ter-audit (AC-20) |
| SCR-CA-02 Panel D | Tulis checklist dokumen | `PUT /credit-analysis/{id}/document-checklist` | Row set by customer_type P/C; verdict per kanal (AC-17) |
| SCR-CA-02 Panel D | Viewer dokumen intake | `GET /applications/{id}/documents` | 6 kategori foto; read-only (data milik 01) |
| SCR-CA-02 Panel F | Tulis rekening + mutasi + rekap | `PUT /credit-analysis/{id}/bank-accounts` | Payload census = GAP-FE02-04 |
| SCR-CA-02 Panel G / CA-03 | Dukcapil read-only | `GET /applications/{id}/dukcapil-result` | Informational, tanpa gate (BR-02-24) |
| SCR-CA-02/05 | Hitung skor internal | `POST /credit-analysis/{id}/scoring` | Refresh risk-category setelah sukses |
| SCR-CA-03 | Grid kolektibilitas per bulan | `GET /applications/{id}/bureau/collectibility` | ≤24 bulan per bank; `source_tier`; tier [LOCKED] (AC-6) |
| SCR-CA-03 | FCL viewer multi-tab | `GET /applications/{id}/fcl-result?party=applicant\|spouse\|guarantor\|reference&view=header\|summary\|history\|detail` | Read-only |
| SCR-CA-03 | Pefindo lookup | `GET /applications/{id}/bureau/pefindo` | Standalone |
| SCR-CA-03/05 | Export SLIK | `GET /credit-analysis/{id}/slik-export` | Regulator-facing; unduhan |
| SCR-CA-04 | Trigger + baca NeoScore | `POST /applications/{id}/neoscore/score` (BE-owned, USULAN BE §4 ⊕); `GET /neoscore-results/{credit_id}` (prior-result) | Render terstruktur §5.7 BE; TIDAK ada call vendor dari FE (AC-18) |
| SCR-CA-02 Panel I | Submit rekomendasi (RFA) | `POST /credit-analysis/{id}/recommendation` | Response `advisories[]` inline; emit `AnalysisComplete` (BE AC-12) |
| SCR-CA-05 | Baca advisories terakhir | `GET /credit-analysis/{id}/recommendation/advisories` | AdvisoryPanel read-only |
| SCR-CA-05 | Print laporan CA | `GET /credit-analysis/{id}/print` | Per customer-type; fidelity [LOCKED] pending OQ-CAUI-01 |
| SCR-CA-07 | List / create / approve request SLIK | `GET /slik-requests`; `POST /slik-requests`; `POST /slik-requests/{id}/approval` | Lifecycle §7.3 BE; field census = GAP-FE02-05 |

**Endpoint yang DIBUTUHKAN layar tetapi TIDAK ada di BE-02 §4** (jangan dikarang — dicatat sebagai GAP §11): lookup master options utk `debtor_group`, `ojk_economic_sector`, bank, `facility_type`, klasifikasi sumber penghasilan (GAP-FE02-01); write contract APPI facility status (GAP-FE02-02); write contract 5C + upload foto CA mobil (GAP-FE02-03); rincian field response worklist/checklist/bank-accounts (GAP-FE02-04); rincian payload `POST /slik-requests` (GAP-FE02-05).

---

## 9. Acceptance Criteria (Given/When/Then)

**AC-FE-1 — Worklist scoped, tanpa bypass**
- **Given** Credit Analyst cabang A login;
- **When** SCR-CA-01 dimuat;
- **Then** hanya item cabang A yang ter-assign dirinya tampil (dari server), dan TIDAK ada kontrol UI "lihat semua cabang/analis" (BE AC-19; D-09).

**AC-FE-2 — Rasio display-only dari BE**
- **Given** SCR-CA-02 Panel B termuat dgn `dsr=47`, `ltv`, `dp_pct`, `dp_net_pct` dari `GET /applications/{id}/credit-analysis`;
- **When** analis mengubah nilai income di form (belum Save);
- **Then** angka rasio TIDAK berubah secara lokal (tidak ada komputasi FE); rasio ter-update hanya setelah `PATCH` sukses + re-fetch (BE BR-02-32/AC-16; menghapus formula client legacy BR-CAUI-1).

**AC-FE-3 — Guard Save preventif**
- **Given** `primary_income = 0` ATAU `mrp = 0`;
- **When** analis menekan Simpan;
- **Then** submit diblok client-side dgn error field-level, TIDAK ada `PATCH` terkirim; bila guard FE ter-bypass, `422` BE dipetakan balik per-field (BE BR-02-28).

**AC-FE-4 — Grid transkripsi SLIK [LOCKED] + hapus ter-audit**
- **Given** analis menambah baris dgn 9 field §5.2 terisi lalu menghapusnya;
- **When** `POST` lalu `DELETE .../slik-history-rows/{rowId}`;
- **Then** payload `POST` memuat tepat field set kontrak BE §5.8; hapus meminta ConfirmDialog dan baris hilang hanya setelah `200` (audit trail di BE — BE AC-20).

**AC-FE-5 — Checklist per customer-type & per kanal**
- **Given** aplikasi `customer_type='C'`;
- **When** Panel D dirender;
- **Then** row set = set corporate **dari response BE** (bukan hardcode); tiap item menampilkan 4 verdict independen (availability+result × kanal Dukcapil/mobile-app) + selfie utk row survey; nilai select terbatas vocabulary ter-konfigurasi (OQ-CRSCORE-08; BE AC-17).

**AC-FE-6 — Kolektibilitas per bulan [LOCKED]**
- **Given** response `GET /applications/{id}/bureau/collectibility` berisi bank dgn 24 bulan, salah satu `{year_month:"2026-05", collectibility:3, days_overdue:95}`;
- **When** KolektibilitasGrid dirender;
- **Then** sel menampilkan tier `3` persis dari field API (FE tidak memetakan ulang days→tier — BE BR-02-06/AC-6) dan badge `source_tier` tampil.

**AC-FE-7 — NeoScore terstruktur, tanpa vendor call dari browser**
- **Given** analis menekan "Lihat Skor";
- **When** SCR-CA-04 dimuat;
- **Then** satu-satunya request FE menuju endpoint BE (`POST /applications/{id}/neoscore/score` / `GET /neoscore-results/{credit_id}`); tidak ada request ke host vendor; render dari payload terstruktur (total_score, recommendation, factor_validity, 4 panel detail) tanpa injeksi HTML (EC-1 `[ARTIFACT]`; BE AC-18).

**AC-FE-8 — RAC async first-class**
- **Given** `rac_status='pending'`;
- **When** SCR-CA-02 dibuka;
- **Then** banner "Menunggu RAC" tampil dan entry non-actionable; FE TIDAK menampilkan status seolah approved (BE BR-02-02/AC-2); saat `rejected`, `reject_detail[].message` dirender dan entry diblokir (default fail-closed; [OPEN] OQ-CRSCORE-06).

**AC-FE-9 — Submit rekomendasi + advisory non-blocking**
- **Given** form §5.6 lengkap dan DSR BE = 47%;
- **When** analis konfirmasi Submit RFA dan `POST /recommendation` sukses `200` dgn `advisories:[DSR_EXCEEDED, BUREAU_STALE]`;
- **Then** AdvisoryPanel warning tampil (bukan error), record berpindah read-only (CA-T3), dan tidak ada affordance keputusan komite di layar (milik FE-03; BE AC-10/AC-11/AC-12).

**AC-FE-10 — Guard RFA**
- **Given** `recommendation` belum dipilih ATAU `ojk_economic_sector` kosong;
- **When** analis menekan Submit RFA;
- **Then** submit diblok dgn daftar kekurangan inline; tidak ada request terkirim; `ojk_economic_sector` hanya dapat diisi dari option list (tidak free-typing — `[LOCKED]` value BE §5.3).

**AC-FE-11 — Tidak ada silent failure**
- **Given** `PUT /credit-analysis/{id}/document-checklist` gagal (500/timeout);
- **When** response/timeout diterima;
- **Then** error tampil (toast + `correlation_id` dapat disalin) dan state form tidak berpura-pura sukses (do-not-replicate EC-5 `62-...screens`; BR-CSB-10).

**AC-FE-12 — FCL viewer multi-tab**
- **Given** SCR-CA-03 dibuka;
- **When** user memilih pihak `spouse` dan view `history`;
- **Then** `GET /applications/{id}/fcl-result?party=spouse&view=history` terpanggil dan hasil dirender read-only; grid transkripsi (Panel C) TIDAK ter-update otomatis dari viewer (kebijakan rekonsiliasi [OPEN] BR-02-30 — tidak diputuskan diam-diam).

**AC-FE-13 — Microflow SLIK direct-check**
- **Given** requester submit form §5.8 lengkap;
- **When** `POST /slik-requests` sukses;
- **Then** record tampil di list dgn status `submitted`; approver melihat aksi approve/forward/correct/reject sesuai perannya; setiap aksi melalui ConfirmDialog + reason (BE §7.3).

**AC-FE-14 — Responsive**
- **Given** viewport mobile (≤ ~640px);
- **When** SCR-CA-02 dan SCR-CA-03 dibuka;
- **Then** semua panel operable (anchor nav, action bar sticky, KolektibilitasGrid scroll dalam container, tabel worklist menjadi card) — NFR responsive.

---

## 10. Dependency

| Dependency | Jenis | Yang dikonsumsi | Sumber |
|---|---|---|---|
| **BE-02-credit-analysis** | API | Seluruh endpoint §8; enum status §7.1–7.3; error envelope; field rasio kanonik; vocabulary verdict/enum | `BE-02 §4/§5/§7` |
| **FE-00 OVERVIEW (app shell & shared)** | FE | AppShell (session `acting_employee`, branch context, guard auth terpusat — jangan replikasi 3 guard rusak legacy `60-app-shell §9` EC-3), DataTable, LookupDialog tunggal (ganti fragmentasi `CALookup.js` EC-11), ConfirmDialog, CurrencyField, DateField, Toast/Alert, busy-button | `60-app-shell-auth-navigation.md`; `67-client-side-behavior.md` |
| **FE-01 intake-cas** | upstream data | Record aplikasi + dokumen upload intake (viewer read-only `GET /applications/{id}/documents`) | `61-intake-cas-screens.md`; BE-02 §10 |
| **FE-03 approval-inbox** | FE | Pemilik UI keputusan komite (STEP 12) + gate Rapindo; deep-link dua arah (inbox ↔ SCR-CA-05) | `63-approval-inbox-screens.md`; `62-...screens §5.10` |
| **Modul master-data (D-08)** | API/data | Options: debtor_group, ojk_economic_sector, bank, facility_type, klasifikasi penghasilan, photo-type master (GAP-FE02-01) | D-08; `66-master-data-screens.md` |
| **BE-02 seam eksternal (via BE)** | tidak langsung | RAC (status display), SLIK/Pefindo/Dukcapil (read), NeoScore (BE-owned call) — FE TIDAK menyentuh seam langsung | BE-02 §8; D-01 Step 7/8 |
| **Arsitektur ITEC (D-11)** | eksternal | Mekanisme auth/role claim, transport final, konvensi API | D-11; OQ-ARCH-STACK |

---

## 11. Keputusan Dibutuhkan (Open Questions & GAP)

> [OPEN] dari KB + meeting + gap kontrak BE — **jangan diselesaikan diam-diam**. FE memakai default paling aman (fail-closed / fitur tidak dirender / data-driven dari BE) sampai diputuskan.

| ID | Pertanyaan | Prioritas | Dampak FE |
|---|---|---|---|
| **GAP-FE02-01** | Endpoint lookup master options TIDAK ada di BE-02 §4: `debtor_group`, `ojk_economic_sector` (OJK code list `[LOCKED]`), bank, `facility_type`, klasifikasi sumber penghasilan, photo-type master. Tambahkan di BE-02 atau tunjuk endpoint modul master-data (D-08)? | P1 | §5.1 #4, §5.2 #3/#4, §5.6 #4–5; tanpa ini form rekomendasi tidak dapat disubmit |
| **GAP-FE02-02** | Write contract **APPI facility status** (entity `APPI_FACILITY_STATUS` ada di BE-02 §3, endpoint tidak eksplisit di §4 — folded ke `PATCH /credit-analysis/{id}`?). Perlu field census payload. | P2 | §5.4; Panel E |
| **GAP-FE02-03** | Kontrak write **5C preliminary (mobil)** + upload foto dokumen CA: tidak ada endpoint 5C/upload di BE-02 §4 (write-target authority = [OPEN] OQ-CRSCORE-01; 3 jalur legacy — BE BR-02-26). SCR-CA-06 tidak dapat di-build sebelum ini + OQ-CRSCORE-10 putus. | P1 | §4.6, §5.7 |
| **GAP-FE02-04** | Field census response belum dirinci di BE-02 §5: kolom `GET /credit-analysis/worklist`, item set `GET`/`PUT` document-checklist, payload `PUT /credit-analysis/{id}/bank-accounts` (+ sumber rekap 3 bulan), payload `PATCH /credit-analysis/{id}` — termasuk mapping field flat FE §5.1 (`primary_income`/`additional_income`/`spouse_income`) ↔ model row-per-source `trx_credit_analysis_financial` (BE-02 §3.3). | P1 | §4.1, §5.1, §5.3, §5.5 |
| **GAP-FE02-05** | Payload `POST /slik-requests` belum dirinci (BE §4 resource-level); shape legacy `tr_slik_request` dipakai sbg referensi (§5.8) — konfirmasi field target + set `type_pengecekan`. | P2 | §5.8; SCR-CA-07 |
| **GAP-FE02-06** | Matrix akses menu per role D-10 utk modul CA (akses read CMO/Marketing Head/Kepala Cabang/Credit-Admin ke worklist & view?). Legacy: menu per-employee tanpa role scheme (`60-app-shell` BR-SHELL-4). | P2 | §2; guard route §3 |
| **CONF-FE02-01** | Konfirmasi penghapusan **approval-scheme picker** legacy (`ApprovalSchemeID` lookup — BR-CAUI-3) karena routing komite dinamis `trans_type_id`+OP+risk (D-01 Step 10, milik 03) dan kontrak BE §5.3 tanpa field scheme. Bila stakeholder mempertahankan pemilihan manual → butuh field + endpoint baru di BE. | P2 | §5.6 guard RFA |
| **OQ-CRSCORE-10** | Framing layar: jalur CMO 5C vs analyst workstation = kanal per-produk ATAU langkah sekuensial satu lifecycle — menentukan nasib SCR-CA-06 & routing §3. Bukti FE condong per-produk, belum konklusif. | **P1** | §2, §3, §4.6, §6.1 |
| **OQ-MEET-06** | Matriks step per produk MACF (D-07) — menentukan konfigurasi workstation config-driven (section mana tampil per produk). Blocks annex per-produk, bukan PRD ini. | P1 | §1.3, §6.2 #5 |
| **OQ-CAUI-01** | Mapping nama layar assignment ("CheckingSlikDashboard", "TrCaDocuments") → artefak nyata (grep FE = absent; PDF v2 mengutip controller yang tidak ada). Konfirmasi sebelum penamaan screen/route final & mapping field print. | P2 | §1.1 SCR-CA-03; §4.5 print |
| **OQ-CAUI-02** | Identitas role "CA approver" (`IsApprover`/`IsLastApprover`) vs reviewer hierarki generik — milik 03, tapi menentukan apakah FE-02 perlu merender affordance approver apa pun (default: tidak). | P2 | §2 |
| **OQ-CAUI-03** | Worklist mobil legacy tidak ditemukan (layar via session `credit_id`) — konfirmasi worklist tunggal lintas produk sah. | P3 | §3 |
| **OQ-CAUI-05** | Gap penomoran checklist item 3–8 (individual): retired vs KYC hilang — menentukan item set final; FE data-driven dari BE sampai putus. | P2 | §5.3 |
| **OQ-CAUI-06** | Varian call NeoScore authoritative (motor ter-wire ke action mobil R4 — EC-8) + kebijakan caching motor (selalu call) vs mobil (cek log dulu — EC-7): unifikasi sengaja vs bug. Menentukan tombol "refresh skor" §6.2 #10. | P3 | §4.4 |
| **OQ-CAUI-07** | Formula DSR (spouse income in/out) — FE hanya display field BE; keputusan tetap dibutuhkan agar angka yang dilihat analis benar (BE BR-02-28). | P2 | §5.1 #7 |
| **OQ-CRSCORE-02** | DSR 40% & freshness 30-hari STEP 11: advisory (render warning) vs hard-block (render blocking) — FE siap dua mode, default advisory sesuai BE. | P1 (satu tema OQ-REG-06/OQ-SLIK-05) | §7 AdvisoryPanel; §9 AC-FE-9 |
| **OQ-CRSCORE-06** | Mekanisme blocking RAC `REJECTED` → menentukan UX baris worklist & banner (default fail-closed). | P2 | §6.2 #2 |
| **OQ-CRSCORE-08** | Vocabulary verdict checklist (`Terlampir/Belum Ada/Tidak Ada`; `Valid/Tidak Valid/Tidak Ada`) — konfirmasi sebagai controlled vocabulary final (partially answered oleh FE KB). | P2 | §5.3 |
| **BR-02-30 (kebijakan)** | Rekonsiliasi grid transkripsi SLIK vs FCL viewer: validasi silang otomatis vs tetap manual — menentukan apakah FE menambah indikator selisih. | P2 | §4.2 Panel C; AC-FE-12 |
| **OQ-MCP-01** | Enforcement "hanya assigned analyst boleh act" di API/session layer (tanpa super-user D-09) — FE mengasumsikan server-side enforcement, guard FE hanya UX. | P1 | §2 |
| **OQ-ARCH-STACK** *(direvisi D-12)* | FE = Next.js `[LOCKED]`; masih [OPEN]: mekanisme auth/session & role claim (ITEC D-11), strategi rendering per screen (USULAN internal FE-00), transport final. | P1 | §3 guard; §8 konvensi fetch |
| **OQ-SHELL-02** *(milik FE-00, disebut krn menyentuh 02)* | Branch pick login tidak di-re-verify server-side (legacy) — branch scoping worklist bergantung padanya. | P1 | §5.9 scoping |

**Tertutup oleh keputusan meeting (tidak lagi open utk modul ini):**
- **Super-user & bypass scoping** — D-09 `[LOCKED]`: tidak ada affordance UI bypass apa pun (§2, AC-FE-1).
- **RAC async** — D-01 Step 8: FE merender `pending` sebagai state first-class, tidak pernah menganggap submit = decision (§6.2 #1, AC-FE-8).
- **NeoScore caller tier (legacy)** — RESOLVED: legacy call di FE tier (EC-1); target call BE-owned (USULAN BE-02 §8) — FE tidak memanggil vendor (AC-FE-7); residual kontrak vendor = OQ-NEOSCORE-01 (milik BE).
- **Bahasa/stack** — D-12 `[LOCKED]`: FE Next.js; dokumen ini ditulis utk tim FE Next.js.
