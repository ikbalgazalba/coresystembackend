# PRD — Menu Master Data (User, Dealer, dst.) [FE]

> **Audience**: Tim Frontend (FE). **Target stack**: **Next.js** `[LOCKED per D-12]`. **Tanggal**: 2026-07-14.
> **Pasangan BE**: `docs/prd/acquisition/BE-07-master-data-menus.md` — kontrak API di §8 dokumen ini WAJIB konsisten dengan §4/§5 file itu (endpoint E1..E38); endpoint yang dibutuhkan layar tetapi TIDAK ada di BE PRD dicatat sebagai **GAP** di §11 (tidak dikarang). Catatan: **E38** (`/number-formats`, admin `cfg_number_format`) ada di BE-07 §4 tetapi BELUM punya layar di dokumen ini — lihat **GAP-FE07-08** §11 (tidak dikarang).
> **Sumber otoritatif**: `.mega-sdd/knowledge-base/.sp-manifests/_ACQUISITION-GROUND-TRUTH.md` v2 (alur final 16-STEP, PDF 08072026) + `.mega-sdd/knowledge-base/.sp-manifests/_MEETING-DECISIONS-2026-07.md` (D-01..D-12, terutama **D-08** mandat modul, **D-09** `[LOCKED]`, **D-10** `[LOCKED]`) + KB FE `60-frontend/66-master-data-screens.md` (satu-satunya layar master legacy yang bekerja: TransTypeHierarchy; pola pagination BR-MASTERDATA-14; §3a eksplisit N/A) + KB FE `60-frontend/60-app-shell-auth-navigation.md`, `60-frontend/67-client-side-behavior.md` (konvensi shell & shared components via FE-00).
> **Status**: **MODUL BARU** (mandat D-08 — "Add: Menu Master (User, Dealer, etc)" `[VERIFIED — doc][INTENT]`). Mayoritas layar TIDAK punya padanan legacy: legacy tidak punya write-path master data yang bekerja end-to-end (BE-07 §"kapabilitas" — `IMasters` insert/update `NotImplementedException`); satu-satunya evidence layar admin adalah **TransTypeHierarchy** (`66-master-data-screens.md §1`). Layar legacy = **EVIDENCE, bukan mandat desain**; UX Next.js baru ditandai **USULAN**. NFR: **responsive mobile + desktop**. **Super-user TIDAK ADA** (D-09 `[LOCKED]`).

Modul FE **07-master-data-menus** adalah presentation layer modul administrasi master data BE-07: layar CRUD untuk master **Tier A** yang dimiliki rebuild acquisition (User/role, Menu + grants, Dealer family, konfigurasi Transaction-Type Hierarchy, approval reason, credit source, blacklist override, public holiday, general parameter, promotion line text, GL link) + **inbox checker** untuk envelope maker-checker (BE-07 E37). Modul ini **bukan salah satu dari 16 STEP** alur acquisition (GT v2) — ia adalah **konfigurasi lintas-step** yang menjadi prasyarat alur itu: dealer master mengisi picker intake (STEP 1–9), definisi Transaction-Type Hierarchy menentukan routing komite STEP 12 (`sp_get_next_approval_scheme` by Plafond + Risiko — eksekusi milik 03, GT `:49-54`), approval reason dipakai disposisi RFA/committee/Vertel/NPP (STEP 9/12/14/15 — BE-07 §10), dan menu tree + user provisioning dikonsumsi app-shell untuk semua layar (BE-07 E6). Misconfiguration di layar hierarchy diam-diam merusak routing approval di hilir (`66-master-data-screens.md` header criticality) — karena itu semua guard otoritatif ada **server-side di BE-07** (fix OQ-MASTERDATA-02 — BR-BE07-15..17); FE hanya me-mirror sebagai UX preventif.

---

## 1. Ruang Lingkup & Kepemilikan

### 1.1 Screens yang DIMILIKI modul ini

| Screen | Fungsi | Evidence legacy | Sumber |
|---|---|---|---|
| **SCR-MST-01 — User List** | Listing/search `APP_USER` + filter role/branch/status; entry lifecycle deactivate/reactivate. | **TIDAK ADA** (net-new — legacy tanpa RBAC/user admin) | D-08; D-09/D-10; BE-07 §3.1, E1 |
| **SCR-MST-02 — User Form (Provision/Edit) + Detail** | Provision user dari employee mirror (picker E8), pilih role enum D-10, scope branch/company; detail + grants efektif. | TIDAK ADA (net-new) | BE-07 E2/E3/E4/E5/E7/E8 |
| **SCR-MST-03 — Menu & Grants** | CRUD menu tree (E9) + grant menu per role (E10) (+ special grant per user E11 — **TIDAK dirender by default**, OQ-BE07-05). Proteksi `trans_type_id_prefix` (maker-checker + impact list). | TIDAK ADA layar admin menu di slice KB (menu tree hanya dikonsumsi shell — `66-...§1` item 4) | BE-07 E9/E10/E11; BR-BE07-14 |
| **SCR-MST-04 — Dealer List** | Listing/search dealer; filter `branch_id`/`is_used_car`/`status`; entry lifecycle. | TIDAK ADA layar admin dealer (legacy read-only; OQ-DLRPTN-13) | D-08; BE-07 §3.2, E12/E15 |
| **SCR-MST-05 — Dealer Detail/Form (multi-tab)** | Tab: Profil (E13/E14), Personnel (E16), Rekening Bank (E18 — **maker-checker**), Akses Cabang (E19), Dokumen (E20). | TIDAK ADA (net-new) | BE-07 E13..E20 |
| **SCR-MST-06 — Dealer Job Titles** | CRUD job title + `dealer_payment_code`. | TIDAK ADA (net-new) | BE-07 E17 |
| **SCR-MST-07 — Transaction-Type Hierarchy (drill-down 3 level)** | Konfigurasi Code → Type → Approval Hierarchy ladder + **PIC picker** (modal employee). Satu-satunya layar ber-evidence legacy penuh. | `Views/TransTypeHierarchy/Index.cshtml:1-671` + `Scripts/TransTypeHierarchy/Index.js:1-2244` `[VERIFIED]` | `66-master-data-screens.md §1/§3/§4`; BE-07 E22..E28 |
| **SCR-MST-08 — Master Operasional (keluarga layar list+form)** | Approval reasons (E29), credit sources + mapping branch (E31), blacklist overrides (E32 — **maker-checker**), public holidays (E33), general parameters (E34), promotion line texts (E35), GL links (E36 — **maker-checker**, read + update ter-audit). | TIDAK ADA (net-new; tabel ada, CRUD tidak — mis. blacklist override OQ-ACQCAS-08) | BE-07 §3.4, E29..E36 |
| **SCR-MST-09 — Inbox Change-Request (Checker)** | Daftar change-request `pending_approval` per resource + keputusan Approve/Reject (E37). Kontrol **BARU** (bukan paritas legacy). | TIDAK ADA (kontrol baru — BE-07 BR-BE07-05: "legacy TIDAK punya maker-checker di master") | BE-07 E37, §7.2 |

Komponen milik modul: **MasterDataTable** (formalisasi konvensi pagination BR-MASTERDATA-14 `[VERIFIED][INTENT]` menjadi SATU komponen reusable — `66-...§7`; BE-07 BR-BE07-20), **EmployeePickerModal** (pengganti PIC picker modal legacy — `66-...§4`; sumber E8/E28), **PendingChangeBadge** (indikator record ber-change-request), **ImpactWarningPanel** (daftar transaction-type ter-impact saat mutasi prefix — BE-07 AC-5), **TierBadge** (penanda `source_tier` lookup — BE-07 E30). Bentuk komponen Next.js = USULAN.

### 1.2 BUKAN milik modul ini (non-goal)

| BUKAN dimiliki | Pemilik | Catatan |
|---|---|---|
| **Layar DukcapilResult & UploadFidusia** | Domain kolateral/integrasi (`31-collateral-bpkb-fidusia`, `50-integrations/dukcapil.md`) | Dua layar "Global" pada slice KB 66 BUKAN master data — TIDAK dibawa ke modul ini (BE-07 §1.2). |
| **`/CRUD` demo screens** | — | `[VERIFIED][ARTIFACT]` discard: demo/prototype berlabel "TEST CRUD", tidak ter-link dari layar mana pun (`66-...§1` item 3, §9 EC1). Yang dipertahankan hanya *konvensi pagination*-nya (→ MasterDataTable). |
| **Autentikasi, session, branch pick, render menu shell** | FE-00 OVERVIEW (KB `60-app-shell-auth-navigation.md`) | Modul ini hanya menyediakan layar admin data authz; konsumsi menu efektif (E6) milik app-shell. Identitas session = stamp `created_by`/maker (BR-SHELL-1 `[LOCKED]`). |
| **Eksekusi routing approval** (walking ladder, inbox komite, eskalasi) | FE-03 / BE-03 | Modul ini memiliki layar *definisi* ladder saja (BE-07 §1.1; `12-product-asset-master.md §8` via BE-07). |
| **Create/update employee** | HR system (system-of-record) | Mirror read-only `[LOCKED]` BR-EMPLOYEE-1 (BE-07 §1.2) — FE TIDAK punya form employee; hanya picker read-only (E8). |
| **Customer master (`tr_CIF`)** | 05-npp (upsert saat aktivasi — D-01 Step 15) | FE-07 tidak merender record customer. |
| **CRUD master pricing/insurance rate** | Ditunda (OQ-EXTMASTERS-01 + D-07/OQ-MEET-06) | `[LOCKED]` rate OJK; tidak ada layar di fase ini. |
| **Katalog eksternal Tier C (310 master `FC_MSTAPP_MCF` — DDL ✅ census 2026-07-22)** | `[OPEN]` OQ-EXTMASTERS-01 (sisa: ownership) | FE-07 TIDAK membuat layar CRUD untuk Tier C; konsumsi lookup (E30) read-only. Layar browse katalog Tier C = TIDAK dirender by default (GAP-FE07-06). |
| **Shared components** (DataTable dasar, LookupDialog, ConfirmDialog, DateField, CurrencyField, FileUploadField, Toast/Alert, BusyButton) | FE-00 OVERVIEW (KB `67-client-side-behavior.md`) | FE-07 mengkonsumsi; §10. |

