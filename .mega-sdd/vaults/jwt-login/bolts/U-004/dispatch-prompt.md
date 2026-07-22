# Dispatch Prompt — U-004 (DTOs)

## Mission
Implement ONE mega-sdd unit: **U-004 — Create DTOs (LoginRequest, JwtResponse, MessageResponse)**. Write 3 Java DTO files, compile, commit atomically. Report DONE or HALT.

## Environment facts
- **Working dir:** `/home/ikbalgazalba/AI/Project/coresystembackend`
- **`./mvnw` is BROKEN** — use system `mvn` (Maven 3.9.16, Java 21, on PATH).
- Git branch: `main`. No pre-commit hooks. gpgsign off.
- Base package: `com.coresystem.coresystembackend`. New package: `com.coresystem.coresystembackend.dto`.
- Deps already on classpath (U-001 committed): spring-boot-starter-web (Jackson), spring-boot-starter.

## Unit spec (authoritative — `/home/ikbalgazalba/AI/Project/coresystembackend/.mega-sdd/vaults/jwt-login/units/U-004.md`)
- task_type: create, depends_on: [] (none), module: M-api
- target_files: `src/main/java/com/coresystem/coresystembackend/dto/LoginRequest.java` (create), `.../dto/JwtResponse.java` (create), `.../dto/MessageResponse.java` (create)
- acceptance_test: `mvn -q compile` (use `mvn`, not `./mvnw`) → passes
- binding_refs: [C-008, OQ-AR-7]

## Reference source (REPLICATE VERBATIM — only change package + optional formatting)

The newmojf reference DTOs are at:
- `/home/ikbalgazalba/AI/Project/newmojf/src/main/java/com/bankmega/newmojf/model/mojf/request/LoginRequest.java`
- `/home/ikbalgazalba/AI/Project/newmojf/src/main/java/com/bankmega/newmojf/model/mojf/response/JwtResponse.java`
- `/home/ikbalgazalba/AI/Project/newmojf/src/main/java/com/bankmega/newmojf/model/mojf/response/MessageResponse.java`

READ them. Replicate field names, types, constructor signatures, getters/setters VERBATIM. The ONLY change is:
- package declaration → `package com.coresystem.coresystembackend.dto;`
- (JwtResponse newmojf has `super();` in ctor — you may keep or drop it; either is fine)

### Exact spec requirements (from unit body)
1. `LoginRequest`: fields `String uname`, `String pass` + getters/setters (no Lombok). newmojf has NO constructor — keep it that way (no-arg default; setters populate). Match reference exactly.
2. `JwtResponse` — replicate EXACTLY:
   - fields: `String token`, `String type = "Bearer "`, `Long id`, `String uname`, `String mitKode`, `String urole`
   - constructor: `(String token, Long id, String uname, String mitKode, String urole)`
   - getters/setters for all 6 fields
   - **NO `@JsonProperty`** (newmojf has none — bare-field Jackson serialization)
   - field names `mitKode` and `urole` are VERBATIM (do NOT rename to kodeMitra/role — OQ-AR-7 default is verbatim replication)
3. `MessageResponse`: field `String message` + constructor `(String message)` + getter/setter. Match reference.

## Hard rules (v1 bullet — preflight snapshot taken)
- FILE_PRESENCE: all 3 dto files MUST exist after bolt.
- (No naming rule cited for dto/ specifically, but use PascalCase class names per §A-001.)

## Anti-patterns (MUST honor)
- **OQ-AR-7 / ADV-001:** DO NOT rename `mitKode`→`kodeMitra` or `urole`→`role`. Verbatim replication is the default.
- DO NOT omit `type = "Bearer "` field (part of newmojf response — ADV-001).
- DO NOT add `@JsonProperty` (no JSON contract change decided).
- No Lombok (OQ-CN-2 deferred) — manual getters/setters.

## Constitution clauses in force
- §C-002: Entity not exposed from REST — DTOs are the boundary (these DTOs satisfy this; just don't import entity).
- §A-001: PascalCase classes, camelCase fields.
- §A-002: `dto/` package (correct — you're using it).

## Target file whitelist
ONLY these 3 files may be created:
- `src/main/java/com/coresystem/coresystembackend/dto/LoginRequest.java`
- `src/main/java/com/coresystem/coresystembackend/dto/JwtResponse.java`
- `src/main/java/com/coresystem/coresystembackend/dto/MessageResponse.java`
No other files. No test files (acceptance is compile-only for this unit — no unit test required; the unit's acceptance_test is `mvn compile`).

## Provenance trailer (MANDATORY in each file)
Add as a block comment at the END of each .java file (after the closing brace is fine, or as a leading comment — pick leading `//` line comment after package for consistency):
```java
// SDD-PROVENANCE: U-004 | vault: .mega-sdd/vaults/jwt-login | replicated from newmojf model/mojf/{request,response}
```
Place it as a comment. Post-flight verifies presence in all 3 files.

## Execution protocol
1. Read the 3 newmojf reference files (paths above).
2. Create the 3 dto files with package `com.coresystem.coresystembackend.dto`, verbatim fields/ctor/getters/setters, + provenance trailer.
3. Run `mvn -q compile` — must exit 0.
4. If compile fails: fix within whitelist, retry. Max 3.
5. `git add` the 3 files, commit:
   ```
   feat(U-004): add LoginRequest, JwtResponse, MessageResponse DTOs

   API-boundary DTOs replicated verbatim from newmojf (field names mitKode/urole
   preserved per OQ-AR-7 default). No @JsonProperty, no Lombok.

   SDD-PROVENANCE: U-004 vault=.mega-sdd/vaults/jwt-login
   ```
   (no --no-verify, no push)
6. Report: commit SHA, sha256 of each of the 3 committed files, compile result, confidence 0.0–1.0.

## Halt conditions
- compile fails after 3 retries → halt `test_fail` with error.
- Hard rule violated → halt `hard_rule_violated`.

## Self-assessment (report)
bolt_self_report: confidence, certain_decisions, uncertain_decisions, retry_history.

Begin now. Spec is complete — no clarifying questions.
