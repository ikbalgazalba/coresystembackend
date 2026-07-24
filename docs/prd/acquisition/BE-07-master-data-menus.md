# PRD — Menu Master Data (User, Dealer, dst.) [BE]

> **Audience**: Tim Backend (BE).
> **Target stack**: **Java** `[LOCKED]` (D-12, SoW user directive 2026-07-14). Framework belum ditetapkan — **rekomendasi: Spring Boot** (USULAN, `[OPEN]` menunggu keputusan arsitektur ITEC Bank Mega per D-11, deadline arsitektur 10 Juli 2026). Transport (REST vs gRPC vs message-bus) `[OPEN]` — kontrak di dokumen ini ditulis level resource+field, path/verb ilustratif.
> **Tanggal**: 2026-07-14.
> **Sumber otoritatif**: `.mega-sdd/knowledge-base/.sp-manifests/_ACQUISITION-GROUND-TRUTH.md` (v2, alur final 16 STEP PDF 08072026), `.mega-sdd/knowledge-base/.sp-manifests/_MEETING-DECISIONS-2026-07.md` (D-01..D-12 + OQ-MEET), KB `10-domains/10-customer-applicant-master.md`, `10-domains/11-dealer-partner-master.md`, `10-domains/12-product-asset-master.md`, `50-integrations/external-masters-and-linked-servers.md`, `30-data-model/reference-entities.md`, KB FE `60-frontend/66-master-data-screens.md` (cross-check kebutuhan API dari layar) + `60-frontend/60-app-shell-auth-navigation.md` (evidence auth/user). Standar schema target: `docs/DB-CONVENTIONS.md` (lampiran WAJIB §3); payung migrasi: `docs/DATA-MIGRATION-PLAN.md` (kolom "Mapping asal" §3 = mapping matrix per tabel).
> **Status**: **MODUL BARU** (tidak ada baseline pre-meeting). Mandat modul: **D-08** — "Add: Menu Master (User, Dealer, etc)" `[VERIFIED — doc][INTENT]`. Konform ke SHARED CONTRACT DIGEST umbrella (`docs/prd/acquisition/00-OVERVIEW.md`).

Kapabilitas ini adalah **modul administrasi master data** milik rebuild acquisition: CRUD API untuk master data yang **dimiliki** acquisition (User/role assignment, Dealer family, konfigurasi Transaction-Type Hierarchy, approval reason, credit source, blacklist override, public holiday, general parameter, promotion line text, Menu tree) plus **satu lapisan read-lookup API** untuk seluruh katalog referensi yang dikonsumsi modul 01–05. Modul ini adalah jawaban langsung atas dua temuan struktural KB: (a) **legacy TIDAK punya write-path master data yang bekerja end-to-end** — hampir semua endpoint masters read-only, interface `IMasters` insert/update melempar `NotImplementedException` (`11-dealer-partner-master.md §5.4`; `10-customer-applicant-master.md §9 Edge Case 9`; OQ-CUSTMASTER-04, OQ-DLRPTN-13), dan (b) **keputusan meeting D-08** memasukkan Menu Master (minimal User & Dealer) ke SoW rebuild BE+FE. Dua keputusan governance mengunci desain user management: **Super user DIHAPUS** (D-09 `[LOCKED]`) dan **sensus role cabang = CMO, Marketing Head, Credit Analyst, Kepala Cabang, Credit (Admin)** dengan hierarki approval tergantung skala risiko (D-10 `[LOCKED]`).

Batas terpenting modul ini adalah **garis kepemilikan vs external masters**: legacy menaruh **310 tabel** `Ms*`/`ms_*` di database terpisah `FC_MSTAPP_MCF` (same-instance cross-database, bukan linked-server hop — `50-integrations/external-masters-and-linked-servers.md §1`; **DDL dump diterima 2026-07-22** — census + read-set acquisition 171 tabel di KB `30-data-model/external-masters-census.md`), dan **kepemilikan owned-vs-read-only katalog itu masih `[OPEN]` OQ-EXTMASTERS-01 (blocker keputusan, umbrella §"Master data catalog")**. PRD ini memodelkan tiga tier kepemilikan (§1.3) agar tim BE bisa membangun tanpa menunggu resolusi penuh: Tier A (owned, CRUD penuh), Tier B (synced mirror read-only), Tier C (external read-only via ACL).

Grounding: `10-domains/10-customer-applicant-master.md` (27+ lookup klasifikasi applicant, gap write-path), `10-domains/11-dealer-partner-master.md` (dealer/bank/branch/employee/location family + business rules), `10-domains/12-product-asset-master.md` (product/asset taxonomy + transaction-type & approval-hierarchy definition + menu tree), `30-data-model/reference-entities.md` (sensus master + mutability; jumlah final 310 per census dump `30-data-model/external-masters-census.md`), `50-integrations/external-masters-and-linked-servers.md` (boundary FC_MSTAPP_MCF + linked servers), `60-frontend/66-master-data-screens.md` (layar TransTypeHierarchy + pola pagination + gap validasi server-side), `_MEETING-DECISIONS-2026-07.md` (D-08, D-09, D-10, D-11, D-12).

---

## 1. Ruang Lingkup & Kepemilikan

### 1.1 Yang dimiliki (in-scope — D-08)

- **User management (role assignment, TANPA super-user)** — entitas `APP_USER`: pemetaan employee (NIK, sumber HR sync) → role sensus D-10 → scope branch/company. **BUKAN** identity store: autentikasi tetap didelegasikan ke corporate directory (LDAP) — aplikasi TIDAK menyimpan password `[VERIFIED][LOCKED]` (BR-SHELL-1, `60-frontend/60-app-shell-auth-navigation.md §7`). Legacy tidak punya skema declared-role/RBAC sama sekali (`66-master-data-screens.md §2` + OQ-ACTORS-02) — model role-grant di §3 adalah **USULAN desain baru** yang di-ground pada D-09/D-10.
- **Menu master + menu-access grant** — penerus `ms_module_menu` (menu tree) + grant akses per role (penerus pola `MsPositionMenu`/`MsPositionMenuSpecial`, `12-product-asset-master.md §11`). Field `trans_type_id_prefix` pada menu adalah **input struktural komposisi `trans_type_id`** `[VERIFIED][LOCKED]` (BR-PRODASSET-14) — CRUD menu WAJIB memproteksi prefix ini (lihat BR-BE07-14).
- **Dealer master family (CRUD penuh)** — `DEALER`, `DEALER_PERSONNEL`, `DEALER_JOB_TITLE`, `DEALER_BANK_REFERENCE`, `DEALER_BRANCH_ACCESS`, `DEALER_DOCUMENT`. Field census dari `MsDealer.cs:1-214` (`11-dealer-partner-master.md §6`); identitas legal (KTP/NPWP) `[LOCKED]` (`30-data-model/reference-entities.md §6`).
- **Konfigurasi Transaction-Type Hierarchy (definition CRUD)** — `TRANSACTION_CODE`, `TRANSACTION_TYPE`, `APPROVAL_HIERARCHY_LEVEL` + PIC picker dari employee mirror. Ini satu-satunya tempat di FE legacy tempat baris PIC/next-PIC/level/is-approver dibuat (`66-master-data-screens.md` header criticality). **Boundary**: modul ini memiliki *definisi* ladder; *walking/eksekusi* ladder saat routing komite milik **03** (BE-03 §"Kepemilikan"; `12-product-asset-master.md §8` note). Semua invariant yang di legacy hanya dienforce di browser JS WAJIB di-re-enforce server-side (fix OQ-MASTERDATA-02 — lihat BR-BE07-15..17).
- **Approval reason master** — `ms_CAS_approval_reason` (reason_id, description, type, active). Dipakai disposisi Approve/Reject/Correction/Verify di 03/05 (umbrella §"reason-code master").
- **Credit source + branch-credit-source mapping** — satu-satunya master yang ter-evidensi **lokal** di DB acquisition, bukan `FC_MSTAPP_MCF` (BR-CREDITSRC-1 `[VERIFIED]`, `11-dealer-partner-master.md §7`) → jelas owned oleh rebuild acquisition.
- **Blacklist-override / whitelist table** — dipakai reason-gate RFA (BE-01 BR-29; `20-acquisition-cas-intake` BR-ACQCAS-22) tetapi **tidak punya CRUD di legacy** (OQ-ACQCAS-08). Modul ini menyediakan CRUD-nya, dengan maker-checker (regulated adjacency — lihat §6).
- **Public holiday, general parameter, promotion line text** — `MsPublicHoliday` & `GFTransactionTypeGLLink` terbukti fisik lokal di `FC_ACQ_MCF` (`30-data-model/reference-entities.md §11`); `MsGeneralParameter` (key/value config + `IsVisible`/`IsUpdateable`) dan `MsPromotionLineText` dikonsumsi langsung layar acquisition. Catatan: `GFTransactionTypeGLLink` datanya `[LOCKED]` (mapping chart-of-accounts) — modul ini hanya menyediakan **read + audit-controlled update**, posting rules milik downstream finance (OQ-MEET-03).
- **Read-lookup API layer (Tier C)** — endpoint baca berpagination/berfilter untuk seluruh lookup referensi yang dikonsumsi 01–05 (27+ klasifikasi applicant `10-customer-applicant-master.md §6`; bank/location/payment-point/insurance-source `11-...§6`; product/asset taxonomy `12-...§4`) — read-only sampai OQ-EXTMASTERS-01 resolved.

### 1.2 Yang BUKAN miliknya (non-goal)

- **Autentikasi & session** — login LDAP, session bootstrap, branch-selection session = milik app-shell FE + auth service (KB `60-frontend/60-app-shell-auth-navigation.md`). Modul ini hanya menyediakan data authz (role/menu-grant) yang dikonsumsi layer itu. Password/credential store **TIDAK dibuat** `[LOCKED]` (BR-SHELL-1).
- **Employee master (create/update)** — HR system adalah system-of-record; `ms_employee_sync` adalah mirror one-way read-only `[VERIFIED][LOCKED]` (BR-EMPLOYEE-1, `11-dealer-partner-master.md §7`). Modul ini TIDAK membuat/mengedit employee — hanya membaca mirror untuk picker + provisioning `APP_USER`.
- **Customer master (`CUSTOMER`/`tr_CIF`)** — penulisan otoritatif milik **05-npp** (upsert saat aktivasi kontrak, D-01 Step 15; umbrella §"Penulisan tr_CIF"); dedup-by-NIK saat intake milik **01**. Modul ini hanya menyajikan *lookup klasifikasi* applicant (marital, profession, dst.), bukan record customer.
- **Eksekusi routing approval** — walking `ms_hierarchy_transaction`, resolve approver, inbox, eskalasi hari — milik **03** (BE-03 O-1). Modul ini memiliki definisi/konfigurasi saja. Ladder credit-analyst (`ms_approval_scheme/level/user`, `trans_type_id='AA00000001'`) juga dikonsumsi 02/03 — definisinya masuk backlog modul ini bila ownership katalognya jatuh ke rebuild (OQ-EXTMASTERS-01).
- **Master pricing/insurance rate family** (`MsInsurance*`, OTR/MarketPrice) — `[LOCKED]` nilai rate OJK; konsumsinya milik 04/insurance downstream. CRUD-nya TIDAK dibangun di fase ini; masuk keputusan ownership OQ-EXTMASTERS-01 + annex per-product D-07/OQ-MEET-06.
- **Dealer payment routing runtime** — BR-BANKRTE-1..3, `sp_get_mapping_dealer_transfer_balance` (GL crosswalk 4-nilai `[LOCKED]` verbatim) = konsumsi milik disbursement (STEP 16 downstream); modul ini hanya menyimpan master yang dirujuknya (dealer bank reference, bank lookup).
- **Screens FE** — layar Next.js dimiliki PRD FE-07 (D-12 split per audience).
- **Fidusia upload & Dukcapil result viewer** — dua layar "Global" pada slice FE 66 milik domain kolateral/integrasi (`31-collateral-bpkb-fidusia`, `50-integrations/dukcapil.md`), bukan master data; tidak dibawa ke modul ini.

### 1.3 Tiga tier kepemilikan master (jawaban OQ-EXTMASTERS untuk keperluan build)

| Tier | Definisi | Contoh | Write? |
|---|---|---|---|
| **A — OWNED** | Master yang dimiliki & di-CRUD modul ini (bukti lokal/di-mandat D-08) | APP_USER, MENU + grants, DEALER family, TRANSACTION_CODE/TYPE/HIERARCHY, APPROVAL_REASON, CREDIT_SOURCE (+branch mapping), BLACKLIST_OVERRIDE, PUBLIC_HOLIDAY, GENERAL_PARAMETER, PROMOTION_LINE_TEXT | CRUD penuh (maker-checker untuk subset sensitif — §6) |
| **B — SYNCED MIRROR** | Sumber eksternal system-of-record; rebuild menyimpan mirror read-only | EMPLOYEE_MIRROR (HR sync `ms_employee_sync` `[LOCKED]`), BRANCH/COMPANY (`MsCompanyBranch` — `BranchIdPassnet` `[LOCKED]` external key) | Read-only + sync job; TIDAK ada endpoint write |
| **C — EXTERNAL READ-ONLY** | Katalog `FC_MSTAPP_MCF` (310 master; read-set acquisition 171 — census KB `30-data-model/external-masters-census.md §7`) yang ownership-nya `[OPEN]` OQ-EXTMASTERS-01 | 27+ lookup klasifikasi applicant, bank, location(+OJK crosswalk `[LOCKED]`), asset/product taxonomy, payment point, insurance source | Read-only via ACL; CRUD ditunda sampai keputusan ownership |

> `[KEPUTUSAN DESAIN BARU]` Tiering ini adalah cara modul tetap buildable tanpa menjawab OQ-EXTMASTERS-01 diam-diam: Tier A dipilih hanya untuk master yang (i) terbukti lokal (BR-CREDITSRC-1; `MsPublicHoliday`/`GFTransactionTypeGLLink` fisik di `FC_ACQ_MCF`), (ii) di-mandat eksplisit D-08 (User, Dealer), atau (iii) satu-satunya admin surface-nya ada di FINCORE.WEB (TransTypeHierarchy — `66-master-data-screens.md §1`). Master lain menunggu keputusan (§11).

### 1.4 Catatan arsitektur (D-11, D-12)

Arsitektur infra final disiapkan tim **ITEC Bank Mega** (D-11) — PRD ini menyatakan asumsi (satu service master-data dengan ACL ke katalog eksternal; TIDAK mereplikasi linked-server/cross-database join sebagai pola — `external-masters-and-linked-servers.md §1/§4` menandai kopling SQL-native itu `[LOCKED]` sebagai *fakta arsitektural yang harus diputuskan sadar*, bukan pola yang dipertahankan) dan menunda topologi final ke deliverable ITEC. Bahasa BE = **Java** `[LOCKED]` (D-12); pola implementasi ditulis framework-agnostic — Spring Boot hanyalah rekomendasi (USULAN).

---

## 2. Aktor & Peran

Sensus role cabang **`[LOCKED]`** per D-10: **CMO, Marketing Head, Credit Analyst, Kepala Cabang, Credit (Admin)** — hierarki approval tergantung **skala risiko**. **Super user DIHAPUS** `[LOCKED]` (D-09): enum role modul ini TIDAK memiliki nilai super-user, dan tidak ada bypass-flag per-user; tabel legacy `ms_trans_super_user` hanya dibaca untuk migrasi/audit (BE-03 §10), tidak di-port sebagai fitur.

