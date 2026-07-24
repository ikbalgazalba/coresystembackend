# PRD — Contract Management & PO Issuance

> **Kapabilitas 04 — contract-cm-po** dari bounded context **Acquisition** (MCF/FINCORE).
> **FASE 12–13**. Kapabilitas ini memfinalisasi **Credit Memo (CM)** — membekukan figur finansial
> `OP`/`ULI`/`LCR` `[LOCKED]`, Payment Option, Upping OTR, life-insurance + vehicle-insurance — lalu menerbitkan
> **Purchase Order (PO)** secara **deterministik & tunggal** sebagai reaksi atas event `MemoApproved` dari
> komite (03). Kepemilikan minting PO dieksplisitkan di sini karena **legacy memicu minting dari modul yang
> salah** (credit-analyst) — bug do-not-replicate (`hidden-gotchas.md §B GOTCHA-8`).
> **Bahasa**: Bahasa Indonesia; identifier/SP/tabel/field/enum/OQ-ID dipertahankan apa adanya.
> **Sumber otoritatif**: `.mega-sdd/knowledge-base/10-domains/23-contract-cm-po.md` (primary),
> `40-business-rules/operational-rules.md`, `40-business-rules/hidden-gotchas.md`,
> `99-rebuild-architecture/data-mutation-policy.md`, `50-integrations/doku-payment-gateway.md`,
> `.sp-manifests/_ACQUISITION-GROUND-TRUTH.md` (FASE 12/13). Konform ke PRD Payung `00-OVERVIEW.md`.

### Disiplin penanda (dari umbrella)

| Penanda | Arti |
|---|---|
| `[LOCKED]` | **WAJIB dipertahankan** (regulatori / kontrak eksternal / external-FK). Additive only. |
| `[INTENT]` | Outcome bisnis wajib dipenuhi; skema/mekanisme bebas didesain ulang. |
| `[ARTIFACT]` | Kecelakaan legacy — dibuang setelah konfirmasi stakeholder. |
| `[OPEN]` | Belum terjawab → masuk §11 (jangan diselesaikan diam-diam). |
| `[KEPUTUSAN DESAIN BARU]` | Desain rebuild baru, bukan turunan langsung legacy. |

---

## 1. Ruang Lingkup & Kepemilikan

### 1.1 Yang DIMILIKI kapabilitas ini (owns)

- **Finalisasi Credit Memo (2nd data entry)** — operational/CMO staff merevisi & memantapkan struktur finansial
  memo selama memo masih **editable** (`status ∈ {draft, corrected}`): Payment Option / installment plan,
  **Upping OTR** (re-entry harga OTR/aset), down payment (gross & net), tenor, admin fee & process fee, seleksi
  asuransi. Sumber: `23 §3a S1`, `23 §5.1-5.3`, GT FASE 12.
- **Sub-record asuransi**: **vehicle-insurance** (`TrCmInsurance`) dan **life-insurance-on-credit**
  (`TrCmLifeInsuranceCredit`, lini car). Sumber: `23 §4`, `23 §11`.
- **Freeze figur finansial `[LOCKED]`** — pada event `MemoApproved`, kapabilitas ini menghitung & membekukan
  `OP` (outstanding principal), `ULI`, `LCR` beserta snapshot `first_*` di memo, serta menyimpan snapshot
  point-in-time financing figures. Sumber: `23 §5.6-5.7`, `operational-rules.md OR-13`. **Freeze adalah reaksi
  04 terhadap keputusan 03**, bukan milik 03 (digest: 03 "BUKAN miliknya: freeze figur finansial").
- **PO minting deterministik tunggal** — pada `MemoApproved` (semua terminasi hierarki), 04 mint `po_number`
  ke `TrPo` dengan `po_number` di-assign **saat mint** (bukan `NULL`). Sumber: `23 §5.8`, digest
  `boundary_ownership PO minting`.
- **Printing PO** (position-gated), **email PO ke dealer**, dan **koreksi PO (Open CM)** saat unit fisik beda
  (warna/tipe) **tanpa re-entry CAS**. Sumber: `23 §5.9-5.13`, GT FASE 13, `operational-rules.md OR-14`.
