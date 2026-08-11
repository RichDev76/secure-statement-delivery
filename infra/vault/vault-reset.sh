#!/usr/bin/env bash
set -euo pipefail

COMPOSE="docker compose"
VAULT_SERVICE="vault"
VOLUME_NAME="vault-data"   # adjust if your compose uses a different volume name
BACKUP_DIR="./vault-backups"
TS=$(date +%Y%m%d%H%M%S)

mkdir -p "${BACKUP_DIR}"

echo "Stopping vault..."
${COMPOSE} down -v || true

# create and mount a short-lived helper container to tar the volume contents
echo "Creating backup of volume ${VOLUME_NAME} to ${BACKUP_DIR}/vault_data_${TS}.tar.gz ..."
docker run --rm -v ${VOLUME_NAME}:/vault/data -v "$(pwd)/${BACKUP_DIR}":/backup alpine \
  sh -c "cd /vault && tar czf /backup/vault_data_${TS}.tar.gz data"

echo "Backup complete: ${BACKUP_DIR}/vault_data_${TS}.tar.gz"

echo "Removing and recreating volume ${VOLUME_NAME}..."
docker volume rm ${VOLUME_NAME} >/dev/null 2>&1 || true
docker volume create ${VOLUME_NAME} >/dev/null

echo "Bringing up vault container (empty data)..."
${COMPOSE} up -d vault

echo "Wipe complete. You must reinitialize Vault using ./vault-init.sh"
