# Binding — acquisition-master-data

> **Vault**: `.mega-sdd/vaults/acquisition-master-data/` (sub-vault module 07, v1.3.0)
> **Codebase map**: `.mega-sdd/codebase/codebase-map.md` (scan 2026-07-24T03:47:51Z, commit `0682bb58`, precision ast, engine tree-sitter)
> **KB**: `--no-kb` (`.mega-sdd/knowledge-base/` absent on disk — OQ-AC-PROVENANCE)
> **Framework pack**: `references/framework-conventions/spring.md` (spring-boot 4.1.1-SNAPSHOT, pack_tier full, detected high confidence)
> **Project constitution gate**: `.mega-sdd/constitution.md` absent (per-vault constitutions only; no project-scope locked layer) — gate skipped, no project-scope CONFLICTs
> **Bound at**: 2026-07-27 · **git HEAD**: `0cc72f8820b258a8c5ae1aa923b0e480c56fc9c2`

## Summary

| Metric | Count |
|---|---|
| claims_total | 19 |
| confirmed | 0 |
| conflict | 0 |
| oq | 0 (vault OQs tracked separately — not binding-blockers; `--strict` not set) |
| new | 19 (greenfield module — no existing implementation) |
| implemented | 0 |
| unknown | 0 |

**Verdict: CLEAN — no CONFLICTs.** Modul 07 (master-data) adalah **greenfield module** di repo brownfield: tidak ada satu pun dari 26 tabel target, 38 endpoint (E1-E38), 27 BR, RBAC D-10, atau tiering A/B/C yang ada di codebase-map. Semua claim = `NEW` implementation-state. Codebase-map silent untuk semua claim 07 → tidak ada contradiction. KB di-skip (`--no-kb`, absent). Project constitution gate: `.mega-sdd/constitution.md` absent (per-vault constitutions only).

**Coexistence note (bukan CONFLICT):** Existing `Users` entity (`mojf_users` table, jwt-login vault) vs vault 07 `mst_user` = **dua tabel berbeda yang hidup berdampingan**, bukan overwrite. `Users` (jwt-login) = auth legacy (field `pass`, `urole` Long, `kode_unit_kerja`); `mst_user` (07) = rebuild RBAC (no `pass` LDAP BR-SHELL-1, `role` enum D-10 `[LOCKED]`, `branch_scope`). Keduanya boleh coexist selama transisi; `mst_user` adalah target rebuild, `Users`/`mojf_users` = `[ARTIFACT]` di-fase-out per OQ migrasi. Tidak ada name collision (table name berbeda: `mst_user` vs `mojf_users`).

## Confirmed Claims

*(none — greenfield module; no existing implementation to confirm against)*

## Implementation State Map

> Semua claim `NEW` — greenfield module 07 di repo brownfield. Vault 07 memodelkan target rebuild; codebase-map hanya mencatat jwt-login artifacts (AuthUserController, Users/mojf_users, JwtUtils, LdapUcsService). Tidak ada anchor codebase-map untuk claim 07 manapun. Detail claim (vault source) ada di kolom "Field diff / claim ref".

