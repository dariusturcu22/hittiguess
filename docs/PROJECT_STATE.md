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
| 10 | Game session: round-by-round gameplay within a group, rounds, guesses, betting, scoring, win condition, and two session-long guess-based leaderboards; ephemeral, purged when the session ends except for a downloadable results export | Game | Ready |
| 11 | Real-time game sync over WebSocket | Realtime | Ready |
| 12 | Voice chat between players in a group, mesh peer-to-peer with Cloudflare TURN fallback, joinable and leavable anytime for the group's lifetime | Realtime | Needs Definition, draft tasks exist, confirmed blocked on stories 11 and 39 |
| 13 | Group-scoped text chat, active from group creation until the group is deleted | Realtime | Needs Definition, draft tasks exist, confirmed blocked on stories 11 and 39 |
| 14 | Song search by link or by keyword before submission | Frontend / Backend | Ready |
| 15 | Song/playlist relational fix, so one song can belong to multiple playlists | Backend | Ready |
| 16 | pgvector-based duplicate detection before running the metadata pipeline | Backend / AI | Ready |
| 17 | Community song reports and confirmations: report button, message, correct year, sources; a separate thumbs-up confirmation on low-confidence cards; admin review queue ranked by a five-tier priority order | Frontend / Backend | Ready, admin review surface additionally needs story 19's admin role, not yet built |
| 18 | Criteria for promoting a reported or newly submitted song to verified | Backend | Needs Definition, criteria decided and validated against real data (`DECISIONS.md`), draft tasks exist, blocked on story 23's `verificationStatus` field, which doesn't exist yet |
| 19 | Admin bulk song import | Backend | Consolidated into story 40 |
| 20 | Local LLM option for lower-cost bulk metadata processing | AI | Needs Definition, model choice decided and validated against real data (`DECISIONS.md`): gpt-5-nano for reconciliation, DeepSeek-V4-Flash for Wikipedia extraction. Blocked on the same schema and implementation work as story 40 |
| 21 | Auto-generated featured playlists: consolidated into story 30, see there for its card-assembly tasks | Backend / AI | Consolidated into story 30 |
| 22 | Test coverage for existing and new functionality | Quality | Ready |
| 23 | Song schema reconciliation against the current implementation | Backend | Ready |
| 24 | Parallelize metadata pipeline fetches across sources | Backend / AI | Needs Definition, draft tasks exist, confirmed blocked, parallelizing today's mostly-stubbed source set is wasted work until story 25 (Discogs) and the spike-validated MusicBrainz/Wikidata/Wikipedia implementations actually ship |
| 25 | Add Discogs as a metadata source | Backend / AI | Ready, implementation validated in `ai/spikes/discogs_spike.py`, including a live-confirmed fallback fix for releases with no linked master |
| 26 | Cache metadata pipeline results by artist/title or YouTube ID | Backend / AI | Ready |
| 27 | Rate limiting | Backend | Ready |
| 28 | UI redesign | Frontend | Ready, one unified pass covering existing pages and the not-yet-built gameplay screens, fresh visual direction, see `DECISIONS.md` |
| 29 | Content-based song recommender: audio-feature metadata (tempo, energy, valence), cosine similarity, works with zero user data | Backend / AI | Dropped, no viable audio-feature data source found (researched, see `TASKS.md`) |
| 30 | Difficulty-tuned game session generation: on-the-spot card sets scored to a group's actual players, easy/medium/hard, defaults to international scope, absorbs story 21's card-assembly mechanics; playing from an existing playlist now also covers published-public ones | Backend / AI / Frontend | Needs Definition, draft tasks exist, confirmed blocked on story 10, no `Guess` entity exists yet for either the aggregate score or the personalized layer |
| 31 | "Similar songs" feature using pgvector embeddings over song title and artist | Backend / AI | Dropped, only the audio-based version was worth building, see story 29 |
| 32 | LLM-as-judge catalog audit: periodic pass over the existing catalog flagging likely duplicate or mislabeled songs | Backend / AI | Dropped, a scheduled/periodic pass is redundant now that every song already flows through story 18's verification pipeline, both on submission and, for the fast tier, again afterward through the patient tier. A separate, manual/on-demand admin tool isn't ruled out but isn't a defined feature either |
| 33 | Analytics data store: separate append-heavy store for usage/event data (games played, session length), apart from the transactional Postgres database | Infra | Ready |
| 34 | First-party usage analytics: track games played and session length through a self-hosted or custom event pipeline, no third-party trackers | Backend / Frontend | Needs Definition, draft tasks exist, confirmed blocked on story 33 and, for the abuse-visibility events, on stories 10, 13, 17, and 27 actually shipping too |
| 35 | Public ground-truth data API: verified `(artist, title, release_year)` triples only, no YouTube links or unverified entries | Backend | Ready, the verified-only filter additionally needs story 23's `verificationStatus` field, not yet built |
| 36 | Open-source collaboration readiness: `CONTRIBUTING.md`, `LICENSE`, `CODE_OF_CONDUCT.md`, issue/PR templates | Docs / Community | Ready |
| 37 | Privacy policy, terms of service, and GDPR compliance | Legal / Compliance | Ready |
| 38 | Observability: error tracking and monitoring | Infra / Quality | Ready |
| 39 | Group: persistent lobby a game session lives inside, invite-link membership, admin role, live-synced settings, chat and voice from creation, timer-based lifecycle | Game | Ready |
| 40 | Catalog seeding queue (admin, patient, backlog-based) and user-facing bulk import (any user, immediate), absorbs story 19 | Backend / AI | Needs Definition, draft tasks exist, the two-tier pipeline shape (fast tier, patient tier) and the rate-limit priority-queue design are both decided and validated (`DECISIONS.md`); blocked on story 23 (schema) and the metadata-sourcing spike's real implementation landing |
| 41 | Submission content safety: reject non-music and compilation submissions, defend LLM-facing steps against prompt injection in untrusted YouTube text | Backend / AI | Needs Definition, draft tasks exist, applies to every submission path, not just story 40, blocked on the same metadata-sourcing spike implementation |
| 42 | Explicit database split: the domain boundary between the core transactional Postgres+pgvector database and story 33's separate analytics/event store | Infra | Needs Definition, draft tasks exist |
| 43 | Metadata minimization: curb raw pipeline/metadata storage growth everywhere it's persisted, not just at story 23/40's specific fields | Backend / AI | Needs Definition, draft tasks exist |
| 44 | Test user infrastructure (dev only): a dedicated `TEST` role and reusable seeded credentials for automated agents, disabled outright in Production | Quality / Backend | Needs Definition, draft tasks exist |

