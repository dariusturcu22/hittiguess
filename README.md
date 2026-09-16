# hittiguess

A multiplayer music guessing game, inspired by Hitster. Players hear a song, guess when it was released, and place it on a chronological timeline.

This is `dev`, the active branch. The project is being reworked from a single Spring Boot app into the two-service architecture and full feature set described in [AGENTS.md](AGENTS.md) and [docs/](docs/). The app currently live in production is an earlier, simpler version, frozen on `legacy`. See that branch's own README for what it is and how to run it.

## Docs

Start with [AGENTS.md](AGENTS.md). It links out to the product vision, game design, technical architecture, the story backlog, and the task list.

## Running locally

### Everything at once

Copy `backend/.env.example` to `backend/.env`, `ai/.env.example` to `ai/.env`, and `frontend/.env.example` to `frontend/.env.local`, filling in real values, then set up the AI service's virtualenv (`cd ai && python -m venv .venv` and install its requirements) and run `npm install` in `frontend/`. After that, one command starts Postgres, the backend, the AI service, and the frontend together:

```bash
make dev
# or, without make:
./scripts/dev.sh
```

```powershell
# Windows PowerShell, no Git Bash or make required:
.\scripts\dev.ps1
```

Ctrl+C stops the backend, AI service, and frontend; the database containers keep running (`docker compose -f backend/docker-compose.yml down` to stop them too).

Ports:

| Service | URL |
| --- | --- |
| Frontend | http://localhost:3000 |
| Backend | http://localhost:8080 |
| AI service | http://localhost:8000 |
| Postgres (core) | localhost:5500 |
| Postgres (analytics) | localhost:5501 |

### Running services individually

Backend:

```bash
cd backend
./mvnw spring-boot:run
```

Needs Java 25 and a Postgres instance, either `docker-compose up -d` or your own. Copy `backend/.env.example` to `backend/.env` and fill in real values, or otherwise get its variables into your environment before running.

AI service:

```bash
cd ai
python -m venv .venv
.venv/Scripts/pip install -e .   # .venv/bin/pip on macOS/Linux
.venv/Scripts/python -m uvicorn app.main:app --reload
```

Copy `ai/.env.example` to `ai/.env` and fill in real values.

Frontend:

```bash
cd frontend
npm install
npm run dev
```

Copy `frontend/.env.example` to `frontend/.env.local` and point `NEXT_PUBLIC_API_URL` at the backend.

The `mobile/` Flutter app is standalone and doesn't connect to the backend.
