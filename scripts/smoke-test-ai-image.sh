#!/usr/bin/env bash
# Builds the FastAPI AI service image, starts it against a throwaway pgvector Postgres,
# and checks that it answers its health endpoint, protects its metrics endpoint with the
# internal API key, runs as a non-root user, and can open its database connection.
#
# Usage: scripts/smoke-test-ai-image.sh
# Needs a running Docker daemon. All containers and the network are removed on exit.

set -euo pipefail

REPOSITORY_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IMAGE_TAG="hittiguess-ai:smoke"
NETWORK_NAME="hittiguess-ai-smoke-net"
DATABASE_CONTAINER="hittiguess-ai-smoke-db"
APP_CONTAINER="hittiguess-ai-smoke-app"
HOST_PORT=18000
INTERNAL_API_KEY_HEADER="X-Internal-Api-Key"
DATABASE_START_WAIT_SECONDS=12
STARTUP_POLL_INTERVAL_SECONDS=2
STARTUP_MAX_POLLS=30
DATABASE_PASSWORD="$(head -c 18 /dev/urandom | base64 | tr -dc 'A-Za-z0-9')"
INTERNAL_API_KEY="$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9')"

cleanup() {
  docker rm -f "$APP_CONTAINER" "$DATABASE_CONTAINER" >/dev/null 2>&1 || true
  docker network rm "$NETWORK_NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT
cleanup

fail() {
  echo "FAIL: $1" >&2
  docker logs "$APP_CONTAINER" 2>&1 | tail -30 >&2 || true
  exit 1
}

status_of() {
  curl -s -o /dev/null -w '%{http_code}' "$@"
}

docker build -t "$IMAGE_TAG" "$REPOSITORY_ROOT/ai"
docker network create "$NETWORK_NAME" >/dev/null

docker run -d --name "$DATABASE_CONTAINER" --network "$NETWORK_NAME" \
  -e POSTGRES_PASSWORD="$DATABASE_PASSWORD" -e POSTGRES_DB=core pgvector/pgvector:pg18 >/dev/null
sleep "$DATABASE_START_WAIT_SECONDS"
docker exec "$DATABASE_CONTAINER" psql -U postgres -d core -c "CREATE EXTENSION IF NOT EXISTS vector" >/dev/null

docker run -d --name "$APP_CONTAINER" --network "$NETWORK_NAME" -p "$HOST_PORT:8000" \
  -e OPENAI_API_KEY=smoke -e YOUTUBE_API_KEY=smoke \
  -e DISCOGS_CONSUMER_KEY=smoke -e DISCOGS_CONSUMER_SECRET=smoke -e DEEPINFRA_API_KEY=smoke \
  -e INTERNAL_SERVICE_API_KEY="$INTERNAL_API_KEY" \
  -e DATABASE_URL="postgresql://postgres:$DATABASE_PASSWORD@$DATABASE_CONTAINER:5432/core" \
  "$IMAGE_TAG" >/dev/null

started=false
for _ in $(seq 1 "$STARTUP_MAX_POLLS"); do
  if [ "$(status_of "http://localhost:$HOST_PORT/health" || true)" = "200" ]; then started=true; break; fi
  if [ "$(docker inspect -f '{{.State.Running}}' "$APP_CONTAINER")" != "true" ]; then break; fi
  sleep "$STARTUP_POLL_INTERVAL_SECONDS"
done
[ "$started" = true ] || fail "the AI service did not answer /health"

unauthenticated_metrics_status="$(status_of "http://localhost:$HOST_PORT/metrics")"
[ "$unauthenticated_metrics_status" != "200" ] || fail "/metrics answered without the internal API key"

authenticated_metrics_status="$(status_of -H "$INTERNAL_API_KEY_HEADER: $INTERNAL_API_KEY" "http://localhost:$HOST_PORT/metrics")"
[ "$authenticated_metrics_status" = "200" ] || fail "/metrics answered $authenticated_metrics_status with the internal API key"

[ "$(docker exec "$APP_CONTAINER" id -u)" != "0" ] || fail "the container runs as root"

docker exec "$APP_CONTAINER" python -c \
  "from app.dedup.database import get_connection; connection = get_connection(); print(connection.run('select 1')); connection.close()" \
  >/dev/null || fail "the service could not open its database connection"

echo "OK: AI image started, answered /health, protected /metrics, and connected to its database"
