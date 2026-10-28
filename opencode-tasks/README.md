# OpenCode task queue (third playtest)

Work through these files one at a time, in numeric order, starting at 01. Each file is self-contained: it names the
files to open, what is wrong today, what to build, and how to test
it. Do not read files a task does not name unless you need them; your context window is small.

## Rules for every task (from AGENTS.md, repeated so you do not need to load it)

Git:
- `main` is the only trunk. Create one branch per task off an up-to-date `main`
  (`git fetch origin && git switch -c <branch> origin/main`). Never commit to `main` or `legacy`.
- Branch prefixes: `fix/`, `feature/`, `chore/`, `docs/`. Each task file names its branch.
- Small focused commits, one coherent change each. Push the branch and open a PR into `main` when the
  task is done and its tests pass. Never merge; the owner merges.
- Never add a `Co-Authored-By` trailer, a "Generated with" footer, or any AI attribution.

Writing (commit messages, PR bodies, comments, docs, UI text):
- No em dashes, no `--`, and no spaced hyphen used as a dash. Split the sentence instead.
- Plain words. No "furthermore", "moreover", "additionally".
- Never first person, and no process narration ("verified by...", "rewrote...", "confirmed that...").
  The subject is the code or the doc: "The refresh now selects songs that never had a duration."
- PR bodies are plain prose paragraphs: no headers, no "Summary"/"Test plan" template, no checklist.

Code:
- Descriptive names, no abbreviations, no single-letter names (loop variables and lambdas included).
- No magic numbers or opaque string codes: name them as constants. A bare index (`items[0]`) or a
  numbered regex group counts too; assign it to a named variable or use a named group.
- As few comments as possible; a comment states a general rule, never a specific song or test case.
- Match the surrounding code. No new dependencies unless the task says so.
- The frontend is a newer Next.js than your training data. Before using a Next API, check
  `frontend/node_modules/next/dist/docs/`. For other libraries, check current docs before coding.

Docs:
- Story 54 in `docs/TASKS.md` lists every task. Tick a task's line in the same PR that finishes it.
- `docs/DECISIONS.md` is append-only: add a new dated entry, never edit or delete a past one.
- When behavior changes, update the doc the task names. Before a PR that touches `docs/TASKS.md`, run
  `python scripts/archive_completed_tasks.py`.

Checks before opening a PR:
- Frontend: `cd frontend && npx tsc --noEmit && npx eslint . && npx vitest run`
- Backend (Windows): `cd backend && set JAVA_HOME=C:\Program Files\Amazon Corretto\jdk25.0.4_8 && mvnw.cmd -q test`
  (Spring app startup only works in WSL Ubuntu; unit and Testcontainers tests work on Windows.)
- AI service: `cd ai && uv run pytest -q`

## Order

| # | File | Branch | Area |
|---|------|--------|------|
| 01 | 01-left-sidebar-spacing.md | fix/left-rail-spacing | frontend |
| 02 | 02-voice-rail-join-slot.md | fix/voice-rail-join-slot | frontend |
| 03 | 03-waveform-five-bars.md | fix/waveform-five-bars | frontend |
| 04 | 04-countdown-clock-sync.md | fix/countdown-clock-sync | backend + frontend |
| 05 | 05-start-session-from-playlist.md | fix/start-session-from-playlist | frontend |
| 06 | 06-page-transitions.md | fix/page-transitions | frontend |
| 07 | 07-spectator-live-drag.md | feature/spectator-live-drag | backend + frontend |
| 08 | 08-game-screen-scale.md | fix/game-screen-scale | frontend |
| 09 | 09-design-proportions-lobby.md | fix/lobby-design-proportions | frontend |
| 10 | 10-design-proportions-gameplay.md | fix/gameplay-design-proportions | frontend |
| 11 | 11-duration-refresh.md | fix/duration-refresh | backend |
| 12 | 12-admin-catalog-origin.md | fix/admin-catalog-origin | backend |
| 13 | 13-musicbrainz-recording-search.md | fix/musicbrainz-recording-search | AI service |
| 14 | 14-wikidata-discogs-matching.md | fix/source-matching | AI service |
| 15 | 15-lock-rule.md | fix/verification-lock-rule | AI service |
| 16 | 16-recheck-catalog.md | chore/recheck-catalog | backend + admin UI |
| 17 | 17-catalog-duplicates.md | fix/catalog-duplicates | backend + admin UI |

`data/year-audit.md` lists the songs in production whose stored year is wrong, from an audit of all
370 catalog songs. Tasks 13 to 16 refer to it.
