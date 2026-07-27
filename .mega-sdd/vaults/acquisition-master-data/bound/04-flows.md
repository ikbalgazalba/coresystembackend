# 04 — Flows

> **TL;DR**: 38 endpoint (E1-E38) dikelompokkan per sub-area + maker-checker envelope generik (E37) + 16 Acceptance Criteria (AC-1..AC-16) Given/When/Then dari BE-07 §9. Urutan build USULAN (BE-07 §10): (1) EMPLOYEE_MIRROR sync + E8 → (2) APP_USER/role/menu E1-E11 → (3) Dealer family E12-E21 → (4) TransType hierarchy E22-E28 → (5) master operasional + lookup + numbering E29-E38.

## F-U-001 — User: Provision & lifecycle APP_USER (E1-E7)

**Actor**: Master Data Admin (Maker = Credit Admin) + HR sync (auto-deactivate). **Source**: BE-07 §4.1, §5 E2/E6, §7.3, §9 AC-1/2/3/4.

- E1 GET `/users` (list/search, filter role/branch/status) · E2 POST `/users` (provision: NIK wajib ada & tidak resigned di mirror + role enum D-10 + scope; tolak `SUPER_USER` → `422 UNKNOWN_ROLE`) · E3 GET `/users/{id}` (detail + grants efektif) · E4 PATCH `/users/{id}` (ubah role/scope, bukan identitas) · E5 POST `/users/{id}/deactivate`/`/reactivate` (reaktivasi ditolak `409` bila mirror resigned) · E6 GET `/users/{id}/menus` (menu tree efektif: role grants + special − inactive; `trans_type_id_prefix` TIDAK diekspos di E6, hanya E9 admin) · E7 GET `/roles` (catalog tertutup D-10 statis)
- **DoD (F-U-001):** `422 EMPLOYEE_NOT_FOUND`/`EMPLOYEE_RESIGNED` eksplisit (bukan sukses-kosong — fix EC1/EC12); `409 USER_ALREADY_EXISTS` (unique `employee_nik`); tanpa field password (BR-SHELL-1); audit create tercatat; sync HR resigned → auto-deactivate `inactive(hr_resigned)` + menu efektif kosong (BR-BE07-27); `SUPER_USER` ditolak (D-09)

## F-U-002 — User: Menu & grants (E9-E11)

**Actor**: Master Data Admin (maker) + checker untuk `trans_type_id_prefix`. **Source**: BE-07 §4.1, §6 BR-BE07-14, §9 AC-4/5.

- E9 GET/POST/PATCH `/menus`, `/menus/{id}` (CRUD tree; perubahan `trans_type_id_prefix` → maker-checker BR-BE07-14; deactivate-only no DELETE) · E10 GET/PUT `/roles/{role}/menu-grants` (baca/ganti set grant per role) · E11 GET/PUT `/users/{id}/menu-grants-special` (grant khusus per user bila dipertahankan OQ-BE07-05; wajib `granted_reason`)
- **DoD (F-U-002):** `trans_type_id_prefix` `[LOCKED]` (BR-PRODASSET-14) — mutasi → `pending_approval` + daftar transaction-type ter-impact; tanpa approve checker, routing tidak berubah; menu inactive + grant inactive tidak dikembalikan di E6; `cfg_menu_role_grant` re-key position→role D-10 (bukan copy 1:1)

## F-U-003 — User: HR mirror picker (E8)

**Actor**: Master Data Admin / Hierarchy Admin. **Source**: BE-07 §4.1, §8, §9 AC-3.

- E8 GET `/employees` (search HR mirror: NIK, nama, branch, position, **status resign eksplisit**). Read-only Tier B.
- **DoD (F-U-003):** `is_resigned` (`Fkeluar`) WAJIB diekspos eksplisit (fix Edge Case 12 — legacy `vw_HREmployeeData` comment-out filter `IsActive`); "resigned", "not found", "source error" = tiga sinyal berbeda (BR-BE07-22); TIDAK ada `POST /employees` (HR system-of-record BR-EMPLOYEE-1)

## F-U-004 — User: Dealer family CRUD (E12-E21)

**Actor**: Master Data Admin (maker) + checker untuk bank-reference & field sensitif. **Source**: BE-07 §4.2, §5 E13/E18, §6 BR-BE07-05/07/08/09/10, §9 AC-6/7/8/9.

