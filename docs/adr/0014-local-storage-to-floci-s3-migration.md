# 0014 — Local disk storage to Floci/S3 migration

**Addendum (2026-08):** decisions 1 and 4 were later revised by the streaming upload work
(see the addendum on [0023](0023-single-streaming-digest-pass-and-deferred-upload-streaming.md)):
`StatementFileStore` became a pull-based streaming port and `store()` no longer buffers
ciphertext — it PUTs with a precomputed Content-Length.

## Context

Statement files are stored encrypted on a shared local disk volume via `LocalStatementFileStore`,
with `Statement.filePath` an absolute path resolved against the container's filesystem. This ties
storage to a single host/container's disk and blocks horizontal scaling. This migration uses
[Floci](https://floci.io) as the local/CI S3-compatible backend.

## Problem

A local volume ties storage to a single host's disk: it doesn't survive horizontal scaling and has
no durability/versioning story.

## Decision

1. **`StatementFileStore` port is unchanged** — only the adapter is replaced. A port redesign that
   folds encryption responsibility into the storage adapter is out of scope for a backend swap.
2. **`S3StatementFileStore` replaces `LocalStatementFileStore`**, named after the protocol (S3),
   not the local emulator — production points at real AWS S3 with only a blank `endpoint` property.
3. **Synchronous `S3Client`, not `S3AsyncClient`** — the call site is already blocking inside an
   `@Transactional` method; async-then-`.join()` adds complexity with no behavioral benefit.
4. **`store()` buffers ciphertext into memory** before `putObject`, since S3 needs a known
   `Content-Length`. Acceptable for statement-sized PDFs; multipart upload is out of scope.
5. **`exists()` propagates any `SdkException` other than `NoSuchKeyException`** — an S3 outage must
   not be silently reported as "file missing" (the same failure class [0013](0013-split-exception-handler-chain.md) fixed for exception handling).
6. **`statements.file_path` renamed to `storage_key`** via a new Flyway migration, since it now
   holds an S3 object key, not a filesystem path.
7. **Floci as the S3-compatible backend everywhere non-prod**, via a plain Testcontainers
   `GenericContainer` rather than a LocalStack-branded module.

## Alternatives

- Fold encryption into the storage-port redesign: rejected — expands this change well beyond a
  backend swap for no requirement driving it.
- `S3AsyncClient` + virtual-thread executor: rejected — no behavioral upside over synchronous.
- Keep `file_path` unrenamed: rejected — immediately misleading once it holds an S3 key.

## Consequences

- Uploads/downloads now depend on the S3/Floci endpoint; `S3HealthIndicator` surfaces that on
  `/actuator/health`.
- The service can run as multiple replicas against the same bucket.
- `store()` buffers the full ciphertext in memory — acceptable at current statement sizes
  (since removed by the streaming work; see addendum).
- Out of scope: multipart upload, S3 versioning/lifecycle, migrating pre-existing statements
  (none exist in production yet).

## References

- `docs/standards/security.md`, `docs/standards/architecture.md`
- [0005 — Local filesystem storage for encrypted statements](0005-local-filesystem-statement-storage.md)
- [0011 — Adopt feature-first hexagonal packaging](0011-adopt-feature-first-hexagonal-packaging.md)
- [0013 — Split exception handler chain](0013-split-exception-handler-chain.md)
