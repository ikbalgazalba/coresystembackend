-- ============================================================
-- V20260728000001__master_data_schema.sql
-- Module 07 (Master Data) — 26 target tables per DB-CONVENTIONS (ADR-14)
-- Vault: .mega-sdd/vaults/acquisition-master-data/ 03-data-model.md
-- ============================================================
-- Conventions (DB-CONVENTIONS.md):
--   PK: id BIGINT GENERATED ALWAYS AS IDENTITY
--   Audit: created_at/created_by/updated_at/updated_by (all mst_/cfg_/map_)
--   log_: only created_at/created_by (INSERT-only)
--   version INTEGER on concurrent-edit tables (extends VersionedEntity)
--   snake_case English singular after prefix
--   CHECK constraints for enums
--   Declared FKs
-- ============================================================

-- ============================================================
-- 1. USER & RBAC (Tier A)
-- ============================================================

CREATE TABLE mst_user (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_nik        VARCHAR(20) NOT NULL,
    role                VARCHAR(20) NOT NULL,
    company_id          VARCHAR(10),
    is_active           BOOLEAN NOT NULL DEFAULT true,
    deactivation_reason VARCHAR(20),
    activation_date     DATE,
    deactivation_date   DATE,
    -- Audit (VersionedEntity)
    version             INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          VARCHAR(50) NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by          VARCHAR(50) NOT NULL,
    -- Constraints
    CONSTRAINT ux_mst_user_employee_nik UNIQUE (employee_nik),
    CONSTRAINT ck_mst_user_role CHECK (role IN ('CMO', 'MARKETING_HEAD', 'CREDIT_ANALYST', 'KEPALA_CABANG', 'CREDIT_ADMIN')),
    CONSTRAINT ck_mst_user_deactivation_reason CHECK (deactivation_reason IS NULL OR deactivation_reason IN ('manual', 'hr_resigned'))
);
COMMENT ON TABLE mst_user IS 'APP_USER provisioning — role enum D-10 [LOCKED], no password (BR-SHELL-1 LDAP), no super-user (D-09)';

CREATE TABLE mst_user_branch_scope (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    branch_id   VARCHAR(10) NOT NULL,
    version     INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  VARCHAR(50) NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by  VARCHAR(50) NOT NULL,
    CONSTRAINT fk_mst_user_branch_scope_user FOREIGN KEY (user_id) REFERENCES mst_user(id)
);
COMMENT ON TABLE mst_user_branch_scope IS 'Multi-branch scope (OQ-BE07-04 resolved)';

-- ============================================================
-- 2. MENU (Tier A — cfg_)
-- ============================================================

CREATE TABLE cfg_menu (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    parent_id           BIGINT,
    module              VARCHAR(50),
    name                VARCHAR(100) NOT NULL,
    route               VARCHAR(255),
    trans_type_id_prefix VARCHAR(10),  -- [LOCKED] verbatim BR-PRODASSET-14
    display_order       INTEGER NOT NULL DEFAULT 0,
    is_active           BOOLEAN NOT NULL DEFAULT true,
    version             INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          VARCHAR(50) NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by          VARCHAR(50) NOT NULL,
    CONSTRAINT fk_cfg_menu_parent FOREIGN KEY (parent_id) REFERENCES cfg_menu(id)
);
COMMENT ON TABLE cfg_menu IS 'Menu tree — trans_type_id_prefix [LOCKED] verbatim (BR-PRODASSET-14); role-driven';

CREATE TABLE cfg_menu_role_grant (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    role        VARCHAR(20) NOT NULL,
    menu_id     BIGINT NOT NULL,
    is_view_only BOOLEAN NOT NULL DEFAULT false,
    is_active   BOOLEAN NOT NULL DEFAULT true,
    version     INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  VARCHAR(50) NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by  VARCHAR(50) NOT NULL,
    CONSTRAINT ux_cfg_menu_role_grant_role_menu_id UNIQUE (role, menu_id),
    CONSTRAINT ck_cfg_menu_role_grant_role CHECK (role IN ('CMO', 'MARKETING_HEAD', 'CREDIT_ANALYST', 'KEPALA_CABANG', 'CREDIT_ADMIN')),
    CONSTRAINT fk_cfg_menu_role_grant_menu FOREIGN KEY (menu_id) REFERENCES cfg_menu(id)
);

