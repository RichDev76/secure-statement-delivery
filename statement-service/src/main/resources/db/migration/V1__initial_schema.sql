CREATE
EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE statements
(
    id               uuid PRIMARY KEY       DEFAULT gen_random_uuid(),
    account_number   varchar(64)   NOT NULL,
    statement_date   date          NOT NULL,
    upload_file_name varchar(1024) NOT NULL,
    file_path        varchar(1024) NOT NULL,
    file_iv          bytea,
    content_hash     varchar(128),
    uploaded_by      varchar(128),
    uploaded_at      timestamptz   NOT NULL DEFAULT now(),
    encrypted        boolean       NOT NULL DEFAULT true,
    size_bytes       bigint
);

CREATE TABLE signed_links
(
    id           uuid PRIMARY KEY      DEFAULT gen_random_uuid(),
    statement_id uuid REFERENCES statements (id) ON DELETE CASCADE,
    token        varchar(256) NOT NULL,
    expires_at   timestamptz  NOT NULL,
    single_use   boolean      NOT NULL DEFAULT true,
    used         boolean      NOT NULL DEFAULT false,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    created_by   varchar(128)
);

CREATE TABLE audit_logs
(
    id             uuid PRIMARY KEY     DEFAULT gen_random_uuid(),
    action         varchar(64) NOT NULL,
    statement_id   uuid,
    account_number varchar(64),
    signed_link_id uuid,
    performed_by   varchar(128),
    performed_at   timestamptz NOT NULL DEFAULT now(),
    details        jsonb
);

CREATE TABLE shedlock
(
    name       VARCHAR(64)              NOT NULL,
    lock_until TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_by  VARCHAR(255)             NOT NULL,
    PRIMARY KEY (name)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_statements_account_date ON statements (account_number, statement_date);
CREATE INDEX IF NOT EXISTS idx_statements_account_date_uploaded ON statements (account_number, statement_date, uploaded_at DESC);
CREATE INDEX IF NOT EXISTS idx_signed_links_token ON signed_links (token);
CREATE INDEX IF NOT EXISTS idx_signed_links_statement_id ON signed_links (statement_id);
CREATE INDEX IF NOT EXISTS idx_signed_links_expires_at ON signed_links (expires_at);
CREATE INDEX IF NOT EXISTS idx_signed_links_used ON signed_links (used);
CREATE INDEX IF NOT EXISTS idx_audit_logs_statement_id ON audit_logs (statement_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_signed_link_id ON audit_logs (signed_link_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_performed_at ON audit_logs (performed_at);
CREATE INDEX IF NOT EXISTS idx_audit_logs_performed_by ON audit_logs (performed_by);
CREATE INDEX IF NOT EXISTS idx_statements_uploaded_at ON statements (uploaded_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_account_number ON audit_logs (account_number);
CREATE INDEX IF NOT EXISTS idx_statements_account_number ON statements (account_number);