| Claim ID | Verdict | State | Anchor | Confidence | Field diff / claim ref |
|---|---|---|---|---|---|
| C-01 | NEW | NEW | — | high | User & RBAC: mst_user + mst_user_branch_scope + cfg_menu + cfg_menu_role_grant (+cfg_menu_user_grant_special OQ-BE07-05); role D-10, no super-user, no password BR-SHELL-1. `03-data-model.md §3.1; constitution §I-004/005` |
| C-02 | NEW | NEW | — | high | Employee mirror Tier B: mst_employee_mirror (HR sync read-only, is_resigned eksplisit). `03-data-model.md §3.1` |
| C-03 | NEW | NEW | — | high | Dealer family Tier A: mst_dealer + mst_dealer_document + mst_dealer_personnel + mst_dealer_job_title + mst_dealer_bank_reference + mst_dealer_branch_access (KTP/NPWP LOCKED zero-diff, payout maker-checker). `03-data-model.md §3.2` |
| C-04 | NEW | NEW | — | high | TransType hierarchy Tier A: cfg_transaction_code + cfg_transaction_type + cfg_hierarchy_matrix (single source admin-write+03-walk; transaction_type_code external-FK LOCKED). `03-data-model.md §3.3; 05-decisions.md D-MD-03` |
| C-05 | NEW | NEW | — | high | Master operasional Tier A: mst_approval_reason + mst_credit_source + mst_branch_credit_source + mst_blacklist_override + mst_public_holiday + mst_general_parameter + mst_promotion_line_text. `03-data-model.md §3.4` |
| C-06 | NEW | NEW | — | high | GL mapping Tier A: map_transaction_type_gl (CoA LOCKED zero-diff, maker-checker, no delete, data milik finance). `03-data-model.md §3.4` |
| C-07 | NEW | NEW | — | high | Numbering config Tier A: cfg_number_format (CREDIT_ID code_type LOCKED, counter→DB sequence, consumer BE-01). `03-data-model.md §3.4; 05-decisions.md D-MD-05` |
| C-08 | NEW | NEW | — | high | Audit log_: log_master_change_request + log_master_audit (append-only INSERT-only). `03-data-model.md §Audit` |
| C-09 | NEW | NEW | — | high | API E1-E11: users/menus/roles/employees (provision, lifecycle, menu tree, grants, HR picker). Existing /api/auth/dologin = jwt-login distinct path. `04-flows.md F-U-001/002/003` |
| C-10 | NEW | NEW | — | high | API E12-E21: dealer family CRUD + payment-eligible-contacts (maker-checker bank-ref). `04-flows.md F-U-004` |
| C-11 | NEW | NEW | — | high | API E22-E28: transaction-type hierarchy config (server-side validation BR-BE07-15..17). `04-flows.md F-U-005` |
| C-12 | NEW | NEW | — | high | API E29-E38: master operasional + lookup Tier C + maker-checker envelope E37 + number-formats. `04-flows.md F-U-006/007` |
| C-13 | NEW | NEW | — | high | Lookup Tier C: FC_MSTAPP_MCF 310 master via ACL (no local table, 503 LOOKUP_SOURCE_UNAVAILABLE). `03-data-model.md §Tier C; constitution §I-008/B-016` |
| C-14 | NEW | NEW | — | high | Maker-checker envelope E37 (kontrol BARU — legacy tidak punya; resource BR-BE07-05). `04-flows.md F-U-007; constitution §I-002` |
| C-15 | NEW | NEW | — | high | Role enum D-10 LOCKED: CMO/MARKETING_HEAD/CREDIT_ANALYST/KEPALA_CABANG/CREDIT_ADMIN; no SUPER_USER (D-09). Existing Users.urole Long = jwt-login distinct. `constitution §I-004; 06-constraints.md BR-BE07-01` |
| C-16 | NEW | NEW | — | high | trans_type_id_prefix + transaction_type_code = external-FK LOCKED char-for-char (BR-PRODASSET-7/14). `constitution §I-007; 06-constraints.md BR-BE07-14/18` |
| C-17 | NEW | NEW | — | high | Validasi ladder server-side (fix OQ-MASTERDATA-02 V4/V6): Level-1 is_approver, next_pic, PIC-not-resigned. `constitution §I-006; 06-constraints.md BR-BE07-15/16/17` |
| C-18 | NEW | NEW | — | high | No hard delete (deactivate-only BR-BE07-03); lookup fail=503 not sukses-kosong (BR-BE07-22). `constitution §I-003/008` |
| C-19 | NEW | NEW | — | high | Flowable cfg_hierarchy_matrix shared 07-admin + 03-walk (single source, OQ-MASTERDATA-03 ✅). No Flowable in map. `02-architecture.md §C-010/011; 05-decisions.md D-MD-03` |

## Tech-OQ Auto-Resolved

> Tech-OQ `scan` resolution fires only at `classification_confidence: high` AND codebase-map has a match. Modul 07 greenfield → codebase-map silent → no scan auto-resolution. Two tech/scan OQs in vault (`OQ-DLRPTN-02` ms_credit_source lokal?, `OQ-EXTMASTERS-08` dual-DB holiday) require DBA/data evidence not in codebase-map → stay `blocking` (not auto-resolved). Per `references/oq-resolution.md`: no/multiple matches flips to blocking, never guess.

- OQ-DLRPTN-02 (tech/scan/medium): `ms_credit_source` local vs export — scan_query needs DB census (`.mega-sdd/knowledge-base/30-data-model/` absent); codebase-map silent → stays open/blocking.
- OQ-EXTMASTERS-08 (tech/scan/medium): `MsPublicHoliday` dual-DB — scan_query needs DB dump comparison (KB absent); codebase-map silent → stays open/blocking.

## Tech-OQ Recommendations

*(none — no `recommend`-mode OQ in vault 07)*

## Suggested Unit Hard Rules

> Promoted to machine-validated Hard Rule ONLY when KB marker `[VERIFIED]` AND anchored in codebase-map. KB absent (`--no-kb`) → no `[VERIFIED]` promotion this pass. Framework pack spring.md `[VERIFIED]`-equivalent conventions (naming/idioms) ARE anchored in codebase-map (existing jwt-login code follows them) → promoted. Anti-patterns (vault BR / constitution §I / do-not-replicate EC) = informational (no KB `[VERIFIED]` marker this pass).

### From framework pack spring.md (anchored in codebase-map — codebase-map §5/§6 confirms existing code follows these)

