---
type: prose
doc_id: 06-constraints
vault_version: "1.1"
aliases: [Constraints]
tags: ["vault/jwt-login", "doc/constraints"]
---<!-- BIND: oq=OQ-CN-1,CN-2 | state=NEW | claim=C-010(deps),C-011(config),C-012(packages) | hard-rules=14(pack8+constitution) | anti-patterns=5 -->



# 06 — Constraints

> **TL;DR**: Technical + business + NFR + pack Hard Rules. · Architect / BE Dev / Security · baca saat implementasi & review.

## Technical constraints

- **Stack lock-in**: Spring Boot 4.1.1-SNAPSHOT (`spring-boot-starter-parent`), Java 21, Maven (wrapper `mvnw`). `(codebase-map.md §7)`
- **Snapshot repository**: parent/BOM resolve dari `repo.spring.io/snapshot` — butuh akses jaringan; behavior bisa berubah antar build. `(codebase-map.md §7)`
- **Namespace wajib**: `jakarta.persistence.*` (bukan `javax.*`) — Boot 4.x / Jakarta EE 10. `(codebase-map.md §7 version_caveat)`
- **Security API**: Spring Security 7.x `SecurityFilterChain` bean; `WebSecurityConfigurerAdapter` dilarang (dihapus Spring 6). `(spring.md §Forbidden patterns)`
- **Injection**: constructor injection; field `@Autowired` dilarang. `(spring.md §Hard Rules)`
- **API boundary**: DTO di controller; entity tidak di-expose langsung. `(spring.md §Hard Rules)`
- **Dependencies yang harus ditambah** (skeleton hanya punya `spring-boot-starter` + `-test`):
  - `spring-boot-starter-web` (HTTP/REST)
  - `spring-boot-starter-security` (auth)
  - `spring-boot-starter-data-jpa` (persistensi)
  - `org.postgresql:postgresql` (driver)
  - `io.jsonwebtoken:jjwt-api` + `jjwt-impl` + `jjwt-jackson` (JWT — lihat OQ-AR-4)
  - (opsional) `org.projectlombok:lombok` (untuk `@Slf4j` seperti newmojf — `(unspecified)` apakah dipakai)
- **Config format**: YAML (`application.yaml`), bukan `.properties` (referensi newmojf pakai `.properties` — HARUS diadaptasi ke YAML). `(codebase-map.md §7)`

## Business constraints

- **Replikasi pola newmojf**: implementasi mengikuti pola `AuthUserController` `/dologin` (LDAP UCS → JWT dari uname → lookup user → JwtResponse). `(seed-PRD §A)`
- **newmojf adalah referensi, bukan target**: konvensi legacy-nya (`javax`, field `@Autowired`, `model/`, `.properties`) diadaptasi ke pack, tidak disalin verbatim. `(seed-PRD §G; conventions.md)`
- Timeline / budget — `(unspecified)`.

## NFR (performance, scalability, security, availability, observability)

- **Security**:
  - `jwtSecret` externalize via env var/placeholder, jangan hardcode literal (referensi newmojf hardcode `jwtMojfSecretkey` = smell). `(spring.md §Security idioms; OQ-AR-3)`
  - Password dikirim ke LDAP UCS (pola newmojf); `PasswordEncoder` bean disediakan di SecurityConfig meski autentikasi utama via LDAP. `(spring.md §Security idioms)`
  - CSRF disabled hanya karena stateless token API (beri comment penjelasan). `(spring.md §Security idioms §CSRF)`
  - CORS: jangan replikasi `@CrossOrigin(origins="*")` wildcard newmojf (`AuthUserController.java:34`) verbatim — konfigurasi via SecurityConfig + allowed-origins eksplisit (lihat OQ-AR-5, advisor ADV-004). `(spring.md §Security idioms)`
  - SSL verification: JANGAN replikasi `disableSslVerification()` trust-all dari `LDAP_UCS_Utils.java:42-61` (constitution B-006, advisor ADV-006).
  - Information leakage: error response ke client JANGAN echo raw `responseDescription`/`e.getMessage()` dari LDAP_UCS (`LDAP_UCS_Utils.java:257-258`); map ke generic message, detail ke log (constitution B-007, OQ-FL-3, advisor ADV-005).
  - Endpoint `/api/auth/dologin` permitAll (login public); endpoint lain default `authenticated()` (future). `(spring.md §Authz mapping)`
