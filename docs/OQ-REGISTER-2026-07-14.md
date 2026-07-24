# Register Pertanyaan Terbuka (Open Question Register) — Revamp Core Acquisition MACF

**Tanggal**: 14 Juli 2026
**Dari**: Tim analisa & penyusun PRD rebuild Acquisition (FINCORE → core baru)
**Untuk**: Tim Bisnis/Product MACF (COBS/SBDV), ITEC Bank Mega, DBA/Data Owner, Risk & Compliance, Tim MOOFI, Ops Cabang

## Konteks

MACF sedang me-revamp core system **Acquisition** (aplikasi kredit dari intake sampai aktivasi kontrak/NPP) dari sistem legacy FINCORE (.NET + SQL Server) ke stack baru (backend **Java**, frontend **Next.js**), mengikuti keputusan meeting New Coresystem MACF (29/04/26) dan PRD Notes Juli 2026. Dokumen analisa menyeluruh atas sistem legacy sudah selesai: 16 PRD modul (backend BE-00..BE-07 dan frontend FE-00..FE-07) plus rencana migrasi data. Butir-butir di bawah adalah **keputusan atau informasi yang kami butuhkan dari tim Bapak/Ibu** untuk melanjutkan desain dan pembangunan. Dokumen ini adalah **FORM JAWABAN**: sebagian besar butir sudah kami sertai pilihan + rekomendasi, sehingga cukup dicentang atau dikoreksi.

**Cara mengisi (3 langkah)**:
1. Pada tiap butir, **centang SATU opsi** (`[x]`) — atau centang **X. Jawaban lain** bila jawaban Anda di luar opsi yang tersedia. Untuk butir bertipe *informasi*, langsung isi informasi yang diminta di kolom jawaban.
2. Isi kolom **JAWABAN TIM** (pilihan + alasan singkat) dan **Dijawab oleh / tanggal**.
3. Kembalikan file/dokumen ini ke tim penyusun PRD — setiap jawaban akan dimasukkan kembali ke PRD melalui register resolusi per-ID (traceable, tidak ada yang "diselesaikan diam-diam").

**Arti prioritas**: **P1** = memblokir fase awal pembangunan/migrasi · **P2** = dibutuhkan sebelum modul terkait dibangun · **P3** = klarifikasi, tidak memblokir. **Jawaban P1 paling ditunggu.**

## Ringkasan jumlah

| Tim tujuan | P1 | P2 | P3 | Total |
|---|---|---|---|---|
| A. Bisnis / Product MACF (COBS/SBDV) | 16 | 49 | 20 | 85 |
| B. ITEC Bank Mega (arsitektur/infra/integrasi) | 14 | 7 | 2 | 23 |
| C. DBA / Data Owner | 11 | 10 | 12 | 33 |
| D. Risk & Compliance | 5 | 16 | 0 | 21 |
| E. Tim MOOFI (mobile) | 2 | 5 | 1 | 8 |
| F. Ops Cabang | 1 | 11 | 6 | 18 |
| **Total** | **49** | **98** | **41** | **188** |

> *Rev. 2026-07-22*: dump `FC_MSTAPP_MCF` diterima → OQ-PRODASSET-05 selesai (entry jadi catatan resolusi,
> keluar dari hitungan P1 C); pertanyaan baru OQ-EXTMASTERS-07 [P1] & OQ-EXTMASTERS-08 [P2] masuk bagian C.

> Satu butir dapat memuat beberapa ID yang satu tema (ditulis bergandengan). Register arkeologi lengkap
> (baseline 247 OQ hasil ekstraksi knowledge-base legacy) tersimpan di KB internal
> `.mega-sdd/knowledge-base/` — dokumen ini adalah **kurasi yang ter-register di layer PRD/dokumen**,
> yaitu yang benar-benar memblokir atau menyentuh keputusan stakeholder.
>
> ID yang sudah **RESOLVED** (a.l. OQ-REG-06 fail-closed, OQ-MCP-01, OQ-AC-01/02, OQ-ACQCAS-01/02,
> OQ-GT-01/02, OQ-PRODASSET-06, OQ-COLL-01, OQ-NPP-03, OQ-CRSCORE-11, OQ-BE03-02/04, OQ-VTL-06) **tidak
> dimasukkan** — status otoritatif mengikuti register payung `BE-00 §11`.
>
> **Update 2026-07-22** (dump `FC_MSTAPP_MCF` diterima dari DBA): **OQ-PRODASSET-05 & OQ-REF-04 &
> OQ-MASTERDATA-03 RESOLVED** (entry PRODASSET-05 ditinggal sebagai catatan resolusi); **OQ-EXTMASTERS-01
> tersisa bagian ownership + linked-server**; **OQ-MASTERDATA-02 premisnya ter-koreksi** (baca ulang
> entry-nya); **2 pertanyaan BARU untuk DBA: OQ-EXTMASTERS-07 [P1] & OQ-EXTMASTERS-08 [P2]** (bagian C).

---

## A. Bisnis / Product MACF (COBS / SBDV)

Keputusan kebijakan produk, alur bisnis, semantik istilah, dan fitur/layar.

### A — Prioritas P1

### [OQ-MEET-06] (P1) — Kami butuh matriks produk × step: daftar lengkap produk pembiayaan MACF dan step mana (dari flow target 15 step) yang berlaku/berbeda per produk.

**Konteks**: keputusan meeting D-07 menyatakan flow 15-step harus didefinisikan per produk; seluruh PRD saat ini baru membedakan lini motor vs mobil secara generik. Memblokir annex PRD per-produk dan hampir semua konfigurasi step/layar per produk. *(Sumber: `_MEETING-DECISIONS-2026-07.md`; seluruh `BE-*/FE-* §11`.)*

**Informasi yang diminta** (butir tipe informasi):
- Daftar produk MACF aktif (nama + kode).
- Per produk: step 1–15 mana yang berlaku / dilewati / bervariasi (termasuk pengecualian gate khusus mobil: usia kendaraan, usia peminjam, DP%).

**Yang perlu disiapkan untuk menutup gap ini**: workshop 1 sesi dengan product owner (COBS/SBDV) + matriks produk×step terisi.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CRSCORE-10] (P1) — Di legacy ada dua jalur kerja analisa kredit: catatan awal 5C oleh CMO, dan workstation penuh Credit Analyst. Apakah keduanya langkah berurutan dalam SATU proses, atau dua kanal untuk lini produk berbeda?

**Konteks**: bukti layar legacy condong "per-produk" (motor vs mobil, dua layar independen) tetapi belum konklusif; menentukan desain proses & layar analisa kredit. *(Sumber: `BE-02 §11`; `FE-02 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Dua kanal untuk lini produk berbeda (motor vs mobil) ← **REKOMENDASI KAMI** (sesuai bukti layar; usulan baru — belum tertulis sebagai default) (konsekuensi: dua konfigurasi workstation per lini)
- [ ] **B.** Langkah berurutan satu lifecycle (CMO menulis dulu, analyst melanjutkan) (konsekuensi: satu proses dengan dua tahap aktor)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: walkthrough singkat dengan analis/CMO cabang tentang praktik nyata per lini produk.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CORE-03 / OQ-CMPO-02] (P1) — Apa definisi bisnis resmi angka OP, ULI, LCR (dan varian "Ost*") yang dibekukan saat finalisasi Credit Memo — dan apakah angka ini direkonsiliasi ke General Ledger?

**Konteks**: MoM meeting menyebut OP = "Plafond Hutang Pokok" (indikasi, bukan definisi); ada dua pemakaian OP (total exposure untuk routing komite vs nilai per-memo yang dibekukan). Menentukan label layar, formula freeze, dan routing komite. *(Sumber: `BE-03 §11`; `BE-04 §11`; `FE-03 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** GL-reconciled → formula kami kunci `[LOCKED]`, tidak boleh berubah di rebuild ← **REKOMENDASI KAMI** (default paling aman) (konsekuensi: formula legacy dipertahankan persis)
- [ ] **B.** Tidak GL-reconciled → formula boleh didesain ulang asal outcome bisnis terjaga
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: definisi tertulis OP/ULI/LCR + varian Ost* dari domain expert finance.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-ASM-01 / OQ-ASM-02] (P1) — Pada tangga approval credit-analyst berjenjang: apa arti "Reject" oleh approver yang BUKAN level terakhir, dan koreksi dari approver puncak dikembalikan ke siapa?

**Konteks**: kode legacy ambigu. Catatan: di komite CM, Reject sudah jelas terminal — pertanyaan ini khusus tangga credit-analyst. *(Sumber: `BE-00 §11`; `BE-03 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Reject di level mana pun = tolak final + wajib alasan; koreksi selalu kembali ke maker asal ← **REKOMENDASI KAMI** (usulan baru — belum tertulis di dokumen sumber) (konsekuensi: paling sederhana & konservatif)
- [ ] **B.** Reject non-final = eskalasi ke level berikutnya (konsekuensi: butuh aturan eskalasi eksplisit)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi praktik nyata dari pemilik kebijakan approval kredit.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-PRD01-01] (P1) — Saat sebuah NIK sudah punya aplikasi yang sedang berjalan, apakah input aplikasi baru cukup di-link ke master customer, atau draft kedua harus DIBLOKIR?

**Konteks**: keputusan meeting mensyaratkan "deduplication lock" NIK di step pertama; detail perilaku belum diputuskan. *(Sumber: `BE-01 §11`; `FE-01 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Blokir draft kedua dengan pesan "aplikasi duplikat sedang berjalan" (HTTP 409) ← **REKOMENDASI KAMI** (default tertulis di PRD) (konsekuensi: cabang tidak bisa membuka 2 aplikasi paralel untuk NIK sama)
- [ ] **B.** Hanya link-to-master; draft paralel diizinkan (konsekuensi: risiko duplikasi proses berulang seperti legacy)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: sign-off pemilik proses intake.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CASFE-04] (P1) — Layar legacy memaksa setiap aplikasi baru bertipe "Corporate" dengan sumber kredit terkunci (credit_source=1). Kebijakan bisnis yang disengaja, atau restriksi tidak sengaja?

**Konteks**: menentukan nilai default & pilihan tipe customer pada form intake baru (layar pertama yang dipakai semua cabang). *(Sumber: `BE-01 §11`; `FE-01 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Restriksi tidak disengaja → sistem baru membiarkan user memilih tipe customer ← **REKOMENDASI KAMI** (usulan baru — belum tertulis di dokumen sumber)
- [ ] **B.** Kebijakan bisnis → pertahankan default Corporate terkunci (konsekuensi: perilaku legacy ditiru)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi kebijakan dari product owner intake.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CMPOFE-01] (P1) — Di legacy, PO otomatis tercetak + ter-email saat approve HANYA untuk lini motor. Apakah sistem baru harus auto-print, dan untuk lini mana?

**Konteks**: menentukan alur kerja cabang pasca-approve. *(Sumber: `BE-04 §11`; `FE-04 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Tanpa auto-print — cetak PO selalu aksi manual untuk kedua lini ← **REKOMENDASI KAMI** (default tertulis: sampai dijawab, sistem baru TIDAK auto-print)
- [ ] **B.** Auto-print + email untuk KEDUA lini (konsekuensi: butuh failure-policy cetak/email otomatis)
- [ ] **C.** Pertahankan perilaku legacy: auto-print hanya motor
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi product owner apakah pola motor-only itu disengaja.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CMPOFE-02] (P1) — Tombol "Print PO" di dua lini membaca field status yang BERBEDA (mobil: status analisa; motor: status memo). Field mana acuan sah boleh-tidaknya cetak?

**Konteks**: menentukan guard tombol Print & kolom listing PO. *(Sumber: `BE-04 §11`; `FE-04 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Satu status kanonik dari backend untuk kedua lini ← **REKOMENDASI KAMI** (usulan baru — belum tertulis di dokumen sumber)
- [ ] **B.** Pertahankan dua acuan berbeda per lini (sebutkan alasannya di kolom jawaban)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi pemilik proses CM/PO: status mana yang selama ini dianggap sah.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CMPOFE-04 / OQ-CMPO-10] (P1) — Layar legacy mengirim status disposisi "V (Verify)"/"C" lewat aksi approval yang sama, tetapi kami tidak menemukan bukti nilainya tersimpan/dipakai. Status V/R nyata dipakai operasional, atau sisa fitur mati?

**Konteks**: menentukan enum status memo yang ditampilkan ke user (jangan menampilkan state hantu). *(Sumber: `BE-04 §11`; `FE-04 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Sisa fitur mati → tidak dibawa/dirender di sistem baru ← **REKOMENDASI KAMI** (default tertulis: FE tidak merender state yang tak pernah tercapai)
- [ ] **B.** Dipakai operasional → jelaskan prosesnya di kolom jawaban (konsekuensi: state ditambahkan ke desain)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi user cabang/HO yang memakai status Verify/Review.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-GAP-01] (P1) — Channel intake "Pooling Order / OMA" masih dipakai produksi? Dipertahankan di sistem baru, atau seluruh intake dikonsolidasi ke aplikasi mobile MOOFI?

**Konteks**: 8 stored procedure masih aktif, tetapi sebagian relasi sudah dinonaktifkan di kode — indikasi channel sedang transisi. Menentukan scope modul intake Phase 1. *(Sumber: `gap-entities.md §4`; `BE-00/BE-01 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Konsolidasi intake ke MOOFI; data Pooling/OMA lama diarsipkan read-only ← **REKOMENDASI KAMI** (salah satu opsi yang tertulis di PRD; berlaku bila tidak ada pengguna aktif)
- [ ] **B.** Channel Pooling/OMA dipertahankan → sistem baru membangun layar + API-nya (konsekuensi: tambahan scope Phase 1)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: informasi siapa pengguna layar OMA sekarang + volume order via channel ini.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-BE07-01] (P1) — Kontrol BARU "maker-checker" untuk perubahan master data (perubahan harus di-approve orang kedua): master mana saja yang wajib, dan siapa checker per master?

**Konteks**: legacy tidak punya kontrol ini; penambahan governance yang butuh sign-off bisnis/COBS. Menentukan layar review perubahan master & alur admin. *(Sumber: `BE-07 §11`; `FE-07 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Mulai dari master berdampak finansial/berisiko (dealer, bank, hierarki approval, user); checker = Kepala Cabang untuk scope cabang, role HO untuk scope nasional ← **REKOMENDASI KAMI** (usulan baru — belum tertulis di dokumen sumber)
- [ ] **B.** Semua master yang bisa di-CRUD wajib maker-checker (konsekuensi: alur admin lebih berat)
- [ ] **C.** Tanpa maker-checker (paritas legacy) (konsekuensi: kontrol governance baru dibatalkan)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: daftar resource + penunjukan checker per resource dari COBS.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-BE07-03] (P1) — Sensus role dari meeting hanya mencakup role CABANG (CMO, Marketing Head, Credit Analyst, Kepala Cabang, Credit/Admin). Role kantor pusat (checker master data HO, reviewer Compliance/AML, Area/Regional Head) ditambahkan atau tidak — dan siapa yang menetapkannya?

**Konteks**: kami tidak menambah role diam-diam (default tertulis). Menentukan enum role, akses layar master, dan chain approval lintas kantor. *(Sumber: `BE-07 §11`; `FE-07 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Tambah role HO minimal (checker master HO + reviewer Compliance) dengan daftar resmi dari governance ← **REKOMENDASI KAMI** (usulan baru — belum tertulis di dokumen sumber)
- [ ] **B.** Tidak ada role HO — semua fungsi di level cabang (konsekuensi: master nasional & review compliance tidak punya aktor)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: daftar role HO resmi + pemilik governance-nya.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-NPPVTL-01] (P1) — Keputusan approve/reject verifikasi telepon (Vertel) dieksekusi dari mana: inbox approval terpusat, layar Vertel sendiri, atau keduanya?

**Konteks**: di legacy layar approval Vertel RUSAK (tombol keputusan tidak pernah muncul), jadi praktik nyata tidak terbaca dari sistem lama; arah fix teknis sudah ditetapkan (tombol muncul berdasarkan flag "pending approver" dari server). *(Sumber: `BE-06 §11`; `FE-06 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Satu surface keputusan di inbox terpusat; layar Vertel untuk input/detail ← **REKOMENDASI KAMI** (usulan baru — belum tertulis di dokumen sumber)
- [ ] **B.** Keputusan di layar Vertel saja
- [ ] **C.** Keduanya (inbox + layar Vertel) (konsekuensi: dua surface harus konsisten)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi alur kerja Kepala Cabang sebagai approver Vertel.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CSB-01 / OQ-CSB-10] (P1) — Aturan pembulatan & presisi angka uang: di mana aturan otoritatifnya, dan bolehkah kami menetapkan SATU konvensi tampilan uang untuk semua layar?