| Aktor | Peran di 07 | Marker |
|---|---|---|
| **Master Data Administrator (Maker)** | Membuat/mengubah record master Tier A (user, dealer, hierarchy config, reason, dsb.). Legacy TIDAK punya role admin ter-deklarasi — `[INFERRED]` dari keberadaan layar admin (`12-product-asset-master.md §2`; `66-...§2` "Configuration operator"). Pemetaan ke sensus D-10: **Credit (Admin)** untuk master operasional cabang; admin HO `[OPEN]` OQ-BE07-03. | `[INFERRED][INTENT]` + D-10 `[LOCKED]` |
| **Master Data Checker (Approver)** | Menyetujui/menolak perubahan pada master sensitif (maker-checker — §6 BR-BE07-05). Kandidat: **Kepala Cabang** (scope cabang) / role HO. Maker ≠ checker (self-approval BLOCKED, konsisten D-01 Step 11). | USULAN `[OPEN]` OQ-BE07-01 |
| **Approval-Hierarchy Administrator** | Mengonfigurasi per branch: transaction code → transaction type → ladder level (PIC, next-PIC, eskalasi, is-approver). | `[VERIFIED][INTENT]` (`12-...§2`; `66-...§2`) |
| **PIC / Approver (employee)** | Employee yang ditunjuk pada level ladder; dicari via picker yang difilter job-title codes (Credit Analyst, Area Head, Branch Manager, Regional Head, Post Head, Credit Dept Head, Area Credit Manager, Admin — `sp_get_pagination_data_pic_trans_type_hierarchy` `12-...§11`). | `[VERIFIED][INTENT]` |
| **HR System (upstream, non-human)** | System-of-record identitas employee; mirror `ms_employee_sync` di-sync one-way. | `[VERIFIED][LOCKED]` (BR-EMPLOYEE-1) |
| **Corporate Directory / LDAP (upstream)** | Verifikasi kredensial saat login (di luar modul; konteks kenapa APP_USER tanpa password). | `[VERIFIED][LOCKED]` (BR-SHELL-1) |
| **Modul 01–05 (konsumen, non-human)** | Membaca lookup + konfigurasi (dealer picker di 01, hierarchy definisi di 03, reason-code di 03/05, dst.). Read-only. | `[VERIFIED][INTENT]` |
| **Dealer / Dealer Personnel** | **Subjek** record master (bukan operator sistem); identitas legal KTP/NPWP `[LOCKED]`. | `[VERIFIED]` |

> Gap legacy yang TIDAK direplikasi: tidak ada satu pun gate role/permission pada layar master legacy selain "session ada" (`66-...§7 BR-MASTERDATA-13 [VERIFIED][OPEN]`; OQ-ACTORS-02). Rebuild WAJIB menerapkan authz eksplisit per endpoint (kolom Auth/Role di §4) — enforce app-layer, konsisten resolusi OQ-MCP-01 (umbrella).

---

## 3. Model Data

**Bagian ini adalah GROUND TRUTH schema modul master-data** — mengikuti `docs/DB-CONVENTIONS.md` (WAJIB): nama tabel target `snake_case` **singular** ber-prefix kelas (`mst_` master, `cfg_` konfigurasi engine/rule, `map_` bridge, `log_` append-only), bahasa Inggris konsisten. Nama entitas KONSEPTUAL huruf besar (mis. `APP_USER`) tetap dipakai di prosa PRD; padanan tabel fisiknya dideklarasikan per entitas + ringkasan §3.0. Tipe tech-agnostic (`identifier`, `string`, `decimal`, `integer`, `boolean`, `date`, `datetime`, `enum`) sesuai `suggested-erd.md`; tipe fisik mengikuti DB-CONVENTIONS §3 (uang `NUMERIC(18,2)`, timestamp `TIMESTAMPTZ` UTC, boolean `NOT NULL DEFAULT false`, enum kecil `VARCHAR`+`CHECK`).

Ketentuan lintas-tabel (TIDAK diulang di census per tabel):

- **Kolom wajib** (DB-CONVENTIONS §4): `created_at`, `created_by`, `updated_at`, `updated_by` di semua `mst_`/`cfg_`/`map_` — memenuhi audit trail master (BR-BE07-04). Master yang di-edit user konkuren juga membawa `version INTEGER NOT NULL DEFAULT 0` (optimistic locking).
- **PK teknis** `id BIGINT GENERATED ALWAYS AS IDENTITY`; business key legacy (mis. `dealer_code`) jadi kolom terpisah ber-unique `ux_` — BUKAN PK (DB-CONVENTIONS §2). Baris "Key:" pada census = business key/unique constraint, bukan PK teknis.
- **Maker-checker** untuk master mutable oleh user (DB-CONVENTIONS §1, baris `mst_`) — via envelope change-request E37; scope resource sensitif per BR-BE07-05/OQ-BE07-01.
- **FK declared nyata** semua relasi (legacy nyaris tanpa FK — DB-CONVENTIONS §2); gate reconciliation migrasi = 0 orphan (`DATA-MIGRATION-PLAN.md §3`).
- **Disposisi migrasi** memakai vocabulary `DATA-MIGRATION-PLAN.md §1`: **MIGRATE** / **MIGRATE-READONLY** / **DISCARD** / **REBUILD**.

### 3.0 Ringkasan tabel target & disposisi migrasi

| Entitas (PRD) | Tabel target | Mapping asal (legacy) | Disposisi | Catatan |
|---|---|---|---|---|
| `APP_USER` | **`mst_user`** | — (BARU, mandat D-08; legacy tanpa RBAC) | REBUILD | Maker-checker per konvensi; TANPA super-user (D-09 `[LOCKED]`); role census D-10 `[LOCKED]` |
| `APP_USER.branch_scope` | **`mst_user_branch_scope`** | — (BARU) | REBUILD | Child normalisasi list (OQ-BE07-04) |
| Role catalog | *(tidak ada tabel)* | — | — | Enum tertutup D-10 = `VARCHAR`+`CHECK`; `mst_role` baru dibuat BILA enum diperluas role HO (OQ-BE07-03) |
| `MENU` | **`cfg_menu`** | `ms_module_menu` (`FC_MSTAPP_MCF`, **DDL ✅ 16 kolom** — census 2026-07-22) | MIGRATE | `trans_type_id_prefix` `[LOCKED]` verbatim (DDL: `varchar(10) NULL`); rebuild role-driven (legacy per-position/employee) |
| `ROLE_MENU_GRANT` | **`cfg_menu_role_grant`** | `ms_position_menu` (**DDL ✅ 7 kolom**) | REBUILD | Re-key position-code → role D-10; bukan copy 1:1 (OQ-BE07-05) |
| `USER_MENU_GRANT_SPECIAL` | **`cfg_menu_user_grant_special`** | `ms_position_menu_special` (**DDL ✅ 7 kolom**) | `[OPEN — OQ-BE07-05]` | Dibangun HANYA bila fitur dipertahankan (risiko backdoor D-09) |
| `EMPLOYEE_MIRROR` | **`mst_employee_mirror`** | `ms_employee_sync` (**DDL ✅ 28 kolom** — shape HR: `NoPeg`,`KdJabat`,`KdCabang`,`FKeluar`) + `vw_HREmployeeData` | REBUILD | Initial load via sync HR (system-of-record), BUKAN copy legacy; read-only (Tier B) |
| `DEALER` | **`mst_dealer`** | `ms_dealer` (`FC_MSTAPP_MCF`, **DDL ✅ 51 kolom** — mapping final §3.1) | MIGRATE | Shape live tinggal OQ-DLRPTN-01; `MsDealer1`/`MsDealerBackup20221227` = `[ARTIFACT — discard]` |
| `DEALER_DOCUMENT` | **`mst_dealer_document`** | 6 kolom file-path `ms_dealer` (**DDL ✅** — `MP_master_dealer_file`…`SPT_account_book_file`, semua `varchar(255) NULL`) | MIGRATE | Transform path FTP → object-storage key (normalisasi `[ARTIFACT]` fix) |
| `DEALER_PERSONNEL` | **`mst_dealer_personnel`** | `ms_dealer_personel` (**DDL ✅ 27 kolom**) | MIGRATE | — |
| `DEALER_JOB_TITLE` | **`mst_dealer_job_title`** | `ms_dealer_job_title` (**DDL ✅ 8 kolom**) | MIGRATE | — |
| `DEALER_BANK_REFERENCE` | **`mst_dealer_bank_reference`** | `ms_dealer_bank_reference` (**DDL ✅ 15 kolom**) | MIGRATE | `[LOCKED]` payout — checksum zero-diff wajib; `MsDealerBankReferenceBackup` = `[ARTIFACT — discard]` |
| `DEALER_BRANCH_ACCESS` | **`mst_dealer_branch_access`** | `ms_dealer_branch_access` (**DDL ✅ 8 kolom**) | MIGRATE | `MsDealerBranchAccessBackup20221227` = `[ARTIFACT — discard]` |
| `TRANSACTION_CODE` | **`cfg_transaction_code`** | `ms_trans_type` (**DDL ✅ 9 kolom**) + `ms_module_menu` (**DDL ✅ 16 kolom**) — backing teridentifikasi dari body SP write | MIGRATE | OQ-PRODASSET-05 ✅ RESOLVED (§3.3) |
| `TRANSACTION_TYPE` | **`cfg_transaction_type`** | idem | MIGRATE | Kode `[LOCKED]` verbatim (BR-PRODASSET-7); prefix-4-char uniqueness dienforce SP legacy (V8 §3.3) |
| `APPROVAL_HIERARCHY_LEVEL` | **`cfg_hierarchy_matrix`** (BUKAN tabel terpisah) | `ms_hierarchy_transaction` (**DDL ✅ 13 kolom** — TABEL YANG SAMA dengan walking table BE-03) | MIGRATE + cleansing | OQ-MASTERDATA-03 ✅ RESOLVED: admin surface menulis LANGSUNG ke `ms_hierarchy_transaction` → target tunggal `cfg_hierarchy_matrix` (BE-03/DB-CONVENTIONS §7); `cfg_approval_hierarchy_level` TIDAK dibangun terpisah. Cleansing tetap wajib utk celah validasi legacy (kontiguitas level + NIK — §3.3 V4/V6) |
| `APPROVAL_REASON` | **`mst_approval_reason`** | `ms_CAS_approval_reason` (**DDL ✅ 8 kolom**) | MIGRATE | Makna `type` = OQ-DLRPTN-04 |
| `CREDIT_SOURCE` | **`mst_credit_source`** | `ms_credit_source` (LOKAL — BR-CREDITSRC-1) | MIGRATE | — |
| `BRANCH_CREDIT_SOURCE` | **`mst_branch_credit_source`** | `ms_branch_credit_source` (**DDL ✅ 10 kolom**) | MIGRATE | — |
| `BLACKLIST_OVERRIDE` | **`mst_blacklist_override`** | tabel whitelist legacy (nama/DDL `[OPEN]` OQ-ACQCAS-08) | MIGRATE (bila berisi data aktif) | Maker-checker + audit append-only (`log_`) |
| `PUBLIC_HOLIDAY` | **`mst_public_holiday`** | `MsPublicHoliday` — LOKAL `FC_ACQ_MCF`, DDL terverifikasi; **copy kedua di `FC_MSTAPP_MCF` (3 kolom, dump 2026-07-22)** | **MIGRATE** | Copy otoritatif = **OQ-EXTMASTERS-08 [P2]**; census penuh §3.4 |
| `GENERAL_PARAMETER` | **`mst_general_parameter`** | `ms_general_parameter` (**DDL ✅ 10 kolom**) | MIGRATE (seed) | `ms_general_parameter_backup` **terkonfirmasi ada di dump** = `[ARTIFACT — discard]` (data tetap diarsip, Prinsip 2) |
| `PROMOTION_LINE_TEXT` | **`mst_promotion_line_text`** | `ms_promotion_line_text` (**DDL ✅ 8 kolom**) | MIGRATE | — |
| `GL_TRANSACTION_TYPE_LINK` | **`map_transaction_type_gl`** | `GFTransactionTypeGLLink` — **LOKAL `FC_ACQ_MCF`** | MIGRATE | `[LOCKED]` CoA — checksum zero-diff MUTLAK; data milik finance, admin surface di 07 |
| *(BARU)* numbering config | **`cfg_number_format`** | `tr_auto_number` + `tr_generate_code` (LOKAL) | REBUILD | Definisi format di-seed; counter TIDAK dimigrasi (sequence DB) — koordinat BE-01 `credit_id`; census §3.4 |
| Katalog eksternal 310 `Ms*` | *(TIDAK dimodelkan — Tier C)* | `FC_MSTAPP_MCF` | — | Read-only via ACL; **DDL ✅ census 2026-07-22** (read-set acquisition 171 tabel); assignment per-Tier menunggu ownership (sisa OQ-EXTMASTERS-01) — lihat §3.5 |

> Tabel `log_` pendamping modul ini (append-only, DB-CONVENTIONS §1): `log_master_change_request` (envelope maker-checker E37 + keputusan checker) dan `log_master_audit` (before-after mutasi Tier A — BR-BE07-04). Keduanya INSERT-only, `created_at`/`created_by` saja.

### 3.1 User management & menu (Tier A — owned)

**`APP_USER`** → tabel target **`mst_user`** (+ child **`mst_user_branch_scope`**: `user_id` FK, `branch_id` FK — normalisasi `branch_scope` list; single vs multi = OQ-BE07-04) — owner: 07. USULAN desain baru (grounded D-08/D-09/D-10 + gap "no RBAC" legacy). **Mapping asal: TIDAK ADA** (master BARU — legacy tanpa skema user/role). **Disposisi: REBUILD** — user di-provision baru saat cutover; kandidat seed dari `mst_employee_mirror` + grant position legacy adalah keputusan operasional cutover, bukan migrasi 1:1. Maker-checker sesuai konvensi master mutable (DB-CONVENTIONS §1; scope checker = OQ-BE07-01). Key: unique `ux_mst_user_employee_nik`.

| Field | Tipe | Catatan / Marker |
|---|---|---|
| `id` | identifier PK | — |
| `employee_nik` | string, unique, FK logis → `EMPLOYEE_MIRROR.nik` | Identitas = employee HR; TIDAK boleh membuat user untuk NIK yang tidak ada di mirror (BR-BE07-02). Legacy: semua actor internal ber-key NIK (`11-...§2`) |
| `role` | enum `CMO \| MARKETING_HEAD \| CREDIT_ANALYST \| KEPALA_CABANG \| CREDIT_ADMIN` | **`[LOCKED]` D-10** — sensus tertutup; TIDAK ada nilai `SUPER_USER` (**`[LOCKED]` D-09**). Penambahan role HO (mis. Compliance/AML reviewer, master-data checker HO) `[OPEN]` OQ-BE07-03 |
| `company_id` | identifier FK → `COMPANY` | Scope legal entity (MAF/MCF — `PT='2'/'3'` legacy, arti `[OPEN]` OQ-DLRPTN-10) |
| `branch_scope` | list<identifier> FK → `BRANCH` | Cabang tempat user beroperasi. Legacy: visibilitas branch employee-scoped (`sp_get_branch` company+NIK — `11-...§4`). Single vs multi-branch `[OPEN]` OQ-BE07-04 |
| `is_active` | boolean | Deactivate-only lifecycle (§7) |
| `deactivation_reason` | enum `manual \| hr_resigned` | Auto-deactivate saat HR mirror menandai resign (fix Edge Case 12 `11-...§9`: status resign WAJIB eksplisit) |
| `activation_date`, `deactivation_date` | date | — |
| *(TIDAK ADA)* `password`, `is_super_user` | — | Password `[LOCKED]` didelegasikan LDAP (BR-SHELL-1); super-user `[LOCKED]` dihapus (D-09) |

