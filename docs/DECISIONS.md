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

## 2026-09 | DJ controls the round's flow, reveal is the DJ's alone

Decision: the DJ, not any player, controls a round's flow, pause, play, close the YouTube tab or app, end the current turn, and reveal. The 2026-07 DJ-link-out entry's "any player can reveal" trade-off is superseded by this: reveal still can't be automatic, there's no programmatic access to a page the app doesn't control, but it's the DJ's manual trigger specifically, fired only after the betting window closes, not a power every player holds. Separately, screen or system audio sharing for a remote session's WebRTC tab capture only starts when the DJ clicks "Open YouTube Link," paired with an explicit UI warning that doing so broadcasts their tab or system audio to the group. The active player's own audio stream cuts off immediately once they lock in a guess, regardless of what's still playing on the DJ's end.

Why: letting any player reveal was a placeholder from when reveal was first designed as a stopgap for the lack of programmatic playback access, not a deliberate multiplayer-UX choice. The DJ already controls playback, giving them the rest of the round's flow controls too keeps one person responsible for pacing the round instead of leaving reveal timing to whichever player clicks first. The audio-sharing warning exists because starting tab capture is a real, consent-relevant action, a player should know before it happens that their audio is about to broadcast.

---

## 2026-09 | Story 30 cut to two top-level modes: Difficulty-Based and Custom, theme generation dropped

Decision: story 30's playlist/session selection is exactly two top-level modes, Difficulty-Based (Auto-Generated), a card set assembled on the spot and scored for the group's actual players, and Custom, the player starts a session from an accessible playlist or pastes one directly. Theme-request generation, absorbed from story 21 into an earlier version of this story, is dropped entirely, and story 21 itself is now Dropped rather than Consolidated. The owned/member/published-public three-way access model for playing from an existing playlist stays: a playlist a player owns, is a member of, or that's been published publicly are all valid under Custom mode, and publishing a playlist publicly stays a real capability with its own `isPublic` flag and publish/unpublish endpoint.

Why: theme generation depended on catalog search (story 14) and new genre/popularity fields that existed for no other reason than to serve it, real scope for a feature that hadn't proven it was worth building yet. Cutting it down to the two top-level modes that matter, an on-the-spot generated set and a player's own accessible playlist, ships a simpler, real feature instead of carrying speculative scope. The three-way access model stays because publishing a playlist publicly is a real, wanted capability independent of the top-level mode count, dropping it was a miscommunication during the original cut, not a deliberate scope decision.

---

## 2026-09 | Story 40's YouTube-ID check and story 16's pgvector check: exact-match sequencing decided

Decision: story 40's exact YouTube-ID check runs first; only a genuinely new ID reaches story 16's pgvector embedding check. A high-confidence pgvector match against an existing `Song` means the new ID is another upload of an already-known song, not a new one: link it into story 40's alternate-ID table against that existing `Song` and stop, no full pipeline run. The full metadata pipeline only runs once both checks come up empty.

Why: this was previously left open pending story 16 actually existing. The two checks solve adjacent but different problems, an exact ID match versus a near-duplicate under a different upload, and stacking them in cheapest-first order (no external calls, then an embedding comparison, then the full pipeline only as a last resort) avoids ever running the expensive pipeline for a song the catalog already has under a different YouTube ID.

---

## 2026-09 | Wikidata sitelinks count also weights story 30's difficulty tiering

Decision: a song's Wikidata sitelinks count, already used as story 30's international-scope gate, also weights which songs each difficulty tier draws from. Easy weights song selection toward higher-sitelink, more widely-recognized songs; hard carries no such weighting and can pull from low-sitelink, niche/obscure catalog entries same as anything else; medium sits between. This blends with, not replaces, the per-song aggregate guess-correctness score and the personalized collaborative-filtering layer, and is what a newly-verified song with no real guesses yet falls back on until it has enough play history for the aggregate score to mean anything.

Why: sitelinks count is a real popularity proxy, a song covered by many language editions of Wikipedia is a widely-recognized one, and it's available the moment a song is verified, unlike the guess-based signals, which need real play history to exist first. Difficulty-based generation is meant to favor popular, recognizable tracks for its easy tier and only reach into the niche/underground catalog this project otherwise deliberately supports (`CLAUDE.md`) as the tier climbs, not treat every verified song the same regardless of how many people would actually recognize it.

---

## 2026-09 | Song editing narrows to MANUAL_ENTRY once verificationStatus ships

