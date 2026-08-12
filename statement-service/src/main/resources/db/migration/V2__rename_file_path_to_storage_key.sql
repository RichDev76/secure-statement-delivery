-- The statements.file_path column historically held an absolute local filesystem path.
-- With storage moved to S3 (Floci in non-prod, real S3 in deployed environments), it now
-- holds an S3 object key. Renamed to storage_key to avoid a column name that misleads the
-- next reader about what the value actually represents. See ADR 0026.
ALTER TABLE statements RENAME COLUMN file_path TO storage_key;