CREATE TABLE cfg_menu_user_grant_special (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    menu_id         BIGINT NOT NULL,
    is_view_only    BOOLEAN NOT NULL DEFAULT false,
    granted_reason  TEXT NOT NULL,  -- wajib (OQ-BE07-05 governance)
    is_active       BOOLEAN NOT NULL DEFAULT true,
    version         INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(50) NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by      VARCHAR(50) NOT NULL,
    CONSTRAINT fk_cfg_menu_user_grant_special_user FOREIGN KEY (user_id) REFERENCES mst_user(id),
    CONSTRAINT fk_cfg_menu_user_grant_special_menu FOREIGN KEY (menu_id) REFERENCES cfg_menu(id)
);

-- ============================================================
-- 3. EMPLOYEE MIRROR (Tier B — read-only, no version)
-- ============================================================

CREATE TABLE mst_employee_mirror (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nik             VARCHAR(20) NOT NULL,  -- business key
    name            VARCHAR(255),
    branch_id       VARCHAR(10),   -- legacy KdCabang
    position_id     VARCHAR(10),   -- legacy KdJabat
    national_id     VARCHAR(16),   -- [LOCKED] NIK NoKtp regulatory
    is_resigned     BOOLEAN NOT NULL DEFAULT false,  -- legacy Fkeluar (fix Edge Case 12)
    employee_status VARCHAR(20),   -- legacy Stpegawai
    join_date       DATE,          -- legacy Tglmasuk
    exit_date       DATE,          -- legacy Tglkeluar
    -- Audit (no version — read-only Tier B)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(50) NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by      VARCHAR(50) NOT NULL,
    CONSTRAINT ux_mst_employee_mirror_nik UNIQUE (nik)
);
COMMENT ON TABLE mst_employee_mirror IS 'HR sync mirror (Tier B read-only, BR-EMPLOYEE-1); is_resigned WAJIB eksplisit (fix EC12)';

-- ============================================================
-- 4. DEALER FAMILY (Tier A)
-- ============================================================

CREATE TABLE mst_dealer (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dealer_code             VARCHAR(10) NOT NULL,  -- business key
    dealer_name             VARCHAR(255) NOT NULL,
    is_authorized_dealer    BOOLEAN NOT NULL DEFAULT false,
    is_selling_new_product_only BOOLEAN NOT NULL DEFAULT true,
    is_used_car             BOOLEAN NOT NULL DEFAULT false,
    parent_dealer_code      VARCHAR(10),  -- FK eksplisit (fix EC7 — NOT join via notes)
    group_code              VARCHAR(10),
    main_dealer_code        VARCHAR(10),
    is_sub_dealer_enabled   BOOLEAN NOT NULL DEFAULT false,  -- fix EC6 (NOT name-literal)
    contact_person          VARCHAR(255),
    contact_job_title_id    VARCHAR(10),
    ktp_no                  VARCHAR(16),  -- [LOCKED] regulatory
    ktp_name                VARCHAR(255),
    npwp_no                 VARCHAR(20),  -- [LOCKED] regulatory
    address                 TEXT,
    location_id             VARCHAR(10),
    mou_no                  VARCHAR(50),
    mou_date                DATE,
    rate_refund             NUMERIC(9,6),
    status                  VARCHAR(10) NOT NULL DEFAULT 'active',
    activation_date         DATE,
    deactivation_date       DATE,
    notes                   TEXT,  -- free-text, NOT join key (fix EC7)
    version                 INTEGER NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by              VARCHAR(50) NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by              VARCHAR(50) NOT NULL,
    CONSTRAINT ux_mst_dealer_dealer_code UNIQUE (dealer_code),
    CONSTRAINT ck_mst_dealer_status CHECK (status IN ('active', 'inactive'))
);
COMMENT ON TABLE mst_dealer IS 'Dealer master (DDL 51 cols → normalized); KTP/NPWP [LOCKED] zero-diff; is_sub_dealer_enabled flag (fix EC6); FK parent eksplisit (fix EC7)';

