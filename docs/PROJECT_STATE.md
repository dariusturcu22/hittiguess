# PROJECT_STATE.md: Stories and Status

This file is the backlog. Every planned or completed piece of functionality is a story with a stable ID. IDs reflect the order stories were written down, not priority; the order to work on stories is decided separately.

`TASKS.md` is what work actually happens from. This file is context, read it to understand the bigger picture behind a task, not as a list of things to do.

## Status legend

- Implemented: built and working.
- Ready: has confirmed tasks in TASKS.md, checked against the real code, can be worked on.
- In Progress: actively being worked on.
- Needs Definition: confirmed as wanted, tasks may exist as a draft, but not yet checked against the real code.
- Dropped: considered and explicitly rejected, distinct from Needs Definition, which just means not yet gotten to.
- Consolidated: merged into another story's tasks rather than kept as its own, see the story it points to.

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
| 14 | Song search by link or by keyword before submission | Frontend / Backend | Ready |
| 15 | Song/playlist relational fix, so one song can belong to multiple playlists | Backend | Ready |
| 16 | pgvector-based duplicate detection before running the metadata pipeline | Backend / AI | Ready |
| 17 | Community song reports: report button, message, correct year, sources | Frontend / Backend | Ready, admin review surface additionally needs story 19's admin role, not yet built |
| 18 | Criteria for promoting a reported or newly submitted song to verified | Backend | Needs Definition |
| 19 | Admin bulk song import | Backend | Consolidated into story 40 |
| 20 | Local LLM option for lower-cost bulk metadata processing | AI | Needs Definition, model/technique choice needs a separate exploration pass first |
| 21 | Auto-generated featured playlists: consolidated into story 30, see there for its card-assembly tasks | Backend / AI | Consolidated into story 30 |
| 22 | Test coverage for existing and new functionality | Quality | Ready |
| 23 | Song schema reconciliation against the current implementation | Backend | Ready |
| 24 | Parallelize metadata pipeline fetches across sources | Backend / AI | Needs Definition, draft tasks exist, confirmed blocked, parallelizing today's mostly-stubbed source set is wasted work until story 25 (Discogs) ships and MusicBrainz/Wikidata (untasked, see open questions) are actually built |
| 25 | Add Discogs as a metadata source | Backend / AI | Ready |
| 26 | Cache metadata pipeline results by artist/title or YouTube ID | Backend / AI | Ready |
| 27 | Rate limiting | Backend | Ready |
| 28 | UI redesign | Frontend | Needs Definition |
| 29 | Content-based song recommender: audio-feature metadata (tempo, energy, valence), cosine similarity, works with zero user data | Backend / AI | Dropped, no viable audio-feature data source found (researched, see `TASKS.md`) |
| 30 | Difficulty-tuned game session generation: on-the-spot card sets scored to a group's actual players, easy/medium/hard, absorbs story 21's card-assembly mechanics | Backend / AI / Frontend | Needs Definition, draft tasks exist, confirmed blocked on story 10, no `Guess` entity exists yet for either the aggregate score or the personalized layer; medium-difficulty's scoring formula is also undecided |
| 31 | "Similar songs" feature using pgvector embeddings over song title and artist | Backend / AI | Dropped, only the audio-based version was worth building, see story 29 |
| 32 | LLM-as-judge catalog audit: periodic pass over the existing catalog flagging likely duplicate or mislabeled songs | Backend / AI | Ready, review surface additionally needs story 19's admin role, not yet built |
| 33 | Analytics data store: separate append-heavy store for usage/event data (games played, session length), apart from the transactional Postgres database | Infra | Ready |
| 34 | First-party usage analytics: track games played and session length through a self-hosted or custom event pipeline, no third-party trackers | Backend / Frontend | Needs Definition, draft tasks exist, confirmed blocked on story 33 and, for the abuse-visibility events, on stories 10, 13, 17, and 27 actually shipping too |
| 35 | Public ground-truth data API: verified `(artist, title, release_year)` triples only, no YouTube links or unverified entries | Backend | Ready, the verified-only filter additionally needs story 23's `verificationStatus` field, not yet built |
| 36 | Open-source collaboration readiness: `CONTRIBUTING.md`, `LICENSE`, `CODE_OF_CONDUCT.md`, issue/PR templates | Docs / Community | Ready |
| 37 | Privacy policy, terms of service, and GDPR compliance | Legal / Compliance | Ready |
| 38 | Observability: error tracking and monitoring | Infra / Quality | Ready |
| 39 | Group: persistent lobby a game session lives inside, invite-link membership, admin role, live-synced settings, chat and voice from creation, timer-based lifecycle | Game | Ready |
| 40 | Catalog seeding queue (admin, patient, backlog-based) and user-facing bulk import (any user, immediate), absorbs story 19 | Backend / AI | Needs Definition, draft tasks exist, blocked on story 20 (LLM tier choice), story 23 (schema), and the metadata-sourcing spike's real implementation landing |

## Open questions

- Flutter app: keep, repurpose, or drop? Must follow the same real-link-out rule as the rest of the product in the meantime, no exceptions.
- Metadata source API usage: resolved. Source set is MusicBrainz, Discogs, and Wikidata; Genius, Last.fm, and live Wikipedia search were reviewed and dropped rather than fixed (see `docs/DECISIONS.md`).
- Story 30's personalized difficulty layer needs real interaction data to beat its own aggregate baseline; the aggregate baseline itself works as soon as story 10 ships and rounds start accumulating `Guess` rows, without needing months of data the way the collaborative-filtering enhancement does, but neither exists until story 10's `Guess` entity does. Likely won't clearly beat the baseline at the 100-200 user target scale until real usage accumulates over months of casual play.
- Story 30: medium-difficulty group scoring formula is undecided. `DECISIONS.md`'s story 30 entry settles easy (the group's lowest individual predicted score) and hard (a plain average), but never states medium's, don't assume it matches hard's.
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
