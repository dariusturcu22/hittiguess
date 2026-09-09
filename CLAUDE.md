# CLAUDE.md

## Project

`hittiguess` is a multiplayer music guessing game, inspired by Hitster. Players hear a song, guess when it was released, and place it on a chronological timeline. It works with both widely recognized, mainstream music and obscure or niche tracks, and supports both in-person and online play.

## Stack

- Backend, core: Spring Boot (Java). Auth, playlist/song CRUD, game session, WebSocket/STOMP. Owns the database schema.
- Backend, AI microservice: Python + FastAPI. Metadata pipeline, LLM synthesis, embeddings. Calls OpenAI directly.
- Frontend: Next.js (TypeScript), deployed on Vercel.
- Database: PostgreSQL + pgvector. Currently hosted on Supabase; migration target undecided, see docs/PROJECT_STATE.md.
- Hosting, backend: currently Fly.io, migrating away; target platform undecided, see docs/PROJECT_STATE.md.
- Mobile: Flutter, deprioritized.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the full technical breakdown.

## Commands

- Everything at once: `make dev`, starts the local Postgres container, both backend services, and the frontend together.
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
- Never write in the first person, and never put process narration, confessions, opinions, or self-commentary into anything that lands in the repo or on GitHub: code comments, commit messages, PR descriptions, docs, issue text, all of it. Describe what the code does and why, as fact. If something went wrong, say so to the user in chat. It does not belong in a permanent project artifact, ever, no exceptions. This has been violated multiple times: a PR description that opened by confessing a process mistake, a code comment that narrated reasoning about a past version instead of documenting the code as it stands, commit/PR messages that narrated debugging steps ("verified directly: added a console.log and confirmed...", "Confirmed the fix by tearing down the container and bringing up a fresh one") instead of stating the underlying fact plainly ("the callback never fired, despite the network call resolving" / "the container crash-looped on every start"), a PR description that narrated the act of checking something ("Confirms story 9's draft tasks against the real code") instead of just stating the fact that resulted from checking it ("Story 9's draft tasks assumed a DJ view, a session model, and a WebSocket layer, none of which exist yet"), and PR sentences with the subject dropped ("Rewrote the task list", "Drafted task breakdowns", "Moves them there") that read as an implied "I" even without the pronoun. Dropping "I" isn't enough on its own, the grammatical subject has to be the code, file, or doc itself ("The task list now reflects that", "TASKS.md's fully-checked sections are now archived"), the way the repo's own PR history already does it.
- PR descriptions are plain prose paragraphs, matching how every merged PR in this repo's history reads. No `## Summary` / `## Test plan` template, no headers, no checklist. State what changed and why, as fact. Don't note that a PR overlaps files with another open PR or that whichever merges last will need conflict resolution, that's operational narration about the PR process, not a fact about the change itself, leave it out.

## Code conventions

- Descriptive names for variables, functions, and classes. Not abbreviated, not vague. No single-letter variable names anywhere, including loop variables, comprehensions, and lambdas; spell it out even when the scope is one line.
- No magic numbers. A numeric literal that carries meaning (a limit, a threshold, a retry count, a slice length, a delay) gets a named constant, not a bare number inline in the code. This includes string literals used as opaque identifiers or codes (an external API's field code, a status string), not just numbers, the test is whether the reader needs outside knowledge to know what the value means, not whether it's numeric.
- A bare list/array index (`items[0]`, `matches[1]`) or regex capture group number (`match.group(1)`) is a magic number too, indexing into a structure doesn't explain itself any more than a limit or a threshold does. Assign it to a descriptively-named variable before use so the meaning is explicit at the point of extraction, not left for the reader to infer from context. Where the language has a cleaner tool for the same job, use that instead of the raw index: tuple-unpack `sys.argv` rather than index into it, use a named regex capture group (`(?P<name>...)` / `match.group("name")`) rather than a numbered one.
- Write as few comments as possible. Code should be understandable by reading it. Add a comment only when the reasoning genuinely can't be inferred from the code itself, and keep it short.
- No comments that just restate what the line of code already says.
- Comments and docstrings state the general rule or invariant, not a walkthrough of the specific example that led to it. Don't name a particular song, ticket, or test case as illustration inside a comment, that belongs in the commit message or PR description, not permanent code.

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

Every task breakdown in `TASKS.md` must include explicit test tasks alongside the feature tasks: unit tests for new services or functions, integration tests for new endpoints, and any test infrastructure a story is the first to need. A story isn't done when the feature code works, it's done when its own tests exist and pass too. This isn't deferred to story 22 (test coverage). That story backfills tests for code that predates this rule; every story from here on carries its own.

## Documentation

- Product vision and goals: [docs/VISION.md](docs/VISION.md)
- Game rules and mechanics: [docs/GAME_DESIGN.md](docs/GAME_DESIGN.md)
- Technical blueprint: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- Stories and their status: [docs/PROJECT_STATE.md](docs/PROJECT_STATE.md)
- What to actually work on: [docs/TASKS.md](docs/TASKS.md)
- Decision history and reasoning: [docs/DECISIONS.md](docs/DECISIONS.md), append-only, never edit or delete past entries
- Completed stories: [docs/ARCHIVE.md](docs/ARCHIVE.md)
- Sequential implementation order for remaining stories: [docs/ROADMAP.md](docs/ROADMAP.md)
- API contracts, entity model, and state diagrams: [docs/SYSTEM_REFERENCE.md](docs/SYSTEM_REFERENCE.md)
- Frontend content requirements, independent of visual design: [docs/FRONTEND_CONTENT.md](docs/FRONTEND_CONTENT.md)

## Task archiving

Before opening a PR from a branch whose changes touch `TASKS.md`, run `python scripts/archive_completed_tasks.py` to move every fully-checked-off section into `ARCHIVE.md`. For a story section this only happens once `PROJECT_STATE.md` also has that story's status as `Implemented`, and its row there is removed too; other fully-checked sections (audit batches, dependency upgrades, chore lists) move on their own once every box under them is checked. Append any new architectural decision to [docs/DECISIONS.md](docs/DECISIONS.md) at the same time.
