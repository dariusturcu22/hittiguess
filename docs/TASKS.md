# TASKS.md: What To Actually Work On

This is the source of truth for day-to-day work. Consult PROJECT_STATE.md only when you need the bigger picture behind one of these.

The tasks below, under stories 9 and 12, are drafts and have not yet been confirmed against the real implementation, except where noted. Before starting any of them, check them against the current code: some tasks may already be done, some may not apply the way they're written, and some may be missing. Once a story's tasks are confirmed accurate, update its status to Ready in PROJECT_STATE.md.

Stories 10, 11, and 39 were checked against the real code: no `Group`, `Session`, `Round`, `Guess`, or WebSocket/STOMP code exists anywhere in the backend, so their draft tasks stand as accurate greenfield work. Marked Ready in PROJECT_STATE.md.

Story 9 and story 12 were checked against the real code and confirmed blocked: both assume a group (story 39), a game session (story 10), and a WebSocket layer (story 11) that don't exist yet. Neither can move to Ready until 10, 11, and 39 do.

"Next available task" means the earliest unchecked box under a Ready or In Progress story.

## Story 9: DJ real YouTube link-out

Confirmed against the real code: there's no DJ view, no group, no session concept, and no WebSocket layer today, so this is new work, not a removal. The QR code task was split out and done separately, see `ARCHIVE.md`'s Bug fixes entry. Blocked on story 39 (group), story 10 (game session), and story 11 (WebSocket sync).

- [ ] Build the DJ view: an "open in YouTube" link-out for remote sessions, opening a new browser tab, never an embedded player
- [ ] Wire WebRTC tab audio capture to that new tab and stream it to the other players
- [ ] Add deep-link handling for in-person sessions (Android intent, iOS universal link, fallback to a plain browser link)
- [ ] Wire the manual reveal trigger over WebSocket, any player can reveal once the song has played

## Story 10: Game session

Checked against real code: no session model exists, this is greenfield work. Based on the `GameSession` shape and round flow in `ARCHITECTURE.md`, and the round/token/reconnect rules in `GAME_DESIGN.md`.

- [ ] Implement `GameSession`, `Player`, `Round`, and `Guess` as ephemeral Postgres rows, purged when the session ends
- [ ] Initialize a session from the group's current settings when the admin starts it (playlist(s), DJ mode, win-condition card count), snapshotting the group's connected members as the roster
- [ ] Assign round 1's active player and DJ
- [ ] Round rotation: active player rotates each round, DJ stays fixed or rotates per the group's setting, skipping players marked `Left`
- [ ] Guess placement and lock-in: before/after/between on the active player's timeline, with a lock-in sound effect
- [ ] 3-5 second countdown after lock-in, then a 15-second betting window; skip the window entirely if no player holds a token
- [ ] Betting: token-holding players may bet during the window, first come first served, concurrency-safe so only the first bet is accepted and a losing attempt doesn't cost a token; a skip-betting action ends the window early
- [ ] Artist/title guess box, available to the active player for the whole turn, independent of timeline placement; a fully correct guess awards a token (matching tolerance is an open question, see `PROJECT_STATE.md`)
- [ ] Scoring: apply the four outcome rules in `GAME_DESIGN.md` (correct placement keeps the card even on a tied release year; a correct guess beats any bet; a wrong guess with a correct bet gives the card to the bettor; a wrong guess with no bet discards it)
- [ ] Win condition: first player to reach the group's configured card count wins, bounded 5-20 for a 2-3 player group or 5-15 for a 4-8 player group
- [ ] Player disconnect: mark `isConnected` false, leave timeline/tokens/turn order untouched
- [ ] Player explicit leave: mark `Left`, exclude from future turns and DJ rotation, existing timeline cards still count toward the final results
- [ ] Active-player turn timeout: if the active player is disconnected when their turn comes, or disconnects mid-turn, auto-skip after 90 seconds and mark them `Left`
- [ ] Auto-abandon the session after 10 minutes with zero connected players, no results export in that case
- [ ] Downloadable results export when a session completes normally
- [ ] Purge all session state (roster, rounds, guesses) once the session ends or is abandoned, hand control back to the group
- [ ] Frontend: drag-and-drop timeline placement, cards animate apart to open a gap with no overlap, animate back into place once placed
- [ ] Frontend: artist/title guess box gives immediate animated feedback, a correct guess animates a token dropping into the player's count, distinct animation for incorrect

Tests:
- [ ] Unit tests for scoring: all four outcome rules, including the tied-release-year case
- [ ] Unit tests for win-condition bounds: 5-20 (2-3 players) and 5-15 (4-8 players), including the boundary values
- [ ] Unit tests for round rotation, both fixed and rotating DJ settings, and rotation skipping `Left` players
- [ ] Unit tests for the active-player turn timeout, including the boundary at 90 seconds
- [ ] Integration test: full session lifecycle, admin starts, roster snapshot, several rounds, win condition hit, results export generated, state purged
- [ ] Integration test: auto-abandon path, session torn down after 10 minutes with zero connected players, confirms no export is generated
- [ ] Integration test: betting concurrency, multiple simultaneous bet attempts on the same guess, exactly one accepted, no token lost by the others
- [ ] Integration test: betting window skipped entirely when no player holds a token
- [ ] Integration test: player disconnects mid-turn, doesn't reconnect within 90 seconds, ends up `Left`, and a later reconnect attempt after that point doesn't restore active status

## Story 11: Real-time game sync over WebSocket

Checked against real code: no WebSocket layer exists, this is greenfield work. Based on the sync model in `ARCHITECTURE.md` (REST for group/session creation and join, WebSocket for state changes). Covers both the group and the game session, not just the session.

