# CLAUDE.md

## Project

`hitguessr` is a multiplayer music guessing game, inspired by Hitster. Players hear a song, guess when it was released, and place it on a chronological timeline. It works with both widely recognized, mainstream music and obscure or niche tracks, and supports both in-person and online play.

## Stack

- Backend, core: Spring Boot (Java). Auth, playlist/song CRUD, game session, WebSocket/STOMP. Owns the database schema.
- Backend, AI microservice: Python + FastAPI. Metadata pipeline, LLM synthesis, embeddings. Calls OpenAI directly.
- Frontend: Next.js (TypeScript), deployed on Vercel.
- Database: PostgreSQL + pgvector. Target host: Azure Database for PostgreSQL Flexible Server.
- Hosting, backend: Azure Container Apps, both services in the same environment.
- Mobile: Flutter, deprioritized.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the full technical breakdown.

## Commands

- Core service: `./mvnw spring-boot:run`, tests: `./mvnw test`
- AI microservice: `uvicorn app.main:app --reload`, tests: `pytest`
- Frontend: `npm run dev`, build: `npm run build`

## Non-negotiable rules

- The DJ is never shown an embedded YouTube player. Playback always happens on the real YouTube page or the real YouTube app. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/DECISIONS.md](docs/DECISIONS.md) for the reasoning.
- Never use unofficial or reverse-engineered APIs for any external service. Official APIs only.
- The AI microservice uses structured output (Pydantic models) for all LLM responses. Never parse LLM output with regex.
- The core service owns the database schema and all migrations. The AI microservice reads and writes data but never alters schema.

## Writing style

This applies everywhere: every markdown file in this repo, chat responses to the person working with you, frontend copy, code comments, commit messages, everything written in natural language.

- No em dashes, and no double-hyphen (`--`) or spaced hyphen (` - `) used as a stand-in for one either. If a sentence wants a dash-style interruption, restructure it into two sentences instead. This has already slipped through as `--` in commit messages and PR descriptions even when the literal em dash character was avoided; watch for the punctuation pattern, not just the character.
- No inflated or overly formal vocabulary. Write the way a person would explain something to a colleague, plainly.
- No filler transition words like "furthermore," "moreover," "additionally."
- Say things directly. If something is a decision that's already been made, state it as a fact, don't re-justify it every time it comes up.
- Never write in the first person, and never put process narration, confessions, opinions, or self-commentary into anything that lands in the repo or on GitHub: code comments, commit messages, PR descriptions, docs, issue text, all of it. Describe what the code does and why, as fact. If something went wrong, say so to the user in chat. It does not belong in a permanent project artifact, ever, no exceptions. This has been violated multiple times: a PR description that opened by confessing a process mistake, a code comment that narrated reasoning about a past version instead of documenting the code as it stands, and commit/PR messages that narrated debugging steps ("verified directly: added a console.log and confirmed...", "Confirmed the fix by tearing down the container and bringing up a fresh one") instead of stating the underlying fact plainly ("the callback never fired, despite the network call resolving" / "the container crash-looped on every start"). A PR's Test Plan checklist is the one place describing verification steps is fine; the Summary section states facts about the code, not what was done to check it.

## Code conventions

- Descriptive names for variables, functions, and classes. Not abbreviated, not vague.
- Write as few comments as possible. Code should be understandable by reading it. Add a comment only when the reasoning genuinely can't be inferred from the code itself, and keep it short.
- No comments that just restate what the line of code already says.

## Git workflow

- Branches: `main`, `dev`, `legacy`. Work only happens on `dev`, through pull requests from `feature/*`, `fix/*`, `chore/*`, or `docs/*` branches. `main` and `legacy` are not touched directly.
- No direct commits to `dev`, `main`, or `legacy`, ever, full stop. This includes documentation, tooling and MCP config (`.mcp.json`, `.claude/`), one-line fixes, and anything that feels too small to bother branching for. If it's a file change, it gets its own branch off `dev` and lands through a PR. There is no exception for "just this once" or "it's not really code." Size or category is never a reason to skip a branch.
- PRs are reviewed on GitHub by the project owner, never merged automatically. Merge only when explicitly told to for a named PR. When merging, use a regular merge, never squash, so the individual commits survive. GitHub auto-deletes the remote branch on merge. Immediately after merging, delete the local copy of the branch.
- Never include a `Co-Authored-By` trailer or any AI-attribution footer on commits or pull requests.
- Commit granularly. Each commit represents one coherent change, not a batch of unrelated changes.

## Task gate

`TASKS.md` is what you work from day to day. `PROJECT_STATE.md` is context, consulted when you need to understand the bigger picture behind a task, not a source of things to do.

Before starting `feature` work on a story, it must be marked `Ready` in [docs/PROJECT_STATE.md](docs/PROJECT_STATE.md), with tasks defined for it in [docs/TASKS.md](docs/TASKS.md). A story can have draft tasks written against it while still marked `Needs Definition`, that alone doesn't unlock work. Only once those tasks have been checked against the real codebase and confirmed accurate does the story move to `Ready`.

If asked to work on a story that's `Needs Definition`, stop and either confirm the existing draft tasks against the real code, or propose a task breakdown if none exists yet. Don't write feature code until that's done.

This gate applies to `feature` work only. `fix`, `chore`, and `docs` branches, including bug fixes listed directly in `TASKS.md`, don't need a story or the `Ready` status.

Any multi-step or batched piece of work, whatever kind of branch it lands on, gets written into `TASKS.md` before the first step starts, not backfilled afterward. An external document, a report, an audit, a plan discussed in conversation, is not a substitute for `TASKS.md`. `TASKS.md` is the historical record of what was planned and what got done, and it only stays accurate if work starts there instead of starting somewhere else and getting written down later, or not at all.

## Documentation

- Product vision and goals: [docs/VISION.md](docs/VISION.md)
- Game rules and mechanics: [docs/GAME_DESIGN.md](docs/GAME_DESIGN.md)
- Technical blueprint: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- Stories and their status: [docs/PROJECT_STATE.md](docs/PROJECT_STATE.md)
- What to actually work on: [docs/TASKS.md](docs/TASKS.md)
- Decision history and reasoning: [docs/DECISIONS.md](docs/DECISIONS.md), append-only, never edit or delete past entries
- Completed stories: [docs/ARCHIVE.md](docs/ARCHIVE.md)

## Session-end habit

Run `python scripts/archive_completed_tasks.py` to move every fully-checked-off `TASKS.md` section into `ARCHIVE.md`. For a story section this only happens once `PROJECT_STATE.md` also has that story's status as `Implemented`, and its row there is removed too; other fully-checked sections (audit batches, dependency upgrades, chore lists) move on their own once every box under them is checked. Append any new architectural decision to [docs/DECISIONS.md](docs/DECISIONS.md).
