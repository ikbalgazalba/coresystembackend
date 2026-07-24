# PRD — NPP Legalization & Downstream

Kapabilitas **05-npp-legalization** adalah **gerbang legalisasi final** pipeline origination pembiayaan kendaraan (FASE 14) sekaligus **titik pemicu distribusi downstream post-acquisition** (FASE 15). Service ini memiliki *aktivasi* kontrak pembiayaan (NPP = *Nota Persetujuan Pembiayaan*): meng-*enforce* gate keras (verification `verified` + freshness 30-hari, validasi chassis/engine, kelengkapan BAST) **in-transaction**, meng-aktivasi `FINANCING_AGREEMENT` (mencetak *Perjanjian Pembiayaan* legal), meng-upsert customer master (`tr_CIF`), lalu men-serahkan konsekuensi ke downstream. Konsumen downstream FASE 15 (disbursement/GL subledger, Dealer Payment, BPKB custody) bersifat **PULL/event — BUKAN push**; service ini meng-emit `AgreementActivated` dan menaruh outbox-row Passnet, tetapi **tidak** meng-eksekusi disbursement/GL, transfer dealer, atau custody BPKB.

- **FASE dicakup**: FASE 14 (NPP / final legality) + FASE 15 (post-acquisition downstream distribution).
- **Kepemilikan otoritatif**: aktivasi `FINANCING_AGREEMENT` (`agreement_no`), penulisan `CUSTOMER`/`tr_CIF`, outbox Passnet, event `AgreementActivated`, enforcement gate NPP.
- **Sumber KB utama**: `10-domains/24-npp-legalization-downstream.md`; `.sp-manifests/_ACQUISITION-GROUND-TRUTH.md:41-46`; `40-business-rules/hidden-gotchas.md §D`; `99-rebuild-architecture/data-mutation-policy.md`; `50-integrations/passnet-mf-payment-sync.md`; `50-integrations/doku-payment-gateway.md`; `10-domains/32-disbursement-subledger.md`; `10-domains/31-collateral-bpkb-fidusia.md`.

---

## 1. Ruang Lingkup & Kepemilikan

### 1.1 Dimiliki service ini (owned)

| Kepemilikan | Deskripsi | Sumber KB |
|---|---|---|
| **Aktivasi NPP / FINANCING_AGREEMENT** | Transisi kontrak ke `active`, penetapan `agreement_no` `[LOCKED]`, stamp approver/approve-date, agreement value/total obligation. | `24-npp §6`; overview §6.1 (pemilik `FINANCING_AGREEMENT` = 05) |
| **Enforcement gate keras NPP (in-transaction)** | Verification hard-gate (`verified` + freshness 30-hari), validasi chassis+engine (`sp_validation_chasis_number`), kelengkapan BAST, due-date ≥ approve-date. Blokir + **rollback** bila gagal. | overview §5 (boundary "Verification hard-gate"); GT `:41-43`; `24-npp §7 BR-NPP-1/21` |
| **Maker→Checker NPP** | Rantai hierarki 2-langkah (submitter + 1 supervisor), audit tiap transisi. | `24-npp §3a/§7 BR-NPP-8` |
| **Penulisan otoritatif `CUSTOMER` (`tr_CIF`)** | Upsert customer master by `national_id` (individu) / `tax_id` (korporat) saat aktivasi. | overview §5 ("Penulisan `tr_CIF`"); `24-npp §6 BR-NPP-16` |
| **Outbox Passnet** | Menaruh 1 baris `PASSNET_SYNC` (`is_sync='0'`) + `passnet_id` `[LOCKED]` per aktivasi (drain eksternal). | `passnet-mf-payment-sync.md §2a/§5`; `24-npp §7 BR-NPP-14/15` |
| **Emisi event `AgreementActivated`** | Sinyal ke downstream (pull/subscribe), bukan push imperatif. | overview §4 (`F15 -.->\|"pull"\|`); hidden-gotchas GOTCHA-12 |
| **Cetak dokumen legal (on-demand)** | Dataset Perjanjian Pembiayaan + surat pernyataan / surat kuasa / MoU via endpoint reports. | `24-npp §6 BR-NPP-4`; `NPPReportsController.cs` |

### 1.2 BUKAN milik service ini (non-goal)

| BUKAN dimiliki | Pemilik | Catatan |
|---|---|---|
| Eksekusi **disbursement / GL subledger booking**, amortisasi, AR-card | disbursement-subledger (post-acquisition, PULL) | Legacy menjalankan cascade GL **inline** di `sp_approve_npp`; rebuild men-*decompose* menjadi konsekuensi downstream yang dipicu event. `32-disbursement §1`; overview §6.1 (`DISBURSEMENT` = post-acq PULL). |
| **Transfer dana ke dealer (Dealer Payment)** | PAYMENT DB eksternal (`PAYMENTContext`, cashier-operated) | Tidak ada reader men-seed run Dealer Payment dari status NPP (OQ-NPP-03). `32-disbursement BR-DISB-16`. |
| **Custody BPKB** (in/out/loan/handover) | collateral-bpkb-fidusia (post-acquisition, PULL) | Candidate-queue di-PULL memakai `verification_status`, bukan di-push NPP. `31-collateral §7 BR-COLL-11`. |
| **Produksi status verifikasi** | kapabilitas verification (produsen) | 05 hanya **meng-enforce** gate-nya; tidak menulis `VERIFICATION`. overview §5. |
| **PO minting** | 04-contract-cm-po | 05 hanya **membaca** `PURCHASE_ORDER`. |
| **Eksekusi/registrasi Passnet & Fidusia** | ETL/consumer eksternal + collateral | 05 hanya menaruh outbox-row; drain/write-back di luar slice (OQ-PASSNET-01). Fidusia = record-keeping internal, BUKAN API pemerintah live. |

### 1.3 Reengineering mandate (bukan mirror legacy)

Semua *do-not-replicate* gotcha yang menyentuh kapabilitas ini **diperbaiki**, bukan ditiru — detail per-item di §6 (kolom Marker/Catatan) & §9. Ringkas: aktivasi **atomik** (rollback penuh bila gagal, ganti commit-on-error), **idempotent** di batas mutasi, gate **fail-closed in-transaction**, downstream **event/outbox** menggantikan fire-and-forget, satu engine config-driven untuk car & motor.

