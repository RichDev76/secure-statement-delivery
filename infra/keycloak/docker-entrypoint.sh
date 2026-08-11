#!/bin/bash
set -euo pipefail

TEMPLATE_DIR="/opt/keycloak/data/import-templates"
IMPORT_DIR="/opt/keycloak/data/import"

# Create import directory if it doesn't exist
mkdir -p "$IMPORT_DIR"

# Process all template files
for template in "$TEMPLATE_DIR"/*.tpl; do
    if [ -f "$template" ]; then
        filename=$(basename "$template" .tpl)
        echo "Processing template: $template -> $IMPORT_DIR/$filename"
        envsubst < "$template" > "$IMPORT_DIR/$filename"
    fi
done

# Execute the original Keycloak entrypoint
exec /opt/keycloak/bin/kc.sh "$@"