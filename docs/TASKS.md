# TASKS.md: What To Actually Work On

This is the source of truth for day-to-day work. Consult PROJECT_STATE.md only when you need the bigger picture behind one of these.

The tasks below, under stories 9 and 12, are drafts and have not yet been confirmed against the real implementation, except where noted. Before starting any of them, check them against the current code: some tasks may already be done, some may not apply the way they're written, and some may be missing. Once a story's tasks are confirmed accurate, update its status to Ready in PROJECT_STATE.md.

Stories 10, 11, and 39 were checked against the real code: no `Group`, `Session`, `Round`, `Guess`, or WebSocket/STOMP code exists anywhere in the backend, so their draft tasks stand as accurate greenfield work. Marked Ready in PROJECT_STATE.md.

Story 9 and story 12 were checked against the real code and confirmed blocked: both assume a group (story 39), a game session (story 10), and a WebSocket layer (story 11) that don't exist yet. Neither can move to Ready until 10, 11, and 39 do.

"Next available task" means the earliest unchecked box under a Ready or In Progress story.

## Story 9: DJ real YouTube link-out

Confirmed against the real code: there's no DJ view, no group, no session concept, and no WebSocket layer today, so this is new work, not a removal. The QR code task was split out and done separately, see `ARCHIVE.md`'s Bug fixes entry. Blocked on story 39 (group), story 10 (game session), and story 11 (WebSocket sync).

- [ ] Build the DJ view: an "open in YouTube" link-out for remote sessions, opening a new browser tab, never an embedded player
- [ ] Wire WebRTC tab audio capture to that new tab and stream it to the other players
- [ ] Add deep-link handling for in-person sessions (Android intent, iOS universal link, fallback to a plain browser link)
- [ ] Wire the manual reveal trigger over WebSocket, any player can reveal once the song has played

## Story 10: Game session

Checked against real code: no session model exists, this is greenfield work. Based on the `GameSession` shape and round flow in `ARCHITECTURE.md`, and the round/token/reconnect rules in `GAME_DESIGN.md`.

- [ ] Implement `GameSession`, `Player`, `Round`, and `Guess` as ephemeral Postgres rows, purged when the session ends
- [ ] Initialize a session from the group's current settings when the admin starts it (playlist(s), DJ mode, win-condition card count), snapshotting the group's connected members as the roster
- [ ] Assign round 1's active player and DJ
- [ ] Round rotation: active player rotates each round, DJ stays fixed or rotates per the group's setting, skipping players marked `Left`
- [ ] Guess placement and lock-in: before/after/between on the active player's timeline, with a lock-in sound effect
- [ ] 3-5 second countdown after lock-in, then a 15-second betting window; skip the window entirely if no player holds a token
- [ ] Betting: token-holding players may bet during the window, first come first served, concurrency-safe so only the first bet is accepted and a losing attempt doesn't cost a token; a skip-betting action ends the window early
- [ ] Artist/title guess box, available to the active player for the whole turn, independent of timeline placement; a fully correct guess awards a token (matching tolerance is an open question, see `PROJECT_STATE.md`)
- [ ] Scoring: apply the four outcome rules in `GAME_DESIGN.md` (correct placement keeps the card even on a tied release year; a correct guess beats any bet; a wrong guess with a correct bet gives the card to the bettor; a wrong guess with no bet discards it)
- [ ] Win condition: first player to reach the group's configured card count wins, bounded 5-20 for a 2-3 player group or 5-15 for a 4-8 player group
- [ ] Player disconnect: mark `isConnected` false, leave timeline/tokens/turn order untouched
- [ ] Player explicit leave: mark `Left`, exclude from future turns and DJ rotation, existing timeline cards still count toward the final results
- [ ] Active-player turn timeout: if the active player is disconnected when their turn comes, or disconnects mid-turn, auto-skip after 90 seconds and mark them `Left`
- [ ] Auto-abandon the session after 10 minutes with zero connected players, no results export in that case
- [ ] Downloadable results export when a session completes normally
- [ ] Purge all session state (roster, rounds, guesses) once the session ends or is abandoned, hand control back to the group
- [ ] Frontend: drag-and-drop timeline placement, cards animate apart to open a gap with no overlap, animate back into place once placed
- [ ] Frontend: artist/title guess box gives immediate animated feedback, a correct guess animates a token dropping into the player's count, distinct animation for incorrect

