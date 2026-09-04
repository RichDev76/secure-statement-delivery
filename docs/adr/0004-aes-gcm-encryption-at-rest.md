# 0004 — AES-GCM encryption at rest

> **Superseded by [0015](0015-time-based-signed-links-and-envelope-encryption.md).** Files were
> encrypted directly with the master key below, not a per-file key wrapped by it; 0015 introduces
> genuine per-file envelope encryption.

## Context

Statements carry PII and financial data, so they need to be encrypted before they ever leave the
application — disk- or database-level encryption alone isn't enough.

## Problem

Disk/DB encryption doesn't help if the storage layer itself is compromised, and it gives us no
way to control encryption per file.

## Decision

We encrypt files with `AES/GCM/NoPadding` directly under a single Master Encryption Key, sourced
via `MasterKeyProvider` (Vault-backed). Each file gets a random 12-byte IV, prepended to the
ciphertext stream, and the 128-bit GCM tag gives us both integrity and authenticity. Account
numbers are hashed (SHA-256) before they're used in storage object keys; the database columns
keep them in clear text, since both search and audit need to query by account number.

## Alternatives

We looked at PostgreSQL's `pgcrypto`, but that ties encryption to the database and would
complicate any future storage migration. Cloud KMS (AWS or GCP) is a reasonable option down the
line, but Vault gets us a provider-agnostic starting point without committing to a cloud vendor
this early.

## Consequences

Ciphertext is unreadable without the master key, and the GCM tag catches tampering. The
trade-off: a single master key compromise decrypts every statement in the system — there's no
per-file blast-radius containment.

## Implementation Notes

`EncryptionService` handles streaming encrypt/decrypt; `MasterKeyProvider` loads the key from
Vault-backed configuration.

## References

- [0015 — Time-based signed links and envelope encryption](0015-time-based-signed-links-and-envelope-encryption.md)
