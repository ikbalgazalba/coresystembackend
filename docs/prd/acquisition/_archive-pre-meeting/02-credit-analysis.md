# PRD — Credit Analysis & Scoring

Kapabilitas ini adalah inti pengambilan-keputusan risiko yang duduk di antara intake aplikasi (01) dan komite approval (03). Ia mengorkestrasi gate risiko otomatis **RAC Bank Mega** (dispatcher CF-konvensional vs US-syariah, request async + ingest callback idempotent), menjalankan analisis kredit (kolektibilitas SLIK/FCL, scoring internal LKK, DSR, rekomendasi analis), dan menyusun komposisi risk-category yang mengkualifikasi `trans_type_id` untuk routing komite. **Cakupan: PRD FASE 9 (credit decisioning) — mencakup RAC + Credit Analysis** sesuai umbrella SHARED CONTRACT (`00-OVERVIEW.md §3`: 02 = **FASE 9**; PDF FASE 11 = PRD FASE 10–11 milik 03-approval-committee). `_ACQUISITION-GROUND-TRUTH.md` memerinci sub-langkah RAC (PDF FASE 9) & Credit Analysis (PDF FASE 10) **DI DALAM** umbrella PRD FASE 9. Kepemilikan: seam RAC (request + callback), SLIK/OJK, Pefindo, NeoScore, dan konsumsi read Dukcapil; emit event `AnalysisComplete` + memasok decision RAC & risk-tier ke 03.

> Sumber utama: `10-domains/21-credit-analysis-scoring.md`, `50-integrations/rac-bank-mega-risk-engine.md`, `50-integrations/slik-ojk.md`, `50-integrations/pefindo.md`, `50-integrations/neoscore.md`, `50-integrations/dukcapil.md`, `10-domains/30-verification-external-checks.md`, `40-business-rules/hidden-gotchas.md`, `99-rebuild-architecture/data-mutation-policy.md`, `.sp-manifests/_ACQUISITION-GROUND-TRUTH.md`.

---

## 1. Ruang Lingkup & Kepemilikan

### Yang DIMILIKI service ini

| # | Milik | Sumber KB |
|---|---|---|
| 1 | **Dispatcher RAC** rute **CF (Conventional Finance)** vs **US (Unit Syariah)** berdasarkan financing-model code pada record kontrak, via Anti-Corruption Layer (ACL). | `21-credit-analysis-scoring.md §1,§5.1`; `rac-bank-mega-risk-engine.md §1`; ground-truth FASE 9 |
| 2 | **Adapter request RAC** (submit profil risiko ke Bank Mega) + **ingester callback/poll** yang menerima decision async dan mendorong status lokal maju; idempotent by `application_id + decision_id`. | `rac-bank-mega-risk-engine.md §3,§9,§10 EC-1`; boundary_ownership "RAC async callback ingest" |
| 3 | **Review kolektibilitas SLIK/FCL** (grid per-bank, sampai 24 bulan) + **fallback Pefindo**; reduksi days-past-due ke skala kolektibilitas OJK 1–5. | `21-...md §5.9-5.10`; `slik-ojk.md §1,§6`; `pefindo.md §1,§6` |
| 4 | **Internal scoring engine (LKK)**: grade applicant → weight → risk-category 9-parameter → bucket low/medium/high/very-high. | `21-...md §5.4-5.6, BR-CRSCORE-7,8,11` |
| 5 | **Checklist validasi dokumen granular** (~40 field), **bank-account detail/mutation**, **DSR 40%**, **freshness bureau 30-hari (advisory)**. | `21-...md §5.12-5.14, BR-CRSCORE-5,6`; `TrCaDocuments.cs` |
| 6 | **Rekomendasi analis** (Recommended / Not-Recommended), narasi positif/negatif, **debtor-group & ojk_economic_sector** (di-assign saat analisis, bukan intake). | `21-...md §5.15, BR-CRSCORE-12` |
| 7 | **Komposisi risk-category penyusun `trans_type_id`** yang mengkualifikasi risk-tier untuk routing komite (disusun di SATU tempat, dikonsumsi 03). | `21-...md BR-CRSCORE-9`; umbrella conventions `id_format` |
| 8 | **Provider parameter NeoScore** + **ingester hasil NeoScore** (bukan pemanggil outbound); **konsumsi read Dukcapil**; **microflow direct-checking SLIK/OJK** (request+approval terpisah). | `neoscore.md §1,§2`; `dukcapil.md §2,§3`; `slik-ojk.md §3, BR-CRSCORE-14` |
| 9 | Emit event **`AnalysisComplete`**; memasok decision RAC + risk-tier ke 03. | umbrella `bounded_contexts.02` |

### Yang BUKAN milik service ini

| Bukan milik | Pemilik | Sumber |
|---|---|---|
| Keputusan komite approve/reject/correction (routing hierarki, aksi approver) | 03-approval-committee | umbrella; `22-approval-committee.md` |
| **Processing/decisioning RAC** (logika accept/reject) — jalan di sisi Bank Mega (JFinMega), eksternal | Bank Mega (eksternal) | `rac-bank-mega-risk-engine.md §1,§4` |
| **PO minting** (TrPo) — di-mint 04 pada event MemoApproved; **service ini TIDAK memicu PO** (bug legacy `CreditAnalystRepositoryEF.cs:692-708` JANGAN direplika) | 04-contract-cm-po | GOTCHA-8; boundary_ownership "PO minting" |
| Finalisasi CM & freeze OP/ULI/LCR | 04-contract-cm-po | umbrella; data-mutation-policy |
| **Hard-gate verifikasi & freshness FASE 14** (403+rollback) — freshness FASE 9 di sini hanya **advisory**, JANGAN dikonflasi | 05-npp / verification | BR-VERIF-7; boundary_ownership "Verification hard-gate" |
| RFA lock (`sp_rfa_cm`) & transisi status 0 | 01-intake-cas | boundary_ownership "RFA" |
| Ladder credit-analyst (`trans_type_id='AA00000001'`) sebagai router komite — distinct, jangan dicampur | (distinct) | GOTCHA-5 |

---

## 2. Aktor & Peran

> Catatan [KEPUTUSAN DESAIN BARU]: RBAC tidak ada di kode legacy (`neoscore.md §7`: "no RBAC scheme exists in code generally"). Peran di bawah adalah desain target; enforcement identitas aktor per endpoint dilacak di OQ-MCP-01.
>
> Catatan [OPEN] OQ-CRSCORE-10 (P1): Framing actor-of-record & time-ordering peran "credit analyst" di tabel ini — apakah jalur capture preliminary-note CMO dan analyst workstation penuh adalah **langkah sekuensial satu lifecycle** (urutan CMO→Analyst) ATAU **kanal alternatif untuk lini produk berbeda** — BELUM ditentukan dan bersifat **provisional**. JANGAN diasumsikan salah satu framing tanpa konfirmasi stakeholder. Distinct dari OQ-CRSCORE-01 (write-target authority); lihat §7.2 dan §11.

