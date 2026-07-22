---
type: prose
doc_id: 05-decisions
vault_version: "1.1"
aliases: [Decisions, ADR]
tags: ["vault/jwt-login", "doc/decisions"]
---

# 05 — Decisions

> **TL;DR**: ADR untuk pilihan kunci: autentikasi LDAP UCS, JWT dari uname, migrasi jakarta, Security 7.x. · Architect / PM · baca saat review trade-off.

### D-001: Autentikasi via LDAP UCS (replikasi newmojf)

coresystembackend butuh mekanisme autentikasi; newmojf memakai LDAP UCS (`LDAP_UCS_Utils.authLDAPNew`). **Decision**: replikasi pola LDAP UCS — autentikasi uname/pass via `LdapUcsService.authLDAPNew`, sukses jika `responseCode` "00"/"01". **Consequences**: konsisten dengan newmojf, tapi butuh infra LDAP UCS aktif (OQ-AR-1, OQ-FL-1 blocking runtime); fallback DB-password untuk dev belum diputuskan. **Source**: `AuthUserController.java:92-101`; seed-PRD §E; Q&A orchestrate-flow (user pilih "LDAP (replikasi newmojf)").

### D-002: JWT diterbitkan dari uname (bukan Authentication object)

newmojf `/dologin` aktif memakai `jwtUtils.generateJwtTokenUname(uname)` langsung (bukan `generateJwtToken(authentication)` yang dikomentari). **Decision**: terbitkan JWT dari `uname` string, lalu lookup user via `findByUname` — replikasi verbatim pola newmojf `/dologin`. **Consequences**: lebih sederhana (tanpa `AuthenticationManager.authenticate` flow), tapi token hanya bawa uname (bukan authorities Spring Security) — role di-resolve dari lookup DB saat login, bukan dari token claims. **Source**: `AuthUserController.java:104-138` (flow `generateJwtTokenUname` aktif; `AuthenticationManager` dikomentari).

### D-003: Migrasi namespace javax → jakarta (Boot 4.x)

Referensi newmojf `mojf_users_Model.java` memakai `javax.persistence.*` (Jakarta EE 8). coresystembackend Spring Boot 4.1.1-SNAPSHOT = Jakarta EE 10. **Decision**: semua import JPA diadaptasi ke `jakarta.persistence.*`. **Consequences**: entity tidak compile jika pakai `javax` di Boot 4.x; ini adaptasi wajib, bukan pilihan. **Source**: `mojf_users_Model.java:5-10`; `codebase-map.md §7 version_caveat`.

### D-004: Spring Security 7.x SecurityFilterChain bean (bukan WebSecurityConfigurerAdapter)

newmojf (legacy) **memakai** `WebSecurityConfigurerAdapter` (VERIFIED — `security_Config.java:29` import + `:54` `extends WebSecurityConfigurerAdapter`; dihapus di Spring 6). **Decision**: `SecurityConfig` sebagai `@Configuration` dengan `@Bean SecurityFilterChain` — permitAll `/api/auth/**`, stateless `SessionCreationPolicy.STATELESS`, CSRF disabled (stateless API), `@Bean PasswordEncoder`. **Consequences**: konsisten dengan pack spring.md (idiom + forbidden pattern); `WebSecurityConfigurerAdapter` dilarang. **Source**: `security_Config.java:29,54` (newmojf referensi); `codebase-map.md §7`; `framework-conventions/spring.md §Idioms`, `§Forbidden patterns`.

### D-005: Constructor injection (bukan field @Autowired)

newmojf `AuthUserController.java:40-47` memakai field `@Autowired`. **Decision**: semua komponen baru pakai constructor injection (`final` fields + constructor; `@Autowired` opsional/omit pada single-ctor). **Consequences**: immutable, testable tanpa DI container, sesuai pack; field `@Autowired` adalah forbidden pattern (pack Hard Rule). **Source**: `AuthUserController.java:40-47`; `framework-conventions/spring.md §Idioms`, `§Hard Rules emitted`.

### D-006: DTO di boundary API (entity tidak di-expose)

**Decision**: controller menerima `LoginRequest` dan mengembalikan `JwtResponse`/`MessageResponse` — entity `Users` tidak pernah dikembalikan langsung. **Consequences**: bentuk API terpisah dari skema DB, menghindari leak JPA internals; sesuai pack Hard Rule. **Source**: `AuthUserController.java:90,133,141,148`; `framework-conventions/spring.md §Hard Rules emitted`.

---

## Sources

- `source/seed-PRD.md` §E, §G
- `AuthUserController.java:40-47,89-150` (newmojf referensi)
- `mojf_users_Model.java:5-10` (newmojf referensi)
- `codebase-map.md §7`; `framework-conventions/spring.md §Idioms`, `§Forbidden patterns`, `§Hard Rules emitted`

## Out of Scope

- Keputusan target DB & infra LDAP — terbuka via OQ-AR-1/OQ-AR-2 (business blocking). `(seed-PRD §I)`
- Keputusan secret management — terbuka via OQ-AR-3. `(seed-PRD §I)`

## Open Questions

- [ ] **OQ-DC-1** [P3] [tech / recommend] [conf: medium]: apakah D-002 (JWT hanya bawa uname, role dari DB lookup) cukup, atau token sebaiknya bawa role claim agar resource server tidak perlu lookup DB? — resolve: lihat Auto-Classification Review
  - recommendation: v1: pertahankan subject=uname saja (replikasi newmojf); resource-server future resolve role via DB lookup `findByUname` saat validasi token.
  - rationale: replikasi verbatim pola newmojf (`generateJwtTokenUname` set hanya subject); menambah role claim = scope creep + masalah sinkronisasi (role berubah → token stale). Trade-off: lookup DB per request terproteksi (latency) vs token lebih besar (stateless). v1 tidak punya endpoint terproteksi jadi lookup belum relevan.
  - scan_citations: `AuthUserController.java:128-138` (generateJwtTokenUname + lookup), `JwtUtils.java:42-50` (subject=uname saja)
  - fallback_if_wrong: jika future resource-server OQ menunjukkan cost lookup tidak terima, embed role claim di token (refresh saat role berubah).
