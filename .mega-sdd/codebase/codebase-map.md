---
generated_by: mega-sdd:scan-codebase
generated_at: 2026-07-24T03:47:51Z
repo_root: ./
scan_depth: 8
scan_includes: ["src/main/java/**", "src/test/java/**", "src/main/resources/**", "pom.xml"]
scan_excludes: ["node_modules/**", "vendor/**", "dist/**", "build/**", "target/**", ".next/**", ".gradle/**", "__pycache__/**", ".venv/**", "coverage/**", ".git/**", ".idea/**", ".vscode/**", ".mega-sdd/**", "*.log"]
languages_detected: ["java"]
package_managers: ["maven"]
test_frameworks: ["junit (via spring-boot-starter-test)", "assertj (transitive via starter-test)"]
engine: tree-sitter
precision_tier: ast
tree_sitter_version: "0.26.10"
grammars_used: ["java"]
last_scanned_commit: 0682bb58c2755f8a6ef86566127a9147233d311e
---

# Codebase Map

> Spring Boot 4.1.1-SNAPSHOT backend (Java 21, Maven). Single-module JWT-auth service with LDAP-UCS password verification, PostgreSQL (existing `newmojf` schema), and a stateless security stack. The repo is a **brownfield, feature-bearing app** (not the bare skeleton CLAUDE.md describes — that doc is stale relative to the `jwt-login` vault work already landed in source via `SDD-PROVENANCE` comments).

## 1. Top-level structure

```
.
├── pom.xml                          # Maven; spring-boot-starter-parent 4.1.1-SNAPSHOT, Java 21
├── mvnw / mvnw.cmd                  # Maven wrapper (committed)
├── run-app.sh                       # Bash launcher: loads .env, fail-fast env check, JVM trust-store flags
├── .env.example                     # env-var contract (14 vars), values blank; .env gitignored
├── .gitignore                       # ignores .env
├── CLAUDE.md                        # (STALE: claims no -web / no HTTP layer; pom actually has starter-web + security + jpa)
├── preview.webp                     # image asset (non-code)
└── src
    ├── main
    │   ├── java/com/coresystem/coresystembackend
    │   │   ├── CoresystembackendApplication.java   # @SpringBootApplication entry point
    │   │   ├── config/SecurityConfig.java          # @Configuration: SecurityFilterChain, PasswordEncoder, CORS, RestClient.Builder
    │   │   ├── controller/AuthUserController.java # @RestController @RequestMapping("/api/auth")
    │   │   ├── dto/{JwtResponse,LoginRequest,MessageResponse}.java
    │   │   ├── entity/Users.java                  # @Entity @Table(name="mojf_users")
    │   │   ├── repository/UserRepository.java     # JpaRepository<Users,Long>
    │   │   ├── security/JwtUtils.java             # @Component; jjwt 0.12.x HS512 token gen/validate
    │   │   └── service/LdapUcsService.java        # @Service; RestClient-based LDAP UCS verify
    │   └── resources/application.yaml             # env-placeholder config (§D-002, no hardcoded secrets)
    └── test/java/com/coresystem/coresystembackend
        ├── CoresystembackendApplicationTests.java           # @SpringBootTest contextLoads
        ├── AuthLoginIntegrationTest.java                    # integration test
        ├── config/SecurityConfigTest.java
        ├── controller/AuthUserControllerTest.java
        ├── security/JwtUtilsTest.java
        └── service/LdapUcsServiceTest.java
```

Package layout (conventional Spring layering): `config`, `controller`, `dto`, `entity`, `repository`, `security`, `service`. Single bounded context (`coresystembackend`); no multi-module Maven.

## 2. Public interfaces

