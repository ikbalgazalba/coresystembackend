---
type: prose
doc_id: 02-architecture
vault_version: "1.1"
aliases: [Architecture]
tags: ["vault/jwt-login", "doc/architecture"]
---

# 02 — Architecture

> **TL;DR**: API-only backend — satu endpoint login + komponen auth (JwtUtils, SecurityConfig 7.x, LDAP_UCS service, Users entity/repo). · IT Architect / Tech Lead / BE Dev · baca saat review struktur & integrasi.

## System overview

coresystembackend adalah backend REST (PROJECT_SHAPE=`api-only`). Klien memanggil `POST /api/auth/dologin` via HTTPS; controller mendelegasikan autentikasi ke `LdapUcsService` (replikasi `LDAP_UCS_Utils.authLDAPNew`); jika sukses, `JwtUtils` menerbitkan token dari uname, `UserRepository` lookup data user, lalu controller merangkai `JwtResponse`. `SecurityConfig` (Spring Security 7.x `SecurityFilterChain` bean) mengizinkan `/api/auth/**` tanpa autentikasi, menonaktifkan CSRF (stateless API), dan menyediakan `PasswordEncoder`. Persistensi via Spring Data JPA ke PostgreSQL.

```
External clients ──── HTTPS ────► AuthUserController (POST /api/auth/dologin)
                                         │
                          ┌──────────────┼──────────────────────┐
                          ▼              ▼                      ▼
                  LdapUcsService     JwtUtils              UserRepository
                  (authLDAPNew)   (generateTokenUname)    (findByUname → Users)
                          │              │                      │
                  LDAP UCS (ext)    config secret/exp          PostgreSQL
```

---

## By component layer

### Backend

| Component | Purpose | Source |
|-----------|---------|--------|
| `AuthUserController` | `@RestController` `/api/auth` — endpoint `POST /dologin`, orchestrasi LDAP→JWT→lookup→response | `AuthUserController.java:38-150` |
| `LdapUcsService` | Replikasi `LDAP_UCS_Utils.authLDAPNew(uname,pass)` → `{responseCode, responseDescription}`. ⚠️ Impl newmojf punya dependensi tersembunyi: AES/ECB encrypt password, HMAC-SHA512 signature, depend `DB_Connection` (cred/URL), dan `disableSslVerification()` trust-all (lihat OQ-AR-6 + constitution B-006). | `AuthUserController.java:92-101`; `LDAP_UCS_Utils.java:42-101,202,267-319` |
| `JwtUtils` | `generateTokenFromUname(uname)` + validate/parse; secret+exp dari config | `AuthUserController.java:128`; `application-test.properties:24-25` |
| `UserRepository` | `JpaRepository<Users, Long>`, `findByUname(String)` lookup user terdaftar | `AuthUserController.java:130`; `mojf_users_Model.java` |
| `Users` (entity) | Entity tabel `users`, adaptasi `mojf_users_Model` ke `jakarta.persistence` | `mojf_users_Model.java:12-50` |
| `SecurityConfig` | `SecurityFilterChain` bean 7.x — permitAll `/api/auth/**`, stateless, CSRF off, `PasswordEncoder` | `codebase-map.md §7`; spring.md pack |
| DTO `LoginRequest`/`JwtResponse`/`MessageResponse` | Request/response boundary objects | `AuthUserController.java:90,133,141,148` |

**Intent**: Arsitektur berlapis standar Spring — `@RestController` → service/repository; controller hanya parsing + delegasi; business logic di service. Auth stateless via JWT.

**Starterkit binding** (`spring` pack):
- Controller di `src/main/java/<pkg>/controller/AuthUserController.java`
- Service di `src/main/java/<pkg>/service/LdapUcsService.java` (+ `JwtUtils` di `security/` atau `util/`)
- Repository di `src/main/java/<pkg>/repository/UserRepository.java`
- Entity di `src/main/java/<pkg>/entity/Users.java` (pack standard; bukan `model/` — lihat OQ-DM-1)
- DTO di `src/main/java/<pkg>/dto/`
- `SecurityConfig` di `src/main/java/<pkg>/config/SecurityConfig.java`
- Constructor injection (bukan field `@Autowired` — newmojf pakai field `@Autowired` line 40-47, HARUS diadaptasi)
- Citations: `framework-conventions/spring.md §File location standards`, `§Idioms`, `codebase-map.md §7`

### Integrations