### 1.3 Reengineering mandate FE (bukan mirror legacy)

- **Guard hierarchy WAJIB tetap di server** — legacy meng-enforce SELURUH invariant ladder (Level-1, next-PIC) hanya di browser JS (`66-...§10` OQ-MASTERDATA-02); rebuild: BE-07 menutupnya server-side (BR-BE07-15..17), FE me-mirror sebagai UX preventif + memetakan error `422 HIERARCHY_RULE_VIOLATION`/`NEXT_PIC_REQUIRED`/`PIC_RESIGNED` balik per-field (§7).
- **Panel show/hide legacy TIDAK direplikasi** — drill-down TransTypeHierarchy legacy = 1 halaman dengan ~20 DOM write manual per transisi panel, 2244 baris untuk 8 state UI (`66-...§9` EC8; §8A `[ARTIFACT]`). Rebuild: routing/component state Next.js biasa; outcome drill-down (Code → Type → Ladder ter-scope) dipertahankan `[INTENT]`.
- **Satu komponen list/pager** — konvensi pagination yang di legacy di-copy-paste per layar (+bug pager wrong-variable EC2 & guard tautologis EC3 `[VERIFIED]` `66-...§9`) diformalkan jadi MasterDataTable tunggal; bug TIDAK direplikasi (disable Previous via cek `page > 1` nyata).
- **Idle timer layar-lokal TIDAK direplikasi** — timer 50 menit screen-local + `clearInterval(x)` variabel undeclared (`66-...§9` EC4/EC5 `[OPEN]`) dibuang; satu mekanisme session-timeout milik FE-00.
- **Vestigial hidden fields TIDAK dirender** — `status_approver` ("Normal Approver") & `notification_day` ("1") force-set JS legacy `[VERIFIED][ARTIFACT]` (`66-...§4`) tidak punya kontrol UI di rebuild (BE-07 §3.3 menandai evaluasi retire).
- **Mapping TIDAK di-derive client-side** — legacy Edit form men-derive Mapping dari `substring(0,2)` kode (BR-MASTERDATA-5 `[INFERRED]`, konvensi belum terjamin — OQ-MASTERDATA-08); rebuild menampilkan nilai `mapping` TERSIMPAN dari server (BR-BE07-18) dan tidak menghitung ulang.
- **Role-gating eksplisit** — legacy: tidak ada gate role di layar master mana pun selain "session ada" (BR-MASTERDATA-13 `[VERIFIED][OPEN]`; OQ-ACTORS-02/OQ-MASTERDATA-04); rebuild WAJIB gate per layar sesuai kolom Auth/Role BE-07 §4 (§2). **Super-user dihapus** (D-09 `[LOCKED]`): tidak ada affordance bypass; **maker ≠ checker** (konsisten D-01 Step 11) — FE menyembunyikan aksi approve untuk maker, BE tetap menolak `403 SELF_APPROVAL_BLOCKED`.
- **Kegagalan lookup = error nyata** — do-not-replicate string-match "service mati" yang menyamarkan error (`66-...§9` EC9 `[ARTIFACT]`) dan silent-empty; FE membedakan empty vs fetch-failed (BR-CSB-11) dan merender `503 LOOKUP_SOURCE_UNAVAILABLE` sebagai kegagalan + Retry (BE-07 BR-BE07-22).
- **Field credential-shaped TIDAK PERNAH dirender** — `MsBank.PasscodeBiBca` **[REDACTED-SECRET]** tidak diekspos endpoint mana pun (BE-07 BR-BE07-25); FE tidak punya kolom/field untuk itu dalam bentuk apa pun.

---

## 2. Aktor & Peran (akses per screen, role-gating)

Role census cabang target-state (D-10 `[LOCKED]`): **CMO, Marketing Head, Credit Analyst, Kepala Cabang, Credit (Admin)** — hierarki approval tergantung **skala risiko**. Legacy tidak punya role-gating layar master (BR-MASTERDATA-13 `[OPEN]`). Pemetaan BE-07 §2: maker master operasional cabang = **Credit (Admin)** (`[INFERRED]` + D-10); checker = kandidat **Kepala Cabang**/role HO (`[OPEN]` OQ-BE07-01); admin HO & perluasan enum role = `[OPEN]` OQ-BE07-03. Mekanisme role claim final menunggu arsitektur ITEC (D-11) — [OPEN] OQ-ARCH-STACK.

| Aktor | SCR-MST-01/02 User | SCR-MST-03 Menu | SCR-MST-04/05/06 Dealer | SCR-MST-07 Hierarchy | SCR-MST-08 Operasional | SCR-MST-09 Inbox Checker |
|---|---|---|---|---|---|---|
| **Credit (Admin)** (maker — BE-07 §2 `[INFERRED][INTENT]`) | Full (E1..E5) | Full (E9/E10); mutasi prefix → jalur checker | Full (E12..E20); write sensitif → jalur checker | — (lihat Hierarchy Admin) | Full; E32/E36 → jalur checker | — (maker melihat status CR miliknya di layar asal) |
| **Hierarchy Administrator** (BE-07 §2 `[VERIFIED][INTENT]` — konfigurasi ladder per branch) | — | — | — | Full (E22..E28) + jalur checker utk type/ladder | — | — |
| **Checker (Kepala Cabang / role HO — `[OPEN]` OQ-BE07-01)** | Read (E1/E3) | Read | Read | Read | Read | **Full**: review + Approve/Reject (E37) — hanya CR yang BUKAN submit-annya sendiri |
| **CMO / Marketing Head / Credit Analyst** | [OPEN] — read-only atau tanpa akses; matrix menu final per role = GAP-FE07-07 §11 | — | — | — | — | — |

Aturan gating FE:
- Aksi Approve/Reject di SCR-MST-09 dirender hanya bila viewer ber-role checker untuk resource ybs **dan** viewer ≠ maker CR itu (UX preventif; enforcement BE `403 SELF_APPROVAL_BLOCKED` — BE-07 §5 E37, §7.2).
- Identitas maker (`created_by`/`maker`) diambil dari session shell (FE-00; BR-SHELL-1 `[LOCKED]`), tidak pernah di-input manual.
- **Tidak ada super user** (D-09 `[LOCKED]`; BE-07 BR-BE07-01): dropdown role di SCR-MST-02 hanya berisi 5 nilai enum D-10 (dari E7); tidak ada tombol/route/mode tersembunyi yang mem-bypass gating maupun grant setara super-user (special-grant E11 tidak dirender by default — OQ-BE07-05, risiko backdoor).
- Branch scope default = branch session (BR-SHELL-3); layar hierarchy ter-scope `branch_id` (paritas konfigurasi per-branch — BE-07 §3.3).

---

## 3. Peta Screen & Route (inventori + usulan route Next.js)

Prefix route group `/masters` mengikuti contoh route menu BE-07 §5 E6 (`"route": "/masters/dealers"`). Seluruh route = **USULAN** (App Router).

| Screen | Route (USULAN) | Jenis render | Guard akses |
|---|---|---|---|
| SCR-MST-01 User List | `/masters/users` | List + server-side pagination/filter (E1) | Credit (Admin) / checker read |
| SCR-MST-02 User Form/Detail | `/masters/users/new` · `/masters/users/[id]` (detail) · `/masters/users/[id]/edit` | Form + picker employee (E8) | Credit (Admin) |
| SCR-MST-03 Menu & Grants | `/masters/menus` (tree) · `/masters/menus/role-grants` | Tree editor + matrix grant per role | Credit (Admin); mutasi prefix → checker |
| SCR-MST-04 Dealer List | `/masters/dealers` | List + filter (E12) | Authenticated read; aksi role-gated |
| SCR-MST-05 Dealer Detail/Form | `/masters/dealers/new` · `/masters/dealers/[code]` (tab: profil, personnel, rekening, akses-cabang, dokumen) | Multi-tab form/detail | Credit (Admin); tab rekening → jalur checker |
| SCR-MST-06 Dealer Job Titles | `/masters/dealer-job-titles` | List + form inline (E17) | Credit (Admin) |
| SCR-MST-07 Hierarchy drill-down | `/masters/approval-hierarchy` (state drill-down via query/nested segment: `?code=` → `?type=` — USULAN, pengganti panel show/hide legacy) | Drill-down 3 level + PIC picker modal | Hierarchy Admin |
| SCR-MST-08 Master Operasional | `/masters/approval-reasons` · `/masters/credit-sources` · `/masters/blacklist-overrides` · `/masters/public-holidays` · `/masters/general-parameters` · `/masters/promotion-line-texts` · `/masters/gl-links` | Keluarga list+form generik (satu layout template) | Credit (Admin); E32/E36 → jalur checker |
| SCR-MST-09 Inbox Change-Request | `/masters/change-requests` | List filter `status`/`resource` (E37) + detail keputusan | Checker (OQ-BE07-01) |

