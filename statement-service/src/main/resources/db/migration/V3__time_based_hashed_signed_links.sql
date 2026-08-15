-- Hard cutover: assumes an empty/reset signed_links table (pre-production, no data to
-- preserve). Single-use links (ADR 0003 baseline) are replaced by purely time-based
-- validity (ADR 0006); the raw token is replaced by a SHA-256 hash at rest (ADR 0021).
ALTER TABLE signed_links
    DROP COLUMN single_use,
    DROP COLUMN used,
    ADD COLUMN token_hash varchar(64) NOT NULL;

DROP INDEX IF EXISTS idx_signed_links_token;
DROP INDEX IF EXISTS idx_signed_links_used;
ALTER TABLE signed_links DROP COLUMN token;

CREATE UNIQUE INDEX idx_signed_links_token_hash ON signed_links (token_hash);