| File | Type | Symbol | Signature | Last_Scanned_Sha256 |
|---|---|---|---|---|
| `.../CoresystembackendApplication.java:7` | class | CoresystembackendApplication | `public static void main(String[] args)` (@SpringBootApplication) | 4c86b293e762 |
| `.../config/SecurityConfig.java:42` | class (@Configuration) | SecurityConfig | `@Bean SecurityFilterChain filterChain(HttpSecurity)`, `@Bean PasswordEncoder passwordEncoder()`, `@Bean CorsConfigurationSource corsConfigurationSource()`, `@Bean RestClient.Builder restClientBuilder()` | bedc33471921 |
| `.../controller/AuthUserController.java:40` | class (@RestController) | AuthUserController | ctor(`LdapUcsService, JwtUtils, UserRepository`); `ResponseEntity<?> login(@RequestBody LoginRequest)` | c3276c198694 |
| `.../security/JwtUtils.java:17` | class (@Component) | JwtUtils | ctor(`@Value jwtSecret, @Value jwtExpirationMs`); `String generateTokenFromUname(String)`, `String getUserNameFromJwt(String)`, `boolean validateJwtToken(String)` | c45476bcd592 |
| `.../service/LdapUcsService.java:47` | class (@Service) | LdapUcsService | ctor(`RestClient, ...LDAP config`); `LdapAuthResult authLDAPNew(String uname, String pass)`; nested `record LdapAuthResult(String responseCode, String responseDescription)` (line 96) | 725cb8a6d48e |
| `.../entity/Users.java:15` | class (@Entity) | Users | JPA entity, 17 `@Column` fields + getters/setters | a50b57de64da |
| `.../repository/UserRepository.java:10` | interface | UserRepository | `extends JpaRepository<Users, Long>`; derived `findByUname(String)` | 52aaa259a918 |
| `.../dto/JwtResponse.java:4` | class (DTO) | JwtResponse | fields: token, type, id, uname, urole, mitKode (+ getters/setters) | cd414d4f91df |
| `.../dto/LoginRequest.java:4` | class (DTO) | LoginRequest | fields: uname, pass (+ getters/setters) | 7e8a6bc05763 |
| `.../dto/MessageResponse.java:4` | class (DTO) | MessageResponse | field: message (+ getter/setter) | cc9b67ecbd77 |

## 3. Routes / Endpoints

| Method | Path | Handler |
|---|---|---|
| POST | `/api/auth/dologin` | `AuthUserController.login(LoginRequest): ResponseEntity<?>` (`AuthUserController.java:54-55`) |

> Single endpoint. Class-level `@RequestMapping("/api/auth")` (line 39) + method `@PostMapping("/dologin")` (line 54). No other controllers. No actuator/health endpoint currently exposed (actuator not on classpath).

## 4. Data models / Schemas

| Entity | File | Fields |
|---|---|---|
| `Users` (@Entity, @Table `mojf_users`) | `entity/Users.java:13-15` | 17 `@Column` fields: `uid_` (Long, @Id, IDENTITY), `uname`, `pass`, `nama_lengkap`, `kode_unit_kerja`, `cakupan` (Long), `urole` (Long), `created_date` (Date), `created_by`, `last_modified` (Date), `modified_by`, `last_login` (Date), `kode_mitra`, `active`, `expired_days`, `kode_kelas_user`, `status_user` (lines 16-50; all `@Column`-mapped to snake_case DB columns) |

DTOs (non-persistent): `LoginRequest{uname, pass}`, `JwtResponse{token, type, id, uname, urole, mitKode}`, `MessageResponse{message}`. JPA `ddl-auto: none` (application.yaml) — schema is pre-existing in the `newmojf` PostgreSQL DB; Hibernate does not manage DDL.

## 5. Naming conventions

- **Case style (symbols):** PascalCase classes (`AuthUserController`, `JwtUtils`), camelCase methods/fields (`generateTokenFromUname`, `jwtSecret`).
- **Case style (DB columns):** snake_case (`nama_lengkap`, `kode_unit_kerja`) via explicit `@Column(name=...)`.
- **File naming:** one public class per file, filename = `<ClassName>.java`.
- **Package layering:** `config` / `controller` / `dto` / `entity` / `repository` / `security` / `service` (conventional Spring tiers).
- **Test files:** suffix `Test.java` (unit, e.g. `JwtUtilsTest`), `Tests.java` (smoke, `CoresystembackendApplicationTests`), `IntegrationTest.java` (integration, `AuthLoginIntegrationTest`). Test packages mirror main packages.
- **SDD provenance convention:** source files carry `// SDD-PROVENANCE: U-NNN | vault: .mega-sdd/vaults/jwt-login | <summary>` trailing comments tying each artifact to its implementing unit.

