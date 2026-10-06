# PROJECT_STATE.md: Stories and Status

This file is the backlog. Every planned or completed piece of functionality is a story with a stable ID. IDs reflect the order stories were written down, not priority; the order to work on stories is decided separately.

Dropped and consolidated stories are removed from the table below rather than kept with a Dropped/Consolidated status row; their reasoning lives in [ARCHIVE.md](ARCHIVE.md)'s "Dropped and consolidated stories" section instead. Gaps in the ID sequence are expected and not a sign something's missing.

`TASKS.md` is what work actually happens from. This file is context, read it to understand the bigger picture behind a task, not as a list of things to do.

## Status legend

- Implemented: built and working.
- Ready: has confirmed tasks in TASKS.md, checked against the real code, can be worked on.
- In Progress: actively being worked on.
- Needs Definition: confirmed as wanted, tasks may exist as a draft, but not yet checked against the real code.
- Dropped: considered and explicitly rejected, distinct from Needs Definition, which just means not yet gotten to. Removed from the table below, see `ARCHIVE.md`.
- Consolidated: merged into another story's tasks rather than kept as its own. Removed from the table below, see `ARCHIVE.md` for which story absorbed it.

A story can have draft tasks written against it in TASKS.md while still marked Needs Definition. That alone doesn't unlock work. A story only becomes Ready once those tasks are confirmed accurate against the real codebase.

## Stories

| ID | Story | Area | Status |
|---|---|---|---|
| 7 | Beta hosting: Azure Container Apps for the Spring core and FastAPI AI services, with a fixed-price Hetzner migration path if sustained Azure use exceeds the cost ceiling | Infra | Ready, deployment tasks in TASKS.md confirmed against the containerized services and current realtime architecture |
| 8 | Beta database provisioning: create separate core and analytics Neon Postgres projects | Infra | Ready, provisioning, migration, security, and validation tasks in TASKS.md |
| 24 | Parallelize metadata pipeline fetches across sources | Backend / AI | Implemented backend slice; completed history is in ARCHIVE.md. Gather-boundary timeout and priority-coordinator verification remain in TASKS.md |
| 28 | UI redesign | Frontend | Ready, one unified pass covering existing pages and gameplay screens, fresh visual direction, see `DECISIONS.md`; the design phase is done and all 53 screens are mocked up in `docs/design/hittiguess-design.html`. Batches A through E are implemented, while visual comparison, route smoke, representative-state, accessibility, and a few import-state gates remain open |
| 30 | Difficulty-tuned game session generation: exactly two top-level modes, Difficulty-Based (Auto-Generated, easy/medium/hard, defaults to international scope, sitelinks-based popularity weighting) and Custom (a playlist owned, member-of, or published-public, or one pasted directly) | Backend / AI / Frontend | Implemented generation and public-playlist slice; completed history is in ARCHIVE.md. Personalized training, retraining, monitoring, and tests remain in TASKS.md. The historical-data conflict remains pending documentation review |
| 34 | First-party usage analytics: track games played and session length through a self-hosted or custom event pipeline, no third-party trackers | Backend / Frontend | Needs Definition, draft tasks exist, confirmed blocked on story 33 and, for the abuse-visibility events, on stories 10, 13, 17, and 27 actually shipping too |
| 35 | Public ground-truth data API: verified `(artist, title, release_year)` triples only, no YouTube links or unverified entries | Backend | Implemented, built on `feature/ground-truth-api`: `GET /api/ground-truth/songs` pages verified triples with an explicit envelope and no YouTube-sourced fields under the general anonymous rate limit. The final YouTube-terms confirmation read stays open in `TASKS.md` as a pre-shipping gate |
| 38 | Observability: error tracking and monitoring | Infra / Quality | Ready, built on `feature/observability`: real Grafana Cloud and Sentry accounts now exist. Metrics ship through a Grafana Alloy container scraping both services locally; traces and logs both ship through Grafana Cloud's unified OTLP gateway, confirmed landing with real credentials; Sentry DSNs are configured for both services. Still open: no live dashboard import, no uptime monitoring (no production deployment exists yet to monitor). The automated usage-limit check is built: a daily scheduled read of Sentry accepted errors and Grafana active series against configurable quotas, warning at 80%, silent until its credentials exist |
| 40 | Catalog seeding queue (admin, patient, backlog-based) and user-facing bulk import (any user, immediate), absorbs story 19 | Backend / AI | Implemented import and seeding slice; completed history is in ARCHIVE.md. Alternate-ID integration after a pgvector match and its test remain in TASKS.md |
| 41 | Submission content safety: reject non-music and compilation submissions, defend LLM-facing steps against prompt injection in untrusted YouTube text | Backend / AI | Implemented classification slice; completed history is in ARCHIVE.md. Source-match tuning and uncertain-case review remain in TASKS.md. The injection-gate ordering conflict remains pending documentation review |
| 47 | Product ground-truth pass: the owner's full feature review as the binding spec, covering landing/auth, library, explore, playlist detail/edit/import, lobby, voice, gameplay, results, and admin, with `docs/design` mockups updated to match | Game / Frontend | Ready, tasks in TASKS.md confirmed against the real code; the built clusters are checked off and the open ones map to real code locations |
| 50 | Auth hardening: email verification on signup, a real password reset flow, and TOTP-based two-factor authentication with backup codes | Backend / Frontend | Ready, tasks checked against the real code; gates Beta (inviting friends to play with real accounts), not Local |

## Open questions

- Future idea, not yet a story: once story 34's abuse-visibility events (rate-limit-exceeded, report-submitted, and now story 41's flagged-injection-attempt) accumulate real data, a user-moderation feature (warnings, bans) based on a pattern of that behavior over time. Deliberately not scoped now, noted so it isn't lost.
- Story 30's personalized difficulty layer needs real interaction data to beat its own aggregate baseline; the aggregate baseline itself works as soon as story 10 ships and rounds start accumulating `Guess` rows, without needing months of data the way the collaborative-filtering enhancement does, but neither exists until story 10's `Guess` entity does. Likely won't clearly beat the baseline at the 100-200 user target scale until real usage accumulates over months of casual play.
- New, surfaced reading YouTube's Developer Policies directly for story 35: raw YouTube API Data (a video's title, description, channel name, view counts) that isn't user-authorized data must be deleted or refreshed within 30 calendar days of storage; it can't be kept indefinitely as-is. If `metadataRaw` ends up persisting these specific fields long-term, story 23's schema design needs to either refresh or drop them on that cycle. This doesn't affect story 35 itself (its published triples are sourced from MusicBrainz/Discogs/Wikidata, not from YouTube API Data), but it's a real, previously unflagged constraint on how `metadataRaw` can be shaped.

## Documentation references

Current product rules: [GAME_DESIGN.md](GAME_DESIGN.md). Technical blueprint: [ARCHITECTURE.md](ARCHITECTURE.md). API and entity reference: [SYSTEM_REFERENCE.md](SYSTEM_REFERENCE.md). Decision history: [DECISIONS.md](DECISIONS.md). Resolved backlog questions: [ARCHIVE.md](ARCHIVE.md#resolved-project-state-questions).

Completed stories are archived once their remaining requirements and tests have an active home in TASKS.md. The source-of-truth conflicts in [the documentation audit](DOCUMENTATION_AUDIT_2026-10-05.md) remain pending review.