> Catatan konvensi: `role` disimpan sebagai `VARCHAR` + `CHECK` (enum kecil stabil — DB-CONVENTIONS §3); **tabel `mst_role` TIDAK dibuat** selama enum tertutup D-10 `[LOCKED]`. Bila OQ-BE07-03 memutuskan perluasan role HO ber-governance, enum diangkat jadi katalog `mst_role` — jangan menambah nilai diam-diam.

**`ROLE_MENU_GRANT`** → tabel target **`cfg_menu_role_grant`** — owner: 07. USULAN (penerus pola position-based `MsPositionMenu`, `12-...§11`). **Mapping asal**: `MsPositionMenu` (`FC_MSTAPP_MCF`, DDL pending OQ-REF-04). **Disposisi: REBUILD** — grant di-re-key dari position-code (`kdjabat`) ke role D-10; TIDAK bisa MIGRATE 1:1 (model akses berubah — OQ-BE07-05); matriks konversi position→role disusun bareng bisnis saat cutover. Key: `ux_cfg_menu_role_grant_role_menu_id` (`role`, `menu_id`).

| Field | Tipe | Catatan / Marker |
|---|---|---|
| `role` | enum (set D-10) | Grant per role, BUKAN per position-code. **Departure dari legacy** yang menurunkan akses dari position code `kdjabat` (BR-EMPLOYEE-2 `[VERIFIED][INTENT]`, `sp_get_user_employee_view`) — outcome "ganti jabatan → akses ikut" dipertahankan lewat re-provisioning role saat sync HR; keputusan final role-based vs position-based `[OPEN]` OQ-BE07-05 |
| `menu_id` | identifier FK → `MENU` | — |
| `is_view_only` | boolean | Paritas flag view-only legacy (`MsPositionMenu`/`MsPositionMenuSpecial` — `12-...§11`) |
| `is_active` | boolean | — |

**`USER_MENU_GRANT_SPECIAL`** → tabel target **`cfg_menu_user_grant_special`** — owner: 07. USULAN opsional (paritas `MsPositionMenuSpecial`: grant per-employee di luar role default). **Mapping asal**: `MsPositionMenuSpecial` (eksternal, DDL pending). **Disposisi: `[OPEN — OQ-BE07-05]`** — tabel dibangun HANYA bila fitur dipertahankan; bila tidak, grant special legacy = DISCARD (register per item). Key: (`employee_nik`, `menu_id`). Field sama dengan `cfg_menu_role_grant` + `granted_by`, `granted_reason`. Risiko jadi backdoor super-user terselubung — bila dipertahankan, WAJIB ter-audit dan tidak boleh menggabungkan grant yang setara super-user (D-09).

**`MENU`** → tabel target **`cfg_menu`** — owner: 07. Penerus `ms_module_menu` (`12-...§11 MsModuleMenu.cs:8-74`); **rebuild role-driven** (akses via `cfg_menu_role_grant`), menggantikan model per-position/per-employee legacy. **Mapping asal**: `ms_module_menu` (`FC_MSTAPP_MCF`, DDL pending OQ-REF-04). **Disposisi: MIGRATE** — tree + `trans_type_id_prefix` dibawa **verbatim** (`[LOCKED]` — checksum zero-diff pada kolom prefix, `DATA-MIGRATION-PLAN.md §3`); entri layar mati (`/CRUD` demo, Dukcapil/Fidusia bila OQ-MASTERDATA-07 memutus discard) di-reject + register. Catatan konvensi: kelas `cfg_` dipakai karena menu tree adalah konfigurasi navigasi/authz, dengan lifecycle `is_active` (bukan `effective_from/to` — menu bukan rule ber-tanggal-efektif; deviasi dicatat sadar). Key: `id`.

| Field | Tipe | Catatan / Marker |
|---|---|---|
| `id` | identifier PK | — |
| `parent_id` | identifier FK self, nullable | Tree (menu berhierarki — `sp_get_hirarki_menu_by_user`, body eksternal `[OPEN]` `12-...§11`) |
| `module` | string | Pengelompokan modul |
| `name`, `route` | string | Label + target layar (route FE Next.js dipetakan di FE-07) |
| `trans_type_id_prefix` | string(2) | **`[LOCKED]`** — input struktural komposisi `trans_type_id` (BR-PRODASSET-14; umbrella §"Komposisi"): reorganisasi menu yang mengubah prefix diam-diam akan mengubah routing approval. Mutasi field ini dibatasi + wajib maker-checker (BR-BE07-14) |
| `display_order` | integer | — |
| `is_active` | boolean | — |

**`EMPLOYEE_MIRROR`** → tabel target **`mst_employee_mirror`** — owner: HR (Tier B, read-only; write HANYA oleh sync job). **Mapping asal**: `ms_employee_sync` + `vw_HREmployeeData` (`11-...§6`; `30-data-model/reference-entities.md §1`). **Disposisi: REBUILD** — initial load via sync langsung dari HR system-of-record (BR-EMPLOYEE-1 `[LOCKED]`), BUKAN copy tabel legacy; mekanisme sync = OQ-BE07-02. TANPA maker-checker (bukan master mutable user).

| Field | Tipe | Catatan / Marker |
|---|---|---|
| `nik` (`NoPeg`) | string, unique (`ux_mst_employee_mirror_nik`) | Employee number — business key seluruh actor internal `[LOCKED]`; PK teknis tetap `id` (DB-CONVENTIONS §2) |
| `name` (`Nama`) | string | — |
| `branch_id` (`KdCabang`), `position_id` (`KdJabat`) | string | Position feeds akses legacy (BR-EMPLOYEE-2) |
| `national_id` (`NoKtp`) | string | `[LOCKED]` (identitas regulasi; juga dipakai cross-check blacklist-employee BR-CUSTMASTER-13) |
| `is_resigned` (`Fkeluar`), `employee_status` (`Stpegawai`) | boolean/string | **WAJIB diekspos eksplisit** — legacy `vw_HREmployeeData` meng-comment-out filter `IsActive` sehingga caller tak bisa membedakan resigned vs not-found vs error (Edge Case 12 `[ARTIFACT]` do-not-replicate) |
| `join_date` (`Tglmasuk`), `exit_date` (`Tglkeluar`) | date | — |

### 3.2 Dealer master family (Tier A — owned; mandat eksplisit D-08)

**`DEALER`** → tabel target **`mst_dealer`** — owner: 07. **Mapping asal**: `ms_dealer` (`FC_MSTAPP_MCF`, **DDL ✅ 51 kolom terverifikasi — dump 2026-07-22**, census KB `30-data-model/external-masters-census.md`; cross-ref model `MsDealer.cs:1-214`, `11-...§6`). **Disposisi: MIGRATE** — kolom identitas legal (`KTP_no`/`KTP_name`/`NPWP_no`) `[LOCKED]` checksum zero-diff; 6 kolom file-path (`MP_master_dealer_file`, `SIUP_file`, `TDP_NIB_file`, `NPWP_file`, `KTP_owner_file`, `SPT_account_book_file` — semua `varchar(255) NULL`) di-split ke `mst_dealer_document`; join-key `notes` (**DDL: `varchar(20) NULL`** — panjang 20 mengonfirmasi pemakaian code-like, bukan catatan) TIDAK dibawa sebagai relasi (Edge Case 7). Key: unique `ux_mst_dealer_dealer_code` (business key; DDL: `dealer_code varchar(10) NOT NULL`; PK teknis `id`).

| Field | Tipe | Catatan / Marker |
|---|---|---|
| `dealer_code` | identifier, business key unique (`ux_mst_dealer_dealer_code`) | Legacy `DealerCode` (PK-like); PK teknis tetap `id` (DB-CONVENTIONS §2) |
| `dealer_name` | string | — |
| `is_authorized_dealer` | boolean | `[INTENT]` status otorisasi |
| `is_selling_new_product_only` | boolean | Dipakai carve-out pencarian used-car (BR-DEALER-2: nilai `0` = jual keduanya → selalu muncul di pencarian used-car) `[VERIFIED][INTENT]` |
| `is_used_car` | boolean | — |
| `parent_dealer_code`, `group_code`, `main_dealer_code` | identifier nullable | Linkage parent/group/main-dealer. **Fix wajib**: legacy join `ms_map_dealer_code.main_dealer_code` ke kolom free-text `ms_dealer.notes` (Edge Case 7 `[ARTIFACT]` do-not-replicate) → rebuild pakai FK bertipe eksplisit |
| `is_sub_dealer_enabled` | boolean | **USULAN — flag eksplisit** menggantikan BR-DEALER-3 legacy (`[ARTIFACT]` discard: match nama literal `'%PT Lucas Digital Indonesia%'` di `sp_validation_sub_dealer`) |
| `contact_person`, `contact_job_title_id` | string / FK | — |
| `ktp_no`, `ktp_name` | string | **`[LOCKED]`** identitas regulasi pemilik |
| `npwp_no` | string | **`[LOCKED]`** (panjang ter-regulasi, paritas BR-CUSTMASTER-5) |
| `address`, `location_id` | string / FK → LOCATION (Tier C) | — |
| `mou_no`, `mou_date` | string/date | Referensi perjanjian kerja sama |
| `rate_refund` | decimal | Term refund-rate |
| `status` | enum `active \| inactive` | Legacy `StatusDealer` + `ActivationDate`/`DeactivationDate` |
| `activation_date`, `deactivation_date` | date | — |
| `notes` | string | **Murni free-text** — tidak lagi jadi join key (fix Edge Case 7); DDL legacy `varchar(20)` |
| `owner_name` | string | DDL ✅ `varchar(60) NOT NULL` — pemilik dealer (pasangan `KTP_owner_file`) |
| `ktp_address`, `npwp_name`, `npwp_address`, `npwp_type` | string | DDL ✅ — alamat identitas legal; ikut zero-diff `[LOCKED]` keluarga NPWP/KTP |
| `contact_job_title_id` | FK | DDL legacy `contact_person_job_title int NOT NULL` |
| `phone`, `mobile_phone`, `fax`, `email` | string | DDL ✅ (`telephone_number`/`mobile_phone_number`/`fax`/`email varchar(500)`) |
| `product_asset_kind_id`, `item_brand` (`item_merk`), `asset_brand_id` | string/FK | DDL ✅ NOT NULL (kecuali `asset_brand_id NULL`) — scoping produk/merk dealer; FK target ke taxonomy Tier C |
| `is_bank_charges`, `is_payment_before_due`, `is_prepayment`, `prepayment` | boolean/decimal | DDL ✅ — term pembayaran dealer (`prepayment numeric(18,0)`) |
| `pkp_cumulative` | decimal | DDL ✅ `numeric(21,2)` — akumulasi PKP (pajak) |
| `status_rate_refund` | string(1) | DDL ✅ — status berlaku `rate_refund` |
| `is_sync` | boolean | DDL ✅ — flag sync legacy; **TIDAK dibawa ke target** (mekanisme sync baru), data tetap diarsip (Prinsip 2) |

> **`[OPEN]` OQ-DLRPTN-01 (P1)**: tiga shape dealer coexist di legacy (`MsDealer`, `MsDealer1`, `MsDealerBackup20221227` — Edge Case 13). Field census di atas memakai `MsDealer` sebagai kandidat live; field ekstra `MsDealer1` (`Phone/Phone2/Fax/EmailGroup/IsDefaultMokas`) ditunda sampai OQ resolved. Backup snapshots (`MsDealerBackup20221227`, `MsDealerBranchAccessBackup20221227`, `MsDealerBankReferenceBackup`) = **`[ARTIFACT — discard]`**: dated backup, bukan schema hidup (`30-...§6`) — TIDAK dibawa; register + konfirmasi zero-caller per `DATA-MIGRATION-PLAN.md §1`.

**`DEALER_DOCUMENT`** → tabel target **`mst_dealer_document`** — owner: 07. USULAN normalisasi: legacy menyimpan 6 kolom file-path scan (SIUP, TDP/NIB, NPWP, KTP, form MP-master-dealer, SPT) langsung di `MsDealer` (`30-...§6` `[ARTIFACT]` "rebuild-normalize into a document/attachment entity"). **Mapping asal**: 6 kolom path `MsDealer` → 1 row per dokumen. **Disposisi: MIGRATE** — transform path FTP → object-storage key (DB-CONVENTIONS §3 baris dokumen); path yang file-nya tak ditemukan saat migrasi = reject + register. Key: `ux_mst_dealer_document_dealer_code_doc_type` (`dealer_code`, `doc_type`).

| Field | Tipe | Catatan |
|---|---|---|
| `dealer_code` | FK | — |
| `doc_type` | enum `SIUP \| TDP_NIB \| NPWP \| KTP \| MP_MASTER_DEALER \| SPT_ACCOUNT_BOOK` | Vocabulary dari 6 kolom legacy |
| `file_ref` | string | Referensi object storage (bukan path FTP legacy) |
| `uploaded_by`, `uploaded_at` | string/datetime | — |

**`DEALER_PERSONNEL`** → tabel target **`mst_dealer_personnel`** — owner: 07. **Mapping asal**: `ms_dealer_personel` (**DDL ✅ 27 kolom — dump 2026-07-22**, census KB; cross-ref model `11-...§6` — level naik `[INFERRED]`→`[VERIFIED]`). **Disposisi: MIGRATE** (mapping final per kolom di-drill saat detail-design dari census). Key: unique `ux_mst_dealer_personnel_personnel_id`.

| Field | Tipe | Catatan / Marker |
|---|---|---|
| `personnel_id` | identifier, business key unique (`ux_mst_dealer_personnel_personnel_id`) | PK teknis tetap `id` (DB-CONVENTIONS §2) |
| `dealer_code` | FK → DEALER | — |
| `name`, `birth_place`, `birth_date` | string/date | — |
| `job_title_id` | FK → DEALER_JOB_TITLE | — |
| `address`, `location_id`, `phone`, `email` | string | — |
| `status` | enum `A(active) \| inactive` | Legacy `PersonnelStatus='A'` dipakai filter eligibility pembayaran (BR-DLRPTN-1) `[VERIFIED][INTENT]` |
| `bank_reference_id` | FK → DEALER_BANK_REFERENCE nullable | Routing pembayaran dealer-side |
| `tax_fields` | string | Paritas field pajak legacy |

**`DEALER_JOB_TITLE`** → tabel target **`mst_dealer_job_title`** — owner: 07. **Mapping asal**: `ms_dealer_job_title` (**DDL ✅ 8 kolom — dump 2026-07-22**; cross-ref `11-...§6`). **Disposisi: MIGRATE**. Key: unique `ux_mst_dealer_job_title_job_title_id`. Field: `description` (string), `dealer_payment_code` (string — mengikat job title ke payment-routing code `[INTENT]`), `is_active`.

