#!/usr/bin/env bash
set -euo pipefail

echo "[entrypoint] Starting statement-service container"

/app/wait-for-config.sh

echo "[entrypoint] Launching Spring Boot application"
exec java ${JAVA_OPTS:-} -jar /app/app.jar
