-- Owned directly by Bucket4j's PostgreSQL SelectForUpdateBasedProxyManager (raw SQL, not JPA) -
-- column shape is its own documented "with expiration" schema, not a domain table. No FK to
-- signed_links: Bucket4j's key mapper stores linkId as text, and Postgres foreign keys require
-- matching column types against signed_links.id (uuid). Stale rows are swept by
-- SignedLinkCleanupService.cleanup() instead (Bucket4jSignedLinkRateLimiter#deleteExpiredBuckets).
CREATE TABLE signed_link_rate_limit_buckets
(
    id         text        PRIMARY KEY,
    state      bytea       NOT NULL,
    expires_at bigint
);
