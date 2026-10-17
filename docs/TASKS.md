# TASKS.md: Active work

Only unfinished work belongs here. [PROJECT_STATE.md](PROJECT_STATE.md) defines story readiness; [ROADMAP.md](ROADMAP.md) sets priority. Completed implementation and audit evidence belong in [ARCHIVE.md](ARCHIVE.md).

Frontend implementation and tests belong to story 28. Feature work requires a Ready story and tasks checked against current code. Story 34 replaces the existing abuse-event log stubs with analytics writes; it does not change enforcement.

## Release: Reconnect recovery

- [ ] Recover session, private guess, DJ link-out, group membership, chat history, and completed-session state after events missed during disconnection
- [ ] Unit and browser tests: missed phase changes, game completion, membership/chat events, and navigation continuity

## Story 28: Frontend completion

- [ ] Build two-factor setup, provisioning QR, confirmation, one-time backup codes, disable, and second login using TOTP or backup codes; backend endpoints exist
- [ ] Unit and backend-backed browser tests: two-factor success, invalid codes, disable proof, and one-use backup codes
- [ ] Compare all app routes/states at 1440x900 in both themes; include identity/truncation, popups, playlist actions, tier selection, imports, gameplay/countdowns, results, admin, and history
- [ ] Compare only the landing page on mobile at 390x844 in both themes
- [ ] Complete contrast, keyboard, reduced-motion, and route-navigation checks; add focused unit/browser tests for coverage gaps
- [ ] Review the exact Joined-tab copy

## Story 47: Mockup reconciliation

- [ ] Update remaining source artboards and the published canvas to match approved requirements found during story 28's visual pass; validate source/canvas equality

## Release: Multiplayer acceptance

- [ ] Verify the production Cloudflare TURN credentials (both variables are set on core) with two devices on different networks and force TURN relay; add backend integration coverage if HTTP-boundary tests leave gaps
- [x] Two-client HTTPS test: full game, round advancement, current DJ link-out, voice join/leave and delivery, tab-audio capture, reconnect, and results

## Story 52: Live playtest fixes

A Chrome and Firefox playtest on the production site found that DJ audio never reached the other players, a player cannot leave a running session, and several voice and layout problems. Voice chat, playlists, and song adding worked.

DJ audio (the most important batch):

- [ ] Confirm in a two-Chrome production run that the DJ's shared audio reaches the other players; if it does not, log the display-capture result (surface type, audio track present), the outgoing track state, and the receiving peer's incoming track and playback state
- [x] Share once per match: a DJ who has not yet shared is asked for the screen share with system audio first, and only then does the Open on YouTube button appear; the share stays alive for the rest of the match, so later rounds do not ask again
- [x] Share the DJ's audio only during that player's own DJ turn: outside it the captured audio is not mixed into the voice stream and nobody receives it, and the audio stops when the DJ locks in; only the current DJ's audio is sent to the other players, never to the DJ; unit tests for the gating
- [x] Detect browsers that cannot capture audio (Firefox, Safari), report the capability to the server when a player joins, and skip those players in DJ rotation; they still play and guess, and see a short notice explaining why; a rotation with no capable player shows the group a clear message; unit and backend integration tests
- [x] Open the YouTube link in a new tab of the same browser window, not a popup window
- [ ] Simplify the DJ view: one primary action, short instructions, no duplicated controls
- [x] The card's sound wave moves for every player from the DJ's audio and ignores voice chat; the DJ's own wave follows the shared audio
- [ ] Two-browser test: share-once across rounds, missing audio track message, unsupported browser handling, wave source (unit tests are in place)

Leaving and ending:

- [x] A player can leave a running session while the game continues without them: Leave game also leaves the group, because a member of a locked group is sent back into its game
- [x] The voice indicator shows a player in voice only after they joined; find and fix the state that marks a player as in voice without joining; unit test
- [x] A player whose tab closed or whose connection dropped is removed from voice after 2 minutes; backend scheduler and integration test

Voice controls:

- [x] Volume sliders for the microphone and the speaker next to the device pickers; the microphone level meter and the speaker test tone already existed

Visual:

- [x] Card colors that are too dark on the dark theme get a readable variant; check every song color
- [ ] Browser test that the round subtitle is never covered by the guessing card at 100%, 125%, and 150% scaling; the stage's minimum height was raised
- [ ] The `/api/playlists/{id}/import-jobs/active` 404 still shows in the console; changing that response needs an API contract decision
- [x] Investigated the `wss://api.hittiguess.com/ws` closed-before-established message: it appears when a page unmounts its socket while connecting and needs no change

## Story 53: Second playtest fixes

A second production playtest found the game borderline playable but buggy: the song only reaches players who joined voice, voice and music degrade each other, the game screens scroll, and several lobby and playlist problems. Every item was checked against the code before it was written here.

Separate song stream and audio quality:

- [ ] Song mesh: a second set of peer connections per game session, signalled over STOMP and keyed by the session, that every player joins automatically as a listener with no microphone and no voice join; backend relay limited to the session's players
- [ ] The DJ's captured audio is attached to the song connections only during the DJ's own turn until the reveal and is detached (`replaceTrack(null)`) at all other times, not muted; the share prompt still appears once per match
- [ ] Remove all song and tab audio from the voice mesh; voice carries only the microphone; lock-in silences only the song stream for the active player, never the DJ's voice
- [ ] Song quality: capture with echo cancellation, noise suppression, and auto gain off, `contentHint` music, stereo Opus at 128 kbps with a single constant for the bitrate; document the bandwidth maths
- [ ] Voice quality: explicit echo cancellation, noise suppression, and auto gain, a noise gate with a sensitivity slider, and an RNNoise filter toggle with a fallback; a Windows tip about call ducking in the voice settings
- [ ] Song volume slider, reachable from the voice settings and from the game page, independent of the speaker volume and working without joining voice
- [ ] Visualizer rewrite: the song stream only, log-spaced bands, dB scaling, smoothing, 16 to 24 bars, clean rendering with no stray pixels
- [ ] Stop the screen capture when the session ends, the player leaves the game or the group, the page unloads, or the share bar is stopped
- [ ] Let Firefox and Safari try the share prompt on desktop, log the result of each attempt (browser, surface, audio track count) to the console and Sentry, keep the old gating behind one flag; record the findings here
- [ ] The voice rail lists who is in the call before a player joins, on the lobby and the session page

Game screens and lobby:

- [ ] The group lobby, loading screens, and every round phase fit the viewport with no scrolling; website pages keep scrolling; checked at 1366x768, 1440x900, 1920x1080, and 125% and 150% zoom
- [ ] Settings opens with the same gap above the footer buttons as the chat
- [ ] Chat: stable avatar color per sender, 24-hour time, newest message at the bottom, taller panel
- [ ] Playlist options popup in one compact row with Confirm inline; remove the "Songs are selected when the session starts" line
- [ ] Left sidebar icons spaced slightly wider than now, not as wide as before

Joining and playlists:

- [ ] Joining a group the user already belongs to returns that group; joining while in another group gives a clear message with a leave option; the join page shows the server's real message and says invalid only for a missing code
- [ ] At Start, build the game's song list once for both difficulty and custom: remove duplicates by normalized artist and title, shuffle, and keep it only in the session
- [ ] Reproduce why Easy, Medium, and Hard did not generate a playlist, fix the cause, and show a clear message when a start fails

Tests:

- [ ] Unit tests for the song hook, the voice hook, the noise gate, the visualizer bands, and the stop conditions; backend tests for song signalling authorization, idempotent join, duplicate removal, and shuffling; Playwright checks for no scrolling at the listed sizes and a two-context song delivery test
- [ ] Manual run: two real Chrome windows with system audio, one DJ and one listener who never joined voice; a Firefox and a Safari attempt; report the results here

## Story 7: Production hosting

Azure Container Apps hosts core and AI in one EU environment. Vercel hosts the frontend. Core stays single-replica because game and STOMP state are process-local. The whole-deployment ceiling is $20/month.

