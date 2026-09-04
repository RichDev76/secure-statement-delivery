# 0005 — Local filesystem storage for encrypted statements

> **Superseded by [0014](0014-local-storage-to-floci-s3-migration.md).** Migrated to
> S3-compatible object storage to support horizontal scaling.

## Context

Encrypted statement files need somewhere to live, separate from their metadata in Postgres.

## Problem

The simplest option available at project start was the local container filesystem; a durable,
shared object store wasn't in scope yet.

## Decision

`FileStorageService` writes encrypted files under a configured base directory
(`statement.storage.base-dir`), structured as
`{baseDir}/statements/{sha256(accountNumber)}/{year}/{month}/{statementId}.pdf.enc`. The account
number is hashed before it ever appears in a path, for privacy.

## Alternatives

Object storage (S3/Floci) from day one was tempting, but felt premature at this stage — it's
exactly what we later adopted in [0014](0014-local-storage-to-floci-s3-migration.md) once
horizontal scaling actually became necessary. Storing file bytes as a DB blob was also considered
and dropped: it bloats Postgres backups for no real benefit over a filesystem path.

## Consequences

This gives us simple, dependency-free storage for a single-instance deployment. The trade-off is
that storage is tied to one host's disk: horizontal scaling would give each replica an incomplete
view of uploaded statements, and there's no durability or versioning story.

## Implementation Notes

`LocalStatementFileStore` implements the `StatementFileStore` port.

## References

- [0014 — Local disk storage to Floci/S3 migration](0014-local-storage-to-floci-s3-migration.md)
