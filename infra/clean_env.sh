#!/usr/bin/env bash
set -euo pipefail

# clean_env.sh - remove compose services, named volumes, and local vault init artifacts

if [ ! -f docker-compose.yml ]; then
  echo "ERROR: docker-compose.yml not found. Run this from infra/."
  exit 1
fi

echo "clean_env.sh: stopping compose and removing volumes/clean artifacts..."

# Stop compose services and remove anonymous volumes
docker compose down -v || true

# Stop and remove any container using the host port 8200 (common local dev case)
# This prevents bind-failures when starting Vault later.
CONTAINER_8200=$(docker ps --format '{{.ID}} {{.Names}} {{.Ports}}' | grep -E '8200->8200|8200:8200' || true)
if [ -n "$CONTAINER_8200" ]; then
  echo "clean_env.sh: stopping container that publishes 8200:"
  echo "$CONTAINER_8200"
  CID=$(echo "$CONTAINER_8200" | awk '{print $1}')
  docker stop "$CID" || true
  docker rm -f "$CID" || true
fi

# remove common named volumes (ignore errors)
docker volume rm vault-data db-data keycloak-data || true

# remove local init and recreate
rm -rf ./vault/init
mkdir -p ./vault/init
chmod 700 ./vault/init

# prune builder cache and dangling images (optional, safe)
docker builder prune -f || true
docker image prune -f || true

echo "clean_env.sh: done. ./vault/init recreated and named volumes removed."
exit 0