## Open questions

- Future idea, not yet a story: once story 34's abuse-visibility events (rate-limit-exceeded, report-submitted, and now story 41's flagged-injection-attempt) accumulate real data, a user-moderation feature (warnings, bans) based on a pattern of that behavior over time. Deliberately not scoped now, noted so it isn't lost.
- Metadata source API usage: resolved. Source set is MusicBrainz, Discogs, Wikidata, and Wikipedia; Genius, Last.fm, and iTunes were reviewed and dropped rather than fixed (see `docs/DECISIONS.md`).
- Story 30's personalized difficulty layer needs real interaction data to beat its own aggregate baseline; the aggregate baseline itself works as soon as story 10 ships and rounds start accumulating `Guess` rows, without needing months of data the way the collaborative-filtering enhancement does, but neither exists until story 10's `Guess` entity does. Likely won't clearly beat the baseline at the 100-200 user target scale until real usage accumulates over months of casual play.
- Deployment target (story 7) and database target (story 8) are both undecided pending more research, deliberately deferred until the app is close to feature-complete locally. Leaving Fly.io is confirmed; Azure Container Apps as the replacement is not. Migrating off Supabase at all is undecided, let alone a target; Azure Database for PostgreSQL and Neon have both come up but neither is chosen.
- New, surfaced reading YouTube's Developer Policies directly for story 35: raw YouTube API Data (a video's title, description, channel name, view counts) that isn't user-authorized data must be deleted or refreshed within 30 calendar days of storage; it can't be kept indefinitely as-is. If `metadataRaw` ends up persisting these specific fields long-term, story 23's schema design needs to either refresh or drop them on that cycle. This doesn't affect story 35 itself (its published triples are sourced from MusicBrainz/Discogs/Wikidata, not from YouTube API Data), but it's a real, previously unflagged constraint on how `metadataRaw` can be shaped.

