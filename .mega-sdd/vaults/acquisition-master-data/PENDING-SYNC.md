# PENDING-SYNC — acquisition-master-data

> Drift findings queued for human resolution via `resolve-oq` / `sync`.
> Generated: 2026-07-28 · 4 findings (1 HIGH, 3 MEDIUM)

## F-001 [HIGH] — log_master_audit entity missing in code
- **Direction**: FIX_CODE — create `MasterAudit.java` @Entity @Table(name="log_master_audit") append-only
- **Vault ref**: 03-data-model.md §Audit (BR-BE07-04)
- **Provenance**: U-009 agent report explicitly deferred this entity

## F-002 [MEDIUM] — HierarchyMatrixController + TransactionTypeController missing @RequestMapping path
- **Direction**: FIX_CODE — add path values to both controllers
- **Vault ref**: 04-flows.md F-U-005 (E22-E28 endpoint paths)

## F-003 [MEDIUM] — MasterOperationalController DELETE endpoints violate BR-BE07-03 [LOCKED]
- **Direction**: FIX_CODE — remove 7 DELETE endpoints; replace with PATCH is_active=false
- **Vault ref**: 06-constraints.md BR-BE07-03 [LOCKED] + OQ-BE07-06 resolved (deactivate-only ALL master)
- **Note**: resolve-oq v1.3.1 changed PUBLIC_HOLIDAY to deactivate-only; U-010 code still has DELETE

## F-004 [MEDIUM] — @ConditionalOnBean pattern unwritten as ADR
- **Direction**: UPDATE_VAULT — add D-MD-06 to 05-decisions.md + constitution §I-010
- **Provenance**: commit 684ba1c (fix: @ConditionalOnBean on JPA-dependent beans)
