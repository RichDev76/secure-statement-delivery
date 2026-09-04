# 0018 — `audit_logs` partitioning and index cleanup

## Context

`audit_logs` is the one genuinely unbounded, append-only table in the schema, with no existing
retention or cleanup mechanism — unlike `signed_links`, which already has a ShedLock-guarded
cleanup job. It has no FK constraints of its own, which simplifies partitioning since there's no
cascade behavior to design around.

## Problem

An unpartitioned, ever-growing audit table means every index on it grows without bound, and
`AuditLogRepository`'s `(:param IS NULL OR ...)` JPQL query defeats partition pruning even after
partitioning — the planner can't statically eliminate partitions behind an OR whose second arm
depends on a possibly-null parameter.

## Decision

We're using monthly RANGE partitioning on `performed_at`. Postgres has no in-place
table-to-partitioned conversion, but no `audit_logs` row is worth preserving in this
pre-production environment either, so `V7` takes the simpler of the two valid paths:
`DROP TABLE audit_logs` followed by `CREATE TABLE audit_logs (...) PARTITION BY RANGE
(performed_at)` with a composite `PRIMARY KEY (id, performed_at)` — Postgres requires the partition
key in every unique key, so `id` alone is no longer globally enforced-unique by Postgres, though a
collision isn't a practical risk for a UUID. Three monthly partitions are seeded up front, plus a
`DEFAULT` safety-net partition. `AuditLog`'s JPA mapping is unaffected — it keeps its existing bare
`@Id UUID id`.

Index cleanup was evidence-led: the `statement_id`, `signed_link_id`, and `performed_by` indexes
are dropped, since no query in `AuditLogRepository`/`AuditQueryService` filters or sorts on them —
confirmed by actually tracing the code, not assumed. A new composite `(account_number,
performed_at)` index backs the real query shape (equality plus range, `ORDER BY ... DESC`).
`statements.idx_statements_account_number` is also dropped (`V6`), since it's a strict prefix of
the existing unique `(account_number, statement_date)` index and was never going to be chosen by
the planner anyway.

Partition creation is a scheduled job, and expiry is explicitly out of scope. `V8` adds a
`create_audit_partitions(months_ahead)` plpgsql function that anchors on the frontier — the max
existing partition's upper bound, read from `pg_catalog` — rather than on `now()`, so it never
collides with or gaps against existing partitions. `AuditPartitionMaintenanceService` and
`AuditPartitionMaintenanceScheduler` mirror `SignedLinkCleanupService`/`Scheduler` almost exactly,
using the same ShedLock pattern, and call it on a monthly cron, checking that `audit_logs_default`
stays empty and logging at ERROR if it doesn't. There's no expiry/drop function — retention is a
separate decision that hasn't been made yet, so partitions just accumulate until it is.

`AuditLogRepository` also moves to `JpaSpecificationExecutor`. A new `AuditLogSpecifications`
builds only the predicates actually supplied, replacing the OR-chain JPQL so a date-range-only
query — the common case — compiles to a plan the planner can actually prune against.

## Alternatives

We considered renaming the old table aside, creating the partitioned parent under the original
name, and `ATTACH`-ing the old table as a historical partition, but that wasn't needed since no
pre-production row is worth preserving. `pg_partman` for partition maintenance was another option,
but it isn't bundled in the plain `postgres:18-alpine` image used here, and adopting it would mean
maintaining a custom image for one scheduled job this codebase can already express directly.
Automatic partition expiry in this same pass was explicitly rejected too — deleting audit data by
default, before a retention policy is actually decided, risks silent data loss for what's meant to
be a compliance trail. And partitioning `statements` or `signed_links` as well didn't make sense:
`signed_links` is already bounded by its own cleanup job, partitioning it by `expires_at` would
demote its unique `token_hash` index to per-partition and break that uniqueness guarantee, and
`statements` partitioning would force the same composite-PK cost for no benefit at current volume.

## Consequences

Every response from `AuditQueryService` is unchanged in shape — only the query construction
changed, not its filtering semantics, which `AuditLogFilteringIT` covers. A stalled maintenance job
doesn't fail inserts, since the `DEFAULT` partition absorbs anything unmatched — it just degrades
silently into "everything piles into `audit_logs_default`" until the ERROR log gets noticed. Wiring
that to real alerting is a follow-up, not resolved here. And `V7`'s `DROP TABLE audit_logs`
discards any existing rows outright, which is fine for this pre-production environment but would
need the rename/`ATTACH PARTITION` approach instead before running against a database with real
historical data worth keeping.

## References

- [PostgreSQL 18 Table Partitioning](https://www.postgresql.org/docs/current/ddl-partitioning.html)
- [0008 — Scheduled signed-link cleanup with ShedLock](0008-scheduled-cleanup-shedlock.md)
- [0016 — Migrate to PostgreSQL 18](0016-migrate-to-postgresql-18.md)