---

## 2. Aktor & Peran

| Aktor | Peran | Sumber |
|---|---|---|
| **Branch Admin / CMO** (maker) | Input data NPP (BAST no/date, chassis/engine, bank reference, dsb), submit RFA. | `24-npp §2`; `TrNPPController.cs:92-131` |
| **Approver / Supervisor** (checker) | Review validasi chassis/engine + kelengkapan BAST, rekam Approve / Reject / Correction. | `24-npp §2`; `TrNPPController.cs:133-137` |
| **Super User (per `trans_type_id`)** | Employee yang, bila merekam keputusan hierarki, di-stamp sebagai otoritas approving-nya sendiri. Perlakuan sebagai **policy flag auditable** (bukan bypass diam-diam) — lihat OQ-NPP-07. | `24-npp §2 BR-NPP-9`; overview §8.1 |
| **System / Activation engine** | Eksekutor cascade internal atomik + emisi `AgreementActivated`. | `24-npp §5 step 9` |
| **kapabilitas verification** (upstream) | **Produsen** `verification_status` + freshness — dikonsumsi 05 sebagai gate (read-only). | overview §5 |
| **Downstream consumers (PULL)** | Cashier (Dealer Payment), BPKB/Vault Custodian, GL/Disbursement engine — poll record eligible. | `24-npp §2/§5 step 12`; GOTCHA-12 |
| **External Bank Mega "Passnet"** | Sistem legacy core (system-of-record NPP number) yang menerima registrasi tiap kontrak aktif via outbox. | `passnet §1` |

> **Enforcement identitas approver (no self-approval)** wajib di **application layer** — SQL layer legacy tidak meng-enforce untuk NPP (OQ-MCP-01, overview §8.1).

---

## 3. Model Data

Bentuk **target** rebuild (tech-agnostic), konform ke Shared ERD umbrella (§6). Field `[LOCKED]` = **additive only** (tanpa ubah nama/tipe/nilai).

### 3.1 Entitas yang dimiliki service ini

**`FINANCING_AGREEMENT`** — kontrak legal pembiayaan (legacy `tr_NPP`). Tier `[LOCKED]` (kontrak legal; additive only — `data-mutation-policy` §per-locked-field).

| Field | Tipe | Marker | Catatan |
|---|---|---|---|
| `id` | identifier (PK) | — | |
| `po_id` | identifier (FK → PURCHASE_ORDER) | — | sumber branch/dealer/tenor/produk (read dari 04). |
| `agreement_no` | string | **[LOCKED]** | Nomor kontrak legal; diassign **saat aktivasi**. WAJIB dipertahankan (kontrak legal + interop Passnet). |
| `status` | enum `pending\|validated\|active\|held` | [LOCKED] (enum kanonik) | Lihat §7 untuk mapping legacy `0/A/C/R/V`. |
| `bast_validated` | boolean | **[KEPUTUSAN DESAIN BARU]** | Hasil hard-gate BAST (§6 BR-NPP-N2). |
| `agreement_date` / `agreement_value` / `total_obligation` | date / decimal | [LOCKED] additive | Field kontrak legal. |
| `approver` / `approved_at` | identifier / datetime | [INTENT] | Stamp checker. |
| `installment_date` | date | [INTENT] | Snapping end-of-month (BR-NPP-11). |
| `insurance_billing_periodical` / `insurance_coverage_period` | enum/int | [INTENT] | Reset saat aktivasi (BR-NPP-12, OQ-NPP-10). |
| `bank_reference_id` | identifier | [INTENT] | Rekening dealer terpilih. |
| `activated_at` | datetime | [INTENT] | |

**`NPP_APPROVAL_STEP` + `NPP_APPROVAL_HISTORY`** — rantai maker→checker khusus NPP (legacy hierarki 2-baris). Tier `[INTENT]` (outcome maker-checker + audit dipertahankan; storage bebas).

| Field | Tipe | Marker | Catatan |
|---|---|---|---|
| `id` (PK), `agreement_id` (FK) | identifier | — | |
| `level` | integer | [INTENT] | Legacy hardcode 2 langkah (BR-NPP-8; OQ-NPP-06). |
| `actor` / `actor_role` | identifier / string | [INTENT] | |
| `action` | enum `pending\|approved\|rejected\|correction` | [LOCKED] enum | Sejajar `APPROVAL_STEP.action` kanonik. |
| `reason_id` / `reason_desc` | identifier / text | [INTENT] | Wajib bila `rejected`/`correction`. |
| `is_super_user_self_approve` | boolean | [INTENT] | Policy flag auditable (BR-NPP-9, OQ-NPP-07). |
| `acted_at` | datetime | [INTENT] | Audit wajib. |

**`PASSNET_SYNC`** — outbox row hand-off ke Passnet (legacy `tr_synchronize_to_passnet`). Tier `[LOCKED]` (format `passnet_id`) + `[KEPUTUSAN DESAIN BARU]` (mekanisme outbox).

| Field | Tipe | Marker | Catatan |
|---|---|---|---|
| `id` (PK), `credit_id`, `company_id`, `branch_id` | identifier | — | |
| `passnet_id` | string(10) | **[LOCKED]** | Format `'5'` + 9-digit zero-padded (`'5'+right('000000000'+seq,9)`) → `5000000001`. WAJIB verbatim (interop sistem eksternal). `sp_get_passnet_id.sql:14`. |
| `is_sync` | enum `pending\|synced\|failed` | [KEPUTUSAN DESAIN BARU] | Legacy `'0'`/`'1'` tanpa writer sync → ganti outbox + reconciliation (BR-NPP-N7). |
| `sync_date` / `return_message` | datetime / text | [INTENT] | Diisi oleh reconciliation (ack/write-back). |

**`CUSTOMER` (shared, penulisan otoritatif 05)** — customer master (`tr_CIF`). Set identitas `[LOCKED]` = `national_id` / `tax_id` / `ojk_economic_sector` (umbrella §6 ERD).

