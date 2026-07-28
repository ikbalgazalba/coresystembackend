# 01 — Overview

> **TL;DR**: Microservice Acquisition = credit-origination pembiayaan kendaraan (motor/mobil) untuk lender multifinance Indonesia. Memiliki STEP 8–15 dari alur 16-STEP final: sinkronisasi MOOFI→FINCORE → RFA cabang → RAC → credit analysis → komite → PO → Vertel → NPP legalization. Menghasilkan catatan akuntansi (jurnal + AR Card), master loan, dokumen PK, dan event untuk downstream (disbursement/BPKB/dealer payment/insurance) yang **PULL**.

## What

Acquisition menerima permohonan pembiayaan kendaraan bersumber dari dealer (Pooling Order), field agent, atau channel mobile (MOOFI — Repeat Order / Instant-Approval), lalu membawanya dari intake pertama melewati credit decisioning, persetujuan komite, kontrak/PO, verifikasi telepon (Vertel), hingga legalisasi (NPP). Rebuild = reengineering, **bukan mirror legacy**: bug legacy "do-not-replicate" WAJIB diperbaiki; keputusan meeting D-01..D-12 adalah requirement target-state yang mengikat di atas perilaku legacy.

**5 kapabilitas inti** (boleh ship sebagai SATU modular service per ADR-01):
- **01 intake-cas** (STEP 8–9): minting `credit_id` (PK unik nasional), draft kontrak `RFA='0'`, RFA & pengecekan cabang
- **02 credit-analysis** (STEP 10–11): RAC risk-gating async (CF konvensional vs US syariah) via ACL + Credit Analysis (validasi dokumen granular, SLIK, scoring, DSR, rekomendasi)
- **03 approval-committee** (STEP 12): routing hierarki maker-checker by `trans_type_id` + Plafond OP + skala risiko; lock OP/ULI/LCR + asuransi di approve
- **04 contract-cm-po** (STEP 13): PO minting deterministik tunggal + cetak/email ke dealer + Open CM koreksi
- **05 npp-legalization** (STEP 15): validasi BAST + chassis, aktivasi atomik (jurnal + AR Card + master loan + PK + sync Passnet + email blast dealer)
- **06 vertel-verification** (STEP 14, kapabilitas pendukung): verifikasi telepon konsumen maker-checker (Admin Cabang → Kepala Cabang), hard-gate sebelum NPP
- **07 master-data-menus** (cross-cutting, D-08): menu master User & Dealer + transaction-type hierarchy + reference data

## Who

**Aktor (role census D-10 `[LOCKED]`, hierarki by skala risiko):**
- **CMO** — originasi/input lapangan (STEP 1–7 via MOOFI); target Correction
- **Marketing Head** — reviewer/approver bernama di credit memo; posisi hierarki `[OPEN]`
- **Credit Analyst** — STEP 11 validasi dokumen granular, bedah SLIK, rekomendasi + justifikasi
- **Kepala Cabang** — approver Vertel (STEP 14) dan NPP (STEP 15)
- **Credit (Admin) / "Admin Cabang"** — STEP 9 cek dokumen + Verify; STEP 13 cetak PO + email; STEP 14 wawancara Vertel + RFA; STEP 15 validasi BAST + RFA NPP

**Super-user DIHAPUS** (D-09 `[LOCKED]`) — tidak ada role/grant/bypass setara. No-self-approval (D-01 S11) enforced app-layer.

**Aktor eksternal:** Dealer personnel (originasi + penerima email PO/blast, BUKAN peserta hierarki approval), Employee via corporate directory LDAP (bukan password store lokal).

## Why

Inti credit-origination pembiayaan kendaraan — funnel dari permohonan sampai kontrak aktif yang siap disburse. Rebuild memenuhi tujuan bisnis + constraint `[LOCKED]`, **bukan** mereplika legacy verbatim. Bug legacy (GL silent no-op, BPKB guard dinonaktifkan, blacklist fail-open, LKK grade→weight bug, security anti-pattern `EXECUTE AS 'sa'`/`sp_OACreate` HTTP/cross-DB DML, FE login guard mati/auth-cookie tak di-enforce/branch trust gap) WAJIB diperbaiki. Migrasi = deliverable Phase 1 (simulation-first + reconciliation gate, ADR-15), bukan aktivitas cutover.

## Success criteria

> PRD TIDAK memfabrikasi angka target; "efisien" jadi terukur setelah baseline (A-13). Kriteria per-fase di BE-00 §10.

- **Phase 1 (Foundation):** customer master dedup-by-NIK dengan dedup lock di capture pertama (D-01 S1); related-person typed + strict validation (D-01 S2); sync MOOFI→FINCORE mint `credit_id` mint-once + draft `RFA='0'`; RFA cabang (Verify/Correction/Reject); AML/blacklist entry-time deterministic broad-match fail-closed; RBAC census D-10 enforced, tidak ada super-user; CRUD master User & Dealer (D-08)
- **Phase 2 (Decisioning):** RAC via ACL (async + callback ingest), rute CF vs US; orkestrasi biro honor freshness 30-hari; routing komite by `trans_type_id`+OP+risk; tiga aksi; enforce no-self-approval; lock OP/ULI/LCR+insurance di committee-approve; PO minting deterministik tunggal; Vertel STEP 14 penuh; verification hard-gate sebelum NPP
- **Phase 3 (Legalization):** NPP activation gated BAST+chassis (hard in-transaction) + Vertel gate + expiry 30-hari; aktivasi atomik (D-01 S15) → TrNpp aktif + PK + jurnal/AR Card + master loan + upsert `tr_CIF` + sync Passnet (outbox) + email blast dealer; GL/subledger normalized + idempotency-key + compensating reversal + fail-closed; downstream PULL
- **Cross-cutting:** semua field/rule `[LOCKED]` 1:1; semua `[ARTIFACT]` dikonfirmasi dibuang; setiap keputusan meeting D-xx terimplementasi & tertelusur; bug do-not-replicate diperbaiki dengan regression test; state machine cocok domain; seam via ACL; idempotency + reversal di titik uang/state eksternal; kontrak layar FE KB terpenuhi

## Sources

- `docs/prd/acquisition/BE-00-OVERVIEW.md` §Tujuan Bisnis, §1 Ruang Lingkup, §2 Aktor, §3 Kapabilitas, §10 Fase, §11 OQ
- `docs/prd/acquisition/BE-01..BE-07` §1 scope, §2 aktor
- `docs/ARCHITECTURE-PROPOSAL.md` §1 Executive Summary, §2 Constraint, §5 Dekomposisi Modul

## Out of Scope

- MOOFI mobile origination STEP 1–7 (upstream; semantik mengikat sebagai kontrak input)
- Post-acquisition eksekusi STEP 16 (Disbursement/GL, BPKB custody, Dealer Payment, Insurance) = downstream PULL
- Frontend (`FE-*` Next.js) — PRD terpisah
- Sibling context COLLECTION, TERMINATION
- Topologi infra final (menunggu ITEC D-11)

## Open Questions

- **OQ-ARCH-STACK** [P1] — framework/transport/topologi (Java LOCKED D-12; Spring Boot USULAN; menunggu ITEC D-11)
- **OQ-MEET-06** [P1] — matrix step per-product MACF (D-07); mem-block annex per-product, bukan payung
- **OQ-AC-PROVENANCE** [P1] — KB `.mega-sdd/knowledge-base/` tidak ada di disk padahal di-sitasi sebagai sumber otoritatif
- Daftar lengkap P1 blocker di `00-index.md` §Open Questions roll-up + `vault.json.open_questions`
