#!/usr/bin/env bash
set -euo pipefail

KEYCLOAK_URL=${KEYCLOAK_URL:-http://localhost:8081}
REALM=${REALM:-statement-service}
CLIENT_ID=${CLIENT_ID:-statement-service-consumer-client}
CLIENT_SECRET=${CLIENT_SECRET}

echo "Requesting token for client: $CLIENT_ID (realm: $REALM) from $KEYCLOAK_URL"

curl -s \
  -X POST "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" \
  -d "grant_type=client_credentials" \
  -d "client_id=$CLIENT_ID" \
  -d "client_secret=$CLIENT_SECRET" \
  | jq .