Tests:
- [ ] Unit tests for scoring: all four outcome rules, including the tied-release-year case
- [ ] Unit tests for win-condition bounds: 5-20 (2-3 players) and 5-15 (4-8 players), including the boundary values
- [ ] Unit tests for round rotation, both fixed and rotating DJ settings, and rotation skipping `Left` players
- [ ] Unit tests for the active-player turn timeout, including the boundary at 90 seconds
- [ ] Integration test: full session lifecycle, admin starts, roster snapshot, several rounds, win condition hit, results export generated, state purged
- [ ] Integration test: auto-abandon path, session torn down after 10 minutes with zero connected players, confirms no export is generated
- [ ] Integration test: betting concurrency, multiple simultaneous bet attempts on the same guess, exactly one accepted, no token lost by the others
- [ ] Integration test: betting window skipped entirely when no player holds a token
- [ ] Integration test: player disconnects mid-turn, doesn't reconnect within 90 seconds, ends up `Left`, and a later reconnect attempt after that point doesn't restore active status

## Story 11: Real-time game sync over WebSocket

Checked against real code: no WebSocket layer exists, this is greenfield work. Based on the sync model in `ARCHITECTURE.md` (REST for group/session creation and join, WebSocket for state changes). Covers both the group and the game session, not just the session.

- [ ] Add the Spring WebSocket/STOMP dependency and base config to the core service
- [ ] Authenticate the WebSocket handshake against the existing JWT auth
- [ ] Define per-group STOMP destinations for broadcast (membership, settings changes, chat, voice signaling) and a client-to-server channel for admin actions
- [ ] Define per-session STOMP destinations for broadcast (round events) and a client-to-server channel for actions (guess, bet, reveal)
- [ ] Broadcast group events: member joined/left, settings changed, game session started
- [ ] Broadcast round events: round started, guess locked, bet placed, reveal triggered, round scored, next round
- [ ] Handle disconnect: mark the member's or player's `isConnected` flag false without ending the group or the session
- [ ] Wire group creation/join (story 39) to register the joining client on the group's topic, and game session start (story 10) to register on the session's topic
- [ ] Frontend: keep the group/session WebSocket connection alive while navigating to other parts of the app, minimize the game to a small persistent widget instead of requiring the player stay on the game screen
- [ ] Frontend: turn notification, a sound plus a clickable visual banner when it's the player's turn and the game screen isn't focused, clicking either returns them to the game

Tests:
- [ ] Integration test: WebSocket connection and session state survive navigating away from the game route and back
- [ ] Integration test: turn notification fires when the player's turn starts while they're on a different route, and doesn't fire when they're already on the game screen
- [ ] Unit tests for the disconnect handler: flag flips without ending the group or session, for both a group member and an in-session player

## Story 12: Voice chat

Blocked on story 11 (WebSocket layer) and story 39 (group): voice is scoped to the group's lifetime, not the game session's, and its signaling rides the WebSocket layer, neither exists yet.

- [ ] Implement WebRTC signaling over the WebSocket layer built in story 11
- [ ] Implement mesh peer connection setup between group members
- [ ] Enforce the 8-participant cap per group
- [ ] Integrate Cloudflare TURN, pay-as-you-go, as the ICE server fallback
- [ ] Add join/leave voice UI, joinable and leavable at any time, not tied to starting a call
- [ ] Frontend: persistent, collapsible right-hand sidebar, vertically stacked circular avatars with names, speaking indicator ring, mute/deafen icon overlays, a trailing join-call button; visible with no speaking indicators when not in the call
- [ ] Frontend: leave animation on a participant departing, remaining avatars animate into the gap
- [ ] Frontend: sidebar stays available during story 11's minimized "playing while away" widget state

Tests:
- [ ] Unit tests for the 8-participant cap, including the boundary
- [ ] Integration test: TURN fallback engages when a direct peer connection fails
- [ ] Integration test: join/leave voice at arbitrary times, independent of whether a game session is active

## Story 13: Group-scoped text chat

Checked against real code: no chat model or endpoint exists. Blocked on story 11 (WebSocket layer) and story 39 (group): chat is scoped to the group's lifetime, not the game session's, and rides the WebSocket layer, neither exists yet.