Navigasi & deep-link:
- Entry point: menu modul MASTER dari app-shell (menu tree E6 — milik FE-00).
- Drill-down hierarchy: `/masters/approval-hierarchy` (list Code) → pilih Code → list Type ter-scope → pilih Type → list Ladder ter-scope; tombol "Kembali" per level (outcome navigasi legacy `66-...§3`/§8A dipertahankan `[INTENT]`, mekanisme routing = USULAN pengganti `[ARTIFACT]` panel plumbing).
- Submit write maker-checker sukses (`202`) → kembali ke layar asal dengan **PendingChangeBadge** pada record + link "lihat change request"; checker masuk via `/masters/change-requests` (deep-link per CR: `/masters/change-requests/[id]` — USULAN).
- Record ber-CR pending: form edit di-disable dengan notice "menunggu persetujuan" (mencegah CR ganda utk record sama — UX preventif; kebijakan concurrency server: SP legacy TIDAK punya optimistic-lock (OQ-PRODASSET-05 ✅ RESOLVED 2026-07-22 — BE-07 §3.3 V1–V9); rebuild pakai kontrak CR/maker-checker).

---

## 4. Komposisi Layar & Komponen

Referensi shared components dari FE-00 (KB `67-client-side-behavior.md`): DataTable dgn pembedaan empty-vs-fetch-failed (BR-CSB-11), LookupDialog tunggal (BR-CSB-13), ConfirmDialog dua-tombol (BR-CSB-18/19), DateField picker konsisten (BR-CSB-7), FileUploadField multipart + busy (BR-CSB-15), Toast/Alert envelope-aware (BR-CSB-9), BusyButton anti double-submit (BR-CSB-20), ConditionalRequired (BR-CSB-2). Jangan duplikasi di modul.

### 4.1 SCR-MST-01/02 — User List & Form

- **List**: header → filter bar (role — options E7; branch; status) → MasterDataTable (kolom: NIK, nama employee, role, branch scope, status, aksi) → pagination. Field census response E1 belum dirinci BE → **GAP-FE07-01**. Mobile: card list (USULAN; NFR responsive).
- **Form Provision** (`/masters/users/new`): (1) **EmployeePickerModal** (E8 — search NIK/nama; kolom menampilkan **status resign eksplisit** per fix Edge Case 12 — baris resigned ditandai dan tidak dapat dipilih); (2) role select (E7 — 5 nilai D-10, TANPA `SUPER_USER`); (3) `company_id`, `branch_scope` (single vs multi = [OPEN] OQ-BE07-04 — default render multi-select sesuai shape `list<identifier>` BE-07 §3.1), `activation_date`. Simpan → E2 (+`Idempotency-Key`).
- **Detail**: identitas employee (read-only dari mirror), role, scope, status + `deactivation_reason` (`manual`/`hr_resigned`), grants efektif (E3); aksi Deactivate/Reactivate (E5 — ConfirmDialog; reaktivasi user `hr_resigned` tidak ditawarkan, mirror guard `409` BE-07 §7.3).
- **Edit** (E4): role/scope saja — identitas employee TIDAK editable (ganti orang = user baru; BR-BE07-02).

### 4.2 SCR-MST-03 — Menu & Grants

- **Tree editor** (E9): tampilan menu berhierarki (parent-child); form field per §5.2. Field `trans_type_id_prefix` ditandai ikon kunci + tooltip "input struktural routing approval — perubahan wajib persetujuan checker" (`[LOCKED]` BR-PRODASSET-14 via BE-07 BR-BE07-14). Mutasi prefix → **ImpactWarningPanel** merender daftar transaction-type ter-impact dari response (BE-07 AC-5) + ConfirmDialog → submit masuk `pending_approval` (`202`).
- **Role grants** (E10): matrix role (baris = menu, kolom = 5 role D-10; cell = granted/`is_view_only`); PUT replace-set per role dengan ConfirmDialog.
- **Special grants** (E11): **TIDAK dirender** by default sampai OQ-BE07-05 diputuskan (risiko backdoor super-user terselubung — D-09).
- Tidak ada tombol Delete menu — deactivate-only (BE-07 E9 "Tidak ada DELETE").

### 4.3 SCR-MST-04/05/06 — Dealer family

- **List** (E12): filter `branch_id` (default branch session), `is_used_car`, `status`; kolom (USULAN — census = GAP-FE07-01): dealer_code, dealer_name, authorized, used-car, status, PendingChangeBadge.
- **Form Profil** (E13/E14): field per §5.3; KTP/NPWP tervalidasi format `[LOCKED]`; `parent_dealer_code` via LookupDialog dealer (validasi FK — error `422 PARENT_DEALER_NOT_FOUND` dipetakan; TIDAK ada input via `notes`, fix Edge Case 7 `[ARTIFACT]` per BE-07); `notes` = textarea bebas berlabel "catatan — bukan referensi relasi". Update field identitas legal (KTP/NPWP) & `is_sub_dealer_enabled` → jalur checker (`202`).
- **Tab Personnel** (E16): list + form (nama, TTL, job title dari E17, kontak, `bank_reference_id`, status `A`/inactive); read default hanya `status='A'` + toggle include-inactive.
- **Tab Rekening Bank** (E18): list + form §5.4. **SEMUA write via maker-checker** — submit → `202` + PendingChangeBadge; rekening BELUM eligible payout sebelum approve (BE-07 AC-8). Konten `[LOCKED]` payout target.
- **Tab Akses Cabang** (E19): multi-select branch → PUT replace-set atomik + ConfirmDialog; copy menjelaskan efek: dealer hanya muncul di picker intake cabang ber-akses aktif (BR-BE07-07 `[VERIFIED][INTENT]`).
- **Tab Dokumen** (E20): daftar `doc_type` enum (SIUP, TDP/NIB, NPWP, KTP, MP-master-dealer, SPT) + FileUploadField per jenis; download/preview file = **GAP-FE07-05** (E20 hanya list/upload).
- **Job Titles** (E17): list + form `description`, `dealer_payment_code`, `is_active`.

### 4.4 SCR-MST-07 — Transaction-Type Hierarchy (drill-down)

Outcome layar legacy dipertahankan `[INTENT]` (`66-...§3/§4`); mekanisme = USULAN:

1. **Level 1 — Transaction Code** (E22 list per `branch_id`; E23 upsert): form Add = `form_requester` (dropdown — sumber options = **GAP-FE07-04**), `form_approval` (read-only "[Generated By System]" — paritas legacy), `transaction_code` (uppercase — normalisasi server-side BR-BE07-19; FE boleh mirror as-typed, USULAN). **Satu aksi Save = upsert** (BR-MASTERDATA-6 `[VERIFIED][INTENT]`; BE-07 E23) — tidak ada pasangan tombol insert/update terpisah.
2. **Level 2 — Transaction Type** (E24 list per code; E25): Add = code (pre-filled, disabled), `description`, `mapping` (pilihan dari TRANSACTION_CODE existing — BE-07 E25 "wajib merujuk TRANSACTION_CODE yang ada"), status Active/Non-Active. **Edit = HANYA `is_active`** — code/description/mapping dirender disabled dgn nilai TERSIMPAN dari server (BR-MASTERDATA-4 `[VERIFIED][INTENT]`; BR-BE07-18; TANPA derive `substring(0,2)` — §1.3). Write → jalur checker (BE-07 E25 "maker + checker").
3. **Level 3 — Approval Hierarchy Ladder** (E26 list per type; E27): Add/Edit = `level` (dropdown — sumber options = **GAP-FE07-04**), PIC & Next PIC via **EmployeePickerModal** (E28 — kandidat ter-filter job-title codes eligible `[VERIFIED]` paritas `sp_get_pagination_data_pic_trans_type_hierarchy`), `is_approver` (Yes/No), `is_active`. Mirror client-side rule (UX preventif; otoritatif BE-07 BR-BE07-15..17): **Level = 1 → Is Approver dipaksa "No" + disabled** (BR-MASTERDATA-1 `[VERIFIED][INTENT]`); **Is Approver = Yes → Next PIC dikosongkan + disabled** (BR-MASTERDATA-2); **Is Approver = No → Next PIC required** (BR-MASTERDATA-3). Field vestigial `status_approver`/`notification_day` TIDAK dirender (§1.3). Write → jalur checker.

Setiap level: MasterDataTable + search + tombol Kembali; baris list menampilkan status Active/Non-Active — kebijakan filter inactive di list = [OPEN] OQ-MASTERDATA-01/`66-...§3` (default FE: tampilkan semua + badge status, USULAN).

### 4.5 SCR-MST-08 — Master Operasional (template list+form)

Satu layout template generik (list MasterDataTable + form modal/panel) di-instantiate per resource:

