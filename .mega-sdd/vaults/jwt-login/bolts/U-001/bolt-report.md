# Bolt Report — U-001 (Maven dependencies foundation)

## Status: DONE

## What was implemented

Foundation unit for the JWT-login feature. Modified only `pom.xml`, adding the 6 declared dependencies after the existing `spring-boot-starter` and `spring-boot-starter-test` entries:

1. `spring-boot-starter-web` (compile) — managed by Boot parent (4.1.1-SNAPSHOT)
2. `spring-boot-starter-security` (compile) — managed by Boot parent
3. `spring-boot-starter-data-jpa` (compile) — managed by Boot parent
4. `org.postgresql:postgresql` (runtime) — managed by Boot parent (resolves to 42.7.13)
5. `io.jsonwebtoken:jjwt-api` 0.12.6 (compile)
6. `io.jsonwebtoken:jjwt-impl` 0.12.6 (runtime)
7. `io.jsonwebtoken:jjwt-jackson` 0.12.6 (runtime)

The jjwt triplet is pinned to `0.12.6` (latest 0.12.x known-good, cached). No Lombok added (deferred per OQ-CN-2). Parent, properties, build, repositories, and pluginRepositories blocks were left intact. SDD-PROVENANCE XML comment trailer added before `</project>`.

## Acceptance test results

| # | Command | Result |
|---|---------|--------|
| 1 | `mvn -q dependency:resolve` | PASS — exit 0 |
| 2 | `mvn -q compile` | PASS — exit 0 |

Resolved-dependency confirmation (`mvn dependency:list`, filtered):

```
io.jsonwebtoken:jjwt-api:jar:0.12.6:compile
io.jsonwebtoken:jjwt-impl:jar:0.12.6:runtime
io.jsonwebtoken:jjwt-jackson:jar:0.12.6:runtime
org.postgresql:postgresql:jar:42.7.13:runtime
org.springframework.boot:spring-boot-starter-data-jpa:jar:4.1.1-SNAPSHOT:compile
org.springframework.boot:spring-boot-starter-security:jar:4.1.1-SNAPSHOT:compile
org.springframework.boot:spring-boot-starter-web:jar:4.1.1-SNAPSHOT:compile
```

Both acceptance tests passed on the first attempt; no retries required.

## Files changed

- `pom.xml` (modified, +43 lines) — only file touched.

## Commit

- SHA: `47a590ed5bc9dc3145bf31ac02c3e9521e903db7`
- Branch: `main`
- Message:
  ```
  feat(U-001): add web/security/jpa/postgresql/jjwt deps

  Foundation unit for JWT-login feature. Adds spring-boot-starter-web,
  -security, -data-jpa, postgresql (runtime), jjwt 0.12.6 triplet.
  No Lombok (deferred OQ-CN-2). ./mvnw broken — built with system mvn.

  SDD-PROVENANCE: U-001 vault=.mega-sdd/vaults/jwt-login
  ```
- No `--no-verify`, no push. Commit includes Co-Authored-By trailer.

## Hard rules honored

- §D-003 (DO_NOT_ADD_DEPS): Only the 6 declared dependencies were added; nothing extra. No Lombok (deferred per OQ-CN-2). The package.json guard is a no-op (no package.json exists). Confirmed: no Hard rule violated.
- Target-file whitelist: only `pom.xml` modified. No Java files created. `application.yaml` untouched.

## Anti-patterns honored

- D-003: No dependencies beyond the declared set. Lombok explicitly deferred.
- jjwt version: pinned to `0.12.6` (0.12.x). The 0.12.x API (`Jwts.builder().signWith(Key)`, `Jwts.parserBuilder().verifyWith(key)`) is preserved for U-005. No non-0.12.x version used.

## target_hashes

- pom.xml sha256 (working tree == committed blob at HEAD): `a76e01cdbfd9814e4f3e793be9b2c2990f7941a8b9eb684fbfb136249370fa10`

## bolt_self_report

- confidence: 0.98
- certain_decisions:
  - jjwt pinned to 0.12.6 (latest 0.12.x known-good, cached, satisfies U-005 API requirement)
  - jjwt-api on compile scope, jjwt-impl and jjwt-jackson on runtime scope (standard jjwt split)
  - postgresql on runtime scope (driver only needed at runtime)
  - Spring Boot starters left unversioned (managed by spring-boot-starter-parent 4.1.1-SNAPSHOT)
  - No Lombok added (OQ-CN-2 deferred)
  - Provenance trailer placed before `</project>`
