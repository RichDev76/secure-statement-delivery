# 0021 — Dependency failure policy and health groups

## Context

Dependency failures had no consistent policy. A Redis outage propagated an unchecked
`RedisConnectionFailureException` through the `@Cacheable` proxy and was misreported as a
decryption failure (500). A non-`NoSuchKey` S3 failure in `exists()` leaked the raw SDK exception
as an unaudited generic 500. Neither matched existing precedent - audit writes already fail open
([0009](0009-asynchronous-audit-logging.md)); S3 outages must already surface loudly rather than
masquerade as "file missing" ([0014](0014-local-storage-to-floci-s3-migration.md)). Separately,
the Docker healthcheck polled overall `/actuator/health`, which folds in Redis - a cache outage
would flip readiness and, with `restart: unless-stopped`, restart-loop a container that could
still serve every request from S3.

## Problem

Per dependency: does a failure degrade silently, fail loud with a classified response, or fail
loud generically - and which dependencies gate container health/readiness?

## Decision

1. **Redis fails open.** `RedisCacheConfig` implements `CachingConfigurer` and registers Spring
   Framework's built-in `LoggingCacheErrorHandler` (WARN, never rethrows) in place of the default
   `SimpleCacheErrorHandler`, which rethrows. A cache failure is now treated as a miss - the
   request falls through to `StatementFileStore`.
2. **S3 fails loud but classified.** `exists()` now throws `StatementStorageUnavailableException`
   instead of leaking `SdkException`. `DownloadService` maps it to a new
   `DownloadOutcome.STORAGE_UNAVAILABLE` → `503` + `Retry-After`, `errorCode: STORAGE_UNAVAILABLE`
   (additive to the OpenAPI contract, same shape as the existing `RATE_LIMITED` addition). This
   keeps a genuine crypto failure (`DECRYPTION_FAILED`, 500) distinguishable from an
   infrastructure outage (503, retryable).
3. **The suspicious-redemption check fails open** - detection-only
   ([0020](0020-signed-link-abuse-hardening.md)); its own failure must never turn a successful
   download into a reported one.
4. **Health groups split by what gates traffic.** `readiness` = `readinessState, db, s3`;
   `liveness` = `livenessState` only. Redis is in neither - visible in overall `/actuator/health`
   for operators, but doesn't affect readiness. The Docker healthcheck now polls
   `/actuator/health/liveness`.
5. **Postgres and JWK/Keycloak are unchanged** - hard dependencies, no degraded mode, fail loud.

## Alternatives

- Resilience4j (circuit breakers/retries on S3): deferred - disproportionate to this project's scope.
- Redis circuit breaker: rejected - `LoggingCacheErrorHandler` already gives the same degrade.
- Hand-rolled `CacheErrorHandler`: written first, replaced with Spring's built-in equivalent once
  found - no reason to maintain a duplicate.

## Consequences

- A Redis outage now costs latency/egress, not availability; visible via WARN logs, no dedicated
  metric yet.
- `STORAGE_UNAVAILABLE` is additive; existing clients unaffected.
- Redis outages no longer show in readiness/liveness - operators must watch overall health or logs.
- No retries/breakers added here, but explicit transport timeouts were: S3 (`S3ClientConfig` -
  apiCall 20s, socket 10s, connection 2s, acquisition 5s) and Redis (`timeout: 2s`,
  `connect-timeout: 1s`), so a stalled call is bounded by pinned values, not SDK defaults.

## Implementation Notes

- `RedisCacheConfig.errorHandler()`; `S3StatementFileStore.exists()`.
- New: `StatementStorageUnavailableException`, `DownloadStorageUnavailableException`.
- `DownloadOutcome`/`DownloadFailureReason`/`DownloadService`/`DownloadResponseFactory`/`DownloadExceptionHandler`.
- OpenAPI: `STORAGE_UNAVAILABLE` enum value, `503` response.
- `application.yml`: `management.endpoint.health.group.{readiness,liveness}`.
- `infra/docker-compose.yml`: healthcheck target → `/actuator/health/liveness`.

## References

- [0009](0009-asynchronous-audit-logging.md), [0014](0014-local-storage-to-floci-s3-migration.md),
  [0020](0020-signed-link-abuse-hardening.md)
