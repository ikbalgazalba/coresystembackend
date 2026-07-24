# PRD — NPP Legalization & Downstream [BE]

> **Audience**: Tim Backend (BE). **Target stack**: **Java** `[LOCKED per D-12]` — framework belum ditetapkan; **rekomendasi: Spring Boot** (USULAN — lihat §11 [OPEN] OQ-ARCH-STACK). **Tanggal**: 2026-07-14.
> **Sumber otoritatif**: `_ACQUISITION-GROUND-TRUTH.md` v2 (PDF "ALUR TRANSAKSI ACQUISITION 08072026", 16-STEP final) + `_MEETING-DECISIONS-2026-07.md` (D-01..D-12) + KB `10-domains/24-npp-legalization-downstream.md`, `10-domains/32-disbursement-subledger.md`, `50-integrations/passnet-mf-payment-sync.md`, `50-integrations/email-sms-notifications.md`, KB FE `60-frontend/65-npp-vertel-screens.md`.
> **Status**: Revisi post-meeting (menggantikan baseline pre-meeting `05-npp-legalization.md`). Arsitektur infra final = deliverable ITEC Bank Mega (D-11) — PRD ini menyatakan asumsi & menandai keputusan yang menunggu dokumen itu.

Kapabilitas **05-npp-legalization** adalah **gerbang legalisasi final** pipeline origination pembiayaan kendaraan (**STEP 15** pada alur final 16-STEP — PDF 08072026) sekaligus **titik pemicu distribusi hilir post-acquisition** (**STEP 16**). Service ini memiliki *aktivasi* kontrak pembiayaan (NPP = *Nota Persetujuan Pembiayaan*): meng-*enforce* gate keras (verification `verified` + freshness **30-hari strict** — D-01 Step 14, validasi chassis/engine `sp_validation_chasis_number`, kelengkapan BAST) **in-transaction**, meng-aktivasi `FINANCING_AGREEMENT` (legacy `TrNpp`), memicu pembentukan **dokumen PK / Perjanjian Pembiayaan** (D-04), meng-upsert customer master (`tr_CIF`), memastikan **jurnal + kartu piutang (AR Card)** (D-06) dan **master loan** (D-05) terbentuk, menaruh outbox-row **Passnet** (GT `:70`), dan meng-emit **email blast ke dealer** (D-03). Konsumen hilir STEP 16 (**Dealer Payment, BPKB Management, Insurance**) bersifat **PULL/event — BUKAN push** (D-01 Step 15; GT `:72-73`); service ini meng-emit `AgreementActivated`, tetapi **tidak** meng-eksekusi transfer dealer, custody BPKB, atau cover insurance.

- **STEP dicakup**: STEP 15 (NPP / final legality) + STEP 16 (post-acquisition downstream distribution). *(Renumbering v2: baseline pre-meeting menyebut FASE 14/15 — supersede oleh GT v2 deltas table.)*
- **Kepemilikan otoritatif**: aktivasi `FINANCING_AGREEMENT` (`agreement_no`), penulisan `CUSTOMER`/`tr_CIF`, outbox Passnet, event `AgreementActivated`, enforcement gate NPP, pemicu email blast dealer (D-03), pemicu pembentukan jurnal/AR Card/master loan (D-05/D-06 — batas eksekusi lihat §1.3).
- **Upstream langsung baru (v2)**: **STEP 14 Vertel** (verifikasi telepon, `TrVerificationCustomer`, approve Kepala Cabang — D-02) adalah **produsen** status verifikasi yang di-gate service ini.
- **Sumber KB utama**: `10-domains/24-npp-legalization-downstream.md`; `_ACQUISITION-GROUND-TRUTH.md:66-73` (STEP 15–16); `_MEETING-DECISIONS-2026-07.md`; `10-domains/32-disbursement-subledger.md`; `50-integrations/passnet-mf-payment-sync.md`; `50-integrations/email-sms-notifications.md`; `60-frontend/65-npp-vertel-screens.md`.

---

## 1. Ruang Lingkup & Kepemilikan

### 1.1 Dimiliki service ini (owned)

| Kepemilikan | Deskripsi | Sumber |
|---|---|---|
| **Aktivasi NPP / FINANCING_AGREEMENT** | Transisi kontrak ke `active` (legacy `TrNpp`, `agreement_status='A'`), penetapan `agreement_no` `[LOCKED]`, stamp approver/approve-date, agreement value/total obligation. Aktivasi **atomik** (D-01 Step 15). | `24-npp §6`; GT `:69-70`; D-01 |
| **Enforcement gate keras NPP (in-transaction)** | Verification hard-gate (`verified` + freshness **30-hari strict** — D-01 Step 14), validasi chassis+engine (`sp_validation_chasis_number`), kelengkapan BAST (D-01 Step 14 — hard gate), due-date ≥ approve-date. Blokir + **rollback** bila gagal. | GT `:66-69`; D-01 Step 14; `24-npp §7 BR-NPP-1/21`; OQ-MEET-05 |
| **Maker→Checker NPP** | RFA NPP oleh **Admin Cabang / Credit (Admin)**; **Approval NPP oleh Kepala Cabang** (GT `:68-69`). Audit tiap transisi. **Tanpa super-user** (D-09 `[LOCKED]`); self-approval **DIBLOKIR** (D-01 Step 11). | GT `:68-69`; D-09; D-10; `24-npp §3a/§7 BR-NPP-8` |
| **Penulisan otoritatif `CUSTOMER` (`tr_CIF`)** | Upsert customer master by `national_id` (individu) / `tax_id` (korporat) saat aktivasi (D-01 Step 15: "upserts customer master"). | D-01 Step 15; `24-npp §6 BR-NPP-16` |
| **Pembentukan dokumen PK (Perjanjian Pembiayaan)** | Output wajib penyelesaian STEP 15 (D-04); dataset cetak tersedia via endpoint reports; print action on-demand (BR-NPP-4). | D-04; GT `:69`; `24-npp §7 BR-NPP-4`; `NPPReportsController.cs` |
| **Pemicu jurnal + AR Card + amortisasi** | Output wajib penyelesaian STEP 15 (D-06; GT `:70` "Terbentuk jurnal dan kartu piutang (AR Card)"). AR Card & Master Loan berada di bounded context ACQUISITION per flow.png (GT `:10-11`). Batas eksekusi vs modul disbursement-subledger: §1.3. | D-06; GT `:10-11,70`; `32-disbursement §1`; OQ-MEET-03 |
| **Pemicu master loan** | Record master loan WAJIB terbentuk saat aktivasi (D-05) — feeds servicing/collection. Field census + ownership final: OQ-MEET-02. | D-05; GT `:10` (flow.png: Master Loan ∈ ACQUISITION); OQ-MEET-02 |
| **Outbox Passnet** | "All data synced to Passnet" (GT `:70`): 1 baris `PASSNET_SYNC` (target `out_event`, §3.1.8; `status=pending` — legacy `is_sync='0'`) + `passnet_id` `[LOCKED]` per aktivasi (drain eksternal). | GT `:70`; `passnet-mf-payment-sync.md §2a/§5`; `24-npp §7 BR-NPP-14/15` |
| **Email blast dealer** | Notifikasi email ke dealer terkait setelah STEP 15 selesai (D-03) — requirement BARU (tidak ada padanan legacy di jalur NPP). Trigger point/template/failure policy: OQ-MEET-01. | D-03; `email-sms-notifications.md` (pola & do-not-replicate) |
| **Emisi event `AgreementActivated`** | Sinyal ke downstream STEP 16 (pull/subscribe), bukan push imperatif (D-01 Step 15: "downstream gets data via PULL, never push"). | D-01 Step 15; GT `:72-73`; hidden-gotchas GOTCHA-12 |
| **Cetak dokumen legal (on-demand)** | Dataset Perjanjian Pembiayaan + dokumen pendamping (important notice, surat kuasa fidusia, surat kuasa penarikan, surat pernyataan konsumen/persetujuan, MoU) via endpoint reports. | `24-npp §6 BR-NPP-4`; `65-npp-vertel §6` (print menu Approved-only); `NPPReportsController.cs` |

### 1.2 BUKAN milik service ini (non-goal)

| BUKAN dimiliki | Pemilik | Catatan |
|---|---|---|
| **Transfer dana ke dealer (Dealer Payment)** | PAYMENT DB eksternal (`PAYMENTContext`, cashier-operated) — downstream STEP 16 PULL | Tidak ada reader men-seed run Dealer Payment dari status NPP (OQ-NPP-03). `32-disbursement BR-DISB-16`; GT `:72`. |
| **Custody BPKB** (in/out/loan/handover) | collateral-bpkb-fidusia — downstream STEP 16 PULL | Candidate-queue di-PULL memakai `verification_status`, bukan di-push NPP. `31-collateral §7 BR-COLL-11`; GT `:72-73`. |
| **Insurance (Cover/Claim/Billing/Refund)** | INSURANCE bounded context — downstream STEP 16 PULL (**BARU v2**: Insurance ditambahkan ke daftar downstream) | GT deltas table (`FASE 15 downstream → STEP 16 downstream (+ Insurance)`); flow.png GT `:12-13`. |
| **Produksi status verifikasi (Vertel)** | modul Vertel / verification (STEP 14, D-02) | 05 hanya **meng-enforce** gate-nya; tidak menulis `TrVerificationCustomer`. GT `:63-65`. |
| **PO minting** | 04-contract-cm-po (STEP 13) | 05 hanya **membaca** `PURCHASE_ORDER`. |
| **Eksekusi/registrasi Passnet & Fidusia** | ETL/consumer eksternal + collateral | 05 hanya menaruh outbox-row; drain/write-back di luar slice (OQ-PASSNET-01). Fidusia = record-keeping internal, BUKAN API pemerintah live. |
| **Aturan posting GL & chart-of-accounts** | disbursement-subledger + finance stakeholder | GL account mapping / posting rules source of truth = OQ-MEET-03; crosswalk bank-ID `[LOCKED]` (`32-disbursement BR-DISB-5`). |

### 1.3 Batas eksekusi jurnal/AR Card/master loan (revisi post-meeting)

Baseline pre-meeting men-decompose seluruh cascade GL/AR ke "downstream PULL". **GT v2 + D-05/D-06 merevisi framing itu**: *jurnal, AR Card (kartu piutang), amortisasi, dan master loan adalah OUTPUT WAJIB penyelesaian STEP 15* (GT `:70`; D-05; D-06) — acquisition baru dinyatakan *complete* setelah output ini terbentuk. Yang tetap didekomposisi sebagai PULL adalah **STEP 16** (Dealer Payment, BPKB, Insurance).

Eksekusi teknisnya (siapa menulis tabel jurnal/AR Card) tetap berada di **engine disbursement-subledger** (aturan `32-disbursement` BR-DISB-1..7 `[LOCKED]` berlaku: balance-check zero-sum, GL-class lookup, formula anuitas, rounding), tetapi **dipicu dan di-track oleh 05** sebagai bagian kontrak aktivasi:

- **Opsi A (USULAN — rekomendasi sementara)**: modular monolith Java, satu transaksi DB mencakup aktivasi header + tr_CIF + outbox Passnet + jurnal + AR Card + amortisasi + master loan — paling dekat dengan semantik "aktivasi atomik" D-01 Step 15 dan memperbaiki fail-soft legacy (Edge Case 7 `sp_approve_npp:721-741`).
- **Opsi B**: microservices — saga/outbox dengan guarantee exactly-once, status intermediate `active_pending_ledger`, reconciliation wajib.
- Keputusan final menunggu dokumen arsitektur ITEC (D-11) → **[OPEN]** di §11. PRD ini menulis Acceptance Criteria pada level *outcome* (output harus terbentuk; kegagalan tidak boleh menghasilkan kontrak "aktif tanpa buku") sehingga valid untuk kedua opsi.

### 1.4 Reengineering mandate (bukan mirror legacy)

Semua *do-not-replicate* gotcha yang menyentuh kapabilitas ini **diperbaiki**, bukan ditiru — detail per-item di §6 & §9. Ringkas: aktivasi **atomik** (rollback penuh bila gagal, ganti commit-on-error `sp_approve_npp:721-741`), **idempotent** di batas mutasi, gate **fail-closed in-transaction**, downstream STEP 16 **event/outbox** menggantikan fire-and-forget, GL posting failure **tidak boleh silent** (`32-disbursement` Edge Case 1: return code `spSubledgerBooking` dibuang caller), satu engine config-driven untuk car & motor, email keluar dari database-tier (`sp_send_dbmail` + `EXECUTE AS 'sa'` = `[ARTIFACT]` do-not-replicate — `email-sms-notifications.md §4`), **super-user dihapus** (D-09).

---

## 2. Aktor & Peran

Sensus role cabang target-state (D-10 `[LOCKED]`): **CMO, Marketing Head, Credit Analyst, Kepala Cabang, Credit (Admin)**; hierarki approval tergantung **skala risiko**. **Super user DIHAPUS dari rebuild** (D-09 `[LOCKED]`; PDF PRD Notes 2.1).

| Aktor | Peran | Sumber |
|---|---|---|
| **Admin Cabang / Credit (Admin)** (maker) | Validasi BAST fisik & aset, input data NPP (BAST no/date, chassis/engine, bank reference, dsb), submit **RFA NPP**. | GT `:66-68`; D-10; `24-npp §2`; `TrNPPController.cs:92-131` |
| **Kepala Cabang** (checker) | Review hasil validasi chassis/engine + kelengkapan BAST, rekam **Approve / Reject / Correction** → aktivasi kontrak. | GT `:68-69` ("Approval NPP by Kepala Cabang"); D-10 |
| **System / Activation engine** | Eksekutor cascade internal atomik (aktivasi, CIF, PK dataset, jurnal/AR/master loan trigger, outbox Passnet, email-blast enqueue) + emisi `AgreementActivated`. | `24-npp §5 step 9`; D-01 Step 15 |
| **Modul Vertel (STEP 14)** (upstream) | **Produsen** status verifikasi telepon (`TrVerificationCustomer`) + freshness — dikonsumsi 05 sebagai hard-gate (read-only). | D-02; GT `:63-65` |
| **Downstream consumers STEP 16 (PULL)** | Cashier (Dealer Payment), BPKB/Vault Custodian, Insurance ops, GL reconciliation — poll/subscribe record eligible. | GT `:72-73`; D-01 Step 15; GOTCHA-12 |
| **External Bank Mega "Passnet"** | Sistem legacy core (system-of-record NPP number) yang menerima registrasi tiap kontrak aktif via outbox. | `passnet §1` |
| **Dealer (penerima email)** | Penerima email blast pasca-aktivasi (D-03); alamat dari master dealer (`ms_dealer.email`). | D-03; `email-sms-notifications.md §5` |

> **Enforcement identitas approver**: **no self-approval WAJIB di application layer** (D-01 Step 11 "self-approval BLOCKED"; SQL layer legacy tidak meng-enforce untuk NPP — OQ-MCP-01, overview §8.1). Legacy super-user self-approve stamp (BR-NPP-9, `sp_approve_npp:22-30,53`) = **[ARTIFACT] — DIHAPUS per D-09**; jangan dibawa ke rebuild dalam bentuk apa pun (OQ-NPP-07 tertutup oleh D-09).
> **Catatan role "Kepala Cabang"**: literal role string kepala-cabang tidak ditemukan di code FE legacy (gate memakai flag generik "pending approver" — `65-npp-vertel §2`, OQ-NPPVTL-02); D-10 menjadikannya role target-state bernama. Rebuild memodelkan role eksplisit `KEPALA_CABANG` pada langkah approve NPP.

