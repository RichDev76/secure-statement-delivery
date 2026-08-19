-- Append-only audit trail: rows may be added and whole partitions dropped (DDL), never mutated.
-- On the partitioned parent: the row trigger propagates to all current and future partitions.

CREATE OR REPLACE FUNCTION audit_logs_block_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only: % is not permitted', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_logs_append_only
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_block_mutation();

-- TRUNCATE bypasses row triggers; partition-targeted TRUNCATE stays possible, same as DROP (DDL).
CREATE TRIGGER audit_logs_append_only_truncate
    BEFORE TRUNCATE ON audit_logs
    FOR EACH STATEMENT EXECUTE FUNCTION audit_logs_block_mutation();
