-- IDs are always assigned by the application (UUIDv7 via IdGeneratorPort/UuidV7IdGenerator).
-- Defaults are dropped rather than replaced with a v7-generating default: every insert path in
-- this codebase already sets the id explicitly before persist (SignedLink even needs it before
-- insert, since it's embedded in the HMAC signature), so a server-side default is unreachable in
-- practice and only risks silently minting a v4 id if anything ever bypassed application code.
ALTER TABLE statements ALTER COLUMN id DROP DEFAULT;
ALTER TABLE signed_links ALTER COLUMN id DROP DEFAULT;
ALTER TABLE audit_logs ALTER COLUMN id DROP DEFAULT;