| Aktor | Peran | Sumber |
|---|---|---|
| **Credit Marketing Officer (CMO) / preparer** | Menyusun preliminary 5C-note; dapat me-(re)trigger gate RAC manual (standalone). Label peran [INFERRED]. | `21-...md §2, §11 CACarController` |
| **Credit Analyst (branch/back-office)** | Aktor utama: review kolektibilitas bureau, validasi checklist dokumen, catat bank-account analysis, stamp Recommended/Not-Recommended. | `21-...md §2` |
| **System / RAC dispatcher** | Trigger otomatis RAC saat resubmission-after-correction; assemble profil risiko; route CF/US. | `21-...md §5.1-5.2` |
| **RAC callback ingester (scheduled/async)** | Menerima decision async Bank Mega, idempotent, dorong status lokal. Legacy: SQL Agent poll `sp_agent_rac_to_cm_bulk` (scheduler [OPEN] OQ-RAC-02). | `rac-...md §3(e),§8,§10 EC-1` |
| **Reminder job (scheduled)** | Mengingatkan approver pending. Legacy hardcode 2 nama — JANGAN direplika (BR-CRSCORE-19). | `21-...md §5.22, BR-CRSCORE-19` |
| **SLIK/OJK direct-check requester + approver** | Microflow request pengecekan registri langsung dengan hierarki approval dari department requester. | `21-...md BR-CRSCORE-14`; `slik-ojk.md §3` |
| **Bank Mega Risk Engine (JFinMega)** — eksternal | Menjalankan decisioning RAC; mengembalikan accept/reject async. | `rac-...md §1` |
| **NeoScore** — eksternal | Consumer-scoring service; call outbound dilakukan tier lain (OQ-NEOSCORE-01). | `neoscore.md §1` |
| **SLIK / Pefindo / Dukcapil** — eksternal | Supply bureau collectibility & civil-registry match (read-only feed ter-staging). | `slik-ojk.md`, `pefindo.md`, `dukcapil.md` |

---

## 3. Model Data

### Entitas yang DIMILIKI service ini

**`CREDIT_ANALYSIS`** (umbrella shared entity; owner 02) — key: `id`, `application_id`

| Field | Tipe | Marker | Catatan |
|---|---|---|---|
| `id` | id | — | PK |
| `application_id` | FK → CREDIT_APPLICATION | — | milik 01 (di-konsumsi) |
| `collectibility` | int 1..5 | **[LOCKED]** | Skala OJK **WAJIB dipertahankan**: `0→1, 1-90→2, 91-120→3, 121-180→4, >180→5` (regulasi OJK nasional). Sumber: `21-...md BR-CRSCORE-3`, data-mutation-policy |
| `dsr` | decimal | [INTENT] | Debt-Service Ratio; cap target 40% (BR-CRSCORE-5) |
| `recommendation` | enum `recommended\|not_recommended` | [INTENT] | keputusan analis |
| `risk_tier` | enum `low\|medium\|high\|very_high` (+ fast-track codes) | [INTENT] | output scoring engine; feed komposisi `trans_type_id` (BR-CRSCORE-9) |
| `positive_negative_narrative` | text | [INTENT] | narasi aspek positif/negatif |
| `debtor_group` | code | [INTENT] | di-assign saat analisis (BR-CRSCORE-12) |
| `ojk_economic_sector` | OJK code | **[LOCKED]** | nilai WAJIB match OJK code list (reporting OJK); data-mutation-policy per-locked-field |
| `status` | enum (lihat §7) | [INTENT] | status record analisis |

**`RAC_SCREENING`** (umbrella shared entity; owner 02, ingest async) — key: `id`, `application_id`

| Field | Tipe | Marker | Catatan |
|---|---|---|---|
| `id` | id | — | PK |
| `application_id` | FK | — | |
| `financing_model_code` | enum (allow-list) | **[LOCKED]** | Branching key ke record set eksternal berbeda (CF: `rac_processing_header/detail`; syariah: `rac_processing_status_bms/detail_bms`) — kontrak external-system, **WAJIB dipertahankan** (`rac-...md §1`). Nilai diterima = **allow-list dapat dikonfigurasi** (seed `CF` + kode syariah `US`/`SY`); anggota pasti kode syariah (US vs SY, dipakai bergantian) menunggu **[OPEN] OQ-CRSCORE-07** — validasi §5.1 memakai allow-list, BUKAN hard-reject dua-nilai. Arti kode syariah institusional [OPEN] OQ-CRSCORE-07 |
| `decision_id` | string | **[LOCKED]** (kontrak) | ReffId/decision eksternal; kunci idempotency ingest bersama `application_id` |
| `rac_status` | enum `pending\|approved\|rejected` | [INTENT] mekanisme, **[LOCKED]** kontrak nilai | `pending` = first-class state (RAC EC-7); nilai `APPROVED`/`REJECTED` diamati dari feed eksternal (vocabulary penuh [OPEN] OQ-RAC-05) |
| `reject_detail` | json | [INTENT] | dari `rac_get_reject_detail_json` (`$.fullResponse.dataReject[].message`) |
| `decided_at` | timestamp | — | `DtmUpd` sisi eksternal |
| `submitted_at` / `submitted_by` | timestamp / actor | — | audit request |

> Idempotency ingest: satu `(application_id, decision_id)` di-apply **tepat sekali**; write-back ke RAC **DILARANG** (`no write-back ke RAC`, umbrella).

**Entitas pendukung yang dimiliki (INTENT — schema bebas didesain ulang, outcome dijaga):**

