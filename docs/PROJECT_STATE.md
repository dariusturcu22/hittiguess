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
| 9 | DJ opens the real YouTube page or app instead of an embedded player | Game / Compliance | Needs Definition, draft tasks exist, confirmed blocked on stories 10 and 11 |
| 10 | Ephemeral game session: invite link, live join only, no persistence beyond a downloadable results export at the end | Game | Needs Definition, draft tasks exist |
| 11 | Real-time game sync over WebSocket | Realtime | Needs Definition, draft tasks exist |
| 12 | Voice chat between players in a session, mesh peer-to-peer with Cloudflare TURN fallback | Realtime | Needs Definition, draft tasks exist, confirmed blocked on story 11 |
| 13 | Session-scoped text chat, same lifetime as the session | Realtime | Needs Definition |
| 14 | Song search by link or by keyword before submission | Frontend / Backend | Needs Definition |
| 15 | Song/playlist relational fix, so one song can belong to multiple playlists | Backend | Needs Definition |
| 16 | pgvector-based duplicate detection before running the metadata pipeline | Backend / AI | Needs Definition |
| 17 | Community song reports: report button, message, correct year, sources | Frontend / Backend | Needs Definition |
| 18 | Criteria for promoting a reported or newly submitted song to verified | Backend | Needs Definition |
| 19 | Admin bulk song import | Backend | Needs Definition |
| 20 | Local LLM option for lower-cost bulk metadata processing | AI | Needs Definition |
| 21 | Auto-generated featured playlists: an agent takes a themed request (for example "90s rock"), searches the catalog, calls the metadata pipeline, and assembles a validated card set from extracted song metadata (genre, popularity, similar signals) | Backend / AI | Needs Definition |
| 22 | Test coverage for existing and new functionality | Quality | Needs Definition |
| 23 | Song schema reconciliation against the current implementation | Backend | Needs Definition |
| 24 | Parallelize metadata pipeline fetches across sources | Backend / AI | Needs Definition |
| 25 | Add Discogs as a metadata source | Backend / AI | Needs Definition |
| 26 | Cache metadata pipeline results by artist/title or YouTube ID | Backend / AI | Needs Definition |
| 27 | Rate limiting | Backend | Needs Definition |
| 28 | UI redesign | Frontend | Needs Definition |
| 29 | Content-based song recommender: Spotify audio-feature metadata (tempo, energy, valence), cosine similarity, works with zero user data | Backend / AI | Needs Definition, blocked on a Spotify API terms review |
| 30 | Collaborative filtering recommendations from real interaction data (ratings, or implicit signals like guess correctness and guess time) | Backend / AI | Needs Definition, blocked on enough real usage data existing |
| 31 | "Similar songs" feature using pgvector embeddings over song title and artist | Backend / AI | Needs Definition |
| 32 | LLM-as-judge catalog audit: periodic pass over the existing catalog flagging likely duplicate or mislabeled songs | Backend / AI | Needs Definition |
| 33 | Analytics data store: separate append-heavy store for usage/event data (games played, session length), apart from the transactional Postgres database | Infra | Needs Definition |
| 34 | First-party usage analytics: track games played and session length through a self-hosted or custom event pipeline, no third-party trackers | Backend / Frontend | Needs Definition, depends on story 33 |
| 35 | Public ground-truth data API: verified `(artist, title, release_year)` triples only, no YouTube links or unverified entries | Backend | Needs Definition |
| 36 | Open-source collaboration readiness: `CONTRIBUTING.md`, `LICENSE`, `CODE_OF_CONDUCT.md`, issue/PR templates | Docs / Community | Needs Definition |

## Open questions

- Flutter app: keep, repurpose, or drop? Must follow the same real-link-out rule as the rest of the product in the meantime, no exceptions.
- Metadata source API usage: resolved. Source set is MusicBrainz, Discogs, and Wikidata; Genius, Last.fm, and live Wikipedia search were reviewed and dropped rather than fixed (see `docs/DECISIONS.md`).
- Compliance and production-readiness: privacy policy, terms of service, GDPR compliance, and observability (error tracking, monitoring) are a stated goal in `docs/VISION.md`, not yet broken into stories.
- Story 10 session state: in-memory only, or ephemeral rows in Postgres cleaned up on session end? `ARCHITECTURE.md` doesn't say. Needs deciding before the `GameSession`/`Player`/`Round`/`Guess` entities are built.
- Story 10 win condition: `GAME_DESIGN.md` says required timeline length "can scale with player count" but doesn't give the actual rule. Needs pinning down before the win-condition task can be implemented.
- Stories 29, 30, and 31 are the building blocks of one overall recommendation strategy, not one story, since they have very different data requirements and can ship independently. Content-based (29) and embeddings (31) need no user interaction data; collaborative filtering (30) does, and likely won't have enough of it at the 100-200 user target scale until real usage accumulates.
- Story 29's Spotify audio-feature source needs its terms of use read directly before it's more than an idea, the same review MusicBrainz, Discogs, and Wikidata already got. Spotify restricted access to its audio-features endpoints for new API keys in late 2024; whether this project's use case still qualifies isn't confirmed.
- Story 32 (LLM catalog audit) and story 18 (verification criteria) are related but distinct: 18 is the per-submission path to verified, 32 is a periodic pass over the whole existing catalog. Whether 32 feeds into 18's criteria or stays a separate audit tool isn't decided.
- Story 35's data (artist/title/release_year triples, sourced from MusicBrainz/Discogs/Wikidata, all CC0) doesn't include anything sourced from the YouTube API, so it doesn't carry the redistribution risk a YouTube-link-inclusive version would have. Worth a final confirmation read of YouTube's terms before shipping regardless, since the catalog's provenance mixes sources.

## Resolved questions

- Where is the current Postgres instance hosted? Supabase, confirmed. Story 8 (Azure Database for PostgreSQL migration) is a move off Supabase, not off an unknown host.
