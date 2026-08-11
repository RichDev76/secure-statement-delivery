#!/usr/bin/env bash
set -euo pipefail

# Output file
INIT_FILE="vault_init.json"

# Only initialize if INIT_FILE does not exist
if [ -f "$INIT_FILE" ]; then
  echo "Vault already initialized (vault_init.json found)."
  exit 0
fi

echo "Initializing Vault..."
docker compose exec -T vault vault operator init \
  -key-shares=1 \
  -key-threshold=1 \
  -format=json \
  > "$INIT_FILE"

chmod 600 "$INIT_FILE"

UNSEAL_KEY=$(jq -r '.unseal_keys_b64[0]' "$INIT_FILE")
ROOT_TOKEN=$(jq -r '.root_token' "$INIT_FILE")

echo "Unsealing Vault..."
docker compose exec -T vault vault operator unseal "$UNSEAL_KEY"

echo "Logging in..."
docker compose exec -T vault vault login "$ROOT_TOKEN"

echo "Vault initialized and unsealed."
echo "Unseal Key and Root Token saved to: $INIT_FILE"