- [x] Restrict the new YouTube API key to the YouTube Data API; the exposed key is deleted
- [ ] Add anomaly alerts and a $12 shutdown Action Group; the Log Analytics daily cap (0.25 GB, 30-day retention) and the $20 budget with email alerts are in place
- [ ] Cost-control test: alerts and shutdown against an isolated resource group
- [ ] Load-test core/AI CPU, memory, WebSockets, and game-action latency; validate resource limits and add a tested active-game admission limit with a clear busy response
- [ ] Test the throughput limit of the C1-only JIT setting under game load; both Azure apps run one always-on replica
- [x] Verify Vercel root frontend, production branch dev, domain, API origin, and shared auth cookies
- [ ] Verify password-reset email delivery in production; Google login, signup, verification, login, and the Resend domain and sender are working
- [x] Add HTTP liveness/readiness probes to both apps
- [ ] Deployment smoke test: health, HTTPS API, WSS STOMP, protected-route session_hint cookie, and internal core-to-AI requests
- [ ] Production two-device acceptance: login, import, full game, reconnect, voice, DJ audio, and results; fix the issues found in the live demo first
- [ ] Plan the move to one Hetzner server before the Azure trial credit ends: document the deployment, rehearse it in isolation, and prepare DNS cutover and rollback
- [ ] Optional: decide whether production deployments require GitHub environment reviewers

## Story 8: Production databases

Both Neon projects are provisioned. Core owns schema migrations; AI connects only to the transactional database.

- [ ] Inspect both Flyway histories, core pgvector/indexes, and analytics tables
- [ ] Verify intended TLS URLs and service boundaries, including deployed AI access to core and no browser credentials or AI access to analytics
- [ ] Test the first request after idle suspend; change pool settings only if needed
- [ ] Configure storage/compute notifications and document backup exports; rehearse isolated restoration of both databases
- [ ] Decide whether existing songs should enter Beta and perform only an approved catalog-only import

## Story 38: Production observability

