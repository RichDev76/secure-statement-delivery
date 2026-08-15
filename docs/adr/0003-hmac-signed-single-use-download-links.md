# 0003 — HMAC-signed, single-use, time-limited download links

> **Superseded by [0015](0015-time-based-signed-links-and-envelope-encryption.md).** Single-use
> consumption and raw-token storage described below were later replaced by purely time-based
> validity and hashed tokens at rest.

## Context

Statements must not be reachable via a plain, guessable URL — access must be controlled,
time-limited, and independently auditable.

## Problem

A caller needs a way to hand a statement download link to a downstream system or customer without
exposing the storage location directly or requiring that party to authenticate with Keycloak.

## Decision

`SignedLinkService` issues a `SignedLink` per download: `id`, `statementId`, an HMAC-SHA256
`token` computed over `{method}|{path}|{expires}` with a shared secret, `expiresAt` (default 900s
from creation), `singleUse`/`used` flags, `createdAt`, `createdBy`. The download endpoint
(`GET /download/{fileName}?expires=...&signature=...`) is whitelisted (no JWT) — the signature and
expiry are the only access control. `DownloadService` validates the token, checks expiry, and
marks single-use links `used` on success.

## Alternatives

- JWT-based links: heavier payload, requires JWT validation on the download path.
- Opaque DB tokens with no signature: a DB lookup is required for every attempt with no
  fast-fail on tampering.

## Consequences

- Fast validation (signature format is self-describing); DB lookup still required to check
  used/expired state.
- Single-use semantics reject a retried download after a network failure.
- The raw token stored in `signed_links.token` is directly reusable by anyone who can read it.

## Implementation Notes

`SignatureUtil` (HMAC-SHA256, Base64 URL-safe encoding) signs; `SignedLinkService` manages the
link lifecycle; `SignedLinkRepository` persists.

## References

- `docs/standards/security.md`
- [0015 — Time-based signed links and envelope encryption](0015-time-based-signed-links-and-envelope-encryption.md)
