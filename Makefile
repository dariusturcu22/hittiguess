SHELL := bash
.SHELLFLAGS := -c

.PHONY: dev db backend ai frontend

# Starts the local Postgres container, the core service, the AI microservice,
# and the frontend together. Ctrl+C stops all three app processes; the DB
# container keeps running (see `docker compose down` in backend/).
dev: db
	trap 'kill $$(jobs -p) 2>/dev/null' EXIT INT TERM; \
	$(MAKE) backend & \
	$(MAKE) ai & \
	$(MAKE) frontend & \
	wait

db:
	docker compose -f backend/docker-compose.yml up -d

backend:
	cd backend && ./mvnw spring-boot:run

ai:
	cd ai && .venv/Scripts/python.exe -m uvicorn app.main:app --reload

frontend:
	cd frontend && npm run dev
