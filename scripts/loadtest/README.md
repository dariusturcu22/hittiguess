# Load Testing Core and AI Microservices

The script `run_loadtest.py` performs repeatable, end-to-end concurrency testing against the hittiguess core service and AI microservice.

## Overview

The load test simulates N concurrent games of 4 to 8 players through their entire lifecycle:

1. User registration and JWT authentication for all players.
2. Group creation by the admin player and invite-based join by remaining members.
3. WebSocket handshake and STOMP 1.2 authentication over `/ws`, subscribing to group, voice, and song topics.
4. Voice signaling exchange between members over STOMP.
5. Pre-game audio check initiation and member audio share confirmations.
6. Game session start, locking the group and opening session topic subscriptions.
7. Round gameplay loops:
   - DJ link-out retrieval (`/api/sessions/{sessionId}/link-out`).
   - WebRTC song mesh signaling over STOMP (`/app/sessions/{sessionId}/song/signal`).
   - Active player timeline card placements (`/app/sessions/{sessionId}/place`).
   - Other players betting (`/app/sessions/{sessionId}/bet`).
8. Measurement and reporting of:
   - CPU utilization (queried from Actuator `/actuator/metrics/process.cpu.usage`).
   - JVM heap memory usage and allocation (`/actuator/metrics/jvm.memory.used` and `max`).
   - Peak and active STOMP WebSocket connection counts.
   - Latencies (min, mean, p50, p95, p99, max in milliseconds) for each distinct game action.

## Running the Load Test

The script is self-contained and uses Python standard libraries (`asyncio`, `urllib`, `socket`, `ssl`). No third-party packages are required.

### Basic execution

Run against local development services:

```bash
python scripts/loadtest/run_loadtest.py --games 5 --players-per-game 4 --rounds 3
```

### Dry-run validation

Run in mock mode to verify metrics reporting and percentile calculations without contacting a server:

```bash
python scripts/loadtest/run_loadtest.py --mock-dry-run --games 5 --players-per-game 4
```

### Options

- `--games`: Number of concurrent games to run in parallel (default: 5).
- `--players-per-game`: Number of players in each game, between 4 and 8 (default: 4).
- `--rounds`: Number of gameplay rounds to simulate per game (default: 3).
- `--core-url`: Base URL of the core backend service (default: `http://localhost:8080`).
- `--ai-url`: Base URL of the AI microservice (default: `http://localhost:8000`).
- `--report-json`: Optional path to output full metrics as a JSON file.

## Container Sizing and Resource Limits

The load testing baseline establishes the Azure Container Apps resource allocations defined in `scripts/azure/create-container-apps.ps1`:

- **Core Container (`hittiguess-core`)**:
  - CPU: 1.0 vCPU
  - Memory: 2.0 GiB RAM
  - Rationale: Core holds process-local game session state, STOMP broker sessions, and HikariCP connection pools. Peak memory usage under sustained concurrent games remains comfortably below 1.5 GiB, preventing garbage-collection pauses from disrupting real-time round timers.

- **AI Container (`hittiguess-ai`)**:
  - CPU: 0.5 vCPU
  - Memory: 1.0 GiB RAM
  - Rationale: The AI microservice offloads heavy LLM inference to OpenAI and DeepInfra, functioning as an asynchronous FastAPI pipeline. 0.5 vCPU and 1.0 GiB RAM accommodates concurrent metadata resolution and pgvector duplicate lookups.

## Active-Game Admission Limit

To protect container resource ceilings from overload, core enforces an active-game admission limit:

- Configuration property: `game.session.max-active-games` (environment variable `MAX_ACTIVE_GAMES`, default 50).
- When the count of active sessions (`SessionStatus.IN_PROGRESS`) reaches this ceiling, attempts to start a new session (at audio check initiation or game start) are refused immediately with HTTP 503 Service Unavailable:
  ```json
  {
    "status": 503,
    "message": "Server is at maximum game capacity. Please try again shortly."
  }
  ```
