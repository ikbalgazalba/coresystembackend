# DB-CONVENTIONS — Standar Schema Database Core Acquisition Baru

> **Status**: DISEPAKATI (keputusan user 2026-07-14) — lampiran WAJIB semua PRD `BE-0x` §3 Model Data.
> Setiap tabel target di PRD ditulis mengikuti konvensi ini. Penyimpangan hanya untuk field `[LOCKED]`
> per `data-mutation-policy.md` (nilai/makna dipertahankan; NAMA tetap boleh mengikuti konvensi kecuali
> di-referensikan sistem eksternal secara literal).
>
> RDBMS asumsi: PostgreSQL (USULAN — final by ITEC, A-2). Semua aturan portable ke RDBMS lain.

---

## 1. Struktur & penamaan tabel

**Satu schema** (`public` / sesuai standar ITEC). Klasifikasi tabel via **prefix**:

| Prefix | Kelas | Contoh | Aturan |
|---|---|---|---|
| `mst_` | Master / reference data | `mst_dealer`, `mst_user`, `mst_public_holiday` | Dimiliki modul master-data kecuali dinyatakan lain; maker-checker bila mutable oleh user |
| `trx_` | Transaksional (spine bisnis) | `trx_application`, `trx_credit_memo`, `trx_agreement` | Selalu punya audit columns; status via kolom `status` tunggal |
| `cfg_` | Konfigurasi engine/rule | `cfg_hierarchy_matrix`, `cfg_ia_policy`, `cfg_deviation_rule` | Versioned (`effective_from`, `effective_to`); perubahan = data, bukan deploy |
| `log_` | Audit / append-only log | `log_approval_history`, `log_slikojk`, `log_document_print` | INSERT-only; tidak pernah UPDATE/DELETE; retensi per kebijakan PDP |
| `map_` | Mapping / bridge antar sistem | `map_moofi_fincore`, `map_nik_repeat_order` | Kunci komposit sumber↔target; idempotent upsert |
| `stg_` | Staging migrasi / integrasi | `stg_legacy_tr_cas`, `stg_rac_callback` | Boleh di-truncate; TIDAK dikonsumsi logic bisnis langsung |
| `out_` | Transactional outbox | `out_event`, `out_notification` | Skeleton ADR-04; dispatcher-only consumer |

Aturan nama:
- `snake_case` semua (tabel, kolom, index, constraint). Bahasa **Inggris** konsisten
  (legacy campur: `tr_penyimpangan_effrate` vs `tr_general_deviation` — distandarkan).
- Nama tabel **singular** setelah prefix (`trx_application`, bukan `trx_applications`) — prefix sudah
  menandai kelas, singular menjaga konsistensi dengan nama entity di kode Java.
- Index: `ix_{table}_{cols}`; unique: `ux_{table}_{cols}`; FK constraint: `fk_{table}_{ref_table}`;
  check: `ck_{table}_{rule}`.

## 2. Kunci & relasi

| Elemen | Standar | Catatan |
|---|---|---|
| Primary key | `id BIGINT GENERATED ALWAYS AS IDENTITY` | UUIDv7 hanya bila ITEC mensyaratkan distributed-friendly (A-2) |
| Business key | kolom terpisah + `ux_` unique | `credit_id` = business key `[LOCKED]` di `trx_application`, BUKAN PK teknis — dirujuk lintas modul & eksternal. Format KINI DIKETAHUI: 14-char `branch(5)+YY+MM+SEQ(5)`, reset bulanan per cabang (OQ-GT-02 resolved — spec: PRD BE-01 §3.1.13 / BE-07 §3.4; jangan duplikasi format di sini) |
| Foreign key | `{entity}_id` + constraint FK nyata | Legacy nyaris tanpa declared FK — rebuild WAJIB declared FK |
| Cross-module ref | via business key (`credit_id`) atau `id` + ownership registry | Tidak ada JOIN lintas modul di write path (ADR-03) |

## 3. Tipe data standar (mapping dari legacy MSSQL)

| Domain | Legacy MSSQL umum | Target | Aturan |
|---|---|---|---|
| Uang / nilai finansial | `decimal(18,0)`, `money`, `varchar`(!) | `NUMERIC(18,2)` | Legacy whole-rupiah: simpan `.00`; rekonsiliasi migrasi membandingkan nilai bulat. TIDAK PERNAH float |
| Rate / persentase | `decimal`, `float` | `NUMERIC(9,6)` | Effective rate presisi 6 desimal |
| Tanggal-waktu | `datetime`, `smalldatetime` | `TIMESTAMPTZ` | Simpan UTC; render WIB/WITA/WIT di FE |
| Tanggal murni | `datetime` (jam 00:00) | `DATE` | Tanggal lahir, tanggal akta, dll |
| Boolean | `int` 0/1, `char(1)` 'Y'/'N', `bit` | `BOOLEAN NOT NULL DEFAULT false` | Tidak ada boolean nullable — three-state pakai enum |
| Enum kecil (≤ ~10 nilai stabil) | `char(1)`, `varchar` kode | `VARCHAR` + `CHECK` constraint | Nilai `[LOCKED]` (mis. status RFA '0', Approved 'A') dipertahankan sebagai nilai; nama kolom tetap standar |
| Enum besar / berubah | kode + lookup eksternal | FK ke `mst_`/`cfg_` table | |
| Teks identitas | `varchar(n)` | `VARCHAR(n)` panjang eksplisit | NIK=16, NPWP=15/16 — panjang `[LOCKED]` regulatori |
| Teks bebas / narasi | `text`, `varchar(max)` | `TEXT` | |
| Dokumen/file | path `varchar` | `VARCHAR` object-key + metadata table | File di object storage, bukan BLOB |

