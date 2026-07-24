---
type: prose
doc_id: 03-data-model
vault_version: "1.1"
aliases: [Data Model, DBML, Schema]
tags: ["vault/jwt-login", "doc/data-model"]
---

# 03 — Data Model

> **TL;DR**: Satu entity `Users` (class) yang memetakan tabel `mojf_users` di DB newmojf existing — adaptasi `mojf_users_Model` ke `jakarta.persistence` (Boot 4.x). · BE Dev / DBA · baca saat design entity/repo.

## Entities (DBML)

```dbml
Table mojf_users {
  id bigint [pk, increment, note: 'uid_ — @Id @GeneratedValue(IDENTITY)']
  uname varchar [note: 'username login — unique lookup key']
  pass varchar [note: 'password (LDAP auth; kolom disimpan sesuai pola newmojf)']
  nama_lengkap varchar [note: 'nama lengkap user']
  kode_unit_kerja varchar [note: 'kode unit kerja']
  cakupan bigint [note: 'cakupan user']
  urole bigint [note: 'role numeric → di-maps ke "ROLE_<urole>" di JwtResponse']
  created_date date [note: 'audit created']
  created_by varchar [note: 'audit created by']
  last_modified date [note: 'audit modified']
  modified_by varchar [note: 'audit modified by']
  last_login date [note: 'timestamp login terakhir (opsional diupdate)']
  kode_mitra varchar [note: 'kode mitra — dikembalikan di JwtResponse']
  active bigint [note: 'status aktif user']
  expired_days bigint [note: 'masa berlaku user (hari)']
  kode_kelas_user varchar [note: 'kelas user']
  status_user bigint [note: 'status user']
}

Note: 'Adaptasi mojf_users_Model.java ke jakarta.persistence (BUKAN javax). Nama tabel = mojf_users (DB newmojf existing — OQ-DM-1 RESOLVED v1.3: pakai skema existing, BUKAN pack-standard users, agar lookup findByUname ke tabel existing berhasil). Field @Column name snake_case sesuai kolom DB newmojf. Class entity tetap `Users` (pack PascalCase).'
```

> **Purpose**: Menyimpan data user terdaftar untuk lookup saat login (`findByUname`). Field `kode_mitra` dan `urole` dipakai di JwtResponse. `(mojf_users_Model.java:12-50; AuthUserController.java:130-138)`

## Constraints

- **Uniqueness**: `mojf_users.uname` harus unik — dipakai sebagai lookup key login (`findByUname`). `(inferred from mojf_users_Model.java uname; AuthUserController.java:130)`
- **PK**: `id` bigint auto-increment (`@GeneratedValue IDENTITY`). `(mojf_users_Model.java:15-18)`
- **Column mapping**: field Java camelCase → kolom DB snake_case eksplisit via `@Column(name=...)` (mis. `namaLengkap` → `nama_lengkap`, `kodeMitra` → `kode_mitra`). `(mojf_users_Model.java:19-50)`
- **Audit**: `created_date/created_by`, `last_modified/modified_by`, `last_login` — kolom audit ada di referensi; update `last_login` saat login sukses = `(unspecified)` (opsional, lihat OQ-DM-2).

## Schema realization (pack)

**Intent**: Entity JPA merepresentasikan tabel user untuk lookup login.
**Starterkit binding** (`spring`):
- `@Entity` + `@Table` + `@Id` + `@GeneratedValue(strategy = IDENTITY)` + `@Column` mapping. `(spring.md §ERD additions)`
- Paket `entity/` (pack), bukan `model/` (referensi newmojf). `(spring.md §File location standards)`
- Namespace `jakarta.persistence.*` (Boot 4.x) — referensi newmojf `javax.persistence.*` HARUS diganti.
- Nama tabel = `mojf_users` (DB newmojf existing, OQ-DM-1 RESOLVED v1.3); class entity = `Users` (pack PascalCase, nama class bebas dari nama tabel via `@Table(name="mojf_users")`).
- Citations: `framework-conventions/spring.md §ERD additions`, `§Naming standards`, `codebase-map.md §7 version_caveat`

---

## Sources

- `mojf_users_Model.java:12-50` (newmojf referensi — struktur field)
- `AuthUserController.java:130-138` (lookup + field yang dipakai di response)
- `source/seed-PRD.md` §D, §G
- `codebase-map.md §7`; `framework-conventions/spring.md §ERD additions`

## Out of Scope

