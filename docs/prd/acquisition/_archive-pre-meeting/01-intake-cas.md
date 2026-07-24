# PRD — Intake & CAS (Credit Application System)

Kapabilitas ini adalah **front of funnel** origination pembiayaan kendaraan: menangkap identitas applicant (+ related-person), memilih product/asset dan menyusun struktur finansial draft, mengunggah dokumen, menjalankan screening AML/blacklist entry-time, dan berakhir pada aksi terminal **RFA (Request For Approval) lock** yang mengunci draft ke **status `0` (RFA-locked)** lalu mengemit event `ApplicationLocked`. Cakupan: **FASE 1–8** (rekonstruksi FASE 1–7 dari kode + FASE 8 RFA dari ground-truth). **Kepemilikan RFA berada di kapabilitas ini** (`sp_rfa_cm` / `sp_rfa_cm_car`, legacy); ia menyuplai sinyal risk-escalation, tetapi **tidak** memiliki komposisi `trans_type_id`, keputusan RAC/komite, PO minting, aktivasi kontrak, maupun seed/routing hierarki komite (yang di-key `trans_type_id`).

Grounding: `10-domains/20-acquisition-cas-intake.md`, `.sp-manifests/_ACQUISITION-GROUND-TRUTH.md` (FASE 8), `40-business-rules/regulatory-rules.md` (AML/blacklist), `40-business-rules/hidden-gotchas.md` (do-not-replicate), `99-rebuild-architecture/data-mutation-policy.md` (marker [LOCKED]), `99-rebuild-architecture/suggested-erd.md` (entity target shape). Konform ke SHARED CONTRACT DIGEST umbrella (`docs/prd/acquisition/00-OVERVIEW.md`).

---

## 1. Ruang Lingkup & Kepemilikan

### 1.1 Yang dimiliki (in-scope, FASE 1–8)

- **CAS application header + child records** — `CREDIT_APPLICATION` (owner) beserta financial profile, corporate profile/owners, bank account, repeat-order detail, dan jawaban kuesioner AML. Sumber: `20-acquisition-cas-intake.md §1,§6`.
- **Capture applicant + related-person** — `RELATED_PERSON` (owner) sebagai typed rows (`spouse|guarantor|reference`). Sumber: umbrella shared-entity `RELATED_PERSON`; taxonomy identitas & relasi tetap milik `10-customer-applicant-master.md` (cross-link, tidak di-re-derive).
- **Product/asset & struktur finansial draft** — capture `ASSET` (chassis_no/engine_no) + seed `CREDIT_MEMO` draft (OTR, DP, tenor, fee, rate). Sumber: `20-...§3a S2`, BR-ACQCAS-4.
- **Upload dokumen** — set dokumen/foto keyed by application + photo-type. Sumber: `20-...§3a S3`.
- **Screening AML/blacklist entry-time** — cek identitas terhadap register blacklist internal + watchlist nasional (APU-PPT/DTTOT) + red-zone geografi + high-risk-profession; hitung AML risk score Low/Medium/High; audit-log setiap hit. Sumber: `regulatory-rules.md §7 BR-REG-1..13`, `20-...§7 BR-ACQCAS-5..9`.
- **RFA lock (KEPEMILIKAN DI SINI)** — gate underwriting penuh atomik + status write `rfa_locked` (legacy `tr_cm.status_approval='0'`) + **emit `ApplicationLocked`** (membawa risk-escalation signals + `product_line` + `finance_scheme`). Seed/routing hierarki komite **BUKAN** di sini: di-key `trans_type_id` yang baru disusun 02 (FASE 9), dibangun downstream (di 02/03, setelah `trans_type_id` disusun 02). Sumber: GT FASE 8; `20-...§5.9,§7 BR-ACQCAS-10..21`.

### 1.2 Yang BUKAN miliknya (non-goal)

- **Komposisi `trans_type_id`** — disusun di SATU tempat, milik **02-credit-analysis** (dipakai 03). RFA hanya **menyuplai sinyal risk-escalation** (eff-rate scale-up BR-ACQCAS-19, aggregate-exposure OP BR-ACQCAS-20) yang dibawa sebagai **konteks** pada payload `ApplicationLocked` (di-re-qualify otoritatif di 03). `[KEPUTUSAN DESAIN BARU]` — departure dari legacy yang meng-eskalasi digit di `sp_rfa_cm`. Sumber: umbrella §7.1; `20-...§7 BR-ACQCAS-20`.
- **RAC decision / biro scoring** — milik 02 (RAC processing jalan di sisi Bank Mega). RFA hanya menyerahkan hand-off.
- **Routing komite, PO minting, freeze OP/ULI/LCR, aktivasi NPP** — milik 03/04/05.
- **5C credit-analysis narrative (CMO)** — hand-off pra-RFA ke **02**, BUKAN endpoint milik 01; RFA tidak menjadikannya hard-gate. Sumber: `20-...§2,§3a S4,§9`.
- **Menutup aplikasi saat reject** — legacy menutup `TrCas`/`TrCm` saat reject; rebuild TIDAK auto-close (OQ-AC-01). State `rejected`/`corrected` ditulis oleh aksi 03 tetapi mendarat pada entity milik 01 (lihat §7).
- **Masters customer/dealer/product** — dikonsumsi read-only via ACL; bukan ditulis di sini. Penulisan otoritatif `CUSTOMER` (`tr_CIF` upsert) tetap milik **05-npp** (lihat §3.3).

