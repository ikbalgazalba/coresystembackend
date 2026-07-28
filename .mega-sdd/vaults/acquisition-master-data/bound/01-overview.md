# 01 — Overview

> **TL;DR**: Modul 07 = administrasi master data acquisition — CRUD API untuk master yang **dimiliki** acquisition (User/role assignment TANPA super-user, Dealer family, konfigurasi Transaction-Type Hierarchy, approval reason, credit source, blacklist override, public holiday, general parameter, promotion line text, Menu tree) + **satu lapisan read-lookup API** untuk katalog referensi `FC_MSTAPP_MCF` (310 tabel) yang dikonsumsi 01–05. Phase 1 leaf module (M7 trunk reference — dikonsumsi semua modul, tidak konsumsi internal). Mandat: D-08.

## What

Modul administrasi master data milik rebuild acquisition. Menjawab dua temuan struktural KB: (a) **legacy TIDAK punya write-path master data yang bekerja end-to-end** — hampir semua endpoint masters read-only, interface `IMasters` insert/update melempar `NotImplementedException` (OQ-CUSTMASTER-04, OQ-DLRPTN-13); (b) **keputusan meeting D-08** memasukkan Menu Master (minimal User & Dealer) ke SoW rebuild BE+FE.

Dua keputusan governance mengunci desain user management: **Super user DIHAPUS** (D-09 `[LOCKED]`) dan **sensus role cabang = CMO, Marketing Head, Credit Analyst, Kepala Cabang, Credit (Admin)** dengan hierarki approval tergantung skala risiko (D-10 `[LOCKED]`).

**Tiga tier kepemilikan master** (BE-07 §1.3 — jawaban OQ-EXTMASTERS untuk build tanpa menunggu resolusi penuh):
- **Tier A — OWNED**: master yang dimiliki & di-CRUD modul ini (bukti lokal / di-mandat D-08 / satu-satunya admin surface di FINCORE.WEB). CRUD penuh (maker-checker untuk subset sensitif).
- **Tier B — SYNCED MIRROR**: sumber eksternal system-of-record; rebuild menyimpan mirror read-only (EMPLOYEE_MIRROR HR sync, BRANCH/COMPANY). Read-only + sync job; TIDAK ada endpoint write.
- **Tier C — EXTERNAL READ-ONLY**: katalog `FC_MSTAPP_MCF` (310 master; read-set acquisition 171 tabel) yang ownership-nya `[OPEN]` OQ-EXTMASTERS-01. Read-only via ACL; CRUD ditunda.

## Who

**Aktor (BE-07 §2):**
- **Master Data Administrator (Maker)** — membuat/mengubah record master Tier A. Legacy TIDAK punya role admin ter-deklarasi (`[INFERRED]` dari keberadaan layar admin). Pemetaan D-10: **Credit (Admin)** untuk master operasional cabang; admin HO `[OPEN]` OQ-BE07-03.
- **Master Data Checker (Approver)** — menyetujui/menolak perubahan master sensitif (maker-checker, BR-BE07-05). Kandidat: Kepala Cabang (scope cabang) / role HO. Maker ≠ checker (D-01 S11). `[OPEN]` OQ-BE07-01.
- **Approval-Hierarchy Administrator** — mengonfigurasi per branch: transaction code → transaction type → ladder level (PIC, next-PIC, eskalasi, is-approver).
- **PIC / Approver (employee)** — employee ditunjuk pada level ladder; dicari via picker difilter job-title codes.
- **HR System (upstream, non-human)** — system-of-record identitas employee; mirror `ms_employee_sync` di-sync one-way `[LOCKED]` BR-EMPLOYEE-1.
- **Corporate Directory / LDAP (upstream)** — verifikasi kredensial saat login (di luar modul; konteks APP_USER tanpa password `[LOCKED]` BR-SHELL-1).
- **Modul 01–05 (konsumen, non-human)** — baca lookup + konfigurasi (dealer picker 01, hierarchy definisi 03, reason-code 03/05, dst.). Read-only.
- **Dealer / Dealer Personnel** — subjek record master (bukan operator); identitas legal KTP/NPWP `[LOCKED]`.

> Gap legacy TIDAK direplikasi: tidak ada satu pun gate role/permission pada layar master legacy selain "session ada" (BR-MASTERDATA-13; OQ-ACTORS-02). Rebuild WAJIB authz eksplisit per endpoint (kolom Auth/Role di §4 BE-07), enforce app-layer (OQ-MCP-01).

## Why

