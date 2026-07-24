# PRD — Approval & Committee (Inbox, Detail Komite, Keputusan) [FE]

> **Audience**: Tim Frontend (FE). **Target stack**: **Next.js** (DIWAJIBKAN per D-12 `[LOCKED]`).
> BE counterpart: **Java** (D-12; framework BELUM ditetapkan — USULAN Spring Boot, lihat BE-03 §11).
> **Tanggal**: 2026-07-14. **Status**: Revisi v2 (post-meeting), pasangan dari
> `BE-03-approval-committee.md` (kontrak API §8 dokumen ini WAJIB konsisten dengan BE-03 §4/§5).
> **Sumber otoritatif**: `.mega-sdd/knowledge-base/.sp-manifests/_ACQUISITION-GROUND-TRUTH.md` (v2, PDF
> 08072026 — 16 STEP), `.mega-sdd/knowledge-base/.sp-manifests/_MEETING-DECISIONS-2026-07.md`
> (D-01..D-12), KB FE `60-frontend/63-approval-inbox-screens.md` (ekstraksi layar legacy — sumber utama
> modul ini), `60-frontend/60-app-shell-auth-navigation.md` + `60-frontend/67-client-side-behavior.md`
> (konvensi shared/shell), KB BE `10-domains/22-approval-committee.md`,
> `20-workflows/approval-state-machine.md`, `20-workflows/maker-checker-pattern.md`.

> **Cakupan**: layar FE untuk **STEP 12 — Hierarki Persetujuan (Credit Committee)** flow final 16-step
> (PDF 08072026): dokumen masuk menu **Inbox Approval**, komite me-review sekuensial multi-level, lalu
> mengeksekusi **Approve / Reject / Correction**; pada terminal Approve **OP/ULI/LCR + asuransi DIKUNCI**
> (`_ACQUISITION-GROUND-TRUTH.md` STEP 12 `[VERIFIED — doc]`). Layar legacy adalah **EVIDENCE, bukan
> mandat desain** — dokumen ini mempertahankan OUTCOME (field, aturan, staging, role-gating) sambil
> mengusulkan UX Next.js yang bersih (ditandai **USULAN**).
> **Bahasa**: Bahasa Indonesia; identifier/field/kode/SP/OQ-ID legacy dipertahankan apa adanya.

> **Disiplin penanda**: `[LOCKED]` = WAJIB dipertahankan 1:1, additive only. `[INTENT]` = outcome
> dipertahankan, mekanisme bebas didesain ulang. `[ARTIFACT]` = kecelakaan legacy, do-not-replicate.
> `[OPEN]` = masuk §11, JANGAN diselesaikan diam-diam. **USULAN** / `[KEPUTUSAN DESAIN BARU]` = desain
> baru, bukan dari legacy. Keputusan meeting dirujuk dengan ID **D-xx**.

---

## 1. Ruang Lingkup & Kepemilikan (screens yang dimiliki modul ini)

### 1.1 Screens yang DIMILIKI FE-03

| ID | Screen | Asal-usul | Marker |
|---|---|---|---|
| **SCR-03-01** | **Inbox Approval** — listing item pending yang di-assign ke approver login (filter Tipe Aplikasi / Repeat Order / search, paging). | Padanan legacy `Views/Approval/Inbox.cshtml` `[VERIFIED]` (KB 63 §1, §4). | `[INTENT]` (outcome listing+filter dipertahankan; UI baru) |
| **SCR-03-02** | **Approval History** — listing item yang SUDAH di-action oleh employee login; read-only struktural (tanpa action slot). | Padanan legacy `Views/Approval/History.cshtml` `[VERIFIED]` (KB 63 §4, BR-AIS-5). | `[INTENT]` |
| **SCR-03-03** | **Detail Aplikasi untuk Komite (CM Committee Detail)** — konteks memo + **indikator risk & plafond** + **hierarki approval (progress per level)** + **riwayat approval (audit timeline)** + action bar keputusan. | **TIDAK ada padanan tunggal di legacy** — legacy men-dispatch dari Inbox ke 6 layar sibling via Controller/Action string server-driven (KB 63 §9 Edge Case 5 `[ARTIFACT]` — do-not-replicate). Layar terpadu ini = **USULAN** `[KEPUTUSAN DESAIN BARU]`, meng-cover `item_type=CM_COMMITTEE` (BE-03 §4 catatan routing). | USULAN; unifikasi lintas item_type = `[OPEN]` OQ-AIS-01 |
| **SCR-03-04** | **Dialog Keputusan Komite** (component dalam SCR-03-03) — form aksi `approve`/`reject`/`correction` + reason + konfirmasi 2-tombol. | Field census dari KB 22 §3a stage S1 `[VERIFIED]` (`sp_approve_cm:167-360`); pola konfirmasi dari KB 67 BR-CSB-18 `[INTENT]`. | `[INTENT]` |

### 1.2 Yang BUKAN milik FE-03 (batas)

| Bukan miliknya | Pemilik | Catatan |
|---|---|---|
| Shell aplikasi: login 2-stage (kredensial + pilih branch), navigasi/menu, session guard, idle timeout. | **FE-00** (umbrella) — grounding KB 60 | FE-03 mengasumsikan konteks employee terautentikasi tersedia; TIDAK mereplika fallback sentinel `"0"` (KB 63 BR-AIS-2 `[ARTIFACT]`). |
| Layar keputusan/detail item_type NON-komite (CAS, CA/CACar, CM entry, NPP, Vertel) yang legacy-nya dituju dispatcher Inbox. | **FE-01/FE-02/FE-04/FE-05** per modulnya | Routing table `item_type → route` didefinisikan di FE-00; FE-03 hanya memiliki entri `CM_COMMITTEE`. |
| Form RFA / Verify cabang (STEP 9) & komposisi awal `trans_type_id`. | FE-01 (STEP 9) / BE-02 (komposisi) | BE-03 §1.2. Routing komite dipicu event `AnalysisComplete` di BE — **tidak ada tombol "submit RFA" di FE-03**. |
| PO minting & layar PO (STEP 13), Vertel (STEP 14), NPP (STEP 15). | FE-04 / FE-05 | BE-03 §1.2 (D-01 S13). |
| Layar admin **IA Policy** (`/ia-policies` — USULAN di BE-03 §4). | belum ditetapkan | `[OPEN]` GAP-FE03-03 §11 — bukan otomatis milik FE-03. |
| Layar input/maintenance **deviasi MP** (Memo Persetujuan — `trx_deviation`/`cfg_deviation_rule`, BE-03 §3.1.5–3.1.6) & tampilan jejak deviasi ter-approve. | belum ditetapkan | `[OPEN]` GAP-FE03-04 §11 — BE-03 §4 juga BELUM punya endpoint deviasi; JANGAN mengarang layar/endpoint. |
| Ladder credit-analyst (`ApprovalSchemeModels` Level1..Level10). | FE-02 (62-credit-analysis-screens) | KB 63 OQ-AIS-02; JANGAN dikonflasi dengan hierarki komite CM (BE-03 BR-AC-6 `[VERIFIED][LOCKED]` fakta dua ladder terpisah). |
| Visualisasi status Vertel/NPP di inbox selain menampilkan row-nya. | FE-05 | Inbox generik menampilkan item lintas domain; layar tujuannya milik modul masing-masing. |