| Resource | Endpoint | Catatan khusus |
|---|---|---|
| Approval Reasons | E29 | Field: `description`, `type`, `is_active`. Filter `type` eksplisit apa adanya (`'1'|'2'|'3'|'9'`) — label makna type TIDAK dihardcode sampai OQ-DLRPTN-04 dijawab. |
| Credit Sources + mapping branch | E31 | Dua panel: master lokal + mapping per branch (`photo_required`, `print_survey_report`). |
| Blacklist Overrides | E32 | Form: `national_id` (`[LOCKED]` key screening), `reason_code` (enum 5-reason car — BE-01 BR-21 via BE-07), `justification` (**wajib** — submit diblok bila kosong), `valid_from`/`valid_until`. **Maker-checker** → `202`. |
| Public Holidays | E33 | Satu-satunya resource dgn tombol Delete (USULAN BE-07; ConfirmDialog) — status final = [OPEN] OQ-BE07-06. |
| General Parameters | E34 | List key/value; parameter `is_updateable=false` dirender **read-only** (tanpa tombol Edit); `is_visible=false` disembunyikan dari listing non-admin (BR-BE07-23). Update → jalur checker. |
| Promotion Line Texts | E35 | `text`, `display_color` (color picker — USULAN), `is_active`. |
| GL Links | E36 | Read + update ter-audit SAJA; mapping `[LOCKED]` CoA; banner peringatan "perubahan wajib sign-off finance" (BR-BE07-24); **maker-checker**; TANPA delete. |

Tidak ada tombol Delete di resource mana pun kecuali Public Holidays (BR-BE07-03 deactivate-only `[INTENT]` → kandidat `[LOCKED]`, OQ-MASTERDATA-01).

### 4.6 SCR-MST-09 — Inbox Change-Request (Checker)

- **List** (E37 `GET /master-change-requests?status=&resource=`): kolom CR-id, resource, action, maker, submitted_at, status; filter status/resource; default `pending_approval`.
- **Detail keputusan**: ringkasan payload perubahan; tampilan **diff before-after** membutuhkan endpoint detail CR yang belum ada di BE-07 §4 → **GAP-FE07-02** (sampai ada: render payload submitted apa adanya dari list/approve response). Aksi **Approve** (+`checker_note` opsional) / **Reject** (+`reject_reason` **wajib**) → ConfirmDialog → E37; approve idempotent by `Idempotency-Key` (BE-07 §5 E37).
- CR milik viewer sendiri: aksi keputusan TIDAK dirender (maker ≠ checker; `403 SELF_APPROVAL_BLOCKED` bila ter-bypass).
- Transisi **maker cancel** ada di state machine BE-07 §7.2 tetapi endpoint-nya tidak ada di §4 → tombol Batalkan CR TIDAK dirender sampai **GAP-FE07-02** dijawab.

---

## 5. Field & Validasi (census per form)

> Kolom **Marker** mengikuti KB: `[LOCKED]` = wajib verbatim; `[INTENT]` = outcome dipertahankan, bentuk bebas; `[ARTIFACT]` = legacy dibuang/diganti; **USULAN** = desain baru FE. Validasi FE = preventif; otoritatif di BE (BR-BE07-15..19 + error §5 BE-07 — gagal = `422`/`409`). Semua field tanggal memakai DateField FE-00 (BR-CSB-7); create memakai `Idempotency-Key` (konvensi BE-07 §4).

### 5.1 User Form → `POST /users` (E2) / `PATCH /users/{id}` (E4)

