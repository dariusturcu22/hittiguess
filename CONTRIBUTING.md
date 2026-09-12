# Contributing to hittiguess

hittiguess is a multiplayer music guessing game with a Spring Boot core backend, a Python/FastAPI AI microservice, and a Next.js frontend. This document covers how to get the project running locally, how the branch and pull request workflow works, and how to pick up a piece of work from the task list.

## Local setup

Clone the repository and check out `dev`, the active development branch. `main` and `legacy` are not where development happens; see the root `README.md` for what each branch is.

You'll need:

- Java 25, for the core backend
- Python, for the AI microservice
- Node.js, for the frontend
- Docker, for the local Postgres container

Copy the example environment files and fill them in:

```bash
cp backend/.env.example backend/.env
cp ai/.env.example ai/.env
cp frontend/.env.example frontend/.env.local
```

See "Secrets and API keys" below for what values each of these actually needs.

`ai/.env.example` lists every environment variable the AI microservice's codebase reads, including ones tied to sources and providers that are not part of the current pipeline (Discogs, DeepInfra, Wikidata/Wikipedia bot logins, various spike-only providers). You do not need values for any of those to run the app locally; leave them blank.

Once both `.env` files are filled in, `make dev` starts everything: the local Postgres container, the core service, the AI microservice, and the frontend, all together. Stop it with Ctrl+C.

To run one service at a time instead:

```bash
./mvnw spring-boot:run     # core service, from backend/
uvicorn app.main:app --reload   # AI microservice, from ai/
npm run dev                # frontend, from frontend/
```

Each service has its own test suite:

```bash
./mvnw test    # core service, from backend/
pytest         # AI microservice, from ai/
```

Run the relevant suite before opening a pull request.

## Secrets and API keys

A new contributor needs sandbox-safe values for three variables, all cheap or free to obtain:

- **`YOUTUBE_API_KEY`** (`ai/.env`): a personal Google Cloud project with the YouTube Data API v3 enabled, and an API key generated from it. The free daily quota is enough for local development; nothing in this project's local dev flow burns through it.
- **`OPENAI_API_KEY`** (`ai/.env`): a personal OpenAI API key. Set a low spending cap or hard limit on the account before using it, since this key gets billed per call by the metadata pipeline's LLM synthesis step.
- **`INTERNAL_SERVICE_API_KEY`** (`backend/.env` and `ai/.env`): not an external credential. It's a shared secret the core service and the AI microservice use to authenticate requests to each other. Pick any string yourself and use the same value in both files.

`DB_PASSWORD` and `JWT_SECRET` (`backend/.env`) are also arbitrary values you choose yourself, not external credentials.

No other variable in either `.env.example` file is required to run the app locally.

## Branch and pull request workflow

All work happens on `dev`, through pull requests. Nobody commits directly to `dev`, `main`, or `legacy`, regardless of how small the change is.

Branch off `dev` using one of these prefixes, matching the kind of change:

- `feature/*` for new functionality
- `fix/*` for bug fixes
- `chore/*` for maintenance work that isn't a feature or a fix
- `docs/*` for documentation-only changes

Commit granularly: each commit should represent one coherent change, not a batch of unrelated edits. Write commit messages and PR descriptions as plain statements of fact about what changed and why, in prose paragraphs, not first person and not narrating the process of getting there. See the PR template for the expected shape.

Open a pull request against `dev` when the work is ready for review. The project owner reviews and merges; pull requests are never merged automatically. Only one pull request from a given batch of planned stories is open for review at a time; if you're picking up the next story in a batch, do the work locally on its own branch, but hold off opening that pull request until the current one merges.

## Picking up a story from TASKS.md

`docs/TASKS.md` is the actual day-to-day work list. `docs/PROJECT_STATE.md` is background, useful for understanding a story's context, not a source of things to pick up.

Before starting **feature** work on a story, check its status in `docs/PROJECT_STATE.md`:

- **Ready** means the story has tasks defined in `TASKS.md` that have been checked against the real codebase, and it can be worked on.
- **Needs Definition** means the story isn't ready for feature work yet, even if draft tasks already exist for it. If you want to work on a story in this state, the first step is confirming its draft tasks against the real code (or writing a task breakdown if none exists), not writing feature code.

This gate only applies to `feature` branches. `fix`, `chore`, and `docs` branches, including bug fixes listed directly in `TASKS.md`, don't need a story or a `Ready` status to start.

Any multi-step or batched piece of work gets written into `TASKS.md` before the first step starts. A plan discussed in a pull request description or an issue isn't a substitute for it.

Every task breakdown for a story includes its own test tasks: unit tests for new services or functions, integration tests for new endpoints, and any test infrastructure the story is the first to need. A story isn't done when the feature code works; it's done when its tests exist and pass too.

## Code style

- Use descriptive names for variables, functions, and classes. No abbreviations, no single-letter names, even for loop variables or short lambdas.
- Avoid magic numbers and magic strings. A numeric or string literal that carries meaning (a limit, a threshold, a status code) gets a named constant instead of a bare literal inline.
- Keep comments minimal. Code should be understandable by reading it; add a comment only when the reasoning genuinely isn't obvious from the code itself, and keep it short. Comments describe the general rule or invariant, not a specific example or test case.

## Writing style

This applies to everything written in natural language in this repository: documentation, code comments, commit messages, and pull request descriptions.

- No em dashes. No double hyphens or spaced hyphens used as a substitute for one either.
- Plain, direct language. No inflated vocabulary, no filler transition words like "furthermore" or "additionally."
- State facts plainly. If something is a decision that's already been made, say so, without re-justifying it every time it comes up.
- Never write in the first person, and never include process narration or self-commentary in anything that lands in the repository: no "I fixed," no "confirmed by adding a console.log," no describing the act of checking something instead of stating what was found. Describe the code and the change as they stand.
- Pull request descriptions are plain prose paragraphs. No `## Summary` or `## Test plan` headers, no checklists. State what changed and why.
