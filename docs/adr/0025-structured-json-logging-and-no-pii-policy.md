# 0025 — Structured JSON logging and no-PII-in-logs policy

## Context

Logging was Spring Boot's default console pattern — not machine-parseable. A sweep also found
the `x-correlation-id` header flowing unvalidated into every log line's MDC value (log-injection
risk), nine call sites logging a customer-identifying value in plaintext, and no HTTP access-log
filter.

## Problem

Default console logging can't be parsed by log aggregators, and PII reaching logs in plaintext
is both a compliance risk and unbounded — nothing catches a new call site logging an account
number.

## Decision

1. **JSON logs via `logstash-logback-encoder`** in every profile except `local` (async-wrapped,
   capped stack traces); plain console stays under `local`.
2. **`x-correlation-id` is validated** (length ≤ 64, charset `[A-Za-z0-9._-]`); anything else is
   replaced with a generated UUID, and the rejection is logged by length, never value.
3. **A new `RequestLoggingFilter`** logs method/endpoint/status/duration via a bounded
   `EndpointLabel` in place of the raw URI, so the download route's filename can never appear.
4. **No customer-identifying value is written to a log** — account numbers, filenames,
   signed-link tokens. Diagnosis uses join keys already at each call site instead
   (`statementId`, `signedLinkId`, `correlationId`), resolvable only via the
   `AuditLogsSearch`-gated audit endpoint. No method signature or audit record changed.
5. **Enforced by tests, not review**: a `LogCapture` helper backs guards asserting both that the
   sensitive value is absent and the replacement join key is present.
6. **Distributed tracing is explicitly deferred, not partially wired.**

## Alternatives

- Mask rather than remove (last 4 digits): rejected — still personal data, and `statementId` is a
  strictly better join key.
- Global masking turbo-filter: rejected as the primary control — hot-path cost, fails open on any
  unrecognised format.
- Declare `traceId`/`spanId` now, wire a tracer later: rejected — a half-wired promise the logs
  can't keep.

## Consequences

- Diagnosing a customer's issue now requires resolving `statementId` through the audit API,
  rather than grepping logs for an account number.
- A log export can be shared with an aggregator without a data-protection review.

## References

- `docs/standards/security.md`
- [0022 — Fail-closed error delivery with audit-on-failure for statement transfer paths](0022-fail-closed-error-delivery-and-audit-on-failure.md)
