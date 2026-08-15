# 0015 — Time-based signed links and envelope encryption

## Context

Signed download links and file-at-rest encryption already existed in this service, on the earlier
design recorded in [0003](0003-hmac-signed-single-use-download-links.md) and
[0004](0004-aes-gcm-encryption-at-rest.md): links were single-use with the raw HMAC token stored
verbatim, and files were encrypted directly with the flat Master Encryption Key (no per-file key).

## Problem

- Single-use links reject a retried download after a network blip.
- The raw token stored in `signed_links.token` meant a read-only DB compromise yielded directly
  reusable download tokens for any unexpired link.
- Every file used the same Master Encryption Key directly — a single key compromise decrypts
  every statement ever uploaded, with no per-file blast-radius containment.

## Decision

1. **Links become purely time-based.** `SignedLinkService.validate()` replaces
   `validateAndConsume()`; the same link can be validated any number of times until `expiresAt`.
2. **Tokens are hashed at rest** (`signed_links.token_hash`, SHA-256, unique-indexed); the raw
   token is `@Transient`, never persisted.
3. **`LinkSigner` gains independent `verify()` plus a nonce.** Validity was previously a bare DB
   lookup with no signature recomputation. `validate()` now verifies the HMAC first (cheap, no DB
   hit) before hashing and looking up — the property that makes token hashing worth anything
   against a DB-only leak. The link's own id is folded into the signed string as a nonce, so two
   links minted for the same file in the same second can't collide on the new unique index.
4. **Signing input moves from a live-request-derived absolute URL to a config-driven relative
   path** (new `SignedLinkProperties`), so independent `verify()` can recompute an identical
   string without depending on the live request's host. This also fixes a pre-existing
   config-binding bug where the configured link TTL was silently ignored.
5. **`GET /download/{fileName}` gains a required `linkId` query parameter** carrying the nonce.
6. **Envelope encryption: a random DEK per file, wrapped by the MEK**, stored only in wrapped form
   (`statements.encrypted_dek`); the raw DEK never leaves a single upload/download call.
7. **DEK wrap uses AES-GCM** (`[version][IV][ciphertext+tag]`, fresh IV per wrap, hard failure on
   an unrecognized version byte) — avoiding the classic unmoded-`Cipher.getInstance("AES")`-
   silently-resolves-to-ECB mistake.
8. **Hard cutover, no data migration** — `V3`/`V4` migrations assume an empty table; no production
   data exists yet to preserve.

## Alternatives

- Keep DB-lookup-only validation (no independent `verify()`): rejected — leaves this project's own
  ADR 0003 intent (signature + DB cross-check) unimplemented.
- AES Key Wrap (RFC 3394) for the DEK wrap: rejected — keeps one cipher primitive (GCM) in play;
  either choice is cryptographically acceptable.
- Legacy-ECB fallback during DEK unwrap: rejected — no persisted rows predate this design, so
  there's nothing to stay compatible with.

## Consequences

- Retried downloads succeed instead of failing on a second attempt; a read-only DB compromise
  alone no longer yields usable download tokens; one statement's DEK leak no longer compromises
  every other statement.
- Breaking by design: any link minted before this deploy is unusable after it. Acceptable — links
  are short-TTL and no production traffic exists yet.
- The redundant post-lookup `linkId`/`expiresAt` cross-checks in `validate()` are intentional
  defense-in-depth, not dead code.

## References

- `docs/standards/security.md`, `docs/standards/testing.md`, `docs/standards/api.md`
- [0003 — HMAC-signed, single-use, time-limited download links](0003-hmac-signed-single-use-download-links.md)
- [0004 — AES-GCM encryption at rest](0004-aes-gcm-encryption-at-rest.md)
- [0011 — Adopt feature-first hexagonal packaging](0011-adopt-feature-first-hexagonal-packaging.md)
- [0014 — Local disk storage to Floci/S3 migration](0014-local-storage-to-floci-s3-migration.md)