**Konteks**: legacy tidak konsisten antar layar; angka uang menyentuh dokumen kontrak & jurnal. *(Sumber: `FE-00 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Satu konvensi server-side untuk seluruh field uang (tampilan seragam) ← **REKOMENDASI KAMI** (arah yang sudah diusulkan di PRD FE-00)
- [ ] **B.** Konvensi berbeda per modul/jenis dokumen (sebutkan mana saja)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: aturan pembulatan resmi dari finance (jumlah desimal, arah pembulatan).

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [GAP-FE01-02 · GAP-FE02-01 · GAP-FE04-04 · GAP-FE05-01 · GAP-FE06-01 · GAP-FE06-02 · GAP-FE06-04] (P1) — Semua layar butuh sumber data dropdown/lookup master (dealer, bank, sektor ekonomi OJK, reason code approve/reject, status kontak, dll.). Siapa pemilik endpoint lookup: modul masing-masing atau modul master-data terpusat?

**Konteks**: PRD backend tiap modul sengaja tidak mengarang endpoint lookup; meeting sudah memutuskan menu Master masuk scope (D-08). Tanpa ini form intake, analisa, NPP, dan Vertel tidak bisa disubmit. *(Sumber: `FE-01/FE-02/FE-04/FE-05/FE-06 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Semua lookup diarahkan ke registry lookup terpusat milik modul master-data (mekanisme E30 di BE-07) ← **REKOMENDASI KAMI** (kandidat yang sudah tertulis di PRD)
- [ ] **B.** Endpoint lookup dimiliki modul masing-masing (konsekuensi: duplikasi kontrak lookup)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: persetujuan pembagian kepemilikan + daftar lookup key per modul (kami siapkan draft-nya).

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [GAP-FE02-03] (P1) — Kontrak penyimpanan catatan analisa 5C + upload dokumen analisa belum bisa dibuat, karena di legacy ada TIGA tempat penyimpanan paralel dan belum diketahui mana yang sah.

**Konteks**: bergantung pada hasil profiling produksi OQ-CRSCORE-01 (bagian C/DBA). Memblokir layar catatan CMO (SCR-CA-06). *(Sumber: `FE-02 §11`; `BE-02 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Tunggu hasil profiling DBA (OQ-CRSCORE-01), lalu tetapkan SATU target tulis ← **REKOMENDASI KAMI**
- [ ] **X. Jawaban lain** (mis. bila tim bisnis sudah tahu jalur mana yang dipakai analis sehari-hari — tulis di kolom jawaban).

**Yang perlu disiapkan untuk menutup gap ini**: jawaban OQ-CRSCORE-01 (profiling) ATAU konfirmasi praktik analis.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### A — Prioritas P2

### [OQ-GT-03] (P2) — Koreksi "Open CM" (buka kembali kontrak yang sudah approve, kembali ke step 1–12): user mengulang input dari awal, atau hanya mengoreksi field tertentu? Dan apa nasib PO pertama saat koreksi?

**Konteks**: satu-satunya butir ground-truth alur yang masih terbuka; menentukan UX koreksi & cakupan re-screening. *(Sumber: `_ACQUISITION-GROUND-TRUTH.md`; `BE-02/BE-04/FE-04 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Koreksi field-scoped, tanpa re-entry penuh ← **REKOMENDASI KAMI** (usulan baru — belum tertulis di dokumen sumber)
- [ ] **B.** Full re-entry step 1–12 (konsekuensi: seluruh gate diulang)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi praktik Open CM + kebijakan nasib PO pertama (batal/diganti).

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CMPO-01 — residual] (P2) — Saat dokumen NPP berstatus "tervalidasi", apakah user masih boleh meng-edit, atau terkunci penuh?

**Konteks**: makna status legacy "0" (= aplikasi terkunci RFA) sudah kami putuskan; ini residual kebijakan editability. *(Sumber: `BE-04/BE-05/FE-05 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Terkunci penuh; edit hanya via jalur koreksi resmi ← **REKOMENDASI KAMI** (default paling aman)
- [ ] **B.** Tetap bisa diedit selama belum final (paritas kebiasaan legacy) (konsekuensi: tombol Edit ditambahkan + guard direvisi)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi kebiasaan admin cabang saat data NPP perlu dikoreksi.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-MEET-01] (P2) — Email blast ke dealer setelah kontrak aktif: kapan dikirim, apa isinya, dan bagaimana bila gagal?

**Konteks**: keputusan meeting D-03 menetapkan fiturnya; detail trigger/template/failure policy belum. *(Sumber: `_MEETING-DECISIONS`; `BE-00/BE-05 §11`.)*

**Pilihan jawaban** (trigger):

- [ ] **A.** Dikirim saat NPP approve (real-time) ← **REKOMENDASI KAMI** (usulan baru — belum tertulis di dokumen sumber)
- [ ] **B.** Batch T+n (sebutkan n)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: template/konten email + kebijakan bila kirim gagal (retry berapa kali? blocking atau tidak?) dari business owner (COBS).

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-MEET-03] (P2) — AR Card & jurnal yang terbentuk saat aktivasi kontrak: dari mana sumber kebenaran mapping akun GL dan aturan posting-nya?

**Konteks**: keputusan meeting D-06 menetapkan AR Card + jurnal sebagai output wajib aktivasi; sumber mapping GL belum. *(Sumber: `_MEETING-DECISIONS`; `BE-05 §11`.)*

**Informasi yang diminta**: dokumen mapping akun GL + aturan posting (dari finance) + konfirmasi keterkaitan kontrak Passnet.

**Yang perlu disiapkan untuk menutup gap ini**: sesi dengan stakeholder finance + dokumen kontrak Passnet.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-MIG-03] (P2) — Saat migrasi kami akan menemukan NIK duplikat historis (satu orang tercatat beberapa kali). Apa disposisinya?

**Konteks**: rencana migrasi sudah menetapkan TIDAK auto-merge — temuan duplikat = keputusan bisnis. *(Sumber: `DATA-MIGRATION-PLAN.md §7`.)*

**Pilihan jawaban**:

- [ ] **A.** Duplikat dicatat dalam laporan; disposisi manual per kasus setelah migrasi ← **REKOMENDASI KAMI** (sejalan aturan "tidak auto-merge" yang sudah tertulis)
- [ ] **B.** Merge manual sebelum cutover (konsekuensi: menambah durasi persiapan migrasi)
- [ ] **C.** Biarkan per-aplikasi selamanya (tanpa dedup)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: aturan disposisi duplikat dari pemilik data customer.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-GAP-08] (P2) — Fitur "Top Up Mega Solusi": penulisan datanya sudah dinonaktifkan di kode. Fiturnya dibatalkan, atau mekanismenya pindah ke flag top-up + repeat order?

**Konteks**: menentukan model top-up di sistem baru + apakah ada data historis yang harus dibawa. *(Sumber: `gap-entities.md §4`; `BE-04 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Fitur dibatalkan → data lama cukup diarsipkan ← **REKOMENDASI KAMI** (berlaku bila data produksi kosong/berhenti — perlu cek profiling)
- [ ] **B.** Mekanisme pindah ke flag top-up + repeat order → butuh mapping migrasi
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi product owner + profiling data produksi tabel terkait.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CMPO-03] (P2) — Formula OP berbeda antar lini (motor: nilai aset − DP bersih; mobil: jumlah yang dibiayai). Disengaja, atau drift copy-paste — dan disatukan?

**Konteks**: menentukan formula freeze CM per lini (terkait matriks produk OQ-MEET-06). *(Sumber: `BE-04 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Satu formula terparameterisasi per produk ← **REKOMENDASI KAMI** (usulan baru — belum tertulis di dokumen sumber)
- [ ] **B.** Dua formula memang beda kebijakan → keduanya dipertahankan & didokumentasikan
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi business-rule owner pembiayaan.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CMPO-04] (P2) — Di lini mobil, angka OP/ULI/LCR yang seharusnya BEKU dihitung-ulang pada disposisi non-approval. Konfirmasi ini bug (tidak akan kami tiru) — atau ada alasan bisnis?

**Konteks**: kami menandainya kandidat "do-not-replicate". *(Sumber: `BE-04 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Bug/regresi → tidak ditiru; angka tetap beku ← **REKOMENDASI KAMI** (default tertulis: do-not-replicate)
- [ ] **B.** Ada alasan bisnis → jelaskan di kolom jawaban
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi singkat pemilik proses CM lini mobil.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CMPO-05] (P2) — Target sudah diputuskan: setiap approval mencetak tepat satu PO. Untuk memo "Level-0" lini motor LAMA yang tidak ber-PO: apakah di-backfill PO saat migrasi?

**Konteks**: residual dari keputusan meeting D-01 S13. *(Sumber: `BE-04 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Backfill PO untuk memo Level-0 motor lama yang eligible ← **REKOMENDASI KAMI** (usulan baru — konsisten "satu PO per approval")
- [ ] **B.** Tanpa backfill — hanya aplikasi baru yang mengikuti aturan baru
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi apakah memo Level-0 motor memang layak PO secara bisnis.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CMPO-06] (P2) — Apa penentu resmi sebuah aplikasi di-route ke varian motor vs mobil? (Kode `001`/`002` yang kami temukan baru inferensi perilaku.)

**Konteks**: butuh daftar kode jenis aset otoritatif. *(Sumber: `BE-04 §11`.)*

**Informasi yang diminta**: daftar kode asset-kind resmi + aturan routing lini.

**Yang perlu disiapkan untuk menutup gap ini**: katalog product-asset master dari pemiliknya.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-ACQCAS-03] (P2) — Gate yang hanya berlaku untuk mobil (usia kendaraan, usia peminjam 65 th, DP% per tujuan): beda kebijakan yang disengaja, atau gap di lini motor?

**Konteks**: bagian dari matriks produk (OQ-MEET-06). *(Sumber: `BE-01 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Gap motor → gate diperluas ke motor via matriks produk ← **REKOMENDASI KAMI** (usulan baru — belum tertulis di dokumen sumber)
- [ ] **B.** Beda kebijakan intensional → tetap car-only
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: jawaban ikut matriks OQ-MEET-06.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-ACQCAS-04] (P2) — Formula penentuan tier Repeat-Order berbeda (mobil: % tenor; motor: jumlah kontrak) dan pencocokan identitas hanya di motor: disengaja atau drift — disatukan?

**Konteks**: menentukan aturan repeat-order lintas lini. *(Sumber: `BE-01 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Disatukan (satu formula + identity-match untuk kedua lini) ← **REKOMENDASI KAMI** (usulan baru — belum tertulis di dokumen sumber)
- [ ] **B.** Intensional per lini → dipertahankan & didokumentasikan
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi pemilik kebijakan repeat-order.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CASFE-07] (P2) — Capture data korporasi di lini motor tidak selengkap mobil (roster pengurus + rekening). Disatukan di sistem baru?

**Konteks**: menentukan konfigurasi step wizard per lini produk. *(Sumber: `FE-01 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Disatukan — capture korporasi lengkap untuk kedua lini ← **REKOMENDASI KAMI** (usulan baru — belum tertulis di dokumen sumber)
- [ ] **B.** Tetap berbeda per lini (konfigurasi per product_line)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: jawaban ikut matriks OQ-MEET-06.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CASFE-08] (P2) — Layar narasi 5C + submit RFA: role siapa yang boleh mengaksesnya? (Legacy tidak membedakan role.)

**Konteks**: pemetaan ke 5 role cabang resmi butuh konfirmasi. *(Sumber: `FE-01 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** CMO ← **REKOMENDASI KAMI** (usulan baru — CMO adalah pemilik data lapangan)
- [ ] **B.** Credit (Admin)
- [ ] **C.** Keduanya
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi pembagian kerja cabang.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [EC7 (KB 61 §9)] (P2) — Layar "Customer Check": pembatasan hanya-kantor-pusat pernah ada tetapi dinonaktifkan. Dikembalikan, atau dihapus resmi?

**Konteks**: menentukan guard layar Customer Check. *(Sumber: `FE-01 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Dihapus resmi — semua cabang boleh akses ← **REKOMENDASI KAMI** (usulan baru — mengikuti kondisi berjalan)
- [ ] **B.** Dikembalikan — HO-only
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi pemilik kebijakan akses.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CRSCORE-08] (P2) — Konfirmasi daftar nilai sah checklist dokumen analisa: `Terlampir/Belum Ada/Tidak Ada` dan `Valid/Tidak Valid/Tidak Ada` (+ selfie survey).

**Konteks**: nilai ini yang teramati di layar legacy; butuh pengesahan sebagai vocabulary final. *(Sumber: `BE-02 §11`; `FE-02 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Sah sebagai vocabulary final ← **REKOMENDASI KAMI** (sesuai temuan layar)
- [ ] **B.** Ada nilai lain — sebutkan di kolom jawaban
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: —

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CAUI-01] (P2) — Nama layar di dokumen alur ("CheckingSlikDashboard", "TrCaDocuments") tidak kami temukan sebagai artefak nyata; kami memetakannya ke worklist CA + grid histori BI-check + checklist dokumen. Benar?

**Konteks**: konfirmasi mapping sebelum penamaan layar/API final. *(Sumber: `BE-02 §11`; `FE-02 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Mapping kami benar ← **REKOMENDASI KAMI** (sesuai temuan)
- [ ] **B.** Ada layar lain yang dimaksud — jelaskan di kolom jawaban
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi penulis dokumen alur.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CAUI-02] (P2) — Role "CA approver" (flag approver di layar mobil; gate Rapindo di motor): role tersendiri, atau sama dengan reviewer hierarki umum?

**Konteks**: menentukan desain RBAC modul analisa/approval. *(Sumber: `BE-02 §11`; `FE-02 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Sama dengan reviewer hierarki (tidak ada role approver CA terpisah) ← **REKOMENDASI KAMI** (usulan baru — belum tertulis di dokumen sumber)
- [ ] **B.** Role tersendiri — sebutkan penunjukannya
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi struktur approval CA nyata.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CAUI-04] (P2) — Gate Rapindo legacy hanya aktif untuk barang bekas bertipe aplikasi "03": apa makna kode "03", dan apakah gate ini berlaku lebih luas di sistem baru?

**Konteks**: menentukan scope pengecekan aset eksternal. *(Sumber: `BE-02 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Scope tetap sempit seperti legacy (barang bekas tipe "03") ← **REKOMENDASI KAMI** (default paling aman sampai ada arahan lain)
- [ ] **B.** Diperluas — sebutkan cakupannya
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: makna kode "03" dari pemilik katalog tipe aplikasi.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [CONF-FE02-01] (P2) — Kami MENGHAPUS pemilihan "approval scheme" manual oleh analis, karena routing komite di sistem baru otomatis (jenis transaksi + plafond + risiko, sesuai keputusan meeting). Konfirmasi tidak ada kebutuhan pemilihan manual.

**Konteks**: bila pemilihan manual dipertahankan → butuh field + endpoint baru. *(Sumber: `FE-02 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Konfirmasi hapus — routing sepenuhnya otomatis ← **REKOMENDASI KAMI** (sesuai keputusan meeting D-01 Step 10)
- [ ] **B.** Pemilihan manual dipertahankan untuk kasus tertentu — sebutkan
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: —

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-BE03-01] (P2) — Dengan role super-user DIHAPUS: apa mekanisme saat approver yang ditunjuk absen/cuti?

**Konteks**: tanpa keputusan ini chain approval bisa macet di approver non-aktif. *(Sumber: `BE-03 §11`; `FE-03 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Delegasi ter-konfigurasi (approver menunjuk pengganti sementara) ← **REKOMENDASI KAMI** (usulan baru — belum tertulis di dokumen sumber)
- [ ] **B.** Reassignment oleh admin
- [ ] **C.** Eskalasi otomatis ke level berikut setelah SLA terlewati
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: kebijakan coverage approval dari pemilik governance.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-AC-03] (P2) — Apa arti kode sumber kredit (`credit_source_id`) `5` vs `6` sebagai nama channel?

**Konteks**: dibutuhkan untuk mendefinisikan channel asal pada jalur Instant-Approval. *(Sumber: `BE-03 §11`.)*

**Informasi yang diminta**: nama channel untuk kode 5 dan 6 (dan daftar lengkap kode credit_source bila ada).

**Yang perlu disiapkan untuk menutup gap ini**: katalog credit_source resmi.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-AC-05] (P2) — Jabatan "ACM" dalam mapping hierarki approval itu apa, dan apa hubungannya dengan "Dept Head Credit"?

**Konteks**: tidak muncul di sensus role cabang — kemungkinan posisi supra-cabang. *(Sumber: `BE-03 §11`.)*

**Informasi yang diminta**: kepanjangan & posisi organisasi "ACM" + relasinya ke Dept Head Credit.

**Yang perlu disiapkan untuk menutup gap ini**: struktur organisasi kredit.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-AC-10] (P2) — Routing komite lini mobil memakai kunci berbeda dari motor. Apakah hasilnya setara (sama-sama memperhitungkan tier risiko), atau mobil memang di-route atas dasar berbeda?