| Field | Tipe | Marker | Catatan |
|---|---|---|---|
| `id` (PK) | identifier | — | |
| `national_id` (NIK/KTP) | string | **[LOCKED]** | Kunci dedup individu; nilai/format/validasi WAJIB (OJK/Dukcapil + AML). |
| `tax_id` (NPWP) | string | **[LOCKED]** | Kunci korporat; nilai/format WAJIB (pelaporan pajak/OJK). |
| `ojk_economic_sector` | string (OJK code) | **[LOCKED]** additive | Sektor ekonomi OJK; bagian set identitas CUSTOMER `[LOCKED]` (umbrella §6 ERD "LOCKED code"). Nilai WAJIB cocok persis OJK code list (`data-mutation-policy` §per-locked-field, `customer.ojk_*` — economic sector + entity/gender korporat). Field `ojk_*` code-set lain tunduk constraint sama. |
| `full_name` | string | [INTENT] | Di-update saat mismatch (BR-NPP-16). |
| `customer_kind` | enum `individual\|corporate` | [INTENT] | |
| `owning_company_id` | identifier | [INTENT] | Legacy: identitas sama di company berbeda → record kedua (BUKAN merge) (BR-NPP-16). |

### 3.2 Entitas dikonsumsi (referensi ke Shared Entities umbrella — TIDAK ditulis di sini)

| Entitas | Pemilik | Interaksi 05 | Marker |
|---|---|---|---|
| `PURCHASE_ORDER` (`po_number`) | 04-contract | **Read** — trigger masuk NPP. | [INTENT] |
| `CREDIT_MEMO` (`trans_type_id`, OP/ULI/LCR) | 04-contract | **Read** — branch/dealer/tenor/rate/asset. | `trans_type_id` [LOCKED] |
| `ASSET` (`chassis_no`, `engine_no`) | 01-intake (capture) | **Validasi final** hard-gate FASE 14. | chassis/engine **[LOCKED]** unik |
| `VERIFICATION` (`status`, `freshness_at`) | verification | **Read** — gate `verified` + freshness 30-hari. | [INTENT]; freshness hard-gate |
| `DISBURSEMENT` / `SUBLEDGER_ENTRY` | post-acq (PULL) | **Downstream** — dipicu event, tidak ditulis 05. | `[LOCKED]` GL crosswalk |
| `BPKB` (`custody_status`) | post-acq (PULL) | **Downstream** — di-PULL. | `custody_status` [LOCKED] |

---

## 4. API Endpoint

> Kontrak level **resource + field**, framework-agnostic (OQ-ARCH-STACK belum diputuskan — overview §7). Auth/role di-enforce **application layer** (no self-approval — OQ-MCP-01).

| Method | Path | Deskripsi | Auth/Role |
|---|---|---|---|
| `POST` | `/npp` | Buat draft NPP dari `PURCHASE_ORDER` yang sudah issued (asset-type = data, bukan endpoint terpisah). | Branch Admin / CMO |
| `GET` | `/npp` | List/pagination NPP (filter status/branch). | Admin, Approver |
| `GET` | `/npp/{id}` | Detail satu NPP. | Admin, Approver |
| `PUT` | `/npp/{id}` | Update data draft NPP (guard: status ∈ {`pending`, `held(correction)`}). | Branch Admin / CMO |
| `POST` | `/npp/{id}/submit` | Submit RFA (maker): jalankan validasi chassis/engine + pre-flight gate; bangun rantai checker; `pending → validated`. | Branch Admin / CMO |
| `GET` | `/npp/{id}/preflight` | Pre-check advisory sebelum approve (verification, chassis, BAST, due-date ≥ approve-date). Read-only. | Approver / Supervisor |
| `POST` | `/npp/{id}/decision` | Rekam keputusan checker: `approve` / `reject` / `correction`. `approve` → aktivasi **atomik in-transaction** (gate re-enforce, cascade internal, emit event). **Idempotency-Key wajib.** | Approver / Supervisor |
| `GET` | `/npp/{id}/agreement` | Dataset cetak Perjanjian Pembiayaan (on-demand). | Admin, Approver |
| `GET` | `/npp/{id}/documents/{docType}` | Dokumen legal pendamping (`approval-letter`, `statement-letter`, `power-of-attorney`, `mou`). | Admin, Approver |
| `GET` | `/npp/{id}/history` | Audit trail maker-checker (`NPP_APPROVAL_HISTORY`). | Admin, Approver, Audit |

**Header lintas-endpoint**: `Idempotency-Key` wajib pada `POST /npp/{id}/decision` (aktivasi memindahkan state legal/eksternal — overview §7.4). Error envelope seragam `{ code, message, details?, correlation_id }` (§7.3).

> **Editability `validated` vs RFA legacy:** Guard `PUT /npp/{id}` di atas **tidak** mengizinkan edit saat `validated`, sedangkan legacy BR-NPP-5 mengizinkan edit saat status RFA (legacy `0`). Apakah RFA-editability dipertahankan atau sengaja di-drop di `validated` adalah **keputusan stakeholder** — jangan diputus diam-diam; kaitkan ke **OQ-CMPO-01** ("arti status `0`"). Bila dipertahankan, §7 perlu transisi `validated → PUT → validated`.

---

## 5. Kontrak Request/Response

### 5.1 `POST /npp` — buat draft (maker, stage S1)

Request (wajib: `po_id`, `bast_no`, `bast_date`, `chassis_no`, `engine_no`, `bank_reference_id`, `installment_date`; opsional lainnya):
```json
{
  "po_id": "PO-2026-000123",
  "bast_no": "BAST/2026/07/0456",
  "bast_date": "2026-07-05",
  "chassis_no": "MH1JBK21XLK123456",
  "engine_no": "JBK2E1234567",
  "item_color": "Hitam",
  "bill_received_date": "2026-07-05",
  "bill_receipt_date": "2026-07-05",
  "down_payment_receipt_no": "DP-000789",
  "bpkb_letter_no": null,
  "bank_reference_id": "DLR-BANK-0007",
  "installment_date": "2026-08-05",
  "insurance_coverage_period": 12,
  "dealer_order_source_tac": [],
  "dealer_order_source_third_party": []
}
```
Response `201 Created`:
```json
{ "id": "NPP-2026-000123", "status": "pending", "agreement_no": null, "bast_validated": false }
```
Error: `409` bila PO belum `issued` / sudah punya NPP aktif; `422` field wajib kosong (`{code:"VALIDATION_ERROR", details:[...]}`).