---

## 3. Model Data

> **Lampiran wajib**: `docs/DB-CONVENTIONS.md` (disepakati 2026-07-14) — semua tabel target ditulis per konvensi itu (prefix kelas §1, kunci §2, tipe §3, kolom wajib §4, satu kolom `status` §5, larangan §6). Format census mengikuti DB-CONVENTIONS **§9**; disposisi migrasi per `docs/DATA-MIGRATION-PLAN.md` §1 (**MIGRATE / MIGRATE-READONLY / DISCARD / REBUILD**). Field `[LOCKED]` = additive only, nilai/makna dipertahankan (`data-mutation-policy.md`); NAMA kolom boleh mengikuti konvensi kecuali direferensikan sistem eksternal secara literal (mis. format `passnet_id`).
>
> **Cakupan census: 26/26 tabel legacy** yang menyentuh modul ini ter-disposisi — **14 OWNED** (§3.1, field census penuh) + **12 DOWNSTREAM-BOUNDARY** (§3.2, kontrak PULL — **TANPA** tabel target acquisition). Nama entitas konseptual yang dipakai §4–§10 (`FINANCING_AGREEMENT`, `CUSTOMER`, `PASSNET_SYNC`, `MASTER_LOAN`, `EMAIL_OUTBOX`, `NPP_APPROVAL_*`) dipetakan ke tabel target di §3.0. Idempotency + fail-closed output STEP 15 pada level schema: §3.3 (ADR-04; `ARCHITECTURE-PROPOSAL.md §7`).

### 3.0 Peta disposisi — 26 tabel legacy tercakup

**Kelas A — OWNED modul npp-legalization (14 tabel; census penuh di §3.1):**

| # | Tabel legacy (kolom) | Entitas PRD | Target (kelas prefix) | Disposisi | Census |
|---|---|---|---|---|---|
| 1 | `tr_NPP` (39) | `FINANCING_AGREEMENT` | `trx_agreement` (`trx_`) | MIGRATE | §3.1.1 |
| 2 | `tr_npp_log` (39, heap tanpa PK) | — (shadow copy) | `log_agreement_snapshot` (`log_`) | MIGRATE-READONLY | §3.1.2 |
| 3 | `tr_NPP_print` (42) | — (print + workflow fidusia) | `log_document_print` (`log_`) + split workflow → collateral | MIGRATE-READONLY (split) | §3.1.3 |
| 4 | `tr_CIF` (10) | `CUSTOMER` | `mst_customer` (`mst_`) | MIGRATE (dedup gate) | §3.1.4 |
| 5 | `tr_ARCard` (23) | AR Card (D-06) | `trx_ar_card` (`trx_`) | MIGRATE | §3.1.5 |
| 6 | `Tr_ARCard_Penalty` (13) | AR Card penalty | `trx_ar_card_penalty` (`trx_`) | MIGRATE | §3.1.5 |
| 7 | `Tr_Social_Fund` (11) | AR Card social fund (syariah/CF) | `trx_ar_card_social_fund` (`trx_`) | MIGRATE | §3.1.5 |
| 8 | `tr_amortization` (14) | Jadwal angsuran (D-06) | `trx_amortization` (`trx_`) | MIGRATE | §3.1.6 |
| 9 | `Tr_OverDue` (30) | Delinquency snapshot | **derived read-model** (tanpa dual-write) | MIGRATE-READONLY | §3.1.7 |
| 10 | `tr_synchronize_to_passnet` (9) | `PASSNET_SYNC` | `out_event` (`out_`, ADR-04) | **REBUILD** (bukan MIGRATE) | §3.1.8 |
| 11 | `tr_synchronize_to_passnet_BPKB` (9) | — (sync BPKB→Passnet) | `out_event` (emitter = collateral) | **REBUILD** | §3.1.8 |
| 12 | `tr_batching_trans` (10) | — (queue tanpa reader lokal) | **tidak ada** — digantikan event `AgreementActivated` | **[OPEN — OQ-NPP-09]**; default DISCARD + register | §3.1.10 |
| 13 | `tr_upload_fidusia` (13, heap tanpa PK) | Registrasi fidusia (record-keeping) | `trx_fiducia_registration` (`trx_`; runtime milik collateral §1.2) | MIGRATE | §3.1.9 |
| 14 | `tr_GFCIT_AccountMaster` (7) | — (GL CIT master, 0 referensi) | **tidak ada** | **DISCARD** `[ARTIFACT: dead]` (guard OQ-GAP-09) | §3.1.11 |

Entitas target BARU tanpa padanan tabel legacy langsung (bagian owned, census di §3.1.12): `log_approval_history` + Flowable `ACT_*` (= `NPP_APPROVAL_STEP`/`NPP_APPROVAL_HISTORY`), `trx_master_loan` (= `MASTER_LOAN`, D-05, [OPEN] OQ-MEET-02), `out_notification` (= `EMAIL_OUTBOX`, D-03).

**Kelas B — DOWNSTREAM-BOUNDARY (12 tabel; BUKAN milik acquisition — §3.2):**

| # | Tabel legacy | Keluarga | Pemilik target | Interaksi 05 |
|---|---|---|---|---|
| 15 | `CFCBSubLedger` | Subledger/GL | disbursement-subledger | Emit jurnal-event STEP 15 (fail-closed §3.3); TIDAK menulis |
| 16 | `CFCITSubLedger` | Subledger/GL | disbursement-subledger | idem |
| 17 | `CFContractSubLedger` | Subledger/GL | disbursement-subledger | idem (leg per `CreditId`) |
| 18 | `CFInterCompanyBranchSubLedger` | Subledger/GL | disbursement-subledger | idem |
| 19 | `CFSupplierSubLedger` | Subledger/GL | disbursement-subledger | idem (leg dealer/supplier) |
| 20 | `GFTransactionTypeGLLink` | Config posting GL | **07-master-data** — target `map_transaction_type_gl` (registry BE-00 §6.3; BE-07 §3.0); data milik finance (OQ-MEET-03) | Read-only referensi; TIDAK menulis |
| 21 | `tr_BPKB` | BPKB custody | collateral-bpkb-fidusia | PULL STEP 16; TIDAK menulis |
| 22 | `tr_BPKB_CMO` | BPKB custody | collateral-bpkb-fidusia | — |
| 23 | `tr_BPKB_loan` | BPKB custody | collateral-bpkb-fidusia | — |
| 24 | `tr_BPKB_loan_log` | BPKB custody | collateral-bpkb-fidusia | — |
| 25 | `tr_bpkb_bast_log` | BPKB custody (BAST custody) | collateral-bpkb-fidusia | — (BAST NPP ≠ BAST custody — §3.2.2) |
| 26 | `storage_location` | BPKB custody (master lokasi) | collateral-bpkb-fidusia | — |

### 3.1 Entitas dimiliki service ini — schema target + field census

#### 3.1.1 `trx_agreement` — entitas `FINANCING_AGREEMENT` (legacy `tr_NPP`, 39 kolom)

- **Kelas**: `trx_` (spine bisnis). **Tier**: `[LOCKED]` kontrak legal (additive only pada field bertanda). **Disposisi**: **MIGRATE** (checksum `[LOCKED]` zero-diff per DATA-MIGRATION-PLAN §3).
- **Mapping asal**: `tr_NPP` (`FC_ACQ_MCF 2.sql:6588-6637`; PK legacy `credit_id`). Seluruh 39 kolom legacy ter-map di bawah — tidak ada kolom dibuang diam-diam.
- **Constraint**: PK `id BIGINT IDENTITY`; `ux_trx_agreement_credit_id`; `ux_trx_agreement_agreement_no` (bila non-null); `ux_trx_agreement_activation_idempotency_key`; FK nyata ke PO/bank-reference; `ck_trx_agreement_status`.

| Kolom target | Tipe target | Null | Asal legacy (`tr_NPP`) | Marker | Catatan |
|---|---|---|---|---|---|
| `id` | `BIGINT IDENTITY` PK | NOT NULL | — (baru) | — | PK teknis (konvensi §2). |
| `credit_id` | `VARCHAR(20)` | NOT NULL, unique | `credit_id` varchar(20) PK | [VERIFIED]×**[LOCKED]** | Business key nasional-unik (minting STEP 8, GT `:25-28`); kunci lintas-modul (Passnet, jurnal, master loan). Format = OQ-GT-02 ✅ RESOLVED (`branch(5)+YY+MM+SEQ(5)` 14-char — BE-01 §3.1.13). |
| `po_id` | `BIGINT` FK → `trx_purchase_order` | NOT NULL | — (baru; legacy join via `credit_id`) | [INTENT] | Declared FK wajib (konvensi §2); sumber branch/dealer/tenor/produk (read dari 04). |
| `agreement_no` | `VARCHAR(25)` | NULL (diisi saat aktivasi) | — (**tidak ada kolom legacy**; "NppNo" legacy = `passnet_id`) | **[LOCKED]** nilai | Nomor kontrak legal, diassign **saat aktivasi**; format/penomoran via `cfg_number_format`. Jangan konflasi dgn `passnet_id`. |
| `passnet_id` | `VARCHAR(10)` | NULL, unique | `tr_synchronize_to_passnet.passnet_id` (dipindah ke header) | [VERIFIED]×**[LOCKED]** | Format `'5'`+9-digit zero-pad verbatim (`sp_get_passnet_id.sql:14`); identitas hidup di header, pengiriman di `out_event` (§3.1.8). |
| `status` | `VARCHAR(10)` + CHECK | NOT NULL | `agreement_status` varchar(1) | [VERIFIED]×**[LOCKED]** enum kanonik | `pending\|validated\|active\|held`; mapping legacy `0/A/C/R/V` = §7. Satu kolom status (konvensi §5). |
| `bast_no` | `VARCHAR(30)` | NULL (WAJIB terisi utk aktivasi) | `BAST_no` | [VERIFIED]×**[LOCKED]** | Hard-gate BR-NPP-N2 (D-01 S14). Rekonsiliasi vs `tr_BPKB.BAST_date` = OQ-NPP-08 (§3.2.2). |
| `bast_date` | `DATE` | NULL (idem) | `BAST_date` | [VERIFIED]×**[LOCKED]** | idem. |
| `bast_validated` | `BOOLEAN` DEFAULT false | NOT NULL | — (baru) | **[KEPUTUSAN DESAIN BARU — D-01 Step 14]** | Hasil hard-gate BAST (§6 BR-NPP-N2). |
| `agreement_date` | `DATE` | NULL | `agreement_date` datetime | [VERIFIED]×**[LOCKED]** | Tanggal kontrak legal. |
| `agreement_value` | `NUMERIC(18,2)` | NULL | `agreement_value` numeric(18,0) | [VERIFIED]×**[LOCKED]** | Whole-rupiah legacy → simpan `.00`; rekonsiliasi nilai bulat (konvensi §3). |
| `total_obligation` | `NUMERIC(18,2)` | NULL | `total_kewajiban` numeric(18,0) | [VERIFIED]×**[LOCKED]** | Rename ID→EN (konvensi §1); NILAI `[LOCKED]` (feed GL/disbursement). |
| `disbursal_repayment` | `NUMERIC(18,2)` | NULL | `disbursal_repayment` numeric(18,0) | [VERIFIED]×**[LOCKED]** | Total finansial feed GL. |
| `disbursal_received` | `NUMERIC(18,2)` | NULL | `disbursal_received` numeric(18,0) | [VERIFIED]×**[LOCKED]** | idem. |
| `nfa_percent` | `NUMERIC(9,6)` | NULL | `NFA_percent` numeric(21,2) | [VERIFIED]×[OPEN→treat LOCKED] | "NFA" tak ter-ekspansi di source (OQ-CORE-05); regulator-adjacent → perlakukan `[LOCKED]` sampai klarifikasi. |
| `is_plafon` | `BOOLEAN` DEFAULT false | NOT NULL | `is_plafon` bit | [VERIFIED]×[INTENT] | |
| `bill_received_date` | `DATE` | NULL | `bill_received_date` datetime | [VERIFIED]×[INTENT] | Trigger snapping due-date (BR-NPP-11). |
| `bill_receipt_date` | `DATE` | NULL | `bill_receipt_date` datetime | [VERIFIED]×[INTENT] | |
| `down_payment_receipt_no` | `VARCHAR(30)` | NULL | `down_payment_receipt_no` | [VERIFIED]×[INTENT] | Jejak administratif DP. |
| `down_payment_receipt_date` | `DATE` | NULL | `down_payment_receipt_date` | [VERIFIED]×[INTENT] | |
| `receipt_no` / `receipt_date` | `VARCHAR(30)` / `DATE` | NULL | `receipt_no` / `receipt_date` | [VERIFIED]×[INTENT] | |
| `bpkb_letter_no` / `bpkb_letter_date` | `VARCHAR(30)` / `DATE` | NULL | `BPKB_letter_no` / `BPKB_letter_date` | [VERIFIED]×**[LOCKED]** | Cross-reference legal sertifikat jaminan (collateral). |
| `installment_date` | `DATE` | NULL | `installment_date` datetime | [VERIFIED]×[INTENT] | Snapping end-of-month (BR-NPP-11); FE auto-calc + lock (`65-npp-vertel §5.4`). |
| `consumer_installment_date` | `DATE` | NULL | `consumen_installment_date` | [VERIFIED]×[INTENT] | Typo legacy "consumen" dinormalisasi (`[ARTIFACT]` nama, makna dijaga). |
| `bank_reference_id` | `BIGINT` FK | NOT NULL | `bank_reference_id` int | [VERIFIED]×[INTENT] | Rekening dealer terpilih (lookup §4). |
| `bank_reference_sub_id` | `BIGINT` FK | NOT NULL | `bank_reference_sub_id` int | [VERIFIED]×[INTENT] | |
| `estimated_insurance_cost` | `NUMERIC(18,2)` | NULL | `estimated_insurance_cost` numeric(21,2) | [VERIFIED]×**[LOCKED]** | Disclosure asuransi konsumen. |
| `insurance_coverage_period` | `VARCHAR(30)` | NULL | `insurance_coverage_period` | [VERIFIED]×**[LOCKED]** nilai | Di-reset saat aktivasi (BR-NPP-12; intent = OQ-NPP-10). |
| `insurance_billing_periodical` | `VARCHAR(1)` + CHECK | NULL | `insurance_billing_periodical` | [VERIFIED]×**[LOCKED]** nilai | idem. |
| `insurance_id` | `VARCHAR(3)` FK → master asuransi | NULL | `insurance_id` | [VERIFIED]×[INTENT] | |
| `other_ap_value` | `NUMERIC(18,2)` | NULL | `other_AP_value` numeric(18,0) | [VERIFIED]×[OPEN] | Arti "AP" tidak ditemukan di source. |
| `disbursal_type_umc` / `disbursal_type_umc_incentive` | `VARCHAR(1)` | NULL | `disbursal_type_UMC` / `_incentive` | [VERIFIED]×[INTENT] | Semantik "UMC" [OPEN]. |
| `incentive_nominal` | `NUMERIC(18,2)` | NULL | `incentive_nominal` numeric(18,0) | [VERIFIED]×[INTENT] | |
| `company_id` | `VARCHAR(5)` | NOT NULL | `company_id` | [VERIFIED]×[INTENT] | Dua legal entity (MCF vs MAF) — menentukan tujuan Passnet (§8). |
| `branch_id` | `VARCHAR(5)` | NULL | `branch_id` | [VERIFIED]×[INTENT] | |
| `approved_by` / `approved_at` | `VARCHAR(50)` / `TIMESTAMPTZ` | NULL | `approve_by` / `approve_date` | [VERIFIED]×[INTENT] | Stamp checker Kepala Cabang; WAJIB ≠ submitter (BR-NPP-N15). |
| `activated_at` | `TIMESTAMPTZ` | NULL | — (baru) | [INTENT] | |
| `pk_document_generated_at` | `TIMESTAMPTZ` | NULL | — (baru) | [KEPUTUSAN DESAIN BARU] | Bukti output D-04 (dataset PK terbentuk pasca-aktivasi). |
| `activation_idempotency_key` | `VARCHAR(64)` | NULL, unique | — (baru) | **[KEPUTUSAN DESAIN BARU — ADR-04]** | Guard replay BR-NPP-N4 in-transaction; detail §3.3. |
| `version` | `INTEGER` DEFAULT 0 | NOT NULL | — (baru) | — | Optimistic locking (konvensi §4). |
| `created_at`/`created_by`/`updated_at`/`updated_by` | per konvensi §4 | NOT NULL | `created_by`/`created_on`/`last_updated_by`/`last_updated_on` | [VERIFIED]×[INTENT] | `TIMESTAMPTZ` UTC. |

