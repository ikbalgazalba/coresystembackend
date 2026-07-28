# DRIFT-REPORT — acquisition-master-data

> **Vault**: `.mega-sdd/vaults/acquisition-master-data/` (v1.3.2, mode=existing)
> **Codebase**: Spring Boot 4.1.1-SNAPSHOT, Java 21, Maven — `src/main/java/com/coresystem/coresystembackend/masterdata/`
> **Scan date**: 2026-07-28 · **git HEAD**: `3960970` · **Scope**: full scan
> **Framework**: spring-boot (high confidence, from codebase-map §7)
> **Working tree**: clean (all committed)

## Summary

| Category | High | Medium | Low | Total |
|---|---|---|---|---|
| Missing in code | 1 | 0 | 0 | 1 |
| Missing in vault | 0 | 1 | 0 | 1 |
| Name drift | 0 | 0 | 0 | 0 |
| Type drift | 0 | 0 | 0 | 0 |
| Behavior drift | 0 | 1 | 0 | 1 |
| Decision violation | 0 | 0 | 0 | 0 |
| Decision unwritten | 0 | 1 | 0 | 1 |
| Confirmed match | — | — | — | 24 |
| **Total findings** | **1** | **3** | **0** | **4** |

**Overall**: vault and code are **highly aligned** (24 confirmed matches, 4 findings — 1 missing entity, 3 minor). No CRITICAL findings. No decision violations. Constitution hash matches.

## Findings

### F-001 — Missing in code: `log_master_audit` entity (HIGH)

- **Category**: Missing in code
- **Confidence**: high
- **Severity**: HIGH (vault entity, BR-BE07-04 audit requirement)
- **Vault ref**: `03-data-model.md §Audit` — `log_master_audit` (before-after JSON snapshot per Tier A mutation, append-only INSERT-only)
- **Code ref**: `grep -rn 'log_master_audit\|MasterAudit' src/main/java/.../masterdata/` → 0 hits
- **Detail**: U-009 (maker-checker) implemented `log_master_change_request` but NOT `log_master_audit`. The U-009 agent's report explicitly noted: "The unit spec mentions `MasterAudit` (log_master_audit) but the task prompt's target_files do not include it. I followed the task prompt strictly and left MasterAudit for a later unit."
- **Suggested action**: Create `MasterAudit.java` `@Entity @Table(name="log_master_audit")` in `masterdata/makercheck/` or `masterdata/common/` — append-only, fields: id, entityType, entityId, beforeJson, afterJson, actorNik, actionPerformedAt. Wire into `MakerCheckerService.approve()` to write before/after snapshot.

### F-002 — Missing in vault: HierarchyMatrixController + TransactionTypeController missing @RequestMapping path value (MEDIUM)

- **Category**: Behavior drift
- **Confidence**: medium
- **Severity**: MEDIUM
- **Vault ref**: `04-flows.md F-U-005` — E22-E28 endpoints expected at `/transaction-codes`, `/transaction-types`, `/approval-hierarchies`
- **Code ref**: `HierarchyMatrixController.java:60` — `@RequestMapping` (no value); `TransactionTypeController.java:64` — `@RequestMapping` (no value)
- **Detail**: Both controllers have `@RequestMapping` annotation WITHOUT a path value — endpoints are mapped at root `/` instead of `/transaction-codes`, `/transaction-types`, `/approval-hierarchies`. Other controllers (DealerController `/dealers`, AppUserController `/users`, etc.) correctly specify paths.
- **Suggested action**: Add path values: `@RequestMapping("/transaction-codes")` / `@RequestMapping("/approval-hierarchies")` (or a combined `/transtype` base). Verify endpoint tests still pass.

### F-003 — Behavior drift: MasterOperationalController has DELETE endpoints (MEDIUM)