- uncertain_decisions:
  - None material. (Minor: `mvn dependency:list -q` suppressed output, so verification used the non-quiet form; this is a verification detail, not a pom decision.)
- retry_history:
  - Attempt 1: both `dependency:resolve` and `compile` passed. No retries needed.

## reuse_decisions

- candidate: reuse-index.yaml scan
  decision: not_applicable
  reason: No `reuse-index.yaml` exists in the project (this is the foundation unit on a Spring Initializr skeleton; there are no existing helpers/services/models to reuse). The unit's scope is pom-only dependency addition, so there is nothing in the codebase to reuse. Reuse-first protocol satisfied vacuously.

## Provenance trailer verification

Present in committed `pom.xml` (line 115, before `</project>`):

```xml
<!-- SDD-PROVENANCE: U-001 | vault: .mega-sdd/vaults/jwt-login | unit: U-001 | deps: web,security,jpa,postgresql,jjwt-0.12.x -->
```

## Self-review findings

- Completeness: all 6 declared dependencies added with correct scopes; both acceptance tests pass.
- Quality: dependencies placed in the `<dependencies>` block after existing entries; formatting (tab indentation) matches the existing file style; parent/repositories/build blocks untouched.
- Discipline: only `pom.xml` modified; no extra deps; no Lombok; no new plugins; no `application.yaml` change.
- Testing: acceptance tests run with real `mvn` against real snapshot + Central repos (not mocks); both exit 0.
- Concerns: none.

## Review panel

**Tier:** full (risk signal: dependency manifest `pom.xml` in target_files → signal #2 fires)
**Lenses dispatched (parallel, blind):** spec, quality, security, standards
**Base SHA:** 37fcb86 · **Head SHA:** 47a590e

| Lens | Verdict | Critical | Important | Minor |
|---|---|---|---|---|
| spec | PASS | 0 | 0 | 1 |
| quality | PASS | 0 | 0 | 1 |
| security | PASS | 0 | 0 | 0 |
| standards | PASS | 0 | 0 | 2 |

**Merge result:** No spec ❌, no Critical → **mergeable**. All findings Minor (cosmetic / style preference). No re-dispatch required.

### Findings (merged; evidence-or-drop applied — all carry file:line)

| # | Severity | Lens(s) | Finding | Evidence |
|---|---|---|---|---|
| 1 | Minor | spec | `./mvnw` is non-functional (missing untracked `.mvn/wrapper/maven-wrapper.properties`); implementer substituted system `mvn`. Pre-existing env gap — `.mvn/` not tracked, not in target_files. Acceptance intent (resolve+compile) genuinely satisfied via `mvn`. Not a bolt defect. | pom.xml (target only); `.mvn/wrapper/` absent at base 37fcb86 |
| 2 | Minor | quality | jjwt version literal `0.12.6` repeated 3× across jjwt-api/impl/jackson. A `<jjwt.version>` property would make upgrades one-line + keep triplet provably aligned. Defensible as-is (frozen 0.12.x, only 3 occurrences); spec didn't ask for a property. | pom.xml head L67/L73/L80 |
| 3 | Minor | standards | `spring-boot-starter-test` (test scope) sits between `spring-boot-starter` and the new production starters, separating the four `spring-boot-starter*` siblings. Conventional layout puts test-scope deps last. Cosmetic. | pom.xml head L41-67 |
| 4 | Minor | standards | Trailing blank line between SDD-PROVENANCE comment and `</project>`. Cosmetic divergence only; no convention forbids it. | pom.xml head L114-116 |

**Dropped-no-evidence:** 0 (all findings anchored to file:line).
**Consensus (≥2 lenses):** 0 (each finding reported by a single lens).

### Gate decision

PASS — mergeable as-is. The 4 Minor findings are cosmetic/style and do not warrant a re-dispatch. Finding #1 (`./mvnw` gap) is an environment issue to address separately (the wrapper repair is out of U-001 scope; all downstream bolts will likewise use `mvn`). Findings #2–4 are optional polish.

### Post-flight Hard-rule scan

- Grammar: v1-bullet
- Rule (§D-003 DO_NOT_ADD_DEPS): **pass** — package.json absent (no-op guard); only the declared 7-dep set added, no extras, no Lombok.
- Provenance trailer: present (verified).
- Evidence: `.mega-sdd/vaults/jwt-login/bolts/U-001/postflight.json` (status: pass).