```
HARD_RULE: Controller classes MUST end with `Controller` suffix
  path_glob: src/main/java/**/controller/**/*.java
  rule_type: NAMING_RULE
  pattern: 'Controller\.java$'
  source: framework-conventions/spring.md §Naming standards; codebase-map §5 (AuthUserController)

HARD_RULE: Service classes MUST end with `Service` suffix
  path_glob: src/main/java/**/service/**/*.java
  rule_type: NAMING_RULE
  pattern: 'Service\.java$'
  source: framework-conventions/spring.md; codebase-map §5 (LdapUcsService)

HARD_RULE: Repository interfaces MUST extend JpaRepository and end with `Repository` suffix
  path_glob: src/main/java/**/repository/**/*.java
  rule_type: NAMING_RULE
  pattern: 'Repository\.java$'
  source: framework-conventions/spring.md; codebase-map §2 (UserRepository extends JpaRepository)

HARD_RULE: Constructor injection (final fields); field @Autowired dilarang
  rule_type: PATTERN_RULE
  source: framework-conventions/spring.md §Idioms; codebase-map §6 (AuthUserController ctor injection); constitution §C-004 (inherited)

HARD_RULE: Entity tidak di-expose langsung dari REST controller — pakai DTO
  rule_type: PATTERN_RULE
  source: framework-conventions/spring.md §Idioms; codebase-map §2 (DTOs LoginRequest/JwtResponse); constitution §C-002 (inherited)

HARD_RULE: jakarta.persistence.* wajib (Boot 4.x); javax.persistence.* dilarang
  rule_type: FORBIDDEN_PATTERN
  pattern: 'javax\.persistence'
  source: constitution §B-001 (inherited); codebase-map §7 spring_boot 4.x
```

### From vault 07 constitution §I + BR-BE07 (informational Anti-patterns — no KB `[VERIFIED]` this pass; promote at execute-bolts when bolt anchors land)

- `I-002` maker-checker WAJIB BR-BE07-05 (resource list) — informational until bolt implements E37 envelope
- `I-003` no hard delete (deactivate-only) — informational
- `I-004` role enum D-10 tertutup, no SUPER_USER — informational (enforce via `CHECK` constraint + service guard)
- `I-006` ladder validation server-side BR-BE07-15..17 — informational (enforce via Bean Validation + service guard)
- `I-007` `trans_type_id_prefix`/`transaction_type_code` external-FK `[LOCKED]` char-for-char — informational (enforce via migration checksum zero-diff)
- `I-008` lookup fail = `503` not sukses-kosong — informational (enforce via ACL adapter error mapping)
- do-not-replicate EC1/5/6/7/12/14 — informational (regression test per item)

## Conflicts [BLOCKING]

*(none — clean binding)*

## Open Questions (vault — not binding-blockers; `--strict` not set)

> Vault 07 carries 28 OQ (12 P1 / 11 P2 / 5 P3; OQ-MASTERDATA-03 ✅ resolved). These are tracked in `vault.json.open_questions` + `00-index.md` roll-up. They do NOT block binding (no CONFLICT). Per `references/conflict-resolution.md`, `--strict` (block on OQ too) is NOT set. Top P1 (governance/DBA/ITEC, not code): OQ-BE07-01/02/03, OQ-EXTMASTERS-01/07, OQ-DLRPTN-01/05, OQ-CUSTMASTER-04, OQ-MASTERDATA-02, OQ-ARCH-STACK, OQ-AC-PROVENANCE, OQ-MEET-02. Recommend `resolve-oq` triage before `generate-units` so units aren't all blocked.

## Auto-Resolved Deferred OQs

*(none — no `defer_to: binding` OQs in vault 07)*

## Phase-advisor pass

> **advisor: pending** — this binding is clean (conflict==0); the phase-advisor adversarial pass for binding (Step 2.12) will run as a separate verification. Since greenfield module produces no CONFLICT verdicts to adversarially check (no false-CONFIRMED possible when nothing is confirmed), the advisor's binding-focus findings would be limited to missed-CONFLICT (none expected) or state-map errors. Recorded as `advisor: pending` in vault.json changelog; if run and clean, upgrades to `advisor: {model, findings:{high:0,med:0,low:0}}`.

## Next step

**CLEAN — produce `<vault>/bound/`.** No CONFLICTs. Proceed to `/mega-sdd:generate-units .mega-sdd/vaults/acquisition-master-data/` (the bound-vault is the nested `bound/` subdir).

> ⚠️ **Caveat untuk generate-units:** 12 P1 OQ masih open (governance/DBA/ITEC — bukan code). `generate-units` akan menghasilkan unit specs, tapi beberapa unit mungkin ter-block oleh OQ yang belum di-resolve (terutama OQ-BE07-01 maker-checker scope, OQ-BE07-02 HR sync mechanism, OQ-EXTMASTERS-01 Tier C ownership). Disarankan: `resolve-oq` triage P1 yang bisa di-jawab (OQ-BE07-01/02/03 governance, OQ-DLRPTN-01 dealer shape) SEBELUM generate-units, supaya unit tidak semua blocked. OQ-AC-PROVENANCE (KB absent) tidak block unit generation (units pakai PRD sebagai source, bukan KB).