CREATE TABLE mst_dealer_document (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dealer_code VARCHAR(10) NOT NULL,
    doc_type    VARCHAR(30) NOT NULL,
    file_ref    VARCHAR(500),  -- object storage key (NOT FTP path)
    uploaded_by VARCHAR(50),
    uploaded_at TIMESTAMPTZ,
    version     INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  VARCHAR(50) NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by  VARCHAR(50) NOT NULL,
    CONSTRAINT ux_mst_dealer_document_dealer_code_doc_type UNIQUE (dealer_code, doc_type),
    CONSTRAINT ck_mst_dealer_document_doc_type CHECK (doc_type IN ('SIUP', 'TDP_NIB', 'NPWP', 'KTP', 'MP_MASTER_DEALER', 'SPT_ACCOUNT_BOOK')),
    CONSTRAINT fk_mst_dealer_document_dealer FOREIGN KEY (dealer_code) REFERENCES mst_dealer(dealer_code)
);

CREATE TABLE mst_dealer_personnel (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    personnel_id      VARCHAR(20) NOT NULL,  -- business key
    dealer_code       VARCHAR(10) NOT NULL,
    name              VARCHAR(255),
    birth_place       VARCHAR(100),
    birth_date        DATE,
    job_title_id      VARCHAR(10),
    address           TEXT,
    location_id       VARCHAR(10),
    phone             VARCHAR(50),
    email             VARCHAR(255),
    status            VARCHAR(10) NOT NULL DEFAULT 'A',  -- BR-DLRPTN-1 eligibility
    bank_reference_id VARCHAR(20),
    version           INTEGER NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        VARCHAR(50) NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by        VARCHAR(50) NOT NULL,
    CONSTRAINT ux_mst_dealer_personnel_personnel_id UNIQUE (personnel_id),
    CONSTRAINT fk_mst_dealer_personnel_dealer FOREIGN KEY (dealer_code) REFERENCES mst_dealer(dealer_code)
);

CREATE TABLE mst_dealer_job_title (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_title_id        VARCHAR(10) NOT NULL,  -- business key
    description         VARCHAR(255),
    dealer_payment_code VARCHAR(20),
    is_active           BOOLEAN NOT NULL DEFAULT true,
    version             INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          VARCHAR(50) NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by          VARCHAR(50) NOT NULL,
    CONSTRAINT ux_mst_dealer_job_title_job_title_id UNIQUE (job_title_id)
);

CREATE TABLE mst_dealer_bank_reference (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dealer_code         VARCHAR(10) NOT NULL,
    bank_reference_id   VARCHAR(20) NOT NULL,
    account_type        VARCHAR(10),
    account_description VARCHAR(255),
    bank_id             VARCHAR(10),
    account_number      VARCHAR(50),  -- [LOCKED] payout zero-diff
    account_name        VARCHAR(255), -- [LOCKED] payout zero-diff
    bank_charges_flag   BOOLEAN NOT NULL DEFAULT false,
    status              VARCHAR(10) NOT NULL DEFAULT 'A',
    activation_date     DATE,
    deactivation_date   DATE,
    version             INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          VARCHAR(50) NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by          VARCHAR(50) NOT NULL,
    CONSTRAINT ux_mst_dealer_bank_reference_dealer_code_bank_reference_id UNIQUE (dealer_code, bank_reference_id),
    CONSTRAINT fk_mst_dealer_bank_reference_dealer FOREIGN KEY (dealer_code) REFERENCES mst_dealer(dealer_code)
);
COMMENT ON TABLE mst_dealer_bank_reference IS 'Rekening dealer; account_number/name [LOCKED] payout zero-diff; maker-checker WAJIB (BR-BE07-05)';

