# Project Constitution — api-platform

**Status**: Active
**Version**: 1.1.1 (extends jwt-login constitution v1.0.0 with containerization clauses §G; v1.1.1 adds G-008 CORS externalization per binding-advisor APB-003)
**Last reviewed**: 2026-07-24
**Sign-off**: Tech Lead / Security (when relevant) — pending

> This constitution extends the project-wide rules established in `../jwt-login/constitution.md` (§A–§F). §A–§F are inherited verbatim and re-asserted below (same project, same codebase). §G adds containerization-specific clauses for this epic.

## §A. Coding standards (Non-negotiable) — inherited

- A-001: Class PascalCase + suffix layer (`Controller`/`Service`/`Repository`/`Config`); method/field camelCase. `framework-conventions/spring.md §Naming standards`
- A-002: Paket layer standar: `controller/`, `service/`, `repository/`, `entity/`, `dto/`, `config/`. `spring.md §File location standards`
- A-003: Test files di `src/test/java/<pkg>/` mencerminkan struktur main. `spring.md §Testing conventions`
- A-004: Tidak ada `System.out.println()` untuk logging — pakai SLF4J `LoggerFactory.getLogger()`. `spring.md §Forbidden patterns`

## §B. Security baselines — inherited + extended

- B-001: `jakarta.persistence.*` wajib (Boot 4.x); `javax.persistence.*` dilarang. `codebase-map.md §7 version_caveat`
- B-002: `SecurityFilterChain` bean (Spring Security 7.x); `WebSecurityConfigurerAdapter` dilarang. `spring.md §Forbidden patterns`
- B-003: `jwtSecret` externalize via env var/placeholder; jangan hardcode literal di `application.yaml`/source. `spring.md §Security idioms §Secrets / config`
- B-004: CSRF disabled hanya untuk stateless token API, dengan comment penjelasan. `spring.md §Security idioms §CSRF`
- B-005: Password dikirim ke LDAP UCS; `PasswordEncoder` bean tetap disediakan. `spring.md §Security idioms §Password hashing`
- B-006: JANGAN replikasi `disableSslVerification()` trust-all. Trust store bankmega di-MOUNT sebagai volume di kontainer, JANGAN diganti trust manager kosong. `jwt-login constitution §B-006`; ADR-AP-3; HR-1
- B-007: Error response ke client JANGAN echo raw exception/internal message. `jwt-login constitution §B-007`
- B-008 (NEW): `/actuator/health/**` permitAll diperbolehkan untuk container probe; endpoint actuator LAIN tidak di-expose kecuali OQ-AP-8 resolve. `ADR-AP-4`; HR-3
- B-009 (NEW): Swagger UI / OpenAPI spec path permitAll di v1; prod gating (disable UI di prod) deferred ke OQ-AP-5. `ADR-AP-5`. Field `LoginRequest.pass` ter-dokumentasi — acceptable untuk API internal UAT, flagged.

## §C. Architecture invariants — inherited

- C-001: Controller tidak memanggil controller lain; pakai Service. `spring.md §Idioms`
- C-002: Entity tidak di-expose langsung dari REST controller — pakai DTO. `spring.md §Hard Rules`
- C-003: Business logic di `@Service`, bukan controller/repository. `spring.md §Hard Rules`
- C-004: Constructor injection (`final` fields); field `@Autowired` dilarang. `spring.md §Hard Rules`
- C-005: Multi-step writes di service `@Transactional`. `spring.md §Hard Rules`

## §D. Anti-patterns (from legacy / reference app) — inherited + extended

- D-001: JANGANG salin verbatim konvensi legacy newmojf. `conventions.md`
- D-002: JANGAN hardcode `jwtSecret` / secret apapun. `jwt-login constitution §D-002`; HR-1
- D-003: JANGAN tambah dependency baru (Lombok, lib JWT, dll) tanpa review/konfirmasi (OQ). `spring.md §Forbidden patterns`. springdoc-openapi + actuator adalah satu-satunya deps baru epic ini (HR-7).
- D-004 (NEW): JANGAN bake secret / trust store / `.env` ke dalam Docker image. Image hanya berisi JRE + JAR. `HR-1`; ADR-AP-3
- D-005 (NEW): JANGAN host-install Maven untuk build image — pakai `./mvnw` (committed wrapper) di builder stage. `HR-2`; ADR-AP-2

## §E. Performance constraints — inherited

- E-001: Target response time / RPS — `(unspecified)` (jwt-login OQ-OV-1). Tidak ada klaim kuantitatif tanpa sumber.
- E-002 (NEW): Image size target / JVM resource limits — `(unspecified)` (OQ-AP-6). Tidak ada klaim kuantitatif tanpa sumber.

## §F. Compliance — inherited

- F-001: Regime compliance (PDP-Indonesia / lainnya) untuk kredensial & data user — `(unspecified)` (jwt-login OQ-CN-1).
- F-002: Audit `last_login` — `(unspecified)` (jwt-login OQ-DM-2).

## §G. Containerization clauses (NEW — this epic)

- G-001: Dockerfile multi-stage: builder `eclipse-temurin:21-jdk` + `./mvnw -B clean package`; runtime `eclipse-temurin:21-jre` + JAR only. `HR-2`; ADR-AP-2
- G-002: `.dockerignore` WAJIB exclude `target/`, `.git/`, `.mega-sdd/`, `.env`, `*.log`, `.idea/`, `.vscode/`, asset non-code. `.env.example` boleh masuk (template, bukan secret). `HR-1`; 02-architecture
- G-003: docker-compose `app` service pakai `env_file: .env` (gitignored) + trust store `volume` mount. Tidak ada `environment:` literal secret. `HR-1`; ADR-AP-3
- G-004: Trust store path di kontainer via env (`${TRUSTSTORE_PATH}` / `JAVA_OPTS`), mount target = OQ-AP-3 (human decision). `ADR-AP-3`
- G-005: Compose healthcheck pakai `/actuator/health`. `HR-3`; Flow 4
- G-006: Spring profiles `dev` / `prod`; compose default `prod`. Profile YAML override ONLY profile-specific keys; base `application.yaml` fail-fast behavior dijaga. `HR-4`; ADR-AP-4
- G-007: Endpoint existing (`POST /api/auth/dologin`) + authz rules jwt-login TIDAK boleh dilemahkan; hanya ADDITIVE permitAll untuk doc + health paths. `HR-5`
- G-008 (NEW): CORS allowed origins WAJIB di-externalize via `${CORS_ALLOWED_ORIGINS}` env placeholder (profile-driven). `SecurityConfig.java:89` hardcode `localhost:3000/8080` HARUS di-override di prod profile; container/prod browser client akan kena CORS rejection kalau tidak. `HR-8`; binding-advisor APB-003; OQ-AP-9
