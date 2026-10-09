-- =====================================================================
-- Shared kernel (PRD §13): penomoran, audit trail, approval, aktivitas,
-- lampiran, notifikasi, tanda tangan elektronik, kunci periode.
-- =====================================================================

-- Penomoran dokumen: [KODE]/[PLANT]/[YYMM]/[00001]. Nomor tidak dipakai ulang.
CREATE TABLE core.doc_sequence (
    doc_code    VARCHAR(16) NOT NULL,
    plant_code  VARCHAR(8)  NOT NULL,
    period      VARCHAR(4)  NOT NULL,
    last_no     INTEGER     NOT NULL DEFAULT 0,
    PRIMARY KEY (doc_code, plant_code, period)
);

-- Audit trail global (SYS-14). Append-only: UPDATE dan DELETE ditolak oleh trigger.
CREATE TABLE core.audit_log (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    table_name  VARCHAR(64)  NOT NULL,
    record_id   VARCHAR(64)  NOT NULL,
    action      VARCHAR(8)   NOT NULL,           -- INSERT / UPDATE / DELETE
    field       VARCHAR(64),
    old_value   TEXT,
    new_value   TEXT,
    reason      TEXT,
    user_id     BIGINT,
    username    VARCHAR(64),
    ts          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_record ON core.audit_log (table_name, record_id, ts DESC);
CREATE INDEX ix_audit_ts ON core.audit_log (ts DESC);
CREATE INDEX ix_audit_user ON core.audit_log (user_id, ts DESC);

CREATE FUNCTION core.forbid_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Tabel % bersifat append-only (ALCOA+): % tidak diizinkan', TG_TABLE_NAME, TG_OP;
END $$;

CREATE TRIGGER trg_audit_log_immutable BEFORE UPDATE OR DELETE ON core.audit_log
    FOR EACH ROW EXECUTE FUNCTION core.forbid_change();

-- Tugas approval per level (APPROVAL_LOG di ERD). Polimorfik ke dokumen mana pun.
CREATE TABLE core.approval_task (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    doc_type          VARCHAR(16)  NOT NULL,
    doc_id            BIGINT       NOT NULL,
    doc_no            VARCHAR(40)  NOT NULL,
    doc_summary       VARCHAR(255),
    doc_amount        NUMERIC(19,2),
    app_code          VARCHAR(8)   NOT NULL,
    plant_id          BIGINT       NOT NULL,
    level             SMALLINT     NOT NULL,
    status            VARCHAR(12)  NOT NULL,     -- WAITING / PENDING / APPROVED / REJECTED / CANCELLED
    assignee_user_id  BIGINT,
    assignee_role     VARCHAR(32),
    assignee_app      VARCHAR(8),
    assignee_label    VARCHAR(128),
    requested_by      BIGINT       NOT NULL,
    activated_at      TIMESTAMPTZ,
    decided_by        BIGINT,
    decided_at        TIMESTAMPTZ,
    decision_reason   TEXT,
    reminded_at       TIMESTAMPTZ,
    escalated_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_approval_doc ON core.approval_task (doc_type, doc_id, level);
CREATE INDEX ix_approval_pending_user ON core.approval_task (assignee_user_id) WHERE status = 'PENDING';
CREATE INDEX ix_approval_pending_role ON core.approval_task (assignee_role, assignee_app) WHERE status = 'PENDING';

-- Riwayat aktivitas dokumen: perubahan status dan komentar (panel kanan form).
CREATE TABLE core.activity_log (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    doc_type     VARCHAR(16)  NOT NULL,
    doc_id       BIGINT       NOT NULL,
    kind         VARCHAR(12)  NOT NULL,          -- STATUS / COMMENT / ATTACHMENT / SIGNATURE
    from_status  VARCHAR(16),
    to_status    VARCHAR(16),
    message      TEXT,
    user_id      BIGINT,
    username     VARCHAR(64),
    ts           TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_activity_doc ON core.activity_log (doc_type, doc_id, ts);
CREATE TRIGGER trg_activity_log_immutable BEFORE UPDATE OR DELETE ON core.activity_log
    FOR EACH ROW EXECUTE FUNCTION core.forbid_change();

-- Lampiran dokumen.
CREATE TABLE core.attachment (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    doc_type      VARCHAR(16)  NOT NULL,
    doc_id        BIGINT       NOT NULL,
    filename      VARCHAR(255) NOT NULL,
    content_type  VARCHAR(128),
    size_bytes    BIGINT       NOT NULL,
    storage_key   VARCHAR(255) NOT NULL,
    sha256        VARCHAR(64)  NOT NULL,
    uploaded_by   BIGINT       NOT NULL,
    uploaded_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_attachment_doc ON core.attachment (doc_type, doc_id);

-- Notifikasi lonceng navbar.
CREATE TABLE core.notification (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    kind        VARCHAR(24)  NOT NULL,
    title       VARCHAR(255) NOT NULL,
    body        TEXT,
    link        VARCHAR(255),
    read_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_notification_user ON core.notification (user_id, created_at DESC);

-- Tanda tangan elektronik (21 CFR Part 11): siapa, makna, kapan (waktu server).
CREATE TABLE core.e_signature (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    doc_type    VARCHAR(16)  NOT NULL,
    doc_id      BIGINT       NOT NULL,
    meaning     VARCHAR(16)  NOT NULL,           -- AUTHOR / REVIEW / APPROVE / RELEASE / VERIFY
    user_id     BIGINT       NOT NULL,
    username    VARCHAR(64)  NOT NULL,
    full_name   VARCHAR(128) NOT NULL,
    reason      TEXT,
    signed_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_esign_doc ON core.e_signature (doc_type, doc_id);
CREATE TRIGGER trg_esign_immutable BEFORE UPDATE OR DELETE ON core.e_signature
    FOR EACH ROW EXECUTE FUNCTION core.forbid_change();

-- Indeks semua dokumen transaksi lintas modul: pencarian global (Ctrl+K), antrean kerja,
-- "dokumen saya", dan tautan dokumen terkait. Diperbarui setiap perubahan status.
CREATE TABLE core.document_index (
    doc_type    VARCHAR(16)   NOT NULL,
    doc_id      BIGINT        NOT NULL,
    doc_no      VARCHAR(40)   NOT NULL,
    app_code    VARCHAR(8)    NOT NULL,
    menu_code   VARCHAR(16)   NOT NULL,
    plant_id    BIGINT        NOT NULL,
    status      VARCHAR(12)   NOT NULL,
    summary     VARCHAR(255),
    amount      NUMERIC(19,2),
    doc_date    DATE          NOT NULL,
    created_by  BIGINT,
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (doc_type, doc_id)
);
CREATE INDEX ix_docidx_no ON core.document_index (lower(doc_no) text_pattern_ops);
CREATE INDEX ix_docidx_creator ON core.document_index (created_by, status, updated_at DESC);
CREATE INDEX ix_docidx_app ON core.document_index (app_code, plant_id, status, updated_at DESC);

-- Kunci periode per modul (FIN-70). module_code 'ALL' mengunci semua modul.
CREATE TABLE core.period_lock (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    module_code  VARCHAR(8)  NOT NULL,
    year         SMALLINT    NOT NULL,
    month        SMALLINT    NOT NULL CHECK (month BETWEEN 1 AND 12),
    locked       BOOLEAN     NOT NULL DEFAULT TRUE,
    locked_by    BIGINT,
    locked_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (module_code, year, month)
);