**`DEALER_BANK_REFERENCE`** → tabel target **`mst_dealer_bank_reference`** — owner: 07. **Mapping asal**: `ms_dealer_bank_reference` (**DDL ✅ 15 kolom — dump 2026-07-22**; cross-ref `11-...§6`). **Disposisi: MIGRATE** — `account_number`/`account_name` `[LOCKED]` payout: checksum zero-diff MUTLAK (`DATA-MIGRATION-PLAN.md §3`). **`[LOCKED]` konten finansial** (identifier rekening feeds disbursement — BR-DLRPTN-2; `30-...§6`). **Maker-checker WAJIB** (BR-BE07-05). Key: `ux_mst_dealer_bank_reference_dealer_code_bank_reference_id` (`dealer_code`, `bank_reference_id`).

| Field | Tipe | Catatan / Marker |
|---|---|---|
| `dealer_code` | FK | — |
| `bank_reference_id` | identifier | — |
| `account_type`, `account_description` | string | — |
| `bank_id` | FK → BANK (Tier C) | — |
| `account_number`, `account_name` | string | `[LOCKED]` payout target |
| `bank_charges_flag` | boolean | — |
| `status` | enum `A \| inactive` | Filter eligibility (BR-DLRPTN-1: inactive di titik mana pun rantai job-title→personnel→bank-ref→bank mengeluarkan orang dari set eligible) |
| `activation_date`, `deactivation_date` | date | — |

**`DEALER_BRANCH_ACCESS`** → tabel target **`mst_dealer_branch_access`** — owner: 07. **Mapping asal**: `ms_dealer_branch_access` (**DDL ✅ 8 kolom — dump 2026-07-22**; cross-ref `11-...§11`). **Disposisi: MIGRATE**. Key: `ux_mst_dealer_branch_access_dealer_code_branch_id` (`dealer_code`, `branch_id`). Field: `is_active`. Aturan: dealer hanya muncul di picker cabang bila punya row akses aktif (BR-DEALER-1 `[VERIFIED][INTENT]`).

### 3.3 Konfigurasi Transaction-Type Hierarchy (Tier A — owned definition)

Tabel target: **`cfg_transaction_code`**, **`cfg_transaction_type`** (kelas `cfg_` — konfigurasi rule routing; perubahan = data, bukan deploy — DB-CONVENTIONS §1). Shape field dari `FINCORE.LIBRARY.DTO/Model/TransTypeHierarchy/*.cs` (`12-...§11`) + layar `66-master-data-screens.md §4`, **kini terkonfirmasi DDL + body SP write dari dump `FC_MSTAPP_MCF` 2026-07-22** (census KB `30-data-model/external-masters-census.md §5`).

**Mapping asal terverifikasi** [VERIFIED]: backing fisik = `ms_trans_type` (9 kolom — `trans_type_id varchar(10)`, `menu_id`, `mapping_risk_category_id`), `ms_module_menu` (16 kolom — `trans_type_id_prefix`), `ms_hierarchy_transaction` (13 kolom — ladder rows). **Disposisi: MIGRATE + cleansing wajib** (celah validasi legacy di V4/V6 bawah).

**✅ OQ-MASTERDATA-03 RESOLVED (evidence 2026-07-22)**: body `sp_insert/update_hirarki_approval_transaksi_trans_type_hierarchy` menulis **LANGSUNG ke `ms_hierarchy_transaction`** — tabel yang SAMA yang di-walk engine routing BE-03. Admin surface = pengisi `cfg_hierarchy_matrix`; **TIDAK ada** tabel `cfg_approval_hierarchy_level` terpisah — satu sumber routing (target: `cfg_hierarchy_matrix` per DB-CONVENTIONS §7; field design §"APPROVAL_HIERARCHY_LEVEL" di bawah berlaku sebagai shape admin-write ke tabel itu).

**✅ OQ-PRODASSET-05 RESOLVED (evidence 2026-07-22)** — aturan validasi write-side legacy dari body SP (detail + kutipan: census KB §5):
- **V1** uniqueness (`employee_id`,`trans_type_id`,`level`,`branch_id`); **V2** satu PIC tidak boleh 2× aktif per (`trans_type_id`,`branch_id`); **V3** invarian single-approver-di-puncak (insert di bawah max level → `is_approver` wajib false; insert saat approver sudah ada → ditolak).
- **V4 (celah)** `position_id`/`spv_position_id` di-enrich dari `ms_employee_sync` TANPA guard — NIK tak dikenal tetap ter-insert (referential check TIDAK ada) → cleansing wajib.
- **V5** uniqueness per (`branch`,`trans_type`,`level`) tanpa memandang employee **di-comment-out** — dua PIC beda di level sama DIBOLEHKAN legacy.
- **V6 (celah)** kontiguitas level TIDAK divalidasi (gap level lolos) → cleansing wajib.
- **V7** `sp_update_kode_...`: uniqueness (`menu_title`,`trans_type_id_prefix`) + tolak kode yang sudah dipakai `ms_trans_type` (LIKE-match); **V8** `sp_insert_tipe_...`: uniqueness prefix 4-char `trans_type_id`; **V9** kontrak error = result-set shape `log_error` (bukan RAISERROR) + CATCH menulis `dbo.log_error` — rebuild ganti dengan error terstruktur API (jangan tiru kontrak result-set).
- **Dampak OQ-MASTERDATA-02**: premis "validasi hanya JS browser" ter-KOREKSI SEBAGIAN — backend legacy MEMVALIDASI V1–V3/V7–V8 di SP; celah nyata tinggal V4+V6. BR-BE07-15..17 kini `[VERIFIED]` dari body SP (bukan lagi re-derivation JS); cleansing import difokuskan ke pelanggaran V4/V6.

**`TRANSACTION_CODE`** → **`cfg_transaction_code`** — Key: `ux_cfg_transaction_code_branch_id_transaction_code` (`branch_id`, `transaction_code`).

| Field | Tipe | Catatan / Marker |
|---|---|---|
| `branch_id` | FK → BRANCH | Konfigurasi per branch (`12-...§5.10`) |
| `transaction_code` (`kodeTransaksi`) | string | Upper-case (paritas FE `keyup` handler — dinormalisasi server-side) |
| `form_requester` (`formRequester`) | string | Dropdown requester (lookup branch-scoped `GetDataRequester`) |
| `form_approval` (`formApproval`) | string | Legacy "[Generated By System]" — dibiarkan system-generated |

**`TRANSACTION_TYPE`** → **`cfg_transaction_type`** — Key: unique `ux_cfg_transaction_type_transaction_type_code`.

| Field | Tipe | Catatan / Marker |
|---|---|---|
| `transaction_type_code` (`kodeTipeTransaksi`) | string | **`[LOCKED]` format**: identifier ini di-match char-for-char oleh routing komite (BR-PRODASSET-7; BE-03 BR-AC-1) — perubahan bentuk memutus routing |
| `description` (`deskripsi`) | string | Immutable pasca-create kecuali status (BR-MASTERDATA-4 `[VERIFIED][INTENT]`) |
| `mapping` | string | FK logis ke TRANSACTION_CODE. **WAJIB disimpan eksplisit** — legacy Edit form men-derive dari `substring(0,2)` kode (BR-MASTERDATA-5 `[INFERRED]`, konvensi belum terjamin — OQ-MASTERDATA-08): rebuild TIDAK men-derive |
| `is_active` | boolean | Satu-satunya field mutable saat edit (BR-MASTERDATA-4) |

**`APPROVAL_HIERARCHY_LEVEL`** → **`cfg_hierarchy_matrix`** (OQ-MASTERDATA-03 ✅ — satu tabel dengan walking table BE-03; shape admin-write di bawah). Key: `ux_cfg_hierarchy_matrix_type_level_pic_branch` (`transaction_type_code`, `level`, `pic_nik`, `branch_id` — paritas V1 SP legacy).

| Field | Tipe | Catatan / Marker |
|---|---|---|
| `transaction_type_code` | FK | — |
| `level` | integer | Ladder level (lookup `GetDataLevel`) |
| `pic_nik`, `pic_name` | string | Assigned PIC (dari EMPLOYEE_MIRROR via picker; filter job-title `12-...§11`) |
| `next_pic_nik`, `next_pic_name` | string nullable | **WAJIB bila `is_approver=false`; WAJIB kosong bila `is_approver=true`** (BR-MASTERDATA-2/3 `[VERIFIED][INTENT]` — kini server-side) |
| `is_approver` | boolean | Level terminal (decision-gate akhir). **Level 1 TIDAK boleh `is_approver=true`** (BR-MASTERDATA-1 `[VERIFIED][INTENT]`) |
| `status_approver` | string | Legacy hidden force-set `"Normal Approver"` (`[ARTIFACT]` vestigial — evaluasi retire; bila dipertahankan, jadikan enum eksplisit) |
| `escalation_days` (`notifikasiHari`) | integer | Legacy hidden force-set `"1"` (`[ARTIFACT]` vestigial di layar; konsep eskalasi hari sendiri `[VERIFIED][LOCKED]` pada definisi ladder — BR-PRODASSET-12) |
| `is_active` | boolean | — |

### 3.4 Master operasional lain (Tier A — owned)

**`APPROVAL_REASON`** → tabel target **`mst_approval_reason`** — **Mapping asal**: `ms_CAS_approval_reason` (**DDL ✅ 8 kolom — dump 2026-07-22**; cross-ref `11-...§6`). **Disposisi: MIGRATE** — nilai `type` dibawa verbatim; makna & subset yang diekspos = OQ-DLRPTN-04. Key: unique `ux_mst_approval_reason_reason_id`. Field: `description` (string), `type` (enum string `'1'|'2'|'3'|'9'` — makna & apakah `'9'` diekspos `[OPEN]` OQ-DLRPTN-04; dua path retrieval legacy tidak konsisten, BR-APPROVAL-REASON-1 `[VERIFIED][?]`), `is_active`.

**`CREDIT_SOURCE`** → tabel target **`mst_credit_source`** — **Mapping asal**: `ms_credit_source` (LOKAL DB acquisition, BR-CREDITSRC-1 `[VERIFIED]`; konfirmasi fisik = OQ-DLRPTN-02). **Disposisi: MIGRATE**. Key: unique `ux_mst_credit_source_credit_source_id`. Field: `description`, `is_active`.

**`BRANCH_CREDIT_SOURCE`** → tabel target **`mst_branch_credit_source`** — **Mapping asal**: `ms_branch_credit_source` (**DDL ✅ 10 kolom — dump 2026-07-22**; cross-ref `11-...§6`). **Disposisi: MIGRATE**. Key: `ux_mst_branch_credit_source_branch_id_credit_source_id` (`branch_id`, `credit_source_id`). Field: `photo_required` (boolean), `print_survey_report` (boolean), `is_active` — availability & flag operasional per branch, bukan global (BR-CREDITSRC-2 `[VERIFIED][INTENT]`).

**`BLACKLIST_OVERRIDE`** → tabel target **`mst_blacklist_override`** — USULAN CRUD baru (tabel ada, CRUD tidak — BE-01 BR-29; OQ-ACQCAS-08). **Mapping asal**: tabel whitelist legacy (nama/DDL `[OPEN]` OQ-ACQCAS-08 — identifikasi bersama BE-01). **Disposisi: MIGRATE bila berisi data override aktif** (row expired = MIGRATE-READONLY ke arsip audit); mutasi pasca-cutover via `log_master_change_request`. Key: `id`; unique parsial per (`national_id`, periode berlaku). Field: `national_id` (string `VARCHAR(16)` `[LOCKED]` key screening), `reason_code` (enum 5-reason car — BE-01 BR-21), `justification` (string wajib), `valid_from`, `valid_until` (date), `is_active`. **Maker-checker WAJIB + append-only audit** (regulated adjacency AML — BR-BE07-05/06).

**`PUBLIC_HOLIDAY`** → tabel target **`mst_public_holiday`** — **satu-satunya master milik modul ini yang fisik ada di dump `FC_ACQ_MCF`**. **Mapping asal**: `MsPublicHoliday` (LOKAL — `FC_ACQ_MCF 2.sql:7288-7302`; DDL 3 kolom terverifikasi dari dump: `ID int IDENTITY`, `Name varchar(50) NULL`, `Date date NULL`). **Disposisi: MIGRATE** — mapping kolom eksplisit di bawah. Dipakai kalkulasi due-date/eskalasi lintas hari libur (`30-...§11`). Hard-delete vs deactivate-only = OQ-BE07-06 (E33).

| Kolom target | Tipe target | Null | Mapping asal (`MsPublicHoliday`) | Marker | Catatan |
|---|---|---|---|---|---|
| `id` | `BIGINT` identity PK | NOT NULL | `ID` (`int IDENTITY`) | `[VERIFIED]` | PK teknis baru; nilai identity legacy TIDAK dipertahankan (tidak dirujuk siapa pun) |
| `holiday_name` | `VARCHAR(50)` | NOT NULL | `Name` (`varchar(50) NULL`) | `[VERIFIED][INTENT]` | Legacy nullable → target NOT NULL; row legacy ber-`Name` NULL = reject + register (`DATA-MIGRATION-PLAN.md §3`) |
| `holiday_date` | `DATE` | NOT NULL, unique (`ux_mst_public_holiday_holiday_date`) | `Date` (`date NULL`) | `[VERIFIED][INTENT]` | Idem NULL-reject; unique mencegah double-entry tanggal sama (legacy tanpa constraint) |
| *(audit + `version`)* | per DB-CONVENTIONS §4 | NOT NULL | — (legacy TANPA kolom audit) | — | Diisi `MIGRATION` sebagai actor saat load |

**`GENERAL_PARAMETER`** → tabel target **`mst_general_parameter`** — **Mapping asal**: `ms_general_parameter` (**DDL ✅ 10 kolom — dump 2026-07-22**; cross-ref `30-...§7`; `ms_general_parameter_backup` terkonfirmasi di dump = `[ARTIFACT — discard]`, data tetap diarsip). **Disposisi: MIGRATE** (berfungsi sebagai seed; pasca-cutover tanpa create/delete via API — E34). Key: unique `ux_mst_general_parameter_parameter`. Field: `value`, `unit`, `description`, `is_visible` (boolean), `is_updateable` (boolean — parameter `is_updateable=false` menolak update via API, hard guard).

**`PROMOTION_LINE_TEXT`** → tabel target **`mst_promotion_line_text`** — **Mapping asal**: `ms_promotion_line_text` (**DDL ✅ 8 kolom — dump 2026-07-22**; cross-ref `12-...§11`). **Disposisi: MIGRATE**. Key: `id`. Field: `text`, `display_color`, `is_active`.

**`GL_TRANSACTION_TYPE_LINK`** → tabel target **`map_transaction_type_gl`** (kelas `map_` — bridge kode transaksi ↔ chart-of-accounts GL, kunci komposit sumber↔target per DB-CONVENTIONS §1) — **Mapping asal**: `GFTransactionTypeGLLink` (LOKAL — `FC_ACQ_MCF 2.sql:7245-7259`, `30-...§11`). **Disposisi: MIGRATE** — **`[LOCKED]`** mapping chart-of-accounts: checksum zero-diff MUTLAK atas seluruh baris (`DATA-MIGRATION-PLAN.md §3`). Key: `ux_map_transaction_type_gl_trx_id_class_id` (`trx_id`, `class_id`). Field: `gl_account_no`. Endpoint modul ini: read + update ter-audit + maker-checker; TIDAK ada delete. Data dimiliki finance (posting rules downstream `[OPEN]` OQ-MEET-03); 07 hanya admin surface — konsumen read-only: 05 + engine disbursement-subledger (BE-05 §3.2.1; registry BE-00 §6.3).

