# DECISIONS.md: Decision Log

Append-only. Never edit or delete past entries. New decisions go at the bottom.

---

## 2026-06 | DJ model for YouTube compliance

Decision: use a DJ role as the architecture for YouTube compliance in online multiplayer. The DJ's device runs playback; other players receive only audio and see only the game UI.

Why: YouTube's IFrame API terms require an embedded player to be visible, unmodified, and not overlaid. In a multiplayer game where everyone has a player, this can't be enforced without breaking gameplay. A DJ model sidesteps this: only one device has any relationship to the player, and that player doesn't guess that round anyway.

Note, 2026-07: the mechanism described here was superseded, see the 2026-07 entry below. The DJ-per-round principle and zero embed on non-DJ devices still stand.

---

## 2026-06 | No unofficial APIs

Decision: use only official APIs for external services. No unofficial or reverse-engineered clients.

Why: unofficial libraries aren't authorized by the underlying service and carry meaningfully higher terms-of-service risk than the official API for the same functionality.

---

## 2026-06 | Multi-source metadata pipeline

Decision: verify song metadata using multiple external sources (YouTube, MusicBrainz, Wikipedia, Genius, Discogs), synthesized by an LLM, rather than relying on a single AI model's training data.

Why: LLM training data is unreliable for niche and underground music. Fetching structured data from specialist databases first, then using the LLM only for synthesis and reconciliation, is more accurate. MusicBrainz and Discogs cover underground and electronic music that mainstream sources miss.

---

## 2026-06 | Separate submittedYear vs verifiedYear

Decision: store `submittedYear` and `verifiedYear` as separate fields, not a single `releaseYear`.

Why: without this distinction, there's no way to tell whether a year was changed after review, which the verification workflow depends on.

Note, 2026-09: reopened as an undecided question rather than settled fact, see the 2026-09 "Reopen release-year field shape" entry below. The audit-trail reasoning here still stands as one option under consideration, it's no longer the final answer.

---

## 2026-06 | Persist metadata pipeline output

Decision: store the full pipeline output as a JSON column, and persist the confidence value.

Why: without this, provenance is lost once the frontend consumes the response. Auditing and re-verification become impossible.

---

## 2026-06 | pgvector for deduplication before the pipeline

Decision: check pgvector similarity against existing verified songs before running the full pipeline. Reuse on a high-confidence match, skipping the LLM call.

Why: reduces cost and latency for songs that are already in the database.

---

## 2026-06 | Flutter deprioritized

Decision: the Flutter app is deprioritized in favor of the web-based game.

Why: playback now happens through a browser tab or the real YouTube app, which works on mobile browsers too, removing the original reason for a dedicated native playback app.

---

## 2026-07 | DJ model refined: link out to the real YouTube instead of embedding

Decision: stop embedding the YouTube player entirely. The DJ is sent to the real YouTube page, a new browser tab, for remote sessions, or the real YouTube app for in-person sessions. Physical cards encode the YouTube URL directly in the QR code.

Why: an earlier prototype hid the iframe and blocked ads, a direct violation of YouTube's developer policies. This exposure doesn't shrink just because the audience stays small or grows without active marketing. It depends on what the software does, not on audience size. The fix is architectural: a plain outbound link to YouTube's own page or app is not an embedded player at all, so the rules governing embedded players don't apply. This holds regardless of user count.

Trade-off accepted: no programmatic access to the playback state on a page we don't control, so round reveal is a manual trigger instead of automatic. Ads always play, unmodified.

Supersedes: the 2026-06 DJ model entry, mechanism only. The underlying DJ-per-round principle is unchanged.

---

## 2026-07 | Split the backend: Spring Boot core, Python/FastAPI AI microservice

Decision: split the backend into two services. Spring Boot keeps auth, CRUD, game session, and WebSocket. A Python/FastAPI service takes over the metadata pipeline, LLM synthesis, and embeddings. Spring AI is removed from the Java side.

Why: Python has a stronger, faster-moving ecosystem for LLM and embeddings work than the Java equivalent. The metadata pipeline was already a distinct component; this gives it its own process and dependency footprint, separate from the core app's CRUD and auth concerns.

Why not a full rewrite to Python instead: the existing Spring Boot auth and CRUD layer already works. Rewriting it would discard working infrastructure for no functional gain.

