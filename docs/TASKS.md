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

Core history and prepared global difficulty are implemented. The completed tasks and tests are in [ARCHIVE.md](ARCHIVE.md#game-history-and-prepared-global-difficulty).

### Personalized difficulty, deferred

Completed generation, selection, public-playlist, and lobby integration work is recorded in [ARCHIVE.md](ARCHIVE.md#story-30-completed-backend-implementation). Personalized training, retraining, monitoring, and their tests remain open. The historical-data conflict is resolved by the implemented core-history and analytics-research boundary in SYSTEM_REFERENCE.md.

- [ ] Train the personalized collaborative-filtering model on retained analytics research observations once there's enough of it to evaluate (scaffolded: `PersonalizedDifficultyPredictor` is the plug point, `AggregateBaselinePredictor` is the shipped baseline; blocked until real play accumulates enough guesses to train and beat the baseline, likely months of casual play at the target scale)
- [ ] Add a scheduled retraining job for the personalized model
- [ ] Add a monitoring check comparing the personalized model's prediction accuracy against the simple aggregate baseline; if the personalized model stops beating the baseline, that's the signal it's stale and needs retraining, not just a fixed schedule

Tests:

- [ ] Unit tests for the personalized model's predictions against a held-out set of real guesses (deferred with the model itself; no trained model or real held-out guesses exist yet)
- [ ] Integration test: the retraining job runs and the monitoring check correctly flags a model that's stopped beating the baseline

## Story 34: First-party usage analytics

Core stores user-visible game summaries, statistics, and prepared difficulty scores. Analytics stores internal usage and research observations with a 365-day raw-event retention window. The analytics store, sessions, reports, and rate limiting already exist; instrumentation and the internal dashboard remain unfinished. History backend work belongs to story 30 and its frontend belongs to story 28.

- [ ] Instrument game session start/end (with the per-game summary), login, playlist creation, and song submission events to write to the analytics store; the required sessions and analytics store already exist
- [ ] Instrument rate-limit-exceeded, report-submitted, and failed-login-attempt events, for abuse visibility, not enforcement; the enforcement services already exist; replace the structured no-op event stubs
- [ ] Build a simple internal dashboard or query surface over the collected events, including a simple way to flag a user who's crossed a rate-limit or report threshold repeatedly
- [ ] No third-party trackers, matches this story's own scope and the "First-party usage analytics" framing

Tests:
- [ ] Integration test: each instrumented event type produces the expected record in the analytics store
- [ ] Integration test: the dashboard/query surface returns correct aggregates for known event data

Consent notice, transferred from story 37:

- [ ] Define and implement the reviewed analytics notice or consent behavior alongside usage instrumentation; this remains deferred with story 34

## Story 41: Submission content safety, non-music rejection and prompt-injection defense

Completed submission-classification work is recorded in [ARCHIVE.md](ARCHIVE.md#story-41-completed-backend-implementation). The source-match secondary signal and uncertain-case review remain deferred. Injection detection is part of the combined structured precheck; rejected submissions stop before source gathering, not before every LLM call.

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
- [ ] Integration test: concurrent structured-source gathering preserves immediate-work priority over the patient backlog

## Deferred: Curated metadata evidence

The metadataRaw compatibility column is unused. Evidence storage requires a defined story before feature work; full external payloads are not an approved storage contract.

- [ ] Define curated evidence fields, source provenance, external-data retention/refresh behavior, and a story/task breakdown against the real pipeline
- [ ] Define persistence, access, and retention tests alongside the approved evidence-storage scope

## Chore: Flutter DJ-model compliance

Flutter remains deprioritized. The inspected mobile client embeds youtube_player_iframe; the non-negotiable real YouTube app/site playback rule still applies.

- [ ] Replace the embedded player with a real YouTube app/site link-out
- [ ] Test that the mobile DJ action opens the official YouTube URL and never renders embedded playback

## Story 38: Observability

Actuator, FastAPI health/metrics, correlation IDs, OpenTelemetry, Sentry, and the dashboard JSON exist. Local delivery to Grafana Cloud has a recorded manual check. Production adaptation and verification remain open; completed implementation is in ARCHIVE.md.

- [ ] Import the dashboard into the live Grafana project
- [ ] Add production uptime monitoring against the deployed core health endpoint
- [ ] Configure and verify production metrics, logs, traces, and Sentry delivery, coordinated with story 7
- [ ] Add a credential-gated integration test proving a metrics scrape and log reach Grafana Cloud; keep secrets out of test output

## Story 28: UI redesign

Auth, library, Explore, imports, admin, lobby, gameplay, results, history, and statistics are implemented. Colocated tests cover the routes and representative states. Reviewed library/lobby, gameplay/voice, and history states have desktop browser coverage in both themes. Completed design and implementation records are in ARCHIVE.md. The complete visual matrix and real-device acceptance remain open.

### Visual and interaction acceptance

- [ ] Compare every app route and relevant state with its current mockup at 1440x900 in both themes
- [ ] Compare the landing page with its mobile mockups at 390x844 in both themes
- [ ] Fill gaps found in the route/state coverage matrix with focused unit or browser tests
- [ ] Complete remaining contrast, keyboard, and reduced-motion spot checks across the visual matrix
- [ ] Verify route navigation preserves the session connection, current state, and guess/link-out freshness
- [ ] Verify real-browser WebRTC join/leave and TURN fallback when a direct connection fails

### Two-factor authentication frontend, transferred from story 50

The backend setup, confirmation, backup-code, disable, and second-login endpoints are implemented. These frontend states gate Beta.

- [ ] Build the setup screen with provisioning QR, confirmation, one-time backup-code display, and disable action
- [ ] Add the second login step for TOTP or a backup code using the intermediate login token
- [ ] Unit tests: setup/confirmation, failure feedback, backup-code display, disable proof, and second-login transitions
- [ ] Browser tests: two-factor setup and login success, invalid code, and one-use backup-code behavior against the backend

### Deferred account and legal requirements

Account pages and legal copy remain deferred. History, summary statistics, deletion anonymization, and history/research personal export are already implemented.

- [ ] Define mockups and build profile/settings controls for account correction, deletion, and personal export
- [ ] Build privacy and terms routes from reviewed legal copy
- [ ] Update provider disclosures, stored-data inventory, retention wording, and any remaining personal-export scope
- [ ] Unit and browser tests: account permissions/failures, legal routes, disclosures, and export/deletion controls

### Other follow-ups

- [ ] Review the exact Joined-tab copy still wanted; the Explore icon and Join controls exist
- [ ] Optional: add exit transitions for removed list items outside the implemented voice departure motion, with focused reduced-motion tests

## Story 47: Product ground-truth pass

The approved product requirements are implemented in the recorded library, lobby, gameplay, voice, import, and admin slices. Later approved requirements take precedence over older mockups. Current product behavior belongs in GAME_DESIGN.md and FRONTEND_CONTENT.md; completed task records are in ARCHIVE.md.

- [ ] Compare remaining mockup states against current approved requirements and update any mismatches in the source files and published canvas
- [ ] Close interactive-state coverage gaps found in story 28's route/state matrix
- [ ] Complete the remaining voice, contrast, and motion acceptance checks in story 28; fixture-based two-client voice status coverage does not replace live TURN and DJ audio acceptance

## Story 7: Beta hosting on Azure Container Apps

Decision confirmed for Beta: Vercel continues to host the frontend. Azure Container Apps Consumption hosts the Spring core and FastAPI AI services in one EU environment. Azure provides the initial public deployment and cloud-platform experience. The backend moves to a fixed-price Hetzner server only when sustained Azure cost exceeds the whole-deployment ceiling. The frontend and Neon projects do not move in that migration.

The current realtime architecture is deliberately single-replica. Spring's simple STOMP broker, socket presence registries, session processing state are process-local. Scale at this stage means bounded admission of games on one tested core replica, not horizontal replication.

- [ ] Review the Log Analytics workspace that Container Apps created automatically in `hittiguess-rg` against the no-log-ingestion rule: switch the environment off it or cap its ingestion and retention
- [ ] Add cost anomaly alerts, a resource-group budget, and an Action Group that shuts down both apps at $12 to preserve a buffer below the $20 whole-deployment ceiling
- [ ] Set explicit CPU and memory limits for both services, validated under load before the values are committed
- [ ] Configure Container Apps secrets for Cloudflare TURN, Grafana, and Sentry
- [ ] Adapt Grafana Alloy's local Docker scrape configuration to the deployed environment, import the existing Grafana dashboard, configure production uptime monitoring against the public core health endpoint, and keep Sentry enabled
- [ ] Add a configurable active-game admission limit derived from a production-like load test, with a clear busy response when the tested capacity is reached
- [ ] Document the Hetzner migration runbook: provision one fixed-price EU server, deploy the same two images plus reverse proxy, move the backend DNS record, validate, then delete Azure resources

Tests and validation:

- [ ] Deployment smoke test: production-like Azure revision accepts HTTPS API traffic, WSS STOMP connection, internal core-to-AI request, and returns healthy status from both services
- [ ] Load test: measure core CPU, memory, WebSocket stability, and game-action latency across the expected peak concurrent games before setting app resource limits and the admission limit
- [ ] Cost-control test: verify each Azure budget alert and the $12 shutdown workflow against a non-production test resource group
- [ ] Manual production test: two devices complete login, playlist import, a game, voice chat, DJ tab-audio sharing, reconnect, and results over HTTPS
- [ ] Migration rehearsal: deploy the same images to an isolated Hetzner server and verify DNS cutover and rollback before Azure sustained-use migration is needed

## Story 8: Production Neon database provisioning

Decision confirmed: Neon hosts both production databases, replacing the earlier Supabase plan. The app has one production deployment, and its closed-beta access gate is a phase of that deployment, not a separate environment. New production projects replace the existing development data source. The transactional project carries the core schema and pgvector extension. The analytics project carries the independent event-store schema. The Free plan's automatic suspend and wake-up replaces Supabase's manual restore after a week of inactivity.

- [ ] Verify the core Flyway history on the new transactional project: the migrations ran on the core app's first start and the public ground-truth endpoint reads the database, but the extension, indexes, and migration history count are not yet inspected directly
- [ ] Verify the analytics Flyway history: the migration ran on the core app's first start; its independent history and `analytics_events` table are not yet inspected directly
- [ ] Decide and perform a controlled catalog-only data import if existing real songs should enter Beta; local test accounts, development credentials, and test-only data do not transfer
- [ ] Configure Neon Free-plan storage and compute usage notifications, document a `pg_dump` backup export procedure, and keep the spending limit enabled if an upgrade is later approved
- [ ] Verify the Spring connection pools survive Neon's five-minute idle suspend: the group-expiry sweeper queries every minute while the container runs, so connections should stay live, and the container scales to zero with the database; confirm with a real idle period and add pool settings (`max-lifetime`, keepalive) only if the first request after a suspend fails
- [ ] Update the privacy policy's database host once the Neon projects hold production data
- [ ] Verify all production services use only their intended database URLs and no browser client receives database credentials

Tests and validation:

- [ ] Production-like connection test: Spring and FastAPI connect through their injected Neon TLS URLs, while the AI service cannot reach analytics data
- [ ] Idle-suspend test: after Neon suspends the compute, the first core request succeeds without a connection error and returns within a few seconds
- [ ] Backup and restore rehearsal: export each Neon project and restore into isolated temporary projects without schema or data loss

## Release playtest acceptance and remaining tests

Stable lobby identities, competing-popup dismissal, Confirm tier selection, playlist add/import actions, deadline countdowns, round-event updates, short invite codes, and TURN mint/fallback behavior are implemented. Unit tests and desktop browser coverage exist for the reviewed library/lobby and gameplay/away states. Completed records are in ARCHIVE.md. The remaining requirements need evidence beyond those source-backed checks.

- [ ] Verify stable identity, name truncation, popup positioning, playlist actions, and the one-row tier picker in the complete desktop visual matrix
- [ ] Recover session, private guess state, DJ link-out, group membership, and chat history after reconnect when relevant events occurred during disconnection; test missed phase changes, membership/chat events, and session completion
- [ ] Verify configured Cloudflare credentials in a live browser and force relay use rather than relying on STUN
- [ ] Add focused frontend unit/browser tests for any identity, selected-total, popup, or countdown cases absent from the coverage matrix
- [ ] Add backend integration coverage for configured TURN responses if the existing HTTP-boundary unit tests leave integration gaps
- [ ] Complete two-client HTTPS coverage for round advancement, current DJ link-out, voice delivery, and tab-audio capture

## LAN playtest acceptance

Completed local-network setup and fix batches are in [ARCHIVE.md](ARCHIVE.md). Archived playtests do not replace this final full-game-plus-voice acceptance check.

- [ ] Manual multi-device playtest: full game plus voice, recorded here once played

## Closed-beta frontend showcase

The Vercel frontend and production API configuration are recorded as deployed. The closed-beta gate, signed access cookie, and proxy unit tests exist. Deployment configuration still needs a direct check; creating another project is not a prerequisite.

- [ ] Verify the existing Vercel project uses root frontend, production branch dev, the intended closed-beta flag/password, and hittiguess.com

## AI service production image

The non-root AI image, TLS URL support, and isolated image smoke test are implemented. The production AI Container App has a recorded Neon URL and provisional resource limits. Validated capacity and a real connection check remain open.

- [ ] Validate AI CPU and memory limits under the story 7 load test
- [ ] Verify the deployed AI service connects to the intended Neon core project over TLS

## Auth cookie domain for production

The frontend runs on hittiguess.com and the core API runs on a sibling subdomain. The backend sets `session_hint`, which the frontend's `proxy.ts` reads, so every auth cookie needs a shared parent domain to reach both hosts. `COOKIE_DOMAIN` sets it; unset, cookies stay host-only as in development.

Tests:

- [ ] Manual production test: log in on hittiguess.com and confirm `proxy.ts` sees `session_hint` on a protected route

## Backend deploy workflow

The workflow's smoke tests, SHA-tagged GHCR images, Azure OIDC federation, and first successful deployment are recorded in ARCHIVE.md. Optional environment approval remains a separate owner preference.

- [ ] Optional: decide whether production deploys need GitHub environment reviewers

## Container Apps creation

`scripts/azure/create-container-apps.ps1` creates the `hittiguess-ai` app (internal ingress, port 8000) and the `hittiguess-core` app (external ingress, port 8080) in `hittiguess-rg`, each at 0 to 1 replicas, from the public GHCR images. It prompts for every secret with hidden input, generates the JWT signing secret and the internal service key locally, stores everything as Container Apps secrets, and prints no secret. Initial limits are 0.5 vCPU and 1 GiB for the AI app and 1 vCPU and 2 GiB for the core app, provisional until the load test.

- [ ] Confirm a real Google login completes: the OAuth client's redirect URI is `https://api.hittiguess.com/login/oauth2/code/google`
- [ ] Verify the hittiguess.com sending domain in Resend and set `EMAIL_FROM_ADDRESS` to an address on it; the shared `onboarding@resend.dev` sender only delivers to the Resend account owner
- [ ] Add HTTP liveness and readiness probes to both apps, which `az containerapp create` cannot set

Tests:

- [ ] Deployment smoke test: HTTPS API traffic, a WSS STOMP connection, and a core-to-AI request succeed on the deployed apps

## Catalog seeding concurrency

Transaction-scoped advisory locks serialize saves and active enqueues for a YouTube ID across replicas. Integration tests cover simultaneous saves/enqueues and visibility while a backlog continues. Database uniqueness remains a follow-up.

- [ ] Merge existing duplicate rows before adding a unique songs(youtube_id) index and a partial unique index for active pending_imports
- [ ] Integration tests: duplicate cleanup preserves playlist/alternate-ID links, and both indexes reject concurrent duplicates

## Core startup time

A cold start of the core app takes about 31 seconds on Azure: about 15 seconds for the platform to schedule a node and start the container, and about 14 seconds for Spring to start on one vCPU. Local measurements against throwaway databases at one CPU gave 23 to 29 seconds for the baseline, 11 to 12 seconds with two CPUs, and 12 to 13 seconds with the JIT limited to the fast compiler (`-XX:TieredStopAtLevel=1`), which needs no extra CPU.

- [ ] Measure the next Azure cold start after the deploy and record the Spring startup time against the previous 14 seconds
- [ ] Re-evaluate the compiler limit under the story 7 load test, since it caps peak throughput of long-running work
- [ ] Decide between scale-to-zero and a minimum of one replica for the core app once the idle cost is checked against the monthly budget, since scale-to-zero costs about 30 seconds on the first request after five idle minutes

## Secrets in AI service logs

Before redaction was implemented, the production YouTube API key reached Container Apps console logs and Log Analytics. Redaction and its tests are complete. The recorded key revocation and old-log cleanup remain open.

- [ ] Rotate the production YouTube Data API key in Google Cloud, restrict the new key to the YouTube Data API, delete the old key, and update the `youtube-api-key` Container Apps secret
- [ ] Purge or let age out the log rows that still contain the old key once it is revoked