- [ ] Add the Spring WebSocket/STOMP dependency and base config to the core service
- [ ] Authenticate the WebSocket handshake against the existing JWT auth
- [ ] Define per-group STOMP destinations for broadcast (membership, settings changes, chat, voice signaling) and a client-to-server channel for admin actions
- [ ] Define per-session STOMP destinations for broadcast (round events) and a client-to-server channel for actions (guess, bet, reveal)
- [ ] Broadcast group events: member joined/left, settings changed, game session started
- [ ] Broadcast round events: round started, guess locked, bet placed, reveal triggered, round scored, next round
- [ ] Handle disconnect: mark the member's or player's `isConnected` flag false without ending the group or the session
- [ ] Wire group creation/join (story 39) to register the joining client on the group's topic, and game session start (story 10) to register on the session's topic
- [ ] Frontend: keep the group/session WebSocket connection alive while navigating to other parts of the app, minimize the game to a small persistent widget instead of requiring the player stay on the game screen
- [ ] Frontend: turn notification, a sound plus a clickable visual banner when it's the player's turn and the game screen isn't focused, clicking either returns them to the game

Tests:
- [ ] Integration test: WebSocket connection and session state survive navigating away from the game route and back
- [ ] Integration test: turn notification fires when the player's turn starts while they're on a different route, and doesn't fire when they're already on the game screen
- [ ] Unit tests for the disconnect handler: flag flips without ending the group or session, for both a group member and an in-session player

## Story 12: Voice chat

Blocked on story 11 (WebSocket layer) and story 39 (group): voice is scoped to the group's lifetime, not the game session's, and its signaling rides the WebSocket layer, neither exists yet.

- [ ] Implement WebRTC signaling over the WebSocket layer built in story 11
- [ ] Implement mesh peer connection setup between group members
- [ ] Enforce the 8-participant cap per group
- [ ] Integrate Cloudflare TURN, pay-as-you-go, as the ICE server fallback
- [ ] Add join/leave voice UI, joinable and leavable at any time, not tied to starting a call
- [ ] Frontend: persistent, collapsible right-hand sidebar, vertically stacked circular avatars with names, speaking indicator ring, mute/deafen icon overlays, a trailing join-call button; visible with no speaking indicators when not in the call
- [ ] Frontend: leave animation on a participant departing, remaining avatars animate into the gap
- [ ] Frontend: sidebar stays available during story 11's minimized "playing while away" widget state

Tests:
- [ ] Unit tests for the 8-participant cap, including the boundary
- [ ] Integration test: TURN fallback engages when a direct peer connection fails
- [ ] Integration test: join/leave voice at arbitrary times, independent of whether a game session is active

## Story 13: Group-scoped text chat

Checked against real code: no chat model or endpoint exists. Blocked on story 11 (WebSocket layer) and story 39 (group): chat is scoped to the group's lifetime, not the game session's, and rides the WebSocket layer, neither exists yet.