Implementation notes: the core service calls the AI microservice over an internal endpoint, not publicly exposed. The core service owns all migrations; the AI microservice never alters schema. Both services run in the same Azure Container Apps environment.

---

## 2026-07 | Move backend hosting from Fly.io to Azure Container Apps

Decision: migrate both backend services to Azure Container Apps. Database moves to Azure Database for PostgreSQL Flexible Server, pgvector enabled. Frontend stays on Vercel.

Why: Fly.io kept compute running regardless of actual traffic, which doesn't fit a usage pattern that's bursty and mostly idle. Azure Container Apps' Consumption plan scales to zero and charges nothing while idle. AWS Fargate and App Runner both maintain a non-zero baseline cost even at low traffic, reproducing the same problem this migration is meant to fix.

Open item: current Postgres host needs confirming before data migration. See PROJECT_STATE.md.

Note, 2026-09: reversed, see the 2026-09 "Deployment platform reopened" entry below. Azure was chosen without comparing alternatives or setting a cost ceiling first; both the hosting platform and the database platform are undecided again.

---

## 2026-08 | Community verification through reports, not thumbs up or down

Decision: replace the earlier thumbs-up/thumbs-down concept with a report flow: a report button, a free-text message, a field for the year the reporter believes is correct, and separate fields for one or more sources.

Why: a simple up or down vote trusts the crowd without capturing any actual information behind the disagreement. Separate structured fields, year and sources, give an admin or a future automated process something concrete to act on, instead of an unexplained vote count.

Open item: what promotes a reported or newly submitted song to fully verified is not yet decided.

---

## 2026-08 | Voice chat: mesh peer-to-peer, no media server, Cloudflare TURN as fallback

Decision: voice chat between players in a session uses a mesh WebRTC topology, signaling over the existing WebSocket layer, capped at 8 participants per session. Cloudflare's pay-as-you-go TURN service is used only as a fallback for connections that can't be established directly. Video is out of scope for now.

Why: a full media server removes the participant limit and enables group video, but requires either self-hosting a real-time media server or paying a per-participant-minute provider, both ruled out. At realistic group sizes for this game, mesh audio-only is well within what peer-to-peer can handle reliably; video mesh is not, since video bitrate is far higher than audio and mesh bandwidth scales with each additional participant. A relay, TURN, is still required regardless of group size, since a meaningful share of real-world network connections can't establish a direct path due to NAT type. At this usage scale, the actual relay cost is negligible, well under the cost of any managed media server or self-hosted alternative.

Why not self-hosting a TURN server instead: self-hosting only becomes cheaper than a pay-as-you-go relay at usage volumes far beyond this project's expected scale. Below that threshold, a managed relay is both cheaper and less operational work.

---

## 2026-08 | Round mechanic: active player has priority on valid placements

Decision: the player whose turn it is places their guess and locks it in first. Other players holding a token may then bet on their own guess, first come, first served. If the active player's placement is correct, including when it shares a release year with an existing card on the timeline, they keep the card regardless of any bet, and the bet is lost.

Why: this matches the official Hitster ruling for the equivalent situation. A same-year tie doesn't override a correct placement, and a bettor only wins the card if the active player was actually wrong.

---

## 2026-08 | Sessions are ephemeral, not persistent groups

Decision: a game session is created with an invite link and exists only while it's being played, the same way a Gartic Phone round works. Everyone who joins is a full player. There's no spectating and no joining a session already in progress. Voice and text chat are scoped to the session's lifetime. When the session ends, nothing persists except a downloadable results export.

Why: an earlier idea involved persistent groups, similar to Discord servers, that would stick around between games. Given the actual usage pattern, friends starting a game together and playing it through, that persistence adds storage and complexity without a clear benefit. The simpler model also removes an entire category of open questions about group membership, moderation, and long-term data retention.

Note, 2026-08: superseded by the "Group and game session are separate entities" entry below. Persistent groups came back once chat/voice persistence and replay without a new invite link turned out to matter more than the complexity this entry was trying to avoid.

---

## 2026-08 | Project state as a story backlog, with a task gate

Decision: `PROJECT_STATE.md` holds a table of stories with stable IDs and a status: Implemented, Ready, In Progress, or Needs Definition. `TASKS.md` holds concrete, checkable tasks. A story can have draft tasks written against it while still marked Needs Definition; that alone doesn't unlock feature work. A story becomes Ready only once its tasks have been checked against the real codebase and confirmed accurate. Feature work requires a Ready story with tasks; fix, chore, and docs work, including bugs listed directly in TASKS.md, doesn't need a story at all. Fully implemented stories move out of both files into `ARCHIVE.md`.

