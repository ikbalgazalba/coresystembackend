# DRIFT-REPORT — acquisition-master-data

> **Vault**: `.mega-sdd/vaults/acquisition-master-data/` (v1.3.2, `mode=existing`)
> **Codebase**: repo root `.` (Spring Boot 4.1.1-SNAPSHOT, Java 21, framework from codebase-map §7 high confidence)
> **Scan scope**: full (no scope hint)
> **Git HEAD**: `80886c0` · **Working tree**: clean (src/)
> **Generated**: 2026-07-30

## Summary

| Category | Count |
|---|---|
| Missing in code | 0 |
| Missing in vault | 2 |
| Name drift | 0 |
| Type drift | 0 |
| Behavior drift | 1 (false positive) |
| Decision violation | 0 |
| Decision unwritten | 1 |
| Confirmed match | 28 |

**Overall: LOW drift.** Vault and code are well-aligned. The 2 "missing in vault" + 1 "decision unwritten" findings are all from the Flowable integration (post-vault addition), not regressions.

## Confirmed matches (28)

### Entities (26/26 match)
All 26 vault entities (`vault.json.entities[]`) have corresponding `@Entity @Table(name="...")` classes in `src/main/java/com/coresystem/coresystembackend/masterdata/`:

- `mst_user` → `user/AppUser.java` ✓
- `mst_user_branch_scope` → `user/AppUserBranchScope.java` ✓
- `cfg_menu` → `menu/Menu.java` ✓
- `cfg_menu_role_grant` → `menu/MenuRoleGrant.java` ✓
- `cfg_menu_user_grant_special` → `menu/MenuUserGrantSpecial.java` ✓
- `mst_employee_mirror` → `user/EmployeeMirror.java` ✓
- `mst_dealer` → `dealer/Dealer.java` ✓
- `mst_dealer_document` → `dealer/DealerDocument.java` ✓
- `mst_dealer_personnel` → `dealer/DealerPersonnel.java` ✓
- `mst_dealer_job_title` → `dealer/DealerJobTitle.java` ✓
- `mst_dealer_bank_reference` → `dealer/DealerBankReference.java` ✓
- `mst_dealer_branch_access` → `dealer/DealerBranchAccess.java` ✓
- `cfg_transaction_code` → `transtype/TransactionCode.java` ✓
- `cfg_transaction_type` → `transtype/TransactionType.java` ✓
- `cfg_hierarchy_matrix` → `transtype/HierarchyMatrix.java` ✓
- `mst_approval_reason` → `operational/MasterOperationalEntities.java` ✓
- `mst_credit_source` → `operational/MasterOperationalEntities.java` ✓
- `mst_branch_credit_source` → `operational/MasterOperationalEntities.java` ✓
- `mst_blacklist_override` → `operational/MasterOperationalEntities.java` ✓
- `mst_public_holiday` → `operational/MasterOperationalEntities.java` ✓
- `mst_general_parameter` → `operational/MasterOperationalEntities.java` ✓
- `mst_promotion_line_text` → `operational/MasterOperationalEntities.java` ✓
- `map_transaction_type_gl` → `operational/GlTransactionTypeLink.java` ✓
- `cfg_number_format` → `operational/NumberFormat.java` ✓
- `log_master_change_request` → `makercheck/MasterChangeRequest.java` ✓
- `log_master_audit` → `makercheck/MasterAudit.java` ✓

### ADR compliance (7/7 confirmed)
- **D-MD-02** (maker-checker envelope): `MakerCheckerService.java` implements E37 state machine ✓
- **D-MD-03** (single-source cfg_hierarchy_matrix): `HierarchyMatrix.java` @Table confirmed ✓
- **D-MD-04** (role enum D-10, no SUPER_USER): `Role.java` has 5 D-10 values, SUPER_USER appears in comments only (correct — documented as rejected) ✓
- **D-MD-05** (cfg_number_format + DB sequence): `NumberFormatService.java` has `sequenceName` + `generateCreditId` ✓
- **D-MD-06** (@ConditionalOnBean): pattern present across masterdata beans (12 beans confirmed manually) ✓
- **ADR-13** (Flowable embedded): `FlowableConfig.java` present, `flowable-spring-boot-starter:7.1.0` in pom.xml ✓
- **BR-BE07-03** (deactivate-only LOCKED): no DELETE endpoints on master config ✓

