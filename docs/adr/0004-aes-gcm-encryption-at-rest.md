# 0004 — AES-GCM encryption at rest

> **Superseded by [0015](0015-time-based-signed-links-and-envelope-encryption.md).** Files were
> encrypted directly with the master key below, not a per-file key wrapped by it; 0015 introduces
> genuine per-file envelope encryption.

## Context

Statements contain sensitive PII and financial data and must be encrypted before they leave the
application, independent of disk- or database-level encryption.

## Problem

Relying solely on disk/DB encryption doesn't protect against a compromised storage layer and
gives no per-file granularity.

## Decision

Files are encrypted with `AES/GCM/NoPadding` directly under a single Master Encryption Key,
sourced via `MasterKeyProvider` (Vault-backed). A random 12-byte IV is generated per file and
prepended to the ciphertext stream; the 128-bit GCM tag provides integrity and authenticity.
Account numbers are hashed (SHA-256) before use in storage paths or persistence — never stored
in clear text.

## Alternatives

- PostgreSQL `pgcrypto`: ties encryption to the database, complicating a future storage migration.
- Cloud KMS (AWS/GCP): viable later; Vault gives a provider-agnostic starting point.

## Consequences

- Ciphertext is unreadable without the master key, with tamper detection via the GCM tag.
- A single master key compromise decrypts every statement — no per-file blast-radius containment.

## Implementation Notes

`EncryptionService` performs streaming encrypt/decrypt; `MasterKeyProvider` loads the key from
Vault-backed configuration.

## References

- `docs/standards/security.md`
- [0015 — Time-based signed links and envelope encryption](0015-time-based-signed-links-and-envelope-encryption.md)