**Konteks**: terkait matriks produk OQ-MEET-06. *(Sumber: `BE-03 §11`.)*

**Informasi yang diminta**: konfirmasi dasar routing komite lini mobil.

**Yang perlu disiapkan untuk menutup gap ini**: penjelasan pemilik kebijakan routing komite.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-NPP-06] (P2) — Kedalaman approval NPP: legacy 2 level tetap; keputusan meeting menyebut hierarki mengikuti skala risiko. Berapa level untuk NPP di sistem baru?

**Konteks**: menentukan bentuk maker-checker NPP (perlu configurable atau tidak). *(Sumber: `BE-05 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** N-level configurable mengikuti skala risiko ← **REKOMENDASI KAMI** (selaras keputusan meeting D-10)
- [ ] **B.** Tetap 2 level tetap (paritas legacy)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: aturan kedalaman per skala risiko.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-VTL-01] (P2) — Verifikasi telepon (Vertel): harus KETAT setelah PO terbit, atau boleh mulai paralel setelah CM approve (seperti legacy) demi efisiensi?

**Konteks**: keputusan meeting D-02 menempatkan Vertel setelah PO (STEP 14). *(Sumber: `BE-06 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Strict post-PO ← **REKOMENDASI KAMI** (default tertulis, sesuai D-02)
- [ ] **B.** Boleh mulai paralel post-CM (konsekuensi: verifikasi bisa terjadi sebelum PO final)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi business owner (COBS).

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-VTL-02] (P2) — Bila CM/PO dikoreksi (Open CM), apakah verifikasi telepon yang sudah selesai ikut BATAL (harus verifikasi ulang)?

**Konteks**: terkait OQ-GT-03. *(Sumber: `BE-06 §11`; `FE-06 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Ya — verifikasi di-invalidate, status kembali "recheck" ← **REKOMENDASI KAMI** (default USULAN tertulis di PRD)
- [ ] **B.** Verifikasi tetap berlaku (konsekuensi: data terverifikasi bisa berbeda dari data terkoreksi)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: sign-off ops/bisnis.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-VTL-04] (P2) — Vertel wajib untuk SEMUA produk, atau ada pengecualian (misal jalur Instant-Approval)?

**Konteks**: bagian dari matriks produk OQ-MEET-06. *(Sumber: `BE-06 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Wajib untuk semua produk tanpa kecuali ← **REKOMENDASI KAMI** (usulan baru — paling konservatif)
- [ ] **B.** Ada pengecualian — sebutkan produk/jalurnya
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: jawaban ikut matriks OQ-MEET-06.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-VERIF-05] (P2) — Perlukah layar "Customer Check" terkonsolidasi (hasil Vertel + Dukcapil + FCL + survey → satu kesimpulan)? Legacy tidak punya — ini fitur BARU bila diminta.

**Konteks**: menambah scope bila diminta. *(Sumber: `BE-06 §11`; `FE-06 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Tidak dibangun dulu (paritas legacy; hasil dilihat per sumber) ← **REKOMENDASI KAMI** (default: fitur baru butuh permintaan eksplisit)
- [ ] **B.** Dibangun sebagai fitur baru (konsekuensi: layar + aturan konsolidasi verdict baru)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: keputusan product owner.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-NPPVTL-02] (P2) — "Kepala Cabang" sebagai role bernama: bagaimana mapping-nya ke struktur organisasi/HR? (Legacy hanya punya flag "pending approver", bukan role.)

**Konteks**: menentukan klaim role di token & gating panel keputusan. *(Sumber: `BE-05/BE-06 §11`; `FE-05/FE-06 §11`.)*

**Informasi yang diminta**: field/atribut HR yang menandai seseorang adalah Kepala Cabang (dan role cabang lainnya).

**Yang perlu disiapkan untuk menutup gap ini**: mapping jabatan HR → 5 role cabang.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-NPPVTL-03] (P2) — Checklist dokumen verifikasi: perlukah status eksplisit "dokumen dikonfirmasi TIDAK ADA" (3 status), atau ketiadaan cukup implisit? (Di legacy opsi "Tidak Ada" selamanya disabled.)

**Konteks**: model data sudah menyiapkan 3-status sebagai usulan. *(Sumber: `BE-06 §11`; `FE-06 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Ketiadaan implisit — opsi "Tidak Ada" tidak dirender ← **REKOMENDASI KAMI** (default FE tertulis, sampai diputuskan)
- [ ] **B.** 3 status eksplisit (Ada / Belum / Dikonfirmasi Tidak Ada)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi kebutuhan audit checklist.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-COL-01] (P2) — Apa penentu sebuah aplikasi butuh penanganan credit-analyst penuh vs langsung dari approval CM ke drafting kontrak?

**Konteks**: menentukan UX & phasing modul analisa. *(Sumber: `BE-00 §11`.)*

**Informasi yang diminta**: kriteria (produk? plafond? risiko?) yang menentukan jalur analisa penuh.

**Yang perlu disiapkan untuk menutup gap ini**: penjelasan pemilik proses kredit.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-SHELL-01] (P2) — Sesi kasir (cashier till): domain siapa — app-shell atau modul Payment?

**Konteks**: di luar scope acquisition kecuali ditarik masuk. *(Sumber: `FE-00 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Domain Payment/cashier — di luar scope acquisition ← **REKOMENDASI KAMI** (posisi dokumen saat ini)
- [ ] **B.** Ditarik masuk scope acquisition — jelaskan alasannya
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: —

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-MASTERDATA-03] (P2) — Konfigurasi "TransType Hierarchy" sebenarnya dikonsumsi proses apa (router komite vs tangga analis)?

**Konteks**: menentukan sekritikal apa layar konfigurasinya + copy peringatan dampak. *(Sumber: `BE-07 §11`; `FE-07 §11`.)*

**Informasi yang diminta**: proses/pengguna nyata konfigurasi ini hari ini.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi admin yang biasa mengubahnya.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-DLRPTN-04] (P2) — Master alasan approve/reject punya kode type `1/2/3/9`: apa maknanya, dan apakah type `9` boleh ditampilkan? (Dua jalur legacy tidak sepakat.)

**Konteks**: default FE sementara: tampilkan kode mentah tanpa label. *(Sumber: `BE-07 §11`; `FE-07 §11`.)*

**Informasi yang diminta**: label makna type 1/2/3/9 + kebijakan type 9.

**Yang perlu disiapkan untuk menutup gap ini**: katalog reason type resmi.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CUSTMASTER-02 / OQ-CUSTMASTER-07] (P2) — Lookup "reference type" dipakai untuk apa (dibawa/dipecah/dipensiunkan)? Dan tipe pemohon benar-benar hanya P/C atau ada nilai lain?

**Konteks**: kelengkapan katalog lookup pemohon. *(Sumber: `BE-07 §11`.)*

**Informasi yang diminta**: pemakaian nyata reference type + daftar lengkap nilai applicant-type.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi pemilik data customer.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-BE07-05] (P2) — Model akses menu: murni role-based, position-based seperti legacy, atau hybrid? Dan apakah "grant menu spesial per-user" dipertahankan?

**Konteks**: grant spesial per-user berisiko jadi pintu belakang, padahal super-user sudah dihapus (keputusan meeting D-09). *(Sumber: `BE-07 §11`; `FE-07 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Role-based murni; grant spesial per-user TIDAK dibawa ← **REKOMENDASI KAMI** (usulan tertulis; layar grant spesial tidak dirender)
- [ ] **B.** Hybrid — grant spesial dipertahankan dengan audit ketat
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: sign-off governance akses.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [GAP-FE00-03 · GAP-FE02-06 · GAP-FE05-05 · GAP-FE06-06 · GAP-FE07-07] (P2) — Matriks awal role → menu: dari 5 role cabang resmi, siapa boleh membuka menu apa (termasuk read-only) di tiap modul?

**Konteks**: mekanisme grant sudah ada — ISINYA butuh keputusan; menentukan sidebar & worklist per role di semua modul. *(Sumber: `FE-00/FE-02/FE-05/FE-06/FE-07 §11`.)*

**Informasi yang diminta**: matriks role × menu (kami siapkan template kosongnya).

**Yang perlu disiapkan untuk menutup gap ini**: workshop singkat COBS/ops mengisi matriks.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [GAP-FE01-05] (P2) — Layar "Customer Check by Passnet ID": modul mana pemiliknya — intake atau reporting?

**Konteks**: di legacy berada di domain Reports (artefak; penempatan bebas). *(Sumber: `FE-01 §11.1`.)*

**Pilihan jawaban**:

- [ ] **A.** Reporting ← **REKOMENDASI KAMI** (mengikuti penempatan legacy)
- [ ] **B.** Intake (modul 01)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: keputusan product owner + definisi endpoint-nya.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [GAP-FE01-07] (P2) — Cetak dokumen LAHS dari layar CAS: dipertahankan (butuh endpoint print) atau dipensiunkan?

**Konteks**: fungsi ada di legacy; kebutuhan nyatanya belum terkonfirmasi. *(Sumber: `FE-01 §11.1`.)*

**Pilihan jawaban**:

- [ ] **A.** Dipensiunkan bila tidak dipakai ← **REKOMENDASI KAMI** (usulan baru — perlu konfirmasi pemakaian)
- [ ] **B.** Dipertahankan → kami definisikan endpoint print/report
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi cabang masih mencetak LAHS atau tidak.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [GAP-FE03-03] (P2) — Layar admin kebijakan Instant-Approval (IA Policy): modul mana pemiliknya?

**Konteks**: aturan eligibility-nya sendiri = OQ-MEET-04 (bagian D). *(Sumber: `FE-03 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Modul admin risk / master-data (di luar inbox approval) ← **REKOMENDASI KAMI** (scope FE-03 saat ini tidak memuatnya)
- [ ] **B.** Ditempatkan di FE-03 (inbox approval)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: penunjukan pemilik layar + role "Risk policy admin".

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [GAP-FE03-04] (P2) — Input deviasi "Memo Persetujuan" (penyimpangan rate/tenor yang di-approve cabang): siapa pemilik layar input-nya, dan apakah panel komite menampilkan jejak deviasi yang mempengaruhi routing/rate?

**Konteks**: di legacy penulis data deviasi tidak ditemukan di aplikasi (lihat OQ-GAP-04, bagian C); target state = input via UI ber-maker-checker. *(Sumber: `FE-03 §11`; `BE-03 §11`.)*

**Pilihan jawaban** (pemilik layar):

- [ ] **A.** Modul master-data / risk-admin ← **REKOMENDASI KAMI** (usulan baru — konsisten maker-checker master)
- [ ] **B.** FE-03 (inbox approval)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: keputusan pemilik layar + konfirmasi jejak deviasi tampil di panel komite (ya/tidak).

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [GAP-FE04-03] (P2) — Koreksi Open CM legacy mengirim identitas ITEM selain nomor PO+kontrak. Apakah satu memo bisa memuat lebih dari satu item?

**Konteks**: menentukan apakah kontrak koreksi butuh `item_id`. *(Sumber: `FE-04 §11.1`.)*

**Pilihan jawaban**:

- [ ] **A.** Satu memo = satu item → kontrak koreksi cukup nomor kontrak + alasan ← **REKOMENDASI KAMI** (sesuai kontrak BE yang sudah ditulis)
- [ ] **B.** Multi-item per memo → `item_id` ditambahkan ke kontrak
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi domain: kasus multi-item nyata ada atau tidak.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [GAP-FE07-03] (P2) — Audit perubahan master (siapa/kapan/sebelum-sesudah) wajib dicatat. Apakah user juga perlu MELIHAT riwayat perubahan per record di layar?

**Konteks**: endpoint baca audit belum ada; kami mengusulkan panel riwayat di detail. *(Sumber: `FE-07 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Ya — panel riwayat di layar detail ← **REKOMENDASI KAMI** (usulan tertulis)
- [ ] **B.** Cukup tercatat di backend (dilihat via permintaan khusus)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: —

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [GAP-FE07-08] (P2) — Admin format penomoran (termasuk format nomor kontrak): perlu layar kelola sendiri, atau cukup diatur via seed/ops?

**Konteks**: perubahan format nomor kontrak berdampak lintas sistem. *(Sumber: `FE-07 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Tanpa layar — diatur via seed/ops ← **REKOMENDASI KAMI** (default tertulis: layar tidak dirender & endpoint tidak dipanggil)
- [ ] **B.** Layar admin dengan jalur maker-checker
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: —

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [GAP-FE06-08] (P2) — Sejumlah kolom wawancara Vertel di model data TIDAK punya padanan input di layar legacy (mis. nama ibu, sumber referensi, catatan "lainnya"). Dirender di form (field & section mana), atau diisi jalur lain?

**Konteks**: kami tidak akan mengarang form tanpa keputusan. *(Sumber: `FE-06 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Tidak dirender sampai ada keputusan per field ← **REKOMENDASI KAMI** (default aman)
- [ ] **B.** Dirender — lampirkan daftar field + penempatannya di kolom jawaban
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: daftar field yang memang diisi petugas saat wawancara telepon.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CMPOFE-08] (P2) — Layar binding asuransi batch (IC1–IC6, per nomor NPP): apakah ini "insurance binding" yang dimaksud keputusan meeting (bagian finalisasi CM), atau proses downstream terpisah (domain INSURANCE)?

**Konteks**: menentukan scope modul kontrak. *(Sumber: `BE-04 §11`; `FE-04 §11`.)*

**Informasi yang diminta**: relasi dua touchpoint asuransi tersebut menurut proses bisnis nyata.

**Yang perlu disiapkan untuk menutup gap ini**: penjelasan pemilik proses asuransi.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-FE04-01] (P2) — Legacy menyimpan seluruh form DIAM-DIAM saat user menekan tombol Back ("save-on-back"). Kami usul menggantinya dengan konfirmasi eksplisit bila ada perubahan belum tersimpan.

**Konteks**: perlu kepastian perilaku lama tidak diandalkan cabang. *(Sumber: `FE-04 §11.3`.)*

**Pilihan jawaban**:

- [ ] **A.** Ganti dengan dirty-check + prompt eksplisit ← **REKOMENDASI KAMI** (usulan tertulis)
- [ ] **B.** Pertahankan save-on-back
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi product owner.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CSB-03 / OQ-CSB-04 / OQ-CSB-05] (P2) — Standarisasi komponen UI: (a) date-picker diaktifkan seragam? (b) satu kontrak grid untuk semua listing? (c) satu dialog lookup untuk semua pencarian master?