**`NUMBER_FORMAT`** *(BARU — infra numbering)* → tabel target **`cfg_number_format`** — per DB-CONVENTIONS §7: `tr_auto_number`/`tr_generate_code` → **sequence DB + `cfg_number_format`**. Owner definisi & admin surface: 07; **konsumen utama: BE-01** — minting `credit_id` STEP 8 ("unik secara nasional"; `credit_id` = business key `[LOCKED]` lintas modul — koordinat BE-01/OQ-GT-02 **RESOLVED — evidence (2026-07-14)**: format legacy `branch_id(5)+YY(2)+MM(2)+SEQ(5 zero-pad)` = 14 char, generator `sp_get_auto_number` `SP/FC_ACQ_MCF/dbo.sp_get_auto_number.StoredProcedure.sql:54-60`, padding `fc_get_sequence_number` `FC_ACQ_MCF 2.sql:282-299`; spec penuh BE-01 §3.1.13). **Mapping asal**: `tr_auto_number` (LOKAL; PK `prefix`+`company_id`+`branch_id`+`period`, kolom `last_number`) + `tr_generate_code` (LOKAL; `code_type`, `period_year`/`period_month`, `code_format`, `last_number`, `branch_id`) + `tr_generate_code_history`. **Disposisi: REBUILD** — definisi format di-seed dari `code_format`/`prefix` legacy; **counter `last_number` TIDAK dimigrasi sebagai kolom increment** (pola manual-increment = `[ARTIFACT]` do-not-replicate, DB-CONVENTIONS §6.5 race) → sequence DB per scope di-seed ≥ nomor maksimum legacy saat cutover (kontinuitas nomor, zero-collision, diverifikasi reconciliation). `tr_generate_code_history` = MIGRATE-READONLY (arsip audit penomoran; retensi per OQ-MIG-02).

| Kolom target | Tipe target | Null | Mapping asal | Marker | Catatan |
|---|---|---|---|---|---|
| `id` | `BIGINT` identity PK | NOT NULL | — | — | — |
| `code_type` | `VARCHAR(50)` | NOT NULL | `tr_generate_code.code_type` / `tr_auto_number.prefix` | `[VERIFIED]` kolom + vocabulary (OQ-GT-02 resolved 2026-07-14) | Registry jenis nomor (target: `CREDIT_ID`). Vocabulary legacy penghasil credit_id: `'TrCas'` (web .NET, `GeneratedCode.cs:7`), `'CreditId'` (SP sync, `FC_ACQ_MCF 2.sql:85803,96053`), `'CreditItid'` sic (r4/car, `dbo.sp_get_auto_number_r4...sql:31`) — tiga counter independen ber-format output identik = risiko tabrakan legacy `[ARTIFACT]` do-not-replicate → rebuild konsolidasi ke SATU `code_type` `CREDIT_ID` (BR-33 BE-01) |
| `company_id` | `VARCHAR(1)` | NULL | `tr_auto_number.company_id` | `[VERIFIED]` | NULL = berlaku semua company |
| `branch_id` | `VARCHAR(5)` | NULL | `tr_auto_number.branch_id` / `tr_generate_code.branch_id` | `[VERIFIED]` | NULL = format global; terisi = scope per branch |
| `format_template` | `VARCHAR(100)` | NOT NULL | `tr_generate_code.code_format` | `[VERIFIED]` kolom + isi (OQ-GT-02 resolved 2026-07-14) | Template placeholder (prefix, period, padding). Format aktual legacy `credit_id`: `{branch_id:5}{YY:2}{MM:2}{SEQ:5 zero-pad}` = 14 char (`sp_get_auto_number...sql:54-60`); nilai kolom legacy `code_format='Branch_YYYY_MM_00001'` hanya label — implementasi memakai YY 2-digit, BUKAN YYYY (jangan seed template dari literal kolom tanpa koreksi) |
| `reset_period` | `VARCHAR` + `CHECK` (`NONE\|MONTHLY\|YEARLY`) | NOT NULL | derive: `tr_auto_number.period` (yyyymm) / `period_year`+`period_month` | `[INFERRED]` | Legacy men-scope counter per period-row → di target jadi kebijakan reset sequence eksplisit |
| `sequence_name` | `VARCHAR` | NOT NULL | — (BARU, pengganti `last_number`) | — | Nama sequence DB per scope; di-seed ≥ max nomor legacy saat cutover |
| `effective_from`, `effective_to` | `DATE` | from NOT NULL, to NULL | — (BARU) | — | Versioning wajib kelas `cfg_` (DB-CONVENTIONS §1) |
| `is_active` | `BOOLEAN` | NOT NULL | — | — | — |

> Perubahan `cfg_number_format` yang menyentuh `code_type` ber-konsumen `[LOCKED]` (`CREDIT_ID`) wajib maker-checker — salah format memutus keunikan nasional nomor kontrak.

### 3.5 Lookup eksternal yang DISERVE read-only (Tier C — via ACL)

**Master eksternal `FC_MSTAPP_MCF` (310 tabel `Ms*`/`ms_*`) TIDAK dimodelkan sebagai tabel target di §3 ini** — diakses **read-only via ACL** (E30; BR-BE07-26), TANPA tabel `mst_` lokal, sampai keputusan ownership. Status per 2026-07-22: (a) **DDL ✅ TERSEDIA** — dump `FC_MSTAPP_MCF.sql` diterima; census 310 tabel + kolom "Dipakai ACQ" (read-set acquisition = 171 tabel) di KB `30-data-model/external-masters-census.md §7` — **OQ-REF-04 RESOLVED**; mapping "pending dump" §3.0–§3.4 sudah di-finalisasi dari DDL; (b) ownership owned-vs-read-only per master TETAP `[OPEN]` **OQ-EXTMASTERS-01 [P1]** — assignment per-Tier final (mana yang naik Tier A `mst_`/`cfg_`, mana tetap Tier C) diisi di sini per format DB-CONVENTIONS §9 SETELAH daftar ownership dari DBA/ITEC; (c) **anomali OQ-EXTMASTERS-07 [P1]**: 8 objek dirujuk code acquisition ABSEN dari dump (a.l. `ms_insurance_cover_type` — jalur insurance CM aktif; census §3) — jangan disposisikan sebelum klarifikasi DBA. Sampai ownership putus, kegagalan baca backing store = `503 LOOKUP_SOURCE_UNAVAILABLE` (BR-BE07-22), BUKAN fallback tabel lokal.

Katalog `FC_MSTAPP_MCF` (310 master — census `30-data-model/external-masters-census.md`; read-set acquisition 171), disajikan lewat endpoint lookup generik E-30 (§4). Kelompok utama + scope filter yang terverifikasi:

| Kelompok | Contoh lookup | Scope/filter terverifikasi | Marker data |
|---|---|---|---|
| Klasifikasi applicant (27+) | marital, nationality, identity-type, profession (+high-risk flag), economic sector (2-level, BR-CUSTMASTER-10), industry, residence status, house condition, title, customer type, customer source, mail-to source, other-installment, evaluation, debtor group (self-ref ≤5 level, BR-CUSTMASTER-11), relationship (2 taxonomy — BR-CUSTMASTER-8), 4 korporat OJK-coded, 5 taxonomy Repeat Order | `applicant_type` (individual `P` / corporate `C`) untuk identity-type & residence — **WAJIB set-membership eksplisit**, BUKAN substring match legacy (Edge Case 5 / BR-CUSTMASTER-9 `[ARTIFACT]` fix) | OJK-coded + identity-type `[LOCKED]`; sisanya `[INTENT]` (`10-...§6`) |
| Bank & pembayaran | bank (+bank group/detail/account), payment point (by payment-type) | Perhatian: `MsBank.PasscodeBiBca` = **[REDACTED-SECRET]** — field credential-shaped TIDAK boleh diekspos endpoint mana pun & TIDAK dimigrasi tanpa security review (OQ-DLRPTN-05/OQ-REF-05 P1) | `[INTENT]`; crosswalk GL `[LOCKED]` (milik disbursement) |
| Lokasi | location (village/district/regency/province) + **`regency_id_OJK` crosswalk** | by id / OJK variant | Crosswalk OJK **`[LOCKED]`** (BR-LOCATION-1 — regulatory reporting); `Dt2Type` kota/kabupaten `[OPEN]` OQ-DLRPTN-11 |
| Product/asset | product, product finance/marketing, application type, asset kind→class→brand→series→type, item-brand(-type), finance type, fuel, ownership proof, top-up type, disbursal type UMC | asset brand/kind-class scoped by parent kind (BR-PRODASSET-2); marketing product company-scoped (hardcoded 2-way mapping `[ARTIFACT]` — fix jadi mapping table, OQ-PRODASSET-07) | Katalog `[INTENT]`; `application_type_id` values kemungkinan `[LOCKED]` external (OQ-REF-01); duplikasi asset-vs-item `[OPEN]` OQ-PRODASSET-01 |
| Lain-lain | insurance source (flat list; shape `[OPEN]` OQ-DLRPTN-15), service bureau (header/branch/necessity-type), BPKB location/reason/receiver, branch/company (Tier B) | BPKB location dual-source `[OPEN]` OQ-DLRPTN-07 | per KB |

---

## 4. API Endpoint

Kontrak level resource+field (framework-agnostic). **Konvensi list terstandar** (formalisasi BR-MASTERDATA-14 `[VERIFIED][INTENT]` — pola yang di legacy di-copy-paste per layar): semua endpoint list menerima `page`, `page_size`, `search`, filter scope opsional; respons `{ items[], page, total_pages, record_count }`. Semua write Tier A mensyaratkan header `Idempotency-Key` untuk create. Auth/Role = enforcement app-layer (fix BR-MASTERDATA-13; role per D-10).

### 4.1 User management & menu

| # | Method | Path | Deskripsi | Auth/Role |
|---|---|---|---|---|
| E1 | GET | `/users` | List/search user + filter role/branch/status. | Credit (Admin) / Checker |
| E2 | POST | `/users` | Provision user: `employee_nik` (wajib ada & tidak resigned di mirror) + `role` (enum D-10) + scope. Tolak `SUPER_USER` (nilai tidak ada di enum — D-09). | Credit (Admin) (maker) |
| E3 | GET | `/users/{id}` | Detail user + grants efektif. | Credit (Admin) / self |
| E4 | PATCH | `/users/{id}` | Ubah role/scope (bukan identitas employee). | Credit (Admin) (maker) |
| E5 | POST | `/users/{id}/deactivate` / `/reactivate` | Lifecycle; reaktivasi ditolak bila mirror resigned. | Credit (Admin) |
| E6 | GET | `/users/{id}/menus` | Menu tree efektif user (role grants + special grants − inactive). Konsumen: app-shell FE (paritas `sp_get_hirarki_menu_by_user`). | Authenticated (self) / sistem |
| E7 | GET | `/roles` | Katalog role tertutup D-10 (statis). | Authenticated |
| E8 | GET | `/employees` | Search HR mirror (picker; NIK, nama, branch, position, **status resign eksplisit**). Read-only Tier B. | Credit (Admin) / Hierarchy Admin |
| E9 | GET/POST/PATCH | `/menus`, `/menus/{id}` | CRUD menu tree. Perubahan `trans_type_id_prefix` → jalur maker-checker (BR-BE07-14). Tidak ada DELETE — deactivate-only. | Credit (Admin) maker + checker utk prefix |
| E10 | GET/PUT | `/roles/{role}/menu-grants` | Baca/ganti set grant menu per role. | Credit (Admin) maker + checker |
| E11 | GET/PUT | `/users/{id}/menu-grants-special` | Grant khusus per user (bila fitur dipertahankan — OQ-BE07-05); wajib `granted_reason`. | Credit (Admin) maker + checker |

### 4.2 Dealer family (mandat D-08)

| # | Method | Path | Deskripsi | Auth/Role |
|---|---|---|---|---|
| E12 | GET | `/dealers` | List/search dealer; filter `branch_id` (hanya dealer ber-akses aktif — BR-DEALER-1), `is_used_car`, `status`. Konsumen picker 01. | Authenticated (read) |
| E13 | POST | `/dealers` | Create dealer (field §3.2; KTP/NPWP tervalidasi format `[LOCKED]`). | Credit (Admin) maker |
| E14 | GET / PATCH | `/dealers/{code}` | Detail / update. Update field identitas legal (KTP/NPWP) & `is_sub_dealer_enabled` → maker-checker. | maker (+checker utk field sensitif) |
| E15 | POST | `/dealers/{code}/deactivate` / `/reactivate` | Lifecycle (deactivation_date). | Credit (Admin) |
| E16 | GET/POST/PATCH | `/dealers/{code}/personnel`, `.../personnel/{id}` | CRUD personnel; `status='A'` filter di read default. | Credit (Admin) |
| E17 | GET/POST/PATCH | `/dealer-job-titles` | CRUD job title (+`dealer_payment_code`). | Credit (Admin) |
| E18 | GET/POST/PATCH | `/dealers/{code}/bank-references` | CRUD rekening dealer. **SEMUA write via maker-checker** (payout target `[LOCKED]`). | maker + **checker WAJIB** |
| E19 | GET/PUT | `/dealers/{code}/branch-access` | Set akses cabang (replace-set atomik). | Credit (Admin) maker |
| E20 | GET/POST | `/dealers/{code}/documents` | Upload/list dokumen legal (object storage; bukan kolom path). | Credit (Admin) |
| E21 | GET | `/dealers/{code}/payment-eligible-contacts?job_title_id=` | Resolusi kontak eligible pembayaran: join job-title→personnel→bank-ref aktif simultan (BR-DLRPTN-1). Read-only untuk konsumen disbursement/04. | Sistem/authenticated |

### 4.3 Transaction-Type Hierarchy config

| # | Method | Path | Deskripsi | Auth/Role |
|---|---|---|---|---|
| E22 | GET | `/transaction-codes?branch_id=` | List paginated per branch (paritas `pagination/kode-transaksi`). | Hierarchy Admin |
| E23 | PUT | `/transaction-codes/{branchId}/{code}` | **Upsert** (paritas legacy: satu aksi Save → `update-data`, tanpa insert terpisah — BR-MASTERDATA-6 `[VERIFIED][INTENT]`). | Hierarchy Admin maker |
| E24 | GET | `/transaction-types?transaction_code=` | List scoped by code. | Hierarchy Admin |
| E25 | POST / PATCH | `/transaction-types`, `/transaction-types/{code}` | Insert / update. PATCH hanya menerima `is_active` (BR-MASTERDATA-4); `mapping` wajib merujuk TRANSACTION_CODE yang ada. | Hierarchy Admin maker + checker |
| E26 | GET | `/approval-hierarchies?transaction_type_code=` | List ladder per type. | Hierarchy Admin |
| E27 | POST / PATCH | `/approval-hierarchies`, `/approval-hierarchies/{id}` | Insert / update level. **Validasi server-side WAJIB**: BR-BE07-15..17 (Level-1, next-PIC, PIC valid & tidak resigned). | Hierarchy Admin maker + checker |
| E28 | GET | `/approval-hierarchies/pic-candidates?search=&branch_id=` | PIC picker — employee mirror difilter job-title codes eligible (paritas `sp_get_pagination_data_pic_trans_type_hierarchy` `[VERIFIED]`). | Hierarchy Admin |

### 4.4 Master operasional lain + lookup layer