Why: without a defined-before-built gate, an AI coding agent will happily start implementing a half-formed idea. Separating "a task exists" from "a task is confirmed against real code" matters specifically because task breakdowns written during planning, without access to the actual repository, can turn out to be wrong once the real code is visible. Archiving completed stories keeps the active files from growing indefinitely.

---

## 2026-08 | Branching: main, dev, legacy

Decision: three persistent branches. `legacy` is frozen at the current implementation and stays live in production while the new architecture is built. `dev` is the active integration branch, worked on exclusively through pull requests from `feature/*`, `fix/*`, `chore/*`, and `docs/*` branches. `main` is not touched until the new architecture is ready to replace what `legacy` is currently serving.

Why: this keeps the current, working deployment available to actual players throughout the rework, rather than breaking it mid-refactor.

---

## 2026-08 | Core-to-AI-microservice auth: shared secret header

Decision: the core service authenticates to the AI microservice's internal endpoint with a shared secret header, `X-Internal-Api-Key`, checked against `INTERNAL_SERVICE_API_KEY` on both sides.

Why: the original split decision left the mechanism open, shared secret header or network-level restriction. A header works identically in local dev and in Azure Container Apps, with no dependency on Container Apps-specific network configuration, and needs no extra infrastructure to set up.

---

## 2026-08 | Pause MusicBrainz, Wikipedia, and Genius pending an API usage review

Decision: only the YouTube Data API source makes live calls in the metadata pipeline right now. MusicBrainz, Wikipedia, and Genius all return no result until a deliberate review confirms each one's API usage is official, legal, and ethical.

Why: porting the pipeline to the AI microservice (story 6) carried these three integrations over unchanged from the pre-split code, without that review having happened yet. Reviewing all three together, deliberately, is preferred over reviewing them one at a time.

Open item: what each of the three needs, and what the resulting integration looks like for each, is planned as the next thing to work through after story 6.

---

## 2026-08 | Metadata source set: MusicBrainz, Discogs, Wikidata; Genius, Last.fm, and live Wikipedia search dropped

Decision: the metadata pipeline's structured sources are MusicBrainz, Discogs, and Wikidata. Genius, Last.fm, and Wikipedia's live search API are dropped, not paused.

Why: reading each source's own current license and terms directly, not a summary of them, MusicBrainz's core fields (title, artist credit, release date) and Discogs's monthly data dumps are both CC0, public domain. Wikidata's entire structured dataset is CC0 and explicitly cleared for commercial or personal reuse, and it supersedes Wikipedia's live search entirely: same underlying project, cleaner license, structured data instead of a fact regex-matched out of article prose. Genius's terms restrict commercial use and broadly prohibit automated data gathering without a clear carve-out for their own API. Last.fm's license is non-commercial by default and explicitly terminable at their discretion. Both were only ever secondary sources for a release date; the license friction on both makes dropping them the cleaner call over trying to fix them.

Note: MusicBrainz's release-group `first-release-date` field, not a specific release's date, is the correct field for an original release year; a plain recording or release search can return several results tied at the same confidence score with different dates (a genuine reissue vs. the original), verified against live queries during this review. Sources reduce how often the LLM has to guess a year, they don't remove the LLM's role reconciling disagreements between sources.

---

## 2026-08 | Product standard: professional-grade, not just working

Decision: the project holds itself to the same standard for the product as for its external dependencies, official and compliant, not just functional. This includes a real privacy policy and terms of service, GDPR compliance, and production-grade observability (error tracking, monitoring).

Why: prompted by finding an existing web-based Hitster clone with no visible terms of service, privacy policy, or GDPR compliance. Not a story yet, a standard the project is held to as stories get defined.

---

## 2026-08 | Database split: not for capacity, only for workload shape

Decision: splitting the database across multiple free-tier instances purely to gain storage capacity is rejected. A real split, separating transactional data (users, songs, ratings, OLTP-shaped) from usage-analytics event data (games played, session length, append-heavy and time-series-shaped), stays on the table for a different reason, see story 33 in `PROJECT_STATE.md`.

