#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/.env.hetzner"

if [ ! -f "${ENV_FILE}" ]; then
  echo "Error: ${ENV_FILE} not found."
  echo "Copy .env.hetzner.example to .env.hetzner and populate production secrets."
  exit 1
fi

echo "================================================================================"
echo "HETZNER DOCKER COMPOSE DEPLOYMENT"
echo "================================================================================"

cd "${SCRIPT_DIR}"

echo "1. Building and pulling images..."
docker compose --env-file "${ENV_FILE}" build
docker compose --env-file "${ENV_FILE}" pull caddy alloy || true

echo "2. Starting services..."
docker compose --env-file "${ENV_FILE}" up -d --remove-orphans

echo "3. Waiting for services to become healthy..."
MAX_ATTEMPTS=30
ATTEMPT=1
CORE_HEALTHY=false

while [ "${ATTEMPT}" -le "${MAX_ATTEMPTS}" ]; do
  STATUS=$(docker inspect --format='{{json .State.Health.Status}}' hittiguess-core 2>/dev/null || echo '"starting"')
  if [ "${STATUS}" = '"healthy"' ]; then
    CORE_HEALTHY=true
    break
  fi
  echo "Waiting for hittiguess-core healthcheck (${ATTEMPT}/${MAX_ATTEMPTS})..."
  sleep 3
  ATTEMPT=$((ATTEMPT + 1))
done

if [ "${CORE_HEALTHY}" != "true" ]; then
  echo "Error: hittiguess-core failed to reach healthy state within timeout."
  docker compose logs core
  exit 1
fi

echo "4. Verifying local reverse proxy routing..."
CADDY_HEALTH=$(curl -s -o /dev/null -w "%{http_code}" http://localhost/caddy-health || echo "000")
if [ "${CADDY_HEALTH}" = "200" ]; then
  echo "  Caddy healthcheck endpoint: PASS (HTTP 200)"
else
  echo "  Caddy healthcheck endpoint: FAIL (HTTP ${CADDY_HEALTH})"
fi

PROMETHEUS_ACCESS=$(curl -s -o /dev/null -w "%{http_code}" -H "Host: api.hittiguess.com" http://localhost/actuator/prometheus || echo "000")
if [ "${PROMETHEUS_ACCESS}" = "403" ]; then
  echo "  External prometheus protection: PASS (HTTP 403 Forbidden)"
else
  echo "  External prometheus protection: WARNING (HTTP ${PROMETHEUS_ACCESS}, expected 403)"
fi

echo "================================================================================"
echo "Deployment completed successfully."
echo "================================================================================"
