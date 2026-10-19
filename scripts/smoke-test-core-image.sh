#!/usr/bin/env bash
# Builds the Spring core image, starts it with production settings against throwaway
# Postgres containers, and checks that it applies every Flyway migration, runs as a
# non-root user, and answers its unauthenticated health probes.
#
# Usage: scripts/smoke-test-core-image.sh
# Needs a running Docker daemon. All containers and the network are removed on exit.

set -euo pipefail

REPOSITORY_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IMAGE_TAG="hittiguess-core:smoke"
NETWORK_NAME="hittiguess-core-smoke-net"
CORE_DATABASE_CONTAINER="hittiguess-core-smoke-core-db"
ANALYTICS_DATABASE_CONTAINER="hittiguess-core-smoke-analytics-db"
APP_CONTAINER="hittiguess-core-smoke-app"
HOST_PORT=18080
DATABASE_START_WAIT_SECONDS=12
STARTUP_POLL_INTERVAL_SECONDS=3
STARTUP_MAX_POLLS=40
DATABASE_PASSWORD="$(head -c 18 /dev/urandom | base64 | tr -dc 'A-Za-z0-9')"
SIGNING_SECRET="$(head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9')"
INTERNAL_API_KEY="$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9')"
EXPECTED_MIGRATION_COUNT="$(find "$REPOSITORY_ROOT/backend/src/main/resources/db/migration" -name 'V*.sql' | wc -l | tr -d ' ')"

cleanup() {
  docker rm -f "$APP_CONTAINER" "$CORE_DATABASE_CONTAINER" "$ANALYTICS_DATABASE_CONTAINER" >/dev/null 2>&1 || true
  docker network rm "$NETWORK_NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT
cleanup

fail() {
  echo "FAIL: $1" >&2
  docker logs "$APP_CONTAINER" 2>&1 | tail -30 >&2 || true
  exit 1
}

docker build -t "$IMAGE_TAG" "$REPOSITORY_ROOT/backend"
docker network create "$NETWORK_NAME" >/dev/null

docker run -d --name "$CORE_DATABASE_CONTAINER" --network "$NETWORK_NAME" \
  -e POSTGRES_PASSWORD="$DATABASE_PASSWORD" -e POSTGRES_DB=core pgvector/pgvector:pg18 >/dev/null
docker run -d --name "$ANALYTICS_DATABASE_CONTAINER" --network "$NETWORK_NAME" \
  -e POSTGRES_PASSWORD="$DATABASE_PASSWORD" -e POSTGRES_DB=analytics postgres:18 >/dev/null
sleep "$DATABASE_START_WAIT_SECONDS"

docker run -d --name "$APP_CONTAINER" --network "$NETWORK_NAME" -p "$HOST_PORT:8080" \
  -e APP_ENV=prod \
  -e DB_URL="jdbc:postgresql://$CORE_DATABASE_CONTAINER:5432/core" -e DB_USERNAME=postgres -e DB_PASSWORD="$DATABASE_PASSWORD" \
  -e ANALYTICS_DB_URL="jdbc:postgresql://$ANALYTICS_DATABASE_CONTAINER:5432/analytics" \
  -e ANALYTICS_DB_USERNAME=postgres -e ANALYTICS_DB_PASSWORD="$DATABASE_PASSWORD" \
  -e JWT_SECRET="$SIGNING_SECRET" -e INTERNAL_SERVICE_API_KEY="$INTERNAL_API_KEY" \
  -e OAUTH2_CLIENT_ID=smoke -e OAUTH2_CLIENT_SECRET=smoke \
  -e FRONTEND_URL=https://frontend.example.test -e FRONTEND_ALLOWED_ORIGINS=https://frontend.example.test \
  -e RESEND_API_KEY=smoke \
  "$IMAGE_TAG" >/dev/null

started=false
for _ in $(seq 1 "$STARTUP_MAX_POLLS"); do
  if docker logs "$APP_CONTAINER" 2>&1 | grep -q "Started BackendApplication"; then started=true; break; fi
  if [ "$(docker inspect -f '{{.State.Running}}' "$APP_CONTAINER")" != "true" ]; then break; fi
  sleep "$STARTUP_POLL_INTERVAL_SECONDS"
done
[ "$started" = true ] || fail "the core service did not start"

for probe_path in /actuator/health /actuator/health/liveness /actuator/health/readiness; do
  status_code="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$HOST_PORT$probe_path")"
  [ "$status_code" = "200" ] || fail "$probe_path answered $status_code instead of 200"
done

applied_migrations="$(docker exec "$CORE_DATABASE_CONTAINER" psql -U postgres -d core -tAc \
  "select count(*) from flyway_schema_history where success")"
[ "$applied_migrations" -ge "$EXPECTED_MIGRATION_COUNT" ] \
  || fail "only $applied_migrations of $EXPECTED_MIGRATION_COUNT core migrations were applied"

vector_extension="$(docker exec "$CORE_DATABASE_CONTAINER" psql -U postgres -d core -tAc \
  "select extname from pg_extension where extname = 'vector'")"
[ "$vector_extension" = "vector" ] || fail "the vector extension is missing"

[ "$(docker exec "$APP_CONTAINER" id -u)" != "0" ] || fail "the container runs as root"

echo "OK: core image started, applied $applied_migrations migrations, and answered its health probes"