**Konteks**: di sebagian layar legacy picker dimatikan (disengaja?); grid & lookup punya banyak varian divergen. *(Sumber: `FE-00 §11`; `FE-01 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Seragam semua (picker aktif, satu grid ber-slot aksi, satu lookup) ← **REKOMENDASI KAMI** (usulan tertulis di FE-00)
- [ ] **B.** Ada kebutuhan varian — sebutkan layar/kasusnya
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi tidak ada kebutuhan input tanggal free-text / lookup bertingkat yang disengaja.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### A — Prioritas P3 (klarifikasi)

### [OQ-ACQCAS-09] (P3) — Ada jalur resmi membatalkan/menghapus aplikasi SEBELUM kunci RFA? (Di legacy tidak ada yang berfungsi.)

**Pilihan jawaban**:

- [ ] **A.** Cancel dirancang sebagai fitur baru dengan alasan wajib ← **REKOMENDASI KAMI** (arah tertulis: cancel = keputusan desain baru)
- [ ] **B.** Tidak perlu cancel pra-RFA
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi kebutuhan cabang. *(Sumber: `BE-01 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-ACQCAS-10] (P3) — Apa makna bisnis kode tipe aplikasi `01/04/05/06` dan arti singkatan "OP" pada nama tipe?

**Informasi yang diminta**: label/pengertian tiap kode (daftar kodenya sendiri sudah kami kunci dari sistem).

**Yang perlu disiapkan untuk menutup gap ini**: katalog tipe aplikasi resmi. *(Sumber: `BE-01 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-ACQCAS-11] (P3) — Apa kepanjangan "TAC" (channel order dealer)?

**Informasi yang diminta**: ekspansi literal + maknanya.

**Yang perlu disiapkan untuk menutup gap ini**: — *(Sumber: `BE-01 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CASFE-09] (P3) — Lookup "NIK Konsumen" pada section Repeat-Order terbaca sebagai lookup karyawan/marketing-source. Apa makna sebenarnya?

**Informasi yang diminta**: makna & label yang benar untuk lookup tersebut.

**Yang perlu disiapkan untuk menutup gap ini**: — *(Sumber: `FE-01 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CRSCORE-07] (P3) — Apa arti institusional kode model pembiayaan `US`/`SY` (dipakai bergantian di kode)?

**Informasi yang diminta**: definisi kedua kode + kapan masing-masing dipakai.

**Yang perlu disiapkan untuk menutup gap ini**: — *(Sumber: `BE-02 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-AC-07] (P3) — Apa arti "RIMO" (digit 5 pada tipe transaksi)? Dan kenapa rate-di-bawah-pasar justru MENURUNKAN tier risiko (semua tier lain menaikkan)?

**Pilihan jawaban**:

- [ ] **A.** Perilaku scaling-down memang intensional — jelaskan alasannya
- [ ] **B.** Anomali → sistem baru menyamakan arah scaling ← **REKOMENDASI KAMI** (usulan baru — belum tertulis di dokumen sumber)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: definisi RIMO. *(Sumber: `BE-03 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-BE03-03] (P3) — Marketing Head masuk hierarki approval komite untuk kombinasi transaksi tertentu, atau di luar komite CM sama sekali?

**Informasi yang diminta**: posisi Marketing Head dalam chain komite.

**Yang perlu disiapkan untuk menutup gap ini**: — *(Sumber: `BE-03 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CMPO-09] (P3) — Kenapa penulisan rate hanya dilakukan untuk kode top-up/repeat-order (kode lain dihapus)?

**Informasi yang diminta**: alasan bisnisnya (file sumber kami korup — juga butuh re-export bersih dari sisi teknis).

**Yang perlu disiapkan untuk menutup gap ini**: penjelasan business-rule owner. *(Sumber: `BE-04 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CMPOFE-11] (P3) — Berapa ambang usia/tanggal lahir untuk eligibility asuransi kesehatan (dipakai untuk tooltip/pesan disable toggle)?

**Informasi yang diminta**: nilai ambang resmi.

**Yang perlu disiapkan untuk menutup gap ini**: aturan produk asuransi kesehatan. *(Sumber: `FE-04 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-NPP-10] (P3) — Saat aktivasi, data asuransi (periodical/coverage) di-reset keras menimpa isian CMO. Kebijakan disengaja, atau overwrite tidak sengaja?

**Pilihan jawaban**:

- [ ] **A.** Overwrite tidak sengaja → tidak ditiru ← **REKOMENDASI KAMI** (konfirmasi sebelum ditiru — arah tertulis)
- [ ] **B.** Kebijakan disengaja → dipertahankan
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi pemilik proses asuransi. *(Sumber: `BE-05 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-NPP-12] (P3) — Status NPP "ditahan (rejected)": benar-benar terminal, atau ada jalur re-open di luar modul ini?

**Pilihan jawaban**:

- [ ] **A.** Terminal ← **REKOMENDASI KAMI** (default)
- [ ] **B.** Ada jalur re-open — sebutkan prosesnya
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: — *(Sumber: `BE-05 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-NPPVTL-05] (P3) — Validasi total subsidi dealer (guard legacy dinonaktifkan di kode): dihidupkan lagi di sistem baru?

**Pilihan jawaban**:

- [ ] **A.** Ya, dihidupkan server-side ← **REKOMENDASI KAMI** (usulan baru — belum tertulis di dokumen sumber)
- [ ] **B.** Tidak — memang sengaja dimatikan
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi kebijakan subsidi. *(Sumber: `FE-05 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-NPPVTL-06] (P3) — Pesan error pre-check approve NPP: copy baku/regulated yang harus dipertahankan, atau bebas ditulis ulang?

**Pilihan jawaban**:

- [ ] **A.** Bebas ditulis ulang dengan makna sama ← **REKOMENDASI KAMI** (default)
- [ ] **B.** Copy baku — lampirkan teks resminya
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: — *(Sumber: `BE-05/FE-05 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-NPPVTL-08] (P3) — Rentang tanggal bisnis yang benar untuk semua field tanggal NPP (menggantikan batas teknis tahun 2000–2100 legacy)?

**Informasi yang diminta**: rentang tanggal wajar per field (mis. tanggal BAST tidak boleh masa depan).

**Yang perlu disiapkan untuk menutup gap ini**: — *(Sumber: `FE-05 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-DLRPTN-10 / OQ-DLRPTN-11] (P3) — Apa arti kode badan hukum `PT='2'/'3'`? Dan apa daftar nilai enum kota/kabupaten (`Dt2Type`)?

**Informasi yang diminta**: master perusahaan kanonik + enum wilayah.

**Yang perlu disiapkan untuk menutup gap ini**: — *(Sumber: `BE-07 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-DLRPTN-06 / OQ-DLRPTN-12 / OQ-DLRPTN-15] (P3) — Master asuransi per-dealer (R2/R4) masih hidup atau orphan? Sumber asuransi pernah difilter dealer/cabang/produk?

**Informasi yang diminta**: status pemakaian + aturan filternya.

**Yang perlu disiapkan untuk menutup gap ini**: — *(Sumber: `BE-07 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-DLRPTN-08 / OQ-DLRPTN-09] (P3) — Aturan "branch exception" (di kode selalu mengembalikan '0'): rule mati atau fitur belum jadi? Payment point perlu scope cabang?

**Pilihan jawaban**:

- [ ] **A.** Rule mati → tidak dibawa ← **REKOMENDASI KAMI** (default tertulis)
- [ ] **B.** Fitur direncanakan → jelaskan kebutuhannya
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: — *(Sumber: `BE-07 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-BE07-06] (P3) — Master hari libur: boleh dihapus permanen (hard-delete), atau ikut aturan nonaktif-saja demi audit kalkulasi jatuh tempo historis?

**Pilihan jawaban**:

- [ ] **A.** Hard-delete diperbolehkan ← **REKOMENDASI KAMI** (usulan tertulis)
- [ ] **B.** Nonaktif-saja (deactivate-only)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: — *(Sumber: `BE-07 §11`; `FE-07 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-SHELL-07] (P3) — Multi-brand pernah aktif dalam satu deployment, atau selalu satu brand per deployment?

**Pilihan jawaban**:

- [ ] **A.** Satu brand per deployment ← **REKOMENDASI KAMI** (default)
- [ ] **B.** Multi-brand pernah/akan aktif — jelaskan
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: — *(Sumber: `FE-00 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CAUI-03] (P3) — Worklist analisa lini mobil tidak kami temukan di legacy (layar diakses via sesi). Sah bila sistem baru memakai SATU worklist lintas produk?

**Pilihan jawaban**:

- [ ] **A.** Sah — satu worklist lintas produk ← **REKOMENDASI KAMI** (default tertulis)
- [ ] **B.** Perlu worklist terpisah per lini — jelaskan
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: — *(Sumber: `FE-02 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

---

## B. ITEC Bank Mega (arsitektur, infrastruktur, integrasi eksternal, security)

### B — Prioritas P1

### [OQ-ARCH-STACK — residual] (P1) — Bahasa sudah dikunci (BE = Java, FE = Next.js). Yang masih kami tunggu dari dokumen arsitektur ITEC: framework backend, transport antar-service, platform messaging/scheduler, topologi infra, dan mekanisme auth/session token.

**Konteks**: deliverable arsitektur ITEC (keputusan meeting D-11, deadline 10 Juli 2026); sampai final, seluruh konvensi kontrak API & event kami berlabel "usulan". *(Sumber: `BE-00 §11`; seluruh `BE-*/FE-* §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Spring Boot + REST/JSON + event bus untuk event lintas modul ← **REKOMENDASI KAMI** (USULAN yang sudah tertulis di PRD)
- [ ] **B.** Stack lain sesuai standar ITEC — lampirkan dokumen arsitektur
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: dokumen arsitektur ITEC (framework, transport, messaging, topologi, auth).

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-MIG-01] (P1) — Strategi cutover migrasi: aplikasi yang sedang berjalan diselesaikan di sistem lama (dual-run terbatas), atau dipindah ke sistem baru sesuai posisi step-nya?

**Konteks**: dua skenario lengkap dengan trade-off sudah terdokumentasi; keputusan bersama bisnis + ITEC; dibutuhkan sebelum Phase 3 tetapi menentukan desain migrasi sejak awal. *(Sumber: `DATA-MIGRATION-PLAN.md §5, §7`; `BE-00 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Skenario A — drain di legacy: aplikasi berjalan selesai di sistem lama; aplikasi baru masuk core baru sejak hari-X ← **REKOMENDASI KAMI** (rekomendasi teknis tertulis: default paling aman) (konsekuensi: dual-run ops terbatas + rekap pelaporan gabungan)
- [ ] **B.** Skenario B — migrasi in-flight per status: semua dipindah, workflow engine di-start di tengah proses (konsekuensi: mapping state per step harus sempurna; rollback sulit)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: volume aplikasi in-flight rata-rata per step, toleransi ops terhadap dual-run, deadline dekomisioning legacy.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-MEET-02] (P1) — "Master loan" yang terbentuk saat aktivasi kontrak: dimiliki context Acquisition atau context Servicing?

**Konteks**: keputusan meeting D-05 menetapkan record ini wajib terbentuk; kepemilikan + daftar field + konsumennya belum. *(Sumber: `_MEETING-DECISIONS`; `BE-05 §11`; `FE-05 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Dimiliki Acquisition (dibentuk saat aktivasi, dikonsumsi servicing) ← **REKOMENDASI KAMI** (indikasi dari diagram alur — perlu ratifikasi arsitektur)
- [ ] **B.** Dimiliki Servicing (acquisition hanya emit event)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: keputusan di dokumen arsitektur ITEC + daftar field (field census) + daftar konsumen.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [GAP-FE00-01 + GAP-FE00-02] (P1) — Layanan auth belum punya PRD: login LDAP, pembentukan session, logout/invalidate, refresh token, endpoint daftar cabang ter-otorisasi per pegawai + pengikatan pilihan cabang saat login.

**Konteks**: memblokir rilis FE Phase 0 (layar login & guard seluruh aplikasi). *(Sumber: `FE-00 §11.1`; `BE-00 §8.2`.)*

**Pilihan jawaban**:

- [ ] **A.** PRD auth service ditulis tim rebuild selaras usulan keamanan kami, mekanisme token mengikuti standar ITEC ← **REKOMENDASI KAMI** (usulan tertulis: token actively-enforced; server menolak cabang di luar daftar)
- [ ] **B.** Auth service disediakan penuh oleh ITEC (kami hanya konsumen) — lampirkan kontraknya
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: keputusan mekanisme auth/session dari arsitektur ITEC (D-11).

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-SHELL-02] (P1) — Di legacy, pilihan cabang pada login tahap-2 DIPERCAYA tanpa diverifikasi ulang server. By-design, atau celah?

**Konteks**: scoping data per cabang di semua listing bergantung pada nilai ini. *(Sumber: `BE-00 §11`; `FE-00 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Celah → sistem baru re-verify server-side terhadap daftar cabang tahap-1 ← **REKOMENDASI KAMI** (USULAN default tertulis)
- [ ] **B.** By-design (advisory) — dipertahankan
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: sign-off security.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-VTL-05] (P1) — Gate "verifikasi telepon harus lolos sebelum legalisasi NPP": dicek sinkron dalam transaksi, atau lewat read-model/replika ter-subscribe?

**Konteks**: bergantung pilihan topologi (monolith modular vs microservices) di dokumen ITEC. *(Sumber: `BE-06 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Sinkron in-transaction (cocok bila monolith modular) ← **REKOMENDASI KAMI** (usulan baru — paling sederhana & konsisten)
- [ ] **B.** Read-model ter-subscribe (cocok bila microservices) (konsekuensi: eventual consistency pada gate)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: keputusan topologi dari dokumen ITEC.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-RAC-01 (+OQ-RAC-07)] (P1) — Screening RAC (risk engine): di environment mana proses submit RAC benar-benar berjalan? Dan field apa saja yang dibutuhkan risk engine pada request selain nomor kontrak & pembuat?

**Konteks**: SP submit RAC dikonfirmasi TIDAK ada di database lokal (diduga sisi Bank Mega); screening kredit adalah gate wajib. *(Sumber: `BE-02 §11`; `BE-00 §11`.)*

**Informasi yang diminta**: lokasi/owner eksekusi submit RAC + spesifikasi payload request risk engine.

**Yang perlu disiapkan untuk menutup gap ini**: kontak tim risk engine Bank Mega + dokumen kontrak integrasinya.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-DOKU-01 (+OQ-DOKU-04)] (P1) — Integrasi cek rekening bank DOKU: siapa/apa yang mengisi kolom hasil (responseStatus/responseAccName)? Pemanggilnya tidak kami temukan di aplikasi. Sekalian: apa arti `responseStatus='0'`?

**Konteks**: menentukan apakah integrasi DOKU live/lengkap + siapa pemilik persist response di sistem baru. *(Sumber: `BE-04 §11`; `BE-05 §11`; `BE-00 §11`.)*

**Informasi yang diminta**: proses/service yang menulis hasil DOKU + kamus status code DOKU.

**Yang perlu disiapkan untuk menutup gap ini**: dokumentasi integrasi DOKU (atau kontak PIC-nya).

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-DUKCAPIL-01] (P1) — Verifikasi data kependudukan (Dukcapil): mekanisme apa yang menginisiasi request dan mengisi hasilnya (API pemerintah langsung, reseller, atau RPA)?

