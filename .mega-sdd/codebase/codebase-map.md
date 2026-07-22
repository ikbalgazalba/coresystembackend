---
generated_by: mega-sdd:scan-codebase
generated_at: 2026-07-22T06:35:00Z
repo_root: /home/ikbalgazalba/AI/Project/coresystembackend
scan_depth: 8
scan_includes: ["src/**", "pom.xml", "mvnw", "mvnw.cmd", "CLAUDE.md", "HELP.md"]
scan_excludes: ["target/**", ".git/**", ".idea/**", ".mega-sdd/**", "*.iml", ".mvn/**", "node_modules/**", "build/**"]
languages_detected: ["java", "yaml", "markdown"]
package_managers: ["maven"]
test_frameworks: ["junit5"]
engine: tree-sitter
precision_tier: ast
tree_sitter_version: "0.26.10"
grammars_used: ["java"]
last_scanned_commit: b27e8cbf5e2e8218ef42ca86be373479ea5c2d1e
scan_mode: incremental-unchanged
change_signal_note: "Healing (2026-07-22): prior Mode D sync (b27e8cb) completed scan+bind+reconcile but ECONNRESET struck before stamp was bumped and SYNC-REPORT was written. No source-code or manifest changes between 941e5b4 and HEAD (0 paths). Stamp bumped to HEAD; §1–§7 content byte-identical (carried forward)."
---

# Codebase Map

> Scanned repo is a Spring Initializr skeleton (greenfield-reachable scan). §2–§6 are
> intentionally near-empty: there is no business code yet. §7 Framework is the critical
> output — it drives pack-aware vault generation downstream. All empty sections are marked
> "None detected" per anti-hallucination rail; nothing is invented.

## 1. Top-level structure

```
coresystembackend/
├── .git/                          (git repo; HEAD = b27e8cb mega-sdd: resolve P1 OQs + Mode D sync)
├── .mvn/                          (Maven wrapper support)
├── CLAUDE.md                      (project instructions)
├── HELP.md                        (Spring Initializr help)
├── mvnw                           (Maven wrapper, unix)
├── mvnw.cmd                       (Maven wrapper, windows)
├── pom.xml                        (Maven manifest — Spring Boot 4.1.1-SNAPSHOT, Java 21)
└── src/
    ├── main/
    │   ├── java/com/coresystem/coresystembackend/
    │   │   └── CoresystembackendApplication.java   (@SpringBootApplication entry point)
    │   └── resources/
    │       └── application.yaml                   (only spring.application.name set)
    └── test/
        └── java/com/coresystem/coresystembackend/
            └── CoresystembackendApplicationTests.java  (@SpringBootTest contextLoads smoke test)
```

## 2. Public interfaces

| File | Type | Symbol | Signature | Last_Scanned_Sha256 |
|---|---|---|---|---|
| `src/main/java/com/coresystem/coresystembackend/CoresystembackendApplication.java:8` | class | `CoresystembackendApplication` | `@SpringBootApplication class CoresystembackendApplication` | 4c86b293e7626a9b3101242e812606385640c751f0e4fae33eded5500adef4cb |
| `src/main/java/com/coresystem/coresystembackend/CoresystembackendApplication.java:10` | method | `CoresystembackendApplication.main` | `public static void main(String[] args)` → `SpringApplication.run(CoresystembackendApplication.class, args)` | 4c86b293e7626a9b3101242e812606385640c751f0e4fae33eded5500adef4cb |

> No other public types (no controllers, services, repositories, entities, DTOs, config, or
> security classes) exist yet. The `com.coresystem.coresystembackend` package contains only the
> application bootstrap class.

## 3. Routes / Endpoints

None detected.

> `spring-boot-starter` (not `-web`) is the only starter — there is no embedded servlet
> container / HTTP layer. No `@RestController`, `@Controller`, or `@*Mapping` annotations
> present. Adding endpoints requires adding `spring-boot-starter-web` (expected next step).

## 4. Data models / Schemas

None detected.