- [ ] Production Sentry and OpenTelemetry settings are set on both apps; remaining: metrics through a Grafana Alloy container (core's `/actuator/prometheus` needs a login, so expose it on an internal-only management port first), import the dashboard, and add core uptime monitoring
- [ ] Verify real metrics, logs, traces, and error delivery; add a credential-gated delivery integration test without secret output

## Backend follow-ups

- [ ] Story 24: verify concurrent source gathering preserves immediate-work priority over the patient backlog, with an integration test
- [ ] Merge existing catalog duplicates and add unique songs(youtube_id) and active pending_imports indexes
- [ ] Integration tests: cleanup preserves playlist/alternate-ID links and indexes reject concurrent duplicates

## Story 51: Admin console and parallel source pipeline

The admin area becomes a console with its own sidebar section, a dashboard, a backfill queue, a song catalog, and the report queue. Backfill and user imports run through seven persisted queues, one per stage: YouTube metadata, MusicBrainz, Discogs, Wikidata, Wikipedia, LLM reconciliation, and embedding. Each queue works at its own pace, and a song is finalized once every stage it needs is done. Wikipedia is queued for a song only when MusicBrainz, Discogs, and Wikidata disagree. The admin screens use approved dark and light mockups. LLM usage has no limit and is tracked with an estimated cost from the official prices. The stage path has no daily 200-song drain cap. The sequential drain and cutover flag are removed after the owner confirmed the production playlist test.

Pipeline and data:

- [x] Cutover: authenticated AI stage client with provider failure mapping, per-stage scheduled workers, cheap due-work checks, and no-attempt pace rescheduling
- [x] Cutover: transactional ready-run persistence, patient-year recording, and V41 embedding execution without engine embedding jobs
- [x] Admin batches and user rechecks always enqueue stage runs; the sequential drain and cutover flag are removed
- [x] User-lane priority, configurable YouTube reserve, and run-based catalog and dashboard reads
- [x] Cutover unit tests: worker decisions, stage payloads, error mapping, reserve behavior, and dependency transitions
- [x] Cutover integration tests: agreeing and disagreeing years, retry and key pause, permanent failure, lease recovery, lane priority, reserve, atomic save and duplicates, and unconditional stage bean registration
- [x] Pipeline docs: unconditional stages, embedding contracts, decision entry, and production verification

- [x] AI stage endpoints: internal authentication, video identification and precheck verdict, source candidates and earliest years, reconciliation, and sanitized provider errors
- [x] AI stage equivalence tests: agreeing years, disagreement, empty source, precheck rejection, and YouTube failure; existing AI tests remain passing
- [x] Persisted source pacing: V45 stage rows, atomic slot reservations, cooldowns, response averages, in-memory fallback, and stage-only 30-second wait cap with retryAfterSeconds
- [x] Pacing tests: SQL, fallback, wait cap, concurrent increasing reservations, and core Testcontainers migration coverage
- [x] AI stage documentation: endpoint contracts, error and retry bodies, pacing table, architecture, and decision entry

- [x] Core migration (V44): per-stage job table (video run, batch, stage, status, attempts, lease, next attempt, result, failing stage, real failure reason), batch table, runs with one active run per YouTube ID, and per-stage pause controls; constraints covered by the engine integration tests
- [x] Job engine leasing: one job per call per stage with `FOR UPDATE SKIP LOCKED`, user imports first, then priority, then age, with retry backoff; the leased-job pattern of the embedding queue is the template; covered by concurrent-worker integration tests
- [x] Stage workers lease jobs from their own queue at the source's pace; pace and cooldown state survive restarts
- [x] Stage dependency rules: the three sources run in parallel after YouTube metadata; agreeing years skip Wikipedia and reconciliation; Wikipedia is added after the first three sources answer otherwise; the run is marked ready to save without an engine embedding job; unit-tested transition table and both paths in integration tests
- [x] Join step: save the ready song and finish its run in one transaction; V41 triggers queue verified embeddings
- [x] User imports use the same queues with priority over the backlog, replacing the drain pause in `MetadataPriorityCoordinator`
- [x] Engine failure handling: classify failures as transient (outage, rejected key, rate limit, timeout, shutdown) or permanent (video unavailable, no usable metadata); transient failures retry with backoff and an attempt count; the real reason and failing stage are stored on the job; unit and integration tests
- [x] Stage workers report failures through the engine, so a transient error never marks a song failed
- [x] Engine: a rejected API key pauses its stage and returns the job without using an attempt or failing the run; integration test
- [ ] Real provider calls report rejected or exhausted keys, and a paused stage raises a dashboard banner
- [x] Engine: jobs whose lease expired are reclaimed inside each lease call, and a lost lease counts as an attempt; integration tests
- [x] A restart or redeploy resumes work without waiting for the daily timer
- [x] Remove the daily drain cap on the stage path; the backlog stops when remaining YouTube units fall below a reserve kept for user imports
- [x] The AI service records YouTube units and LLM tokens per call; cost estimates use the official per-unit prices kept as named constants with the date they were read

Admin API:

- [x] Queue API: stage state, counts, pace, cooldown, response averages, estimates, paged jobs, and paged runs with stage progress for the Songs tab
- [x] Control API: stage and global pause/resume, run retry/discard/bump, retry all failed, batch list/cancel, enqueue, and run one now
- [x] Batch reporting: additive skipped-duplicate count column and enqueue response batch ID, with migration and duplicate-reporting tests
- [x] Song controls: per-source findings and review reason in detail, rerun, year/status update, and catalog delete with playlist and alternate-ID cleanup
- [x] Dashboard API: backward-compatible stage queues, paused-stage banner reasons, and YouTube reserve fields
- [x] Queue/control tests: service units, admin/user/validation MockMvc coverage, and real-row Testcontainers checks for every control
- [x] Regenerate the frontend API client from the local backend spec and commit only intended additions separately
- [x] Queue API documentation: request/response contracts and completed task boxes

- [x] `/api/users/me` returns the account role; remove the 403 probe in the admin layout
- [x] Usage endpoint: `GET /api/admin/usage` returns YouTube units against the daily limit with the next midnight Pacific reset, and DeepInfra and OpenAI calls, tokens, and estimated cost for the UTC day and month, backed by the daily usage table
- [x] Dashboard endpoint v1: `GET /api/admin/dashboard` returns the catalog counts (verified, needs review, in queue, processing, error, total), queue totals (waiting and in progress across every origin), and the usage view with YouTube units against the daily 10,000-unit default with its midnight Pacific reset and LLM and embedding usage with estimated cost for today and the month
- [x] Dashboard endpoint, per-queue depth: waiting, in progress, and failed per stage queue, once the stage queues exist
- [x] Queue endpoint: waiting, in progress, done, and failed counts per stage, current pace, cooldown state, average response time, estimated time to drain, and the list of songs still needing work; a per-stage job list with lane (user import or backfill), attempts, next attempt, and last error
- [x] Control service methods: pause and resume per stage, retry and discard a failed run, cancel a batch, and bump a run above every waiting job; integration tests
- [x] Controls endpoints: pause and resume per queue and for the whole backlog; retry, retry all, and discard failed items; cancel a batch; run one YouTube ID now at top priority; re-run the pipeline for one song
- [x] Batches: each submitted playlist or ID list is a batch with progress, skipped duplicates, and an estimate
- [x] Song catalog endpoint: server-side search by title or artist with a minimum of three characters, 25 results per page, and a trigram index so a search never scans the whole table; each row has title, artists, release year, status (processing, in queue, verified, needs review, error), pipeline (fast or full, from the queue origin), date added, and added by (admin seed when empty)
- [x] Song detail endpoint, read only: the catalog fields, stored verification status, confidence, and sitelinks, open report count, and the queue history for its YouTube ID
- [x] Song detail additions once the stage queues exist: what each source found, why the song is verified or needs review, and the actions to set year or status, re-run, or delete

Frontend:

- [x] Backfill pages: overview stage cards, batches, enqueue and run-now forms, and a Songs tab with real progress and run controls
- [x] Page API support: artist and batch fields, global run-state filters/counts, read-only embedding progress, immediate waiting retries, and stage-scoped retry-all; backend tests and regenerated bindings
- [x] Stage page: stage counts, pace samples, paged jobs, status filters, pause/resume, and retry failed work
- [x] Dashboard completion: queue table, rejected-key pause reasons with Resume, and user reserve figures
- [x] Song drawer completion: source findings, review reason, rerun, year/status edits, and confirmed deletion
- [x] Navigation: Backfill queue rail entry and stage routes; obsolete catalog-backlog route removed
- [x] Admin page tests: each screen and action, confirmation dialogs, loading/empty/error states, mutation failures, and accessible controls
- [x] Admin visual verification: real local backend, approved mockups at 1440x900 in light and dark themes, including the song drawer
- [x] Admin page docs: content requirements, implementation guide, and checked tasks for verified work

- [x] Admin section in the sidebar in its own accent color with Dashboard, Backfill queue, Song catalog, and Reports, visible only to admins
- [x] Song catalog page (debounced search, status filters, pagination) and read-only song detail drawer
- [x] Dashboard page v1 at `/admin`: catalog count tiles, YouTube quota with reset countdown, and AI usage with estimated cost, refreshed every 30 seconds
- [x] Dashboard queue table, rejected-key banner and reserve figures, and the backfill queue (per-queue panel, a page per stage queue, batches, failed items with reasons, song list)
- [x] Design dark and light mockups for the admin sidebar section, dashboard, backfill queue, song catalog, and song detail; replace the catalog backlog mockup and its 400-song quota figure; validate source and canvas equality

Tests:

- [x] Unit tests: failure classification, retry backoff, and the stage dependency rules including the conditional Wikipedia stage
- [x] Unit tests: status and pipeline derivation, cost estimation, and the YouTube reserve rule
- [x] Integration tests: job leasing under concurrent workers and user-import priority in the job engine
- [x] Integration tests: restart recovery, usage counters, role in `/api/users/me`, the dashboard, queue, and catalog endpoints, minimum search length, page size, and search index use, and admin-only access
- [x] Frontend unit and browser tests: admin sidebar visibility by role, dashboard figures, per-queue panel, pause and retry actions, catalog search limits, and detail drawer
- [x] Update `ARCHITECTURE.md`, `SYSTEM_REFERENCE.md`, `FRONTEND_CONTENT.md`, and `FRONTEND_IMPLEMENTATION_GUIDE.md` for the new endpoints, queues, and screens

Production and cleanup:

- [x] Owner confirmation that the enabled stage pipeline passes a small real playlist in production; earlier stacked branches merged into dev
- [x] Cleanup after that confirmation: remove the old sequential drain and flag, retain pending-import history, update docs, run all required tests, and archive completed tasks

Production fixes from the first enabled playlist (throughput and admin metrics):

- [x] Stage workers keep leasing due jobs in a bounded burst instead of one job per tick
- [x] Per-stage concurrency limits replace the exclusive lease, configurable per stage, on a bounded executor sized to the limits
- [x] In-process wake-ups start a stage as soon as its jobs are created or become due, with a longer idle poll
- [x] Queue metrics from `pipeline_jobs`: average job duration, average queue wait, throughput, source request time
- [x] Drain estimates per stage from measured throughput, concurrency, and source pace, plus an overall estimate
- [x] Embedding row filled from the V41 queue, with a migration recording claim and storage times
- [x] Count definitions checked and documented: expired leases, failed jobs of discarded runs, the UTC day
- [x] Finished jobs clear their failure fields, and read paths hide stale failure data on finished jobs
- [x] AI service logs transient upstream failures as warnings without the error reporter, and serves enough concurrent stage calls
- [x] Tests: drain and metric formulas, concurrency limit under parallel workers, burst, wake-ups, metric queries, throughput with a slow fake AI client, stale failure data, AI log levels
- [x] Update `SYSTEM_REFERENCE.md`, `ARCHITECTURE.md`, and `DECISIONS.md`

Later, once the console exists: an audit log of admin actions, catalog health checks (duplicates, missing embeddings, missing durations), a list of safety rejections, and a catalog CSV export.

## Story 34: Usage analytics, definition required

Core participant history/statistics and analytics difficulty research already exist. Raw analytics retention is 365 days. This story covers internal usage and abuse visibility with no third-party trackers.

- [ ] Confirm event fields and tasks against current code for session start/end, login/failed login, playlist creation, submission, rate-limit, report, and injection events
- [ ] Implement approved event writes, replace abuse-event stubs, and build internal aggregates/repeated-abuse visibility
- [ ] Define and implement the reviewed notice or consent behavior
- [ ] Integration tests: event records and dashboard/query aggregates

## Deferred work

- [ ] Story 28: define account mockups and build correction, deletion, and personal-export controls; add unit/browser permissions and failure tests
- [ ] Story 28: build privacy/terms routes from reviewed copy; update providers including Neon, data inventory, retention, and remaining export scope; test routes/disclosures
- [ ] Story 30: after enough real research data exists, train personalized difficulty, schedule retraining, and monitor against the shipped aggregate baseline; test held-out predictions and retraining/staleness detection
- [ ] Story 41: tune source-match confidence using real submissions and route uncertain cases to manual review without rejecting niche tracks; define classifier/review tests with the approved thresholds
- [ ] Story 24: add a gather-boundary per-source timeout and unit tests when the latency budget requires it; request-level timeouts already exist
- [ ] Define curated metadata evidence fields, provenance, retention/refresh, access, and a Ready story with persistence/access/retention tests before populating metadataRaw
- [ ] Flutter: replace embedded playback with official YouTube app/site link-out and test the DJ action; mobile remains deprioritized
- [ ] Optional: removed-item exit motion beyond voice departure, with reduced-motion tests

Genre enrichment and automatic Topic-upload upgrades are dropped. Pattern-based user moderation remains an unscoped future idea after usage/abuse data exists.
