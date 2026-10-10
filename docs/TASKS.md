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

## Admin catalog seeding redesign, requirements pending

Both the seeding behavior and the admin page change. The owner supplies the requirements; these tasks are the confirmed starting points and the story stays Needs Definition until the breakdown is checked against the code.

- [ ] Classify pipeline failures as transient (service outage, rejected key, rate limit, timeout, shutdown mid-item) or permanent (video unavailable, no usable metadata); a transient failure leaves the item pending with a retry delay and attempt count instead of marking it failed
- [ ] Store the real failure reason and failing stage on each failed item; show it in the admin view
- [ ] Add a retry action for failed items; failed rows are never picked up again today, and the status diagram in SYSTEM_REFERENCE.md describes a retry that does not exist
- [ ] Resume interrupted work after a restart or redeploy without waiting for the daily timer; the drain currently also starts at every application boot because the daily timer fires immediately
- [ ] Redesign the admin catalog page and its enqueue/drain flow from the owner's requirements (per-playlist progress, status detail, daily quota visibility)
- [ ] Unit and integration tests: transient versus permanent classification, retry limits, interrupted-item recovery, retry action, and the status endpoint

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