**Catatan super-user**: role super-user **DIHAPUS** (**D-09** `[LOCKED]`) — TIDAK ada layar, toggle,
atau jalur UI override apa pun di modul ini.

---

## 2. Aktor & Peran (akses per screen, role-gating)

Census role cabang (**D-10** `[LOCKED]`): **CMO, Marketing Head, Credit Analyst, Kepala Cabang,
Credit (Admin)**. Legacy TIDAK punya RBAC statis — kedua controller Approval malah men-comment-out
`[Authorize]` (KB 63 BR-AIS-3 `[VERIFIED][ARTIFACT]` — do-not-replicate); menu di-resolve per-employee
(KB 60 BR-SHELL-4). Rebuild: gate autentikasi terpusat di FE-00 + otorisasi nyata di BE
(`[KEPUTUSAN DESAIN BARU]`, selaras BE-03 §4 kolom Auth/Role).

| Screen | Siapa yang mengakses | Gating |
|---|---|---|
| SCR-03-01 Inbox | **Approver** (Kepala Cabang / kepala area / kawil / ACM / Credit Analyst bila muncul di chain — BE-03 §2) | Scoped OTOMATIS ke employee login (`approver` dari konteks auth — BE-03 §5.3; KB 63 BR-AIS-1 `[VERIFIED][INTENT]`: tidak ada kontrol melihat antrian orang lain). Tanpa sesi valid → **401** → redirect login (guard FE-00). |
| SCR-03-02 History | Approver (item yang ia action), Auditor | Scoped ke `actor` dari konteks auth (BE-03 §4 `GET /approval-history`). Read-only struktural (KB 63 BR-AIS-5). |
| SCR-03-03 Detail Komite | Approver ter-assign (aksi), Credit (Admin) & Auditor (lihat progres/riwayat, tanpa aksi) | Tombol aksi dirender HANYA bila `actionable=true` dari BE (server-side determination — KB 63 BR-AIS-4/5 `[INTENT]`); enforcement final tetap di BE (assigned-only D-09 + no-self-approval **D-01 S11**). FE TIDAK mengambil keputusan eligibility sendiri. |
| SCR-03-04 Dialog Keputusan | Approver ter-assign step current pending | Hanya terbuka dari action bar SCR-03-03 yang `actionable`. |

- **No-self-approval (D-01 S11 `[INTENT]`)**: FE tidak perlu (dan tidak boleh) menghitung sendiri
  maker≠checker; BE menolak dengan **403 `APPROVER_IDENTITY_DENIED`** (BE-03 §5.2) — FE menampilkan
  pesan terstruktur (lihat §7).
- **Marketing Head**: role ter-konfirmasi D-10 tetapi keterlibatannya di chain komite belum ter-evidensi
  (`[OPEN]` OQ-BE03-03) — JANGAN membuat gating/label khusus Marketing Head di FE.
- **System (IA lane, D-01 S11)**: bukan pengguna layar; jejaknya muncul sebagai badge
  `auto_approved_ia` di riwayat (lihat §4.3, §6).

---

## 3. Peta Screen & Route (inventori screen + usulan route Next.js)

Semua route di bawah = **USULAN** (App Router, di dalam layout shell FE-00). Legacy memakai dispatch
`Controller/Action` string dari server (KB 63 Edge Case 5 `[ARTIFACT]`) — rebuild menggantinya dengan
**routing table eksplisit `item_type → route` di FE** (kontrak `item_type` enum tertutup, BE-03 §4
catatan) `[KEPUTUSAN DESAIN BARU]`.

| Screen | Route (USULAN) | Jenis | Catatan |
|---|---|---|---|
| SCR-03-01 Inbox Approval | `/approval/inbox` | list page | Filter & page state di query string (`?page=&size=&applicationTypeId=&isRepeatOrder=&search=`) agar shareable/back-button-safe — USULAN; padanan param BE-03 §4 `GET /approval-inbox`. |
| SCR-03-02 Approval History | `/approval/history` | list page | Query string `?page=&size=&search=`. |
| SCR-03-03 Detail Komite | `/approval/cm/[memoId]` | detail page | Target routing untuk `item_type=CM_COMMITTEE`. `memoId` = `memo_id` (`credit_id` legacy — PK kontrak nasional, BE-03 §3.1). |
| SCR-03-04 Dialog Keputusan | (bukan route — modal/dialog dalam SCR-03-03) | component | Opsional intercepted route `/approval/cm/[memoId]/decide` bila tim FE memilih pattern itu — USULAN. |

**Routing table item (dimiliki FE-00, entri FE-03):**

| `item_type` (enum tertutup dari BE) | Route tujuan | Pemilik |
|---|---|---|
| `CM_COMMITTEE` | `/approval/cm/[memoId]` | **FE-03** |
| (item_type domain lain: CAS/CA/NPP/Vertel dsb.) | didefinisikan modul pemiliknya | FE-01/02/04/05 |

Aturan: `item_type` di luar tabel → row dirender **inert** (teks tanpa link) + telemetri warning; JANGAN
membangun URL dari string yang dikirim server (do-not-replicate KB 63 Edge Case 5).

---

## 4. Komposisi Layar & Komponen (layout, form, table, dialog per screen)

Seluruh komponen generik (DataTable + pagination, ConfirmDialog, StatusBadge, Toast/Alert, loading
overlay, currency formatter tunggal id-ID, guard auth) dipakai dari **shared components FE-00** —
JANGAN duplikasi (grounding konsolidasi: KB 67 BR-CSB-11 satu grid contract, BR-CSB-19 satu vocabulary
dialog, BR-CSB-6 satu currency formatter). NFR: **responsive mobile + desktop** — tabel lebar di-render
sebagai card list di breakpoint kecil (USULAN).

### 4.1 SCR-03-01 — Inbox Approval

- **Filter bar** (form GET, lihat census §5.1): dropdown *Tipe Aplikasi*, select *Repeat Order*, input
  *search*, tombol Cari/Reset.
- **Tabel inbox** (kolom lengkap di §5.3): row klik → navigasi via routing table §3 HANYA bila
  `item_type` dikenal; kolom aksi menampilkan tombol **"Proses"** bila `actionable=true` (pengganti
  tombol "Go" legacy yang bergantung `ActionApproved` non-empty — KB 63 BR-AIS-5 `[INTENT]`).