**Konteks**: OQ integrasi prioritas tertinggi; default tertulis — FE tidak merender tombol "request Dukcapil" sampai terjawab. *(Sumber: `BE-06 §11`; `FE-06 §11`; `BE-00 §11`.)*

**Informasi yang diminta**: arsitektur integrasi Dukcapil saat ini (inisiator, jalur, pengisi hasil) + rencana target.

**Yang perlu disiapkan untuk menutup gap ini**: dokumentasi/PIC integrasi Dukcapil.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-NPP-02 / OQ-PASSNET-01 / OQ-PASSNET-02 / OQ-DISB-09] (P1) — Integrasi Passnet: proses apa yang men-drain antrian sinkronisasi (flag `is_sync='0'`) dan menulis balik? Scope Passnet hanya master data NPP, atau juga eksekusi payment? Apakah record outbound pernah di-acknowledge?

**Konteks**: menentukan desain outbox/ACL Passnet (output aktivasi kontrak). *(Sumber: `BE-05 §11`; `BE-00 §11`.)*

**Informasi yang diminta**: proses drainer + scope kontrak Passnet + mekanisme ack.

**Yang perlu disiapkan untuk menutup gap ini**: dokumen kontrak Passnet + PIC integrasinya.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-DISB-05 / OQ-RPT-06] (P1) — Pembayaran ke dealer (Dealer Payment): apa penentu sebuah kontrak masuk batch pembayaran, dan siapa yang mem-post header di database PAYMENT eksternal?

**Konteks**: pola tarik-data (PULL) downstream sudah dikonfirmasi; kriteria eligibility-nya belum. *(Sumber: `BE-05 §11`; `BE-00 §11`.)*

**Informasi yang diminta**: kriteria eligibility batch + pemilik proses posting header.

**Yang perlu disiapkan untuk menutup gap ini**: dokumentasi proses Dealer Payment.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-DLRPTN-05 / OQ-REF-05 / OQ-EXTMASTERS-05] (P1 — aksi security) — Kami menemukan kredensial tersimpan PLAINTEXT: kolom `MsBank.PasscodeBiBca` dan kredensial database di layanan MINIAPI. Masih live? Mohon review & rotasi — independen dari proyek rebuild.

**Konteks**: risiko keamanan berjalan + kredensial ikut tersalin saat migrasi. *(Sumber: `BE-07 §11`; `BE-00 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Kredensial dirotasi + dipindah ke secret store; kolom plaintext tidak dibawa ke sistem baru ← **REKOMENDASI KAMI** (usulan baru — aksi security standar)
- [ ] **B.** Sudah tidak live — konfirmasi tertulis
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: status live/tidaknya kedua kredensial + jadwal rotasi.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-BE07-02] (P1) — Sinkronisasi data pegawai HR → mirror di core baru: mekanisme & frekuensi apa yang tersedia (batch/CDC/API)? Apakah field resign tersedia cukup real-time untuk auto-nonaktifkan user?

**Konteks**: user resign harus segera kehilangan akses. *(Sumber: `BE-07 §11`.)*

**Informasi yang diminta**: opsi mekanisme sync yang disediakan infra + latensi field resign.

**Yang perlu disiapkan untuk menutup gap ini**: kontak pemilik sistem HR + katalog interface yang tersedia.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CSB-02] (P1) — Batas ukuran & tipe file upload dokumen: berapa nilai final dan di mana dienforce? (Legacy: peringatan ±500 KB yang tidak pernah dienforce end-to-end.)

**Konteks**: komponen upload dipakai intake, CM, Vertel, dealer. *(Sumber: `FE-00 §11`; `FE-01 §11`; `FE-06 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Limit server-side tunggal + mirror di FE (nilai ditetapkan bersama infra, mis. 2 MB/file) ← **REKOMENDASI KAMI** (usulan baru — nilai belum ada di dokumen sumber)
- [ ] **B.** Limit berbeda per jenis dokumen — lampirkan daftarnya
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: kapasitas storage target + nilai limit resmi.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### B — Prioritas P2

### [OQ-NEOSCORE-01 — residual target] (P2) — Skoring vendor NeoScore: di legacy dipanggil langsung dari tier frontend (HTTP polos + data pribadi — sudah kami tandai TIDAK ditiru). Ratifikasi arah target: panggilan pindah ke backend via jalur TLS + kontrak respons terstruktur dengan vendor.

**Pilihan jawaban**:

- [ ] **A.** Ratifikasi: call BE-owned via ACL TLS; FE tidak pernah memanggil vendor ← **REKOMENDASI KAMI** (USULAN tertulis)
- [ ] **B.** Skema lain — jelaskan
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: kontak vendor NeoScore untuk kontrak respons/transport/authn. *(Sumber: `BE-02 §11`; `FE-02 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-SLIK-07 / OQ-PEFINDO-01] (P2) — Orkestrasi pengecekan multi-biro (SLIK/Dukcapil/Pefindo): bagaimana fan-out-nya diatur, dan mekanisme apa yang menginisiasi request Pefindo?

**Informasi yang diminta**: arsitektur orkestrasi biro saat ini + PIC-nya.

**Yang perlu disiapkan untuk menutup gap ini**: dokumentasi integrasi biro kredit. *(Sumber: `BE-02 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-RAC-05 / OQ-RAC-06] (P2) — Vocabulary lengkap status balikan RAC (`rac_get_status`/`STATUS_DESC` — ada nilai "pending" tersendiri?) dan daftar lengkap nilai `message` history RAC (kode legacy hanya memeriksa `'0'`).

**Informasi yang diminta**: kamus status & message resmi dari pemilik risk engine.

**Yang perlu disiapkan untuk menutup gap ini**: dokumen kontrak RAC. *(Sumber: `BE-02 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-NPPVTL-04] (P2) — Cek aset eksternal Rapindo (mobil non-top-up): fungsi CHECK-nya masih requirement integrasi? (Cascade registrasinya sudah mati — jangan dikonflasi.)

**Pilihan jawaban**:

- [ ] **A.** Masih requirement → endpoint validasi aset dibangun via ACL ← **REKOMENDASI KAMI** (check terpantau live di legacy)
- [ ] **B.** Sudah tidak diperlukan → tidak dibawa
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: status kontrak/kerjasama Rapindo. *(Sumber: `BE-05 §11`; `FE-05 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-PRODASSET-01 / OQ-PRODASSET-03] (P2) — Katalog aset: legacy punya DUA katalog paralel (jenis→kelas→merek vs item-merek-tipe + harga OTR). Disatukan atau tetap dua? Plus: endpoint product-list legacy ternyata stub — dari mana UI mengambil daftar produk sebenarnya?

**Pilihan jawaban**:

- [ ] **A.** Disatukan menjadi satu katalog aset kanonik ← **REKOMENDASI KAMI** (usulan baru — keputusan desain, dititip ke arsitektur ITEC)
- [ ] **B.** Tetap dua katalog (jelaskan pembagian perannya)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: sumber product-list nyata + kepemilikan katalog aset. *(Sumber: `BE-00 §11`; `BE-07 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-REF-01] (P2) — Apakah nilai kode tipe aplikasi (`application_type_id`) di-parse sistem eksternal (Passnet/GL)?

**Pilihan jawaban**:

- [ ] **A.** Ya → kode dikunci `[LOCKED]`, tidak boleh berubah ← **REKOMENDASI KAMI** (default paling aman)
- [ ] **B.** Tidak → kode boleh didesain ulang
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi pemilik integrasi Passnet/GL. *(Sumber: `BE-07 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-SHELL-03] (P2) — Cookie role-claim legacy: pernah dimaksudkan ada diferensiasi role? Konfirmasi layer claim lama aman dibuang total (arah role sudah dijawab sensus role meeting).

**Pilihan jawaban**:

- [ ] **A.** Aman dibuang total ← **REKOMENDASI KAMI** (arah tertulis)
- [ ] **B.** Ada pemakaian lain — jelaskan
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: — *(Sumber: `FE-00 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### B — Prioritas P3

### [GAP-FE00-05] (P3) — Durasi idle timeout session & perilaku warning-nya?

**Pilihan jawaban**:

- [ ] **A.** 30 menit sliding (paritas legacy) + warning sebelum logout ← **REKOMENDASI KAMI** (default usulan tertulis)
- [ ] **B.** Nilai lain sesuai kebijakan security — sebutkan
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: kebijakan keamanan session ITEC. *(Sumber: `FE-00 §11.1`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CAUI-06] (P3) — Tombol skor NeoScore di layar motor ter-wire ke action milik mobil, dan kebijakan caching-nya berbeda (motor selalu call; mobil cek log dulu). Unifikasi disengaja, atau bug copy-paste?

**Pilihan jawaban**:

- [ ] **A.** Satu varian call + kebijakan caching seragam di sistem baru ← **REKOMENDASI KAMI** (usulan baru — belum tertulis di dokumen sumber)
- [ ] **B.** Perbedaan disengaja — jelaskan alasannya
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi PIC integrasi NeoScore. *(Sumber: `BE-02 §11`; `FE-02 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

---

## C. DBA / Data Owner (MACF)

Permintaan dump/akses, profiling data produksi, dan pertanyaan "siapa yang menulis/menjalankan ini" yang hanya bisa dijawab dari sisi database/job server.

### C — Prioritas P1

### [OQ-EXTMASTERS-01 + OQ-REF-04] (P1) — ✅ SEBAGIAN TERJAWAB (dump diterima 2026-07-22). SISA: master mana yang dimiliki aplikasi acquisition (boleh CRUD) vs read-only milik sistem lain, dan apakah linked server `MACF-DBSTG / DBMCF / DBKONSOL / dbrep` masih reachable/live?

**Konteks**: **UPDATE 2026-07-22 — dump DDL `FC_MSTAPP_MCF` SUDAH kami terima** (310 tabel + 112 SP + 2 UDF; census di KB `30-data-model/external-masters-census.md`) — permintaan dump DITUTUP, terima kasih. Yang masih kami butuhkan tinggal dua hal di bawah. *(Sumber: `DATA-MIGRATION-PLAN.md §1, §6`; `BE-00 §11`; `BE-07 §11`.)*

**Informasi yang diminta (sisa)**:

- Daftar kepemilikan: master owned vs read-only, per tabel (cukup untuk 171 tabel read-set acquisition — daftar kami sediakan dari census).
- Status live/reachable 4 linked server di atas.

**Yang perlu disiapkan untuk menutup gap ini**: daftar ownership + cek konfigurasi linked server dari DBA.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-EXTMASTERS-07] (P1 — baru 2026-07-22) — 8 objek direferensikan kode acquisition tapi TIDAK ada di dump `FC_MSTAPP_MCF` yang kami terima: `ms_insurance_cover_type`, `ms_dp_minim_gross_hdr_master`, `ms_dp_minim_gross_dtl_master`, `DP_minim_gross_amount_dtl_master`, `ms_CAS_risk_category`, `supplier_mapping_OJK`, `ms_company_branch_HBA3`, `ms_item_brand_type_jangandipake`. Statusnya apa?

**Konteks**: `ms_insurance_cover_type` dipakai jalur AKTIF perhitungan asuransi CM (12 referensi, full-stack); `ms_dp_minim_gross_*` dipakai `sp_insert_cas_po`. Kalau tabel-tabel ini benar sudah tidak ada di produksi, jalur SP perujuknya error/mati — kalau masih ada, dump-nya berarti parsial. *(Sumber: KB `30-data-model/external-masters-census.md §3`.)*

**Pilihan jawaban**:

- [ ] **A.** Tabel-tabel ini sudah DI-DROP dari `FC_MSTAPP_MCF` → SP perujuknya memang jalur mati (sebutkan kapan/kenapa bila tahu)
- [ ] **B.** Masih ada tapi TIDAK ikut ter-ekspor (dump parsial) → kirim DDL susulan ← **REKOMENDASI KAMI** (paling mungkin untuk `ms_insurance_cover_type` yang jalurnya aktif)
- [ ] **C.** Ada di database LAIN (sebutkan database-nya per tabel)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: cek keberadaan 8 objek di produksi (`sys.tables` lintas database) + DDL susulan bila ada.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-EXTMASTERS-08] (P2 — baru 2026-07-22) — `MsPublicHoliday` ada di DUA database (`FC_ACQ_MCF` dan `FC_MSTAPP_MCF`, sama-sama 3 kolom `ID/Name/Date`) — copy mana yang otoritatif/di-maintain hari ini?

**Konteks**: menentukan sumber migrasi `mst_public_holiday` (dipakai kalkulasi due-date & eskalasi hari libur). *(Sumber: KB `30-data-model/external-masters-census.md §4`; `BE-07 §3.4`.)*

**Pilihan jawaban**:

- [ ] **A.** Copy `FC_ACQ_MCF` yang di-maintain; `FC_MSTAPP_MCF` copy lama/sinkron
- [ ] **B.** Copy `FC_MSTAPP_MCF` yang di-maintain; `FC_ACQ_MCF` copy lama/sinkron
- [ ] **C.** Dua-duanya di-maintain terpisah (perlu rekonsiliasi isi saat migrasi)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: bandingkan isi + timestamp tulis terakhir kedua tabel di produksi.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-MIG-05] (P1 — BLOCKER) — Kami butuh akses snapshot data produksi (atau statistik dari DBA) untuk mem-profiling SEMUA tabel/kolom yang kami klasifikasi "mati/DISCARD" berdasarkan pembacaan kode: % terisi, jumlah nilai distinct, tanggal tulis terakhir, sampel nilai.

**Konteks**: prinsip proyek — bukti kode ≠ bukti data; kolom "mati menurut kode" bisa saja terisi oleh sistem lama/eksternal. Tidak ada yang di-drop dari schema target sebelum profiling lolos; seluruh data legacy tetap diarsip 100% apa pun hasilnya. *(Sumber: `DATA-MIGRATION-PLAN.md §3, §7`.)*

**Informasi yang diminta**: akses snapshot read-only ATAU statistik per tabel/kolom untuk daftar klaim dead/DISCARD (daftar kami lampirkan).

**Yang perlu disiapkan untuk menutup gap ini**: persetujuan akses + jadwal snapshot dari DBA.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-RAC-02] (P1) — SQL Agent job / scheduler apa yang memanggil poller hasil RAC (`sp_agent_rac_to_cm_bulk`), dan berapa frekuensinya?

**Konteks**: menentukan SLA poller pengganti di sistem baru. *(Sumber: `BE-02 §11`; `BE-00 §11`.)*

**Informasi yang diminta**: nama job + jadwal + histori kegagalannya (bila ada).

**Yang perlu disiapkan untuk menutup gap ini**: ekspor daftar SQL Agent job terkait acquisition.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-NPP-13] (P1) — Tabel riwayat approval hierarki NPP (`tr_hierarchy_approval_transaction`) DIBACA kode saat kontrak aktif, tetapi kami tidak menemukan penulisnya. Ada job/archival writer di sisi DB, atau dead code?

**Konteks**: menentukan sumber audit history NPP untuk migrasi. *(Sumber: `BE-05 §11`.)*

**Informasi yang diminta**: proses penulis tabel tersebut (job/trigger/sistem lain) + profil isinya di produksi.

**Yang perlu disiapkan untuk menutup gap ini**: pengecekan trigger/job di DB produksi.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-VERIF-02] (P1) — Beberapa SP (survey Zoom 2W, OJK-checking → FCL) tidak punya pemanggil di codebase aplikasi. Ada pemanggil eksternal di sisi database/job — integrasi live atau dead?

**Konteks**: menentukan apakah integrasi survey/OJK-checking harus dibawa. *(Sumber: `BE-06 §11`; `BE-01 §11`.)*

**Informasi yang diminta**: daftar pemanggil eksternal SP tersebut (job msdb, sistem lain) + frekuensi eksekusi terakhir.

**Yang perlu disiapkan untuk menutup gap ini**: audit pemanggil dari sisi DBA.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CUSTMASTER-04 / OQ-DLRPTN-13] (P1) — Master (lookup pemohon, dealer, bank, payment) di-maintain lewat apa hari ini? Tidak ada jalur tulis di aplikasi yang kami baca — ada aplikasi admin lain di luar repo, atau di-update langsung di DB?

