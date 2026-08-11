#!/usr/bin/env bash
set -euo pipefail

# new_env.sh - robust, idempotent bootstrap for Vault + AppRole
# Usage: run from infra/ directory (where docker-compose.yml lives)


RETRY=80       # number of attempts for waits
SLEEP=3        # sleep seconds between attempts
INIT_OUT=./vault/init/vault-init-output.json
UNSEAL_FILE=./vault/init/vault-unseal-key
ROOT_TOKEN_FILE=./vault/init/vault-root-token
ROLE_ID_FILE=./vault/init/approle-role-id
SECRET_ID_FILE=./vault/init/approle-secret-id

die(){ echo "ERROR: $*"; exit 1; }
info(){ echo "INFO: $*"; }

# sanity checks
if [ ! -f docker-compose.yml ]; then
  die "docker-compose.yml not found in current dir. Run from infra/."
fi
if ! command -v jq >/dev/null 2>&1; then
  die "jq is required on host. Install it and re-run."
fi
if ! command -v docker >/dev/null 2>&1; then
  die "docker CLI required on host."
fi

# ensure init dir exists (host side). Note: this directory is bind-mounted
# into the vault container at /vault/init. We later ensure vault user
# ownership inside the container after it starts.
mkdir -p ./vault/init
chmod 700 ./vault/init

# Build vault image (so envsubst/curl changes in Dockerfile are picked up if present)
info "Building vault image (docker compose build vault)"
docker compose build vault || info "docker compose build vault returned non-zero; continuing"

# Start DB first if compose contains a db service (best-effort)
if grep -qiE '(^\s*db:)|postgres' docker-compose.yml 2>/dev/null || docker compose ps db >/dev/null 2>&1; then
  info "Detected DB in compose; starting db service first"
  docker compose up -d db || true
  # Wait for db to respond (best-effort)
  i=0
  until docker exec -i db pg_isready >/dev/null 2>&1 || [ $i -ge 40 ]; do
    i=$((i+1)); echo "  waiting for db (attempt $i/40)"; sleep 2
  done
  if [ $i -ge 40 ]; then
    info "Postgres did not become ready in this script's wait window; continuing anyway (Vault init may fail if DB required)."
  else
    info "Postgres appears ready."
  fi
fi

# Free host port 8200 if held by a docker container (safe for local dev)
DOCKER_8200_LINE=$(docker ps --format '{{.ID}} {{.Names}} {{.Ports}}' | grep -E '8200->8200|8200:8200' || true)
if [ -n "$DOCKER_8200_LINE" ]; then
  info "Found a docker container publishing host:8200, stopping & removing it to free port (local dev convenience)."
  echo "$DOCKER_8200_LINE"
  CID=$(echo "$DOCKER_8200_LINE" | awk '{print $1}')
  docker stop "$CID" || true
  docker rm -f "$CID" || true
  sleep 1
fi

# Start vault
info "Starting vault container (docker compose up -d vault)"
docker compose up -d vault

# Ensure the "vault" user inside the container owns /vault/init.
# This is important because the host bind-mount (./vault/init) can
# override ownership and cause permission issues for Vault and any
# consumers of the init artifacts.
info "Ensuring vault user owns /vault/init inside the vault container"
if ! docker exec -i vault sh -c 'chown -R vault:vault /vault/init 2>/dev/null || chown -R vault /vault/init 2>/dev/null || true'; then
  info "Warning: could not adjust ownership of /vault/init inside vault container; continuing."
fi

# Wait for HTTP health endpoint inside container
info "Waiting for Vault HTTP endpoint inside container. Accepting 200/429/501/503 as 'up'."
i=0
HEALTH_OK=0
# Track whether we've positively confirmed unseal to avoid false negatives from
# transient status reads right after unseal.
UNSEAL_CONFIRMED=0
while [ $i -lt $RETRY ]; do
  i=$((i+1))
  # Ask curl to return only the HTTP status code; treat any response as connectivity
  HTTP_CODE=$(docker exec -i vault sh -c "curl -sS -m 3 -o /dev/null -w '%{http_code}' http://127.0.0.1:8200/v1/sys/health" 2>/dev/null || echo "")

  case "$HTTP_CODE" in
    200|429|501|503)
      HEALTH_OK=1
      echo "  vault HTTP endpoint returned $HTTP_CODE (container up)."
      break
      ;;
    "")
      echo "  vault HTTP endpoint not responding yet (attempt $i/$RETRY)..."
      ;;
    *)
      echo "  vault HTTP endpoint returned $HTTP_CODE (waiting)..."
      ;;
  esac
  sleep $SLEEP