| Entitas | Isi | Sumber |
|---|---|---|
| `BUREAU_COLLECTIBILITY_SNAPSHOT` | grid per-bank ≤24 bulan (year-month, tier 1-5, days-overdue); source SLIK tier-1/mirror → Pefindo fallback | `21-...md §5.9-5.10`; `slik-ojk.md §6`; `pefindo.md §6` |
| `DOCUMENT_CHECKLIST` (~40 field) | statement + result per item (identity, incorporation, tax, ownership, survey, social-media, Dukcapil ket/hasil pairs) | `21-...md §5.12`; `TrCaDocuments.cs`; `dukcapil.md §3` |
| `BANK_ACCOUNT_DETAIL` + `MUTATION_ENTRY` | rekening + entri mutasi bulanan (di-key manual analis) | `21-...md §5.9` |
| `INTERNAL_SCORING_RESULT` | applicant grade → weight → risk-category (9 param); blacklist override | `21-...md BR-CRSCORE-7,8` |
| `NEOSCORE_LOG` | `credit_id, parameter, result(raw), total_score, status, log_date` | `neoscore.md §6` |
| `AML_PEP_SCORE` | skor PEP/profile/product/geo/channel → bucket low/med/high (reader [OPEN]) | `21-...md BR-CRSCORE-16` |
| `SLIK_DIRECT_CHECK_REQUEST` | microflow request registri langsung (status submitted/forwarded/approved/corrected/rejected) | `21-...md BR-CRSCORE-14`; `slik-ojk.md §5` |

### Shared entities yang DIRUJUK (bukan milik 02)

- **`CREDIT_APPLICATION`** (owner 01) — `status` enum: `draft\|rfa_locked\|risk_gated\|analyzing\|committee\|approved\|rejected\|corrected\|cancelled`. `otr_price` **[LOCKED]** source. Service ini menggerakkan transisi `rfa_locked→risk_gated→analyzing→committee`.
- **`CREDIT_MEMO`** (owner 04; finalize) — `trans_type_id` **[LOCKED]** external-FK **disusun dari 02**. Komposisi = (application-type code)+(menu-entry prefix)+(sequence), risk-tier-qualified. **WAJIB dipertahankan char-for-char** (dicocokkan ke tabel referensi approval-hierarchy di `FC_MSTAPP_MCF`). OP/ULI/LCR frozen milik 04.
- **`CUSTOMER`** (owner 05 tulis; 01 dedup) — `national_id` (NIK) & `tax_id` (NPWP) **[LOCKED]** (identitas OJK/AML); dikonsumsi sebagai key bureau/Dukcapil.

---

## 4. API Endpoint

> [KEPUTUSAN DESAIN BARU] Kontrak ditulis level resource+field, framework-agnostic (transport REST/gRPC/message-bus belum ditentukan, OQ-ARCH-STACK). `Auth/Role` bersifat desain target (OQ-MCP-01).

| Method | Path | Deskripsi | Auth/Role |
|---|---|---|---|
| POST | `/applications/{id}/rac-screening` | Submit/trigger gate RAC (route CF/US via ACL). Return **envelope call**, bukan decision. | System, CMO |
| GET | `/applications/{id}/rac-screening` | Baca status/decision RAC terkini (incl. `pending`). | Analyst, System, 03 (read) |
| GET | `/applications/{id}/rac-screening/history` | History-check RAC (helper first-submission branch). | Analyst, System |
| POST | `/rac-screening/callbacks` | **Ingest callback/poll decision async** dari Bank Mega ACL; idempotent by `application_id+decision_id`. | RAC ingester (service-to-service) |
| POST | `/applications/{id}/credit-analysis` | Buka/insiasi record analisis (application → `analyzing`). | Analyst |
| GET | `/applications/{id}/credit-analysis` | Baca record analisis. | Analyst, 03 (read) |
| PATCH | `/credit-analysis/{id}` | Update review bureau / checklist dokumen / bank-account (Draft). | Analyst |
| GET | `/applications/{id}/bureau/collectibility` | Grid kolektibilitas SLIK/FCL ≤24 bulan (fallback Pefindo transparan). | Analyst |
| GET | `/applications/{id}/bureau/pefindo` | Lookup asset/akad Pefindo (standalone). | Analyst |
| GET | `/applications/{id}/dukcapil-result` | Read-only civil-registry match (tanpa gate). | Analyst |
| POST | `/credit-analysis/{id}/scoring` | Hitung internal grade + risk-category (engine LKK). | Analyst, System |
| GET | `/applications/{id}/risk-category` | Baca risk-tier + komposisi penyusun `trans_type_id`. | Analyst, 03 (read) |
| GET | `/applications/{id}/neoscore-param` | Assemble parameter NeoScore (channel default/car). | External caller tier |
| POST | `/neoscore-results` | Log hasil NeoScore (write-back dari tier pemanggil). | External caller tier |
| GET | `/neoscore-results/{credit_id}` | Idempotency/prior-result check. | External caller tier |
| POST | `/credit-analysis/{id}/recommendation` | Submit rekomendasi (RFA): jalankan DSR + freshness advisory; emit `AnalysisComplete`; application → `committee`. | Analyst |
| GET | `/credit-analysis/{id}/recommendation/advisories` | Baca pesan advisory (DSR>40%, bureau >30 hari). | Analyst |
| POST | `/slik-requests` | Buat request direct-checking registri (microflow terpisah). | Requester |
| POST | `/slik-requests/{id}/approval` | Aksi approval microflow SLIK (approve/forward/correct/reject). | SLIK approver |
| GET | `/slik-requests` | List/paginate request direct-checking. | Requester, approver |

---

## 5. Kontrak Request/Response

### 5.1 `POST /applications/{id}/rac-screening` — Submit gate RAC

Request (wajib: `financing_model_code`, `created_by`):
```json
{
  "financing_model_code": "CF",
  "created_by": "EMP-00123"
}
```
Response `202 Accepted` — **hanya envelope call, BUKAN decision** (decision datang async; RAC EC-4):
```json
{
  "application_id": "APP-2026-0001",
  "rac_status": "pending",
  "submitted_at": "2026-07-07T03:12:00Z",
  "call_envelope": { "error_number": 0, "error_message": null },
  "correlation_id": "corr-8f1a"
}
```
- `400` **HANYA** bila `financing_model_code` tidak ada dalam **allow-list financing-model code yang dapat dikonfigurasi** (seed: `CF` konvensional + kode syariah terkonfirmasi `US`/`SY`). Nilai yang berada di allow-list **dipertahankan char-for-char** (`[LOCKED]`; JANGAN dinormalisasi atau dipersempit diam-diam). Himpunan pasti kode syariah (US vs SY, dipakai bergantian) **[OPEN] OQ-CRSCORE-07** — sampai diputus, seed mencakup CF+US+SY agar nilai legacy sah tidak ditolak; **JANGAN** hard-reject dua-nilai. `502` bila ACL Bank Mega tak reachable (fail-closed, tidak menandai approved). Error envelope seragam `{code,message,details?,correlation_id}`.

### 5.2 `POST /rac-screening/callbacks` — Ingest decision async (idempotent)