- **Category**: Behavior drift
- **Confidence**: medium
- **Severity**: MEDIUM (BR-BE07-03 [LOCKED] deactivate-only)
- **Vault ref**: `06-constraints.md BR-BE07-03 [LOCKED]` — "Master konfigurasi TIDAK bisa di-hard-delete — lifecycle hanya create + toggle active/inactive." OQ-BE07-06 resolved: PUBLIC_HOLIDAY also deactivate-only (E33 DELETE dropped).
- **Code ref**: `MasterOperationalController.java` — DELETE=7 endpoints
- **Detail**: The vault resolved OQ-BE07-06 to "deactivate-only (no hard-delete)" for ALL master including PUBLIC_HOLIDAY. But the U-010 agent's code still has 7 DELETE endpoints (the agent's report says "DELETE allowed for public-holiday" per the original spec, but resolve-oq v1.3.1 changed this to deactivate-only). This is a **resolve-oq decision not yet propagated to code**.
- **Suggested action**: Remove DELETE endpoints from MasterOperationalController; replace with PATCH `is_active=false` (deactivate-only). This aligns with the resolved OQ-BE07-06.

### F-004 — Decision unwritten: @ConditionalOnBean(JpaRepository.class) pattern not in vault (LOW→MEDIUM)

- **Category**: Decision unwritten
- **Confidence**: high
- **Severity**: MEDIUM
- **Vault ref**: Not in `05-decisions.md` or `constitution.md`
- **Code ref**: 18 files use `@ConditionalOnBean(JpaRepository.class)` across `masterdata/`
- **Detail**: The `@ConditionalOnBean(JpaRepository.class)` pattern was introduced during execute-bolts to prevent context-load regression on pre-existing tests that exclude JPA autoconfiguration (`contextLoads`, `AuthLoginIntegrationTest`). This is a significant architectural decision (all JPA-dependent master-data beans are conditional) that should be captured as an ADR or constitution clause.
- **Suggested action**: Add `D-MD-06: @ConditionalOnBean on JPA-dependent master-data beans` to `05-decisions.md` + constitution §I-010. Cite commit `684ba1c` as provenance.

## Confirmed matches (24)

- **24/26 vault entities** present in code with correct `@Table(name=...)` names (all match vault exactly).
- **Role enum D-10**: exactly `CMO, MARKETING_HEAD, CREDIT_ANALYST, KEPALA_CABANG, CREDIT_ADMIN` — no `SUPER_USER` (D-09 ✅).
- **AppUser**: no `password` field (BR-SHELL-1 ✅), no `isSuperUser` (D-09 ✅).
- **MakerCheckerService**: self-approval blocked (`403 SELF_APPROVAL_BLOCKED`), terminal immutable (`409`), `202 pending_approval` on submit.
- **HierarchyMatrixService**: server-side validation BR-BE07-15..17 (Level-1 is_approver→422, next_pic required/empty, PIC resigned→422).
- **EmployeeMirror**: `isResigned` boolean field explicit (fix Edge Case 12 ✅).
- **Dealer**: `isSubDealerEnabled` boolean flag (fix EC6 ✅), `parentDealerCode` typed FK (fix EC7 ✅), KTP/NPWP `@Column` mapped `[LOCKED]`.
- **LookupController**: `503 LOOKUP_SOURCE_UNAVAILABLE` on backing unreachable (fix EC1/EC12 ✅).
- **NumberFormatService**: `generateCreditId` format `branch(5)+YY+MM+SEQ(5)` (OQ-GT-02 ✅).
- **Provenance trailers**: 57 files with `SDD-PROVENANCE` comments.
- **Constitution hash**: matches (`6704b851...`).
- **OQ status**: 0 open, 12 resolved, 16 deferred (consistent with vault.json).

## PENDING-SYNC queue

| # | Finding | Category | Severity | Suggested direction |
|---|---|---|---|---|
| F-001 | log_master_audit entity missing | Missing in code | HIGH | FIX_CODE — create entity + wire into MakerCheckerService |
| F-002 | Controller @RequestMapping missing path | Behavior drift | MEDIUM | FIX_CODE — add path values |
| F-003 | DELETE endpoints violate BR-BE07-03 LOCKED | Behavior drift | MEDIUM | FIX_CODE — remove DELETE, replace with PATCH deactivate |
| F-004 | @ConditionalOnBean pattern unwritten | Decision unwritten | MEDIUM | UPDATE_VAULT — add ADR D-MD-06 + constitution §I-010 |
