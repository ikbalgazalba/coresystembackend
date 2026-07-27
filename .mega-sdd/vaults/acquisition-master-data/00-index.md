# Vault Index — acquisition-master-data

> **Vault**: Acquisition → Master Data (BE-07) · **Project**: coresystembackend · **Slice**: Phase 1 Foundation leaf module
> **Source**: `docs/prd/acquisition/BE-07-master-data-menus.md` (v2.1, 2026-07-22) + 07-owned rows of `BE-00 §6.3` registry
> **Parent vault**: `.mega-sdd/vaults/acquisition/` (umbrella — source of truth untuk boundary antar-modul; sub-vault ini cross-reference ke sana, BUKAN duplikasi)
> **Generated**: 2026-07-27 · **Multi-PRD**: sub-slice of acquisition epic over jwt-login + api-platform

## Vault Lock

| Field | Value |
|---|---|
| `vault_version` | 1.2 |
| `project_shape` | api-only |
| `implementation_mode` | existing |
| `prd_status` | draft |
| `output_mode` | compact |
| `constitution_version` | 1.3.0 (extends acquisition v1.2.0; §A–§H inherited + §I master-data) |
| `source_documents` | BE-07-master-data-menus.md (primary) · BE-00-OVERVIEW.md §6.3 (registry 07 rows) · DB-CONVENTIONS.md (schema) · ARCHITECTURE-PROPOSAL.md §5 (M7 trunk) |
| `design_system_flags` | HAS_UI_COMPONENTS=false · HAS_TOKENS=false · HAS_A11Y=false · HAS_VOICE_BRAND=false (BE-only; FE di PRD FE-07) |
| `slice_scope` | module 07 only (full module — User/RBAC + Dealer family + Transaction-Type Hierarchy + master operasional + lookup Tier C) |

## Implementation Notes for AI Consumers

Vault ini adalah **sub-slice fokus** modul 07 (master-data) dari vault umbrella `acquisition`. Tujuan: persempit konteks build ke master-data (Phase 1 leaf module) tanpa menanggung kompleksitas 5 kapabilitas inti. Vault umbrella tetap source of truth untuk boundary antar-modul (siapa publish/consume apa); sub-vault ini **cross-reference** ke umbrella, bukan duplikasi.

**Yang WAJIB dijaga AI consumer:**
- **Jangan fabrikasi.** Setiap klaim sitasi ke `BE-07 §N`. Marker mutability: `[LOCKED]` (regulatori/external-FK/governance) 1:1 additive-only; `[INTENT]` outcome wajib mekanisme bebas; `[ARTIFACT]` buang setelah sign-off; `[OPEN]`→OQ; **USULAN**=desain baru belum diputuskan.
- **Modul 07 = trunk reference leaf** (ARCHITECTURE-PROPOSAL §3/§5 `M7`): dikonsumsi 01–05 + app-shell FE, TIDAK konsumsi internal. Interface contracts yang 07 PUBLISH (RBAC/menu, dealer family, TransType hierarchy, `cfg_number_format` untuk mint `credit_id`, `map_transaction_type_gl`, `mst_approval_reason`, `mst_blacklist_override`) HARUS benar sejak awal — modul lain nanti terblokir jika salah.
- **Tiga tier kepemilikan master** (BE-07 §1.3, jawaban OQ-EXTMASTERS untuk build): Tier A OWNED (CRUD penuh), Tier B SYNCED MIRROR (HR read-only), Tier C EXTERNAL READ-ONLY (FC_MSTAPP_MCF 310 tabel via ACL, ownership `[OPEN]` OQ-EXTMASTERS-01).
- **Super-user DIHAPUS** (D-09 `[LOCKED]`) — tidak ada nilai `SUPER_USER` di enum role, tidak ada bypass flag, tidak ada special-grant setara. Role census D-10 `[LOCKED]`: `CMO | MARKETING_HEAD | CREDIT_ANALYST | KEPALA_CABANG | CREDIT_ADMIN`. No-self-approval (D-01 S11).
- **Maker-checker = kontrol BARU** (legacy TIDAK punya — `12-...§3a` "not by a wizard or maker-checker hand-off"); jangan klaim paritas. BR-BE07-05 daftar resource WAJIB maker-checker: DEALER_BANK_REFERENCE (payout), BLACKLIST_OVERRIDE (AML), GL_TRANSACTION_TYPE_LINK (CoA), GENERAL_PARAMETER, MENU.trans_type_id_prefix, TRANSACTION_TYPE/APPROVAL_HIERARCHY_LEVEL, dealer legal identity, NUMBER_FORMAT `CREDIT_ID`. Scope checker = OQ-BE07-01.
- **Do-not-replicate** (BE-07 §11 marker-fidelity): substring identity-type match (EC5), notes-as-join-key (EC7), name-literal sub-dealer (EC6), silent-success error swallowing (EC1/EC12/fuel), `IMasters` NotImplementedException (EC14), file-path columns di dealer, hardcoded company→folder, `TOP 1` tanpa ORDER BY, `/CRUD` demo, vestigial hidden fields `status_approver`/`notifikasi_hari` force-set JS.
- **Schema** otoritatif = `docs/DB-CONVENTIONS.md` (ADR-14): prefix `mst_/cfg_/log_/map_`, singular snake_case English, PK `id BIGINT IDENTITY` + business key terpisah, declared FK wajib, audit columns wajib, satu kolom `status`/lifecycle. Master mutable user konkuren bawa `version INTEGER` (optimistic locking).

