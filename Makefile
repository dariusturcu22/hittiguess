SHELL := bash
.SHELLFLAGS := -c

.PHONY: dev db backend ai frontend

# Starts the local Postgres containers, the core service, the AI microservice,
# and the frontend together, via scripts/dev.sh (the single source of truth
# for this; PowerShell users without bash/make run scripts/dev.ps1 directly).
dev:
	./scripts/dev.sh

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
