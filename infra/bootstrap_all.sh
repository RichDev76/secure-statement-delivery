#!/usr/bin/env bash
set -euo pipefail

# bootstrap_all.sh - clean + bootstrap vault + start services (uses docker compose logs)
if [ ! -f docker-compose.yml ]; then
  echo "ERROR: docker-compose.yml not found. Run this from infra/."
  exit 1
fi

echo "bootstrap_all.sh: cleaning environment..."
./clean_env.sh

echo "bootstrap_all.sh: bootstrapping vault..."
./bootstrap_vault.sh

echo "bootstrap_all.sh: starting services..."
./start_services.sh

echo "bootstrap_all.sh: done."

