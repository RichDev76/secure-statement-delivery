# 0009 — Asynchronous, fail-open audit logging

## Context

Every upload, download, and link-generation attempt (success or failure) must produce an audit
log entry for compliance and investigation.

## Problem

Writing an audit row synchronously on the request path adds DB latency to every API call, and a
transient audit-write failure should not be allowed to fail the user-facing operation it's
recording.

## Decision

`AuditService.record(...)` builds the `AuditLog` and submits the actual save to a dedicated
executor — fire-and-forget from the caller's perspective. A failed save is logged at ERROR and
otherwise swallowed; callers on the download path additionally wrap their own `record(...)` call
in a try/catch so an audit failure never fails the response being audited.

## Alternatives

- Synchronous write on the request thread: simplest, but couples audit-store latency/availability
  to every API call's response time.
- A message queue (Kafka/SQS): decouples fully, but is infrastructure this project doesn't
  otherwise need.

## Consequences

- Audit writes never add request latency and never fail a user-facing operation.
- Audit rows are not guaranteed to be visible immediately after the triggering request returns —
  tests asserting on audit rows must poll, not assert immediately.
- A dead executor or sustained save failures lose audit rows silently beyond the ERROR log.

## Implementation Notes

`AuditService.record` submits to an injected `ExecutorService`.

## Addendum — Loss made measurable, trail made append-only

Audit stays best-effort by design, but two hardening pieces close its sharpest edges:

- Every dropped write (executor rejection or save failure) now increments the
  `statement.audit.dropped` counter — the alertable signal that entries are actually being lost,
  which the ERROR log alone could not provide. A sustained nonzero rate is the trigger for
  upgrading to a transactional outbox; if a regulator ever requires every download to be
  evidenced, that upgrade stops being optional.
- `audit_logs` is append-only at the database layer (V12): a row trigger on the partitioned
  parent rejects UPDATE/DELETE on every current and future partition. A REVOKE was rejected
  because the single application role owns the schema, making it self-revocable; genuine
  migration-owner vs. runtime role separation is deferred as infra work.
