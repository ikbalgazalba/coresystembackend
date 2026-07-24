# PRD — Contract Management & PO Issuance [BE]

> **Kapabilitas 04 — contract-cm-po** dari bounded context **Acquisition** (MCF/FINCORE) — **dokumen backend**.
> **Audience**: tim Backend Engineering. **Target stack**: **BE = Java** `[LOCKED]` (D-12); framework **belum
> ditetapkan** — **USULAN: Spring Boot** (lihat §1.4; keputusan final menunggu dokumen arsitektur ITEC Bank Mega,
> D-11) `[OPEN]`. **Tanggal**: 2026-07-14 (revisi v2, post-meeting; supersedes baseline pre-meeting
> `04-contract-cm-po.md`).
> **Posisi alur**: **STEP 12–13** dari alur final 16-STEP (PDF 08072026) — sebelumnya "FASE 12–13" pada v1.
> Kapabilitas ini memfinalisasi **Credit Memo (CM)** — membekukan figur finansial `OP`/`ULI`/`LCR` `[LOCKED]`,
> Payment Option, Upping OTR, **dan mengunci binding asuransi** (`TrCmLifeInsuranceCredit` jiwa +
> `TrCmInsurance` kendaraan) saat committee-approve (D-01 S12; GT STEP 12) — lalu menerbitkan **Purchase Order
> (PO)** secara **deterministik & tunggal — tepat satu PO per approval** (D-01 S13), mencetaknya, dan
> **meng-email PDF PO ke dealer** (GT STEP 13). Kepemilikan minting PO dieksplisitkan di sini karena **legacy
> memicu minting dari modul yang salah** (credit-analyst) — bug do-not-replicate (`hidden-gotchas.md §B GOTCHA-8`).
> **Bahasa**: Bahasa Indonesia; identifier/SP/tabel/field/enum/OQ-ID/D-ID dipertahankan apa adanya.
> **Sumber otoritatif**:
> `.mega-sdd/knowledge-base/.sp-manifests/_ACQUISITION-GROUND-TRUTH.md` (v2 — alur final 16 STEP, PDF 08072026),
> `.mega-sdd/knowledge-base/.sp-manifests/_MEETING-DECISIONS-2026-07.md` (decision register D-01..D-12),
> `.mega-sdd/knowledge-base/10-domains/23-contract-cm-po.md` (KB backend primary),
> `.mega-sdd/knowledge-base/60-frontend/64-contract-cm-po-screens.md` (KB frontend — cross-check kebutuhan API),
> `40-business-rules/operational-rules.md`, `40-business-rules/hidden-gotchas.md`,
> `99-rebuild-architecture/data-mutation-policy.md`, `50-integrations/doku-payment-gateway.md`,
> `50-integrations/email-sms-notifications.md`. Konform ke PRD Payung `00-OVERVIEW.md`.
> **Keputusan meeting terintegrasi**: D-01 (S12, S13, S15), D-02, D-03 (disambiguasi), D-09, D-10, D-11, D-12.

### Disiplin penanda (dari umbrella)

| Penanda | Arti |
|---|---|
| `[LOCKED]` | **WAJIB dipertahankan** (regulatori / kontrak eksternal / external-FK / keputusan governance). Additive only. |
| `[INTENT]` | Outcome bisnis wajib dipenuhi; skema/mekanisme bebas didesain ulang. |
| `[ARTIFACT]` | Kecelakaan legacy — dibuang setelah konfirmasi stakeholder. |
| `[OPEN]` | Belum terjawab → masuk §11 (jangan diselesaikan diam-diam). |
| `[KEPUTUSAN DESAIN BARU]` | Desain rebuild baru, bukan turunan langsung legacy. |
| `[USULAN]` | Usulan desain penulis PRD — bukan turunan KB/keputusan; butuh review arsitek/stakeholder. |

---

## 1. Ruang Lingkup & Kepemilikan

### 1.0 Posisi dalam alur final 16-STEP (delta v1 → v2) — WAJIB dibaca dulu

Ground-truth **v2** (PDF 08072026) me-renumber dan me-restrukturisasi alur; kapabilitas ini terdampak langsung:

| v1 (baseline pre-meeting) | v2 (final) | Dampak ke 04 |
|---|---|---|
| FASE 12 = CM 2nd data entry (layar finalisasi FINCORE) | **STEP 12 = Hierarki Persetujuan (Credit Committee)** — pada approve (`sp_approve_cm_moofi`, contract status **Approved = `'A'`**): **OP/ULI/LCR + Asuransi Jiwa (`TrCmLifeInsuranceCredit`) + Asuransi Kendaraan (`TrCmInsurance`) di-LOCK (dikunci)**; audit ke `tr_hierarchy_transaction`. | Freeze figur finansial **dan lock binding asuransi** terjadi sebagai reaksi atas keputusan komite (GT STEP 12; D-01 S12). Entry 2nd-data CM **dilipat ke upstream (jalur moofi, STEP 1–8)**; surface finalisasi FINCORE (§4/§5.1) tetap dispesifikasikan untuk jalur non-moofi/branch (OQ-GT-01 ✅ RESOLVED — evidence: non-moofi = jalur manual web, tetap live; scope port = keputusan desain, §11) + OQ-MEET-06 (matriks produk, masih OPEN). |
| FASE 13 = PO issuance | **STEP 13 = Penerbitan PO & Koreksi** — Admin Cabang cetak PO (`sp_print_po_acquisition`; varian car `_mobil` per v1) dan **file PDF di-email ke Dealer**; **Fase Koreksi (Open CM)**: bila unit fisik beda (warna/tipe), cabang boleh Open CM dan **kembali ke proses Moofi (Step 1–12)**. | PO minting **single & deterministic — tepat satu PO per approval** (D-01 S13). Email PDF PO ke dealer = bagian standar STEP 13 (bukan opsional terpisah). Return-target koreksi **diperluas** ke Step 1–12 — granularitas (full re-entry vs field-scoped) = **`[OPEN]` OQ-GT-03**. |
| — | **STEP 14 = Vertel** (baru; D-02) | Downstream langsung 04 kini **Vertel** (antrean verifikasi telepon, `TrVerificationCustomer`) **sebelum** NPP (STEP 15). `POIssued` memberi feed antrean Vertel. Vertel **bukan** milik 04 (milik 05/umbrella §4.1). |

Sumber: `_ACQUISITION-GROUND-TRUTH.md` (STEP 12/13/14 + tabel Deltas v1→v2); `_MEETING-DECISIONS-2026-07.md` D-01/D-02.

> **Dual approve path OQ-GT-01 (✅ RESOLVED — evidence 2026-07-14, §11)**: alur final memakai `sp_approve_cm_moofi`; legacy `sp_approve_cm` /
> `sp_approve_cm_car` (non-moofi) masih ada di kode. Channel mana yang tetap masuk scope rebuild per produk =
> P2 entry-point dispatcher (GT STEP 12 note; OQ-MEET-06). Handler `MemoApproved` di 04 **wajib source-agnostic**
> terhadap jalur asal (moofi / non-moofi / Instant-Approval lane D-01 S11 — lihat §5.3).

### 1.1 Yang DIMILIKI kapabilitas ini (owns)

- **Finalisasi Credit Memo (2nd data entry)** — operational/CMO staff merevisi & memantapkan struktur finansial
  memo selama memo masih **editable** (`status ∈ {draft, corrected}`): Payment Option / installment plan,
  **Upping OTR** (re-entry harga OTR/aset), down payment (gross & net), tenor, admin fee & process fee, seleksi
  asuransi. Sumber: `23 §3a S1`, `23 §5.1-5.3`, GT v1 FASE 12. **Delta v2**: pada alur final, entry ini dilipat
  ke jalur moofi upstream (§1.0); kapabilitas ini tetap memiliki surface finalisasi untuk jalur koreksi
  (memo `corrected`) dan jalur non-moofi (OQ-GT-01 RESOLVED — evidence: kedua jalur LIVE, pemisah = trigger;
  keputusan port jalur non-moofi = keputusan desain, lihat §11).
- **Sub-record asuransi**: **vehicle-insurance** (`TrCmInsurance`) dan **life-insurance-on-credit**
  (`TrCmLifeInsuranceCredit`, lini car). Sumber: `23 §4`, `23 §11`.