> Field kontrak lama `DealerPaymentStatus`/`ArScheduleStatus` = **vestigial tanpa backing column** di DDL `tr_NPP` → dibuang (BR-NPP-20, `[ARTIFACT]`). Rantai maker→checker TIDAK disimpan di header — lihat §3.1.12.

#### 3.1.2 `log_agreement_snapshot` (legacy `tr_npp_log`, 39 kolom identik `tr_NPP`)

- **Kelas**: `log_` (append-only; hanya `created_at`/`created_by` — konvensi §4). **Disposisi**: **MIGRATE-READONLY** (baris historis dibawa apa adanya sebagai arsip audit).
- **Mapping asal**: `tr_npp_log` (`FC_ACQ_MCF 2.sql:9151-...`) — heap **tanpa PK**, shadow copy kolom-identik `tr_NPP` (core-entities §5). Pola shadow-table = **dilarang direplikasi** (konvensi §6.1, `[ARTIFACT]` pola).
- **Target**: snapshot INSERT-only per transisi status `trx_agreement` (kolom = subset field §3.1.1 + `snapshot_reason`, `agreement_id` FK). Riwayat transisi via `log_`, bukan kolom `last_*` bertumpuk (konvensi §5). Field census = identik §3.1.1 (tidak diduplikasi di sini).

#### 3.1.3 `log_document_print` (legacy `tr_NPP_print`, 42 kolom — SPLIT)

- **Kelas**: `log_`. **Disposisi**: **MIGRATE-READONLY** (counter legacy di-snapshot ke arsip; log event baru mulai dari cutover — event print individual TIDAK bisa direkonstruksi dari counter).
- **Mapping asal**: `tr_NPP_print` (`FC_ACQ_MCF 2.sql:9197-9249`, PK `credit_id`). Kolom print-tracking bespoke = larangan eksplisit konvensi **§6.2** → tabel generik.
- **Ownership tabel**: `log_document_print` = tabel **SHARED cross-cutting** (ditetapkan BE-00 §6.3, catatan setelah registry); definisi kanonik generik = BE-02 §3.11. Census di bawah = pemetaan sumber `tr_NPP_print` ke tabel generik yang sama; harmonisasi kolom saat DDL: pola `entity_type`/`entity_id` di sini menggeneralisasi `document_ref` BE-02 §3.11 (satu DDL final).
- **SPLIT dua kelompok**:
  1. **Print tracking (21 kolom — owned 05)**: 3 famili flat (`is_print_PK`/`sum_of_print_PK`/`first/last_print_date_PK`/`first/last_print_by_PK`; idem `_akad`; idem `_fiduciary`) → baris `log_document_print` generik. `[VERIFIED]×[ARTIFACT]` pola; outcome (siapa cetak apa, kapan, berapa kali — BR-NPP-4 `[LOCKED]` dokumen regulated) dipertahankan.
  2. **Workflow fidusia/notaris (21 kolom — BUKAN owned 05)**: `fiduciary_send/receive/revision_*`, `notary_send/receive_*`, `complete_status`/`complete_check_*`, `is_upload` = workflow administrasi pengiriman dokumen fidusia → milik **collateral-bpkb-fidusia** (§1.2); didokumentasikan di PRD modul collateral, `[OPEN]` penempatan final (koordinasi).

| Kolom target | Tipe target | Null | Asal legacy | Marker | Catatan |
|---|---|---|---|---|---|
| `id` | `BIGINT IDENTITY` PK | NOT NULL | — | — | |
| `entity_type` / `entity_id` | `VARCHAR(30)` / `BIGINT` | NOT NULL | — (generik) | [KEPUTUSAN DESAIN BARU] | Utk NPP: `AGREEMENT` + `trx_agreement.id`. |
| `document_type` | `VARCHAR(40)` + CHECK | NOT NULL | sufiks famili `_PK`/`_akad`/`_fiduciary` | [VERIFIED]×[INTENT] | Set dokumen legal = §4 `documents/{docType}`. |
| `printed_by` / `printed_at` | `VARCHAR(50)` / `TIMESTAMPTZ` | NOT NULL | `first/last_print_by_*`, `first/last_print_date_*`, `sum_of_print_*` | [VERIFIED]×[INTENT] | Satu baris per event print; agregat (count/first/last) = query. |
| `created_at` / `created_by` | per konvensi §4 (log_) | NOT NULL | — | — | Append-only. |

#### 3.1.4 `mst_customer` — entitas `CUSTOMER` (legacy `tr_CIF`, 10 kolom)

- **Kelas**: `mst_`. **Penulisan otoritatif = modul 05** saat aktivasi (D-01 Step 15 "upserts customer master") — penyimpangan eksplisit dari default konvensi §1 ("dimiliki modul master-data kecuali dinyatakan lain"); **koordinasi schema dengan BE-07** (master-data) sebagai reader/steward, dan dengan OQ-MEET-02 (relasi customer ↔ master loan servicing).
- **Mapping asal**: `tr_CIF` (PK `CIF`). **Disposisi**: **MIGRATE** + dedup report (duplikat NIK historis = keputusan bisnis OQ-MIG-03, BUKAN auto-merge — DATA-MIGRATION-PLAN §3).
- **Constraint**: `ux_mst_customer_identity_company` pada (`national_id`|`tax_id`, `owning_company_id`) — mengunci semantik BR-NPP-16 (record **kedua** per company, BUKAN merge).

| Kolom target | Tipe target | Null | Asal legacy (`tr_CIF`) | Marker | Catatan |
|---|---|---|---|---|---|
| `id` | `BIGINT IDENTITY` PK | NOT NULL | — | — | |
| `cif_no` | `VARCHAR(20)` | NOT NULL, unique | `CIF` varchar(20) PK | [VERIFIED]×[INTENT] | Nomor CIF legacy dibawa sebagai business key migrasi; penomoran baru = sequence + `cfg_number_format`. Interop eksternal nomor CIF = [OPEN]. |
| `national_id` | `VARCHAR(16)` | NULL (CHECK: wajib bila `individual`) | `KTP` varchar(20) | [VERIFIED]×**[LOCKED]** | NIK — panjang 16 `[LOCKED]` regulatori (konvensi §3); legacy varchar(20) → validasi saat migrasi, non-konform = reject + register. Kunci dedup individu (OJK/Dukcapil + AML). |
| `tax_id` | `VARCHAR(16)` | NULL (CHECK: wajib bila `corporate`) | `NPWP` varchar(20) | [VERIFIED]×**[LOCKED]** | NPWP 15/16 — kunci korporat; pelaporan pajak/OJK. |
| `full_name` | `VARCHAR(100)` | NULL | `customer_name` varchar(50) | [VERIFIED]×[INTENT] | Di-update saat mismatch (BR-NPP-16). |
| `customer_kind` | `VARCHAR(10)` + CHECK `individual\|corporate` | NOT NULL | `customer_type` varchar(1) | [VERIFIED]×[INTENT] | Enumerasi nilai legacy [OPEN] — validasi vocabulary saat migrasi. |
| `owning_company_id` | `VARCHAR(5)` | NOT NULL | `PT` varchar(3) | [VERIFIED]×[INTENT] | Identitas sama di company berbeda → record kedua (BR-NPP-16). |
| `ojk_economic_sector` | `VARCHAR` (OJK code) | NULL | — (**tidak ada di `tr_CIF`**; seed dari data applicant 01-intake) | **[LOCKED]** additive | Bagian set identitas CUSTOMER `[LOCKED]` (umbrella §6 ERD); nilai WAJIB cocok persis OJK code list. Field `ojk_*` lain tunduk constraint sama. |
| audit + `version` | per konvensi §4 | NOT NULL | `created_by/on`, `last_updated_by/on` | [VERIFIED]×[INTENT] | |

#### 3.1.5 `trx_ar_card` + `trx_ar_card_penalty` + `trx_ar_card_social_fund` — kartu piutang (D-06)

- **Kelas**: `trx_`. **Disposisi**: **MIGRATE** (financial sums + `[LOCKED]` checksum wajib zero-diff; koordinasi cutover dgn servicing/collection — DATA-MIGRATION-PLAN §4.6).
- **Mapping asal**: `tr_ARCard` 23 kolom (`FC_ACQ_MCF 2.sql:7482-7543`), `Tr_ARCard_Penalty`, `Tr_Social_Fund` (`:9593-9617`) — keduanya anak baris `tr_ARCard` (komposit `Credit_id`+`ARCardID`+seq).
- **Kepemilikan**: AR Card ∈ bounded context ACQUISITION per flow.png (GT `:10-11`); **penulisan teknis = engine disbursement-subledger, dipicu & di-track 05** (§1.3). **Ownership jurnal + GL mapping = [OPEN] OQ-MEET-03** — tabel jurnal/subledger SENDIRI bukan target di PRD ini (§3.2.1).
- **Constraint**: `ux_trx_ar_card_credit_period` (`credit_id`,`period`,`period_sequence`) — kunci idempotensi generate (§3.3); FK `credit_id` → `trx_agreement.credit_id`.

`trx_ar_card` (asal `tr_ARCard`):

| Kolom target | Tipe target | Null | Asal legacy | Marker | Catatan |
|---|---|---|---|---|---|
| `id` | `BIGINT IDENTITY` PK | NOT NULL | `ARCardId` numeric identity | [VERIFIED]×[ARTIFACT→PK teknis] | Surrogate legacy digantikan `id`; anak me-refer `ar_card_id`. |
| `credit_id` | `VARCHAR(20)` FK | NOT NULL | `Credit_Id` | [VERIFIED]×**[LOCKED]** | |
| `branch_id` | `VARCHAR(5)` | NOT NULL | `BranchId` | [VERIFIED]×[INTENT] | |
| `period` | `INTEGER` | NOT NULL | `Period` | [VERIFIED]×**[LOCKED]** | Natural key bersama. |
| `period_sequence` | `INTEGER` | NOT NULL | `PeriodSecq` | [VERIFIED]×**[LOCKED]** makna | >1 billing event per periode (partial payment); typo "Secq" dinormalisasi (`[ARTIFACT]` nama). |
| `collection_date` | `DATE` | NOT NULL | `CollectionDate` | [VERIFIED]×**[LOCKED]** | |
| `first_installment` / `installment` / `principal` / `interest` | `NUMERIC(18,2)` | NOT NULL | idem numeric(18,0) | [VERIFIED]×**[LOCKED]** | Whole-rupiah → `.00`; rekonsiliasi bulat. |
| `ost_ar` / `ost_op` / `ost_uli` | `NUMERIC(18,2)` | NOT NULL | `OstAR`/`OstOP`/`OStULI` | [VERIFIED]×[OPEN→treat LOCKED] | Abbrev. tak ter-ekspansi (OQ-CORE-03); harus rekonsiliasi GL → perlakukan `[LOCKED]`. |
| `collection_id` | `BIGINT` | NULL | `CollectionId` | [VERIFIED]×[INTENT] | |
| `pay_date` / `pay_amount` | `DATE` / `NUMERIC(18,2)` | NULL | `PayDate`/`PayAmt` | [VERIFIED]×**[LOCKED]** | Event pembayaran aktual. |
| `is_ar` | `BOOLEAN` | NOT NULL | `IsAR` varchar(1) | [VERIFIED]×[INTENT] | char→boolean (konvensi §3); nilai legacy divalidasi saat migrasi [OPEN]. |
| `ar_voucher_no` / `ar_pay_date` | `VARCHAR(13)` / `DATE` | NULL | `ARVoucherNo`/`ARPayDate` | [VERIFIED]×**[LOCKED]** | Link posting voucher GL/AR. |
| audit | per konvensi §4 | NOT NULL | `UsrCrt`/`DtmCrt`/`UsrUpd`/`DtmUpd` | [VERIFIED]×[INTENT] | |

`trx_ar_card_penalty` (asal `Tr_ARCard_Penalty`): `id` PK; `ar_card_id` FK → `trx_ar_card.id` (asal `ARCardID`); `credit_id` `VARCHAR(20)` NOT NULL (asal `Credit_id` **varchar(100)** — anomali lebar `[ARTIFACT]`, dinormalisasi ke 20 + validasi migrasi); `penalty_sequence` (asal `Penalty_Secq`); `penalty_amount`/`pay_amount`/`discount_amount` `NUMERIC(18,2)` (asal decimal(10,2)) [VERIFIED]×**[LOCKED]** — denda + diskon diskresioner, cap denda regulator-limited; `collection_id`; `pay_date` `DATE`; audit §4. Unique (`ar_card_id`,`penalty_sequence`).

`trx_ar_card_social_fund` (asal `Tr_Social_Fund`): `id` PK; `ar_card_id` FK; `credit_id` (asal varchar(100) — idem normalisasi); `social_fund_sequence` (asal `Social_Fund_Secq`); `social_fund_amount`/`pay_amount` `NUMERIC(18,2)` (asal numeric(18,0)) [VERIFIED]×**[LOCKED]** — potongan social fund per periode (produk syariah/CF, `32-disbursement BR-DISB-10`); `collection_id`; `pay_date`; audit §4. Applicability per produk = OQ-MEET-06 (D-07).

#### 3.1.6 `trx_amortization` — jadwal angsuran (legacy `tr_amortization`, 14 kolom)

- **Kelas**: `trx_`. **Disposisi**: **MIGRATE**. **Mapping asal**: `tr_amortization` (`FC_ACQ_MCF 2.sql:7367-7394`; PK `BranchId`+`Credit_Id`+`Period`).
- Jadwal TEORETIS (vs `trx_ar_card` = aktual). Formula anuitas/rounding = `[LOCKED]` BR-DISB-6 (`32-disbursement`) — perubahan metode amortisasi adalah perubahan finansial/regulatori, bukan pilihan teknis.