**Provenance caveat (inherited dari umbrella):** BE-07 sitasi KB `.mega-sdd/knowledge-base/` (file `10-domains/10-..`, `11-..`, `12-..`, `50-integrations/external-masters..`, `30-data-model/reference-entities.md`, `60-frontend/66-..`) sebagai sumber otoritatif teknis, tetapi KB **tidak ada di disk** (OQ-AC-PROVENANCE di umbrella). Companion docs (ARCHITECTURE-PROPOSAL, DB-CONVENTIONS, DATA-MIGRATION-PLAN) ADA. Jangan fabrikasi konten KB.

## Reading paths

| Pembaca | Baca dulu |
|---|---|
| Dev modul 07 | 01-overview → 02-architecture (modules + interfaces PUBLISHED) → 03-data-model (24 target tabel + 2 log = 26 total census) → 04-flows (E1-E38 + maker-checker envelope) → BE-07 §5 kontrak request/response |
| QA | 04-flows (AC-1..AC-16 Given/When/Then) → 06-constraints (BR-BE07-01..27 + regulated) |
| Architect | 02-architecture (tiering A/B/C + ACL) → 05-decisions (ADR relevan 07) → 00-index §OQ (OQ-BE07-01/02/03 blocker) |

## Auto-Classification Review

> OQ di vault ini sebagian besar **business / blocking** (perlu stakeholder/DBA/ITEC/HR). Beberapa `tech / scan` (OQ-DLRPTN-02 `ms_credit_source` lokal?, OQ-EXTMASTERS-08 dual-DB holiday) — tapi tetap menunggu pihak eksternal. Manual review: semua OQ-BE07-* (baru, governance), OQ-EXTMASTERS-* (DBA/ITEC).

## Open Questions roll-up

> Tag PRD dipreserve. Roll-up paling memblokir dari ~27 OQ BE-07 §11. Status ✅ = RESOLVED (evidence 2026-07-22). Roll-up lengkap di `vault.json.open_questions` + BE-07 §11.