CREATE TABLE mst_dealer_branch_access (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dealer_code VARCHAR(10) NOT NULL,
    branch_id   VARCHAR(10) NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT true,
    version     INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  VARCHAR(50) NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by  VARCHAR(50) NOT NULL,
    CONSTRAINT ux_mst_dealer_branch_access_dealer_code_branch_id UNIQUE (dealer_code, branch_id),
    CONSTRAINT fk_mst_dealer_branch_access_dealer FOREIGN KEY (dealer_code) REFERENCES mst_dealer(dealer_code)
);

-- ============================================================
-- 5. TRANSACTION-TYPE HIERARCHY (Tier A — cfg_)
-- ============================================================

CREATE TABLE cfg_transaction_code (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    branch_id       VARCHAR(10) NOT NULL,
    transaction_code VARCHAR(20) NOT NULL,
    form_requester  VARCHAR(100),
    form_approval   VARCHAR(100),
    version         INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(50) NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by      VARCHAR(50) NOT NULL,
    CONSTRAINT ux_cfg_transaction_code_branch_id_transaction_code UNIQUE (branch_id, transaction_code)
);

CREATE TABLE cfg_transaction_type (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_type_code VARCHAR(20) NOT NULL,  -- [LOCKED] external-FK char-for-char BR-PRODASSET-7
    description         VARCHAR(255),
    mapping             VARCHAR(20) NOT NULL,  -- FK logis ke TransactionCode — disimpan eksplisit (NOT substring derive)
    is_active           BOOLEAN NOT NULL DEFAULT true,
    version             INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          VARCHAR(50) NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by          VARCHAR(50) NOT NULL,
    CONSTRAINT ux_cfg_transaction_type_transaction_type_code UNIQUE (transaction_type_code)
);
COMMENT ON TABLE cfg_transaction_type IS 'transaction_type_code [LOCKED] external-FK; PATCH hanya is_active (BR-BE07-18); mapping disimpan eksplisit';

CREATE TABLE cfg_hierarchy_matrix (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_type_code VARCHAR(20) NOT NULL,
    level               INTEGER NOT NULL,
    pic_nik             VARCHAR(20) NOT NULL,
    pic_name            VARCHAR(255),
    next_pic_nik        VARCHAR(20),  -- nullable; WAJIB bila is_approver=false
    next_pic_name       VARCHAR(255),
    is_approver         BOOLEAN NOT NULL DEFAULT false,
    status_approver     VARCHAR(50),  -- vestigial
    escalation_days     INTEGER NOT NULL DEFAULT 1,
    is_active           BOOLEAN NOT NULL DEFAULT true,
    version             INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          VARCHAR(50) NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by          VARCHAR(50) NOT NULL,
    CONSTRAINT ux_cfg_hierarchy_matrix_type_level_pic UNIQUE (transaction_type_code, level, pic_nik),
    CONSTRAINT ck_cfg_hierarchy_matrix_level CHECK (level >= 1)
);
COMMENT ON TABLE cfg_hierarchy_matrix IS 'SINGLE SOURCE — admin write (07) + 03 walk tabel sama (OQ-MASTERDATA-03 ✅); server-side validation BR-BE07-15..17';

-- ============================================================
-- 6. MASTER OPERASIONAL (Tier A)
-- ============================================================

CREATE TABLE mst_approval_reason (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reason_id   VARCHAR(10) NOT NULL,
    description VARCHAR(255),
    type        VARCHAR(5),  -- '1'|'2'|'3'|'9' — expose ALL (OQ-DLRPTN-04 resolved)
    is_active   BOOLEAN NOT NULL DEFAULT true,
    version     INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  VARCHAR(50) NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by  VARCHAR(50) NOT NULL,
    CONSTRAINT ux_mst_approval_reason_reason_id UNIQUE (reason_id)
);