done

if [ $HEALTH_OK -ne 1 ]; then
  echo "ERROR: Vault HTTP health did not respond in time. Tail logs:"
  docker logs vault --tail 200 || true
  die "Vault HTTP health not reachable"
fi

# -----------------------
# Initialize if needed & robust unseal
# -----------------------

# Check current status inside container with explicit VAULT_ADDR
STATUS_JSON=$(docker exec -i vault sh -c 'VAULT_ADDR=http://127.0.0.1:8200 vault status -format=json' 2>/dev/null || true)
STATUS_JSON=${STATUS_JSON:-"{}"}
INIT=$(echo "$STATUS_JSON" | jq -rs '.[0].initialized // false' | tr -d '\r' | xargs)
SEALED=$(echo "$STATUS_JSON" | jq -rs '.[0].sealed // true' | tr -d '\r' | xargs)
info "Pre-init status: initialized=$INIT sealed=$SEALED"

# Initialize if necessary
if [ "$INIT" != "true" ]; then
  info "Initializing Vault (1 share, threshold 1)..."
  docker exec -i vault sh -c 'VAULT_ADDR=http://127.0.0.1:8200 vault operator init -key-shares=1 -key-threshold=1 -format=json' > "${INIT_OUT}" 2> ./vault/init/vault-init-init.err || true

  if [ ! -s "${INIT_OUT}" ]; then
    echo "ERROR: vault operator init produced no JSON output. See vault logs and init stderr:"
    echo "---- init stderr ----"
    sed -n '1,200p' ./vault/init/vault-init-init.err || true
    echo "---- vault logs (last 200) ----"
    docker logs vault --tail 200 || true
    die "vault operator init failed"
  fi

  jq -r '.unseal_keys_b64[0]' "${INIT_OUT}" > "${UNSEAL_FILE}"
  jq -r '.root_token' "${INIT_OUT}" > "${ROOT_TOKEN_FILE}"
  chmod 600 "${UNSEAL_FILE}" "${ROOT_TOKEN_FILE}"
  info "Wrote vault-unseal-key and vault-root-token to ./vault/init (chmod 600)"
  # Give Vault a brief moment to settle after initialization
  sleep 2
fi

# Refresh status
STATUS_JSON=$(docker exec -i vault sh -c 'VAULT_ADDR=http://127.0.0.1:8200 vault status -format=json' 2>/dev/null || true)
# If empty, set a default
STATUS_JSON=${STATUS_JSON:-"{}"}
SEALED=$(echo "$STATUS_JSON" | jq -rs '.[0].sealed // true' | tr -d '\r' | xargs)
info "Post-init status: sealed=$SEALED"

