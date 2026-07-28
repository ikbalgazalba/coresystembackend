# Seed PRD — acquisition (pointer)

> This vault was generated from a **multi-file PRD set** (8 docs), not a single seed-PRD. The authoritative sources are the original PRD files, cited with sha256 in `../vault.json.source_documents`. This file is a pointer for the mega-sdd convention (`source/seed-PRD.md`).

## Source documents (authoritative — do not edit here)

| Doc | Path | Version | Date | sha256 |
|---|---|---|---|---|
| BE-00 OVERVIEW (umbrella) | `docs/prd/acquisition/BE-00-OVERVIEW.md` | 2.1 | 2026-07-22 | 0aaf1e37... |
| BE-01 intake-cas | `docs/prd/acquisition/BE-01-intake-cas.md` | 2.1 | 2026-07-14 | 35242109... |
| BE-02 credit-analysis | `docs/prd/acquisition/BE-02-credit-analysis.md` | 2.1 | 2026-07-14 | 3de64268... |
| BE-03 approval-committee | `docs/prd/acquisition/BE-03-approval-committee.md` | 2.1 | 2026-07-22 | 7e1c9b32... |
| BE-04 contract-cm-po | `docs/prd/acquisition/BE-04-contract-cm-po.md` | 2.1 | 2026-07-14 | 46a37b9a... |
| BE-05 npp-legalization | `docs/prd/acquisition/BE-05-npp-legalization.md` | 2.1 | 2026-07-14 | 282c7654... |
| BE-06 vertel-verification | `docs/prd/acquisition/BE-06-vertel-verification.md` | 2.1 | 2026-07-14 | 8dbebd94... |
| BE-07 master-data-menus | `docs/prd/acquisition/BE-07-master-data-menus.md` | 2.1 | 2026-07-22 | 6e3b8b50... |

## Companion docs (cited, present on disk)

- `docs/ARCHITECTURE-PROPOSAL.md` — ADR-01..15
- `docs/DB-CONVENTIONS.md` — schema standard (ADR-14)
- `docs/DATA-MIGRATION-PLAN.md` — migration (ADR-15)

## Provenance caveat

BE-00 + ARCHITECTURE-PROPOSAL cite `.mega-sdd/knowledge-base/` as the authoritative technical source (KB hasil ekstraksi legacy .NET + 473 SP + schema `FC_ACQ_MCF` + FE `FINCORE.WEB`). **KB tidak ada di disk** saat vault ini dibuat → captured as `OQ-AC-PROVENANCE`. Do not fabricate KB content; restore/move KB or update citations before `bind-codebase`.
