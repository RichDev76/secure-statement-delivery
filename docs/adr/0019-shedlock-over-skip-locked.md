# 0019 — Keep ShedLock over a hand-rolled `SKIP LOCKED` scheduler mutex

## Context

Two `@Scheduled` jobs are guarded by ShedLock (per [0008](0008-scheduled-cleanup-shedlock.md)):
`SignedLinkCleanupScheduler` (cron `0 0/5 * * * *`, every 5 minutes) and
`AuditPartitionMaintenanceScheduler` (monthly), both via `@SchedulerLock(lockAtMostFor,
lockAtLeastFor)` backed by the `shedlock` table. A Postgres-native `SELECT ... FOR UPDATE SKIP
LOCKED` mutex came up as a dependency-free alternative — Spring's cron triggering would stay
exactly as-is on every node, and only the locking mechanism inside the scheduled method would
change.

## Problem

A `SKIP LOCKED` mutex — one row per job name, claimed with `FOR UPDATE SKIP LOCKED` inside a
transaction, released automatically on commit — has no way to express "held for at least N seconds
past completion." `SignedLinkCleanupScheduler` ticks every 5 minutes, and ordinary clock drift or a
GC pause between nodes is enough for a second node's same-tick invocation to grab the row moments
after the first node released it, running the job twice for one logical tick.

## Decision

We're keeping ShedLock as-is; no code change. Closing the gap above would require a `locked_until`
TTL column checked instead of the raw row lock — which is exactly ShedLock's own `shedlock` table
design, reimplemented by hand for no behavioral gain. `@SchedulerLock`'s one-line declarative use
over `@Scheduled` also fits this project's "prefer AOP for cross-cutting concerns" convention
better than a per-job locking helper would. The one genuine advantage of `SKIP LOCKED` — releasing
instantly on crash instead of waiting for `lockAtMostFor` — doesn't require switching mechanisms at
all: `lockAtMostFor` is currently a generous 5 minutes for both jobs, each of which completes in
well under a minute, and can simply be tuned down separately.

## Alternatives

`SELECT ... FOR UPDATE SKIP LOCKED` against a `job_locks` table runs into the gap described above,
and correcting it for this project's actual cron frequency just converges back to reimplementing
ShedLock's TTL design. Postgres advisory locks (`pg_try_advisory_lock`) are the closer
Postgres-native mutex primitive — session-scoped, released on connection close — but share the same
missing-`lockAtLeastFor` gap, and add a documented incompatibility with PgBouncer
transaction-pooling mode. That's not used in this stack today, but it's a real constraint on a lock
mechanism meant to last. Tightening `lockAtMostFor` instead of switching mechanisms isn't rejected
— it's just a small, separate follow-up, not part of this decision.

## Consequences

No code, migration, or dependency change results from this; the decision is recorded so the
question isn't re-litigated from scratch next time it comes up. `lockAtMostFor: PT5M` on both jobs
remains wider than either job's real runtime — tightening it is a low-risk follow-up, not resolved
here.

## References

- [0008 — Scheduled signed-link cleanup with ShedLock](0008-scheduled-cleanup-shedlock.md)
- [ShedLock](https://github.com/lukas-krecan/ShedLock)
