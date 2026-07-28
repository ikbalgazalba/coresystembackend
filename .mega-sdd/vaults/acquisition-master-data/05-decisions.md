# 05 — Decisions

> **TL;DR**: ADR relevan modul 07 (inherited dari umbrella ARCHITECTURE-PROPOSAL §4) + keputusan meeting D-08/D-09/D-10/D-11/D-12 + keputusan desain baru BE-07 (tiering A/B/C, maker-checker envelope, single-source `cfg_hierarchy_matrix`). Status ADR *Proposed* (menunggu ITEC D-11).

## ADR relevan modul 07 (inherited umbrella)

### ADR-02 — Zero stored procedure (inherited)
Logika bisnis 100% application layer. **Implikasi 07**: validasi ladder V1-V9 (OQ-PRODASSET-05 ✅ verified dari body SP legacy) di-re-implement di service Java 07 — BR-BE07-15..17 server-side (bukan JS browser, bukan SP). Celah legacy V4 (NIK tanpa guard) + V6 (kontiguitas) ditutup di rebuild.

### ADR-03 — Satu schema + prefix kelas + ownership registry (inherited)
**Implikasi 07**: 26 tabel target prefix `mst_/cfg_/log_/map_`. `cfg_hierarchy_matrix` = **single source** (07 admin surface + 03 walking engine akses tabel yang sama — OQ-MASTERDATA-03 ✅; NO separate `cfg_approval_hierarchy_level`). ArchUnit menolak repository modul lain menulis tabel milik 07.

### ADR-05 — Anti-Corruption Layer (inherited)
**Implikasi 07**: Tier C `FC_MSTAPP_MCF` (310 master) via ACL service API (BR-BE07-26 — NO cross-DB three-part-name / linked-server four-part-name sebagai pola aplikasi baru). Tier B HR sync via sync job. Kegagalan baca = `503 LOOKUP_SOURCE_UNAVAILABLE` (BR-BE07-22).

### ADR-07 — RBAC 5 peran + maker-checker server-side authoritative (inherited)
**Implikasi 07**: role census D-10 `[LOCKED]`; super-user DIHAPUS D-09; no-self-approval D-01 S11. 07 = sumber data authz (role/menu-grant) yang dikonsumsi app-shell FE + semua modul. Maker-checker envelope E37 = kontrol BARU (legacy TIDAK punya).

### ADR-13 — Flowable embedded (inherited — konteks, BUKAN 07 langsung)
Approval human-task layer dijalankan Flowable. **Implikasi 07**: 07 miliki **definisi** ladder (`cfg_hierarchy_matrix`); **eksekusi** routing (inbox, eskalasi, siapa bertindak) milik 03. 07 tidak menjalankan Flowable langsung — hanya sediakan config yang di-read delegate engine 03. `log_master_change_request`/`log_master_audit` = audit 07 independent dari `ACT_*` engine.

### ADR-14 — Standarisasi schema DB-CONVENTIONS (inherited)
**Implikasi 07**: seluruh 26 tabel target mengikuti konvensi (prefix, singular, PK `id` identity + business key, declared FK, audit cols, satu `status`/lifecycle, optimistic locking `version`).

### ADR-15 — Migrasi simulation-first (inherited)
**Implikasi 07**: mapping matrix per tabel di BE-07 §3 kolom "Mapping asal"; `[LOCKED]` zero-diff (KTP/NPWP dealer, account_number/name payout, CoA GL, `trans_type_id_prefix`); cleansing wajib V4/V6 ladder; no-data-left-behind (backup tables `MsDealerBackup*` = `[ARTIFACT — discard]` schema, data tetap diarsip).

## Keputusan meeting D-08..D-12 (inherited umbrella — TARGET-STATE mengikat)

| ID | Keputusan | Marker | Dipakai di 07 |
|---|---|---|---|
| D-08 | Menu Master (User, Dealer, dst.) masuk SoW | `[INTENT]` | §1.1 — mandat modul 07 |
| D-09 | Super user DIHAPUS | `[LOCKED]` | §2, §3.1, BR-BE07-01 — no `SUPER_USER` enum, no bypass |
| D-10 | Role census cabang: CMO, Marketing Head, Credit Analyst, Kepala Cabang, Credit (Admin); hierarki by skala risiko | `[LOCKED]` | §2, §3.1 `mst_user.role`, E7 catalog |
| D-11 | Arsitektur disiapkan ITEC Bank Mega | dependensi eksternal | §1.4, §8 — topologi final deferred |
| D-12 | Target stack: BE=Java, FE=Next.js; PRD split per audience | `[LOCKED]` | header — Java LOCKED, Spring Boot USULAN |

## Keputusan desain baru BE-07 (USULAN — grounded D-08/D-09/D-10 + gap legacy)

