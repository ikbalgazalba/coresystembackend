# 06 — Constraints

> **TL;DR**: 27 aturan bisnis (BR-BE07-01..27) dari BE-07 §6 + technical constraints inherited umbrella. Regulated master (payout/AML/CoA) = maker-checker WAJIB. No hard delete (deactivate-only). Lookup fail = error eksplisit (`503`), bukan sukses-kosong. `trans_type_id_prefix` + `transaction_type_code` = external-FK `[LOCKED]`. Super-user DIHAPUS. Do-not-replicate EC1/EC5/EC6/EC7/EC12/EC14 + vestigial hidden fields.

## Technical constraints (inherited umbrella + 07-specific)

- **BE = Java** `[LOCKED]` (D-12); framework USULAN Spring Boot 3.x + Spring Modulith + Java 21 (OQ-ARCH-STACK, ITEC D-11). Repo existing: Spring Boot 4.1.1-SNAPSHOT + Java 21.
- **RDBMS = PostgreSQL** USULAN (final ITEC A-2); satu schema + prefix kelas (`docs/DB-CONVENTIONS.md`, ADR-14).
- **Transport** USULAN REST/JSON (OpenAPI 3); envelope `{ code, message, details?, correlation_id }`. Kontrak list terstandar (BR-BE07-20).
- **Zero stored procedure** (ADR-02) — validasi ladder V1-V9 di service Java (BR-BE07-15..17 server-side).
- **ACL boundary** (BR-BE07-26 `[LOCKED]`): NO cross-DB three-part-name / linked-server four-part-name sebagai pola aplikasi baru. Tier C via ACL service API; perubahan semantik transaksional diputuskan sadar (masuk asumsi D-11).
- **AuthN** = LDAP `[LOCKED]` (BR-SHELL-1, no password store); 07 hanya sediakan data authz (role/menu-grant). Branch re-verify server-side (OQ-SHELL-02, inherited).
- **Migrasi** = simulation-first + reconciliation gate (ADR-15); `[LOCKED]` zero-diff (KTP/NPWP dealer, account_number/name payout, CoA GL, `trans_type_id_prefix`, `transaction_type_code`); no-data-left-behind (backup tables = `[ARTIFACT]` schema, data diarsip).

## Business rules (BE-07 §6 — BR-BE07-01..27)

### Governance & role (LOCKED)
- **BR-BE07-01** `[LOCKED]`: Role user = enum tertutup **CMO, MARKETING_HEAD, CREDIT_ANALYST, KEPALA_CABANG, CREDIT_ADMIN**; TIDAK ada nilai/flag super-user di entitas/endpoint/grant mana pun. `ms_trans_super_user` legacy hanya dibaca utk migrasi audit. Percobaan grant setara super-user via special-grant = pelanggaran D-09 (OQ-BE07-05).
- **BR-BE07-02** `[LOCKED]`: `APP_USER` hanya bisa dibuat untuk NIK yang ada di `EMPLOYEE_MIRROR` dan tidak resigned; aplikasi TIDAK menyimpan password (auth = corporate directory LDAP). Tidak ada endpoint create employee.
- **BR-BE07-27** `[INTENT]` (USULAN): User yang HR mirror-nya berubah resigned di-deactivate otomatis oleh sync job (`deactivation_reason=hr_resigned`); grant menu efektif ikut mati. Outcome legacy "posisi berubah → akses berubah" (BR-EMPLOYEE-2) dipertahankan pada re-provisioning role.

### Lifecycle & audit
- **BR-BE07-03** `[INTENT]` → kandidat `[LOCKED]` (OQ-MASTERDATA-01): Master konfigurasi (menu, transaction code/type, hierarchy, reason, dealer, user) TIDAK bisa di-hard-delete — lifecycle hanya create + toggle active/inactive. Pengecualian: `PUBLIC_HOLIDAY` (OQ-BE07-06).
- **BR-BE07-04** `[INTENT]` (USULAN): Setiap mutasi master Tier A tercatat audit (who/when/before-after); mutasi resource maker-checker juga menyimpan change-request + keputusan checker. Legacy tidak punya audit master (tidak ada write path sama sekali).

