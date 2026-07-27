# Project Constitution — acquisition-master-data

**Status**: Active
**Version**: 1.3.0 (extends acquisition constitution v1.2.0 with master-data clauses §I; §A–§H inherited verbatim)
**Last reviewed**: 2026-07-27
**Sign-off**: Tech Lead / Security / Compliance (when relevant) — pending

> This constitution extends the project-wide rules established in `../acquisition/constitution.md` (§A–§H, which itself extends `../api-platform/constitution.md` §A–§G and `../jwt-login/constitution.md` §A–§F). §A–§H are inherited verbatim and re-asserted below (same project, same codebase — Spring Boot 4.1.1-SNAPSHOT + Java 21). §I adds master-data-module-specific clauses (BE-07 modul 07).

## §A. Coding standards (Non-negotiable) — inherited

- A-001..A-004: inherited (PascalCase + suffix layer, paket layer standar + `acquisition.masterdata` sub-package, test co-located, SLF4J + correlation id per `credit_id`).

## §B. Security baselines — inherited + extended

- B-001..B-014: inherited dari acquisition (jakarta.persistence, SecurityFilterChain, jwtSecret, CSRF, LDAP, no trust-all, no echo, actuator, Swagger, fail-closed gates B-010, no-self-approval B-011, super-user removed B-012, branch re-verify B-013, hapus anti-pattern legacy B-014).
- **B-015 (NEW)**: `MsBank.PasscodeBiBca` = **[REDACTED-SECRET]** — TIDAK diekspos endpoint mana pun dan TIDAK dimigrasi tanpa security review; bila live → secrets manager (BR-BE07-25, OQ-DLRPTN-05/OQ-REF-05). `BE-07 §6, §8`.
- **B-016 (NEW)**: ACL boundary Tier C — NO cross-DB three-part-name / linked-server four-part-name sebagai pola aplikasi baru (BR-BE07-26 `[LOCKED]`). Kegagalan baca lookup = `503 LOOKUP_SOURCE_UNAVAILABLE`, BUKAN sukses-kosong (BR-BE07-22). `BE-07 §6, §8`.

## §C. Architecture invariants — inherited + extended

- C-001..C-009: inherited (controller↔service, entity not exposed, logic in @Service, constructor injection, @Transactional, cross-module via event/API, zero-SP, PO minting 04, downstream PULL).
- **C-010 (NEW)**: Modul 07 = trunk reference leaf — dikonsumsi 01–05 + app-shell FE, TIDAK konsumsi internal. Konsumen baca 07 via API publik read-only; NO direct repo/tabel access 07 dari modul lain. `cfg_hierarchy_matrix` = pengecualian ter-dokumentasi: 07 (admin write definisi) + 03 (walking engine read) akses tabel yang sama via repo ter-scope (OQ-MASTERDATA-03 ✅ — single source, NO separate `cfg_approval_hierarchy_level`). `ARCH-PROP §3/§5, BE-07 §3.3`.
- **C-011 (NEW)**: 07 miliki **definisi** ladder (`cfg_hierarchy_matrix`); **eksekusi** routing (inbox, eskalasi, siapa bertindak) milik 03. 07 tidak menjalankan Flowable langsung — hanya sediakan config yang di-read delegate engine 03. `ADR-13, BE-07 §7.4`.

## §D. Anti-patterns (from legacy) — inherited + extended

- D-001..D-008: inherited (no verbatim newmojf, no hardcoded secret, no new dep tanpa review, no bake secret ke image, no host-install Maven, no do-not-replicate GOTCHA, no hardcode routing di BPMN, no sentuh ACT_*).
- **D-009 (NEW)**: JANGAN replika do-not-replicate master-data legacy: substring identity-type match (EC5), notes-as-join-key (EC7), name-literal sub-dealer (EC6), silent-success error swallowing (EC1/EC12/fuel), `IMasters` NotImplementedException (EC14), file-path columns di dealer, hardcoded company→folder, `TOP 1` tanpa ORDER BY, `/CRUD` demo menu, vestigial hidden fields `status_approver`/`notifikasi_hari` force-set JS. Setiap bug WAJIB diperbaiki + regression test. `BE-07 §11 marker-fidelity`.

## §E. Performance constraints — inherited

- E-001..E-003: inherited (response time/image size = unspecified; KPI baseline A-13 prasyarat Phase 2).

## §F. Compliance — inherited + extended

- F-001..F-005: inherited (PDP-Indonesia, audit `last_login`, audit `log_approval_history` regulatori, data OJK residen Indonesia, migrasi no-data-left-behind).
- **F-006 (NEW)**: Setiap mutasi master Tier A tercatat audit (who/when/before-after) di `log_master_audit` + `log_master_change_request` (append-only, INSERT-only); mutasi resource maker-checker juga menyimpan change-request + keputusan checker (BR-BE07-04). Legacy tidak punya audit master (tidak ada write path sama sekali). `BE-07 §6`.