| Kolom target | Tipe target | Null | Asal legacy | Marker | Catatan |
|---|---|---|---|---|---|
| `id` | `BIGINT IDENTITY` PK | NOT NULL | — | — | |
| `credit_id` | `VARCHAR(20)` FK | NOT NULL | `Credit_Id` | [VERIFIED]×**[LOCKED]** | `ux_trx_amortization_credit_period` (`credit_id`,`period`) — idempotensi generate (§3.3). |
| `branch_id` | `VARCHAR(5)` | NOT NULL | `BranchId` (bagian PK legacy) | [VERIFIED]×[INTENT] | Keluar dari key (derivable dari agreement). |
| `period` | `INTEGER` | NOT NULL | `Period` | [VERIFIED]×**[LOCKED]** | |
| `collection_date` | `DATE` | NOT NULL | `CollectionDate` | [VERIFIED]×**[LOCKED]** | Due date terjadwal. |
| `installment` / `principal` / `interest` | `NUMERIC(18,2)` | NOT NULL | idem numeric(21,2) | [VERIFIED]×**[LOCKED]** | Hasil formula anuitas BR-DISB-6. |
| `ost_ar` / `ost_op` / `ost_uli` | `NUMERIC(18,2)` | NOT NULL | `OstAR`/`OstOP`/`OStULI` numeric(18,0) | [VERIFIED]×[OPEN→treat LOCKED] | OQ-CORE-03. |
| audit | per konvensi §4 | NOT NULL | `UsrCrt`/`DtmCrt`/`UsrUpd`/`DtmUpd` | [VERIFIED]×[INTENT] | |

#### 3.1.7 `Tr_OverDue` (30 kolom) → read-model DERIVED — tanpa tabel dual-write

- **Disposisi**: **MIGRATE-READONLY** (snapshot historis → arsip servicing); **TIDAK dibangun tabel operasional baru** di acquisition.
- **Mapping asal**: `Tr_OverDue` (`FC_ACQ_MCF 2.sql:9249-9290`; PK `Credit_Id`). Legacy `sp_approve_npp` men-seed baris saat aktivasi **tanpa reader lokal** (Edge Case 11 keluarga; `24-npp §6`).
- **Census ringkas** (semua [VERIFIED]): key `Credit_Id`; aging bucket `ODH`,`ODAmt`,`OD14/30/60/90/180`,`ODM180` — batas bucket 14/30/60/90/180 = konvensi regulatori NPL **[LOCKED]** (pelaporan delinquency); `OstAR/OstOP/OstULI` [OPEN→treat LOCKED] (OQ-CORE-03); `MinPayment`,`NewPenalty`,`OldPenalty`,`DecreaseOD`,`AmtODDecreased` **[LOCKED]**; 9 kolom triple `Current/Next/Latest` (`CollDate`/`Arrears`/`Installment`) [INTENT] snapshot rolling; audit 4 kolom.
- **Keputusan rebuild**: `Tr_OverDue` = rollup derived dari AR Card (indikasi kuat; OQ-CORE-12) → target **menghitung** (query/materialized view di servicing/collection), BUKAN dual-write saat aktivasi. Seed-at-activation legacy TIDAK direplikasi; outcome "kontrak baru tampil dgn arrears 0" dipenuhi derivasi. `[INFERRED]`×[INTENT]; konfirmasi = OQ-CORE-12/OQ-DISB-12.

#### 3.1.8 `out_event` — entitas `PASSNET_SYNC` (legacy `tr_synchronize_to_passnet` + `tr_synchronize_to_passnet_BPKB`) — REBUILD

- **Kelas**: `out_` (transactional outbox — skeleton **ADR-04**; dispatcher-only consumer, konvensi §1). **Disposisi**: **REBUILD** — outbox mulai KOSONG; baris legacy TIDAK dimigrasi sebagai event. `passnet_id` per kontrak (identitas `[LOCKED]`) dibawa via `trx_agreement.passnet_id` (§3.1.1). Baris legacy `is_sync='0'` yang masih pending saat cutover = keputusan rekonsiliasi manual (JANGAN auto-replay) — register bersama OQ-PASSNET-01.
- **Mapping asal**: `tr_synchronize_to_passnet` (9 kolom; PK `credit_id`; writer `sp_approve_npp:467-483`; **tanpa drainer/write-back ditemukan** — fire-and-forget, `passnet §2a/§6`) dan `tr_synchronize_to_passnet_BPKB` (9 kolom; PK `credit_id`; hanya reset-to-pending dari `BPKBRepositoryEF.cs:763-779`; **seed INSERT + completion tidak ditemukan** — OQ-PASSNET-03). Emitter event BPKB = modul **collateral** (bukan 05); schema `out_event` shared infra.

| Kolom target | Tipe target | Null | Asal legacy | Marker | Catatan |
|---|---|---|---|---|---|
| `id` | `BIGINT IDENTITY` PK | NOT NULL | — | — | Identity/sequence (larangan §6.5). |
| `aggregate_type` / `aggregate_id` | `VARCHAR(30)` / `VARCHAR(40)` | NOT NULL | `credit_id` | [VERIFIED]×[INTENT] | Utk NPP: `AGREEMENT` + `credit_id`. |
| `event_type` | `VARCHAR(40)` | NOT NULL | — (baru) | [KEPUTUSAN DESAIN BARU] | `PassnetRegistration` (BR-NPP-14), `AgreementActivated` (BR-NPP-N6), `JournalRequested` (§3.3), `BpkbSyncReset` (emitter collateral), dst. |
| `payload` | `JSONB` | NOT NULL | `company_id`,`branch_id`,`credit_id`,`passnet_id` | [VERIFIED]×**[LOCKED]** isi Passnet | Payload PassnetRegistration memuat `passnet_id` format verbatim `'5'`+9-digit (`sp_get_passnet_id.sql:14`) — nilai = "NppNo" yang di-parse downstream. |
| `idempotency_key` | `VARCHAR(80)` | NOT NULL, unique | — (baru) | **[KEPUTUSAN DESAIN BARU — ADR-04]** | Deterministik per flow (§3.3); consumer idempotent. |
| `status` | `VARCHAR(12)` + CHECK `pending\|sent\|failed\|dead_letter` | NOT NULL | `is_sync` varchar(15) `'0'`/`'1'` | [VERIFIED]×[KEPUTUSAN DESAIN BARU] | Legacy tanpa writer sync → outbox + reconciliation (BR-NPP-N7); retry + dead-letter. |
| `sent_at` / `last_error` / `retry_count` | `TIMESTAMPTZ` / `TEXT` / `INTEGER` | NULL/NULL/NOT NULL | `sync_date` / `return_message` | [VERIFIED]×[INTENT] | Diisi dispatcher/reconciliation (ack/write-back). |
| `created_at` / `created_by` | per konvensi §4 (append-only) | NOT NULL | `created_by`/`created_on` | [VERIFIED]×[INTENT] | |

#### 3.1.9 `trx_fiducia_registration` (legacy `tr_upload_fidusia`, 13 kolom — heap tanpa PK)

- **Kelas**: `trx_`. **Disposisi**: **MIGRATE**. **Penempatan runtime**: record-keeping fidusia = **collateral-bpkb-fidusia post-acquisition** (§1.2 — BUKAN API pemerintah live); census di-anchor di PRD ini karena data legacy ter-link ke NPP via join `tr_upload_fidusia.nppno = tr_synchronize_to_passnet.passnet_id` (`sp_get_pagination_upload_fidusia`). Koordinasi schema final dgn PRD collateral.
- **Mapping asal**: `tr_upload_fidusia` (`FC_ACQ_MCF 2.sql:9669-9689`). Saat migrasi: `nppno` di-resolve → `agreement_id` via `passnet_id`; baris tanpa match = reject + register (DATA-MIGRATION-PLAN §3).

| Kolom target | Tipe target | Null | Asal legacy | Marker | Catatan |
|---|---|---|---|---|---|
| `id` | `BIGINT IDENTITY` PK | NOT NULL | — (legacy tanpa PK — `[ARTIFACT]`) | — | |
| `agreement_id` | `BIGINT` FK → `trx_agreement` | NOT NULL | `nppno` varchar(10) (= `passnet_id`) | [VERIFIED]×[INTENT] | Declared FK menggantikan join string. |
| `fiducia_grantor` | `VARCHAR(150)` | NULL | `pemberi_fidusia` | [VERIFIED]×**[LOCKED]** | Pihak pemberi fidusia (dokumen legal). |
| `certificate_no` / `certificate_date` | `VARCHAR(50)` / `DATE` | NULL | `nomor_sertifikat` / `tanggal_sertifikat` | [VERIFIED]×**[LOCKED]** | Nomor/tanggal sertifikat fidusia (Kemenkumham). |
| `deed_no` / `deed_date` | `VARCHAR(50)` / `DATE` | NULL | `nomor_akta` / `tanggal_akta` | [VERIFIED]×**[LOCKED]** | Akta notaris. |
| `notary_name` | `VARCHAR(150)` | NULL | `nama_lengkap_notaris` | [VERIFIED]×**[LOCKED]** | |
| — (tidak dibawa) | — | — | `branch_name` varchar(200), `nama_debitur` varchar(150) | [VERIFIED]×[ARTIFACT] | Denormalisasi (larangan §6.3) → derive via `agreement_id` → branch/customer. |
| audit | per konvensi §4 | NOT NULL | `created_by/on`, `last_updated_by/on` | [VERIFIED]×[INTENT] | |

#### 3.1.10 `tr_batching_trans` (10 kolom) — [OPEN — OQ-NPP-09]

- **Mapping asal**: `tr_batching_trans` (PK `credit_id`; kolom: audit 2, `company_id`, `branch_id`, `credit_id`, `dealer_code`, `BAST_date`, `tenor`, `st`, `cancel_id`). Writer tunggal `sp_approve_npp:272-276` (`st=1, cancel_id=0`) — **tanpa reader lokal** di seluruh SP corpus + kode .NET (`24-npp` Edge Case 11) `[VERIFIED]`.
- **Disposisi**: **[OPEN — OQ-NPP-09]** — default **DISCARD + register** (retensi arsip = OQ-MIG-02); TIDAK dibangun tabel padanan. Outcome "sinyal batch post-aktivasi" utk consumer eksternal (bila terbukti ada) sudah tercakup event `AgreementActivated` di `out_event` (PULL/subscribe, BR-NPP-N6) — bila consumer nyata terkonfirmasi, yang didokumentasikan adalah **kontrak PULL**-nya, bukan tabel push baru.

#### 3.1.11 `tr_GFCIT_AccountMaster` (7 kolom) — [ARTIFACT — discard]

- **Mapping asal**: `tr_GFCIT_AccountMaster` (`FC_ACQ_MCF 2.sql:8952-8968`; `CashInTransitID` PK, `BranchID`, `GLAccountNo`, audit `Usr/Dtm`). **0 referensi** di 473 SP + kode .NET (gap-entities §2.14) `[VERIFIED: 0 references]`.
- **Disposisi**: **DISCARD** `[ARTIFACT: dead/vestigial]` — masuk register discard dgn konfirmasi stakeholder. **Guard**: dump ber-scope acquisition — pemakaian modul GL/finance lain belum tersingkirkan = **[OPEN — OQ-GAP-09]**; JANGAN drop final sebelum OQ tertutup. Fungsi mapping GL-CIT hidupnya di keluarga subledger downstream (§3.2.1), bukan di acquisition.

#### 3.1.12 Entitas target tanpa padanan tabel legacy langsung

**Maker→checker NPP** (= `NPP_APPROVAL_STEP` + `NPP_APPROVAL_HISTORY`): runtime = **Flowable `ACT_*`** (konvensi §8 — dikelola engine, payload tetap di `trx_`); keputusan/riwayat WAJIB juga ditulis ke **`log_approval_history`** milik kita (append-only, audit regulatori independen dari engine). Mapping asal: rantai hierarki 2-baris legacy (tabel hierarchy milik modul 03; pembacaan `tr_hierarchy_approval_transaction` utk history NPP = OQ-NPP-13); in-flight legacy TIDAK dimigrasi ke Flowable (DATA-MIGRATION-PLAN §4.4/§5). Field: `agreement_id`/`credit_id`, `level` [INTENT] (kedalaman by skala risiko D-10; OQ-NPP-06), `actor`/`actor_role` (checker = `KEPALA_CABANG`), `action` enum `pending|approved|rejected|correction` **[LOCKED]** (sejajar `APPROVAL_STEP.action` kanonik), `reason_id`/`reason_desc` (wajib bila reject/correction), `acted_at`. Field legacy `is_super_user_self_approve` **DIHAPUS** (D-09 `[LOCKED]`) — tidak ada jalur bypass; audit no-self-approval dari `actor` vs submitter.

**`trx_master_loan`** (= `MASTER_LOAN`, D-05 — BARU): Tier [INTENT] outcome wajib + **[OPEN]** field census & ownership final = **OQ-MEET-02** (flow.png menempatkan Master Loan ∈ ACQUISITION — GT `:10-11`). **Disposisi**: **REBUILD** — tidak ada tabel legacy padanan tunggal; utk kontrak aktif dimigrasi, record di-derive dari `tr_CM` (frozen OP/ULI) + `tr_NPP` + jadwal amortisasi. Field USULAN: `id` PK, `agreement_id` FK 1:1, `credit_id`, `customer_id` FK → `mst_customer`, `principal` (OP) / `unearned_income` (ULI) / `tenor` / `effective_rate` `NUMERIC(18,2)`/`NUMERIC(9,6)` (snapshot frozen Credit Memo — GT STEP 12), `installment_amount` / `first_due_date` (dari amortisasi BR-DISB-6 `[LOCKED]`), `status` [OPEN] (lifecycle servicing di luar scope acquisition), audit §4.

**`out_notification`** (= `EMAIL_OUTBOX`, D-03 — BARU): kelas `out_`; **REBUILD** (tidak ada padanan legacy di jalur NPP; pola dedup referensi `tr_mail_notif_send_log` — `email-sms §9`). Field: `id` PK, `agreement_id`, `dealer_id`, `recipient` [INTENT] (dari `ms_dealer.email`), `template_code` [OPEN — OQ-MEET-01], `status` `pending|sent|failed` [KEPUTUSAN DESAIN BARU], unique (`agreement_id`,`template_code`) = dedup, `sent_at`/`failure_reason`, `created_at`/`created_by`. Enqueue in-transaction; kirim async worker (BR-NPP-N14).

### 3.2 Downstream-boundary — kontrak PULL, TANPA tabel target acquisition

> **12 tabel berikut BUKAN milik modul ini.** PRD ini TIDAK mendefinisikan tabel target untuk mereka — schema targetnya hidup di PRD modul pemiliknya (11 di luar acquisition; pengecualian `GFTransactionTypeGLLink` → target `map_transaction_type_gl` milik modul **07-master-data**, lihat §3.2.1). Yang didokumentasikan di sini hanyalah **kontrak boundary**: apa yang 05 emit/baca, dan invariant yang wajib dipegang kedua sisi. (Konvensi §2: tidak ada JOIN lintas modul di write path — ADR-03; referensi lintas modul via business key `credit_id`.)

#### 3.2.1 Keluarga subledger/GL (6 tabel) — milik **disbursement-subledger** (`32-disbursement`)

`CFCBSubLedger` (bank sub-ledger per `BankAccountID`), `CFCITSubLedger` (cash-in-transit per `CashInTransitID`), `CFContractSubLedger` (leg per kontrak `CreditId`), `CFInterCompanyBranchSubLedger` (antar company/branch), `CFSupplierSubLedger` (leg dealer/supplier — catat kolom `AgreementNo` varchar(25)), `GFTransactionTypeGLLink` (config mapping `TrxId`+`ClassId` → `GLAccountNo`; heap tanpa PK). **Catatan ownership `GFTransactionTypeGLLink`**: tabel targetnya = **`map_transaction_type_gl` milik modul 07-master-data** (registry BE-00 §6.3; census & admin surface = BE-07 §3.0; data milik finance — OQ-MEET-03) — 05 dan engine subledger hanya membacanya read-only.