### 5.2 `POST /npp/{id}/submit` — submit RFA (maker → checker)

Menjalankan validasi chassis/engine (`sp_validation_chasis_number` — BR-NPP-1) + pre-flight gate. Request body kosong / minimal (`{ "acting_employee": "EMP-014" }`).

Response `200 OK` (lolos → `validated`):
```json
{ "id": "NPP-2026-000123", "status": "validated",
  "checker": { "level": 2, "assignee_role": "supervisor", "action": "pending" } }
```
Response `422 Unprocessable` (chassis/engine mismatch — gate FASE 14):
```json
{ "code": "CHASSIS_ENGINE_MISMATCH",
  "message": "Nomor rangka/mesin tidak cocok dengan Credit Memo",
  "details": { "chassis_no": "mismatch", "expected_item_year": "L", "found": "K" },
  "correlation_id": "..." }
```

### 5.3 `POST /npp/{id}/decision` — keputusan checker (aktivasi in-transaction)

Request (wajib: `action`; `reason_id` wajib bila `reject`/`correction`; header `Idempotency-Key`):
```json
{ "action": "approve", "acting_employee": "EMP-020", "reason_id": null, "reason_desc": null }
```

**Happy-path** `200 OK` (aktivasi sukses, atomik):
```json
{
  "id": "NPP-2026-000123",
  "status": "active",
  "agreement_no": "PP/MCF/2026/07/000123",
  "passnet_id": "5000000123",
  "customer": { "id": "CIF-0099", "national_id_matched": true },
  "activated_at": "2026-07-07T04:12:00Z",
  "event_emitted": "AgreementActivated"
}
```

**Gate gagal — verification** `403 Forbidden` (block + **rollback**, tidak ada side-effect):
```json
{ "code": "VERIFICATION_GATE_FAILED",
  "message": "Verifikasi customer belum 'verified' atau kadaluarsa (>30 hari)",
  "details": { "verification_status": "recheck", "freshness_days": 41 },
  "correlation_id": "..." }
```

**Gate gagal — BAST** `422` (`{code:"BAST_INCOMPLETE"}`, `bast_no`/`bast_date` null — BR-NPP-N2).

**Gate gagal — due-date** `422` (`{code:"DUE_DATE_BEFORE_APPROVAL"}` — BR-NPP-7, kini di-enforce di mutasi).

**Reject / Correction** `200 OK` (hanya update status header, **tanpa** cascade — BR-NPP-17):
```json
{ "id": "NPP-2026-000123", "status": "held", "disposition": "correction", "reason_id": "RSN-07" }
```

**Idempotent replay** (Idempotency-Key sama, sudah `active`) → `200 OK` mengembalikan hasil aktivasi pertama, **tanpa** menjalankan ulang cascade (BR-NPP-N4, perbaiki Edge Case 6).

---

## 6. Aturan Bisnis

> Kolom **Marker**: `[LOCKED]` = requirement keras (WAJIB dipertahankan). `[INTENT]` = desain target (jaga outcome). `[KEPUTUSAN DESAIN BARU]` = desain rebuild baru (bukan dari legacy). `[ARTIFACT]` = akun legacy, dibuang setelah konfirmasi stakeholder.

### 6.1 Aturan legacy dipertahankan (regulasi / kontrak eksternal / outcome)

| ID | Aturan | Sumber KB | Marker | Catatan |
|---|---|---|---|---|
| BR-NPP-1 | Validasi nomor rangka (`chassis_no`) + mesin (`engine_no`) WAJIB sebelum NPP dapat diaktivasi (gate legality FASE 14). | `24-npp §7 BR-NPP-1`; `sp_validation_chasis_number.sql` | **[LOCKED]** (identitas aset legal) | chassis/engine unik `[LOCKED]` (data-mutation-policy). |
| BR-NPP-2 | Motor (item `001`): cek chassis membandingkan **year-code** 1-karakter vs item-year Credit Memo — bukan uniqueness. | `24-npp §7 BR-NPP-2` | [INTENT] | Coverage gap legacy → rebuild perluas ke uniqueness (BR-NPP-N5). |
| BR-NPP-3 | Car/top-up (`application_type 03`, non Mega-Solusi): cek chassis query kontrak ACTIVE dengan chassis sama → blokir bila ada. | `24-npp §7 BR-NPP-3` | [INTENT] | Anti double-finance; rebuild jadikan general (BR-NPP-N5). |
| BR-NPP-4 | Setelah aktif, Perjanjian Pembiayaan + dokumen legal pendamping tersedia cetak on-demand via endpoint reports; **bukan** dipicu transaksi approval. | `24-npp §7 BR-NPP-4`; `NPPReportsController.cs` | **[LOCKED]** (dokumen regulated) | `sp_approve_npp` TIDAK memanggil print. |
| BR-NPP-5 | NPP hanya editable saat status ∈ {Draft, Correction, RFA}; Approved/Rejected mem-blokir edit. | `24-npp §7 BR-NPP-5`; `sp_validation_npp_status.sql:16` | [INTENT] | Map ke guard `pending`/`held(correction)`. |
| BR-NPP-8 | Rantai maker→checker: submitter (pre-decided) + 1 supervisor (pending). | `24-npp §7 BR-NPP-8` | [INTENT] | Kedalaman = OQ-NPP-06. |
| BR-NPP-11 | Due-date angsuran di-snap ke day-of-month tetap (+offset) bila approve-date = trigger "bill received date". | `24-npp §7 BR-NPP-11` | [INTENT] | Alignment siklus billing. |
| BR-NPP-12 | Insurance Billing Periodical + Coverage Period di-overwrite ke nilai tetap saat aktivasi. | `24-npp §7 BR-NPP-12` | [INTENT] | Intent tak jelas → OQ-NPP-10 (jangan diselesaikan diam-diam). |
| BR-NPP-13 | GL booking + Repeat-Order subsidy dispatch by item-type (motor vs car); subsidy motor tergated flag repeat-order CAS. | `24-npp §7 BR-NPP-13` | [INTENT] | Eksekusi = milik downstream (PULL), 05 hanya emit event. |
| BR-NPP-14 | Registrasi Passnet: `passnet_id` di-mint format `'5'`+9-digit zero-pad, link 1:1 ke credit/contract id. | `24-npp §7 BR-NPP-14`; `passnet §5` | **[LOCKED]** (format interop eksternal) | WAJIB verbatim. |
| BR-NPP-16 | Customer master (individu by `national_id`, korporat by `tax_id`) WAJIB ada & di-rekonsiliasi tiap aktivasi: nama di-update bila mismatch; record **kedua** (bukan merge) bila identitas sama di company berbeda. | `24-npp §7 BR-NPP-16` | **[LOCKED]** (field identitas) | Mekanik upsert `[INTENT]`; identitas `[LOCKED]`. |
| BR-NPP-17 | Reject/Correction hanya update status header — **tidak** ada side-effect cascade. | `24-npp §7 BR-NPP-17` | [INTENT] | Scoping side-effect ke aktivasi genuine. |