- **Observability**: logging via SLF4J (`@Slf4j`/`LoggerFactory`), jangan `System.out.println`. `(spring.md §Forbidden patterns)`
- **Performance/availability**: target kuantitatif (RPS, latency) — `(unspecified)` (OQ-OV-1).

## Pack Hard Rules (inherited from `framework-conventions/spring.md`)

Dipropagasikan ke unit sebagai `id: constitution-spring-*` / Hard Rules di generate-units:

- Controller classes MUST end with `Controller` suffix.
- Service classes MUST end with `Service` suffix.
- Repository interfaces MUST end with `Repository` suffix and extend a Spring Data repository.
- Entity classes MUST carry `@Entity` and `@Id`.
- Field injection (`@Autowired` on a field) MUST NOT be used — constructor injection.
- Entity objects MUST NOT be returned from REST controllers — use DTOs.
- Business logic MUST reside in `@Service` classes, not controllers/repositories.
- Multi-step writes MUST be `@Transactional` at service layer.

## Starterkit binding (tech-agnostic intent → pack realization)

**Intent**: ikuti konvensi Spring Boot standar untuk struktur, naming, idiom, security.
**Starterkit binding** (`spring`):
- Struktur paket: `controller/`, `service/`, `repository/`, `entity/`, `dto/`, `config/`.
- Naming: PascalCase class + suffix sesuai layer; camelCase method/field; snake_case kolom/tabel.
- Idiom: constructor injection, layered, DTO boundary, JPA repository, SecurityFilterChain, `@Transactional` service, `@RestControllerAdvice` global exception.
- Citations: `framework-conventions/spring.md §File location standards`, `§Naming standards`, `§Idioms`, `§Hard Rules emitted`, `§Security idioms`, `codebase-map.md §7`

---

## Sources

- `source/seed-PRD.md` §G, §I
- `codebase-map.md §7`
- `framework-conventions/spring.md` (pack — §File location standards, §Naming, §Idioms, §Hard Rules, §Forbidden patterns, §Security idioms)
- `application-test.properties:24,3` (newmojf referensi — secret + ddl-auto)

## Out of Scope

- Hardening lanjutan (MFA, rate-limiting, IP allowlist) — `(unspecified)` (OQ-FL-2). `(seed-PRD §F)`
- Observability stack (metrics/tracing) — di luar v1. `(seed-PRD §F)`

## Open Questions

- [ ] **OQ-CN-1** [P3] [business] [conf: low]: regime compliance yang berlaku (PDP-Indonesia / lainnya) untuk penanganan kredensial login & data user? — resolve: PO/compliance team
- [ ] **OQ-CN-2** [P3] [tech / recommend] [conf: medium]: pakai Lombok (`@Slf4j` seperti newmojf) atau SLF4J manual `LoggerFactory`? — resolve: lihat Auto-Classification Review
  - recommendation: SLF4J manual `LoggerFactory.getLogger(...)` (atau `@Slf4j` hanya jika Lombok sudah di dependency).
  - rationale: skeleton belum deklaras Lombok; menambah dependency baru perlu review (anti-pattern "new dep tanpa review"). SLF4J manual zero-dependency dan memenuhi pack forbidden pattern (no `System.out`).
  - scan_citations: `AuthUserController.java:32` (`@Slf4j` Lombok referensi), `codebase-map.md §2` (skeleton tanpa Lombok)
  - fallback_if_wrong: jika tim sudah standar pakai Lombok, tambahkan `org.projectlombok:lombok` (provided scope) + `@Slf4j`.
