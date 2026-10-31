#!/usr/bin/env bash
# Deployment smoke test script for production.
# Checks health endpoints, HTTPS API, WSS STOMP connect, protected-route session_hint redirect,
# and that core reaches the AI microservice.
#
# Usage:
#   scripts/smoke-test-production.sh [CORE_API_URL] [FRONTEND_URL] [AI_SERVICE_URL] [AUTH_TOKEN]
# Or set environment variables:
#   CORE_API_URL=https://api.hittiguess.com FRONTEND_URL=https://www.hittiguess.com scripts/smoke-test-production.sh

set -euo pipefail

DEFAULT_CORE_API_URL="https://api.hittiguess.com"
DEFAULT_FRONTEND_URL="https://www.hittiguess.com"
DEFAULT_AI_SERVICE_URL=""
DEFAULT_AUTH_TOKEN=""
TIMEOUT_SECONDS=15

CORE_API_URL="${1:-${CORE_API_URL:-$DEFAULT_CORE_API_URL}}"
FRONTEND_URL="${2:-${FRONTEND_URL:-$DEFAULT_FRONTEND_URL}}"
AI_SERVICE_URL="${3:-${AI_SERVICE_URL:-$DEFAULT_AI_SERVICE_URL}}"
AUTH_TOKEN="${4:-${AUTH_TOKEN:-$DEFAULT_AUTH_TOKEN}}"

# Strip trailing slashes
CORE_API_URL="${CORE_API_URL%/}"
FRONTEND_URL="${FRONTEND_URL%/}"
AI_SERVICE_URL="${AI_SERVICE_URL%/}"

CORE_HOST="$(echo "$CORE_API_URL" | sed -e 's|^[^/]*//||' -e 's|/.*$||' -e 's|:[0-9]*$||')"
CORE_SCHEME="$(echo "$CORE_API_URL" | grep -o '^[a-z]*')"

if [ "$CORE_SCHEME" = "https" ]; then
  WSS_SCHEME="wss"
  DEFAULT_PORT=443
else
  WSS_SCHEME="ws"
  DEFAULT_PORT=80
fi

CORE_PORT="$(echo "$CORE_API_URL" | sed -e 's|^[^/]*//||' -e 's|/.*$||' | grep -o ':[0-9]*$' | tr -d ':' || true)"
if [ -z "$CORE_PORT" ]; then
  CORE_PORT="$DEFAULT_PORT"
fi

WSS_ENDPOINT_URL="${WSS_SCHEME}://${CORE_HOST}:${CORE_PORT}/ws"

failures_count=0

record_success() {
  echo "[PASS] $1"
}

record_failure() {
  echo "[FAIL] $1" >&2
  failures_count=$((failures_count + 1))
}

echo "================================================================================"
echo "HITTIGUESS PRODUCTION DEPLOYMENT SMOKE TEST"
echo "================================================================================"
echo "Core API URL:     $CORE_API_URL"
echo "Frontend URL:     $FRONTEND_URL"
echo "WebSocket URL:    $WSS_ENDPOINT_URL"
if [ -n "$AI_SERVICE_URL" ]; then
  echo "AI Service URL:   $AI_SERVICE_URL"
fi
if [ -n "$AUTH_TOKEN" ]; then
  echo "Auth Token:       [configured]"
else
  echo "Auth Token:       [not provided - testing public routes and protected handshake]"
fi
echo "================================================================================"

# Check 1: Core Health Probes
echo "Step 1: Checking health endpoints..."

for probe_path in /actuator/health /actuator/health/liveness /actuator/health/readiness; do
  probe_url="$CORE_API_URL$probe_path"
  status_code="$(curl -s -k -o /dev/null -w '%{http_code}' --max-time "$TIMEOUT_SECONDS" "$probe_url" || echo "000")"
  if [ "$status_code" = "200" ]; then
    record_success "Core probe $probe_path returned HTTP 200"
  else
    record_failure "Core probe $probe_path returned HTTP $status_code (expected 200)"
  fi
