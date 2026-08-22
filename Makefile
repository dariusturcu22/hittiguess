SHELL := bash
.SHELLFLAGS := -c

.PHONY: dev db backend ai frontend

# Starts the local Postgres container, the core service, the AI microservice,
# and the frontend together. Ctrl+C stops all three app processes; the DB
# container keeps running (see `docker compose down` in backend/).
#
# Cleanup on Ctrl+C is best-effort, not guaranteed. Windows has no real
# process groups, so plain `kill` on the backgrounded job doesn't reach
# grandchild processes; taskkill //T walks the real Windows process tree
# instead, using the real Windows PID looked up through `ps` (Git Bash's own
# PID for the job, $!, is a different number). This works reliably in
# isolated testing, but Git Bash's signal emulation on Windows occasionally
# misses one of the three under real Ctrl+C. If something's still running
# after Ctrl+C: `tasklist` for java.exe/python.exe/node.exe and `taskkill //F
# //T //PID <pid>` on whichever is left.
dev: db
	killtree() { \
		w=$$(ps -p "$$1" 2>/dev/null | awk 'NR==2{print $$4}'); \
		[ -n "$$w" ] && taskkill //F //T //PID "$$w" >/dev/null 2>&1; \
		true; \
	}; \
	trap 'killtree $$bpid; killtree $$apid; killtree $$fpid' EXIT INT TERM; \
	$(MAKE) backend & bpid=$$!; \
	$(MAKE) ai & apid=$$!; \
	$(MAKE) frontend & fpid=$$!; \
	wait

db:
	docker compose -f backend/docker-compose.yml up -d

# Spring Boot doesn't load .env files itself, only an IDE plugin (e.g.
# IntelliJ's EnvFile) does that automatically; a plain shell needs it sourced.
backend:
	cd backend && set -a && . ./.env && set +a && ./mvnw spring-boot:run

ai:
	cd ai && .venv/Scripts/python.exe -m uvicorn app.main:app --reload

# Calls next directly instead of npm run dev; npm spawns it through an extra
# cmd.exe layer on Windows that detaches from the parent process tree, which
# breaks taskkill //T in the dev target above and leaves node running after
# Ctrl+C.
frontend:
	cd frontend && ./node_modules/.bin/next dev
