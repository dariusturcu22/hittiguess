#!/usr/bin/env bash
# Spins up the whole app (Postgres, backend, AI service, frontend) with one
# command. Works in any POSIX shell: macOS, Linux, or Git Bash on Windows.
# Windows PowerShell users without Git Bash should use scripts/dev.ps1 instead.
#
# Ctrl+C stops the backend, AI service, and frontend. The database containers
# keep running (docker compose -f backend/docker-compose.yml down to stop them).
#
# Cleanup on Ctrl+C is best-effort. Windows has no real process groups, so
# plain `kill` on the backgrounded job doesn't reach grandchild processes;
# taskkill //T walks the real Windows process tree instead, using the real
# Windows PID looked up through `ps` (Git Bash's own PID for the job, $!, is
# a different number). If something's still running after Ctrl+C: `tasklist`
# for java.exe/python.exe/node.exe and `taskkill //F //T //PID <pid>` on
# whichever is left.
set -euo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [ ! -f backend/.env ]; then
	echo "backend/.env is missing. Copy backend/.env.example to backend/.env and fill in real values first." >&2
	exit 1
fi

echo "Starting Postgres containers..."
docker compose -f backend/docker-compose.yml up -d

pids=()
killtree() {
	local w
	w=$(ps -p "$1" 2>/dev/null | awk 'NR==2{print $4}')
	if [ -n "${w:-}" ] && command -v taskkill >/dev/null 2>&1; then
		taskkill //F //T //PID "$w" >/dev/null 2>&1 || true
	else
		kill "$1" 2>/dev/null || true
	fi
}
cleanup() {
	echo
	echo "Stopping backend, AI service, and frontend..."
	for pid in "${pids[@]}"; do
		killtree "$pid"
	done
}
trap cleanup EXIT INT TERM

(
	cd backend
	set -a
	. ./.env
	set +a
	./mvnw spring-boot:run
) &
pids+=("$!")

(
	cd ai
	if [ -x .venv/Scripts/python.exe ]; then
		py=.venv/Scripts/python.exe
	else
		py=.venv/bin/python
	fi
	"$py" -m uvicorn app.main:app --reload
) &
pids+=("$!")

(
	cd frontend
	./node_modules/.bin/next dev
) &
pids+=("$!")

cat <<'EOF'

hittiguess is starting up. Give it a minute for all three to finish booting.

  frontend   http://localhost:3000
  backend    http://localhost:8080
  ai         http://localhost:8000
  postgres   localhost:5500 (core), localhost:5501 (analytics)

Press Ctrl+C to stop the backend, AI service, and frontend.
EOF

wait
