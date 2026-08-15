-- audit_logs has no data worth preserving in this environment (pre-production): partition it via
-- a clean drop and recreate rather than the rename-old-table/ATTACH PARTITION dance Postgres
-- otherwise requires to convert an existing table to a partitioned one in place.
DROP TABLE audit_logs;

CREATE TABLE audit_logs (
    id             uuid        NOT NULL,
    action         varchar(64) NOT NULL,
    statement_id   uuid,
    account_number varchar(64),
    signed_link_id uuid,
    performed_by   varchar(128),
    performed_at   timestamptz NOT NULL DEFAULT now(),
    details        jsonb,
    PRIMARY KEY (id, performed_at) -- Postgres requires the partition key in every unique key
) PARTITION BY RANGE (performed_at);

-- statement_id/signed_link_id/performed_by indexes are not recreated: no query in
-- AuditLogRepository/AuditQueryService filters or sorts on them (traced directly, not assumed).
-- account_number is folded into a composite index below, matching the actual query shape
-- (equality on account_number, range + ORDER BY DESC on performed_at).
CREATE INDEX idx_audit_logs_performed_at ON audit_logs (performed_at);
CREATE INDEX idx_audit_logs_account_performed ON audit_logs (account_number, performed_at);

CREATE TABLE audit_logs_2026_08 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE audit_logs_2026_09 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE audit_logs_2026_10 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');

-- Safety net only: AuditPartitionMaintenanceService creates real forward partitions ahead of
-- need. If this ever receives rows, partition-creation maintenance has fallen behind - the
-- service checks and logs an ERROR when that happens.
CREATE TABLE audit_logs_default PARTITION OF audit_logs DEFAULT;