- E12 GET `/dealers` (list/search; filter `branch_id` hanya dealer ber-akses aktif BR-BE07-07, `is_used_car`, `status`) · E13 POST `/dealers` (create; KTP/NPWP tervalidasi `[LOCKED]`) · E14 GET/PATCH `/dealers/{code}` (update field identitas legal → maker-checker) · E15 POST `/dealers/{code}/deactivate`/`/reactivate` · E16 GET/POST/PATCH `/dealers/{code}/personnel` · E17 GET/POST/PATCH `/dealer-job-titles` · E18 GET/POST/PATCH `/dealers/{code}/bank-references` (**SEMUA write maker-checker WAJIB** payout) · E19 GET/PUT `/dealers/{code}/branch-access` (replace-set atomik) · E20 GET/POST `/dealers/{code}/documents` (object storage) · E21 GET `/dealers/{code}/payment-eligible-contacts?job_title_id=` (read; join job-title→personnel→bank-ref aktif simultan BR-BE07-10)
- **DoD (F-U-004):** `422 PARENT_DEALER_NOT_FOUND` (FK eksplisit — fix EC7 notes-as-join-key); `is_sub_dealer_enabled` flag eksplisit (fix EC6 name-literal `'%PT Lucas Digital Indonesia%'`); E18 → `202 pending_approval` (BUKAN `201`); self-approve → `403 SELF_APPROVAL_BLOCKED`; `account_number`/`account_name` `[LOCKED]` payout zero-diff; E21 eligible = semua status aktif simultan (BR-DLRPTN-1)

## F-U-005 — User: Transaction-Type Hierarchy config (E22-E28)

**Actor**: Hierarchy Admin (maker) + checker. **Source**: BE-07 §4.3, §5 E27, §6 BR-BE07-15/16/17/18/19, §9 AC-10/11.

- E22 GET `/transaction-codes?branch_id=` · E23 PUT `/transaction-codes/{branchId}/{code}` (upsert BR-BE07-19) · E24 GET `/transaction-types?transaction_code=` · E25 POST/PATCH `/transaction-types`, `/transaction-types/{code}` (PATCH hanya `is_active` BR-BE07-18; `mapping` wajib merujuk TRANSACTION_CODE yang ada) · E26 GET `/approval-hierarchies?transaction_type_code=` · E27 POST/PATCH `/approval-hierarchies`, `.../{id}` (validasi server-side WAJIB BR-BE07-15..17) · E28 GET `/approval-hierarchies/pic-candidates?search=&branch_id=` (PIC picker difilter job-title codes)
- **DoD (F-U-005):** validasi **server-side** (fix OQ-MASTERDATA-02 — legacy TERNYATA validate V1-V3/V7-V8 di SP, celah V4+V6): `422 HIERARCHY_RULE_VIOLATION` Level-1 `is_approver=true` (BR-BE07-15); `422 NEXT_PIC_REQUIRED`/`NEXT_PIC_MUST_BE_EMPTY` (BR-BE07-16); `422 PIC_NOT_FOUND`/`PIC_RESIGNED` (BR-BE07-17 — fix V4 NIK tanpa guard); `transaction_type_code` `[LOCKED]` external-FK char-for-char (BR-PRODASSET-7); `mapping` disimpan eksplisit (bukan derive `substring`); `cfg_hierarchy_matrix` = single source (admin write langsung ke tabel walk 03, OQ-MASTERDATA-03 ✅); cleansing import wajib V4+V6

## F-U-006 — User: Master operasional + lookup + numbering (E29-E38)

**Actor**: Master Data Admin (maker) + checker untuk resource WAJIB maker-checker. **Source**: BE-07 §4.4, §5 E30/E37, §6 BR-BE07-05/06/20/21/22/23/24, §9 AC-12/13/14/15/16.