Decision: once story 23's `verificationStatus` field exists, editing a song's fields directly is restricted to `MANUAL_ENTRY` songs only. `VERIFIED` and `NEEDS_REVIEW` songs lose the edit action entirely and keep only the report affordance (`NEEDS_REVIEW` also keeps story 17's thumbs-up confirmation once that ships). Today's live Song Detail page, which has no `verificationStatus` field yet and lets any song's fields be edited, is unaffected until story 23 lands; this decision governs the shape editing takes once it does.

Why: surfaced during story 28's design pass. `VERIFIED` data already went through source agreement or an admin's manual review, hand-editing it back out undermines the trust tier the pipeline just established; `NEEDS_REVIEW` data came from LLM reconciliation of real, if disagreeing, source data, correcting it by hand the same way a `MANUAL_ENTRY` song would skips the review process story 17 already defines for exactly this case. `MANUAL_ENTRY` is different in kind, a human guess with zero source corroboration, the least-trusted tier specifically because nothing else vouches for it, so it's the one tier where letting the submitter (or another member) fix it directly by hand is a real improvement rather than a bypass.

---

## 2026-09 | Import songs from an existing playlist, and its background-import UX, given their own scope

Decision: two pieces of new scope surfaced during story 28's design pass, neither previously tracked. First, a third way to add songs to a playlist alongside search-and-add (story 14) and YouTube-playlist import (story 40): copying songs directly from a playlist the player already has access to (owned, a member of, or published publicly) into the one they're editing, instant, no metadata pipeline involved since every song is already a resolved catalog row. Given its own story, 45, blocked on story 15's song/playlist join table. Second, the background-import UX for story 40's YouTube-crawl path specifically: a temporary left-sidebar icon and a fading toast, both reopening the import screen with live progress, so leaving the screen doesn't cancel an in-progress crawl. Added as frontend tasks under story 40 rather than a new story, since it's UX for a capability story 40 already owns.

Why: both were reasonable, wanted features once the design pass reached the "add songs to a playlist" screens, but CLAUDE.md's task gate means design work surfacing new scope gets written into the backlog before it's built, not folded silently into mockups with no corresponding story or task.

---

## 2026-09 | Playlist membership: owner/admin, granular permissions, kick versus ban, per-playlist identity

Decision: a playlist gets a real owner/admin, today's `Playlist.users` many-to-many has no such concept. Only the owner can rename the playlist, change its cover/color/description, toggle it public, delete it, or manage other members. Each non-owner member holds three independently revocable grants: read, write (add songs), and delete (remove existing songs), not a single role. Removing a member is two distinct actions: kicking (membership ends, the invite link or code still lets them rejoin later) and banning (membership ends and rejoining is blocked outright). Separately, joining a playlist by invite prompts for a per-playlist display name and avatar, defaulting to the account's own but editable, the same pattern `GAME_DESIGN.md` already specifies for joining a group, now extended to playlists.

Why: surfaced during story 28's design pass on the Edit playlist and Join by invite screens. Even read being revocable, not just write and delete, was a deliberate call: it lets an owner temporarily shut a member out without removing them from the playlist outright, distinct from a kick. Extending per-space identity to playlists mirrors the reasoning that already justified it for groups, playing under a different name or avatar with people who don't share every space, without conflating a playlist's own membership model with a game group's.

---

## 2026-09 | DJ holds no in-app round controls; reveal and turn advance run automatically

Decision: the DJ's only in-app action is "Open YouTube Link." Pause, play, close, end turn, and reveal, all previously listed as DJ-only controls, are removed. Playback control happens entirely on the real YouTube page or app, not mirrored into the game. The round's own flow no longer waits on a DJ trigger anywhere: the betting countdown and window already run on their own timers once the active player locks in a placement, so reveal firing automatically once the betting window closes, and the active player role advancing automatically once scoring resolves, both fall out of timers the game already has running rather than needing a new one.

Why: surfaced during story 28's design pass on the DJ view screen. Mirroring play/pause/close into the app duplicates controls the DJ already has open on the real YouTube tab for no benefit. End turn and reveal looked like they needed a manual trigger, but the betting window was already timed, closing it and firing the reveal off the same clock removes a step without changing when either happens.

---

## 2026-09 | A flagged prompt-injection attempt is an abuse-visibility event

Decision: when story 41's injection-detection check flags a submission, that flag itself writes an abuse-visibility event, the same category as story 34's rate-limit-exceeded and report-submitted events, not just an ambiguous-content outcome.

Why: an attempted prompt injection is evidence of intent, not the same kind of uncertainty as a song the non-music classifier genuinely can't place. Tracking it the same way as the other abuse-visibility signals keeps a pattern of injection attempts reviewable later, the same reasoning already applied to rate-limit and report abuse. Depends on story 34's event pipeline existing.

Open item: whether a flagged submission is also outright rejected, versus routed to manual review, still needs a call, not made here.

---

## 2026-09 | Story 20 greenlit: the validated metadata pipeline is built into production

Decision: the local/cheap LLM spike's validated two-tier pipeline and model choice (gpt-5-nano for reconciliation, DeepSeek-V4-Flash for Wikipedia extraction, the patient/fast tier shape) is built into the real AI microservice, not left validated-but-unbuilt in `ai/spikes/`. Story 20's own remaining scope narrows to exactly the client infrastructure this needs beyond what already exists: a DeepInfra (OpenAI-compatible) client for DeepSeek-V4-Flash, copied and adapted from `ai/spikes/openai_compatible_spike.py`'s validated implementation. gpt-5-nano needs no new client, the existing `openai_client.py` already calls OpenAI by model name.

Why: the spike ran real accuracy numbers against a 70-song test set (99% patient tier, 90% fast tier) and turned up no reliability concern worth re-testing before building; the only thing left open was whether to actually ship it, not whether it works. The winning design isn't a genuinely local, self-hosted model, llama.cpp's accuracy came in well below its own hosted twin under quantization, so "local LLM option" narrows in practice to "the cheapest hosted models that actually clear the accuracy bar," which the spike already identified.

---

## 2026-09 | Story 39's timer lifecycle runs on a scheduled sweep, not a per-group in-JVM timer

Decision: the group lifecycle's two 30-minute timers (creation to first session, and session end to the next one) share a single nullable `expiresAt` column on `Group`. A `@Scheduled` job (`GroupExpirySweeper`, running every 60 seconds) deletes any group whose `expiresAt` has passed, covering both windows through the same mechanism rather than one timer implementation per case. `expiresAt` is null while a session is actually in progress, since neither timer should run then.

Why: a per-group `Timer` or `ScheduledExecutorService` task can't be exercised in a test without actually waiting out its delay. A sweep can: a test sets `expiresAt` to an already-past instant and calls the sweep method directly, no real waiting involved. This is also the first `@Scheduled` usage in the backend, `@EnableScheduling` is added on `BackendApplication` for it.

---

## 2026-09 | Story 39 ships group settings persistence without the real-time broadcast; voice and chat stay presence-only or entirely deferred

Decision: story 39 builds `Group` and `Member` fully, including the admin-only settings update endpoint, but three pieces of its original task list don't ship complete, each already scoped to a story ROADMAP.md sequences right after this one.

Settings changes (playlist(s), DJ mode, win-condition card count) persist correctly through `GroupService.updateGroupSettings`, a single method left clean for story 11 to call from its WebSocket handler or publish an event from. Nothing pushes the update to other members yet, since story 11 (real-time sync over WebSocket) doesn't exist in this codebase and is the very next batch after this one; ROADMAP.md's Phase 1 note already calls out building story 11 alongside stories 10 and 39 for exactly this reason.

Voice gets a plain `isInVoice` presence flag on `Member` and join/leave-voice endpoints that flip it, nothing more. The actual WebRTC mesh and signaling belong to story 12, which is blocked on stories 11 and 39 both shipping and owns that mechanism on its own terms.

Chat isn't built at all: no `ChatMessage` entity, no send/receive/persistence endpoint. Story 13 owns group-scoped text chat outright and is also blocked on stories 11 and 39. `Group` keeps a normal `Long` primary key, which is all story 13 needs to attach messages to it later.

Why: building real-time broadcast, WebRTC signaling, or message persistence into story 39 would duplicate work story 11, 12, and 13 already own, ahead of the WebSocket layer all three actually depend on. Shipping the group/member model and its REST surface now, with clean extension points for the deferred pieces, unblocks those later stories without guessing at a transport layer that doesn't exist yet.

---

## 2026-09 | Story 11: STOMP config, JWT handshake auth, and the group-event messaging pattern

Decision: the core service gets one STOMP-over-WebSocket endpoint (`/ws`, no SockJS fallback), one application-destination prefix (`/app`), and one simple-broker prefix (`/topic`), configured in `WebSocketConfig`. The WebSocket handshake authenticates the same JWT the REST side already validates, but through a `ChannelInterceptor` on the STOMP CONNECT frame rather than an HTTP handshake interceptor: a browser's WebSocket handshake can't carry a custom `Authorization` header, so the client sends the token as a native STOMP header on CONNECT instead, and `StompAuthenticationChannelInterceptor` reads it there, matching the token-based-authentication pattern Spring's own documentation recommends over the HTTP-session-oriented handshake-interceptor approach.

Per-group STOMP destinations follow `/topic/groups/{groupId}/membership`, `/topic/groups/{groupId}/settings`, `/topic/groups/{groupId}/chat`, and `/topic/groups/{groupId}/voice` (`GroupDestinations`), plus a reserved client-to-server `/app/groups/{groupId}/admin` for a future WebSocket-driven admin action, unused today since every admin action (settings, start session) still runs over REST and broadcasts from `GroupService`'s own methods. Chat and voice are naming reservations only, story 13 and story 12 own the actual send/receive and signaling logic. Story 10's game-session batch extends this same convention in parallel under `/topic/sessions/{sessionId}/...` and `/app/sessions/{sessionId}/...`, documented in `GroupDestinations`' javadoc rather than built now, since no session model exists yet.

`GroupService` doesn't talk to STOMP directly. It publishes a `GroupBroadcastEvent` (an event type plus the fresh `GroupDetailDTO` snapshot) through Spring's `ApplicationEventPublisher`, no prior usage existed in this codebase, this is the first. `GroupBroadcastListener` picks the event up, serializes it to JSON with the application's own Jackson `ObjectMapper`, and forwards it to the matching topic via `SimpMessagingTemplate`. Broadcasting the whole group snapshot on every change, rather than a bespoke delta payload per event type, keeps the client side of this simple: replace the local copy of the group on any message. `GroupService` broadcasts member joined, member left, settings changed, and game session started as the story's task list names, plus member-connection-changed (disconnect/reconnect) and admin-transfer, both genuine "membership changes" a client needs reflected live even though the original task list didn't spell them out individually. A client's WebSocket disconnect is handled for the group-member half only: `GroupSessionEventListener` reacts to Spring's `SessionDisconnectEvent`, resolves the member from the socket's authenticated principal (not a request-scoped `SecurityContext`, which doesn't exist on a socket event), and calls a new `GroupService.disconnectMember(groupId, userId)` overload. The in-session-player half of disconnect handling has no code to write against yet, story 10 owns the `Player` entity.

A client registers presence by subscribing to its group's membership topic right after creating or joining the group over REST; `GroupSessionEventListener` reacts to that subscription (via Spring's `SessionSubscribeEvent`) to record the socket session against the group in `GroupPresenceRegistry`, an in-memory session-id-to-group-id map read back on disconnect. Group-side state events (membership, settings changes) log as a single structured line, `groupEvent type=... groupId=...`, a shape story 12's WebRTC connection lifecycle logging can append to later without redesigning the log format.

This batch's tests couldn't exercise a real network socket: this sandbox's JDK cannot open even a loopback TCP connection under any NIO selector provider (embedded Tomcat's `NioEndpoint` fails with "Unable to establish loopback connection" the same class of constraint `GroupLifecycleIntegrationTest`'s own comments already document for `java.net.http.HttpClient` construction against the OAuth2 client beans). `GroupWebSocketIntegrationTest` drives the real Spring message channels and the real simple broker directly instead: sending genuine STOMP SUBSCRIBE/DISCONNECT frames into `clientInboundChannel` and publishing the `SessionSubscribeEvent`/`SessionDisconnectEvent` the WebSocket transport layer would normally publish, then asserting against the broker's own `SubscriptionRegistry` (which session is subscribed to which destination right now) rather than capturing delivered frames, since actual frame delivery also passes through `SubProtocolWebSocketHandler`, which needs a live `WebSocketSession` this sandbox can't create. The subscription registry is what determines delivery in production, so asserting against it directly still proves the reconnect guarantee (no stale or duplicate subscription survives a disconnect-and-resubscribe cycle) without depending on that transport-facing code.

Why: a ChannelInterceptor on CONNECT is Spring's documented approach for token-based STOMP authentication and mirrors the REST side's own Authorization-header path, rather than inventing a cookie-forwarding scheme for the handshake. An event-publisher-then-listener indirection keeps `GroupService` ignorant of the transport layer entirely, the same separation the existing codebase already draws between `GroupService` and `GroupMapper`/`JwtUtil`; it also means the existing Mockito-based `GroupServiceTest` only grew an `ApplicationEventPublisher` mock rather than a `SimpMessagingTemplate` mock scattered across every test that touches a mutating method. Broadcasting member-connection and admin-transfer changes was a deliberate small extension past the story's literal four-event list: leaving a peer's connection dot stale in every other client's UI until their next unrelated group event would be a worse outcome than the story text technically not naming it. The subscription-registry-based test strategy is the sandbox-driven adaptation of this batch's testing, in the same spirit as `GroupLifecycleIntegrationTest`'s minimal JPA-only context: prove the guarantee against the real component that provides it, without requiring infrastructure this environment cannot stand up.

---

## 2026-09 | Story 10: round-timer scheduling, betting concurrency, and round-rotation rules `GAME_DESIGN.md` left unspecified

Decision: every round-level timer (the 3-5 second lock-in countdown, the 15-second betting window, the 90-second active-player turn timeout, and the 10-minute auto-abandon check) is split into a plain, directly-callable effect method on `GameSessionService` and a thin scheduling layer, `GameSessionScheduler`, that wraps a single injected Spring `TaskScheduler` bean. `GameSessionService` never waits on real time itself: `lockInPlacement` schedules `startBettingWindowEffect` after the countdown, `startBettingWindowEffect` schedules `revealEffect` after the betting window (or calls it immediately if no eligible player holds a token), `disconnectPlayer` schedules `turnTimeoutEffect` when the disconnecting player is mid-turn, and `checkAutoAbandonEffect` is scheduled the instant every player is disconnected. Every effect method is idempotent against being called more than once or after the state it expects has already moved on (a status check at the top returns early), since a manual call, a real timer, and a skip action can all reach the same effect. A dedicated `TaskScheduler` bean (`SchedulingConfig`, a pooled `ThreadPoolTaskScheduler`) is added for this; `GroupExpirySweeper`'s existing `@Scheduled` sweep now also runs on this pool instead of Spring's single-threaded default, a side effect of the bean existing at all rather than a deliberate change to that sweep.

Betting concurrency is handled with one atomic conditional `UPDATE`, not application-level locking: `RoundRepository.tryAcceptBet` sets a round's `bettor_player_id` only `WHERE bettor_player_id IS NULL`, and Postgres's own row-level locking serializes concurrent attempts against the same row, so exactly one caller's update affects a row and every other caller's affects zero, with no retry loop or `SELECT ... FOR UPDATE` needed. A token is deducted (`PlayerRepository.deductToken`, itself a guarded conditional `UPDATE`) only after that round's bet is actually accepted, so a losing attempt truly costs nothing, matching `GAME_DESIGN.md`'s betting rule exactly.

`GAME_DESIGN.md` describes DJ mode ("fixed" or "rotating") without saying how the DJ role interacts with the active-player rotation. Resolved: in `FIXED` mode, one player (the earliest in turn order at session start) is the permanent DJ for the whole session and never enters the active-player rotation at all, playing music but never taking a timeline turn themselves; the remaining players rotate the active-player role among themselves, skipping anyone marked `Left`. In `ROTATING` mode, every player rotates through the active-player role in turn order, and each round's DJ is whichever player is next up after that round's active player, so DJ duty and playing your own turn never collide for anyone. If fewer than two players remain with `ACTIVE` status after a round resolves (through timeout or explicit leave), the session can't continue and is abandoned rather than completed, since no win condition was reached.

Song selection is a fixed, non-randomized queue: at session start, one song per player is popped off the group's combined playlist songs (in playlist order) as their starting anchor card, and the remaining songs form an ordered queue popped from the front as each round starts. `GAME_DESIGN.md` doesn't specify a selection algorithm; a real shuffle is left for a later pass, since nothing in this story's rules or tests depends on song order being unpredictable, and a deterministic queue is what let the round-flow tests construct exact placement/scoring scenarios without needing to intercept a random source.

A completed or abandoned session's rows (roster, rounds, guesses, timeline cards) are deleted immediately as `GAME_DESIGN.md` and `ARCHITECTURE.md` require; the one thing kept past the purge, a completed session's results export, lives in a small in-memory `SessionResultsStore` keyed by group id rather than its own database table, since nothing in the story calls for keeping it forever, across a restart, or queryable by anything other than the group it belonged to.

Why: splitting a timer's effect from its scheduling mechanism is what makes round-level timers (measured in seconds) unit-testable at all, the same reasoning `GroupExpirySweeper`'s sweep-based design already established for the group lifecycle's 30-minute timers, extended here to real wall-clock scheduling for the cases (the full lifecycle, auto-abandon, and disconnect/timeout integration tests) that need to prove the wiring itself works. Two different test doubles cover that proof: an immediate-fire `TaskScheduler` for the lifecycle and disconnect tests, which turns a scheduled effect into a synchronous call so a multi-round game plays out instantly instead of over real minutes, and a real `ThreadPoolTaskScheduler` for the betting-concurrency test, which needs a round's `BETTING` state actually committed and visible to other database connections before racing real threads against it. An atomic conditional `UPDATE` for betting concurrency was chosen over pessimistic locking because it needs no explicit lock management and is trivially correct under Postgres's own row-level locking, the same reasoning a unique-constraint-style guard would get for any single-winner race. The DJ-rotation rule was resolved by picking the interpretation that keeps every round structurally sound (the DJ and the active player are never the same person, matching `GAME_DESIGN.md`'s "separate roles" framing) while giving `FIXED` mode a real, distinct meaning from `ROTATING` rather than collapsing into it.