# Unseal if sealed (robust, with retries and error capture)
if [ "$SEALED" = "true" ]; then
  if [ ! -s "$UNSEAL_FILE" ]; then
    die "Vault is sealed and unseal key file '$UNSEAL_FILE' is missing or empty"
  fi

  UNSEAL_KEY=$(tr -d '\r\n' < "$UNSEAL_FILE")
  info "Attempting to unseal Vault using $UNSEAL_FILE (container-local VAULT_ADDR=127.0.0.1:8200)..."

  UNSEAL_ERR=./vault/init/unseal.err
  UNSEAL_OUT=./vault/init/unseal.out
  rm -f "$UNSEAL_ERR" "$UNSEAL_OUT"

  # retry a few times (defensive)
  RETRIES=5
  n=0
  while [ $n -lt $RETRIES ]; do
    n=$((n+1))
    echo "  unseal attempt $n/$RETRIES..."
    docker exec -i vault sh -c "VAULT_ADDR='http://127.0.0.1:8200' vault operator unseal '$UNSEAL_KEY'" >"$UNSEAL_OUT" 2>"$UNSEAL_ERR" || true

    if [ -s "$UNSEAL_ERR" ]; then
      echo "  unseal stderr (attempt $n):"
      sed -n '1,200p' "$UNSEAL_ERR" || true
    fi

    STATUS_JSON=$(docker exec -i vault sh -c 'VAULT_ADDR=http://127.0.0.1:8200 vault status -format=json' 2>/dev/null || true)
    STATUS_JSON=${STATUS_JSON:-"{}"}
    SEALED=$(echo "$STATUS_JSON" | jq -rs '.[0].sealed // true' | tr -d '\r' | xargs)
    if [ "$SEALED" = "false" ]; then
      info "Vault successfully unsealed on attempt $n"
      UNSEAL_CONFIRMED=1
      break
    fi
    sleep 2
  done

  STATUS_JSON=$(docker exec -i vault sh -c 'VAULT_ADDR=http://127.0.0.1:8200 vault status -format=json' 2>/dev/null || true)
  STATUS_JSON=${STATUS_JSON:-"{}"}
  SEALED=$(echo "$STATUS_JSON" | jq -rs '.[0].sealed // true' | tr -d '\r' | xargs)

  if [ "$SEALED" = "true" ]; then
    # Vault can take a brief moment to reflect unsealed status after operator unseal.
    # Perform a short settle wait before declaring failure.
    SETTLE_TRIES=10
    j=0
    while [ $j -lt $SETTLE_TRIES ]; do
      j=$((j+1))
      sleep 1
      STATUS_JSON=$(docker exec -i vault sh -c 'VAULT_ADDR=http://127.0.0.1:8200 vault status -format=json' 2>/dev/null || true)
      STATUS_JSON=${STATUS_JSON:-"{}"}
      SEALED=$(echo "$STATUS_JSON" | jq -rs '.[0].sealed // true' | tr -d '\r' | xargs)
      if [ "$SEALED" = "false" ]; then
        info "Vault reported unsealed after settle wait ($j s)"
        UNSEAL_CONFIRMED=1
        break
      fi
    done
  fi

  if [ "$SEALED" = "true" ]; then
    # As an additional safeguard, if the last unseal stdout shows Sealed false, trust that and proceed.
    if [ -s "$UNSEAL_OUT" ] && grep -qE '^Sealed\s+false$' "$UNSEAL_OUT"; then
      info "Unseal command output indicates Sealed=false; proceeding despite delayed status update"
      UNSEAL_CONFIRMED=1
    else
      echo "ERROR: Vault remains sealed after $RETRIES attempts. Collected output:"
      echo "---- unseal stdout ----"
      [ -s "$UNSEAL_OUT" ] && sed -n '1,200p' "$UNSEAL_OUT" || echo "(no stdout)"
      echo "---- unseal stderr ----"
      [ -s "$UNSEAL_ERR" ] && sed -n '1,200p' "$UNSEAL_ERR" || echo "(no stderr)"
      echo "---- vault logs (last 200) ----"
      docker logs vault --tail 200 || true
      die "Unseal failed — inspect output above"
    fi
  fi
else
  info "Vault not sealed (no unseal required)"
fi

# Ensure Vault is unsealed before proceeding to policy/roles
STATUS_JSON=$(docker exec -i vault sh -c 'VAULT_ADDR=http://127.0.0.1:8200 vault status -format=json' 2>/dev/null || true)
STATUS_JSON=${STATUS_JSON:-"{}"}
SEALED=$(echo "$STATUS_JSON" | jq -rs '.[0].sealed // true' | tr -d '\r' | xargs)

# If status still reports sealed, double-check via health endpoint and UNSEAL_CONFIRMED flag to avoid
# false negatives caused by status lag immediately after unseal.
if [ "$SEALED" != "false" ]; then
  # Check health endpoint for active/standby which implies unsealed
  HEALTH_CODE=$(docker exec -i vault sh -c "curl -sS -m 3 -o /dev/null -w '%{http_code}' http://127.0.0.1:8200/v1/sys/health" 2>/dev/null || echo "")
  case "$HEALTH_CODE" in
    200|429)
      info "Health endpoint indicates Vault is unsealed (HTTP $HEALTH_CODE). Proceeding."
      ;;
    *)
      if [ "$UNSEAL_CONFIRMED" = "1" ]; then
        info "Unseal previously confirmed; proceeding despite transient sealed status."
      else
        die "Vault is still sealed; aborting before policy/role configuration"
      fi
      ;;
  esac