### 6.2 Keputusan desain baru & perbaikan do-not-replicate

| ID | Aturan | Sumber KB (gotcha) | Marker | Catatan |
|---|---|---|---|---|
| BR-NPP-N1 | **Verification hard-gate in-transaction**: sebelum aktivasi, `VERIFICATION.status = verified` **dan** freshness ≤ 30 hari. Gagal → block `403` + **rollback** penuh. | overview §5 boundary_ownership ("Verification hard-gate sebelum NPP"); data-mutation-policy | **[KEPUTUSAN DESAIN BARU]** | Legacy tak punya gate ini. 05 = eksekutor; verification = produsen status. |
| BR-NPP-N2 | **BAST hard-gate**: `bast_no` + `bast_date` WAJIB non-null & lengkap sebelum activate; gagal → block `422`. Set `bast_validated=true`. | GOTCHA-15; `24-npp BR-NPP-21`; OQ-NPP-14 | **[KEPUTUSAN DESAIN BARU]** | Legacy hanya prosedural (approver eyeball). Keputusan "hard vs prosedural" = OQ-NPP-14. Rebuild default **fail-closed**. |
| BR-NPP-N3 | **Aktivasi atomik**: seluruh konsekuensi internal (aktivasi header, `tr_CIF` upsert, outbox Passnet, emit event) dalam **satu transaksi**; error apa pun → **ROLLBACK penuh**. | Edge Case 7 (`sp_approve_npp:721-741` commit-on-error); overview §7.3 | **[KEPUTUSAN DESAIN BARU]** | Ganti pola `IF XACT_STATE()=1 COMMIT` fail-soft. |
| BR-NPP-N4 | **Idempotent di batas mutasi**: guard re-aktivasi hidup **di dalam** transaksi aktivasi (via `Idempotency-Key` + status check), bukan hanya pre-check caller. Replay → hasil pertama, tanpa cascade ulang. | Edge Case 6 (guard di `sp_validation_npp_status`, bukan `sp_approve_npp`) | **[KEPUTUSAN DESAIN BARU]** | Cegah duplikasi amortisasi/AR/overdue/CIF. |
| BR-NPP-N5 | **Uniqueness chassis/engine general**: cek duplikat chassis+engine terhadap semua kontrak aktif untuk **semua** lini (motor & car), satu tempat sentral. | BR-NPP-2/3; ASSET `[LOCKED]` unik | **[KEPUTUSAN DESAIN BARU]** | Legacy hanya cek narrow (motor year-code, car top-up saja). |
| BR-NPP-N6 | **Downstream = PULL/event, BUKAN push**: 05 emit `AgreementActivated`; disbursement/GL, Dealer Payment, BPKB **pull/subscribe** — 05 tidak meng-eksekusi/menulis mereka. | GOTCHA-12; `24-npp §5 step 12` | **[KEPUTUSAN DESAIN BARU]** | Bila pull dipertahankan, dokumentasikan eligibility-query sebagai kontrak (OQ-NPP-03/04). |
| BR-NPP-N7 | **Passnet via outbox + reconciliation**: outbox transaksional; consumer/ack menulis balik `is_sync=synced/failed` + `return_message`; retry idempotent + dead-letter. | GOTCHA-13; Edge Case 2; overview §8.3 | **[KEPUTUSAN DESAIN BARU]** | Legacy fire-and-forget tanpa write-back (OQ-PASSNET-01/02). |
| BR-NPP-N8 | **Due-date check di dalam mutasi**: aturan due-date ≥ approve-date (BR-NPP-7) di-enforce di dalam aktivasi, bukan hanya endpoint advisory. | Edge Case 9 (`sp_validate_npp_approve` UI-only) | **[KEPUTUSAN DESAIN BARU]** | Cegah bypass caller langsung. |
| BR-NPP-N9 | **Satu engine config-driven car/motor**: kolapskan pasangan endpoint/method motor vs car (`/save`,`/insert`,`/car/*`) → satu resource, asset-type sebagai data. | GOTCHA-10; `24-npp §5 step 1` | **[KEPUTUSAN DESAIN BARU]** | Aturan lini eksplisit & table-driven. |
| BR-NPP-N10 | **Jangan konflasi dua cek 30-hari**: freshness **verification** (hard-gate, `VERIFICATION.freshness_at`) berbeda dari freshness **SLIK/FCL** credit-analysis (advisory). | overview §4/§5; `24-npp §5 step 2 / BR-NPP-6`; Edge Case 8 | **[KEPUTUSAN DESAIN BARU]** | Pisahkan; jangan campur. |
| BR-NPP-N11 | **FCL/SLIK freshness (BR-NPP-6) di-enforce sentral & konsisten** untuk semua jalur pembuatan NPP (legacy hanya di endpoint car plain insert/update). | Edge Case 8 (`sp_validation_npp_fcl_expiry` inkonsisten) | **[KEPUTUSAN DESAIN BARU]** | Satu titik enforcement; scope final = keputusan (advisory vs blocking — kaitkan OQ-REG-06). |
| BR-NPP-N12 | **Aktivasi TIDAK menjalankan cascade GL/subledger/subsidy inline**; itu konsekuensi downstream (post-acquisition) dipicu event. 05 hanya menulis entitas yang dimilikinya (§3.1). | `32-disbursement §1` vs overview §6.1 (ownership) | **[KEPUTUSAN DESAIN BARU]** | Decompose transaksi monolitik legacy sesuai boundary umbrella. |
| BR-NPP-N13 | **Cetak print action bukan bagian mutasi aktivasi** — tetap on-demand (BR-NPP-4), konform. | BR-NPP-4 | [INTENT] | — |
| BR-NPP-20 | Field `DealerPaymentStatus`/`ArScheduleStatus` di kontrak lama = **vestigial** (tanpa backing column) → **dibuang**. | `24-npp §7 BR-NPP-20` | `[ARTIFACT]` | Konfirmasi stakeholder → ADR. |
| BR-NPP-DEAD | Blok Rapindo claim-asset (`sp_validation_skip_rapindo`) = **dead code** (di-comment) → JANGAN dihidupkan as-is; re-implement deliberate bila masih requirement. | Edge Case 1 | `[ARTIFACT]` | — |