- **Konsumsi DOKU account-validate** (Cek Rekening) — validasi rekening bank customer sebelum finalisasi.
  Seam **dimiliki 04** (umbrella §9 #6). Sumber: `50-integrations/doku-payment-gateway.md`.
- **Emit event `POIssued`** (dan `MemoCorrectionOpened` pada koreksi).

### 1.2 Yang BUKAN miliknya (non-goal)

- **Keputusan approve/reject/correction komite** — milik **03-approval-committee** (FASE 10–11). 04 hanya
  **mengonsumsi** event `MemoApproved` untuk mem-freeze + mint. Sumber: digest row 03/04.
- **RFA lock (`sp_rfa_cm`, transisi ke status `0` = RFA-locked)** — milik **01-intake-cas** (boundary FASE 8).
  04 **menghormati** editability guard-nya (memo hanya editable di `draft`/`corrected`), tetapi **tidak
  memiliki** endpoint RFA. Sumber: digest `boundary_ownership RFA`; umbrella §5.
- **Komposisi awal `trans_type_id`** — disusun **02-credit-analysis** (`sp_get_trans_type_id_cm`,
  risk-tier-qualified). 04 hanya **membawa** nilainya char-for-char. Sumber: umbrella §7.1.
- **Validasi BAST + chassis/engine, aktivasi kontrak (NPP), cetak Financing Agreement** — milik
  **05-npp-legalization** (FASE 14). Sumber: digest row 05.
- **GL posting / disbursement / subledger** — post-acquisition (PULL). Sumber: umbrella §1.2.
- **JANGAN memicu PO dari modul credit-analyst** (`CreditAnalystRepositoryEF.cs:692-708`) — bug legacy
  do-not-replicate (`hidden-gotchas.md §B GOTCHA-8`). Minting **terpusat** di 04.

### 1.3 Departure kunci (fix bug legacy) — [KEPUTUSAN DESAIN BARU]

**04 adalah SATU-SATUNYA writer `CREDIT_MEMO`** — termasuk transisi `status → finalized/approved/corrected` dan freeze
`OP`/`ULI`/`LCR`. **03 hanya menulis `APPROVAL_STEP`/`APPROVAL_HISTORY` dan meng-emit `MemoApproved`.** Di
legacy, aksi komite (`sp_approve_cm`) menulis memo **dan** PO di-mint dari modul credit-analyst yang salah.
Rebuild **sengaja menyimpang**: seluruh mutasi memo + freeze + mint dikolapskan ke **satu handler
deterministik 04** yang bereaksi atas `MemoApproved`. Ini adalah inti kapabilitas ini. Sumber:
`hidden-gotchas.md §B GOTCHA-6/GOTCHA-8`, digest `boundary_ownership PO minting`.

---

## 2. Aktor & Peran

Sumber: `23 §2`; umbrella §2 (tak ada RBAC statis di legacy — peran direkonstruksi; rebuild bebas
memperkenalkan permission layer yang benar).

| Peran | Deskripsi | Aksi dimiliki 04 | Mutabilitas |
|---|---|---|---|
| **Operational / CMO staff** | Merevisi & memantapkan struktur finansial draft memo sebelum lock; minta validasi rekening DOKU; ambil quote asuransi. | Finalization (S1), insurance-quote, bank-account validate | `[INTENT]` |
| **Branch admin** | Memicu **koreksi (Open CM)** saat unit fisik beda. (RFA lock dipicu di boundary 01, **bukan** di sini.) | PO/memo correction (S5) | `[INTENT]` |
| **PO printer (position-gated branch employee)** | Hanya employee yang posisinya lolos lookup HR (`sp_get_check_user_print_po`) boleh mencetak PO. Nilai posisi yang lolos = `[OPEN]` (master HR eksternal). | Print PO, email PO | `[LOCKED]` (gate ada) / `[OPEN]` (katalog posisi) |
| **Credit-committee hierarchy (03, sibling)** | Menentukan disposisi final; **memasok** event `MemoApproved` yang memicu freeze + mint. Bukan aktor internal 04. | — (upstream) | out-of-scope |
| **Dealer (eksternal)** | Menerima PO cetak/email sebagai guarantee unit dikirim; tidak menyentuh sistem langsung. | — (penerima output) | `[INFERRED]` |

---

## 3. Model Data

Bentuk **target** rebuild (tech-agnostic: `identifier`, `string`, `decimal`, `enum`). Field `[LOCKED]` =
additive only (tanpa ubah nama/tipe/nilai). Konform entitas + enum umbrella §6.

### 3.1 Entitas dimiliki 04

**`CREDIT_MEMO`** (legacy `tr_CM`) — pemilik finalize + freeze; `trans_type_id` disusun 02.

| Field | Tipe | Catatan / Marker |
|---|---|---|
| `id` | identifier PK | |
| `application_id` | identifier FK → CREDIT_APPLICATION (01) | |
| `trans_type_id` | string | **`[LOCKED]` external-FK** — dibawa char-for-char dari 02; dicocokkan ke `FC_MSTAPP_MCF` approval-hierarchy. Umbrella §7.1. |
| `product_line` | enum `car\|motor` | Menentukan varian formula/insurance. Kode aset `001`=motor/`002`=car = `[OPEN]` OQ-CMPO-06. |
| `payment_option` | decimal / ref | Payment option + installment plan (S1). `23 §4`. |
| `otr_price` | decimal | Harga OTR/aset; **Upping OTR** = re-submit field ini selama editable (`[OPEN]` OQ karena tak ada field/SP dedikasi — `23 §4`). |
| `down_payment_gross` / `down_payment_net` | decimal | `23 §4`. |
| `tenor_months` | integer | |
| `admin_fee` / `process_fee` | decimal | |
| `outstanding_principal` (`OP`) | decimal | **`[LOCKED]` frozen-at-approval**. Arti bisnis `OP` = `[OPEN]` OQ-CMPO-02/OQ-CORE-03. `data-mutation-policy.md`. |
| `uli` (`ULI`) | decimal | **`[LOCKED]` frozen** (= `OP` − `LCR`? arti `[OPEN]`). `23 BR-CMPO-4`. |
| `lcr` (`LCR`) | decimal | **`[LOCKED]` frozen** (= installment × tenor). `23 BR-CMPO-4`. |
| `first_op` / `first_uli` / `first_lcr` | decimal | Snapshot `first_*` saat approval (baseline auditable immutable). `23 §5.6`. |
| `life_insurance` | boolean | Flag; detail di `TrCmLifeInsuranceCredit`. |
| `vehicle_insurance` | boolean | Flag; detail di `TrCmInsurance`. |
| `status` | enum `draft\|finalized\|approved\|corrected\|rejected` | Umbrella §7.2 (kanonik). Mapping legacy: `D`=draft, `C`=corrected, `0`=finalized (RFA-locked/awaiting hierarchy), approved=approved, `rejected`=terminal (disposisi reject komite → §7.1). `V`/`R` display-only = `[OPEN]` OQ-CMPO-10. |
| `approved_by` / `approved_at` | identifier / datetime | Distamp saat freeze. `23 §5.6`. |

**`PURCHASE_ORDER`** (legacy `tr_PO`) — pemilik mint + print + correction.

| Field | Tipe | Catatan / Marker |
|---|---|---|
| `id` | identifier PK | |
| `memo_id` | identifier FK → CREDIT_MEMO | |
| `po_number` | string | **Di-assign SAAT mint** (bukan `NULL` seperti legacy `tr_cm.po_no`). `hidden-gotchas.md §B GOTCHA-6`; umbrella §7.1. |
| `status` | enum `issued\|corrected\|fulfilled` | Umbrella ERD §6. Mapping legacy open/printed/reopened → lihat §7. `[INTENT]`. |
| `print_count` | integer | **`[LOCKED]` audit trail** (`23 BR-CMPO-9`). Increment atomic (fix race Edge Case 3). |
| `first_print_user` / `first_print_at` | identifier / datetime | **`[LOCKED]` audit**. |
| `last_print_user` / `last_print_at` | identifier / datetime | **`[LOCKED]` audit**. |

**`CM_INSURANCE_VEHICLE`** (legacy `TrCmInsurance`) — cover type, insurance type, model/tier, premium.
Premium dari rate-master ber-referensi **OJK** (`sp_generate_insurance_cost_R4_OJK` /
`sp_generate_estimated_insurance_cost_R2`) — **`[LOCKED]` metodologi rate-table** (`23 BR-CMPO-13`).

**`CM_INSURANCE_LIFE`** (legacy `TrCmLifeInsuranceCredit`, lini car) — premium & terms life-insurance-on-credit.
`23 §4`.

**`CM_FINANCING_SNAPSHOT`** (frozen point-in-time) — snapshot figur financing (price, DP, fees, rate) saat
approval; versi lama diarsipkan ke log sebelum diganti. `23 §5.7`. Downstream dibaca 05 (NPP). `[INTENT]`
(outcome frozen baseline wajib; mekanisme delete-and-restore bebas didesain ulang).

**`PO_CORRECTION_LOG`** — catatan event reopen/correction PO. `23 §6`.

### 3.2 Shared entities yang DIRUJUK (bukan milik 04)

| Entitas | Pemilik | Dipakai 04 untuk |
|---|---|---|
| `CREDIT_APPLICATION` | 01-intake | Header + seed nilai finalisasi (mekanisme seed = `[OPEN]` OQ-CMPO-08). |
| `CREDIT_ANALYSIS` / `trans_type_id` | 02 | `trans_type_id` (routing key) dibawa apa adanya. |
| `APPROVAL_STEP` / `APPROVAL_HISTORY` | 03 | Sumber event `MemoApproved`. |
| `DEALER` / `ASSET` / OTR-price master | masters (read-only) | Identifikasi dealer/aset, lookup harga OTR (`sp_get_Harga_OTR`). |
| `FINANCING_AGREEMENT` | 05-npp | Konsumen downstream memo terfinalisasi + frozen figures. |
| `RAC_SCREENING` | 02 | Koreksi meng-emit event re-screen (bukan DELETE lintas-DB — GOTCHA-11). |

---

## 4. API Endpoint

> Target stack `[OPEN]` (OQ-ARCH-STACK). Kontrak ditulis level **resource + field**, **framework-agnostic**;
> HTTP method/path adalah representasi, bukan penguncian REST.

| Method | Path | Deskripsi | Auth/Role |
|---|---|---|---|
| `GET` | `/credit-memos/{memoId}` | Baca memo + frozen figures + insurance sub-record. | Operational/CMO, Branch |
| `PUT` | `/credit-memos/{memoId}/finalization` | Finalisasi/revisi struktur finansial (payment option, Upping OTR, DP, fees, seleksi asuransi). **Guard**: `status ∈ {draft, corrected}`. | Operational/CMO |
| `GET` | `/credit-memos/{memoId}/insurance-quote` | Hitung premium vehicle/life per lini (setiap lini **always-resolving** — fix silent-zero BR-CMPO-12). | Operational/CMO |
| `GET` | `/credit-memos/{memoId}/bank-account:validate` | Validasi rekening bank via **DOKU ACL** (sync). Query: `bank_id`, `account_no`. | Operational/CMO |
| `EVENT` | *(subscribe)* `MemoApproved` → **freeze `OP`/`ULI`/`LCR` + mint PO** | Handler internal **idempotent** (key = `memo_id` + `approval_decision_id`). `MemoApproved` **approve-only**; correction/reject via event komite terpisah (OQ-CMPO-11), **bukan** handler ini. Mint tunggal, semua terminasi hierarki. | Internal (system) |
| `GET` | `/credit-memos/{memoId}/purchase-order` | Ambil PO untuk sebuah memo/credit (legacy `get-po_no`; kini `po_number` non-NULL). | Branch |
| `GET` | `/purchase-orders/{poNumber}` | Baca PO + audit print. | Branch |
| `POST` | `/purchase-orders/{poNumber}/print` | Cetak PO. **Gate**: posisi employee lolos `sp_get_check_user_print_po`; memo `approved` + frozen snapshot ada. Increment `print_count` atomic. | PO printer (position-gated) |
| `POST` | `/purchase-orders/{poNumber}/email` | Email PO ke dealer via **service email least-privilege** (fix do-not-replicate legacy BR-CMPO-20); recipient dari data dealer terverifikasi; **idempotent** by `is_send` (BR-CMPO-21). **Guard**: setelah minimal 1x print. Kontrak §5.6. | PO printer / Branch |
| `POST` | `/purchase-orders/{poNumber}/correction` | **Open CM**: reopen memo → `corrected` + PO → `corrected`, restore live figures dari snapshot, emit `MemoCorrectionOpened`. **Tanpa re-entry CAS**. Preserve print history. | Branch admin |

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

### 5.3 (Event handler) `MemoApproved` → freeze + mint

> **`MemoApproved` = approve-only** — 03 meng-emit event ini **hanya** pada terminal **approve** (`03 §5.2/§7.2/§10.2`;
> umbrella). Handler ini menangani **jalur approve saja**. Disposisi **correction/reject** **TIDAK** datang lewat
> `MemoApproved`; keduanya tiba lewat **event komite inbound terpisah** (nama kontrak lintas-service `[OPEN]`
> **OQ-CMPO-11**) dan ditangani di §7.1 (memo → `corrected`/`rejected`) **tanpa** freeze/mint. Menyuntik
> `disposition=correction|reject` ke handler ini = **phantom input** — jangan.

Input event (dari 03, selalu approve):

```json
{ "memo_id": "CM-000123", "approval_decision_id": "AD-777", "disposition": "approved", "hierarchy_level": 2 }
```

Efek deterministik (idempotent by `memo_id`+`approval_decision_id`):
1. Hitung & bekukan `OP`/`ULI`/`LCR` + `first_*` di memo; set `status=approved`, stamp `approved_by/at`.
   Karena `MemoApproved` **approve-only**, freeze berjalan saat event diterima; figur **tak pernah** disentuh pada
   correction/reject (do-not-replicate BR-CMPO-6/GOTCHA-10 — jalur itu ditangani event komite terpisah §7.1, bukan handler ini).
2. Mint `PURCHASE_ORDER` dengan `po_number` di-assign (non-NULL), `status=issued`. **Semua terminasi hierarki
   termasuk Level-0** (target [KEPUTUSAN DESAIN BARU] yang memperbaiki GOTCHA-8; eligibility Level-0 motor tetap
   `[OPEN]` OQ-CMPO-05).
3. Emit `POIssued { memo_id, po_number }`.

### 5.4 `POST /purchase-orders/{poNumber}/print`

Response `200 OK`:

```json
{ "po_number": "PO-2026-000045", "print_count": 1, "first_print_at": "2026-07-07T02:10:00Z", "printed_by": "EMP-88", "correlation_id": "..." }
```

Response `403 Forbidden` (posisi tak berwenang): `{ "code": "PO_PRINT_NOT_AUTHORIZED", ... }`
Response `409 Conflict` (memo belum approved / snapshot belum ada — **fix Edge Case 6** silent-empty):
`{ "code": "PO_NOT_READY", "message": "Memo belum final-approved / frozen snapshot belum tersedia." }`

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
  "correlation_id": "..."
}
```

> Koreksi **tidak** menghapus record RAC lintas-DB (fix GOTCHA-11) — ia meng-emit `MemoCorrectionOpened`; 02
> me-re-screen RAC secara **idempotent**, 01 me-re-lock. **Dua entry-point koreksi** — (a) application-level via 01 `E10 reopen`, (b) PO-level via 04 Open-CM — keduanya bermuara ke re-screen idempotent di 02; idempotency menjamin aman bila terpicu dobel; pemilik re-lock aplikasi = **01**. Print history **dipertahankan** (fix Edge Case 4).

### 5.6 `POST /purchase-orders/{poNumber}/email` (kirim PO ke dealer)

> **Fix do-not-replicate legacy** (BR-CMPO-20): kirim via **service email app-tier ber-identity least-privilege**
> (bukan Database Mail `EXECUTE AS LOGIN='sa'`); recipient di-resolve **server-side dari data dealer terverifikasi**
> (bukan fallback `julia@mcf.co.id` / trial-branch override); body **ter-escape/templated** (bukan HTML string-concat).
> **Guard**: memo `approved` + PO sudah di-print ≥1x. **Idempoten** by `tr_PO_send_to_email.is_send` (BR-CMPO-21).

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

---

## 6. Aturan Bisnis

Setiap BR dilacak ke KB. `[LOCKED]`=WAJIB dipertahankan; `[INTENT]`=outcome dipertahankan, desain bebas;
`[ARTIFACT]`=do-not-replicate/dibuang.

| ID | Aturan | Sumber KB | Marker | Catatan rebuild |
|---|---|---|---|---|
| BR-CMPO-1 | Field struktur finansial memo hanya bisa diubah saat `status ∈ {draft, corrected}`; setelah locked/approved, edit ditolak. | `23 §7 BR-CMPO-1` | `[INTENT]` | Guard editability wajib; return `409 MEMO_NOT_EDITABLE`. |
| BR-CMPO-2 | Satu aksi finalisasi melayani create + revisi memo (existence-check). | `23 §7 BR-CMPO-2` | `[ARTIFACT]` | Outcome (revisi progresif) dipertahankan; mekanisme shared-procedure bebas → pisahkan create/update bila lebih bersih. |
| BR-CMPO-3 | RFA lock diblok guard Draft/Correction yang sama; sukses → memo ke state locked "awaiting hierarchy". | `23 §7 BR-CMPO-3` | `[INTENT]` | **RFA dimiliki 01** (boundary) yang emit `ApplicationLocked`; **04 (sole writer) menulis transisi** `draft/corrected→finalized` sebagai handler event itu. |
| BR-CMPO-4 | Final approval menghitung & membekukan `OP`, `LCR`(=installment×tenor), `ULI`(=selisih) + snapshot `first_*`. | `23 §7 BR-CMPO-4`, `OR-13` | `[LOCKED]` | Perilaku computed-and-frozen WAJIB; arti `OP`/`ULI`/`LCR` = `[OPEN]` OQ-CMPO-02. |
| BR-CMPO-5 | Formula `OP` berbeda antar lini: motor = asset price − net DP; car = funded amount langsung. | `23 §7 BR-CMPO-5` | `[INTENT]` | Outcome (1 figur principal per lini) dipertahankan; **mana formula authoritative = `[OPEN]` OQ-CMPO-03** — jangan pilih diam-diam. |
| BR-CMPO-6 | Varian car me-recompute/overwrite frozen `OP`/`LCR`/`ULI` bahkan pada disposisi non-approval. | `23 §7 BR-CMPO-6`, `hidden-gotchas.md §C GOTCHA-10` | `[ARTIFACT]` | **Do-not-replicate**: freeze **hanya** pada `disposition=approved`; jangan sentuh figur pada correction/reject. |
| BR-CMPO-7 | PO number/record dibuat otomatis saat level approval final selesai (bukan aksi langsung di domain ini). | `23 §7 BR-CMPO-7` | `[LOCKED]` | Trigger point (post-approval) dipertahankan; **pindahkan minting ke 04** (bukan credit-analyst). |
| BR-CMPO-8 | Terminasi "Level 0" tak auto-mint PO; hanya lini car punya self-heal mint saat print, motor tidak. | `23 §7 BR-CMPO-8`, `hidden-gotchas.md §B GOTCHA-8` | `[ARTIFACT]`/`[OPEN]` | **Do-not-replicate asimetri**: target — **semua terminasi (termasuk Level-0) mint** deterministik. Eligibility Level-0 motor = `[OPEN]` OQ-CMPO-05. |
| BR-CMPO-9 | Print PO increment counter, stamp first/last print user+date, lock status. | `23 §7 BR-CMPO-9` | `[LOCKED]` | Audit trail dealer/compliance-facing WAJIB dipertahankan; increment **atomic** (fix race Edge Case 3). |
| BR-CMPO-10 | Koreksi PO = satu aksi reopen: PO→open, memo→Correction, restore live figures dari snapshot, delete snapshot — tanpa re-create aplikasi. | `23 §7 BR-CMPO-10`, `OR-14` | `[INTENT]` | Outcome (koreksi tanpa re-entry CAS) WAJIB; mekanisme delete-and-restore bebas; **preserve print history** (fix Edge Case 4). |
| BR-CMPO-11 | Reopen memo (satu asset kind = motor) menghapus record RAC Bank Mega → paksa re-screen eksternal. | `23 §7 BR-CMPO-11`, `hidden-gotchas.md §C GOTCHA-11` | `[LOCKED]` (kebutuhan re-screen) | Kebutuhan re-screen WAJIB; **mekanisme diganti**: emit `MemoCorrectionOpened` (idempotent), **bukan** DELETE lintas-DB. |
| BR-CMPO-12 | Shared "get insurance fee" hanya menghitung premium untuk satu asset kind (motor `001`); lini lain silent-zero. | `23 §7 BR-CMPO-12` | `[ARTIFACT]` | **Do-not-replicate**: setiap lini punya path premium eksplisit always-resolving; opt-out asuransi = flag eksplisit, bukan efek zero-fee. |
| BR-CMPO-13 | Premium asuransi dihitung dari rate-master ber-referensi **OJK** (tabel R2/R4 OJK). | `23 §7 BR-CMPO-13` | `[LOCKED]` | Metodologi rate-table-driven dipertahankan; storage/lookup bebas. |
| BR-CMPO-14 | Rate "bottom rate" ditulis ke rate-history hanya untuk kode top-up/repeat-order; kode lain dihapus. | `23 §7 BR-CMPO-14` | `[OPEN]` | Sumber ter-corrupt (UTF-16); kondisi persis = `[OPEN]` OQ-CMPO-09. |
| BR-CMPO-15 | Kegagalan validasi dikembalikan sebagai row error di dalam envelope "success"; transaksi tetap commit. | `23 §7 BR-CMPO-15`, `23 §9 Edge Case 1` | `[ARTIFACT]` | **Do-not-replicate**: surface sebagai `4xx` non-success (`422`), bukan success berisi error. |
| BR-CMPO-16 | `tr_cm.po_no` selalu ditulis `NULL` walau ada reader (`get-po_no`). | `hidden-gotchas.md §B GOTCHA-6` | `[ARTIFACT]` | **Do-not-replicate**: `po_number` di-assign **saat mint** (non-NULL). |
| BR-CMPO-17 | Car & motor diverge di gate + formula (age/DP caps, OP/ULI/LCR, blacklist codes). | `hidden-gotchas.md §C GOTCHA-10` | `[INTENT]` | Perlakukan car/motor sebagai **konfigurasi satu engine** table-driven, bukan dua code path divergen. |
| BR-CMPO-18 | DOKU call: HTTP dari dalam T-SQL (`sp_OACreate`), IP hardcoded, response discarded, no auth, JSON string-concat. | `doku-payment-gateway.md §4-9`, `data-mutation-policy.md` | `[ARTIFACT]` | **Do-not-replicate**: HTTP client app-tier + timeout/retry/circuit-breaker + own response persist + real JSON serializer. Write-back owner = `[OPEN]` OQ-DOKU-01. |
| BR-CMPO-19 | Setiap transisi memo/PO material tercatat auditable (maker-checker); print position-gated. | `operational-rules.md OR-2`, `23 §2` | `[INTENT]` | Audit + identitas actor di-enforce app-layer (OQ-MCP-01). |
| BR-CMPO-20 | **Email PO** (`sp_send_email_print_PO`) legacy: (a) `EXECUTE AS LOGIN='sa'` privilege-escalation sebelum `sp_send_dbmail`; (b) hardcoded personal-email fallback `julia@mcf.co.id` + trial-branch recipient override ke alamat personal; (c) unescaped HTML string-concat di body email. | `email-sms-notifications.md §4/§5/§10/§11`, `data-mutation-policy.md` (Do-not-preserve anti-patterns) | `[ARTIFACT]` | **Do-not-replicate**. DIPERBAIKI: kirim via **service email app-tier ber-identity least-privilege** (bukan `sa`/Database Mail); recipient dari **data dealer terverifikasi** (bukan fallback personal / trial-branch override); body **ter-escape/templated** (real templating engine, bukan concat). Kontrak §5.6; seam §8. Failure-mode = `[OPEN]` OQ-CMPO-12. |
| BR-CMPO-21 | Send email PO **idempoten**: cek `tr_PO_send_to_email.is_send=0` sebelum kirim, set `1` setelah kirim (per `PO_no`) → request kedua = no-op untuk bagian mail-send. | `email-sms-notifications.md §9` | `[INTENT]` | Outcome **idempotency WAJIB dijaga**; mekanisme flag/tabel bebas didesain ulang. Print-count & mail-send = dua domain idempotensi terpisah dalam PO yang sama. |

---

## 7. State Machine

Dua state value ter-kopel: `CREDIT_MEMO.status` dan `PURCHASE_ORDER.status`. Termasuk jalur non-happy-path.

### 7.1 `CREDIT_MEMO.status` — enum `draft | finalized | approved | corrected` (+terminal `rejected` di app)

> Mapping legacy: `D`=draft, `C`=corrected, `0`=finalized (RFA-locked/awaiting hierarchy), approved=approved.
> `V`(Verify)/`R`(Review) muncul **hanya sebagai display label** guard — tak ada write site (`[OPEN]` OQ-CMPO-10);
> tidak dimodelkan sebagai state tercapai sampai dikonfirmasi. Sumber: `23 §8`.

| Dari | Aksi | Ke | Guard/Prasyarat |
|---|---|---|---|
| *(none)* | Finalisasi pertama (seed) | `draft` | Seed dari aplikasi (mekanisme `[OPEN]` OQ-CMPO-08). |
| `draft` | Revisi finalisasi (S1) | `draft` | `status ∈ {draft, corrected}` (BR-CMPO-1). |
| `corrected` | Revisi finalisasi (S1) | `corrected` | Sama guard. |
| `draft`/`corrected` | RFA lock (**boundary 01** emit `ApplicationLocked`) | `finalized` | Guard editability; **01 emit event, 04 (sole writer) menerapkan transisi** finalize pada event itu (BR-CMPO-3). |
| `finalized` | Komite `correction` (03 → **event komite inbound**, nama `[OPEN]` OQ-CMPO-11) | `corrected` | Disposisi correction; memo balik ke branch untuk rework. **Pre-mint** — **tanpa** freeze, **tanpa** mint PO. Bukan flow post-mint Open-CM §5.5. |
| `finalized` | Komite `reject` (03 → **event komite inbound**, nama `[OPEN]` OQ-CMPO-11) | `rejected` (terminal) | Disposisi reject; write-site legacy `[OPEN]` (`23 §5.5`). **Pre-mint** — **tanpa** freeze, **tanpa** mint PO. Closure aplikasi = concern 01 (OQ-AC-01). |
| `finalized` | **`MemoApproved` (04 handler)** | `approved` | `disposition=approved`; freeze `OP`/`ULI`/`LCR`+`first_*` (BR-CMPO-4); **freeze hanya di sini** (fix BR-CMPO-6). |
| `approved` | Koreksi PO (Open CM) | `corrected` | Restore live figures dari snapshot; emit `MemoCorrectionOpened` (BR-CMPO-10/11). |

### 7.2 `PURCHASE_ORDER.status` — enum `issued | corrected | fulfilled`

> Print-lock/print-count/first-last-print = **atribut**, bukan status (konform umbrella ERD §6). Mapping legacy:
> open/printed → `issued` (+atribut print); reopened → `corrected`.

| Dari | Aksi | Ke | Guard/Prasyarat |
|---|---|---|---|
| *(none)* | **Mint** pada `MemoApproved` (04) | `issued` | `po_number` di-assign non-NULL; idempotent by `memo_id`+`approval_decision_id`; semua terminasi hierarki (BR-CMPO-7/8). |
| `issued` | Print (position-gated) | `issued` | Memo `approved` + snapshot ada (fix Edge Case 6); increment `print_count` atomic; stamp print user/date (BR-CMPO-9). |
| `issued` | Email ke dealer | `issued` | Setelah ≥1 print (`23 §5.11`); kirim via service email least-privilege + idempoten `is_send` (BR-CMPO-20/21, §5.6). |
| `issued` | Koreksi (Open CM) | `corrected` | Unit fisik beda; preserve print history (fix Edge Case 4); memo → `corrected`. |
| `corrected` | Re-finalize → re-approve → mint/reuse | `issued` | Loop balik; **bisa** `po_number` baru (target: audited renumber, bukan destructive delete). |
| `issued` | Downstream fulfill (dealer ship / NPP aktif) | `fulfilled` | Konsumsi PULL oleh 05/downstream (terminal untuk 04). |

**Jalur gap `[OPEN]`**: terminasi **Level-0 lini motor** — legacy tak auto-mint & tak punya self-heal
(OQ-CMPO-05). Target rebuild: mint deterministik semua terminasi; eligibility Level-0 motor tetap butuh
konfirmasi stakeholder sebelum coding.

---

## 8. Integrasi Eksternal

| Seam | Arah | Sync/Async | Pemilik | ACL / do-not-replicate | Sumber |
|---|---|---|---|---|---|
| **DOKU** (bank-account inquiry "Cek Rekening") | outbound | **sync** | **04** | **WAJIB via ACL app-tier**. Ganti `sp_OACreate` HTTP-dari-T-SQL + IP hardcoded (`10.90.7.3:81`) dengan HTTP client (timeout/retry/circuit-breaker); own response persist (write-back owner `[OPEN]` OQ-DOKU-01); real JSON serializer (fix unescaped concat); no-auth = review (OQ auth). **Bukan** funds-movement. | `doku-payment-gateway.md §2-11` |
| **Email PO ke dealer** (kirim PO cetak) | outbound | **async** (fire-and-forget) | **04** | **WAJIB via ACL/service email app-tier**. Ganti `sp_send_email_print_PO` (Database Mail dari T-SQL + `EXECUTE AS LOGIN='sa'`) dengan **service email ber-identity least-privilege**; recipient dari **data dealer terverifikasi** (bukan fallback `julia@mcf.co.id` / trial-branch override); body **ter-escape/templated** (bukan HTML concat); pertahankan guard idempoten `is_send`. Failure-mode `[OPEN]` OQ-CMPO-12. **Bukan** funds-movement. | `email-sms-notifications.md §3-11`, `data-mutation-policy.md` |

**Boundary event (internal, bukan seam eksternal):**

- **Konsumsi `MemoApproved`** dari **03** — memicu freeze + mint (idempotent). `23 §5.6-5.8`.
- **Emit `POIssued`** — dikonsumsi **05-npp** (baca memo terfinalisasi + frozen figures untuk Financing
  Agreement) via **PULL**. `23 §6`, digest.
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

**Freeze deterministik (fix BR-CMPO-6)**
- **AC-5** *Given* memo `finalized`, *When* `MemoApproved{disposition=approved}`, *Then* `OP`/`ULI`/`LCR`+`first_*`
  dibekukan, `status=approved`, `approved_by/at` distamp. (BR-CMPO-4)
- **AC-6a** *(jalur correction)* *Given* memo `finalized`, *When* **event komite disposisi `correction`** tiba
  (bukan `MemoApproved`; event komite inbound, nama `[OPEN]` OQ-CMPO-11), *Then* memo → `corrected` (balik ke
  branch untuk rework), figur frozen **TIDAK** disentuh (car maupun motor), **tidak** ada PO ter-mint. (fix
  BR-CMPO-6 / GOTCHA-10)
- **AC-6b** *(jalur reject)* *Given* memo `finalized`, *When* **event komite disposisi `reject`** tiba (bukan
  `MemoApproved`), *Then* memo → `rejected` (terminal), figur frozen **TIDAK** disentuh, **tidak** ada PO
  ter-mint; closure aplikasi = concern 01 (OQ-AC-01). (fix BR-CMPO-6 / GOTCHA-10)

**PO minting terpusat & deterministik (fix GOTCHA-6/8)**
- **AC-7** *Given* `MemoApproved` (level manapun, **termasuk Level-0**), *When* handler jalan, *Then* tepat
  **satu** PO ter-mint dengan `po_number` **non-NULL**, `status=issued`. (BR-CMPO-7/16)
- **AC-8** *Given* `MemoApproved` di-redeliver/di-retry (`approval_decision_id` sama), *When* handler jalan
  ulang, *Then* **tidak** ada PO ganda (idempotent). (umbrella §7.4)
- **AC-9** *Given* modul credit-analyst (03/02), *When* approval selesai, *Then* modul itu **TIDAK** mint PO —
  minting **hanya** di 04. (fix GOTCHA-8)

**Print**
- **AC-10** *Given* employee posisi tak berwenang, *When* `POST /print`, *Then* `403 PO_PRINT_NOT_AUTHORIZED`.
- **AC-11** *Given* memo belum approved / snapshot belum ada, *When* `POST /print`, *Then* `409 PO_NOT_READY`
  (bukan hasil kosong senyap). (fix Edge Case 6)
- **AC-12** *Given* dua request print near-simultan, *When* keduanya jalan, *Then* `print_count` naik akurat
  (atomic), tak ada dua "first print". (fix Edge Case 3)

**Koreksi (Open CM)**
- **AC-13** *Given* PO `issued` (sudah di-print) & unit fisik beda, *When* `POST /correction`, *Then* memo →
  `corrected`, PO → `corrected`, live figures ter-restore, **print history dipertahankan**, dan `MemoCorrectionOpened`
  ter-emit; **tanpa** membuat aplikasi CAS baru. (BR-CMPO-10; fix Edge Case 4)
- **AC-14** *Given* koreksi lini motor, *When* `POST /correction`, *Then* RAC re-screen dipicu via **event**
  (idempotent), **bukan** DELETE lintas-DB ke Bank Mega. (fix GOTCHA-11)

**Email PO (fix do-not-replicate legacy)**
- **AC-15** *Given* PO `issued` sudah di-print ≥1x, *When* `POST /email`, *Then* PO terkirim ke **dealer
  terverifikasi** via **service email least-privilege** (bukan `sa`/Database Mail), body **ter-escape/templated**,
  `is_send` → `1`. (BR-CMPO-20/21)
- **AC-16** *Given* PO sudah pernah di-email (`is_send=1`), *When* `POST /email` lagi, *Then* **no-op idempotent**
  (tidak kirim ulang), response menandai `already_sent`. (BR-CMPO-21)
- **AC-17** *Given* PO `issued` **belum** di-print, *When* `POST /email`, *Then* `409 PO_NOT_PRINTED` (guard ≥1 print).

---

## 10. Dependency

### 10.1 Upstream dikonsumsi

| Sumber | Bentuk | Isi |
|---|---|---|
| **01-intake-cas** | read + boundary | `CREDIT_APPLICATION` header; **RFA lock** (guard editability, dipicu 01); seed nilai finalisasi (`[OPEN]` OQ-CMPO-08). |
| **02-credit-analysis** | read | `trans_type_id` (routing key, char-for-char) yang disusun 02. |
| **03-approval-committee** | **event** `MemoApproved` | Trigger tunggal freeze + mint. |
| **Masters (dealer/asset/OTR)** | read-only | Identifikasi dealer/aset, `sp_get_Harga_OTR`. Owned vs read-only = `[OPEN]` OQ-EXTMASTERS-01. |
| **DOKU** | external, sync (ACL) | Validasi rekening bank. |

### 10.2 Downstream dipicu

| Target | Bentuk | Isi |
|---|---|---|
| **05-npp-legalization** | **event** `POIssued` + **PULL** | 05 membaca memo terfinalisasi + frozen figures + snapshot untuk membangun Financing Agreement (`23 §6`). |
| **02-credit-analysis** / **01-intake** | **event** `MemoCorrectionOpened` | Re-screen RAC (idempotent) + re-lock — menggantikan DELETE lintas-DB (GOTCHA-11). |

> Semua downstream = **PULL / event**, bukan push langsung ke eksekusi. 04 **tidak** melakukan GL posting,
> aktivasi NPP, atau transfer dealer (out-of-scope §1.2).

---

## 11. Keputusan Dibutuhkan (Open Questions)

Jangan diselesaikan diam-diam — butuh domain-expert/stakeholder sign-off. OQ-ID dari KB.

| OQ-ID | Prioritas | Pertanyaan | Resolves |
|---|---|---|---|
| **OQ-CMPO-01** | P1 | "Status 0" flow-doc = header-level status parent aplikasi, atau status memo sendiri? (`0`=RFA-locked di kode; editing di `D`/`C`). | Konfirmasi 01-intake arti header status `0`; menentukan mapping status saat migrasi. |
| **OQ-CMPO-02 / OQ-CORE-03** | P1 | Arti bisnis `OP`, `ULI`, `LCR` (dan varian `Ost*`) — GL-reconciled? butuh `[LOCKED]`? | Domain-expert; menentukan treatment field frozen. |
| **OQ-CMPO-03** | P2 | Formula `OP` motor (asset−netDP) vs car (funded amount) — intentional atau copy-paste drift? | Business-rule owner; unify vs keep two formulas. |
| **OQ-CMPO-04** | P2 | Kenapa car me-recompute frozen `OP`/`ULI`/`LCR` di disposisi non-approval? | Code-owner / regression test; konfirmasi do-not-replicate. |
| **OQ-CMPO-05** | P1 | Terminasi **Level-0** (tak auto-mint) — bagaimana PO terbit untuk lini **motor** (tanpa self-heal fallback car)? | Konfirmasi apakah Level-0 motor PO-eligible; menentukan penanganan mint. |
| **OQ-CMPO-06** | P2 | Apa yang menentukan routing varian motor vs car? (kode `001`/`002` hanya inferensi behavioral). | Product-asset master; daftar kode asset-kind authoritative. |
| **OQ-CMPO-07** | P3 | Prosedur print PO plain (non-`staging`/`motor`) masih reachable dari entry-point live? | Full-codebase search WEB/MINIAPI + konfirmasi ops. |
| **OQ-CMPO-08** | P2 | Bagaimana finalisasi **pertama** mendapat nilai awal — server-side clone dari aplikasi, atau re-key layar? | Konfirmasi 01/02 mekanisme seed (P1 clone/seed trace). |
| **OQ-CMPO-09** | P3 | Kenapa rate-write hanya untuk kode top-up/repeat-order (hapus kode lain)? (sumber UTF-16 corrupt). | Business-rule owner + re-export bersih. |
| **OQ-CMPO-10** | P2 | Write-site mana yang men-set memo `V`(Verify)/`R`(Review)? (hanya display label di guard). | Repo search lebih luas / konfirmasi status vestigial. |
| **OQ-CMPO-11** | P2 | Nama & kontrak **event komite inbound** (03→04, **pre-mint**, saat memo `finalized`) untuk disposisi **correction** & **reject** yang men-transisikan memo → `corrected`/`rejected`. 03 hanya menamai `MemoApproved` (approve-only); correction/reject di 03 di-route ke 01 sebagai event/status **tanpa nama** (`03 §5.2/§10.2`). 04 = **sole writer `CREDIT_MEMO`** → butuh sinyal untuk menulis transisi. **Berbeda** dari `MemoCorrectionOpened` (outbound 04, **post-mint** §5.5). Subscribe 1 event komite generik atau per-disposisi? | Pemilik umbrella memutus kontrak event lintas-service; menentukan handler §7.1 correction/reject (terkait OQ-AC-01). |
| **OQ-CMPO-12** | P3 | Failure-mode endpoint **email PO** (§5.6) saat service email gagal: retry / degrade-gracefully (email advisory/non-blocking) atau block? Legacy `sp_send_email_print_PO` fail-closed-by-accident (no TRY/CATCH, no retry). | Stakeholder ops; menentukan perilaku retry/degrade endpoint email standalone (`email-sms-notifications.md §8`). |
| **OQ-DOKU-01** | P1 | Siapa/apa yang mengisi `DOKU_*.responseStatus/responseAccName` (write-back SP tanpa caller)? | Menentukan apakah integrasi complete/live; owner response persist di rebuild. |
| **OQ-DOKU-04** | P3 | Arti semantik `responseStatus='0'` (success? pending? enum)? | Mapping status code DOKU di rebuild. |
| **OQ-REG-06** | P1 (global) | Fail-closed vs fail-open untuk semua regulated/validation gate saat dependency throw mid-check? | Kebijakan global; menyentuh validation finalisasi + DOKU gate. |
| **OQ-MCP-01** | P1 | Apakah app/session layer enforce "hanya assigned employee/super-user boleh act"? (SQL layer legacy tidak). | Audit maker-checker (§6 BR-CMPO-19). |
| **OQ-ARCH-STACK** | — `[KEPUTUSAN DESAIN BARU]` | Target stack (bahasa/runtime/transport REST vs gRPC vs bus) belum ditentukan. | Kontrak §4-5 ditulis resource+field agnostik sampai diputuskan. |

> **Sudah RESOLVED (bukan blocker):** kepemilikan **PO minting** di 04 (digest); **freeze OP/ULI/LCR** milik 04
> (bukan 03); **RFA lock** milik 01. Ini keputusan desain umbrella yang sudah final, bukan OQ terbuka.