### D-MD-01 — Tiga tier kepemilikan master (A/B/C)
**Keputusan**: Tier A OWNED (CRUD penuh, bukti lokal/D-08/admin-surface-FINCORE.WEB), Tier B SYNCED MIRROR (HR read-only + sync job), Tier C EXTERNAL READ-ONLY (`FC_MSTAPP_MCF` via ACL, ownership OQ-EXTMASTERS-01).
**Konteks**: legacy TIDAK punya write-path master (NotImplementedException); 310 master di DB terpisah ownership-nya `[OPEN]`. Tiering = cara 07 tetap buildable tanpa menjawab OQ-EXTMASTERS-01 diam-diam.
**Konsekuensi**: Tier A dipilih hanya untuk master yang (i) terbukti lokal, (ii) di-mandat D-08, atau (iii) satu-satunya admin surface FINCORE.WEB. Master lain menunggu keputusan.

### D-MD-02 — Maker-checker envelope generik (E37)
**Keputusan**: resource sensitif (BR-BE07-05) write masuk `pending_approval`; checker (≠ maker) approve/reject; state machine §7.2.
**Konteks**: legacy TIDAK punya maker-checker di master (`12-...§3a`); kontrol BARU. D-01 S11 (no-self-approval) mengikat.
**Konsekuensi**: (+) governance payout/AML/CoA; (−) overhead change-request; scope checker OQ-BE07-01.

### D-MD-03 — Single-source `cfg_hierarchy_matrix` (OQ-MASTERDATA-03 ✅)
**Keputusan**: admin surface 07 menulis LANGSUNG ke `ms_hierarchy_transaction` → target tunggal `cfg_hierarchy_matrix` (tabel yang SAMA yang di-walk engine routing 03). NO separate `cfg_approval_hierarchy_level`.
**Konteks**: evidence 2026-07-22 body SP `sp_insert/update_hirarki_approval_transaksi_trans_type_hierarchy` menulis langsung ke `ms_hierarchy_transaction`.
**Konsekuensi**: 07 (admin) + 03 (walking) akses tabel yang sama via repo ter-scope; cleansing wajib V4/V6.

### D-MD-04 — Role enum tertutup (no `mst_role` table selama D-10 tertutup)
**Keputusan**: `mst_user.role` = `VARCHAR`+`CHECK` enum D-10; tabel `mst_role` TIDAK dibuat selama enum tertutup `[LOCKED]`. Bila OQ-BE07-03 perluas role HO → enum diangkat jadi `mst_role` (jangan tambah nilai diam-diam).
**Konteks**: D-10 census tertutup; D-09 no super-user.

### D-MD-05 — `cfg_number_format` + DB sequence (ganti manual-increment legacy)
**Keputusan**: definisi format di-seed dari `code_format`/`prefix` legacy; counter `last_number` TIDAK dimigrasi (pola manual-increment = `[ARTIFACT]` do-not-replicate race DB-CONVENTIONS §6.5) → sequence DB per scope di-seed ≥ max legacy saat cutover.
**Konteks**: OQ-GT-02 ✅ format `credit_id` `branch(5)+YY+MM+SEQ(5)`; 3 code_type legacy (`TrCas`/`CreditId`/`CreditItid`) ber-format identik = risiko tabrakan → konsolidasi ke `CREDIT_ID` (BR-33 BE-01).
**Konsekuensi**: `CREDIT_ID` code_type change → maker-checker (salah format memutus keunikan nasional).

## Sources

- `docs/ARCHITECTURE-PROPOSAL.md §4` (ADR-02/03/05/07/13/14/15), `§10` (assumption register)
- `docs/prd/acquisition/BE-07-master-data-menus.md §1.3` (tiering), `§1.4` (D-11/D-12), `§6 BR-BE07-05` (maker-checker), `§3.3` (OQ-MASTERDATA-03), `§3.4` (cfg_number_format)
- `docs/prd/acquisition/BE-00 §11.0` (D-08..D-12)
- `.mega-sdd/vaults/acquisition/05-decisions.md` (parent — ADR-01..15 full)

## Open Questions

- **OQ-ARCH-STACK** [P1] — framework/transport/topologi (ITEC D-11); semua ADR *Proposed*
- **OQ-BE07-01/03** [P1] — maker-checker scope + checker role; HO roles (expand enum D-10 → `mst_role`?)
- **OQ-EXTMASTERS-01** [P1] — Tier C ownership (menentukan tiering final D-MD-01)
- Lengkap di `00-index.md` roll-up

### D-MD-06 — @ConditionalOnBean(JpaRepository.class) on JPA-dependent master-data beans (drift-fix F-004)
- **Keputusan**: Semua JPA-dependent master-data beans (services + controllers yang inject JpaRepository) diberi `@ConditionalOnBean(JpaRepository.class)` agar skip saat JPA autoconfig excluded (pre-existing contextLoads/AuthLogin tests exclude DataSource+Hibernate).
- **Konteks**: commit `684ba1c` — pre-existing jwt-login tests (`contextLoads`, `AuthLoginIntegrationTest`) exclude JPA autoconfiguration; tanpa conditional, MakerCheckerService/Controller injection fails context-load.
- **Konsekuensi**: (+) no context-load regression; (−) beans hanya aktif saat JPA live (runtime normal). Test focused-context tetap perlu `@MockitoBean` utk repos.
- **Sumber**: drift-fix F-004, commit `684ba1c`.
