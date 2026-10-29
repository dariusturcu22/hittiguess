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

Five branches off `dev`, one PR each, in this order: `feat/song-stream-and-voice-quality` (A), `fix/no-scroll-game-ui` (B), `fix/lobby-chat-playlist-ui` (C), `fix/invite-join` (D), `fix/game-song-list` (E). Each PR carries its own tests and docs.

### Part A: separate song stream and voice quality (branch `feat/song-stream-and-voice-quality`)

- [x] Song mesh: a second set of peer connections per game session, signalled over STOMP and keyed by the session, that every player joins automatically as a listener with no microphone and no voice join; backend relay limited to the session's players
  - [x] Backend `SongSignalingController` (`@MessageMapping("/sessions/{sessionId}/song/signal")`), `SessionDestinations.songSignalQueue(sessionId)` as `/queue/sessions/{sessionId}/song-signal`, authorization through `PlayerRepository.existsBySessionIdAndUserId` for sender and target, relay through `convertAndSendToUser`; the group TURN credentials endpoint is reused
  - [x] Backend tests: a non-player is rejected, a player of another session is rejected, the relay reaches only the target
  - [x] Frontend song hook: one persistent `sendrecv` connection per pair for the whole session, the lower user id offers, the sender track swaps with `replaceTrack` when the DJ changes, and a fresh offer after a peer failure like voice's `peerResetCount`; a player who reloads or joins mid-session recovers
- [x] The DJ's captured audio is attached to the song connections only during the DJ's own turn until the reveal and is detached (`replaceTrack(null)`) at all other times, not muted; the share prompt still appears once per match
  - [x] The capture stays alive for the whole match, is used directly as the sender track, and is tapped by the visualizer only, never routed through the voice mixer
  - [ ] Attaching at turn start produces no audible glitch: check for a start-up delay and pre-attach shortly before the song starts if there is one
  - [x] Tests: attach and detach on turn changes, one share per match, no renegotiation when the DJ changes
- [x] Remove all song and tab audio from the voice mesh; voice carries only the microphone; lock-in silences only the song stream for the active player, never the DJ's voice
  - [x] Remove the tab mixer, `startTabAudio`, `setTabAudioBroadcasting`, `isSharingTabAudio`, and the tab error plumbing from `use-voice-mesh.ts`; voice never changes the song's quality or routing and never needs a voice join
  - [x] Keep the `shouldSilenceDjForActivePlayer` semantics (`COUNTDOWN`, `BETTING`, `REVEALED`, `SCORED`) and retarget them from the DJ's incoming audio element to the song element
  - [x] Tests: the voice hook carries no tab code, lock-in mutes only the song element
- [x] Song quality: capture with echo cancellation, noise suppression, and auto gain off, `contentHint` music, stereo Opus at 128 kbps with a single constant for the bitrate; document the bandwidth maths
  - [x] SDP munging in both directions (`stereo=1; sprop-stereo=1`, `maxaveragebitrate=128000`, `useinbandfec=1`, `maxplaybackrate=48000`) plus `RTCRtpSender.setParameters` max bitrate taken from one named constant, so 192 kbps is a one-line change
  - [x] Document roughly bitrate x listeners for DJ upload in `ARCHITECTURE.md`
- [x] Voice quality: explicit echo cancellation, noise suppression, and auto gain, a noise gate with a sensitivity slider, and an RNNoise filter toggle with a fallback; a Windows tip about call ducking in the voice settings
  - [x] Open the microphone with `echoCancellation: true`, `noiseSuppression: true`, `autoGainControl: true`, `channelCount: 1`, `contentHint` speech, and cap the sender at about 40 kbps
  - [x] Add `@sapphi-red/web-noise-suppressor`, pinned to an exact version at least two weeks old after a licence check, default on, and fall back to the plain constraints when the worklet fails
  - [x] Persist the gate sensitivity and the filter toggle in the shared audio-device store; the Windows tip is a collapsible hint shown only on Windows
  - [x] Tests: the explicit constraints, the gate threshold with attack and release, the RNNoise fallback
- [x] Song volume slider, reachable from the voice settings and from the game page, independent of the speaker volume and working without joining voice
  - [x] Tests: the volume store and the game-page control
