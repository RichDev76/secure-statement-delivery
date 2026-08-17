#!/usr/bin/env bash
set -euo pipefail

# Orchestrates the one scenario Bruno's script sandbox cannot self-automate (it has no docker/shell
# access): stopping the S3-compatible object store mid-download to prove the real 503 STORAGE_UNAVAILABLE
# path, then running "Download during S3 outage (manual).bru" against that real outage.
#
# Requires: the full docker-compose stack already up (infra/docker-compose.yml), and the Bruno CLI (`bru`).
# Run from anywhere; paths below are resolved relative to this script's location.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
INFRA_DIR="$REPO_ROOT/infra"

# shellcheck source=/dev/null
source "$INFRA_DIR/.env"

cd "$SCRIPT_DIR"
echo "Authenticating, uploading a fresh statement, and minting a signed link while floci is healthy..."
bru run \
  --env local \
  --env-var "ADMIN_CLIENT_SECRET=$KEYCLOAK_ADMIN_CLIENT_SECRET" \
  --env-var "CONSUMER_CLIENT_SECRET=$KEYCLOAK_CONSUMER_CLIENT_SECRET" \
  "Auth/Get Admin Token.bru" \
  "Statement-Service/upload/Upload PDF statement.bru" \
  "Statement-Service/{statementId}/Get signed download link by statement id.bru"

cd "$INFRA_DIR"
echo "Stopping floci to simulate an S3 outage..."
docker compose stop floci

restore_floci() {
  cd "$INFRA_DIR"
  echo "Restoring floci..."
  docker compose start floci
  for _ in $(seq 1 20); do
    status="$(docker compose ps floci --format '{{.Status}}')"
    if [[ "$status" == *healthy* ]]; then
      echo "floci is healthy again."
      return
    fi
    sleep 2
  done
  echo "WARNING: floci did not report healthy within 40s - check it manually." >&2
}
trap restore_floci EXIT

cd "$SCRIPT_DIR"
bru run \
  --env local \
  --env-var "ADMIN_CLIENT_SECRET=$KEYCLOAK_ADMIN_CLIENT_SECRET" \
  --env-var "CONSUMER_CLIENT_SECRET=$KEYCLOAK_CONSUMER_CLIENT_SECRET" \
  "Statement-Service/download/{fileName}/Download during S3 outage (manual).bru"