---

## 2. Aktor & Peran

| Aktor | Peran | Marker |
|---|---|---|
| **Applicant / Customer** (individual `perorangan` / corporate `badan usaha`) | Subjek identitas, financial profile, ownership structure; subjek semua screening. | `[VERIFIED][INTENT]` |
| **Spouse / Guarantor / Personal References** | Related-person, disimpan sebagai typed rows child `CREDIT_APPLICATION`. Taxonomy dimiliki `10-customer-applicant-master.md`. | `[VERIFIED][INTENT]` |
| **Branch Data-Entry / Marketing Staff** (Maker, web channel) | Key aplikasi, pilih asset/dealer/terms, upload dokumen. | `[INFERRED][INTENT]` |
| **Mobile Field Surveyor** (Maker, mobile channel, P2) | Capture survey FCL + foto di aplikasi mobile terpisah; di-sync server-side. Caller sync `[OPEN]` (OQ-ACQCAS-06). | `[VERIFIED][INTENT/OPEN]` |
| **CMO (Credit Marketing Officer)** | Entry 5C narrative (car channel) pra-RFA — hand-off ke 02, bukan aksi milik 01. | `[VERIFIED][INTENT]` |
| **Dealer / Dealer Personnel** | Sumber order (channel TAC / Third-Party) untuk atribusi insentif/refund. | `[VERIFIED][INTENT]` |
| **Branch Admin** | **Menekan RFA** — aksi terminal domain (file lock). | `[VERIFIED][LOCKED]` |
| **Compliance / AML Reviewer** | Visibilitas flag AML "High" dibatasi ke allow-list position-code. | `[INFERRED][LOCKED]` (BR-REG-13) |

---

## 3. Model Data

Tipe tech-agnostic (`identifier`, `string`, `decimal`, `integer`, `boolean`, `datetime`, `enum`) sesuai `suggested-erd.md`. Nama entity/field mengikuti umbrella verbatim; nama legacy dicatat sebagai sekunder.

### 3.1 Entitas dimiliki kapabilitas ini

**`CREDIT_APPLICATION`** — owner: 01-intake. Key: `id`.

| Field | Tipe | Catatan / Marker |
|---|---|---|
| `id` | identifier PK | — |
| `customer_id` | identifier FK | Ke `CUSTOMER` (dedup-by-NIK di intake, lihat §3.3) |
| `product_line` | enum `car\|motor` | `[LOCKED]` semantik — legacy `item_id` (`'001'`=motor, `'002'`=car), external-master-referenced. Satu engine, config-driven (bukan dua code-path). BR-ACQCAS-1; GOTCHA-10 |
| `finance_scheme` | enum `conventional_CF\|syariah_US` | Rute FASE 9 (dipakai 02). GT FASE 9 |
| `application_type_id` | string | `'02'`=Standard, `'03'`=UMC/used-vehicle. **Orthogonal** dari `finance_scheme`. Set lengkap `[OPEN]` OQ-ACQCAS-10 |
| `status` | enum | `draft\|rfa_locked\|risk_gated\|analyzing\|committee\|approved\|rejected\|corrected\|cancelled` (kanonik umbrella). `rfa_locked` = legacy `status_approval='0'` |
| `otr_price` | decimal | `[LOCKED]` source (harga OTR). data-mutation-policy §Per-locked-field; ERD |
| `down_payment` | decimal | `nett_down_payment` (net/clean) & `gross_down_payment` legacy |
| `tenor_months` | integer | — |
| `is_blacklist`, `is_apuppt` | boolean | **Display/audit only** — WAJIB di-re-derive server-side, TIDAK dipercaya dari client (fix Edge Case 7 / BR-ACQCAS-9) |
| `created_by`, `created_at` | string/datetime | Maker (audit) |

**`RELATED_PERSON`** — owner: 01-intake. Key: `id`; (`customer_id` + `role`).

| Field | Tipe | Catatan / Marker |
|---|---|---|
| `id` | identifier PK | — |
| `customer_id` | identifier FK | — |
| `role` | enum `spouse\|guarantor\|reference` | **Typed rows** menggantikan positional "magic number" (1=spouse,2=guarantor,3–5=refs). `[INTENT]` do-not-replicate GOTCHA-17 / BR-CUSTMASTER-6/7 |
| `name`, `relationship` | string | Validasi per-role `[INTENT]` (guarantor: taxonomy spouse/guarantor/co-applicant; reference: taxonomy family/acquaintance) |

**`ASSET`** — owner: 01-intake (capture); validasi final di **05-npp** (FASE 14). Key: `chassis_no` + `engine_no` (unik).

| Field | Tipe | Catatan / Marker |
|---|---|---|
| `chassis_no` (nomor rangka), `engine_no` (nomor mesin) | string | `[LOCKED]` unik — WAJIB dipertahankan; divalidasi hard-gate `sp_validation_chasis_number` di FASE 14 (milik 05) |
| `brand`, `type`, `ownership_proof` | string/enum | Kind/class/brand/type/model/year dari master `12-product-asset-master.md` (read-only) |

**Screening records (dimiliki 01):**