- [x] Visualizer rewrite: the song stream only, log-spaced bands, dB scaling, smoothing, 16 to 24 bars, clean rendering with no stray pixels
  - [x] `fftSize` 2048, `smoothingTimeConstant` about 0.8, `minDecibels` about -90, `maxDecibels` about -10, 40 Hz to 16 kHz log bands from the context sample rate, a gentle spectral tilt, per-bar attack and release, heights from dB values
  - [ ] Canvas rendering with `devicePixelRatio` handling, a static resting state when there is no audio, and `prefers-reduced-motion` respected; check the card border and background at several zoom levels in a real browser
  - [x] Tests: the band maths (log spacing, tilt, smoothing) against a fake analyser
- [x] Stop the screen capture when the session ends, the player leaves the game or the group, the page unloads, or the share bar is stopped
  - [x] Stop all tracks and detach the song sender on each of those events; a route change inside the app does not stop the capture
  - [x] Tests: one per stop path
- [ ] Let Firefox and Safari try the share prompt on desktop, log the result of each attempt (browser, surface, audio track count) to the console and Sentry, keep the old gating behind one flag; record the findings here
  - [x] The frontend has no Sentry SDK today: add `@sentry/nextjs`, initialise it from a DSN environment variable, and record each share outcome as a breadcrumb alongside the `console.info` line
  - [x] When no audio track comes back, show the browser name with a retry and keep the round moving; the message tells players the DJ has no audio
  - [ ] Collect a Firefox and a Safari result, then decide whether to restore the skip
- [x] The voice rail lists who is in the call before a player joins, on the lobby and the session page
  - [x] Hide the rail only when the user is in no group; keep the Join button at the bottom
- [x] Handle autoplay blocked on a listener: a small "Click to enable sound" affordance on the session page and a retry on the first user gesture
- [ ] Manual, for the owner: two real Chrome windows with system audio, one DJ and one listener who never joined voice; a Firefox and a Safari attempt; report the results here

### Part B: no-scroll game screens (branch `fix/no-scroll-game-ui`)

- [x] The group lobby, loading screens, and every round phase fit the viewport with no scrolling; website pages keep scrolling; checked at 1366x768, 1440x900, 1920x1080, and 125% and 150% zoom
  - [x] Wrap the two game routes with `h-full overflow-hidden` and keep `results` scrollable; a `sessions/[sessionId]/layout.tsx` would also wrap `results`, so either wrap inside each page root or reset `results` in its own layout
  - [x] Remove the `min-h-[720px]`, `min-h-[680px]`, and `min-h-[480px]` values and scale content with `short:`/`very-short:` variants, `clamp()`, or container queries; the timeline, tokens, buttons, and cards stay visible and usable
  - [ ] The round subtitle stays clear of the DJ/guessing card at every listed size (confirm by eye at 125% and 150% zoom)
  - [x] Lobby popups may scroll inside their own panel only when their content genuinely exceeds it