## 4. Kolom wajib

Semua tabel `trx_`/`mst_`/`cfg_`/`map_`:

```sql
created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
created_by  VARCHAR(50) NOT NULL,          -- user id / system actor
updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
updated_by  VARCHAR(50) NOT NULL
```

- `log_` tables: hanya `created_at`/`created_by` (append-only).
- Soft-delete: `deleted_at TIMESTAMPTZ NULL` HANYA bila bisnis butuh restore; default = hard delete
  dilarang pada `trx_` (pakai status), diperbolehkan pada `mst_` non-referenced.
- Optimistic locking: `version INTEGER NOT NULL DEFAULT 0` pada tabel yang di-edit user konkuren
  (CM, master data).

## 5. Status & state machine

- SATU kolom `status` per tabel spine — dilarang status ganda-makna (legacy `tr_CA` punya 3 kolom
  status overlap: `CA_decision`, `approval`, `CA_status` — do-not-replicate).
- Nilai status = vocabulary state machine modul (PRD §7); transisi hanya via service/engine, dienforce
  CHECK + application layer.
- Riwayat transisi → `log_` table (append-only), bukan kolom `last_*` bertumpuk.

## 6. Larangan eksplisit (dari gotcha legacy)

1. ❌ Shadow table / suffix `_shd`, `_R2`, `_staging` untuk versioning → pakai `log_` + `version`.
2. ❌ Kolom print-tracking bespoke (`sum_of_print`, `last_printed_by`) → `log_document_print` generik.
3. ❌ Denormalisasi identitas (NIK ditulis ulang di CA/CM/NPP) → derive via JOIN; satu sumber.
4. ❌ Kolom polimorfik posisi (related-person by index) → typed rows + `role` enum.
5. ❌ Increment manual `logId` (race di `DOKU_API_Log`) → identity/sequence.
6. ❌ Tabel temp permanen (`temppotonganro`) → `stg_` + lifecycle jelas, atau CTE.

## 7. Contoh penerapan (before → after)

| Legacy | Target | Kelas |
|---|---|---|
| `tr_CAS` | `trx_application` | spine intake |
| `tr_CAS_references` | `trx_application_related_person` | typed roles (D-01 S2) |
| `tr_CM` (124 kolom!) | `trx_credit_memo` + `trx_credit_memo_insurance` + `trx_credit_memo_payment` (dinormalisasi per §3 PRD BE-04) | spine contract |
| `ms_hierarchy_transaction` (eksternal) | `cfg_hierarchy_matrix` | config engine |
| `tr_hierarchy_transaction` | `log_approval_history` (+ Flowable runtime tables utk in-flight) | audit |
| `tr_ia_history` | `log_instant_approval` | audit policy lane |
| `CASMobile_mappingfincore` | `map_moofi_fincore` | bridge STEP 8 |
| `tr_mapping_NIK_RO` | `map_nik_repeat_order` | bridge dedup/RO |
| `tr_synchronize_to_passnet` | `out_event` (outbox, ADR-04) | infra |
| `tr_auto_number` / `tr_generate_code` | sequence DB + `cfg_number_format` | infra numbering `credit_id` |

## 8. Workflow engine (Flowable) — footprint DB

Keputusan ADR-13: Flowable embedded memakai tabel runtime-nya sendiri (prefix `ACT_*`, dikelola engine —
di luar konvensi ini, JANGAN disentuh manual). Aturan integrasi:
- Data bisnis TIDAK disimpan di variabel proses kecuali key (`credit_id`, `task ref`) — payload tetap di
  tabel `trx_`.
- Keputusan/riwayat approval tetap ditulis ke `log_approval_history` milik kita (audit independen dari
  engine, kebutuhan regulatori) — engine bukan satu-satunya sumber audit.
- Definisi proses (BPMN) di-versioning di repo; matriks per-produk (D-07) & hierarki (trans_type_id + OP
  + risk) dibaca dari `cfg_` tables oleh delegate — BUKAN di-hardcode di BPMN.

## 9. Kepatuhan PRD

Setiap PRD `BE-0x` §3 Model Data WAJIB per tabel target:
1. Nama target sesuai konvensi ini + kelas prefix.
2. **Mapping asal**: tabel/kolom legacy sumber (basis `DATA-MIGRATION-PLAN.md`).
3. Field census: kolom | tipe target | nullability | marker confidence×mutability | catatan.
4. Klasifikasi tabel legacy yang TIDAK dibawa: `[ARTIFACT — discard]` dengan alasan, atau
   `[OPEN — OQ-xx]` bila butuh keputusan.
