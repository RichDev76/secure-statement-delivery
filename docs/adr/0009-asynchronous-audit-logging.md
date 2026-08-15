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

## References

- `docs/standards/observability.md`
