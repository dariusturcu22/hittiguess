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

- [ ] Verify live Cloudflare credentials and force TURN relay; add backend integration coverage if HTTP-boundary tests leave gaps
- [ ] Two-client HTTPS test: full game, round advancement, current DJ link-out, voice join/leave and delivery, tab-audio capture, reconnect, and results
- [ ] Record the final multi-device LAN full-game and voice playtest

## Story 7: Production hosting

Azure Container Apps hosts core and AI in one EU environment. Vercel hosts the frontend. Core stays single-replica because game and STOMP state are process-local. The whole-deployment ceiling is $20/month.

- [ ] Restrict the new YouTube API key to the YouTube Data API; confirm the exposed key's log lines have aged out of Log Analytics
- [ ] Disable or cap Log Analytics ingestion/retention; add anomaly alerts, a resource-group budget, and a $12 shutdown Action Group
- [ ] Cost-control test: alerts and shutdown against an isolated resource group
- [ ] Load-test core/AI CPU, memory, WebSockets, and game-action latency; validate resource limits and add a tested active-game admission limit with a clear busy response
- [ ] Test the throughput limit of the C1-only JIT setting under game load; Azure stays on scale-to-zero for the trial
- [ ] Verify Vercel root frontend, production branch dev, beta gate, domain, API origin, and shared auth cookies
- [ ] Verify real Google login and ordinary signup, verification, login, and password-reset delivery; configure the Resend domain and sender
- [ ] Add HTTP liveness/readiness probes to both apps
- [ ] Deployment smoke test: health, HTTPS API, WSS STOMP, protected-route session_hint cookie, and internal core-to-AI requests
- [ ] Production two-device acceptance: login, import, full game, reconnect, voice, DJ audio, and results
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

- [ ] Configure production TURN, Grafana, and Sentry secrets; adapt Alloy scraping, import the dashboard, and add core uptime monitoring
- [ ] Verify real metrics, logs, traces, and error delivery; add a credential-gated delivery integration test without secret output

## Backend follow-ups

- [ ] Story 24: verify concurrent source gathering preserves immediate-work priority over the patient backlog, with an integration test
- [ ] Merge existing catalog duplicates and add unique songs(youtube_id) and active pending_imports indexes
- [ ] Integration tests: cleanup preserves playlist/alternate-ID links and indexes reject concurrent duplicates

## Story 51: Admin console and parallel source pipeline

The admin area becomes a console with its own sidebar section, a dashboard, a backfill queue, a song catalog, and the report queue. Backfill and user imports run through seven persisted queues, one per stage: YouTube metadata, MusicBrainz, Discogs, Wikidata, Wikipedia, LLM reconciliation, and embedding. Each queue works at its own pace, and a song is finalized once every stage it needs is done. Wikipedia is queued for a song only when MusicBrainz, Discogs, and Wikidata disagree. Frontend screens wait for approved mockups. LLM usage has no limit and is tracked with an estimated cost from the official prices. The daily 200-song drain cap is removed.

Pipeline and data:

- [x] AI stage endpoints: internal authentication, video identification and precheck verdict, source candidates and earliest years, reconciliation, and sanitized provider errors
- [x] AI stage equivalence tests: agreeing years, disagreement, empty source, precheck rejection, and YouTube failure; existing AI tests remain passing
- [x] Persisted source pacing: V45 stage rows, atomic slot reservations, cooldowns, response averages, in-memory fallback, and stage-only 30-second wait cap with retryAfterSeconds
- [x] Pacing tests: SQL, fallback, wait cap, concurrent increasing reservations, and core Testcontainers migration coverage
- [x] AI stage documentation: endpoint contracts, error and retry bodies, pacing table, architecture, and decision entry

