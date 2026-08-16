-- Bounds how many times a single signed link can be redeemed. Replaces pure time-based validity
-- (0015) with a hard ceiling on total redemptions, absorbing legitimate retries while capping
-- what a leaked link is worth in total, not just how fast it can be drained.
ALTER TABLE signed_links ADD COLUMN redemption_count integer NOT NULL DEFAULT 0;