| OQ-ID | Priority | Category | Resolution mode | Summary |
|---|---|---|---|---|
| OQ-EXTMASTERS-01 | P1 | business | blocking | Masters `FC_MSTAPP_MCF` owned vs read-only per master + liveness linked-server. **Deferred (v1.3.1)**: menunggu DBA/ITEC ownership per-master + linked-server liveness (DDL dump ✅ 2026-07-22, sisa kepemilikan). |
| OQ-EXTMASTERS-07 | P1 | business | blocking | 8 objek dirujuk code acquisition ABSEN dari dump (mis. `ms_insurance_cover_type`). **Deferred (v1.3.1)**: menunggu DBA klarifikasi 8 objek absen. |
| OQ-DLRPTN-01 | P1 | business | blocking | ~~Shape dealer live: `MsDealer` vs `MsDealer1` vs `MsDealerBackup20221227`~~ **✅ RESOLVED v1.3.1** → `MsDealer` = live (field census §3.2 pakai ini); `MsDealer1`/`MsDealerBackup20221227` = `[ARTIFACT — discard]` (data diarsip Prinsip 2). Field ekstra MsDealer1 (Phone2/Fax/EmailGroup/IsDefaultMokas) TIDAK dibawa. |
| OQ-REF-05 / OQ-DLRPTN-05 / OQ-EXTMASTERS-05 | P1 | business | blocking | `[SECURITY]` `MsBank.PasscodeBiBca` live credential? + plaintext MINIAPI cred. **Deferred (v1.3.1)**: menunggu security review (aksi independen rebuild). |
| OQ-CUSTMASTER-04 / OQ-DLRPTN-13 | P1 | business | blocking | Bagaimana ~27 lookup + dealer/bank masters di-maintain hari ini (no write path)? **Deferred (v1.3.1)**: menunggu konfirmasi stakeholder (sibling admin app?). |
| OQ-BE07-01 | P1 | business | blocking | ~~Scope final maker-checker (BR-BE07-05) + siapa checker per resource~~ **✅ RESOLVED v1.3.1** → Checker = **Kepala Cabang** untuk SEMUA resource maker-checker (cabang-scope, konsisten D-10). `credit_id` minting tetap BE (ADR-08/OQ-GT-02 LOCKED, FE hanya minta/menampilkan). Lihat §RESOLVED. |
| OQ-BE07-02 | P1 | business | blocking | Mekanisme & frekuensi sync HR → `EMPLOYEE_MIRROR` (batch/CDC/API); field resign real-time cukup utk auto-deactivate? **Deferred (v1.3.1)**: menunggu HR system capability assessment + ITEC D-11 topologi. |
| OQ-BE07-03 | P1 | business | blocking | Role HO (master-data checker HO, Compliance/AML, Area/Regional Head di PIC picker) — expand enum D-10? governance? **Deferred (v1.3.1)**: menunggu governance decision role HO + stakeholder sign-off. mst_role TIDAK dibuat selama D-10 tertutup (D-MD-04). |
| OQ-MASTERDATA-02 | P1 | business | blocking | TER-KOREKSI 2026-07-22: backend legacy validasi V1-V3/V7-V8 di SP; celah nyata V4 (NIK tanpa guard) + V6 (kontiguitas level). **Deferred (v1.3.1)**: runtime validation RESOLVED (U-008 BR-BE07-15..17 server-side); cleansing import = OQ-MIG-05 (profil data prod). |
| OQ-ARCH-STACK | P1 | business | blocking | Framework BE (USULAN Spring Boot), transport, topologi. **Deferred (v1.3.1)**: menunggu deliverable ITEC D-11. Java LOCKED D-12. |
| OQ-AC-PROVENANCE | P1 | tech | scan | KB `.mega-sdd/knowledge-base/` tidak ada di disk (inherited umbrella). **Deferred (v1.3.1)**: menunggu restore/move KB atau ubah sitasi PRD. Tidak block unit generation (units pakai PRD). |
| OQ-MEET-02 | P1 | business | blocking | Owner master loan — inherited umbrella. **Deferred (v1.3.1)**: menunggu ITEC D-11. 07 sediakan lookup saja. |
| OQ-MASTERDATA-03 | P2 | business | blocking | Apa konsumsi nyata konfigurasi TransTypeHierarchy (router komite `ms_hierarchy_transaction` vs ladder CA `ms_approval_scheme`)? |
| OQ-MASTERDATA-01 | P2 | business | blocking | ~~Deactivate-only (no hard delete) = kebutuhan audit disengaja?~~ **✅ RESOLVED v1.3.1** → **LOCKED** — deactivate-only = kebutuhan audit disengaja; BR-BE07-03 upgrade ke `[LOCKED]` (preservasi historis routing). |
| OQ-DLRPTN-04 | P2 | business | blocking | ~~Makna type approval-reason `'1'\|'2'\|'3'\|'9'` + apakah `'9'` diekspos~~ **✅ RESOLVED v1.3.1** → **Expose semua type** (no hardcode subset; endpoint read menerima filter `type` eksplisit, TIDAK menyembunyikan '9'). Makna semantik TBD tapi data verbatim dibawa. |
| OQ-BE07-04 | P2 | business | blocking | ~~Scope user: single-branch atau multi-branch~~ **✅ RESOLVED v1.3.1** → **Multi-branch** (`branch_scope` = list; mendukung Kepala Cabang/Compliance cover >1 cabang). `mst_user_branch_scope` child table relevan. |
| OQ-BE07-05 | P2 | business | blocking | ~~Model akses menu; `USER_MENU_GRANT_SPECIAL` dipertahankan?~~ **✅ RESOLVED v1.3.1** → **Role-based + special grant retained WITH governance** (`granted_reason` wajib + audit append-only + TIDAK boleh setara super-user D-09). `cfg_menu_user_grant_special` DIBANGUN (U-004) dengan governance ketat. |
| OQ-DLRPTN-02 | P2 | tech | scan | `ms_credit_source` genuinely lokal atau artefak export? |
| OQ-DLRPTN-07 | P2 | business | blocking | Sumber otoritatif BPKB location: lokal vs union linked-server `MsLokasiBPKB`? |
| OQ-CUSTMASTER-02 / OQ-CUSTMASTER-07 | P2 | business | blocking | Lookup "reference type" dipakai apa? Set applicant-type hanya `P`/`C`? |
| OQ-PRODASSET-01 / OQ-PRODASSET-03 | P2 | business | blocking | Katalog asset otoritatif (asset-* vs item-brand-*)? Endpoint product list legacy = stub — sumber UI? |
| OQ-REF-01 | P2 | business | blocking | Nilai `application_type_id` di-parse sistem eksternal (Passnet/GL)? LOCKED vs INTENT |
| OQ-EXTMASTERS-08 | P2 | tech | scan | `MsPublicHoliday` di DUA database (`FC_ACQ_MCF` & `FC_MSTAPP_MCF`) — copy mana otoritatif? |
| OQ-DLRPTN-10 / OQ-DLRPTN-11 | P3 | business | blocking | Arti kode legal entity `PT='2'/'3'` + enum `Dt2Type` kota/kabupaten |
| OQ-BE07-06 | P3 | business | blocking | ~~`PUBLIC_HOLIDAY` boleh hard-delete (USULAN E33) atau deactivate-only?~~ **✅ RESOLVED v1.3.1** → **Deactivate-only (no hard-delete)** — ikut BR-BE07-03 LOCKED; E33 DELETE di-drop, PATCH `is_active`. |
| OQ-DLRPTN-06 / OQ-DLRPTN-12 / OQ-DLRPTN-15 | P3 | business | blocking | `MsInsuranceByDealerR2/R4` hidup/orphan; insurance source shape |
| OQ-DLRPTN-08 / OQ-DLRPTN-09 | P3 | business | blocking | `sp_get_branch_exception` stub mati/unfinished; payment-point branch-scoping? |
| OQ-MASTERDATA-07 | P3 | business | blocking | Layar Dukcapil/Fidusia/`/CRUD` demo masih reachable dari menu live? |

