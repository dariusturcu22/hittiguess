# hitguessr

A multiplayer music guessing game, inspired by Hitster. Players hear a song, guess when it was released, and place it on a chronological timeline.

This is `dev`, the active branch. The project is being reworked from a single Spring Boot app into the two-service architecture and full feature set described in [CLAUDE.md](CLAUDE.md) and [docs/](docs/). The app currently live in production is an earlier, simpler version, frozen on `legacy`. See that branch's own README for what it is and how to run it.

## Docs

Start with [CLAUDE.md](CLAUDE.md). It links out to the product vision, game design, technical architecture, the story backlog, and the task list.

## Running locally

Backend:

```bash
cd backend
./mvnw spring-boot:run
```

Needs Java 25 and a Postgres instance, either `docker-compose up -d` or your own. Environment variables: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `OPENAI_API_KEY`, `YOUTUBE_API_KEY`, `OAUTH2_CLIENT_ID`, `OAUTH2_CLIENT_SECRET`.

Frontend:

```bash
cd frontend
npm install
npm run dev
```

Needs `NEXT_PUBLIC_API_URL` pointing at the backend.

The `mobile/` Flutter app is standalone and doesn't connect to the backend.