---

## 2026-09 | Story 46 migration: owner selection for pre-existing playlists

Decision: the story 46 migration assigns each pre-existing playlist's owner as the lowest user id among its `user_playlists` members, its earliest-created account. A playlist that held songs but never had a `user_playlists` row of its own falls back further: the contributor of its oldest song becomes both owner and sole member, since a song's `added_by` is always a real account and nothing else ties such a playlist to a user at all. A playlist matching neither case, no members and no songs, is dropped by the migration; the application never leaves a playlist in that state outside of migration, since the last member leaving already deletes it.

Why: no creation timestamp exists on `Playlist` to read an actual creator from, so a deterministic stand-in was needed. Lowest user id approximates "earliest account, most likely the creator" without adding a real audit trail this story doesn't otherwise need. The song-contributor fallback exists because real data can apparently include a playlist with songs but no matching `user_playlists` row (this surfaced in the migration test suite's existing fixtures), and every playlist needs a determinable owner coming out of this migration, not just the common case.

Note, 2026-09: reversed, see the 2026-09 "Story 46 migration reversed" entry below. Pre-existing playlists and songs are cleared outright instead of having an owner guessed for them.

---

## 2026-09 | Story 46: the playlist owner can't leave while other members remain

Decision: leaving a playlist you own is only allowed once you're its last remaining member, in which case leaving deletes the playlist exactly as it already did before this story. While other members are still on the playlist, the owner's leave attempt is rejected; kicking or banning every other member first, or waiting for them to leave, clears the way.