Request (wajib: `application_id`, `decision_id`, `rac_status`):
```json
{
  "application_id": "APP-2026-0001",
  "decision_id": "REFF-77219",
  "financing_model_code": "CF",
  "rac_status": "REJECTED",
  "reject_detail": [{ "message": "DSR melebihi ambang RAC" }],
  "decided_at": "2026-07-07T03:20:00Z"
}
```
Response `200 OK`:
```json
{ "application_id": "APP-2026-0001", "decision_id": "REFF-77219",
  "applied": true, "resulting_status": "rejected", "correlation_id": "corr-8f2b" }
```
- **Idempotent**: `(application_id, decision_id)` yang identik → `applied:false, "reason":"already_processed"`, `200` (tidak menggandakan efek). Sumber: umbrella boundary_ownership; `rac-...md §9`.
- **Late-approval override**: bila decision baru (`decision_id` berbeda) `APPROVED` tiba setelah status lokal sudah final berlawanan (rejected/corrected), override HANYA boleh dijalankan bila **eksplisit, ter-log, auditable** — bukan silent flip (perbaikan RAC EC-2; kebijakan window [OPEN] OQ-RAC-03).
- **No write-back ke RAC**; ACL tidak melakukan DML mentah ke DB Bank Mega (perbaikan RAC EC-5).

### 5.3 `POST /credit-analysis/{id}/recommendation` — Submit rekomendasi (RFA)

Request (wajib: `recommendation`, `positive_negative_narrative`, `debtor_group`, `ojk_economic_sector`):
```json
{
  "recommendation": "recommended",
  "positive_negative_narrative": "Kapasitas cukup; agunan memadai.",
  "debtor_group": "GRP-01",
  "ojk_economic_sector": "OJK-45201"
}
```
Response `200 OK` (advisory dikembalikan inline, non-blocking di FASE 9):
```json
{
  "credit_analysis_id": "CA-0001",
  "status": "recommended",
  "advisories": [
    { "code": "DSR_EXCEEDED", "message": "DSR 47% > 40%", "severity": "advisory" },
    { "code": "BUREAU_STALE", "message": "Cek bureau > 30 hari; ulang sebelum NPP", "severity": "advisory" }
  ],
  "event_emitted": "AnalysisComplete",
  "correlation_id": "corr-9c01"
}
```
- `ojk_economic_sector` **[LOCKED]** — nilai WAJIB match OJK code list.
- Advisory DSR/freshness di FASE 9 **advisory** (BR-CRSCORE-5,6); apakah menjadi hard-block adalah **[OPEN] OQ-CRSCORE-02** (default target regulated gate = fail-closed, [KEPUTUSAN DESAIN BARU], tapi belum diputus untuk kedua check ini — lihat §11). Freshness hard-gate ada di FASE 14 (05), **jangan dikonflasi**.

### 5.4 `GET /applications/{id}/risk-category` — Risk-tier & komposisi trans_type_id

Response `200 OK`:
```json
{
  "application_id": "APP-2026-0001",
  "risk_tier": "high",
  "blacklist_override_applied": false,
  "trans_type_id_composition": {
    "application_type_code": "01",
    "menu_entry_prefix": "CM",
    "risk_tier_qualifier": "H",
    "note": "trans_type_id final [LOCKED] external-FK, char-for-char"
  }
}
```
- `blacklist_override_applied:true` → `risk_tier="very_high"` **[LOCKED]** (BR-CRSCORE-8).
- Komposisi disusun HANYA di sini, dikonsumsi 03 (hindari drift). Ladder `AA00000001` distinct.

### 5.5 `POST /neoscore-results` — Log hasil NeoScore

Request (wajib: `credit_id`, `result`):
```json
{ "credit_id": "APP-2026-0001", "parameter": "default", "result": "{...structured...}", "user_id": "EMP-00123" }
```
Response `201 Created`:
```json
{ "credit_id": "APP-2026-0001", "total_score": 720, "status": "logged", "correlation_id": "corr-a1" }
```
- Parsing hasil **WAJIB** memakai kontrak terstruktur (JSON/schema-validated), BUKAN tag-strip + label-offset (perbaikan NeoScore §6/EC-5).
- Repeat submission `credit_id` sama **WAJIB** menyimpan hasil segar; JANGAN clobber dengan stale placeholder (perbaikan NeoScore EC-3).

### 5.6 `GET /applications/{id}/bureau/collectibility` — Grid kolektibilitas

Response `200 OK` (tier 1-5 per bulan; fallback SLIK→Pefindo transparan):
```json
{
  "application_id": "APP-2026-0001",
  "source_tier": "slik_primary",
  "banks": [
    { "bank_name": "Bank X", "months": [
      { "year_month": "2026-06", "collectibility": 1, "days_overdue": 0 },
      { "year_month": "2026-05", "collectibility": 2, "days_overdue": 12 }
    ]}
  ]
}
```
- `collectibility` **[LOCKED]** skala OJK; **satu fungsi tunggal** dipakai lintas source (perbaikan SLIK EC-6 / Pefindo EC-3, dua komputasi inkonsisten).

---

## 6. Aturan Bisnis