---

## 7. State Machine

**Status kanonik `FINANCING_AGREEMENT.status`** (overview §7.2): `pending` · `validated` · `active` · `held`.

**Mapping legacy → kanonik** (dokumentasikan saat migrasi):

| Legacy `agreement_status` | Kanonik | Catatan |
|---|---|---|
| (Draft, belum submit) | `pending` | Pre-submit; belum ada validasi chassis / rantai checker. |
| `0` (RFA-locked: chassis-validated, rantai checker dibangun, menunggu keputusan approver) | `validated` | Submission NPP menulis `agreement_status='0'` (RFA) **setelah** validasi chassis + build checker chain (`24-npp §5 step 4`; state machine `24-npp §8`). Gate keras lolos di submit; re-enforce di aktivasi. Arti status `0` = OQ-CMPO-01. |
| `A` (Approved/Active) | `active` | Terminal happy-path; picu downstream + event. |
| `C` (Correction) | `held` (disposition=`correction`) | Re-openable ke `pending`. |
| `R` (Reject/"Review"/"Reject" — label drift Edge Case 12) | `held` (disposition=`rejected`) | Terminal; label kanonik tunggal ("Rejected"). |
| `V` (Verify) | — | Tidak ada write-path ditemukan; **tidak dimodelkan** sebagai state reachable (OQ-NPP-05). |

> Enum kanonik meng-*collapse* correction & reject ke `held`; dibedakan oleh field `disposition`/`reason` (correction re-openable; rejected terminal). Ini konform umbrella, sambil jujur ke workflow.

### Tabel transisi

| Dari | Aksi | Ke | Guard / Prasyarat |
|---|---|---|---|
| — | `POST /npp` (dari PO issued) | `pending` | PO status `issued`, belum ada NPP aktif. |
| `pending` | `PUT /npp/{id}` (edit) | `pending` | Status editable (BR-NPP-5). |
| `pending` | `POST /submit` (RFA) | `validated` | Chassis/engine valid (BR-NPP-1/N5); pre-flight gate lolos; bangun checker (BR-NPP-8). |
| `pending` | `POST /submit` gagal chassis | `pending` | `422 CHASSIS_ENGINE_MISMATCH` — tetap `pending`. |
| `validated` | `POST /decision approve` | `active` | **Hanya pada langkah checker TERAKHIR** (tidak ada `NPP_APPROVAL_STEP` pending tersisa — gate eksplisit "no remaining pending rows", perbaiki Edge Case 5 agar aman bila hierarki diperdalam). Lalu **in-transaction re-enforce**: verification `verified`+≤30h (N1) → BAST lengkap (N2) → due-date ≥ approve-date (N8) → chassis re-check. Semua lolos → aktivasi atomik (N3), mint `agreement_no`, upsert `tr_CIF`, outbox Passnet, emit `AgreementActivated`. |
| `validated` | `approve` gagal verification | `validated` | `403 VERIFICATION_GATE_FAILED` + **rollback**; tidak ada side-effect (N1). |
| `validated` | `approve` gagal BAST | `validated` | `422 BAST_INCOMPLETE` + rollback (N2). |
| `validated` | `approve` gagal due-date | `validated` | `422 DUE_DATE_BEFORE_APPROVAL` + rollback (N8). |
| `validated` | `POST /decision correction` | `held(correction)` | Reason wajib; hanya update status (BR-NPP-17). |
| `validated` | `POST /decision reject` | `held(rejected)` | Reason wajib; hanya update status (BR-NPP-17). |
| `held(correction)` | `PUT /npp/{id}` → `POST /submit` | `pending` → `validated` | Guard status editable (BR-NPP-5). |
| `held(rejected)` | — | (terminal) | Tidak re-editable via save/update (inferred terminal — OQ-NPP-12). |
| `active` | (replay `decision` idempotent) | `active` | Idempotency-Key sama → hasil pertama, tanpa cascade ulang (N4). |
| `active` | — | (terminal workflow) | Downstream ber-reaksi via PULL/event (N6). |

Jalur non-happy-path tercakup: chassis mismatch (tetap `pending`), tiga gate fail saat approve (rollback ke `validated`), correction bounce-back, reject terminal, idempotent replay.

---

## 8. Integrasi Eksternal

Semua seam eksternal **WAJIB** lewat Anti-Corruption Layer (ACL) app-tier; **hapus** linked-server DML lintas-DB & HTTP dari dalam T-SQL (overview §8.2; data-mutation-policy anti-patterns).