### Maker-checker (kontrol BARU — legacy TIDAK punya)
- **BR-BE07-05** `[OPEN]` scope final OQ-BE07-01: **Maker-checker WAJIB** untuk: `DEALER_BANK_REFERENCE` (payout `[LOCKED]`), `BLACKLIST_OVERRIDE` (regulated AML), `GL_TRANSACTION_TYPE_LINK` (`[LOCKED]` CoA), `GENERAL_PARAMETER`, `MENU.trans_type_id_prefix`, `TRANSACTION_TYPE`/`APPROVAL_HIERARCHY_LEVEL` (menentukan routing approval), field identitas legal `DEALER`, `NUMBER_FORMAT` utk `code_type` ber-konsumsi `[LOCKED]` (`CREDIT_ID`). Checker ≠ maker (self-approval blocked D-01 S11). Maker-checker di master = kontrol BARU; jangan diklaim paritas.
- **BR-BE07-06** `[LOCKED]` adjacency AML: Mutasi `BLACKLIST_OVERRIDE` append-only di audit; override wajib `justification` + masa berlaku; dipakai reason-gate RFA di 01 (read-only dari sisi 01). CRUD-nya baru (legacy tabel tanpa CRUD — OQ-ACQCAS-08).

### Dealer family
- **BR-BE07-07** `[VERIFIED][INTENT]`: Dealer hanya muncul pada picker suatu cabang bila punya row `DEALER_BRANCH_ACCESS` aktif untuk cabang itu (dealer = partner branch-scoped, bukan global). Path "main dealer code" legacy yang join via `notes` TIDAK direplika (EC7 `[ARTIFACT]`).
- **BR-BE07-08** `[VERIFIED][INTENT]`: Dealer `is_selling_new_product_only=false` selalu ter-include di pencarian dealer used-car (carve-out mixed-inventory); override kondisi-item per application-type `'03'` = rule konsumsi milik 01.
- **BR-BE07-09** `[ARTIFACT]` → fix flag: Visibilitas sub-dealer dikendalikan flag eksplisit `is_sub_dealer_enabled` — match nama literal `'%PT Lucas Digital Indonesia%'` TIDAK di-port (EC6). Migrasi: set flag `true` utk dealer yang memenuhi rule legacy saat cutover.
- **BR-BE07-10** `[VERIFIED][INTENT]`: Kontak dealer eligible pembayaran diresolusi via join job-title → personnel → bank-reference dengan SEMUA status aktif simultan; record non-aktif di titik mana pun mengeluarkan kontak dari set eligible (BR-DLRPTN-1). Diekspos E21 (read) utk konsumen disbursement.

