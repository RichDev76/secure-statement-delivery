#!/usr/bin/env bash
set -euo pipefail

# bootstrap_vault.sh - orchestrates safe vault bootstrap by calling new_env.sh
# Run from infra/

if [ ! -f docker-compose.yml ]; then
  echo "ERROR: docker-compose.yml not found. Run this from infra/."
  exit 1
fi

# Ensure host port 8200 not bound by non-docker process (new_env.sh will auto-stop docker container owner)
if ss -ltnp 2>/dev/null | grep -q ':8200'; then
  DOCKER_LINE=$(docker ps --format '{{.ID}} {{.Names}} {{.Ports}}' | grep -E '8200->8200|8200:8200' || true)
  if [ -n "$DOCKER_LINE" ]; then
    echo "bootstrap_vault.sh: Found container exposing 8200; will remove it before starting our Vault."
    echo "$DOCKER_LINE"
    CID=$(echo "$DOCKER_LINE" | awk '{print $1}')
    docker stop "$CID" || true
    docker rm -f "$CID" || true
  else
    echo "bootstrap_vault.sh: Port 8200 is currently used by a non-docker process. Please free it or re-run with different mapping. Aborting."
    exit 1
  fi
fi

# Delegate to new_env.sh which contains the robust logic
./new_env.sh
echo "bootstrap_vault.sh: success."
exit 0
