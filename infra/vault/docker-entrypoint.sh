#!/usr/bin/env sh
set -euo pipefail

# docker-entrypoint.sh - render vault.hcl.tpl -> vault.hcl then exec vault server
# expects vault.hcl.tpl to be mounted at /vault/config/vault.hcl.tpl

TEMPLATE="/vault/config/vault.hcl.tpl"
TARGET="/vault/config/vault.hcl"
INIT_DIR="/vault/init"

# ensure init dir exists
mkdir -p "${INIT_DIR}"
chmod 700 "${INIT_DIR}"

# Render template if present
if [ -f "${TEMPLATE}" ]; then
  echo "[entrypoint] Rendering ${TEMPLATE} -> ${TARGET}"
  envsubst < "${TEMPLATE}" > "${TARGET}"
  chmod 644 "${TARGET}"
  echo "---- rendered /vault/config/vault.hcl ----"
  sed -n '1,160p' "${TARGET}" || true
  echo "---- end rendered ----"
else
  echo "[entrypoint] No template ${TEMPLATE} found - expecting /vault/config/vault.hcl directly"
fi

# ensure Vault binary present
if ! command -v vault >/dev/null 2>&1; then
  echo "[entrypoint] vault binary not found in image - aborting"
  exit 1
fi

# If command passed is 'server -config=...' then run it, and attempt a
# best-effort auto-unseal that does NOT replace new_env.sh as the
# primary initializer/unsealer.

VAULT_ADDR="${VAULT_ADDR:-http://127.0.0.1:8200}"
UNSEAL_KEY_FILE="${INIT_DIR}/vault-unseal-key"

echo "[entrypoint] Starting vault server in background with: $*"
vault "$@" &
VAULT_PID=$!

# Wait for Vault HTTP endpoint to come up (best-effort, non-fatal)
MAX_WAIT=60
SLEEP=2
i=0

while [ "$i" -lt "$MAX_WAIT" ]; do
  i=$((i+1))
  # We only care that the health endpoint responds at all; status code can be 200/429/501/503
  HTTP_CODE=$(curl -sS -m 3 -o /dev/null -w '%{http_code}' "${VAULT_ADDR%/}/v1/sys/health" 2>/dev/null || echo "")
  case "$HTTP_CODE" in
    200|429|501|503)
      echo "[entrypoint] Vault HTTP endpoint is up (status $HTTP_CODE)."
      break
      ;;
    "")
      echo "[entrypoint] Vault HTTP endpoint not responding yet (attempt $i/$MAX_WAIT)..."
      ;;
    *)
      echo "[entrypoint] Vault HTTP endpoint returned $HTTP_CODE (attempt $i/$MAX_WAIT)..."
      ;;
  esac
  sleep "$SLEEP"
done

# Best-effort auto-unseal: only if key file exists and Vault reports 'sealed: true'.
if [ -r "$UNSEAL_KEY_FILE" ]; then
  echo "[entrypoint] Found unseal key file at $UNSEAL_KEY_FILE; checking if Vault is sealed..."

  # Get textual status; avoid extra dependencies like jq.
  STATUS_OUTPUT=$(VAULT_ADDR="$VAULT_ADDR" vault status 2>/dev/null || true)

  echo "$STATUS_OUTPUT" | grep -Ei '^Sealed[[:space:]]*:?[[:space:]]*true' 2>/dev/null || SEALED_FALSE=$?

  if echo "$STATUS_OUTPUT" | grep -Ei '^Sealed[[:space:]]*:?[[:space:]]*true' 2>/dev/null; then
    UNSEAL_KEY=$(tr -d '\r\n' < "$UNSEAL_KEY_FILE")
    echo "[entrypoint] Vault is sealed; attempting auto-unseal using $UNSEAL_KEY_FILE"

    if ! VAULT_ADDR="$VAULT_ADDR" vault operator unseal "$UNSEAL_KEY" 2>&1; then
      echo "[entrypoint] WARNING: auto-unseal attempt failed (non-fatal). Vault may remain sealed."
    else
      # Re-check status to confirm (best-effort)
      STATUS_OUTPUT=$(VAULT_ADDR="$VAULT_ADDR" vault status 2>/dev/null || true)
      if echo "$STATUS_OUTPUT" | grep -Ei '^Sealed[[:space:]]*:?[[:space:]]*false' 2>/dev/null; then
        echo "[entrypoint] Vault successfully auto-unsealed."
      else
        echo "[entrypoint] WARNING: auto-unseal command ran but Vault still reports sealed."
      fi
    fi
  else
    echo "[entrypoint] Vault does not appear to be sealed; skipping auto-unseal."
  fi
else
  echo "[entrypoint] No readable unseal key file at $UNSEAL_KEY_FILE; skipping auto-unseal."
fi

# Keep PID 1 running by waiting on the Vault server process
wait "$VAULT_PID"