### Credit source & lookup
- **BR-BE07-11** `[VERIFIED]`/`[INTENT]`: `CREDIT_SOURCE` = master LOKAL acquisition (bukan `FC_MSTAPP_MCF`); availability + flag `photo_required`/`print_survey_report` di-scope per branch via mapping (BR-CREDITSRC-2). Konfirmasi fisik = OQ-DLRPTN-02.
- **BR-BE07-12** `[ARTIFACT]` → fix: Lookup identity-type/residence-status ter-scope applicant-type via **mapping set-membership eksplisit** (many-to-many), bukan substring match (EC5/BR-CUSTMASTER-9). Nilai mapping aktual dikonfirmasi saat migrasi (OQ-CUSTMASTER-03).
- **BR-BE07-13** `[VERIFIED][INTENT]`: Economic sector = hierarki 2-level ketat; debtor group = hierarki self-referencing ≤5 level — dipertahankan pada shape lookup E30.
- **BR-BE07-20** `[VERIFIED][INTENT]`: Semua endpoint list mengikuti kontrak pagination standar tunggal (page/page_size/search → items/total_pages/record_count) sebagai komponen reusable. Formalisasi konvensi yang di legacy di-copy-paste per layar (+bug pager EC2/EC3 tidak direplikasi).
- **BR-BE07-21** `[VERIFIED][INTENT]`: Read lookup default active-only; `include_inactive=true` tersedia untuk layar admin (toggle yang tidak ada di legacy).
- **BR-BE07-22** `[ARTIFACT]` → fix: Kegagalan baca lookup/mirror = error eksplisit (`503`/`404`), TIDAK PERNAH sukses-kosong; "employee resigned", "not found", "source error" = tiga sinyal berbeda (EC1/EC12/fuel — do-not-replicate silent-success).
- **BR-BE07-23** `[VERIFIED][INTENT]`: `GENERAL_PARAMETER.is_updateable=false` menolak mutasi via API (`409`); `is_visible=false` disembunyikan dari listing non-admin.

### External-FK LOCKED (routing/GL)
- **BR-BE07-14** `[VERIFIED][LOCKED]`: `MENU.trans_type_id_prefix` = input struktural komposisi `trans_type_id`: mutasinya wajib maker-checker + tercatat audit + memicu warning listing transaction-type ter-impact. Reorganisasi menu TIDAK boleh mengubah routing diam-diam. Format `trans_type_id` di-match char-for-char external-FK (BR-PRODASSET-7 `[LOCKED]`).
- **BR-BE07-24** `[LOCKED]`: `GL_TRANSACTION_TYPE_LINK` (mapping CoA) read + update-ter-audit saja; nilai mapping `[LOCKED]` — perubahan wajib maker-checker + sign-off finance. Journal/posting rules downstream = OQ-MEET-03.
- **BR-BE07-25** **[REDACTED-SECRET]** `[OPEN]` OQ-DLRPTN-05/OQ-REF-05: Field credential-shaped pada master eksternal (`MsBank.PasscodeBiBca`) TIDAK diekspos endpoint mana pun dan TIDAK dimigrasi tanpa security review; bila live → secrets manager. Aksi security independen timeline rebuild (OQ-EXTMASTERS-05).
- **BR-BE07-26** `[LOCKED]` fakta kopling; redesign deliberate: Akses master eksternal Tier C via ACL (service boundary); TIDAK mereplikasi cross-database three-part-name join maupun linked-server four-part-name sebagai pola akses aplikasi baru. Perubahan semantik transaksional diputuskan sadar — masuk asumsi D-11.

### Transaction-Type Hierarchy validation (server-side — fix OQ-MASTERDATA-02)
- **BR-BE07-15** `[VERIFIED][INTENT]`: Level 1 ladder TIDAK boleh `is_approver=true` (level pertama bukan terminal approver) — dienforce **server-side**. Update 2026-07-22: legacy TERNYATA juga enforce server-side di SP (V3) — bukan hanya JS.
- **BR-BE07-16** `[VERIFIED][INTENT]`: `is_approver=false` → `next_pic` WAJIB; `is_approver=true` → `next_pic` WAJIB kosong (terminal tidak punya successor) — server-side. SP legacy menulis empty-string utk spv saat approver (bukan menolak input) — rebuild validasi eksplisit.
- **BR-BE07-17** `[VERIFIED][INTENT]`: `pic_nik`/`next_pic_nik` WAJIB merujuk employee mirror yang ada dan tidak resigned saat write; PIC picker difilter job-title codes eligible. Mencegah ladder menunjuk approver resign; celah legacy V4 (enrichment `ms_employee_sync` tanpa guard) — rebuild menutupnya.
- **BR-BE07-18** `[VERIFIED][INTENT]` / derive = `[INFERRED]` don't-port: Edit `TRANSACTION_TYPE` hanya boleh mengubah `is_active`; code/description/mapping immutable pasca-create. `mapping` disimpan eksplisit, TIDAK di-derive dari `substring(0,2)` kode (OQ-MASTERDATA-08 tidak perlu resolved bila mapping disimpan eksplisit).
- **BR-BE07-19** `[VERIFIED][INTENT]`: `TRANSACTION_CODE` bersifat upsert (satu aksi Save); kode dinormalisasi upper-case server-side.

