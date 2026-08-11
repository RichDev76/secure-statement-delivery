#!/usr/bin/env bash
set -euo pipefail
CONFIG_URL=${CONFIG_URL:-http://config-server:8888/actuator/health}
TIMEOUT=${CONFIG_WAIT_TIMEOUT:-120}
INTERVAL=${CONFIG_WAIT_INTERVAL:-3}
start=$(date +%s)
echo "[wait] Waiting for Config Server at $CONFIG_URL (timeout ${TIMEOUT}s)"
while true; do
  if curl -fsS "$CONFIG_URL" >/dev/null 2>&1; then
    echo "[wait] Config Server is up"
    break
  fi
  now=$(date +%s)
  if [ $((now - start)) -ge "$TIMEOUT" ]; then
    echo "[wait] Timeout waiting for Config Server" >&2
    exit 1
  fi
  sleep "$INTERVAL"
done
exec "$@"