> No `@Entity`, `@Table`, `@Id`, or JPA repository interfaces exist. JPA is not yet on the
> classpath (`spring-boot-starter-data-jpa` not declared). No database driver declared.

## 5. Naming conventions

- **Case style:** PascalCase classes, camelCase methods/fields (inferred from the single
  `CoresystembackendApplication` bootstrap class; insufficient sample for a strong convention
  claim — the Spring pack's standards in §7 will be authoritative for new code).
- **File suffix:** `Application.java` for entry point; test files end `Tests.java` (note:
  Spring pack convention is `Test` suffix; existing smoke test uses `Tests` — pre-existing
  Initializr default, will not be a conflict target).
- **Test files:** `*Tests.java` (JUnit 5 Jupiter via `spring-boot-starter-test`).
- **Package:** `com.coresystem.coresystembackend` (lowercase dot-separated).

## 6. Pattern signatures

- **Auth pattern:** none (no security dependency, no `SecurityFilterChain`, no jwt/session
  middleware present yet — `spring-boot-starter-security` not declared).
- **Error handling:** none (no `@ControllerAdvice` / `@RestControllerAdvice` / global handler).
- **State:** none (stateless skeleton; no session/store).
- **View/component pattern:** none (REST/MVC layer absent — API-only intent inferred from
  backend project name; no `templates/` or `static/` resource dirs).
  - View dir: none
  - View naming: none
  - Exemplar selection: none

## 7. Framework

```yaml
framework:
  name: spring
  version: "4.1.1-SNAPSHOT (Boot 4.x / Spring Framework 7.x / Jakarta EE 10)"
  confidence: high
  pack_path: references/framework-conventions/spring.md
  detection_source: "pom.xml declares parent spring-boot-starter-parent 4.1.1-SNAPSHOT + dependency spring-boot-starter; spring-snapshots repository enabled (repo.spring.io/snapshot)"
  pack_version_note: "Convention pack targets Spring Boot 3.x. Project is Boot 4.1.1-SNAPSHOT. Idioms carry over (SecurityFilterChain bean, constructor injection, DTOs at boundaries, jakarta.* namespace). Pack's WebSecurityConfigurerAdapter removal rule is already satisfied (none exists). NOTE: Boot 4.x mandates jakarta.persistence (NOT javax.persistence) and Spring Security 7.x — the pack's 3.x assumptions hold for structure but namespace/security-API versioning must be honored by downstream units."
```

### Starterkit facts (for pack-aware vault generation)

- **Build tool:** Maven (wrapper committed — `mvnw`/`mvnw.cmd`; no local Maven install needed).
- **Java:** 21 (`<java.version>21</java.version>`).
- **Parent POM:** `spring-boot-starter-parent` `4.1.1-SNAPSHOT` (resolves from
  `repo.spring.io/snapshot` — builds require network; behavior can shift between snapshots).
- **Declared dependencies:** `spring-boot-starter` (core only, NO web container),
  `spring-boot-starter-test` (test scope, JUnit 5 + Mockito + MockMvc).
- **Missing starters the feature will need (to be added as units):**
  `spring-boot-starter-web` (HTTP), `spring-boot-starter-security` (auth), 
  `spring-boot-starter-data-jpa` (persistence), a JDBC driver (`postgresql`), and a JWT
  library (`io.jsonwebtoken:jjwt-api/-impl/-jackson` triplet).
- **Config:** `application.yaml` (YAML, not `.properties`) — only `spring.application.name:
  coresystembackend` set. Spring profiles convention available (`application-{profile}.yml`).
- **Entry point:** `com.coresystem.coresystembackend.CoresystembackendApplication` (standard
  `@SpringBootApplication` + `SpringApplication.run`).
- **Base package:** `com.coresystem.coresystembackend` — new packages nest under it:
  `controller`, `service`, `repository`, `entity` (pack prefers `entity/`; newmojf reference
  uses `model/` — pack standard `entity/` is the binding target unless an OQ overrides), 
  `dto`, `config`, `security`, `exception`.
```
