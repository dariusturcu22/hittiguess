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

## 2026-09 | iTunes Search API reviewed and rejected as a metadata source

Decision: the iTunes Search API is not added to the metadata pipeline's source set.

Why: its terms of use are scoped to Apple's Affiliate Program, promotional use only. Content must sit proximate to a store badge or purchase link and "is not used for independent entertainment value apart from its promotional purpose," with required "provided courtesy of iTunes" attribution on any preview use. None of that fits a backend metadata source with no iTunes purchase links or store badges anywhere in the product, the same mismatch that got Genius and Last.fm dropped in the entry above. Its rate limit (about 20 calls/minute) is also far below MusicBrainz/Discogs/Wikidata's. Rejected on terms alone, without live testing, matching how Genius and Last.fm were handled.

---

## 2026-09 | Metadata pipeline call/reconcile shape: MusicBrainz, Discogs, Wikidata

Decision: for each of the three structured sources, query both the submitted track and its parent album, then take the earliest valid release year across every candidate either query returns, not the first or top-scored candidate alone. Compare and select by extracted year, never by raw date string.

Why: validated against 42 real songs (a hand-picked mainstream/mid-tier/niche/Romanian set, and a real 34-song YouTube playlist), a track-only query missed the true original date repeatedly, always in the same direction: it found a reissue, remix, or standalone-single release instead of the earliest one. A niche vaporwave track's own MusicBrainz release-group gave a 2019 reissue date instead of 2011; a real playlist song ("Hey Mama") has its own 2015 single release on Discogs but is also track 10 on a 2014 album, and trusting whichever master a search result listed first picked the later one. Comparing by extracted year rather than raw date string matters too: Wikidata zero-pads an unknown month/day to "-00-00", which sorts as numerically earlier than a fully-precise same-year date in naive string comparison despite not being an earlier real day, and the schema only stores `releaseYear` as a plain int regardless.

