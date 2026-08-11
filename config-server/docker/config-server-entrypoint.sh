#!/usr/bin/env sh
set -eu

if [ -n "${BASH_VERSION:-}" ]; then
  set -o pipefail
fi

ROLE_ID_FILE="${APPROLE_ROLE_ID_FILE:-/vault/init/approle-role-id}"
SECRET_ID_FILE="${APPROLE_SECRET_ID_FILE:-/vault/init/approle-secret-id}"
TOKEN_FILE="${CONFIG_SERVER_TOKEN_FILE:-/vault/init/config-server-token}"
VAULT_ADDR="${VAULT_ADDR:-http://vault:8200}"

login_with_approle() {
  ROLE_ID=$(cat "$ROLE_ID_FILE")
  SECRET_ID=$(cat "$SECRET_ID_FILE")
  i=0
  until [ $i -ge 20 ]; do
    TOKEN_JSON=$(curl -s --fail -X POST "${VAULT_ADDR}/v1/auth/approle/login" \
      -H "Content-Type: application/json" \
      -d "{\"role_id\":\"${ROLE_ID}\",\"secret_id\":\"${SECRET_ID}\"}") && break
    i=$((i+1)); echo "Waiting for Vault (attempt $i)..." ; sleep 3
  done
  [ -n "$TOKEN_JSON" ] || return 1
  VAULT_TOKEN=$(echo "$TOKEN_JSON" | jq -r '.auth.client_token')
  [ -n "$VAULT_TOKEN" ] && export VAULT_TOKEN && return 0
  return 1
}

if [ -f "$ROLE_ID_FILE" ] && [ -f "$SECRET_ID_FILE" ]; then
  if login_with_approle; then
    echo "AppRole login successful."
    export SPRING_CLOUD_CONFIG_SERVER_VAULT_TOKEN="$VAULT_TOKEN"
    export SPRING_CLOUD_VAULT_TOKEN="$VAULT_TOKEN"
  else
    echo "AppRole login failed after retries."
  fi
fi

if [ -z "${VAULT_TOKEN:-}" ] && [ -f "$TOKEN_FILE" ]; then
  export VAULT_TOKEN=$(cat "$TOKEN_FILE")
  export SPRING_CLOUD_CONFIG_SERVER_VAULT_TOKEN="$VAULT_TOKEN"
  export SPRING_CLOUD_VAULT_TOKEN="$VAULT_TOKEN"
  echo "Using fallback token file at $TOKEN_FILE"
fi

exec "$@"