- **Bentuk kontrak dari sisi 05**: aktivasi STEP 15 meng-emit **`JournalRequested`** (in-transaction, `out_event`, idempotency key deterministik — §3.3); engine subledger yang MENULIS legs jurnal ke keluarga tabel ini (atau tabel target penggantinya per PRD disbursement) dengan aturan `[LOCKED]` `32-disbursement`: balance zero-sum (BR-DISB-1), GL-class lookup, crosswalk bank-ID verbatim (BR-DISB-5). **05 TIDAK pernah menulis / membuat tabel target utk keluarga ini.**
- **Fail-closed**: hasil posting WAJIB kembali ke 05 (sync return pada Opsi A / ack event pada Opsi B) — kontrak TIDAK boleh final-`active` tanpa buku (BR-NPP-N12, AC-12); DILARANG mengulang Edge Case 1 `32-disbursement` (return code `spSubledgerBooking` dibuang caller).
- **Kolom kunci kontrak korelasi** (basis rekonsiliasi lintas-boundary): `TrxSource`, `TrxReferenceNo1/2`, `VoucherNo`, `GLAccountNo`, `CreditId`/`AgreementNo`, flag `is_sync` (sinkron GL hilir). `[VERIFIED]` dari DDL; semantik penuh + chart-of-accounts = **[OPEN — OQ-MEET-03]** (finance stakeholder).
- **Disposisi migrasi**: milik rencana migrasi modul disbursement-subledger (bukan PRD ini); acquisition hanya menjamin `credit_id`/`agreement_no`/`passnet_id` stabil sebagai kunci korelasi.

#### 3.2.2 Keluarga BPKB custody (6 tabel) — milik **collateral-bpkb-fidusia** (STEP 16 PULL)

`tr_BPKB` (46 kolom — custody in/out, lokasi, print SK, **`BAST_date`/`BAST_by` sendiri**), `tr_BPKB_CMO` (data pemilik BPKB per kontrak), `tr_BPKB_loan` + `tr_BPKB_loan_log` (peminjaman BPKB keluar), `tr_bpkb_bast_log` (log BAST custody), `storage_location` (master lokasi penyimpanan).

- **Bentuk kontrak dari sisi 05**: downstream BPKB **PULL** candidate-queue pasca-aktivasi — predikat eligibility legacy memakai `verification_status` (kondisi `agreement_status='A'` di-comment — `sp_get_pagination_BPKB_Lookup:106-124`; intensionalitas = **[OPEN — OQ-NPP-04/OQ-COLL-01]**). 05 meng-emit `AgreementActivated`; **TIDAK menulis satu pun tabel keluarga ini** (BR-NPP-N6; GT `:72-73`).
- **Boundary BAST — eksplisit**: BAST **serah-terima kendaraan saat NPP** (`trx_agreement.bast_no`/`bast_date`, hard-gate D-01 S14) **MILIK modul ini**; BAST **custody dokumen BPKB** (`tr_BPKB.BAST_date`/`BAST_by`, `tr_bpkb_bast_log`) **BUKAN** — dua record BAST terpisah tanpa rekonsiliasi di legacy (Edge Case 10). Mana otoritatif + wajib rekonsiliasi = **[OPEN — OQ-NPP-08]**; JANGAN merge diam-diam saat rebuild/migrasi.
- **Disposisi migrasi**: milik PRD modul collateral; catatan utk modul itu: `storage_location` (2 kolom, master polos) kandidat `mst_storage_location`; pola print-counter di `tr_BPKB` tunduk larangan konvensi §6.2 (→ `log_document_print` generik yang sama dgn §3.1.3).

### 3.3 Jurnal + AR Card + master loan — idempotency key + fail-closed pada schema (output atomik STEP 15)

Jurnal, AR Card, amortisasi, dan master loan = **output WAJIB penyelesaian STEP 15** (D-05/D-06; GT `:70`; BR-NPP-N12) — dieksekusi dalam **satu unit kerja transaksional** per sequence **`ARCHITECTURE-PROPOSAL.md §7`** (activate → jurnal+AR Card → master loan → PK record → upsert customer → outbox rows → COMMIT; error apa pun = ROLLBACK — kontras `sp_approve_npp:721-741` commit-on-error). Schema target MENEGASKAN kontrak ini secara struktural (ADR-04 baris "NPP activation" + "GL/jurnal posting"):

| Mekanik | Enforcement schema | Sumber |
|---|---|---|
| **Idempotency aktivasi** | `trx_agreement.activation_idempotency_key` unique + status-check in-transaction; replay → hasil pertama tanpa cascade ulang (AC-5). | BR-NPP-N4; ADR-04 |
| **Idempotency jurnal** | `out_event.idempotency_key` unique, deterministik `{credit_id}:JOURNAL_DISBURSEMENT:v1`; engine subledger konsumen idempotent + compensating reversal — **TIDAK PERNAH silent-commit**. | ADR-04 tabel flow; `32-disbursement` Edge Case 1/4 |
| **Idempotency AR Card / amortisasi** | `ux_trx_ar_card_credit_period` + `ux_trx_amortization_credit_period` — INSERT duplikat gagal keras di DB, bukan diam-diam dobel. | §3.1.5/§3.1.6 |
| **Idempotency master loan / customer / email** | unique `trx_master_loan.agreement_id` (1:1); `ux_mst_customer_identity_company` (upsert-by-key); unique `out_notification(agreement_id, template_code)` (dedup D-03). | §3.1.4/§3.1.12 |
| **Fail-closed** | Opsi A (§1.3): semua tabel §3.1 + `out_event` dalam SATU transaksi DB — COMMIT bersama atau ROLLBACK bersama. Opsi B: `active_pending_ledger` (flag, bukan state kanonik) + reconciliation wajib. Kontrak TIDAK boleh final-`active` tanpa buku. | D-01 S15; BR-NPP-N3/N12; AC-12; ARCHITECTURE-PROPOSAL §7 |
| **Ownership jurnal** | Tabel jurnal/subledger BUKAN target PRD ini (§3.2.1); GL mapping & posting rules = **[OPEN — OQ-MEET-03]**; master loan ownership = **[OPEN — OQ-MEET-02]**. | §1.3 |

### 3.4 Entitas dikonsumsi (referensi ke Shared Entities umbrella — TIDAK ditulis di sini)

| Entitas | Pemilik | Interaksi 05 | Marker |
|---|---|---|---|
| `PURCHASE_ORDER` (`po_number`) | 04-contract (STEP 13) | **Read** — trigger masuk NPP. | [INTENT] |
| `CREDIT_MEMO` (`trans_type_id`, OP/ULI/LCR frozen) | 04-contract (STEP 12 lock) | **Read** — branch/dealer/tenor/rate/asset; nilai OP/ULI/LCR + asuransi jiwa/kendaraan sudah **dikunci** saat committee approve (GT `:52-53`). | `trans_type_id` [LOCKED] |
| `ASSET` (`chassis_no`, `engine_no`) | 01-intake (capture) | **Validasi final** hard-gate STEP 15. | chassis/engine **[LOCKED]** unik |
| `VERIFICATION` / `TrVerificationCustomer` (`status`, `freshness_at`) | modul Vertel (STEP 14, D-02) | **Read** — gate `verified` + freshness **30-hari strict** (D-01 Step 14). | [INTENT]; freshness hard-gate; konsekuensi expiry = OQ-MEET-05 |
| `DISBURSEMENT` / `SUBLEDGER_ENTRY` (keluarga `CF*SubLedger` — §3.2.1) | disbursement-subledger engine | **Dipicu** aktivasi via event `JournalRequested` (outcome wajib D-06; idempotency + fail-closed §3.3); aturan posting `[LOCKED]` di `32-disbursement`. AR Card & amortisasi sendiri = owned §3.1.5/§3.1.6. | GL crosswalk `[LOCKED]`; OQ-MEET-03 |
| `BPKB` (`custody_status`) | collateral (STEP 16 PULL) | **Downstream** — di-PULL. | `custody_status` [LOCKED] |
| `INSURANCE` (Cover/Claim/Billing/Refund) | INSURANCE context (STEP 16 PULL — BARU v2) | **Downstream** — di-PULL/subscribe. | [OPEN] kontrak eligibility |

---

## 4. API Endpoint

> Kontrak level **resource + field**. Bahasa BE = **Java** (D-12 `[LOCKED]`); framework **[OPEN]** (rekomendasi Spring Boot — USULAN); transport REST diasumsikan (konvensi final menunggu arsitektur ITEC, D-11). Auth/role di-enforce **application layer** (no self-approval — D-01 Step 11; role per D-10).

| Method | Path | Deskripsi | Auth/Role |
|---|---|---|---|
| `POST` | `/npp` | Buat draft NPP dari `PURCHASE_ORDER` yang sudah issued **dan Vertel approved (STEP 14)** (asset-type = data, bukan endpoint terpisah — BR-NPP-N9). | Admin Cabang / Credit (Admin) |
| `GET` | `/npp` | List/pagination NPP (filter status/branch). Status label kanonik tunggal (perbaiki drift Edge Case 12). | Admin, Kepala Cabang |
| `GET` | `/npp/{id}` | Detail satu NPP. | Admin, Kepala Cabang |
| `PUT` | `/npp/{id}` | Update data draft NPP (guard: status ∈ {`pending`, `held(correction)`} — BR-NPP-5). | Admin Cabang / Credit (Admin) |
| `POST` | `/npp/{id}/submit` | Submit **RFA NPP** (maker): jalankan validasi chassis/engine + pre-flight gate; bangun rantai checker (Kepala Cabang); `pending → validated`. | Admin Cabang / Credit (Admin) |
| `GET` | `/npp/{id}/preflight` | Pre-check advisory sebelum approve (verification+30-hari, chassis, BAST, due-date ≥ approve-date). Read-only — pengganti `ValidateNppApprove` legacy (FE memanggilnya sebelum Approve — `65-npp-vertel §5.9`). | Kepala Cabang |
| `POST` | `/npp/{id}/decision` | Rekam keputusan checker: `approve` / `reject` / `correction`. `approve` → aktivasi **atomik in-transaction** (gate re-enforce, cascade internal, emit event). **Idempotency-Key wajib.** | Kepala Cabang |
| `GET` | `/npp/{id}/agreement` | Dataset cetak Perjanjian Pembiayaan / PK (on-demand; tersedia pasca-aktivasi — D-04). | Admin, Kepala Cabang |
| `GET` | `/npp/{id}/documents/{docType}` | Dokumen legal pendamping: `approval-letter`, `statement-letter`, `power-of-attorney-fiducia`, `power-of-attorney-withdrawal`, `important-notice`, `mou` (set dari `NPPReportsController.cs` + print-menu FE `65-npp-vertel §5.11`; gate: status `active` saja). | Admin, Kepala Cabang |
| `GET` | `/npp/{id}/history` | Audit trail maker-checker (`NPP_APPROVAL_HISTORY`). | Admin, Kepala Cabang, Audit |
| `GET` | `/npp/lookups/credits` | Lookup record acquisition eligible masuk NPP (pengganti credit-lookup popup FE — `NppProcessCreditPartialView`). | Admin Cabang |
| `GET` | `/npp/lookups/dealer-bank-references?dealer_id=` | Lookup rekening bank dealer (pengganti `DealerBankRefPartialView`). | Admin Cabang |
| `GET` | `/npp/validations/chassis?credit_id=&chassis_no=&engine_no=` | Advisory chassis/engine check saat data-entry (pengganti `CheckChasisCode` FE; hard check tetap di submit+approve). | Admin Cabang |
| `GET` | `/npp/validations/installment-date?credit_id=&bill_received_date=` | Kalkulasi/lock installment date per billing cycle (pengganti `CheckBillingReceivedDate` FE; BR-NPP-11). | Admin Cabang |
| `GET` | `/npp/validations/asset-check?credit_id=` | Advisory external asset-identity check utk car non-top-up (pengganti `CheckRapindo`+`CheckProductMarketing` FE — Edge Case 3 FE; kelanjutan integrasi = OQ-NPPVTL-04). | Admin Cabang |

**Header lintas-endpoint**: `Idempotency-Key` wajib pada `POST /npp/{id}/decision` (aktivasi memindahkan state legal/eksternal — overview §7.4). Error envelope seragam `{ code, message, details?, correlation_id }` (overview §7.3).

> **Editability `validated` vs RFA legacy:** Guard `PUT /npp/{id}` di atas **tidak** mengizinkan edit saat `validated`, sedangkan legacy BR-NPP-5 mengizinkan edit saat status RFA (legacy `0`). Apakah RFA-editability dipertahankan atau sengaja di-drop di `validated` = **keputusan stakeholder** — jangan diputus diam-diam; kaitkan ke **OQ-CMPO-01** ("arti status `0`"; catatan v2: STEP 8 menyebut draft kontrak auto-created "Status RFA = '0'" — GT `:28`). Bila dipertahankan, §7 perlu transisi `validated → PUT → validated`.
> **Draft-path NPP:** Web FE legacy hanya punya jalur RFA-only (Edge Case 9 FE; BR-NPPVTL-20) — Draft NPP tidak reachable dari FINCORE.WEB. Rebuild menyediakan `pending` (draft) sebagai state sah via `POST /npp` + `PUT`; keputusan apakah FE baru memakai draft = OQ-NPPVTL-09 (tidak memblokir kontrak BE ini).

---

## 5. Kontrak Request/Response

### 5.1 `POST /npp` — buat draft (maker, stage S1)

Request (wajib: `po_id`, `bast_no`, `bast_date`, `chassis_no`, `engine_no`, `bank_reference_id`, `installment_date`; opsional lainnya):
```json
{
  "po_id": "PO-2026-000123",
  "bast_no": "BAST/2026/07/0456",
  "bast_date": "2026-07-05",
  "chassis_no": "MH1JBK21XLK123456",
  "engine_no": "JBK2E1234567",
  "item_color": "Hitam",
  "bill_received_date": "2026-07-05",
  "bill_receipt_date": "2026-07-05",
  "down_payment_receipt_no": "DP-000789",
  "bpkb_letter_no": null,
  "bank_reference_id": "DLR-BANK-0007",
  "installment_date": "2026-08-05",
  "insurance_coverage_period": 12,
  "dealer_order_source_tac": [],
  "dealer_order_source_third_party": []
}
```
Response `201 Created`:
```json
{ "id": "NPP-2026-000123", "status": "pending", "agreement_no": null, "bast_validated": false }
```
Error: `409` bila PO belum `issued` / sudah punya NPP aktif / **Vertel belum approved (STEP 14)** (`{code:"VERTEL_NOT_APPROVED"}`); `422` field wajib kosong (`{code:"VALIDATION_ERROR", details:[...]}`).

### 5.2 `POST /npp/{id}/submit` — submit RFA (maker → checker)

Menjalankan validasi chassis/engine (`sp_validation_chasis_number` — BR-NPP-1) + pre-flight gate. Request body kosong / minimal (`{ "acting_employee": "EMP-014" }`).

