<!-- BIND: constitution_hash=6b2ce926... | clauses-§B-005/§C-001-now-machine-validated(ADV-BIND-002) -->

# Project Constitution

**Status**: Active
**Version**: 1.0
**Last reviewed**: 2026-07-22
**Sign-off**: Tech Lead / Security (when relevant) — pending

---

## §A. Coding standards (Non-negotiable)

- A-001: Class PascalCase + suffix layer (`Controller`/`Service`/`Repository`); method/field camelCase. `framework-conventions/spring.md §Naming standards`
- A-002: Paket layer standar: `controller/`, `service/`, `repository/`, `entity/`, `dto/`, `config/`. `spring.md §File location standards`
- A-003: Test files di `src/test/java/<pkg>/` mencerminkan struktur main. `spring.md §Testing conventions`
- A-004: Tidak ada `System.out.println()` untuk logging — pakai SLF4J `LoggerFactory.getLogger()`. `spring.md §Forbidden patterns`

## §B. Security baselines

- B-001: `jakarta.persistence.*` wajib (Boot 4.x); `javax.persistence.*` dilarang. `codebase-map.md §7 version_caveat`
- B-002: `SecurityFilterChain` bean (Spring Security 7.x); `WebSecurityConfigurerAdapter` dilarang. `spring.md §Forbidden patterns`
- B-003: `jwtSecret` externalize via env var/placeholder; jangan hardcode literal di `application.yaml`/source. `spring.md §Security idioms §Secrets / config`; OQ-AR-3
- B-004: CSRF disabled hanya untuk stateless token API, dengan comment penjelasan. `spring.md §Security idioms §CSRF`
- B-005: Password dikirim ke LDAP UCS (pola newmojf); `PasswordEncoder` bean tetap disediakan. `spring.md §Security idioms §Password hashing`
- B-006: JANGAN replikasi `disableSslVerification()` trust-all (X509TrustManager kosong + HostnameVerifier allHostsValid) dari `LDAP_UCS_Utils.java:42-61` — security anti-pattern grade constitution. Jika endpoint LDAP UCS butuh SSL khusus, raise OQ security-exception eksplisit; jangan disable verifikasi global. `LDAP_UCS_Utils.java:42-61`; advisor ADV-006
- B-007: Error response ke client JANGAN echo raw exception/internal message verbatim (risiko information leakage dari `LDAP_UCS_Utils.responseDescription=e.getMessage()`). Map ke generic message; detail internal hanya di log server-side. `LDAP_UCS_Utils.java:257-258`; `AuthUserController.java:144`; advisor ADV-005

## §C. Architecture invariants

- C-001: Controller tidak memanggil controller lain; pakai Service. `spring.md §Idioms`
- C-002: Entity tidak di-expose langsung dari REST controller — pakai DTO. `spring.md §Hard Rules`
- C-003: Business logic di `@Service`, bukan controller/repository. `spring.md §Hard Rules`
- C-004: Constructor injection (`final` fields); field `@Autowired` dilarang. `spring.md §Hard Rules`
- C-005: Multi-step writes di service `@Transactional`. `spring.md §Hard Rules`

## §D. Anti-patterns (from legacy / reference app)

- D-001: JANGAN salin verbatim konvensi legacy newmojf (`javax.persistence`, field `@Autowired`, paket `model/`, config `.properties`) — adaptasi ke pack Spring Boot 4.x. `conventions.md`; seed-PRD §G
- D-002: JANGAN hardcode `jwtSecret` seperti referensi newmojf (`jwtMojfSecretkey`) — security smell. `application-test.properties:24`; spring.md §Security idioms
- D-003: JANGAN tambah dependency baru (Lombok, lib JWT, dll) tanpa review/konfirmasi (lihat OQ-AR-4, OQ-CN-2). `spring.md §Forbidden patterns`

## §E. Performance constraints

- E-001: Target response time / RPS — `(unspecified)` (OQ-OV-1, P3). Tidak ada klaim kuantitatif tanpa sumber.

## §F. Compliance

- F-001: Regime compliance (PDP-Indonesia / lainnya) untuk kredensial & data user — `(unspecified)` (OQ-CN-1, P3). Tidak ada klaim compliance tanpa konfirmasi PO.
- F-002: Audit `last_login` — `(unspecified)` (OQ-DM-2, P3); v1 replikasi verbatim newmojf (read-only lookup, no write).