Why: the owner is the sole source of every owner-only action, including managing membership itself. An owner leaving mid-playlist would strand the remaining members with no one able to change grants, kick, ban, or otherwise administer it, and this story doesn't introduce an ownership-transfer mechanism to hand that authority off cleanly first.

Note, 2026-09: reversed, see the 2026-09 "Owner leave and ownership transfer" entry below. The owner can leave at any time; leadership passes automatically instead of being blocked.

---

## 2026-09 | Song deletion once a song isn't playlist-exclusive: unlink first, delete only when orphaned everywhere

Decision: story 15 replaces `Song`'s singular `@ManyToOne playlist` with a `song_playlists` join table, so a song can belong to more than one playlist. Removing a song from a playlist now only unlinks that one join row. The song row itself is only deleted outright once that removal leaves it with zero remaining playlists.

Why: there's no catalog view today that can reach a song with no playlist at all, so leaving one behind as a fully orphaned, unreachable row is worse than deleting it. But a straight delete on every removal, the old behavior when a song could only ever have one playlist, would silently break the song for every other playlist it's still linked to once story 45's cross-playlist copy exists. Checking for zero remaining playlists before deleting gets both: no dangling rows, and no destructive surprise for a shared song.

Note, 2026-09: reversed, see the 2026-09 "Song deletion reversed" entry below. A song is a standalone catalog entity, independent of any playlist it belongs to; it is never deleted as a side effect of removing it from a playlist, including its last one.