fi

# Ensure root token exists
if [ ! -s "${ROOT_TOKEN_FILE}" ]; then
  die "Missing root token at ${ROOT_TOKEN_FILE} - cannot proceed to create policies/roles"
fi
ROOT_TOKEN=$(tr -d '\r\n' < "${ROOT_TOKEN_FILE}")

# Ensure KV v2 is enabled at path secret/ to match policies (idempotent)
info "Ensuring KV v2 secrets engine is enabled at path 'secret/'"
docker exec -i vault /bin/sh -c "export VAULT_ADDR=http://127.0.0.1:8200 VAULT_TOKEN=${ROOT_TOKEN}; \
  vault secrets list -format=json | jq -e 'has(\"secret/\")' >/dev/null 2>&1 || \
  vault secrets enable -path=secret -version=2 kv >/dev/null 2>&1 || true"

# -----------------------
# Load local .env into Vault KV v2
# -----------------------

load_env_into_vault() {
  local env_file="./.env"

  if [ ! -f "${env_file}" ]; then
    info "No ${env_file} found in infra directory; skipping .env -> Vault import."
    return 0
  fi

  info "Loading key/value pairs from ${env_file} into Vault KV v2 under secret/statement-service ..."

  # Parse .env into key=value pairs while capturing an optional CONFIG_PROFILE.
  # We avoid "source" to keep the script robust against unexpected shell syntax.
  local line key val
  local -a kv_pairs=()
  local config_profile=""

  while IFS= read -r line || [ -n "$line" ]; do
    # Trim leading/trailing whitespace
    line="${line%%[[:space:]]*}"
    case "$line" in
      ''|'#'* )
        continue
        ;;
    esac

    # Require an '=' separator
    case "$line" in
      *"="*) ;;
      *)
        continue
        ;;
    esac

    key=${line%%=*}
    val=${line#*=}

    # Trim spaces around key and value
    key="${key%%[[:space:]]*}"
    key="${key##*[[:space:]]}"
    val="${val%%[[:space:]]}"

    [ -z "$key" ] && continue

    if [ "$key" = "CONFIG_PROFILE" ]; then
      config_profile="$val"
    fi

    kv_pairs+=("${key}=${val}")
  done < "${env_file}"

  if [ ${#kv_pairs[@]} -eq 0 ]; then
    info "No valid KEY=VALUE pairs found in ${env_file}; nothing to write to Vault."
    return 0
  fi

  # Helper: write a set of key/value pairs to a given secret path using the root token.
  write_pairs_to_path() {
    local path="$1"; shift
    local -a pairs=("$@")

    if [ ${#pairs[@]} -eq 0 ]; then
      return 0
    fi

    info "Writing ${#pairs[@]} entries to Vault path '${path}'"
    docker exec -i \
      -e VAULT_ADDR="http://127.0.0.1:8200" \
      -e VAULT_TOKEN="${ROOT_TOKEN}" \
      vault \
      vault kv put "${path}" "${pairs[@]}" >/dev/null
  }

  # Base context used by Spring Cloud Config (maps to application.properties/yml)
  write_pairs_to_path "secret/statement-service" "${kv_pairs[@]}"

  # Optional profile-specific context: secret/statement-service/<profile>
  if [ -n "$config_profile" ]; then
    info "Detected CONFIG_PROFILE='${config_profile}' in ${env_file}; writing profile-specific secret path."
    write_pairs_to_path "secret/statement-service/${config_profile}" "${kv_pairs[@]}"
  else
    info "No CONFIG_PROFILE found in ${env_file}; skipping profile-specific Vault path."
  fi
}

load_env_into_vault

# Create policy + AppRole (idempotent)
info "Writing policy and creating AppRole (config-server)"
docker exec -i vault /bin/sh -c "export VAULT_ADDR=http://127.0.0.1:8200 VAULT_TOKEN=${ROOT_TOKEN} && cat > /tmp/config-server-policy.hcl <<'POL'
# Broadened policy to allow Config Server to read both application-level and app-specific contexts.
# Spring Cloud Config Server's Vault backend may read contexts like 'application' and '{app}'.
# Grant read/list on all keys under 'secret/' (KV v2 data + metadata paths).
path \"secret/data/*\" {
  capabilities = [\"read\",\"list\"]
}
path \"secret/metadata/*\" {
  capabilities = [\"list\"]
}
POL
vault policy write config-server /tmp/config-server-policy.hcl || true
vault auth enable -path=approle approle || true
vault write -f auth/approle/role/config-server token_policies=config-server token_ttl=12h token_max_ttl=24h || true
"

# Fetch role_id
info "Fetching role_id"
docker exec -i vault /bin/sh -c "export VAULT_ADDR=http://127.0.0.1:8200 VAULT_TOKEN=${ROOT_TOKEN} && vault read -field=role_id auth/approle/role/config-server/role-id" > "${ROLE_ID_FILE}"
if [ ! -s "${ROLE_ID_FILE}" ]; then
  die "Failed to fetch role_id from Vault"
fi
chmod 600 "${ROLE_ID_FILE}"

# Create secret_id properly
info "Creating approle secret_id (-f) and saving to host"
docker exec -i vault /bin/sh -c "export VAULT_ADDR=http://127.0.0.1:8200 VAULT_TOKEN=${ROOT_TOKEN} && vault write -format=json -f auth/approle/role/config-server/secret-id" > /tmp/secret_id_response.json 2>/tmp/secret_id_err.log || true
if [ ! -s /tmp/secret_id_response.json ]; then
  echo "Secret-id creation failed. Stderr (first 200 chars):"
  sed -n '1,200p' /tmp/secret_id_err.log || true
  die "Secret-id creation failed (see stderr above)"
fi
jq -r '.data.secret_id' /tmp/secret_id_response.json > "${SECRET_ID_FILE}"
if [ ! -s "${SECRET_ID_FILE}" ]; then
  echo "DEBUG: secret-id JSON:"
  jq . /tmp/secret_id_response.json || true
  die "Extracted secret_id is empty"
fi
chmod 600 "${SECRET_ID_FILE}"

info "Wrote approle-role-id and approle-secret-id to ./vault/init (chmod 600)."
ls -la ./vault/init || true

# Test AppRole login (prefer host publish to localhost)
ROLE_ID=$(cat "${ROLE_ID_FILE}")
SECRET_ID=$(cat "${SECRET_ID_FILE}")
info "Testing AppRole login against host:8200 (if published)"
if curl -sS -X POST http://localhost:8200/v1/auth/approle/login -H "Content-Type: application/json" -d "{\"role_id\":\"${ROLE_ID}\",\"secret_id\":\"${SECRET_ID}\"}" >/dev/null 2>&1; then
  info "AppRole login endpoint reachable on host:8200. Print token with the command shown in the info message below."
else
  CONIP=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' vault || true)
  if [ -n "$CONIP" ]; then
    info "Trying AppRole login via container IP ${CONIP}"
    docker run --rm --network container:vault curlimages/curl:7.87.0 -sS -X POST "http://${CONIP}:8200/v1/auth/approle/login" -H "Content-Type: application/json" -d "{\"role_id\":\"${ROLE_ID}\",\"secret_id\":\"${SECRET_ID}\"}" | jq . || true
  else
    info "Could not determine vault container IP; skipping container-IP login test."
  fi
fi

info "Bootstrap complete. Artifacts in ./vault/init. Secure vault-root-token & vault-unseal-key."
info "To print token (host): ROLE_ID=\$(cat ./vault/init/approle-role-id); SECRET_ID=\$(cat ./vault/init/approle-secret-id); curl -sS -X POST http://localhost:8200/v1/auth/approle/login -H 'Content-Type: application/json' -d '{\"role_id\":\"'\${ROLE_ID}'\",\"secret_id\":\"'\${SECRET_ID}'\"}' | jq ."

exit 0

