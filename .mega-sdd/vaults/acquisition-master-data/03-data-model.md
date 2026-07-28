# 03 — Data Model

> **TL;DR**: 24 tabel target + 2 `log_` pendamping (26 total), mengikuti `docs/DB-CONVENTIONS.md` (ADR-14). Tiga tier kepemilikan: Tier A OWNED (CRUD), Tier B SYNCED MIRROR (HR read-only), Tier C EXTERNAL READ-ONLY (FC_MSTAPP_MCF via ACL, no local table). PK `id BIGINT IDENTITY` + business key terpisah. Declared FK wajib. Audit columns wajib. Master mutable user konkuren bawa `version INTEGER` (optimistic locking). Census kolom penuh + mapping legacy di BE-07 §3 (ground truth schema).

## Schema conventions (otoritatif `docs/DB-CONVENTIONS.md` — inherited umbrella)

- Prefix: `mst_` master · `cfg_` konfigurasi engine/rule (versioned `effective_from/to` kecuali menu = lifecycle `is_active`) · `log_` audit append-only · `map_` bridge. Singular snake_case English.
- PK `id BIGINT GENERATED ALWAYS AS IDENTITY`; business key legacy (mis. `dealer_code`) jadi kolom terpisah `ux_` unique — BUKAN PK.
- Kolom wajib: `created_at/created_by/updated_at/updated_by` semua `mst_/cfg_/map_`; `log_` hanya `created_at/created_by`. Optimistic locking `version INTEGER` pada tabel edit konkuren.
- Tipe: uang `NUMERIC(18,2)`; rate `NUMERIC(9,6)`; `TIMESTAMPTZ` UTC; `DATE`; `BOOLEAN NOT NULL DEFAULT false`; enum kecil `VARCHAR`+`CHECK` (role D-10, status); NIK `VARCHAR(16)` `[LOCKED]`, NPWP 15/16 `[LOCKED]`.
- Satu kolom `status`/lifecycle per spine; riwayat → `log_` append-only. Larangan: shadow table, print-tracking bespoke, denormalisasi identitas, kolom polimorfik posisi, increment manual, temp permanen.
- Maker-checker via envelope E37 (DB-CONVENTIONS §1 baris `mst_`); scope BR-BE07-05/OQ-BE07-01. FK declared nyata; gate migrasi = 0 orphan.

## Tier A — OWNED (CRUD penuh; maker-checker untuk subset BR-BE07-05)

### User management & menu (BE-07 §3.1)

| Tabel target | Key (business/unique) | Mapping asal (legacy) | Disposisi | Catatan |
|---|---|---|---|---|
| `mst_user` | `ux_mst_user_employee_nik` | — (BARU, mandat D-08; legacy tanpa RBAC) | REBUILD | TANPA super-user (D-09); role enum D-10 `[LOCKED]`; TIDAK ada `password` (BR-SHELL-1); `deactivation_reason` enum `manual\|hr_resigned` |
| `mst_user_branch_scope` | (`user_id`, `branch_id`) | — (BARU) | REBUILD | Child normalisasi `branch_scope` list; single vs multi = OQ-BE07-04 |
| `cfg_menu` | `id` (self-FK `parent_id`) | `ms_module_menu` (DDL ✅ 16 kolom) | MIGRATE | `trans_type_id_prefix` `[LOCKED]` verbatim (DDL `varchar(10) NULL`); rebuild role-driven (legacy per-position/employee); deactivate-only (no DELETE) |
| `cfg_menu_role_grant` | `ux_..._role_menu_id` (`role`,`menu_id`) | `ms_position_menu` (DDL ✅ 7 kolom) | REBUILD | Re-key position-code → role D-10; bukan copy 1:1 (OQ-BE07-05); `is_view_only` paritas |
| `cfg_menu_user_grant_special` | (`employee_nik`,`menu_id`) | `ms_position_menu_special` (DDL ✅ 7 kolom) | `[OPEN — OQ-BE07-05]` | Dibangun HANYA bila fitur dipertahankan (risiko backdoor D-09); `granted_by`/`granted_reason` |