- [ ] Implement `ChatMessage` as an ephemeral Postgres row (sender, group, body, timestamp)
- [ ] Client-to-server STOMP channel to send a message, riding the WebSocket layer built in story 11
- [ ] Broadcast new messages to the group's STOMP topic
- [ ] Load message history when a client joins or reconnects to a group
- [ ] Purge chat history when the group is deleted, matching the group's ephemeral lifecycle
- [ ] Message length limit (500 characters) and a per-user send rate limit (5 messages per 10 seconds) to prevent spam within a group
- [ ] Frontend: semi-transparent bottom-left overlay, toggled by a keybind or a clickable button, rather than a persistent input field, plain username-and-message lines, no threading (see `GAME_DESIGN.md`'s Interaction and animation section)

Tests:
- [ ] Unit tests for the message length limit and the per-user send rate limit, including the boundary values
- [ ] Integration test: message history loads correctly on join and on reconnect
- [ ] Integration test: chat history is gone once the group is deleted

## Story 39: Group

Checked against real code: no group model exists, this is greenfield work. Based on `ARCHITECTURE.md`'s Group shape and lifecycle, and `GAME_DESIGN.md`'s Groups section.

- [ ] Implement `Group` and `Member` as ephemeral Postgres rows; `Member` carries a per-group display name and avatar, separate from the user's account profile
- [ ] `POST` endpoint to create a group; creator becomes admin
- [ ] Enforce one active group membership per user
- [ ] Generate a unique 4-letter join code alongside the existing invite link when a group is created
- [ ] `POST` endpoint to join a group via invite link or join code, only while the group hasn't started a game session yet
- [ ] On join, prompt for a per-group display name and avatar, defaulting to the user's account values but editable; other members only ever see this per-group identity, never the account profile
- [ ] Settings (playlist(s), DJ mode, win-condition card count), editable by the admin only, broadcast to all members in real time
- [ ] Chat available from group creation, stored for the life of the group
- [ ] Voice joinable and leavable at any time (see story 12 for the WebRTC mechanics)
- [ ] 30-minute timer from group creation to the admin starting a game session, delete the group if it fires
- [ ] Admin action to start a game session (see story 10), locks the group to new members
- [ ] 30-minute timer from a game session ending to the admin starting another, delete the group and remove every member if it fires
- [ ] Explicit leave vs. disconnect: disconnect only flips `isConnected`, explicit leave removes membership
- [ ] Admin explicitly leaves: promote the next-earliest-joined member to admin, or delete the group if none remain
- [ ] Admin action to voluntarily promote another member to admin at any time, independent of leaving
- [ ] On app load, check the logged-in user's active group membership and prompt to return or leave, no link-based reconnect
- [ ] Frontend: visually mark the admin, a crown icon, distinct from regular members

Tests:
- [ ] Unit tests for join-code generation: uniqueness, and the 4-letter format
- [ ] Unit tests for the one-active-group-per-user constraint
- [ ] Unit tests for per-group profile isolation: a member's account profile is never exposed through group-scoped endpoints, only their per-group identity
- [ ] Unit tests for admin transfer: both the explicit-promote action and the auto-promote-on-leave path, including the no-members-remain deletion case
- [ ] Integration test: full group lifecycle, create, join via both invite link and join code, settings broadcast live, admin starts a session, group locks to new members
- [ ] Integration test: both 30-minute timers, pre-session and between-sessions, including that they don't fire early or fail to fire
- [ ] Integration test: explicit leave removes membership while disconnect only flips the connection flag

## Story 23: Song schema reconciliation

Checked against real code and `ARCHITECTURE.md`'s target shape (line 36): `Song` today has a single `releaseYear` int, a single `songTag` enum, a single `artist` string, and no `verificationStatus`, `confidence`, or `metadataRaw` fields. No migration tool exists yet, schema changes today happen only through Hibernate's `ddl-auto=update`; this story introduces Flyway rather than add another layer of auto-DDL.

Two parts of the target shape are genuinely undecided, not just unconfirmed against code, so this story doesn't cover them yet:
- Whether release year should be two fields (`submittedYear`, immutable, and `verifiedYear`, null until verification) or one mutable field plus `verificationStatus`. The two-field version preserves what was originally submitted even after a correction, useful for auditing bad sources over time, closer in spirit to why `metadataRaw` exists at all. The one-field version is simpler. Neither is chosen.
- How multiple artists are stored and guessed. A song can have a main artist plus one or more featured artists (for example, an "artist A feat. artist B" credit); today's single `artist` string can't represent that, and it's undecided whether featured artists need to be guessed correctly too for a round to count as correct, whether storage should be an array of artist entries, and what the guess-box UI looks like for more than one artist (multiple text boxes, or something else). Affects story 10's artist/title guess box, this story's schema, and the AI microservice's extraction logic, none of which assume multiple artists today.

- [ ] Introduce Flyway as the schema migration tool
- [ ] Add a `verificationStatus` field, `UNVERIFIED`/`VERIFIED` as a placeholder pair pending story 18
- [ ] Persist `confidence` on `Song`, depends on the `SongMetadataResponse` fix below existing first
- [ ] Persist `metadataRaw`, the full pipeline output, for auditability
- [ ] Replace the single `songTag` enum with a multi-value `tags` relation
- [ ] Data migration for existing rows: default `verificationStatus`
- [ ] Update `SongDTO`, `CreateSongRequest`, `UpdateSongRequest`, and regenerate the frontend's orval client and song forms for the new shape

Tests:
- [ ] Unit tests for the data migration: `verificationStatus` defaulted correctly for existing rows
- [ ] Integration test: existing API responses (`SongDTO`) don't break for rows migrated from the old shape

## Bug fixes

No story required for these. Fix on a `fix` branch.

- [ ] `SongMetadataResponse` (Java) silently drops the AI microservice's `confidence`, `source`, and `reasoning` fields: `SongMetadataResult` (Python) computes and returns all three today, but the Java record deserializing that response only declares `title/artist/releaseYear/gradientColor1/gradientColor2`, so the other three are read off the wire and discarded on every metadata call. Extend the record to keep them.

## Story 22: Test coverage

Checked against real code: the backend has exactly one test file, an empty `contextLoads()` smoke test, zero controller/service/security coverage. The AI microservice has unit tests only for pure functions (`llm.synthesize`, `prompt.build`, `sources/util.py` helpers), nothing for `router.py`, `service.py`'s orchestration, or `auth.py`. The frontend has no test runner installed at all. `.github/workflows/pr-checks.yml` runs `mvnw compile` and `npm run lint && npm run build`, no test execution step for either service, and no job at all for the AI microservice, so even its existing pytest tests never run in CI today.

- [ ] Add a CI job for the AI microservice (none exists today) running its existing `pytest` suite
- [ ] Add a `mvnw test` step to the backend CI job (currently compile-only)
- [ ] Add JUnit/Mockito tests for every backend service (`PlaylistService`, `SongMetadataService`, `UserService`, `AuthService`, `ExportService`), covering the access-control checks in `PlaylistService`, the rate limiter in `SongMetadataService`, and the account-enumeration-avoidance logic in `AuthService`
- [ ] Add `@WebMvcTest`/MockMvc tests for every controller
- [ ] Add a Spring Security test covering JWT auth, refresh-token rotation, and CSRF
- [ ] Add tests for `ai/app/metadata/router.py`, `service.py`'s orchestration, and `auth.py`'s internal-key check, using FastAPI's `TestClient`
- [ ] Add a frontend unit test runner (Vitest or Jest, neither installed today) plus React Testing Library, and a `test` script in `package.json`
- [ ] Add frontend unit tests for the song forms' hand-written validation (`AddSongForm.tsx`, `SongForm.tsx`) and the auth forms
- [ ] Add Playwright for frontend integration/end-to-end tests, none exist today; separate from the unit test runner above, drives the real browser against the real backend rather than mocking it
- [ ] Add Playwright coverage for the core flows that exist today: login/register, playlist CRUD, song add/edit, export
- [ ] Add the new test steps to `.github/workflows/pr-checks.yml` for all three services

## Story 27: Rate limiting

Checked against real code: the only rate limiting anywhere is `SongMetadataService`'s single in-flight-request-per-user gate on `/api/metadata/song`, a `ConcurrentHashMap`-backed set, not a time-window limiter. No rate-limiting library (Bucket4j, resilience4j) exists in `pom.xml`. `/auth/login` and `/auth/register` have no rate limiting at all today.

- [ ] Add a rate-limiting library (Bucket4j is the standard Spring choice) to `pom.xml`
- [ ] Add per-user or per-IP request-window rate limits across public-facing endpoints, not just the existing single in-flight gate
- [ ] Rate-limit `/auth/login` and `/auth/register` specifically, to blunt credential-stuffing and enumeration attempts
- [ ] Standardize the 429 response shape; the metadata endpoint's current 429 uses Spring's default `ProblemDetail`, not the app's own `ErrorResponse` record used elsewhere in `GlobalExceptionHandler`
- [ ] Rate-limit the AI microservice's `/metadata/resolve` endpoint directly, not just the core service's call into it, since anything holding the shared `X-Internal-Api-Key` secret can call it directly

Tests:
- [ ] Unit tests for the rate limiter: requests under the limit pass, requests over the limit get rejected, including the boundary value
- [ ] Integration test: `/auth/login` and `/auth/register` rate limiting specifically
- [ ] Integration test: the AI microservice's `/metadata/resolve` rate limit triggers independent of the core service's own limiting

## Story 36: Open-source collaboration readiness

- [ ] Add `CONTRIBUTING.md`: local dev setup (`make dev`), the branch/PR workflow already defined in `CLAUDE.md` written for an external audience, how to pick up a story from `TASKS.md`
- [x] Add `LICENSE`: MIT, chosen over AGPLv3/BSL since there's no revenue or scale to protect, and MIT is the stronger signal for a portfolio project, zero friction for anyone evaluating the code
- [ ] Add `CODE_OF_CONDUCT.md`
- [ ] Add GitHub issue templates (bug report, feature request) and a PR template matching the repo's actual PR description style (plain prose, no `## Summary`/`## Test plan`, see `CLAUDE.md`'s writing-style rules)
- [ ] Document which secrets a new contributor needs (`YOUTUBE_API_KEY`, `OPENAI_API_KEY`, `INTERNAL_SERVICE_API_KEY`) and how they get sandbox-safe values, since both external API keys carry real cost/quota implications

