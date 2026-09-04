# 0015 — Time-based signed links and envelope encryption

## Context

Signed download links and file-at-rest encryption already existed in this service, under the
earlier design recorded in [0003](0003-hmac-signed-single-use-download-links.md) and
[0004](0004-aes-gcm-encryption-at-rest.md): links were single-use with the raw HMAC token stored
verbatim, and files were encrypted directly with the flat Master Encryption Key — no per-file key.

## Problem

Single-use links reject a retried download after a network blip. The raw token stored in
`signed_links.token` meant a read-only DB compromise would hand over directly reusable download
tokens for any unexpired link. And every file used the same Master Encryption Key directly, so a
single key compromise would decrypt every statement ever uploaded, with no per-file blast-radius
containment.

## Decision

Links become purely time-based. `SignedLinkService.validate()` replaces `validateAndConsume()`,
and the same link can now be validated repeatedly until `expiresAt` (later bounded by
[0020](0020-signed-link-abuse-hardening.md)'s redemption cap).

Tokens are hashed at rest (`signed_links.token_hash`, SHA-256, unique-indexed); the raw token is
`@Transient` and never persisted.

`LinkSigner` gains an independent `verify()` plus a nonce. Validity used to be a bare DB lookup
with no signature recomputation. `validate()` now verifies the HMAC first — cheap, no DB hit —
before hashing and looking up, which is the property that makes token hashing worth anything
against a DB-only leak. The link's own id gets folded into the signed string as a nonce, so two
links minted for the same file in the same second can't collide on the new unique index.

Signing input moves from a live-request-derived absolute URL to a config-driven relative path (new
`SignedLinkProperties`), so an independent `verify()` can recompute an identical string without
depending on the live request's host. This also happened to fix a pre-existing config-binding bug
where the configured link TTL was silently ignored.

`GET /download/{fileName}` now requires a `linkId` query parameter carrying the nonce.

Envelope encryption gives each file a random DEK, wrapped by the MEK and stored only in wrapped
form (`statements.encrypted_dek`); the raw DEK never leaves a single upload/download call.

The DEK wrap uses AES-GCM (`[version][IV][ciphertext+tag]`, a fresh IV per wrap, hard failure on an
unrecognized version byte) — avoiding the classic mistake where an unmoded
`Cipher.getInstance("AES")` silently resolves to ECB.

And it's a hard cutover with no data migration: `V3`/`V4` assume an empty table, since no
production data exists yet to preserve.

## Alternatives

Keeping DB-lookup-only validation, without an independent `verify()`, would have left this
project's own ADR 0003 intent — signature plus DB cross-check — unimplemented. AES Key Wrap (RFC
3394) for the DEK wrap was on the table too, but it just keeps one cipher primitive (GCM) in play;
either choice is cryptographically fine, so there wasn't a strong reason to add a second primitive.
A legacy-ECB fallback during DEK unwrap wasn't needed either, since no persisted rows predate this
design — there's nothing to stay compatible with.

## Consequences

Retried downloads now succeed instead of failing on a second attempt. A read-only DB compromise
alone no longer yields usable download tokens, and one statement's DEK leak no longer compromises
every other statement. This is breaking by design: any link minted before this deploy is unusable
after it, which is acceptable since links are short-TTL and there's no production traffic yet. The
redundant post-lookup `linkId`/`expiresAt` cross-checks in `validate()` are intentional
defense-in-depth, not leftover dead code.

## References

- [0003 — HMAC-signed, single-use, time-limited download links](0003-hmac-signed-single-use-download-links.md)
- [0004 — AES-GCM encryption at rest](0004-aes-gcm-encryption-at-rest.md)
- [0011 — Adopt feature-first hexagonal packaging](0011-adopt-feature-first-hexagonal-packaging.md)
- [0014 — Local disk storage to Floci/S3 migration](0014-local-storage-to-floci-s3-migration.md)
- [0020 — Signed-link abuse hardening](0020-signed-link-abuse-hardening.md) (amends decision 1 with a redemption cap)