- [x] Assert against the real scroll container (the `(app)` layout's `overflow-y-auto` div), not `documentElement`, which `h-screen overflow-hidden` already clips
- [ ] Playwright covers the lobby and one gameplay phase at 1366x768, 1440x900, and 1920x1080; extend it to the 8-member lobby, each lobby popup, round intro, betting, reveal, between-rounds loading, game over, and 125% and 150% zoom, and run it (e2e does not run in CI)

### Part C: lobby, chat, and playlist-selection UI (branch `fix/lobby-chat-playlist-ui`)

- [x] Settings opens with the same gap above the footer buttons as the chat, anchored to the same footer wrapper
- [x] Chat: stable avatar color per sender from `senderId`, 24-hour time with `hourCycle: "h23"`, newest message at the bottom, and a taller panel
  - [x] Reverse the newest-first history in the client without changing the API contract, auto-scroll to the bottom on open and on each new message, and stay put when the reader has scrolled up
- [x] Playlist options popup in one compact row with Confirm inline (Easy, Medium, Hard, divider, Custom, Confirm), a smaller footprint than `w-[480px]`, and the "Songs are selected when the session starts" line removed; keep the error alert
- [x] Left sidebar icons spaced slightly wider than now, not as wide as before (`RAIL_GROUP_GAP_CLASSES` around `gap-5`, scaling down for `short`/`very-short`), fitting at 1366x768 and 150% zoom without scrolling the rail
- [x] Tests: extend `group-chat-overlay.test.tsx` and the lobby page tests for the new chat, popup, and rail behaviour

### Part D: invite link and joining (branch `fix/invite-join`)

- [x] Joining a group the user already belongs to returns that group; joining while in another group gives a clear message with a leave option; the join page shows the server's real message and says invalid only for a missing code
  - [x] Reorder `joinGroup`: resolve the group first, then branch on the caller's membership (`memberRepository.findByUser`); the same group returns the detail with no new member and no `MEMBER_JOINED` event, a different group gives a "leave it first" 409
  - [x] Join page: read the server message for 409 and keep "That invite link isn't valid." for 404 only; the leave-and-retry button uses the cached active membership for the other group's id
- [x] A group deleted when its last member left is a plain 404 today, so the link is dead with no way to say "this group has ended" unless a tombstone is added; keep the generic wording and note it
- [x] Tests: backend idempotent join, cross-group 409, and the existing removed and full cases; frontend join page cases for each message; update `docs/SYSTEM_REFERENCE.md` if the endpoint description changes

### Part E: playlist generation for a game (branch `fix/game-song-list`)

- [x] At Start, build the game's song list once for both difficulty and custom: remove duplicates by normalized artist and title, shuffle, and keep it only in the session
  - [x] Normalized key: lowercase, strip diacritics and punctuation, drop bracketed and dash-suffixed qualifiers; keep the first occurrence; share one normalization for both paths
  - [x] Inject `Random` so the shuffle is deterministic under test
- [x] Reproduce why Easy, Medium, and Hard did not generate a playlist, fix the cause, and show a clear message when a start fails
  - [x] Run the core locally in WSL against throwaway Postgres: the local catalog holds 29 verified songs (26 in international scope; EASY 10, MEDIUM 9, HARD 7), too few for a 30-card start; selection now widens across tiers and names the real counts
- [ ] Run the per-tier eligible-song count query from PR 318 against the production database and confirm the catalog is large enough for the game sizes players use; if not, import more songs
  - [x] The likely cause is a tier band holding fewer verified `sitelinks >= 5` rows than `players * win cards * headroom`, which fails the start with a 409; widen the sampling or surface the real message, and report the local counts per tier
- [x] Tests: normalization units, deterministic shuffle, tier and custom integration including no duplicates and two starts differing in order, and an assert that no playlist rows are created

## Story 54: Third playtest fixes

A third playtest found audio working end to end and the problems below. The metadata items cover the 370
songs in the production catalog; `opencode-tasks/data/year-audit.md` lists the songs with a wrong year,
artist, or duplicate. Each task has a self-contained brief in `opencode-tasks/`.

Layout and game screens:

- [x] Left rail icons spaced further apart at every height step
- [ ] Game screens (lobby, round intro, every round phase, between rounds) laid out as the 1440x900 designs and scaled evenly to the window, replacing the per-element `short:`/`very-short:` shrinking
- [ ] Lobby matches its design artboards in text size, spacing, and alignment
- [ ] Gameplay matches its design artboards in text size, spacing, alignment, and timeline card size
- [x] The waveform has 5 bars everywhere in the app, driven by the song's frequency bands

Gameplay behavior:

- [ ] Countdowns use the server's clock: every response that carries a deadline also carries the server time, and clients correct for their own clock offset
- [ ] Every player sees the active player's card move live while it is dragged, with the same waveform, as in the GameSessionCardDraggingSpectator design
- [x] The voice rail's Join button is the next slot under the people in the call, at the top when the call is empty

Navigation:

- [ ] Start session on a playlist creates a group when the user has none, and asks to use the current group or leave it and create a new one when the user has one
- [ ] A logged-in visit to `/` redirects to `/playlists` before the landing page renders
- [ ] A finished game goes straight to its results with a loader, without the "This game has ended" screen in between
- [ ] Entering a game from the lobby shows a loader instead of a flash of the lobby

Catalog and pipeline:

- [ ] The duration refresh fetches songs that never had a duration, and keeps old durations when a refresh fails
- [ ] MusicBrainz lookups find multi-artist songs and include the albums a recording appears on
- [ ] Wikidata and Discogs matching rejects other artists' works, compilations, and non-song entities
- [ ] The Wikipedia-assisted lock no longer takes the earliest of disagreeing years when only one source supports it
- [ ] Recheck the catalog after the pipeline fixes and correct the songs in the year audit
- [ ] A completed pipeline run attaches its video to an existing song with the same normalized artist and title instead of creating a duplicate, and the admin can merge the 12 existing duplicate pairs
- [ ] The admin catalog shows songs added before pipeline tracking as "Before tracking"

Tests:

- [ ] Unit tests for each change above; backend tests for the server time field and the live drag relay; AI service tests for the new matching and lock rules against recorded source responses
- [ ] Playwright screenshots of the lobby and each round phase at 1366x768, 1440x900, and 1920x1080 compared side by side with the design artboards

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
