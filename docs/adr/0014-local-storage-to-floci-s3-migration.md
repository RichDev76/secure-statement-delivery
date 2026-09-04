# 0014 — Local disk storage to Floci/S3 migration

**Addendum (2026-08):** decisions 1 and 4 below were later revised by the streaming upload work
(see the addendum on [0023](0023-single-streaming-digest-pass-and-deferred-upload-streaming.md)):
`StatementFileStore` became a pull-based streaming port, and `store()` no longer buffers
ciphertext — it PUTs with a precomputed Content-Length instead.

## Context

Statement files were stored encrypted on a shared local disk volume via `LocalStatementFileStore`,
with `Statement.filePath` an absolute path resolved against the container's filesystem. That ties
storage to a single host's disk and blocks horizontal scaling. This migration uses
[Floci](https://floci.io) as the local/CI S3-compatible backend.

## Problem

A local volume ties storage to one host's disk: it doesn't survive horizontal scaling, and there's
no durability or versioning story.

## Decision

The `StatementFileStore` port itself is unchanged — only the adapter is being replaced. Folding
encryption responsibility into the storage adapter would be a port redesign, and that's out of
scope for what's meant to be a backend swap.

`S3StatementFileStore` replaces `LocalStatementFileStore`, named after the protocol (S3) rather
than the local emulator, so production can point at real AWS S3 with nothing more than a blank
`endpoint` property.

We use a synchronous `S3Client`, not `S3AsyncClient` — the call site is already blocking inside an
`@Transactional` method, so async-then-`.join()` would just add complexity with no behavioral
benefit.

`store()` buffers ciphertext into memory before `putObject`, since S3 needs a known
`Content-Length` up front. That's acceptable for statement-sized PDFs; multipart upload is out of
scope here.

`exists()` propagates any `SdkException` other than `NoSuchKeyException` — an S3 outage must never
be silently reported as "file missing," the same failure class [0013](0013-split-exception-handler-chain.md)
already fixed for exception handling generally.

`statements.file_path` is renamed to `storage_key` via a new Flyway migration, since it now holds
an S3 object key rather than a filesystem path.

And Floci is the S3-compatible backend everywhere non-prod, via a plain Testcontainers
`GenericContainer` rather than a LocalStack-branded module.

## Alternatives

Folding encryption into the storage-port redesign would have expanded this change well beyond a
backend swap, with nothing actually driving that scope. `S3AsyncClient` plus a virtual-thread
executor didn't offer any behavioral upside over the synchronous approach. And leaving `file_path`
unrenamed would have been immediately misleading once it holds an S3 key instead of a path.

## Consequences

Uploads and downloads now depend on the S3/Floci endpoint being reachable; `S3HealthIndicator`
surfaces that on `/actuator/health`. The service can run as multiple replicas against the same
bucket. `store()` buffers the full ciphertext in memory, which is acceptable at current statement
sizes (and has since been removed entirely by the streaming work — see the addendum above). Out of
scope for this change: multipart upload, S3 versioning/lifecycle, and migrating pre-existing
statements (none exist in production yet anyway).

## References

- [0005 — Local filesystem storage for encrypted statements](0005-local-filesystem-statement-storage.md)
- [0011 — Adopt feature-first hexagonal packaging](0011-adopt-feature-first-hexagonal-packaging.md)
- [0013 — Split exception handler chain](0013-split-exception-handler-chain.md)