## §G. Containerization clauses — inherited (from api-platform)

- G-001..G-008: inherited (multi-stage Dockerfile, .dockerignore, compose env_file+trust-store, healthcheck, spring profiles, additive permitAll, CORS externalize).

## §H. Acquisition clauses — inherited (from acquisition)

- H-001..H-008: inherited (schema DB-CONVENTIONS, credit_id business key, trans_type_id external-FK, marker mutability, NPP atomik, role census D-10, OQ-ID preserve, migrasi simulation-first).

## §I. Master-data clauses (NEW — this sub-vault, BE-07 modul 07)

- **I-001**: Tiga tier kepemilikan master (D-MD-01): Tier A OWNED (CRUD penuh, bukti lokal/D-08/admin-surface-FINCORE.WEB), Tier B SYNCED MIRROR (HR read-only + sync job), Tier C EXTERNAL READ-ONLY (`FC_MSTAPP_MCF` via ACL, ownership OQ-EXTMASTERS-01). Tier A dipilih hanya untuk master yang terbukti lokal / di-mandat D-08 / satu-satunya admin surface FINCORE.WEB. `BE-07 §1.3`.
- **I-002**: Maker-checker envelope generik E37 (D-MD-02) — resource BR-BE07-05 write masuk `pending_approval`; checker (≠ maker, `403 SELF_APPROVAL_BLOCKED`) approve/reject. Maker-checker = kontrol BARU (legacy TIDAK punya — jangan klaim paritas). Resource WAJIB: DEALER_BANK_REFERENCE (payout), BLACKLIST_OVERRIDE (AML), GL_TRANSACTION_TYPE_LINK (CoA), GENERAL_PARAMETER, MENU.trans_type_id_prefix, TRANSACTION_TYPE/APPROVAL_HIERARCHY_LEVEL, dealer legal identity, NUMBER_FORMAT `CREDIT_ID`. Scope checker = OQ-BE07-01. `BE-07 §6 BR-BE07-05, §7.2`.
- **I-003**: No hard delete (BR-BE07-03) — master konfigurasi lifecycle hanya create + toggle active/inactive; `DELETE` → `405`/`404`. Pengecualian: `PUBLIC_HOLIDAY` (OQ-BE07-06). `BE-07 §6`.
- **I-004**: Role enum tertutup D-10 (D-MD-04) — `mst_user.role` = `VARCHAR`+`CHECK` `CMO|MARKETING_HEAD|CREDIT_ANALYST|KEPALA_CABANG|CREDIT_ADMIN`; TIDAK ada `SUPER_USER` (D-09). Tabel `mst_role` TIDAK dibuat selama enum tertutup; bila OQ-BE07-03 perluas role HO → diangkat jadi `mst_role` (jangan tambah nilai diam-diam). `BE-07 §3.1, §6 BR-BE07-01`.
- **I-005**: `APP_USER` TANPA password (BR-SHELL-1 `[LOCKED]`) — auth delegated LDAP; `APP_USER` hanya bisa dibuat untuk NIK yang ada di `mst_employee_mirror` dan tidak resigned (BR-BE07-02). TIDAK ada `POST /employees` (HR system-of-record BR-EMPLOYEE-1). `BE-07 §3.1, §6`.
- **I-006**: Validasi ladder server-side (fix OQ-MASTERDATA-02) — BR-BE07-15 (Level-1 `is_approver=true` ditolak), BR-BE07-16 (`next_pic` required/empty), BR-BE07-17 (PIC harus ada & tidak resigned — fix celah V4 NIK tanpa guard). Cleansing import wajib V4+V6 (OQ-MIG-05 profil prod). `BE-07 §3.3, §6`.
- **I-007**: `transaction_type_code` + `trans_type_id_prefix` = external-FK `[LOCKED]` char-for-char (BR-PRODASSET-7/14) — mutasi prefix → maker-checker + warning transaction-type ter-impact; `mapping` disimpan eksplisit (bukan derive `substring`). `BE-07 §6 BR-BE07-14/18`.
- **I-008**: Lookup fail = error eksplisit (BR-BE07-22) — `503 LOOKUP_SOURCE_UNAVAILABLE`/`404`, BUKAN sukses-kosong (fix EC1/EC12/fuel silent-success). "employee resigned", "not found", "source error" = tiga sinyal berbeda. `BE-07 §6`.
- **I-009**: `cfg_number_format` + DB sequence (D-MD-05) — counter `last_number` TIDAK dimigrasi (pola manual-increment `[ARTIFACT]` do-not-replicate race); sequence DB per scope di-seed ≥ max legacy saat cutover. `CREDIT_ID` code_type change → maker-checker (salah format memutus keunikan nasional `credit_id`, OQ-GT-02 ✅). Consumer BE-01. `BE-07 §3.4`.
