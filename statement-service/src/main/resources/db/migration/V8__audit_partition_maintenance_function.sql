-- Creates monthly audit_logs partitions ahead of need. Anchors on the "frontier" - the max
-- existing partition's upper bound, read back from pg_catalog - rather than on now(), so it
-- never collides with or leaves a gap against whatever partitions already exist (e.g. if the job
-- hasn't run in a while, or on the very first run after V7's initial two forward partitions).
--
-- No expiry/drop function: partition creation is mandatory (inserts fail once no partition
-- matches), but automatic deletion of old partitions is a separate, not-yet-made retention
-- decision - partitions accumulate until that decision is made.
CREATE OR REPLACE FUNCTION create_audit_partitions(months_ahead int)
RETURNS void AS $$
DECLARE
    frontier date;
    partition_start date;
    partition_end date;
    partition_name text;
    i int;
BEGIN
    SELECT COALESCE(
        MAX((regexp_match(pg_get_expr(c.relpartbound, c.oid), 'TO \(''(\d{4}-\d{2}-\d{2})'))[1]::date),
        date_trunc('month', CURRENT_DATE)::date
    )
    INTO frontier
    FROM pg_inherits pi
    JOIN pg_class c ON c.oid = pi.inhrelid
    WHERE pi.inhparent = 'audit_logs'::regclass
      AND c.relname <> 'audit_logs_default';

    FOR i IN 0..(months_ahead - 1) LOOP
        partition_start := (frontier + (i || ' months')::interval)::date;
        partition_end := (partition_start + interval '1 month')::date;
        partition_name := 'audit_logs_' || to_char(partition_start, 'YYYY_MM');

        IF NOT EXISTS (SELECT 1 FROM pg_class WHERE relname = partition_name) THEN
            EXECUTE format(
                'CREATE TABLE %I PARTITION OF audit_logs FOR VALUES FROM (%L) TO (%L)',
                partition_name, partition_start, partition_end);
        END IF;
    END LOOP;
END;
$$ LANGUAGE plpgsql;