Response `200 OK` (lolos → `validated`):
```json
{ "id": "NPP-2026-000123", "status": "validated",
  "checker": { "level": 2, "assignee_role": "KEPALA_CABANG", "action": "pending" } }
```
Response `422 Unprocessable` (chassis/engine mismatch — gate STEP 15):
```json
{ "code": "CHASSIS_ENGINE_MISMATCH",
  "message": "Nomor rangka/mesin tidak cocok dengan Credit Memo",
  "details": { "chassis_no": "mismatch", "expected_item_year": "L", "found": "K" },
  "correlation_id": "..." }
```

### 5.3 `POST /npp/{id}/decision` — keputusan checker (aktivasi in-transaction)

Request (wajib: `action`; `reason_id` wajib bila `reject`/`correction`; header `Idempotency-Key`):
```json
{ "action": "approve", "acting_employee": "EMP-020", "reason_id": null, "reason_desc": null }
```

**Happy-path** `200 OK` (aktivasi sukses, atomik — D-01 Step 15):
```json
{
  "id": "NPP-2026-000123",
  "status": "active",
  "agreement_no": "PP/MCF/2026/07/000123",
  "passnet_id": "5000000123",
  "customer": { "id": "CIF-0099", "national_id_matched": true },
  "master_loan_id": "ML-2026-000123",
  "ledger": { "journal_posted": true, "ar_card_generated": true, "amortization_generated": true },
  "pk_document": { "available": true },
  "dealer_email": { "status": "pending" },
  "activated_at": "2026-07-14T04:12:00Z",
  "event_emitted": "AgreementActivated"
}
```
> Field `master_loan_id` / `ledger.*` mengikuti keputusan Opsi A/B §1.3: pada Opsi B nilainya dapat berstatus `pending` dengan status kontrak `active_pending_ledger` — kontrak response final dikunci setelah D-11/OQ-MEET-02/03.

**Gate gagal — verification** `403 Forbidden` (block + **rollback**, tidak ada side-effect):
```json
{ "code": "VERIFICATION_GATE_FAILED",
  "message": "Verifikasi customer (Vertel) belum 'verified' atau kadaluarsa (>30 hari)",
  "details": { "verification_status": "recheck", "freshness_days": 41 },
  "correlation_id": "..." }
```

**Gate gagal — BAST** `422` (`{code:"BAST_INCOMPLETE"}`, `bast_no`/`bast_date` null — BR-NPP-N2, D-01 Step 14).

**Gate gagal — due-date** `422` (`{code:"DUE_DATE_BEFORE_APPROVAL"}` — BR-NPP-7, kini di-enforce di mutasi).

**Reject / Correction** `200 OK` (hanya update status header, **tanpa** cascade — BR-NPP-17):
```json
{ "id": "NPP-2026-000123", "status": "held", "disposition": "correction", "reason_id": "RSN-07" }
```

**Self-approval** `403 Forbidden` (`{code:"SELF_APPROVAL_BLOCKED"}` — D-01 Step 11; tanpa jalur super-user, D-09).

**Idempotent replay** (Idempotency-Key sama, sudah `active`) → `200 OK` mengembalikan hasil aktivasi pertama, **tanpa** menjalankan ulang cascade (BR-NPP-N4, perbaiki Edge Case 6).

---

## 6. Aturan Bisnis

> Kolom **Marker**: `[LOCKED]` = requirement keras (WAJIB dipertahankan). `[INTENT]` = desain target (jaga outcome). `[KEPUTUSAN DESAIN BARU]` = desain rebuild baru. `[ARTIFACT]` = akun legacy, dibuang setelah konfirmasi stakeholder. Aturan yang bersumber dari meeting menyebut **D-xx**.

### 6.1 Aturan legacy dipertahankan (regulasi / kontrak eksternal / outcome)

| ID | Aturan | Sumber | Marker | Catatan |
|---|---|---|---|---|
| BR-NPP-1 | Validasi nomor rangka (`chassis_no`) + mesin (`engine_no`) via `sp_validation_chasis_number` WAJIB sebelum NPP dapat diaktivasi (gate legality STEP 15). | GT `:67-68`; `24-npp §7 BR-NPP-1`; `sp_validation_chasis_number.sql` | **[LOCKED]** (identitas aset legal) | chassis/engine unik `[LOCKED]` (data-mutation-policy). |
| BR-NPP-2 | Motor (item `001`): cek chassis legacy membandingkan **year-code** 1-karakter vs item-year Credit Memo — bukan uniqueness. FE hanya memicu check di layar motor (Edge Case 5 FE). | `24-npp §7 BR-NPP-2`; `65-npp-vertel BR-NPPVTL-7` | [INTENT] | Coverage gap legacy → rebuild perluas ke uniqueness (BR-NPP-N5) **untuk BE dan FE**. |
| BR-NPP-3 | Car/top-up (`application_type 03`, non Mega-Solusi): cek chassis query kontrak ACTIVE dengan chassis sama → blokir bila ada. | `24-npp §7 BR-NPP-3` | [INTENT] | Anti double-finance; rebuild jadikan general (BR-NPP-N5). |
| BR-NPP-4 | Setelah aktif, Perjanjian Pembiayaan (PK) + dokumen legal pendamping tersedia cetak on-demand via endpoint reports; print action **bukan** bagian transaksi approval. D-04 menegaskan dokumen PK WAJIB terbentuk setelah STEP 15 selesai — dataset di-generate pasca-commit (non-blocking), print on-demand. | D-04; GT `:69`; `24-npp §7 BR-NPP-4`; `NPPReportsController.cs` | **[LOCKED]** (dokumen regulated) | `sp_approve_npp` TIDAK memanggil print; outcome D-04 dipenuhi via `pk_document_generated_at` + AC-1. |
| BR-NPP-5 | NPP hanya editable saat status ∈ {Draft, Correction, RFA}; Approved/Rejected mem-blokir edit. | `24-npp §7 BR-NPP-5`; `sp_validation_npp_status.sql:16` | [INTENT] | Map ke guard `pending`/`held(correction)`; listing FE meng-gate Edit/Print by status (BR-NPPVTL-10/18). |
| BR-NPP-8 | Rantai maker→checker: submitter (pre-decided) + checker. Legacy hardcode 2 langkah; target: kedalaman hierarki mengikuti **skala risiko** (D-10). | `24-npp §7 BR-NPP-8`; D-10 | [INTENT] | Kedalaman N-level configurable = OQ-NPP-06; gate "last pending step" wajib (BR — lihat §7). |
| BR-NPP-11 | Due-date angsuran di-snap ke day-of-month tetap (+offset) bila approve-date = trigger "bill received date"; FE meng-auto-calc & lock field (manual entry dibatasi window kecil). | `24-npp §7 BR-NPP-11`; `65-npp-vertel §5.4/BR-NPPVTL-4` | [INTENT] | Alignment siklus billing; kalkulasi di BE (endpoint validations/installment-date), FE hanya display. |
| BR-NPP-12 | Insurance Billing Periodical + Coverage Period di-overwrite ke nilai tetap saat aktivasi. | `24-npp §7 BR-NPP-12` | [INTENT] | Intent tak jelas → OQ-NPP-10 (jangan diselesaikan diam-diam). |
| BR-NPP-13 | GL booking + Repeat-Order subsidy dispatch by item-type (motor vs car); subsidy motor tergated flag repeat-order CAS. | `24-npp §7 BR-NPP-13`; `32-disbursement BR-DISB-2/8` | [INTENT] | Eksekusi = engine disbursement-subledger (§1.3); leg set & balance check `[LOCKED]` di `32-disbursement`. |
| BR-NPP-14 | Registrasi Passnet: `passnet_id` di-mint format `'5'`+9-digit zero-pad, link 1:1 ke credit/contract id. "All data synced to Passnet" = output STEP 15 (GT `:70`). | GT `:70`; `24-npp §7 BR-NPP-14`; `passnet §5` | **[LOCKED]** (format interop eksternal) | WAJIB verbatim. |
| BR-NPP-16 | Customer master (individu by `national_id`, korporat by `tax_id`) WAJIB ada & di-rekonsiliasi tiap aktivasi: nama di-update bila mismatch; record **kedua** (bukan merge) bila identitas sama di company berbeda. D-01 Step 15 mengonfirmasi "upserts customer master". | D-01 Step 15; `24-npp §7 BR-NPP-16` | **[LOCKED]** (field identitas) | Mekanik upsert `[INTENT]`; identitas `[LOCKED]`. |
| BR-NPP-17 | Reject/Correction hanya update status header — **tidak** ada side-effect cascade. | `24-npp §7 BR-NPP-17` | [INTENT] | Scoping side-effect ke aktivasi genuine. |
| BR-NPP-9 | ~~Super-user self-approve stamp~~ — **DIHAPUS**. Legacy men-stamp super-user sebagai approving authority-nya sendiri (`sp_approve_npp:22-30,53`). Rebuild **TIDAK BOLEH** memiliki role super-user maupun jalur self-approval. | **D-09 `[LOCKED]`**; D-01 Step 11; `24-npp §7 BR-NPP-9` | **[ARTIFACT — dihapus per keputusan meeting]** | Menutup OQ-NPP-07/OQ-MCP-01 sisi kebijakan; enforcement app-layer tetap wajib. |

### 6.2 Keputusan desain baru, keputusan meeting & perbaikan do-not-replicate

| ID | Aturan | Sumber | Marker | Catatan |
|---|---|---|---|---|
| BR-NPP-N1 | **Verification hard-gate in-transaction**: sebelum aktivasi, status verifikasi Vertel = `verified` **dan** freshness ≤ **30 hari strict**. Gagal → block `403` + **rollback** penuh. | **D-01 Step 14** ("consumer-verification status expires after a strict 30-day limit"); D-02; GT `:63-65` | **[INTENT — diputuskan meeting]** (sebelumnya [KEPUTUSAN DESAIN BARU]) | Konsekuensi expiry (auto-cancel vs re-verify) + titik mulai clock = **OQ-MEET-05**. 05 = eksekutor gate; Vertel = produsen status. |
| BR-NPP-N2 | **BAST hard-gate**: `bast_no` + `bast_date` WAJIB non-null & lengkap sebelum activate; gagal → block `422`. Set `bast_validated=true`. | **D-01 Step 14** ("verification hard-gate: BAST + chassis/engine validation"); GT `:66-67`; GOTCHA-15; `24-npp BR-NPP-21` | **[INTENT — diputuskan meeting]** | Meeting memutuskan hard-gate (menutup arah utama OQ-NPP-14); definisi "lengkap" (cukup no+tanggal vs perlu dokumen upload) = OQ-NPP-14 (narrowed, P2). FE guard legacy TIDAK mengecek BAST (Edge Case 2 FE) → FE baru wajib selaras. |
| BR-NPP-N3 | **Aktivasi atomik**: seluruh konsekuensi internal (aktivasi header, `tr_CIF` upsert, outbox Passnet, master-loan/jurnal/AR trigger, emit event) dalam **satu unit kerja transaksional**; error apa pun → **ROLLBACK penuh** (atau kompensasi saga penuh pada Opsi B §1.3). | **D-01 Step 15** ("contract activation is atomic"); Edge Case 7 (`sp_approve_npp:721-741` commit-on-error) | **[INTENT — diputuskan meeting]** | Ganti pola `IF XACT_STATE()=1 COMMIT` fail-soft. |
| BR-NPP-N4 | **Idempotent di batas mutasi**: guard re-aktivasi hidup **di dalam** transaksi aktivasi (via `Idempotency-Key` + status check), bukan hanya pre-check caller. Replay → hasil pertama, tanpa cascade ulang. | Edge Case 6 (guard di `sp_validation_npp_status`, bukan `sp_approve_npp`); `32-disbursement` Edge Case 3 | **[KEPUTUSAN DESAIN BARU]** | Cegah duplikasi amortisasi/AR/overdue/CIF/jurnal. |
| BR-NPP-N5 | **Uniqueness chassis/engine general**: cek duplikat chassis+engine terhadap semua kontrak aktif untuk **semua** lini (motor & car), satu tempat sentral di BE. | BR-NPP-2/3; ASSET `[LOCKED]` unik; Edge Case 5 FE (client check motor-only) | **[KEPUTUSAN DESAIN BARU]** | Legacy hanya cek narrow (motor year-code, car top-up saja). |
| BR-NPP-N6 | **Downstream STEP 16 = PULL/event, BUKAN push**: 05 emit `AgreementActivated`; **Dealer Payment, BPKB, Insurance** pull/subscribe — 05 tidak meng-eksekusi/menulis mereka. | **D-01 Step 15** ("downstream gets data via PULL, never push"); GT `:72-73`; GOTCHA-12 | **[INTENT — diputuskan meeting]** | Eligibility-query per consumer = kontrak terdokumentasi (OQ-NPP-03/04; Insurance [OPEN]). |
| BR-NPP-N7 | **Passnet via outbox + reconciliation**: outbox transaksional; consumer/ack menulis balik `out_event.status=sent/failed` + `last_error` (semantik legacy `is_sync`/`return_message` — §3.1.8); retry idempotent + dead-letter. Transport legacy = linked-server D2D (`[MACF-DBMCF].[EPMCF]`/`[MACF-DBMAF].[EPMAF]`) → ganti kontrak ACL. | GOTCHA-13; `passnet §2a/§4/§6`; Edge Case 2 | **[KEPUTUSAN DESAIN BARU]** | Legacy fire-and-forget tanpa write-back (OQ-PASSNET-01/02). |
| BR-NPP-N8 | **Due-date check di dalam mutasi**: aturan due-date ≥ approve-date (BR-NPP-7) di-enforce di dalam aktivasi, bukan hanya endpoint advisory (legacy: `sp_validate_npp_approve` UI-only; FE memanggil pre-check lalu approve — BR-NPPVTL-9). | Edge Case 9; `65-npp-vertel BR-NPPVTL-9` | **[KEPUTUSAN DESAIN BARU]** | Cegah bypass caller langsung. |
| BR-NPP-N9 | **Satu engine config-driven car/motor**: kolapskan pasangan endpoint/method motor vs car (`/save`,`/insert`,`/car/*`) → satu resource, asset-type sebagai data. | GOTCHA-10; `24-npp §5 step 1`; `65-npp-vertel §6` (4 endpoint legacy) | **[KEPUTUSAN DESAIN BARU]** | Aturan lini eksplisit & table-driven. |
| BR-NPP-N10 | **Jangan konflasi dua cek 30-hari**: freshness **verifikasi Vertel** (hard-gate D-01 S14, `VERIFICATION.freshness_at`) berbeda dari freshness **SLIK/FCL** credit-analysis (BR-NPP-6, advisory). | D-01 Step 14; `24-npp §5 step 2 / BR-NPP-6`; Edge Case 8 | **[KEPUTUSAN DESAIN BARU]** | Pisahkan; jangan campur. |
| BR-NPP-N11 | **FCL/SLIK freshness (BR-NPP-6) di-enforce sentral & konsisten** untuk semua jalur pembuatan NPP (legacy hanya di endpoint car plain insert/update). | Edge Case 8 (`sp_validation_npp_fcl_expiry` inkonsisten) | **[KEPUTUSAN DESAIN BARU]** | Satu titik enforcement; scope final = keputusan (advisory vs blocking — kaitkan OQ-REG-06). |
| BR-NPP-N12 | **Jurnal + AR Card + amortisasi + master loan = output WAJIB penyelesaian STEP 15** (D-05/D-06; GT `:70`): kontrak tidak boleh berstatus final-`active` tanpa output ini terbentuk (fail-closed / kompensasi — TIDAK boleh silent seperti Edge Case 1 `32-disbursement`). Eksekusi teknis di engine disbursement-subledger; orkestrasi & tracking oleh 05 (§1.3 Opsi A/B). | **D-05; D-06**; GT `:70`; `32-disbursement §1`, Edge Case 1 | **[INTENT — diputuskan meeting]** | Merevisi framing baseline "GL = downstream pull murni". GL mapping/posting rules = OQ-MEET-03; master loan census = OQ-MEET-02. |
| BR-NPP-N13 | **Cetak print action bukan bagian mutasi aktivasi** — dataset PK di-generate pasca-commit; print on-demand (BR-NPP-4), konform D-04. | BR-NPP-4; D-04 | [INTENT] | — |
| BR-NPP-N14 | **Email blast dealer pasca-aktivasi** (D-03): setelah STEP 15 selesai, sistem mengirim notifikasi email ke dealer terkait. Desain: enqueue `EMAIL_OUTBOX` di dalam transaksi aktivasi; pengiriman async oleh notification worker **app-tier Java** (BUKAN dari database — legacy `msdb.dbo.sp_send_dbmail` + `EXECUTE AS LOGIN='sa'` = `[ARTIFACT]` do-not-replicate). Dedup per (agreement, template); kegagalan kirim TIDAK membatalkan aktivasi (advisory, non-blocking). | **D-03**; `email-sms-notifications.md §4/§9` (pola dedup `tr_mail_notif_send_log`; do-not-replicate `sa`/hardcoded recipients) | **[INTENT — diputuskan meeting]** + [OPEN] detail | Trigger point (approve vs T+n batch), template, failure policy = **OQ-MEET-01**. Alamat dealer dari master (`ms_dealer.email`); dilarang fallback email personal hardcoded (Edge Case 2 `email-sms`). |
| BR-NPP-N15 | **Approver NPP = Kepala Cabang; self-approval diblokir; tanpa super-user**: keputusan `approve` hanya sah dari assigned checker ber-role `KEPALA_CABANG` yang ≠ submitter. | GT `:68-69`; **D-09; D-10; D-01 Step 11** | **[LOCKED]** (governance meeting) | Enforcement application layer + audit `NPP_APPROVAL_HISTORY`. |
| BR-NPP-20 | Field `DealerPaymentStatus`/`ArScheduleStatus` di kontrak lama = **vestigial** (tanpa backing column) → **dibuang**. | `24-npp §7 BR-NPP-20` | `[ARTIFACT]` | Konfirmasi stakeholder → ADR. |
| BR-NPP-DEAD | Blok Rapindo claim-asset (`sp_validation_skip_rapindo` registration cascade) = **dead code** (di-comment) → JANGAN dihidupkan as-is. **Catatan FE (BARU)**: gate *check* Rapindo di layar entry car non-top-up MASIH LIVE (`npp.js:161-166,1230-1270`) dan berbeda dari cascade registrasi yang mati — dua hal ini JANGAN dikonflasi (Edge Case 3 FE). Re-implement deliberate bila masih requirement. | Edge Case 1 (`24-npp`); `65-npp-vertel` Edge Case 3; OQ-NPPVTL-04 | `[ARTIFACT]` (registrasi) / [OPEN] (check) | Endpoint `validations/asset-check` di §4 mengabstraksi check live; kelanjutan integrasi = OQ-NPPVTL-04. |

