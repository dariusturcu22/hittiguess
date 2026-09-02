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
| 7 | Hosting migration off Fly.io, target platform undecided | Infra | Needs Definition |
| 8 | Database migration off Supabase, whether to migrate at all and to what platform both undecided | Infra | Needs Definition |
| 9 | DJ opens the real YouTube page or app instead of an embedded player | Game / Compliance | Needs Definition, draft tasks exist, confirmed blocked on stories 10, 11, and 39 |
| 10 | Game session: round-by-round gameplay within a group, rounds, guesses, betting, scoring, win condition; ephemeral, purged when the session ends except for a downloadable results export | Game | Ready |
| 11 | Real-time game sync over WebSocket | Realtime | Ready |
| 12 | Voice chat between players in a group, mesh peer-to-peer with Cloudflare TURN fallback, joinable and leavable anytime for the group's lifetime | Realtime | Needs Definition, draft tasks exist, confirmed blocked on stories 11 and 39 |
| 13 | Group-scoped text chat, active from group creation until the group is deleted | Realtime | Needs Definition, draft tasks exist, confirmed blocked on stories 11 and 39 |
| 14 | Song search by link or by keyword before submission | Frontend / Backend | Needs Definition |
| 15 | Song/playlist relational fix, so one song can belong to multiple playlists | Backend | Needs Definition |
| 16 | pgvector-based duplicate detection before running the metadata pipeline | Backend / AI | Needs Definition |
| 17 | Community song reports: report button, message, correct year, sources | Frontend / Backend | Needs Definition |
| 18 | Criteria for promoting a reported or newly submitted song to verified | Backend | Needs Definition |
| 19 | Admin bulk song import | Backend | Needs Definition |
| 20 | Local LLM option for lower-cost bulk metadata processing | AI | Needs Definition |
| 21 | Auto-generated featured playlists: an agent takes a themed request (for example "90s rock"), searches the catalog, calls the metadata pipeline, and assembles a validated card set from extracted song metadata (genre, popularity, similar signals) | Backend / AI | Needs Definition |
| 22 | Test coverage for existing and new functionality | Quality | Needs Definition, draft tasks exist |
| 23 | Song schema reconciliation against the current implementation | Backend | Needs Definition, draft tasks exist |
| 24 | Parallelize metadata pipeline fetches across sources | Backend / AI | Needs Definition |
| 25 | Add Discogs as a metadata source | Backend / AI | Needs Definition |
| 26 | Cache metadata pipeline results by artist/title or YouTube ID | Backend / AI | Needs Definition |
| 27 | Rate limiting | Backend | Needs Definition, draft tasks exist |
| 28 | UI redesign | Frontend | Needs Definition |
| 29 | Content-based song recommender: audio-feature metadata (tempo, energy, valence), cosine similarity, works with zero user data | Backend / AI | Needs Definition, audio-feature data source not chosen |
| 30 | Collaborative filtering recommendations from real interaction data (ratings, or implicit signals like guess correctness and guess time) | Backend / AI | Needs Definition, blocked on enough real usage data existing |
| 31 | "Similar songs" feature using pgvector embeddings over song title and artist | Backend / AI | Needs Definition |
| 32 | LLM-as-judge catalog audit: periodic pass over the existing catalog flagging likely duplicate or mislabeled songs | Backend / AI | Needs Definition |
| 33 | Analytics data store: separate append-heavy store for usage/event data (games played, session length), apart from the transactional Postgres database | Infra | Needs Definition |
| 34 | First-party usage analytics: track games played and session length through a self-hosted or custom event pipeline, no third-party trackers | Backend / Frontend | Needs Definition, depends on story 33 |
| 35 | Public ground-truth data API: verified `(artist, title, release_year)` triples only, no YouTube links or unverified entries | Backend | Needs Definition |
| 36 | Open-source collaboration readiness: `CONTRIBUTING.md`, `LICENSE`, `CODE_OF_CONDUCT.md`, issue/PR templates | Docs / Community | Needs Definition, draft tasks exist |
| 37 | Privacy policy, terms of service, and GDPR compliance | Legal / Compliance | Needs Definition, draft tasks exist |
| 38 | Observability: error tracking and monitoring | Infra / Quality | Needs Definition, draft tasks exist |
| 39 | Group: persistent lobby a game session lives inside, invite-link membership, admin role, live-synced settings, chat and voice from creation, timer-based lifecycle | Game | Ready |

## Open questions

- Flutter app: keep, repurpose, or drop? Must follow the same real-link-out rule as the rest of the product in the meantime, no exceptions.
- Metadata source API usage: resolved. Source set is MusicBrainz, Discogs, and Wikidata; Genius, Last.fm, and live Wikipedia search were reviewed and dropped rather than fixed (see `docs/DECISIONS.md`).
- Stories 29, 30, and 31 are the building blocks of one overall recommendation strategy, not one story, since they have very different data requirements and can ship independently. Content-based (29) and embeddings (31) need no user interaction data; collaborative filtering (30) does, and likely won't have enough of it at the 100-200 user target scale until real usage accumulates.
- Story 29 needs an audio-feature data source decided; Spotify was suggested but not approved, so it's not the source. Whatever source gets chosen needs the same terms-of-use review MusicBrainz, Discogs, and Wikidata already got before it's more than an idea.
- Story 32 (LLM catalog audit) and story 18 (verification criteria) are related but distinct: 18 is the per-submission path to verified, 32 is a periodic pass over the whole existing catalog. Whether 32 feeds into 18's criteria or stays a separate audit tool isn't decided.
- Story 35's data (artist/title/release_year triples, sourced from MusicBrainz/Discogs/Wikidata, all CC0) doesn't include anything sourced from the YouTube API, so it doesn't carry the redistribution risk a YouTube-link-inclusive version would have. Worth a final confirmation read of YouTube's terms before shipping regardless, since the catalog's provenance mixes sources.
- Story 23: whether release year should be `submittedYear` (immutable) plus `verifiedYear` (null until verified), or one mutable field plus `verificationStatus`. The two-field version preserves the original submission after a correction; the one-field version is simpler. Undecided.
- Story 23: how multiple artists (a main artist plus one or more featured artists) are stored and guessed. Today's `Song.artist` is a single string. Undecided whether storage should be an array, whether featured artists must be guessed correctly too, and what the guess-box UI looks like for more than one artist. Affects story 10's artist/title guess box and the AI microservice's extraction logic too, not just this story's schema.
- How much typo tolerance an in-round artist/title guess gets before counting as correct (see `GAME_DESIGN.md`'s Earning tokens section) is undecided, needs testing to balance against false positives. Distinct from story 18's song-verification criteria, this is about matching a player's guess text during a round, not about trusting a submitted song's metadata.
- Deployment target (story 7) and database target (story 8) are both undecided pending more research, deliberately deferred until the app is close to feature-complete locally. Leaving Fly.io is confirmed; Azure Container Apps as the replacement is not. Migrating off Supabase at all is undecided, let alone a target; Azure Database for PostgreSQL and Neon have both come up but neither is chosen.

## Resolved questions

- Where is the current Postgres instance hosted? Supabase, confirmed.
- Compliance and production-readiness: privacy policy, terms of service, GDPR compliance, and observability were a stated goal in `docs/VISION.md` with no story attached. Now stories 37 and 38.
- Story 10's session-state persistence and win-condition scaling were open questions. Both resolved: ephemeral Postgres rows, and an admin-configured card count bounded by player count. The group/game-session split that resolved them is documented in `ARCHITECTURE.md` and `GAME_DESIGN.md`, and logged in `DECISIONS.md`. Now story 39.
