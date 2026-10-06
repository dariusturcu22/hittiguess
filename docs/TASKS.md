# TASKS.md: What To Actually Work On

This is the source of truth for day-to-day work. Consult PROJECT_STATE.md only when you need the bigger picture behind one of these.

Before starting any task, check it against the current code: some tasks may already be done, some may not apply the way they're written, and some may be missing. Once a story's tasks are confirmed accurate, update its status to Ready (or Implemented, once its own backend batch is actually done) in PROJECT_STATE.md.

Completed backend records are in [ARCHIVE.md](ARCHIVE.md); [PROJECT_STATE.md](PROJECT_STATE.md) retains stories with active follow-ups. Story 28 owns the remaining frontend requirements and verification against those backends. Completed marketing, theme, scrollbar, and LAN fix batches are archived.

"Next available task" means the earliest unchecked box under a Ready or In Progress story.

## Standing policy: all frontend work lives in story 28

Every story other than story 28 is backend-only. Any frontend task a story would otherwise carry (a page, a component, a WebRTC/browser-side piece, a frontend test) is tracked under story 28's implementation phase instead, not built in that story's own batch. Story 28 is the single place all frontend lands, wired against the real backends every prior batch shipped. Frontend tasks already written inline under other stories stay listed there marked "story 28" for traceability, but they are not part of that story's own batch completion; a backend story is done when its backend code and backend tests pass.

## Standing policy: story 34 abuse-visibility event writes are stubbed until story 34 ships

