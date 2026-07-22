# Dispatch Prompt — U-002 (Users entity)

## Mission
Implement ONE mega-sdd unit: **U-002 — Create Users entity (jakarta.persistence)** replicating `mojf_users_Model` field set. Write `Users.java`, compile, commit. Report DONE or HALT.

## Environment facts
- **Working dir:** `/home/ikbalgazalba/AI/Project/coresystembackend`
- **`./mvnw` is BROKEN** — use system `mvn`.
- Git branch: `main`. No hooks. gpgsign off.
- Base package: `com.coresystem.coresystembackend`. Entity package: `com.coresystem.coresystembackend.entity`.
- Deps on classpath (U-001): spring-boot-starter-data-jpa (jakarta.persistence).

## Unit spec (authoritative — `.mega-sdd/vaults/jwt-login/units/U-002.md`)
- task_type: create, depends_on: [U-001] (DONE — classpath ready), module: M-persistence
- target_files: `src/main/java/com/coresystem/coresystembackend/entity/Users.java` (create)
- acceptance_test: `mvn -q compile` → passes
- binding_refs: [C-003, C-004, OQ-DM-1, OQ-DM-2, OQ-DM-3]

## Reference source (replicate field-by-field)
`/home/ikbalgazalba/AI/Project/newmojf/src/main/java/com/bankmega/newmojf/model/mojf/mojf_users_Model.java`

READ it. The reference has 18 fields with `@Column(name=...)` snake_case mappings. Replicate ALL of them. The reference uses `javax.persistence` + `java.sql.Date` — you MUST change these (see below).

## Required adaptations (from spec — NON-NEGOTIABLE)

1. **Namespace:** `jakarta.persistence.*` (NOT `javax.persistence.*` — constitution §B-001, won't compile on Boot 4.x). Imports: `jakarta.persistence.Entity`, `jakarta.persistence.Table`, `jakarta.persistence.Column`, `jakarta.persistence.Id`, `jakarta.persistence.GeneratedValue`, `jakarta.persistence.GenerationType`.

2. **Table name:** `@Table(name = "users")` (NOT `mojf_users`) — per OQ-DM-1 recommendation (pack standard `users`). The unit spec step 2 confirms `users` is the default.

3. **Date type:** Use `java.util.Date` (the spec says `java.util.Date`; newmojf used `java.sql.Date` — switch to `java.util.Date` which is the JPA-portable choice). Applies to: `createdDate`, `lastModified`, `lastLogin`. Import `java.util.Date`.

4. **Class name:** `Users` (PascalCase per §A-001; newmojf's `mojf_users_Model` is a legacy naming violation — do NOT copy the class name).

5. **18 fields** (exact, with @Column snake_case names from reference):
   - `id` (Long): `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "uid_")`
   - `uname` (String): `@Column(name = "uname")`
   - `pass` (String): `@Column(name = "pass")`
   - `namaLengkap` (String): `@Column(name = "nama_lengkap")`
   - `kodeUnitKerja` (String): `@Column(name = "kode_unit_kerja")`
   - `cakupan` (Long): `@Column(name = "cakupan")`
   - `urole` (Long): `@Column(name = "urole")`
   - `createdDate` (Date): `@Column(name = "created_date")`
   - `createdBy` (String): `@Column(name = "created_by")`
   - `lastModified` (Date): `@Column(name = "last_modified")`
   - `modifiedBy` (String): `@Column(name = "modified_by")`
   - `lastLogin` (Date): `@Column(name = "last_login")`
   - `kodeMitra` (String): `@Column(name = "kode_mitra")`
   - `active` (Long): `@Column(name = "active")`
   - `expiredDays` (Long): `@Column(name = "expired_days")`
   - `kodeKelasUser` (String): `@Column(name = "kode_kelas_user")`
   - `statusUser` (Long): `@Column(name = "status_user")`
   (That's 17 — verify against reference; the reference also has these. Count matches 18 incl id. Replicate exactly as the reference lists them.)

6. **Getters/setters** for ALL fields (no Lombok — OQ-CN-2). Match reference style.

## Hard rules (v1 bullet — preflight taken)
- FILE_PRESENCE: `src/main/java/com/coresystem/coresystembackend/entity/Users.java` MUST exist after bolt.
- NAMING_RULE: `entity/*.java` MUST follow PascalCase → `Users` (✓).

## Anti-patterns (MUST honor)
- **§B-001:** NO `javax.persistence` — `jakarta.persistence` only.
- **§C-003:** NO business logic / JPA callbacks in entity — POJO only (no `@PrePersist`, no update logic).
- **OQ-DM-2:** DO NOT add `last_login` update logic — v1 is read-only replication (the field EXISTS but no write method/callback). Just a getter/setter like the reference.
- **OQ-DM-3:** ddl-auto=none (no DDL scripts — out of scope; just the entity).
- No Lombok.

## Constitution clauses in force
- §B-001 (jakarta), §A-001 (PascalCase), §A-002 (entity/ package), §C-002 (entity not in REST — satisfied by being a pure entity), §C-003 (no logic in entity).

## Target file whitelist
ONLY `src/main/java/com/coresystem/coresystembackend/entity/Users.java`. No other file. No test (acceptance is compile-only).

## Provenance trailer (MANDATORY)
Add a line comment after the package declaration:
```java
// SDD-PROVENANCE: U-002 | vault: .mega-sdd/vaults/jwt-login | replicated from newmojf mojf_users_Model (jakarta.persistence, table=users)
```

## Execution protocol
1. Read the reference `mojf_users_Model.java`.
2. Create `Users.java` with jakarta.persistence, @Table(name="users"), java.util.Date, all 18 fields + getters/setters + provenance trailer.
3. `mvn -q compile` — must exit 0.
4. If fails: fix within whitelist, retry. Max 3.
5. Commit:
   ```
   feat(U-002): add Users JPA entity (jakarta.persistence)

   Replicates mojf_users_Model 18-field set adapted to jakarta.persistence
   (Boot 4.x), @Table(name=users), java.util.Date. POJO only, no Lombok.

   SDD-PROVENANCE: U-002 vault=.mega-sdd/vaults/jwt-login
   ```
   (no --no-verify, no push)
6. Report: commit SHA, Users.java sha256, compile result, confidence.

## Halt conditions
- compile fails after 3 retries → halt `test_fail`.
- javax.persistence present → halt `hard_rule_violated` (§B-001).

## Self-assessment
bolt_self_report: confidence, certain/uncertain decisions, retry_history.

Begin now. Spec complete — no questions.