## Field `[LOCKED]` (dipertahankan 1:1 additive-only)
- `trans_type_id_prefix` (menu, BR-PRODASSET-14) · `transaction_type_code` format external-FK (BR-PRODASSET-7) · KTP/NPWP dealer identity zero-diff · `mst_dealer_bank_reference.account_number`/`account_name` payout zero-diff · `map_transaction_type_gl` CoA zero-diff · `cfg_number_format` `CREDIT_ID` code_type · `regency_id_OJK` crosswalk (BR-LOCATION-1) + 4 lookup korporat OJK-coded · `BRANCH.branch_id_passnet` (BranchIdPassnet) external key · `national_id` NIK VARCHAR(16) (mst_employee_mirror `NoKtp`, mst_blacklist_override).

## Do-not-replicate (BE-07 §11 marker-fidelity)
- **EC5** substring identity-type match → set-membership eksplisit (BR-BE07-12)
- **EC6** name-literal sub-dealer `'%PT Lucas Digital Indonesia%'` → flag `is_sub_dealer_enabled` (BR-BE07-09)
- **EC7** notes-as-join-key → FK eksplisit `parent_dealer_code` (BR-BE07-07)
- **EC1/EC12/fuel** silent-success error swallowing → error eksplisit `503`/`404` (BR-BE07-22)
- **EC14** `IMasters` NotImplementedException one-size-fits-all → write-path proper per master
- file-path columns di dealer → split `mst_dealer_document` + object storage
- hardcoded company→folder mapping → mapping table
- `TOP 1` tanpa `ORDER BY` fallback (milik disbursement, dicatat agar tidak menular)
- `/CRUD` demo menu → `[ARTIFACT]` discard (OQ-MASTERDATA-07)
- vestigial hidden fields `status_approver`/`notifikasi_hari` force-set JS → enum eksplisit atau retire

## Non-functional (inherited umbrella)
- Performance quantitative = `(unspecified)` sampai baseline A-13 + ITEC D-11 (constitution §E pattern: no claim without source).
- Dependensi arsitektur eksternal D-11: topologi service master-data + strategi ACL final menunggu deliverable ITEC.

## Sources

- `docs/prd/acquisition/BE-07-master-data-menus.md §6` (BR-BE07-01..27), `§8` (integrasi), `§11` (marker-fidelity do-not-replicate)
- `docs/DB-CONVENTIONS.md` (schema constraints, ADR-14)
- `docs/ARCHITECTURE-PROPOSAL.md §2.2` (do-not-replicate), `§10` (assumption register)
- `.mega-sdd/vaults/acquisition/06-constraints.md` (parent — NFR + regulated gates)

## Open Questions

- **OQ-BE07-01** [P1] — scope final maker-checker (BR-BE07-05) + checker role
- **OQ-BE07-02** [P1] — HR sync mechanism (BR-BE07-27 auto-deactivate)
- **OQ-BE07-03** [P1] — HO roles (expand enum D-10 → `mst_role`?)
- **OQ-REF-05/DLRPTN-05/EXTMASTERS-05** [P1] — `PasscodeBiBca` security (BR-BE07-25)
- **OQ-MASTERDATA-01/02** [P1/P2] — deactivate-only LOCKED?; celah V4/V6 cleansing
- **OQ-EXTMASTERS-01/07/08** — Tier C ownership + 8 objek absen + dual-DB holiday
- Lengkap di `00-index.md` roll-up