## Resolved questions

- Where is the current Postgres instance hosted? Supabase, confirmed.
- Compliance and production-readiness: privacy policy, terms of service, GDPR compliance, and observability were a stated goal in `docs/VISION.md` with no story attached. Now stories 37 and 38.
- Story 10's session-state persistence and win-condition scaling were open questions. Both resolved: ephemeral Postgres rows, and an admin-configured card count bounded by player count. The group/game-session split that resolved them is documented in `ARCHITECTURE.md` and `GAME_DESIGN.md`, and logged in `DECISIONS.md`. Now story 39.
- Flutter app: keep. In the meantime it follows the same real-link-out rule as the rest of the product, opening the real YouTube app instead of an embedded or hidden player, no exceptions.
- The community verification/report system: report resolution stays fully manual, an admin decides every case, nothing auto-changes `verificationStatus`. The admin review queue ranks by a five-tier priority order (converging reports first, then non-converging reports, then confirmed-but-unreported cards, then unconfirmed cards, `VERIFIED` cards with no report never appear at all), not submission time. See `DECISIONS.md`.
- Story 23: release year is one mutable field plus `verificationStatus`, not a separate `submittedYear`/`verifiedYear` pair. Once verified, the field doesn't change except through the same review process that verified it in the first place.
- Story 30: medium-difficulty group scoring uses the median of the group's individual predicted scores, a middle ground between easy's worst-case protection (lowest individual score) and hard's plain average, with no extra weighting factor to tune.
- Story 32 (periodic LLM catalog audit) is dropped, so its relationship to story 18 no longer applies: every song already flows through story 18's verification pipeline on submission, and again through the patient tier after the fast tier answers, making a separate scheduled audit pass redundant.
- Story 35: read YouTube's actual Developer Policies directly rather than a summary. Section III.E.4.h prohibits substituting or deriving new metrics from YouTube's own numeric/engagement data (its own example is about view/like counts), it does not restrict publishing independently-sourced facts (artist, title, release year, all CC0 from MusicBrainz/Discogs/Wikidata) merely because a YouTube video's title or channel name was used as a lookup key to find them. Story 35 itself is clear to ship as designed; see the new open item above for a related but separate constraint this same research surfaced.
- Story 23: a song's artists are an ordered list, each tagged `MAIN` or `FEATURED` (more than one `MAIN` artist is allowed, a joint credit like "Queen & David Bowie" has two, neither featured). The role tag is display-only, guessing and scoring treat every artist on the list identically. A remix, cover, or mashup clause is the only thing that survives the AI microservice's title-cleaning pass, a `(feat. X)` clause instead gets stripped and extracted into the artist list; each remix/cover/mashup becomes its own separate `Song`, not a variant of the original.
- Story 30: a country/language filter dimension is dropped. A difficulty-generated set defaults to international scope instead, using Wikidata's sitelinks count (already validated during the metadata-sourcing spike) as the signal. Playing from an existing playlist now also includes playlists someone has published publicly, a new capability, alongside ones a player owns or is a member of.
- Story 10: every player except the DJ, the active player included, can guess a round's title/artist. Only the active player's guess affects tokens; every guess, active or not, feeds two session-long leaderboards ("Most Artists Guessed," counting every correct individual artist name regardless of a song's total artist count, and "Most Titles Guessed"), shown alongside the main card-count ranking at session end.
- Typo tolerance for in-round artist/title guesses (see `GAME_DESIGN.md`'s Earning tokens section): normalize both the guess and the canonical answer (lowercase, strip punctuation, strip diacritics, collapse whitespace) and compare with Damerau-Levenshtein edit distance, a flat budget of 1 regardless of title length. See `DECISIONS.md`.
- Database split: the project has one database split worth defining explicitly, the boundary between the core transactional Postgres+pgvector instance and story 33's separate analytics/event store. Now story 42. No second transactional database (e.g. one per service) is planned.
- Story 37's legal/static pages: exactly two, Privacy Policy and Terms of Service. A cookie-consent page stays gated on story 34 shipping, no other legal or static page is planned.
