# 0025 — Structured JSON logging and no-PII-in-logs policy

## Context

Logging was Spring Boot's default console pattern, which isn't machine-parseable. A sweep also
found the `x-correlation-id` header flowing unvalidated into every log line's MDC value (a
log-injection risk), nine call sites logging a customer-identifying value in plaintext, and no HTTP
access-log filter at all.

## Problem

Default console logging can't be parsed by log aggregators, and PII reaching logs in plaintext is
both a compliance risk and effectively unbounded — nothing catches a new call site logging an
account number.

## Decision

Logs go out as JSON via `logstash-logback-encoder` in every profile except `local` (async-wrapped,
capped stack traces); plain console output stays under `local`.

`x-correlation-id` is now validated — length ≤ 64, charset `[A-Za-z0-9._-]` — and anything else is
replaced with a generated UUID, with the rejection logged by length only, never by value.

A new `RequestLoggingFilter` logs method/endpoint/status/duration via a bounded `EndpointLabel` in
place of the raw URI, so the download route's filename can never show up in a log line.

No customer-identifying value gets written to a log at all — no account numbers, filenames, or
signed-link tokens. Diagnosis relies on join keys already present at each call site instead
(`statementId`, `signedLinkId`, `correlationId`), resolvable only through the
`AuditLogsSearch`-gated audit endpoint. No method signature or audit record needed to change for
this.

This is enforced by tests, not review: a `LogCapture` helper backs guards asserting both that the
sensitive value is absent and that the replacement join key is present.

And distributed tracing is explicitly deferred, not partially wired in.

## Alternatives

Masking rather than removing (e.g. last 4 digits) still leaves personal data in the logs, and
`statementId` is a strictly better join key anyway. A global masking turbo-filter isn't a good
primary control — it costs on the hot path and fails open on any format it doesn't recognize.
Declaring `traceId`/`spanId` now and wiring a tracer later would just be a half-wired promise the
logs can't keep.

## Consequences

Diagnosing a customer's issue now requires resolving `statementId` through the audit API, rather
than grepping logs for an account number. A log export can be shared with an aggregator without
needing a data-protection review first.

## References

- [0022 — Fail-closed error delivery with audit-on-failure for statement transfer paths](0022-fail-closed-error-delivery-and-audit-on-failure.md)