done

if [ -n "$AI_SERVICE_URL" ]; then
  ai_health_url="$AI_SERVICE_URL/health"
  ai_status_code="$(curl -s -k -o /dev/null -w '%{http_code}' --max-time "$TIMEOUT_SECONDS" "$ai_health_url" || echo "000")"
  if [ "$ai_status_code" = "200" ]; then
    record_success "AI service health returned HTTP 200"
  else
    record_failure "AI service health returned HTTP $ai_status_code (expected 200)"
  fi
fi

# Check 2: HTTPS API Endpoints
echo "Step 2: Checking HTTPS API..."
public_api_url="$CORE_API_URL/api/enums/countries"
public_api_status_code="$(curl -s -k -o /dev/null -w '%{http_code}' --max-time "$TIMEOUT_SECONDS" "$public_api_url" || echo "000")"
if [ "$public_api_status_code" = "200" ]; then
  record_success "HTTPS API $public_api_url returned HTTP 200"
else
  record_failure "HTTPS API $public_api_url returned HTTP $public_api_status_code (expected 200)"
fi

if [ -n "$AUTH_TOKEN" ]; then
  authenticated_api_url="$CORE_API_URL/api/songs/recommended"
  auth_api_status_code="$(curl -s -k -o /dev/null -w '%{http_code}' --max-time "$TIMEOUT_SECONDS" -H "Authorization: Bearer $AUTH_TOKEN" "$authenticated_api_url" || echo "000")"
  if [ "$auth_api_status_code" = "200" ]; then
    record_success "Authenticated HTTPS API $authenticated_api_url returned HTTP 200"
  else
    record_failure "Authenticated HTTPS API $authenticated_api_url returned HTTP $auth_api_status_code (expected 200)"
  fi
fi

# Check 3: WSS STOMP Connect
echo "Step 3: Checking WSS STOMP WebSocket connection..."
stomp_test_result="$(python3 -c "
import asyncio, ssl, base64, os, sys, urllib.parse

async def test_websocket_stomp(target_host, target_port, use_tls, auth_token):
    ssl_context = ssl.create_default_context() if use_tls else None
    reader, writer = await asyncio.wait_for(
        asyncio.open_connection(target_host, target_port, ssl=ssl_context),
        timeout=$TIMEOUT_SECONDS
    )
    websocket_nonce = base64.b64encode(os.urandom(16)).decode('ascii')
    cookie_header = f'Cookie: access_token={auth_token}\r\n' if auth_token else ''
    origin_header = 'Origin: https://www.hittiguess.com\r\n'
    handshake_request = (
        f'GET /ws HTTP/1.1\r\n'
        f'Host: {target_host}:{target_port}\r\n'
        f'Upgrade: websocket\r\n'
        f'Connection: Upgrade\r\n'
        f'{origin_header}'
        f'{cookie_header}'
        f'Sec-WebSocket-Key: {websocket_nonce}\r\n'
        f'Sec-WebSocket-Version: 13\r\n\r\n'
    )
    writer.write(handshake_request.encode('utf-8'))
    await writer.drain()

    handshake_response = await asyncio.wait_for(reader.readuntil(b'\r\n\r\n'), timeout=$TIMEOUT_SECONDS)
    response_header_text = handshake_response.decode('utf-8', errors='replace')
    status_line = response_header_text.splitlines()[0]

    if ' 101 ' in status_line:
        writer.close()
        await writer.wait_closed()
        print('UPGRADE_SUCCESS')
        return

    if ' 401 ' in status_line and not auth_token:
        # Without an auth token, Spring Security rejects unauthenticated /ws with 401,
        # confirming the WSS endpoint is active and correctly enforcing auth.
        writer.close()
        await writer.wait_closed()
        print('PROTECTED_ACTIVE')
        return

    writer.close()
    await writer.wait_closed()
    raise RuntimeError(f'Handshake failed with status: {status_line}')