- **`AML_SCREENING`** (per application) — hasil cek blacklist/watchlist + red-zone + profession + AML risk score (Low/Medium/High), reason code/description. `[LOCKED]` untuk kewajiban regulasi (BR-REG-1..13).
- **`AML_HIT_LOG`** (append-only) — legacy `tr_APUPPT_blacklist_log`: matched name, ID, birth date, watchlist reference code, branch, officer. `[LOCKED]` BR-REG-10.
- **Document/photo record** — keyed by application + photo-type.

### 3.2 Shared entities yang direferensikan (dari umbrella)

| Entity | Key | Owner (umbrella) | Peran di 01 |
|---|---|---|---|
| `CUSTOMER` | `national_id` (NIK) unik; `tax_id` (NPWP) | Penulisan otoritatif **05-npp** (`tr_CIF` upsert); target **dedup-at-intake 01** | Dedup-by-NIK saat capture (§3.3). Field `national_id`/`tax_id`/`ojk_economic_sector` `[LOCKED]` |
| `CREDIT_MEMO` | `id`; `trans_type_id` | Finalize **04**; `trans_type_id` disusun **02** | **Seed draft di sini** (stub car saat CAS-insert; insert motor saat asset step). Status `draft` sampai RFA. OP/ULI/LCR `[LOCKED]` frozen-at-approval (milik 04, bukan di sini) |
| `CREDIT_ANALYSIS`, `RAC_SCREENING` | `application_id` | **02** | Dipicu setelah `ApplicationLocked` (downstream, §10) |

### 3.3 Dedup-by-NIK di intake vs penulisan otoritatif CUSTOMER `[KEPUTUSAN DESAIN BARU]`

Legacy me-recapture identitas penuh applicant di setiap aplikasi dan hanya mengisi `tr_CIF` saat NPP approval (akhir funnel), tidak pernah dipakai ulang di intake (GOTCHA-16 `[INTENT]`). Rebuild:

- **01 (intake)** = **target dedup**: saat capture, lookup/link `CUSTOMER` by `national_id` (NIK); reuse master bila sudah ada, bukan re-capture buta. Field identitas `[LOCKED]` (nilai/format/validasi WAJIB dipertahankan; nama field boleh berubah).
- **05 (NPP activation)** = **penulisan otoritatif** `tr_CIF` (KTP/NIK + NPWP) tetap dipegang 05. 01 hanya **membaca/menautkan**, tidak menjadi source-of-truth penulisan.

Sumber: `data-mutation-policy.md §Customer identity`; umbrella shared-entity `CUSTOMER`; GOTCHA-16.

---

## 4. API Endpoint

Kontrak ditulis level resource+field (framework-agnostic; REST/gRPC/message-bus belum ditentukan — OQ-ARCH-STACK). Path/verb bersifat ilustratif resource-shape, bukan penguncian framework.

| # | Method | Path | Deskripsi | Auth/Role |
|---|---|---|---|---|
| E1 | POST | `/credit-applications` | Create CAS header + identity + related-persons + financial profile; `product_line` sebagai discriminator (satu resource, bukan insert/motor vs insert/car). Dedup-by-NIK. | Branch Data-Entry/Marketing |
| E2 | PATCH | `/credit-applications/{id}` | Update draft (identity/financial profile/related-persons) selama status `draft`/`corrected`. | Branch Data-Entry/Marketing |
| E3 | GET | `/credit-applications/{id}` | Baca aplikasi + child records; flag AML ditandai "as declared at intake" bila RFA belum jalan. | Branch staff / Committee (read) |
| E4 | PUT | `/credit-applications/{id}/asset-financials` | Capture/refresh asset + struktur finansial draft (OTR, DP, tenor, dealer, fee, rate). Seed/refresh `CREDIT_MEMO` draft. Idempotent (insert-if-absent-else-update by application). | Branch Data-Entry/Marketing |
| E5 | POST | `/credit-applications/{id}/documents` | Upload dokumen/foto (KTP, KK, slip gaji, foto unit, dst) keyed photo-type. | Branch Data-Entry / CMO |
| E6 | POST | `/screening/blacklist` | Screening blacklist entry-time (broad match deterministik; register internal + employee cross-check). Server-authoritative. | Branch Data-Entry/Marketing |
| E7 | POST | `/screening/aml-questionnaire` | Screening APU-PPT/DTTOT entry-time (broad match + red-zone + profession + AML risk score) + audit-log hit. | Branch Data-Entry/Marketing |
| E8 | POST | `/credit-applications/{id}/rfa` | **RFA lock (aksi terminal, KEPEMILIKAN 01).** Jalankan gate underwriting penuh atomik; sukses → `rfa_locked`, emit `ApplicationLocked`. **Idempotency-Key WAJIB.** | **Branch Admin** |
| E9 | POST | `/credit-applications/{id}/cancel` | Batalkan draft (→ `cancelled`). `[KEPUTUSAN DESAIN BARU]` — tidak ada path live di legacy (OQ-ACQCAS-09). | Branch Admin |
| E10 | POST | `/credit-applications/{id}/reopen` | Re-open dari `corrected` untuk re-submit RFA; memicu **re-screen RAC idempotent** (event), BUKAN destructive delete (fix GOTCHA-11). | Branch Admin |

> Hand-off (BUKAN owned): entry 5C CMO narrative diteruskan ke **02-credit-analysis**; RFA tidak hard-gate padanya. Endpoint mobile-survey sync (`spSyncPoolOrderToCAS`) = P2, caller `[OPEN]` OQ-ACQCAS-06.

---

## 5. Kontrak Request/Response