| # | Method | Path | Deskripsi | Auth/Role |
|---|---|---|---|---|
| E29 | GET/POST/PATCH | `/approval-reasons` | CRUD reason (type `'1'|'2'|'3'|'9'` — set diekspos `[OPEN]` OQ-DLRPTN-04; sampai resolved, endpoint read menerima filter `type` eksplisit, TIDAK hardcode subset). | Credit (Admin) maker |
| E30 | GET | `/lookups/{lookup_key}` | **Lookup generik Tier C** — `lookup_key` = katalog terdaftar (mis. `marital-status`, `identity-type`, `economic-sector`, `bank`, `location`, `asset-brand`…). Query: `applicant_type=`, `parent_id=`, `branch_id=`, `include_inactive=false` (default active-only, BR-PRODASSET-1). Registry key ada di §5. | Authenticated |
| E31 | GET/POST/PATCH | `/credit-sources` + `/branches/{id}/credit-sources` | CRUD credit source (lokal) + mapping per branch dengan flag `photo_required`/`print_survey_report`. | Credit (Admin) maker |
| E32 | GET/POST/PATCH | `/blacklist-overrides` | CRUD override/whitelist (BE-01 BR-29). **Maker-checker WAJIB**; setiap mutasi → audit append-only. | maker + **checker WAJIB** |
| E33 | GET/POST/PATCH/DELETE | `/public-holidays` | CRUD kalender libur. Satu-satunya master ber-DELETE fisik (data kalender, tanpa konsumen historis — USULAN; lihat OQ-BE07-06). | Credit (Admin) |
| E34 | GET/PATCH | `/general-parameters`, `/general-parameters/{key}` | Read/update parameter; `is_updateable=false` → `409`. Tanpa create/delete via API (seed migrasi). | Credit (Admin) maker + checker |
| E35 | GET/POST/PATCH | `/promotion-line-texts` | CRUD banner text (+display color). | Credit (Admin) |
| E36 | GET/PATCH | `/gl-transaction-type-links` | Read + update ter-audit mapping GL `[LOCKED]`. Maker-checker WAJIB; tanpa delete. | maker + **checker WAJIB** |
| E37 | GET/POST | `/master-change-requests`, `.../{id}/approve`, `.../{id}/reject` | **Envelope maker-checker generik** untuk semua resource bertanda "checker WAJIB": write masuk sebagai change-request `pending_approval`; checker (≠ maker) approve/reject. | maker / checker |
| E38 | GET/POST/PATCH | `/number-formats` | Admin konfigurasi `cfg_number_format` (§3.4) — definisi format penomoran (`code_type`, template, reset period, scope). Perubahan `code_type` ber-konsumen `[LOCKED]` (`CREDIT_ID` — BE-01) wajib maker-checker. Generator nomor sendiri = service internal konsumen (BE-01), bukan endpoint publik. | Credit (Admin) maker + **checker utk `CREDIT_ID`** |

> Endpoint yang sengaja TIDAK ada: `POST /employees` (HR system-of-record — BR-EMPLOYEE-1 `[LOCKED]`), `DELETE` untuk semua master konfigurasi (deactivate-only — BR-BE07-03), endpoint apa pun yang mengekspos `PasscodeBiBca` (**[REDACTED-SECRET]**), endpoint super-user grant (D-09 `[LOCKED]`).

---

## 5. Kontrak Request/Response

Error envelope seragam (konsisten BE-01..05): `{ code, message, details?, correlation_id }`.

### E2 — POST /users (provision user)

Request (`*` wajib):

```json
{
  "employee_nik": "05123",                    // * harus ada di EMPLOYEE_MIRROR & tidak resigned
  "role": "CREDIT_ADMIN",                      // * enum tertutup D-10 [LOCKED]
  "company_id": "3",
  "branch_scope": ["0101", "0102"],
  "activation_date": "2026-07-15"
}
```

Response `201 Created`:

```json
{
  "id": "USR-000045",
  "employee_nik": "05123",
  "employee_name": "Budi Santoso",             // resolved dari mirror
  "role": "CREDIT_ADMIN",
  "branch_scope": ["0101", "0102"],
  "is_active": true,
  "created_at": "2026-07-14T03:00:00Z"
}
```

Error: `422 UNKNOWN_ROLE` (nilai di luar enum D-10 — termasuk percobaan `SUPER_USER`, pesan menyebut D-09); `422 EMPLOYEE_NOT_FOUND`; `422 EMPLOYEE_RESIGNED` (mirror `is_resigned=true` — eksplisit, bukan empty-silent seperti Edge Case 1/12 legacy); `409 USER_ALREADY_EXISTS` (unique `employee_nik`).

### E6 — GET /users/{id}/menus

Response `200 OK`:

```json
{
  "employee_nik": "05123",
  "role": "CREDIT_ADMIN",
  "menus": [
    { "menu_id": "M-030", "parent_id": null, "module": "MASTER", "name": "Master Dealer",
      "route": "/masters/dealers", "is_view_only": false, "display_order": 1,
      "children": [ ... ] }
  ]
}
```

Menu inactive dan grant inactive TIDAK dikembalikan. `trans_type_id_prefix` TIDAK diekspos di endpoint ini (internal routing concern — hanya di E9 untuk admin).

### E13 — POST /dealers

Request:

```json
{
  "dealer_code": "DLR-0451",
  "dealer_name": "PT Maju Motor",
  "is_authorized_dealer": true,
  "is_selling_new_product_only": false,
  "is_used_car": true,
  "is_sub_dealer_enabled": false,
  "parent_dealer_code": null,
  "contact_person": "Andi", "contact_job_title_id": "JT-02",
  "ktp_no": "3275xxxxxxxxxxxx",                // * [LOCKED] format KTP
  "ktp_name": "Andi Wijaya",
  "npwp_no": "09.xxx.xxx.x-xxx.xxx",           // [LOCKED] panjang ter-regulasi
  "address": "...", "location_id": "LOC-3275",
  "mou_no": "MOU/2026/041", "mou_date": "2026-05-01",
  "rate_refund": 0.5,
  "activation_date": "2026-07-15",
  "notes": "catatan bebas — bukan join key"
}
```

Response `201`: record penuh + `status: "active"`. Error: `422` validasi format KTP/NPWP; `409 DEALER_CODE_EXISTS`; `422 PARENT_DEALER_NOT_FOUND` (FK eksplisit — bukan match via `notes`, fix Edge Case 7).

### E18 — POST /dealers/{code}/bank-references (maker-checker)

Request:

```json
{
  "account_type": "OPR",
  "bank_id": "000122",
  "account_number": "1234567890",
  "account_name": "PT Maju Motor",
  "bank_charges_flag": false,
  "activation_date": "2026-07-15"
}
```

Response `202 Accepted` (BUKAN `201` — masuk antrean checker):

```json
{
  "change_request_id": "MCR-2026-00088",
  "resource": "dealer-bank-reference",
  "action": "create",
  "status": "pending_approval",
  "maker": "05123",
  "submitted_at": "2026-07-14T03:10:00Z"
}
```

Setelah `POST /master-change-requests/MCR-2026-00088/approve` oleh checker (≠ maker; self-approve → `403 SELF_APPROVAL_BLOCKED`), record hidup + respons approve membawa record final. Reject → `status: "rejected"` + `reject_reason` wajib; data tidak berubah.

### E27 — POST /approval-hierarchies (validasi server-side — fix OQ-MASTERDATA-02)

Request:

```json
{
  "transaction_type_code": "0224000004",
  "level": 1,
  "pic_nik": "04021",
  "next_pic_nik": "03544",                     // wajib karena is_approver=false
  "is_approver": false,
  "is_active": true
}
```

Response `201` (via change-request bila checker aktif). Error (semua **server-side**, tidak lagi hanya JS FE):

```json
{ "code": "HIERARCHY_RULE_VIOLATION",
  "message": "Level 1 tidak boleh menjadi terminal approver",
  "details": [ { "rule": "BR-BE07-15", "field": "is_approver" } ],
  "correlation_id": "..." }
```

Varian: `422 NEXT_PIC_REQUIRED` (BR-BE07-16), `422 NEXT_PIC_MUST_BE_EMPTY` (is_approver=true), `422 PIC_NOT_FOUND` / `422 PIC_RESIGNED` (BR-BE07-17), `422 TRANSACTION_TYPE_NOT_FOUND`, `409 DUPLICATE_LEVEL_PIC`.

### E30 — GET /lookups/{lookup_key} (lookup generik Tier C)

`GET /lookups/identity-type?applicant_type=P&page=1&page_size=50`

Response `200 OK`:

```json
{
  "lookup_key": "identity-type",
  "source_tier": "C_EXTERNAL_READONLY",        // transparansi boundary (§1.3)
  "items": [
    { "id": "01", "name": "KTP", "applicant_types": ["P"], "is_active": true }
  ],
  "page": 1, "total_pages": 1, "record_count": 4
}
```

Aturan: filter `applicant_type` = **set-membership eksplisit** terhadap mapping many-to-many (fix Edge Case 5 / BR-CUSTMASTER-9 `[ARTIFACT]`); default active-only (BR-PRODASSET-1); lookup berhierarki (economic-sector, debtor-group, asset taxonomy) menerima `parent_id`. **Kegagalan baca = error nyata** (`503 LOOKUP_SOURCE_UNAVAILABLE`), BUKAN sukses-kosong (fix Edge Case 1 `11-...§9` + gotcha fuel `12-...§9` — do-not-replicate silent-success). Registry `lookup_key` awal: seluruh baris §3.5; penambahan key = konfigurasi, bukan endpoint baru.

### E37 — Envelope maker-checker generik

`GET /master-change-requests?status=pending_approval&resource=dealer-bank-reference` → list untuk inbox checker. Approve:

```json
POST /master-change-requests/{id}/approve
{ "checker_note": "verified via MOU doc" }
```

Response `200`: `{ "id": "...", "status": "approved", "applied_record": { ... }, "checker": "07001", "approved_at": "..." }`. Guard: checker harus punya role checker utk resource ybs + `checker != maker` (`403 SELF_APPROVAL_BLOCKED`). Approve idempotent by `Idempotency-Key`.

---

## 6. Aturan Bisnis