> Role catalog = enum tertutup D-10 `VARCHAR`+`CHECK`; **tabel `mst_role` TIDAK dibuat** selama enum tertutup `[LOCKED]`. Bila OQ-BE07-03 memutuskan perluasan role HO ber-governance, enum diangkat jadi `mst_role` — jangan tambah nilai diam-diam.

### Employee mirror (Tier B — SYNCED MIRROR, BE-07 §3.1)

| Tabel target | Key | Mapping asal | Disposisi | Catatan |
|---|---|---|---|---|
| `mst_employee_mirror` | `ux_mst_employee_mirror_nik` | `ms_employee_sync` + `vw_HREmployeeData` (DDL ✅ 28 kolom) | REBUILD | Owner: HR (Tier B read-only; write HANYA sync job); `is_resigned` (`Fkeluar`) WAJIB diekspos eksplisit (fix Edge Case 12 — legacy comment-out filter `IsActive`); `national_id` (`NoKtp`) `[LOCKED]`; TANPA maker-checker |

### Dealer master family (BE-07 §3.2 — mandat eksplisit D-08)

| Tabel target | Key | Mapping asal | Disposisi | Catatan |
|---|---|---|---|---|
| `mst_dealer` | `ux_mst_dealer_dealer_code` | `ms_dealer` (DDL ✅ 51 kolom) | MIGRATE | KTP/NPWP `[LOCKED]` zero-diff; 6 kolom file-path → split `mst_dealer_document`; `is_sub_dealer_enabled` USULAN (fix EC6 name-literal); join via `notes` TIDAK direplika (fix EC7); shape live OQ-DLRPTN-01; backup = `[ARTIFACT — discard]` |
| `mst_dealer_document` | `ux_..._dealer_code_doc_type` | 6 kolom path `ms_dealer` (DDL ✅) | MIGRATE | Transform path FTP → object-storage key; doc_type enum `SIUP\|TDP_NIB\|NPWP\|KTP\|MP_MASTER_DEALER\|SPT_ACCOUNT_BOOK` |
| `mst_dealer_personnel` | `ux_..._personnel_id` | `ms_dealer_personel` (DDL ✅ 27 kolom) | MIGRATE | `status='A'` filter eligibility pembayaran (BR-DLRPTN-1) |
| `mst_dealer_job_title` | `ux_..._job_title_id` | `ms_dealer_job_title` (DDL ✅ 8 kolom) | MIGRATE | `dealer_payment_code` `[INTENT]` |
| `mst_dealer_bank_reference` | `ux_..._dealer_code_bank_reference_id` | `ms_dealer_bank_reference` (DDL ✅ 15 kolom) | MIGRATE | `account_number`/`account_name` `[LOCKED]` payout zero-diff; **maker-checker WAJIB** (BR-BE07-05); backup = `[ARTIFACT — discard]` |
| `mst_dealer_branch_access` | `ux_..._dealer_code_branch_id` | `ms_dealer_branch_access` (DDL ✅ 8 kolom) | MIGRATE | Dealer hanya muncul di picker cabang bila row akses aktif (BR-BE07-07/BR-DEALER-1); backup = `[ARTIFACT — discard]` |

### Transaction-Type Hierarchy config (BE-07 §3.3 — owned definition)

| Tabel target | Key | Mapping asal | Disposisi | Catatan |
|---|---|---|---|---|
| `cfg_transaction_code` | `ux_..._branch_id_transaction_code` | `ms_trans_type` (DDL ✅ 9 kolom) + `ms_module_menu` | MIGRATE | OQ-PRODASSET-05 ✅; upsert (BR-BE07-19); upper-case server-side |
| `cfg_transaction_type` | `ux_..._transaction_type_code` | idem | MIGRATE | `transaction_type_code` `[LOCKED]` external-FK char-for-char (BR-PRODASSET-7); PATCH hanya `is_active` (BR-BE07-18); `mapping` disimpan eksplisit (bukan derive `substring`) |
| `cfg_hierarchy_matrix` | `ux_..._type_level_pic_branch` (`transaction_type_code`,`level`,`pic_nik`,`branch_id`) | `ms_hierarchy_transaction` (DDL ✅ 13 kolom — TABEL SAMA dengan walking table BE-03) | MIGRATE + cleansing | OQ-MASTERDATA-03 ✅: admin surface menulis LANGSUNG ke tabel yang di-walk 03 — single source, NO separate `cfg_approval_hierarchy_level`; cleansing wajib celah V4 (NIK tanpa guard) + V6 (kontiguitas level); BR-BE07-15..17 server-side |