---

## 2026-09 | Song deletion reversed: a song is independent of any playlist, never deleted for having zero

Decision: removing a song from a playlist only ever unlinks the join row. `PlaylistService.deleteSong` no longer deletes the `Song` row itself under any circumstance, including when that removal leaves it with zero remaining playlists. `checkSongBelongsToPlaylist` also moves off `song.getPlaylists()`'s in-memory collection (a stream filter over every playlist the song belongs to) onto a single repository existence query (`SongRepository.existsByIdAndPlaylistsId`) against the join table directly, since the check only ever needs to know about one playlist, not load them all.

Why: a song is a standalone catalog entity in its own right, not something that exists only in service of being in a playlist. Treating "zero playlists" as a deletion trigger, the prior decision's compromise, still destroys real data the moment a song is unlinked from its last playlist, exactly the kind of destructive surprise that decision was trying to avoid for a song shared across playlists, just triggered by a smaller number of playlists (one) instead of several. A song sitting with zero playlists is a normal, permanent, valid state, not a signal that something needs cleaning up. The access-check query change is a plain efficiency fix: filtering a fully-loaded collection in Java does strictly more work than a direct exists query against the join table for a check that only cares about one specific playlist.

---

## 2026-09 | SongTag replaced by a genre field, reading the settled design mockups back into the backend