| ID | Aturan | Sumber KB | Marker | Catatan (perilaku rebuild) |
|---|---|---|---|---|
| BR-BE07-01 | Role user = enum tertutup **CMO, MARKETING_HEAD, CREDIT_ANALYST, KEPALA_CABANG, CREDIT_ADMIN**; TIDAK ada nilai/flag super-user di entitas, endpoint, maupun grant mana pun. | D-09, D-10 | `[LOCKED]` | `ms_trans_super_user` legacy hanya dibaca utk migrasi audit (BE-03 §10). Percobaan grant setara super-user via special-grant = pelanggaran D-09 (lihat OQ-BE07-05). |
| BR-BE07-02 | `APP_USER` hanya bisa dibuat untuk NIK yang ada di `EMPLOYEE_MIRROR` dan tidak resigned; aplikasi TIDAK menyimpan password (auth = corporate directory). | BR-EMPLOYEE-1 `11-...§7`; BR-SHELL-1 `60-frontend/60-...§7` | `[LOCKED]` | HR & LDAP system-of-record. Tidak ada endpoint create employee. |
| BR-BE07-03 | Master konfigurasi (menu, transaction code/type, hierarchy, reason, dealer, user) TIDAK bisa di-hard-delete — lifecycle hanya create + toggle active/inactive. | BR-MASTERDATA-7 `66-...§7`; BR-PRODASSET-1 | `[INTENT]` → kandidat `[LOCKED]` | Paritas legacy (tidak ada delete action) + preservasi jejak historis routing. Konfirmasi upgrade ke LOCKED = OQ-MASTERDATA-01. Pengecualian: `PUBLIC_HOLIDAY` (OQ-BE07-06). |
| BR-BE07-04 | Setiap mutasi master Tier A tercatat audit (who/when/before-after); mutasi resource maker-checker juga menyimpan change-request + keputusan checker. | Konsistensi umbrella §audit; D-01 S11 (self-approval blocked sebagai prinsip) | `[INTENT]` (USULAN) | Legacy tidak punya audit master (tidak ada write path sama sekali). |
| BR-BE07-05 | **Maker-checker WAJIB** untuk: `DEALER_BANK_REFERENCE` (payout `[LOCKED]`), `BLACKLIST_OVERRIDE` (regulated), `GL_TRANSACTION_TYPE_LINK` (`[LOCKED]` CoA), `GENERAL_PARAMETER`, `MENU.trans_type_id_prefix`, `TRANSACTION_TYPE`/`APPROVAL_HIERARCHY_LEVEL` (menentukan routing approval), field identitas legal `DEALER`, `NUMBER_FORMAT` utk `code_type` ber-konsumen `[LOCKED]` (`CREDIT_ID` — §3.4/E38). Checker ≠ maker (self-approval blocked). | USULAN; grounded D-01 S11 (self-approval blocked), kritikalitas per `66-...` header + `30-...` mutability | `[OPEN]` scope final OQ-BE07-01 | Legacy TIDak punya maker-checker di master (`12-...§3a` "not by a wizard or maker-checker hand-off") — ini kontrol BARU; jangan diklaim paritas. |
| BR-BE07-06 | Mutasi `BLACKLIST_OVERRIDE` append-only di audit; override wajib `justification` + masa berlaku; dipakai reason-gate RFA di 01 (read-only dari sisi 01). | BE-01 BR-29; `20-...§7 BR-ACQCAS-22` | `[LOCKED]` adjacency AML | CRUD-nya baru (legacy tabel tanpa CRUD — OQ-ACQCAS-08). |
| BR-BE07-07 | Dealer hanya muncul pada picker suatu cabang bila punya row `DEALER_BRANCH_ACCESS` aktif untuk cabang itu (dealer = partner branch-scoped, bukan global). | BR-DEALER-1 `11-...§7` | `[VERIFIED][INTENT]` | Path "main dealer code" legacy yang join via `notes` TIDAK direplikasi (Edge Case 7 `[ARTIFACT]`). |
| BR-BE07-08 | Dealer `is_selling_new_product_only=false` selalu ter-include di pencarian dealer used-car (carve-out mixed-inventory); override kondisi-item per application-type `'03'` (`is_item_new` forced `'1'`) adalah rule konsumsi milik 01, didokumentasikan di sini karena datanya milik 07. | BR-DEALER-2 `11-...§7` | `[VERIFIED][INTENT]` | — |
| BR-BE07-09 | Visibilitas sub-dealer dikendalikan flag eksplisit `is_sub_dealer_enabled` pada dealer master — match nama literal `'%PT Lucas Digital Indonesia%'` TIDAK di-port. | BR-DEALER-3 `11-...§7`; Edge Case 6 | `[ARTIFACT]` → fix flag | Migrasi: set flag `true` utk dealer yang memenuhi rule legacy saat cutover. |
| BR-BE07-10 | Kontak dealer eligible pembayaran diresolusi via join job-title → personnel → bank-reference dengan SEMUA status aktif simultan; record non-aktif di titik mana pun mengeluarkan kontak dari set eligible. | BR-DLRPTN-1 `11-...§7` | `[VERIFIED][INTENT]` | Diekspos sebagai E21 (read) utk konsumen disbursement. Anchor rekening utk credit tertentu tetap credit-memo-driven (BR-DLRPTN-2 `[LOCKED]`) — milik konsumen, bukan 07. |
| BR-BE07-11 | `CREDIT_SOURCE` adalah master LOKAL acquisition (bukan `FC_MSTAPP_MCF`); availability + flag `photo_required`/`print_survey_report` di-scope per branch via mapping. | BR-CREDITSRC-1/2 `11-...§7` | `[VERIFIED]` / `[INTENT]` | Konfirmasi penempatan fisik legacy = OQ-DLRPTN-02 (tidak memblokir Tier A). |
| BR-BE07-12 | Lookup identity-type/residence-status ter-scope applicant-type via **mapping set-membership eksplisit** (many-to-many), bukan substring match. | BR-CUSTMASTER-9 `10-...§7`; Edge Case 5 | `[ARTIFACT]` → fix | Nilai mapping aktual dikonfirmasi saat migrasi data (OQ-CUSTMASTER-03). |
| BR-BE07-13 | Economic sector = hierarki 2-level ketat (kategori → kode); debtor group = hierarki self-referencing ≤5 level — dipertahankan pada shape lookup E30. | BR-CUSTMASTER-10/11 `10-...§7` | `[VERIFIED][INTENT]` | Read-only Tier C. |
| BR-BE07-14 | `MENU.trans_type_id_prefix` adalah input struktural komposisi `trans_type_id`: mutasinya wajib maker-checker + tercatat audit + memicu warning listing transaction-type yang ter-impact. Reorganisasi menu TIDAK boleh mengubah routing diam-diam. | BR-PRODASSET-14 `12-...§7`; umbrella §"Komposisi trans_type_id" | `[VERIFIED][LOCKED]` | Format `trans_type_id` di-match char-for-char external-FK (BR-PRODASSET-7 `[LOCKED]`). |
| BR-BE07-15 | Level 1 ladder TIDAK boleh `is_approver=true` (level pertama bukan terminal approver) — dienforce **server-side**. | BR-MASTERDATA-1 `66-...§7`; **body SP legacy V3 (census KB §5)** | `[VERIFIED][INTENT]` | **Update 2026-07-22**: legacy TERNYATA juga enforce server-side di SP (`Low Level Must Not Have True Is Approver`) — bukan hanya JS (OQ-MASTERDATA-02 ter-koreksi sebagian). |
| BR-BE07-16 | `is_approver=false` → `next_pic` WAJIB; `is_approver=true` → `next_pic` WAJIB kosong (terminal tidak punya successor) — server-side. | BR-MASTERDATA-2/3 `66-...§7`; SP legacy: approver row ditulis dengan `spv_*=''` | `[VERIFIED][INTENT]` | SP legacy menulis empty-string utk spv saat approver (bukan menolak input) — rebuild validasi eksplisit. |
| BR-BE07-17 | `pic_nik`/`next_pic_nik` WAJIB merujuk employee mirror yang ada dan tidak resigned saat write; PIC picker difilter job-title codes eligible. | `12-...§11` (`sp_get_pagination_data_pic_...` `[VERIFIED]`); Edge Case 12 `11-...` | `[VERIFIED][INTENT]` | Mencegah ladder menunjuk approver resign; **celah legacy terkonfirmasi dari SP (V4 §3.3): enrichment `ms_employee_sync` tanpa guard** — rebuild menutupnya. |
| BR-BE07-18 | Edit `TRANSACTION_TYPE` hanya boleh mengubah `is_active`; code/description/mapping immutable pasca-create. `mapping` disimpan eksplisit, TIDAK di-derive dari `substring(0,2)` kode. | BR-MASTERDATA-4/5 `66-...§4/§7` | `[VERIFIED][INTENT]` / derive = `[INFERRED]` don't-port | OQ-MASTERDATA-08 (konvensi prefix) tidak perlu resolved bila mapping disimpan eksplisit. |
| BR-BE07-19 | `TRANSACTION_CODE` bersifat upsert (satu aksi Save); kode dinormalisasi upper-case server-side. | BR-MASTERDATA-6 `66-...§7` | `[VERIFIED][INTENT]` | Outcome dipertahankan; mekanisme bebas. |
| BR-BE07-20 | Semua endpoint list mengikuti kontrak pagination standar tunggal (page/page_size/search → items/total_pages/record_count) sebagai komponen reusable. | BR-MASTERDATA-14 `66-...§7` | `[VERIFIED][INTENT]` | Formalisasi konvensi yang di legacy di-copy-paste per layar (+bug pager Edge Case 2/3 tidak direplikasi). |
| BR-BE07-21 | Read lookup default active-only; `include_inactive=true` tersedia untuk layar admin (toggle yang tidak ada di legacy). | BR-PRODASSET-1 `12-...§7` | `[VERIFIED][INTENT]` | — |
| BR-BE07-22 | Kegagalan baca lookup/mirror = error eksplisit (`503`/`404`), TIDAK PERNAH sukses-kosong; "employee resigned", "not found", dan "source error" adalah tiga sinyal berbeda. | Edge Case 1/2/12 `11-...§9`; gotcha fuel `12-...§9` | `[ARTIFACT]` → fix | Do-not-replicate silent-success. |
| BR-BE07-23 | `GENERAL_PARAMETER.is_updateable=false` menolak mutasi via API (`409`); `is_visible=false` disembunyikan dari listing non-admin. | `MsGeneralParameter` `30-...§7` | `[VERIFIED][INTENT]` | — |
| BR-BE07-24 | `GL_TRANSACTION_TYPE_LINK` (mapping CoA) read + update-ter-audit saja; nilai mapping `[LOCKED]` — perubahan wajib maker-checker + sign-off finance. | `30-...§11` | `[LOCKED]` | Journal/posting rules downstream = OQ-MEET-03. |
| BR-BE07-25 | Field credential-shaped pada master eksternal (`MsBank.PasscodeBiBca`) TIDAK diekspos endpoint mana pun dan TIDAK dimigrasi tanpa security review; bila live → secrets manager. | `11-...§7 BR-BANKMASTER-1`; `30-...` cross-cutting; `50-...§8` (plaintext cred MINIAPI) | **[REDACTED-SECRET]** `[OPEN]` OQ-DLRPTN-05/OQ-REF-05 | Aksi security independen timeline rebuild (OQ-EXTMASTERS-05). |
| BR-BE07-26 | Akses master eksternal Tier C via ACL (service boundary); TIDAK mereplikasi cross-database three-part-name join maupun linked-server four-part-name sebagai pola akses aplikasi baru. | `50-...§1/§4` | `[LOCKED]` fakta kopling; redesign deliberate | Perubahan semantik transaksional (tidak ada lagi single-transaction join lintas sistem) diputuskan sadar — masuk asumsi arsitektur D-11. |
| BR-BE07-27 | User yang HR mirror-nya berubah resigned di-deactivate otomatis oleh sync job (`deactivation_reason=hr_resigned`); grant menu efektif ikut mati. | Turunan BR-EMPLOYEE-1/2; Edge Case 12 | `[INTENT]` (USULAN) | Outcome legacy "posisi berubah → akses berubah" (BR-EMPLOYEE-2) dipertahankan pada re-provisioning role. |

---

## 7. State Machine

### 7.1 Lifecycle record master Tier A (non-maker-checker)

```
(∅) --create (E-post)--> active
active --deactivate--> inactive
inactive --reactivate--> active
```

Tidak ada transisi delete (BR-BE07-03). Paritas legacy state machine (B) `66-master-data-screens.md §8`.

### 7.2 Lifecycle change-request (maker-checker — resource sensitif)

| Dari | Aksi | Ke | Guard |
|---|---|---|---|
| `(∅)` | Maker submit write (E18/E25/E27/E32/E34/E36/E38-`CREDIT_ID`/E9-prefix/E14-sensitif) | `pending_approval` | Payload valid (semua BR §6 dievaluasi SAAT submit, bukan saat approve saja); maker punya role maker |
| `pending_approval` | Checker approve (E37) | `applied` | `checker != maker` (`403 SELF_APPROVAL_BLOCKED`); checker berwenang utk resource; approve = terapkan mutasi + audit |
| `pending_approval` | Checker reject | `rejected` | `reject_reason` wajib; nol perubahan pada master |
| `pending_approval` | Maker cancel | `cancelled` | Hanya maker pembuat; nol perubahan |
| `applied` / `rejected` / `cancelled` | — | (terminal) | Immutable; audit permanen |

### 7.3 Lifecycle `APP_USER`

| Dari | Aksi | Ke | Guard |
|---|---|---|---|
| `(∅)` | E2 provision | `active` | NIK ada di mirror & tidak resigned (BR-BE07-02); role enum D-10 |
| `active` | E5 deactivate (manual) | `inactive(manual)` | — |
| `active` | Sync job: mirror resigned | `inactive(hr_resigned)` | Otomatis (BR-BE07-27) |
| `inactive(manual)` | E5 reactivate | `active` | Ditolak bila mirror resigned |
| `inactive(hr_resigned)` | E5 reactivate | — (`409`) | Tidak bisa reaktivasi user resigned |

### 7.4 Referensi: shape ladder approval (definisi, bukan eksekusi)

Definisi ladder (level → PIC → next-PIC/eskalasi → terminal `is_approver`) yang dimiliki modul ini adalah state-machine *master definition* per `12-product-asset-master.md §8`; **eksekusinya** (inbox, escalation clock, siapa bertindak) milik 03 dan tidak diduplikasi di sini. Menonaktifkan level (`is_active=false`) mengeluarkannya dari routing masa depan tanpa menghapus histori (paritas node `Deactivated` KB §8).

---

## 8. Integrasi Eksternal

Semua akses eksternal via **ACL**; tidak ada cross-DB DML, tidak ada linked-server sebagai pola aplikasi baru (BR-BE07-26).

| Seam | Arah | Sync/Async | Owner | Peran di 07 |
|---|---|---|---|---|
| **HR system → `EMPLOYEE_MIRROR`** (`ms_employee_sync`, `vw_HREmployeeData`) | inbound (sync job) | async batch/CDC (mekanisme `[OPEN]` OQ-BE07-02) | HR (system-of-record) `[LOCKED]` BR-EMPLOYEE-1 | Sumber picker PIC/user provisioning; trigger auto-deactivate (BR-BE07-27). Status resign WAJIB ikut ter-sync eksplisit. |
| **Corporate directory (LDAP)** | — (tidak dipanggil 07) | — | Auth service / app-shell | Konteks: alasan `APP_USER` tanpa password (BR-SHELL-1 `[LOCKED]`). 07 hanya menyuplai data authz (role/menu). |
| **`FC_MSTAPP_MCF` external masters (310 `Ms*` — DDL ✅ census 2026-07-22)** | inbound (read) | sync | `[OPEN]` OQ-EXTMASTERS-01 (owned vs read-only — blocker keputusan); OQ-EXTMASTERS-07 (8 objek absen dari dump) | Backing store Tier C (E30). Legacy = same-instance three-part-name (network-free); rebuild = ACL/service API — keputusan sadar atas perubahan semantik transaksional (`50-...§4` `[LOCKED]` fakta). |
| **Linked servers** (`MACF-DBSTG`, `MACF-DBMCF`, `MACF-DBKONSOL`, `macf-dbrep`) | inbound (read, legacy) | sync (legacy) | infra legacy; reachability `[OPEN]` OQ-EXTMASTERS-01 (inventory `50-...§5`) | 07 TIDAK menambah dependensi linked-server baru. Data yang legacy tarik dari sana utk master (mis. BPKB location union dual-source — OQ-DLRPTN-07) diputuskan single source saat migrasi. |
| **Passnet** | outbound key compat | — | 05/downstream | `BRANCH.branch_id_passnet` `[LOCKED]` external-system key (`30-...§1`) — mirror Tier B wajib membawanya utuh. |
| **OJK reporting** | outbound (via modul reporting) | — | regulatory | Crosswalk `regency_id_OJK` (BR-LOCATION-1 `[LOCKED]`) + 4 lookup korporat OJK-coded `[LOCKED]` — nilai kode WAJIB dipertahankan verbatim di Tier C. |
| **GL/finance** | outbound (read by finance) | — | finance | `GL_TRANSACTION_TYPE_LINK` `[LOCKED]`; crosswalk bank-ID 4-nilai (`50-...§6` `[VERIFIED][LOCKED]` — copy verbatim, milik disbursement) tidak diubah 07. |

---

## 9. Acceptance Criteria (Given/When/Then)

**AC-1 (provision user happy path)**
Given employee NIK `05123` ada di `EMPLOYEE_MIRROR` dengan `is_resigned=false`,
When Credit (Admin) POST `/users` dengan `role="CREDIT_ADMIN"` dan `Idempotency-Key`,
Then respons `201` berisi user aktif ber-role enum D-10, tanpa field password, dan audit create tercatat.

**AC-2 (super-user diblokir — D-09)**
Given payload `role="SUPER_USER"` (atau nilai apa pun di luar enum D-10),
When POST `/users`,
Then respons `422 UNKNOWN_ROLE` dengan pesan merujuk D-09/D-10, dan tidak ada mekanisme grant lain yang menghasilkan hak setara super-user.

**AC-3 (user untuk employee resigned ditolak; auto-deactivate)**
Given NIK `09999` bertanda `is_resigned=true` di mirror,
When POST `/users` untuk NIK itu,
Then `422 EMPLOYEE_RESIGNED` (eksplisit, bukan sukses-kosong).
And Given user aktif yang kemudian resigned di sync HR berikutnya,
Then user otomatis `inactive(hr_resigned)` dan `GET /users/{id}/menus` mengembalikan set kosong.

**AC-4 (menu efektif per role)**
Given role `CREDIT_ADMIN` di-grant menu `M-030` (aktif) dan `M-031` (menu `is_active=false`),
When GET `/users/{id}/menus` untuk user ber-role itu,
Then hanya `M-030` dikembalikan, berbentuk tree dengan `is_view_only` terisi.

**AC-5 (proteksi trans_type_id_prefix — BR-BE07-14)**
Given menu `M-040` dengan `trans_type_id_prefix="02"` yang dirujuk transaction-type aktif,
When maker PATCH `/menus/M-040` mengubah prefix menjadi `"05"`,
Then mutasi masuk `pending_approval` (bukan langsung applied) dan respons menyertakan daftar transaction-type ter-impact; tanpa approve checker, routing tidak berubah.

**AC-6 (dealer branch-scoped picker — BR-BE07-07)**
Given dealer `DLR-0451` punya akses aktif hanya ke branch `0101`,
When GET `/dealers?branch_id=0102`,
Then `DLR-0451` tidak ada di hasil; When GET dengan `branch_id=0101`, Then muncul.

**AC-7 (dealer create + FK parent eksplisit)**
Given payload dealer dengan `parent_dealer_code="DLR-XXXX"` yang tidak ada,
When POST `/dealers`,
Then `422 PARENT_DEALER_NOT_FOUND` — linkage TIDAK diresolusi via kolom `notes` (fix Edge Case 7).

**AC-8 (maker-checker rekening dealer — BR-BE07-05)**
Given maker `05123` men-submit bank reference baru untuk `DLR-0451`,
When POST `/dealers/DLR-0451/bank-references`,
Then respons `202` dengan change-request `pending_approval`; rekening BELUM eligible payout.
When checker `07001` (≠ maker) approve,
Then record hidup + audit maker/checker lengkap.
When `05123` mencoba approve change-request-nya sendiri,
Then `403 SELF_APPROVAL_BLOCKED`.

