# 0022 — Fail-closed error delivery with audit-on-failure for statement transfer paths

## Context

ADR [0013](0013-split-exception-handler-chain.md) split the handler chain but left delivery gaps.
Lazy decryption committed the 200 before the GCM tag was checked - `CipherInputStream` swallows
`AEADBadTagException`, so tampered ciphertext streamed as a truncated 200 with a
`DOWNLOAD_SUCCESS` audit row. Upload failures were never audited while downloads audit six
reasons. Several emitted errorCodes were missing from the OpenAPI enum, and DEBUG logs printed
raw service arguments, including signed-link tokens.

## Problem

What doctrine governs how failures become responses, audit rows, and log lines - and how is it
enforced against drift?

## Decision

Four rules, each enforced by a test:

1. **Failures are determined before the response commits.** `FileCipher.decrypt` is eager
   (`byte[]` in/out, `doFinal`-based): the GCM tag is verified before the 200. `open()`
   classifies `SdkException` as `StatementStorageUnavailableException` (mirroring `exists()`,
   [0021](0021-dependency-failure-policy-and-health-groups.md)); `DownloadService` narrows its
   broad catch to storage-unavailable / file-missing / residual-decryption.
2. **Every client-observable error carries a contract-documented `errorCode`** - including
   401/403 (`UNAUTHENTICATED`/`ACCESS_DENIED`) and 413 (`UPLOAD_TOO_LARGE`, 10MB multipart cap);
   the enum backfill ships as spec 1.12.0 ([0020](0020-signed-link-abuse-hardening.md) precedent).
3. **Transfer-path failures leave audit evidence.** `UPLOAD_FAILED` + `UploadFailureReason`
   mirror the download side; account number persisted only when it matches the account pattern.
   Audit stays fail-open ([0009](0009-asynchronous-audit-logging.md)) but never silent: empty
   catches become WARNs; `record()` guards `RejectedExecutionException`.
4. **Logs never carry raw argument content.** `LoggingAspect` prints values only for a type
   allowlist (UUID, scalars, enums, `java.time`); strings become `String[len=N]`. Fails closed.

Drift protection: `ExceptionHandlerCompletenessTest` requires every production `Throwable` to be
claimed by an advice or whitelisted with a rationale, and every map-backed advice to have
metadata for every handled type.

## Alternatives

- **Advice-level upload auditing** - no business context there; double-audit risk.
- **Streaming decrypt with trailer verification** - correct long-term; owned by the streaming plan.
- **Parameter-name log masking** - fails open without `-parameters`; type allowlist fails closed.

## Consequences

- Downloads briefly hold ciphertext + plaintext (~2x file size), bounded by the 10MB cap.
- S3-outage and deleted-object downloads change status (500 → 503/404) as documented corrections.
- Audit volume grows with failed uploads; additive value in unconstrained `varchar(64)`, no migration.
- DEBUG string detail shrinks to lengths.

## Implementation Notes

Spec 1.11.0 → 1.12.0 (additive). Plan: `docs/ErrorHandlingGapsAndFixPlan.html`. Keystone test:
`CorruptCiphertextDownloadIT` (tampered byte → 500 `DECRYPTION_FAILED`, no `DOWNLOAD_SUCCESS` row).

## References

ADR [0013](0013-split-exception-handler-chain.md) · ADR [0020](0020-signed-link-abuse-hardening.md)
· ADR [0021](0021-dependency-failure-policy-and-health-groups.md) · RFC 9457