| ID | Aturan | Sumber KB | Marker | Catatan |
|---|---|---|---|---|
| BR-02-01 | Gate RAC bercabang **CF (konvensional)** vs **US (syariah)** berdasarkan financing-model code; tiap rute baca/clear record set eksternal terpisah. | `21-...md BR-CRSCORE-1`; `rac-...md §1` | **[LOCKED]** | Kontrak external-system; **WAJIB dipertahankan**. Arti kode US [OPEN] OQ-CRSCORE-07 |
| BR-02-02 | RAC di-model **async submit + poll/callback**; submit hanya balikkan envelope call, decision (accept/reject) tiba terpisah. State **`pending`** adalah first-class. | `rac-...md §6, §10 EC-4,EC-7` | [INTENT] outcome | JANGAN model sync call-and-get-decision |
| BR-02-03 | Ingest decision RAC **idempotent** by `application_id+decision_id`; efek di-apply tepat sekali; **no write-back ke RAC**; tanpa DML mentah ke DB Bank Mega. | `rac-...md §9, EC-5`; umbrella boundary_ownership; data-mutation-policy anti-patterns | [INTENT] (mekanisme) / **[LOCKED]** (no cross-DB DML) | Perbaikan: owned API contract (submit + cancel/resubmit idempotent) |
| BR-02-04 | Late-approval override (decision `APPROVED` terlambat membalik status final berlawanan) HARUS eksplisit, ter-log, auditable — bukan silent flip. Window legacy 120 menit. | `rac-...md §9, §10 EC-2` | [INTENT] / do-not-replicate | Kebijakan window [OPEN] OQ-RAC-03 |
| BR-02-05 | CM reopen-for-correction memicu **re-screen RAC**; model sebagai event idempotent eksplisit, BUKAN destructive delete record RAC eksternal. | GOTCHA-11; `rac-...md §3(c)` | [VERIFIED][INTENT] / do-not-replicate | Legacy `sp_trans_open_cm:53-78` hanya cover item_id='001' (EC-6) |
| BR-02-06 | Skala kolektibilitas OJK: `0→1, 1-90→2, 91-120→3, 121-180→4, >180→5`; **satu fungsi tunggal** dipakai konsisten lintas SLIK/Pefindo. | `21-...md BR-CRSCORE-3`; `slik-ojk.md §6, EC-6`; `pefindo.md §6, EC-3`; data-mutation-policy | **[LOCKED]** | Regulasi OJK nasional; **WAJIB dipertahankan** |
| BR-02-07 | Internal scoring: risk-category disusun dari 9 parameter berbobot (grade, indikator historis branch/dealer, tenor, DP-ratio, blacklist, DSR, principal, jarak residensi) via master threshold configurable. | `21-...md BR-CRSCORE-7` | [INTENT] | Bobot/threshold configurable |
| BR-02-08 | **Blacklist flag** memaksa bucket **very-high** terlepas parameter lain. | `21-...md BR-CRSCORE-8` | **[LOCKED]** | Satu-satunya kontrol AML-adjacent di scoring engine |
| BR-02-09 | Mapping grade→weight **WAJIB monotonic**; **weight strictly TURUN dengan risiko** (weight = skor favorability/kualitas — grade terbaik ⇒ weight tertinggi, grade terburuk ⇒ weight terendah; BUKAN penalti yang naik). Perbaiki bug legacy di mana grade kedua-terburuk = weight grade terbaik (grade kedua-terburuk TIDAK boleh = weight grade terbaik). Tambah unit test asersi **`weight strictly decreases with risk`**. | GOTCHA-3; `21-...md §9 EC-1, BR-CRSCORE-11` | [VERIFIED][ARTIFACT] / do-not-replicate | `sp_get_customer_scoring:437-444`. **Catatan arah**: hanya GOTCHA-3 memfiksasi tanda (turun); EC-1 & BR-CRSCORE-11 hanya "monotonic" tanpa arah, dan draf awal PRD ini sempat menyatakan arah sebaliknya (weight naik) — tim WAJIB verifikasi tanda terhadap SP `sp_get_customer_scoring:437-444` saat implementasi. |
| BR-02-10 | Guard pembagian nol pada parameter persentase (DP-ratio, principal-ratio) yang membagi dengan cost aset. | `21-...md §9 EC-10` | [VERIFIED] / do-not-replicate | Tanpa guard → runtime arithmetic error |
| BR-02-11 | Output risk-category dibaca 02 untuk menyusun `trans_type_id` (risk-tier-qualified) yang meng-drive routing komite; disusun **di satu tempat**, dikonsumsi 03. | `21-...md BR-CRSCORE-9`; umbrella conventions | **[LOCKED]** (external-FK) | Char-for-char match `FC_MSTAPP_MCF`; ladder `AA00000001` distinct (GOTCHA-5) |
| BR-02-12 | Fast-track category untuk satu vehicle-category: DP-nol → murni score NeoScore ≥ threshold + repeat-order flag; DP-lain → threshold score + min-DP% keduanya terpenuhi. | `21-...md BR-CRSCORE-10` | [INTENT] | |
| BR-02-13 | **DSR cap 40%** (existing+proposed installment / total income) dihitung saat submit rekomendasi; di FASE 9 dikembalikan sebagai pesan advisory. | `21-...md BR-CRSCORE-5, §9 EC-4` | [VERIFIED][OPEN] | Hard-block vs advisory [OPEN] OQ-CRSCORE-02 / OQ-REG-06 |
| BR-02-14 | **Freshness bureau 30-hari** di-flag saat submit rekomendasi sebagai advisory ("ulang sebelum NPP"). **ADVISORY di FASE 9** — distinct dari hard-gate 403+rollback FASE 14 (05); **jangan dikonflasi**. | `21-...md BR-CRSCORE-6`; BR-VERIF-7; boundary_ownership | [VERIFIED][OPEN] | Enforcement [OPEN] OQ-CRSCORE-02 / OQ-SLIK-05 |
| BR-02-15 | Regulated gate (AML/SLIK/DSR/verification) default **fail-closed**: kegagalan mid-check memblokir, bukan meloloskan. | GOTCHA-2; umbrella conventions `status_enum_note` | **[KEPUTUSAN DESAIN BARU]** | Global policy [OPEN] OQ-REG-06; belum diputus untuk DSR/freshness FASE 9 |
| BR-02-16 | First-submission vs resubmission RAC = jalur berbeda: resubmission clear record prior; first-submission konsultasi history-check helper. Pertahankan keduanya distinct. | `21-...md §9 EC-9`; `rac-...md §3(a),(d)` | [VERIFIED] | Vocabulary `message` helper [OPEN] OQ-RAC-06 |
| BR-02-17 | debtor-group & ojk_economic_sector di-assign **saat analisis**, bukan intake. | `21-...md BR-CRSCORE-12` | [VERIFIED][INTENT] | `ojk_economic_sector` [LOCKED] value |
| BR-02-18 | Microflow direct-checking SLIK/OJK punya lifecycle+vocabulary sendiri (submitted/forwarded/approved/corrected/rejected) dengan routing dari department requester, terpisah dari status record analisis. | `21-...md BR-CRSCORE-14`; `slik-ojk.md §3` | [VERIFIED][INTENT] | |
| BR-02-19 | Batch bridge OJK-checking mengelompokkan request per NIK, restart grup bila >30 hari sejak request sebelumnya; stage request terbaru per grup; job companion rekonsiliasi hasil. Konsolidasi logika grouping (jangan duplikat). | `21-...md BR-CRSCORE-15`; `slik-ojk.md §9, EC-3` | [VERIFIED][INTENT] | |
| BR-02-20 | **NeoScore**: 02 hanya provider parameter + ingester hasil (BUKAN pemanggil outbound). Parsing hasil WAJIB terstruktur (bukan tag-strip/offset); repeat submission simpan hasil segar (jangan clobber stale). | `neoscore.md §1,§6, EC-3,EC-5` | [VERIFIED][LOCKED] (parse=de-facto contract) / do-not-replicate | Call site [OPEN] OQ-NEOSCORE-01/OQ-CRSCORE-09 |
| BR-02-21 | Reminder job pending-approver WAJIB derive recipient dari approver-of-record live; JANGAN hardcode nama/email. | `21-...md BR-CRSCORE-19, §9 EC-3` | [VERIFIED][ARTIFACT] / do-not-replicate | |
| BR-02-22 | AML/PEP-adjacent score (PEP/profile/product/geo/channel → low/med/high) dihitung & dipersist per aplikasi. | `21-...md BR-CRSCORE-16` | [VERIFIED][OPEN] | Reader tak ditemukan → OQ-CRSCORE-04 (jangan tandai dead tanpa konfirmasi) |
| BR-02-23 | Aturan "jumlah reference person scaling dengan worst kolektibilitas" (2 utk tier 1-3, 3 utk tier 4-5) hanya komentar SQL, tak ter-enforce. JANGAN diam-diam di-encode sebagai enforced. | `21-...md BR-CRSCORE-4, §9 EC-5` | [VERIFIED][OPEN] | Enforcement [OPEN] OQ-CRSCORE-05 |
| BR-02-24 | Dukcapil match dikonsumsi **read-only, tanpa coded gate** di kapabilitas ini (informational untuk judgment analis). | `dukcapil.md §2`; BR-VERIF-8 | [VERIFIED][INTENT] | Gating (jika ada) prosedural — OQ-VERIF-01 |
| BR-02-25 | Service ini **TIDAK mint PO**; PO di-mint 04 pada MemoApproved. JANGAN replika trigger PO dari modul credit-analyst legacy. | GOTCHA-8; boundary_ownership "PO minting" | [VERIFIED][INTENT] / do-not-replicate | `CreditAnalystRepositoryEF.cs:692-708` |
| BR-02-26 | Konsolidasi ke **satu** model approval-progress kanonik (legacy punya 2 set bookkeeping paralel; write-target 5C-note ada 3 jalur). Jangan replika duplikasi. | `21-...md §9 EC-6,EC-7, BR-CRSCORE-13` | [VERIFIED][OPEN] | Source-of-truth [OPEN] OQ-CRSCORE-03, OQ-CRSCORE-01, OQ-OVERVIEW-01 |
| BR-02-27 | Instant-Approval (IA) trial-cohort override (paksa low-risk trans-type via string-position editing identifier) diperlakukan sebagai policy flag eksplisit auditable — JANGAN replika string-hack. | GOTCHA-9; `21-...md §11 (sp_get_trans_type_id_cm trial-cohort)` | [OPEN] | Permanen vs pilot [OPEN] OQ-PRODASSET-06 |