- E29 GET/POST/PATCH `/approval-reasons` (type `'1'|'2'|'3'|'9'` OQ-DLRPTN-04) · E30 GET `/lookups/{lookup_key}` (Tier C generic; `applicant_type=`/`parent_id=`/`branch_id=`/`include_inactive=false`) · E31 GET/POST/PATCH `/credit-sources` + `/branches/{id}/credit-sources` · E32 GET/POST/PATCH `/blacklist-overrides` (**maker-checker WAJIB** AML) · E33 GET/POST/PATCH/DELETE `/public-holidays` (satu-satunya ber-DELETE OQ-BE07-06) · E34 GET/PATCH `/general-parameters` (`is_updateable=false`→`409`; no create/delete) · E35 GET/POST/PATCH `/promotion-line-texts` · E36 GET/PATCH `/gl-transaction-type-links` (read + audited update; **maker-checker WAJIB** CoA; no delete) · E37 GET/POST `/master-change-requests`, `.../{id}/approve`, `.../{id}/reject` (envelope generik) · E38 GET/POST/PATCH `/number-formats` (admin `cfg_number_format`; `CREDIT_ID` code_type change → maker-checker)
- **DoD (F-U-006):** E30 filter `applicant_type` = **set-membership eksplisit** (fix EC5 BR-BE07-12 — bukan substring match); E30 kegagalan baca = `503 LOOKUP_SOURCE_UNAVAILABLE` (BR-BE07-22 — fix EC1/EC12/fuel silent-success); E30 default active-only (BR-BE07-21); E32 `justification` wajib + append-only audit (BR-BE07-06); E34 `is_updateable=false` → `409` (BR-BE07-23); E36 CoA `[LOCKED]` zero-diff; E38 `CREDIT_ID` format `branch(5)+YY+MM+SEQ(5)` (OQ-GT-02 ✅, consumer 01); `DELETE` pada master konfigurasi → `405`/`404` (BR-BE07-03, kecuali PUBLIC_HOLIDAY OQ-BE07-06)

## F-U-007 — User: Maker-checker envelope (E37 — generik untuk resource BR-BE07-05)

**Actor chain**: Maker (Credit Admin / Hierarchy Admin) → Checker (Kepala Cabang scope cabang / HO role OQ-BE07-01). **Source**: BE-07 §5 E37, §6 BR-BE07-05, §7.2, §9 AC-8.

- Maker submit write (E18/E25/E27/E32/E34/E36/E38-`CREDIT_ID`/E9-prefix/E14-sensitif) → `pending_approval` (payload valid SAAT submit, bukan saat approve saja)
- Checker approve (≠ maker; `403 SELF_APPROVAL_BLOCKED`) → `applied` (terapkan mutasi + audit) · Checker reject → `rejected` (`reject_reason` wajib; nol perubahan) · Maker cancel → `cancelled` (hanya maker pembuat)
- Terminal `applied`/`rejected`/`cancelled` immutable; audit permanen
- **Operator surface**: (1) worklist/inbox `GET /master-change-requests?status=pending_approval&resource=`; (2) decision affordance approve/reject; (3) human-readable state labels `pending_approval`/`applied`/`rejected`/`cancelled`; (4) audit timeline `log_master_change_request` + `log_master_audit`. Approve idempotent by `Idempotency-Key`.
- **DoD (F-U-007):** maker-checker = kontrol BARU (legacy TIDAK punya — jangan klaim paritas); resource WAJIB maker-checker BR-BE07-05: DEALER_BANK_REFERENCE (payout), BLACKLIST_OVERRIDE (AML), GL_TRANSACTION_TYPE_LINK (CoA), GENERAL_PARAMETER, MENU.trans_type_id_prefix, TRANSACTION_TYPE/APPROVAL_HIERARCHY_LEVEL, dealer legal identity, NUMBER_FORMAT `CREDIT_ID`; checker ≠ maker enforced app-layer (D-01 S11)

## Sources

- `docs/prd/acquisition/BE-07-master-data-menus.md §4` (API E1-E38), `§5` (kontrak request/response), `§6` (BR), `§7` (state machine), `§9` (AC-1..16), `§10` (urutan build)
- `.mega-sdd/vaults/acquisition/04-flows.md` (parent — F-U-006 umbrella master-data)

## Open Questions

- **OQ-BE07-01** [P1] — scope final maker-checker + siapa checker per resource
- **OQ-DLRPTN-04** [P2] — makna type approval-reason `'9'`
- **OQ-BE07-06** [P3] — PUBLIC_HOLIDAY hard-delete vs deactivate-only
- **OQ-MASTERDATA-02** [P1] — celah V4/V6 cleansing import
- Lengkap di `00-index.md` roll-up