- **Pagination** server-driven (`page`, `size`, `total`, `total_pages` dari envelope BE-03 §5.3);
  guard out-of-range milik BE (server clamp — BE-03 §5.3 `[KEPUTUSAN DESAIN BARU]`, menutup KB 63
  OQ-AIS-07); FE cukup menonaktifkan tombol next/prev di tepi.
- **Empty state**: bedakan "Data Not Available" (sukses, 0 row) vs "Gagal memuat data" (fetch failed) —
  outcome dipertahankan dari varian pager legacy yang lebih lengkap (KB 67 BR-CSB-11 `[INTENT]`).
- Mobile (USULAN): card per item — baris 1: `transaction_id` + StatusBadge; baris 2: `debtor_name` +
  `transaction_date`; baris 3: Next PIC; tombol Proses full-width.

### 4.2 SCR-03-02 — Approval History

- Filter bar: **hanya** free-text search (legacy tidak punya filter Tipe Aplikasi/RO di History —
  KB 63 §4 `[VERIFIED][INTENT]`; jangan menambah filter tanpa keputusan).
- Tabel history (kolom §5.4): **tanpa** kolom Debtor Name dan **tanpa** action cell (read-only
  struktural — KB 63 BR-AIS-5 `[VERIFIED][INTENT]`; model item BE juga tanpa action slot, BE-03 §5.4);
  plus kolom hasil keputusan `action` + `acted_at`.
- Row klik → navigasi **view-only** ke detail (routing table §3), tanpa action bar.

### 4.3 SCR-03-03 — Detail Aplikasi untuk Komite (USULAN, satu halaman 4 panel)

1. **Panel Header / Konteks Memo** — `memo_id`, `trans_type_id` (badge monospace — `[LOCKED]`
   char-for-char, jangan diformat/dipotong, BE-03 BR-AC-1), nama debitur, tanggal transaksi,
   description, status komite (`routing|in_review|approved|rejected|correction` — BE-03 §7.1).
   **Data detail aplikasi/finansial CM yang lebih dalam (angka OP/ULI/LCR pre-lock, asuransi, dsb.)
   BELUM punya endpoint di BE-03 → GAP-FE03-01 §11 — JANGAN mengarang endpoint.**
2. **Panel Indikator Risk & Plafond** — menampilkan jejak auditable `risk_tier_resolution`
   (bentuk field: BE-03 §5.1): `base_risk_digit`, `final_risk_digit` (badge tier Low/Medium/High/
   Very-High), flag `effective_rate_escalation_applied`, `exposure_escalation_applied`,
   `aggregate_op` + `op_threshold_applied` (format Rupiah, formatter tunggal FE-00), `acm_swap_applied`,
   `ia_policy_applied`. Label "Plafond Hutang Pokok (OP)" per **D-01 S10** (ekspansi parsial akronim;
   makna GL penuh = OQ-CORE-03 `[OPEN]`). **Ketersediaan data ini via GET untuk layar = GAP-FE03-02
   §11** (di BE-03 bentuk ini terdokumentasi pada response `POST committee-routing` yang system-triggered).
3. **Panel Hierarki Approval (progress per level)** — stepper vertikal dari
   `GET /credit-memos/{memoId}/approval-steps` + `GET /credit-memos/{memoId}/approval-progress`
   (BE-03 §4): satu node per step — `sequence`, `level_label` (mis. `kepala_cabang`, `kepala_area`,
   `acm_final` — decode singkatan `[INFERRED]`, BE-03 §2), nama/NIK assigned employee, status step
   (`pending|approved|rejected|correction|voided|auto_approved_ia`). Node `voided` dirender redup +
   label "Dibatalkan (correction)" — void EKSPLISIT, bukan disembunyikan (do-not-replicate empty-string
   sentinel, BE-03 BR-AC-12). Chain **strictly sequential** — tidak ada tampilan quorum/parallel
   (BE-03 BR-AC-5 `[VERIFIED][INTENT]`).
4. **Panel Riwayat Approval (audit timeline)** — dari `GET /credit-memos/{memoId}/approval-history`
   (grounded `tr_hierarchy_transaction` `[VERIFIED — doc]` STEP 12): actor, action, reason, timestamp,
   `correlation_id`; baris IA menampilkan badge **"Instant Approval"** + `ia_policy_id` (D-01 S11
   auditable); baris migrasi legacy dengan `legacy_super_user_override=true` menampilkan badge
   "Override (legacy, read-only)" — TIDAK pernah muncul untuk data baru (D-09 `[LOCKED]`).
- **Action bar** (sticky bottom di mobile — USULAN): tombol **Approve** (primary), **Correction**
  (warning), **Reject** (destructive) — dirender hanya bila `actionable=true`; membuka SCR-03-04.

### 4.4 SCR-03-04 — Dialog Keputusan Komite

- Modal 2-langkah: (1) **form keputusan** (census §5.2) → (2) **konfirmasi 2-tombol**
  (proceed/cancel, satu vocabulary Bahasa Indonesia dari komponen FE-00 — konsolidasi KB 67 BR-CSB-18/19
  `[INTENT]`; aksi status-changing irreversible WAJIB konfirmasi eksplisit).
- Teks konfirmasi per aksi (USULAN): Approve level terakhir menyebut konsekuensi **"OP/ULI/LCR +
  asuransi akan DIKUNCI dan tidak dapat diubah"** (outcome STEP 12 `[VERIFIED — doc]`); Reject menyebut
  **"permanen"**; Correction menyebut **"dokumen kembali ke proses origination Step 1–7 untuk perbaikan
  CMO"** (BE-03 §5.2 `correction_target=STEP_1_7_ORIGINATION`).
- Tombol submit: busy-indicator + disabled selama request (KB 67 BR-CSB-20 `[INTENT]`), `idempotency_key`
  di-generate per attempt (BE-03 §5.2 wajib).

---

## 5. Field & Validasi (census per form)

