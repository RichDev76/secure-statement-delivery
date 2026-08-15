-- Hard cutover: assumes an empty/reset statements table.
-- ADR 0004 envelope encryption: each file gets a random per-file DEK; the DEK itself is
-- wrapped under the Master Encryption Key and persisted here — the raw DEK is never stored.
ALTER TABLE statements ADD COLUMN encrypted_dek bytea NOT NULL;