**AC-9 (eligibility kontak pembayaran — BR-BE07-10)**
Given personnel dealer ber-status `A` dengan job title cocok dan bank reference `A`,
When GET `/dealers/{code}/payment-eligible-contacts?job_title_id=JT-02`,
Then kontak muncul; When bank reference dinonaktifkan, Then kontak hilang dari set eligible (tanpa delete data).

**AC-10 (validasi ladder server-side — fix OQ-MASTERDATA-02)**
Given payload `level=1, is_approver=true`,
When POST `/approval-hierarchies` langsung ke API (tanpa FE),
Then `422 HIERARCHY_RULE_VIOLATION` merujuk BR-BE07-15 — invariant tidak lagi bergantung JS browser.
And Given `is_approver=false` tanpa `next_pic_nik`, Then `422 NEXT_PIC_REQUIRED` (BR-BE07-16).
And Given `pic_nik` resigned, Then `422 PIC_RESIGNED` (BR-BE07-17).

**AC-11 (transaction type immutable — BR-BE07-18)**
Given transaction type existing,
When PATCH mengirim perubahan `description`,
Then `422` (hanya `is_active` yang diterima); mapping tersimpan eksplisit dan tidak berubah karena kode di-rename tidak dimungkinkan.

**AC-12 (lookup scoped set-membership — BR-BE07-12)**
Given identity-type `KITAS` ter-map hanya ke applicant type `P`,
When GET `/lookups/identity-type?applicant_type=C`,
Then `KITAS` tidak dikembalikan; tidak ada partial-text matching pada filter.

**AC-13 (lookup fail = error eksplisit — BR-BE07-22)**
Given backing store Tier C tidak reachable,
When GET `/lookups/marital-status`,
Then `503 LOOKUP_SOURCE_UNAVAILABLE` + `correlation_id` — BUKAN `200` dengan list kosong.

**AC-14 (no hard delete — BR-BE07-03)**
Given approval reason aktif,
When klien mengirim `DELETE /approval-reasons/{id}`,
Then `405`/`404` (route tidak ada); deactivate via PATCH `is_active=false` dan record historis tetap terbaca dengan `include_inactive=true`.

**AC-15 (blacklist override ter-governance — BR-BE07-06)**
Given maker submit override untuk NIK tertentu tanpa `justification`,
When POST `/blacklist-overrides`,
Then `422`; dengan justification lengkap → `202 pending_approval`; setelah approve, mutasi tercatat append-only dan 01 dapat membacanya pada gate RFA.

**AC-16 (general parameter guard — BR-BE07-23)**
Given parameter `X` dengan `is_updateable=false`,
When PATCH `/general-parameters/X`,
Then `409` dan nilai tidak berubah.

---

## 10. Dependency

**Upstream dikonsumsi:**
- **HR system** → sync `EMPLOYEE_MIRROR` (Tier B) — prasyarat provisioning user & PIC picker. Mekanisme sync `[OPEN]` OQ-BE07-02.
- **Corporate directory (LDAP)** — dependensi auth service/app-shell, bukan langsung 07; kontrak: identitas login = NIK yang sama dengan mirror (BR-SHELL-1 `[LOCKED]`).
- **`FC_MSTAPP_MCF` katalog eksternal** — backing Tier C; DDL ✅ (OQ-REF-04 RESOLVED 2026-07-22, census KB `30-data-model/external-masters-census.md`); ownership `[OPEN]` OQ-EXTMASTERS-01 (blocker keputusan untuk mengangkat lookup mana pun ke Tier A).
- **ITEC architecture deliverable (D-11)** — menentukan topologi service master-data + strategi ACL.

**Downstream konsumen (read-only terhadap 07):**
- **01-intake** — dealer/subdealer picker (E12, BR-BE07-07/08/09), lookup klasifikasi applicant (E30), `mst_blacklist_override` utk reason-gate RFA (BE-01 BR-21/BR-29), **`cfg_number_format`** utk minting `credit_id` STEP 8 (§3.4; koordinat BE-01/OQ-GT-02 — format exact **RESOLVED — evidence**: 14-char `branch_id(5)+YY(2)+MM(2)+SEQ(5)`, spec BE-01 §3.1.13).
- **02-credit-analysis** — lookup risk/klasifikasi; `trans_type_id_prefix` menu (via komposisi milik 02).
- **03-approval-committee** — definisi `TRANSACTION_CODE/TYPE/APPROVAL_HIERARCHY_LEVEL` + `APPROVAL_REASON` (routing & disposisi; eksekusi milik 03). Catatan: konsumen nyata konfigurasi TransTypeHierarchy `[OPEN]` OQ-MASTERDATA-03 — jangan asumsikan feed tunggal.
- **04-contract-cm-po / disbursement** — `DEALER_BANK_REFERENCE` + E21 eligibility (payout), payment masters lookup.
- **05-npp-legalization** — reason-code Vertel/NPP (`ms_CAS_approval_reason` — umbrella §Vertel), branch/Passnet key.
- **App-shell FE (60)** — E6 menu efektif + role user.
- **FE-07 (Next.js)** — seluruh layar master (PRD FE terpisah per D-12).

**Urutan build (USULAN):** (1) `EMPLOYEE_MIRROR` sync + E8 → (2) `APP_USER`/role/menu (E1–E11) → (3) Dealer family (E12–E21) → (4) TransType hierarchy config (E22–E28) → (5) master operasional lain + lookup layer + numbering (E29–E38). Alasan: 01–05 semuanya butuh lookup layer; user/menu dibutuhkan app-shell paling awal; hierarchy config dibutuhkan sebelum 03 diuji end-to-end (paritas catatan Rebuild Phase `66-...` header).

---

## 11. Keputusan Dibutuhkan (Open Questions)

| OQ-ID | Pertanyaan | Menyentuh | Prioritas |
|---|---|---|---|
| **OQ-EXTMASTERS-01** | **PARTIALLY RESOLVED 2026-07-22 — dump ✅ diterima** (310 tabel; census KB `30-data-model/external-masters-census.md`). SISA: katalog owned oleh rebuild atau read-only per master + linked servers `MACF-DBSTG/DBMCF/DBKONSOL/dbrep` masih reachable/live? Menentukan master Tier C mana yang naik Tier A (CRUD). **Blocker keputusan** (umbrella) — lihat §3.5. | 07 (§1.3, §3.5, E30); semua modul | P1 |
| **OQ-REF-04** | ✅ **RESOLVED 2026-07-22** — DDL `FC_MSTAPP_MCF` diterima (`FC_MSTAPP_MCF.sql`, 310 tabel + 112 SP + 2 UDF); mapping "pending dump" §3.0–§3.4 di-finalisasi dari DDL. | 07 | ~~P1~~ |
| **OQ-EXTMASTERS-07** *(baru)* | 8 objek dirujuk code acquisition ABSEN dari dump (a.l. `ms_insurance_cover_type` — jalur aktif; `ms_dp_minim_gross_*`; daftar penuh census §3): di-drop, pindah DB, atau ekspor parsial? Absen-dari-dump ≠ absen-dari-prod (Prinsip 3). | 07; migrasi masters | P1 |
| **OQ-EXTMASTERS-08** *(baru)* | `MsPublicHoliday` ada di `FC_ACQ_MCF` DAN `FC_MSTAPP_MCF` — copy mana otoritatif utk migrasi `mst_public_holiday`? | 07 §3.4 | P2 |
| **OQ-DLRPTN-01** | Shape dealer live: `MsDealer` vs `MsDealer1` vs backup? Menentukan field census final `DEALER` (field `Phone2/Fax/EmailGroup/IsDefaultMokas` dibawa atau tidak). | 07 §3.2 | P1 |
| **OQ-DLRPTN-05 / OQ-REF-05 / OQ-EXTMASTERS-05** | `MsBank.PasscodeBiBca` live credential? + status rotasi plaintext credential MINIAPI. **Aksi security independen rebuild.** | 07 (E30 bank), infra | P1 |
| **OQ-CUSTMASTER-04 / OQ-DLRPTN-13** | Bagaimana ~27 lookup applicant + master dealer/bank/payment di-maintain hari ini (tidak ada write path di slice)? Ada aplikasi admin sibling di luar repo? Menentukan apakah Tier A perlu diperluas & sumber data migrasi. | 07 seluruh Tier | P1 |
| **OQ-PRODASSET-05** | ✅ **RESOLVED 2026-07-22** — body SP write ada di dump `FC_MSTAPP_MCF`; aturan V1–V9 terverifikasi (§3.3; census KB §5). Uniqueness V1/V7/V8 ✅ ada; referential NIK check ❌ TIDAK ada (V4); level sequencing hanya aturan approver-puncak (V3), kontiguitas ❌ tidak divalidasi (V6). | 07 §3.3/§6 | ~~P1~~ |
| **OQ-MASTERDATA-02** | **TER-KOREKSI SEBAGIAN 2026-07-22** — backend legacy TERNYATA memvalidasi V1–V3/V7–V8 di SP (bukan hanya JS); celah nyata = V4 (NIK tanpa guard) + V6 (kontiguitas level). Cleansing saat import tetap wajib, difokuskan ke dua celah itu; konfirmasi profil pelanggaran di data prod tetap perlu (gabung OQ-MIG-05). | 07 migrasi | P1 |
| **OQ-BE07-01** *(baru)* | **Scope final maker-checker** (BR-BE07-05): daftar resource yang diusulkan + siapa checker per resource (Kepala Cabang utk scope cabang? role HO baru?). Maker-checker di master = kontrol BARU (legacy tidak punya) — butuh sign-off bisnis/COBS. | 07 §6/§7 | P1 |
| **OQ-BE07-02** *(baru)* | Mekanisme & frekuensi sync HR → `EMPLOYEE_MIRROR` di target state (batch/CDC/API), dan apakah field resign (`Fkeluar`) tersedia real-time cukup utk auto-deactivate (BR-BE07-27). | 07 §8 | P1 |
| **OQ-BE07-03** *(baru)* | Sensus D-10 hanya role **cabang**. Role HO (master-data checker HO, Compliance/AML reviewer BR-REG-13, Area/Regional Head yang muncul di PIC picker) — apakah enum diperluas, dan oleh siapa governance-nya? Jangan menambah role diam-diam. | 07 §2/§3.1 | P1 |
| **OQ-MASTERDATA-03** | Apa yang benar-benar mengonsumsi konfigurasi TransTypeHierarchy (router komite = `ms_hierarchy_transaction`, ladder CA = `ms_approval_scheme` — BE-03 BR-AC-6 memisahkan keduanya)? Menentukan kritikalitas & bentuk final entitas §3.3 vs katalog eksternal. | 07, 03 | P2 |
| **OQ-MASTERDATA-01** | Deactivate-only (tanpa hard delete) = kebutuhan audit yang disengaja? Bila ya, upgrade BR-BE07-03 ke `[LOCKED]`. | 07 §6 | P2 |
| **OQ-DLRPTN-04** | Makna type approval-reason `'1'|'2'|'3'|'9'` + apakah `'9'` diekspos (dua path legacy tidak sepakat — BR-APPROVAL-REASON-1). | 07 E29; 03/05 | P2 |
| **OQ-BE07-04** *(baru)* | Scope user: single-branch atau multi-branch (`branch_scope` list)? Legacy: visibilitas branch employee-scoped via `sp_get_branch` (company+NIK). | 07 §3.1 | P2 |
| **OQ-BE07-05** *(baru)* | Model akses menu: role-based murni (USULAN §3.1) vs position-based legacy (BR-EMPLOYEE-2) vs hybrid; dan apakah `USER_MENU_GRANT_SPECIAL` dipertahankan (risiko backdoor D-09). | 07 §3.1/E10-E11 | P2 |
| **OQ-DLRPTN-02** | `ms_credit_source` genuinely lokal atau artefak export? Menentukan apakah CREDIT_SOURCE migrasi bareng acquisition atau masters-service. | 07 §3.4 | P2 |
| **OQ-DLRPTN-07** | Sumber otoritatif BPKB location: `ms_bpkb_location` lokal vs union linked-server `MsLokasiBPKB`? Single source saat migrasi. | 07 E30; kolateral | P2 |
| **OQ-CUSTMASTER-02 / OQ-CUSTMASTER-07** | Lookup "reference type" dipakai apa (carry/split/retire)? Set applicant-type benar-benar hanya `P`/`C` atau ada nilai lain? | 07 E30 | P2 |
| **OQ-PRODASSET-01 / OQ-PRODASSET-03** | Katalog asset otoritatif (asset-* vs item-brand-*)? + endpoint product list legacy = stub — dari mana UI sumber product list sebenarnya? | 07 E30; 01 | P2 |
| **OQ-REF-01** | Nilai `application_type_id` di-parse sistem eksternal (Passnet/GL)? Menentukan `[LOCKED]` vs `[INTENT]` kode. | 07 E30 | P2 |
| **OQ-DLRPTN-10 / OQ-DLRPTN-11** | Arti kode legal entity `PT='2'/'3'` (butuh company master kanonik utk `company_id`) + enum `Dt2Type` kota/kabupaten. | 07 §3.1/E30 | P3 |
| **OQ-BE07-06** *(baru)* | `PUBLIC_HOLIDAY` boleh hard-delete (USULAN E33) atau ikut deactivate-only demi audit kalkulasi due-date historis? | 07 E33 | P3 |
| **OQ-DLRPTN-06 / OQ-DLRPTN-12 / OQ-DLRPTN-15** | `MsInsuranceByDealerR2/R4` hidup atau orphan; insurance source pernah difilter dealer/branch/product; shape hasil `sp_get_insurance_source`. | 07 E30; insurance | P3 |
| **OQ-DLRPTN-08 / OQ-DLRPTN-09** | `sp_get_branch_exception` (stub selalu `'0'`) rule mati atau fitur belum jadi; payment-point butuh branch-scoping? | 07 E30 | P3 |
| **OQ-MASTERDATA-07** | Layar Dukcapil/Fidusia/`/CRUD` demo masih reachable dari menu live? (Menentukan data migrasi menu; `/CRUD` = `[ARTIFACT]` discard.) | 07 E9 seed | P3 |

> Catatan marker-fidelity: `[LOCKED]` (D-09 no-super-user; D-10 role census; BR-SHELL-1 no-password-store; BR-EMPLOYEE-1 HR one-way; `trans_type_id_prefix` + format `transaction_type_code` external-FK; KTP/NPWP dealer; account number payout; `regency_id_OJK` + 4 lookup OJK-coded; `BranchIdPassnet`; `GL_TRANSACTION_TYPE_LINK`) = WAJIB dipertahankan. `[INTENT]` = outcome dijaga, mekanisme bebas (deactivate-only, branch-scoping dealer, pagination standar, active-only default). `[ARTIFACT]` do-not-replicate: substring identity-type match (EC5 `10-...`), notes-as-join-key (EC7 `11-...`), name-literal sub-dealer (EC6 `11-...`), silent-success error swallowing (EC1/EC12 `11-...`; fuel `12-...`), `IMasters` NotImplementedException one-size-fits-all (EC14 `11-...`), file-path columns di dealer, hardcoded company→folder mapping (EC7 `66-...`), `TOP 1` tanpa `ORDER BY` fallback (EC4 `11-...` — milik disbursement, dicatat agar tidak menular), `/CRUD` demo (EC1 `66-...`), vestigial hidden fields `status_approver`/`notifikasi_hari` force-set JS. `[OPEN]`/OQ = JANGAN diselesaikan diam-diam — lihat tabel §11.