---

## 7. State Machine

Kapabilitas ini menggerakkan **dua** state machine + transisi `CREDIT_APPLICATION.status` (milik 01, digerakkan 02).

### 7.1 `RAC_SCREENING.rac_status`

Status: `not_submitted` → `pending` → `approved` | `rejected` (+ override transition).

| Dari | Aksi | Ke | Guard/Prasyarat |
|---|---|---|---|
| not_submitted | submit RAC (route CF/US) | pending | financing_model_code valid; ACL reachable (else fail-closed, tetap not_submitted) |
| pending | ingest decision `APPROVED` | approved | idempotent by `(application_id, decision_id)` |
| pending | ingest decision `REJECTED` | rejected | idempotent |
| pending | ingest duplicate `decision_id` | pending (no-op) | `applied:false` (already_processed) |
| rejected/corrected(final) | late `APPROVED` (decision_id baru, dalam window) | approved (override) | **HARUS eksplisit + ter-log + auditable** (BR-02-04); non-happy-path |
| approved/rejected | CM reopen-for-correction | pending (re-screen) | event idempotent, bukan destructive delete (BR-02-05) |

### 7.2 `CREDIT_ANALYSIS.status`

Status: `queued` → `under_review` → `recommended` → (handoff ke 03). Return-path menyentuh record ini; walk multi-level milik 03.

> **[OPEN] OQ-CRSCORE-10 (P1) — ordering & actor-of-record provisional**: Pemodelan sebagai **satu lifecycle linear** (`queued→under_review→recommended`) dengan **Credit Analyst sebagai aktor tunggal** (S2) BELUM dikonfirmasi. Alternatifnya, jalur capture preliminary-note CMO dan analyst workstation penuh bisa merupakan **kanal alternatif untuk lini produk berbeda**, bukan langkah sekuensial satu lifecycle. Kedua dimensi (actor-of-record + time-ordering) menunggu OQ-CRSCORE-10 (§11); JANGAN diasumsikan salah satu framing tanpa konfirmasi stakeholder. Distinct dari OQ-CRSCORE-01 (write-target authority).

| Dari | Aksi | Ke | Guard/Prasyarat |
|---|---|---|---|
| — | RAC approved (S1) | queued | `rac_status='approved'`; application → `risk_gated` |
| queued | analis buka record (S2) | under_review | actor = Credit Analyst; application → `analyzing` |
| under_review | submit rekomendasi (S3) | recommended | recommendation + narrative + debtor_group + ojk_economic_sector terisi; emit `AnalysisComplete`; application → `committee` |
| recommended | **03**: non-base reviewer kembalikan (correction) | under_review | non-happy-path; return-to-analyst (mekanik walk milik 03) |
| recommended | **03**: base-tier reviewer kembalikan | queued | non-happy-path; **re-trigger RAC** pada resubmission; application → `corrected` lalu `risk_gated` |
| recommended | **03**: terminal approve | (handoff) | milik 03; 02 memasok decision+risk-tier; **02 TIDAK mint PO** (BR-02-25) |
| recommended | **03**: terminal reject | (handoff) | milik 03; application → `rejected` |

> Non-happy-path terkait: RAC `rejected` (S1) → aplikasi berhenti sebelum credit analysis (blocking mechanism [OPEN] OQ-CRSCORE-06). RAC `pending` berkepanjangan → tetap first-class (bukan diasumsikan approved).

### 7.3 `SLIK_DIRECT_CHECK_REQUEST.status` (microflow terpisah)

`submitted` → `forwarded` → `approved`; `submitted` → `corrected` → `submitted`; `submitted` → `rejected`.

| Dari | Aksi | Ke | Guard |
|---|---|---|---|
| submitted | reviewer bukan terminal | forwarded | routing dari department requester |
| forwarded | terminal approver approve | approved | |
| submitted | terminal approver approve | approved | acting = terminal |
| submitted | reviewer kirim balik | corrected | |
| corrected | preparer resubmit | submitted | |
| submitted | reviewer reject | rejected | terminal |

---

## 8. Integrasi Eksternal

> Semua seam **WAJIB lewat Anti-Corruption Layer (ACL)** — tanpa cross-DB DML mentah ke DB counterpart (perbaikan atas linked-server pattern legacy; data-mutation-policy anti-patterns). Klasifikasi arah/mode konform umbrella `integration_seams`.