Decision: `SongTag` (`PLAYLIST`/`SPECIAL`/`ANIME`) and the `song_tags` table are dropped outright, with no automatic backfill into anything, existing tag data doesn't carry forward. `Song` gets a nullable `genre` string instead, populated later by the metadata pipeline rather than user-submitted, so it's absent from `CreateSongRequest`/`UpdateSongRequest` the same way `confidence` and `metadataRaw` are. `GET /api/enums/tags` is removed with it, there's no fixed enum left to enumerate. Separately, `CardGenerator`'s printed card is redrawn to match the settled look in `docs/design/source/CardOptions.dc.html`: one flat color per card instead of a two-color gradient, rounded corners, a thick border, a hard offset shadow, and only artist/year/title on the face, no tag triangle or country flag (the tag triangle's removal also drops the last thing that read a playlist's color for card rendering, superseding a fixed placeholder color a different PR had introduced there in the meantime). The card/QR export endpoints gain a `PaperSize` parameter (A4, Letter, defaulting to A4), and the page grid is now computed from that paper's real pixel dimensions at 300 DPI instead of a fixed constant that never actually corresponded to a real sheet of paper.

Why: `docs/design/source`'s mockups (built during story 28's design pass, before this schema and before story 28's own implementation phase) were never cross-checked against the current backend when they were made. Going through them now: no mockup shows `PLAYLIST`/`SPECIAL`/`ANIME` or a multi-select tag picker anywhere, `SongDetailLight.dc.html` instead shows a single genre chip next to the year, and no submission-flow mockup lets a user set one, consistent with it being pipeline-derived. `CardOptions.dc.html` explicitly settles the card's visual shape and states plainly what's on it and what isn't. This work is backend only; the frontend side (a real genre/tag input surface, and a still-undesigned print-settings screen for choosing paper size) is deferred to story 28's implementation phase, where the two get reconciled directly against whatever the frontend actually needs instead of guessed at now.