## Story 37: Privacy policy, terms of service, and GDPR compliance

Checked against real code: `DELETE /me` (`UserController` → `UserService.deleteUser()`) already does a real hard delete of the `User` row, not a deactivation, but hasn't been checked for what happens to `Song.addedBy` references or shared playlists on deletion. No analytics exist yet (story 34), so there's nothing to disclose there until it ships.

- [ ] Draft a privacy policy covering what's actually collected today: auth data (username, email, OAuth provider ID), playlist/song data
- [ ] Draft terms of service
- [ ] Add a GDPR data-export endpoint: a logged-in user can download their own account, playlist, and song data
- [ ] Audit and harden the existing `DELETE /me` flow for `Song.addedBy` references and shared-playlist edge cases, so account deletion doesn't leave orphaned references or unexpectedly delete other members' shared playlists
- [ ] Add a cookie/consent notice, only needed once story 34 (first-party analytics) ships; skip until then since no third-party trackers are planned

Tests:
- [ ] Integration test: GDPR export endpoint returns the user's complete account, playlist, and song data
- [ ] Integration test: `DELETE /me` with existing `Song.addedBy` references and shared-playlist memberships behaves per the decided handling, no orphaned references, no other member's playlist unexpectedly deleted

## Story 38: Observability

Checked against real code: no Spring Boot Actuator dependency exists in `pom.xml`, no health-check endpoint exists today. The backend already uses SLF4J logging (from the story-6-era audit fixes), but there's no request-id/correlation-id to trace one user action across both services. The AI microservice swallows every pipeline and OpenAI failure into a generic `status="ERROR"` response with no alerting.