- [x] Core migration (V44): per-stage job table (video run, batch, stage, status, attempts, lease, next attempt, result, failing stage, real failure reason), batch table, runs with one active run per YouTube ID, and per-stage pause controls; constraints covered by the engine integration tests
- [x] Job engine leasing: one job per call per stage with `FOR UPDATE SKIP LOCKED`, user imports first, then priority, then age, with retry backoff; the leased-job pattern of the embedding queue is the template; covered by concurrent-worker integration tests
- [ ] Stage workers lease jobs from their own queue at the source's pace; pace and cooldown state survive restarts
- [x] Stage dependency rules: the three sources run in parallel after YouTube metadata; agreeing years skip Wikipedia and reconciliation; Wikipedia is added after the first three sources answer otherwise; the run is marked ready to save and its embedding queued; unit-tested transition table and both paths in integration tests
- [ ] Join step: save the song when its run is ready to save, before its embedding job runs
- [ ] User imports use the same queues with priority over the backlog, replacing the drain pause in `MetadataPriorityCoordinator`
- [x] Engine failure handling: classify failures as transient (outage, rejected key, rate limit, timeout, shutdown) or permanent (video unavailable, no usable metadata); transient failures retry with backoff and an attempt count; the real reason and failing stage are stored on the job; unit and integration tests
- [ ] Stage workers report failures through the engine, so a transient error never marks a song failed
- [x] Engine: a rejected API key pauses its stage and returns the job without using an attempt or failing the run; integration test
- [ ] Real provider calls report rejected or exhausted keys, and a paused stage raises a dashboard banner
- [x] Engine: jobs whose lease expired are reclaimed inside each lease call, and a lost lease counts as an attempt; integration tests
- [ ] A restart or redeploy resumes work without waiting for the daily timer
- [ ] Remove the daily drain cap; the backlog stops when remaining YouTube units fall below a reserve kept for user imports
- [x] The AI service records YouTube units and LLM tokens per call; cost estimates use the official per-unit prices kept as named constants with the date they were read

Admin API:

- [x] `/api/users/me` returns the account role; remove the 403 probe in the admin layout
- [x] Usage endpoint: `GET /api/admin/usage` returns YouTube units against the daily limit with the next midnight Pacific reset, and DeepInfra and OpenAI calls, tokens, and estimated cost for the UTC day and month, backed by the daily usage table
- [x] Dashboard endpoint v1: `GET /api/admin/dashboard` returns the catalog counts (verified, needs review, in queue, processing, error, total), queue totals (waiting and in progress across every origin), and the usage view with YouTube units against the daily 10,000-unit default with its midnight Pacific reset and LLM and embedding usage with estimated cost for today and the month
- [ ] Dashboard endpoint, per-queue depth: waiting, in progress, and failed per stage queue, once the stage queues exist
- [ ] Queue endpoint: waiting, in progress, done, and failed counts per stage, current pace, cooldown state, average response time, estimated time to drain, and the list of songs still needing work; a per-stage job list with lane (user import or backfill), attempts, next attempt, and last error
- [x] Control service methods: pause and resume per stage, retry and discard a failed run, cancel a batch, and bump a run above every waiting job; integration tests
- [ ] Controls endpoints: pause and resume per queue and for the whole backlog; retry, retry all, and discard failed items; cancel a batch; run one YouTube ID now at top priority; re-run the pipeline for one song
- [ ] Batches: each submitted playlist or ID list is a batch with progress, skipped duplicates, and an estimate
- [x] Song catalog endpoint: server-side search by title or artist with a minimum of three characters, 25 results per page, and a trigram index so a search never scans the whole table; each row has title, artists, release year, status (processing, in queue, verified, needs review, error), pipeline (fast or full, from the queue origin), date added, and added by (admin seed when empty)
- [x] Song detail endpoint, read only: the catalog fields, stored verification status, confidence, and sitelinks, open report count, and the queue history for its YouTube ID
- [ ] Song detail additions once the stage queues exist: what each source found, why the song is verified or needs review, and the actions to set year or status, re-run, or delete

Frontend:

- [ ] Admin section in the sidebar in its own accent color with Dashboard, Backfill queue, Song catalog, and Reports, visible only to admins
- [x] Song catalog page (debounced search, status filters, pagination) and read-only song detail drawer
- [x] Dashboard page v1 at `/admin`: catalog count tiles, YouTube quota with reset countdown, and AI usage with estimated cost, refreshed every 30 seconds
- [ ] Dashboard queue table, rejected-key banner and reserve figures, and the backfill queue (per-queue panel, a page per stage queue, batches, failed items with reasons, song list)
- [x] Design dark and light mockups for the admin sidebar section, dashboard, backfill queue, song catalog, and song detail; replace the catalog backlog mockup and its 400-song quota figure; validate source and canvas equality

Tests:

- [x] Unit tests: failure classification, retry backoff, and the stage dependency rules including the conditional Wikipedia stage
- [ ] Unit tests: status and pipeline derivation, cost estimation, and the YouTube reserve rule
- [x] Integration tests: job leasing under concurrent workers and user-import priority in the job engine
- [ ] Integration tests: restart recovery, usage counters, role in `/api/users/me`, the dashboard, queue, and catalog endpoints, minimum search length, page size, and search index use, and admin-only access
- [ ] Frontend unit and browser tests: admin sidebar visibility by role, dashboard figures, per-queue panel, pause and retry actions, catalog search limits, and detail drawer
- [ ] Update `ARCHITECTURE.md`, `SYSTEM_REFERENCE.md`, `FRONTEND_CONTENT.md`, and `FRONTEND_IMPLEMENTATION_GUIDE.md` for the new endpoints, queues, and screens

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
