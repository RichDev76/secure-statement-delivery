# 0020 — Signed-link abuse hardening

## Context

[0015](0015-time-based-signed-links-and-envelope-encryption.md) made signed download links purely
time-based: the same link is redeemable any number of times until `expiresAt`. That removed the
only thing bounding what a leaked link is worth — a leaked, still-valid link is now
indistinguishable from the legitimate holder, redeemable as many times as it's presented, with no
detection signal and no rate control.

## Problem

We looked at what one redeemed download actually costs: HMAC verification is microseconds, the DB
lookup is 1-3ms, and the S3/Floci fetch — tens to hundreds of ms — is the dominant time cost, while
client egress dominates the dollar cost. That settled two things. Total exposure from a leaked link
depends on how many times it gets redeemed, not how fast, so a rate limiter alone doesn't reduce
that exposure — it just smooths it out over time. And `Referrer-Policy` only closes one of several
leak pathways (the Referer header); copy-paste, browser history, and infra logs are still open.

## Decision

Bounded redemption count is now the main control, rather than one of several equal measures. A new
`redemption_count` column plus an atomic conditional `UPDATE` in `SignedLinkRepository` caps
`maxRedemptions` at 3. That's tight on purpose: it now doubles as the resource-cost ceiling per
leaked link, not just retry-tolerance. Exhausted redemptions return the same result as an expired
link, so the two are indistinguishable to the caller.

Per-link rate limiting runs on Postgres (`bucket4j_jdk17-postgresql`) rather than Redis.
`validate()` and the redemption-count update already hit Postgres on every request, so checking the
bucket there too adds little extra cost. In-process state was ruled out because we scale
horizontally — Postgres wasn't. Bucket state lives in `signed_link_rate_limit_buckets`, a dedicated
table: Bucket4j's primary-key column is `text` while `signed_links.id` is `uuid`, and an FK needs
matching types, so no FK or cascade is possible here. Stale rows get swept by the existing
`SignedLinkCleanupService` instead of a new scheduled job.

Ciphertext caching does use Redis, though, for a different reason than the rate limiter's Postgres
choice. `maxRedemptions` allows up to 3 identical S3 GETs of the same object for one link, and
that's genuine, bounded reuse worth caching. `EncryptedFileFetcher` — a port in `statement`,
implemented by `CachingEncryptedFileFetcher` in `infrastructure.cache` — caches only ciphertext,
TTL-bound to the link expiry. DEK unwrap and decryption still run on every call, so 0015's
plaintext-exposure boundary is preserved exactly.

Anomaly logging is detection-only. Before recording `DOWNLOAD_SUCCESS`, we check for a prior
successful redemption of the same link from a different `ip`/`userAgent` and log at `WARN`. This
just reads audit data the codebase already collected but never queried, so it closes the no-signal
gap without any new storage.

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
solely for ciphertext caching. `signed_link_rate_limit_buckets` has no FK to `signed_links`;
cleanup runs as a companion query in `SignedLinkCleanupService` instead of a database-enforced
cascade, and a future primary-key mapper change would need to re-verify this. The token-in-URL
transport itself is unchanged and deliberately out of scope here — it's the industry-standard
trade-off for a bare, one-click link, matching S3/GCS/Azure SAS presigned URLs. These measures
bound its consequences; they don't remove the vector.

## References

- [0008 — Scheduled signed-link cleanup with ShedLock](0008-scheduled-cleanup-shedlock.md)
- [0015 — Time-based signed links and envelope encryption](0015-time-based-signed-links-and-envelope-encryption.md)
- [OWASP API Security Top 10 — API2:2023 / API6:2023](https://owasp.org/API-Security/editions/2023/en/0xa2-broken-authentication/)

## Addendum — Limiter failure posture

The limiter originally failed open on any error, which silently removed the only abuse control on
an unauthenticated endpoint exactly when the system was degraded. It now fails closed (429 +
`Retry-After`): the limiter is Postgres-backed, so a limiter outage implies link validation would
fail moments later anyway — almost no legitimate traffic is sacrificed, while outage-window
signature-guessing floods get throttled to zero. We rejected a configurable posture as an
alternative — one more knob for a decision that already has a clear right answer.