| Seam | Arah | Sync/Async | Pemilik | Aturan ACL |
|---|---|---|---|---|
| **Passnet / mf-payment** | Outbound (Fincore → Passnet) | **Async** (outbox) | **05-npp** | Outbox transaksional + reconciliation; `passnet_id` `[LOCKED]` format verbatim. Drain/write-back eksternal belum ter-lokasi (OQ-PASSNET-01/02). Legacy = linked-server D2D `[MACF-DBMCF].[EPMCF]`/`[MACF-DBMAF].[EPMAF]` → ganti kontrak API/outbox. |
| **Passnet (read cross-check)** | Inbound (Fincore ← Passnet) | Sync | collateral (fidusia) | Cross-check NPP-number + legal-entity via ACL read; **jangan** hidupkan write-back `IsUpload=1` yang di-comment tanpa konfirmasi (Edge Case 3, OQ-PASSNET-04). Bukan owned 05. |
| **Dealer Payment** | Downstream PULL | Async (batch) | PAYMENT DB eksternal (`PAYMENTContext`) | 05 **tidak** men-seed; consumer poll record eligible. Eligibility-determinant + poster header = OQ-NPP-03. |
| **DOKU (bank-account inquiry)** | Outbound | Sync | **04-contract-cm-po** (bukan 05) | Disebut untuk konteks — inquiry rekening di stage CM (bukan aktivasi NPP). Rebuild: HTTP client app-tier (ganti `sp_OACreate` OLE ke IP hardcode), write-back response owned (OQ-DOKU-01). |
| **Fidusia** | Internal record-keeping | n/a | collateral (post-acquisition) | **BUKAN** API pemerintah live — record-keeping internal. Bukan owned 05. |
| **GL / Disbursement subledger** | Downstream PULL/event | Async | disbursement-subledger | GL crosswalk bank-ID `[LOCKED]` verbatim; idempotent + reversal + fail-closed (overview §8.4). Bukan owned 05. |
| **BPKB custody** | Downstream PULL | Async | collateral | Candidate-queue PULL by `verification_status` (OQ-NPP-04/OQ-COLL-01). Guard di-enforce in-transaction di domain-nya. Bukan owned 05. |

---

## 9. Acceptance Criteria (Given/When/Then)

**AC-1 — Happy path aktivasi**
- **Given** NPP `validated`, verification `verified` (freshness 12 hari), chassis/engine cocok, BAST lengkap, due-date ≥ approve-date, Idempotency-Key baru;
- **When** approver `POST /npp/{id}/decision {action:"approve"}`;
- **Then** status → `active`, `agreement_no` di-mint, `passnet_id` `'5'`+9-digit dibuat, `tr_CIF` di-upsert by `national_id`, `AgreementActivated` di-emit, semua dalam **satu transaksi**; response `200`.

**AC-2 — Verification gate fail (fail-closed + rollback)**
- **Given** NPP `validated`, `VERIFICATION.status = recheck` (atau freshness 41 hari);
- **When** approver `approve`;
- **Then** `403 VERIFICATION_GATE_FAILED`, transaksi **rollback penuh**, status tetap `validated`, TIDAK ada `agreement_no`/`tr_CIF`/outbox/event (BR-NPP-N1).

**AC-3 — BAST hard-gate**
- **Given** `bast_no`/`bast_date` null saat approve;
- **When** approver `approve`;
- **Then** `422 BAST_INCOMPLETE`, rollback, `bast_validated=false` (BR-NPP-N2, OQ-NPP-14).

**AC-4 — Chassis/engine mismatch di submit**
- **Given** `chassis_no`/`engine_no` tak cocok Credit Memo;
- **When** maker `POST /submit`;
- **Then** `422 CHASSIS_ENGINE_MISMATCH`, status tetap `pending`, checker tidak dibangun (BR-NPP-1).

**AC-5 — Idempotent replay aktivasi**
- **Given** NPP sudah `active` via Idempotency-Key `K`;
- **When** `POST /decision` diulang dengan `K`;
- **Then** `200` mengembalikan hasil aktivasi pertama; cascade & `tr_CIF` upsert **tidak** dijalankan ulang; tak ada duplikasi (BR-NPP-N4).

**AC-6 — Reject/Correction tanpa side-effect**
- **Given** NPP `validated`;
- **When** approver `reject`/`correction` (reason terisi);
- **Then** status → `held`, hanya header update; TIDAK ada aktivasi/outbox/CIF/event (BR-NPP-17); `correction` re-openable ke `pending`, `reject` terminal.

**AC-7 — Downstream PULL, bukan push**
- **Given** NPP `active` + `AgreementActivated` di-emit;
- **When** downstream (disbursement/Dealer Payment/BPKB) beroperasi;
- **Then** mereka **pull/subscribe** record eligible; 05 tidak menulis `DISBURSEMENT`/`BPKB`/PAYMENT DB (BR-NPP-N6, GOTCHA-12).

**AC-8 — Passnet outbox + reconciliation**
- **Given** aktivasi sukses;
- **When** outbox-row `PASSNET_SYNC` (`is_sync=pending`) dibuat & consumer ack;
- **Then** reconciliation menulis `is_sync=synced`/`failed` + `return_message`; retry idempotent; kegagalan permanen → dead-letter (BR-NPP-N7).

**AC-9 — No self-approval (audit)**
- **Given** approver identity = submitter (non super-user);
- **When** `approve`;
- **Then** ditolak di application layer (no self-approval, OQ-MCP-01); super-user override ter-audit ke `NPP_APPROVAL_HISTORY` (BR-NPP-9, overview §8.1).

**AC-10 — Due-date enforced in mutation**
- **Given** due-date < approve-date;
- **When** `approve` langsung (bypass endpoint advisory);
- **Then** `422 DUE_DATE_BEFORE_APPROVAL`, rollback (BR-NPP-N8, perbaiki Edge Case 9).

---

## 10. Dependency

### 10.1 Upstream dikonsumsi (read)

| Sumber | Yang dikonsumsi | Mode |
|---|---|---|
| **04-contract-cm-po** | `PURCHASE_ORDER` (`po_number` issued) sebagai trigger masuk; `CREDIT_MEMO` (branch/dealer/tenor/rate/asset/`trans_type_id`). | Read (event `POIssued` sebagai sinyal siap). |
| **01-intake-cas** | `ASSET` (`chassis_no`/`engine_no`) untuk validasi final; identitas applicant (NIK/NPWP) untuk seed `tr_CIF`. | Read. |
| **kapabilitas verification** | `VERIFICATION.status` + `freshness_at` (hard-gate). | Read (gate). |
| **02-credit-analysis** | Freshness SLIK/FCL (**advisory**, BR-NPP-6/N11) — jangan konflasi dgn verification (N10). | Read (advisory). |

