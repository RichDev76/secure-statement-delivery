# 0008 — Scheduled signed-link cleanup with ShedLock

## Context

Expired (and, at the time, used) signed links accumulate in `signed_links` and need periodic
removal.

## Problem

In a multi-instance deployment, more than one instance running the same cleanup job at once wastes
DB work and risks lock contention.

## Decision

`SignedLinkCleanupScheduler` runs on a configurable cron schedule, guarded by a ShedLock
`@SchedulerLock` (backed by a `shedlock` table in Postgres) so only one instance executes per run.
It delegates to `SignedLinkCleanupService`, which deletes expired links in batches via
`SignedLinkRepository`. Everything's configurable via `SignedLinkCleanupProperties` (`enabled`,
`cron`, `retentionPeriod`, `batchSize`, `lockAtMostFor`, `lockAtLeastFor`).

## Alternatives

Quartz was on the table but felt heavier and more boilerplate than one periodic job needs. A
Kubernetes CronJob would move the logic outside the application entirely, which makes it harder to
test and keep in sync with the codebase.

## Consequences

We get exactly-one-instance execution without needing an external scheduler, at the cost of a new
`shedlock` table dependency.

## Implementation Notes

`SignedLinkCleanupScheduler`, `SignedLinkCleanupService`, `SignedLinkCleanupProperties`.

## References

- [ShedLock](https://github.com/lukas-krecan/ShedLock)
- [0019 — Keep ShedLock over a hand-rolled `SKIP LOCKED` scheduler mutex](0019-shedlock-over-skip-locked.md)
