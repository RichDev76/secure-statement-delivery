-- idx_statements_account_number (account_number) is a strict prefix of the existing unique
-- idx_statements_account_date (account_number, statement_date) index, so the planner can never
-- prefer it over the unique index for an account_number-only lookup. Pure write/storage overhead.
--
-- idx_statements_account_date_uploaded and idx_statements_uploaded_at are NOT dropped here:
-- StatementQueryService.searchPaged supports client-specified sort by uploadedAt (among other
-- columns), a real, used code path — dropping either without pg_stat_user_indexes evidence from
-- a real environment would be speculative, not evidence-led.
DROP INDEX IF EXISTS idx_statements_account_number;