### 10.2 Downstream dipicu — PULL / event (BUKAN push)

| Target | Cara dipicu | Kepemilikan | OQ |
|---|---|---|---|
| **Disbursement / GL subledger + amortisasi + AR-card** | Event `AgreementActivated` → consumer **pull**; eksekusi GL idempotent + fail-closed. | disbursement-subledger (post-acq) | GL crosswalk `[LOCKED]`; OQ (fail-open OQ-REG-06) |
| **Dealer Payment (transfer dana)** | **Pull** batch cashier dari PAYMENT DB eksternal; eligibility belum ter-lokasi. | PAYMENT DB eksternal | **OQ-NPP-03** |
| **BPKB custody** | **Pull** candidate-queue by `verification_status` (`agreement_status` di-comment). | collateral (post-acq) | **OQ-NPP-04 / OQ-COLL-01** |
| **Passnet registration** | **Outbox** `PASSNET_SYNC` di-drain consumer eksternal + write-back. | 05 (outbox) → ETL eksternal | **OQ-PASSNET-01/02** |
| **Event `AgreementActivated`** | Emitted 05; consumer subscribe. | 05 (emit) | — |

---

## 11. Keputusan Dibutuhkan (Open Questions)

> `[OPEN]` dari KB — **jangan** diselesaikan diam-diam. Rebuild memakai default fail-closed di mana relevan, tetapi keputusan final butuh stakeholder.

| OQ-ID | Pertanyaan | Prioritas | Dampak |
|---|---|---|---|
| **OQ-NPP-14** | BAST completeness: jadikan **hard gate in-transaction** (default rebuild, BR-NPP-N2) atau tetap kontrol **prosedural/manual**? | P1 | Menentukan apakah butuh BR baru vs dokumentasi kontrol manual. |
| **OQ-NPP-03 / OQ-DISB-05** | Tak ada reader men-seed Dealer Payment dari `agreement_status='A'` — apa penentu eligibility batch Dealer Payment & siapa post header di PAYMENT DB eksternal? | P1 | Kontrak downstream seam FASE 15. |
| **OQ-NPP-02 / OQ-PASSNET-01/02** | Siapa men-drain `tr_synchronize_to_passnet` (`is_sync='0'`) & menulis balik? Scope Passnet (master NPP vs eksekusi payment)? | P1 | Desain outbox/ACL Passnet. |
| **OQ-NPP-04 / OQ-COLL-01** | BPKB candidate-queue: key by `verification_status='A'` (live) vs `agreement_status='A'` (di-comment) — intentional atau regresi? | P2 | Predikat eligibility BPKB. |
| **OQ-NPP-06** | Hierarki 2-level tetap (BR-NPP-8) = desain sengaja atau simplifikasi stale dari rantai lebih dalam? | P2 | Bentuk maker-checker (N-level configurable?). |
| **OQ-NPP-07 / OQ-MCP-01** | Self-approval-by-super-user (BR-NPP-9) = kontrol bypass yang di-intend atau artefak? API/session layer enforce "hanya assigned/super-user boleh act" utk endpoint NPP approve? | P2 | Audit maker-checker; policy flag. |
| **OQ-NPP-05** | `V` (Verify) status reachable di produksi? Tak ada write-path ditemukan. | P3 | Kelengkapan state machine. |
| **OQ-CMPO-01** | Arti legacy status `0` ("Status 0" flow-doc = header parent aplikasi atau status memo/agreement sendiri; `0`=RFA-locked di kode) — cross-domain (01/04, overview §5). | P1 | Mapping legacy→kanonik (§7: `0`→`validated`) + guard editability `PUT` (§4). |
| **OQ-NPP-10** | Hard-reset Insurance Periodical/Coverage (BR-NPP-12) = policy sengaja atau overwrite tak sengaja nilai CMO? | P3 | Aturan insurance-at-activation. |
| **OQ-NPP-12** | `held(rejected)` benar-benar terminal atau ada path out-of-slice yang re-open? | P3 | Terminalitas state. |
| **OQ-NPP-08** | `tr_NPP.bast_no/bast_date` vs `tr_BPKB.BAST_date/BPKB_no` — dua record BAST terpisah; harus rekonsiliasi? Mana authoritative? | P2 | Konsistensi BAST lintas domain (Edge Case 10). |
| **OQ-NPP-09** | Identitas consumer `tr_batching_trans` (write tanpa reader lokal) — batch/collection job eksternal? | P3 | Perlukah rebuild queue setara. |
| **OQ-NPP-01** | `sp_validate_npp_rfa` (dipanggil `/validate-rfa-npp`) tidak ada di SP dump lokal — hosted di environment lain? | P6 | Kelengkapan validasi RFA. |
| **OQ-NPP-11** | `SpSyncToPassnetR2_reversal` (mobile-CAS migration reversal) terkait atau naming collision murni dgn `tr_synchronize_to_passnet`? | P3 | Perlakukan unrelated hingga terbukti. |
| **OQ-NPP-13** | `sp_get_history_npp` membaca `tr_hierarchy_approval_transaction` saat `agreement_status='A'` — dead code atau ada archival writer? | P1 | Sumber audit history NPP. |
| **OQ-REG-06** | Fail-open vs **fail-closed** untuk SEMUA regulated gate (termasuk verification/chassis/BAST/FCL NPP) saat SP core throw mid-check. | P1 (highest-impact) | Kebijakan global gate; menyentuh N1/N2/N11. |
| **OQ-DOKU-01** | Siapa mengisi `DOKU_*.response*` (write-back tanpa caller)? | P1 | ACL DOKU (seam CM, disebut untuk konteks). |
| **OQ-ARCH-STACK** | `[KEPUTUSAN DESAIN BARU]` — target stack (bahasa/runtime/transport REST vs gRPC vs message-bus) belum ditentukan; kontrak ditulis level resource+field. | — | Konvensi API semua kapabilitas. |