### Master operasional lain (BE-07 §3.4)

| Tabel target | Key | Mapping asal | Disposisi | Catatan |
|---|---|---|---|---|
| `mst_approval_reason` | `ux_..._reason_id` | `ms_CAS_approval_reason` (DDL ✅ 8 kolom) | MIGRATE | `type` enum `'1'\|'2'\|'3'\|'9'` — makna & subset diekspos OQ-DLRPTN-04; consumed 03/05 |
| `mst_credit_source` | `ux_..._credit_source_id` | `ms_credit_source` (LOKAL — BR-CREDITSRC-1) | MIGRATE | Satu-satunya master ter-evidensi lokal DB acquisition; konfirmasi fisik OQ-DLRPTN-02 |
| `mst_branch_credit_source` | `ux_..._branch_id_credit_source_id` | `ms_branch_credit_source` (DDL ✅ 10 kolom) | MIGRATE | `photo_required`/`print_survey_report` scoped per branch (BR-CREDITSRC-2) |
| `mst_blacklist_override` | `id`; unique parsial (`national_id`, periode berlaku) | tabel whitelist legacy (nama/DDL OQ-ACQCAS-08) | MIGRATE (bila data aktif) | `national_id VARCHAR(16)` `[LOCKED]`; **maker-checker WAJIB + append-only audit** (AML BR-BE07-05/06); consumed 01 reason-gate RFA |
| `mst_public_holiday` | `ux_..._holiday_date` | `MsPublicHoliday` (LOKAL `FC_ACQ_MCF`, DDL 3 kolom) | MIGRATE | Satu-satunya master 07 fisik di dump `FC_ACQ_MCF`; copy kedua di `FC_MSTAPP_MCF` = OQ-EXTMASTERS-08; `holiday_name`/`holiday_date` NOT NULL (reject NULL legacy); satu-satunya ber-DELETE fisik (OQ-BE07-06) |
| `mst_general_parameter` | `ux_..._parameter` | `ms_general_parameter` (DDL ✅ 10 kolom) | MIGRATE (seed) | `is_updateable=false` → `409` (hard guard BR-BE07-23); `is_visible=false` disembunyikan listing non-admin; no create/delete via API (E34); backup = `[ARTIFACT — discard]` |
| `mst_promotion_line_text` | `id` | `ms_promotion_line_text` (DDL ✅ 8 kolom) | MIGRATE | `text`, `display_color`, `is_active` |
| `map_transaction_type_gl` | `ux_..._trx_id_class_id` (`trx_id`,`class_id`) | `GFTransactionTypeGLLink` (LOKAL `FC_ACQ_MCF`) | MIGRATE | `[LOCKED]` CoA zero-diff MUTLAK; **maker-checker WAJIB** (BR-BE07-05/24); no delete; data milik finance (posting rules OQ-MEET-03); 07 admin surface; consumed 05 + engine disbursement-subledger |
| `cfg_number_format` | `id` | `tr_auto_number` + `tr_generate_code` (LOKAL) | REBUILD | Definisi format di-seed; counter `last_number` TIDAK dimigrasi (sequence DB, seed ≥ max legacy); `code_type` vocabulary (target `CREDIT_ID`); `format_template` (jangan seed dari literal kolom `code_format='Branch_YYYY_MM_00001'` — aktual YY 2-digit); `reset_period` NONE/MONTHLY/YEARLY; **`CREDIT_ID` code_type change → maker-checker**; consumer BE-01 (mint `credit_id` STEP 8, OQ-GT-02 ✅ format `branch(5)+YY+MM+SEQ(5)`) |

