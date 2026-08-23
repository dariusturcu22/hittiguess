# PROJECT_STATE.md: Stories and Status

This file is the backlog. Every planned or completed piece of functionality is a story with a stable ID. IDs reflect the order stories were written down, not priority; the order to work on stories is decided separately.

`TASKS.md` is what work actually happens from. This file is context, read it to understand the bigger picture behind a task, not as a list of things to do.

## Status legend

- Implemented: built and working.
- Ready: has confirmed tasks in TASKS.md, checked against the real code, can be worked on.
- In Progress: actively being worked on.
- Needs Definition: confirmed as wanted, tasks may exist as a draft, but not yet checked against the real code.

A story can have draft tasks written against it in TASKS.md while still marked Needs Definition. That alone doesn't unlock work. A story only becomes Ready once those tasks are confirmed accurate against the real codebase.

## Stories

| ID | Story | Area | Status |
|---|---|---|---|
| 1 | User authentication with refresh tokens | Backend | Implemented |
| 2 | Playlist creation and management | Backend | Implemented |
| 3 | Song submission and CRUD | Backend | Implemented |
| 4 | Multi-source metadata pipeline (YouTube, MusicBrainz, Wikipedia, Genius) synthesized by an LLM | Backend / AI | Implemented, in the AI microservice since story 6 |
| 5 | Printable PDF/QR card generation | Backend | Implemented |
| 7 | Hosting migration from Fly.io to Azure Container Apps | Infra | Needs Definition, draft tasks exist |
| 8 | Database migration to Azure Database for PostgreSQL Flexible Server, pgvector enabled | Infra | Needs Definition, depends on story 23 |
| 9 | DJ opens the real YouTube page or app instead of an embedded player | Game / Compliance | Needs Definition, draft tasks exist |
| 10 | Ephemeral game session: invite link, live join only, no persistence beyond a downloadable results export at the end | Game | Needs Definition |
| 11 | Real-time game sync over WebSocket | Realtime | Needs Definition |
| 12 | Voice chat between players in a session, mesh peer-to-peer with Cloudflare TURN fallback | Realtime | Needs Definition, draft tasks exist |
| 13 | Session-scoped text chat, same lifetime as the session | Realtime | Needs Definition |
| 14 | Song search by link or by keyword before submission | Frontend / Backend | Needs Definition |
| 15 | Song/playlist relational fix, so one song can belong to multiple playlists | Backend | Needs Definition |
| 16 | pgvector-based duplicate detection before running the metadata pipeline | Backend / AI | Needs Definition |
| 17 | Community song reports: report button, message, correct year, sources | Frontend / Backend | Needs Definition |
| 18 | Criteria for promoting a reported or newly submitted song to verified | Backend | Needs Definition |
| 19 | Admin bulk song import | Backend | Needs Definition |
| 20 | Local LLM option for lower-cost bulk metadata processing | AI | Needs Definition |
| 21 | Auto-generated featured playlists from extracted song metadata (genre, popularity, and similar signals) | Backend / AI | Needs Definition |
| 22 | Test coverage for existing and new functionality | Quality | Needs Definition |
| 23 | Song schema reconciliation against the current implementation | Backend | Needs Definition |
| 24 | Parallelize metadata pipeline fetches across sources | Backend / AI | Needs Definition |
| 25 | Add Discogs as a metadata source | Backend / AI | Needs Definition |
| 26 | Cache metadata pipeline results by artist/title or YouTube ID | Backend / AI | Needs Definition |
| 27 | Rate limiting | Backend | Needs Definition |
| 28 | UI redesign | Frontend | Needs Definition |

## Open questions

- Flutter app: keep, repurpose, or drop? Must follow the same real-link-out rule as the rest of the product in the meantime, no exceptions.
- Metadata source API usage: resolved. Source set is MusicBrainz, Discogs, and Wikidata; Genius, Last.fm, and live Wikipedia search were reviewed and dropped rather than fixed (see `docs/DECISIONS.md`).
- Compliance and production-readiness: privacy policy, terms of service, GDPR compliance, and observability (error tracking, monitoring) are a stated goal in `docs/VISION.md`, not yet broken into stories.

## Resolved questions

- Where is the current Postgres instance hosted? Supabase, confirmed. Story 8 (Azure Database for PostgreSQL migration) is a move off Supabase, not off an unknown host.