Stories that would write an abuse-visibility event (story 41's flagged-injection event, story 17's report-submitted event, story 27's rate-limit-exceeded event) write a stubbed no-op (a structured log line marked `TODO: story 34`) rather than a real event, since story 34's analytics event pipeline is Phase 3 and not built. Story 34 replaces these stubs with real writes. See `DECISIONS.md`.

## Story 30: Difficulty-tuned game session generation

Completed generation, selection, public-playlist, and lobby integration work is recorded in [ARCHIVE.md](ARCHIVE.md#story-30-completed-backend-implementation). Personalized training, retraining, monitoring, and their tests remain open. The historical-data conflict in the documentation audit remains unresolved.

- [ ] Train the personalized collaborative-filtering model on accumulated `Guess` data (story 10) once there's enough of it to evaluate (scaffolded: `PersonalizedDifficultyPredictor` is the plug point, `AggregateBaselinePredictor` is the shipped baseline; blocked until real play accumulates enough guesses to train and beat the baseline, likely months of casual play at the target scale)
- [ ] Add a scheduled retraining job for the personalized model
- [ ] Add a monitoring check comparing the personalized model's prediction accuracy against the simple aggregate baseline; if the personalized model stops beating the baseline, that's the signal it's stale and needs retraining, not just a fixed schedule

Tests:

- [ ] Unit tests for the personalized model's predictions against a held-out set of real guesses (deferred with the model itself; no trained model or real held-out guesses exist yet)
- [ ] Integration test: the retraining job runs and the monitoring check correctly flags a model that's stopped beating the baseline

## Story 34: First-party usage analytics

Story 42 owns the explicit domain boundary this story reads and writes against: the transactional `GameSession`/`Round`/`Guess` rows this story's game-history task reads a summary from stay in the core database and purge exactly as story 10 specifies; only the compact event/summary data this story writes goes in story 33's separate analytics store. Depends on story 33's store existing, and also on the events it instruments actually existing: story 10 (game session, no `GameSession` model exists yet), story 17 (reports, no `SongReport` entity exists yet), and story 27 (rate limiting, only a narrow one-in-flight-request-per-user concurrency gate exists today on `/api/metadata/song`, not the general per-user/per-IP time-window limiter this depends on for login/register or other endpoints). Login and playlist-creation events can be instrumented once story 33 lands, independent of the others. Event scope is deliberately count/aggregate-based, not behavioral click-tracking: usage stats for the project's own understanding (games played, session length, playlists created, songs submitted, login activity), and abuse-visibility signals that turn existing enforcement into something reviewable (rate-limit-exceeded events from stories 13/27, report submissions from story 17, failed login attempts), not a new detection mechanism of its own.

- [ ] Instrument game session start/end (with the per-game summary), login, playlist creation, and song submission events to write to the analytics store; the game-session half depends on story 10, the rest can start once story 33 lands
- [ ] Instrument rate-limit-exceeded, report-submitted, and failed-login-attempt events, for abuse visibility, not enforcement; depends on stories 13/27/17 actually shipping their enforcement first, none of which exist yet
- [ ] Build a simple internal dashboard or query surface over the collected events, including a simple way to flag a user who's crossed a rate-limit or report threshold repeatedly
- [ ] Build a per-user game history page in the frontend, querying the current user's own game-summary events from the analytics store; the transactional `GameSession`/`Round`/`Guess` rows still purge exactly as story 10 already specifies, this reads only from the separate analytics store
- [ ] No third-party trackers, matches this story's own scope and the "First-party usage analytics" framing

Tests:
- [ ] Integration test: each instrumented event type produces the expected record in the analytics store
- [ ] Integration test: the dashboard/query surface returns correct aggregates for known event data
- [ ] Integration test: a user's game history page returns only their own game summaries, not other users'

Consent notice, transferred from story 37:

- [ ] Add a cookie/consent notice, only needed once story 34 (first-party analytics) ships; skip until then since no third-party trackers are planned. Deliberately deferred, not a gap: there's nothing to consent to yet

## Story 40: Catalog seeding queue and user-facing bulk import

Completed seeding, import, progress, and priority-coordination work is recorded in [ARCHIVE.md](ARCHIVE.md#story-40-completed-backend-implementation). Canonical song reuse after a pgvector match still needs alternate-ID integration and its test. Story 16 exists; this follow-up covers the integration between the two mechanisms.

- [ ] When story 16's pgvector check returns a high-confidence match for a YouTube ID that passed this story's own exact-ID check as new, link that ID into the alternate-ID table against the matched `Song` instead of creating a new one, and stop there, skipping the full pipeline for it (the alternate-ID linking primitive exists; the pgvector match that triggers it depends on story 16, not yet built)

Tests:

- [ ] Integration test: a new YouTube ID that pgvector matches with high confidence links into the alternate-ID table against the existing `Song` and never triggers the full pipeline

## Story 41: Submission content safety, non-music rejection and prompt-injection defense

Completed submission-classification work is recorded in [ARCHIVE.md](ARCHIVE.md#story-41-completed-backend-implementation). The source-match secondary signal and uncertain-case review remain deferred. The injection-gate ordering conflict in the documentation audit remains unresolved.

- [ ] Use a match (or lack of one) against MusicBrainz/Discogs/Wikidata as a secondary signal for this same ambiguous tier, not a standalone gate: a real game-soundtrack track should resolve to an actual catalogued release, resolving to nothing across all three lowers confidence but doesn't reject outright on its own, this project explicitly wants niche/underground coverage, which also won't always resolve
  - Deferred: tuning how a source non-match lowers confidence needs real submission data to set the weighting without over-rejecting niche tracks, the same data-tuning dependency story 30 carries; the classifier ships without it rather than guessing a threshold
- [ ] Still-uncertain cases after all of the above route to manual review, not a hard reject, the same "escalate, don't guess" principle already set for artist/title verification
  - Deferred: this manual-review tier depends on the source-match secondary signal above to define "still uncertain" without a threshold; deferred with it. A confident non-music or compilation verdict rejects, and a genuine no-answer song still reaches story 18's MANUAL_ENTRY route downstream

## Story 24: Parallelize metadata pipeline fetches across sources

Completed structured-source concurrency work is recorded in [ARCHIVE.md](ARCHIVE.md#story-24-completed-backend-implementation). The gather timeout and priority-coordinator verification remain open.

- [ ] Add a per-source hard timeout at the gather boundary (a cap on how long the whole gather waits on any one source, distinct from each source's own request timeout): deferred, each source already carries its own request-level timeout through `get_with_backoff`, a gather-level cap is only worth adding alongside story 40's on-the-spot latency budget
- [ ] Confirm the priority-queue rate-limit design (story 40, `DECISIONS.md`'s "Rate-limit contention" entry) still holds once fetches run concurrently: story 40's pause/resume mechanism has since been built; this is to verify the interaction, not to build either piece from scratch

Tests:

- [ ] Unit test for the gather-level per-source timeout: deferred with that task above

## Chore: Flutter DJ-model compliance

Flutter is kept, not dropped, deprioritized behind the web app per the existing 2026-06 `DECISIONS.md` entry. In the meantime it must follow the same non-negotiable rule as the rest of the product: the DJ is never shown an embedded YouTube player, playback happens on the real YouTube app.

- [x] Check the current Flutter code for any embedded or hidden YouTube playback (an in-app WebView or player widget); not yet confirmed against the real Flutter codebase (done: `mobile/lib/main.dart` embeds `youtube_player_iframe`, so the item below applies)
- [ ] If one exists, replace it with a real link-out to the YouTube app, matching the 2026-07 `DECISIONS.md` entry's mechanism for the web DJ view

## Story 38: Observability

Checked against real code: no Spring Boot Actuator dependency exists in `pom.xml`, no health-check endpoint exists today. The backend already uses SLF4J logging (from the story-6-era audit fixes), but there's no request-id/correlation-id to trace one user action across both services. The AI microservice swallows every pipeline and OpenAI failure into a generic `status="ERROR"` response with no alerting.

Goes deeper than a minimal setup, deliberately: metrics, logs, and traces together (Prometheus, Loki, Tempo), not just health checks and error tracking. All consumed through Grafana Cloud's free tier rather than self-hosted, self-hosting any of these means an always-on VM that doesn't fit the project's whole-deployment cost ceiling (see `DECISIONS.md`), while the free tier covers this project's scale at $0. Instrumentation itself is OpenTelemetry, the vendor-neutral standard, so nothing here locks the project into Grafana Cloud specifically.

- [x] Add Spring Boot Actuator to the core service for health/metrics endpoints, expose `/actuator/prometheus`
- [x] Add an equivalent health endpoint to the AI microservice (FastAPI has none today), expose metrics via `prometheus-fastapi-instrumentator`
- [x] Set up a Grafana Cloud free-tier account, point both services' Prometheus metrics at it — a Grafana Alloy container (`observability/alloy/config.alloy`, wired into `backend/docker-compose.yml`) scrapes both services' local endpoints (`/actuator/prometheus`, `/metrics`) via `host.docker.internal` and remote-writes to Grafana Cloud's hosted Prometheus, since neither service runs as its own container for Alloy to scrape directly
- [x] Add OpenTelemetry auto-instrumentation to both services for distributed tracing, viewable in Grafana Cloud's Tempo — a real trace, sent with `OTEL_SDK_DISABLED=false` and a real Grafana Cloud OTLP endpoint, was confirmed to land in Tempo. The AI microservice's exporter had a real bug fixed here: passing `endpoint` directly to `OTLPSpanExporter` skips the SDK's own per-signal path resolution, so every export 404'd silently until the `/v1/traces` suffix was added explicitly; confirmed via a direct HTTP check against Grafana Cloud's gateway before and after the fix
- [x] Ship both services' structured logs to Grafana Cloud's Loki — logs ship through the same unified OTLP gateway as traces instead of a separate Loki-specific push path: the backend gets a second Logback appender (`OpenTelemetryAppender`), the AI microservice gets an OTLP `LoggingHandler` attached to the `app` logger specifically, not the root logger, since attaching to root captures the exporter's own HTTP transport logs and re-exports them, an unbounded feedback loop confirmed by reproducing it before fixing it. A real log record was confirmed to land in Grafana Cloud (`204` from the OTLP gateway) after the fix
- [x] Add error tracking (Sentry, free tier) to both services — SDK wiring is in place in both services (`sentry-spring-boot-4`, `sentry-sdk`); real Sentry projects now exist for both services and real DSNs are configured
- [x] Add a request-id/correlation-id filter so one user action can be traced across both services' logs, and correlates with the OpenTelemetry trace for the same request
- [x] Build a basic Grafana dashboard: request rate, error rate, latency percentiles for both services — written as dashboard-as-code at `observability/grafana/hittiguess-overview-dashboard.json`, not provisioned into a live Grafana instance since no account exists yet
- [ ] Add uptime monitoring for the production deployment — not done, there is no production deployment yet (stories 7 and 8, hosting and database migration, are both still undecided), uptime monitoring is meaningless without one
- [x] Surface the AI microservice's per-source fetch failures and OpenAI call failures as visible alerts, rather than only the generic swallowed `status="ERROR"` response — each metadata source and the OpenAI synthesis call now logs a structured error and calls the Sentry SDK's capture path distinctly, instead of disappearing into the pipeline's generic error response
- [x] Add a periodic check against Grafana Cloud's and Sentry's free-tier usage limits, so approaching them is noticed before either starts silently dropping data or asking for payment (a daily scheduled check reads Sentry accepted errors and Grafana active series against configurable quotas and warns at 80%; each half stays silent until its credentials exist; log and trace gigabyte billing has no stable code endpoint and stays a console billing alert)

Tests:
- [x] Integration test: Actuator health endpoint reports correctly both when healthy and when a dependency (the database) is down
- [x] Integration test: a request-id set on an incoming request propagates through a core-service-to-AI-service call, appears in both services' logs, and correlates with a single OpenTelemetry trace — the backend side (filter, MDC, span attribute, RestClient interceptor) is proven against the real production code path; the AI microservice side (reusing an incoming id, echoing it, logging it) is proven independently in its own suite. This sandbox's JDK cannot open real loopback sockets between processes (the same limitation `BackendApplicationTests` is excluded for), so a literal cross-process HTTP call between the two real running services could not be executed here; `MockRestServiceServer` stands in for the AI service's HTTP boundary while exercising every other real component.
- [ ] Integration test: a metrics scrape and a log line both actually reach Grafana Cloud in a real (non-mocked) call — confirmed manually with real credentials (a trace, a log record, and Alloy's own metrics scrape all verified against Grafana Cloud's actual response), but not committed as a permanent automated test, since that needs real Grafana Cloud credentials available in CI, not configured yet

## Story 35: Public ground-truth data API

The final YouTube-terms confirmation read stays an open question (`PROJECT_STATE.md`), kept open deliberately; the build itself isn't blocked on it since the story's actual output data doesn't include anything YouTube-sourced, so it's placed as the last task before shipping rather than before starting.

- [x] Add a public read-only endpoint exposing verified `(artist, title, release_year)` triples only, no YouTube-sourced fields (built as `GET /api/ground-truth/songs`, joint MAIN-artist display string plus title plus locked year)
- [x] Filter to verified songs only, depends on story 23's `verificationStatus` field existing (the query filters on `VERIFIED`; covered by service and data integration tests)
- [x] Add pagination and rate limiting for public consumption (coordinate with story 27) (Spring page parameters with an explicit envelope, plus the general 60-per-minute anonymous bucket, covered by page-boundary and rate-limit tests)
- [ ] Final confirmation read of YouTube's terms before shipping, since the catalog's overall provenance mixes sources even though this endpoint's own data doesn't include anything YouTube-sourced

Tests:
- [x] Integration test: the endpoint returns only verified songs, unverified songs never appear
- [x] Integration test: no YouTube-sourced field (`youtubeId` or anything derived from it) appears in the response shape
- [x] Unit tests for pagination and the rate limit, including boundary values

## Story 28: UI redesign

Checked against real code: the frontend covers auth, landing, playlist/song CRUD, imports, admin views, group lobby, game session, chat/voice shell, away widget, DJ link-out, and results export. Batches A through E are implemented and wired to generated hooks and realtime clients. The remaining unchecked items below are open import states, visual/state coverage, accessibility, and broader route smoke coverage.

Scope decided: one unified redesign pass covering both the existing pages and the gameplay screens, not two separate efforts. A fresh visual direction, not constrained to the current shadcn/Tailwind theme tokens, though the underlying component library stays unless a specific component doesn't hold up under the new direction. Mockups were built as a multi-artboard canvas via the `design` skill and reviewed before implementation.

Design phase complete: `docs/design/hittiguess-design.html` covers all 53 screens across auth, playlist management, song review, import, and gameplay, plus the landing page, in both light and dark themes, iterated and reviewed directly by the project owner. Implementation through Batch E is present; the remaining tasks below are explicit verification gates and open states.

- [x] Design phase: establish the fresh visual direction (color, type, spacing, component style) and apply it across every existing page: landing, login, register, forgot-password, dashboard/playlist list, playlist detail, song detail, add song, join-by-invite. See `docs/design/hittiguess-design.html`
- [x] Design phase: extend the same visual system to the gameplay screens `GAME_DESIGN.md` specs but that don't exist as code yet: group lobby (member list, admin crown, join code/link, settings), game session/timeline (drag-and-drop cards, guess box, token count, betting window), DJ view (open-in-YouTube link-out), voice sidebar, text chat overlay, turn notification banner, the minimized "playing while away" widget state, and the results/leaderboard screen. See `docs/design/hittiguess-design.html`
- [x] Review pass against every mockup with the project owner before implementation starts, checking each gameplay screen against `GAME_DESIGN.md`'s spec for anything the design missed
- [x] Implementation: rebuild the existing pages' actual layouts to match their mockups across Batches A through D, not just their color/font tokens. The remaining route smoke and rendered comparison checks are listed below. See `docs/FRONTEND_IMPLEMENTATION_GUIDE.md` for the required per-page workflow and verification step
- [x] Implementation: build the new gameplay screens as real Next.js components/routes and wire them to stories 9/10/11/12/13/39's actual backends. Representative-state and rendered verification remain open
- [x] Component/token boundary: no longer retheme-only where a mockup's layout differs from the existing page's layout; `docs/FRONTEND_IMPLEMENTATION_GUIDE.md` supersedes the retheme-only rule for those pages. shadcn primitives (`components/shadcn/*`) are still used wherever they're the natural fit for a control (button, input, dialog, table), never replaced with hand-built markup for a form control or anything interactive; but a page's overall layout is rebuilt to match its mockup rather than kept as-is (decided in `DECISIONS.md`: Batch F keeps the primitives, layout is rebuilt per mockup)

### Batch E: Gameplay screens

- [x] Build the group lobby route and shell from the `GroupLobby*` mockups, including member presence, admin indicators, join link, group settings, and the empty, two-player, and eight-player layouts
- [x] Wire the group lobby to the generated group-management hooks and persistent group WebSocket events, with loading, forbidden, missing, and connection-error states
- [x] Bridge the browser's HTTP-only access-token cookie into the STOMP authentication flow, so the gameplay client can connect without exposing the token to JavaScript
- [x] Build the game-session route and shared round shell from the `GameSession*` mockups, including player, DJ, and spectator layouts, current-song card, timeline, token count, and persistent session connection
- [x] Implement timeline card placement and guess submission against the game-session API, including dragging, dropped, locked, and animated reveal states
- [x] Implement betting-window preparation and active states, including token-holder variants and round progression from session broadcasts
- [x] Build the DJ link-out action and audio-sharing warning, always opening the real YouTube page or app rather than embedding playback
- [x] Add the group text-chat overlay, turn notification, away widget, and voice sidebar to the gameplay shell, wired to the group-chat, WebSocket signaling, and TURN-credentials APIs
- [x] Build the results and leaderboard route from the `Results*` mockups for two-player and eight-player sessions, including the session export action
- [ ] Render every Batch E route and state at each mockup's desktop and mobile breakpoint in both themes, comparing directly against its matching source mockup
- [x] Add unit coverage for each new interactive component and Playwright coverage for lobby join, session start, placement, betting, link-out warning, chat, and results export

Live Playwright validation of the group and gameplay flows requires the backend services to be started from this same checkout.

Tests:
- [x] Frontend test: each redesigned existing page renders without regression (a smoke test per route) (every one of the 24 routes has colocated tests, verified by audit)
- [x] Frontend test: the new gameplay screens render correctly against representative mock state (empty, mid-game, varying player counts) (session tests cover active-player, DJ, spectator, and pre-round states plus guess submission)
- [x] Frontend test: the drag-and-drop timeline placement and the guess box's animated feedback behave per `GAME_DESIGN.md`'s Interaction and animation section (keyboard placement, placement feedback, feedback clearing, and guess submission are covered)
- [x] Accessibility check: color contrast and keyboard navigation for the new visual direction, specifically the semi-transparent chat overlay and the voice sidebar (chat focus and Escape handling plus sidebar labeled controls and toggle states are covered, contrast audited under Batch F)

### Visual fidelity remediation for Batches A through D

The rendered Story 28 audit compares every existing page with its authoritative Dark mockup in `docs/design/source`, with Light and mobile variants where supplied. These tasks close the concrete layout and state gaps found by that audit.

- [x] Rebuild the shared app shell states from `AppShellDark`: active session, profile panel with statistics, and voice participant rail
- [x] Match the landing desktop and mobile structure, CTA copy, card fan, feature sections, final CTA, and footer
- [x] Match login and register control placement, card sizing, spacing, and Google button treatment
- [x] Add a deterministic capture state for the transient OAuth2 redirect screen
- [x] Match Your Playlists grid placement and remove the extra top-level join action
- [x] Match Playlist Detail's permanent member panel, header proportions, toolbar, banner, and song rows
- [x] Match Edit Playlist's description, cover edit affordance, explicit save and cancel actions, invite copy action, delete panel, and member metadata
- [x] Match Join by Invite's playlist summary, cover, member avatars, and join identity controls
- [x] Match verified, needs-review, and editable song detail card dimensions, actions, cover treatment, and footer structure
- [x] Match Add Song result density and selected-song panel, with deterministic editable and locked review states
- [x] Match Explore Public Playlists card mosaics and populated grid
- [x] Match the YouTube import link step and implement the processing list, progress bar, temporary sidebar progress icon, and progress toast states
- [x] Preserve the matching choose-source and existing-playlist import layouts while adding designed loading, empty, and error states (both steps render loading, retryable error, and empty states, covered by import page tests)
- [x] Expose and render the catalog backlog's per-item queue required by `AdminCatalogBacklogDark`
- [x] Match the report queue's artist metadata, convergence count, tier badges, and designed loading, empty, and error states

Tests:
- [x] Frontend tests cover every new or changed interactive state in this remediation (colocated tests per route carry the states; the full suite passes)
- [x] Route smoke tests cover every Batch A through D route (every one of the 24 routes has colocated tests, verified by audit)
- [ ] Render every affected page at 1440x900 in Dark and Light and compare it directly with its mockup
- [ ] Render the landing page at 390x844 in Dark and Light and compare it directly with its mobile mockups

### Frontend follow-ups from archived backend stories

Completed backend records for stories 10, 11, and 12 are in ARCHIVE.md. These unfinished frontend requirements retain their original scope and historical deferral wording. The WebSocket test's protocol-level replacement is in the archived story 11 record. Conflicts identified in the documentation audit remain pending review.

- [ ] Frontend: artist/title guess box gives immediate animated feedback, a correct guess animates a token dropping into the player's count, distinct animation for incorrect. Deferred to story 28, backend only this phase
- [ ] Frontend: turn notification, a sound plus a clickable visual banner when it's the player's turn and the game screen isn't focused, clicking either returns them to the game. Deferred alongside the item above, and also depends on story 10's turn concept existing

Tests:

- [ ] Integration test: WebSocket connection and session state survive navigating away from the game route and back. This describes frontend routing behavior with no backend-only analog; deferred alongside the two frontend tasks above. Replaced for this batch by a real backend-testable equivalent proving the same underlying guarantee at the protocol level, see below
- [ ] Integration test: turn notification fires when the player's turn starts while they're on a different route, and doesn't fire when they're already on the game screen. Deferred alongside the frontend turn-notification task, entirely frontend/game-session behavior that doesn't exist on either side yet
- [ ] Frontend: WebRTC mesh, TURN fallback engaging on a failed direct connection, and join/leave-at-arbitrary-times behavior verified against a real browser (story 28)

### LAN playtest follow-ups

Completed LAN fixes and feedback-polish tasks are in ARCHIVE.md. Owner-direction and optional motion items remain open; the optional item is not a release gate.

- [ ] Joined tab copy and explore call-to-action icon (blocked on mockup direction for new profile/settings pages)
- [ ] Motion: exit transitions for a removed list item (kicked member, deleted song, dismissed report) are out of scope: they need delayed-removal state management no component in this codebase has today, not a one-line class addition. Left for a dedicated pass if wanted later
- [ ] Profile and settings pages need mockups before building (blocked on owner direction)

## Story 47: Product ground-truth pass

Source: the owner's full feature review, which is the binding spec for everything below. Where this section conflicts with an older task, a mockup, or existing code, this section wins and the mockup gets updated to match (`docs/design` updates are tracked here, not as a separate effort). Status Needs Definition: the boxes below are captured from the review, not yet confirmed against the real code. Confirm each cluster against the code before building it.

Library (`Your playlists`):

- [x] Add an All filter beside Owned, Joined, and Saved
- [x] Give each tab its own end tile: Owned keeps New playlist, Joined gets Join playlist, Saved gets Explore public playlists
- [x] Add a Join playlist button beside Create playlist

Explore:

- [x] Add All, Saved, and Not saved filters
- [x] Make each playlist card open its playlist detail; the Save/Saved action stays on the card

Playlist titles and covers:

- [x] Enforce the six predetermined title colors everywhere a color is set, no other values
- [x] Build the four-tile mosaic cover: squared YouTube thumbnails with no black bars, placeholders filling empty tiles, thumbnails filling in progressively as songs are added

Playlist detail:

- [x] Show member circles only, opening a centered full member list popup on click
- [x] Show the ghost empty state with no songs, no in-list search box, and no redundant call to action
- [x] Scope the song search to songs inside the playlist
- [x] Start session opens gameplay with that playlist already selected
- [x] Confirm before leaving a playlist
- [x] Add the export options UI (content choice, paper size, download or print; duplex not built)
- [x] Offer invite by code and invite by link, each copying a ready message; invite URLs respect localhost versus production

Edit playlist:

- [x] Implement cover change, title, title color, description, public toggle, and invite-link copy
- [x] Confirm delete works behind its warning, and the members tab grants, kick, and ban all work
- [x] Decide the pixel-art cover rule (upload pixelized for direct database storage, same rule for profile pictures); pixelize and store won

App-wide and imports:

- [x] Audit every clickable control for the pointer hand cursor
- [x] Link songs from an existing playlist instantly
- [x] Run YouTube imports in the background with a sidebar progress indicator, hover progress, greyed pending songs in the detail view, and a return path to the live progress screen
- [x] Show catalog recommendations by default with fetch-more, and keep the add-tray contents across navigation until committed
- [x] Show continuous staged progress on single-song fetch: submitted title and channel plus the sources being consulted

Lobby, voice, and gameplay:

- [x] Animate lobby members floating per the design; keep Start game, Chat, and Settings
- [x] Make the voice sidebar collapsible everywhere, mandatory only while in a call
- [x] Order sidebar participants top to bottom with the join control after the last participant
- [x] Add the voice settings popup: speaker and microphone selection plus a test control (shipped without a prior design)
- [x] Narrow lobby settings to DJ mode (with a player picker for a fixed DJ) and cards to win; move playlist choice to a multi-select popup that merges duplicates into a temporary playlist
- [x] Close lobby popups on outside click
- [x] Block starting alone with an explanatory message (two players minimum)
- [x] Confirm before leaving the lobby; drop the stray Live label
- [x] Fix away-status reliability
- [x] Show the first-time countdown only, enforce DJ, turn, guessing, token, betting, skip, and leaderboard rules per `GAME_DESIGN.md`
- [x] Animate the unrevealed card as an audio-reactive visualizer
- [x] Offer results download options (PDF print, copy as text, CSV download; shipped without a prior design)
- [x] Verify admin pages update live with processing state

Design:

- [ ] Update the `docs/design` mockups to this section wherever they disagree (the canvas file stores its artboards in an editor-internal script block, so this needs the canvas editor, not raw text edits)

Tests:

- [x] Confirm each cluster above against the real code before building it (the gate to Ready)
- [ ] Frontend tests for every new or changed interactive state, following the story 28 verification pattern
- [x] Playwright multi-user coverage for join, lobby, full rounds, results, and the import background flow
- [ ] Voice delivery check with the synthetic-tone method, plus the contrast and motion spot checks from story 28

## Story 50: Auth hardening

The backend authentication slice is built: `User` carries email-verification and two-factor fields, `AuthController` exposes verification, password-reset, and two-factor endpoints, and `EmailService` sends through Resend. The forgot-password request and confirmation pages are wired to their generated hooks. The two-factor setup and second-login-step screens remain open. Gates Beta, not Local.

Email provider: Resend, chosen for its free tier (3,000 emails/month) and simple REST API, matching this project's existing pattern of picking the smallest free-tier service that does the job (Grafana Cloud, Sentry). Needs a real account and API key from the project owner, the same account-creation pattern story 38 (observability) used.

Two-factor authentication: TOTP (an authenticator app, e.g. Google Authenticator or Authy generating a 6-digit code from a shared secret), not SMS. No third-party SMS provider, no per-message cost, and it's the standard low-cost second factor for a project at this scale.

- [x] Add the Resend Java SDK (or a plain `RestClient` call to its REST API, whichever this project's existing HTTP-client conventions favor once checked against `AiServiceConfig`'s pattern) and an `EmailService` wrapping it; document `RESEND_API_KEY` in `backend/.env.example`
- [x] Add `emailVerified` (boolean, default false) to `User`; a Google OAuth2 signup sets it `true` immediately, since Google has already verified that email, only a local username/password signup starts unverified
- [x] Add an `EmailVerificationToken` entity (user, token, expiresAt), issued on registration and re-sendable; `POST /auth/register` sends a verification email with a link/token instead of (or alongside) completing signup, and a new `POST /auth/verify-email` endpoint marks the account verified when a valid, unexpired token is submitted
- [x] Decide and enforce what an unverified account can and can't do: block login entirely until verified (the simpler rule, avoids gating every downstream endpoint individually) versus allowing login but restricting real actions; document whichever is chosen in `DECISIONS.md`
- [x] Add a resend-verification-email endpoint, rate-limited the same way other auth endpoints are (see story 27, `RateLimitingFilter`'s existing `/auth/*` bucket)
- [x] Add a `PasswordResetToken` entity (user, token, expiresAt, used), `POST /auth/password-reset/request` (accepts an email, always returns success regardless of whether the email exists, to avoid leaking which emails are registered, and emails a reset link/token only if it does), and `POST /auth/password-reset/confirm` (token plus new password, single-use, expires after a short window)
- [x] Wire the frontend forgot-password request and confirmation pages to the generated request/confirm hooks (`frontend/app/(auth)/forgot-password`, `frontend/app/(auth)/reset-password`)
- [x] Add `totpSecret` (nullable, encrypted at rest or at minimum never returned by any DTO once set) and `twoFactorEnabled` (boolean, default false) to `User`
- [x] Add `POST /auth/2fa/setup` (admin/self, authenticated): generates a TOTP secret and a provisioning URI/QR code, not yet enabled until confirmed
- [x] Add `POST /auth/2fa/confirm`: the user submits one valid code generated from the new secret to prove they've actually added it to an authenticator app before `twoFactorEnabled` flips true
- [x] Add backup/recovery codes: a set of one-time-use codes generated alongside 2FA setup, shown once, each usable exactly once in place of a TOTP code if the authenticator app is unavailable
- [x] Add `POST /auth/2fa/disable` (requires the current password or a valid code, not just being logged in, to prevent a hijacked session from silently turning it off)
- [x] Change the login flow for a `twoFactorEnabled` account: `POST /auth/login` with a correct password but 2FA enabled returns a short-lived, narrowly-scoped intermediate token (not a real access/refresh pair) instead of completing login; a new `POST /auth/2fa/verify` endpoint accepts that intermediate token plus a TOTP or backup code and only then issues the real access/refresh cookies
- [ ] Frontend: the 2FA setup screen (QR code, confirmation step, backup codes display), and the login flow's second step when 2FA is required; these auth states remain open

Tests:
- [x] Unit tests for `EmailService` (mocked HTTP call to Resend, not a real send in any test)
- [x] Unit and integration tests for the email-verification flow: an unverified account can't log in (or is restricted, per whichever rule was chosen), a valid token verifies the account, an expired or already-used token is rejected, a resend request is rate-limited
- [x] Unit and integration tests for password reset: a request for a nonexistent email still returns success and sends no real error signal, a valid token resets the password and is then rejected on reuse, an expired token is rejected
- [x] Unit tests for TOTP setup/confirm/disable: an unconfirmed secret doesn't enable 2FA, a wrong code during confirm doesn't enable it either, disable requires the extra proof and a bare authenticated request alone is rejected
- [x] Integration test for the two-step login flow: a 2FA-enabled account's login with just a password doesn't issue real tokens, a correct second-factor code completes it, a wrong or reused backup code is rejected
- [x] Unit test confirming no DTO or API response ever includes `totpSecret` or an unused backup code in plain form after initial generation
- [x] Wire the frontend's existing forgot-password form to the new request/confirm endpoints (already wired: the request page calls `useRequestPasswordReset`, the confirm page calls `useConfirmPasswordReset` with a mismatch guard, both covered by colocated tests)

## Story 7: Beta hosting on Azure Container Apps

Decision confirmed for Beta: Vercel continues to host the frontend. Azure Container Apps Consumption hosts the Spring core and FastAPI AI services in one EU environment. Azure provides the initial public deployment and cloud-platform experience. The backend moves to a fixed-price Hetzner server only when sustained Azure cost exceeds the whole-deployment ceiling. The frontend and Neon projects do not move in that migration.

The current realtime architecture is deliberately single-replica. Spring's simple STOMP broker, socket presence registries, pending generation pools, and session-results cache are process-local. Scale at this stage means bounded admission of games on one tested core replica, not horizontal replication.

- [ ] Create the dedicated Azure subscription, EU resource group, tags, and Consumption-only Container Apps environment with no VNet, private endpoint, dedicated workload profile, Azure Database, Container Registry, or Azure log-ingestion resource
- [ ] Create subscription and resource-group monthly budgets, cost anomaly alerts, and an Action Group that notifies at $5 and $10 and shuts down both apps at $12 to preserve a buffer below the $20 whole-deployment ceiling
- [ ] Create production container definitions for the Spring core and FastAPI AI service, with explicit CPU and memory limits validated under load before the values are committed
- [ ] Configure the Spring core Container App with external HTTPS and WebSocket ingress, a managed Azure domain before any custom domain, `minReplicas: 0`, `maxReplicas: 1`, and single active revision routing
- [ ] Configure the FastAPI Container App with internal-only ingress, `minReplicas: 0`, `maxReplicas: 1`, and the core app's internal service-discovery URL
- [ ] Build commit-SHA-tagged backend images in GitHub Actions, publish them to GitHub Container Registry, and deploy them through Azure OpenID Connect federation without a long-lived Azure credential
- [ ] Configure Container Apps secrets for database connections, authentication, OAuth, email, YouTube, OpenAI, DeepInfra, Cloudflare TURN, Grafana, and Sentry; no secret may enter a repository file, image layer, or workflow log
- [ ] Configure production Spring settings: `APP_ENV=prod`, frontend URL and allowed origins, secure cookies, OAuth redirect URI, production API URL, and internal AI-service URL
- [ ] Configure Vercel production environment variables with the Azure core HTTPS URL and verify the derived WebSocket URL is WSS
- [ ] Adapt Grafana Alloy's local Docker scrape configuration to the deployed environment, import the existing Grafana dashboard, configure production uptime monitoring against the public core health endpoint, and keep Sentry enabled
- [ ] Add a configurable active-game admission limit derived from a production-like load test, with a clear busy response when the tested capacity is reached
- [ ] Document the Hetzner migration runbook: provision one fixed-price EU server, deploy the same two images plus reverse proxy, move the backend DNS record, validate, then delete Azure resources

Tests and validation:

- [ ] Container build test: both production images start and expose their health endpoints with injected test-safe configuration
- [ ] Deployment smoke test: production-like Azure revision accepts HTTPS API traffic, WSS STOMP connection, internal core-to-AI request, and returns healthy status from both services
- [ ] Load test: measure core CPU, memory, WebSocket stability, and game-action latency across the expected peak concurrent games before setting app resource limits and the admission limit
- [ ] Cost-control test: verify each Azure budget alert and the $12 shutdown workflow against a non-production test resource group
- [ ] Manual production test: two devices complete login, playlist import, a game, voice chat, DJ tab-audio sharing, reconnect, and results over HTTPS
- [ ] Migration rehearsal: deploy the same images to an isolated Hetzner server and verify DNS cutover and rollback before Azure sustained-use migration is needed

## Story 8: Production Neon database provisioning

Decision confirmed: Neon hosts both production databases, replacing the earlier Supabase plan. The app has one production deployment, and its closed-beta access gate is a phase of that deployment, not a separate environment. New production projects replace the existing development data source. The transactional project carries the core schema and pgvector extension. The analytics project carries the independent event-store schema. The Free plan's automatic suspend and wake-up replaces Supabase's manual restore after a week of inactivity.

- [ ] Create the `hittiguess-core` Neon project in the EU region closest to the Azure environment, with the `vector` extension enabled
- [ ] Create the `hittiguess-analytics` Neon project in the same region
- [ ] Set production database credentials and TLS connection URLs for the Spring core and AI service, keeping the AI service limited to the transactional database
- [ ] Run the core Flyway history against the new transactional project and verify the pgvector extension, schema, indexes, and migration history
- [ ] Run the analytics Flyway history against the analytics project and verify its independent history and `analytics_events` table
- [ ] Decide and perform a controlled catalog-only data import if existing real songs should enter Beta; local test accounts, development credentials, and test-only data do not transfer
- [ ] Configure Neon Free-plan storage and compute usage notifications, document a `pg_dump` backup export procedure, and keep the spending limit enabled if an upgrade is later approved
- [ ] Configure the Spring connection pool (`spring.datasource.hikari.max-lifetime` and keepalive) so connections are retired before Neon's five-minute suspend closes them, and verify the first request after an idle suspend succeeds
- [ ] Update the privacy policy's database host once the Neon projects hold production data
- [ ] Verify all production services use only their intended database URLs and no browser client receives database credentials

Tests and validation:

- [ ] Fresh-database integration test: the transactional Flyway history provisions a working schema with pgvector and the analytics history provisions independently
- [ ] Production-like connection test: Spring and FastAPI connect through their injected Neon TLS URLs, while the AI service cannot reach analytics data
- [ ] Idle-suspend test: after Neon suspends the compute, the first core request succeeds without a connection error and returns within a few seconds
- [ ] Backup and restore rehearsal: export each Neon project and restore into isolated temporary projects without schema or data loss

## Release playtest acceptance and remaining tests

Completed implementation is recorded in [ARCHIVE.md](ARCHIVE.md#release-playtest-fixes-completed-implementation-slice). The compound requirements below retain their original wording. Their source-backed pieces are complete; rendered layout, missed-event reconnect recovery, live configured TURN, and complete test coverage remain open. A secure HTTPS origin is required for microphone and DJ tab capture.

- [ ] Make lobby member identity stable across realtime refreshes: render real avatars,
  assign initials avatars from a stable member identifier, preserve member order, and
  show four more name characters before truncation
- [ ] Replace the tier popup's Generate flow with the requested Confirm flow, show
  Easy, Medium, Hard, and Custom on one row with icons and a divider before Custom
- [ ] Make Chat, Settings, and playlist selection mutually exclusive, dismissible by an
  outside click, consistently positioned, and visibly clickable
- [ ] Fix playlist detail actions: move import into the add-song path, widen search,
  use pointer cursors, and make import discoverable
- [ ] Synchronize gameplay round state after a reconnect and eliminate stale DJ link-out
  and card state after a round advances
- [ ] Verify TURN credential minting returns a TURN server when Cloudflare configuration
  is present, while retaining the STUN-only fallback on failure

Tests:

- [ ] Frontend unit tests for stable lobby identity, popup interaction, selected-song
  totals, playlist actions, and deadline-based countdowns
- [ ] Backend unit and integration tests for short playlist invite codes and TURN
  credential responses
- [ ] Two-client browser coverage for round advancement, DJ link-out freshness, voice,
  and tab-audio capture over HTTPS

## LAN playtest acceptance

Completed local-network setup and fix batches are in [ARCHIVE.md](ARCHIVE.md). Archived playtests do not replace this final full-game-plus-voice acceptance check.

- [ ] Manual multi-device playtest: full game plus voice, recorded here once played

## Closed-beta frontend showcase

The frontend is deployed alone on Vercel at hittiguess.com while the backend, database, and AI service are not deployed. `NEXT_PUBLIC_CLOSED_BETA=true` limits visitors to the landing page and a closed-beta screen that links to the legacy app. A server-side `BETA_ACCESS_PASSWORD` unlocks the full frontend for beta testers through a signed cookie. The flag set to false restores the normal behavior.

- [x] Add the flag, the access-code check, and the signed `beta_access` cookie (`lib/beta-access.ts`, `app/api/beta-access/route.ts`)
- [x] Redirect every route except `/`, `/closed-beta`, and the access-code endpoint to `/closed-beta` for visitors without the cookie (`proxy.ts`)
- [x] Skip the landing page's current-user request for visitors without access
- [x] Add the closed-beta screen with the legacy app link, a back-to-home link, and the access-code field
- [ ] Create the separate Vercel project (root `frontend`, production branch `dev`), set both environment variables, and attach hittiguess.com

Tests:

- [x] Unit tests for the proxy redirect rules, cookie validation, and flag-off behavior (`proxy.closed-beta.test.ts`)

## Auth cookie domain for production

The frontend runs on hittiguess.com and the core API will run on a sibling subdomain. The backend sets `session_hint`, which the frontend's `proxy.ts` reads, so every auth cookie needs a shared parent domain to reach both hosts. `COOKIE_DOMAIN` sets it; unset, cookies stay host-only as in development.

- [x] Add `app.cookie-domain` (from `COOKIE_DOMAIN`) and apply it to every cookie `CookieUtil` creates, including deletions so a browser removes the same cookie
- [ ] Create the core API's custom domain (for example `api.hittiguess.com`) on the Azure Container App and set `COOKIE_DOMAIN=hittiguess.com` in its production configuration
- [ ] Add the API subdomain to the frontend's `NEXT_PUBLIC_API_URL` in Vercel and to the Content-Security-Policy through that variable

Tests:

- [x] Unit tests for `CookieUtil`: host-only without a domain, the configured domain on all three auth cookies, deletion using the same domain, and the other cookie attributes unchanged
- [ ] Manual production test: log in on hittiguess.com and confirm `proxy.ts` sees `session_hint` on a protected route