CREATE TABLE mst_credit_source (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    credit_source_id VARCHAR(10) NOT NULL,
    description VARCHAR(255),
    is_active   BOOLEAN NOT NULL DEFAULT true,
    version     INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  VARCHAR(50) NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by  VARCHAR(50) NOT NULL,
    CONSTRAINT ux_mst_credit_source_credit_source_id UNIQUE (credit_source_id)
);
COMMENT ON TABLE mst_credit_source IS 'LOKAL acquisition DB (BR-CREDITSRC-1, OQ-DLRPTN-02 resolved)';

CREATE TABLE mst_branch_credit_source (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    branch_id           VARCHAR(10) NOT NULL,
    credit_source_id    VARCHAR(10) NOT NULL,
    photo_required      BOOLEAN NOT NULL DEFAULT false,
    print_survey_report BOOLEAN NOT NULL DEFAULT false,
    is_active           BOOLEAN NOT NULL DEFAULT true,
    version             INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          VARCHAR(50) NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by          VARCHAR(50) NOT NULL,
    CONSTRAINT ux_mst_branch_credit_source_branch_id_credit_source_id UNIQUE (branch_id, credit_source_id)
);

CREATE TABLE mst_blacklist_override (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    national_id     VARCHAR(16) NOT NULL,  -- [LOCKED] NIK screening key
    reason_code     VARCHAR(10),
    justification   TEXT NOT NULL,  -- wajib (BR-BE07-06)
    valid_from      DATE NOT NULL,
    valid_until     DATE,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    version         INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(50) NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by      VARCHAR(50) NOT NULL
);
COMMENT ON TABLE mst_blacklist_override IS 'AML whitelist/override; national_id [LOCKED]; maker-checker WAJIB + append-only audit (BR-BE07-05/06)';

CREATE TABLE mst_public_holiday (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    holiday_name    VARCHAR(50) NOT NULL,  -- NOT NULL (reject NULL legacy)
    holiday_date    DATE NOT NULL,         -- NOT NULL (reject NULL legacy)
    version         INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(50) NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by      VARCHAR(50) NOT NULL,
    CONSTRAINT ux_mst_public_holiday_holiday_date UNIQUE (holiday_date)
);
COMMENT ON TABLE mst_public_holiday IS 'FC_ACQ_MCF otoritatif (OQ-EXTMASTERS-08 resolved); deactivate-only NO delete (OQ-BE07-06 resolved)';

CREATE TABLE mst_general_parameter (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    parameter       VARCHAR(100) NOT NULL,
    param_value     VARCHAR(500),  -- renamed from 'value' (H2 reserved keyword)
    unit            VARCHAR(50),
    description     VARCHAR(255),
    is_visible      BOOLEAN NOT NULL DEFAULT true,
    is_updateable   BOOLEAN NOT NULL DEFAULT true,  -- false → 409 (BR-BE07-23)
    version         INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(50) NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by      VARCHAR(50) NOT NULL,
    CONSTRAINT ux_mst_general_parameter_parameter UNIQUE (parameter)
);

CREATE TABLE mst_promotion_line_text (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    text            TEXT,
    display_color   VARCHAR(20),
    is_active       BOOLEAN NOT NULL DEFAULT true,
    version         INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(50) NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by      VARCHAR(50) NOT NULL
);

-- ============================================================
-- 7. GL MAPPING (Tier A — map_)
-- ============================================================

CREATE TABLE map_transaction_type_gl (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trx_id          VARCHAR(20) NOT NULL,   -- FK logis ke cfg_transaction_type
    class_id        VARCHAR(20) NOT NULL,
    gl_account_no   VARCHAR(50),  -- [LOCKED] CoA zero-diff
    is_active       BOOLEAN NOT NULL DEFAULT true,
    version         INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(50) NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by      VARCHAR(50) NOT NULL,
    CONSTRAINT ux_map_transaction_type_gl_trx_id_class_id UNIQUE (trx_id, class_id)
);
COMMENT ON TABLE map_transaction_type_gl IS '[LOCKED] CoA zero-diff; maker-checker WAJIB; no delete (BR-BE07-24); data milik finance';