## 6. Pattern signatures

- **Auth pattern:** JWT (stateless). `SecurityConfig.filterChain` sets `SessionCreationPolicy.STATELESS`, CSRF disabled (no session to forge), `/api/auth/**` `permitAll`, `anyRequest().authenticated()`. Token issued by `JwtUtils.generateTokenFromUname` (HS512, jjwt 0.12.x `signWith(Key)`). Password verification delegated to LDAP UCS (`LdapUcsService.authLDAPNew`), NOT local DB passwords. **No `OncePerRequestFilter`/JWT request filter present** — `/dologin` issues a token but no filter currently validates the bearer on subsequent requests (authz rule is `anyRequest().authenticated()` with no authentication source wired → potential gap, surfaces as OQ during binding).
- **Error handling:** try-catch in controller (`AuthUserController.login`, returns `ResponseEntity` with `HttpStatus` codes). No `@ControllerAdvice` / `@ExceptionHandler`. Generic error message convention per constitution §B-007 (referenced in `LdapUcsService` provenance).
- **State:** none (stateless REST API; no session, no cookies, no in-memory state container).
- **External HTTP:** Spring 7.x `RestClient` (declarative builder pattern via `RestClient.Builder` bean) — NOT `RestTemplate`/`WebClient`. Spring Boot 4.x removed the auto-configured `RestClient.Builder`, so it is provided explicitly in `SecurityConfig`.
- **Secrets:** fully externalized to env vars (constitution §D-002). `application.yaml` uses `${VAR}` placeholders with NO defaults (fail-fast on missing). `.env` (gitignored) + `.env.example` (contract) + `run-app.sh` (auto-load + fail-fast). Trust store is host-path-specific (`/home/ikbalgazalba/.ssl-truststores/bankmega-truststore.p12`) — **non-portable; relevant to containerization**.
- **View/component pattern:** none (API-only stack, no presentation layer). `exemplar_selection: n/a`.

## 7. Framework

```yaml
framework:
  name: spring-boot
  version: "4.1.1-SNAPSHOT"
  confidence: high
  pack_path: references/framework-conventions/spring.md      # registry: spring | full | ready — pack IS installed
  detection_source: "pom.xml: spring-boot-starter-parent 4.1.1-SNAPSHOT + starters (web, security, data-jpa, test); java.version=21"
  starters:
    - spring-boot-starter
    - spring-boot-starter-web
    - spring-boot-starter-security
    - spring-boot-starter-data-jpa
    - spring-boot-starter-test (scope=test)
  key_libraries:
    - postgresql (runtime, JDBC driver)
    - jjwt-api / jjwt-impl / jjwt-jackson 0.12.6 (JWT)
  build_tool: maven (mvnw wrapper committed)
  java_version: "21"
  snapshot_repo: "repo.spring.io/snapshot (spring-snapshots) — builds require network; behavior may shift between snapshot resolutions"
  spring_security_version_note: "Spring Security 7.x (boot 4.x) — component-based DSL (SecurityFilterChain @Bean), no WebSecurityConfigurerAdapter"
```

**Observations relevant to incoming vault (Swagger/OpenAPI + Docker):**
- OpenAPI/Swagger dependency (`springdoc-openapi-*`) is **absent** from `pom.xml` — must be added.
- No `actuator` on classpath — a health/readiness endpoint would need the starter for container probes.
- No Docker artifacts (`Dockerfile`, `docker-compose.yml`, `.dockerignore`) exist.
- `application.yaml` env-placeholder pattern is container-friendly (12-factor) but the host-specific trust-store path in `run-app.sh` is NOT portable into a container — likely an OQ for the Docker unit.
- `.env` is gitignored and host-local; container env strategy must reconcile env-var injection (compose `environment:`/`env_file:` vs baked-in).
