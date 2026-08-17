-- Bucket4j's SelectForUpdateBasedProxyManager initializes a bucket by inserting the row with a
-- NULL state and populating it in a second statement under the row lock; its documented schema
-- keeps state nullable. V10's NOT NULL broke that first insert, so every tryConsume threw and
-- the deliberate fail-open in Bucket4jSignedLinkRateLimiter turned rate limiting into a no-op.
ALTER TABLE signed_link_rate_limit_buckets
    ALTER COLUMN state DROP NOT NULL;