### Audit (BE-07 §3.0 catatan)

| Tabel target | Key | Disposisi | Catatan |
|---|---|---|---|
| `log_master_change_request` | `id` | REBUILD (append-only) | Envelope maker-checker E37 + keputusan checker; INSERT-only |
| `log_master_audit` | `id` | REBUILD (append-only) | Before-after mutasi Tier A (BR-BE07-04); INSERT-only |

## Tier C — EXTERNAL READ-ONLY (BE-07 §3.5 — NO local table, via ACL)

Katalog `FC_MSTAPP_MCF` (310 master; read-set acquisition 171 tabel, census KB `30-data-model/external-masters-census.md §7`) **TIDAK dimodelkan sebagai tabel target** — diakses read-only via ACL (E30; BR-BE07-26). DDL ✅ dump 2026-07-22 (OQ-REF-04 ✅). Ownership owned-vs-read-only `[OPEN]` OQ-EXTMASTERS-01 (menentukan Tier C mana naik Tier A). 8 objek absen dari dump OQ-EXTMASTERS-07. Kegagalan baca = `503 LOOKUP_SOURCE_UNAVAILABLE` (BR-BE07-22).

Kelompok utama E30: (a) klasifikasi applicant 27+ (marital, nationality, identity-type, profession+high-risk, economic-sector 2-level, debtor-group self-ref ≤5, relationship 2 taxonomy, 4 korporat OJK-coded); (b) bank & pembayaran (`MsBank.PasscodeBiBca` = **[REDACTED-SECRET]** — OQ-DLRPTN-05); (c) location + `regency_id_OJK` crosswalk `[LOCKED]` (BR-LOCATION-1); (d) product/asset taxonomy (asset-kind→class→brand→series→type, item-brand, finance-type); (e) lain-lain (insurance source, service bureau, BPKB location).

## State machines (BE-07 §7)

| Resource | Field/flow | States | Guard |
|---|---|---|---|
| Tier A record (non-maker-checker) | lifecycle | `active ⇄ inactive` | No delete (BR-BE07-03); paritas legacy state machine B |
| Change-request (maker-checker) | E37 envelope | `(∅)→pending_approval→applied\|rejected\|cancelled` | checker ≠ maker (`403 SELF_APPROVAL_BLOCKED`); `reject_reason` wajib; terminal immutable |
| APP_USER | lifecycle | `active → inactive(manual) / inactive(hr_resigned)` | E2 provision (NIK ada & tidak resigned); sync resigned → auto-deactivate (BR-BE07-27); `inactive(hr_resigned)` tidak bisa reaktivasi (`409`) |
| Ladder definition | `is_active` toggle | active/inactive | Definisi (07); eksekusi routing milik 03; nonaktif mengeluarkan dari routing masa depan tanpa hapus histori |

## Sources

- `docs/prd/acquisition/BE-07-master-data-menus.md §3` (census ground truth — sumber otoritatif), `§7` (state machine)
- `docs/DB-CONVENTIONS.md` (ADR-14 — schema otoritatif)
- `docs/prd/acquisition/BE-00-OVERVIEW.md §6.3` (registry baris owner 07)

## Open Questions

- **OQ-EXTMASTERS-01/07/08** [P1/P2] — Tier C ownership + 8 objek absen + `MsPublicHoliday` dual-DB
- **OQ-DLRPTN-01/02** [P1/P2] — dealer shape live + `ms_credit_source` lokal?
- **OQ-BE07-04/05** [P2] — user single vs multi-branch; menu role-based vs position-based vs hybrid; `USER_MENU_GRANT_SPECIAL` dipertahankan?
- **OQ-MASTERDATA-01/02/03** — deactivate-only LOCKED?; celah V4/V6 cleansing; konsumsi nyata TransTypeHierarchy
- **OQ-DLRPTN-04** [P2] — makna type approval-reason `'9'`
- Lengkap di `00-index.md` roll-up
