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
| 8 | Production Neon database validation | Infra | Ready; both projects are provisioned. Migration inspection, backups/restores, idle wake, and connection-boundary checks remain in TASKS.md |
| 24 | Parallelize metadata pipeline fetches across sources | Backend / AI | Implemented backend slice; completed history is in ARCHIVE.md. Gather-boundary timeout and priority-coordinator verification remain in TASKS.md |
| 28 | UI redesign and remaining frontend requirements | Frontend | Ready; app routes, history, statistics, and reviewed library/lobby/gameplay states are implemented. Full desktop visual/state acceptance, two-factor frontend, and live media checks remain. Account/legal pages remain deferred |
| 30 | Difficulty-tuned game session generation: exactly two top-level modes, Difficulty-Based (Auto-Generated, easy/medium/hard, defaults to international scope, sitelinks-based popularity weighting) and Custom (a playlist owned, member-of, or published-public, or one pasted directly) | Backend / AI / Frontend | Implemented generation and public-playlist slice; completed history is in ARCHIVE.md. Core history, prepared global difficulty, and generation at Start are implemented; participant history and statistics are in story 28. Personalized ML remains deferred |
| 34 | First-party usage analytics: track games played and session length through a self-hosted or custom event pipeline, no third-party trackers | Backend / Frontend | Needs Definition; analytics storage and enforcement prerequisites exist. Usage/abuse instrumentation, dashboard requirements, and notice behavior still need confirmation against current code before feature work |
| 38 | Observability: error tracking and monitoring | Infra / Quality | Ready, built on `feature/observability`: real Grafana Cloud and Sentry accounts now exist. Metrics ship through a Grafana Alloy container scraping both services locally; traces and logs both ship through Grafana Cloud's unified OTLP gateway, confirmed landing with real credentials; Sentry DSNs are configured for both services. Still open: no live dashboard import, no production uptime monitoring; the Azure apps now exist. The automated usage-limit check is built: a daily scheduled read of Sentry accepted errors and Grafana active series against configurable quotas, warning at 80%, silent until its credentials exist |
| 41 | Submission content safety: reject non-music and compilation submissions, defend LLM-facing steps against prompt injection in untrusted YouTube text | Backend / AI | Implemented combined-precheck classification and injection detection; rejected submissions stop before source gathering. Completed history is in ARCHIVE.md. Source-match tuning and uncertain-case review remain in TASKS.md |
| 47 | Product ground-truth pass: the owner's full feature review as the binding spec, covering landing/auth, library, explore, playlist detail/edit/import, lobby, voice, gameplay, results, and admin, with `docs/design` mockups updated to match | Game / Frontend | Ready, tasks in TASKS.md confirmed against the real code; completed clusters are archived; remaining mockup, coverage, and acceptance tasks map to current code |

## Open questions

- Future idea, not yet a story: once story 34's abuse-visibility events (rate-limit-exceeded, report-submitted, and now story 41's flagged-injection-attempt) accumulate real data, a user-moderation feature (warnings, bans) based on a pattern of that behavior over time. Deliberately not scoped now, noted so it isn't lost.
- Story 30's personalized difficulty layer needs enough retained research observations to evaluate against the shipped global baseline. The observation pipeline and anonymous aggregates exist; model training remains deferred until real usage supports a meaningful held-out evaluation.
- New, surfaced reading YouTube's Developer Policies directly for story 35: raw YouTube API Data (a video's title, description, channel name, view counts) that isn't user-authorized data must be deleted or refreshed within 30 calendar days of storage; it can't be kept indefinitely as-is. If `metadataRaw` ends up persisting these specific fields long-term, story 23's schema design needs to either refresh or drop them on that cycle. This doesn't affect story 35 itself (its published triples are sourced from MusicBrainz/Discogs/Wikidata, not from YouTube API Data), but it's a real, previously unflagged constraint on how `metadataRaw` can be shaped.

## Documentation references

Current product rules: [GAME_DESIGN.md](GAME_DESIGN.md). Technical blueprint: [ARCHITECTURE.md](ARCHITECTURE.md). API and entity reference: [SYSTEM_REFERENCE.md](SYSTEM_REFERENCE.md). Decision history: [DECISIONS.md](DECISIONS.md). Resolved backlog questions: [ARCHIVE.md](ARCHIVE.md#resolved-project-state-questions).

Completed stories are archived once their remaining requirements and tests have an active home in TASKS.md. Reviewed audit decisions and the remaining deferred requirements are in [the documentation audit](DOCUMENTATION_AUDIT_2026-10-05.md). Automatic embedding indexing is pending review in PR #274; TASKS.md retains it until merge.
