# 0008 — Scheduled signed-link cleanup with ShedLock

## Context

Expired (and, at the time, used) signed links accumulate in `signed_links` and need periodic
removal.

## Problem

In a multi-instance deployment, more than one instance running the same cleanup job concurrently
wastes DB work and risks lock contention.

## Decision

`SignedLinkCleanupScheduler` runs on a configurable cron schedule, guarded by a ShedLock
`@SchedulerLock` (backed by a `shedlock` table in Postgres) so only one instance executes per
run, and delegates to `SignedLinkCleanupService`, which deletes expired links in batches via
`SignedLinkRepository`. Configurable via
`SignedLinkCleanupProperties` (`enabled`, `cron`, `retentionPeriod`, `batchSize`, `lockAtMostFor`,
`lockAtLeastFor`).

## Alternatives

- Quartz: heavier, more boilerplate than needed for one periodic job.
- Kubernetes CronJob: moves logic outside the application, harder to test and keep in sync with
  the codebase.

## Consequences

Exactly-one-instance execution without an external scheduler; adds a `shedlock` table dependency.

## Implementation Notes

`SignedLinkCleanupScheduler`, `SignedLinkCleanupService`, `SignedLinkCleanupProperties`.

## References

- [ShedLock](https://github.com/lukas-krecan/ShedLock)