Modul 07 = **trunk reference leaf** (ARCHITECTURE-PROPOSAL §3/§5 `M7`): dikonsumsi 01–05 + app-shell FE, TIDAK konsumsi internal. Interface contracts yang 07 publish **harus benar sejak awal** — modul lain nanti terblokir jika salah:
- RBAC/menu (`mst_user`, `cfg_menu`, role census D-10) — prasyarat no-super-user D-09 + no-self-approval D-01 S11 di semua modul
- Dealer family (`mst_dealer` + personnel/bank-ref/branch-access) — origination 01 & PO email 04 butuh ini; KTP/NPWP zero-diff
- `cfg_transaction_code`/`cfg_transaction_type` (`trans_type_id` external-FK `[LOCKED]`) — fondasi committee routing Phase 2
- `cfg_hierarchy_matrix` (shared dengan 03 — admin surface menulis LANGSUNG ke tabel yang di-walk engine routing, OQ-MASTERDATA-03 ✅)
- `cfg_number_format` (consumed 01 untuk mint `credit_id` STEP 8 — format `branch(5)+YY+MM+SEQ(5)` OQ-GT-02 ✅)
- `map_transaction_type_gl` (read finance/05; CoA `[LOCKED]` zero-diff)
- `mst_employee_mirror` (HR sync — prasyarat provisioning user & PIC picker)
- `mst_approval_reason` (consumed 03/05 untuk disposisi)
- `mst_blacklist_override` (AML, consumed 01 reason-gate RFA)

Maker-checker di master = **kontrol BARU** (legacy TIDAK punya — `12-...§3a` "not by a wizard or maker-checker hand-off"); jangan klaim paritas.

## Success criteria

> Dari BE-07 §9 AC-1..AC-16 (Given/When/Then). PRD TIDAK memfabrikasi angka kuantitatif.

- **AC-1**: Provision user happy path — NIK ada di mirror & tidak resigned → `201` user aktif ber-role enum D-10, tanpa field password, audit create tercatat
- **AC-2**: Super-user diblokir (D-09) — `role="SUPER_USER"` → `422 UNKNOWN_ROLE` merujuk D-09/D-10; tidak ada mekanisme grant setara
- **AC-3**: User resigned ditolak + auto-deactivate — `422 EMPLOYEE_RESIGNED` (eksplisit, bukan sukses-kosong); sync HR resigned → otomatis `inactive(hr_resigned)` + menu efektif kosong
- **AC-4**: Menu efektif per role — hanya menu aktif + grant aktif dikembalikan, tree dengan `is_view_only`
- **AC-5**: Proteksi `trans_type_id_prefix` (BR-BE07-14) — mutasi prefix → `pending_approval` + daftar transaction-type ter-impact; tanpa approve, routing tidak berubah
- **AC-6/7**: Dealer branch-scoped picker + FK parent eksplisit (fix EC7 notes-as-join-key)
- **AC-8**: Maker-checker rekening dealer — `202 pending_approval`; self-approve → `403 SELF_APPROVAL_BLOCKED`
- **AC-9**: Eligibility kontak pembayaran — join job-title→personnel→bank-ref aktif simultan (BR-DLRPTN-1)
- **AC-10**: Validasi ladder server-side (fix OQ-MASTERDATA-02) — Level-1 `is_approver=true` → `422`; `next_pic` required/empty; PIC resigned ditolak
- **AC-11**: Transaction type immutable — PATCH hanya `is_active`; mapping disimpan eksplisit (bukan derive `substring`)
- **AC-12/13**: Lookup scoped set-membership (fix EC5) + fail = error eksplisit `503` (bukan sukses-kosong — fix EC1/EC12/fuel)
- **AC-14**: No hard delete (BR-BE07-03) — `DELETE` → `405`/`404`; deactivate-only
- **AC-15/16**: Blacklist override ter-governance (justification + append-only audit) + general parameter guard (`is_updateable=false` → `409`)

## Sources

- `docs/prd/acquisition/BE-07-master-data-menus.md` §1 (scope+tiering), §2 (aktor), §9 (AC), §11 (OQ)
- `docs/ARCHITECTURE-PROPOSAL.md §3/§5` (M7 trunk reference leaf)
- `docs/prd/acquisition/BE-00-OVERVIEW.md §2` (role census D-10, D-09 super-user)

## Out of Scope

- Autentikasi & session (app-shell FE + auth service; 07 hanya data authz)
- Employee master create/update (HR system-of-record; mirror read-only)
- Customer master `mst_customer` (milik 05-npp; 07 hanya lookup klasifikasi)
- Eksekusi routing approval (milik 03; 07 hanya definisi/konfigurasi)
- Master pricing/insurance rate family (konsumsi 04/insurance downstream; OQ-EXTMASTERS-01)
- Dealer payment routing runtime (GL crosswalk = konsumsi disbursement STEP 16)
- Screens FE (PRD FE-07)
- 5 kapabilitas inti + 06 vertel (vault umbrella `acquisition`)

## Open Questions

- **OQ-BE07-01/02/03** [P1] — maker-checker scope + checker role; HR sync mechanism; HO roles (expand enum D-10?)
- **OQ-EXTMASTERS-01/07** [P1] — masters ownership + 8 objek absen dari dump
- **OQ-DLRPTN-01/05** [P1] — dealer shape live + `PasscodeBiBca` security
- **OQ-CUSTMASTER-04/DLRPTN-13** [P1] — bagaimana ~27 lookup + master di-maintain (no write path)
- **OQ-MASTERDATA-02** [P1] — celah validasi V4 (NIK) + V6 (kontiguitas); cleansing import wajib
- **OQ-ARCH-STACK** [P1] — framework/transport/topologi (ITEC D-11)
- Daftar lengkap P2/P3 di `00-index.md` §Open Questions roll-up + `vault.json.open_questions`