| External system | Direction | Protocol | Purpose | Source |
|-----------------|-----------|----------|---------|--------|
| LDAP UCS | sync outbound | LDAP (replikasi `LDAP_UCS_Utils`) | Autentikasi kredensial uname/pass | `AuthUserController.java:92-93` |
| PostgreSQL | sync outbound | JDBC | Baca user terdaftar di tabel `users` | `application-test.properties:33-42` |

**Auth & integration patterns**: klien→API via JWT Bearer; API→LDAP UCS via utilitas LDAP; API→PostgreSQL via Spring Data JPA. Detail endpoint LDAP UCS → OQ-AR-1, OQ-FL-1.

---

## API contracts

### Public API — `/api/auth`

| Endpoint | Method | Purpose | Auth | Errors | Source |
|----------|--------|---------|------|--------|--------|
| `/api/auth/dologin` | POST | Autentikasi LDAP UCS → terbitkan JWT → return user identity | public (permitAll) | `400` kredensial salah / LDAP gagal; `401` auth failed (exception path) | `AuthUserController.java:89-150` |

#### `POST /api/auth/dologin`

**Purpose**: Login → dapatkan JWT + identitas user.
**Auth**: public (`SecurityConfig` permitAll `/api/auth/**`).

**Request / Input**:
```json
{
  "uname": "String — username",
  "pass": "String — password (plaintext ke LDAP UCS, sesuai pola newmojf)"
}
```

**Response / Output (success — 200)** — field names verbatim dari DTO newmojf `JwtResponse` (Jackson serialize bare field names; no `@JsonProperty`, no global `PropertyNamingStrategy`):
```json
{
  "token": "String — JWT",
  "type": "Bearer ",
  "id": 123,
  "uname": "String — username",
  "mitKode": "String — kode mitra user (nilai dari entity.kodeMitra)",
  "urole": "String — role; format ROLE_<urole> (nilai diisi Urole = \"ROLE_\"+usr.getUrole())"
}
```

> ⚠️ **Field-name conflation note (advisor ADV-001)**: `mitKode`/`urole` adalah nama field DTO response (`JwtResponse.java:5-10`); `kodeMitra`/`urole` (entity field `kodeMitra`) adalah sumber nilai di Java. Jackson men-serialisasi nama field DTO (`mitKode`, `urole`), bukan nama field entity. `type="Bearer "` field selalu ada. Apakah coresystembackend replikasi verbatim nama DTO newmojf (`mitKode`/`urole`) atau rename ke `kodeMitra`/`role` (adaptasi) → **OQ-AR-7**.

**Errors / Failure modes**:
- `400` / `MessageResponse` — LDAP `responseCode` bukan "00"/"01" (kredensial salah) → `{message: responseDescription}`. `(AuthUserController.java:144)`
- `400` / `MessageResponse` — `respLdap == null` (gagal konek LDAP UCS) → `{message: "Failed to connect to LDAP service"}`. `(AuthUserController.java:148)`
- `401` / `MessageResponse` — exception saat generate JWT/lookup → `{message: "Authentication failed"}`. `(AuthUserController.java:141)`

**Source**: `AuthUserController.java:89-150`; `seed-PRD §E`.

---

## Sources

- `source/seed-PRD.md` §A, §E, §G
- `AuthUserController.java:38-150` (newmojf referensi)
- `application-test.properties:24-25,33-42` (newmojf referensi)
- `codebase-map.md §7 Framework`
- `framework-conventions/spring.md §File location standards`, `§Idioms`, `§Security idioms`

## Out of Scope

- Endpoint terproteksi (resource server JWT validation) — di luar scope login issuance; validasi token di SecurityConfig disiapkan tapi endpoint terproteksi = future. `(seed-PRD §F)`
- Refresh-token endpoint `(seed-PRD §F)`

## Open Questions