> Inti nilai dokumen ini. Sumber tiap baris disebut; aturan yang hanya ada di klien legacy TIDAK
> dijadikan satu-satunya enforcement — server rules menang (KB index `_screen-inventory.md` seam #4).

### 5.1 Form Filter Inbox (SCR-03-01)

| Field | Label | Tipe | Required | Format/Aturan validasi | Sumber options | Sumber |
|---|---|---|---|---|---|---|
| `applicationTypeId` | Tipe Aplikasi | select (single) | Tidak | Nilai harus salah satu opsi lookup; default "Pilih" (kosong = tanpa filter). | **Backend lookup** `GET /approval-inbox/application-types` (padanan `sp_get_ms_application_type_inbox`; pair `application_type_inbox_id`/`application_type_inbox_name`) — BE-03 §4 | KB 63 §4 `[VERIFIED][INTENT]` (`Inbox.cshtml:42-58`; `ApprovalServices.cs:60-85`) |
| `isRepeatOrder` | Is Repeat Order | select (single) | Tidak | Enum tertutup: `""`=Pilih, `"0"`=tidak, `"1"`=ya. Legacy men-hardcode opsi di view (provenance beda dari filter sebelah — KB 63 BR-AIS-7); rebuild: tetap enum konstanta FE (2 nilai domain stabil), BUKAN lookup — USULAN. | Konstanta FE | KB 63 §4 `[VERIFIED][INTENT]` (`Inbox.cshtml:64-70`); param BE-03 §4 |
| `search` | Pencarian | text | Tidak | Free text; trim; tanpa aturan format klien. Kolom yang di-match ditetapkan BE (minimal `transaction_id`, `debtor_name` — BE-03 §5.3); semantik legacy penuh `[OPEN]` OQ-AIS-06. | — | KB 63 §4 `[VERIFIED][INTENT]` (`Inbox.cshtml:73`) |
| `page` | Halaman | number (pager) | n/a | Integer ≥ 1; FE menonaktifkan next/prev di tepi berdasarkan `total_pages`; **clamp otoritatif di server** (BE-03 §5.3 — menutup OQ-AIS-07; do-not-replicate guard klien-saja KB 63 BR-AIS-8 `[ARTIFACT]`). | — | KB 63 §4 (`Inbox.cshtml:35,204,239-296`) |
| `size` | Baris/halaman | select | Tidak | Enum (mis. 10/20/50). Legacy fixed 5 via konstanta shared (KB 63 BR-AIS-6 `[ARTIFACT]` — pilihan UX bebas). Default USULAN **20** (contoh BE-03 §5.3); keputusan final = OQ-FE03-03. | Konstanta FE | KB 63 BR-AIS-6 (`Commons.cs:18`) |
| (implisit) approver | — | dari konteks auth | ya (server) | **BUKAN input** — TIDAK ada field employee; TIDAK ada fallback `"0"` (KB 63 BR-AIS-2/Edge Case 3 `[ARTIFACT]` do-not-replicate); tanpa sesi → 401. | Session (FE-00) | KB 63 §4; BE-03 §5.3 |

### 5.2 Form Keputusan Komite (SCR-03-04) — request `POST /credit-memos/{memoId}/approval-decision`

| Field | Label | Tipe | Required | Format/Aturan validasi | Sumber options | Sumber |
|---|---|---|---|---|---|---|
| `action` | Keputusan | tersirat dari tombol yang ditekan | **Ya** | Enum tertutup `approve\|reject\|correction` (BE-03 §5.2). Legacy: decision code `StatusApproval` per stage S1 `[VERIFIED]`. | Action bar SCR-03-03 | KB 22 §3a S1 (`sp_approve_cm:167-360`); BE-03 §5.2 |
| `reason_id` | Alasan (kode) | select (single) | **Ya — setiap aksi** | Wajib terisi sebelum submit; opsi **per aksi** — dropdown di-fetch ulang saat dialog dibuka sesuai aksi yang dipilih. | `GET /approval-reasons?action={action}` (padanan `sp_get_approver_reason`) — BE-03 §4 | KB 22 §3a S1 `[VERIFIED]`; BE-03 §4/§5.2 |
| `reason` | Alasan (uraian) | textarea | **Ya — setiap aksi** | Free text, trim, non-empty. Max-length tidak ter-evidensi di legacy → `[OPEN]` OQ-FE03-02 (sementara ikuti batas kolom BE). | — | KB 22 §3a S1 `[VERIFIED]`; BE-03 §5.2 ("`reason_id` + `reason` wajib setiap aksi") |
| `analysis` | Anotasi analisa (opsional) | textarea | Tidak | Opsional; visibilitas conditional — hanya bila reviewer memilih menganotasi worksheet credit-analysis (collapse/expand — USULAN). | — | KB 22 §3a S1 (`analysis/conclusion` optional, conditional) `[VERIFIED]`; BE-03 §5.2 |
| `conclusion` | Kesimpulan (opsional) | textarea | Tidak | Sama dengan `analysis`. | — | idem |
| `memo_id` | — | hidden (dari route) | **Ya** | = `{memoId}` pada path; ikut dikirim di body sesuai kontrak BE-03 §5.2. | Route param §3 | BE-03 §5.2 |
| `step_id` | — | hidden (dari state layar) | **Ya** | Diambil dari step current `pending` yang ditampilkan; bukan input user. | `GET .../approval-steps` | BE-03 §5.2 |
| `actor_employee_id` | — | dari konteks auth | **Ya** | Bukan input user; server memverifikasi = `assigned_employee_id` (assigned-only, **D-09**) dan ≠ maker (**D-01 S11**). FE tidak mengirim pilihan actor. | Session | BE-03 §5.2 |
| `idempotency_key` | — | hidden, generated | **Ya** | UUID per attempt; digenerate saat dialog konfirmasi dibuka; dipakai ulang untuk retry request yang sama (BE idempotent — BE-03 AC-8). | FE | BE-03 §5.2 `[KEPUTUSAN DESAIN BARU]` |

Validasi klien = kenyamanan (disable submit sampai `reason_id` + `reason` terisi); **enforcement
otoritatif di BE** — jangan ulangi pola legacy "aturan hanya di browser" (KB 67 §7 P2 sites;
`_screen-inventory.md` seam #4).

### 5.3 Grid Inbox (SCR-03-01) — kolom (bukan form; census tampilan)

Mapping 1:1 ke item response `GET /approval-inbox` (BE-03 §5.3), yang memetakan kolom grid legacy
(KB 63 §6 `[VERIFIED]` — `Inbox.cshtml:92-176`, `PaginationInboxApprovalModels.cs:1-25`):

| Kolom tampil | Field API | Format | Catatan |
|---|---|---|---|
| Transaction ID | `transaction_id` | string, link kondisional | Link hanya bila `item_type` dikenal routing table §3 (pengganti semantik legacy "`Controller` non-empty" — KB 63 BR-AIS-4 `[INTENT]`). |
| Transaction Date | `transaction_date` | **dd-MM-yyyy** | Format tanggal grid legacy dipertahankan (KB 63 §6 `[VERIFIED]`). |
| Description | `description` | string | — |
| Debtor Name | `debtor_name` | string | Hanya di Inbox (tidak ada di History). |
| PIC NIK / PIC Name | `pic_nik` / `pic_name` | string | Actor step sebelumnya. |
| Next PIC NIK / Next PIC Name | `next_pic_nik` / `next_pic_name` | string | Approver berikutnya (baris di inbox saya ⇒ umumnya saya). |
| Status | `status` | StatusBadge | Set pending kanonik (padanan legacy `V\|0\|E\|P` — BE-03 §5.3); FE hanya MENAMPILKAN, tidak men-drive transisi (KB 63 §8 `[VERIFIED][INTENT]`). |
| (aksi) | `actionable` | tombol "Proses" | Boolean eksplisit dari BE (pengganti `ActionApproved` non-empty — KB 63 BR-AIS-5). |
| (routing) | `item_type`, `memo_id`, `sequence` | tidak ditampilkan | Untuk navigasi & konteks. Field legacy `Action`/`ActionEdit` yang tak pernah dibaca TIDAK dibawa (KB 63 Edge Case 4; BE-03 §5.3) — residual OQ-AIS-04. |

### 5.4 Grid History (SCR-03-02)

Bentuk = §5.3 **minus** `debtor_name`, **minus** `actionable`/action cell (read-only struktural —
KB 63 BR-AIS-5 `[VERIFIED][INTENT]`; BE-03 §5.4), **plus**:

| Kolom tampil | Field API | Format |
|---|---|---|
| Keputusan | `action` | StatusBadge (`approved`/`rejected`/`correction`/`auto_approved_ia`) |
| Waktu Keputusan | `acted_at` | dd-MM-yyyy HH:mm |

### 5.5 Form Filter History (SCR-03-02)

| Field | Tipe | Required | Aturan | Sumber |
|---|---|---|---|---|
| `search` | text | Tidak | Sama dengan §5.1; satu-satunya filter di History (KB 63 §4 `[VERIFIED][INTENT]` — `History.cshtml:31`). | BE-03 §4 `GET /approval-history` |
| `page` / `size` | pager | n/a | Sama dengan §5.1. | idem |

---

## 6. Aturan Interaksi & Staging

- **Tidak ada wizard di layar milik FE-03** — Inbox/History adalah one-shot list-and-dispatch
  (KB 63 §3a `[VERIFIED]`: N/A single-step). Staging multi-role S0→S1→S2/S3/S4 (RFA → review sekuensial →
  terminal) adalah state machine BE (KB 22 §3a; BE-03 §7) — FE **menampilkan** posisinya (stepper §4.3),
  tidak men-drive-nya (KB 63 §8 `[VERIFIED][INTENT]`).
- **Interaksi kunci per aturan**:

| # | Aturan interaksi | Perilaku FE | Sumber |
|---|---|---|---|
| IX-1 | Row navigable hanya bila `item_type` dikenal | Link/knop dirender; selain itu row inert (tanpa link). JANGAN submit URL buatan server (legacy `gotopage` hidden-form POST — KB 63 Edge Case 5 `[ARTIFACT]`). | KB 63 BR-AIS-4; BE-03 §4 |
| IX-2 | Aksi keputusan hanya pada step current pending milik saya | Action bar muncul hanya bila `actionable=true`; bila false → panel view-only + keterangan "Menunggu level sebelumnya / bukan giliran Anda". | KB 63 BR-AIS-5; BE-03 §5.3 |
| IX-3 | `reason_id` + `reason` wajib sebelum submit | Submit disabled sampai keduanya valid; opsi reason di-refetch per `action`. | BE-03 §5.2; KB 22 §3a S1 |
| IX-4 | Konfirmasi eksplisit untuk aksi irreversible | Dialog 2-tombol proceed/cancel; teks konsekuensi per aksi (§4.4). | KB 67 BR-CSB-18 `[INTENT]` |
| IX-5 | Anti double-submit | Tombol disabled + spinner selama in-flight (KB 67 BR-CSB-20) **plus** `idempotency_key` (BE-03 AC-8) — perlindungan ganda; jangan andalkan disable klien saja. | KB 67 BR-CSB-20; BE-03 §5.2 |
| IX-6 | Setelah keputusan sukses | Toast sukses + navigasi kembali ke `/approval/inbox` (item hilang dari inbox karena step sudah di-action); pola flash-notification lintas layar dipertahankan sebagai outcome (KB 63 §5.7) via mekanisme FE-00. | KB 63 §5.7 `[INTENT]` |
| IX-7 | Approve terminal | Response memuat `financial_lock` (BE-03 §5.2) → tampilkan ringkasan lock (OP/LCR/ULI + flag asuransi jiwa/kendaraan + `locked_at`) di layar konfirmasi sukses — outcome STEP 12 `[VERIFIED — doc]`. FE TIDAK menghitung OP/LCR/ULI sendiri (formula `[LOCKED]` milik BE — BR-AC-10). **Catatan konsistensi (OQ-BE03-02 RESOLVED — opsi b)**: eksekutor tulis freeze = **modul 04 sebagai konsumen event `MemoApproved`** (eventually-consistent — BE-03 §5.2 note/O-8; BE-04 §5.3); ringkasan di layar ini berasal dari response 03, sedangkan layar detail CM (FE-04) bisa menampilkan state lock sesaat setelah event terkonsumsi — copy FE JANGAN menjanjikan "terkunci seketika di semua layar". | BE-03 §5.2/O-8; BE-04 §5.3 |
| IX-8 | Correction | Response memuat `voided_future_steps` → stepper §4.3 langsung menandai step tersebut `voided` (visible, redup); pesan: dokumen kembali ke origination Step 1–7 (perbaikan CMO). | BE-03 §5.2/BR-AC-12 |
| IX-9 | Reject | Pesan "Rejected — permanen" (STEP 12); TIDAK menampilkan klaim "aplikasi ditutup" (closure `tr_cas` = `[OPEN]` OQ-AC-01 milik 01 — jangan mengarang copy). | BE-03 BR-AC-13 |
| IX-10 | Filter state preserved | Legacy mempertahankan pilihan filter via hidden field + postback (KB 63 §4); rebuild: state di query string (§3) — outcome sama (filter tidak hilang saat paging), mekanisme baru — USULAN. | KB 63 §4 `[INTENT]` |
| IX-11 | Item IA lane | TIDAK pernah muncul sebagai pending di Inbox (auto-approved saat routing — BE-03 §5.2 varian IA); tampil di riwayat/History dengan badge "Instant Approval" + `ia_policy_id`. Eligibility = `[OPEN]` OQ-MEET-04 — FE tidak menampilkan/menjelaskan aturan eligibility apa pun. | BE-03 §5.2, D-01 S11 |
| IX-12 | Tidak ada jalur super-user | Tidak ada UI "act as"/override untuk step yang bukan milik user (D-09 `[LOCKED]`). | D-09; BE-03 BR-AC-7 |

- **Conditional rendering**: blok `analysis`/`conclusion` collapsed by default (KB 22 §3a S1
  visibility=conditional); panel Indikator Risk menampilkan baris escalation hanya bila flag-nya true
  (mis. `acm_swap_applied` ⇒ callout "Approver final dialihkan ke ACM").

---

## 7. State Tampilan (loading/empty/error/status-driven display)

| State | Perilaku (outcome yang dipertahankan / diperbaiki) | Sumber |
|---|---|---|
| **Loading** | Indikator loading membungkus SETIAP request (list, lookup, decision) — outcome KB 67 BR-CSB-8 `[INTENT]`; mekanisme bebas (skeleton row untuk tabel, spinner tombol untuk aksi — USULAN). | KB 67 BR-CSB-8 |
| **Empty vs gagal** | Dua state DIBEDAKAN: "Data tidak tersedia" (sukses, 0 item) vs "Gagal memuat data dari server" + tombol Coba Lagi. JANGAN satu pesan untuk keduanya. | KB 67 BR-CSB-11 `[INTENT]` |
| **Error terstruktur** | Branch pada `code` terstruktur dari BE — `INVALID_MEMO_STATUS`, `HIERARCHY_UNRESOLVED`, `APPROVER_IDENTITY_DENIED`, `STEP_NOT_ACTIONABLE` (BE-03 §5) → pesan Bahasa Indonesia + `correlation_id` ditampilkan untuk support. **DILARANG** string-sniffing pesan error (legacy match literal "Object reference not set..." — KB 63 Edge Case 2 `[ARTIFACT]`). | KB 63 Edge Case 2; BE-03 §8 anti-pattern |
| **403 identity/self-approval** | Pesan spesifik: "Anda bukan approver yang ditugaskan untuk step ini / self-approval tidak diizinkan" — tanpa menyebut jalur override (tidak ada, D-09). | BE-03 §5.2 |
| **409 STEP_NOT_ACTIONABLE** | Item sudah di-action pihak lain/di tab lain → tawarkan refresh detail; jangan silent-fail (do-not-replicate silent swallow — KB 67 BR-CSB-10 `[ARTIFACT]`). | KB 67 BR-CSB-10; BE-03 §5.2 |
| **401 / tanpa sesi** | Redirect ke login via guard terpusat FE-00; TIDAK merender grid kosong dengan sentinel `"0"` (KB 63 Edge Case 3 `[ARTIFACT]`). | KB 63 BR-AIS-2; KB 60 Edge Case 3 |
| **Status-driven display** | StatusBadge enum tertutup: `pending` (netral), `approved` (positif), `rejected` (destruktif), `correction` (warning), `voided` (redup), `auto_approved_ia` (info + ikon otomatis). Memo terminal (`approved`/`rejected`) ⇒ action bar disembunyikan permanen; `correction` ⇒ banner "Menunggu perbaikan CMO (Step 1–7)". | BE-03 §7.1; KB 63 §8 |
| **Setiap request punya failure path yang terlihat** | Kontrak komponen non-negotiable: tidak ada call site tanpa handler gagal yang user-visible (8/24 call sites legacy silent — KB 67 Edge Case 7 `[ARTIFACT]` do-not-replicate). | KB 67 BR-CSB-10 |

---

## 8. Kontrak Konsumsi API (endpoint BE yang dipakai per screen)

Seluruh endpoint di bawah **ADA di BE-03 §4** (bentuk request/response: BE-03 §5). Tidak ada endpoint
lain yang boleh diasumsikan; kebutuhan yang tidak terpenuhi dicatat sebagai **GAP di §11**.

| Screen | Endpoint (BE-03 §4) | Dipakai untuk | Rujukan kontrak |
|---|---|---|---|
| SCR-03-01 | `GET /approval-inbox` (`page`, `size`, `applicationTypeId`, `isRepeatOrder`, `search`; `approver` dari auth) | Data grid + envelope pagination | BE-03 §5.3 |
| SCR-03-01 | `GET /approval-inbox/application-types` | Opsi dropdown Tipe Aplikasi | BE-03 §4 |
| SCR-03-02 | `GET /approval-history` (`page`, `size`, `search`; `actor` dari auth) | Data grid history (tanpa action slot) | BE-03 §5.4 |
| SCR-03-03 | `GET /credit-memos/{memoId}/approval-steps` | Rantai step + step current pending (stepper) | BE-03 §4 |
| SCR-03-03 | `GET /credit-memos/{memoId}/approval-progress` | View progres level (aktif/selesai) | BE-03 §4 |
| SCR-03-03 | `GET /credit-memos/{memoId}/approval-history` | Audit timeline maker-checker (incl. IA & flag migrasi legacy) | BE-03 §4 |
| SCR-03-04 | `GET /approval-reasons?action={action}` | Opsi `reason_id` per aksi | BE-03 §4 |
| SCR-03-04 | `POST /credit-memos/{memoId}/approval-decision` | Eksekusi approve/reject/correction; response memuat `next_pending_step` / `financial_lock` / `voided_future_steps` | BE-03 §5.2 |

Catatan konsistensi kontrak:
- `item_type` enum tertutup + `actionable` boolean adalah kontrak pengganti Controller/Action string &
  `ActionApproved` legacy — sudah dinyatakan di BE-03 §4 (catatan routing FE) dan §5.3.
- FE **tidak** memanggil `POST /credit-memos/{memoId}/committee-routing` (system/event-handler — BE-03
  §4) dan **tidak** memanggil `/ia-policies` (USULAN BE, kepemilikan layar `[OPEN]` GAP-FE03-03).
- Kebutuhan layar yang **TIDAK** tersedia di BE-03: detail aplikasi/CM untuk panel header (GAP-FE03-01)
  dan `risk_tier_resolution` via GET (GAP-FE03-02) — lihat §11; jangan mengarang endpoint.

---

## 9. Acceptance Criteria (Given/When/Then)

**AC-FE-1 — Inbox scoped ke approver login, tanpa sentinel**
- **Given** approver `EMP-A` login dengan 1 item pending; **When** membuka `/approval/inbox`;
- **Then** grid menampilkan item `EMP-A` saja (kolom §5.3 lengkap, tanggal `dd-MM-yyyy`), TANPA kontrol
  memilih employee lain (KB 63 BR-AIS-1); **dan Given** sesi tidak valid **Then** redirect login —
  TIDAK ada request dengan employee `"0"` (KB 63 Edge Case 3 `[ARTIFACT]`).

**AC-FE-2 — Filter & paging**
- **Given** inbox berisi item beragam; **When** user memilih Tipe Aplikasi (opsi dari
  `GET /approval-inbox/application-types`), Repeat Order = `1`, mengisi search, lalu berpindah halaman;
- **Then** request membawa `applicationTypeId`, `isRepeatOrder=1`, `search`, `page` (BE-03 §5.3); pilihan
  filter TIDAK hilang saat paging (IX-10); di halaman terakhir tombol next disabled; out-of-range tidak
  mungkin dikirim dari kontrol UI dan bila terjadi (deep-link) FE merender hasil clamp server.

**AC-FE-3 — Routing table eksplisit**
- **Given** row dengan `item_type=CM_COMMITTEE`; **When** diklik; **Then** navigasi ke
  `/approval/cm/{memo_id}`. **Given** row dengan `item_type` tak dikenal; **Then** row inert (tanpa
  link), tanpa error runtime, telemetri warning tercatat — TIDAK ada URL yang dibangun dari string
  server (KB 63 Edge Case 5 `[ARTIFACT]`).

**AC-FE-4 — Gating aksi via `actionable`**
- **Given** detail memo dengan `actionable=false` (bukan giliran user); **When** layar dirender;
- **Then** action bar tidak muncul, panel progres/riwayat tetap tampil; **Given** `actionable=true`
  **Then** tombol Approve/Correction/Reject tampil.

**AC-FE-5 — Dialog keputusan: validasi reason**
- **Given** dialog Approve terbuka; **When** `reason_id` atau `reason` kosong; **Then** submit disabled;
- **When** keduanya terisi dan user mengonfirmasi pada dialog 2-tombol; **Then** `POST
  .../approval-decision` terkirim sekali dengan `action`, `reason_id`, `reason`, `step_id`,
  `idempotency_key` (BE-03 §5.2); tombol busy selama in-flight (IX-5).

**AC-FE-6 — Approve terminal menampilkan financial lock**
- **Given** user adalah approver level terakhir; **When** Approve sukses dengan response
  `memo_committee_state=approved` + `financial_lock`;
- **Then** layar sukses menampilkan OP/LCR/ULI (format Rupiah formatter tunggal), status lock asuransi
  jiwa & kendaraan, `locked_at`; TANPA menampilkan/menjanjikan nomor PO (PO minting = modul 04, D-01
  S13; BE-03 §5.2 note).

**AC-FE-7 — Correction menandai voided eksplisit**
- **Given** memo dengan step 2 & 3 pending; **When** Correction sukses (`voided_future_steps` berisi
  keduanya); **Then** stepper menampilkan step tsb. ber-status `voided` (redup, tetap terlihat), banner
  "kembali ke origination Step 1–7 untuk perbaikan CMO" — bukan menyembunyikan step (BE-03 BR-AC-12).

**AC-FE-8 — Error terstruktur, bukan string-sniffing**
- **Given** BE mengembalikan 403 `APPROVER_IDENTITY_DENIED` (mis. percobaan self-approval, D-01 S11);
- **Then** FE menampilkan pesan spesifik ber-`correlation_id` hasil branch pada `code` — tidak ada logic
  yang mencocokkan teks pesan error (KB 63 Edge Case 2 `[ARTIFACT]`); **dan** untuk 409
  `STEP_NOT_ACTIONABLE` FE menawarkan refresh detail.

**AC-FE-9 — History read-only + jejak IA/legacy**
- **Given** employee dengan riwayat berisi keputusan manusia, satu item `auto_approved_ia`, dan satu
  baris migrasi `legacy_super_user_override=true`; **When** membuka `/approval/history` dan detail
  riwayatnya;
- **Then** tidak ada tombol aksi apa pun di History (KB 63 BR-AIS-5); item IA berbadge "Instant
  Approval" + `ia_policy_id`; baris legacy berbadge read-only "Override (legacy)" dan pola itu tidak
  pernah muncul pada data baru (D-09).

**AC-FE-10 — Empty vs gagal dibedakan; setiap request punya failure path**
- **Given** BE mengembalikan 0 item; **Then** "Data tidak tersedia". **Given** request list gagal
  (network/5xx); **Then** state "Gagal memuat data" + Coba Lagi — dua state berbeda (KB 67 BR-CSB-11);
  tidak ada call site tanpa feedback gagal (KB 67 Edge Case 7 `[ARTIFACT]`).

**AC-FE-11 — Responsive**
- **Given** viewport mobile (≤ 640px); **When** membuka ketiga screen; **Then** grid ber-render sebagai
  card list tanpa horizontal scroll halaman, action bar keputusan sticky & dapat dijangkau, dialog
  keputusan full-screen sheet (USULAN); di desktop tabel penuh. (NFR rebuild; disiplin responsive
  mengikuti konvensi FE-00.)

**AC-FE-12 — Indikator risk & plafond auditable**
- **Given** memo dengan `exposure_escalation_applied=true` + `acm_swap_applied=true` (data tersedia per
  resolusi GAP-FE03-02); **When** detail dirender; **Then** panel indikator menampilkan tier final
  Very-High-Risk, `aggregate_op` vs `op_threshold_applied` (Rupiah), dan callout ACM-swap — nilai
  threshold TIDAK di-hardcode di FE (OQ-AC-02 `[OPEN]`).

---

## 10. Dependency

### 10.1 Modul BE

| Dependency | Bentuk | Catatan |
|---|---|---|
| **BE-03-approval-committee** | Seluruh endpoint §8 | Kontrak request/response = BE-03 §5; error codes = BE-03 §5.1–5.2. |
| BE-02 / BE-04 (via BE-03) | tidak langsung | FE-03 TIDAK memanggil endpoint 02/04 untuk layar ini kecuali GAP-FE03-01 diputuskan mengarah ke sana (§11). |

### 10.2 Shared components dari FE-00 (jangan duplikasi)

| Komponen FE-00 | Dipakai di | Grounding konsolidasi |
|---|---|---|
| Auth guard / session context (employee, branch) | semua screen | KB 60 BR-SHELL-1 `[LOCKED]`, BR-SHELL-3 `[INTENT]`; satu guard terpusat (KB 60 Edge Case 3 `[ARTIFACT]` do-not-replicate 3 guard setengah jadi) |
| DataTable + Pagination (satu grid contract, empty-vs-failed) | SCR-03-01/02 | KB 67 BR-CSB-11 `[INTENT]` |
| ConfirmDialog 2-tombol (satu vocabulary, Bahasa Indonesia) | SCR-03-04 | KB 67 BR-CSB-18/19 `[INTENT]` |
| Toast/flash notification lintas navigasi | IX-6 | KB 63 §5.7; KB 67 BR-CSB-9 |
| StatusBadge (enum status kanonik) | semua screen | BE-03 §7.1 |
| Currency formatter tunggal (id-ID) | panel indikator & financial lock | KB 67 BR-CSB-6/Edge Case 3 `[INTENT]` (satu konvensi, tanpa default menyimpang) |
| Loading overlay / skeleton + button-busy | semua request | KB 67 BR-CSB-8/BR-CSB-20 `[INTENT]` |
| Routing table `item_type → route` (registry) | SCR-03-01/02 | BE-03 §4 catatan; KB 63 Edge Case 5 |

### 10.3 Modul FE lain

- **FE-01/FE-02/FE-04/FE-05** — pemilik route tujuan `item_type` non-komite yang muncul di Inbox
  generik; FE-03 tidak merender detail item tersebut.
- **FE-02** — pemilik tampilan ladder credit-analyst (`Level1..Level10`) bila dipertahankan
  (KB 63 OQ-AIS-02); FE-03 hanya memiliki stepper komite CM.

### 10.4 Eksternal

- Deliverable arsitektur **ITEC Bank Mega** (**D-11**) — topologi deployment/BFF/gateway memengaruhi
  base URL & auth transport FE; PRD ini menahan asumsi di level kontrak resource.

---

## 11. Keputusan Dibutuhkan (Open Questions)

**GAP kontrak (layar butuh endpoint yang TIDAK ada di BE-03 §4 — jangan dikarang):**

| ID | Prioritas | Pertanyaan / GAP | Dampak |
|---|---|---|---|
| **GAP-FE03-01** | P1 | **Endpoint detail aplikasi/CM untuk panel header SCR-03-03** (identitas debitur, ringkasan finansial pre-lock, konteks aplikasi) tidak ada di BE-03 §4 — subjek `CREDIT_MEMO`/`CREDIT_APPLICATION` dimiliki 02/04/01 (BE-03 §3.2). Opsi: (a) FE konsumsi read-endpoint modul 02/04 (belum didefinisikan di PRD BE terkait), (b) BE-03 mengekspos read-model ringkas `GET /credit-memos/{memoId}/committee-summary` (USULAN). Diputus bersama pemilik umbrella + BE-02/04. | Tanpa ini SCR-03-03 hanya bisa menampilkan field yang ada di item inbox + steps. |
| **GAP-FE03-02** | P1 | **`risk_tier_resolution` (indikator risk & plafond) via GET** — di BE-03 bentuk ini hanya terdokumentasi pada response `POST /committee-routing` (§5.1) yang system-triggered. USULAN: sertakan `risk_tier_resolution` pada response `GET /credit-memos/{memoId}/approval-steps` (jejak auditable yang sama). | Panel §4.3(2) & AC-FE-12 bergantung padanya. |
| **GAP-FE03-03** | P2 | **Kepemilikan layar admin IA Policy** — BE-03 §4 mengusulkan `GET/POST /ia-policies` (role "Risk policy admin" `[OPEN]`). Modul FE mana yang memiliki layarnya (FE-03? master-data/risk-admin terpisah)? Aturan eligibility sendiri = OQ-MEET-04. | Scope FE-03 saat ini: TIDAK termasuk. |
| **GAP-FE03-04** | P2 | **Layar/afordance deviasi MP (Memo Persetujuan)** — BE-03 memiliki `trx_deviation` + `cfg_deviation_rule` dengan target-state "input MP via UI ber-maker-checker" (BE-03 §3.1.5; writer legacy tidak ditemukan di dump — OQ-GAP-04), dan `EFF_RATE_MIN` dievaluasi 03 saat routing. TIDAK ada endpoint deviasi di BE-03 §4 dan TIDAK ada screen deviasi di FE-03. Diputus bersama pemilik umbrella + BE-03: (a) siapa pemilik layar input MP (FE-03? master-data/risk-admin?), (b) apakah SCR-03-03 menampilkan jejak deviasi ter-approve pada memo. JANGAN mengarang layar/endpoint sebelum diputus. | Tanpa keputusan: input deviasi MP tidak punya jalur UI di rebuild; panel Detail Komite tidak menampilkan deviasi yang mempengaruhi routing/rate. |

**OQ diwarisi KB/BE yang menyentuh FE-03:**

| OQ-ID | Prioritas | Pertanyaan | Dampak FE |
|---|---|---|---|
| **OQ-AIS-01** | P1 | Apakah keenam tujuan dispatch legacy membentuk satu kontrak decision-entry konsisten sehingga SCR-03-03/04 bisa menjadi komponen "act on this item" generik lintas `item_type`, atau butuh N layar per domain? (KB 63 §10.) | Menentukan reusability SCR-03-03 di luar `CM_COMMITTEE`. |
| **OQ-AIS-02** | P1 | Kepemilikan tampilan hierarki: apakah visualisasi scheme `Level1..Level10` (milik area credit-analyst) tetap di FE-02, dan stepper komite CM cukup dari `approval-steps` (usulan PRD ini)? Jangan konflasi dua ladder (BE-03 BR-AC-6). | Konfirmasi pembagian FE-02 vs FE-03. |
| **OQ-AIS-04** | P2 | Field legacy `Action`/`ActionEdit` tak pernah dibaca view — BE-03 §5.3 memutuskan tidak membawanya; residual: konfirmasi tidak ada consumer lain sebelum final. | Bila ternyata dipakai: butuh slot aksi ke-3/４ di row. |
| **OQ-AIS-06** | P3 | Semantik kolom `search` legacy — BE-03 §5.3 menetapkan minimal `transaction_id` + `debtor_name` `[KEPUTUSAN DESAIN BARU]`; residual: apakah ada kolom lain yang selama ini di-match dan diandalkan user. | Placeholder & help-text kolom pencarian. |
| **OQ-BE03-01** | P2 | Coverage saat assigned approver absen (super-user dihapus D-09): delegasi/reassignment/SLA-escalation? | Bila ada reassignment → FE-03 butuh surface tambahan (belum dispesifikasikan — jangan dibangun dulu). |
| **OQ-MEET-04** | P2 | Eligibility IA lane per product/plafond (D-01 S11). | FE hanya menampilkan jejak `auto_approved_ia`; tidak menampilkan aturan. |
| **OQ-AC-01** | P1 | Apakah committee Reject menutup `tr_cas`? (milik 01/BE.) | Menentukan copy pesan pasca-Reject (IX-9) — sementara netral. |
| **OQ-AC-02** | P1 | Threshold aggregate-exposure (35jt kode vs 30jt komentar). | FE menampilkan `op_threshold_applied` dari BE; tidak hardcode. |
| **OQ-CORE-03** | P1 | Arti bisnis penuh OP/ULI/LCR (label & tooltip layar). "Plafond Hutang Pokok (OP)" = ekspansi parsial D-01 S10. | Label panel indikator & ringkasan lock. |

**OQ baru FE-03:**

| OQ-ID | Prioritas | Pertanyaan |
|---|---|---|
| **OQ-FE03-01** | P2 | **Visibilitas `aggregate_op`** (exposure lintas aplikasi, termasuk data spouse by NIK — BE-03 §3.3) kepada SEMUA level approver di panel indikator: apakah ada batasan privacy/kebijakan yang mengharuskan masking/level-gating? Resolusi: risk & compliance owner. |
| **OQ-FE03-02** | P3 | Max-length & aturan format field `reason` free text (tidak ter-evidensi di legacy FE maupun KB 22). Resolusi: samakan dengan constraint kolom BE. |
| **OQ-FE03-03** | P3 | Page size default listing (legacy fixed 5 = `[ARTIFACT]` bebas diubah; contoh BE-03 memakai 20). Resolusi: UX owner; murni pilihan desain. |

> **Sudah tertutup — jangan diangkat ulang**: OQ-AIS-07 (server-side page clamp — DITUTUP oleh BE-03
> §5.3 guard eksplisit); OQ-AIS-05 (gate `[Authorize]` legacy — dijawab desain rebuild: guard terpusat
> FE-00 + otorisasi BE, KB 63 BR-AIS-3 `[ARTIFACT]` tidak direplika); eksistensi IA lane & requirement
> no-self-approval (D-01 S11); role super-user (DIHAPUS, D-09 `[LOCKED]`); bahasa stack (D-12 `[LOCKED]`).