Why: capacity was the only reason raised for a multi-instance split. Song metadata plus user, playlist, and ratings data is small enough to stay well under a single free-tier instance's limits even at the 100-200 user target scale, checked directly rather than assumed.

---

## 2026-08 | Group and game session are separate entities

Decision: what was one ephemeral "session" is now two entities. A group is the persistent lobby, invite-link membership, an admin role, live-synced game settings, chat, and voice, that a game session lives inside. The game session is the round-by-round gameplay itself, created only when the group's admin starts one, and still fully ephemeral, purged when it ends except for a downloadable results export. A group can run more than one game session over its lifetime.

Why: a single-tier model (Gartic Phone's approach, in-memory, gone when the round ends) doesn't fit a product with persistent chat and voice, replay without recreating an invite link, or an admin able to configure settings before anyone commits to starting. Splitting the two lets chat and voice exist before, between, and after games without tying their lifetime to one round of play.

Note: the group's lifecycle runs on fixed timers, not activity tracking, deliberately. An activity-based timer (reset by any interaction) was considered and rejected: it opens a loophole where starting and immediately abandoning a game session resets the clock indefinitely. Fixed timers: 30 minutes from group creation to the admin starting a session, 30 minutes from a session ending to the admin starting another, 10 minutes with zero connected players before an in-progress session is torn down as abandoned. Reconnecting to a still-active group happens through account state on app load, not by reusing the invite link, the link is for joining a group for the first time only.

Note: the game session's win condition is an admin-configured setting, not an automatic formula: minimum 5 cards always, maximum 20 for a 2-3 player group, maximum 15 for a 4-8 player group.

See story 10 (game session), story 39 (group), and stories 9, 12, and 13 in `PROJECT_STATE.md`, and `ARCHITECTURE.md` and `GAME_DESIGN.md` for the full shape.

---

## 2026-09 | Open source, non-commercial, no monetization or third-party tracking

Decision: the project is open source with no monetization plan, ever. No ads, no third-party trackers, no selling user data. First-party analytics (stories 33, 34) stay in scope, they're for improving the product for the people playing it, not for anyone else's benefit.

Why: built for friends and family, not to compete for users or ad revenue. Reaching the ~100-200 user scale of that group is a complete win, not a floor to grow past.

---

## 2026-09 | Whole-deployment cost ceiling: target $0, tolerate up to ~$20/month total

Decision: cost stays low deliberately. The entire deployment, every service combined (backend, AI microservice, frontend, database, observability, everything), targets $0/month and tolerates up to roughly $20/month total if there's a real reason, such as a genuine skills investment, not convenience. That ceiling applies to the whole stack, not per service. If real usage outgrows what fits the budget, the response is gameplay balancing, capping concurrent sessions, queuing players, or similar, not paying for more infrastructure.

Why: the project is non-commercial and small-scale by design (see the open-source decision above); infrastructure spend should match that, not creep upward service by service across a multi-service architecture.

---

## 2026-09 | Deployment platform reopened

Decision: the earlier choice of Azure Container Apps and Azure Database for PostgreSQL is reversed. Deployment platform, both compute and database, is undecided again, deliberately deferred until the app is close to feature-complete locally rather than decided now. Leaving Fly.io for backend hosting stays decided; where it moves to doesn't. Whether to migrate off Supabase at all, and to what, is also undecided; Azure Database for PostgreSQL and Neon have both come up as candidates, neither chosen.

Why: the original Azure decision was made without comparing real alternatives or the whole-deployment cost ceiling now in place (see above). Revisiting once, closer to feature-complete, avoids re-deciding hosting multiple times as the app's actual resource needs become clearer.

---

## 2026-09 | Reopen release-year field shape, defer artist modeling entirely

Decision: whether release year is `submittedYear` plus `verifiedYear` (preserves the original submission after a correction) or one mutable field plus `verificationStatus` (simpler) is reopened as undecided. Separately, how a song with multiple or featured artists is stored and guessed, today's schema assumes a single `artist` string, is new, undecided scope: whether storage is an array, whether every featured artist must be guessed correctly, and what the guess-box UI looks like for more than one artist are all open.

Why: the year-field question was settled without weighing the two options against each other explicitly. The artist question was never considered at all in the original schema design, surfaced only once a real "feat." credit was worked through concretely.

---

## 2026-09 | Game session mechanics: reconnect, leave, token earning, betting timing

Decision: a disconnected player keeps their timeline, tokens, and turn order untouched, marked only `isConnected: false`. An explicit leave, or a disconnected active player's turn going unanswered for 90 seconds, marks them `Left`: excluded from future turns and DJ rotation, but their existing timeline cards still count toward the final results. Guessing a song's artist and title is a separate action from timeline placement, available to the active player for the whole turn; a fully correct guess earns a token. After timeline placement locks in (with a sound effect), a 3-5 second countdown leads into a 15-second betting window, skipped entirely if no player holds a token, endable early with a skip-betting action, and concurrency-safe so only the first bet is accepted and a losing attempt doesn't cost a token.

Why: mirrors the same disconnect-versus-leave distinction already decided for groups, applied down to the player level inside a session, rather than leaving session-level reconnect behavior unspecified. The betting sequence's timing gives players who already heard the song enough time to act without dead air.

---

## 2026-09 | Group additions: join code, per-group identity, voluntary admin transfer

Decision: a group can be joined by a 4-letter code as well as the existing invite link. On joining, a member is prompted for a per-group display name and avatar, defaulting to their account's own but editable and private to that group, other members never see the real account profile. The admin can voluntarily promote another member to admin at any time, independent of the existing auto-promote-on-leave path.

Why: a join code is easier to share verbally or in person than a link. Per-group identity lets someone play under a different name or avatar with people they don't want to share their main profile with, coworkers versus close friends, for example.

---

## 2026-09 | Every story's tasks carry their own tests

Decision: every task breakdown in `TASKS.md` must include explicit test tasks alongside the feature tasks, not deferred to a separate test-coverage story. Story 22 stays scoped to backfilling tests for code that predates this rule.

Why: stories get implemented autonomously from `TASKS.md`; a story needs a real completion criterion beyond "the feature code works."

---

## 2026-09 | License: MIT

Decision: the project is licensed MIT, a permissive license anyone can fork, redistribute, and self-host, including a competing hosted instance, with no restriction.

Why: AGPLv3 and the Business Source License were both considered first, on the assumption that a hosted fork by someone else was worth guarding against. Neither actually protects anything real here: there's no revenue or user base to lose to a competing fork, since the project has no monetization plan and isn't being marketed for growth (see the open-source/non-commercial decision above). MIT is also the stronger choice for the project's actual purpose, a portfolio piece: it's the license every engineer and recruiter recognizes instantly with zero friction to clone and evaluate, where a non-standard restrictive license would need explaining and could read as mismatched against a project that describes itself as small-scale and non-commercial.

---

## 2026-09 | Drop stories 29 and 31, no viable audio-feature source

Decision: story 29 (content-based recommender using audio features like tempo, energy, valence) and story 31 (similar-songs feature) are both dropped, not left open.

Why: researched directly rather than assumed. AcousticBrainz, the obvious free source, shut down its live API and submission pipeline in February 2022; only a frozen 2022 dataset remains, with coverage skewed toward mainstream music already analyzed before the shutdown, the opposite of the niche and underground coverage this project prioritizes. Self-hosting Essentia (the toolkit AcousticBrainz itself used) works on any song but needs the actual audio file, and the only way to get that for a YouTube-sourced song is unofficial downloading, which violates the project's non-negotiable official-APIs-only rule and the DJ-link-out architecture built specifically to avoid touching YouTube's media stream. Paid catalog APIs are real ongoing cost for a nice-to-have feature and still don't solve the niche-track coverage gap. Story 31's only version worth building, audio-based similarity, depends on the same missing data; its fallback (text-embedding similarity over artist/title) was explicitly rejected as not a real substitute, it mostly catches same-artist or similarly-worded matches, not "sounds like."

---

## 2026-09 | Per-user game history, reopened and reversed

Decision: a per-user game history page is added (story 34), reading compact per-game summaries (group, players, win/loss, cards won, final score) from the separate analytics store (story 33). The transactional `GameSession`/`Round`/`Guess` rows still purge exactly as story 10 specifies; the history page reads only from the analytics store, never from session state that no longer exists.

Why: an earlier planning session explicitly decided against a persistent per-user game-history tab, staying purge-only to match the ephemeral session framing, but that decision was only ever said in conversation, never written into this log or any doc. Revisited once story 33's separate analytics store made it effectively free: the store already needs to exist for usage stats, and a compact game summary costs nothing extra to retain there, without touching the core session model's ephemerality at all.