| Seam | Arah | Mode | Owner | Catatan ACL |
|---|---|---|---|---|
| **RAC Bank Mega (JFinMega)** | outbound request + inbound callback | **async** | 02 | Submit balikkan envelope call saja; decision async via callback/poll ingester; idempotent by `application_id+decision_id`; **no write-back**; owned API contract (submit + cancel/resubmit), bukan `DELETE` ke DB eksternal. `rac-...md §2,§4` |
| **SLIK / OJK** | outbound (pull) | **sync** | 02 | Read grid kolektibilitas via ACL (bukan OPENQUERY string-concat, perbaikan injection SLIK EC-1). Microflow direct-check + RPA-staging batch idempotent. Tier CLKNAE* [OPEN] OQ-SLIK-01 |
| **Pefindo** | outbound (pull) | **sync** | 02 | Fallback bureau saat SLIK kosong; `varchar(16)` NIK [LOCKED]; satu fungsi kolektibilitas tunggal. Request-initiation path [OPEN] OQ-PEFINDO-01 |
| **NeoScore** | outbound | **async** | 02 | **02 provider param + ingester hasil, BUKAN pemanggil**; call site tier lain (OQ-NEOSCORE-01). Parsing terstruktur; NIK canonical konsisten (perbaikan EC-1); NumberOfDependents populate konsisten (EC-2) |
| **Dukcapil** | outbound | **async** | 02 / verification | Read-only civil-registry match; **02 tidak gate** atas Dukcapil (informational). Field set [LOCKED] (KYC/AML). `dukcapil.md §2` |

---

## 9. Acceptance Criteria

**AC-1 (happy: RAC approve → CA queue)**
- **Given** aplikasi `rfa_locked` dengan `financing_model_code='CF'`,
- **When** `POST /applications/{id}/rac-screening` lalu `POST /rac-screening/callbacks` dengan `rac_status='APPROVED'`,
- **Then** `RAC_SCREENING` → `approved`, `CREDIT_APPLICATION.status` → `risk_gated`, `CREDIT_ANALYSIS` → `queued`.

**AC-2 (RAC async — bukan sync)**
- **Given** submit RAC,
- **When** response diterima,
- **Then** response adalah envelope call (`rac_status='pending'`), **bukan** decision accept/reject; decision hanya datang via callback.

**AC-3 (idempotent ingest)**
- **Given** callback `(application_id, decision_id)` sudah diproses,
- **When** callback identik dikirim ulang,
- **Then** `applied:false, reason:already_processed`, status tidak berubah, tanpa efek ganda.

**AC-4 (late-approval override auditable)**
- **Given** status lokal sudah `rejected`,
- **When** decision `APPROVED` terlambat (decision_id baru, dalam window),
- **Then** override HANYA berlaku bila tercatat sebagai entri audit eksplisit (bukan silent flip); jika kebijakan window belum diputus (OQ-RAC-03), sistem menahan & menandai untuk review.

**AC-5 (dispatch CF vs US)**
- **Given** `financing_model_code='US'`,
- **When** submit RAC,
- **Then** rute US (record set `_bms`) dipakai; `financing_model_code` dipertahankan char-for-char.

**AC-6 (kolektibilitas OJK exact)**
- **Given** days-overdue `95`,
- **When** grid kolektibilitas dihitung (SLIK atau Pefindo),
- **Then** tier = `3` (91-120) — mapping identik lintas source (satu fungsi tunggal).

**AC-7 (blacklist override)**
- **Given** applicant ber-blacklist flag,
- **When** scoring dihitung,
- **Then** `risk_tier='very_high'` terlepas parameter lain.

**AC-8 (grade→weight monotonic — bug fixed)**
- **Given** grade band memburuk (risiko naik),
- **When** weight di-resolve,
- **Then** weight strictly **menurun** dengan risiko — asersi unit test `weight strictly decreases with risk` (per GOTCHA-3; weight = skor favorability/kualitas, grade terbaik ⇒ weight tertinggi); grade kedua-terburuk TIDAK sama dengan grade terbaik. Catatan arah: hanya GOTCHA-3 memfiksasi tanda (EC-1/BR-CRSCORE-11 agnostik) — verifikasi terhadap `sp_get_customer_scoring:437-444` saat implementasi.

**AC-9 (guard pembagian nol)**
- **Given** cost aset = 0,
- **When** DP-ratio/principal-ratio dihitung,
- **Then** ditangani sebagai validasi (bukan runtime arithmetic error).

**AC-10 (DSR advisory di FASE 9)**
- **Given** DSR 47% (>40%),
- **When** `POST /credit-analysis/{id}/recommendation`,
- **Then** advisory `DSR_EXCEEDED` dikembalikan inline; submission tetap lanjut (advisory) SELAMA OQ-CRSCORE-02 belum memutuskan hard-block; **tidak** meng-hard-block sebagai gate final (jangan konflasi dengan gate NPP FASE 14).

**AC-11 (freshness advisory ≠ NPP hard gate)**
- **Given** cek bureau > 30 hari,
- **When** submit rekomendasi FASE 9,
- **Then** advisory `BUREAU_STALE` (non-blocking di FASE 9); hard-gate 403+rollback tetap di FASE 14 milik 05.

**AC-12 (emit AnalysisComplete + no PO minting)**
- **Given** rekomendasi tersubmit,
- **When** record → `recommended`,
- **Then** event `AnalysisComplete` di-emit, application → `committee`, risk-tier & decision RAC dipasok ke 03, dan **tidak ada PO yang di-mint** oleh kapabilitas ini.

**AC-13 (trans_type_id komposisi tunggal)**
- **Given** risk_tier terhitung,
- **When** `GET /applications/{id}/risk-category`,
- **Then** komposisi penyusun `trans_type_id` disediakan (risk-tier-qualified), disusun hanya di 02, `trans_type_id` final char-for-char [LOCKED]; ladder `AA00000001` tidak dicampur.

**AC-14 (NeoScore ingest — bukan caller)**
- **Given** hasil NeoScore dari tier pemanggil,
- **When** `POST /neoscore-results` untuk `credit_id` yang sudah punya log,
- **Then** hasil segar disimpan (bukan clobber stale), parsing terstruktur; kapabilitas ini tidak melakukan outbound call NeoScore.

**AC-15 (fail-closed regulated gate — target)**
- **Given** ACL RAC/SLIK gagal mid-check,
- **When** gate regulasi dievaluasi,
- **Then** default fail-closed (blokir), aplikasi tidak lolos diam-diam ([KEPUTUSAN DESAIN BARU]; final policy OQ-REG-06).

---

## 10. Dependency

