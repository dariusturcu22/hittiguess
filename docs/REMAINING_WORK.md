# Remaining work: 2026-10-10

This inventory summarizes [TASKS.md](TASKS.md). Completed records are in [ARCHIVE.md](ARCHIVE.md), implementation order is in [ROADMAP.md](ROADMAP.md), and story readiness is in [PROJECT_STATE.md](PROJECT_STATE.md). Source presence and fixture tests do not prove live production or real-device acceptance.

## Review pending

Automatic verified-song embedding indexing is implemented in [PR #274](https://github.com/dariusturcu22/hittiguess/pull/274). It includes verified inserts/upgrades, artist/title invalidation, missing-embedding backfill, durable retry leases, and duplicate-reuse tests. The merged branch retains the gap until that PR lands. The documentation reconciliation closes the remaining audit corrections; deferred requirements and acceptance tasks remain listed below.

## Product implementation and local acceptance

- Recover session state, private guess state, DJ link-out, group membership/chat history, and session completion after events missed during a disconnect. Existing on-connect session refresh and round-event updates cover only part of this.
- Complete two-factor setup, QR provisioning, confirmation, backup-code display, disable, and second login screens with unit and backend-backed browser tests. The backend endpoints already exist.
- Complete the desktop dark/light visual and interactive-state matrix for app screens, including lobby identities, tier/popup layout, imports, gameplay, results, admin, and history. Only the landing page needs mobile checks.
- Reconcile any remaining mockup-state mismatches, cover gaps in route/state tests, and finish contrast, keyboard, reduced-motion, and navigation continuity checks.
- Verify real two-client HTTPS voice and DJ tab-audio delivery, join/leave while playing, current-round link-out, and forced TURN relay use. Synthetic-tone/fixture voice-status tests and TURN mint/fallback unit tests already exist.
- Record the final multi-device full-game-plus-voice LAN acceptance run.
- Review the exact Joined-tab copy still wanted. Explore and Join controls already exist.
- Verify concurrent metadata gathering still respects the immediate-work/patient-backlog priority coordinator.
- Merge existing catalog duplicates and add database uniqueness for song YouTube IDs and active pending imports, preserving playlist and alternate-ID links with integration tests. Transaction locks already prevent concurrent duplicates through the current services.

## Production configuration and validation

- Rotate and restrict the production YouTube Data API key, revoke the logged key, update the Container Apps secret, and purge or age out old log rows. Redaction code and tests are implemented.
- Review/cap the Azure Log Analytics workspace, add anomaly and resource-group alerts, implement the $12 shutdown Action Group, and test cost controls against an isolated resource group. The $20 subscription budget is recorded as created.
- Load-test both services and WebSockets, validate CPU/memory limits, derive a game-admission limit, and give capacity failures a clear response.
- Measure deployed core cold-start time after the JIT change, check throughput under load, and decide scale-to-zero versus a minimum replica within the budget.
- Provision production Cloudflare TURN, Grafana, and Sentry settings; adapt Alloy scraping, import the dashboard, add uptime monitoring, and verify real metrics/logs/traces/error delivery. Add the credential-gated delivery integration test.
- Verify the existing Vercel project root, dev production branch, beta-gate variables, domain, API origin, and shared auth cookies. Confirm real Google login and ordinary verified-account login.
- Verify the Resend sending domain and sender address. A recorded API key does not establish delivery to ordinary testers.
- Add explicit Container Apps liveness/readiness probes and verify deployed HTTPS API traffic, WSS STOMP, and core-to-AI requests.
- Inspect both production Neon migration histories, core pgvector/indexes, correct TLS URLs and service boundaries, and AI's real core connection.
- Configure Neon usage notifications, document backup exports, rehearse isolated restores, and test the first request after idle suspend. Local independent-migration tests already pass.
- Decide whether to import existing real catalog songs into Beta and perform only the approved catalog-only transfer.
- Document and rehearse the Hetzner fallback, DNS cutover, and rollback.
- Complete production two-device login, import, full game, reconnect, voice, DJ audio, and results acceptance.
- Optional: decide whether GitHub production deployments need required reviewers.

## Usage analytics, definition required

Story 34 still needs a confirmed breakdown for usage/abuse events, the internal query/dashboard, repeated-abuse flags, notice behavior, and integration tests. Its database and enforcement prerequisites exist. Participant-visible history is already in core and is not unfinished analytics work. No third-party trackers are planned.

## Deferred or optional

- Profile/settings mockups and account correction, deletion, and export controls; privacy/terms routes; provider disclosures, inventory, retention, and remaining export scope. Existing backend controls and history/research handling do not complete these views.
- Personalized model training, scheduled retraining, baseline comparison, and held-out evaluation after enough real observations exist.
- Source-match safety-confidence tuning and uncertain-case manual review after submission data supports thresholds.
- A gather-boundary hard timeout and its unit test; source request timeouts already exist.
- Flutter's replacement of embedded YouTube playback with the official app/site link-out, plus tests. Flutter remains deprioritized.
- Raw-evidence storage scope before populating metadataRaw; the current pipeline leaves it unused.
- Optional removed-item exit motion beyond the implemented voice departure animation.

Genre enrichment and automatic Topic-upload upgrade suggestions are dropped. They are not remaining implementation work. History, prepared global difficulty, generation at Start, verified-result catalog persistence, alternate-upload reuse, and the reviewed API/product corrections are implemented.