**✅ RESOLVED (jangan di-blokir):** OQ-REF-04 (dump `FC_MSTAPP_MCF` ✅ 2026-07-22, 310 tabel+112 SP+2 UDF) · OQ-PRODASSET-05 (V1-V9 verified dari body SP; celah V4+V6) · OQ-GT-02 (`credit_id` format — inherited umbrella, consumer 01) · OQ-MASTERDATA-03 ✅ (admin surface menulis LANGSUNG ke `ms_hierarchy_transaction` → target tunggal `cfg_hierarchy_matrix`, no separate `cfg_approval_hierarchy_level`).

**✅ RESOLVED v1.3.1 (resolve-oq round 1, 2026-07-27):**
- **OQ-BE07-01** → Checker = **Kepala Cabang** untuk SEMUA resource maker-checker (DEALER_BANK_REFERENCE, BLACKLIST_OVERRIDE, GL_TRANSACTION_TYPE_LINK, GENERAL_PARAMETER, MENU.trans_type_id_prefix, TRANSACTION_TYPE/APPROVAL_HIERARCHY_LEVEL, dealer legal identity, NUMBER_FORMAT CREDIT_ID). Cabang-scope, konsisten D-10. `credit_id` minting tetap BE (ADR-08/OQ-GT-02 LOCKED — FE hanya minta/menampilkan, bukan minting). BR-BE07-05 scope final = Kepala Cabang.
- **OQ-DLRPTN-01** → `MsDealer` = live (field census §3.2 pakai ini); `MsDealer1`/`MsDealerBackup20221227` = `[ARTIFACT — discard]` (data diarsip Prinsip 2). Field ekstra MsDealer1 (Phone2/Fax/EmailGroup/IsDefaultMokas) TIDAK dibawa.
- **OQ-BE07-04** → **Multi-branch** (`branch_scope` = list; mendukung Kepala Cabang/Compliance cover >1 cabang). `mst_user_branch_scope` child table relevan.
- **OQ-BE07-05** → **Role-based + special grant retained WITH governance** (`granted_reason` wajib + audit append-only + TIDAK boleh setara super-user D-09). `cfg_menu_user_grant_special` DIBANGUN (U-004) dengan governance ketat.