| # | Field (payload BE) | Label (ID) | Tipe input | Required | Format & validasi FE | Sumber options/nilai | Marker | Sumber |
|---|---|---|---|---|---|---|---|---|
| 1 | `employee_nik` | Employee (NIK) | EmployeePickerModal (read-only setelah pilih) | **Ya** | Harus dari picker E8; free-typing DILARANG; baris resigned tidak dapat dipilih (BE `422 EMPLOYEE_RESIGNED`/`EMPLOYEE_NOT_FOUND`) | E8 (mirror HR, Tier B) | `[LOCKED]` (BR-BE07-02: NIK wajib ada di mirror & tidak resigned) | BE-07 §3.1, §5 E2 |
| 2 | `role` | Role | select | **Ya** | satu dari enum tertutup; nilai lain → BE `422 UNKNOWN_ROLE` | E7 (5 nilai D-10; TANPA SUPER_USER) | **`[LOCKED]`** D-09/D-10 | BE-07 BR-BE07-01, §5 E2 |
| 3 | `company_id` | Perusahaan | select | Tidak (kontrak E2: hanya #1/#2 bertanda wajib) | pilihan valid | company master (Tier B — arti kode `PT='2'/'3'` [OPEN] OQ-DLRPTN-10) | [INTENT] | BE-07 §3.1, §5 E2 |
| 4 | `branch_scope` | Cabang | multi-select | Tidak (idem; scope kosong = kebijakan BE) | pilihan cabang valid | branch master (Tier B) | [INTENT]; single vs multi = [OPEN] OQ-BE07-04 | BE-07 §3.1, §5 E2 |
| 5 | `activation_date` | Tanggal Aktivasi | DateField | Tidak | tanggal valid | picker | [INTENT] | BE-07 §5 E2 |
| — | *(TIDAK dirender)* `password`, `is_super_user` | — | — | — | Field TIDAK ADA dalam bentuk apa pun | — | **`[LOCKED]`** (BR-SHELL-1 via BE-07; D-09) | BE-07 §3.1 |

### 5.2 Menu Form → `POST/PATCH /menus` (E9)

| # | Field | Label | Tipe | Required | Validasi FE | Marker | Sumber |
|---|---|---|---|---|---|---|---|
| 1 | `parent_id` | Induk Menu | tree-picker, nullable | Tidak | node valid | [INTENT] | BE-07 §3.1 MENU |
| 2 | `module` | Modul | text/select | Ya | non-empty | [INTENT] | BE-07 §3.1 |
| 3 | `name` / `route` | Label / Route | text ×2 | Ya | route = path FE valid | [INTENT] | BE-07 §3.1 |
| 4 | `trans_type_id_prefix` | Prefix Trans-Type | text(2) | Ya | 2 karakter; **perubahan → ImpactWarningPanel + jalur checker** (`202`) | **`[LOCKED]`** (BR-PRODASSET-14 via BR-BE07-14) | BE-07 §3.1, E9, AC-5 |
| 5 | `display_order` | Urutan | number | Tidak | integer ≥ 0 | [INTENT] | BE-07 §3.1 |
| 6 | `is_active` | Aktif | toggle | Ya | — | [INTENT] | BE-07 §3.1 |

### 5.3 Dealer Form (Profil) → `POST /dealers` (E13) / `PATCH /dealers/{code}` (E14)

Field per BE-07 §3.2 + contoh request §5 E13. Ringkasan validasi FE:

| # | Field | Required | Validasi FE | Marker | Sumber |
|---|---|---|---|---|---|
| 1 | `dealer_code`, `dealer_name` | **Ya** | non-empty; duplikat → BE `409 DEALER_CODE_EXISTS` | [INTENT] | BE-07 §3.2, §5 E13 |
| 2 | `is_authorized_dealer`, `is_selling_new_product_only`, `is_used_car`, `is_sub_dealer_enabled` | Ya (toggle) | — ; `is_sub_dealer_enabled` = flag eksplisit pengganti match nama literal (BR-BE07-09 `[ARTIFACT]` fix); update-nya → jalur checker | [INTENT] / fix `[ARTIFACT]` | BE-07 §3.2 |
| 3 | `parent_dealer_code`, `group_code`, `main_dealer_code` | Tidak | via LookupDialog dealer (FK eksplisit); invalid → `422 PARENT_DEALER_NOT_FOUND` | [INTENT] (fix Edge Case 7 `[ARTIFACT]`) | BE-07 §3.2, §5 E13 |
| 4 | `ktp_no`, `ktp_name` | **Ya** | format KTP; mismatch → `422` | **`[LOCKED]`** identitas regulasi | BE-07 §3.2 |
| 5 | `npwp_no` | Ya | panjang/format NPWP | **`[LOCKED]`** | BE-07 §3.2 |
| 6 | `address`, `location_id` | Ya | location via lookup E30 (`location`) | [INTENT] | BE-07 §3.2, E30 |
| 7 | `contact_person`, `contact_job_title_id` | Tidak | job title dari E17 | [INTENT] | BE-07 §3.2 |
| 8 | `mou_no`, `mou_date`, `rate_refund` | Tidak | date valid; decimal ≥ 0 | [INTENT] | BE-07 §3.2 |
| 9 | `activation_date` | Ya | date valid | [INTENT] | BE-07 §5 E13 |
| 10 | `notes` | Tidak | textarea bebas — BUKAN join key (copy UI menegaskan) | [INTENT] (fix EC7) | BE-07 §3.2 |

Field ekstra `MsDealer1` (`Phone2/Fax/EmailGroup/IsDefaultMokas`) TIDAK dirender sampai [OPEN] OQ-DLRPTN-01 dijawab (BE-07 §3.2 note).

### 5.4 Dealer Bank Reference → `POST /dealers/{code}/bank-references` (E18 — maker-checker)

| # | Field | Required | Validasi FE | Marker | Sumber |
|---|---|---|---|---|---|
| 1 | `account_type`, `account_description` | Ya / Tidak | non-empty | [INTENT] | BE-07 §3.2, §5 E18 |
| 2 | `bank_id` | **Ya** | pilihan valid lookup `bank` (E30) — kolom credential-shaped TIDAK pernah tampil (BR-BE07-25) | [INTENT] | BE-07 §5 E18, E30 |
| 3 | `account_number`, `account_name` | **Ya** | digits / non-empty | **`[LOCKED]`** payout target | BE-07 §3.2 |
| 4 | `bank_charges_flag` | Ya (toggle) | — | [INTENT] | BE-07 §5 E18 |
| 5 | `activation_date` | Ya | date valid | [INTENT] | BE-07 §5 E18 |

Submit → **`202 Accepted`** + CR `pending_approval` (BUKAN `201`) — FE WAJIB membedakan copy sukses: "Menunggu persetujuan checker", bukan "Tersimpan" (BE-07 §5 E18).

### 5.5 Approval Hierarchy Ladder → `POST/PATCH /approval-hierarchies` (E27)

| # | Field | Required | Validasi FE (mirror preventif) | Marker | Sumber |
|---|---|---|---|---|---|
| 1 | `transaction_type_code` | Ya (pre-filled, disabled) | dari drill-down | `[LOCKED]` format (BR-PRODASSET-7 via BE-07 §3.3) | BE-07 §3.3 |
| 2 | `level` | **Ya** | dropdown; **Level 1 → `is_approver` dipaksa No + disabled** | [INTENT] (BR-MASTERDATA-1 → BR-BE07-15) | `66-...§4/§7`; BE-07 §5 E27 |
| 3 | `pic_nik` (+display `pic_name`) | **Ya** | via EmployeePickerModal E28; resigned tidak dapat dipilih (BE `422 PIC_RESIGNED`/`PIC_NOT_FOUND`) | [INTENT] (BR-BE07-17) | `66-...§4`; BE-07 E28 |
| 4 | `next_pic_nik` (+`next_pic_name`) | **Conditional** | `is_approver=No` → wajib (BE `422 NEXT_PIC_REQUIRED`); `is_approver=Yes` → dikosongkan + disabled (BE `422 NEXT_PIC_MUST_BE_EMPTY`) | [INTENT] (BR-MASTERDATA-2/3 → BR-BE07-16) | `66-...§4/§7`; BE-07 §5 E27 |
| 5 | `is_approver` | **Ya** | Yes/No | [INTENT] | `66-...§4` |
| 6 | `is_active` | **Ya** | Yes/No | [INTENT] | `66-...§4` |
| — | *(TIDAK dirender)* `status_approver`, `notification_day` | — | vestigial hidden force-set legacy | `[ARTIFACT]` discard | `66-...§4/§9`; BE-07 §3.3 |

Duplikat (type, level, PIC) → BE `409 DUPLICATE_LEVEL_PIC` dipetakan sebagai error form (BE-07 §5 E27).

### 5.6 Keputusan Checker → `POST /master-change-requests/{id}/approve|reject` (E37)

| Field | Label | Required | Validasi FE | Sumber |
|---|---|---|---|---|
| `checker_note` | Catatan Checker | Tidak (approve) | max-length ikut BE ([OPEN] tidak dispesifikasi) | BE-07 §5 E37 |
| `reject_reason` | Alasan Penolakan | **Ya** (reject) | non-empty; kosong → submit diblok | BE-07 §5 E18 ("reject_reason wajib") |
| (header) `Idempotency-Key` | — | **Ya** | satu key per intent; retry memakai key sama | BE-07 §5 E37 ("Approve idempotent by Idempotency-Key") |

### 5.7 Filter List standar (semua layar list)

Param `page`, `page_size`, `search` + filter scope per resource; respons `{items[], page, total_pages, record_count}` — kontrak pagination standar tunggal (BE-07 §4 konvensi; BR-BE07-20). Toggle `include_inactive` tersedia di layar admin (default active-only — BR-BE07-21).

---

## 6. Aturan Interaksi & Staging

### 6.1 Staging: §3a KB = N/A — TIDAK ADA staged wizard legacy (jangan dikarang)

KB `66-master-data-screens.md §3a` menyatakan **eksplisit N/A, dan keputusan itu deliberate**: drill-down 3 level TransTypeHierarchy *terlihat* seperti wizard multi-step tetapi setiap level adalah **master independen yang di-persist sendiri-sendiri** — memilih Code hanya mem-filter list Type, memilih Type hanya mem-filter list Ladder; tidak ada data satu level yang dibawa dan di-submit bersama level berikutnya; tiap Save/Update adalah HTTP POST lengkap dengan validasi sendiri (`[VERIFIED][INTENT]`, `Index.js:1-2244`). Kriteria staged-input (wizard menuju satu outcome ter-persist; maker→checker hand-off; status field yang meng-gate input berikutnya) **tidak terpenuhi** di legacy. Layar Global (Dukcapil/Fidusia) pun single-step — dan bukan milik modul ini (§1.2).

Karena itu FE-07 **TIDAK memodelkan staging legacy apa pun**. Dua hal yang BUKAN staging legacy dan diberi label jujur:

1. **Drill-down Code → Type → Ladder** = navigasi antar master bersarang (`[INTENT]` outcome scoping), dirender sebagai routing/nested state biasa — bukan wizard, tanpa carry-forward data antar form.
2. **Maker-checker change-request** (BE-07 §7.2, E37) = **kontrol BARU** hasil desain rebuild (USULAN BE-07 BR-BE07-05, di-ground pada D-01 Step 11 self-approval-blocked + kritikalitas data), **BUKAN paritas legacy** — BE-07 sendiri menegaskan "legacy TIDAK punya maker-checker di master; jangan diklaim paritas". Alur dua-aktornya: maker submit write resource sensitif → `202 pending_approval` → checker (≠ maker) approve/reject di SCR-MST-09 → `applied`/`rejected` (terminal). Scope final resource + siapa checker = [OPEN] OQ-BE07-01.

### 6.2 Conditional rendering & disable/enable

| # | Trigger | Efek UI | Marker | Sumber |
|---|---|---|---|---|
| 1 | Pilih `level = 1` pada form ladder | `is_approver` dipaksa "No" + disabled (mirror; otoritatif server) | [INTENT] (BR-MASTERDATA-1 → BR-BE07-15) | `66-...§7`; BE-07 §6 |
| 2 | `is_approver = Yes` | Next PIC dikosongkan + tombol Select disabled; = No → Next PIC required | [INTENT] (BR-MASTERDATA-2/3 → BR-BE07-16) | `66-...§7`; BE-07 §6 |
| 3 | Edit Transaction Type | Hanya `is_active` enabled; code/description/mapping disabled dgn nilai tersimpan server (BUKAN derive substring) | [INTENT] (BR-MASTERDATA-4; BR-BE07-18); derive = `[INFERRED]` don't-port | `66-...§4/§7`; BE-07 §6 |
| 4 | Resource bertanda maker-checker (E18/E25/E27/E32/E34/E36/E9-prefix/E14-sensitif) | Submit → `202` + copy "menunggu persetujuan"; record asal ber-PendingChangeBadge; form edit record itu disabled selama CR pending | USULAN (kontrol baru BE-07 §7.2) | BE-07 §5 E18, §7.2 |
| 5 | Viewer = maker sebuah CR | Aksi Approve/Reject CR itu TIDAK dirender; direct-call → `403 SELF_APPROVAL_BLOCKED` notice | [INTENT] (D-01 S11; D-09) | BE-07 §5 E37 |
| 6 | Mutasi `trans_type_id_prefix` | ImpactWarningPanel: daftar transaction-type ter-impact (dari response) + ConfirmDialog sebelum submit | **`[LOCKED]`** kontrol (BR-BE07-14) | BE-07 AC-5 |
| 7 | `GENERAL_PARAMETER.is_updateable = false` | Baris tanpa tombol Edit (read-only); bila ter-bypass → `409` dipetakan notice | [INTENT] (BR-BE07-23) | BE-07 §3.4, AC-16 |
| 8 | Pilih employee di picker (E8/E28) | Baris `is_resigned=true` ditandai + tidak selectable (fix Edge Case 12 — resigned/not-found/error = tiga sinyal berbeda) | [INTENT] (BR-BE07-17/22) | BE-07 §3.1, §6 |
| 9 | Toggle `include_inactive` di layar admin | List memuat record inactive + badge status (fitur yang tidak ada di legacy) | [INTENT] (BR-BE07-21) | BE-07 §6 |
| 10 | Blacklist override: `justification` kosong | Submit diblok client-side + BE `422` (AC-15 BE-07) | [INTENT] (BR-BE07-06 `[LOCKED]` adjacency AML) | BE-07 §6, AC-15 |
| 11 | Semua resource konfigurasi (kecuali Public Holidays) | TIDAK ada tombol/route Delete; lifecycle = toggle aktif (deactivate/reactivate + ConfirmDialog) | [INTENT] → kandidat `[LOCKED]` (BR-BE07-03; OQ-MASTERDATA-01) | `66-...§7` BR-MASTERDATA-7; BE-07 AC-14 |
| 12 | Double-click submit/keputusan/upload | BusyButton disabled + spinner selama request (BR-CSB-20); create idempotent via `Idempotency-Key` | [INTENT] | `67-client-side` BR-CSB-20; BE-07 §4 |
| 13 | Semua aksi state-changing | ConfirmDialog dua-tombol vocabulary tunggal Bahasa Indonesia (BR-CSB-18/19) | [INTENT] | `67-client-side` BR-CSB-18/19 |
| 14 | Lookup E30 pada form (bank, location, dst.) | Kegagalan (`503 LOOKUP_SOURCE_UNAVAILABLE`) → error state + Retry pada field/panel, BUKAN dropdown kosong seolah "tidak ada data" | [INTENT] (BR-BE07-22 fix `[ARTIFACT]` silent-success) | BE-07 §5 E30, AC-13 |

---

## 7. State Tampilan

| State | Perilaku | Sumber |
|---|---|---|
| **Loading** | Skeleton rows utk list; skeleton panel utk form/detail; BusyButton utk aksi mutasi (modernisasi full-screen "Please Wait" modal legacy `66-...§5` → inline/skeleton = USULAN) | `66-...§5`; `67-client-side` BR-CSB-8 |
| **Empty vs fetch-failed** | WAJIB dibedakan: "Belum ada data" + CTA tambah vs "Gagal memuat data" + Retry + `correlation_id`; kegagalan lookup Tier C = failed, BUKAN empty (BR-BE07-22) | `67-client-side` BR-CSB-11; BE-07 AC-13 |
| **Error request** | Setiap kegagalan punya feedback user-visible (toast/alert + `correlation_id` dari envelope `{code,message,details?,correlation_id}`); DILARANG silent swallow & string-match "service mati" (`66-...§9` EC9 `[ARTIFACT]`) | BE-07 §5; `66-...§9` |
| **Status-driven display (record master)** | `active` → badge "Aktif"; `inactive` → "Non-Aktif" (+`deactivation_reason` utk user: "dinonaktifkan manual"/"resign HR"); record ber-CR pending → **PendingChangeBadge** "Menunggu Persetujuan" + edit disabled | BE-07 §7.1/§7.3 |
| **Status-driven display (change-request)** | `pending_approval` → "Menunggu"; `applied` → "Diterapkan"; `rejected` → "Ditolak" (+`reject_reason`); `cancelled` → "Dibatalkan"; terminal = read-only permanen | BE-07 §7.2 |
| **Error mapping (form)** | `422 UNKNOWN_ROLE`/`EMPLOYEE_NOT_FOUND`/`EMPLOYEE_RESIGNED` → error field employee/role (pesan menyebut D-09/D-10 utk UNKNOWN_ROLE); `409 USER_ALREADY_EXISTS` → notice + link user existing; `422` KTP/NPWP → error field; `409 DEALER_CODE_EXISTS` → error field kode; `422 PARENT_DEALER_NOT_FOUND` → error field parent; `422 HIERARCHY_RULE_VIOLATION`/`NEXT_PIC_REQUIRED`/`NEXT_PIC_MUST_BE_EMPTY`/`PIC_NOT_FOUND`/`PIC_RESIGNED`/`TRANSACTION_TYPE_NOT_FOUND` → error per-field via `details[].field`; `409 DUPLICATE_LEVEL_PIC` → notice form | BE-07 §5 E2/E13/E27 |
| **Error mapping (keputusan/guard)** | `403 SELF_APPROVAL_BLOCKED` → notice "Anda maker change-request ini" (D-01 S11; tanpa jalur bypass — D-09); `409` reaktivasi user resigned → notice; `409` general-parameter non-updateable → notice | BE-07 §5 E37, §7.3, AC-16 |
| **Idempotent replay** | Retry create/approve dgn `Idempotency-Key` sama setelah timeout → hasil pertama dirender tanpa efek/error ganda | BE-07 §4/§5 E37 |
| **Tier C transparency** | Layar yang menampilkan lookup eksternal merender **TierBadge** dari `source_tier` response E30 (mis. "Read-only — katalog eksternal") — TIDAK menawarkan aksi tulis utk Tier B/C | BE-07 §5 E30, §1.3 |
| **Responsive** | Semua screen operable mobile & desktop (NFR); tabel → card list; form multi-tab → accordion/anchor nav + sticky action bar (USULAN) | Catatan rebuild (header) |
| **i18n** | Seluruh label/status/pesan Bahasa Indonesia (konvensi FE-00) | FE-00 §NFR |

---

## 8. Kontrak Konsumsi API (per screen — konsisten BE-07 §4/§5)

Semua request memakai konvensi FE-00 (auth header/session; error envelope `{code, message, details?, correlation_id}`; create ber-header `Idempotency-Key` — BE-07 §4). List: `page`/`page_size`/`search` → `{items[], page, total_pages, record_count}`. Transport REST diasumsikan; final menunggu arsitektur ITEC (D-11) — [OPEN] OQ-ARCH-STACK.

| Screen | Interaksi | Endpoint (BE-07 §4) | Catatan konsumsi |
|---|---|---|---|
| SCR-MST-01 | Muat list user + filter | **E1** `GET /users` | Census kolom = GAP-FE07-01 |
| SCR-MST-02 | Provision user | **E2** `POST /users` | Error mapping §7; `Idempotency-Key` |
| SCR-MST-02 | Detail + grants efektif | **E3** `GET /users/{id}` | — |
| SCR-MST-02 | Ubah role/scope | **E4** `PATCH /users/{id}` | Identitas employee tidak editable |
| SCR-MST-02 | Lifecycle | **E5** `POST /users/{id}/deactivate` / `/reactivate` | Reaktivasi resigned → `409` |
| SCR-MST-02 | Katalog role | **E7** `GET /roles` | 5 nilai D-10 statis |
| SCR-MST-02 / SCR-MST-07 | Picker employee | **E8** `GET /employees` · **E28** `GET /approval-hierarchies/pic-candidates?search=&branch_id=` | E8 = picker umum (status resign eksplisit); E28 = ter-filter job-title eligible |
| SCR-MST-03 | CRUD menu tree | **E9** `GET/POST/PATCH /menus`, `/menus/{id}` | Prefix → jalur checker; tanpa DELETE |
| SCR-MST-03 | Grant per role | **E10** `GET/PUT /roles/{role}/menu-grants` | Replace-set + ConfirmDialog |
| SCR-MST-03 | Special grants | **E11** `GET/PUT /users/{id}/menu-grants-special` | TIDAK dirender by default (OQ-BE07-05) |
| SCR-MST-04 | Muat list dealer | **E12** `GET /dealers` | Filter branch/used-car/status |
| SCR-MST-05 | Create / detail+update dealer | **E13** `POST /dealers` · **E14** `GET/PATCH /dealers/{code}` | Field sensitif → `202` |
| SCR-MST-05 | Lifecycle dealer | **E15** `POST /dealers/{code}/deactivate` / `/reactivate` | — |
| SCR-MST-05 | Personnel | **E16** `GET/POST/PATCH /dealers/{code}/personnel` | Default `status='A'` |
| SCR-MST-06 | Job titles | **E17** `GET/POST/PATCH /dealer-job-titles` | — |
| SCR-MST-05 | Rekening bank | **E18** `GET/POST/PATCH /dealers/{code}/bank-references` | **Semua write → `202` maker-checker** |
| SCR-MST-05 | Akses cabang | **E19** `GET/PUT /dealers/{code}/branch-access` | Replace-set atomik |
| SCR-MST-05 | Dokumen legal | **E20** `GET/POST /dealers/{code}/documents` | Download = GAP-FE07-05 |
| — (bukan FE-07) | Kontak eligible pembayaran | E21 `GET .../payment-eligible-contacts` | Konsumen = disbursement/04; FE-07 TIDAK memanggil |
| SCR-MST-07 | List code per branch | **E22** `GET /transaction-codes?branch_id=` | — |
| SCR-MST-07 | Upsert code | **E23** `PUT /transaction-codes/{branchId}/{code}` | Satu aksi Save (BR-BE07-19) |
| SCR-MST-07 | List type per code | **E24** `GET /transaction-types?transaction_code=` | — |
| SCR-MST-07 | Insert/update type | **E25** `POST/PATCH /transaction-types`, `/{code}` | PATCH hanya `is_active`; → jalur checker |
| SCR-MST-07 | List ladder per type | **E26** `GET /approval-hierarchies?transaction_type_code=` | — |
| SCR-MST-07 | Insert/update ladder | **E27** `POST/PATCH /approval-hierarchies`, `/{id}` | Error server-side dipetakan per-field (§7); → jalur checker |
| SCR-MST-08 | Approval reasons | **E29** `GET/POST/PATCH /approval-reasons` | Filter `type` eksplisit, tanpa hardcode subset |
| SCR-MST-05/08 (form) | Lookup referensi | **E30** `GET /lookups/{lookup_key}` | bank/location/dll.; `source_tier` → TierBadge; `503` = failed state |
| SCR-MST-08 | Credit sources + mapping | **E31** `GET/POST/PATCH /credit-sources` + `/branches/{id}/credit-sources` | — |
| SCR-MST-08 | Blacklist overrides | **E32** `GET/POST/PATCH /blacklist-overrides` | **Maker-checker**; justification wajib |
| SCR-MST-08 | Public holidays | **E33** `GET/POST/PATCH/DELETE /public-holidays` | Satu-satunya DELETE (OQ-BE07-06) |
| SCR-MST-08 | General parameters | **E34** `GET/PATCH /general-parameters`, `/{key}` | `is_updateable=false` → read-only |
| SCR-MST-08 | Promotion line texts | **E35** `GET/POST/PATCH /promotion-line-texts` | — |
| SCR-MST-08 | GL links | **E36** `GET/PATCH /gl-transaction-type-links` | **Maker-checker**; tanpa delete |
| SCR-MST-09 | Inbox + keputusan CR | **E37** `GET/POST /master-change-requests`, `.../{id}/approve`, `.../{id}/reject` | Approve idempotent; reject_reason wajib |
| — (bukan FE-07) | Menu efektif user | E6 `GET /users/{id}/menus` | Konsumen = app-shell FE-00; FE-07 hanya layar admin datanya |

**Endpoint yang DIBUTUHKAN layar tetapi TIDAK ada di BE-07 §4** (jangan dikarang — dicatat sebagai GAP §11): detail change-request + diff before-after & endpoint maker-cancel (GAP-FE07-02); read audit trail per record (GAP-FE07-03); sumber options `form_requester` & `level` form hierarchy (GAP-FE07-04); download dokumen dealer + batas file (GAP-FE07-05); enumerasi registry `lookup_key` (GAP-FE07-06); field census response list (GAP-FE07-01).

**Arah sebaliknya — endpoint BE-07 TANPA layar FE**: **E38** `GET/POST/PATCH /number-formats` (admin `cfg_number_format` — BE-07 §3.4/§4; mutasi `code_type` `CREDIT_ID` via jalur checker E37). Layar admin number-format BELUM didefinisikan di SCR-MST mana pun → **GAP-FE07-08** §11; FE-07 TIDAK memanggil E38 sampai diputuskan.

---

## 9. Acceptance Criteria (Given/When/Then)

**AC-FE-1 — Tidak ada affordance super-user (D-09)**
- **Given** SCR-MST-02 dibuka oleh Credit (Admin);
- **When** dropdown role dirender (E7);
- **Then** hanya 5 nilai enum D-10 tampil, TANPA `SUPER_USER` dalam bentuk apa pun; percobaan submit nilai lain (ter-bypass) → `422 UNKNOWN_ROLE` dirender sebagai error field dgn pesan merujuk D-09/D-10 (BE-07 AC-2).

**AC-FE-2 — Picker employee menolak resigned secara eksplisit**
- **Given** NIK `09999` bertanda `is_resigned=true` di mirror;
- **When** maker mencari NIK itu di EmployeePickerModal (E8);
- **Then** baris tampil DENGAN tanda resign eksplisit dan tidak dapat dipilih (bukan hilang diam-diam — tiga sinyal resigned/not-found/error dibedakan); bila ter-bypass, BE `422 EMPLOYEE_RESIGNED` dipetakan ke field (BR-BE07-02/22; BE-07 AC-3).

**AC-FE-3 — Mirror rule Level-1 ladder (paritas BR-MASTERDATA-1, otoritatif server)**
- **Given** form ladder Add dgn `level=1` terpilih;
- **When** field `is_approver` dirender;
- **Then** nilai dipaksa "No" + kontrol disabled (paritas legacy `66-...§7`); bila payload ter-bypass langsung ke API, BE `422 HIERARCHY_RULE_VIOLATION` (BR-BE07-15) dipetakan balik ke field — FE guard bukan pengganti enforcement.

**AC-FE-4 — Next PIC mengikuti Is Approver**
- **Given** `is_approver` diubah Yes → No → Yes;
- **When** form ladder dirender ulang tiap perubahan;
- **Then** Yes: Next PIC dikosongkan + disabled; No: Next PIC required (submit diblok bila kosong; BE `422 NEXT_PIC_REQUIRED`/`NEXT_PIC_MUST_BE_EMPTY` — BR-MASTERDATA-2/3; BR-BE07-16).

**AC-FE-5 — Edit Transaction Type hanya status; mapping TIDAK di-derive**
- **Given** transaction type existing dibuka di form Edit;
- **When** form dirender;
- **Then** hanya toggle Active/Non-Active enabled; code/description/mapping disabled dgn **nilai tersimpan dari server** — TIDAK ada logika `substring(0,2)` di FE (BR-MASTERDATA-4/5; BR-BE07-18; BE-07 AC-11).

**AC-FE-6 — Transaction Code satu aksi Save (upsert)**
- **Given** form Code diisi `form_requester` + `transaction_code`;
- **When** Save ditekan;
- **Then** satu request E23 (PUT upsert) terkirim — tidak ada pemilihan insert-vs-update oleh user (BR-MASTERDATA-6; BR-BE07-19); kode tampil uppercase.

**AC-FE-7 — Tanpa tombol Delete pada master konfigurasi**
- **Given** SCR-MST-03/07/08 (kecuali Public Holidays) dirender untuk role mana pun;
- **When** user mencari aksi hapus;
- **Then** tidak ada tombol/route Delete; lifecycle hanya deactivate/reactivate (BR-BE07-03; `66-...§7` BR-MASTERDATA-7; BE-07 AC-14); Public Holidays punya Delete + ConfirmDialog (USULAN — OQ-BE07-06).

**AC-FE-8 — Alur maker-checker `202`**
- **Given** maker submit rekening bank dealer baru (E18);
- **When** response `202` + CR `pending_approval` diterima;
- **Then** copy sukses berbunyi "menunggu persetujuan" (BUKAN "tersimpan"), record ber-PendingChangeBadge, edit record disabled selama pending, dan rekening TIDAK tampil sebagai aktif (BE-07 AC-8, §7.2).

**AC-FE-9 — Self-approval diblokir di inbox checker**
- **Given** checker membuka SCR-MST-09 dan salah satu CR adalah submit-annya sendiri;
- **When** list/detail dirender;
- **Then** aksi Approve/Reject utk CR itu TIDAK dirender; percobaan direct-call → `403 SELF_APPROVAL_BLOCKED` sebagai notice tanpa jalur bypass (D-01 S11; D-09; BE-07 §5 E37).

**AC-FE-10 — Proteksi prefix menu + impact list**
- **Given** maker mengubah `trans_type_id_prefix` menu `M-040` dari `"02"` ke `"05"`;
- **When** Simpan ditekan;
- **Then** ImpactWarningPanel menampilkan daftar transaction-type ter-impact dari response + ConfirmDialog; setelah konfirmasi, mutasi masuk `pending_approval` (bukan langsung applied) — routing tidak berubah tanpa approve checker (BR-BE07-14; BE-07 AC-5).

**AC-FE-11 — Kegagalan lookup = failed state, bukan empty**
- **Given** backing store Tier C tidak reachable;
- **When** form membuka lookup `marital-status`/`bank` (E30) dan menerima `503 LOOKUP_SOURCE_UNAVAILABLE`;
- **Then** field/panel merender state gagal + Retry + `correlation_id` — BUKAN dropdown kosong; empty ("Belum ada data") dan failed dibedakan (BR-BE07-22; BE-07 AC-13; BR-CSB-11).

**AC-FE-12 — Guard general parameter**
- **Given** parameter `X` dgn `is_updateable=false`;
- **When** listing E34 dirender;
- **Then** baris `X` tanpa tombol Edit; percobaan PATCH ter-bypass → `409` dirender notice + nilai tidak berubah di UI (BR-BE07-23; BE-07 AC-16).

**AC-FE-13 — Blacklist override ter-governance**
- **Given** form override diisi tanpa `justification`;
- **When** Submit ditekan;
- **Then** submit diblok client-side dgn error field; dgn justification + masa berlaku lengkap → `202 pending_approval` + PendingChangeBadge (BR-BE07-06; BE-07 AC-15).

**AC-FE-14 — Satu komponen pagination, bug pager tidak direplikasi**
- **Given** list ladder dibuka pada halaman 1 dan list type pada halaman 3;
- **When** tombol Previous dirender di keduanya;
- **Then** disable Previous dihitung dari state pager MASING-MASING list (cek `page > 1` nyata) — bug wrong-variable EC2 & guard tautologis EC3 legacy tidak muncul (`66-...§9`; BR-BE07-20).

**AC-FE-15 — Drill-down scoping tanpa carry-forward**
- **Given** operator memilih Code `02` lalu Type `0224000004`;
- **When** list Ladder dirender;
- **Then** hanya baris ladder milik type itu tampil (E26 scoped); form Add ladder TIDAK membawa nilai form level lain (paritas §3a N/A — tiap level submit independen); tombol Kembali mengembalikan ke level sebelumnya.

**AC-FE-16 — Tanpa silent failure & tanpa idle timer lokal**
- **Given** request apa pun (E1..E37) gagal;
- **When** response error/network failure diterima;
- **Then** feedback user-visible tampil (pesan per `code` + `correlation_id`); TIDAK ada string-match "Object reference..." (`66-...§9` EC9 `[ARTIFACT]`); dan TIDAK ada countdown idle 50-menit screen-local (EC4/EC5) — session timeout tunggal milik FE-00.

**AC-FE-17 — Responsive**
- **Given** viewport mobile (≤ ~640px);
- **When** SCR-MST-05 (multi-tab) dan SCR-MST-07 (drill-down) dibuka;
- **Then** semua tab/level, picker modal, dan action bar tetap operable (tab → accordion, tabel → card list, sticky action bar) — NFR responsive.

---

## 10. Dependency

| Dependency | Jenis | Yang dikonsumsi | Sumber |
|---|---|---|---|
| **BE-07-master-data-menus** | API | Seluruh endpoint §8 (E1..E5, E7..E20, E22..E37); error envelope + `Idempotency-Key`; state machine §7.1–7.3; enum role D-10; kontrak pagination standar | BE-07 §4/§5/§7 |
| **FE-00 OVERVIEW (app shell & shared)** | FE | AppShell (menu E6, session identity BR-SHELL-1 `[LOCKED]`, branch context BR-SHELL-3), DataTable, LookupDialog, ConfirmDialog, DateField, FileUploadField, Toast/Alert, BusyButton, ConditionalRequired | `60-app-shell-auth-navigation.md`; `67-client-side-behavior.md` |
| **HR mirror (via BE-07 Tier B)** | data | Picker employee E8/E28 (prasyarat provisioning user & PIC) — mekanisme sync [OPEN] OQ-BE07-02 | BE-07 §8 |
| **FE-01 intake (downstream)** | konsumen | Dealer picker intake membaca hasil konfigurasi modul ini (E12 + BR-BE07-07/08/09) — konsumen data, bukan dependency FE-07 | BE-07 §10 |
| **FE-03 approval-committee (downstream)** | konsumen | Definisi ladder + reason yang dikonfigurasi di sini dikonsumsi routing/disposisi STEP 12 (eksekusi milik 03; konsumen nyata TERKONFIRMASI: admin surface menulis langsung ke `ms_hierarchy_transaction` yang di-walk router BE-03 (OQ-MASTERDATA-03 ✅ RESOLVED 2026-07-22 — BE-07 §3.3)) | BE-07 §10; GT `:49-54` |
| **FE-05/FE-06 (downstream)** | konsumen | Reason-code Vertel/NPP (STEP 14/15) — menjawab GAP-FE05-01/GAP-FE06-01 sisi penyedia layar admin-nya (endpoint lookup reason utk modul konsumen tetap keputusan BE — lihat GAP terkait di FE-05/FE-06 §11) | BE-07 §10; GT `:63-71` |
| **Arsitektur ITEC (D-11)** | eksternal | Mekanisme auth/role claim, transport final, object storage dokumen (E20) | D-11; OQ-ARCH-STACK |

Urutan konsumsi build (mengikuti urutan build BE-07 §10 — USULAN): layar User/Menu paling awal (dibutuhkan app-shell), lalu Dealer family, lalu Hierarchy config (dibutuhkan sebelum 03 diuji end-to-end), lalu master operasional + inbox checker.

---

## 11. Keputusan Dibutuhkan (Open Questions & GAP)

> [OPEN] dari KB + BE-07 + gap kontrak — **jangan diselesaikan diam-diam**. FE memakai default paling aman (fail-closed / fitur tidak dirender) sampai diputuskan.

| ID | Pertanyaan | Prioritas | Dampak FE |
|---|---|---|---|
| **GAP-FE07-01** | Field census response list (E1 user, E12 dealer, E22/E24/E26 hierarchy, E29.. operasional) belum dirinci di BE-07 §5 (hanya E2/E6/E13/E18/E27/E30/E37 ber-contoh) → kolom list & param filter final. | P2 | §4.1–4.6 kolom MasterDataTable |
| **GAP-FE07-02** | SCR-MST-09 butuh (a) endpoint **detail change-request** + diff before-after utk review checker, dan (b) endpoint **maker cancel** (transisi `cancelled` ada di BE-07 §7.2 tetapi §4 E37 hanya list/approve/reject). Tambahkan di BE-07 atau konfirmasi cukup payload list. | P1 | §4.6; tanpa (a) checker memutus tanpa melihat diff; tombol Batalkan tidak dirender sampai (b) ada |
| **GAP-FE07-03** | BR-BE07-04 mewajibkan audit (who/when/before-after) utk semua mutasi Tier A, tetapi TIDAK ada endpoint read audit di §4 — apakah FE-07 merender riwayat perubahan per record (USULAN: panel riwayat di detail)? | P2 | Detail SCR-MST-02/05/07/08 |
| **GAP-FE07-04** | Sumber options dropdown `form_requester` & `level` pada form hierarchy: legacy branch-scoped lookup (`GetDataRequester`/`GetDataLevel` — `66-...§4`/BR-MASTERDATA-15 `[VERIFIED]`); BE-07 §4 tidak memuat endpoint padanannya dan registry E30 §3.5 tidak menyebut key-nya. | P2 | §4.4 form Code/Ladder |
| **GAP-FE07-05** | Download/preview dokumen dealer (E20 hanya GET list/POST upload; `file_ref` = object storage) + batas tipe/ukuran file upload. | P3 | §4.3 Tab Dokumen |
| **GAP-FE07-06** | Enumerasi registry `lookup_key` (E30 per-key; daftar = konfigurasi BE-07 §5) — dibutuhkan HANYA bila layar browse katalog Tier C dibuat. Default FE: layar itu TIDAK dirender (Tier C bukan milik modul — §1.2). | P3 | Inventori §1.1 |
| **GAP-FE07-07** | Matrix akses menu final per role D-10 utk layar master (apakah CMO/Marketing Head/Credit Analyst punya read-only?). Legacy: tanpa gate sama sekali (BR-MASTERDATA-13 `[OPEN]`). (Mirror GAP-FE06-06.) | P2 | §2; guard route §3 |
| **GAP-FE07-08** | BE-07 **E38** (`/number-formats` — admin `cfg_number_format`, konsumen `credit_id` BE-01) belum punya layar di FE-07: apakah ditambahkan sebagai resource SCR-MST-08 (list+form template, mutasi `CREDIT_ID` → jalur checker) atau admin-nya di luar FE (seed/ops)? Default FE: layar TIDAK dirender, E38 TIDAK dipanggil. | P2 | Inventori §1.1; §4.5; §8 |
| **OQ-BE07-01** | Scope final maker-checker + siapa checker per resource (Kepala Cabang scope cabang? role HO?) — kontrol BARU, butuh sign-off bisnis/COBS. | P1 | §2 gating SCR-MST-09; daftar resource ber-`202` §6.2 #4 |
| **OQ-BE07-03** | Role HO (checker HO, admin HO) — perluasan enum D-10 & governance-nya. Jangan menambah role diam-diam. | P1 | §2; dropdown role §5.1 |
| **OQ-BE07-04** | Scope user single-branch vs multi-branch (`branch_scope`). Default FE: multi-select (mengikuti shape BE-07 §3.1). | P2 | §5.1 #4 |
| **OQ-BE07-05** | `USER_MENU_GRANT_SPECIAL` dipertahankan? Risiko backdoor super-user (D-09). Default FE: layar E11 TIDAK dirender. | P2 | §4.2 |
| **OQ-BE07-06** | Public holiday hard-delete (USULAN E33) vs deactivate-only. Default FE render Delete + ConfirmDialog mengikuti E33; siap dicabut. | P3 | §4.5 |
| **OQ-MASTERDATA-01** | Deactivate-only = kebutuhan audit disengaja? (menentukan upgrade BR-BE07-03 → `[LOCKED]` + kebijakan tampilan row inactive di list drill-down — `66-...§3` "tidak terkonfirmasi apakah list memfilter inactive"). | P2 | §4.4; §6.2 #11 |
| **OQ-MASTERDATA-03** | ✅ **RESOLVED 2026-07-22** — layar ini feeder LANGSUNG matrix routing komite (`ms_hierarchy_transaction` = walking table BE-03; evidence BE-07 §3.3). Kritikalitas layar = TINGGI; ImpactWarningPanel wajib. | ~~P2~~ | §4.4; ImpactWarningPanel |
| **OQ-MASTERDATA-07** | Layar Dukcapil/Fidusia/`/CRUD` masih reachable dari menu live? Menentukan data seed migrasi menu (E9) — `/CRUD` = `[ARTIFACT]` discard. | P3 | §4.2 seed menu |
| **OQ-DLRPTN-01** | Shape dealer live (`MsDealer` vs `MsDealer1`): field `Phone2/Fax/EmailGroup/IsDefaultMokas` dirender atau tidak. Default: tidak dirender. | P1 | §5.3 |
| **OQ-DLRPTN-04** | Makna type approval-reason `'1'|'2'|'3'|'9'` — label human-readable filter type di SCR-MST-08. Default: tampilkan kode mentah tanpa label makna. | P2 | §4.5 |
| **OQ-EXTMASTERS-01** | Katalog Tier C naik Tier A? Menentukan apakah FE-07 kelak menambah layar CRUD utk lookup eksternal (saat ini: read-only, tidak ada layar). **Phase-1 blocker** (umbrella). | P1 | Inventori §1.1; TierBadge §7 |
| **OQ-ARCH-STACK** *(direvisi D-12)* | FE = Next.js `[LOCKED]`; masih [OPEN]: mekanisme auth/session & role claim (menunggu ITEC D-11), strategi rendering, transport final. | P1 | Guard route §3; konvensi fetch §8 |

**Tertutup oleh keputusan meeting / dokumen ini (tidak lagi open utk modul ini):**
- **Super-user UI** — D-09 `[LOCKED]`: tidak ada affordance/role/grant setara super-user di layar mana pun; `403 SELF_APPROVAL_BLOCKED` dirender sebagai notice tanpa jalur alternatif.
- **Staging layar master** — KB 66 §3a eksplisit **N/A** (deliberate): drill-down = navigasi antar master independen; FE-07 TIDAK memodelkan wizard/staged-input legacy (§6.1). Maker-checker = kontrol BARU BE-07, diberi label USULAN, bukan paritas.
- **Validasi ladder client-only (OQ-MASTERDATA-02, arah fix)** — arah ditetapkan: otoritatif server-side (BR-BE07-15..17); FE hanya mirror preventif. Yang tersisa milik BE (migrasi data legacy inkonsisten — tetap tercatat di BE-07 §11).