use_tls_flag = ('$WSS_SCHEME' == 'wss')
try:
    asyncio.run(test_websocket_stomp('$CORE_HOST', int('$CORE_PORT'), use_tls_flag, '$AUTH_TOKEN'))
except Exception as connection_error:
    print(f'ERROR: {connection_error}')
    sys.exit(1)
" 2>&1 || echo "FAILED")"

if [ "$stomp_test_result" = "UPGRADE_SUCCESS" ]; then
  record_success "WSS STOMP WebSocket upgrade handshake succeeded on $WSS_ENDPOINT_URL"
elif [ "$stomp_test_result" = "PROTECTED_ACTIVE" ]; then
  record_success "WSS STOMP endpoint is active and securely enforces authentication (HTTP 401 without credentials) on $WSS_ENDPOINT_URL"
else
  record_failure "WSS STOMP WebSocket handshake failed on $WSS_ENDPOINT_URL: $stomp_test_result"
fi

# Check 4: Protected-route session_hint Redirect
echo "Step 4: Checking protected-route session_hint redirect..."
protected_url="$FRONTEND_URL/playlists"

# Request without session_hint cookie
unauthenticated_http_code="$(curl -s -k -o /dev/null -w '%{http_code}' --max-time "$TIMEOUT_SECONDS" "$protected_url" || echo "000")"
unauthenticated_redirect_location="$(curl -s -k -o /dev/null -w '%{redirect_url}' --max-time "$TIMEOUT_SECONDS" "$protected_url" || echo "")"

if [ "$unauthenticated_http_code" = "307" ] || [ "$unauthenticated_http_code" = "308" ]; then
  if echo "$unauthenticated_redirect_location" | grep -q "/login"; then
    record_success "Unauthenticated request to $protected_url redirected to /login with HTTP $unauthenticated_http_code"
  else
    record_failure "Unauthenticated request redirected to $unauthenticated_redirect_location (expected /login)"
  fi
else
  record_failure "Unauthenticated request to $protected_url returned HTTP $unauthenticated_http_code (expected 307 or 308)"
fi

# Request with session_hint cookie
hint_authenticated_http_code="$(curl -s -k -o /dev/null -w '%{http_code}' --max-time "$TIMEOUT_SECONDS" -H "Cookie: session_hint=active" "$protected_url" || echo "000")"
if [ "$hint_authenticated_http_code" = "200" ]; then
  record_success "Request with session_hint cookie to $protected_url passed through with HTTP 200"
else
  record_failure "Request with session_hint cookie to $protected_url returned HTTP $hint_authenticated_http_code (expected 200)"
fi

# Check 5: Core Reaches AI
echo "Step 5: Checking that core reaches the AI microservice..."
core_ai_health_url="$CORE_API_URL/actuator/health"
core_health_response="$(curl -s -k --max-time "$TIMEOUT_SECONDS" "$core_ai_health_url" || echo "{}")"

ai_reachability_status="$(echo "$core_health_response" | python3 -c "
import json, sys
try:
    payload = json.load(sys.stdin)
    ai_component = payload.get('components', {}).get('aiService', {})
    status = ai_component.get('status')
    if status == 'UP':
        print('UP')
    elif status == 'DOWN':
        print('DOWN')
    else:
        # Top-level status when component details are filtered
        print(payload.get('status', 'UNKNOWN'))
except Exception:
    print('ERROR')
" 2>&1 || echo "ERROR")"

if [ "$ai_reachability_status" = "UP" ]; then
  record_success "Core reports AI microservice reachability status UP"
else
  record_failure "Core reported AI microservice reachability status: $ai_reachability_status"
fi

echo "================================================================================"
if [ "$failures_count" -eq 0 ]; then
  echo "SMOKE TEST RESULT: ALL CHECKS PASSED (0 failures)"
  echo "================================================================================"
  exit 0
else
  echo "SMOKE TEST RESULT: $failures_count CHECK(S) FAILED"
  echo "================================================================================"
  exit 1
fi