---

## 7. State Machine

**Status kanonik `FINANCING_AGREEMENT.status`** (= `trx_agreement.status`, §3.1.1; overview §7.2): `pending` · `validated` · `active` · `held`.

**Mapping legacy → kanonik** (dokumentasikan saat migrasi):

| Legacy `agreement_status` | Kanonik | Catatan |
|---|---|---|
| (Draft, belum submit) | `pending` | Pre-submit; belum ada validasi chassis / rantai checker. Web FE legacy tidak pernah mencapainya (RFA-only, Edge Case 9 FE) — state tetap dimodelkan sah di BE. |
| `0` (RFA-locked: chassis-validated, rantai checker dibangun, menunggu keputusan approver) | `validated` | Submission NPP menulis `agreement_status='0'` (RFA) **setelah** validasi chassis + build checker chain (`24-npp §5 step 4`; state machine `24-npp §8`). Gate keras lolos di submit; re-enforce di aktivasi. Arti status `0` lintas-domain = OQ-CMPO-01 (v2: STEP 8 juga memakai "Status RFA = '0'" utk draft kontrak — GT `:28`). |
| `A` (Approved/Active) | `active` | Terminal happy-path; picu output STEP 15 + event + downstream STEP 16. |
| `C` (Correction) | `held` (disposition=`correction`) | Re-openable ke `pending`. |
| `R` (Reject/"Review"/"Reject" — label drift Edge Case 12; UI listing legacy menampilkan "Reject" — BR-NPPVTL-11) | `held` (disposition=`rejected`) | Terminal; label kanonik tunggal ("Rejected"). |
| `V` (Verify) | — | Tidak ada write-path ditemukan; **tidak dimodelkan** sebagai state reachable (OQ-NPP-05). |

> Enum kanonik meng-*collapse* correction & reject ke `held`; dibedakan oleh field `disposition`/`reason` (correction re-openable; rejected terminal). Ini konform umbrella, sambil jujur ke workflow.
> Bila Opsi B §1.3 dipilih, tambahkan sub-status internal `active_pending_ledger` (bukan state kanonik baru; representasi flag) — final setelah D-11.

### Tabel transisi

| Dari | Aksi | Ke | Guard / Prasyarat |
|---|---|---|---|
| — | `POST /npp` (dari PO issued) | `pending` | PO status `issued`; **Vertel STEP 14 approved** (D-02); belum ada NPP aktif utk credit_id. |
| `pending` | `PUT /npp/{id}` (edit) | `pending` | Status editable (BR-NPP-5). |
| `pending` | `POST /submit` (RFA) | `validated` | Chassis/engine valid (BR-NPP-1/N5); pre-flight gate lolos; bangun checker Kepala Cabang (BR-NPP-8/N15). |
| `pending` | `POST /submit` gagal chassis | `pending` | `422 CHASSIS_ENGINE_MISMATCH` — tetap `pending`. |
| `validated` | `POST /decision approve` | `active` | **Hanya pada langkah checker TERAKHIR** (tidak ada `NPP_APPROVAL_STEP` pending tersisa — gate eksplisit "no remaining pending rows", perbaiki Edge Case 5 agar aman bila hierarki risiko-based diperdalam per D-10). **Approver = Kepala Cabang ≠ submitter** (N15). Lalu **in-transaction re-enforce**: verification `verified`+≤30h (N1, D-01 S14) → BAST lengkap (N2) → due-date ≥ approve-date (N8) → chassis re-check. Semua lolos → aktivasi atomik (N3), mint `agreement_no`, upsert `tr_CIF`, master loan + jurnal + AR Card + amortisasi (N12), outbox Passnet (BR-NPP-14), enqueue email dealer (N14), generate dataset PK (N13/D-04), emit `AgreementActivated`. |
| `validated` | `approve` gagal verification | `validated` | `403 VERIFICATION_GATE_FAILED` + **rollback**; tidak ada side-effect (N1). |
| `validated` | `approve` gagal BAST | `validated` | `422 BAST_INCOMPLETE` + rollback (N2). |
| `validated` | `approve` gagal due-date | `validated` | `422 DUE_DATE_BEFORE_APPROVAL` + rollback (N8). |
| `validated` | `approve` oleh submitter sendiri | `validated` | `403 SELF_APPROVAL_BLOCKED` (D-01 S11; D-09). |
| `validated` | `POST /decision correction` | `held(correction)` | Reason wajib; hanya update status (BR-NPP-17). |
| `validated` | `POST /decision reject` | `held(rejected)` | Reason wajib; hanya update status (BR-NPP-17). |
| `held(correction)` | `PUT /npp/{id}` → `POST /submit` | `pending` → `validated` | Guard status editable (BR-NPP-5); listing menawarkan Edit utk Correction rows (BR-NPPVTL-10). |
| `held(rejected)` | — | (terminal) | Tidak re-editable via save/update (inferred terminal — OQ-NPP-12). |
| `active` | (replay `decision` idempotent) | `active` | Idempotency-Key sama → hasil pertama, tanpa cascade ulang (N4). |
| `active` | — | (terminal workflow) | Downstream STEP 16 ber-reaksi via PULL/event (N6); print menu dokumen legal unlocked (BR-NPP-4/BR-NPPVTL-10). |

Jalur non-happy-path tercakup: chassis mismatch (tetap `pending`), tiga gate fail + self-approval saat approve (rollback ke `validated`), correction bounce-back, reject terminal, idempotent replay.

---

## 8. Integrasi Eksternal

Semua seam eksternal **WAJIB** lewat Anti-Corruption Layer (ACL) app-tier Java; **hapus** linked-server DML lintas-DB, `EXECUTE AS 'sa'`, dan HTTP/mail dari dalam T-SQL (overview §8.2; `passnet §4` `[LOCKED]` fakta arsitektur; `email-sms §4`). Topologi infra final = deliverable ITEC (D-11).

| Seam | Arah | Sync/Async | Pemilik | Aturan ACL |
|---|---|---|---|---|
| **Passnet / mf-payment** | Outbound (Fincore → Passnet) | **Async** (outbox) | **05-npp** | "All data synced to Passnet" = output STEP 15 (GT `:70`). Outbox transaksional + reconciliation; `passnet_id` `[LOCKED]` format verbatim. Drain/write-back eksternal belum ter-lokasi (OQ-PASSNET-01/02). Legacy = linked-server D2D `[MACF-DBMCF].[EPMCF]`/`[MACF-DBMAF].[EPMAF]` → ganti kontrak API/outbox; perhatikan dua legal entity (MCF vs MAF). |
| **Passnet (read cross-check)** | Inbound (Fincore ← Passnet) | Sync | collateral (fidusia) | Cross-check NPP-number + legal-entity via ACL read; **jangan** hidupkan write-back `IsUpload=1` yang di-comment tanpa konfirmasi (Edge Case 3 passnet, OQ-PASSNET-04). Bukan owned 05. |
| **Email blast dealer (D-03)** | Outbound | **Async** (EMAIL_OUTBOX + worker) | **05-npp** (enqueue) → notification service | BARU per meeting. App-tier mail service Java (SMTP/provider — [OPEN]); DILARANG `sp_send_dbmail`+`sa`, fallback email personal hardcoded, allow-list NIK pilot (Edge Case 1/2 `email-sms` — do-not-replicate). Dedup + audit send-log (pola `tr_mail_notif_send_log`). Template/trigger/failure policy = OQ-MEET-01. Tidak ada kapabilitas SMS di legacy (negative search `email-sms §1`) — SMS di luar scope kecuali requirement baru. |
| **Dealer Payment** | Downstream STEP 16 PULL | Async (batch) | PAYMENT DB eksternal (`PAYMENTContext`) | 05 **tidak** men-seed; consumer poll record eligible. Eligibility-determinant + poster header = OQ-NPP-03/OQ-DISB-05. |
| **BPKB custody** | Downstream STEP 16 PULL | Async | collateral | Candidate-queue PULL by `verification_status` (OQ-NPP-04/OQ-COLL-01). Guard di-enforce in-transaction di domain-nya. Bukan owned 05. |
| **Insurance (Cover/Claim/Billing/Refund)** | Downstream STEP 16 PULL (**BARU v2**) | Async | INSURANCE bounded context | Ditambahkan ke daftar downstream oleh GT v2 (deltas table). Kontrak eligibility/subscribe = [OPEN] (belum ada KB slice insurance downstream utk NPP). |
| **GL / Disbursement subledger** | Output STEP 15 (D-06) — dipicu 05, dieksekusi engine subledger | Sync (Opsi A) / Async-saga (Opsi B) | disbursement-subledger | GL crosswalk bank-ID `[LOCKED]` verbatim (BR-DISB-5); balance zero-sum `[LOCKED]` (BR-DISB-1); posting failure TIDAK boleh silent (Edge Case 1 `32-disbursement`); idempotent + reversal path wajib didesain (Edge Case 4). Mapping akun = OQ-MEET-03. |
| **Rapindo (asset-identity check)** | Outbound check (data-entry time) | Sync | [OPEN] | Check live di FE legacy (Edge Case 3 FE) vs cascade registrasi dead (Edge Case 1 BE). Abstraksi via `validations/asset-check`; kelanjutan = OQ-NPPVTL-04. |
| **DOKU (bank-account inquiry)** | Outbound | Sync | **04-contract-cm-po** (bukan 05) | Disebut untuk konteks — inquiry rekening di stage CM. Rebuild: HTTP client app-tier (OQ-DOKU-01). |
| **Fidusia** | Internal record-keeping | n/a | collateral (post-acquisition) | **BUKAN** API pemerintah live — record-keeping internal. Bukan owned 05. |

---

## 9. Acceptance Criteria (Given/When/Then)

**AC-1 — Happy path aktivasi (STEP 15 lengkap)**
- **Given** NPP `validated`, verifikasi Vertel `verified` (freshness 12 hari), chassis/engine cocok, BAST lengkap, due-date ≥ approve-date, approver = Kepala Cabang ≠ submitter, Idempotency-Key baru;
- **When** Kepala Cabang `POST /npp/{id}/decision {action:"approve"}`;
- **Then** status → `active`, `agreement_no` di-mint, `passnet_id` `'5'`+9-digit dibuat, `tr_CIF` di-upsert by `national_id`, **master loan terbentuk** (D-05), **jurnal + AR Card + amortisasi terbentuk** (D-06, GT `:70`), dataset **PK tersedia** (D-04), **email dealer ter-enqueue** (D-03), `AgreementActivated` di-emit — seluruh konsekuensi internal dalam **satu unit kerja transaksional** (D-01 S15); response `200`.

**AC-2 — Verification gate fail (fail-closed + rollback)**
- **Given** NPP `validated`, status Vertel = `recheck` (atau freshness 41 hari > 30-hari strict — D-01 S14);
- **When** approver `approve`;
- **Then** `403 VERIFICATION_GATE_FAILED`, transaksi **rollback penuh**, status tetap `validated`, TIDAK ada `agreement_no`/`tr_CIF`/master loan/jurnal/outbox/email/event (BR-NPP-N1).

**AC-3 — BAST hard-gate**
- **Given** `bast_no`/`bast_date` null saat approve;
- **When** approver `approve`;
- **Then** `422 BAST_INCOMPLETE`, rollback, `bast_validated=false` (BR-NPP-N2, D-01 S14; definisi lengkap = OQ-NPP-14 narrowed).

**AC-4 — Chassis/engine mismatch di submit**
- **Given** `chassis_no`/`engine_no` tak cocok Credit Memo;
- **When** maker `POST /submit`;
- **Then** `422 CHASSIS_ENGINE_MISMATCH`, status tetap `pending`, checker tidak dibangun (BR-NPP-1).

**AC-5 — Idempotent replay aktivasi**
- **Given** NPP sudah `active` via Idempotency-Key `K`;
- **When** `POST /decision` diulang dengan `K`;
- **Then** `200` mengembalikan hasil aktivasi pertama; cascade, `tr_CIF` upsert, jurnal, AR Card, master loan, outbox, email **tidak** dijalankan ulang; tak ada duplikasi (BR-NPP-N4).