- Migrasi data user dari DB newmojf → coresystembackend `(seed-PRD §F)`
- Skema tabel lain (roles/permissions terpisah) — newmojf pakai `urole` numeric single-column, replikasi pola itu. `(inferred; mojf_users_Model.java:29-30)`
- DDL/migration scripts (Hibernate `ddl-auto` atau Flyway) — `(unspecified)` (lihat OQ-DM-3)

## Open Questions

- [x] **OQ-DM-1** [P2] [tech / recommend] [conf: medium]: nama tabel entity — pakai `users` (pack standard snake_case plural) atau replikasi `mojf_users` (referensi newmojf)? → **Resolved v1.3** (2026-07-23, implementation-verified, commit 810fd54 — P1 drift fix): VERIFIED + RESOLVED. Cabang OQ-AR-2 (pakai DB newmojf existing) → entity `Users` pakai `@Table(name="mojf_users")` (`Users.java:14`). Awalnya drift (table=`users`, lookup gagal); dideteksi & diperbaiki di commit 810fd54 (OQ-DM-1 P1 drift detect). Live test konfirmasi: `findByUname("orisys06")` → user id=1561 ditemukan di tabel `mojf_users` DB newmojf.
  - recommendation: `users` (pack standard) KECUALI jika replikasi ke DB newmojf existing yang tabelnya `mojf_users` — maka `@Table(name="mojf_users")` untuk kompatibilitas data.
  - rationale: pack menetapkan `users`; tapi jika OQ-AR-2 memilih pakai DB newmojf existing, nama tabel harus cocok skema existing (`mojf_users`) agar lookup user terdaftar berhasil.
  - scan_citations: `mojf_users_Model.java:13` (`@Table(name="mojf_users")`), `framework-conventions/spring.md §Naming standards` (`users`)
  - fallback_if_wrong: jika tabel existing beda nama, sesuaikan `@Table(name=...)`.
- [x] **OQ-DM-2** [P3] [tech / recommend] [conf: low]: update `last_login` saat login sukses? newmojf punya kolomnya tapi `/dologin` tidak terlihat mengupdatenya. → **Resolved v1.3** (2026-07-23, implementation-verified, commit 6783558 U-008): VERIFIED — v1 read-only, tidak ada write `last_login`. `AuthUserController` hanya panggil `userRepository.findByUname` (read); tidak ada `setLastLogin`/`save`. Field `lastLogin` tetap ada di entity untuk mapping kolom DB, tapi tidak di-write di flow login. Sesuai rekomendasi (replikasi verbatim pola newmojf). Audit-login = future enhancement jika PO minta.
  - recommendation: Tidak update `last_login` di v1 (replikasi verbatim pola newmojf yang hanya lookup, tidak write).
  - rationale: `/dologin` newmojf hanya read user via `findUserAccount`; menambah write `last_login` = scope creep di luar replikasi.
  - scan_citations: `AuthUserController.java:130` (read-only lookup), `mojf_users_Model.java:39-40` (kolom last_login ada)
  - fallback_if_wrong: jika PO ingin audit login, tambahkan `@Transactional` write di service layer.
- [x] **OQ-DM-3** [P3] [tech / recommend] [conf: medium]: strategi DDL — Hibernate `ddl-auto=none` (sesuai newmojf, skema dikelola eksternal) atau `update`/Flyway? → **Resolved v1.3** (2026-07-23, implementation-verified, commit 6595b3b U-008): VERIFIED — `application.yaml` `spring.jpa.hibernate.ddl-auto: none`. Sesuai cabang OQ-AR-2 (pakai DB newmojf existing): skema `mojf_users` dikelola eksternal/DBA, Hibernate tidak mengubah skema. Flyway tidak dipakai di v1 (skema existing sudah ada); jika future migrasi ke DB baru khusus coresystembackend, pertimbangkan Flyway.
  - recommendation: `spring.jpa.hibernate.ddl-auto=none` (skema dikelola DBA/eksternal, replikasi pola newmojf).
  - rationale: newmojf pakai `ddl-auto=none` (application-test.properties:3); skema tabel `mojf_users` sudah ada di DB target. `none` mencegah Hibernate mengubah skema produksi.
  - scan_citations: `application-test.properties:3` (`ddl-auto=none`), `framework-conventions/spring.md §Idioms`
  - fallback_if_wrong: jika DB baru khusus coresystembackend (OQ-AR-2), pertimbangkan Flyway untuk DDL terkontrol.
