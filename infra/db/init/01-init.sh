#!/usr/bin/env bash
set -euo pipefail

# Use canonical Postgres superuser env names
POSTGRES_SUPERUSER="${POSTGRES_USER:-postgres}"
POSTGRES_SUPERPASS="${POSTGRES_PASSWORD:-}"

: "${APP_DB:?APP_DB is required (set in infra/.env)}"
: "${APP_DB_USER:?APP_DB_USER is required (set in infra/.env)}"
: "${APP_DB_PASSWORD:?APP_DB_PASSWORD is required (set in infra/.env)}"

export PGPASSWORD="${POSTGRES_SUPERPASS}"

# Connect to default 'postgres' DB as superuser
# NOTE: do NOT add literal single quotes inside the command string
PSQL_SUPER="psql --username=${POSTGRES_SUPERUSER} --dbname=postgres -v ON_ERROR_STOP=1 -q"

# 1) Create role if missing
${PSQL_SUPER} <<-SQL
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${APP_DB_USER}') THEN
    CREATE ROLE ${APP_DB_USER} WITH LOGIN PASSWORD '${APP_DB_PASSWORD}';
  END IF;
END
\$\$;
SQL

# 2) Create database if missing
exists=$(${PSQL_SUPER} -tAc "SELECT 1 FROM pg_database WHERE datname='${APP_DB}';")
if [ -z "$exists" ]; then
  ${PSQL_SUPER} -c "CREATE DATABASE ${APP_DB};"
fi

# 3) Ensure DB owner and grants
psql --username="${POSTGRES_SUPERUSER}" --dbname="${APP_DB}" -v ON_ERROR_STOP=1 <<-SQL
ALTER DATABASE ${APP_DB} OWNER TO ${APP_DB_USER};
ALTER SCHEMA public OWNER TO ${APP_DB_USER};
GRANT ALL ON SCHEMA public TO ${APP_DB_USER};
GRANT CREATE ON SCHEMA public TO ${APP_DB_USER};
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO ${APP_DB_USER};
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO ${APP_DB_USER};
SQL

echo "DB init finished for ${APP_DB} / ${APP_DB_USER}"
