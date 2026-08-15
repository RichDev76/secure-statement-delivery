# 0005 — Local filesystem storage for encrypted statements

> **Superseded by [0014](0014-local-storage-to-floci-s3-migration.md).** Migrated to
> S3-compatible object storage to support horizontal scaling.

## Context

Encrypted statement files need somewhere to live, distinct from their metadata in Postgres.

## Problem

The simplest available option at project start was the local container filesystem; a durable,
shared object store was not yet in scope.

## Decision

`FileStorageService` writes encrypted files under a configured base directory
(`statement.storage.base-dir`), structured as
`{baseDir}/statements/{sha256(accountNumber)}/{year}/{month}/{statementId}.pdf.enc` — the account
number is hashed before it appears in any path, for privacy.

## Consequences

- Simple, dependency-free storage for a single-instance deployment.
- Ties storage to one host's disk: horizontal scaling gives each replica an incomplete view of
  uploaded statements, and there is no durability/versioning story.

## Implementation Notes

`LocalStatementFileStore` implements the `StatementFileStore` port.

## References

- [0014 — Local disk storage to Floci/S3 migration](0014-local-storage-to-floci-s3-migration.md)
