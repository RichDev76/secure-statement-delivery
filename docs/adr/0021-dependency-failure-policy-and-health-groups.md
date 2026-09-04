# 0021 — Dependency failure policy and health groups

## Context

Dependency failures had no consistent policy. A Redis outage propagated an unchecked
`RedisConnectionFailureException` through the `@Cacheable` proxy and was misreported as a
decryption failure (500). A non-`NoSuchKey` S3 failure in `exists()` leaked the raw SDK exception
as an unaudited generic 500. Neither matched existing precedent — audit writes already fail open
([0009](0009-asynchronous-audit-logging.md)), and S3 outages already have to surface loudly rather
than masquerade as "file missing" ([0014](0014-local-storage-to-floci-s3-migration.md)). Separately,
the Docker healthcheck polled overall `/actuator/health`, which folds in Redis — a cache outage
would flip readiness and, with `restart: unless-stopped`, restart-loop a container that could still
serve every request from S3.

## Problem

Per dependency: does a failure degrade silently, fail loud with a classified response, or fail
loud generically — and which dependencies gate container health/readiness?

## Decision

Redis fails open. `RedisCacheConfig` implements `CachingConfigurer` and registers Spring
Framework's built-in `LoggingCacheErrorHandler` (WARN, never rethrows) in place of the default
`SimpleCacheErrorHandler`, which rethrows. A cache failure is now treated as a miss, and the
request falls through to `StatementFileStore`.

S3 fails loud but classified. `exists()` now throws `StatementStorageUnavailableException` instead
of leaking `SdkException`. `DownloadService` maps it to a new `DownloadOutcome.STORAGE_UNAVAILABLE`
→ `503` + `Retry-After`, `errorCode: STORAGE_UNAVAILABLE` — additive to the OpenAPI contract, the
same shape as the existing `RATE_LIMITED` addition. This keeps a genuine crypto failure
(`DECRYPTION_FAILED`, 500) distinguishable from an infrastructure outage (503, retryable).

The suspicious-redemption check fails open too — it's detection-only
([0020](0020-signed-link-abuse-hardening.md)), and its own failure must never turn a successful
download into a reported one.

Health groups now split by what gates traffic: `readiness` is `readinessState, db, s3`; `liveness`
is `livenessState` only. Redis sits in neither — it's still visible in overall
`/actuator/health` for operators, but no longer affects readiness. The Docker healthcheck now polls
`/actuator/health/liveness`.

Postgres and JWK/Keycloak are unchanged — hard dependencies, no degraded mode, fail loud, same as
always.

## Alternatives

Resilience4j (circuit breakers/retries on S3) is disproportionate to this project's scope, so we
deferred it. A Redis circuit breaker wasn't worth adding — `LoggingCacheErrorHandler` already gives
the same degrade. We wrote a hand-rolled `CacheErrorHandler` first, then replaced it once we found
Spring's built-in equivalent — no reason to maintain a duplicate.

## Consequences

A Redis outage now costs latency and egress rather than availability, visible via WARN logs, though
there's no dedicated metric for it yet. `STORAGE_UNAVAILABLE` is additive, so existing clients are
unaffected. Redis outages no longer show up in readiness/liveness, so operators have to watch
overall health or logs to catch them. No retries or breakers were added here, but explicit
transport timeouts were: S3 (`S3ClientConfig` — apiCall 20s, socket 10s, connection 2s, acquisition
5s) and Redis (`timeout: 2s`, `connect-timeout: 1s`), so a stalled call is now bounded by pinned
values rather than SDK defaults.

## Implementation Notes

`RedisCacheConfig.errorHandler()`; `S3StatementFileStore.exists()`. New:
`StatementStorageUnavailableException`, `DownloadStorageUnavailableException`.
`DownloadOutcome`/`DownloadFailureReason`/`DownloadService`/`DownloadResponseFactory`/`DownloadExceptionHandler`.
OpenAPI gains the `STORAGE_UNAVAILABLE` enum value and its `503` response.
`application.yml`: `management.endpoint.health.group.{readiness,liveness}`.
`infra/docker-compose.yml`: healthcheck target now points at `/actuator/health/liveness`.

## References

- [0009 — Asynchronous, fail-open audit logging](0009-asynchronous-audit-logging.md)
- [0014 — Local disk storage to Floci/S3 migration](0014-local-storage-to-floci-s3-migration.md)
- [0020 — Signed-link abuse hardening](0020-signed-link-abuse-hardening.md)