Error envelope seragam di semua boundary: `{ code, message, details?, correlation_id }`. Regulated gate = **fail-closed** default.

### E1 — POST /credit-applications (create CAS)

Request (field wajib ditandai `*`):

```json
{
  "product_line": "car",                         // * enum car|motor
  "finance_scheme": "conventional_CF",           // * enum conventional_CF|syariah_US
  "application_type_id": "02",                    // * '02' standard | '03' UMC
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
    { "role": "reference", "name": "…", "relationship": "…" }
  ],
  "financial_profile": { "monthly_income": 0, "monthly_expense": 0, "other_installments": 0 }
}
```

Response `201 Created`:

```json
{
  "id": "APP-2026-0001",
  "customer_id": "CUST-000123",                  // linked/deduped by NIK
  "customer_dedup": "matched_existing",          // matched_existing|created_new
  "status": "draft",
  "created_at": "2026-07-07T02:00:00Z"
}
```

Status: `201` sukses; `422` validasi (mis. related-person role invalid, NIK format); `409` konflik dedup non-resolvable.

### E4 — PUT /credit-applications/{id}/asset-financials

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

Response `200 OK`: `{ "id":"APP-2026-0001","credit_memo_status":"draft" }`. Menjamin **satu row `CREDIT_MEMO` terisi penuh** sebelum RFA (fix asimetri car/motor Edge Case 4).

### E6 / E7 — Screening (server-authoritative, broad match)

Request E7:

```json
{ "national_id":"…", "name":"…", "birth_date":"…", "birth_place":"…",
  "address":"…", "occupation":"…", "customer_kind":"individual" }
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

Bila core screening error mid-check → **fail-closed**: `503` + `{ "code":"SCREENING_UNAVAILABLE", ... }`; aplikasi TIDAK boleh lolos sebagai "clean" (OQ-REG-06 mengonfirmasi kebijakan). Narrow exact-match variant **TIDAK di-port** (GOTCHA-1).

### E8 — POST /credit-applications/{id}/rfa (RFA lock — aksi terminal)

Header WAJIB: `Idempotency-Key: <uuid>`. Request:

```json
{ "acting_employee_id": "EMP-0007", "target_status": "rfa_locked" }
```

Response `200 OK` (semua gate lulus, atomik):

```json
{
  "id": "APP-2026-0001",
  "status": "rfa_locked",                         // legacy status_approval='0'
  "credit_memo_status": "draft",
  "risk_escalation_signals": {                    // KONTEKS; di-re-qualify OTORITATIF di 03, bukan trans_type_id final
    "effective_rate_below_min": true,
    "aggregate_exposure_op": 40000000,
    "instant_approval_cohort": false
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

---

## 6. Aturan Bisnis

| ID | Aturan | Sumber KB | Marker | Catatan (perilaku rebuild) |
|---|---|---|---|---|
| BR-01 | `product_line` (car/motor) adalah hard branch external-master-referenced, BUKAN atribut display. | `20-...§7 BR-ACQCAS-1` | `[LOCKED]` | Satu engine config-driven; divergensi gate = konfigurasi per-line (GOTCHA-10), bukan dua code-path. |
| BR-02 | Tepat SATU row `CREDIT_MEMO` terisi penuh harus ada sebelum RFA. | `20-...§7 BR-ACQCAS-3,4; §9 EC4` | `[INTENT]` | Jangan replikasi asimetri stub-car vs insert-motor; jamin invariant "CM draft lengkap sebelum dokumen/RFA". |
| BR-03 | Struktur finansial (OTR/DP/tenor/dealer/fee/rate) di-capture via step terpisah, insert-if-absent-else-update by application. | `20-...§7 BR-ACQCAS-4` | `[INTENT]` | Wizard dua-langkah asli di backend. |
| BR-04 | Screening blacklist entry-time = **broad match deterministik** (any single ID OR name+birth); narrow exact-match-all-fields TIDAK di-port. | `20-...§7 BR-ACQCAS-5,6; regulatory-rules §7 BR-REG-3,4,5; GOTCHA-1` | `[INTENT]` (fix) | Satu screening service; tutup coverage gap. Variant produksi authoritative `[OPEN]` OQ-ACQCAS-01/02. |
| BR-05 | Screening dibaca dari sumber **live**, bukan staging replica; hindari lag terhadap register terbaru. | `20-...§7 BR-ACQCAS-7` | `[INTENT]` (fix) | Legacy broad variant baca `MACF-DBSTG` replica. |
| BR-06 | Kegagalan mid-screening = **fail-closed** (block), untuk SEMUA regulated gate (AML/blacklist). | `hidden-gotchas GOTCHA-2; regulatory-rules §9 EC3, OQ-REG-06` | `[OPEN]→fail-closed` | Kebijakan default rebuild; konfirmasi OQ-REG-06/OQ-AC (§11). |
| BR-07 | `is_blacklist`/`is_apuppt` di header di-**re-derive server-side** saat RFA (live), tidak dipercaya dari payload client. | `20-...§7 BR-ACQCAS-9; §9 EC7` | `[INTENT]` (fix) | Stored flag = display/audit "as declared at intake". |
| BR-08 | AML risk score dihitung per aplikasi → tier Low(≤1.5)/Medium(1.5–2.5)/High(>2.5); bobot & threshold dari weight-reference table (tunable). | `regulatory-rules §7 BR-REG-11,12` | `[LOCKED]` metodologi / `[INTENT]` threshold | Jangan port duplicated-conditional individual/corporate (EC7 regulatory). |
| BR-09 | Visibilitas flag AML "High" dibatasi ke allow-list position-code (segregation of duties). | `regulatory-rules §7 BR-REG-13` | `[LOCKED]` | Enforce di app/session layer (OQ-MCP-01 adjacent). |
| BR-10 | Setiap AML/watchlist hit ditulis append-only ke audit log (name, ID, birth, watchlist ref code, branch, officer). | `regulatory-rules §7 BR-REG-10; 20-...§6` | `[LOCKED]` | `tr_APUPPT_blacklist_log`. |
| BR-11 | Red-zone geografi & high-risk-profession di-flag independen; individual/flagged wajib isi kuesioner EDD. | `regulatory-rules §7 BR-REG-7,8,9` | `[LOCKED]` | Branch tagging `APUPPTListMaster` yang dinonaktifkan HARUS di-restore atau di-retire formal (BR-ACQCAS-8, EC6) — jangan bawa dead branch diam-diam. |
| BR-12 | Related-person = **typed rows** dengan validasi per-role; positional "magic number" dibuang. | `20-...§2; 10-cust §7 BR-CUSTMASTER-6,7; GOTCHA-17` | `[INTENT]` (fix) | Validasi range/uniqueness per role (legacy hanya spouse yang di-force). |
| BR-13 | Dedup `CUSTOMER` by NIK saat intake; reuse master, bukan re-capture buta. Penulisan otoritatif `tr_CIF` tetap milik 05. | `data-mutation-policy §Customer; GOTCHA-16` | `[INTENT]` (NEW) | Lihat §3.3. |
| BR-14 | **RFA hanya diizinkan** bila status memo = Draft (`D`) atau Correction (`C`); status lain ditolak dengan pesan menyebut status saat ini. | `20-...§7 BR-ACQCAS-10` | `[LOCKED]` | Cegah re-submit/duplikasi file in-flight/selesai. |
| BR-15 | RFA mensyaratkan `nett_down_payment` (DP bersih setelah subsidi) **> 0**. | `20-...§7 BR-ACQCAS-11` | `[LOCKED]` | DP ≤ 0 = struktur finansial invalid. |
| BR-16 | (car) Usia kendaraan saat jatuh tempo ≤ 18 th (8 th untuk double-cabin), hanya used/UMC. Motor tak punya cek ini. | `20-...§7 BR-ACQCAS-12` | `[LOCKED]` cap / `[OPEN]` scope | Extend ke motor? OQ-ACQCAS-03. |
| BR-17 | (car) Usia borrower saat jatuh tempo ≤ 65 th. Motor tak punya cek ini. Superseded profession-tiered age (dead) dibuang. | `20-...§7 BR-ACQCAS-13; regulatory-rules BR-REG-23; GOTCHA-20` | `[INTENT]` (outcome 65 dijaga) | Extend ke motor? OQ-ACQCAS-03. |
| BR-18 | (car) DP ≥ 15% OTR (Investment) / 20% (Multi-purpose), kecuali deviasi tercatat. Motor tak punya cek ini. | `20-...§7 BR-ACQCAS-14` | `[INTENT]` | OQ-ACQCAS-03. |
| BR-19 | Repeat-Order/top-up: old contract harus ada & (top-up) active; tak lintas company; ≥50% angsuran terbayar; **(motor only)** identitas old-contract cocok applicant. | `20-...§7 BR-ACQCAS-15` | `[INTENT]` | Asimetri identity-match car vs motor → OQ-ACQCAS-04-adjacent. |
| BR-20 | Repeat-Order decision-tier dihitung dari worst-overdue + riwayat angsuran; **formula car (%tenor) ≠ motor (count)**. | `20-...§7 BR-ACQCAS-16; GOTCHA-10` | `[INTENT]` | Jaga outcome per channel; unify? OQ-ACQCAS-04. |
| BR-21 | RFA re-screen blacklist reason-code: **car 5 reason** (Pelsus/repossession/write-off/90+ overdue/prior CM reject), masing-masing bypass via override table; **motor 1 reason**. | `20-...§7 BR-ACQCAS-17; GOTCHA-10` | `[LOCKED]` semantik / `[INTENT]` scope | Extend ke motor? OQ-ACQCAS-05. |
| BR-22 | Admin fee: UMC (`'03'`) ≥ minimum tenor-banded dari fee-schedule master; standard (`'02'`) cukup non-zero. | `20-...§7 BR-ACQCAS-18` | `[INTENT]` | Schedule = data (master table), bukan logic. |
| BR-23 | Effective rate < market-min → **risk-tier digit di-scale-up** (kecuali sudah top tier), bukan reject langsung. | `20-...§7 BR-ACQCAS-19` | `[INTENT]` | Under-priced deal dirutekan ke tier scrutiny lebih tinggi. **Sinyal dibawa sebagai konteks & di-re-qualify OTORITATIF di 03** saat routing (03 O-2/BR-AC-1a/BR-AC-3); komposisi `trans_type_id` milik 02, bukan 01. |
| BR-24 | Aggregate-exposure "OP" (applicant+spouse) & Instant-Approval trial-cohort dapat memaksa risk tier tertinggi / reshape approver chain. Mekanisme+hierarki **milik 03**. | `20-...§7 BR-ACQCAS-20; GOTCHA-4,9` | `[INTENT]` cross-domain | 01 hanya menyuplai sinyal via `ApplicationLocked`. Threshold OP 35jt vs 30jt `[OPEN]` OQ-AC-02. IA = policy flag auditable (bukan string-position hack). |
| BR-25 | RFA sukses = SATU transaksi atomik (semua cek + status write); gagal → pesan error, **nol perubahan**. | `20-...§7 BR-ACQCAS-21` | `[INTENT]` | Lock all-or-nothing dari sisi caller. Seed hierarki komite BUKAN bagian transaksi RFA (di-key `trans_type_id`, dibangun downstream). |
| BR-26 | Re-lock RFA idempotent (Idempotency-Key); re-open memicu **re-screen RAC idempotent (event)**, bukan destructive delete. | umbrella §7.4; `GOTCHA-11` | `[INTENT]` (fix) | Legacy `sp_trans_open_cm:53-78` menghapus record RAC Bank Mega. |
| BR-27 | RFA sukses meng-emit `ApplicationLocked` (membawa risk-escalation signals + product_line + finance_scheme). | umbrella §3/§5; GT FASE 8 | `[INTENT]` (NEW) | Konsumen: 02 (RAC risk-gating). |
| BR-28 | Reject dari komite TIDAK auto-close aplikasi (legacy menutup `TrCas`/`TrCm`). | umbrella §3 non-goal; `20-...` | `[OPEN]` OQ-AC-01 | State `rejected` mendarat di entity 01, tanpa side-effect closure. |
| BR-29 | Blacklist override/whitelist table (dipakai reason-gate RFA) tidak punya CRUD di codebase; butuh admin screen. | `20-...§7 BR-ACQCAS-22; §9 EC10` | `[OPEN]` OQ-ACQCAS-08 | Konfirmasi cara maintain hari ini. |
| BR-30 | Dead SP `sp_insert_cas`/`sp_insert_tr_cas`/`sp_insert_cas_with_table_type`/`sp_update_cas`/`sp_delete_cas` (target `tr_credit_analyst`) **tidak di-port**. | `20-...§7 BR-ACQCAS-24; §9 EC3; GOTCHA-18` | `[ARTIFACT]` | Live path = EF-direct write + `sp_insert_cm`/`_car`. |
| BR-31 | Hindari pola sync-over-async (`.Result[0]` blocking) pada question-flag APU-PPT; gunakan `await` proper. | `20-...§9 EC6` | `[ARTIFACT]` (fix) | Cegah thread-pool starvation. |

---

## 7. State Machine

Entity yang dimiliki: `CREDIT_APPLICATION.status` = `draft | rfa_locked | risk_gated | analyzing | committee | approved | rejected | corrected | cancelled` (kanonik umbrella). `CREDIT_MEMO.status` (`draft|finalized|approved|corrected`) di-seed di sini tetap `draft`.

> **Representasi "Status 0"** `[KEPUTUSAN DESAIN BARU]`: RFA memetakan `CREDIT_APPLICATION.status → rfa_locked`, sementara `CREDIT_MEMO` yang di-seed tetap `draft`. Apakah "Status 0" flow-doc merujuk header parent atau memo sendiri = `[OPEN]` OQ-CMPO-01.

01 memiliki state sampai `rfa_locked`. State `risk_gated`/`analyzing`/`committee`/`approved`/`rejected`/`corrected` ditulis kapabilitas sibling (02/03) tetapi mendarat pada entity milik 01 — ditampilkan sebagai inbound/outbound edge.

| Dari | Aksi | Ke | Guard / Prasyarat |
|---|---|---|---|
| `(∅)` | POST create (E1) | `draft` | Identity valid; dedup-by-NIK resolved (BR-13) |
| `draft` | PATCH / asset-financials / documents | `draft` | Screening entry-time boleh dipanggil kapan pun (cross-cutting, bukan stage) |
| `draft` | POST rfa (E8) — **semua gate lulus** | `rfa_locked` | BR-14 (status Draft/Correction), BR-15 (DP>0), BR-16..23 gate underwriting, BR-06 fail-closed, BR-07 re-derive; atomik (BR-25); emit `ApplicationLocked` (BR-27) |
| `draft` | POST rfa — **gate gagal** | `draft` | `422` `RFA_GATE_FAILED`; **nol perubahan** state/data (BR-25) |
| `draft`/`corrected` | POST rfa — status guard gagal | (unchanged) | `409` bila status ≠ Draft/Correction (BR-14) |
| `draft` | POST cancel (E9) | `cancelled` | `[KEPUTUSAN DESAIN BARU]` (OQ-ACQCAS-09) |
| `rfa_locked` | POST rfa (Idempotency-Key sama) | `rfa_locked` | Idempotent, tanpa efek ganda (BR-26) |
| `rfa_locked` | Hand-off → 02 (out of scope) | `risk_gated` | RAC risk-gating (downstream; inbound edge dari sisi 01) |
| `committee` | Aksi 03: correction | `corrected` | Ditulis 03; entity milik 01 |
| `corrected` | POST reopen (E10) + re-submit RFA | `rfa_locked` | Re-screen RAC idempotent event, bukan destructive delete (BR-26) |
| `committee` | Aksi 03: reject | `rejected` | **Tidak** auto-close (BR-28, OQ-AC-01) |
| `committee` | Aksi 03: approve | `approved` | Ditulis 03 |

Non-happy-path tercakup: gate gagal (no-op), status-guard (`409`), screening fail-closed (`503`), cancel (NEW), reject-tanpa-closure, re-lock idempotent, re-open re-screen.

---

## 8. Integrasi Eksternal

Semua akses eksternal WAJIB lewat **Anti-Corruption Layer (ACL)**; hapus anti-pattern legacy (cross-DB linked-server DML, outbound HTTP dari dalam T-SQL `sp_OACreate`, baca staging replica untuk regulated gate).

| Seam | Arah | Sync/Async | Owner | Peran di 01 |
|---|---|---|---|---|
| **Blacklist / AML screening** (register internal + watchlist nasional DTTOT/APU-PPT + red-zone + high-risk-profession masters + AML score) | outbound (read) + audit-log write | sync | 01 (entry-time) | Gate entry-time (E6/E7), broad match deterministik, fail-closed, live source (BR-04/05/06). |
| **Masters CUSTOMER** (`tr_CIF`/customer master) | inbound (read) | sync | Penulisan otoritatif 05 | Dedup-by-NIK di intake (BR-13, §3.3), read-only. |
| **Masters DEALER** (`11-dealer-partner-master.md`) | inbound (read) | sync | references | Pilih dealer/subdealer/surveyor/CMO di asset step. |
| **Masters PRODUCT/ASSET** (`12-product-asset-master.md`) | inbound (read) | sync | references | Pilih kind/class/brand/type/year + application-type catalog. |
| **RAC Bank Mega (risk engine)** | outbound hand-off | async | **02** | 01 hanya **emit `ApplicationLocked`** saat RFA; RAC request/callback ingest dimiliki 02. 01 tidak memanggil RAC langsung. |
| **External masters & linked servers** (`FC_MSTAPP_MCF`) | inbound (read) | sync | references (Phase 1) | Kompatibilitas external-FK (`trans_type_id` char-for-char) — komposisi milik 02. `[OPEN]` OQ-EXTMASTERS-01. |

---

## 9. Acceptance Criteria

**AC-1 (happy path RFA lock)**
Given aplikasi car status `draft` dengan CM draft terisi penuh, DP bersih > 0, semua gate underwriting (usia kendaraan/borrower/DP%/repeat-order/blacklist re-screen/admin-fee/eff-rate) lulus, dan screening live tidak error,
When Branch Admin POST `/credit-applications/{id}/rfa` dengan `Idempotency-Key`,
Then dalam satu transaksi atomik status → `rfa_locked`, event `ApplicationLocked` (membawa `risk_escalation_signals` + `product_line` + `finance_scheme`) di-emit, response `200`.

**AC-2 (gate gagal = no-op)**
Given aplikasi dengan `down_payment = 0`,
When POST rfa,
Then response `422 RFA_GATE_FAILED` merujuk `BR-ACQCAS-11`, dan **tidak ada** perubahan state/data (draft tetap editable).

**AC-3 (status guard)**
Given aplikasi sudah `rfa_locked` (bukan Idempotency-Key retry),
When POST rfa dengan key baru,
Then response `409 RFA_INVALID_STATE` menyebut status saat ini; tanpa perubahan.

**AC-4 (idempotent re-lock)**
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

**AC-8 (typed related-person validasi)**
Given payload dengan dua row `role="spouse"`,
When E1 dijalankan,
Then `422` validasi per-role (uniqueness spouse) — bukan diterima diam-diam seperti positional legacy.

**AC-9 (dedup-by-NIK)**
Given `CUSTOMER` dengan NIK X sudah ada,
When E1 create aplikasi baru dengan NIK X,
Then aplikasi ditautkan ke `customer_id` existing (`customer_dedup="matched_existing"`), bukan re-capture duplikat.

**AC-10 (re-open re-screen non-destruktif)**
Given aplikasi `corrected`,
When POST reopen lalu re-submit RFA,
Then re-screen RAC dimodelkan sebagai event idempotent — record RAC eksternal **tidak** dihapus destruktif (fix GOTCHA-11).

**AC-11 (reject tanpa closure)**
Given komite reject (aksi 03),
When state mendarat di 01,
Then `status=rejected` tanpa auto-close `CREDIT_APPLICATION`/`CREDIT_MEMO` (BR-28; OQ-AC-01 masih terbuka).

---

## 10. Dependency

**Upstream dikonsumsi (pull/read via ACL):**
- Masters `CUSTOMER` (dedup, penulisan otoritatif milik 05), `DEALER` (`11-...`), `PRODUCT/ASSET` (`12-...`), external masters `FC_MSTAPP_MCF`.
- Channel mobile-survey (FCL) — P2, caller sync `[OPEN]` OQ-ACQCAS-06.
- Related-person taxonomy & corporate-owner model dari `10-customer-applicant-master.md` (cross-link, read).

**Downstream dipicu:**
- **Event `ApplicationLocked`** (push) → **02-credit-analysis** memicu RAC risk-gating (rute CF/US) + queue Credit Analysis. Membawa risk-escalation signals (bukan `trans_type_id` final).
- Re-open → event re-screen RAC idempotent (bukan destructive delete).

**Bukan dependency 01:** trans_type_id composition (02), seed/routing hierarki komite (`tr_hierarchy_transaction`, di-key `trans_type_id` — dibangun downstream setelah 02 menyusun `trans_type_id`; routing milik 03), PO minting (04), verification hard-gate & NPP activation (05).

---

## 11. Keputusan Dibutuhkan (Open Questions)

| OQ-ID | Pertanyaan | Menyentuh | Prioritas |
|---|---|---|---|
| **OQ-REG-06** | Saat core screening SP (tanpa error-handling) throw mid-check, app-layer fail-closed (block) atau fail-open? Kebijakan untuk SEMUA regulated gate. **Highest-impact.** Rebuild default = fail-closed (BR-06), butuh sign-off. | 01, 02; pre-phase global | P1 |
| **OQ-ACQCAS-01** | Blacklist produksi-authoritative: narrow `sp_check_blacklist` vs broad `sp_check_blacklist_test_ilyas`? Rebuild pilih broad, konfirmasi. | 01; Phase 1 blocker | P1 |
| **OQ-ACQCAS-02** | Kuesioner AML authoritative: `sp_check_APUPPT` vs `sp_check_APUPPT_Test_Ilyas` (+ audit-log side-effect)? | 01 | P1 |
| **OQ-ACQCAS-06** | Siapa/apa meng-invoke `spSyncPoolOrderToCAS` / `spNewZoomInsertSurvey_2w` / `SpSyncToFincoreR4_Reverse` (tak ada caller C#)? Channel mobile live atau dead? | 01 | P1 |
| **OQ-REG-01** | Endpoint screening mana yang benar-benar dipanggil front-end produksi (gap narrow live atau dead)? | 01 | P2 |
| **OQ-REG-02** | Intake-only AML screening = desain kontrol yang di-sign-off, atau titik screening kedua di credit-analysis yang tak pernah dibangun? | 01, 02 | P1 |
| **OQ-ACQCAS-03** | Gate car-only (usia kendaraan / usia borrower 65 / DP% by purpose) = beda kebijakan car-vs-motor intensional, atau gap motor? Extend ke motor? | 01 | P2 |
| **OQ-ACQCAS-04** | Divergensi formula Repeat-Order decision-tier (car %tenor vs motor count) + identity-match (motor only) = intensional atau drift? Unify? | 01 | P2 |
| **OQ-ACQCAS-05** | Re-screen blacklist RFA car 5-reason vs motor 1-reason = intensional atau gap motor? Extend semua reason ke motor? | 01 | P2 |
| **OQ-AC-01** | Reject apakah harus menutup aplikasi? Legacy menutup `TrCas`/`TrCm`; rebuild default TIDAK auto-close (BR-28). | 01, 03 | P2 |
| **OQ-CMPO-01** | "Status 0" flow-doc = header-level status parent aplikasi atau status memo sendiri? Rebuild pilih `CREDIT_APPLICATION.status=rfa_locked` + `CREDIT_MEMO=draft`; dokumentasikan mapping. | 01, 04 | P2 |
| **OQ-ACQCAS-07** | Aksi mobile "reverse to mobile" berefek pada `tr_CAS`/`tr_CM` lokal, atau record lokal tetap live dgn data stale? Risiko orphan/duplikat. | 01 | P2 |
| **OQ-ACQCAS-08** | Bagaimana blacklist-override/whitelist table di-maintain hari ini (tak ada CRUD)? Butuh admin screen? | 01 | P2 |
| **OQ-AC-02** | Threshold eskalasi aggregate-exposure OP: Rp 35.000.000 (kode) vs ~Rp 30.000.000 (komentar)? Sinyal disuplai 01, gate milik 03. | 03 (disuplai 01) | P2 |
| **OQ-ACQCAS-09** | Ada path live cancel/delete CAS/CM pra-RFA? Hanya delete-legacy ke tabel dead. Cancel = `[KEPUTUSAN DESAIN BARU]` (E9). | 01 | P3 |
| **OQ-ACQCAS-10** | Set lengkap `application_type_id` + arti (`'02'` Standard, `'03'` UMC confirmed; `'05'` di dead code) + arti singkatan "OP"? | 01 | P3 |
| **OQ-ACQCAS-11** | Ekspansi literal "TAC" (channel order-source dealer) tak dinyatakan di kode. | 01 | P3 |
| **OQ-EXTMASTERS-01** | Masters `FC_MSTAPP_MCF` dimiliki rebuild atau read-only; linked server MACF-DBSTG/DBMCF/DBKONSOL/dbrep masih live? | references; Phase 1 blocker | P1 |
| **OQ-ARCH-STACK** | `[KEPUTUSAN DESAIN BARU]` Target stack (bahasa/runtime/transport REST vs gRPC vs message-bus) belum ditentukan — kontrak ditulis level resource+field agnostik. | semua kapabilitas | — |

> Catatan marker-fidelity: `[LOCKED]` (BR-01,14,15,16 cap,21 semantik; field NIK/NPWP/ojk_*/chassis/engine/otr_price; AML BR-08/09/10/11) = WAJIB dipertahankan (regulasi / external-FK / kontrak eksternal). `[INTENT]` = desain target boleh redesign, jaga outcome. `[OPEN]`/OQ = jangan diselesaikan diam-diam. Do-not-replicate: GOTCHA-1 (narrow screen), -2 (fail-open), -10 (car/motor split), -11 (RAC destructive delete), -16 (no dedup), -17 (positional related-person), -18 (dead `*_cas`), EC6 (sync-over-async).