- [ ] **OQ-AR-1** [P1] [business] [conf: high]: coresystembackend belum punya infra LDAP UCS aktif — pakai LDAP UCS newmojf yang sama, atau perlu mock/fallback auth DB untuk dev? — resolve: stakeholder/infra team
- [ ] **OQ-AR-2** [P1] [business] [conf: high]: target database — sambung ke PostgreSQL newmojf existing (10.95.1.43:5432/newmojf) atau DB baru khusus coresystembackend? Tabel users sudah berisi data user terdaftar? — resolve: infra team
- [ ] **OQ-AR-3** [P2] [tech / recommend] [conf: medium]: `jwtSecret` dikelola bagaimana — hardcode seperti referensi (`jwtMojfSecretkey`) atau externalize via env var? — resolve: lihat Auto-Classification Review
  - recommendation: Externalize via env var / placeholder di `application.yaml` (`coresystem.app.jwtSecret=${JWT_SECRET:dev-only-secret}`); jangan hardcode literal.
  - rationale: pack security idiom melarang secret di source; referensi newmojf hardcode `jwtMojfSecretkey` (security smell). Spring Boot placeholder + env var konsisten dengan pack dan aman untuk snapshot builds.
  - scan_citations: `application-test.properties:24` (referensi hardcode — anti-pattern), `framework-conventions/spring.md §Security idioms §Secrets / config`
  - fallback_if_wrong: jika env var tidak feasible di env deploy, fallback ke Spring Cloud Config/Vault.
- [ ] **OQ-AR-4** [P2] [tech / recommend] [conf: high]: lib JWT yang dipakai — newmojf VERIFIED memakai `io.jsonwebtoken:jjwt` (legacy API). Versi 0.12.x kompatibel Boot 4.x? — resolve: lihat Auto-Classification Review
  - recommendation: `io.jsonwebtoken:jjwt-api` + `jjwt-impl` + `jjwt-jackson` versi `0.12.x` (runtime scope untuk impl/jackson).
  - rationale: newmojf VERIFIED memakai jjwt (`JwtUtils.java:12-17` import `io.jsonwebtoken.*`). TAPI impl-nya pakai **API legacy** `signWith(SignatureAlgorithm.HS512, jwtSecret)` (`JwtUtils.java:38,48`) dan `Jwts.parser().setSigningKey(...)` (`:53,58`) — API ini **dihapus di jjwt 0.11+**. Replikasi ke 0.12.x WAJIB rewrite: `Jwts.builder().signWith(Key)` (Keys.hmacShaKeyFor) + `Jwts.parserBuilder().verifyWith(key).build()`. Versi 0.12.x mendukung Jakarta + Java 21.
  - scan_citations: `JwtUtils.java:12-17,38,48,53,58` (VERIFIED jjwt + legacy API), `codebase-map.md §7` (Boot 4.x / Java 21)
  - fallback_if_wrong: jika jjwt 0.12.x tidak kompatibel snapshot Boot 4.x, fallback ke `nimbus-jose-jwt` atau Spring Security OAuth2 resource server JWT.
- [ ] **OQ-AR-5** [P2] [tech / recommend] [conf: medium]: strategi CORS — replikasi verbatim `@CrossOrigin(origins = "*")` (wildcard, referensi newmojf `AuthUserController.java:34`) atau konfigurasi CORS via `SecurityFilterChain` `http.cors(Customizer)` dengan allowed-origins list? — resolve: lihat Auto-Classification Review
  - recommendation: konfigurasi CORS via `SecurityFilterChain` `http.cors(Customizer.withDefaults())` + `CorsConfigurationSource` bean dengan allowed-origins eksplisit (bukan wildcard `*`), kecuali dev local.
  - rationale: wildcard `@CrossOrigin(origins="*")` pada API stateless JWT adalah security smell (pack `spring.md §Security idioms`); configurasi terpusat via SecurityConfig lebih可控 + sesuai pack. newmojf pakai wildcard (legacy smell) — adaptasi ke CORS config eksplisit.
  - scan_citations: `AuthUserController.java:34` (`@CrossOrigin(origins = "*")` referensi), `framework-conventions/spring.md §Security idioms`
  - fallback_if_wrong: jika client coresystembackend membutuhkan wildcard (mis. banyak origin dinamis tak terduga), pertimbangkan allowlist pattern atau `*` hanya di profile dev.
- [ ] **OQ-AR-6** [P1] [business] [conf: high]: prasyarat integrasi LDAP UCS — newmojf `LDAP_UCS_Utils.authLDAPNew` butuh provisioning: AES key (`aesKey`), client credentials (`clientId`/`clientSecret`), `partnerId`/`channelId`, `urlToken`/`urlVerifyPassword` (dari `DB_Connection` bean). Semua ini harus tersedia untuk coresystembackend; dari mana? — resolve: infra/security team
- [ ] **OQ-AR-7** [P2] [business] [conf: medium]: nama field DTO response — replikasi verbatim newmojf (`token, type, id, uname, mitKode, urole`) atau adaptasi rename (`kodeMitra`/`role`) demi konsistensi penamaan pack/produk? — resolve: PO/architect (kontrak API ke client coresystembackend)
