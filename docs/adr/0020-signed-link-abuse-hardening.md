# 0020 — Signed-link abuse hardening

## Context

[0015](0015-time-based-signed-links-and-envelope-encryption.md) made signed download links purely
time-based: the same link is redeemable any number of times until `expiresAt`. That removed the
only thing bounding what a leaked link is worth — a leaked, still-valid link is now
indistinguishable from the legitimate holder, redeemable as many times as it's presented, with no
detection signal and no rate control.

## Problem

Walking the actual resource cost of one redeemed download — HMAC verify at microseconds, DB
lookups at roughly 1-3ms, S3/Floci fetch at tens to hundreds of ms as the dominant cost, and client
egress as the dominant dollar cost — settled two things. Total exposure from one leaked link is
bounded by how many times it's redeemed, not how fast, so a rate limiter alone doesn't reduce
exposure, only smooths it. And `Referrer-Policy` closes exactly one of several leak pathways (the
Referer header), not the others — copy-paste, browser history, infra logs.

## Decision

Bounded redemption count is the primary control here, not just one of several equal measures. A
new `redemption_count` column plus an atomic conditional `UPDATE` in `SignedLinkRepository` caps
`maxRedemptions` at 3 — tight, because it now doubles as the resource-cost ceiling per leaked link,
not just retry-tolerance. Exhausted redemptions return the existing expired-link result, not a
distinguishable one.

Per-link rate limiting is Postgres-backed (`bucket4j_jdk17-postgresql`), not Redis. `validate()`
and the redemption-count update already touch Postgres on every request, so the marginal cost of
also checking the bucket there is small, and horizontal scaling rules out in-process state, not
Postgres. Bucket state lives in `signed_link_rate_limit_buckets`, a dedicated table — Bucket4j's
primary-key column is `text` while `signed_links.id` is `uuid`, and matching types are required for
an FK, so no FK/cascade is possible here. Stale rows get swept by the existing
`SignedLinkCleanupService` rather than a new scheduled job.

Ciphertext caching, on the other hand, is Redis-backed, and that's a different justification than
the one we rejected for the rate limiter. With `maxRedemptions` allowing up to 3 identical S3 GETs
of the same object for one link, that's genuine, bounded reuse. `EncryptedFileFetcher` — a port in
`statement`, implemented by `CachingEncryptedFileFetcher` in `infrastructure.cache` — caches only
ciphertext, TTL-bound to the link expiry; DEK unwrap and decryption still run on every call, so
0015's plaintext-exposure boundary is preserved exactly.

Anomaly logging is detection-only. Before recording `DOWNLOAD_SUCCESS`, we check for a prior
successful redemption of the same link from a different `ip`/`userAgent` and log at `WARN` — this
reads audit data the codebase already collects and never queried, closing the "no signal" gap
without any new storage.

## Alternatives

A concurrency bulkhead on the download path has real value — per-link controls can't see a
coordinated actor spread across many leaked links exhausting shared pools — but it's a general
capacity-planning concern independent of whether any link leaks, so we deferred it as outside a
take-home's assigned scope. Explicit revocation needs an admin/authorization model this project
doesn't have. IP/CIDR or session binding carries real false-positive risk for mobile/NAT clients
for comparatively little marginal benefit once redemption count and rate limiting already exist.
And infra-layer log/CDN controls are outside this codebase by definition.

## Consequences

There are two new pieces of infrastructure this project didn't previously run: Postgres gains a
second application table with no ORM mapping (Bucket4j owns it directly), and Redis is introduced
solely for ciphertext caching. `signed_link_rate_limit_buckets` has no FK to `signed_links` —
cleanup is a companion query in `SignedLinkCleanupService`, not a database-enforced cascade, and a
future primary-key mapper change would need to re-verify this. The token-in-URL transport itself is
unchanged and deliberately not addressed here — it's the industry-standard trade-off for a bare,
one-click link, matching S3/GCS/Azure SAS presigned URLs; these measures bound its consequences
rather than removing the vector.

## Addendum — Limiter failure posture

The limiter originally failed open on any error, which silently removed the only abuse control on
an unauthenticated endpoint exactly when the system was degraded. It now fails closed (429 +
`Retry-After`): the limiter is Postgres-backed, so a limiter outage implies link validation would
fail moments later anyway — almost no legitimate traffic is sacrificed, while outage-window
signature-guessing floods get throttled to zero. We rejected a configurable posture as an
alternative — one more knob for a decision that already has a clear right answer.

## References

- [0008 — Scheduled signed-link cleanup with ShedLock](0008-scheduled-cleanup-shedlock.md)
- [0015 — Time-based signed links and envelope encryption](0015-time-based-signed-links-and-envelope-encryption.md)
- [OWASP API Security Top 10 — API2:2023 / API6:2023](https://owasp.org/API-Security/editions/2023/en/0xa2-broken-authentication/)
