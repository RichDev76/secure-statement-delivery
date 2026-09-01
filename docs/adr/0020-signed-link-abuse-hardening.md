# 0020 — Signed-link abuse hardening

## Context

[0015](0015-time-based-signed-links-and-envelope-encryption.md) made signed download links purely
time-based: the same link is redeemable any number of times until `expiresAt`. That removed the
only thing bounding what a leaked link is worth — a leaked, still-valid link is now
indistinguishable from the legitimate holder, redeemable as many times as presented, with no
detection signal and no rate control.

## Problem

Walking the actual resource cost of one redeemed download (HMAC verify: microseconds; DB lookups:
~1-3ms; S3/Floci fetch: tens-hundreds of ms, the dominant cost; client egress: dominant dollar
cost) settled two things: total exposure from one leaked link is bounded by how many times it's
redeemed, not how fast — so a rate limiter alone doesn't reduce exposure, only smooths it; and
`Referrer-Policy` closes exactly one of several leak pathways (Referer header), not the others
(copy-paste, browser history, infra logs).

## Decision

1. **Bounded redemption count is the primary control**, not one of several equal measures. A new
   `redemption_count` column plus an atomic conditional `UPDATE` in `SignedLinkRepository`,
   `maxRedemptions = 3` — tight because it now doubles as the resource-cost ceiling per leaked
   link, not just retry-tolerance. Exhausted redemptions return the *existing* expired-link result,
   not a distinguishable one.
2. **Per-link rate limiting is Postgres-backed (`bucket4j_jdk17-postgresql`), not Redis.**
   `validate()` and the redemption-count update already touch Postgres on every request, so the
   marginal cost of also checking the bucket there is small; horizontal scaling rules out
   in-process state, not Postgres. Bucket state lives in `signed_link_rate_limit_buckets`, a
   dedicated table (Bucket4j's primary-key column is `text`, `signed_links.id` is `uuid` —
   matching types are required for an FK, so no FK/cascade is possible); stale rows are swept by
   the existing `SignedLinkCleanupService` rather than a new scheduled job.
3. **Ciphertext caching is Redis-backed, and that's a different justification than the one
   rejected for the rate limiter.** With `maxRedemptions` allowing up to 3 identical S3 GETs of the
   same object for one link, that's genuine, bounded reuse. `EncryptedFileFetcher` (a port in
   `statement`, implemented by `CachingEncryptedFileFetcher` in `infrastructure.cache`) caches
   only ciphertext, TTL-bound to the link expiry; DEK unwrap and decryption still run on every
   call, preserving 0015's plaintext-exposure boundary exactly.
4. **Anomaly logging is detection-only.** Before recording `DOWNLOAD_SUCCESS`, check for a prior
   successful redemption of the same link from a different `ip`/`userAgent` and log at `WARN` —
   reads audit data this codebase already collects and never queried, closing the "no signal" gap
   without new storage.

## Alternatives

- Concurrency bulkhead on the download path: real value (per-link controls can't see a coordinated
  actor spread across many leaked links exhausting shared pools), but a general capacity-planning
  concern independent of whether any link leaks — deferred, not part of a take-home's assigned
  scope.
- Explicit revocation: needs an admin/authorization model this project doesn't have.
- IP/CIDR or session binding: real false-positive risk for mobile/NAT clients for comparatively
  little marginal benefit once redemption count and rate limiting exist.
- Infra-layer log/CDN controls: outside this codebase by definition.

## Consequences

- Two new pieces of infrastructure this project didn't previously run: Postgres gains a second
  application table with no ORM mapping (Bucket4j owns it directly), and Redis is introduced
  solely for ciphertext caching.
- `signed_link_rate_limit_buckets` has no FK to `signed_links` — cleanup is a companion query in
  `SignedLinkCleanupService`, not a database-enforced cascade; a future primary-key mapper change
  would need to re-verify this.
- The token-in-URL transport itself is unchanged and deliberately not addressed here — the
  industry-standard trade-off for a bare, one-click link (matching S3/GCS/Azure SAS presigned
  URLs); these measures bound its consequences rather than removing the vector.

## Addendum — Limiter failure posture

The limiter originally failed open on any error, silently removing the only abuse control on an
unauthenticated endpoint exactly when the system was degraded. It now fails closed (429 +
`Retry-After`): the limiter is Postgres-backed, so a limiter outage implies link validation would
fail moments later anyway — almost no legitimate traffic is sacrificed, while outage-window
signature-guessing floods are throttled to zero. Alternative (configurable posture) rejected:
one more knob for a decision with a clear right answer here.

## References

- [0008 — Scheduled signed-link cleanup with ShedLock](0008-scheduled-cleanup-shedlock.md)
- [0015 — Time-based signed links and envelope encryption](0015-time-based-signed-links-and-envelope-encryption.md)
- [OWASP API Security Top 10 — API2:2023 / API6:2023](https://owasp.org/API-Security/editions/2023/en/0xa2-broken-authentication/)