Goes deeper than a minimal setup, deliberately: metrics, logs, and traces together (Prometheus, Loki, Tempo), not just health checks and error tracking. All consumed through Grafana Cloud's free tier rather than self-hosted, self-hosting any of these means an always-on VM that doesn't fit the project's whole-deployment cost ceiling (see `DECISIONS.md`), while the free tier covers this project's scale at $0. Instrumentation itself is OpenTelemetry, the vendor-neutral standard, so nothing here locks the project into Grafana Cloud specifically.

- [ ] Add Spring Boot Actuator to the core service for health/metrics endpoints, expose `/actuator/prometheus`
- [ ] Add an equivalent health endpoint to the AI microservice (FastAPI has none today), expose metrics via `prometheus-fastapi-instrumentator`
- [ ] Set up a Grafana Cloud free-tier account, point both services' Prometheus metrics at it
- [ ] Add OpenTelemetry auto-instrumentation to both services for distributed tracing, viewable in Grafana Cloud's Tempo
- [ ] Ship both services' structured logs to Grafana Cloud's Loki
- [ ] Add error tracking (Sentry, free tier) to both services
- [ ] Add a request-id/correlation-id filter so one user action can be traced across both services' logs, and correlates with the OpenTelemetry trace for the same request
- [ ] Build a basic Grafana dashboard: request rate, error rate, latency percentiles for both services
- [ ] Add uptime monitoring for the production deployment
- [ ] Surface the AI microservice's per-source fetch failures and OpenAI call failures as visible alerts, rather than only the generic swallowed `status="ERROR"` response
- [ ] Add a periodic check against Grafana Cloud's and Sentry's free-tier usage limits, so approaching them is noticed before either starts silently dropping data or asking for payment

Tests:
- [ ] Integration test: Actuator health endpoint reports correctly both when healthy and when a dependency (the database) is down
- [ ] Integration test: a request-id set on an incoming request propagates through a core-service-to-AI-service call, appears in both services' logs, and correlates with a single OpenTelemetry trace
- [ ] Integration test: a metrics scrape and a log line both actually reach Grafana Cloud in a real (non-mocked) call