-- ============================================================
-- 8. NUMBERING CONFIG (Tier A — cfg_)
-- ============================================================

CREATE TABLE cfg_number_format (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code_type       VARCHAR(50) NOT NULL,  -- target: CREDIT_ID [LOCKED]
    company_id      VARCHAR(5),
    branch_id       VARCHAR(10),
    format_template VARCHAR(100) NOT NULL,
    reset_period    VARCHAR(10) NOT NULL DEFAULT 'MONTHLY',
    sequence_name   VARCHAR(100) NOT NULL,  -- DB sequence per scope (NOT manual increment)
    effective_from  DATE NOT NULL,
    effective_to    DATE,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    version         INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(50) NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by      VARCHAR(50) NOT NULL,
    CONSTRAINT ck_cfg_number_format_reset_period CHECK (reset_period IN ('NONE', 'MONTHLY', 'YEARLY'))
);
COMMENT ON TABLE cfg_number_format IS 'Numbering config (CREDIT_ID [LOCKED]); counter→DB sequence (fix DB-CONVENTIONS §6.5); consumer BE-01 mint credit_id';

-- ============================================================
-- 9. AUDIT LOG (log_ — INSERT-only, no version, no updated_at)
-- ============================================================

CREATE TABLE log_master_change_request (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    resource        VARCHAR(50) NOT NULL,
    resource_id     VARCHAR(50),
    action          VARCHAR(20) NOT NULL,
    payload         TEXT,  -- JSON proposed mutation
    status          VARCHAR(20) NOT NULL DEFAULT 'pending_approval',
    maker_nik       VARCHAR(20) NOT NULL,
    submitted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    checker_nik     VARCHAR(20),
    checked_at      TIMESTAMPTZ,
    checker_note    TEXT,
    reject_reason   TEXT,
    -- log_ audit: only created_at/created_by (no updated_at — INSERT-only)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(50) NOT NULL,
    CONSTRAINT ck_log_master_change_request_action CHECK (action IN ('create', 'update', 'deactivate', 'reactivate', 'delete')),
    CONSTRAINT ck_log_master_change_request_status CHECK (status IN ('pending_approval', 'applied', 'rejected', 'cancelled'))
);

CREATE TABLE log_master_audit (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entity_type         VARCHAR(50) NOT NULL,
    entity_id           VARCHAR(50),
    before_json         TEXT,
    after_json          TEXT,
    actor_nik           VARCHAR(20) NOT NULL,
    action_performed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          VARCHAR(50) NOT NULL
);
COMMENT ON TABLE log_master_audit IS 'Before/after JSON snapshot per Tier A mutation (BR-BE07-04); append-only INSERT-only';

-- ============================================================
-- INDEXES (performance — per DB-CONVENTIONS ix_{table}_{cols})
-- ============================================================

CREATE INDEX ix_mst_user_is_active ON mst_user (is_active);
CREATE INDEX ix_mst_employee_mirror_is_resigned ON mst_employee_mirror (is_resigned);
CREATE INDEX ix_mst_dealer_status ON mst_dealer (status);
CREATE INDEX ix_mst_dealer_branch_access_branch_id ON mst_dealer_branch_access (branch_id);
CREATE INDEX ix_cfg_hierarchy_matrix_transaction_type_code ON cfg_hierarchy_matrix (transaction_type_code);
CREATE INDEX ix_log_master_change_request_status ON log_master_change_request (status);
CREATE INDEX ix_log_master_change_request_resource ON log_master_change_request (resource);

-- ============================================================
-- END V20260728000001 — 26 tables, 7 indexes
-- ============================================================