**⏳ DEFERRED v1.3.1 (menunggu eksternal):**
- **OQ-BE07-02** → Defer ke HR/ITEC: mekanisme sync (batch/CDC/API) bergantung HR system capability + topologi ITEC D-11. Sync job stub di U-002 tetap `TBD: OQ-BE07-02` sampai resolved.
- **OQ-BE07-03** → Defer ke governance/stakeholder: perluasan role HO = keputusan org governance. mst_role table TIDAK dibuat selama D-10 tertutup (D-MD-04). Bila OQ-BE07-01 nanti perlu HO checker (tidak — sudah Kepala Cabang all), baru bahas.
- **OQ-MASTERDATA-02** → Defer ke migrasi (OQ-MIG-05): runtime validation V4/V6 RESOLVED (U-008 BR-BE07-15..17 server-side); cleansing import profil data prod = OQ-MIG-05.

## Sources

- `docs/prd/acquisition/BE-07-master-data-menus.md` — PRD modul 07 (sumber otoritatif: §1 scope+tiering, §3 data model census, §4 API E1-E38, §5 kontrak, §6 BR-BE07-01..27, §7 state machine, §8 integrasi, §9 AC-1..16, §11 OQ)
- `docs/prd/acquisition/BE-00-OVERVIEW.md §6.3` — Table-Ownership Registry baris owner 07 (2 tabel kanonik + admin surface shared)
- `docs/DB-CONVENTIONS.md` — standar schema (ADR-14)
- `docs/ARCHITECTURE-PROPOSAL.md §3/§5` — M7 trunk reference leaf module
- `.mega-sdd/vaults/acquisition/` — vault umbrella (parent — boundary antar-modul)
- `.mega-sdd/vaults/acquisition/constitution.md` — konstitusi di-extend (§A–§H inherited)

## Out of Scope (vault sub ini)

- **Autentikasi & session** — login LDAP, session bootstrap, branch-selection session = milik app-shell FE + auth service (07 hanya sediakan data authz role/menu-grant). Password/credential store TIDAK dibuat (BR-SHELL-1 `[LOCKED]`).
- **Employee master create/update** — HR system-of-record; `mst_employee_mirror` = mirror one-way read-only (BR-EMPLOYEE-1). 07 hanya baca mirror untuk picker + provisioning APP_USER.
- **Customer master (`mst_customer`/`tr_CIF`)** — penulisan otoritatif milik 05-npp (umbrella). 07 hanya sajikan lookup klasifikasi applicant.
- **Eksekusi routing approval** — walking `cfg_hierarchy_matrix`, resolve approver, inbox, eskalasi = milik 03 (BE-03). 07 miliki definisi/konfigurasi saja.
- **Master pricing/insurance rate family** (`MsInsurance*`, OTR/MarketPrice) — `[LOCKED]` rate OJK, konsumsi 04/insurance downstream; CRUD tidak dibangun fase ini (OQ-EXTMASTERS-01 + annex per-product D-07).
- **Dealer payment routing runtime** — GL crosswalk 4-nilai = konsumsi disbursement (STEP 16); 07 hanya simpan master yang dirujuk.
- **Screens FE** — PRD FE-07 (D-12 split per audience).
- **Fidusia upload & Dukcapil result viewer** — domain kolateral/integrasi, bukan master data.
- **5 kapabilitas inti (01-05) + 06 vertel** — di vault umbrella `acquisition`; sub-vault ini hanya 07.

## Sibling vaults

- `../acquisition/` — umbrella vault (parent; 5 kapabilitas inti + 06 vertel + 07 overview)
- `../jwt-login/` — auth foundation (sudah ship)
- `../api-platform/` — Swagger+Docker (sudah ship)