**AC-6 — Reject/Correction tanpa side-effect**
- **Given** NPP `validated`;
- **When** approver `reject`/`correction` (reason terisi);
- **Then** status → `held`, hanya header update; TIDAK ada aktivasi/outbox/CIF/jurnal/email/event (BR-NPP-17); `correction` re-openable ke `pending`, `reject` terminal.

**AC-7 — Downstream STEP 16 PULL, bukan push**
- **Given** NPP `active` + `AgreementActivated` di-emit;
- **When** downstream (Dealer Payment / BPKB / **Insurance**) beroperasi;
- **Then** mereka **pull/subscribe** record eligible; 05 tidak menulis `DISBURSEMENT`-payment/`BPKB`/PAYMENT DB/insurance store (BR-NPP-N6, D-01 S15, GT `:72-73`).

**AC-8 — Passnet outbox + reconciliation**
- **Given** aktivasi sukses;
- **When** outbox-row `PASSNET_SYNC` (`out_event.status=pending`) dibuat & consumer ack;
- **Then** reconciliation menulis `out_event.status=sent`/`failed` + `last_error` (semantik legacy `is_sync`/`return_message` — mapping §3.1.8); retry idempotent; kegagalan permanen → `dead_letter` (BR-NPP-N7).

**AC-9 — No self-approval; no super-user (D-09/D-01 S11)**
- **Given** identitas approver = submitter;
- **When** `approve`;
- **Then** `403 SELF_APPROVAL_BLOCKED` di application layer; TIDAK ada jalur/flag super-user apa pun yang mengubah hasil ini (D-09 `[LOCKED]`); percobaan ter-audit ke `NPP_APPROVAL_HISTORY`.

**AC-10 — Due-date enforced in mutation**
- **Given** due-date < approve-date;
- **When** `approve` langsung (bypass endpoint advisory/preflight);
- **Then** `422 DUE_DATE_BEFORE_APPROVAL`, rollback (BR-NPP-N8, perbaiki Edge Case 9).

**AC-11 — Email blast dealer (D-03)**
- **Given** aktivasi sukses; dealer punya `email` di master;
- **When** notification worker memproses `EMAIL_OUTBOX`;
- **Then** email terkirim ke alamat master dealer, `status=sent` + `sent_at` terisi; kirim ulang di-dedup per (agreement, template); kegagalan kirim → `status=failed` + retry/dead-letter **tanpa** mempengaruhi status kontrak; TIDAK ada pengiriman dari database-tier / `sa` / alamat personal hardcoded (BR-NPP-N14).

**AC-12 — Ledger fail-closed (bukan silent seperti legacy)**
- **Given** aktivasi berjalan dan posting jurnal gagal balance (zero-sum check BR-DISB-1) atau engine subledger error;
- **When** transaksi aktivasi dieksekusi;
- **Then** aktivasi **tidak** menghasilkan kontrak final-`active` tanpa buku: Opsi A → rollback penuh; Opsi B → status `active_pending_ledger` + alarm reconciliation. DILARANG mengulang Edge Case 1 `32-disbursement` (return code dibuang, kontrak aktif tanpa GL) (BR-NPP-N12).

**AC-13 — Boundary freshness 30-hari (D-01 S14)**
- **Given** verifikasi Vertel `verified` dengan umur tepat 30 hari (hari ke-30) vs 31 hari;
- **When** approver `approve` pada masing-masing kondisi;
- **Then** hari ke-30 lolos gate, hari ke-31 diblokir `403` (strict 30-day limit); definisi titik mulai clock mengikuti resolusi OQ-MEET-05 dan di-test ulang saat diputuskan.

---

## 10. Dependency

### 10.1 Upstream dikonsumsi (read)

| Sumber | Yang dikonsumsi | Mode |
|---|---|---|
| **04-contract-cm-po (STEP 12–13)** | `PURCHASE_ORDER` (`po_number` issued) sebagai trigger masuk; `CREDIT_MEMO` (branch/dealer/tenor/rate/asset/`trans_type_id`; OP/ULI/LCR + asuransi **frozen** saat committee approve — GT `:52-53`). | Read (event `POIssued` sebagai sinyal siap). |
| **Modul Vertel (STEP 14, D-02)** | Status verifikasi telepon (`TrVerificationCustomer`) + `freshness_at` — **hard-gate** `verified` + ≤30 hari (D-01 S14). | Read (gate). Prasyarat `POST /npp` + re-enforce saat approve. |
| **01-intake-cas (STEP 1–8)** | `ASSET` (`chassis_no`/`engine_no`) untuk validasi final; identitas applicant (NIK/NPWP) untuk seed `tr_CIF`; `credit_id` hasil minting STEP 8. | Read. |
| **02-credit-analysis** | Freshness SLIK/FCL (**advisory**, BR-NPP-6/N11) — jangan konflasi dgn gate Vertel (N10). | Read (advisory). |
| **Master data (D-08)** | `ms_dealer` (email dealer utk D-03), dealer bank reference, reason codes. | Read. |

### 10.2 Output STEP 15 & downstream STEP 16 — PULL / event (BUKAN push)

| Target | Cara dipicu | Kepemilikan | OQ |
|---|---|---|---|
| **Jurnal + AR Card + amortisasi** (output STEP 15, D-06) | Dipicu aktivasi (Opsi A in-tx / Opsi B saga); engine subledger mengeksekusi aturan `[LOCKED]` `32-disbursement`. | disbursement-subledger engine (dalam bounded context ACQUISITION per flow.png GT `:10`) | **OQ-MEET-03** (GL mapping) |
| **Master loan** (output STEP 15, D-05) | Dibuat saat aktivasi; feeds servicing/collection. | [OPEN] — flow.png indikasi ACQUISITION | **OQ-MEET-02** (P1) |
| **Dokumen PK** (output STEP 15, D-04) | Dataset di-generate pasca-commit; print on-demand endpoint reports. | 05-npp | — |
| **Email blast dealer** (output STEP 15, D-03) | `EMAIL_OUTBOX` + async worker. | 05-npp (enqueue) | **OQ-MEET-01** |
| **Passnet registration** | **Outbox** `PASSNET_SYNC` di-drain consumer eksternal + write-back. | 05 (outbox) → ETL eksternal | **OQ-PASSNET-01/02** |
| **Dealer Payment (transfer dana)** | **Pull** batch cashier dari PAYMENT DB eksternal; eligibility belum ter-lokasi. | PAYMENT DB eksternal | **OQ-NPP-03 / OQ-DISB-05** |
| **BPKB custody** | **Pull** candidate-queue by `verification_status` (`agreement_status` di-comment). | collateral (STEP 16) | **OQ-NPP-04 / OQ-COLL-01** |
| **Insurance** (**BARU v2**) | **Pull/subscribe** pasca-aktivasi. | INSURANCE context (STEP 16) | [OPEN] kontrak eligibility |
| **Event `AgreementActivated`** | Emitted 05; consumer subscribe. | 05 (emit) | — |

---

## 11. Keputusan Dibutuhkan (Open Questions)

> `[OPEN]` dari KB + meeting — **jangan** diselesaikan diam-diam. Rebuild memakai default fail-closed di mana relevan, tetapi keputusan final butuh stakeholder. Item yang TERTUTUP oleh meeting dicatat di bawah tabel.

| OQ-ID | Pertanyaan | Prioritas | Dampak |
|---|---|---|---|
| **OQ-MEET-02** | Master loan (D-05): owned Acquisition atau servicing context? Field census + consumer list. (flow.png indikasi ACQUISITION — GT `:10`.) Resolves: dokumen arsitektur ITEC + stakeholder. | P1 | Entitas `MASTER_LOAN` / `trx_master_loan` §3.1.12 (skema USULAN) + Opsi A/B §1.3. |
| **OQ-MEET-03** | AR Card & jurnal (D-06): GL account mapping + posting rules source of truth (finance stakeholder + kontrak Passnet). | P2 | BR-NPP-N12, AC-12; leg set `[LOCKED]` `32-disbursement` sebagai baseline. |
| **OQ-MEET-01** | Email blast dealer (D-03): trigger point (NPP approve vs T+n batch), template/konten, failure policy. Resolves: business owner (COBS). | P2 | BR-NPP-N14, AC-11, skema `EMAIL_OUTBOX`. |
| **OQ-MEET-05** | Expiry verifikasi 30-hari (D-01 S14): konsekuensi expiry (auto-cancel vs wajib re-verify Vertel) + titik mulai clock (tanggal approve Vertel? tanggal telepon?). Resolves: ops stakeholder. | P2 | BR-NPP-N1, AC-13; UX antrian NPP kadaluarsa. |
| **OQ-MEET-06** | Matrix step per produk MACF (D-07) — step applicability/variance per produk. BLOCKS annex per-produk, bukan PRD umbrella ini. | P1 | Parameterisasi gate/cascade per produk (mis. syariah vs CF — social fund `32-disbursement BR-DISB-10`). |
| **OQ-NPP-03 / OQ-DISB-05** | Tak ada reader men-seed Dealer Payment dari `agreement_status='A'` — apa penentu eligibility batch Dealer Payment & siapa post header di PAYMENT DB eksternal? | P1 | Kontrak downstream seam STEP 16. |
| **OQ-NPP-02 / OQ-PASSNET-01/02** | Siapa men-drain `tr_synchronize_to_passnet` (`is_sync='0'`) & menulis balik? Scope Passnet (master NPP vs eksekusi payment)? | P1 | Desain outbox/ACL Passnet. |
| **OQ-NPP-04 / OQ-COLL-01** | BPKB candidate-queue: key by `verification_status='A'` (live) vs `agreement_status='A'` (di-comment) — intentional atau regresi? | P2 | Predikat eligibility BPKB. |
| **OQ-NPP-06** | Hierarki 2-level tetap (BR-NPP-8) vs N-level by **skala risiko** (D-10) untuk NPP spesifik — berapa kedalaman utk NPP? | P2 | Bentuk maker-checker (configurable?); gate "last pending step" §7. |
| **OQ-NPP-14** *(narrowed)* | BAST hard-gate SUDAH diputuskan (D-01 S14). Sisa: definisi "lengkap" — cukup `bast_no`+`bast_date` non-null, atau wajib dokumen BAST ter-upload/scan? Dan rekonsiliasi vs `tr_BPKB.BAST_date` (OQ-NPP-08). | P2 | Detail BR-NPP-N2 + skema field/dokumen. |
| **OQ-NPP-08** | `tr_NPP.bast_no/bast_date` vs `tr_BPKB.BAST_date/BPKB_no` — dua record BAST terpisah; harus rekonsiliasi? Mana authoritative? | P2 | Konsistensi BAST lintas domain (Edge Case 10). |
| **OQ-NPP-05** | `V` (Verify) status reachable di produksi? Tak ada write-path ditemukan. | P3 | Kelengkapan state machine. |
| **OQ-CMPO-01** | Arti legacy status `0` — v2 menambah data: STEP 8 draft kontrak auto-created "Status RFA = '0'" (GT `:28`) sementara kode NPP memakai `0`=RFA-locked. Cross-domain (01/04). | P1 | Mapping legacy→kanonik (§7) + guard editability `PUT` (§4). |
| **OQ-NPP-10** | Hard-reset Insurance Periodical/Coverage (BR-NPP-12) = policy sengaja atau overwrite tak sengaja nilai CMO? | P3 | Aturan insurance-at-activation. |
| **OQ-NPP-12** | `held(rejected)` benar-benar terminal atau ada path out-of-slice yang re-open? | P3 | Terminalitas state. |
| **OQ-NPP-09** | Identitas consumer `tr_batching_trans` (write tanpa reader lokal) — batch/collection job eksternal? | P3 | Perlukah rebuild queue setara. |
| **OQ-NPP-01** | `sp_validate_npp_rfa` (dipanggil `/validate-rfa-npp`) tidak ada di SP dump lokal — hosted di environment lain? | P6 | Kelengkapan validasi RFA. |
| **OQ-NPP-11** | `SpSyncToPassnetR2_reversal` (mobile-CAS migration reversal) terkait atau naming collision murni dgn `tr_synchronize_to_passnet`? (KB passnet: confirmed unrelated per isi file — tinggal konfirmasi formal.) | P3 | Perlakukan unrelated hingga terbukti. |
| **OQ-NPP-13** | `sp_get_history_npp` membaca `tr_hierarchy_approval_transaction` saat `agreement_status='A'` — dead code atau ada archival writer? | P1 | Sumber audit history NPP. |
| **OQ-REG-06** | Fail-open vs **fail-closed** untuk SEMUA regulated gate (verification/chassis/BAST/FCL NPP) saat komponen core throw mid-check. | P1 (highest-impact) | Kebijakan global gate; menyentuh N1/N2/N11/N12. |
| **OQ-NPPVTL-04** | Rapindo *check* (live di FE entry, car non-top-up) vs *registration cascade* (dead) — apakah check-only masih requirement integrasi eksternal? | P2 | Endpoint `validations/asset-check`; scope ACL Rapindo. |
| **OQ-NPPVTL-09** | NPP entry RFA-only (web FE legacy; draft path dead) = perilaku diterima, atau rebuild wajib draft-save genuine? | P2 | Reachability state `pending` dari FE baru; guard `PUT`. |
| **OQ-NPPVTL-02** | "Kepala Cabang" (D-10) = role bernama baru di sistem (target) — konfirmasi mapping ke struktur hierarki existing (legacy hanya punya flag "pending approver"). | P2 | RBAC endpoint `decision`; klaim role di token/session. |
| **OQ-NPPVTL-06** | Pesan error pre-check approve = copy user-facing/regulated atau string diagnostik bebas redesign? | P3 | Kontrak error envelope §5. |
| **OQ-DOKU-01** | Siapa mengisi `DOKU_*.response*` (write-back tanpa caller)? | P1 | ACL DOKU (seam CM, disebut untuk konteks). |
| **OQ-ARCH-STACK** *(direvisi D-12)* | Bahasa BE = **Java** `[LOCKED per D-12]`; FE = Next.js. Masih [OPEN]: framework (rekomendasi **Spring Boot** — USULAN), runtime/versi JDK, transport final (REST vs gRPC vs message-bus utk event `AgreementActivated`), platform messaging/outbox. Menunggu dokumen arsitektur ITEC (D-11, deadline 10 Jul 2026). | P1 | Konvensi API & event semua kapabilitas. |
| **OQ-GT-02** | Format/sequence minting `credit_id` nasional-unik (STEP 8) — dipakai 05 sebagai kunci lintas-modul. → **RESOLVED — evidence (2026-07-14)**: 14-char `branch_id(5)+YY(2)+MM(2)+SEQ(5 zero-pad)`, reset bulanan per cabang (spec BE-01 §3.1.13; BE-00 §11). | ~~P2~~ **RESOLVED — evidence** | Validasi referensi `credit_id` §3.1.1 (`trx_agreement.credit_id`). |

**Tertutup oleh keputusan meeting (tidak lagi open untuk modul ini):**
- **OQ-NPP-07 / OQ-MCP-01 (sisi kebijakan)** — super-user & self-approval: **DIJAWAB** oleh D-09 (super user dihapus, `[LOCKED]`) + D-01 Step 11 (self-approval blocked). Sisa kerja = enforcement app-layer (BR-NPP-N15), bukan pertanyaan bisnis.
- **OQ-NPP-14 (arah utama)** — BAST menjadi **hard gate** per D-01 Step 14; tersisa versi narrowed (definisi kelengkapan) di tabel di atas.