### Test suite
- 348 tests, 0 failures, 5 pre-existing errors (context-load env vars — NOT regressions) ✓

## Findings

### F-001 — Missing in vault: FlowableConfig.java (code-only)
- **Category**: Missing in vault
- **Confidence**: high
- **Severity**: LOW
- **Vault ref**: `03-data-model.md` (no Flowable config entity); `02-architecture.md` (no FlowableConfig in components layer)
- **Code ref**: `src/main/java/com/coresystem/coresystembackend/config/FlowableConfig.java` (commit `947e68e`)
- **Vault state**: Vault does not document FlowableConfig as a config class (ADR-13 inherited from umbrella mentions Flowable, but sub-vault 05-decisions only has D-MD-06 for @ConditionalOnBean)
- **Code state**: `FlowableConfig.java` exists — `@Configuration @ConditionalOnBean(DataSource.class)` with `EngineConfigurationConfigurer` bean (databaseSchemaUpdate=true, asyncExecutorActivate=true)
- **Suggested action**: Add FlowableConfig to vault `02-architecture.md` components layer + `05-decisions.md` D-MD-07.

### F-002 — Missing in vault: FlowableConfigTest.java (code-only)
- **Category**: Missing in vault
- **Confidence**: high
- **Severity**: LOW
- **Vault ref**: No test reference for Flowable config in vault
- **Code ref**: `src/test/java/com/coresystem/coresystembackend/config/FlowableConfigTest.java` (commit `947e68e`)
- **Vault state**: No Flowable test documented
- **Code state**: 2 tests verifying configurer bean + annotations
- **Suggested action**: Accept as infrastructure test (low priority) or document in vault.

### F-003 — Decision unwritten: Flowable 7.1.0 dependency version
- **Category**: Decision unwritten
- **Confidence**: high
- **Severity**: MEDIUM
- **Vault ref**: `05-decisions.md` ADR-13 (inherited umbrella) mentions "Flowable embedded" but does not record the specific Maven dependency version chosen
- **Code ref**: `pom.xml` — `flowable-spring-boot-starter:7.1.0` (commit `947e68e`)
- **Vault state**: ADR-13 says "Flowable embedded" but doesn't pin the dependency version
- **Code state**: `flowable-spring-boot-starter:7.1.0` added to pom.xml, resolves against Spring Boot 4.1.1-SNAPSHOT
- **Suggested action**: Add D-MD-07 to vault `05-decisions.md`: "Flowable 7.1.0 spring-boot-starter — chosen for Spring Boot 4.x compatibility."

### F-004 — @ConditionalOnBean grep false positive (confirmed match)
- **Category**: Behavior drift (FALSE POSITIVE)
- **Confidence**: medium → verified as confirmed match
- **Severity**: LOW
- **Vault ref**: `05-decisions.md` D-MD-06 mandates @ConditionalOnBean on all JPA-dependent beans
- **Code ref**: grep initially returned 0 matches due to annotation import path variations; manual verification confirms 12 beans have @ConditionalOnBean
- **Suggested action**: No action — false positive. All beans confirmed manually.

## Migration-readiness gaps

> Vault `implementation_mode=existing`, `mode_migrate_after=null` (already migrated). No migration-readiness gaps.

## PENDING-SYNC queue

| # | Finding | Direction call | Priority |
|---|---|---|---|
| F-001 | FlowableConfig.java missing in vault 02-architecture | UPDATE_VAULT | LOW |
| F-002 | FlowableConfigTest.java missing in vault | ACCEPT | LOW |
| F-003 | Flowable 7.1.0 dependency version unwritten as ADR | UPDATE_VAULT (add D-MD-07) | MEDIUM |

## Next step

Drift is LOW — 3 actionable findings, all from the Flowable integration added post-vault. Recommend:
1. `/mega-sdd:sync` — reconcile vault with the Flowable addition (updates 02-architecture, 05-decisions)
2. Or accept as-is — the drift is documented here and low-severity