### Upstream yang dikonsumsi
- **01-intake-cas** (event `ApplicationLocked`; status `rfa_locked`): header CAS, applicant, product/asset, financing-model code, blacklist flag, branch/dealer. RFA lock milik 01; re-open memicu re-screen RAC. — **pull/event**
- **Master reference** `FC_MSTAPP_MCF` (trans-type/approval-hierarchy reference) untuk validasi char-for-char `trans_type_id` — **read (linked-server legacy → owned read via ACL)**; reachability [OPEN] OQ-EXTMASTERS-01.
- **CUSTOMER** (NIK/NPWP) untuk key bureau & Dukcapil — konsumsi read.

### Eksternal (seam §8)
- RAC Bank Mega (async), SLIK (sync), Pefindo (sync), NeoScore (async, call di tier lain), Dukcapil (async, read-only).

### Downstream yang dipicu
- **03-approval-committee**: menerima event **`AnalysisComplete`** + decision RAC + risk-tier + komposisi `trans_type_id`; 03 MEMBACA decision RAC untuk routing, **tidak menulis balik**. — **event + pull(read)**
- **04-contract-cm-po**: mengonsumsi komposisi `trans_type_id` saat finalize CM; PO minting milik 04 (bukan dipicu 02). — **downstream (bukan event dari 02)**
- **05-npp / verification**: freshness bureau advisory FASE 9 hanya informasi; hard-gate FASE 14 milik 05 (distinct).

---

## 11. Keputusan Dibutuhkan (Open Questions)

| OQ-ID | Pertanyaan | Dampak |
|---|---|---|
| **OQ-REG-06** | Saat core screening throw mid-check, app-layer fail-closed (blokir) atau fail-open (lolos)? Kebijakan global untuk SEMUA regulated gate. **Highest-impact.** | 01, 02; semua regulated gate (AML/SLIK/DSR/verification) |
| **OQ-CRSCORE-02** | DSR 40% & freshness 30-hari (FASE 9) di-enforce hard-block oleh calling-layer atau advisory? Belum boleh diputus diam-diam. | 02; fail-open/closed |
| **OQ-CRSCORE-06** | Apakah RAC `REJECTED` benar-benar memblokir progres ke credit analysis, di mana & bagaimana? (Propagasi ditemukan; logic decision tetap eksternal.) | 02; ACL RAC |
| **OQ-CRSCORE-07** | Arti institusional kode financing-model non-konvensional (US/SY; dua nilai dipakai bergantian). | 02 |
| **OQ-CRSCORE-01 / OQ-OVERVIEW-01** | Dari 3 write-target 5C-note paralel, mana authoritative? CA vs CREDITSANALYST source-of-truth. | 02 |
| **OQ-CRSCORE-03** | Relasi dua set bookkeeping approval-progress paralel (mana live/legacy/scoped). | 02; konsolidasi model |
| **OQ-CRSCORE-04** | Apakah AML/PEP score benar-benar dibaca dari tier manapun (web/mobile/job)? | 02 |
| **OQ-CRSCORE-05** | Aturan "reference count scaling dengan kolektibilitas" di-enforce di luar slice ini? | 02 |
| **OQ-CRSCORE-08** | Vocabulary valid field "result" checklist dokumen (kini free-text). | 02 |
| **OQ-CRSCORE-10** | Actor-of-record & time-ordering peran "credit analyst": jalur preliminary-note CMO vs analyst workstation penuh — **langkah sekuensial satu lifecycle** ATAU **kanal alternatif untuk lini produk berbeda**? JANGAN diasumsikan salah satu framing tanpa konfirmasi stakeholder. **[P1]**, distinct dari OQ-CRSCORE-01 (write-target authority). | 02; framing aktor §2 + ordering/actor state machine §7.2 (keduanya provisional) |
| **OQ-RAC-01** | Di mana `sp_insert_rac_processing*` (incl. `_syariah`) & `sp_Cek_rac_history` benar-benar eksekusi (Bank Mega / intermediary)? | 02; ACL RAC |
| **OQ-RAC-02** | SQL Agent job/scheduler apa yang memanggil poll `sp_agent_rac_to_cm_bulk` & frekuensinya? | 02; ketahanan async |
| **OQ-RAC-03** | Late-approval override 120-menit = policy sengaja atau race-mitigation? | 02; idempotency |
| **OQ-RAC-05** | Set literal penuh `rac_get_status`/`STATUS_DESC` (adakah nilai pending distinct)? | 02 |
| **OQ-RAC-06** | Vocabulary penuh output `message` `sp_Cek_rac_history` (hanya `'0'` diperiksa). | 02 |
| **OQ-RAC-07** | Field request-level apa yang dibutuhkan risk engine di luar `credit_id`/`CreatedBy`? | 02; kontrak payload |
| **OQ-NEOSCORE-01 / OQ-CRSCORE-09** | Tier mana yang benar-benar melakukan outbound call NeoScore & transport-nya? | 02; ACL NeoScore |
| **OQ-SLIK-05** | Freshness SLIK 30-hari di-enforce hard-block sebelum NPP atau informational? (satu tema dengan OQ-CRSCORE-02) | 02, 05 |
| **OQ-SLIK-01** | Apa itu family tabel `CLKNAE*` (Tier-3 fallback) & relasinya ke SLIK/Pefindo? | 02; ACL SLIK |
| **OQ-SLIK-07 / OQ-PEFINDO-01** | Orkestrasi fan-out multi-bureau (`IsDukcapil`/`IsPefindo`) & mekanisme initiate request Pefindo. | 02; ACL bureau |
| **OQ-PRODASSET-06** | IA trial-cohort override (force low-risk trans-type via string-position editing) = policy permanen atau pilot hack stale? | 02, 03; IA policy flag |
| **OQ-CORE-03 / OQ-CMPO-02** | Arti bisnis OP/ULI/LCR (dan Ost*) — GL-reconciled & butuh [LOCKED]? (menyentuh komposisi risk/routing) | 02, 03, 04 |
| **OQ-MCP-01** | Apakah API/session layer meng-enforce "hanya assigned employee/super-user boleh act" untuk endpoint credit-analyst approve (SQL layer legacy tidak)? | 02, 03; NFR audit |
| **OQ-EXTMASTERS-01** | Apakah master `FC_MSTAPP_MCF` dimiliki rebuild atau read-only; linked server MACF-* masih live/reachable? | references; validasi `trans_type_id` |
| **OQ-ARCH-STACK** | [KEPUTUSAN DESAIN BARU] Target stack (bahasa/runtime/transport REST/gRPC/message-bus) belum ditentukan. | semua kapabilitas; konvensi kontrak API §4 |