---

## 2026-09 | Story 14's song search is catalog-wide, backend built first

Decision: story 14's search endpoint (`GET /api/songs/search`) searches the whole `songs` table, not one playlist or one user's playlists. It's a plain authenticated top-level route rather than nested under a specific playlist's URL, since a catalog-wide search has no single playlist to check membership against, and needs none of `PlaylistService`'s per-playlist access checks. A query is matched by exact YouTube ID if it parses as a bare video ID or a YouTube link (watch URL, `youtu.be` short link, or embed URL, the same shapes `AddSongForm.tsx`'s `extractYoutubeId` already recognizes, now replicated server-side as `YoutubeLinkParser`), and otherwise as a case-insensitive keyword against title and artist name. This work is backend only, the same split the song-genre-and-print-redesign batch used: wiring `AddSongForm.tsx` to check search results before submission, and building the search UI itself, are deferred to story 28's implementation phase.

Why: the task list's own motivating use case, checking whether a song is already in the catalog before it's resubmitted as a near-duplicate, only makes sense against every song, not one playlist's subset. This mirrors story 16's pgvector dedup check, also catalog-wide against every `VERIFIED` song, so the two dedup paths (plain keyword/link versus embedding-based similarity) end up covering the same scope. Building the endpoint now and the UI later avoids guessing at a search surface against mockups that predate this decision, the same reasoning already applied to the genre field and the printed card.

---

## 2026-09 | Story 16 built: pgvector duplicate detection, similarity threshold, AI microservice database client

