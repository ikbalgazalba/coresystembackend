# Seed PRD — acquisition-master-data (pointer)

> This sub-vault was generated from a **single primary PRD** (BE-07) + 07-owned rows of the umbrella registry (BE-00 §6.3). The authoritative source is the original PRD file, cited with sha256 in `../vault.json.source_documents`. This file is a pointer for the mega-sdd convention (`source/seed-PRD.md`).

## Source documents (authoritative — do not edit here)

| Doc | Path | Version | Date | sha256 |
|---|---|---|---|---|
| BE-07 master-data-menus (PRIMARY) | `docs/prd/acquisition/BE-07-master-data-menus.md` | 2.1 | 2026-07-22 | 6e3b8b50... |
| BE-00 OVERVIEW (umbrella — §6.3 registry 07 rows, §2 role census) | `docs/prd/acquisition/BE-00-OVERVIEW.md` | 2.1 | 2026-07-22 | 0aaf1e37... |

## Companion docs (cited, present on disk)

- `docs/ARCHITECTURE-PROPOSAL.md` — ADR-01..15 (§3/§5 M7 trunk reference leaf; §4 ADR-02/03/05/07/13/14/15 relevan 07)
- `docs/DB-CONVENTIONS.md` — schema standard (ADR-14)
- `docs/DATA-MIGRATION-PLAN.md` — migration (ADR-15; mapping matrix BE-07 §3 kolom "Mapping asal")

## Parent vault

- `../acquisition/` — umbrella vault (source of truth untuk boundary antar-modul; sub-vault ini cross-reference, BUKAN duplikasi)

## Provenance caveat (inherited)

BE-07 cites `.mega-sdd/knowledge-base/` (file `10-domains/10-customer-applicant-master.md`, `11-dealer-partner-master.md`, `12-product-asset-master.md`, `50-integrations/external-masters-and-linked-servers.md`, `30-data-model/reference-entities.md`, `30-data-model/external-masters-census.md`, `60-frontend/66-master-data-screens.md`, `60-frontend/60-app-shell-auth-navigation.md`) as authoritative technical source. **KB tidak ada di disk** → inherited `OQ-AC-PROVENANCE` dari umbrella. Do not fabricate KB content; restore/move KB or update citations before `bind-codebase`.
