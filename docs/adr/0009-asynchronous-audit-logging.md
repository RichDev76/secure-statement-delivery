# 0009 — Asynchronous, fail-open audit logging

## Context

Every upload, download, and link-generation attempt — success or failure — needs to produce an
audit log entry for compliance and investigation.

## Problem

Writing an audit row synchronously on the request path adds DB latency to every API call, and a
transient audit-write failure shouldn't be allowed to fail the user-facing operation it's
recording.

## Decision

`AuditService.record(...)` builds the `AuditLog` and hands the actual save off to a dedicated
executor — fire-and-forget from the caller's perspective. A failed save is logged at ERROR and
otherwise swallowed. Callers on the download path also wrap their own `record(...)` call in a
try/catch, so an audit failure can never fail the response it's meant to be auditing.

## Alternatives

A synchronous write on the request thread is the simplest option, but it couples audit-store
latency and availability to every API call's response time. A message queue like Kafka or SQS
would decouple things fully, but that's infrastructure this project doesn't otherwise need.

## Consequences

Audit writes never add request latency and never fail a user-facing operation. The trade-off is
that audit rows aren't guaranteed to be visible immediately after the triggering request returns
— tests asserting on audit rows have to poll rather than assert immediately — and a dead executor
or sustained save failures can lose audit rows silently beyond the ERROR log.

## Implementation Notes

`AuditService.record` submits to an injected `ExecutorService`.

## Addendum — Loss made measurable, trail made append-only

Audit stays best-effort by design, but two hardening pieces close its sharpest edges. Every
dropped write — executor rejection or save failure — now increments the
`statement.audit.dropped` counter, which is the alertable signal that entries are actually being
lost; the ERROR log alone couldn't give us that. A sustained nonzero rate is the trigger for
upgrading to a transactional outbox — if a regulator ever requires every download to be evidenced,
that upgrade stops being optional. Separately, `audit_logs` is now append-only at the database
layer (V12): a row trigger on the partitioned parent rejects UPDATE/DELETE on every current and
future partition. We rejected a REVOKE-based approach because the single application role owns the
schema, making it self-revocable; genuine migration-owner vs. runtime role separation is deferred
as infra work.
