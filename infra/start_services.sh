#!/usr/bin/env bash
set -euo pipefail

# start_services.sh - wait for Vault readiness then start DB/Keycloak/ConfigServer
if [ ! -f docker-compose.yml ]; then
  echo "ERROR: docker-compose.yml not found. Run from infra/."
  exit 1
fi

info(){ echo "INFO: $*"; }
die(){ echo "ERROR: $*"; exit 1; }

# Wait for vault container to be present
if ! docker ps --format '{{.Names}}' | grep -q '^vault$'; then
  die "vault container not running. Run ./bootstrap_vault.sh first."
fi

# Wait until Vault initialized & unsealed
info "Waiting for Vault to be initialized and unsealed..."
i=0
RETRY_LIMIT=120
RETRY_SLEEP=2
until docker exec -i vault vault status -format=json 2>/dev/null | jq -e '.initialized == true and .sealed == false' >/dev/null 2>&1 || [ $i -ge $RETRY_LIMIT ]; do
  i=$((i+1)); echo "  vault not ready (attempt $i)"; sleep $RETRY_SLEEP
done
if [ $i -ge $RETRY_LIMIT ]; then
  docker logs vault --tail 200 || true
  die "Vault did not become ready in time."
fi
info "Vault ready."

# Start remaining services (db/keycloak/config-server)
info "Starting db, keycloak, config-server, statement-service..."
docker compose up -d db keycloak config-server statement-service