**Konteks**: menentukan perluasan layar CRUD & sumber data migrasi masters. *(Sumber: `BE-07 §11`.)*

**Informasi yang diminta**: cara maintain nyata per kelompok master (aplikasi sibling? script DBA?).

**Yang perlu disiapkan untuk menutup gap ini**: inventaris tooling admin yang ada.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-PRODASSET-05] — ✅ RESOLVED 2026-07-22 (tidak perlu dijawab lagi)

Body SP write `*_trans_type_hierarchy` ternyata IKUT dalam dump `FC_MSTAPP_MCF` yang kami terima. Hasil analisis: validasi backend ADA (uniqueness kombinasi PIC+tipe+level+cabang; satu PIC tidak boleh 2× aktif per hierarki; invarian single-approver-di-puncak; uniqueness kode/prefix), TAPI ada dua celah: referential check NIK tidak ada (NIK tak dikenal tetap ter-insert) dan kontiguitas level tidak divalidasi. Detail: KB `30-data-model/external-masters-census.md §5`; `BE-07 §3.3` (V1–V9).

### [OQ-MASTERDATA-02] (P1) — ⚠️ PREMIS TER-KOREKSI 2026-07-22: backend legacy TERNYATA memvalidasi sebagian aturan tangga hierarki (di SP `FC_MSTAPP_MCF`, bukan hanya JavaScript). Pertanyaan yang tersisa: seberapa besar data produksi melanggar DUA celah yang benar-benar tidak divalidasi?

**Konteks**: dari body SP di dump `FC_MSTAPP_MCF` (2026-07-22): uniqueness + invarian approver DIVALIDASI backend; yang TIDAK divalidasi hanya (1) NIK PIC tak dikenal tetap ter-insert (tanpa referential check), (2) gap/kontiguitas level. Cleansing import difokuskan ke dua celah itu. *(Sumber: KB `30-data-model/external-masters-census.md §5`; `BE-07 §3.3/§11`.)*

**Pilihan jawaban**:

- [ ] **A.** Setuju — profiling produksi difokuskan ke dua celah itu (NIK yatim + gap level) ← **REKOMENDASI KAMI**
- [ ] **B.** Ada validasi lain lagi di luar SP ini — tunjukkan lokasinya
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: profil pelanggaran dua celah tsb. di data hierarki produksi (bisa satu paket dengan OQ-MIG-05).

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-DLRPTN-01] (P1) — Master dealer punya tiga tabel (`MsDealer`, `MsDealer1`, `MsDealerBackup20221227`) — mana yang LIVE dipakai produksi?

**Konteks**: menentukan field census final entity DEALER + sumber migrasi; field ekstra (`Phone2/Fax/EmailGroup/IsDefaultMokas`) default tidak dirender sampai jelas. *(Sumber: `BE-07 §11`; `FE-07 §11`; `BE-00 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** `MsDealer` live; lainnya backup/abandoned ← **REKOMENDASI KAMI** (dugaan wajar — perlu konfirmasi)
- [ ] **B.** `MsDealer1` live
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: cek timestamp tulis terakhir ketiga tabel di produksi.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-GAP-04] (P1) — Tabel deviasi umum (`tr_general_deviation`): PENULIS barisnya tidak ada di aplikasi (0 insert di seluruh kode). Diisi manual/job DBA/sistem lain? Plus: apa makna bisnis kolom `param_2` (tanggal) yang 0-pemakaian?

**Konteks**: makna kolom lain sudah kami verifikasi (ID = kode jenis deviasi; param_4 = flag persetujuan; CGS_no = nomor memo CGS Cabang); menentukan pemodelan deviasi + seed aturan. *(Sumber: `gap-entities.md §4`; `BE-03 §11`; `BE-00 §11`.)*

**Informasi yang diminta**: jalur insert nyata (siapa/berapa sering) + makna `param_2`.

**Yang perlu disiapkan untuk menutup gap ini**: audit writer dari sisi DBA + profil isi kolom.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CRSCORE-01 / OQ-OVERVIEW-01] (P1) — Catatan analisa 5C ditulis ke TIGA tempat paralel di legacy, dan ada dua modul kembar (CA vs CREDITSANALYST) dengan controller bernama sama. Mohon profiling produksi: tabel mana yang benar-benar terisi/terupdate?

**Konteks**: tidak bisa dijawab dari kode (sudah kami coba); penentu source-of-truth modul analisa; memblokir kontrak penyimpanan 5C (GAP-FE02-03). *(Sumber: `BE-00 §11`; `BE-02 §11`.)*

**Informasi yang diminta**: statistik tulis (row count, last-write) ketiga target 5C + kedua modul kembar di produksi.

**Yang perlu disiapkan untuk menutup gap ini**: query profiling dari DBA (kami siapkan daftar tabelnya).

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### C — Prioritas P2

### [OQ-GAP-05] (P2) — Tabel penyimpangan eff-rate lama (`tr_penyimpangan_effrate`) 0 referensi di kode. Konfirmasi via data produksi bahwa sudah digantikan `tr_general_deviation` dan aman di-drop dari schema target.

**Pilihan jawaban**:

- [ ] **A.** Konfirmasi drop dari schema target (data tetap diarsip 100%) ← **REKOMENDASI KAMI** (default tertulis, berlaku setelah profiling lolos)
- [ ] **B.** Masih terisi/dipakai → reklasifikasi minimal arsip-baca
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: profil isi tabel di produksi. *(Sumber: `gap-entities.md §4`; `BE-03 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-GAP-07] (P2) — Tabel mapping NIK repeat-order (`tr_mapping_NIK_RO`): tidak ada jalur pengisian di aplikasi (0 insert; tidak terdaftar di EF). Di-load dari mana? Perlu dibawa hidup, atau cukup diselesaikan sekali oleh dedup migrasi customer?

**Pilihan jawaban**:

- [ ] **A.** Sekali-selesai — diserap dedup migrasi customer, tidak dibawa hidup ← **REKOMENDASI KAMI** (usulan baru — belum tertulis di dokumen sumber)
- [ ] **B.** Dibawa hidup sebagai mapping berjalan (sebutkan proses pengisinya)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: informasi loader nyata (migrasi satu-kali / job DBA / sistem lain). *(Sumber: `gap-entities.md §4`; `BE-01 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-GAP-10] (P2) — Buffer balikan RAC (`tr_temp_verify`, tanpa primary key): berapa lama data tinggal, pernah dibersihkan? Bagaimana multi-baris per kontrak ditangani bila balikan datang lebih dari sekali?

**Informasi yang diminta**: kebijakan retensi/cleanup + profil multi-baris per kontrak di produksi.

**Yang perlu disiapkan untuk menutup gap ini**: profiling tabel (input untuk skenario migrasi in-flight OQ-MIG-01-B). *(Sumber: `gap-entities.md §4`; `BE-03 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CORE-08 / OQ-DATA-03 (+OQ-CRSCORE-03)] (P2) — Pasangan tabel BYTE-IDENTIK progress approval (`tr_CA_approval_progress` vs `_transaction`): mana live vs history? Plus scope kunci `CF_No` (khusus CA atau lintas jenis transaksi)?

**Informasi yang diminta**: statistik tulis kedua tabel + penjelasan pembagian perannya.

**Yang perlu disiapkan untuk menutup gap ini**: profiling produksi. *(Sumber: `BE-02 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-NOTIF-01 / OQ-NOTIF-02] (P2) — Email/SMS: trigger/scheduler apa yang jalan di produksi, dan varian SP mail mana yang benar-benar deployed?

**Informasi yang diminta**: daftar job mail aktif + SP yang dipanggilnya (relevan juga untuk email blast dealer OQ-MEET-01).

**Yang perlu disiapkan untuk menutup gap ini**: ekspor job scheduler mail. *(Sumber: `BE-00 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-VERIF-03] (P2) — Tabel verifikasi lama `CFVerifikasiKonsumen`: benar-benar tidak dipakai end-to-end, atau ada tool/report eksternal yang menulis/membaca langsung?

**Pilihan jawaban**:

- [ ] **A.** Tidak dipakai → aman di-drop dari schema target (data diarsip) ← **REKOMENDASI KAMI** (temuan kode: tabel pengganti sudah kanonik)
- [ ] **B.** Ada pemakai eksternal — sebutkan
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: cek akses eksternal + profil tulis terakhir. *(Sumber: `BE-06 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-DLRPTN-02] (P2) — `ms_credit_source` di DB acquisition: master lokal asli, atau artefak export dari DB master?

**Pilihan jawaban**:

- [ ] **A.** Lokal asli → ikut migrasi acquisition ← **REKOMENDASI KAMI** (dugaan dari temuan kode — perlu konfirmasi)
- [ ] **B.** Artefak export → sumber aslinya di masters-service
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi lineage tabel. *(Sumber: `BE-07 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-DLRPTN-07] (P2) — Lokasi BPKB: sumber otoritatif `ms_bpkb_location` lokal, atau union dengan linked-server `MsLokasiBPKB`? Harus SATU sumber saat migrasi.

**Pilihan jawaban**:

- [ ] **A.** Lokal `ms_bpkb_location` sebagai sumber tunggal ← **REKOMENDASI KAMI** (usulan baru — perlu konfirmasi)
- [ ] **B.** Linked-server yang otoritatif
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: perbandingan isi kedua sumber. *(Sumber: `BE-07 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-ACQCAS-08] (P2) — Tabel whitelist/override blacklist: bagaimana di-maintain hari ini (tidak ada CRUD di aplikasi)?

**Informasi yang diminta**: proses maintain nyata (script? langsung DB?) + siapa yang berwenang.

**Yang perlu disiapkan untuk menutup gap ini**: informasi dari DBA/ops; kandidat layar admin baru di menu Master. *(Sumber: `BE-01 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### C — Prioritas P3

### [OQ-GAP-09] (P3) — Konfirmasi drop 3 tabel 0-referensi: `tr_GFCIT_AccountMaster` (mungkin dipakai modul GL di luar acquisition), `produk_other_income_skema_III` (apa arti "skema III"?), `temppotonganro`.

**Pilihan jawaban**:

- [ ] **A.** Drop dari schema target setelah profiling (data diarsip) ← **REKOMENDASI KAMI** (default)
- [ ] **B.** Ada pemakai di luar acquisition — sebutkan
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: profil isi + cek pemakaian lintas modul. *(Sumber: `gap-entities.md §4`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CMPO-13] (P3) — 15 kolom `tr_CM` yang maknanya tak terjawab (a.l. `finish_date`, `po_source`, `komper_max`) + beberapa enum kecil: mohon profiling isi di produksi untuk menentukan bawa/drop per kolom.

**Informasi yang diminta**: % terisi + sampel nilai per kolom (kolom-kolom ini kami parkir di staging, tidak masuk schema live sampai terjawab).

**Yang perlu disiapkan untuk menutup gap ini**: profiling + sesi singkat domain expert. *(Sumber: `BE-04 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-SLIK-01] (P3) — Keluarga tabel `CLKNAE*` (fallback tier-3) itu apa, dan apa relasinya ke SLIK/Pefindo?

**Informasi yang diminta**: asal-usul & pengisi tabel tersebut.

**Yang perlu disiapkan untuk menutup gap ini**: — *(Sumber: `BE-02 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-NEOSCORE-03] (P3) — Write-back skor ke `DUMP_MACF.tbl_Submit.score_rimo` masih load-bearing (dipakai proses lain)?

**Informasi yang diminta**: konsumen kolom tersebut di produksi.

**Yang perlu disiapkan untuk menutup gap ini**: cek dependensi lintas DB. *(Sumber: `BE-02 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CRSCORE-04] (P3) — Skor AML/PEP yang tersimpan: pernah benar-benar DIBACA dari tier mana pun (web/mobile/job)?

**Informasi yang diminta**: profil akses baca kolom skor tersebut.

**Yang perlu disiapkan untuk menutup gap ini**: — *(Sumber: `BE-02 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CRSCORE-05] (P3) — Aturan "jumlah referensi naik mengikuti kolektibilitas": dienforce di luar bagian kode yang kami baca?

**Informasi yang diminta**: lokasi enforcement lain bila ada.

**Yang perlu disiapkan untuk menutup gap ini**: — *(Sumber: `BE-02 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-DISB-01] (P3) — Proses apa yang membalik flag `is_sync` subledger dari unset → synced?

**Informasi yang diminta**: job/proses pengubah flag tersebut.

**Yang perlu disiapkan untuk menutup gap ini**: — *(Sumber: `BE-00 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-NPP-01] (P3) — `sp_validate_npp_rfa` dipanggil aplikasi tetapi tidak ada di dump lokal — hosted di environment lain?

**Informasi yang diminta**: lokasi + body SP tersebut.

**Yang perlu disiapkan untuk menutup gap ini**: — *(Sumber: `BE-05 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-NPP-05] (P3) — Status NPP `V` (Verify): reachable di produksi? (Tidak ada jalur tulis di kode.)

**Informasi yang diminta**: profil nilai status NPP di produksi.

**Yang perlu disiapkan untuk menutup gap ini**: — *(Sumber: `BE-05 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-NPP-09] (P3) — Siapa konsumen `tr_batching_trans` (ditulis aplikasi tanpa pembaca lokal — job batch/collection eksternal?)

**Informasi yang diminta**: konsumen nyata tabel tersebut.

**Yang perlu disiapkan untuk menutup gap ini**: — *(Sumber: `BE-05 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-NPP-11] (P3) — `SpSyncToPassnetR2_reversal` terkait antrian Passnet, atau kebetulan nama? (Analisa kami: unrelated — tinggal konfirmasi formal.)

**Pilihan jawaban**:

- [ ] **A.** Unrelated ← **REKOMENDASI KAMI** (sesuai isi file)
- [ ] **B.** Terkait — jelaskan
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: — *(Sumber: `BE-05 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-AC-06] (P3) — Ada guard di aplikasi legacy yang mencegah submitter CM menjadi approver di case yang sama? (Hanya untuk pemahaman data historis migrasi — requirement target sudah diputuskan: self-approval diblokir.)

**Informasi yang diminta**: ada/tidaknya guard historis (memengaruhi interpretasi data approval lama).

**Yang perlu disiapkan untuk menutup gap ini**: — *(Sumber: `BE-03 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

---

## D. Risk & Compliance

Kebijakan regulasi (OJK/SLIK/AML), gate risiko, retensi data pribadi, dan kebijakan Instant-Approval.

### D — Prioritas P1

### [OQ-GAP-03] (P1 — REGULATORI) — Kami menemukan mekanisme BYPASS pengecekan SLIK di jalur sinkronisasi mobile. Kondisi bisnis apa yang memicunya, SIAPA yang berwenang mengaktifkan, dan apakah OJK/compliance mensyaratkan approval + jejak audit yang lebih kaya?

**Konteks**: jejak audit legacy hanya satu baris teks `'by pass slik'` — TANPA nomor kontrak dan tanpa identitas pemberi izin. *(Sumber: `gap-entities.md §4`; `BE-02 §11`; `BE-00 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Jalur bypass TIDAK diaktifkan di sistem baru (fail-closed) ← **REKOMENDASI KAMI** (default tertulis di PRD, berlaku sampai diputus)
- [ ] **B.** Bypass dipertahankan → wajib approval ber-identitas + audit lengkap (nomor kontrak, pemberi izin, alasan) (konsekuensi: fitur + layar approval baru)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: daftar kondisi bisnis pemicu bypass + kebijakan wewenang + ketentuan OJK yang berlaku.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-REG-02] (P1) — Screening AML hanya terjadi di tahap intake (entry) di legacy. Apakah itu desain kontrol yang memang di-sign-off, atau seharusnya ada titik screening kedua di tahap analisa kredit yang tidak pernah dibangun?

**Konteks**: arsitektur kontrol AML sistem baru berisiko mewarisi gap kepatuhan bila tidak dikonfirmasi. *(Sumber: `BE-01 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Tambahkan titik screening kedua di analisa kredit ← **REKOMENDASI KAMI** (usulan baru — belum tertulis di dokumen sumber; konservatif)
- [ ] **B.** Intake-only memang desain yang di-sign-off — lampirkan referensinya
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: kebijakan titik screening AML resmi.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-SLIK-05] (P1) — Kesegaran hasil SLIK 30 hari: dienforce sebagai HARD BLOCK sebelum legalisasi NPP, atau cukup informasi bagi analis?

**Konteks**: legacy tidak meng-enforce; kebijakan fail-closed untuk gate regulated sudah di-sign-off secara umum. *(Sumber: `BE-02 §11`; `BE-05 §11`; `BE-00 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Hard block ← **REKOMENDASI KAMI** (konsisten kebijakan fail-closed yang sudah di-sign-off)
- [ ] **B.** Informational saja (konsekuensi: NPP bisa jalan dengan SLIK basi)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi ketentuan kesegaran SLIK yang berlaku.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CRSCORE-02] (P1) — Batas DSR 40% dan kesegaran data 30 hari pada tahap ANALISA: hard block (menghentikan proses) atau advisory (peringatan yang bisa dilewati)?

**Konteks**: layar sudah kami siapkan untuk dua mode. *(Sumber: `BE-02 §11`; `FE-02 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Advisory di tahap analisa ← **REKOMENDASI KAMI** (default sementara yang tertulis di PRD; gate keras tetap ada di hilir)
- [ ] **B.** Hard block di tahap analisa
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: keputusan risk policy owner.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [FE-OQ-01] (P1) — Bila hasil cek blacklist/AML positif (hit), apakah layar harus MEMBLOKIR keras proses input? Legacy tidak pernah memblokir (hanya menampilkan hasil).

**Konteks**: hard-block di UI = kapabilitas baru yang butuh sign-off compliance. *(Sumber: `FE-01 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Tampilan informasional di layar; gate otoritatif tetap di server ← **REKOMENDASI KAMI** (default tertulis di PRD)
- [ ] **B.** Hard-block di layar juga (input berhenti saat hit)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: sign-off compliance.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### D — Prioritas P2

### [OQ-MEET-04] (P2) — Jalur Instant-Approval (auto-approve tanpa antrian manusia untuk aplikasi mobile): apa aturan eligibility-nya per produk/plafond?

**Konteks**: mekanisme lane tetap dibangun (keputusan meeting D-01 S11); yang menunggu adalah ISI aturan. *(Sumber: `_MEETING-DECISIONS`; `BE-02/BE-03 §11`; `FE-01/FE-03 §11`.)*

**Informasi yang diminta**: matriks eligibility (produk × plafond × kriteria risiko) dari risk policy owner.

**Yang perlu disiapkan untuk menutup gap ini**: dokumen kebijakan IA resmi.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-AC-04] (P2) — Dua allow-list Instant-Approval legacy (daftar kontrak & cabang trial): dimigrasikan sebagai policy aktif, atau di-retire?

**Pilihan jawaban**:

- [ ] **A.** Retire — kebijakan IA baru diisi dari jawaban OQ-MEET-04 ← **REKOMENDASI KAMI** (usulan baru — belum tertulis di dokumen sumber)
- [ ] **B.** Migrasi sebagai policy aktif (pilot dilanjutkan apa adanya)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: status pilot IA (masih berjalan atau selesai). *(Sumber: `BE-03 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-MIG-02] (P2) — Arsip permanen data legacy (termasuk yang tidak dibawa ke sistem baru): berapa lama retensinya dan di mana disimpan — menimbang kebutuhan audit OJK vs pembatasan UU PDP?

**Informasi yang diminta**: masa retensi resmi + kelas penyimpanan + aturan akses arsip.

**Yang perlu disiapkan untuk menutup gap ini**: kebijakan retensi dari compliance. *(Sumber: `DATA-MIGRATION-PLAN.md §7`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-GAP-11] (P2) — Log integrasi DOKU berisi PII (NIK, tanggal lahir, nomor rekening): apa kebijakan retensi & audit-nya menurut UU PDP — dan apakah log ini dibawa ke sistem baru?

**Pilihan jawaban**:

- [ ] **A.** Dibawa dengan retensi terbatas + akses ter-restriksi ← **REKOMENDASI KAMI** (usulan baru — belum tertulis di dokumen sumber)
- [ ] **B.** Tidak dibawa — cukup diarsip
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: kebijakan UU PDP dari Compliance/DPO. *(Sumber: `gap-entities.md §4`; `BE-04 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CAUI-05] (P2) — Checklist dokumen wajib debitur individual di legacy MELOMPATI nomor item 3–8. Item tersebut dipensiunkan sengaja, atau ada pengecekan KYC yang hilang?

**Pilihan jawaban**:

- [ ] **A.** Dipensiunkan sengaja → set dokumen mengikuti yang tersisa
- [ ] **B.** KYC hilang → item ditambahkan kembali ← **REKOMENDASI KAMI** bila tidak ada bukti pensiun resmi (usulan baru — konservatif)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: daftar dokumen KYC wajib individual resmi. *(Sumber: `BE-02 §11`; `FE-02 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CAUI-07] (P2) — Formula DSR yang sah: MEMASUKKAN penghasilan pasangan (formula yang DITAMPILKAN ke analis di legacy) atau TIDAK (3 formula server legacy)?

**Konteks**: bila ada dasar regulasi, formula kami kunci `[LOCKED]`. *(Sumber: `BE-02 §11`; `FE-02 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Exclude penghasilan pasangan (mengikuti formula server) ← **REKOMENDASI KAMI** (usulan baru — belum tertulis di dokumen sumber)
- [ ] **B.** Include penghasilan pasangan
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: dasar regulasi/kebijakan formula DSR.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CRSCORE-06] (P2) — Bila hasil screening RAC = REJECTED, apakah benar-benar memblokir lanjut ke analisa kredit?

**Pilihan jawaban**:

- [ ] **A.** Ya — blokir (fail-closed) ← **REKOMENDASI KAMI** (default tertulis; dokumen alur: "Rejected → stop")
- [ ] **B.** Tidak — advisory, analis boleh lanjut dengan justifikasi
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi kebijakan risk. *(Sumber: `BE-02 §11`; `FE-02 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-ACQCAS-05] (P2) — Re-screening blacklist saat kunci RFA memakai 5 alasan di lini mobil tetapi hanya 1 alasan di motor: disengaja, atau gap?

**Pilihan jawaban**:

- [ ] **A.** Gap → semua alasan diberlakukan ke kedua lini ← **REKOMENDASI KAMI** (usulan baru — konservatif kepatuhan)
- [ ] **B.** Disengaja → dipertahankan per lini
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi kebijakan screening. *(Sumber: `BE-01 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CASFE-03] (P2) — Re-check blacklist saat tombol Next di wizard memakai pasangan parameter tertentu (status kawin + ID penjamin). Apa input riil pengecekan yang seharusnya?

**Konteks**: di sistem baru re-screen dipindah ke server; kami butuh daftar input sah. *(Sumber: `FE-01 §11`.)*

**Informasi yang diminta**: parameter resmi re-check blacklist.

**Yang perlu disiapkan untuk menutup gap ini**: kebijakan screening compliance.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CASFE-06] (P2) — Interpretasi reason-code blacklist berbeda antara mobil dan motor: disatukan?

**Pilihan jawaban**:

- [ ] **A.** Disatukan — satu interpretasi lintas lini ← **REKOMENDASI KAMI** (usulan baru — belum tertulis di dokumen sumber)
- [ ] **B.** Tetap beda per lini
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: kamus reason-code blacklist resmi. *(Sumber: `FE-01 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-RAC-03] (P2) — Override "late approval" 120 menit pada jalur RAC: kebijakan yang disengaja, atau mitigasi race-condition teknis?

**Informasi yang diminta**: status kebijakan override tersebut (dipertahankan/dibuang di sistem baru).

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi pemilik kebijakan RAC. *(Sumber: `BE-02 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-DUKCAPIL-04] (P2) — Perlukah aturan kedaluwarsa hasil Dukcapil analog aturan 30 hari (seperti SLIK & verifikasi telepon), mengingat sama-sama menjadi masukan legalisasi NPP?

**Pilihan jawaban**:

- [ ] **A.** Ya — analog 30 hari ← **REKOMENDASI KAMI** (usulan baru — konsistensi antar gate)
- [ ] **B.** Tidak perlu kedaluwarsa
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: — *(Sumber: `BE-06 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-VERIF-01] (P2) — Hasil pencocokan Dukcapil / checklist KTP: selama ini meng-gate secara PROSEDURAL (sign-off manual sebelum approve verifikasi) walau tidak dienforce kode?

**Pilihan jawaban**:

- [ ] **A.** Murni informational — tidak jadi gate coded ← **REKOMENDASI KAMI** (default tertulis)
- [ ] **B.** Gate prosedural nyata → dibuat gate coded baru
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi praktik ops/compliance. *(Sumber: `BE-06 §11`; `FE-06 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-FE03-01] (P2) — Panel komite menampilkan TOTAL EXPOSURE lintas aplikasi (termasuk data pasangan by NIK) ke SEMUA level approver. Adakah batasan privacy yang mengharuskan masking/pembatasan per level?

**Pilihan jawaban**:

- [ ] **A.** Tampil untuk semua level approver (data dibutuhkan untuk keputusan) ← **REKOMENDASI KAMI** (usulan baru — belum tertulis di dokumen sumber)
- [ ] **B.** Masking/pembatasan per level — sebutkan aturannya
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: penilaian privacy (UU PDP) atas tampilan data pasangan. *(Sumber: `FE-03 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-MASTERDATA-01] (P2) — Master data legacy hanya bisa DINONAKTIFKAN (tidak pernah dihapus). Kebutuhan audit yang disengaja?

**Pilihan jawaban**:

- [ ] **A.** Ya — aturan "deactivate-only" dikunci sebagai kebijakan tetap ← **REKOMENDASI KAMI** (konsisten kebutuhan audit)
- [ ] **B.** Bukan — hard-delete diperbolehkan untuk master tertentu (sebutkan)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: — *(Sumber: `BE-07 §11`; `FE-07 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [BR-02-30 (kebijakan)] (P2) — Transkripsi manual hasil SLIK oleh analis vs dokumen asli di viewer: perlukah VALIDASI SILANG otomatis, atau tetap manual?

**Pilihan jawaban**:

- [ ] **A.** Tetap manual + indikator selisih opsional di layar ← **REKOMENDASI KAMI** (usulan baru — belum tertulis di dokumen sumber)
- [ ] **B.** Validasi silang otomatis (konsekuensi: parser hasil SLIK harus dibangun)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: — *(Sumber: `FE-02 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

---

## E. Tim MOOFI (aplikasi mobile)

Pertanyaan yang hanya bisa dijawab dari sisi repo/database MOOFI (`mob_acq`) atau menyangkut kontrak sinkronisasi mobile → core (E13/STEP 8).

### E — Prioritas P1

### [OQ-GAP-02] (P1) — Riwayat Instant-Approval (`tr_ia_history`): kami sudah menyisir seluruh kode core — TIDAK ada penulisnya di sisi FINCORE; semua indikasi menunjuk pipeline IA di sisi mobile (`mob_acq`). Proses apa di MOOFI yang menulisnya?

**Konteks**: menentukan sumber otoritatif migrasi log Instant-Approval + kontrak seed IA lane. *(Sumber: `gap-entities.md §4`; `BE-03 §11`; `BE-00 §11`.)*

**Informasi yang diminta**:
- Proses/service MOOFI yang menulis `tr_ia_history`.
- Daftar lengkap nilai `message` (selain 'Very High Potential Approval').
- Arti flag `isIA` dan `isActive`.

**Yang perlu disiapkan untuk menutup gap ini**: akses repo/DB MOOFI atau sesi dengan engineer MOOFI.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-PRD01-02] (P1) — Duality lock: MOOFI Step 6 mengirim "RFA Lock", sementara draft di core lahir dengan status RFA. Siapa emitter kanonik event `ApplicationLocked`, dan gate underwriting mana yang WAJIB diulang di sisi core saat ingest (vs dipercaya dari MOOFI)?

**Konteks**: menentukan kontrak sinkronisasi STEP 8 (E13) dan kepemilikan event. *(Sumber: `BE-01 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Emitter tunggal = core (modul intake); SEMUA screening regulated di-derive ulang di sisi core ← **REKOMENDASI KAMI** (default USULAN tertulis di PRD)
- [ ] **B.** Emitter = MOOFI; core mempercayai hasil screening mobile (konsekuensi: core bergantung pada kontrol di luar kendalinya)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: kesepakatan kontrak event dengan tim MOOFI.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### E — Prioritas P2

### [OQ-GAP-06] (P2) — Tabel jembatan `CASMobile_mappingfincore`: 0 pembaca/penulis di sisi core (dipastikan exhaustive); penulisnya diduga sistem CASMobile/MOOFI. Siapa yang menulis — dan masih diperlukan sebagai bridge, atau digantikan penuh kontrak sinkronisasi baru (E13)?

**Pilihan jawaban**:

- [ ] **A.** Digantikan penuh kontrak E13; tabel lama diarsipkan ← **REKOMENDASI KAMI** (usulan baru — sesuai arah konsolidasi kontrak sync)
- [ ] **B.** Masih dipakai sebagai bridge — jelaskan proses penulis/pembacanya
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi penulis dari sisi CASMobile/MOOFI. *(Sumber: `gap-entities.md §4`; `BE-01 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-ACQCAS-06] (P2) — Beberapa SP sinkronisasi (`spSyncPoolOrderToCAS`, `spNewZoomInsertSurvey_2w`, `SpSyncToFincoreR4_Reverse`) tidak punya pemanggil di kode core. Dari sisi MOOFI/mobile, mekanisme apa yang meng-invoke-nya?

**Konteks**: dibutuhkan untuk memetakan mekanisme legacy → kontrak E13. *(Sumber: `BE-01 §11`.)*

**Informasi yang diminta**: pemanggil + jadwal/trigger tiap SP tersebut dari sisi mobile.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi engineer MOOFI.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-ACQCAS-07] (P2) — Aksi "reverse to mobile" (aplikasi dikembalikan ke mobile): apakah berefek pada record aplikasi di sisi core, atau record core tetap hidup dengan data basi?

**Konteks**: risiko orphan/duplikat saat aplikasi bolak-balik antar sistem. *(Sumber: `BE-01 §11`.)*

**Informasi yang diminta**: perilaku nyata reverse terhadap record kedua sisi.

**Yang perlu disiapkan untuk menutup gap ini**: walkthrough alur reverse dengan tim MOOFI.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-REG-01] (P2) — Endpoint screening mana yang benar-benar dipanggil aplikasi MOBILE di produksi? (Untuk web sudah kami evidensikan; channel MOOFI belum.)

**Informasi yang diminta**: daftar endpoint screening yang dipanggil MOOFI + payload-nya.

**Yang perlu disiapkan untuk menutup gap ini**: akses kode MOOFI atau konfirmasi engineer. *(Sumber: `BE-01 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CMPOFE-06 / OQ-CMPO-08] (P2) — Form finalisasi CM saat pertama dibuka: nilai awalnya di-CLONE otomatis dari aplikasi (kandidat: draft kontrak hasil sinkronisasi MOOFI STEP 8), atau di-key-in ulang oleh user?

**Konteks**: kami tidak menemukan pre-seed di kode web; menentukan strategi default-value form. *(Sumber: `BE-04 §11`; `FE-04 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Clone server-side dari draft hasil sync STEP 8 ← **REKOMENDASI KAMI** (kandidat yang tertulis di PRD)
- [ ] **B.** Re-key manual oleh user (paritas praktik lama)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi isi draft kontrak yang dikirim MOOFI saat sync.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### E — Prioritas P3

### [OQ-AC-09] (P3) — Logika pembentukan chain approval channel MOBILE identik dengan desktop (di luar divergensi yang sudah kami catat)?

**Konteks**: prioritas praktis naik karena MOOFI = jalur utama Step 1–8. *(Sumber: `BE-03 §11`.)*

**Informasi yang diminta**: konfirmasi paritas logika chain-build mobile vs desktop.

**Yang perlu disiapkan untuk menutup gap ini**: review bersama engineer MOOFI.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

---

## F. Ops Cabang

Praktik kerja nyata di cabang, kebijakan operasional, dan toleransi downtime.

### F — Prioritas P1

### [OQ-MEET-05] (P1) — Status verifikasi konsumen KEDALUWARSA setelah 30 hari (keputusan meeting): apa konsekuensinya, dan kapan hitungan 30 hari dimulai?

**Konteks**: menentukan semantik gate 30-hari di NPP + UX antrian kedaluwarsa (banner, tombol "Verifikasi Ulang"). *(Sumber: `_MEETING-DECISIONS`; `BE-05/BE-06 §11`; `FE-05/FE-06 §11`.)*

**Pilihan jawaban**:

- [ ] **A.** Verifikasi ULANG (status kembali "recheck"); clock mulai dari tanggal verifikasi disetujui ← **REKOMENDASI KAMI** (default tertulis di PRD; titik mulai clock = USULAN)
- [ ] **B.** Aplikasi AUTO-CANCEL saat lewat 30 hari
- [ ] **C.** Verifikasi ulang, tetapi clock mulai dari titik lain (mis. tanggal telepon) — sebutkan
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi ops stakeholder.

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### F — Prioritas P2

### [OQ-NPP-14 — residual] (P2) — BAST sebagai gate wajib sebelum aktivasi SUDAH diputuskan. Residual: definisi "lengkap" — cukup nomor + tanggal BAST terisi, atau wajib dokumen BAST ter-upload/scan?

**Pilihan jawaban**:

- [ ] **A.** Cukup nomor + tanggal BAST terisi ← **REKOMENDASI KAMI** (usulan baru — belum tertulis di dokumen sumber)
- [ ] **B.** Wajib upload dokumen BAST (konsekuensi: komponen upload + kontrak baru)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi praktik serah-terima di cabang. *(Sumber: `BE-05 §11`; `FE-05 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-NPP-08] (P2) — BAST tercatat di DUA tempat (record NPP dan record BPKB) dengan field masing-masing: mana yang otoritatif, dan haruskah direkonsiliasi?

**Pilihan jawaban**:

- [ ] **A.** Record NPP otoritatif; record BPKB direkonsiliasi terhadapnya ← **REKOMENDASI KAMI** (usulan baru — belum tertulis di dokumen sumber)
- [ ] **B.** Record BPKB otoritatif
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi tim yang mengisi keduanya hari ini. *(Sumber: `BE-05 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-NPPVTL-09] (P2) — Entry NPP di web legacy hanya bisa langsung RFA (tidak ada simpan-draft yang berfungsi). Perilaku ini diterima, atau sistem baru wajib menyediakan simpan draft sungguhan?

**Pilihan jawaban**:

- [ ] **A.** Sediakan simpan draft sungguhan ← **REKOMENDASI KAMI** (usulan baru — status draft sudah ada di desain backend)
- [ ] **B.** RFA-only diterima (paritas legacy)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi kebiasaan admin cabang saat input NPP. *(Sumber: `BE-05 §11`; `FE-05 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-VTL-03] (P2) — Kedalaman approval verifikasi telepon: cukup 1 level (Kepala Cabang), atau multi-level menurut skala risiko?

**Pilihan jawaban**:

- [ ] **A.** 1 level — Kepala Cabang ← **REKOMENDASI KAMI** (default sesuai ground truth alur)
- [ ] **B.** Multi-level per skala risiko (konsekuensi: status antara "verified interim" dipakai)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi risk/ops policy owner. *(Sumber: `BE-06 §11`; `FE-06 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-VERIF-04] (P2) — Kapabilitas "verifikasi yang DITOLAK muncul kembali di antrian" di legacy rusak. Pernah berfungsi lalu rusak, atau tidak pernah ada — dan diimplementasikan di sistem baru?

**Pilihan jawaban**:

- [ ] **A.** Implementasikan di sistem baru ← **REKOMENDASI KAMI** (default tertulis)
- [ ] **B.** Tidak perlu — ditolak = keluar antrian permanen
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi alur kerja petugas verifikasi. *(Sumber: `BE-06 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CMPOFE-03] (P2) — Koreksi Open CM harus DIBLOKIR mulai titik mana di downstream? (Legacy: lini motor dicek terhadap legalisasi NPP; mobil tidak.) Dengan verifikasi telepon disisipkan sebagai step baru, di titik mana koreksi ditutup?

**Pilihan jawaban**:

- [ ] **A.** Koreksi diblokir sejak verifikasi telepon DIMULAI ← **REKOMENDASI KAMI** (usulan baru — belum tertulis di dokumen sumber)
- [ ] **B.** Koreksi diblokir sejak legalisasi NPP (paritas motor legacy)
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi pemilik proses NPP/Vertel + ops. *(Sumber: `BE-04 §11`; `FE-04 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CMPOFE-05] (P2) — Siapa saja (posisi/jabatan) yang berwenang MENCETAK PO? (Daftar di kode legacy hanya hint yang tidak dienforce.)

**Informasi yang diminta**: katalog posisi berwenang cetak PO.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi kebijakan cabang. *(Sumber: `FE-04 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-FE04-02] (P2) — Kami usul menyatukan 2 listing terpisah (motor/mobil) menjadi 1 layar ber-filter, dan 2 form CM menjadi 1 form ber-varian. Apakah pemisahan per lini adalah kebutuhan operasional (mis. pembagian kerja tim cabang per lini)?

**Pilihan jawaban**:

- [ ] **A.** Konsolidasi 1 layar + filter lini ← **REKOMENDASI KAMI** (usulan tertulis)
- [ ] **B.** Tetap terpisah per lini — jelaskan pembagian kerjanya
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi pembagian kerja cabang. *(Sumber: `FE-04 §11.3`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-FE04-03] (P2) — Panel keputusan komite kami pindahkan seluruhnya ke Inbox approval (tidak lagi menempel di layar upload dokumen). Apakah approver perlu melihat dokumen upload dalam konteks yang sama saat memutus?

**Pilihan jawaban**:

- [ ] **A.** Ya → inbox menyematkan tampilan dokumen read-only ← **REKOMENDASI KAMI** (opsi yang tertulis di PRD bila approver membutuhkannya)
- [ ] **B.** Tidak perlu — approver cukup ringkasan
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi alur kerja approver. *(Sumber: `FE-04 §11.3`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [GAP-FE00-04] (P2) — Akses lintas cabang pada layar list/filter: default scope = cabang session. Adakah role yang boleh melihat data cabang lain (mis. Area Head)?

**Pilihan jawaban**:

- [ ] **A.** Scope ketat cabang session untuk semua role ← **REKOMENDASI KAMI** (default tertulis)
- [ ] **B.** Role tertentu boleh lintas cabang — sebutkan role + cakupannya
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: keputusan ops + risk. *(Sumber: `FE-00 §11.1`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-BE07-04] (P2) — Satu user terikat SATU cabang, atau bisa MULTI-cabang?

**Pilihan jawaban**:

- [ ] **A.** Multi-cabang (daftar cabang per user) ← **REKOMENDASI KAMI** (default mengikuti desain yang sudah ditulis)
- [ ] **B.** Single-cabang
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi praktik penugasan pegawai lintas cabang. *(Sumber: `BE-07 §11`; `FE-07 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### F — Prioritas P3

### [OQ-MIG-04] (P3) — Window freeze sistem lama saat pengambilan snapshot final migrasi: berapa jam input cabang boleh berhenti?

**Pilihan jawaban**:

- [ ] **A.** Freeze di akhir pekan / di luar jam operasional ← **REKOMENDASI KAMI** (usulan baru — belum tertulis di dokumen sumber)
- [ ] **B.** Freeze di hari kerja dengan durasi maksimum tertentu — sebutkan
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: toleransi downtime input dari ops cabang. *(Sumber: `DATA-MIGRATION-PLAN.md §7`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CMPO-07] (P3) — Prosedur cetak PO varian "plain" masih dipakai dari layar mana pun? (Kode live memakai varian motor/mobil-staging; ada diskrepansi dokumen-vs-kode yang sudah kami catat.)

**Informasi yang diminta**: konfirmasi ops varian cetak yang benar-benar dipakai.

**Yang perlu disiapkan untuk menutup gap ini**: — *(Sumber: `BE-04 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-CMPO-12] (P3) — Bila pengiriman email PDF PO GAGAL: retry, lanjut tanpa email (advisory), atau blokir proses? (Legacy gagal diam-diam.)

**Pilihan jawaban**:

- [ ] **A.** Email non-blocking + retry otomatis ← **REKOMENDASI KAMI** (usulan baru — belum tertulis di dokumen sumber)
- [ ] **B.** Blokir sampai email terkirim
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: konfirmasi ops. *(Sumber: `BE-04 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-VERIF-06] (P3) — Koreksi dari approver puncak "selalu kembali ke maker asal": aturan disengaja (kami jadikan aturan resmi), atau efek samping kode?

**Pilihan jawaban**:

- [ ] **A.** Dijadikan aturan resmi: satu transisi koreksi → maker asal ← **REKOMENDASI KAMI** (default tertulis)
- [ ] **B.** Bukan aturan — koreksi boleh ke level mana pun
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: — *(Sumber: `BE-06 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-MASTERDATA-07] (P3) — Layar Dukcapil/Fidusia/demo `/CRUD` masih reachable dari menu live? (Menentukan data seed migrasi menu; layar demo akan dibuang.)

**Informasi yang diminta**: daftar menu yang benar-benar tampil di produksi.

**Yang perlu disiapkan untuk menutup gap ini**: screenshot/ekspor menu produksi. *(Sumber: `BE-07 §11`; `FE-07 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

### [OQ-AIS-06] (P3) — Kolom pencarian inbox approval: adakah kolom lain yang selama ini diandalkan user selain nomor transaksi + nama debitur?

**Pilihan jawaban**:

- [ ] **A.** Cukup 2 kolom itu ← **REKOMENDASI KAMI** (default tertulis)
- [ ] **B.** Ada kolom lain — sebutkan
- [ ] **X. Jawaban lain** — tulis di kolom jawaban.

**Yang perlu disiapkan untuk menutup gap ini**: — *(Sumber: `FE-03 §11`.)*

**JAWABAN TIM**: `___________________________`

**Dijawab oleh / tanggal**: `____________ / ____________`

---

## Catatan: OQ internal antar-tim penyusun (TIDAK membutuhkan jawaban tim eksternal)

Butir berikut adalah detail kontrak antar-modul yang akan kami putuskan internal (tim penyusun PRD BE/FE); dicantumkan demi kelengkapan register — **tidak perlu diisi**:

| ID | Tema | Sumber |
|---|---|---|
| OQ-CMPO-11 | Nama & kontrak event komite inbound (03→04) untuk disposisi correction/reject | `BE-03/BE-04 §11` |
| GAP-FE03-01, GAP-FE03-02 | Read-endpoint ringkasan komite + indikator risk-tier via GET | `FE-03 §11` |
| OQ-AIS-01, OQ-AIS-02, OQ-AIS-04 | Generalisasi komponen decision-entry inbox; pembagian tampilan hierarki FE-02 vs FE-03; field `Action/ActionEdit` yang tak terbaca | `FE-03 §11` |
| OQ-CASFE-01 | Otoritas synthesis layar shared upload/RFA (pembagian FE-01 vs FE-04) | `FE-01 §11` |
| OQ-CMPOFE-12, OQ-CMPOFE-13 | Paritas section Data Asset & validasi upload layar mobil (file legacy belum terbaca penuh) | `FE-04 §11` |
| GAP-FE01-01, -03, -04, -06 | Endpoint list/worklist intake, list+download dokumen, read required-reference-count, kontrak save narasi 5C | `FE-01 §11.1` |
| GAP-FE02-02, -04, -05 | Kontrak write APPI facility status; field census response worklist/checklist/payload; payload SLIK request | `FE-02 §11` |
| GAP-FE04-01, GAP-FE04-02 | Kepemilikan endpoint upload dokumen memo + submit RFA; perluasan payload finalisasi (field car-line/UMC) | `FE-04 §11.1` |
| GAP-FE05-02, GAP-FE05-03 | Field census response list/detail NPP; daftar field opsional payload entry | `FE-05 §11` |
| GAP-FE06-03, GAP-FE06-05, GAP-FE06-07 | Field census + kontrak pre-fill layar Vertel; limit upload (mengikuti OQ-CSB-02); kontrak riwayat attempt kontak | `FE-06 §11` |
| GAP-FE07-01, GAP-FE07-02, GAP-FE07-04, GAP-FE07-05, GAP-FE07-06 | Field census list master; endpoint detail change-request + maker-cancel; sumber dropdown form hierarchy; download dokumen dealer; enumerasi registry lookup | `FE-07 §11` |
| OQ-FE03-02, OQ-FE03-03 | Max-length field alasan; page size default listing | `FE-03 §11` |
| OQ-AC-08 | Recompute effective-rate yang dibuang di SP approve: no-effect disengaja atau regresi (arkeologi kode) | `BE-03 §11` |

---

## Penutup — urutan jawaban yang kami harapkan

1. **Gelombang 1 (paling ditunggu — memblokir Phase 1 / pre-phase):**
   - **OQ-EXTMASTERS-01 (sisa) + OQ-EXTMASTERS-07 + OQ-MIG-05** (DBA): dump `FC_MSTAPP_MCF` ✅ SUDAH diterima 2026-07-22 (terima kasih!) — sisa blocker keras: daftar ownership per master + klarifikasi 8 objek absen dari dump (OQ-EXTMASTERS-07) + akses profiling data prod (OQ-MIG-05).
   - **OQ-ARCH-STACK residual + GAP-FE00-01/02 + OQ-SHELL-02** (ITEC — deliverable arsitektur D-11 + auth service).
   - **OQ-MEET-06** (Bisnis — matriks produk × step): memblokir seluruh annex per-produk.
   - **OQ-MIG-01** (Bisnis + ITEC — strategi cutover): dibutuhkan sebelum Phase 3, tetapi menentukan desain migrasi sejak awal.
2. **Gelombang 2:** seluruh P1 lainnya per tim (bagian A–F di atas).
3. **Gelombang 3:** P2 sebelum modul terkait dibangun; P3 kapan saja.

**Apa yang terjadi dengan jawaban Anda**: setiap jawaban (centangan + kolom JAWABAN TIM) dicatat kembali ke
PRD melalui register resolusi per-ID (dengan tanggal + pemberi keputusan), sehingga keputusan Anda menjadi
bagian traceable dari spesifikasi — tidak ada pertanyaan yang "diselesaikan diam-diam" oleh tim teknis.
Bila sebuah rekomendasi kami dicentang tanpa perubahan, itu pun tetap dicatat sebagai keputusan ber-sign-off.

**Cara mengembalikan**: kirim kembali file ini (atau salinannya) yang sudah terisi ke tim penyusun PRD
rebuild Acquisition — boleh dicicil per bagian; jawaban P1 didahulukan.

---

*Dokumen ini dirangkum dari register Open Question di 16 PRD modul (`docs/prd/acquisition/BE-00..07`,
`FE-00..07` §11), rencana migrasi data (`docs/DATA-MIGRATION-PLAN.md §7`), register keputusan meeting
(`_MEETING-DECISIONS-2026-07.md`), ground truth alur (`_ACQUISITION-GROUND-TRUTH.md`), dan register gap
ekstraksi data (`30-data-model/gap-entities.md §4`) — status per 14 Juli 2026.*







