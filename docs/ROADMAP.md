# ROADMAP.md: Implementation Order

This file orders remaining work. [TASKS.md](TASKS.md) owns the requirements and tests, and [PROJECT_STATE.md](PROJECT_STATE.md) governs whether a story is ready to start. Completed implementation summaries and the checked frontend batch plan are in [ARCHIVE.md](ARCHIVE.md#completed-roadmap-implementation-history).

## Readiness tiers

- **Local**: the owner can complete a game with another local player, including the admin surface, voice, and DJ audio. Stories 28 and 47 retain visual, interaction, test, and mockup-review follow-ups. Release-playtest reconnect and test requirements and final LAN acceptance remain open in TASKS.md.
- **Beta**: real accounts can play a deployed app. Story 50's remaining two-factor frontend work and stories 7 and 8's provisioning and validation must be complete. Azure Container Apps, Vercel, and separate transactional and analytics Neon projects are the selected deployment plan; Hetzner is the cost-control fallback.
- **Finished**: the deployed app is ready for public announcement with story 34's first-party analytics and its dependent consent notice complete.

## Local: review and validate

1. Story 47: reconcile the remaining product-review requirements and design mockups, complete interactive-state test coverage, and run the voice, contrast, and motion acceptance checks.
2. Story 28: complete the desktop/mobile and light/dark rendered matrix and the transferred frontend requirements and tests. The completed marketing, theme, and scrollbar batches are archived. Profile/settings direction and optional exit motion remain explicit follow-ups; optional motion is not a release gate.
3. Release playtest acceptance: retain the unresolved parts of the compound UI requirements, missed-event reconnect recovery, configured TURN validation, frontend/backend test coverage, and two-client HTTPS browser checks.
4. LAN playtest acceptance: complete and record the final full-game-plus-voice multi-device run.

The documentation audit's design conflicts remain pending owner review. A visual pass must use the reviewed source of truth rather than treating conflicting mockups and later requirements as interchangeable.

## Backend follow-ups

- Story 24: gather-boundary timeout, its unit test, and priority-coordinator verification.
- Story 30: personalized model training, scheduled retraining, monitoring, and held-out tests. The historical-data conflict remains pending review before the data prerequisite can be resolved. This enhancement does not block Local.
- Story 41: deferred source-match confidence tuning and uncertain-case manual review.
- Story 35: the existing pre-shipping terms-confirmation gate remains in TASKS.md pending reconciliation with the recorded terms read.
- Flutter DJ-model compliance: the embedded-player replacement remains open; Flutter stays deprioritized behind the web app.

## Beta: deploy and validate

1. Story 50: complete two-factor setup and the second login step in the frontend, with the required tests.
2. Story 7: provision Azure Container Apps, cost controls, production configuration, image delivery, load/admission validation, and the Hetzner migration runbook/rehearsal.
3. Story 8: provision and validate the transactional and analytics Neon projects, migrations, backups, and connection boundaries.
4. Story 38: import the dashboard into the live environment, add production uptime monitoring once a deployment exists, and retain the real observability-delivery test requirement.
5. Complete production device-level game, reconnect, voice, DJ audio, import, and results acceptance before inviting friends for Beta matches.

## Finished

Story 34 owns first-party usage and abuse-event instrumentation, the query/dashboard and game-history surfaces, their tests, and the consent notice transferred from story 37. Its existing story gate remains in PROJECT_STATE.md; the completed analytics store and backend enforcement records are in ARCHIVE.md.