Per-source specifics, each confirmed against live data, not assumed:
- MusicBrainz: query the release-group endpoint, never the plain recording/release endpoints (settled already in this file's 2026-08 entry). Scan every release-group tied at the top score, not just the first, prefer the type matching query intent (Single for a track-level query, Album for an album-level one, confirmed necessary: an album query for "Thriller" collided with the unrelated same-titled "Thriller" single without this), and take the earliest first-release-date among whichever are dated.
- Discogs: a release search returns individual pressings and reissues; follow to the master resource for the canonical year, but a track can belong to more than one distinct master, its own single release and the album it also appears on, so check every distinct master found among the results, not just the first, and take the earliest. Treat a master year of `0` as unknown, Discogs uses that instead of null.
- Wikidata: search the title alone, a combined "artist title" query returns nothing. Use a wide result window (20, not the platform default of a handful), a common title can bury the real song several results down. Prefer whichever result's description mentions the artist; if none do, fall back only to a result whose description sounds like an actual music release, never to an unrelated top-ranked result, a narrow-window, no-fallback-check version of this search resolved "Dark Horse" to a 2008 Nickelback album instead of Katy Perry's 2013 song. A song's own `P361` ("part of") claim, when present, resolves its parent album directly, more reliable than guessing the album's title and searching for it separately.
- Title preprocessing: strip a `(feat. X)`/`(ft. X)` clause before querying MusicBrainz or Wikidata, both return zero matches otherwise; Discogs' search tolerates the clause as-is. The featured artist names aren't discarded, they're extracted into a separate list, feeding story 23's open question on multi-artist storage.

Note: this settles how the three structured sources are called and reconciled against each other. It does not settle two adjacent, still-open questions: how their combined result reconciles against the LLM synthesis step when sources genuinely disagree past a one-year single/album gap (the existing confidence-guideline logic in `prompt.py` already exists for this, unchanged by this decision), and how the pipeline gets a usable artist name at all when a YouTube video's title and channel don't carry one, confirmed on real anime openings across three different channels, where only the description states the real artist, in a different format every time. That gap is decided separately (see `TASKS.md`'s spike entry): a structured-output LLM call, not per-channel regex, extracts title/artist/featured-artists from the raw title, channel, and description when the channel doesn't look like a real artist.

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

---

## 2026-09 | Story 30 redefined: difficulty-tuned session generation, absorbs story 21

Decision: story 30 changes from a generic "collaborative filtering recommendations" placeholder into a concrete feature: generating a game session's card set on the spot, scored to a chosen difficulty (easy/medium/hard) for the group's actual players, instead of only playing from an admin-picked playlist. Absorbs story 21's card-assembly mechanics rather than keeping two stories doing adjacent things; difficulty becomes one more selection criterion alongside theme. Built in two tiers: a per-song aggregate correct-guess-rate score that works immediately and covers first-time players, and a personalized collaborative-filtering layer on top that only ships once it demonstrably beats that aggregate baseline. Group-level scoring for easy mode uses the group's lowest individual predicted score, not an average, so the least experienced player is protected rather than averaged away; hard mode uses a plain average, since it's opt-in.

Why: the original framing, "recommend songs a user might like," doesn't fit how the game actually works, players don't browse and pick songs individually. Reframed around what collaborative filtering can genuinely predict here: whether a given player would get a given song right, which directly powers something the game already wants, curated playlists tuned to a group's skill, rather than an abstract recommendation feature with no clear place to surface it. The personalized-vs-baseline comparison also gives the retraining pipeline a real signal to act on (the model degrading relative to the simple baseline) instead of a fixed schedule with no actual quality check behind it.

---

## 2026-09 | Story 21 folded into story 30, not just cross-referenced

Decision: story 21's tasks (theme-based catalog search, genre/popularity fields, metadata-pipeline gap-filling, the review UI) move into story 30's task list directly. Story 21's row in `PROJECT_STATE.md` points to story 30 instead of carrying its own tasks.

Why: both stories generate a game session's card set, one from a theme, one from a difficulty tier; keeping them as two separate stories with two separate generation endpoints would mean building the same underlying mechanism twice instead of once with two combinable selection criteria.

---

## 2026-09 | Metadata pipeline final shape: two-tier lock-or-LLM, Wikipedia added as a fourth source

Decision: the metadata pipeline for resolving a song's release year has two tiers, both now final. Patient tier (the admin backlog drain, and full verification of anything the fast tier touches): query MusicBrainz, Discogs, and Wikidata always; lock the year immediately with no LLM call at all when all three agree exactly; when they don't, fetch and extract Wikipedia (a dedicated reading-comprehension LLM call) and reconcile all four sources' candidates with a second LLM call. Fast tier (on-the-spot, answers immediately): dispatch each song to exactly one of MusicBrainz or Wikipedia, never both, whichever is free grabs the next song under dynamic work-stealing dispatch rather than a fixed split; every fast-tier answer is provisional and gets re-queued through the patient tier afterward. Wikipedia is added as a fourth structured/LLM-assisted source, alongside MusicBrainz, Discogs, and Wikidata. Model choice: gpt-5-nano reconciles among structured candidates, DeepSeek-V4-Flash reads Wikipedia's prose, two different models for two different jobs, not one model for everything.

Why: validated against a real 70-song test set spanning mainstream, adversarial, and niche/regional cases, not guessed at. The patient tier scored 99% (69/70), with 53% of songs locking without any LLM call at all. The fast tier scored 90% (63/70). Wikipedia earned a permanent seat in the pipeline specifically because its errors are largely uncorrelated with the other three sources': it solved cases (a single-vs-album date trap, a case where two independent structured sources agreed with each other on the same wrong year) that all three structured sources missed together, at the cost of a few new mistakes of its own that the other sources don't make. The two-model split exists because a real test found gpt-5-nano notably worse at the reading-comprehension extraction task specifically, despite being the stronger reconciler of the two, one model wasn't uniformly best at both jobs.

Open item: this settles the pipeline shape itself, not expected to be revisited again barring a real problem found during implementation. What a genuine no-answer (no source has anything at all) does downstream, and whether any of this gets built into the real AI microservice versus staying validated-but-unbuilt in the spike, are both still open, see `TASKS.md`.

---

## 2026-09 | Rate-limit contention: on-the-spot traffic always preempts the admin backlog

Decision: the admin catalog-seeding backlog drain and on-the-spot user requests (including playlist imports) share the same external rate-limit budgets, MusicBrainz, Discogs, Wikidata, and Wikipedia all come from the same outbound IP. On-the-spot traffic is always high priority; the backlog drain pauses while any on-the-spot traffic is active and resumes once it's clear, rather than the two paths contending for the same budget in real time. Every song the fast tier answers provisionally gets re-added to the admin backlog afterward, to be resolved properly through the patient pipeline at low priority like everything else already in that queue.

Why: this was surfaced, not resolved, when story 40 was first drafted; "on-the-spot always wins on priority" was already decided for queue ordering, but nothing defined what that meant for the two paths' shared external rate-limit budgets specifically. A pause-and-resume design avoids building real-time token-bucket arbitration between two internal consumers of the same external ceiling, and fits the two-tier pipeline's own shape: the backlog drain has no latency pressure of its own, so yielding to on-the-spot traffic costs it nothing but time.

---

## 2026-09 | Verification schema: one release-year field, four-tier verificationStatus, reportable at every tier

Decision: release year is one mutable field plus `verificationStatus`, not a separate `submittedYear`/`verifiedYear` pair. `verificationStatus` has four tiers: `UNVERIFIED` (default, not yet processed), `VERIFIED` (story 18's lock, all three structured sources agreed, immutable except through the report/re-verification process), `NEEDS_REVIEW` (an LLM-reconciled year, not locked), and `MANUAL_ENTRY` (a human-entered year for a song no source, including Wikipedia, had any data on at all, the least-trusted tier). Every card, at every tier including `VERIFIED`, can be reported; a report against an already-`VERIFIED` card sinks to the bottom of the admin review queue rather than being blocked outright.

Why: a single field plus a status is simpler than two year fields and still gives a clear audit trail, whether a year is locked, LLM-guessed, or human-entered is exactly the information that mattered about "was this corrected," not preserving a separate original-submission value alongside it. `MANUAL_ENTRY` exists as its own tier, below `NEEDS_REVIEW`, because a human guess with zero source corroboration is a genuinely different confidence level than an LLM reconciling real, if disagreeing, source data. Allowing reports on locked cards too, rather than exempting them, keeps one code path for every card; the admin-queue priority ordering (lowest confidence first, already decided) does the real work of not wasting review time on a report against a card three independent official sources already agreed on.

Open item: the report's own data shape (message, suggested year, one or more sources) was already decided in the 2026-08 "Community verification through reports" entry above. How a submitted report actually gets resolved, whether that stays fully manual or has any automated assist, and how the community thumbs-up signal on `NEEDS_REVIEW`/`MANUAL_ENTRY` cards works mechanically (a vote threshold, what it does to `verificationStatus`, if anything, on its own) are still under discussion, not settled here.

---

## 2026-09 | Story 32 dropped: redundant with the verification pipeline

Decision: story 32 (a scheduled, periodic LLM-as-judge pass over the whole catalog flagging likely duplicate or mislabeled songs) is dropped, not deferred.

Why: every song already gets verified on submission through story 18's pipeline, and a fast-tier answer gets re-verified through the patient tier afterward. A separate scheduled audit pass over the whole catalog duplicates that coverage. A manual, admin-triggered version, run on demand rather than on a schedule, isn't ruled out, but isn't a defined feature.

---

## 2026-09 | Flutter kept, held to the same DJ-model rule as the web app

Decision: the Flutter app is kept, not dropped or repurposed. In the meantime, it follows the same non-negotiable rule as the rest of the product: the DJ is never shown an embedded or hidden YouTube player, playback happens on the real YouTube app.

Why: settles the "keep, repurpose, or drop" question that had been sitting open since Flutter was deprioritized behind the web-based game. Compliance with the DJ-model rule doesn't wait for a future repurposing decision, it applies now, the same way it already applies to the web app.

---

## 2026-09 | Story 30 medium difficulty: median of individual scores

Decision: medium-difficulty group scoring (story 30) uses the median of the group's individual predicted scores.

Why: easy already uses the group's lowest individual score (worst-case protection) and hard uses a plain average (no protection, opt-in). The median sits between the two without introducing a new weighting factor to tune.

---

## 2026-09 | YouTube Developer Policies read directly for story 35; a related metadataRaw constraint surfaced

Decision: story 35 (the public ground-truth data API) is confirmed clear to ship as designed. Separately, any raw YouTube API Data (a video's title, description, channel name, view counts) that ends up persisted in `metadataRaw` needs its own delete-or-refresh cycle within 30 calendar days; it can't be kept indefinitely as-is.

Why: read YouTube's actual Developer Policies directly rather than a summary. Section III.E.4.h prohibits substituting or deriving new metrics from YouTube's own numeric/engagement data, its own example is entirely about view and like counts, it says nothing about publishing an independently-sourced fact (artist, title, release year, all CC0 from MusicBrainz/Discogs/Wikidata) merely because a video's title or channel name was used as a lookup key to find it. Section III.E.4.c/d separately requires non-authorized API Data to be deleted or refreshed within 30 calendar days of storage, a real constraint on `metadataRaw`'s shape that wasn't on record before this read, unrelated to story 35's own data, which was never sourced from YouTube API Data in the first place.

---

## 2026-09 | Report and confirmation resolution: fully manual, ranked by a five-tier priority queue

Decision: a submitted report never changes `verificationStatus` on its own, an admin decides every case manually. The admin review queue ranks cards by priority, not submission time: (1) converging reports, two or more independent reports on the same card suggesting the same year, ranked highest regardless of current status, including an already-`VERIFIED` card; (2) reported without convergence, a single report or several that disagree with each other, ranked below convergent reports by confidence tier; (3) unreported `NEEDS_REVIEW`/`MANUAL_ENTRY` cards with at least one community confirmation (a thumbs-up, distinct from a report, shown only on these two tiers), ranked by confirmation count, a fast confirm rather than research; (4) unreported cards with no confirmations, ranked by confidence tier alone; (5) `VERIFIED` cards with no report never enter the queue. Convergence is defined as two independent reports agreeing on the same year, not three. The review surface shows the admin the actual signals behind a card's rank (report count, whether they converge and on what year, confirmation count), not a single opaque score.

Why: at this project's scale (100-200 users), full manual review is not a burden, and any automation that lets a report or a vote count flip `verificationStatus` on its own risks trusting a single anonymous claim, or a small group's coordinated votes, over reconciliation that already ran against real sources, against the project's own "professional-grade, not just working" standard. A confidence-only queue and a report/confirmation-blind queue were both considered and rejected: confidence alone ignores real evidence a report or a wave of confirmations represents, and treating every report identically regardless of convergence wastes review time on isolated, likely-mistaken reports ahead of ones where multiple people independently agree. The two-independent-reports convergence bar, not three, accounts for how few people are ever going to report the same niche song's error at this user scale; a higher bar would mean most genuine errors never reach it.

---

## 2026-09 | Multi-artist storage: ordered list with roles, equal for guessing, remix/cover/mashup split into their own songs

Decision: a song's artists are an ordered list, each entry tagged `MAIN` or `FEATURED`. More than one `MAIN` artist is allowed, a joint credit like "Queen & David Bowie" has two, neither featured. The role tag is a display concern only: a physical card prints the main artist(s), then "featuring" the featured ones. For guessing and scoring, every artist on the list is treated identically, naming any single one correctly earns the token, not all of them. A `(feat. X)`/`(ft. X)` clause is stripped from the song title and its artist extracted into the list instead, superseding the AI microservice's existing title-cleaning rule, which currently keeps that clause in the title text. A remix, cover, or mashup clause is the only thing that survives in the title, and each becomes its own separate `Song`, its own artist list and release year, not a variant of the original; story 16's pgvector duplicate check must not treat one as a near-duplicate of the track it's based on.

Why: a flat, equally-weighted list is simpler than modeling a strict single-main-plus-features hierarchy, and matches how real credits actually work, some songs have multiple co-equal artists with no featured credit at all. Keeping the main/featured role as display-only, rather than scoring-relevant, avoids penalizing a correct guess just because the player named the featured artist instead of the main one. Splitting a remix/cover/mashup into its own song follows from treating it as what it actually is, a different recording, often by a different artist, with its own release date, not the same song with an alternate title.

---

## 2026-09 | Database split: one explicit boundary, transactional versus analytics

Decision: the project has exactly one database split, and it's given its own story (42) rather than staying implicit inside story 33's provisioning task: the transactional Postgres+pgvector instance holds every entity either service reads or writes today (users, groups, sessions, rounds, guesses, songs, playlists, embeddings), and story 33's separate append-heavy store holds only usage/event data. No per-service database (one for the core service, a separate one for the AI microservice) is planned.

Why: story 33 already decided to provision a separate analytics store, but the actual domain boundary, which entities live where, was only ever implicit in that story's tasks. Giving it its own story documents the boundary at the architecture level once, instead of restating it inconsistently across stories 33 and 34.

---

## 2026-09 | Session-long guess leaderboards, open to every player except the DJ

Decision: every player except the DJ, the round's active player included, can submit a title/artist guess. The active player's guess works exactly as it already does, both correct earns the token, independent of placement correctness, either one wrong earns nothing. Every guess, active or not, also feeds two running per-player tallies for the session: "Most Artists Guessed" (every individual artist name correctly given, main or featured, from any song, counts once, regardless of how many total artists that song has) and "Most Titles Guessed" (every fully-correct title). Both leaderboards are shown alongside the main card-count ranking at session end.

Why: the existing mechanic only lets the active player guess, meaning everyone else has no reason to pay attention to a round's title and artist at all. A second, token-independent leaderboard gives every player a reason to engage every round without changing the core token/betting economy, since these tallies never affect card ownership or win condition.

---

## 2026-09 | Story 30: country/language filter dropped in favor of a sitelinks-based international default, and public playlists

Decision: a country/language filter dimension for difficulty-generated sessions is dropped. A difficulty-generated set defaults to international scope instead, a song counts as international if its Wikidata sitelinks count (the number of language-edition Wikipedia articles covering it) clears a threshold, decided once there's enough real catalog data to check it against, not guessed now. Separately, playing from an existing playlist now covers three cases: a playlist the player owns, one they're a member of, and one someone has published publicly for anyone to use, a new capability.

Why: a dedicated country/language filter adds a real design and data-modeling surface (whether it's its own dimension, how it interacts with difficulty tiers) for a need the sitelinks-based international default already covers more simply, distinguishing a globally-known hit from a domestically-known one in the same language or country, without needing a separate filter a user has to think to apply. The sitelinks signal itself was already validated during the metadata-sourcing spike, this decision is about how to use it, not new testing. Public playlists exist because "select a playlist you have access to" was previously limited to ownership or membership, with no way for one person's curated set to reach anyone outside their own group.

---

## 2026-09 | Typo-tolerance threshold for in-round title/artist guesses: normalized, flat edit-distance budget of 1

Decision: a title or artist guess is checked against the canonical answer by normalizing both strings (lowercase, strip all punctuation, strip diacritics, collapse out all whitespace) and comparing the result with Damerau-Levenshtein edit distance, where an adjacent-letter transposition counts as one edit rather than two. A guess counts as correct at an edit distance of 1 or less; anything higher fails, regardless of how long the title or artist name is. This applies identically to the title guess and to each artist-name guess, naming any single artist correctly on a multi-artist song already earns the token per the existing rule.

No separate word-count, word-order, or per-word-ratio rule is needed: normalizing away spacing and punctuation before computing a single whole-string edit distance already produces the right answer for every calibration example worked through, including cases that originally looked like they needed their own handling. A missing word ("Beatles" for "The Beatles"), a reordered pair ("Rhapsody Bohemian"), and an added word ("Bohemian Rhapsody Song") all land at an edit distance well past 1 once compared as one string, without a dedicated rule for any of them. A short, unrelated real word swapped in for another ("App" for "Up", "and" for "N'" in "Guns N' Roses") fails the same way a long word's typo passes: the rule cares about edit distance, not word length or meaning, so "Guns and Roses" does not count as correct.

A length-scaling budget (more characters allowing more tolerated edits) was tested directly against real examples up to 8 words and 30+ characters, two independent one-letter typos in a title of that length still did not count as correct. The crossover point where a longer title would earn budget for a second typo, if one exists at all, sits above the length of nearly any real song title or artist name in this catalog, so scaling was dropped in favor of a flat budget of 1 everywhere.

Why: the mechanism was already decided as edit-distance/fuzzy matching, not semantic or ML-based, so a rule has to hold up character by character, not by recognizing that "N'" means "and." A flat, unscaled budget of 1 is also the simplest rule that fits every calibration example from both sessions, and calibration explicitly tested and rejected the alternative (scaling with length) rather than assuming it away.

---

## 2026-09 | Story 28 scoped: one unified redesign pass, fresh visual direction

Decision: story 28's UI redesign covers the existing implemented pages (landing, auth, dashboard/playlist and song CRUD) and the not-yet-built gameplay screens `GAME_DESIGN.md` already specs (timeline, guess box, voice sidebar, chat overlay, DJ view, results/leaderboards) in one unified pass, not two separate efforts. The visual direction starts fresh rather than building on the current shadcn/Tailwind theme tokens, though the underlying shadcn component library stays unless a specific component doesn't hold up under the new direction. Mockups are built as a multi-artboard canvas through the `design` skill (Claude Design's canvas editor, available directly in this environment) rather than an external tool, reviewed before any implementation code is written.

Why: the gameplay screens don't exist as code yet, so designing them separately from the existing pages would mean shipping two visually disconnected halves of the same app. A single pass keeps the whole product consistent from the start instead of redesigning the existing pages once now and the new ones again later.

---
