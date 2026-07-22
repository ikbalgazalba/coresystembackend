---
type: prose
doc_id: 01-overview
vault_version: "1.1"
aliases: [Overview]
tags: ["vault/jwt-login", "doc/overview"]
---<!-- BIND: oq=OQ-OV-1 | state=NEW | jwt-response-field-names-fixed(ADV-001) -->



# 01 — Overview

> **TL;DR**: Fitur login JWT untuk coresystembackend — replikasi pola newmojf (LDAP UCS → JWT + lookup user). · Architect / PM / BE Dev · baca saat review scope & success criteria.

## Product

Fitur login berbasis JWT pada coresystembackend: satu endpoint `POST /api/auth/dologin` yang menerima username + password, mengautentikasi via LDAP UCS (pola `LDAP_UCS_Utils.authLDAPNew` dari newmojf), menerbitkan JWT dari uname, lalu lookup data user terdaftar dari tabel `users` dan mengembalikan `{token, id, uname, kodeMitra, role}`. `(seed-PRD §A; AuthUserController.java:89-150)`

## Target users / personas

- **API consumer / client app** — klien yang memanggil `/api/auth/dologin` untuk mendapatkan JWT, lalu menggunakannya di header `Authorization: Bearer <token>` untuk akses endpoint terproteksi. `(seed-PRD §B)`

## Problem & motivation

coresystembackend saat ini skeleton Spring Initializr tanpa layer HTTP/security (`spring-boot-starter` saja, belum `-web`/`-security`). Dibutuhkan mekanisme autentikasi yang konsisten dengan pola newmojf agar klien bisa mendapatkan token akses stateless. `(seed-PRD §C; codebase-map.md §7)`

## Success criteria

- Endpoint `POST /api/auth/dologin` menerima `{uname, pass}` dan mengembalikan `JwtResponse` field verbatim newmojf `{token, type="Bearer ", id, uname, mitKode, urole}` (nama field final → OQ-AR-7) untuk kredensial valid. `(seed-PRD §D; JwtResponse.java:5-10; AuthUserController.java:133-138)`
- JWT diterbitkan dari uname; secret + expiration dibaca dari config (`jwtExpirationMs=86400000` → 24 jam). `(seed-PRD §D; application-test.properties:24-25)`
- User terdaftar dibaca dari tabel `users` (struktur `mojf_users_Model`, diadaptasi ke `jakarta.persistence`). `(seed-PRD §D; mojf_users_Model.java)`
- Kredensial salah / LDAP gagal → respons 400 dengan `MessageResponse`. `(AuthUserController.java:144-148)`
- Kebutuhan KPI/SLA lebih lanjut → `(unspecified)` (lihat OQ).

---

## Sources

- `source/seed-PRD.md` §A–§D
- `AuthUserController.java:89-150` (newmojf referensi)
- `application-test.properties:24-25` (newmojf referensi)
- `codebase-map.md §7 Framework`

## Out of Scope

- Refresh-token rotation `(seed-PRD §F)`
- Password change / reset `(seed-PRD §F)`
- User CRUD / registration `(seed-PRD §F)`
- Server-side session (stateless JWT) `(seed-PRD §F)`

## Open Questions

- [ ] **OQ-OV-1** [P3] [business] [conf: low]: success criteria kuantitatif (response time target, RPS) — tidak dinyatakan di brief; perlu konfirmasi PO? — resolve: PO/stakeholder
