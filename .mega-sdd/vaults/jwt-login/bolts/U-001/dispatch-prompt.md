# Dispatch Prompt — U-001 (Maven dependencies foundation)

## Mission

Implement ONE mega-sdd unit: **U-001 — Add Maven dependencies for web, security, JPA, PostgreSQL, JWT**. Write the target file, write + run the acceptance test, commit atomically. Report DONE or HALT.

## Environment facts (CRITICAL — read first)

- **Working dir:** `/home/ikbalgazalba/AI/Project/coresystembackend`
- **`./mvnw` is BROKEN** — `.mvn/wrapper/maven-wrapper.properties` is missing. **Use system `mvn` instead** (Maven 3.9.16, Java 21.0.11 via SDKMAN, on PATH). Every `./mvnw` command in the unit → substitute `mvn`.
- Spring Boot **4.1.1-SNAPSHOT** parent is cached locally; `repo.spring.io/snapshot` is reachable. Maven Central is reachable (jjwt + postgresql already in `~/.m2` cache).
- Git branch: `main`. No pre-commit hooks. gpgsign off.
- Repo is a Spring Initializr skeleton — only `CoresystembackendApplication` exists. This is the foundation unit.

## Unit spec (authoritative — implement EXACTLY this)

```
id: U-001
title: Add Maven dependencies for web, security, JPA, PostgreSQL, JWT
task_type: create
depends_on: []
module: M-foundation
target_files:
  - path: pom.xml
    operation: modify
acceptance_test:
  - type: test
    command: ./mvnw -q dependency:resolve   # USE: mvn -q dependency:resolve
    expects: passes
  - type: test
    command: ./mvnw -q compile              # USE: mvn -q compile
    expects: passes
binding_refs: [C-010, OQ-AR-4, OQ-CN-2]
```

### Implementation steps (from unit body)
1. Add `spring-boot-starter-web` (compile scope).
2. Add `spring-boot-starter-security` (compile scope).
3. Add `spring-boot-starter-data-jpa` (compile scope).
4. Add `org.postgresql:postgresql` (runtime scope).
5. Add jjwt triplet: `io.jsonwebtoken:jjwt-api` (compile) + `io.jsonwebtoken:jjwt-impl` (runtime) + `io.jsonwebtoken:jjwt-jackson` (runtime), version **0.12.x** (resolve exact patch via `mvn dependency:tree`; pick the latest 0.12.x that resolves — 0.12.6 is known-good and cached).
6. Do NOT add Lombok (deferred per OQ-CN-2). Use SLF4J manual `LoggerFactory` if logging is needed (not needed in this pom-only unit).
7. Keep the existing `spring-snapshots` repository block intact.
8. Run `mvn -q dependency:resolve` then `mvn -q compile` to confirm classpath resolves.

### Acceptance criteria
- `mvn dependency:resolve` exits 0.
- `mvn compile` exits 0 (existing `CoresystembackendApplication` still compiles).
- `pom.xml` declares: spring-boot-starter-web, spring-boot-starter-security, spring-boot-starter-data-jpa, postgresql (runtime), jjwt-api + jjwt-impl + jjwt-jackson 0.12.x.
- No Lombok added.

## Hard rules (v1 bullet grammar — pre-flight snapshot taken)

- DO NOT add new package.json dependencies (Citation: constitution §D-003 DO_NOT_ADD_DEPS)
  - NOTE: this is a no-op guard for pom.xml (no package.json exists). The real dependency discipline: only add the deps listed above — nothing extra (D-003).

## Anti-patterns (MUST honor)

- **D-003:** Do NOT add dependencies beyond the feature's declared set. Lombok explicitly deferred (OQ-CN-2).
- **jjwt version:** Do NOT pin a non-0.12.x version. The legacy newmojf `signWith(SignatureAlgorithm, String)` API is removed in 0.11+; U-005 requires the 0.12.x API (`Jwts.builder().signWith(Key)`, `Jwts.parserBuilder().verifyWith(key)`). Pin `0.12.6` (or latest 0.12.x that resolves).

## Constitution clauses in force (this unit)

- §D-003: No new dep without review. The deps in this unit ARE reviewed (C-010 binding + OQ-AR-4/OQ-CN-2 resolved). Add ONLY the declared set.
- §A (coding standards), §B (security — not directly applicable to pom-only, but the jjwt 0.12.x constraint supports B-002/B-003 downstream).

## Target file whitelist

**ONLY `pom.xml` may be modified.** No other file. Do not create Java files. Do not touch `application.yaml` (that's U-008).

## Provenance trailer (MANDATORY in every modified file)

Every modified file MUST end with a provenance trailer comment. For `pom.xml` (XML), add as an XML comment near the end (before `</project>`):

```xml
<!-- SDD-PROVENANCE: U-001 | vault: .mega-sdd/vaults/jwt-login | unit: U-001 | deps: web,security,jpa,postgresql,jjwt-0.12.x -->
```

The post-flight scan verifies this trailer is present — missing → halt `provenance_missing`.

## Execution protocol

1. Read current `pom.xml`.
2. Modify it: add the 6 dependencies (web, security, jpa, postgresql runtime, jjwt-api compile, jjwt-impl runtime, jjwt-jackson runtime) with version `0.12.6` for the jjwt triplet. Place new `<dependency>` entries in the `<dependencies>` block after the existing two. Keep everything else (parent, properties, build, repositories) intact.
3. Add the provenance trailer XML comment before `</project>`.
4. Run `mvn -q dependency:resolve` — must exit 0.
5. Run `mvn -q compile` — must exit 0.
6. If either fails: diagnose, fix within the whitelist (pom.xml only), retry. Max 3 retries.
7. If both pass: `git add pom.xml` then commit with message:
   ```
   feat(U-001): add web/security/jpa/postgresql/jjwt deps

   Foundation unit for JWT-login feature. Adds spring-boot-starter-web,
   -security, -data-jpa, postgresql (runtime), jjwt 0.12.6 triplet.
   No Lombok (deferred OQ-CN-2). ./mvnw broken — built with system mvn.

   SDD-PROVENANCE: U-001 vault=.mega-sdd/vaults/jwt-login
   ```
   (Do NOT use --no-verify. Do NOT push.)
8. Report: files modified, commit SHA, sha256 of committed pom.xml, acceptance test results (verbatim last lines), confidence 0.0–1.0, any uncertain decisions.

## Halt conditions (emit blocker, stop, do NOT commit)

- `dependency:resolve` or `compile` fails after 3 retries → halt `test_fail` with the error output.
- A Hard rule is violated → halt `hard_rule_violated`.
- A dependency won't resolve from the snapshot repo → halt, report which dep + the repo error.

## Self-assessment (report this)

After commit, report a `bolt_self_report` block:
- confidence: <0.0–1.0>
- certain_decisions: [list]
- uncertain_decisions: [list]
- retry_history: [attempts]

Begin now. You have all context you need. Do NOT ask clarifying questions — the spec is complete.
