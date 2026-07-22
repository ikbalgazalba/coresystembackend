---
type: prose
doc_id: 03-data-model
vault_version: "1.1"
aliases: [Data Model, DBML, Schema]
tags: ["vault/jwt-login", "doc/data-model"]
---

# 03 — Data Model

> **TL;DR**: Satu entity `users` — adaptasi `mojf_users_Model` ke `jakarta.persistence` (Boot 4.x). · BE Dev / DBA · baca saat design entity/repo.

## Entities (DBML)

```dbml
Table users {
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

Note: 'Adaptasi mojf_users_Model.java tabel mojf_users → jakarta.persistence (BUKAN javax). Nama tabel users (pack standard) vs mojf_users (referensi) → lihat OQ-DM-1. Field @Column name snake_case sesuai kolom DB newmojf.'
```

> **Purpose**: Menyimpan data user terdaftar untuk lookup saat login (`findByUname`). Field `kode_mitra` dan `urole` dipakai di JwtResponse. `(mojf_users_Model.java:12-50; AuthUserController.java:130-138)`

## Constraints

- **Uniqueness**: `users.uname` harus unik — dipakai sebagai lookup key login (`findByUname`). `(inferred from mojf_users_Model.java uname; AuthUserController.java:130)`
- **PK**: `id` bigint auto-increment (`@GeneratedValue IDENTITY`). `(mojf_users_Model.java:15-18)`
- **Column mapping**: field Java camelCase → kolom DB snake_case eksplisit via `@Column(name=...)` (mis. `namaLengkap` → `nama_lengkap`, `kodeMitra` → `kode_mitra`). `(mojf_users_Model.java:19-50)`
- **Audit**: `created_date/created_by`, `last_modified/modified_by`, `last_login` — kolom audit ada di referensi; update `last_login` saat login sukses = `(unspecified)` (opsional, lihat OQ-DM-2).

## Schema realization (pack)

**Intent**: Entity JPA merepresentasikan tabel user untuk lookup login.
**Starterkit binding** (`spring`):
- `@Entity` + `@Table` + `@Id` + `@GeneratedValue(strategy = IDENTITY)` + `@Column` mapping. `(spring.md §ERD additions)`
- Paket `entity/` (pack), bukan `model/` (referensi newmojf). `(spring.md §File location standards)`
- Namespace `jakarta.persistence.*` (Boot 4.x) — referensi newmojf `javax.persistence.*` HARUS diganti.
- Tabel snake_case plural (`users`) per pack; referensi `mojf_users` → OQ-DM-1.
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

- [ ] **OQ-DM-1** [P2] [tech / recommend] [conf: medium]: nama tabel entity — pakai `users` (pack standard snake_case plural) atau replikasi `mojf_users` (referensi newmojf)? — resolve: lihat Auto-Classification Review
  - recommendation: `users` (pack standard) KECUALI jika replikasi ke DB newmojf existing yang tabelnya `mojf_users` — maka `@Table(name="mojf_users")` untuk kompatibilitas data.
  - rationale: pack menetapkan `users`; tapi jika OQ-AR-2 memilih pakai DB newmojf existing, nama tabel harus cocok skema existing (`mojf_users`) agar lookup user terdaftar berhasil.
  - scan_citations: `mojf_users_Model.java:13` (`@Table(name="mojf_users")`), `framework-conventions/spring.md §Naming standards` (`users`)
  - fallback_if_wrong: jika tabel existing beda nama, sesuaikan `@Table(name=...)`.
- [ ] **OQ-DM-2** [P3] [tech / recommend] [conf: low]: update `last_login` saat login sukses? newmojf punya kolomnya tapi `/dologin` tidak terlihat mengupdatenya. — resolve: lihat Auto-Classification Review
  - recommendation: Tidak update `last_login` di v1 (replikasi verbatim pola newmojf yang hanya lookup, tidak write).
  - rationale: `/dologin` newmojf hanya read user via `findUserAccount`; menambah write `last_login` = scope creep di luar replikasi.
  - scan_citations: `AuthUserController.java:130` (read-only lookup), `mojf_users_Model.java:39-40` (kolom last_login ada)
  - fallback_if_wrong: jika PO ingin audit login, tambahkan `@Transactional` write di service layer.
- [ ] **OQ-DM-3** [P3] [tech / recommend] [conf: medium]: strategi DDL — Hibernate `ddl-auto=none` (sesuai newmojf, skema dikelola eksternal) atau `update`/Flyway? — resolve: lihat Auto-Classification Review
  - recommendation: `spring.jpa.hibernate.ddl-auto=none` (skema dikelola DBA/eksternal, replikasi pola newmojf).
  - rationale: newmojf pakai `ddl-auto=none` (application-test.properties:3); skema tabel `users` sudah ada di DB target. `none` mencegah Hibernate mengubah skema produksi.
  - scan_citations: `application-test.properties:3` (`ddl-auto=none`), `framework-conventions/spring.md §Idioms`
  - fallback_if_wrong: jika DB baru khusus coresystembackend (OQ-AR-2), pertimbangkan Flyway untuk DDL terkontrol.