- [ ] Implement `ChatMessage` as an ephemeral Postgres row (sender, group, body, timestamp)
- [ ] Client-to-server STOMP channel to send a message, riding the WebSocket layer built in story 11
- [ ] Broadcast new messages to the group's STOMP topic
- [ ] Load message history when a client joins or reconnects to a group
- [ ] Purge chat history when the group is deleted, matching the group's ephemeral lifecycle
- [ ] Message length limit (500 characters) and a per-user send rate limit (5 messages per 10 seconds) to prevent spam within a group
- [ ] Frontend: semi-transparent bottom-left overlay, toggled by a keybind or a clickable button, rather than a persistent input field, plain username-and-message lines, no threading (see `GAME_DESIGN.md`'s Interaction and animation section)

Tests:
- [ ] Unit tests for the message length limit and the per-user send rate limit, including the boundary values
- [ ] Integration test: message history loads correctly on join and on reconnect
- [ ] Integration test: chat history is gone once the group is deleted

## Story 39: Group

Checked against real code: no group model exists, this is greenfield work. Based on `ARCHITECTURE.md`'s Group shape and lifecycle, and `GAME_DESIGN.md`'s Groups section.

- [ ] Implement `Group` and `Member` as ephemeral Postgres rows; `Member` carries a per-group display name and avatar, separate from the user's account profile
- [ ] `POST` endpoint to create a group; creator becomes admin
- [ ] Enforce one active group membership per user
- [ ] Generate a unique 4-letter join code alongside the existing invite link when a group is created
- [ ] `POST` endpoint to join a group via invite link or join code, only while the group hasn't started a game session yet
- [ ] On join, prompt for a per-group display name and avatar, defaulting to the user's account values but editable; other members only ever see this per-group identity, never the account profile
- [ ] Settings (playlist(s), DJ mode, win-condition card count), editable by the admin only, broadcast to all members in real time
- [ ] Chat available from group creation, stored for the life of the group
- [ ] Voice joinable and leavable at any time (see story 12 for the WebRTC mechanics)
- [ ] 30-minute timer from group creation to the admin starting a game session, delete the group if it fires
- [ ] Admin action to start a game session (see story 10), locks the group to new members
- [ ] 30-minute timer from a game session ending to the admin starting another, delete the group and remove every member if it fires
- [ ] Explicit leave vs. disconnect: disconnect only flips `isConnected`, explicit leave removes membership
- [ ] Admin explicitly leaves: promote the next-earliest-joined member to admin, or delete the group if none remain
- [ ] Admin action to voluntarily promote another member to admin at any time, independent of leaving
- [ ] On app load, check the logged-in user's active group membership and prompt to return or leave, no link-based reconnect
- [ ] Frontend: visually mark the admin, a crown icon, distinct from regular members

Tests:
- [ ] Unit tests for join-code generation: uniqueness, and the 4-letter format
- [ ] Unit tests for the one-active-group-per-user constraint
- [ ] Unit tests for per-group profile isolation: a member's account profile is never exposed through group-scoped endpoints, only their per-group identity
- [ ] Unit tests for admin transfer: both the explicit-promote action and the auto-promote-on-leave path, including the no-members-remain deletion case
- [ ] Integration test: full group lifecycle, create, join via both invite link and join code, settings broadcast live, admin starts a session, group locks to new members
- [ ] Integration test: both 30-minute timers, pre-session and between-sessions, including that they don't fire early or fail to fire
- [ ] Integration test: explicit leave removes membership while disconnect only flips the connection flag

## Story 31: Dropped

Was: a "similar songs" feature using text embeddings over song title and artist. Dropped: the only version of "similar songs" worth building is audio-based (how a song actually sounds), not text-based (which mostly just catches same-artist or similarly-worded matches). See story 29 for why the audio-based version doesn't have a viable data source either.

## Story 29: Dropped, no viable audio-feature source found

Researched directly rather than left open: AcousticBrainz, the obvious free option, shut down its live API and submission pipeline in February 2022; only a frozen dataset remains, dated June 2022, with coverage skewed toward mainstream music already analyzed before the shutdown, exactly the opposite of the niche/underground coverage this project cares about. Self-hosting Essentia (the toolkit AcousticBrainz itself used) would work on any song, but needs the actual audio file, and the only way to get that for a YouTube-sourced song is unofficial downloading, which violates `CLAUDE.md`'s non-negotiable official-APIs-only rule and the DJ-link-out architecture built specifically to avoid touching YouTube's media stream. Paid catalog APIs (Apple Music at $99/year, various smaller commercial ones) are real ongoing cost for a nice-to-have feature and still don't reliably cover niche YouTube-only tracks. No option clears the bar. Dropped rather than left blocked indefinitely.

## Story 30: Difficulty-tuned, theme-aware game session generation

Redefined from a generic "collaborative filtering recommendations" idea into a concrete feature, and consolidates story 21 (auto-generated featured playlists) into it rather than keeping two stories doing adjacent "assemble a card set" work. Generates a game session's card set on the spot from a theme request, a difficulty tier (easy/medium/hard), or both together, scored against the actual players in the group, instead of only playing from an admin-picked playlist.

Two tiers for difficulty, so this works from day one rather than waiting months for enough data:
- A per-song aggregate difficulty score (percentage of all guesses on that song that were correct, across everyone) works immediately, even with a handful of plays per song, and covers first-time players with no personal history.
- A personalized layer (collaborative filtering: for a given player and song, predict correct-or-not and roughly how fast, learned from patterns across all players and songs, same technique Netflix-style recommenders use, applied to interaction outcomes instead of ratings) only adds value once there's enough per-player history to beat the aggregate baseline. Depends on story 10 shipping and real rounds accumulating; realistically months of casual play before the personalized layer clearly outperforms the simple aggregate at this project's 100-200 user scale, see `PROJECT_STATE.md`.

Inference is cheap and local: scoring the whole catalog against a specific group's players is a small numeric comparison per song, no external API call, runs in well under a second even for a full catalog, unlike the metadata pipeline which costs money per call. The only real cost is periodic retraining, a scheduled batch job, cheap at this data scale.

Theme side, from story 21: depends on story 14's catalog search existing, the agent needs to query the catalog by theme/keyword. What "validated" means for a theme-generated set depends on story 18's verification criteria, currently undecided, don't invent a threshold; for now, generated sets draw from verified songs only, same as any other selection.

- [ ] Add a `SongDifficulty` aggregate view or table: per-song correct-guess percentage across all historical guesses, updated as new rounds complete
- [ ] Add group-level difficulty scoring for "easy": the lowest individual predicted score among the group's actual players, not the average, so the least experienced player is protected rather than left behind by a group average that looks easy on paper
- [ ] Add group-level difficulty scoring for "hard": a plain average across the group's players, no floor to protect, opt-in past the easy default
- [ ] Decide and add group-level difficulty scoring for "medium": `DECISIONS.md`'s story 30 entry only settles easy (lowest individual score) and hard (plain average); medium's formula isn't decided, don't assume it matches hard's
- [ ] Add genre/popularity fields to `Song` if story 23's reconciliation doesn't already cover them, today's `Song` has no genre field, only a single `songTag` enum, needed for theme matching
- [ ] Build the theme-matching flow: theme request → catalog search (story 14) → metadata pipeline calls to fill any gaps in genre/popularity data for candidate songs
- [ ] Add the on-the-spot generation endpoint: given a group, an optional theme, an optional difficulty tier, and a target card count, score the full verified catalog for the group's actual players (blending personalized predictions where available with the aggregate baseline for first-time players), filter to whichever criteria were given, return enough songs with headroom above the win-condition card count so a session doesn't run out or repeat
- [ ] Train the personalized collaborative-filtering model on accumulated `Guess` data (story 10) once there's enough of it to evaluate
- [ ] Add a scheduled retraining job for the personalized model
- [ ] Add a monitoring check comparing the personalized model's prediction accuracy against the simple aggregate baseline; if the personalized model stops beating the baseline, that's the signal it's stale and needs retraining, not just a fixed schedule
- [ ] Add the frontend: a theme request field and a difficulty selector (easy/medium/hard), either or both, plus a review step to inspect and confirm the generated set before saving

Tests:
- [ ] Unit tests for the aggregate difficulty score calculation
- [ ] Unit tests for both group-scoring strategies (worst-case-protected for easy, average for hard), including groups with a mix of experienced and first-time players
- [ ] Unit tests for the personalized model's predictions against a held-out set of real guesses
- [ ] Integration test: on-the-spot generation for a full-sized group (up to 8 players) returns a scored, filtered card set in well under a second, for theme, difficulty, and both together
- [ ] Integration test: the retraining job runs and the monitoring check correctly flags a model that's stopped beating the baseline
- [ ] Frontend test: the review UI lets a user inspect and confirm the generated set before saving

## Story 33: Analytics data store

- [ ] Choose and provision a separate append-heavy store for usage/event data, apart from the transactional Postgres database (a separate schema, or a dedicated event/time-series store)
- [ ] Define the event schema: game session start/end (with a compact per-game summary, group, players, win/loss, cards won, final score, for story 34's game history feature), login, playlist created, song submitted, rate-limit-exceeded (user, endpoint), report submitted, failed login attempt
- [ ] Decide a retention policy

Tests:
- [ ] Integration test: an event write to the new store doesn't touch or block the transactional database
- [ ] Unit test for the retention policy's cleanup logic

## Story 34: First-party usage analytics

Depends on story 33's store existing, and also on the events it instruments actually existing: story 10 (game session, no `GameSession` model exists yet), story 17 (reports, no `SongReport` entity exists yet), and story 27 (rate limiting, only a narrow one-in-flight-request-per-user concurrency gate exists today on `/api/metadata/song`, not the general per-user/per-IP time-window limiter this depends on for login/register or other endpoints). Login and playlist-creation events can be instrumented once story 33 lands, independent of the others. Event scope is deliberately count/aggregate-based, not behavioral click-tracking: usage stats for the project's own understanding (games played, session length, playlists created, songs submitted, login activity), and abuse-visibility signals that turn existing enforcement into something reviewable (rate-limit-exceeded events from stories 13/27, report submissions from story 17, failed login attempts), not a new detection mechanism of its own.

- [ ] Instrument game session start/end (with the per-game summary), login, playlist creation, and song submission events to write to the analytics store; the game-session half depends on story 10, the rest can start once story 33 lands
- [ ] Instrument rate-limit-exceeded, report-submitted, and failed-login-attempt events, for abuse visibility, not enforcement; depends on stories 13/27/17 actually shipping their enforcement first, none of which exist yet
- [ ] Build a simple internal dashboard or query surface over the collected events, including a simple way to flag a user who's crossed a rate-limit or report threshold repeatedly
- [ ] Build a per-user game history page in the frontend, querying the current user's own game-summary events from the analytics store; the transactional `GameSession`/`Round`/`Guess` rows still purge exactly as story 10 already specifies, this reads only from the separate analytics store
- [ ] No third-party trackers, matches this story's own scope and the "First-party usage analytics" framing

Tests:
- [ ] Integration test: each instrumented event type produces the expected record in the analytics store
- [ ] Integration test: the dashboard/query surface returns correct aggregates for known event data
- [ ] Integration test: a user's game history page returns only their own game summaries, not other users'

## Story 15: Song/playlist relational fix

Checked against real code: `Song.playlist` is a required singular `@ManyToOne`, one song belongs to exactly one playlist today. Touches the same table as story 23; sequencing or combining the two migrations avoids two separate schema changes to `Song`.

- [ ] Introduce a join table between `Song` and `Playlist`, replacing the singular `@ManyToOne playlist` on `Song`
- [ ] Rewrite `Playlist.songs`'s `@OneToMany(mappedBy = "playlist", cascade = CascadeType.ALL, orphanRemoval = true)` relation and its `addSong`/`removeSong` helpers, both of which assume the singular back-reference (`song.setPlaylist(this)`/`song.setPlaylist(null)`) that a join table removes
- [ ] Migrate existing data: each song's current single playlist link becomes one row in the new join table
- [ ] Update `PlaylistService`'s `checkSongBelongsToPlaylist`, which currently assumes one song belongs to exactly one playlist; `checkPlaylistAccess` doesn't need changing, it checks playlist-user membership and doesn't touch the song relation
- [ ] Decide song deletion semantics once a song isn't playlist-exclusive: does removing a song from one playlist delete it outright, or only unlink it? `PlaylistController`'s current delete-song endpoint, via `Playlist.removeSong` and `orphanRemoval = true`, does a real delete today
- [ ] Update `SongDTO`/`PlaylistDetailDTO`, `PlaylistMapper.toDetailDTO` (the code path that assembles a playlist's song list), and the frontend to reflect a song appearing in multiple playlists
- [ ] Coordinate with story 23 (schema reconciliation), both touch `Song`'s shape

Tests:
- [ ] Unit tests for `checkSongBelongsToPlaylist` against the new many-to-many relation, plus a regression check that `checkPlaylistAccess` is unaffected
- [ ] Integration test: migrating existing data preserves each song's original playlist link
- [ ] Integration test: a song in multiple playlists behaves correctly for access checks and the decided deletion semantics

## Story 14: Song search by link or keyword before submission

Checked against real code: `SongRepository` has zero custom query methods, no backend search capability exists. The only "search" today is `DataTable`'s client-side substring filter over an already-loaded playlist's songs, not a real query.

- [ ] Add a backend search endpoint, `SongRepository` has no query methods to build on today
- [ ] Support search by artist/title keyword and by YouTube link/ID (the link-parsing logic already exists client-side as `extractYoutubeId` in `AddSongForm.tsx`, currently not shared with the backend)
- [ ] Decide search scope: within one playlist, across the user's playlists, or catalog-wide, affects both the query and which of `PlaylistService`'s access checks apply (catalog-wide search would need one, since it isn't a per-playlist access check)
- [ ] Wire `AddSongForm.tsx`'s submission flow to check search results first, so a song already in the catalog isn't resubmitted as a near-duplicate (distinct from story 16's pgvector-based similarity check; this is a plain keyword/link pre-check)
- [ ] Add the frontend search UI, replacing or extending the current client-side-only title filter in `DataTable`

Tests:
- [ ] Unit tests for the search query: keyword matching and YouTube link/ID matching
- [ ] Integration test: search results respect the chosen scope's access checks
- [ ] Frontend test: the search UI returns and displays results correctly

## Story 16: pgvector-based duplicate detection

Checked against real code: no pgvector dependency in `pom.xml`, no vector-DB client or embedding code anywhere in `ai/app`, this is greenfield on both services. Based on `ARCHITECTURE.md`'s RAG/dedup section (line 127-129): normalize `artist + title`, embed, check similarity before running the full pipeline, reuse existing data on a high-confidence match.

- [ ] Enable the pgvector Postgres extension (coordinate with story 8/23 if a migration tool lands around the same time)
- [ ] Add an embedding step to the AI microservice: normalize `artist + title`, generate an embedding via OpenAI's embeddings API, no embedding client exists in `ai/app` today
- [ ] Store embeddings for verified songs
- [ ] Add a similarity-check step before the source fetch/LLM synthesis in `metadata/service.py`'s `resolve_metadata`, reuse existing data on a high-confidence match instead of re-running the pipeline
- [ ] Decide and document the similarity threshold for "high-confidence match", flagged as still unresolved in `ARCHITECTURE.md`
- [ ] Coordinate with story 15 if dedup needs to consider a song already existing under a different playlist relationship

Tests:
- [ ] Unit tests for the similarity-check step (mocked embedding client): a high-confidence match reuses existing data, a low-confidence match proceeds to the full pipeline
- [ ] Integration test: submitting a near-duplicate song reuses existing verified data instead of re-running the LLM

## Story 19: Admin bulk song import

Checked against real code: `User.role` only has a `USER` value today, no `ADMIN` value or admin-only access check exists anywhere in the system. This is a prerequisite for this story, not something to assume already exists.

- [ ] Add an `ADMIN` value to `User.role` and an admin-only access check, neither exists today
- [ ] Add a bulk-import endpoint (CSV or JSON) that runs each entry through the existing metadata pipeline
- [ ] Add a progress/result summary for a bulk import run, since a large batch calling the AI microservice per row takes time and can partially fail
- [ ] Add the admin-only import UI

Tests:
- [ ] Unit tests for the admin-only access check, including a non-admin request rejected
- [ ] Integration test: bulk import processes multiple rows and reports per-row success/failure

## Story 17: Community song reports

Depends on story 19 for the admin review surface, and references story 18's still-undecided verification criteria without depending on its implementation timeline.

- [ ] Add a `SongReport` entity (reporter, song, message, suggested correct year, sources, status)
- [ ] `POST` endpoint to submit a report, available to any authenticated user who can view the song
- [ ] Add a report button to the song detail page (`SongForm.tsx`), which has no report affordance today
- [ ] Admin review surface to see open reports (needs story 19's admin role)
- [ ] What a submitted report should do to `verificationStatus` is a story 18 dependency, currently undecided, don't invent behavior here

Tests:
- [ ] Unit tests for `SongReport` validation
- [ ] Integration test: submitting a report end to end, visible on the admin review surface

## Story 18: Criteria for promoting a reported or newly submitted song to verified

Still an open design question (see `PROJECT_STATE.md`'s open questions): whether verification is confidence-threshold-based, manual admin review, some combination, or something else isn't decided. No tasks drafted here, writing implementation tasks now would mean inventing the undecided design itself rather than reflecting a real decision. Stories 17, 19, 23, and 32 all reference this story's eventual outcome without depending on its implementation timeline.

## Story 32: LLM-as-judge catalog audit

Whether this feeds into story 18's verification criteria or stays a separate audit tool is still an open question (`PROJECT_STATE.md`); built here as a standalone flagging tool, integration with verification is a later decision.

- [ ] Add a scheduled job runner: no `@Scheduled` usage or scheduling dependency exists anywhere in the backend today, `@EnableScheduling` isn't declared
- [ ] Add a periodic pass over the catalog, calling the AI microservice with an LLM-as-judge prompt to flag likely duplicate or mislabeled entries; the AI microservice has only one route today (`POST /metadata/resolve`, single YouTube URL in, one song's metadata out), so this needs a brand new batch/judge endpoint, not an extension of the existing one
- [ ] Surface flagged results on a reviewable surface (needs story 19's admin role)

Tests:
- [ ] Unit tests for the flagging logic (mocked LLM call)
- [ ] Integration test: the scheduled job runs and produces flagged results on the review surface

## Story 24: Parallelize metadata pipeline fetches across sources

Checked against real code: `_gather_all_metadata` calls its sources sequentially with plain synchronous `httpx.get`, no `asyncio.gather`, no `httpx.AsyncClient`, no thread pool anywhere in the pipeline.

- [ ] Convert the source fetch functions to async, using `httpx.AsyncClient`
- [ ] Run the parallel-eligible sources concurrently with `asyncio.gather` in `_gather_all_metadata`
- [ ] Parallelizing today's stubbed MusicBrainz/Wikipedia/Genius calls is wasted work, since the resolved source set is MusicBrainz, Discogs, and Wikidata (`PROJECT_STATE.md`); wait until the real three sources are actually implemented (Discogs via story 25, MusicBrainz un-stubbed, Wikidata built, none done yet) before parallelizing, rather than the current four stubbed/live sources
- [ ] Add a per-source timeout so one slow source doesn't block the whole gather

Tests:
- [ ] Unit test confirming sources are fetched concurrently, not sequentially (mock call-order/timing assertion)
- [ ] Unit test for the per-source timeout: a slow source doesn't block the others

## Story 25: Add Discogs as a metadata source

Checked against real code: `sources/musicbrainz.py`, `wikipedia.py`, and `genius.py` are stubs returning empty results, each commented with a reference to the 2026-08 pause decision. No `discogs.py` or `wikidata.py` file exists. The resolved source set is MusicBrainz, Discogs, and Wikidata (`PROJECT_STATE.md`), settled, not open. This story covers Discogs only: un-stubbing MusicBrainz and building Wikidata still need their own design pass (how the pipeline should actually shape those calls) before tasks can be drafted for them, not yet decided, so they're left untasked here rather than folded into this story's scope or guessed at.

- [ ] Add `sources/discogs.py`, following `sources/youtube.py`'s pattern (the only currently-live source) for HTTP client usage, timeout, and broad-exception-to-`UNKNOWN_DEFAULTS` fallback
- [ ] Add the Discogs API token to AI service config (`config.py`), following the existing `youtube_api_key`/`openai_api_key` pattern
- [ ] Wire Discogs into `_gather_all_metadata` and add a `_append_discogs_data` function in `prompt.py`, matching the existing per-source prompt-section pattern
- [ ] Confirm Discogs' API terms of use permit this usage, matching the review MusicBrainz and Wikidata already got per `PROJECT_STATE.md`

Tests:
- [ ] Unit tests for `discogs.py`'s request building and response parsing, mirroring `youtube.py`'s existing test pattern
- [ ] Unit test for the fallback behavior on a failed Discogs call

## Spike: MusicBrainz and Wikidata sourcing

Handoff item 1. Story 25 (Discogs) is separate and already `Ready`. MusicBrainz is a settled source, paused pending this review (`DECISIONS.md`'s 2026-08 entries); Wikidata has no source file yet. Needs real experimentation against real submitted songs before writing an implementation task list, not a guess at the integration shape.

- [x] Query MusicBrainz's live API against real submitted songs (42 total across a hand-picked mainstream/mid/niche/Romanian set and a real 34-song YouTube playlist): confirmed the release-group `first-release-date` field is reachable and correct, but only after also querying the parent album's release-group and taking the earliest of the two, a track-only query missed the true original date repeatedly (a niche track resolved to a 2019 reissue instead of 2011; 4 of 5 mismatches found against the real playlist were MusicBrainz alone disagreeing with Discogs/Wikidata/the video's own metadata, all consistent with this same track-only gap)
- [x] Query Wikidata's API against the same songs, decided: the `wbsearchentities`/`wbgetentities` REST actions, not SPARQL. Confirmed findings: search on the title alone (a combined "artist title" query returns nothing); `wbsearchentities` needs a wide result window, not just the top few, a common title can bury the real song 6+ results down (Katy Perry's "Dark Horse" ranked behind a Nickelback album, an unrelated film, and a restaurant); never fall back to an unrelated top-ranked result when nothing in the pool matches the artist, prefer no match over a wrong one; a song's own `P361` ("part of") claim can resolve its parent album directly, without needing to know or guess the album's title
- [x] Evaluate Apple's iTunes Search API as a candidate additional source: terms-of-use read done, rejected without live testing, its license is promotional/affiliate-only, see `DECISIONS.md`'s 2026-09 entry
- [x] Test multiple query-phrasing strategies per source: confirmed titles with a `(feat. X)`/`(ft. X)` clause return zero MusicBrainz and Wikidata matches until that clause is stripped (Discogs' search tolerates it fine as-is); confirmed native-script (non-romanized) titles work correctly when typed accurately, a niche track initially tested under a romanized guess found nothing, the same track under its real stylized title resolved correctly
- [x] Confirmed Discogs needs the same "check every candidate, take the earliest" treatment already applied to MusicBrainz: a track can belong to more than one distinct master (its own standalone-single release and the album it also appears on), each with its own year, trusting whichever master a search result lists first picked a 2015 remix single over the true 2014 album original for a real playlist song ("Hey Mama")
- [x] Fixed: Discogs' master resource uses `year: 0`, not null, for an unknown year (live-tested on "Titanium"), now treated as unknown rather than a literal date
- [x] Fixed: Wikidata's documented anonymous rate limit is 10 requests/minute, not the ~2/second this had been running at; a descriptive User-Agent isn't confirmed to buy the more lenient browser-identified tier, paced to the stricter number
- [x] Extract featured artists as a structured list (not just a stripped clause) when a title has a `(feat. X, Y & Z)` clause, feeds story 23's open question on multi-artist storage; verified against real playlist titles including mixed comma-and-ampersand lists and hyphenated names
- [ ] New finding, not yet a task with a decided shape: a video's title and channel name don't always carry the real artist at all. Live-tested on a real Jujutsu Kaisen opening, title and channel (the licensor, "Crunchyroll") both omit the artist entirely, only the video's description states it ("... by Who-ya Extended"). Today's pipeline only reads title and channel (`youtube.py`, `clean_youtube_text`); needs a description-parsing fallback for when the channel doesn't look like a real artist (a known licensor/broadcaster channel, not a music channel), decide the shape before implementing
- [ ] Decide how the pipeline should call and reconcile MusicBrainz, Wikidata, and (if adopted) iTunes results against each other and against the existing YouTube/Discogs sources
- [ ] Build `ai/app/metadata/sources/wikidata.py` per the decided shape, following `youtube.py`'s pattern for HTTP client usage, timeout, and broad-exception-to-`UNKNOWN_DEFAULTS` fallback
- [ ] Un-stub `ai/app/metadata/sources/musicbrainz.py` per the decided shape
- [ ] Wire both into `service.py`'s `_gather_all_metadata` and add matching `_append_musicbrainz_data`/`_append_wikidata_data` sections in `prompt.py` (the MusicBrainz one already exists and may need adjusting for the decided shape)

Tests:
- [ ] Unit tests for `musicbrainz.py` and `wikidata.py`'s request building, response parsing, and fallback behavior, mirroring `youtube.py`'s existing test pattern

## Story 26: Cache metadata pipeline results by artist/title or YouTube ID

- [ ] Add a cache layer in front of `resolve_metadata`, no cache exists today, every call re-runs the full source-fetch and LLM pipeline
- [ ] Decide cache backend: in-memory (simple, doesn't survive restarts or share across multiple AI service workers) vs. Redis/Postgres-backed
- [ ] Set a TTL or invalidation policy, metadata for a given YouTube ID rarely changes, but upstream source data can be corrected
- [ ] Coordinate with story 16: a pgvector similarity hit and a plain cache hit solve overlapping but different problems (near-duplicate vs. exact-repeat lookups), avoid building two redundant caching layers

Tests:
- [ ] Unit tests for cache hit/miss behavior
- [ ] Unit test for TTL expiration

## Story 20: Local LLM option for lower-cost bulk metadata processing

Checked against real code: `ai/app/clients/openai_client.py` is the only LLM client, a module-level `OpenAI` singleton, no other client exists. Which local model or technique to use is undecided, and stays undecided until a separate exploration pass, on its own branch, tests structured-output support and accuracy against real cases first. No implementation tasks drafted here yet, same treatment as the pipeline-gathering questions: deciding now would mean guessing at a model choice instead of testing it.

## Story 23: Song schema reconciliation

Checked against real code and `ARCHITECTURE.md`'s target shape (line 36): `Song` today has a single `releaseYear` int, a single `songTag` enum, a single `artist` string, and no `verificationStatus`, `confidence`, or `metadataRaw` fields. No migration tool exists yet, schema changes today happen only through Hibernate's `ddl-auto=update`; this story introduces Flyway rather than add another layer of auto-DDL.

Two parts of the target shape are genuinely undecided, not just unconfirmed against code, so this story doesn't cover them yet:
- Whether release year should be two fields (`submittedYear`, immutable, and `verifiedYear`, null until verification) or one mutable field plus `verificationStatus`. The two-field version preserves what was originally submitted even after a correction, useful for auditing bad sources over time, closer in spirit to why `metadataRaw` exists at all. The one-field version is simpler. Neither is chosen.
- How multiple artists are stored and guessed. A song can have a main artist plus one or more featured artists (for example, an "artist A feat. artist B" credit); today's single `artist` string can't represent that, and it's undecided whether featured artists need to be guessed correctly too for a round to count as correct, whether storage should be an array of artist entries, and what the guess-box UI looks like for more than one artist (multiple text boxes, or something else). Affects story 10's artist/title guess box, this story's schema, and the AI microservice's extraction logic, none of which assume multiple artists today.

- [ ] Introduce Flyway as the schema migration tool
- [ ] Add a `verificationStatus` field, `UNVERIFIED`/`VERIFIED` as a placeholder pair pending story 18
- [ ] Persist `confidence` on `Song`, depends on the `SongMetadataResponse` fix in this file's Bug fixes section existing first
- [ ] Persist `metadataRaw`, the full pipeline output, for auditability
- [ ] Replace the single `songTag` enum with a multi-value `tags` relation, and update `SongMapper`'s default-to-`NONE` behavior in `toDTO`/`toEntity`, which won't map cleanly onto "no tags" vs. a tag literally named `NONE` once it's a collection
- [ ] Data migration for existing rows: default `verificationStatus`
- [ ] Update `SongDTO`, `CreateSongRequest`, `UpdateSongRequest`, `SongMapper`, and regenerate the frontend's orval client and song forms for the new shape

Tests:
- [ ] Unit tests for the data migration: `verificationStatus` defaulted correctly for existing rows
- [ ] Integration test: existing API responses (`SongDTO`) don't break for rows migrated from the old shape

## Bug fixes

No story required for these. Fix on a `fix` branch.

- [ ] `SongMetadataResponse` (Java) silently drops the AI microservice's `confidence`, `source`, and `reasoning` fields: `SongMetadataResult` (Python) computes and returns all three today, but the Java record deserializing that response only declares `title/artist/releaseYear/gradientColor1/gradientColor2`, so the other three are read off the wire and discarded on every metadata call. Extend the record to keep them.
- [ ] `DELETE /me` (`UserService.deleteUser()`) throws an unhandled `DataIntegrityViolationException` for any user who has ever added a song: `Song.addedBy` (`Song.java:41-43`) is a non-nullable `@ManyToOne` with no inverse mapping on `User` and no cascade rule, so the FK Hibernate generates under `ddl-auto=update` has no `ON DELETE` clause. It also orphans a playlist when the deleting user is its last remaining member: unlike `leavePlaylist()` (`UserService.java`), which deletes a playlist once `getUserCount() == 0`, `deleteUser()` has no equivalent check.

## Story 22: Test coverage

Checked against real code: the backend has exactly one test file, an empty `contextLoads()` smoke test, zero controller/service/security coverage. The AI microservice has unit tests only for pure functions (`llm.synthesize`, `prompt.build`, `sources/util.py` helpers), nothing for `router.py`, `service.py`'s orchestration, or `auth.py`. The frontend has no test runner installed at all. `.github/workflows/pr-checks.yml` runs `mvnw compile` and `npm run lint && npm run build`, no test execution step for either service, and no job at all for the AI microservice, so even its existing pytest tests never run in CI today.

- [ ] Add a CI job for the AI microservice (none exists today) running its existing `pytest` suite
- [ ] Add a `mvnw test` step to the backend CI job (currently compile-only)
- [ ] Add JUnit/Mockito tests for every backend service (`PlaylistService`, `SongMetadataService`, `UserService`, `AuthService`, `ExportService`), covering the access-control checks in `PlaylistService`, the rate limiter in `SongMetadataService`, and the account-enumeration-avoidance logic in `AuthService`
- [ ] Add `@WebMvcTest`/MockMvc tests for every controller
- [ ] Add a Spring Security test covering JWT auth, refresh-token rotation, and CSRF
- [ ] Add tests for `ai/app/metadata/router.py`, `service.py`'s orchestration, and `auth.py`'s internal-key check, using FastAPI's `TestClient`
- [ ] Add a frontend unit test runner (Vitest or Jest, neither installed today) plus React Testing Library, and a `test` script in `package.json`
- [ ] Add frontend unit tests for the song forms' hand-written validation (`AddSongForm.tsx`, `SongForm.tsx`) and the auth forms
- [ ] Add Playwright for frontend integration/end-to-end tests, none exist today; separate from the unit test runner above, drives the real browser against the real backend rather than mocking it
- [ ] Add Playwright coverage for the core flows that exist today: login/register, playlist CRUD, song add/edit, export
- [ ] Add the new test steps to `.github/workflows/pr-checks.yml` for all three services

## Story 27: Rate limiting

Checked against real code: the only rate limiting anywhere is `SongMetadataService`'s single in-flight-request-per-user gate on `/api/metadata/song`, a `ConcurrentHashMap`-backed set, not a time-window limiter. No rate-limiting library (Bucket4j, resilience4j) exists in `pom.xml`. `/auth/login` and `/auth/register` have no rate limiting at all today.

- [ ] Add a rate-limiting library (Bucket4j is the standard Spring choice) to `pom.xml`
- [ ] Add per-user or per-IP request-window rate limits across public-facing endpoints, not just the existing single in-flight gate
- [ ] Rate-limit `/auth/login` and `/auth/register` specifically, to blunt credential-stuffing and enumeration attempts
- [ ] Standardize the 429 response shape; the metadata endpoint's current 429 uses Spring's default `ProblemDetail`, not the app's own `ErrorResponse` record used elsewhere in `GlobalExceptionHandler`
- [ ] Rate-limit the AI microservice's `/metadata/resolve` endpoint directly, not just the core service's call into it, since anything holding the shared `X-Internal-Api-Key` secret can call it directly

Tests:
- [ ] Unit tests for the rate limiter: requests under the limit pass, requests over the limit get rejected, including the boundary value
- [ ] Integration test: `/auth/login` and `/auth/register` rate limiting specifically
- [ ] Integration test: the AI microservice's `/metadata/resolve` rate limit triggers independent of the core service's own limiting

## Story 36: Open-source collaboration readiness

- [ ] Add `CONTRIBUTING.md`: local dev setup (`make dev`), the branch/PR workflow already defined in `CLAUDE.md` written for an external audience, how to pick up a story from `TASKS.md`
- [x] Add `LICENSE`: MIT, chosen over AGPLv3/BSL since there's no revenue or scale to protect, and MIT is the stronger signal for a portfolio project, zero friction for anyone evaluating the code
- [ ] Add `CODE_OF_CONDUCT.md`
- [ ] Add GitHub issue templates (bug report, feature request) and a PR template matching the repo's actual PR description style (plain prose, no `## Summary`/`## Test plan`, see `CLAUDE.md`'s writing-style rules)
- [ ] Document which secrets a new contributor needs (`YOUTUBE_API_KEY`, `OPENAI_API_KEY`, `INTERNAL_SERVICE_API_KEY`) and how they get sandbox-safe values, since both external API keys carry real cost/quota implications

## Story 37: Privacy policy, terms of service, and GDPR compliance

Checked against real code: `DELETE /me` (`UserController` → `UserService.deleteUser()`) already does a real hard delete of the `User` row, not a deactivation. It's not just unaudited for `Song.addedBy` references and shared playlists, both are confirmed live bugs, see this file's Bug fixes section. No analytics exist yet (story 34), so there's nothing to disclose there until it ships.

- [ ] Draft a privacy policy covering what's actually collected today: auth data (username, email, OAuth provider ID), playlist/song data
- [ ] Draft terms of service
- [ ] Add a GDPR data-export endpoint: a logged-in user can download their own account, playlist, and song data; `ExportController`/`ExportService` exist today but export playlist/song content, not a full personal-data dump, don't assume they already cover this
- [ ] Fix the `DELETE /me` bug in this file's Bug fixes section (FK violation on `Song.addedBy`, orphaned playlists on last-member deletion), then confirm no other edge case leaves orphaned references or unexpectedly deletes other members' shared playlists
- [ ] Add a cookie/consent notice, only needed once story 34 (first-party analytics) ships; skip until then since no third-party trackers are planned

Tests:
- [ ] Integration test: GDPR export endpoint returns the user's complete account, playlist, and song data
- [ ] Integration test: `DELETE /me` with existing `Song.addedBy` references and shared-playlist memberships behaves per the decided handling, no orphaned references, no other member's playlist unexpectedly deleted

## Story 38: Observability

Checked against real code: no Spring Boot Actuator dependency exists in `pom.xml`, no health-check endpoint exists today. The backend already uses SLF4J logging (from the story-6-era audit fixes), but there's no request-id/correlation-id to trace one user action across both services. The AI microservice swallows every pipeline and OpenAI failure into a generic `status="ERROR"` response with no alerting.

Goes deeper than a minimal setup, deliberately: metrics, logs, and traces together (Prometheus, Loki, Tempo), not just health checks and error tracking. All consumed through Grafana Cloud's free tier rather than self-hosted, self-hosting any of these means an always-on VM that doesn't fit the project's whole-deployment cost ceiling (see `DECISIONS.md`), while the free tier covers this project's scale at $0. Instrumentation itself is OpenTelemetry, the vendor-neutral standard, so nothing here locks the project into Grafana Cloud specifically.

- [ ] Add Spring Boot Actuator to the core service for health/metrics endpoints, expose `/actuator/prometheus`
- [ ] Add an equivalent health endpoint to the AI microservice (FastAPI has none today), expose metrics via `prometheus-fastapi-instrumentator`
- [ ] Set up a Grafana Cloud free-tier account, point both services' Prometheus metrics at it
- [ ] Add OpenTelemetry auto-instrumentation to both services for distributed tracing, viewable in Grafana Cloud's Tempo
- [ ] Ship both services' structured logs to Grafana Cloud's Loki
- [ ] Add error tracking (Sentry, free tier) to both services
- [ ] Add a request-id/correlation-id filter so one user action can be traced across both services' logs, and correlates with the OpenTelemetry trace for the same request
- [ ] Build a basic Grafana dashboard: request rate, error rate, latency percentiles for both services
- [ ] Add uptime monitoring for the production deployment
- [ ] Surface the AI microservice's per-source fetch failures and OpenAI call failures as visible alerts, rather than only the generic swallowed `status="ERROR"` response
- [ ] Add a periodic check against Grafana Cloud's and Sentry's free-tier usage limits, so approaching them is noticed before either starts silently dropping data or asking for payment

Tests:
- [ ] Integration test: Actuator health endpoint reports correctly both when healthy and when a dependency (the database) is down
- [ ] Integration test: a request-id set on an incoming request propagates through a core-service-to-AI-service call, appears in both services' logs, and correlates with a single OpenTelemetry trace
- [ ] Integration test: a metrics scrape and a log line both actually reach Grafana Cloud in a real (non-mocked) call

## Story 35: Public ground-truth data API

The final YouTube-terms confirmation read stays an open question (`PROJECT_STATE.md`), kept open deliberately; the build itself isn't blocked on it since the story's actual output data doesn't include anything YouTube-sourced, so it's placed as the last task before shipping rather than before starting.

- [ ] Add a public read-only endpoint exposing verified `(artist, title, release_year)` triples only, no YouTube-sourced fields
- [ ] Filter to verified songs only, depends on story 23's `verificationStatus` field existing
- [ ] Add pagination and rate limiting for public consumption (coordinate with story 27)
- [ ] Final confirmation read of YouTube's terms before shipping, since the catalog's overall provenance mixes sources even though this endpoint's own data doesn't include anything YouTube-sourced

Tests:
- [ ] Integration test: the endpoint returns only verified songs, unverified songs never appear
- [ ] Integration test: no YouTube-sourced field (`youtubeId` or anything derived from it) appears in the response shape
- [ ] Unit tests for pagination and the rate limit, including boundary values
