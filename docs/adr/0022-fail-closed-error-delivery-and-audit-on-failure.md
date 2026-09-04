# 0022 — Fail-closed error delivery with audit-on-failure for statement transfer paths

## Context

ADR [0013](0013-split-exception-handler-chain.md) split the handler chain but left delivery gaps.
Lazy decryption committed the 200 before the GCM tag was even checked — `CipherInputStream`
swallows `AEADBadTagException`, so tampered ciphertext streamed out as a truncated 200 with a
`DOWNLOAD_SUCCESS` audit row attached. Upload failures were never audited at all, while downloads
already audit six reasons. Several emitted errorCodes were missing from the OpenAPI enum, and DEBUG
logs printed raw service arguments, including signed-link tokens.

## Problem

What doctrine governs how failures become responses, audit rows, and log lines — and how is it
enforced against drift?

## Decision

Four rules, each backed by a test.

Failures are determined before the response commits. `FileCipher.decrypt` is now eager (`byte[]`
in/out, `doFinal`-based), so the GCM tag is verified before the 200 goes out. `open()` classifies
`SdkException` as `StatementStorageUnavailableException`, mirroring `exists()`
([0021](0021-dependency-failure-policy-and-health-groups.md)); `DownloadService` narrows its broad
catch to storage-unavailable, file-missing, and residual-decryption cases specifically.

Every client-observable error carries a contract-documented `errorCode` — including 401/403
(`UNAUTHENTICATED`/`ACCESS_DENIED`) and 413 (`UPLOAD_TOO_LARGE`, the 10MB multipart cap). The enum
backfill ships as spec 1.12.0, following the [0020](0020-signed-link-abuse-hardening.md) precedent.

Transfer-path failures now leave audit evidence. `UPLOAD_FAILED` plus `UploadFailureReason` mirror
the download side, with the account number persisted only when it matches the account pattern.
Audit stays fail-open ([0009](0009-asynchronous-audit-logging.md)) but is never silent anymore:
empty catches become WARNs, and `record()` guards against `RejectedExecutionException`.

And logs never carry raw argument content. `LoggingAspect` only prints values for a type allowlist
(UUID, scalars, enums, `java.time`); everything else — strings included — becomes `String[len=N]`.
This fails closed by design.

Drift protection comes from `ExceptionHandlerCompletenessTest`, which requires every production
`Throwable` to be claimed by an advice or explicitly whitelisted with a rationale, and every
map-backed advice to carry metadata for every type it handles.

## Alternatives

Advice-level upload auditing didn't have the business context to do this well, and risked
double-auditing. Streaming decrypt with trailer verification is the correct long-term answer, but
we're deferring it alongside the broader streaming work in ADR-0023. Parameter-name log masking
fails open without `-parameters` on the build; the type allowlist fails closed instead, which is
why we went with that.

## Consequences

Downloads now briefly hold both ciphertext and plaintext — roughly 2x file size — bounded by the
10MB cap. S3-outage and deleted-object downloads change status (500 → 503/404) as documented
corrections. Audit volume grows with failed uploads, which is additive value stored in an
unconstrained `varchar(64)` with no migration needed. And DEBUG string detail shrinks down to
lengths only.

## Implementation Notes

Spec moves 1.11.0 → 1.12.0 (additive). Keystone test: `CorruptCiphertextDownloadIT` — a tampered
byte produces a 500 `DECRYPTION_FAILED`, with no `DOWNLOAD_SUCCESS` row.

## References

- [0013 — Split exception handler chain](0013-split-exception-handler-chain.md)
- [0020 — Signed-link abuse hardening](0020-signed-link-abuse-hardening.md)
- [0021 — Dependency failure policy and health groups](0021-dependency-failure-policy-and-health-groups.md)
- RFC 9457