Decision: story 16's dedup check runs in `metadata/service.py`'s `resolve_metadata`, before the source-fetch/LLM step. It normalizes `artist + title` (lowercase, diacritics stripped, punctuation removed, whitespace collapsed, the same normalization shape already used for in-round guess matching), embeds the result with `text-embedding-3-small`, and compares it by cosine distance against every `VERIFIED` song's stored embedding. A distance at or below 0.08 (roughly 0.92 cosine similarity) counts as high-confidence: the existing song's data is reused and the LLM call is skipped entirely. Any failure in this check (an unreachable database, an embedding API error) is treated as no match, not a hard failure, so the full pipeline still runs. The core service's Flyway migration `V7__enable_pgvector_and_add_song_embedding.sql` enables the `vector` extension and adds a nullable `embedding vector(1536)` column plus an HNSW cosine-ops index to `songs`; the local dev Postgres image (`backend/docker-compose.yml`) and every Testcontainers-based backend test moved from `postgres:18-alpine` to `pgvector/pgvector:pg18`, the same Postgres major version with the extension binary bundled in. The AI microservice gets its own direct Postgres connection (`ai/app/dedup/database.py`, a new `database_url` setting) using `pg8000`, a pure-Python driver, rather than `psycopg`: `psycopg`'s compiled bindings failed to load under this project's actual Windows development environment (an OS-level Application Control policy blocking the native DLL), while `pg8000` has no native extension to load at all. Both are officially supported by the `pgvector` Python package's own driver adapters, so the switch cost nothing in capability. The AI microservice only ever reads and writes rows through this connection, never DDL, consistent with `CLAUDE.md`'s schema-ownership rule. Storing an embedding for a verified song (`store_verified_song_embedding`) is implemented and tested but has no caller yet: no code path anywhere in the backend today actually promotes a song to `VERIFIED` (story 18's promotion criteria isn't built), so there is no real trigger to wire it to yet. It becomes story 18's natural hook once that promotion flow exists. Story 15's song/playlist join table was an unmerged, un-landed PR when this story was built; today's dedup check only ever compares against `songs` rows directly and doesn't consider playlist relationships at all, so whether a near-duplicate match should behave differently when a song can belong to multiple playlists is an open question, to be revisited now that story 15 has actually merged.

Why: text-embedding-3-small clusters near-identical short "artist title" strings (differing only in casing, punctuation, minor wording, or an upload's extra qualifiers) far tighter than 0.08 cosine distance, while distinct songs land well above it in practice; ARCHITECTURE.md flagged the exact threshold as an open question, and this is a deliberately conservative starting point rather than a value tuned against production data that doesn't exist yet, revisit once real submissions accumulate. Swallowing dedup-check failures rather than propagating them keeps a best-effort optimization from becoming a new way for the whole submission pipeline to fail. Picking a database client that only needs to satisfy this one project's actual development machine, rather than defaulting to whichever driver is more popular in the wider Python ecosystem, avoids a dependency the team's own environment can't reliably load; pg8000 is a mature, actively maintained driver with first-class pgvector support, not a fallback of last resort. Leaving `store_verified_song_embedding` uncalled rather than inventing a fake trigger for it keeps the story's scope to what it actually owns, and having the function already built, tested, and correct removes real work from whichever story wires the real trigger later.

---

## 2026-09 | Story 46 migration reversed: pre-existing playlists and songs are cleared, not assigned a guessed owner

Decision: the story 46 migration no longer tries to determine an owner for any pre-existing playlist. Every existing playlist and song is deleted outright (along with their join and tag rows), `user_playlists` is still dropped the same as before. `users` is untouched.

Why: nothing in the pre-story-46 schema names a real creator, so any owner the migration picks is a guess, not a fact. Guessing an owner from `user_playlists` row order stands on data that was never meant to carry that meaning. Clearing the catalog instead of migrating it forward on a guess keeps the owner column's data honest from the first row it's ever set on.

---

## 2026-09 | Owner leave and ownership transfer: leadership passes automatically, and the owner can hand it off at any time

Decision: the playlist owner can leave at any time. If other members remain, the member who joined earliest is promoted to owner automatically as part of that same leave; a playlist with other members left is never without an owner. Separately, the owner can transfer ownership to any current member at any time through a dedicated endpoint; the previous owner is not removed and keeps their existing membership and grants, just without the owner-only powers.

Why: blocking the owner from leaving until everyone else was gone, the prior decision, forced a departing owner to kick or ban every other member first just to leave their own playlist, destroying those memberships as a side effect of an unrelated decision to leave. Automatic promotion by join order gives every playlist a deterministic next owner without asking the departing owner to choose one under pressure. The separate transfer endpoint covers the case where the owner wants to hand off leadership deliberately, without leaving.

---