- **Freeze figur finansial `[LOCKED]` + lock binding asuransi** — pada event `MemoApproved`, kapabilitas ini
  menghitung & membekukan `OP` (outstanding principal), `ULI`, `LCR` beserta snapshot `first_*` di memo,
  menyimpan snapshot point-in-time financing figures, **dan mengunci sub-record asuransi jiwa + kendaraan**
  (GT STEP 12: "OP/ULI/LCR values, Asuransi Jiwa (TrCmLifeInsuranceCredit), Asuransi Kendaraan (TrCmInsurance)
  are LOCKED (dikunci)"; D-01 S12). Sumber: `23 §5.6-5.7`, `operational-rules.md OR-13`. **Freeze adalah reaksi
  04 terhadap keputusan 03**, bukan milik 03 (digest: 03 "BUKAN miliknya: freeze figur finansial").
- **PO minting deterministik tunggal — tepat satu PO per approval** — pada `MemoApproved` (semua terminasi
  hierarki), 04 mint `po_number` ke `TrPo` dengan `po_number` di-assign **saat mint** (bukan `NULL`).
  Sumber: `23 §5.8`, digest `boundary_ownership PO minting`; **D-01 S13** ("single deterministic PO minting
  immediately after CM approval — exactly one PO per approval").
- **Printing PO** (position-gated) **+ email PDF PO ke dealer sebagai bagian standar alur cetak** (GT STEP 13:
  "the PDF file is emailed to the Dealer"; FE `64 §5 langkah 9`: aksi print memicu dealer-email-send dalam
  request yang sama), dan **koreksi PO (Open CM)** saat unit fisik beda (warna/tipe) — return-target v2 =
  proses Moofi Step 1–12, granularitas `[OPEN]` OQ-GT-03. Sumber: `23 §5.9-5.13`, GT STEP 13,
  `operational-rules.md OR-14`.
- **Konsumsi DOKU account-validate** (Cek Rekening) — validasi rekening bank customer sebelum finalisasi.
  Seam **dimiliki 04** (umbrella §9 #6). Sumber: `50-integrations/doku-payment-gateway.md`.
- **Emit event `POIssued`** (dan `MemoCorrectionOpened` pada koreksi).

### 1.2 Yang BUKAN miliknya (non-goal)

- **Keputusan approve/reject/correction komite** — milik **03-approval-committee** (STEP 12 sisi keputusan;
  routing `sp_get_next_approval_scheme` by Plafond + Risiko; audit `tr_hierarchy_transaction`). 04 hanya
  **mengonsumsi** event `MemoApproved` untuk mem-freeze + lock asuransi + mint. Sumber: GT STEP 12; digest row 03/04.
- **RFA lock (`sp_rfa_cm` legacy; jalur moofi `sp_approve_cm_moofi` di STEP 9)** — milik **01-intake-cas**
  (boundary STEP 9). 04 **menghormati** editability guard-nya (memo hanya editable di `draft`/`corrected`),
  tetapi **tidak memiliki** endpoint RFA. Sumber: digest `boundary_ownership RFA`; GT STEP 9; umbrella §5.
- **Komposisi awal `trans_type_id`** — disusun **02-credit-analysis** (`sp_get_trans_type_id_cm`,
  risk-tier-qualified; per D-01 S8 risk-category dari RAC callback meng-compose `trans_type_id`). 04 hanya
  **membawa** nilainya char-for-char. Sumber: umbrella §7.1; D-01 S8.
- **Vertel (STEP 14, D-02)** — verifikasi telepon konsumen (`TrVerificationCustomer`), RFA Vertel, approve
  Kepala Cabang — milik **05** (umbrella §4.1). 04 hanya memberi feed antrean via `POIssued`.
- **Validasi BAST + chassis/engine, aktivasi kontrak (NPP), cetak Financing Agreement / dokumen PK** — milik
  **05-npp-legalization** (STEP 15; D-04). Sumber: GT STEP 15; digest row 05.
- **Email blast ke dealer pasca STEP 15 (D-03)** — notifikasi post-contract-activation, milik 05 (OQ-MEET-01).
  **JANGAN dirancukan** dengan **email PDF PO ke dealer di STEP 13** yang dimiliki 04 (§5.6). Dua notifikasi
  berbeda pada titik alur berbeda.
- **Insurance-cover binding batch** (layar FE IC1–IC6 di `64 §3a` — grouping kontrak ter-legalisasi per nomor
  NPP ke satu cover request insurer) — kemungkinan proses downstream konteks INSURANCE, **bukan** insurance
  binding per-memo D-01 S12. Relasi keduanya = `[OPEN]` **OQ-CMPOFE-08** (§11); sampai terjawab, IC1–IC6
  di-luar-scope 04.
- **GL posting / disbursement / subledger** — post-acquisition; downstream menarik data via **PULL, bukan push**
  (D-01 S15). Sumber: umbrella §1.2.
- **JANGAN memicu PO dari modul credit-analyst** (`CreditAnalystRepositoryEF.cs:692-708`) — bug legacy
  do-not-replicate (`hidden-gotchas.md §B GOTCHA-8`). Minting **terpusat** di 04.

### 1.3 Departure kunci (fix bug legacy) — [KEPUTUSAN DESAIN BARU]

**04 adalah SATU-SATUNYA writer `CREDIT_MEMO`** — termasuk transisi `status → finalized/approved/corrected` dan freeze
`OP`/`ULI`/`LCR` + lock asuransi. **03 hanya menulis `APPROVAL_STEP`/`APPROVAL_HISTORY` (audit
`tr_hierarchy_transaction`) dan meng-emit `MemoApproved`.** Di legacy, aksi komite (`sp_approve_cm` /
`sp_approve_cm_moofi`) menulis memo **dan** PO di-mint dari modul credit-analyst yang salah. Rebuild
**sengaja menyimpang**: seluruh mutasi memo + freeze + lock asuransi + mint dikolapskan ke **satu handler
deterministik 04** yang bereaksi atas `MemoApproved`. Keputusan meeting **D-01 S13** ("exactly one PO per
approval") mengkonfirmasi arah desain ini sebagai requirement target-state `[INTENT]`, bukan sekadar preferensi
rebuild. Sumber: `hidden-gotchas.md §B GOTCHA-6/GOTCHA-8`, digest `boundary_ownership PO minting`; D-01 S13.

### 1.4 Catatan arsitektur target (Java) — [USULAN kecuali dinyatakan lain]

- **Bahasa: Java** `[LOCKED]` (D-12 — SoW: BE = Java, FE = Next.js; PRD dipisah per audience `BE-*`/`FE-*`).
- **Framework**: `[OPEN]` — belum ditetapkan; menunggu dokumen arsitektur **ITEC Bank Mega** (D-11, deadline
  10 Juli 2026). **USULAN penulis: Spring Boot 3.x** (Spring Web + Spring Data JPA + Spring Validation),
  dengan alasan: ekosistem enterprise-standar, dukungan transactional event handling (`@Transactional` +
  transactional outbox) yang dibutuhkan handler idempotent §5.3, dan integrasi resilience (Resilience4j
  timeout/retry/circuit-breaker) untuk seam DOKU §8. **Jangan dianggap keputusan** — tandai di §11
  (OQ-ARCH-STACK residual).
- **Pola implementasi yang disyaratkan outcome-nya** (mekanisme bebas): handler `MemoApproved` idempotent
  (unique constraint `memo_id`+`approval_decision_id`), increment `print_count` atomic (DB-level
  `UPDATE ... SET print_count = print_count + 1` atau optimistic lock), email dispatch async least-privilege
  (BUKAN dari DB tier), semua downstream PULL (D-01 S15).

---

## 2. Aktor & Peran

Sumber: `23 §2`; umbrella §2 (tak ada RBAC statis di legacy — peran direkonstruksi; rebuild bebas
memperkenalkan permission layer yang benar). **Delta v2**: sensus peran cabang final per **D-10** `[LOCKED]` =
**CMO, Marketing Head, Credit Analyst, Kepala Cabang, Credit (Admin)**; hierarki approval bergantung **skala
risiko**. **Super user DIHAPUS** per **D-09** `[LOCKED]` — rebuild **TIDAK BOLEH** menyediakan role super-user
maupun bypass ekuivalen di endpoint mana pun milik 04.

| Peran (target, D-10) | Peran baseline / legacy | Aksi dimiliki 04 | Mutabilitas |
|---|---|---|---|
| **CMO / Credit (Admin)** | Operational / CMO staff — merevisi & memantapkan struktur finansial draft memo sebelum lock; minta validasi rekening DOKU; ambil quote asuransi. | Finalization (S1), insurance-quote, bank-account validate | `[INTENT]` (aksi) / `[LOCKED]` (sensus role D-10) |
| **Credit (Admin) / Admin Cabang** | Branch admin — memicu **koreksi (Open CM)** saat unit fisik beda. (RFA lock dipicu di boundary 01, **bukan** di sini.) | PO/memo correction (S5) | `[INTENT]` |
| **Admin Cabang (PO printer, position-gated)** | PO printer — GT STEP 13 menyebut eksplisit **Admin Cabang** yang mencetak PO. Gate legacy: posisi employee lolos lookup HR (`sp_get_check_user_print_po`). Gate **ada** = `[LOCKED]`; pemetaan kode posisi legacy ↔ role census D-10 = `[OPEN]` (OQ-CMPOFE-05). Catatan FE: check posisi di web layer legacy **dead code** (`64 BR-CMPOFE-14` `[ARTIFACT]`) — enforcement WAJIB server-side (BE). | Print PO, email PO | `[LOCKED]` (gate) / `[OPEN]` (katalog posisi) |
| **Credit-committee hierarchy (03, sibling)** | Menentukan disposisi final (routing by trans_type_id + Plafond OP + risk level, D-01 S10; self-approval BLOCKED + Instant-Approval lane, D-01 S11); **memasok** event `MemoApproved` yang memicu freeze + lock asuransi + mint. Bukan aktor internal 04. | — (upstream) | out-of-scope |
| **Dealer (eksternal)** | Menerima PO cetak + **email PDF PO** sebagai guarantee unit dikirim; tidak menyentuh sistem langsung. | — (penerima output) | `[INFERRED]` |

> **D-09 enforcement di BE 04**: tidak ada role yang boleh melewati guard editability (§6 BR-CMPO-1), gate
> print (§5.4), atau guard idempotensi email (§5.6). Audit maker-checker tetap per `operational-rules.md OR-2`
> (OQ-MCP-01).

---

## 3. Model Data

> **GROUND TRUTH schema modul contract-cm-po.** Bentuk **target** rebuild per **`docs/DB-CONVENTIONS.md`**
> (lampiran WAJIB; RDBMS asumsi PostgreSQL — USULAN, final ITEC A-2). Setiap tabel target ditulis per
> konvensi §9: nama + kelas prefix, **mapping asal** legacy, **field census** ber-marker (confidence
> `[VERIFIED]/[INFERRED]/[OPEN]` × mutability `[LOCKED]/[INTENT]/[ARTIFACT]`), dan **disposisi migrasi**.
> Field `[LOCKED]` = additive only (nilai/makna dipertahankan; NAMA boleh mengikuti konvensi kecuali
> dirujuk sistem eksternal secara literal). Konform entitas + enum umbrella §6. Kontrak antar-service
> tetap level resource + field (§4–§5) — skema di bawah adalah kontrak **persistence**, bukan kontrak API.
>
> **Kolom wajib** konvensi §4 berlaku untuk SEMUA tabel `trx_`/`out_` di bawah: `created_at TIMESTAMPTZ`,
> `created_by VARCHAR(50)`, `updated_at TIMESTAMPTZ`, `updated_by VARCHAR(50)` — mapping seragam dari legacy
> `created_by/created_on/last_updated_by/last_updated_on` (tidak diulang di tiap census kecuali menyimpang);
> tabel `log_` hanya `created_at/created_by` (append-only). `version INTEGER NOT NULL DEFAULT 0`
> (optimistic locking) pada `trx_credit_memo` + satelit yang diedit user. PK teknis semua tabel =
> `id BIGINT GENERATED ALWAYS AS IDENTITY`; business key (`credit_id`, `po_number`) = kolom terpisah +
> unique constraint (konvensi §2).

**Peta entitas konseptual → tabel target** (nama konseptual dipakai §4–§10):

| Entitas konseptual (§4–§10) | Tabel target |
|---|---|
| `CREDIT_MEMO` | `trx_credit_memo` (+ `trx_credit_memo_payment`, grup `trx_credit_memo_insurance*`) |
| `PURCHASE_ORDER` | `trx_purchase_order` |
| `PO_EMAIL_LOG` | `out_notification` (dispatch + idempotensi) + `log_po_email` (audit kirim) |
| `CM_INSURANCE_VEHICLE` | `trx_credit_memo_insurance_vehicle` + `trx_credit_memo_insurance_cover_year` |
| `CM_INSURANCE_LIFE` | `trx_credit_memo_insurance_life` |
| `CM_FINANCING_SNAPSHOT` | `trx_credit_memo_financing_snapshot` (+ arsip `log_credit_memo_financing_snapshot`) |
| `PO_CORRECTION_LOG` | `log_credit_memo_reopen` |

### 3.0 Disposisi tabel legacy milik modul — coverage 20/20

| # | Tabel legacy | Target | Kelas | Disposisi migrasi |
|---|---|---|---|---|
| 1 | `tr_CM` (124 kolom; `FC_ACQ_MCF 2.sql:6453-6583`) | `trx_credit_memo` + `trx_credit_memo_payment` + `trx_credit_memo_insurance` (+ 1 kolom → `trx_credit_memo_disbursement`, 3 → `log_*`, 2 → modul collections, 15 `[OPEN]` → `stg_legacy_tr_cm`, 7 drop `[ARTIFACT]`) | `trx_` | Normalisasi — census penuh kolom-per-kolom §3.1. |
| 2 | `tr_CM_Insurance` | `trx_credit_memo_insurance_vehicle` + `trx_credit_memo_insurance_cover_year` | `trx_` | Migrate; `TLO1-5`/`AllRisk1-5` → typed rows (konvensi §6 #4). §3.3. |
| 3 | `tr_CM_life_insurance_credit` | `trx_credit_memo_insurance_life` | `trx_` | Migrate + `locked_at/locked_by` baru (D-01 S12). §3.3. |
| 4 | `tr_cm_health_insurance` | `trx_credit_memo_insurance_health` | `trx_` | Migrate; heap tanpa PK → `id` + FK NOT NULL. §3.3. |
| 5 | `tr_CM_UMC` | `trx_credit_memo_disbursement` | `trx_` | Migrate. §3.4. |
| 6 | `tr_CM_bank_account` | `trx_credit_memo_bank_account` | `trx_` | Migrate. §3.4. |
| 7 | `tr_CM_rate` | `trx_credit_memo_rate` | `trx_` | Migrate (BR-CMPO-14 / OQ-CMPO-09). §3.5. |
| 8 | `tr_CM_subsidi_DP` | `trx_credit_memo_dp_subsidy` | `trx_` | Migrate. §3.5. |
| 9 | `tr_cm_deposit_installment` | fold → `trx_credit_memo_payment` | `trx_` | 1:1 by `credit_id`; duplikasi vs kolom deposit `tr_CM` direkonsiliasi. §3.2. |
| 10 | `tr_cm_deposit_installment_detail` | `trx_credit_memo_deposit_period` | `trx_` | Migrate. §3.2. |
| 11 | `tr_PO` | `trx_purchase_order` | `trx_` | Migrate + kolom mint deterministik (D-01 S13). §3.6. |
| 12 | `tr_PO_send_to_email` | `out_notification` | `out_` | Re-model outbox ADR-04; idempotensi `is_send` dipertahankan. §3.6. |
| 13 | `tr_send_PO_log` | `log_po_email` | `log_` | Snapshot figur denormalized di-drop. §3.6. |
| 14 | `tr_items` | `trx_financed_asset` (`record_scheme='STANDARD'`) | `trx_` | Merge dua tabel paralel. §3.7. |
| 15 | `tr_items_UMC` | `trx_financed_asset` (`record_scheme='UMC'`) | `trx_` | idem. §3.7. |
| 16 | `DOKU_Inquiry_Account_Bank_Check` | `trx_bank_account_inquiry` | `trx_` | Migrate; `idx` manual-increment → identity (konvensi §6 #5); PII retensi OQ-GAP-11. §3.8. |
| 17 | `DOKU_API_Log` | `log_doku_api` *(kondisional)* | `log_` | `[ARTIFACT: log]` — default diganti structured logging app-tier; tabel dibawa hanya bila retensi menuntut (OQ-GAP-11). §3.8. |
| 18 | `log_open_transaction` | `log_credit_memo_reopen` | `log_` | Migrate + diperkaya `reason`/`return_target`. §3.9. |
| 19 | `Tr_TopUpMegaSolusi` | — **TIDAK dibawa** | — | `[ARTIFACT — discard: disabled]`; OQ-GAP-08. §3.10. |
| 20 | `produk_other_income_skema_III` | — **TIDAK dibawa** | — | `[ARTIFACT — discard: dead/vestigial]` 0 referensi. §3.10. |

> Di luar 20 tabel census: `tr_CM_Fincore` (snapshot figur saat terminal-approve; dihapus `sp_trans_open_cm`
> saat Open CM) → `trx_credit_memo_financing_snapshot` §3.9. Kolom `tr_CM` ber-semantik `[OPEN]` diparkir di
> `stg_legacy_tr_cm` (kelas `stg_` konvensi §1 — TIDAK dikonsumsi logic bisnis) sampai OQ-CMPO-13 terjawab.

### 3.1 `trx_credit_memo` — spine memo (normalisasi `tr_CM`)

#### 3.1.1 Definisi target & kunci

**Mapping asal**: `tr_CM` (124 kolom — tabel terbesar skema legacy; `FC_ACQ_MCF 2.sql:6453-6583`). **Pecahan
normalisasi**: inti finansial + identitas + status di sini; struktur pembayaran → `trx_credit_memo_payment`
(§3.2); seleksi/fee asuransi → `trx_credit_memo_insurance` (§3.3). Pemilik finalize + freeze; `trans_type_id`
disusun 02.

**Kunci & kolom target TANPA padanan legacy 1:1**:

| Kolom target | Tipe | Marker | Catatan |
|---|---|---|---|
| `id` | `BIGINT` identity PK | `[KEPUTUSAN DESAIN BARU]` | PK teknis (konvensi §2). |
| `credit_id` | `VARCHAR(20) NOT NULL` + `ux_trx_credit_memo_credit_id` | `[VERIFIED]`×`[LOCKED]` | Business key — di-mint STEP 8 (sync MOOFI→FINCORE) sebagai nomor kontrak **unik nasional** (GT STEP 8); format/sequence = resolved OQ-GT-02 (`branch(5)+YY+MM+SEQ(5)` 14-char, BE-01 §3.1.13; milik 01 — 04 hanya konsumen); dirujuk lintas modul & eksternal. |
| `application_id` | `BIGINT` FK → `trx_application` (01) | `[KEPUTUSAN DESAIN BARU]` | Declared FK nyata (konvensi §2); legacy join by `credit_id` unenforced. |
| `trans_type_id` | `VARCHAR` | `[VERIFIED]`×`[LOCKED]` | **external-FK** — dibawa char-for-char dari 02; dicocokkan `FC_MSTAPP_MCF` approval-hierarchy (umbrella §7.1; D-01 S8). Bukan kolom `tr_CM` — dipasok pipeline 02; lokasi persist legacy `[OPEN]`. |
| `product_line` | `VARCHAR` + `CHECK (CAR\|MOTOR)` | `[KEPUTUSAN DESAIN BARU]` | Menentukan varian formula/insurance. Derivasi dari kode aset `001`=motor/`002`=car = `[OPEN]` OQ-CMPO-06; matriks produk MACF = `[OPEN]` OQ-MEET-06 (D-07). |
| `status` | `VARCHAR(10)` + `CHECK (draft\|finalized\|approved\|corrected\|rejected)` | `[VERIFIED]`×`[INTENT]` | SATU kolom status (konvensi §5) — konsolidasi `status_approval` + `status_credit`. Nilai legacy `D/C/0/A` `[LOCKED]` → mapping kanonik §7.1; `V`/`R` display-only = `[OPEN]` OQ-CMPO-10/OQ-CMPOFE-04. |
| `version` | `INTEGER NOT NULL DEFAULT 0` | konvensi §4 | Optimistic locking — memo diedit user konkuren. |

#### 3.1.2 Census penuh 124 kolom `tr_CM` — pemetaan eksplisit per kolom

Alias target: **CM** = `trx_credit_memo`, **PAY** = `trx_credit_memo_payment` (§3.2), **INS** =
`trx_credit_memo_insurance` (§3.3), **DSB** = `trx_credit_memo_disbursement` (§3.4), **STG** =
`stg_legacy_tr_cm` (parkir kolom `[OPEN]` — di luar skema live), **DROP** = tidak dibawa (`[ARTIFACT]`
dengan alasan). Marker = confidence × mutability.

| # | Kolom legacy (tipe) | Target | Marker | Catatan / disposisi |
|---|---|---|---|---|
| 1 | `created_by` `varchar(15)` | CM.`created_by` `VARCHAR(50)` | `[VERIFIED]`×`[INTENT]` | Audit konvensi §4. |
| 2 | `created_on` `datetime` | CM.`created_at` `TIMESTAMPTZ` | `[VERIFIED]`×`[INTENT]` | Simpan UTC (konvensi §3). |
| 3 | `last_updated_by` `varchar(15)` | CM.`updated_by` `VARCHAR(50)` | `[VERIFIED]`×`[INTENT]` | |
| 4 | `last_updated_on` `datetime` | CM.`updated_at` `TIMESTAMPTZ` | `[VERIFIED]`×`[INTENT]` | |
| 5 | `credit_id` `varchar(20)` PK | CM.`credit_id` (unique) | `[VERIFIED]`×`[LOCKED]` | Business key; PK teknis diganti `id` (§3.1.1). |
| 6 | `credit_date` `datetime` | CM.`memo_date` `DATE` | `[VERIFIED]`×`[INTENT]` | |
| 7 | `fin_code` `char(2)` | CM.`financing_type_code` `VARCHAR(2)` | `[VERIFIED]`×`[INTENT]` | CF konvensional / SY-US syariah — input assignment insurer (`sp_get_insurance_random_R2`); nilai dipertahankan. |
| 8 | `is_QQ` `varchar(5)` | CM.`is_qq` `BOOLEAN NOT NULL DEFAULT false` | `[VERIFIED]`×`[INTENT]` | Pembiayaan QQ (atas nama); konversi boolean konvensi §3. |
| 9 | `QQ_name` `varchar(150)` | CM.`qq_name` `VARCHAR(150) NULL` | `[VERIFIED]`×`[INTENT]` | Nama pihak QQ (muncul di dokumen kontrak). |
| 10 | `dealer_code` `varchar(10)` | CM.`dealer_id` `VARCHAR(10)` FK → `mst_dealer` | `[VERIFIED]`×`[INTENT]` | Declared FK (konvensi §2). |
| 11 | `tenor` `int` | CM.`tenor_months` `INTEGER` | `[VERIFIED]`×`[LOCKED]` | Term kontraktual inti; input formula LCR. |
| 12 | `installment_id` `varchar(5)` | PAY.`payment_option_id` `VARCHAR(5)` | `[VERIFIED]`×`[INTENT]` | Payment option / installment plan (S1, §5.1). |
| 13 | `asset_cost` `numeric(18,0)` | CM.`otr_price` `NUMERIC(18,2)` | `[VERIFIED]`×`[LOCKED]` | Harga OTR/aset; **Upping OTR** = re-submit selama editable. Legacy whole-rupiah → simpan `.00`; rekonsiliasi migrasi bandingkan nilai bulat (konvensi §3). Input formula OP motor. |
| 14 | `gross_down_payment` `numeric(18,0)` | CM.`down_payment_gross` `NUMERIC(18,2)` | `[VERIFIED]`×`[LOCKED]` | Aturan DP-minimum OJK; boundary DP=0 eksplisit (Edge Case 10 `23 §9`). |
| 15 | `disc_deposit` `numeric(18,0)` | PAY.`deposit_discount` `NUMERIC(18,2)` | `[VERIFIED]`×`[LOCKED]` | Struktur DP. |
| 16 | `deposit_installment` `int` | PAY.`deposit_installment_months` `INTEGER` | `[VERIFIED]`×`[LOCKED]` | Skema deposit-installment; rekonsiliasi vs header `tr_cm_deposit_installment` (§3.2). |
| 17 | `deposit` `numeric(18,0)` | PAY.`deposit_amount` `NUMERIC(18,2)` | `[VERIFIED]`×`[LOCKED]` | idem #16. |
| 18 | `amount_installment` `numeric(18,0)` | CM.`installment_amount` `NUMERIC(18,2)` | `[VERIFIED]`×`[LOCKED]` | Input LCR (= installment × tenor). |
| 19 | `admin_fee` `numeric(18,0)` | CM.`admin_fee` `NUMERIC(18,2)` | `[VERIFIED]`×`[LOCKED]` | Fee disclosure konsumen. |
| 20 | `insurance_fee` `numeric(18,0)` | INS.`insurance_fee` `NUMERIC(18,2)` | `[VERIFIED]`×`[LOCKED]` | Premi (nett) — metodologi rate-table OJK (BR-CMPO-13). |
| 21 | `nett_down_payment` `numeric(18,0)` | CM.`down_payment_net` `NUMERIC(18,2)` | `[VERIFIED]`×`[LOCKED]` | Input formula OP motor (`asset_cost − nett_down_payment`, `sp_approve_cm:204-353`). |
| 22 | `jml_pembiayaan` `numeric(18,0)` | CM.`financed_amount` `NUMERIC(18,2)` | `[VERIFIED]`×`[LOCKED]` | Jumlah pembiayaan; basis formula OP car (BR-CMPO-5; OQ-CMPO-03). |
| 23 | `rate_value` `numeric(18,0)` | CM.`rate_value` `NUMERIC(18,2)` | `[VERIFIED]`×`[OPEN]` | Relasi vs effective/flat tak terjawab KB → OQ-CMPO-13; dibawa apa adanya. |
| 24 | `effective_rate` `float` | CM.`effective_rate` `NUMERIC(9,6)` | `[VERIFIED]`×`[LOCKED]` | Disclosure OJK (APR-equivalent); presisi 6 desimal (konvensi §3) — JANGAN float. |
| 25 | `flat_rate` `float` | CM.`flat_rate` `NUMERIC(9,6)` | `[VERIFIED]`×`[LOCKED]` | |
| 26 | `overdue_rate` `float` | CM.`overdue_rate` `NUMERIC(9,6)` | `[VERIFIED]`×`[LOCKED]` | |
| 27 | `item_id` `varchar(10)` | CM.`item_id` `VARCHAR(10)` | `[VERIFIED]`×`[INTENT]` | Klasifikasi aset (master katalog); kode `001`/`002` = kandidat penentu `product_line` (OQ-CMPO-06). |
| 28 | `item_merk_id` `varchar(10)` | CM.`item_brand_id` `VARCHAR(10)` | `[VERIFIED]`×`[INTENT]` | |
| 29 | `item_merk_type_id` `varchar(10)` | CM.`item_brand_type_id` `VARCHAR(10)` | `[VERIFIED]`×`[INTENT]` | |
| 30 | `year_item` `varchar(4)` | CM.`asset_year` `SMALLINT` | `[VERIFIED]`×`[INTENT]` | |
| 31 | `product_id` `int` | CM.`product_id` `INTEGER` | `[VERIFIED]`×`[INTENT]` | FK master produk. |
| 32 | `finish_date` `smalldatetime` | STG | `[VERIFIED]`×`[OPEN]` | Semantik tak ditemukan di KB — OQ-CMPO-13. |
| 33 | `status_approval` `varchar(2)` | CM.`status` (konsolidasi) | `[VERIFIED]`×`[INTENT]` | Nilai `D/C/0/A` `[LOCKED]` → §7.1; `V`/`R` display-only (OQ-CMPO-10). |
| 34 | `disc_type` `char(1)` | PAY.`discount_type` `VARCHAR(1)` | `[INFERRED]`×`[INTENT]` | Tipe diskon/deposit; enum `[OPEN]` OQ-CMPO-13. |
| 35 | `ongkos_tagih` `decimal(9,0)` | CM.`billing_fee` `NUMERIC(18,2)` | `[VERIFIED]`×`[LOCKED]` | Fee disclosure. |
| 36 | `ongkos_BBN` `decimal(9,0)` | CM.`bbn_fee` `NUMERIC(18,2)` | `[VERIFIED]`×`[LOCKED]` | Biaya balik nama kendaraan. |
| 37 | `is_item_new` `tinyint` | CM.`is_asset_new` `BOOLEAN` | `[VERIFIED]`×`[INTENT]` | |
| 38 | `channel_name` `varchar(45)` | DROP | `[VERIFIED]`×`[ARTIFACT]` | Denormalisasi — derive dari `channel_id` → master (konvensi §6 #3). |
| 39 | `status_credit` `varchar(2)` | DROP (konsolidasi ke CM.`status`) | `[VERIFIED]`×`[ARTIFACT]` | Status ganda-makna dilarang (konvensi §5); matriks nilai vs `status_approval` diverifikasi saat migrasi (OQ-CMPO-01 / OQ-CMPOFE-02). |
| 40 | `is_tutup_buka` `varchar(15)` | STG | `[INFERRED]`×`[OPEN]` | Kemungkinan penanda tutup-buka (reopen); OQ-CMPO-13. |
| 41 | `approval_description` `varchar(255)` | `log_approval_history` (03) | `[VERIFIED]`×`[ARTIFACT]` (penempatan) | Catatan keputusan = audit approval milik 03, bukan kolom spine memo. |
| 42 | `application_type_id` `varchar(15)` | CM.`application_type_id` `VARCHAR(15)` | `[VERIFIED]`×`[INTENT]` | `'03'` = top-up (bukti `Tr_TopUpMegaSolusi` §3.10). |
| 43 | `reason_RFA` `datetime` | DROP | `[VERIFIED]`×`[ARTIFACT]` | Mistyped `datetime` untuk "reason" (OQ-CORE-02); alasan RFA hidup di audit 01/03. |
| 44 | `collectible_period` `bit` | STG | `[VERIFIED]`×`[OPEN]` | OQ-CMPO-13. |
| 45 | `collectible_sequence_period` `numeric(18,0)` | STG | `[VERIFIED]`×`[OPEN]` | OQ-CMPO-13. |
| 46 | `OP` `numeric(18,0)` | CM.`outstanding_principal` `NUMERIC(18,2)` | `[VERIFIED]`×`[LOCKED]` | **Frozen-at-approval** (BR-CMPO-4; D-01 S12). Motor: `asset_cost − nett_down_payment` (`sp_approve_cm:204-353`); arti bisnis = `[OPEN]` OQ-CMPO-02/OQ-CORE-03; D-01 S10 memakai "Plafond Hutang Pokok (OP)" sebagai kunci routing komite. |
| 47 | `ULI` `numeric(18,0)` | CM.`uli` `NUMERIC(18,2)` | `[VERIFIED]`×`[LOCKED]` | **Frozen**; `= LCR − OP` (`sp_approve_cm`); arti bisnis `[OPEN]`. |
| 48 | `LCR` `numeric(18,0)` | CM.`lcr` `NUMERIC(18,2)` | `[VERIFIED]`×`[LOCKED]` | **Frozen**; `= amount_installment × tenor`. |
| 49 | `first_OP` `numeric(18,0)` | CM.`first_op` `NUMERIC(18,2)` | `[VERIFIED]`×`[LOCKED]` | Snapshot baseline auditable immutable saat approval (`23 §5.6`). |
| 50 | `first_ULI` `numeric(18,0)` | CM.`first_uli` `NUMERIC(18,2)` | `[VERIFIED]`×`[LOCKED]` | idem. |
| 51 | `first_LCR` `numeric(18,0)` | CM.`first_lcr` `NUMERIC(18,2)` | `[VERIFIED]`×`[LOCKED]` | idem. |
| 52 | `approved_by` `varchar(20)` | CM.`approved_by` `VARCHAR(50)` | `[VERIFIED]`×`[INTENT]` | Distamp saat freeze (§5.3). |
| 53 | `approved_date` `datetime` | CM.`approved_at` `TIMESTAMPTZ` | `[VERIFIED]`×`[INTENT]` | idem. |
| 54 | `is_convert` `varchar(255)` | STG | `[INFERRED]`×`[OPEN]` | Kemungkinan flag konversi data lama; OQ-CMPO-13. |
| 55 | `last_pay_date` `varchar(35)` | DROP | `[INFERRED]`×`[ARTIFACT]` | Domain servicing + tanggal bertipe varchar; bukan data akuisisi. |
| 56 | `area_collection_kons_id` `varchar(35)` | transfer → modul collections | `[INFERRED]`×`[INTENT]` | Penugasan area collection saat origination; pemilik target di luar 04 (di luar scope acquisition). |
| 57 | `is_milik_PT` `bit` | CM.`is_company_owned` `BOOLEAN` | `[INFERRED]`×`[INTENT]` | Unit milik badan usaha. |
| 58 | `po_source` `varchar(10)` | STG | `[INFERRED]`×`[OPEN]` | "PO" di sini ≠ purchase order 04 (kemungkinan sumber order); OQ-CMPO-13. |
| 59 | `po_source_other` `char(5)` | STG | `[INFERRED]`×`[OPEN]` | idem #58. |
| 60 | `NIK_collection_code` `char(5)` | transfer → modul collections | `[INFERRED]`×`[INTENT]` | Kode kolektor. |
| 61 | `NIK_surveryor_code` `varchar(10)` | CM.`surveyor_employee_id` `VARCHAR(10)` | `[VERIFIED]`×`[INTENT]` | Typo legacy "surveryor" TIDAK direplikasi (nama bukan `[LOCKED]`). |
| 62 | `channel_id` `varchar(15)` | CM.`channel_id` `VARCHAR(15)` | `[VERIFIED]`×`[INTENT]` | Terkait dispatcher channel OQ-GT-01 (moofi vs non-moofi). |
| 63 | `product_marketing_id` `varchar(15)` | CM.`marketing_product_id` `VARCHAR(15)` | `[VERIFIED]`×`[INTENT]` | Kode `'098'` mengecualikan snapshot Fincore (§3.9). |
| 64 | `usr_correction` `varchar(15)` | `log_credit_memo_reopen`.`created_by` | `[VERIFIED]`×`[ARTIFACT]` (bentuk) | Riwayat koreksi = log append-only (konvensi §5), bukan kolom `last_*` bertumpuk di spine. |
| 65 | `dtm_correction` `datetime` | `log_credit_memo_reopen`.`created_at` | `[VERIFIED]`×`[ARTIFACT]` (bentuk) | idem #64. |
| 66 | `is_flag_quota` `varchar(15)` | STG | `[INFERRED]`×`[OPEN]` | OQ-CMPO-13. |
| 67 | `package_id` `varchar(20)` | CM.`package_id` `VARCHAR(20)` | `[VERIFIED]`×`[INTENT]` | |
| 68 | `program_id` `bit` | STG | `[VERIFIED]`×`[OPEN]` | Kolom `bit` bernama `_id` (anomali skema); OQ-CMPO-13. |
| 69 | `insurance_fee_gross` `numeric(18,0)` | INS.`insurance_fee_gross` `NUMERIC(18,2)` | `[VERIFIED]`×`[LOCKED]` | |
| 70 | `uang_muka_murni_kons` `numeric(18,0)` | PAY.`customer_pure_down_payment` `NUMERIC(18,2)` | `[VERIFIED]`×`[LOCKED]` | Struktur DP (uang muka murni konsumen). |
| 71 | `installment_decimal` `numeric(18,0)` | STG | `[VERIFIED]`×`[OPEN]` | Nama vs tipe kontradiktif; OQ-CMPO-13. |
| 72 | `model_id` `varchar(10)` | CM.`model_id` `VARCHAR(10)` | `[VERIFIED]`×`[INTENT]` | |
| 73 | `CC` `varchar(100)` | CM.`engine_capacity` `VARCHAR(100)` | `[INFERRED]`×`[INTENT]` | Kapasitas mesin (spec aset). |
| 74 | `first_payment` `numeric(18,0)` | PAY.`first_payment` `NUMERIC(18,2)` | `[VERIFIED]`×`[LOCKED]` | Pembayaran pertama (ADDM/ADDB). |
| 75 | `tipe_guna` `varchar(10)` | CM.`asset_usage_type` `VARCHAR(10)` | `[INFERRED]`×`[INTENT]` | Peruntukan unit. |
| 76 | `is_new_price_list` `varchar(255)` | DROP | `[INFERRED]`×`[ARTIFACT]` | Flag versi price-list sebagai `varchar(255)`; pricing via master/`cfg_` versioned (konvensi §1). |
| 77 | `biaya_provisi` `numeric(18,0)` | CM.`provision_fee` `NUMERIC(18,2)` | `[VERIFIED]`×`[LOCKED]` | |
| 78 | `biaya_provisi_Ins` `numeric(18,0)` | INS.`insurance_provision_fee` `NUMERIC(18,2)` | `[VERIFIED]`×`[LOCKED]` | |
| 79 | `NIK_MH` `varchar(10)` | CM.`marketing_head_employee_id` `VARCHAR(10)` | `[INFERRED]`×`[INTENT]` | MH = Marketing Head (sensus role D-10). |
| 80 | `tipe_cover` `varchar(10)` | INS.`cover_type` `VARCHAR(10)` | `[VERIFIED]`×`[INTENT]` | COMPREHENSIVE/TLO dsb (§5.1). |
| 81 | `biaya_proses_id` `varchar(15)` | CM.`process_fee_scheme_id` `VARCHAR(15)` | `[VERIFIED]`×`[INTENT]` | |
| 82 | `nominal_biaya_proses` `numeric(18,0)` | CM.`process_fee` `NUMERIC(18,2)` | `[VERIFIED]`×`[LOCKED]` | |
| 83 | `package_pembiayaan_secq` `bit` | STG | `[INFERRED]`×`[OPEN]` | OQ-CMPO-13. |
| 84 | `paket_pembiayaan_id` `char(2)` | CM.`financing_package_id` `VARCHAR(2)` | `[VERIFIED]`×`[INTENT]` | |
| 85 | `biaya_proses_seq` `varchar(20)` | STG | `[INFERRED]`×`[OPEN]` | OQ-CMPO-13. |
| 86 | `is_TAC` `varchar(20)` | CM.`is_tac` `VARCHAR(20)` → `BOOLEAN` | `[INFERRED]`×`[INTENT]` | Skema insentif TAC dealer; kardinalitas nilai `[OPEN]` — konversi boolean bila terbukti biner saat migrasi. |
| 87 | `TAC_max` `numeric(18,0)` | CM.`tac_max` `NUMERIC(18,2)` | `[INFERRED]`×`[INTENT]` | |
| 88 | `komper_max` `varchar(20)` | STG | `[INFERRED]`×`[OPEN]` | OQ-CMPO-13. |
| 89 | `leasing_code` `varchar(5)` | CM.`leasing_code` `VARCHAR(5)` | `[VERIFIED]`×`[INTENT]` | Semantik (joint-financing?) `[OPEN]`. |
| 90 | `refund_bunga` `numeric(18,0)` | CM.`dealer_interest_refund` `NUMERIC(18,2)` | `[VERIFIED]`×`[INTENT]` | |
| 91 | `refund_bunga_percent` `float` | CM.`dealer_interest_refund_rate` `NUMERIC(9,6)` | `[VERIFIED]`×`[INTENT]` | |
| 92 | `interest_rate_type_id` `varchar(10)` | CM.`interest_rate_type_id` `VARCHAR(10)` | `[VERIFIED]`×`[INTENT]` | |
| 93 | `CGSCabangNo` `varchar(10)` | STG | `[INFERRED]`×`[OPEN]` | Paralel `tr_general_deviation.CGS_no` (OQ-GAP-04); OQ-CMPO-13. |
| 94 | `is_avalis` `bit` | CM.`is_avalist` `BOOLEAN` | `[INFERRED]`×`[INTENT]` | Penjaminan avalis. |
| 95 | `installment_type` `varchar(1)` | PAY.`installment_type` `VARCHAR(1)` + `CHECK` | `[VERIFIED]`×`[INTENT]` | ADDM/ADDB `[INFERRED]`; enum final `[OPEN]` OQ-CMPO-13. |
| 96 | `nominal_biaya_proses_kredit` `numeric(18,0)` | PAY.`process_fee_financed` `NUMERIC(18,2)` | `[INFERRED]`×`[INTENT]` | Porsi fee yang dikreditkan (vs tunai). |
| 97 | `admin_fee_kredit` `numeric(18,0)` | PAY.`admin_fee_financed` `NUMERIC(18,2)` | `[INFERRED]`×`[INTENT]` | idem. |
| 98 | `loss_fee` `numeric(18,0)` | INS.`loss_fee` `NUMERIC(18,2)` | `[VERIFIED]`×`[INTENT]` | Duplikat `tr_CM_Insurance.LossFee` — otoritatif `[OPEN]`; konsolidasi saat migrasi (§3.3). |
| 99 | `loss_fee_kredit` `numeric(18,0)` | PAY.`loss_fee_financed` `NUMERIC(18,2)` | `[INFERRED]`×`[INTENT]` | |
| 100 | `biaya_provisi_kredit` `numeric(18,0)` | PAY.`provision_fee_financed` `NUMERIC(18,2)` | `[INFERRED]`×`[INTENT]` | |
| 101 | `biaya_provisi_ins_kredit` `numeric(18,0)` | PAY.`insurance_provision_fee_financed` `NUMERIC(18,2)` | `[INFERRED]`×`[INTENT]` | |
| 102 | `customer_pay_amount` `numeric(18,0)` | PAY.`customer_pay_amount` `NUMERIC(18,2)` | `[VERIFIED]`×`[INTENT]` | |
| 103 | `residual_value` `numeric(18,0)` | CM.`residual_value` `NUMERIC(18,2)` | `[VERIFIED]`×`[LOCKED]` | Nilai sisa kontraktual. |
| 104 | `discount_dealer` `numeric(18,0)` | CM.`dealer_discount` `NUMERIC(18,2)` | `[VERIFIED]`×`[INTENT]` | |
| 105 | `branch_id` `varchar(5)` | CM.`branch_id` `VARCHAR(5)` | `[VERIFIED]`×`[INTENT]` | |
| 106 | `branch_id_first` `varchar(5)` | CM.`originating_branch_id` `VARCHAR(5)` | `[VERIFIED]`×`[INTENT]` | Cabang asal. |
| 107 | `is_life_ins` `varchar(1)` | INS.`has_life_insurance` `BOOLEAN NOT NULL DEFAULT false` | `[VERIFIED]`×`[INTENT]` | Flag; detail di `trx_credit_memo_insurance_life` (§3.3). |
| 108 | `is_topup_ms` `bit` | CM.`is_topup_mega_solusi` `BOOLEAN` | `[VERIFIED]`×`[INTENT]` | Menyerap fungsi `Tr_TopUpMegaSolusi` (§3.10; OQ-GAP-08). |
| 109 | `PO_no` `varchar(25)` | DROP | `[VERIFIED]`×`[ARTIFACT]` | GOTCHA-6 / BR-CMPO-16 — selalu ditulis NULL + denormalisasi `tr_PO`; derive via `trx_purchase_order.credit_id`. |
| 110 | `company_id` `varchar(3)` | CM.`company_id` `VARCHAR(5)` | `[VERIFIED]`×`[INTENT]` | Panjang legacy inkonsisten (`tr_PO` varchar(5), `tr_CM_UMC` int) — distandarkan `VARCHAR(5)` lintas tabel. |
| 111 | `STNK_status_id` `varchar(2)` | CM.`stnk_status_id` `VARCHAR(2)` | `[VERIFIED]`×`[INTENT]` | |
| 112 | `risk_category_id` `varchar(5)` | CM.`risk_category_id` `VARCHAR(5)` | `[VERIFIED]`×`[INTENT]` | Kunci routing komite (D-01 S10) bersama `trans_type_id` + Plafond OP. |
| 113 | `old_npp` `varchar(20)` | CM.`previous_agreement_id` `VARCHAR(20)` | `[VERIFIED]`×`[INTENT]` | RO/top-up; juga pengganti `Tr_TopUpMegaSolusi.NPP_Old` (§3.10). |
| 114 | `ownership_account_type_id` `varchar(10)` | DSB.`ownership_account_type_id` `VARCHAR(10)` | `[VERIFIED]`×`[LOCKED]` | Duplikat kolom sama di `tr_CM_UMC` — konsolidasi satu kolom di §3.4; otoritatif `[OPEN]` (bandingkan nilai saat migrasi). |
| 115 | `bank_account_id_umc` `varchar(30)` | DROP | `[VERIFIED]`×`[ARTIFACT]` | Duplikat `tr_CM_UMC.bank_account_id_umc` → DSB (§3.4). |
| 116 | `BPKB_invoice` `bit` | CM.`bpkb_invoice` `BOOLEAN` | `[INFERRED]`×`[INTENT]` | |
| 117 | `AR` `char(1)` | STG | `[INFERRED]`×`[OPEN]` | Juga di `tr_send_PO_log.ar`; OQ-CMPO-13. |
| 118 | `is_package_product` `bit` | CM.`is_package_product` `BOOLEAN` | `[VERIFIED]`×`[INTENT]` | |
| 119 | `package_product_amount` `numeric(21,0)` | CM.`package_product_amount` `NUMERIC(21,2)` | `[VERIFIED]`×`[INTENT]` | |
| 120 | `is_TopUp_Type` `varchar(3)` | CM.`topup_type` `VARCHAR(3)` | `[VERIFIED]`×`[INTENT]` | Enum `[OPEN]`. |
| 121 | `is_health_ins` `varchar(1)` | INS.`has_health_insurance` `BOOLEAN NOT NULL DEFAULT false` | `[VERIFIED]`×`[INTENT]` | Detail di `trx_credit_memo_insurance_health` (§3.3). |
| 122 | `health_insurance_fee` `numeric(18,0)` | INS.`health_insurance_fee` `NUMERIC(18,2)` | `[VERIFIED]`×`[LOCKED]` | |
| 123 | `sub_dealer_code` `varchar(15)` | CM.`sub_dealer_id` `VARCHAR(15)` | `[VERIFIED]`×`[INTENT]` | |
| 124 | `bpkb_invoice_type` `varchar(10)` | CM.`bpkb_invoice_type` `VARCHAR(10)` | `[INFERRED]`×`[INTENT]` | |

**Ringkasan normalisasi `tr_CM` (124/124 ter-disposisi)**: **74 → `trx_credit_memo`** (audit 4 + inti
finansial/identitas/status/aset/org — termasuk 6 figur frozen `OP/ULI/LCR/first_*` `[LOCKED]`),
**14 → `trx_credit_memo_payment`**, **8 → `trx_credit_memo_insurance`**,
**1 → `trx_credit_memo_disbursement`** (#114), **3 → `log_*`** (#41, #64, #65),
**2 → transfer modul collections** (#56, #60), **15 → `stg_legacy_tr_cm` `[OPEN]`** (#32, #40, #44, #45,
#54, #58, #59, #66, #68, #71, #83, #85, #88, #93, #117 — OQ-CMPO-13),
**7 → DROP `[ARTIFACT]`** (#38, #39, #43, #55, #76, #109, #115).
**Disposisi migrasi**: 1 baris `tr_CM` → 1 baris CM + 1 baris PAY + 1 baris INS (+ baris log bila
`usr_correction` terisi); nilai uang whole-rupiah disimpan `.00` dan direkonsiliasi bulat (konvensi §3).

### 3.2 `trx_credit_memo_payment` + `trx_credit_memo_deposit_period`

**`trx_credit_memo_payment`** — struktur pembayaran per memo (1:1). **Mapping asal**: 14 kolom pembayaran
`tr_CM` (alias PAY di census §3.1.2) + **fold** `tr_cm_deposit_installment` (header 1:1 by `credit_id`;
`FC_ACQ_MCF 2.sql:8606+`). Kunci: `id` PK, `credit_memo_id` FK → `trx_credit_memo` + unique.

Census fold `tr_cm_deposit_installment` (7 kolom):

| Kolom legacy (tipe) | Target | Marker | Catatan / disposisi |
|---|---|---|---|
| `created_by/on`, `last_updated_by/on` | diserap kolom audit PAY | `[VERIFIED]`×`[INTENT]` | Konvensi §4. |
| `credit_id` `varchar(20)` PK | via PAY.`credit_memo_id` FK | `[VERIFIED]`×`[LOCKED]` | |
| `deposit_month` `int` | PAY.`deposit_installment_months` | `[VERIFIED]`×`[LOCKED]` | Konsolidasi dgn `tr_CM.deposit_installment` (census #16); selisih nilai = temuan migrasi `[OPEN]`. |
| `deposit_amount` `numeric(21,2)` | PAY.`deposit_amount` `NUMERIC(18,2)` | `[VERIFIED]`×`[LOCKED]` | Konsolidasi dgn `tr_CM.deposit` (#17); `= deposit_month × amount_installment` (`sp_insert_cm`). |

**`trx_credit_memo_deposit_period`** — detail per-periode skema deposit installment (1:N). **Mapping
asal**: `tr_cm_deposit_installment_detail` (12 kolom; `FC_ACQ_MCF 2.sql:8644-8664`; gap-entities §2.10).

| Kolom legacy (tipe) | Target | Marker | Catatan / disposisi |
|---|---|---|---|
| `created_by/on`, `last_updated_by/on` | audit konvensi §4 | `[VERIFIED]`×`[INTENT]` | |
| `credit_id` `varchar(20)` (PK-1) | `credit_memo_id` FK; `ux(credit_memo_id, period_sequence)` | `[VERIFIED]`×`[LOCKED]` | Join ke spine. |
| `period_secquence` `int` (PK-2) | `period_sequence` `INTEGER` | `[VERIFIED]`×`[INTENT]` | Typo legacy "secquence" TIDAK direplikasi. |
| `cash_id` `varchar(20)` | `cash_reference_id` `VARCHAR(20)` | `[VERIFIED]`×`[INTENT]` | Kaitan kas penerimaan deposit `[INFERRED]`. |
| `deposit_month` `int` | `deposit_months` `INTEGER` | `[VERIFIED]`×`[INTENT]` | Disalin dari header. |
| `deposit_amount` `numeric(21,2)` | `deposit_amount` `NUMERIC(18,2)` | `[VERIFIED]`×`[INTENT]` | |
| `verify_user` `varchar(20)` / `verify_date` `datetime` | `verified_by` `VARCHAR(50)` / `verified_at` `TIMESTAMPTZ` | `[VERIFIED]`×`[INTENT]` | Jejak maker-checker penerimaan deposit. |
| `is_used_deposit` `bit` NOT NULL | `is_deposit_used` `BOOLEAN NOT NULL DEFAULT false` | `[VERIFIED]`×`[INTENT]` | Konsumen di modul servicing `[OPEN]` — koordinasi lintas-konteks saat rebuild. |

**Disposisi migrasi**: legacy delete+regenerate via loop `sp_insert_cm`/`sp_insert_cm_pilot` → rebuild:
regenerasi idempotent oleh service saat struktur kredit berubah, di bawah guard editability BR-CMPO-1.

### 3.3 Grup asuransi (lock D-01 S12)

> **RESOLVED by convention (2026-07-14) — eksekutor lock/freeze lintas 03↔04**: kolom `locked_at`/`locked_by`
> pada kedua sub-record asuransi di bawah **diisi oleh 04 saat konsumsi event `MemoApproved`** dari 03
> (BE-03 §11 **OQ-BE03-02 → opsi b**). Dasar: **ADR-03** write-by-owner (03 tidak menulis tabel milik 04) +
> **ADR-04** outbox. Freeze/lock bersifat **eventually-consistent** dalam transaksi konsumsi event (§5.3).

**`trx_credit_memo_insurance`** — seleksi/ringkasan asuransi per memo (1:1). **Mapping asal**: 8 kolom
asuransi `tr_CM` (alias INS di census §3.1.2: `insurance_fee`, `insurance_fee_gross`,
`insurance_provision_fee`, `cover_type`, `loss_fee`, `has_life_insurance`, `has_health_insurance`,
`health_insurance_fee`). Kolom target baru: `has_vehicle_insurance BOOLEAN NOT NULL DEFAULT false`
`[KEPUTUSAN DESAIN BARU]` — migrasi = EXISTS baris `tr_CM_Insurance` (legacy implisit via keberadaan row).

**`trx_credit_memo_insurance_vehicle`** — sub-record asuransi kendaraan. **Mapping asal**: `tr_CM_Insurance`
(25 kolom). Premium dari rate-master ber-referensi **OJK** (`sp_generate_insurance_cost_R4_OJK` /
`sp_generate_estimated_insurance_cost_R2`) — `[LOCKED]` metodologi rate-table (BR-CMPO-13).

| Kolom legacy (tipe) | Target | Marker | Catatan / disposisi |
|---|---|---|---|
| `created_by/on`, `last_updated_by/on` (4) | audit konvensi §4 | `[VERIFIED]`×`[INTENT]` | |
| `credit_id` `varchar(20)` PK | `credit_memo_id` FK + unique | `[VERIFIED]`×`[LOCKED]` | 1:1 per memo. |
| `ACP` `char(1)` | `acp` `VARCHAR(1)` | `[VERIFIED]`×`[OPEN]` | Ekspansi "ACP" tak ditemukan — OQ-CMPO-13. |
| `InsTipeBayar` `char(1)` | `payment_method` `VARCHAR(1)` | `[INFERRED]`×`[INTENT]` | Tunai/kredit; enum `[OPEN]`. |
| `InsModel` `int` | `insurance_model` `INTEGER` | `[VERIFIED]`×`[INTENT]` | Model/tier premi. |
| `TLO1`..`TLO5` (5 × `bit`) | → `trx_credit_memo_insurance_cover_year` rows `cover_type='TLO'` | `[VERIFIED]`×`[ARTIFACT]` (bentuk) | Kolom polimorfik posisi dilarang (konvensi §6 #4); outcome cakupan-per-tahun `[INTENT]`. |
| `AllRisk1`..`AllRisk5` (5 × `bit`) | → rows `cover_type='ALLRISK'` | `[VERIFIED]`×`[ARTIFACT]` (bentuk) | idem. |
| `LossFee` `numeric(18,0)` | `loss_fee` `NUMERIC(18,2)` | `[VERIFIED]`×`[LOCKED]` | Konsolidasi dgn `tr_CM.loss_fee` (census #98) — otoritatif `[OPEN]`. |
| `CPFee` `numeric(18,0)` | `cp_fee` `NUMERIC(18,2)` | `[INFERRED]`×`[OPEN]` | Ekspansi "CP" tak ditemukan — OQ-CMPO-13. |
| `PolicyFee` `numeric(18,0)` | `policy_fee` `NUMERIC(18,2)` | `[VERIFIED]`×`[LOCKED]` | Biaya polis. |
| `TahunKredit` `int` / `TahunTunai` `int` | `years_financed` / `years_cash` `INTEGER` | `[INFERRED]`×`[INTENT]` | Tahun cover dibayar kredit vs tunai. |
| `KreditFee` / `TunaiFee` `numeric(18,0)` | `premium_financed` / `premium_cash` `NUMERIC(18,2)` | `[INFERRED]`×`[INTENT]` | |
| *(baru)* | `locked_at` `TIMESTAMPTZ NULL` / `locked_by` `VARCHAR(50) NULL` | `[KEPUTUSAN DESAIN BARU]`; outcome `[LOCKED]` | Lock saat `MemoApproved` (GT STEP 12; D-01 S12); mutasi pasca-lock → `409 INSURANCE_LOCKED` sampai memo `corrected` (BR-CMPO-22). |

**`trx_credit_memo_insurance_cover_year`** *(baru — normalisasi typed rows)*: `credit_memo_id` FK,
`year_sequence SMALLINT` (1–5), `cover_type VARCHAR + CHECK (TLO|ALLRISK)`,
`ux(credit_memo_id, year_sequence)`. **Migrasi**: `TLOn=1` → row `(n,'TLO')`; `AllRiskn=1` → row
`(n,'ALLRISK')`; keduanya `1` pada tahun sama = temuan migrasi `[OPEN]`.

**`trx_credit_memo_insurance_life`** — life-insurance-on-credit (lini car). **Mapping asal**:
`tr_CM_life_insurance_credit` (16 kolom; `TrCmLifeInsuranceCredit` — lock STEP 12).

| Kolom legacy (tipe) | Target | Marker | Catatan / disposisi |
|---|---|---|---|
| `created_by/on`, `last_updated_by/on` (4) | audit konvensi §4 | `[VERIFIED]`×`[INTENT]` | |
| `credit_id` `varchar(14)` PK | `credit_memo_id` FK + unique | `[VERIFIED]`×`[LOCKED]` | Panjang legacy 14 ≠ 20 (inkonsisten skema) — target seragam via FK. |
| `Total_Fee` `numeric(18,0)` | `total_fee` `NUMERIC(18,2)` | `[VERIFIED]`×`[LOCKED]` | |
| `Tahun_Tunai` `nvarchar(255)` | `years_cash` `INTEGER` | `[VERIFIED]`×`[ARTIFACT]` (tipe) | `nvarchar` untuk tahun — cast + validasi migrasi. |
| `Tunai_Fee` `numeric(18,0)` | `premium_cash` `NUMERIC(18,2)` | `[VERIFIED]`×`[INTENT]` | |
| `Tahun_Kredit` `nvarchar(255)` | `years_financed` `INTEGER` | `[VERIFIED]`×`[ARTIFACT]` (tipe) | idem `Tahun_Tunai`. |
| `Kredit_Fee` `numeric(18,0)` | `premium_financed` `NUMERIC(18,2)` | `[VERIFIED]`×`[INTENT]` | |
| `Item_Code` `nvarchar(255)` | `plan_code` `VARCHAR(50)` | `[VERIFIED]`×`[INTENT]` | = `plan_code` §5.1. |
| `Item_Price` / `Vendor_Price` `numeric(18,0)` | `item_price` / `vendor_price` `NUMERIC(18,2)` | `[VERIFIED]`×`[INTENT]` | |
| `Total_Fee_Insurance` `numeric(18,0)` | `total_insurance_fee` `NUMERIC(18,2)` | `[VERIFIED]`×`[LOCKED]` | |
| `Persen_Premi` `float` | `premium_rate` `NUMERIC(9,6)` | `[VERIFIED]`×`[LOCKED]` | Presisi rate konvensi §3. |
| `Kredit_Fee_Vendor` `numeric(18,0)` | `vendor_premium_financed` `NUMERIC(18,2)` | `[INFERRED]`×`[INTENT]` | |
| *(baru)* | `locked_at` / `locked_by` | `[KEPUTUSAN DESAIN BARU]`; outcome `[LOCKED]` | Perlakuan lock sama dgn vehicle (GT STEP 12; D-01 S12). |

**`trx_credit_memo_insurance_health`** — asuransi kesehatan. **Mapping asal**: `tr_cm_health_insurance`
(8 kolom; heap TANPA PK — `[ARTIFACT]` bentuk → target `id` PK + FK NOT NULL).

| Kolom legacy (tipe) | Target | Marker | Catatan / disposisi |
|---|---|---|---|
| `created_by/on`, `last_updated_by/on` (4) | audit konvensi §4 | `[VERIFIED]`×`[INTENT]` | |
| `credit_id` `varchar(20)` **NULL** | `credit_memo_id` FK **NOT NULL** | `[VERIFIED]`×`[LOCKED]` | Nullable di legacy — baris yatim = temuan migrasi `[OPEN]`. |
| `start_cover_date` / `end_cover_date` `datetime` | `cover_start_date` / `cover_end_date` `DATE` | `[VERIFIED]`×`[INTENT]` | |
| `identity_number` `varchar(45)` NOT NULL | `insured_identity_number` `VARCHAR(16)` | `[VERIFIED]`×`[LOCKED]` | NIK = 16 (panjang regulatori, konvensi §3); legacy 45 — validasi/trim migrasi, nilai non-NIK = temuan `[OPEN]`. |

> Lock D-01 S12 hanya menyebut jiwa + kendaraan; sub-record health mengikuti guard editability memo
> BR-CMPO-1 `[INTENT]` (tidak diberi `locked_at` sendiri sampai dinyatakan lain).

### 3.4 `trx_credit_memo_disbursement` & `trx_credit_memo_bank_account`

**`trx_credit_memo_disbursement`** — rekening tujuan pencairan skema UMC (1:1). **Mapping asal**:
`tr_CM_UMC` (25 kolom). Kunci: `id` PK, `credit_memo_id` FK + unique.

| Kolom legacy (tipe) | Target | Marker | Catatan / disposisi |
|---|---|---|---|
| `created_by/on`, `last_updated_by/on` (4, `varchar(60)`) | audit konvensi §4 | `[VERIFIED]`×`[INTENT]` | |
| `credit_id` `varchar(20)` PK | `credit_memo_id` FK + unique | `[VERIFIED]`×`[LOCKED]` | |
| `account_name_umc` `varchar(100)` | `account_name` `VARCHAR(100)` | `[VERIFIED]`×`[LOCKED]` | Rekening pencairan — fraud-sensitive. |
| `bank_id_umc` `varchar(30)` | `bank_id` `VARCHAR(30)` | `[VERIFIED]`×`[LOCKED]` | |
| `account_no_umc` `varchar(30)` | `account_no` `VARCHAR(30)` | `[VERIFIED]`×`[LOCKED]` | PII rekening (PDP). |
| `is_same_KK` `bit` | `is_same_family_card` `BOOLEAN` | `[INFERRED]`×`[INTENT]` | |
| `scheme_id` `varchar(10)` | `scheme_id` `VARCHAR(10)` | `[VERIFIED]`×`[INTENT]` | |
| `is_personal_account` `varchar(10)` | `is_personal_account` `BOOLEAN` | `[VERIFIED]`×`[INTENT]` | Tipe varchar → boolean (konvensi §3). |
| `company_id` `int` NOT NULL | `company_id` `VARCHAR(5)` | `[VERIFIED]`×`[INTENT]` | Tipe `int` menyimpang dari varchar tabel lain — distandarkan. |
| `id_agent_type` `varchar(10)` / `agent_id` `varchar(20)` | `agent_type_id` / `agent_id` | `[INFERRED]`×`[INTENT]` | |
| `is_external` `bit` | `is_external` `BOOLEAN` | `[VERIFIED]`×`[INTENT]` | |
| `identity_number_of_STNK` `varchar(20)` | `stnk_identity_number` `VARCHAR(20)` | `[VERIFIED]`×`[LOCKED]` | Identitas pada STNK. |
| `agent_name` `varchar(100)` | `agent_name` `VARCHAR(100)` | `[INFERRED]`×`[INTENT]` | Snapshot nama agen; sumber master `[OPEN]`. |
| `ownership_account_type_id` `varchar(100)` | `ownership_account_type_id` `VARCHAR(10)` | `[VERIFIED]`×`[LOCKED]` | Konsolidasi dgn `tr_CM.ownership_account_type_id` (census #114); otoritatif `[OPEN]`. |
| `bank_account_id_umc` `varchar(100)` | `bank_account_id` `VARCHAR(30)` | `[VERIFIED]`×`[LOCKED]` | Konsolidasi dgn `tr_CM.bank_account_id_umc` (#115, di-drop di sana). |
| `bank_name_umc` `varchar(100)` | DROP | `[VERIFIED]`×`[ARTIFACT]` | Denormalisasi — derive dari `bank_id` → master bank (konvensi §6 #3). |
| `destination_bank_id_umc` / `destination_bank_account_id_umc` / `destination_account_no_umc` `varchar(30)`; `destination_account_name_umc` `varchar(100)` | `destination_bank_id` / `destination_bank_account_id` / `destination_account_no` / `destination_account_name` | `[VERIFIED]`×`[LOCKED]` | Set rekening tujuan akhir; fraud-sensitive. |
| `order_source_agent_id` `varchar(8)` | `order_source_agent_id` `VARCHAR(8)` | `[INFERRED]`×`[INTENT]` | |

**`trx_credit_memo_bank_account`** — rekening bank customer (input validasi DOKU §5.2; 1:1). **Mapping
asal**: `tr_CM_bank_account` (9 kolom).

| Kolom legacy (tipe) | Target | Marker | Catatan / disposisi |
|---|---|---|---|
| `created_by/on`, `last_updated_by/on` (4, `varchar(60)`) | audit konvensi §4 | `[VERIFIED]`×`[INTENT]` | |
| `credit_id` `varchar(20)` PK | `credit_memo_id` FK + unique | `[VERIFIED]`×`[LOCKED]` | |
| `account_owner_id` `varchar(30)` | `account_owner_id` `VARCHAR(30)` | `[VERIFIED]`×`[INTENT]` | |
| `bank_id` `varchar(30)` | `bank_id` `VARCHAR(30)` | `[VERIFIED]`×`[INTENT]` | Dipetakan SWIFT via `ms_DOKU_BankCodeMapping` saat inquiry (§3.8). |
| `account_no` `varchar(30)` / `account_name` `varchar(100)` | `account_no` / `account_name` | `[VERIFIED]`×`[LOCKED]` | PII rekening (PDP); diverifikasi DOKU account-validate. |

### 3.5 `trx_credit_memo_rate` & `trx_credit_memo_dp_subsidy`

**`trx_credit_memo_rate`** — rate per memo. **Mapping asal**: `tr_CM_rate` (7 kolom; PK legacy hanya
`credit_id` → efektif 1:1 meski konsepnya per-jenis-rate). Target: `id` PK + `credit_memo_id` FK +
`ux(credit_memo_id, rate_type_id)` (mengizinkan >1 jenis rate tanpa ganti kunci).

| Kolom legacy (tipe) | Target | Marker | Catatan / disposisi |
|---|---|---|---|
| `created_by/on`, `last_updated_by/on` (4) | audit konvensi §4 | `[VERIFIED]`×`[INTENT]` | |
| `credit_id` `varchar(20)` PK | `credit_memo_id` FK | `[VERIFIED]`×`[LOCKED]` | |
| `rate_type_id` `varchar(3)` | `rate_type_id` `VARCHAR(3)` | `[VERIFIED]`×`[INTENT]` | |
| `percentage_rate` `numeric(18,4)` | `percentage_rate` `NUMERIC(9,6)` | `[VERIFIED]`×`[LOCKED]` | Presisi rate konvensi §3. Perilaku legacy write bottom-rate hanya kode top-up/RO + delete kode lain (BR-CMPO-14) = `[OPEN]` OQ-CMPO-09 — JANGAN replikasi delete diam-diam; riwayat perubahan → log. |

**`trx_credit_memo_dp_subsidy`** — subsidi DP per jenis (1:N). **Mapping asal**: `tr_CM_subsidi_DP`
(8 kolom; PK komposit `credit_id`+`subsidi_DP_id`). Target: `id` PK +
`ux(credit_memo_id, subsidy_type_id)`.

| Kolom legacy (tipe) | Target | Marker | Catatan / disposisi |
|---|---|---|---|
| `created_by/on` (NOT NULL), `last_updated_by/on` | audit konvensi §4 | `[VERIFIED]`×`[INTENT]` | |
| `credit_id` `varchar(14)` (PK-1) | `credit_memo_id` FK | `[VERIFIED]`×`[LOCKED]` | Panjang legacy 14 (inkonsisten) — seragam via FK. |
| `subsidi_DP_id` `varchar(3)` (PK-2) | `subsidy_type_id` `VARCHAR(3)` | `[VERIFIED]`×`[INTENT]` | |
| `exclude_DP_gross` `bit` | `is_excluded_from_gross_dp` `BOOLEAN` | `[VERIFIED]`×`[INTENT]` | |
| `amount` `numeric(18,0)` | `amount` `NUMERIC(18,2)` | `[VERIFIED]`×`[LOCKED]` | Bagian struktur DP (aturan DP-minimum OJK). |

### 3.6 `trx_purchase_order` + `out_notification` + `log_po_email`

**`trx_purchase_order`** — pemilik mint + print + correction. **Mapping asal**: `tr_PO` (16 kolom;
`FC_ACQ_MCF 2.sql:6638-6665`).

| Kolom legacy (tipe) | Target | Marker | Catatan / disposisi |
|---|---|---|---|
| `created_by/on`, `last_updated_by/on` (4) | audit konvensi §4 | `[VERIFIED]`×`[INTENT]` | |
| `PO_no` `varchar(25)` PK | `po_number` `VARCHAR(25) NOT NULL` + `ux_trx_purchase_order_po_number` | `[VERIFIED]`×`[LOCKED]` | Business key — dirujuk sinkronisasi Passnet (eksternal). **Di-assign SAAT mint** (non-NULL; fix GOTCHA-6/BR-CMPO-16). PK teknis = `id`. |
| `credit_id` `varchar(20)` NOT NULL | `credit_id` `VARCHAR(20) NOT NULL` + FK → `trx_credit_memo(credit_id)` | `[VERIFIED]`×`[LOCKED]` | Legacy unenforced → declared FK (konvensi §2). |
| `PO_date` `datetime` | `po_date` `TIMESTAMPTZ` | `[VERIFIED]`×`[INTENT]` | = waktu mint (§5.3). |
| `company_id` `varchar(5)` NOT NULL | `company_id` `VARCHAR(5) NOT NULL` | `[VERIFIED]`×`[INTENT]` | |
| `branch_id` `varchar(5)` NOT NULL | `branch_id` `VARCHAR(5) NOT NULL` | `[VERIFIED]`×`[INTENT]` | |
| `status_PO` `char(1)` NOT NULL | `status` `VARCHAR(10) NOT NULL` + `CHECK (issued\|corrected\|fulfilled)` | `[VERIFIED]`×`[INTENT]` | Mapping legacy: open/printed → `issued` (+atribut print), reopened → `corrected` (§7.2). |
| `is_print` `bit` NOT NULL | DROP | `[VERIFIED]`×`[ARTIFACT]` | Derivable dari `print_count > 0`. |
| `sum_of_print` `int` NOT NULL | `print_count` `INTEGER NOT NULL DEFAULT 0` | `[VERIFIED]`×`[LOCKED]` | Audit trail BR-CMPO-9; increment **atomic** (fix race Edge Case 3). Penyimpangan sadar dari konvensi §6 #2 (larangan print-tracking bespoke) — dijustifikasi field `[LOCKED]`; riwayat per-event tetap ditulis ke `log_document_print` generik (definisi kanonik **BE-02 §3.11**; owner **cross-cutting** per catatan SHARED BE-00 §6.3 — catatan itu merujuk balik §3.6 ini sebagai pemakai 04). |
| `first_print_date` / `first_print_by` | `first_print_at` `TIMESTAMPTZ` / `first_print_by` `VARCHAR(50)` | `[VERIFIED]`×`[LOCKED]` | idem justifikasi. |
| `last_print_date` / `last_print_by` | `last_print_at` / `last_print_by` | `[VERIFIED]`×`[LOCKED]` | idem. |
| *(baru)* | `approval_decision_id` `VARCHAR NOT NULL` + `ux(credit_id, approval_decision_id)` | `[KEPUTUSAN DESAIN BARU]` | Kunci idempotensi mint — menjamin **"exactly one PO per approval"** (D-01 S13; BR-CMPO-24; §5.3). `credit_id` = kolom memo-ref tabel ini (padanan konseptual `memo_id` di §5.3/BR-CMPO-24). |
| *(baru)* | `document_uri` `VARCHAR NULL` | `[USULAN]`; outcome `[INTENT]` | Object-key PDF PO ter-render (konvensi §3 dokumen) — email PDF STEP 13 + download File-PO FE (`64 §11`). |

**`out_notification`** — outbox email PO ke dealer (kelas `out_`, ADR-04; dispatcher-only consumer).
**Mapping asal**: `tr_PO_send_to_email` (11 kolom).

| Kolom legacy (tipe) | Target | Marker | Catatan / disposisi |
|---|---|---|---|
| `created_by/on`, `last_updated_by/on` (4) | audit konvensi §4 | `[VERIFIED]`×`[INTENT]` | |
| `send_email_id` `int` IDENTITY PK | `id` `BIGINT` identity | `[VERIFIED]`×`[INTENT]` | |
| `PO_no` `varchar(25)` | `po_number` `VARCHAR(25)` + `ux(notification_type, po_number)` | `[VERIFIED]`×`[LOCKED]` | Unique = guard idempoten BR-CMPO-21 per `PO_no` (`email-sms-notifications.md §9`). |
| `branch_id` `varchar(5)` | `branch_id` `VARCHAR(5)` | `[VERIFIED]`×`[INTENT]` | |
| `dealer_code` `varchar(25)` | `recipient_dealer_id` `VARCHAR(25)` | `[VERIFIED]`×`[INTENT]` | Recipient di-resolve **server-side** dari master dealer terverifikasi (BR-CMPO-20) — alamat email TIDAK disimpan bebas di sini. |
| `file_path` `varchar(50)` / `file_name` `varchar(30)` | `attachment_object_key` `VARCHAR` | `[VERIFIED]`×`[ARTIFACT]` (bentuk) | Path lokal → object-key storage; isi = `document_uri` PO. |
| `is_send` `bit` | `status` `VARCHAR` + `CHECK (PENDING\|SENT\|FAILED)` | `[VERIFIED]`×`[INTENT]` | Outcome idempotency **WAJIB** (BR-CMPO-21); proyeksi API `is_send` = `status='SENT'` (§5.6). |
| *(baru)* | `notification_type` `VARCHAR NOT NULL` (nilai modul ini: `'PO_EMAIL_DEALER'`) | `[KEPUTUSAN DESAIN BARU]` | Diskriminator jenis notifikasi — `out_notification` adalah outbox generik lintas modul (ADR-04); komponen pertama `ux(notification_type, po_number)` guard idempoten BR-CMPO-21. |
| *(baru)* | `sent_to` `VARCHAR`, `sent_at` `TIMESTAMPTZ` | `[KEPUTUSAN DESAIN BARU]` | Jejak recipient terverifikasi + waktu kirim (kontrak §5.6). |

**`log_po_email`** — audit kirim email PO (append-only). **Mapping asal**: `tr_send_PO_log` (21 kolom).
Kolom dibawa: `id` (identity), `po_number` (`PO_no`), `credit_id`, `recipient_email` (`email`
`varchar(300)` — PII, retensi PDP), `created_by/created_at`, + `sent_at`/`status` hasil dispatch.
**15 kolom snapshot figur memo** (`credit_date`, `approved_date`, `item_id`, `item_merk_id`,
`item_merk_type_id`, `asset_cost`, `gross_down_payment`, `nett_down_payment`, `tenor` `tinyint`,
`amount_installment`, `effective_rate`, `flat_rate`, `jml_pembiayaan`, `dealer_code`, `ar`) =
`[VERIFIED]`×`[ARTIFACT]` denormalisasi isi email — **DI-DROP**; derive dari memo +
`trx_credit_memo_financing_snapshot` bila perlu re-render. **Disposisi**: legacy punya DUA send-log
berbeda bentuk (`tr_PO_send_to_email` + `tr_send_PO_log` — gotcha core-entities §7) → rebuild **satu**
jalur `out_notification` (dispatch/idempotensi) + `log_po_email` (audit) untuk kedua entry point
(§5.4 print-triggered & §5.6 re-send) — fix duplikasi `64 Edge Case 8`.

### 3.7 `trx_financed_asset` (unit fisik pada aplikasi)

**Mapping asal**: `tr_items` (12 kolom) + `tr_items_UMC` (11 kolom) — dua tabel paralel beda skema
pencairan (`FC_ACQ_MCF 2.sql:9073-9118`) → **merge-with-scheme-flag** (rekomendasi core-entities §6).
Kunci: `id` PK; `credit_id VARCHAR(20) NOT NULL`; `record_scheme VARCHAR + CHECK (STANDARD|UMC)`
`[KEPUTUSAN DESAIN BARU]`; `ux(credit_id, record_scheme)`. Legacy `tr_items` ber-FK declared ke `tr_NPP`.

| Kolom legacy (tipe) | Target | Marker | Catatan / disposisi |
|---|---|---|---|
| `created_by/on`, `last_updated_by/on` (4 × 2 tabel) | audit konvensi §4 | `[VERIFIED]`×`[INTENT]` | |
| `credit_id` `varchar(20)` PK (masing-masing) | `credit_id` + `record_scheme` | `[VERIFIED]`×`[LOCKED]` | |
| `chasis_no` / `chasis_no_UMC` `varchar(35)` | `chassis_no` `VARCHAR(35)` | `[VERIFIED]`×`[LOCKED]` | Identitas registrasi kendaraan (BPKB/STNK); typo legacy "chasis" tidak direplikasi. |
| `machine_no` / `machine_no_UMC` `varchar(35)` | `engine_no` `VARCHAR(35)` | `[VERIFIED]`×`[LOCKED]` | |
| `item_color` `varchar(20)` (hanya `tr_items`) | `color` `VARCHAR(20)` | `[VERIFIED]`×`[INTENT]` | NULL untuk baris scheme UMC. Basis koreksi Open CM "warna/tipe beda" (GT STEP 13). |
| `car_no_UMC` `varchar(14)` (kedua tabel) | `registration_no` `VARCHAR(14)` | `[VERIFIED]`×`[OPEN]` | Semantik vs plat 3-kolom `[OPEN]` — OQ-CMPO-13. |
| `prefix_plat` / `plat_no` / `plat_code` `varchar(5)` | `plate_prefix` / `plate_no` / `plate_region_code` `VARCHAR(5)` | `[VERIFIED]`×`[LOCKED]` | 3 kolom = konvensi format plat Indonesia (bukan error — core-entities §6). |

Diisi/diubah via §5.1 `vehicle_registration` (jalur UMC/third-party); validasi BAST + chassis/engine
= milik 05 (STEP 15).

### 3.8 `trx_bank_account_inquiry` + `log_doku_api` (seam DOKU)

**`trx_bank_account_inquiry`** — cache request+response inquiry rekening bank pra-pencairan (own
response persist, fix BR-CMPO-18/Edge Case 6.1; `sp_check_Rek_Doku` membaca baris sukses sebelum call
API). **Mapping asal**: `DOKU_Inquiry_Account_Bank_Check` (23 kolom; `FC_ACQ_MCF 2.sql:7215-7241`;
gap-entities §2.16). Write-back owner = `[OPEN]` OQ-DOKU-01.

| Kolom legacy (tipe) | Target | Marker | Catatan / disposisi |
|---|---|---|---|
| `idx` `varchar(8)` | DROP → `id` `BIGINT` identity | `[VERIFIED]`×`[ARTIFACT]` | Increment manual `top 1 idx+1` = race — konvensi §6 #5. |
| `companyId` `varchar(3)` | `company_id` `VARCHAR(5)` | `[VERIFIED]`×`[INTENT]` | |
| `agreementNumber` `varchar(20)` | `credit_id` `VARCHAR(20)` | `[VERIFIED]`×`[LOCKED]` | Nama legacy **menyesatkan** — diisi `@creditId` oleh `sp_check_Rek_Doku` (gap-entities §2.16); rename memperbaiki semantik. |
| `nppApproveDate` `datetime` | `npp_approved_at` `TIMESTAMPTZ` | `[VERIFIED]`×`[INTENT]` | Konteks inquiry pasca-approval NPP menjelang pencairan `[INFERRED]`. |
| `bankCode` `varchar(10)` | `bank_swift_code` `VARCHAR(10)` | `[VERIFIED]`×`[INTENT]` | SWIFT dari `ms_DOKU_BankCodeMapping`. |
| `bankID` `varchar(10)` / `bankName` `varchar(255)` | `bank_id` / `bank_name` | `[VERIFIED]`×`[INTENT]` | Snapshot request. |
| `accCity` / `accName` `varchar(255)` / `accNo` `varchar(50)` | `account_city` / `account_name` / `account_no` | `[VERIFIED]`×`[INTENT]` | Rekening yang diklaim; PII (PDP). |
| `senderAmount` `bigint` | `sender_amount` `NUMERIC(18,2)` | `[VERIFIED]`×`[INTENT]` | |
| `phoneNumber` `varchar(15)` | `phone_number` `VARCHAR(15)` | `[VERIFIED]`×`[LOCKED]` | PII verifikasi pemilik. |
| `birthDate` `datetime` | `birth_date` `DATE` | `[VERIFIED]`×`[LOCKED]` | PII. |
| `personalId` `varchar(20)` | `identity_number` `VARCHAR(16)` | `[VERIFIED]`×`[LOCKED]` | NIK — panjang 16 regulatori (konvensi §3); legacy 20 → validasi migrasi. |
| `usrCrt` `varchar(10)` / `dtmCrt` `datetime` | `created_by` / `created_at` | `[VERIFIED]`×`[INTENT]` | |
| `responseStatus` `varchar(5)` | `response_status` `VARCHAR(5)` | `[VERIFIED]`×`[INTENT]` | `'0'` = match — arti persis `[OPEN]` OQ-DOKU-04. |
| `responseMessage` `varchar(100)` | `response_message` `VARCHAR(100)` | `[VERIFIED]`×`[INTENT]` | |
| `responseBankID` / `responseBankCode` `varchar(10)` / `responseBankName` `varchar(255)` | `response_bank_id` / `response_bank_swift_code` / `response_bank_name` | `[VERIFIED]`×`[INTENT]` | |
| `responseAccNo` `varchar(50)` / `responseAccName` `varchar(255)` | `response_account_no` / `response_account_name` | `[VERIFIED]`×`[INTENT]` | Dibandingkan vs request untuk vonis valid; response **di-own** (fix Edge Case 6.1). Filter anomali legacy (`accName NOT LIKE '%uang%'`) TIDAK direplikasi hardcoded → rule validasi eksplisit `[INTENT]`. |

**Disposisi**: retensi PII (UU PDP) = **OQ-GAP-11** (§11).

**`log_doku_api`** *(kondisional)* — **Mapping asal**: `DOKU_API_Log` (10 kolom) `[ARTIFACT: log]`
(insert-only via `sp_Insert_API_DOKU_Log_Error`; tanpa reader). **Disposisi default**: DIGANTI structured
logging app-tier di ACL DOKU (§8); tabel target hanya dibuat bila retensi regulatori menuntut
(**OQ-GAP-11**). Bila dibawa: `logId` `varchar(15)` manual-increment → `id BIGINT` identity (fix race —
konvensi §6 #5, larangan eksplisit); `id` → `correlation_id`; `userID` → `created_by`;
`method`/`menu`/`menuDetail` → `method`/`menu`/`menu_detail`; `header`/`param`/`result` `varchar(max)` →
`TEXT` (redaksi PII sebelum simpan); `logDate` → `created_at`. Append-only (konvensi §1).

### 3.9 `log_credit_memo_reopen` & `trx_credit_memo_financing_snapshot`

**`log_credit_memo_reopen`** — audit Open CM / koreksi PO (append-only; merekam aksi berisiko unlock
kontrak). **Mapping asal**: `log_open_transaction` (5 kolom; writer `sp_trans_open_cm` — insert
`('SP OPEN CM', getdate(), @creditId, '3')` setelah menghapus `tr_CM_Fincore`) + kebutuhan baru
entitas `PO_CORRECTION_LOG` (`23 §6`).

| Kolom legacy (tipe) | Target | Marker | Catatan / disposisi |
|---|---|---|---|
| `created_by` `varchar(15)` NOT NULL / `created_on` `datetime` NOT NULL | `created_by` / `created_at` | `[VERIFIED]`×`[INTENT]` | Konvensi `log_` (§4) — tanpa `updated_*`. |
| `log_id` `bigint` IDENTITY PK | `id` `BIGINT` identity | `[VERIFIED]`×`[INTENT]` | |
| `credit_id` `varchar(20)` NOT NULL | `credit_id` `VARCHAR(20) NOT NULL` | `[VERIFIED]`×`[LOCKED]` | |
| `menu_id` `int` NOT NULL | `source_menu_id` `INTEGER` | `[VERIFIED]`×`[OPEN]` | Enumerasi `[OPEN]` (legacy `'3'` via `sp_trans_open_cm`; gap-entities §3). |
| *(baru)* | `po_number` `VARCHAR(25)`, `reason` `TEXT`, `return_target` `VARCHAR` (per OQ-GT-03), `print_history_snapshot_ref` `VARCHAR` | `[KEPUTUSAN DESAIN BARU]` | Payload koreksi §5.5 (preserve audit print — fix Edge Case 4); juga menampung `usr_correction`/`dtm_correction` `tr_CM` (census #64–65). |

**`trx_credit_memo_financing_snapshot`** (+ arsip `log_credit_memo_financing_snapshot`) — snapshot
point-in-time figur financing saat approval (price, DP, fees, rate). **Mapping asal**: `tr_CM_Fincore`
(legacy — di luar 20 tabel census modul; writer `sp_approve_cm` terminal-approve, **dikecualikan** kode
produk marketing `'098'` = intensionalitas `[OPEN]`; dihapus `sp_trans_open_cm` saat Open CM). Field =
subset figur finansial CM (§3.1.2) — 1:1 mengikuti kolom sumber. `[INTENT]` — outcome frozen baseline
wajib (`23 §5.7`); mekanisme delete-and-restore legacy **DIGANTI**: versi lama diarsipkan ke
`log_credit_memo_financing_snapshot` (append-only), BUKAN delete (konvensi §6 #1 — larangan shadow/versioning
destruktif). Downstream dibaca 05 (NPP) via PULL (D-01 S15).

### 3.10 Tabel legacy TIDAK dibawa (discard register)

| Tabel legacy (kolom) | Klasifikasi | Bukti | Disposisi |
|---|---|---|---|
| `Tr_TopUpMegaSolusi` (8: audit 4, `Company_id` `varchar(3)`, `Branch_id` `varchar(5)`, `credit_id` `varchar(20)`, `NPP_Old` `varchar(10)`) | `[ARTIFACT — discard: disabled]` | Satu-satunya insert di `sp_approve_npp` di-comment-out (anotasi `--- Tasya`, dump @14350/@15094); 0 reader (gap-entities §2.13) | **TIDAK dibawa.** Fungsi terserap `trx_credit_memo.is_topup_mega_solusi` + `previous_agreement_id` (census #108/#113). Status fitur + data historis yang mungkin perlu dimigrasikan = **OQ-GAP-08** (§11) — konfirmasi sebelum drop final. |
| `produk_other_income_skema_III` (9: `cm_no` `varchar(50)`, `paket_produk_id` `int`, `nilai_beli`/`nilai_jual` `decimal(18,2)`, `alasan_id` `varchar(5)`, audit 4 varian `create_by`) | `[ARTIFACT — discard: dead/vestigial]` | **0 referensi** SP + kode .NET (gap-entities §2.15); definisi "skema III" tak ditemukan `[OPEN]` | **TIDAK dibawa.** Konfirmasi drop tercatat OQ-GAP-09 (gap-entities §4; P3 — bukan blocker PRD ini). |

### 3.11 Shared entities yang DIRUJUK (bukan milik 04)

| Entitas | Pemilik | Dipakai 04 untuk |
|---|---|---|
| `CREDIT_APPLICATION` | 01-intake | Header + seed nilai finalisasi (mekanisme seed = `[OPEN]` OQ-CMPO-08; catatan v2: STEP 8 sync MOOFI→FINCORE membentuk draft kontrak berisi data applicant/penjamin/kapasitas bayar/struktur pinjaman — kandidat kuat sumber seed, konfirmasi tetap di 01). |
| `CREDIT_ANALYSIS` / `trans_type_id` | 02 | `trans_type_id` (routing key) dibawa apa adanya. |
| `APPROVAL_STEP` / `APPROVAL_HISTORY` (audit `tr_hierarchy_transaction`) | 03 | Sumber event `MemoApproved` (GT STEP 12). |
| `DEALER` / `ASSET` / OTR-price master | masters (read-only; D-08 Menu Master dalam scope rebuild, dimiliki modul master) | Identifikasi dealer/aset, lookup harga OTR (`sp_get_Harga_OTR`); **alamat email dealer terverifikasi** untuk kirim PDF PO (BR-CMPO-20). |
| `FINANCING_AGREEMENT` | 05-npp | Konsumen downstream memo terfinalisasi + frozen figures (PULL, D-01 S15). |
| `TrVerificationCustomer` (Vertel) | 05 (STEP 14, D-02) | Antrean Vertel ter-feed setelah `POIssued`; 04 tidak menulis. |
| `RAC_SCREENING` | 02 | Koreksi meng-emit event re-screen (bukan DELETE lintas-DB — GOTCHA-11). |

---

## 4. API Endpoint

> **Bahasa BE = Java** `[LOCKED]` (D-12). Transport/protocol (REST vs gRPC vs bus) & framework masih `[OPEN]`
> (menunggu ITEC, D-11; lihat OQ-ARCH-STACK di §11 — sudah **ter-narrow** oleh D-12). Kontrak ditulis level
> **resource + field**; HTTP method/path adalah representasi, bukan penguncian REST.

| Method | Path | Deskripsi | Auth/Role (D-10) |
|---|---|---|---|
| `GET` | `/credit-memos/{memoId}` | Baca memo + frozen figures + insurance sub-record. | CMO / Credit (Admin) / Kepala Cabang |
| `PUT` | `/credit-memos/{memoId}/finalization` | Finalisasi/revisi struktur finansial (payment option, Upping OTR, DP, fees, seleksi asuransi). **Guard**: `status ∈ {draft, corrected}`. **Catatan v2**: pada alur final entry dilipat ke moofi (§1.0); endpoint ini melayani jalur koreksi + jalur non-moofi (= jalur manual web, OQ-GT-01 ✅ RESOLVED — evidence; scope port = keputusan desain, §11). | CMO / Credit (Admin) |
| `GET` | `/credit-memos/{memoId}/insurance-quote` | Hitung premium vehicle/life per lini (setiap lini **always-resolving** — fix silent-zero BR-CMPO-12). Dipakai FE untuk auto-refetch premi on-change OTR/tenor/asset (`64 BR-CMPOFE-5`). | CMO / Credit (Admin) |
| `POST` | `/credit-memos/{memoId}/calculation-preview` | **[USULAN]** Simulasi kalkulasi net-DP / financed amount / installment server-side — pengganti kalkulasi client-side legacy (`64 BR-CMPOFE-3`, Edge Case 9 string-parsing); satu sumber formula dengan freeze §5.3 agar preview FE = angka yang dibekukan. | CMO / Credit (Admin) |
| `GET` | `/credit-memos/{memoId}/bank-account:validate` | Validasi rekening bank via **DOKU ACL** (sync). Query: `bank_id`, `account_no`. | CMO / Credit (Admin) |
| `EVENT` | *(subscribe)* `MemoApproved` → **freeze `OP`/`ULI`/`LCR` + lock asuransi + mint PO** | Handler internal **idempotent** (key = `memo_id` + `approval_decision_id`). `MemoApproved` **approve-only**; correction/reject via event komite terpisah (OQ-CMPO-11), **bukan** handler ini. Mint tunggal — **tepat satu PO per approval** (D-01 S13), semua terminasi hierarki, source-agnostic (moofi / non-moofi / Instant-Approval lane D-01 S11). | Internal (system) |
| `GET` | `/purchase-orders` | **[USULAN]** Listing PO/acquisition utk layar listing FE (kolom: `po_number`, `status`, `print_count`, "Jumlah Send Mail", status memo/CA) — grounded pada layar `Index.cshtml`/`IndexMotor.cshtml` (`64 §11`). Filter: branch, status, product_line, tanggal. | Credit (Admin) / Kepala Cabang |
| `GET` | `/credit-memos/{memoId}/purchase-order` | Ambil PO untuk sebuah memo/credit (legacy `get-po_no`; kini `po_number` non-NULL). | Credit (Admin) |
| `GET` | `/purchase-orders/{poNumber}` | Baca PO + audit print + status email. | Credit (Admin) |
| `GET` | `/purchase-orders/{poNumber}/document` | **[USULAN]** Download PDF PO ter-render (`document_uri`) — grounded aksi "File-PO" di listing 2-wheel (`64 §11 IndexMotor.cshtml`) + kebutuhan PDF STEP 13. | Credit (Admin) |
| `POST` | `/purchase-orders/{poNumber}/print` | Cetak PO. **Gate**: posisi employee lolos gate cetak (legacy `sp_get_check_user_print_po`; role target Admin Cabang per GT STEP 13/D-10); memo `approved` + frozen snapshot ada. Increment `print_count` atomic. **Side effect v2**: men-trigger **dispatch email PDF PO ke dealer secara async & idempotent** (GT STEP 13; FE `64 §5.9` paritas) — lihat §5.4. | Admin Cabang (position-gated) |
| `POST` | `/purchase-orders/{poNumber}/email` | **Re-send / kirim manual** email PO ke dealer via **service email least-privilege** (fix do-not-replicate BR-CMPO-20); recipient dari data dealer terverifikasi; **idempotent** by `is_send` (BR-CMPO-21). **Guard**: setelah minimal 1x print. Kontrak §5.6. | Admin Cabang / Credit (Admin) |
| `POST` | `/purchase-orders/{poNumber}/correction` | **Open CM**: reopen memo → `corrected` + PO → `corrected`, restore live figures dari snapshot, emit `MemoCorrectionOpened`. Return-target v2 = proses Moofi Step 1–12 (granularitas `[OPEN]` OQ-GT-03); outcome legacy "tanpa re-entry CAS penuh" tetap `[INTENT]` BR-CMPO-10. Preserve print history. **Guard tambahan (dari FE)**: tolak bila legalization/Vertel/NPP downstream sudah melewati ambang (lihat §6 BR-CMPO-26, `[OPEN]` OQ-CMPOFE-03). | Credit (Admin) / Admin Cabang |

---

## 5. Kontrak Request/Response

Envelope error seragam (umbrella §7.3): `{ code, message, details?, correlation_id }`. Regulated/validation
gate = **fail-closed** (fix Edge Case 1: legacy mengembalikan "success" berisi error payload — do-not-replicate).

### 5.1 `PUT /credit-memos/{memoId}/finalization`

Request (field wajib bertanda `*`):

```json
{
  "payment_option": "REGULAR_12",          // *
  "otr_price": 25000000,                    // * — non-zero, catalog-recognized (BR-CMPO / validation)
  "down_payment_gross": 5000000,            // *
  "down_payment_net": 4500000,              // *
  "tenor_months": 12,                       // *
  "admin_fee": 500000,                      // *
  "process_fee": 250000,                    // *
  "vehicle_insurance": {                     // optional (conditional: insurance type selected)
    "cover_type": "COMPREHENSIVE",
    "insurance_type": "R2_OJK",
    "tier": "A"
  },
  "life_insurance": {                        // optional (car line; flag set)
    "enabled": true,
    "plan_code": "LIFE_STD"
  },
  "vehicle_registration": {                  // optional (conditional: UMC/third-party ownership scheme)
    "chassis_no": "MH1...",
    "engine_no": "JB..."
  }
}
```

Response `200 OK` (sukses finalisasi, memo tetap editable):

```json
{
  "memo_id": "CM-000123",
  "status": "draft",
  "otr_price": 25000000,
  "computed_installment": 1980000,
  "vehicle_insurance": { "premium": 750000, "source": "R2_OJK" },
  "correlation_id": "..."
}
```

Response `409 Conflict` (guard editability gagal — memo sudah locked/approved):

```json
{ "code": "MEMO_NOT_EDITABLE", "message": "Credit memo status 'finalized' tidak dapat diubah (hanya draft|corrected).", "correlation_id": "..." }
```

Response `422 Unprocessable Entity` (validation gate — **fix Edge Case 1**, bukan "success"):

```json
{ "code": "OTR_PRICE_INVALID", "message": "Harga OTR tidak ditemukan pada daftar referensi / bernilai nol.", "details": { "otr_price": 0 }, "correlation_id": "..." }
```

> Angka moneter WAJIB numeric typed (`BigDecimal` di BE) — **jangan** replikasi parsing string ber-separator
> legacy (Edge Case 9 `23 §9`; FE Edge Case 9 `64 §9`).

### 5.2 `GET /credit-memos/{memoId}/bank-account:validate` (DOKU ACL, sync)

Request query: `?bank_id=014&account_no=1234567890`

Response `200 OK` (owned response — bukan discarded seperti legacy Edge Case 6.1):

```json
{
  "is_valid": true,
  "status_code": "0",             // '0' = match (arti persis [OPEN] OQ-DOKU-04)
  "account_name": "BUDI SANTOSO",
  "message": "Rekening valid.",
  "correlation_id": "..."
}
```

Response `502/504` bila broker DOKU gagal/timeout — **fail-closed** (jangan lolos diam-diam; legacy discard
response + no timeout = do-not-replicate).

### 5.3 (Event handler) `MemoApproved` → freeze + lock asuransi + mint

> **`MemoApproved` = approve-only** — 03 meng-emit event ini **hanya** pada terminal **approve** (`03 §5.2/§7.2/§10.2`;
> umbrella; GT STEP 12 approve = `sp_approve_cm_moofi`, status `'A'`). Handler ini menangani **jalur approve saja**.
> Disposisi **correction/reject** **TIDAK** datang lewat `MemoApproved`; keduanya tiba lewat **event komite inbound
> terpisah** (nama kontrak lintas-service `[OPEN]` **OQ-CMPO-11**) dan ditangani di §7.1 (memo →
> `corrected`/`rejected`) **tanpa** freeze/mint. Menyuntik `disposition=correction|reject` ke handler ini =
> **phantom input** — jangan.
> **Source-agnostic (v2)**: event approve dapat berasal dari jalur moofi, jalur non-moofi (OQ-GT-01), maupun
> **Instant-Approval lane** (D-01 S11, mobile-origin, auditable policy path — eligibility `[OPEN]` OQ-MEET-04).
> Handler TIDAK boleh membedakan efeknya: freeze + lock + mint sama untuk semua sumber.
> **RESOLVED by convention (2026-07-14)**: handler ini = **eksekutor freeze OP/ULI/LCR + lock asuransi**
> (BE-03 §11 **OQ-BE03-02 → opsi b**) — dasar: **ADR-03** write-by-owner + **ADR-04** outbox. Konsekuensi:
> freeze **eventually-consistent** dalam transaksi konsumsi event (bukan inline di transaksi approve 03);
> `locked_at`/`locked_by` asuransi (§3.3) diisi di langkah 2 handler ini.

Input event (dari 03, selalu approve):

```json
{ "memo_id": "CM-000123", "approval_decision_id": "AD-777", "disposition": "approved", "hierarchy_level": 2 }
```

Efek deterministik (idempotent by `memo_id`+`approval_decision_id`):
1. Hitung & bekukan `OP`/`ULI`/`LCR` + `first_*` di memo; set `status=approved` (legacy `'A'`), stamp
   `approved_by/at`. Karena `MemoApproved` **approve-only**, freeze berjalan saat event diterima; figur
   **tak pernah** disentuh pada correction/reject (do-not-replicate BR-CMPO-6/GOTCHA-10 — jalur itu ditangani
   event komite terpisah §7.1, bukan handler ini).
2. **Lock binding asuransi**: stamp `locked_at/locked_by` pada `CM_INSURANCE_VEHICLE`
   (`trx_credit_memo_insurance_vehicle`; legacy `TrCmInsurance`) dan `CM_INSURANCE_LIFE`
   (`trx_credit_memo_insurance_life`; legacy `TrCmLifeInsuranceCredit`) — §3.3; mutasi sub-record asuransi setelah titik ini ditolak
   (`409 INSURANCE_LOCKED`) sampai memo kembali `corrected`. (GT STEP 12; D-01 S12) `[LOCKED]` outcome.
3. Tulis `CM_FINANCING_SNAPSHOT` (`trx_credit_memo_financing_snapshot`; arsipkan versi lama ke
   `log_credit_memo_financing_snapshot` — §3.9). `23 §5.7`.
4. Mint `PURCHASE_ORDER` (`trx_purchase_order`, §3.6) dengan `po_number` di-assign (non-NULL), `status=issued` — **tepat satu PO per
   approval** (D-01 S13; unique constraint `memo_id`+`approval_decision_id`). **Semua terminasi hierarki
   termasuk Level-0** (D-01 S13 memperbaiki GOTCHA-8; residual migrasi Level-0 motor lihat OQ-CMPO-05).
5. Emit `POIssued { memo_id, po_number }` → memberi feed antrean **Vertel** (STEP 14, D-02) dan downstream 05.

### 5.4 `POST /purchase-orders/{poNumber}/print`

Efek: render PDF PO → arsip ke file storage (`document_uri`) → increment `print_count` **atomic** + stamp
first/last print → **dispatch email PDF ke dealer (async, idempotent by `is_send`)** — GT STEP 13 menjadikan
email bagian standar alur cetak; FE legacy juga memicu email dalam request print yang sama (`64 §5.9`).
Kegagalan dispatch email **tidak membatalkan** print (email advisory/non-blocking — failure-mode final
`[OPEN]` OQ-CMPO-12).

Response `200 OK`:

```json
{
  "po_number": "PO-2026-000045",
  "print_count": 1,
  "first_print_at": "2026-07-07T02:10:00Z",
  "printed_by": "EMP-88",
  "document_uri": "postorage://po/PO-2026-000045.pdf",
  "email_dispatch": { "requested": true, "already_sent": false },
  "correlation_id": "..."
}
```

Response `403 Forbidden` (posisi tak berwenang): `{ "code": "PO_PRINT_NOT_AUTHORIZED", ... }`
Response `409 Conflict` (memo belum approved / snapshot belum ada — **fix Edge Case 6** silent-empty):
`{ "code": "PO_NOT_READY", "message": "Memo belum final-approved / frozen snapshot belum tersedia." }`

> Auto-print+email **saat approve** (tanpa aksi print manual) hanya ter-evidensi utk lini motor di legacy
> (`64 BR-CMPOFE-11`) — intensionalitas `[OPEN]` OQ-CMPOFE-01; **jangan** distandarkan diam-diam. Sampai
> terjawab: mint saat approve (§5.3) TANPA auto-print; print = aksi eksplisit endpoint ini.

### 5.5 `POST /purchase-orders/{poNumber}/correction` (Open CM)

Request: `{ "credit_id": "CM-000123", "reason": "Warna unit fisik berbeda" }`

Response `200 OK`:

```json
{
  "po_number": "PO-2026-000045",
  "po_status": "corrected",
  "memo_status": "corrected",
  "print_history_preserved": true,
  "rescreen_event_emitted": "MemoCorrectionOpened",
  "return_target": "MOOFI_STEP_1_12",
  "correlation_id": "..."
}
```

Response `409 Conflict` (guard downstream — lihat BR-CMPO-26): `{ "code": "PO_CORRECTION_BLOCKED_DOWNSTREAM",
"message": "Legalisasi/Vertel/NPP sudah berjalan melewati ambang koreksi." }` — ambang persis `[OPEN]`
OQ-CMPOFE-03.

> Koreksi **tidak** menghapus record RAC lintas-DB (fix GOTCHA-11) — ia meng-emit `MemoCorrectionOpened`; 02
> me-re-screen RAC secara **idempotent**, 01 me-re-lock. **Dua entry-point koreksi** — (a) application-level via 01
> `E10 reopen`, (b) PO-level via 04 Open-CM — keduanya bermuara ke re-screen idempotent di 02; idempotency menjamin
> aman bila terpicu dobel; pemilik re-lock aplikasi = **01**. Print history **dipertahankan** (fix Edge Case 4);
> aksi Open CM tercatat append-only di `log_credit_memo_reopen` (§3.9; paritas legacy `log_open_transaction`).
> **Return-target v2**: GT STEP 13 menyebut koreksi "kembali ke proses Moofi (Step 1–12)"; outcome legacy
> BR-CMPO-10 = koreksi **tanpa re-entry CAS penuh** `[INTENT]`. Granularitas (full re-entry Step 1–12 vs
> field-scoped correction) = **`[OPEN]` OQ-GT-03** — response memuat `return_target` agar FE bisa route;
> nilai final enum menunggu resolusi OQ-GT-03. **Jangan implement salah satu diam-diam.**

### 5.6 `POST /purchase-orders/{poNumber}/email` (re-send PO ke dealer)

> **Fix do-not-replicate legacy** (BR-CMPO-20): kirim via **service email app-tier ber-identity least-privilege**
> (bukan Database Mail `EXECUTE AS LOGIN='sa'`); recipient di-resolve **server-side dari data dealer terverifikasi**
> (bukan fallback `julia@mcf.co.id` / trial-branch override); body **ter-escape/templated** (bukan HTML string-concat).
> **Guard**: memo `approved` + PO sudah di-print ≥1x. **Idempoten** by `tr_PO_send_to_email.is_send` (BR-CMPO-21).
> **Attachment = PDF PO ter-arsip** (`document_uri`) — GT STEP 13 ("the PDF file is emailed to the Dealer").
> Target persistence: `out_notification` (dispatch/idempotensi) + `log_po_email` (audit) — §3.6.
> Peran endpoint di v2: **re-send / kirim manual** — kirim pertama normalnya sudah di-dispatch otomatis oleh
> print (§5.4); idempotensi `is_send` menjamin tidak dobel kirim.

Request: `{ }` — recipient **tidak** dari klien (di-resolve server-side dari dealer terverifikasi; **tanpa** recipient-override arbitrer).

Response `200 OK` (terkirim):

```json
{ "po_number": "PO-2026-000045", "is_send": true, "sent_to_dealer": "dealer-terverifikasi@…", "correlation_id": "..." }
```

Response `200 OK` (idempotent no-op — sudah pernah terkirim, `is_send=1`):

```json
{ "po_number": "PO-2026-000045", "is_send": true, "already_sent": true, "correlation_id": "..." }
```

Response `409 Conflict` (guard — PO belum di-print):

```json
{ "code": "PO_NOT_PRINTED", "message": "Email PO hanya setelah minimal 1x print.", "correlation_id": "..." }
```

> Failure-mode saat service email gagal (retry / degrade-gracefully vs block) = `[OPEN]` **OQ-CMPO-12**
> (email advisory/non-blocking; `email-sms-notifications.md §8`). Jangan diputus diam-diam.
> **Konsolidasi**: legacy menduplikasi logika upload-PDF + email-send di dua tempat (`64 Edge Case 8`
> `[ARTIFACT]`) — rebuild WAJIB satu kapabilitas PO-issuance bersama untuk kedua entry point (print §5.4 &
> re-send §5.6) agar tidak drift.

### 5.7 `GET /purchase-orders` (listing) — [USULAN]

Query: `?branch_id=&status=&product_line=&from=&to=&page=&size=`. Response row (grounded kolom layar
`Index.cshtml`/`IndexMotor.cshtml` — `64 §6`): `{ po_number, credit_id, memo_status, po_status, print_count,
send_mail_count, dealer_name, asset_desc, approved_at }`. Catatan: gate tombol Print di FE legacy membaca field
status **berbeda per lini** (`status_ca` 4-wheel vs `status_cm` 2-wheel — `64 BR-CMPOFE-12`, `[OPEN]`
OQ-CMPOFE-02); rebuild mengekspos **satu** field status memo kanonik + status PO — pemetaan final menunggu
OQ-CMPOFE-02.

---

## 6. Aturan Bisnis

Setiap BR dilacak ke KB / GT / D-xx. `[LOCKED]`=WAJIB dipertahankan; `[INTENT]`=outcome dipertahankan, desain
bebas; `[ARTIFACT]`=do-not-replicate/dibuang.

| ID | Aturan | Sumber | Marker | Catatan rebuild |
|---|---|---|---|---|
| BR-CMPO-1 | Field struktur finansial memo hanya bisa diubah saat `status ∈ {draft, corrected}`; setelah locked/approved, edit ditolak. | `23 §7 BR-CMPO-1` | `[INTENT]` | Guard editability wajib; return `409 MEMO_NOT_EDITABLE`. |
| BR-CMPO-2 | Satu aksi finalisasi melayani create + revisi memo (existence-check). | `23 §7 BR-CMPO-2` | `[ARTIFACT]` | Outcome (revisi progresif) dipertahankan; mekanisme shared-procedure bebas → pisahkan create/update bila lebih bersih. |
| BR-CMPO-3 | RFA lock diblok guard Draft/Correction yang sama; sukses → memo ke state locked "awaiting hierarchy". | `23 §7 BR-CMPO-3`; GT STEP 9 (`sp_approve_cm_moofi`) | `[INTENT]` | **RFA dimiliki 01** (boundary) yang emit `ApplicationLocked`; **04 (sole writer) menulis transisi** `draft/corrected→finalized` sebagai handler event itu. |
| BR-CMPO-4 | Final approval menghitung & membekukan `OP`, `LCR`(=installment×tenor), `ULI`(=selisih) + snapshot `first_*`. | `23 §7 BR-CMPO-4`, `OR-13`; GT STEP 12; **D-01 S12** | `[LOCKED]` | Perilaku computed-and-frozen WAJIB; arti `OP`/`ULI`/`LCR` = `[OPEN]` OQ-CMPO-02. |
| BR-CMPO-5 | Formula `OP` berbeda antar lini: motor = asset price − net DP; car = funded amount langsung. | `23 §7 BR-CMPO-5` | `[INTENT]` | Outcome (1 figur principal per lini) dipertahankan; **mana formula authoritative = `[OPEN]` OQ-CMPO-03** — jangan pilih diam-diam. Terkait matriks per-produk D-07/OQ-MEET-06. |
| BR-CMPO-6 | Varian car me-recompute/overwrite frozen `OP`/`LCR`/`ULI` bahkan pada disposisi non-approval. | `23 §7 BR-CMPO-6`, `hidden-gotchas.md §C GOTCHA-10` | `[ARTIFACT]` | **Do-not-replicate**: freeze **hanya** pada `disposition=approved`; jangan sentuh figur pada correction/reject. |
| BR-CMPO-7 | PO number/record dibuat otomatis saat level approval final selesai (bukan aksi langsung di domain ini). | `23 §7 BR-CMPO-7`; **D-01 S13** | `[LOCKED]` | Trigger point (post-approval) dipertahankan; **pindahkan minting ke 04** (bukan credit-analyst). |
| BR-CMPO-8 | Terminasi "Level 0" tak auto-mint PO; hanya lini car punya self-heal mint saat print, motor tidak. | `23 §7 BR-CMPO-8`, `hidden-gotchas.md §B GOTCHA-8` | `[ARTIFACT]` | **Do-not-replicate asimetri**. Target **ter-decide oleh D-01 S13**: **semua approval mint tepat satu PO** deterministik (termasuk Level-0). Residual OQ-CMPO-05 tinggal soal forensik/migrasi data legacy Level-0 motor (lihat §11). |
| BR-CMPO-9 | Print PO increment counter, stamp first/last print user+date, lock status. | `23 §7 BR-CMPO-9` | `[LOCKED]` | Audit trail dealer/compliance-facing WAJIB dipertahankan; increment **atomic** (fix race Edge Case 3). |
| BR-CMPO-10 | Koreksi PO = satu aksi reopen: PO→open, memo→Correction, restore live figures dari snapshot, delete snapshot — tanpa re-create aplikasi. | `23 §7 BR-CMPO-10`, `OR-14`; GT STEP 13 | `[INTENT]` | Outcome (koreksi tanpa re-entry CAS penuh) WAJIB; mekanisme delete-and-restore bebas; **preserve print history** (fix Edge Case 4); audit reopen → `log_credit_memo_reopen` (§3.9). Return-target v2 "Step 1–12" — granularitas `[OPEN]` OQ-GT-03. |
| BR-CMPO-11 | Reopen memo (satu asset kind = motor) menghapus record RAC Bank Mega → paksa re-screen eksternal. | `23 §7 BR-CMPO-11`, `hidden-gotchas.md §C GOTCHA-11` | `[LOCKED]` (kebutuhan re-screen) | Kebutuhan re-screen WAJIB; **mekanisme diganti**: emit `MemoCorrectionOpened` (idempotent), **bukan** DELETE lintas-DB. |
| BR-CMPO-12 | Shared "get insurance fee" hanya menghitung premium untuk satu asset kind (motor `001`); lini lain silent-zero. | `23 §7 BR-CMPO-12` | `[ARTIFACT]` | **Do-not-replicate**: setiap lini punya path premium eksplisit always-resolving; opt-out asuransi = flag eksplisit, bukan efek zero-fee. |
| BR-CMPO-13 | Premium asuransi dihitung dari rate-master ber-referensi **OJK** (tabel R2/R4 OJK). | `23 §7 BR-CMPO-13` | `[LOCKED]` | Metodologi rate-table-driven dipertahankan; storage/lookup bebas. |
| BR-CMPO-14 | Rate "bottom rate" ditulis ke rate-history hanya untuk kode top-up/repeat-order; kode lain dihapus. | `23 §7 BR-CMPO-14` | `[OPEN]` | Sumber ter-corrupt (UTF-16); kondisi persis = `[OPEN]` OQ-CMPO-09. |
| BR-CMPO-15 | Kegagalan validasi dikembalikan sebagai row error di dalam envelope "success"; transaksi tetap commit. | `23 §7 BR-CMPO-15`, `23 §9 Edge Case 1` | `[ARTIFACT]` | **Do-not-replicate**: surface sebagai `4xx` non-success (`422`), bukan success berisi error. |
| BR-CMPO-16 | `tr_cm.po_no` selalu ditulis `NULL` walau ada reader (`get-po_no`). | `hidden-gotchas.md §B GOTCHA-6` | `[ARTIFACT]` | **Do-not-replicate**: `po_number` di-assign **saat mint** (non-NULL); kolom `tr_CM.PO_no` TIDAK dibawa — derive via `trx_purchase_order` (census §3.1.2 #109). |
| BR-CMPO-17 | Car & motor diverge di gate + formula (age/DP caps, OP/ULI/LCR, blacklist codes). | `hidden-gotchas.md §C GOTCHA-10`; D-07 | `[INTENT]` | Perlakukan car/motor sebagai **konfigurasi satu engine** table-driven, bukan dua code path divergen; parameterisasi per produk MACF menunggu OQ-MEET-06. |
| BR-CMPO-18 | DOKU call: HTTP dari dalam T-SQL (`sp_OACreate`), IP hardcoded, response discarded, no auth, JSON string-concat. | `doku-payment-gateway.md §4-9`, `data-mutation-policy.md` | `[ARTIFACT]` | **Do-not-replicate**: HTTP client app-tier + timeout/retry/circuit-breaker + own response persist + real JSON serializer. Write-back owner = `[OPEN]` OQ-DOKU-01. |
| BR-CMPO-19 | Setiap transisi memo/PO material tercatat auditable (maker-checker); print position-gated. | `operational-rules.md OR-2`, `23 §2`; D-09/D-10 | `[INTENT]` | Audit + identitas actor di-enforce app-layer (OQ-MCP-01); **tanpa role super-user / bypass** (D-09 `[LOCKED]`). |
| BR-CMPO-20 | **Email PO** (`sp_send_email_print_PO`) legacy: (a) `EXECUTE AS LOGIN='sa'` privilege-escalation sebelum `sp_send_dbmail`; (b) hardcoded personal-email fallback `julia@mcf.co.id` + trial-branch recipient override ke alamat personal; (c) unescaped HTML string-concat di body email. | `email-sms-notifications.md §4/§5/§10/§11`, `data-mutation-policy.md` | `[ARTIFACT]` | **Do-not-replicate**. DIPERBAIKI: service email app-tier ber-identity least-privilege; recipient dari **data dealer terverifikasi**; body **ter-escape/templated**. Kontrak §5.6; seam §8. Failure-mode = `[OPEN]` OQ-CMPO-12. |
| BR-CMPO-21 | Send email PO **idempoten**: cek `tr_PO_send_to_email.is_send=0` sebelum kirim, set `1` setelah kirim (per `PO_no`) → request kedua = no-op untuk bagian mail-send. | `email-sms-notifications.md §9` | `[INTENT]` | Outcome **idempotency WAJIB dijaga**; mekanisme flag/tabel bebas didesain ulang (target: `out_notification.status` + `ux(notification_type, po_number)`, §3.6). Print-count & mail-send = dua domain idempotensi terpisah dalam PO yang sama. Idempotensi ini juga melindungi dispatch-otomatis §5.4 + re-send §5.6. |
| BR-CMPO-22 *(baru, v2)* | Pada approve komite, **binding asuransi jiwa (`TrCmLifeInsuranceCredit`) + kendaraan (`TrCmInsurance`) di-LOCK** bersama `OP`/`ULI`/`LCR`; mutasi sub-record asuransi pasca-lock ditolak sampai memo `corrected`. | GT STEP 12; **D-01 S12** | `[LOCKED]` (outcome lock) | Implement via `locked_at/locked_by` (§3.3) atau mekanisme setara; guard `409 INSURANCE_LOCKED`. |
| BR-CMPO-23 *(baru, v2)* | **Print PO memicu email PDF PO ke dealer** (dispatch async, idempotent by `is_send`) sebagai bagian standar STEP 13 — bukan aksi yang bisa terlupa terpisah. | GT STEP 13 ("PDF file is emailed to the Dealer"); FE `64 §5.9` (paritas legacy: print → email dalam satu request) | `[INTENT]` | Kegagalan email tidak membatalkan print (advisory; final `[OPEN]` OQ-CMPO-12). Endpoint §5.6 tetap ada untuk re-send. |
| BR-CMPO-24 *(baru, v2)* | **Tepat satu PO per approval** — setiap `MemoApproved` menghasilkan tepat satu `PURCHASE_ORDER`; tidak ada mint dari modul lain, tidak ada self-heal mint saat print, tidak ada PO ganda pada redelivery. | **D-01 S13**; `hidden-gotchas.md §B GOTCHA-8` (anti-pattern yang diperbaiki) | `[INTENT]` | Enforce dengan unique constraint (`memo_id`,`approval_decision_id`) + handler idempotent §5.3. |
| BR-CMPO-25 *(baru, v2)* | Open CM (Fase Koreksi STEP 13) mengembalikan proses ke **jalur Moofi Step 1–12**; angka frozen di-restore dari snapshot dan re-screen dipicu via event. | GT STEP 13; `23 §7 BR-CMPO-10/11` | `[INTENT]` / `[OPEN]` (granularitas) | Full re-entry vs field-scoped = **OQ-GT-03**; sampai resolusi, kontrak §5.5 memuat `return_target` eksplisit. |
| BR-CMPO-26 *(baru, dari FE)* | Open CM di-gate terhadap kemajuan downstream: legacy 2-wheel memblokir Open CM bila status legalisasi (NPP) sudah melewati Correction/blank; 4-wheel tidak punya check ekuivalen. Dengan sisipan **Vertel (STEP 14, D-02)**, gate rebuild harus mempertimbangkan status Vertel + NPP. | `64 BR-CMPOFE-13` + Edge Case 3 (asimetri ter-verifikasi); D-02 | `[INTENT]` (gate harus ada) / `[OPEN]` (ambang persis) | Ambang blokir final (Vertel started? NPP RFA? aktivasi?) = **OQ-CMPOFE-03** — jangan pilih diam-diam; response `409 PO_CORRECTION_BLOCKED_DOWNSTREAM`. |
| BR-CMPO-27 *(baru, v2)* | Dual approve path legacy (`sp_approve_cm` non-moofi vs `sp_approve_cm_moofi`) = entry-point dispatcher per channel/produk; handler 04 wajib source-agnostic. | GT STEP 12 note; **OQ-GT-01**; D-07/OQ-MEET-06 | `[OPEN]` (scope channel) | Channel mana yang bertahan di rebuild per produk MACF menunggu keputusan desain port jalur (OQ-GT-01 RESOLVED — evidence: keduanya LIVE, pemisah = trigger; lihat §11) + OQ-MEET-06; desain handler §5.3 tidak bergantung jawabannya (source-agnostic). |

---

## 7. State Machine

Dua state value ter-kopel: `CREDIT_MEMO.status` dan `PURCHASE_ORDER.status`. Termasuk jalur non-happy-path.

### 7.1 `CREDIT_MEMO.status` — enum `draft | finalized | approved | corrected` (+terminal `rejected` di app)

> Mapping legacy: `D`=draft, `C`=corrected, `0`=finalized (RFA-locked/awaiting hierarchy), **`A`=approved**
> (GT STEP 12). Catatan v2 utk OQ-CMPO-01: GT STEP 8 menunjukkan draft kontrak hasil sync MOOFI dibuat dengan
> **"Status RFA = '0'"** — evidence baru bahwa `0` adalah status RFA level header/kontrak yang di-seed saat
> sync, **bukan** status editable memo; tetap butuh konfirmasi 01 (OQ-CMPO-01 belum ditutup).
> `V`(Verify)/`R`(Review) muncul **hanya sebagai display label** guard di BE — tak ada write site (`[OPEN]`
> OQ-CMPO-10); FE **mengirim** nilai `V`/`C` lewat aksi approval yang sama (`64 OQ-CMPOFE-04`) tetapi persist
> BE belum terbukti; tidak dimodelkan sebagai state tercapai sampai dikonfirmasi. Sumber: `23 §8`.

| Dari | Aksi | Ke | Guard/Prasyarat |
|---|---|---|---|
| *(none)* | Finalisasi pertama (seed) | `draft` | Seed dari aplikasi (mekanisme `[OPEN]` OQ-CMPO-08; kandidat v2: draft kontrak STEP 8 sync MOOFI). |
| `draft` | Revisi finalisasi (S1) | `draft` | `status ∈ {draft, corrected}` (BR-CMPO-1). |
| `corrected` | Revisi finalisasi (S1) | `corrected` | Sama guard. |
| `draft`/`corrected` | RFA lock (**boundary 01** emit `ApplicationLocked`; jalur moofi STEP 9 `sp_approve_cm_moofi`) | `finalized` | Guard editability; **01 emit event, 04 (sole writer) menerapkan transisi** finalize pada event itu (BR-CMPO-3). |
| `finalized` | Komite `correction` (03 → **event komite inbound**, nama `[OPEN]` OQ-CMPO-11) | `corrected` | Disposisi correction; per GT STEP 12 target rework = **Step 1–7 (CMO fix, jalur moofi)**. **Pre-mint** — **tanpa** freeze, **tanpa** mint PO. Bukan flow post-mint Open-CM §5.5. |
| `finalized` | Komite `reject` (03 → **event komite inbound**, nama `[OPEN]` OQ-CMPO-11) | `rejected` (terminal) | Disposisi reject (GT STEP 12: "Rejected (permanent)"); write-site legacy `[OPEN]` (`23 §5.5`). **Pre-mint** — **tanpa** freeze, **tanpa** mint PO. Closure aplikasi = concern 01 (OQ-AC-01). |
| `finalized` | **`MemoApproved` (04 handler)** | `approved` | `disposition=approved`; freeze `OP`/`ULI`/`LCR`+`first_*` (BR-CMPO-4) **+ lock asuransi (BR-CMPO-22)**; **freeze hanya di sini** (fix BR-CMPO-6). |
| `approved` | Koreksi PO (Open CM) | `corrected` | Restore live figures dari snapshot; emit `MemoCorrectionOpened` (BR-CMPO-10/11); guard downstream BR-CMPO-26; return-target OQ-GT-03. |

### 7.2 `PURCHASE_ORDER.status` — enum `issued | corrected | fulfilled`

> Print-lock/print-count/first-last-print **dan status email (`is_send`)** = **atribut**, bukan status (konform
> umbrella ERD §6). Mapping legacy: open/printed → `issued` (+atribut print); reopened → `corrected`.

| Dari | Aksi | Ke | Guard/Prasyarat |
|---|---|---|---|
| *(none)* | **Mint** pada `MemoApproved` (04) | `issued` | `po_number` di-assign non-NULL; idempotent by `memo_id`+`approval_decision_id`; **semua terminasi hierarki — tepat satu PO per approval** (BR-CMPO-7/24; D-01 S13). |
| `issued` | Print (position-gated) | `issued` | Memo `approved` + snapshot ada (fix Edge Case 6); increment `print_count` atomic; stamp print user/date (BR-CMPO-9); **dispatch email PDF dealer async idempotent** (BR-CMPO-23). |
| `issued` | Email ke dealer (re-send §5.6) | `issued` | Setelah ≥1 print (`23 §5.11`); via service email least-privilege + idempoten `is_send` (BR-CMPO-20/21). |
| `issued` | Koreksi (Open CM) | `corrected` | Unit fisik beda; guard downstream BR-CMPO-26; preserve print history (fix Edge Case 4); memo → `corrected`. |
| `corrected` | Re-finalize → re-approve → mint/reuse | `issued` | Loop balik (return-target OQ-GT-03); **bisa** `po_number` baru (target: audited renumber via `log_credit_memo_reopen` §3.9, bukan destructive delete). |
| `issued` | Downstream fulfill (Vertel STEP 14 → NPP STEP 15 aktif / dealer ship) | `fulfilled` | Konsumsi **PULL** oleh 05/downstream (D-01 S15) — terminal untuk 04. |

**Jalur gap legacy (ter-decide arah target)**: terminasi **Level-0 lini motor** — legacy tak auto-mint & tak
punya self-heal. **Target v2 per D-01 S13: semua approval mint tepat satu PO** (termasuk Level-0). Residual
`[OPEN]` OQ-CMPO-05 = forensik legacy + kebijakan **migrasi** memo Level-0 motor lama yang tak ber-PO —
konfirmasi stakeholder tetap dibutuhkan sebelum backfill data.

---

## 8. Integrasi Eksternal

| Seam | Arah | Sync/Async | Pemilik | ACL / do-not-replicate | Sumber |
|---|---|---|---|---|---|
| **DOKU** (bank-account inquiry "Cek Rekening") | outbound | **sync** | **04** | **WAJIB via ACL app-tier**. Ganti `sp_OACreate` HTTP-dari-T-SQL + IP hardcoded (`10.90.7.3:81`) dengan HTTP client (timeout/retry/circuit-breaker — di Java: **USULAN** Resilience4j); own response persist (write-back owner `[OPEN]` OQ-DOKU-01); real JSON serializer (fix unescaped concat); no-auth = review (OQ auth). **Bukan** funds-movement. | `doku-payment-gateway.md §2-11` |
| **Email PO (PDF) ke dealer** (STEP 13) | outbound | **async** (fire-and-forget, idempotent) | **04** | **WAJIB via ACL/service email app-tier**. Ganti `sp_send_email_print_PO` (Database Mail dari T-SQL + `EXECUTE AS LOGIN='sa'`) dengan **service email ber-identity least-privilege**; recipient dari **data dealer terverifikasi** (bukan fallback `julia@mcf.co.id` / trial-branch override); body **ter-escape/templated**; attachment = **PDF PO** (`document_uri`); pertahankan guard idempoten `is_send`. Di-dispatch otomatis oleh print (§5.4) + re-send manual (§5.6). Failure-mode `[OPEN]` OQ-CMPO-12. **Bukan** funds-movement. **Berbeda dari email blast dealer pasca STEP 15 (D-03, milik 05, OQ-MEET-01).** | `email-sms-notifications.md §3-11`, `data-mutation-policy.md`; GT STEP 13 |

**Boundary event (internal, bukan seam eksternal):**

- **Konsumsi `MemoApproved`** dari **03** — memicu freeze + lock asuransi + mint (idempotent; source-agnostic
  moofi / non-moofi / Instant-Approval lane D-01 S11). `23 §5.6-5.8`; GT STEP 12.
- **Emit `POIssued`** — dikonsumsi **05**: memberi feed antrean **Vertel (STEP 14, D-02)** lalu NPP (STEP 15;
  baca memo terfinalisasi + frozen figures untuk Financing Agreement) via **PULL** (D-01 S15). `23 §6`, digest.
- **Emit `MemoCorrectionOpened`** pada koreksi — dikonsumsi **02** (RAC re-screen idempotent) & **01** (re-lock).
  **Menggantikan** DELETE lintas-DB legacy (fix GOTCHA-11). `hidden-gotchas.md §C GOTCHA-11`.

> Digest `integration_seams`: seam eksternal langsung milik **04** = **DOKU** (sync, account-validate) **dan
> email-PO ke dealer** (async, outbound notification; `email-sms-notifications.md §3`). RAC re-screen adalah
> milik **02**; 04 hanya **memicu event**, tidak memanggil Bank Mega langsung, dan **tanpa** cross-DB DML.

---

## 9. Acceptance Criteria (Given/When/Then)

**Happy path — finalisasi**
- **AC-1** *Given* memo `status=draft`, *When* operational staff `PUT /finalization` dengan OTR valid non-zero,
  *Then* memo ter-update, tetap `draft`, response `200` berisi installment terkomputasi. (BR-CMPO-1)

**Gate gagal — editability**
- **AC-2** *Given* memo `status=finalized` (RFA-locked), *When* `PUT /finalization`, *Then* `409
  MEMO_NOT_EDITABLE`, memo tak berubah. (BR-CMPO-1/3)

**Gate gagal — validasi (fix Edge Case 1)**
- **AC-3** *Given* OTR price = 0 / tak ada di referensi, *When* `PUT /finalization`, *Then* `422
  OTR_PRICE_INVALID` (bukan "success" berisi error); tidak ada commit parsial. (BR-CMPO-15)

**DOKU**
- **AC-4** *Given* rekening valid, *When* `GET /bank-account:validate`, *Then* `200 { is_valid:true, account_name }`
  dari response yang **di-own** (bukan discarded). *And* bila broker timeout → `502/504` **fail-closed**. (BR-CMPO-18)

**Freeze deterministik (fix BR-CMPO-6) + lock asuransi (D-01 S12)**
- **AC-5** *Given* memo `finalized`, *When* `MemoApproved{disposition=approved}`, *Then* `OP`/`ULI`/`LCR`+`first_*`
  dibekukan, `status=approved` (legacy `A`), `approved_by/at` distamp. (BR-CMPO-4)
- **AC-5b** *Given* memo `finalized` dengan sub-record `TrCmInsurance` + `TrCmLifeInsuranceCredit`, *When*
  `MemoApproved` diproses, *Then* kedua sub-record asuransi ter-stamp `locked_at/locked_by`; *And When* ada
  `PUT` yang mencoba mengubah sub-record asuransi setelahnya, *Then* `409 INSURANCE_LOCKED`. (BR-CMPO-22; GT
  STEP 12; D-01 S12)
- **AC-6a** *(jalur correction)* *Given* memo `finalized`, *When* **event komite disposisi `correction`** tiba
  (bukan `MemoApproved`; event komite inbound, nama `[OPEN]` OQ-CMPO-11), *Then* memo → `corrected` (rework
  kembali ke jalur Moofi Step 1–7 per GT STEP 12), figur frozen **TIDAK** disentuh (car maupun motor), **tidak**
  ada PO ter-mint. (fix BR-CMPO-6 / GOTCHA-10)
- **AC-6b** *(jalur reject)* *Given* memo `finalized`, *When* **event komite disposisi `reject`** tiba (bukan
  `MemoApproved`), *Then* memo → `rejected` (terminal — GT STEP 12 "Rejected (permanent)"), figur frozen
  **TIDAK** disentuh, **tidak** ada PO ter-mint; closure aplikasi = concern 01 (OQ-AC-01). (fix BR-CMPO-6 / GOTCHA-10)

**PO minting terpusat & deterministik — tepat satu PO per approval (fix GOTCHA-6/8; D-01 S13)**
- **AC-7** *Given* `MemoApproved` (level manapun, **termasuk Level-0**), *When* handler jalan, *Then* tepat
  **satu** PO ter-mint dengan `po_number` **non-NULL**, `status=issued`. (BR-CMPO-7/16/24)
- **AC-8** *Given* `MemoApproved` di-redeliver/di-retry (`approval_decision_id` sama), *When* handler jalan
  ulang, *Then* **tidak** ada PO ganda (idempotent; unique constraint). (umbrella §7.4; BR-CMPO-24)
- **AC-9** *Given* modul credit-analyst (03/02), *When* approval selesai, *Then* modul itu **TIDAK** mint PO —
  minting **hanya** di 04. (fix GOTCHA-8)
- **AC-9b** *Given* `MemoApproved` yang berasal dari **Instant-Approval lane** (D-01 S11) atau jalur moofi vs
  non-moofi, *When* handler jalan, *Then* efek freeze + lock + mint **identik** dengan jalur komite manusia
  (source-agnostic). (BR-CMPO-27; OQ-GT-01 tidak memblokir desain handler)

**Print**
- **AC-10** *Given* employee posisi tak berwenang (di luar gate `sp_get_check_user_print_po` / role Admin
  Cabang), *When* `POST /print`, *Then* `403 PO_PRINT_NOT_AUTHORIZED`; enforcement **server-side** (check FE
  legacy adalah dead code — `64 BR-CMPOFE-14`). (BR-CMPO-9/19)
- **AC-11** *Given* memo belum approved / snapshot belum ada, *When* `POST /print`, *Then* `409 PO_NOT_READY`
  (bukan hasil kosong senyap). (fix Edge Case 6)
- **AC-12** *Given* dua request print near-simultan, *When* keduanya jalan, *Then* `print_count` naik akurat
  (atomic), tak ada dua "first print". (fix Edge Case 3)
- **AC-12b** *Given* PO `issued` belum pernah di-email, *When* `POST /print` sukses, *Then* PDF PO ter-arsip
  (`document_uri` terisi) dan **dispatch email dealer ter-trigger sekali** (async); *And* kegagalan kirim email
  **tidak** menggagalkan response print (advisory — failure-mode final OQ-CMPO-12). (BR-CMPO-23; GT STEP 13)

**Koreksi (Open CM)**
- **AC-13** *Given* PO `issued` (sudah di-print) & unit fisik beda, *When* `POST /correction`, *Then* memo →
  `corrected`, PO → `corrected`, live figures ter-restore, **print history dipertahankan**, dan `MemoCorrectionOpened`
  ter-emit; **tanpa** membuat aplikasi CAS baru; response memuat `return_target` (nilai final per OQ-GT-03).
  (BR-CMPO-10/25; fix Edge Case 4)
- **AC-14** *Given* koreksi lini motor, *When* `POST /correction`, *Then* RAC re-screen dipicu via **event**
  (idempotent), **bukan** DELETE lintas-DB ke Bank Mega. (fix GOTCHA-11)
- **AC-14b** *Given* proses downstream (Vertel/NPP) sudah melewati ambang koreksi (ambang final per
  OQ-CMPOFE-03), *When* `POST /correction`, *Then* `409 PO_CORRECTION_BLOCKED_DOWNSTREAM` — konsisten untuk
  **kedua** lini (fix asimetri `64 Edge Case 3`). (BR-CMPO-26)

**Email PO (fix do-not-replicate legacy)**
- **AC-15** *Given* PO `issued` sudah di-print ≥1x dan `is_send=0`, *When* `POST /email`, *Then* PDF PO terkirim
  ke **dealer terverifikasi** via **service email least-privilege** (bukan `sa`/Database Mail), body
  **ter-escape/templated**, `is_send` → `1`. (BR-CMPO-20/21/23)
- **AC-16** *Given* PO sudah pernah di-email (`is_send=1` — termasuk oleh dispatch otomatis print §5.4), *When*
  `POST /email` lagi, *Then* **no-op idempotent** (tidak kirim ulang), response menandai `already_sent`. (BR-CMPO-21)
- **AC-17** *Given* PO `issued` **belum** di-print, *When* `POST /email`, *Then* `409 PO_NOT_PRINTED` (guard ≥1 print).

**Governance role (D-09/D-10)**
- **AC-18** *Given* user manapun dengan klaim role "super user" / role di luar sensus D-10, *When* memanggil
  endpoint mutasi 04 mana pun, *Then* ditolak oleh permission layer — **tidak ada bypass super-user**. (D-09
  `[LOCKED]`; BR-CMPO-19)

---

## 10. Dependency

### 10.1 Upstream dikonsumsi

| Sumber | Bentuk | Isi |
|---|---|---|
| **MOOFI → FINCORE sync (STEP 8, via 01)** | read | `credit_id` (PK, nomor kontrak unik nasional — format resolved OQ-GT-02 `branch(5)+YY+MM+SEQ(5)`, milik 01), draft kontrak (applicant/penjamin, kapasitas bayar, struktur pinjaman, foto), Status RFA=`0`; SP validasi `sp_validation_mobile_to_fincore`. Kandidat sumber seed finalisasi (OQ-CMPO-08). GT STEP 8. |
| **01-intake-cas** | read + boundary | `CREDIT_APPLICATION` header; **RFA lock** (guard editability, dipicu 01 — STEP 9 `sp_approve_cm_moofi`); seed nilai finalisasi (`[OPEN]` OQ-CMPO-08). |
| **02-credit-analysis** | read | `trans_type_id` (routing key, char-for-char) yang disusun 02 (risk-category dari RAC callback, D-01 S8). |
| **03-approval-committee** | **event** `MemoApproved` | Trigger tunggal freeze + lock asuransi + mint (GT STEP 12; routing dinamis trans_type_id + Plafond OP + risk level D-01 S10; self-approval blocked + Instant-Approval lane D-01 S11; audit `tr_hierarchy_transaction`). |
| **Masters (dealer/asset/OTR)** | read-only (D-08: CRUD master dimiliki modul master, dalam SoW) | Identifikasi dealer/aset, `sp_get_Harga_OTR`, **email dealer terverifikasi** (BR-CMPO-20). Owned vs read-only = `[OPEN]` OQ-EXTMASTERS-01. |
| **DOKU** | external, sync (ACL) | Validasi rekening bank. |

### 10.2 Downstream dipicu

| Target | Bentuk | Isi |
|---|---|---|
| **05 — Vertel (STEP 14, D-02) lalu NPP (STEP 15)** | **event** `POIssued` + **PULL** | `POIssued` memberi feed antrean Vertel (`TrVerificationCustomer`, RFA Vertel, approve Kepala Cabang); setelahnya 05 membaca memo terfinalisasi + frozen figures + snapshot untuk Financing Agreement / dokumen PK (D-04) (`23 §6`; GT STEP 14–15). Semua via **PULL, bukan push** (D-01 S15). |
| **02-credit-analysis** / **01-intake** | **event** `MemoCorrectionOpened` | Re-screen RAC (idempotent) + re-lock — menggantikan DELETE lintas-DB (GOTCHA-11). Return-target user flow per OQ-GT-03. |

> Semua downstream = **PULL / event**, bukan push langsung ke eksekusi (D-01 S15). 04 **tidak** melakukan GL
> posting, aktivasi NPP, transfer dealer, maupun **email blast dealer pasca STEP 15** (D-03 — milik 05,
> OQ-MEET-01) — out-of-scope §1.2.

---

## 11. Keputusan Dibutuhkan (Open Questions)

Jangan diselesaikan diam-diam — butuh domain-expert/stakeholder sign-off. OQ-ID dari KB / GT v2 / decision
register; baris berlatar keputusan meeting menyebut D-xx terkait.

| OQ-ID | Prioritas | Pertanyaan | Resolves |
|---|---|---|---|
| **OQ-GT-01** | ~~P1~~ **RESOLVED — evidence** | Dual approve path `sp_approve_cm` (non-moofi) vs `sp_approve_cm_moofi` (jalur final v2) — channel mana melayani origination mana (dealer/field vs mobile) dan mana yang masuk scope rebuild per produk? Menentukan apakah surface finalisasi §5.1 tetap punya jalur entry non-moofi. → **RESOLVED — evidence (2026-07-14; detail BE-00 §11 / BE-03 §11)**: pemisah aktual = **trigger** (manual web vs agent otomatis — IA + bulk RAC), bukan channel murni; KEDUA SP LIVE. Residual = keputusan desain port satu/dua jalur (per produk, bersama D-07/OQ-MEET-06) — menentukan surface §5.1; handler §5.3 tetap source-agnostic (BR-CMPO-27). | Evidence terpetakan; residual = keputusan desain + workshop D-07/OQ-MEET-06. |
| **OQ-GT-03** | P2 | Return-target Open CM (STEP 13 koreksi) "kembali ke Step 1–12" — **full re-entry** jalur Moofi vs **field-scoped correction** (outcome legacy BR-CMPO-10 tanpa re-entry CAS)? Menentukan nilai `return_target` §5.5 + UX koreksi. | Code-read alur Open CM + stakeholder (GT §Open cross-check). |
| **OQ-MEET-06** | P1 | Matriks step per produk MACF (D-07) — step mana berlaku/berbeda per produk; menyentuh divergensi car/motor (BR-CMPO-5/17) dan scope dual path. **Blocker annex per-produk, bukan PRD ini.** | Workshop product owner. |
| **OQ-MEET-04** | P2 | Eligibility **Instant-Approval lane** (D-01 S11) per produk/plafond — sumber `MemoApproved` tambahan yang harus dilayani handler §5.3 (source-agnostic). | Risk policy owner. |
| **OQ-CMPO-01** | P1 | "Status 0" flow-doc = header-level status parent aplikasi, atau status memo sendiri? (`0`=RFA-locked di kode; editing di `D`/`C`.) **Evidence baru v2**: GT STEP 8 — draft kontrak sync MOOFI dibuat dengan "Status RFA = '0'" → mendukung pembacaan header-level, belum konklusif. | Konfirmasi 01-intake; menentukan mapping status saat migrasi. |
| **OQ-CMPO-02 / OQ-CORE-03** | P1 | Arti bisnis `OP`, `ULI`, `LCR` (dan varian `Ost*`) — GL-reconciled? butuh `[LOCKED]`? (D-01 S10 memakai frasa "Plafond Hutang Pokok (OP)" — indikasi, bukan definisi.) | Domain-expert; menentukan treatment field frozen. |
| **OQ-CMPO-03** | P2 | Formula `OP` motor (asset−netDP) vs car (funded amount) — intentional atau copy-paste drift? | Business-rule owner; unify vs keep two formulas (terkait OQ-MEET-06). |
| **OQ-CMPO-04** | P2 | Kenapa car me-recompute frozen `OP`/`ULI`/`LCR` di disposisi non-approval? | Code-owner / regression test; konfirmasi do-not-replicate. |
| **OQ-CMPO-05** | P2 *(diturunkan dari P1)* | **Arah target sudah diputus D-01 S13** (semua approval mint tepat satu PO, termasuk Level-0). Residual: (a) forensik legacy — apakah memo Level-0 motor memang PO-eligible secara bisnis; (b) kebijakan **migrasi/backfill** memo Level-0 motor lama yang tak ber-PO. | Konfirmasi stakeholder sebelum backfill data legacy. |
| **OQ-CMPO-06** | P2 | Apa yang menentukan routing varian motor vs car? (kode `001`/`002` hanya inferensi behavioral). | Product-asset master; daftar kode asset-kind authoritative. |
| **OQ-CMPO-07** | P3 | Prosedur print PO plain (non-`staging`/`motor`) masih reachable dari entry-point live? **Catatan v2**: PDF 08072026 justru menyebut nama plain `sp_print_po_acquisition` (car `_mobil`) di STEP 13 — diskrepansi doc-vs-kode (kode live bind `_motor`/`_mobil_staging`) didokumentasikan per instruksi GT header. | Full-codebase search WEB/MINIAPI + konfirmasi ops. |
| **OQ-CMPO-08** | P2 | Bagaimana finalisasi **pertama** mendapat nilai awal — server-side clone dari aplikasi, atau re-key layar? **Kandidat v2**: draft kontrak STEP 8 (sync MOOFI). FE juga tak menemukan pre-seed (`64 OQ-CMPOFE-06`). | Konfirmasi 01/02 mekanisme seed (P1 clone/seed trace). |
| **OQ-CMPO-09** | P3 | Kenapa rate-write hanya untuk kode top-up/repeat-order (hapus kode lain)? (sumber UTF-16 corrupt). | Business-rule owner + re-export bersih. |
| **OQ-CMPO-10** | P2 | Write-site mana yang men-set memo `V`(Verify)/`R`(Review)? (BE: hanya display label; FE **mengirim** `V`/`C` via aksi approval yang sama — `64 OQ-CMPOFE-04`.) | Trace write path BE utk submission FE `StatusApproval="V"`; konfirmasi status vestigial. |
| **OQ-CMPO-11** | P2 | Nama & kontrak **event komite inbound** (03→04, **pre-mint**, saat memo `finalized`) untuk disposisi **correction** & **reject** yang men-transisikan memo → `corrected`/`rejected`. 03 hanya menamai `MemoApproved` (approve-only); correction/reject di-route ke Step 1–7 (GT STEP 12) **tanpa nama event**. 04 = **sole writer `CREDIT_MEMO`** → butuh sinyal untuk menulis transisi. **Berbeda** dari `MemoCorrectionOpened` (outbound 04, **post-mint** §5.5). Subscribe 1 event komite generik atau per-disposisi? | Pemilik umbrella memutus kontrak event lintas-service; menentukan handler §7.1 correction/reject (terkait OQ-AC-01). |
| **OQ-CMPO-12** | P3 | Failure-mode dispatch **email PO** (§5.4/§5.6) saat service email gagal: retry / degrade-gracefully (email advisory/non-blocking) atau block? Legacy `sp_send_email_print_PO` fail-closed-by-accident (no TRY/CATCH, no retry). Makin penting di v2 karena email = bagian standar STEP 13. | Stakeholder ops; menentukan perilaku retry/degrade (`email-sms-notifications.md §8`). |
| **OQ-CMPO-13** *(baru, dari census §3.1.2)* | P3 | Semantik 15 kolom `tr_CM` tak terjawab KB (`finish_date`, `is_tutup_buka`, `collectible_period`, `collectible_sequence_period`, `is_convert`, `po_source`, `po_source_other`, `is_flag_quota`, `program_id`, `installment_decimal`, `package_pembiayaan_secq`, `biaya_proses_seq`, `komper_max`, `CGSCabangNo`, `AR`) + enum/kolom kecil terkait yang tetap dibawa live dengan semantik `[OPEN]` (`disc_type`, `installment_type`, `ACP`, `CPFee`, `car_no_UMC`, `rate_value` — census #23, relasi vs effective/flat) — 15 kolom pertama diparkir `stg_legacy_tr_cm`, TIDAK masuk skema live sampai terjawab. | Domain-expert + data-profiling produksi; menentukan bawa/drop per kolom. |
| **OQ-GAP-08** | P2 | `Tr_TopUpMegaSolusi`: insert dinonaktifkan (comment `--- Tasya`) — fitur top-up Mega Solusi dibatalkan, atau mekanisme pindah ke `is_topup_ms` + repeat-order? Ada data historis yang harus dimigrasikan sebelum discard final (§3.10)? | Product owner + data-profiling (gap-entities §2.13). |
| **OQ-GAP-11** | P2 | Retensi & kebutuhan audit `DOKU_API_Log` / `DOKU_Inquiry_Account_Bank_Check` (berisi PII: NIK, tanggal lahir, nomor rekening) — kebijakan UU PDP untuk `trx_bank_account_inquiry` + keputusan bawa/tidaknya `log_doku_api` (§3.8). | Compliance/DPO + ops (gap-entities §4). |
| **OQ-CMPOFE-01** | P1 (dari KB FE) | Auto print+email **saat approve** hanya utk lini motor di legacy (`64 BR-CMPOFE-11`) — intentional atau gap? Menentukan apakah handler §5.3 juga men-trigger print otomatis, atau print selalu aksi manual §5.4 utk kedua lini. | Product owner; sampai terjawab, §5.3 TIDAK auto-print. |
| **OQ-CMPOFE-02** | P1 (dari KB FE) | Gate tombol Print membaca field status berbeda per lini (`status_ca` 4-wheel vs `status_cm` 2-wheel) — mana yang authoritative? Menentukan guard §5.4 + field listing §5.7. | Konfirmasi owner 02/03. |
| **OQ-CMPOFE-03** | P2 (dari KB FE) | Ambang blokir Open CM terhadap kemajuan downstream (legacy: 2-wheel cek status legalisasi NPP; 4-wheel tidak) — dengan Vertel (STEP 14, D-02) disisipkan, pada titik mana koreksi harus diblokir? | Owner 05 (NPP/Vertel) + ops; menentukan guard BR-CMPO-26. |
| **OQ-CMPOFE-08** | P2 (dari KB FE) | Layar insurance-cover binding batch (IC1–IC6, keyed by nomor NPP) = "insurance binding" D-01 S12, atau proses downstream terpisah (konteks INSURANCE)? Menentukan scope §1.2. | Product owner; relasi dua touchpoint asuransi. |
| **OQ-DOKU-01** | P1 | Siapa/apa yang mengisi `DOKU_*.responseStatus/responseAccName` (write-back SP tanpa caller)? | Menentukan apakah integrasi complete/live; owner response persist di rebuild. |
| **OQ-DOKU-04** | P3 | Arti semantik `responseStatus='0'` (success? pending? enum)? | Mapping status code DOKU di rebuild. |
| **OQ-REG-06** | P1 (global) | Fail-closed vs fail-open untuk semua regulated/validation gate saat dependency throw mid-check? | Kebijakan global; menyentuh validation finalisasi + DOKU gate. |
| **OQ-MCP-01** | P1 | Apakah app/session layer enforce "hanya assigned employee boleh act"? (SQL layer legacy tidak; super-user sudah DIHAPUS per D-09.) | Audit maker-checker (§6 BR-CMPO-19). |
| **OQ-ARCH-STACK** | P2 *(ter-narrow oleh D-12)* | **Bahasa BE = Java sudah `[LOCKED]` (D-12).** Residual: framework (USULAN Spring Boot §1.4), transport (REST vs gRPC vs bus), topologi infra — menunggu dokumen arsitektur **ITEC Bank Mega** (D-11, deadline 10 Juli 2026). | ITEC deliverable + arsitek; kontrak §4-5 tetap resource+field agnostik sampai final. |

> **Sudah RESOLVED (bukan blocker):** kepemilikan **PO minting** di 04 (digest; dikonfirmasi D-01 S13 —
> tepat satu PO per approval); **freeze OP/ULI/LCR + lock asuransi** milik 04 sebagai reaksi `MemoApproved`
> (bukan 03; GT STEP 12 + D-01 S12); **RFA lock** milik 01; **bahasa BE = Java** (D-12); **super user dihapus**
> (D-09); **sensus role cabang** (D-10). Ini keputusan final umbrella/meeting, bukan OQ terbuka.
