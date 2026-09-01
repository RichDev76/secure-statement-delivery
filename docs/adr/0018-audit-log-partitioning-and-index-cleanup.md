# 0018 — `audit_logs` partitioning and index cleanup

## Context

`audit_logs` is the one genuinely unbounded, append-only table in the schema, with no existing
retention or cleanup mechanism (unlike `signed_links`, which already has a ShedLock-guarded
cleanup job). It has no FK constraints of its own, which simplifies partitioning — no cascade
behavior to design around.

## Problem

An unpartitioned, ever-growing audit table means every index on it grows without bound, and
`AuditLogRepository`'s `(:param IS NULL OR ...)` JPQL query defeats partition pruning even after
partitioning — the planner can't statically eliminate partitions behind an OR whose second arm
depends on a possibly-null parameter.

## Decision

1. **Monthly RANGE partitioning on `performed_at`.** Postgres has no in-place table-to-partitioned
   conversion, but no `audit_logs` row is worth preserving in this pre-production environment
   either, so `V7` takes the simpler of the two valid paths: `DROP TABLE audit_logs` followed by
   `CREATE TABLE audit_logs (...) PARTITION BY RANGE (performed_at)` with composite
   `PRIMARY KEY (id, performed_at)` (Postgres requires the partition key in every unique key —
   `id` alone is no longer globally enforced-unique by Postgres, though collision isn't a practical
   risk for a UUID), three seeded monthly partitions, and a `DEFAULT` safety-net partition.
   `AuditLog`'s JPA mapping is unaffected — it keeps its existing bare `@Id UUID id`.
2. **Index cleanup, evidence-led.** `statement_id`, `signed_link_id`, and `performed_by` indexes
   are dropped — no query in `AuditLogRepository`/`AuditQueryService` filters or sorts on them,
   confirmed by tracing the actual code, not assumed. A new composite `(account_number,
   performed_at)` index backs the real query shape (equality + range, `ORDER BY ... DESC`).
   `statements.idx_statements_account_number` is also dropped (`V6`) — a strict prefix of the
   existing unique `(account_number, statement_date)` index, so never chosen by the planner.
3. **Partition creation is a scheduled job; expiry is explicitly out of scope.** `V8` adds a
   `create_audit_partitions(months_ahead)` plpgsql function that anchors on the *frontier* (the
   max existing partition's upper bound, read from `pg_catalog`), not on `now()`, so it never
   collides with or gaps against existing partitions. `AuditPartitionMaintenanceService` +
   `AuditPartitionMaintenanceScheduler` (mirroring `SignedLinkCleanupService`/`Scheduler` exactly,
   same ShedLock pattern) call it on a monthly cron and check `audit_logs_default` stays empty,
   logging at ERROR if not. No expiry/drop function — retention is a separate, not-yet-made
   decision; partitions accumulate until that decision is made.
4. **`AuditLogRepository` moves to `JpaSpecificationExecutor`.** A new `AuditLogSpecifications`
   builds only the predicates actually supplied, replacing the OR-chain JPQL so a date-range-only
   query (the common case) compiles to a plan the planner can prune against.

## Alternatives

- Rename-old-table-aside → create the partitioned parent under the original name → `ATTACH` the
  old table as a historical partition: not needed — no pre-production row is worth preserving.
- `pg_partman` for partition maintenance: rejected — not bundled in the plain `postgres:18-alpine`
  image used here; adopting it would mean a custom image for one scheduled job this codebase can
  already express directly.
- Automatic partition expiry in this pass: rejected (explicit decision) — deleting audit data by
  default, before a retention policy is actually decided, risks silent data loss for what's
  described as a compliance trail.
- Partitioning `statements` or `signed_links` too: rejected — `signed_links` is already bounded by
  its own cleanup job, and partitioning by `expires_at` would demote its unique `token_hash` index
  to per-partition, breaking that uniqueness guarantee; `statements` partitioning would force the
  same composite-PK cost for no benefit at current volume.

## Consequences

- Every response from `AuditQueryService` is unchanged in shape — only the query construction
  changed, not its filtering semantics (covered by `AuditLogFilteringIT`).
- A stalled maintenance job doesn't fail inserts (the `DEFAULT` partition absorbs anything
  unmatched) — it degrades silently into "everything piles into `audit_logs_default`" until the
  ERROR log is noticed. Wiring that to real alerting is a follow-up, not resolved here.
- `V7`'s `DROP TABLE audit_logs` discards any existing rows outright — true to this
  pre-production environment; would need the rename/`ATTACH PARTITION` approach instead before
  running against a database with real historical data worth keeping.

## References

- [PostgreSQL 18 Table Partitioning](https://www.postgresql.org/docs/current/ddl-partitioning.html)
- [0008 — Scheduled signed-link cleanup with ShedLock](0008-scheduled-cleanup-shedlock.md)
- [0016 — Migrate to PostgreSQL 18](0016-migrate-to-postgresql-18.md)
