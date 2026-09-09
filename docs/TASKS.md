# TASKS.md: What To Actually Work On

This is the source of truth for day-to-day work. Consult PROJECT_STATE.md only when you need the bigger picture behind one of these.

The tasks below, under stories 9 and 12, are drafts and have not yet been confirmed against the real implementation, except where noted. Before starting any of them, check them against the current code: some tasks may already be done, some may not apply the way they're written, and some may be missing. Once a story's tasks are confirmed accurate, update its status to Ready in PROJECT_STATE.md.

Stories 10, 11, and 39 were checked against the real code: no `Group`, `Session`, `Round`, `Guess`, or WebSocket/STOMP code exists anywhere in the backend, so their draft tasks stand as accurate greenfield work. Marked Ready in PROJECT_STATE.md.

Story 9 and story 12 were checked against the real code and confirmed blocked: both assume a group (story 39), a game session (story 10), and a WebSocket layer (story 11) that don't exist yet. Neither can move to Ready until 10, 11, and 39 do.

"Next available task" means the earliest unchecked box under a Ready or In Progress story.

## Chore: Backlog refinement from the project owner's specification

A written specification from the project owner added new cross-cutting architecture stories, corrected several existing stories, and called for a documentation cleanup pass. Split across separate branches below since it touches unrelated parts of the backlog; each is checked off once its PR merges.

- [x] Cross-cutting: new stories for the database split (42), metadata minimization (43), and test user infrastructure (44); the access-token reissuance bug fix; a rate-limit stress-testing task on story 27; a legal-page scope clarification on story 37
- [ ] Story 9: DJ-controlled reveal flow, audio cutoff sequence, audio-sharing UI warning, and its missing test tasks
- [ ] Stories 10 and 11: dedupe the active/non-active player token and leaderboard tasks, add story 11's telemetry requirement
- [ ] Stories 24 and 25: full task rewrite against current rate limits, architecture, and decisions
- [ ] Stories 26, 30, and 40: caching-layer scope review, playlist-selection cut down to two modes, dedup pipeline's exact-match linking decided
- [ ] Backlog cleanup: remove dropped/consolidated stories from the active tables, audit for conflicting decisions across docs
- [ ] Implementation roadmap document, explicit sequential order for remaining stories
- [ ] Structured system docs: API contracts, entity model, state diagrams
- [ ] Frontend content specifications, independent of story 28's visual design

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
- [ ] Artist/title guess box, available to the active player for the whole turn, independent of timeline placement; a fully correct guess awards a token, matching normalizes both strings (lowercase, strip punctuation, strip diacritics, collapse whitespace) and compares them with Damerau-Levenshtein edit distance, a flat budget of 1 regardless of length (see `DECISIONS.md`). For a song with more than one artist (main or featured, story 23), naming any single one of them correctly is enough, not all of them
- [ ] Scoring: apply the four outcome rules in `GAME_DESIGN.md` (correct placement keeps the card even on a tied release year; a correct guess beats any bet; a wrong guess with a correct bet gives the card to the bettor; a wrong guess with no bet discards it)
- [ ] Extend the artist/title guess box to every player except the DJ, not just the active player; a non-active player's guess never earns a token or affects placement/betting, it only counts toward the two session-long leaderboards below
- [ ] Track two running per-player tallies for the session: total individual artists correctly named (every correct name, main or featured, from any song, adds one, regardless of how many total artists that song has) and total fully-correct title guesses
- [ ] Win condition: first player to reach the group's configured card count wins, bounded 5-20 for a 2-3 player group or 5-15 for a 4-8 player group
- [ ] Player disconnect: mark `isConnected` false, leave timeline/tokens/turn order untouched
- [ ] Player explicit leave: mark `Left`, exclude from future turns and DJ rotation, existing timeline cards still count toward the final results
- [ ] Active-player turn timeout: if the active player is disconnected when their turn comes, or disconnects mid-turn, auto-skip after 90 seconds and mark them `Left`
- [ ] Auto-abandon the session after 10 minutes with zero connected players, no results export in that case
- [ ] Downloadable results export when a session completes normally, including the main card-count ranking and the two separate "Most Artists Guessed"/"Most Titles Guessed" leaderboards
- [ ] Purge all session state (roster, rounds, guesses) once the session ends or is abandoned, hand control back to the group
- [ ] Frontend: drag-and-drop timeline placement, cards animate apart to open a gap with no overlap, animate back into place once placed
- [ ] Frontend: artist/title guess box gives immediate animated feedback, a correct guess animates a token dropping into the player's count, distinct animation for incorrect

Tests:
- [ ] Unit tests for the guess-matching function: normalization (punctuation, diacritics, whitespace) and the flat edit-distance-1 budget, covering both a correct-typo case and a same-distance wrong-word case (`DECISIONS.md`'s worked examples), plus the multi-artist any-one-correct rule
- [ ] Unit tests for scoring: all four outcome rules, including the tied-release-year case
- [ ] Unit tests for the two leaderboard tallies: a non-active player's guess updates them without touching tokens or placement; a multi-artist song credits a correct featured-artist name the same as a correct main-artist name
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

A country/language filter dimension (a "Romanian songs only" mode alongside difficulty) is dropped, decided against. Instead, a difficulty-generated set defaults to international scope, a song counts as international if its Wikidata sitelinks count (the number of language-edition Wikipedia articles covering it) clears some threshold, a signal already validated during the metadata-sourcing spike, not new testing. The other way into a session, playing from an existing playlist, now covers three cases: a playlist the player owns, one they're a member of, or one someone has published for anyone to use, publishing a playlist publicly is a new capability that doesn't exist today.

Two tiers for difficulty, so this works from day one rather than waiting months for enough data:
- A per-song aggregate difficulty score (percentage of all guesses on that song that were correct, across everyone) works immediately, even with a handful of plays per song, and covers first-time players with no personal history.
- A personalized layer (collaborative filtering: for a given player and song, predict correct-or-not and roughly how fast, learned from patterns across all players and songs, same technique Netflix-style recommenders use, applied to interaction outcomes instead of ratings) only adds value once there's enough per-player history to beat the aggregate baseline. Depends on story 10 shipping and real rounds accumulating; realistically months of casual play before the personalized layer clearly outperforms the simple aggregate at this project's 100-200 user scale, see `PROJECT_STATE.md`.

Inference is cheap and local: scoring the whole catalog against a specific group's players is a small numeric comparison per song, no external API call, runs in well under a second even for a full catalog, unlike the metadata pipeline which costs money per call. The only real cost is periodic retraining, a scheduled batch job, cheap at this data scale.

Theme side, from story 21: depends on story 14's catalog search existing, the agent needs to query the catalog by theme/keyword. A theme-generated set draws from `VERIFIED`-status songs only (story 18's lock, not `NEEDS_REVIEW` or `MANUAL_ENTRY`), same as any other selection.

- [ ] Add a `SongDifficulty` aggregate view or table: per-song correct-guess percentage across all historical guesses, updated as new rounds complete
- [ ] Add group-level difficulty scoring for "easy": the lowest individual predicted score among the group's actual players, not the average, so the least experienced player is protected rather than left behind by a group average that looks easy on paper
- [ ] Add group-level difficulty scoring for "hard": a plain average across the group's players, no floor to protect, opt-in past the easy default
- [ ] Add group-level difficulty scoring for "medium": the median of the group's individual predicted scores, a middle ground between easy's worst-case protection and hard's plain average, with no extra weighting factor to tune
- [ ] Add genre/popularity fields to `Song` if story 23's reconciliation doesn't already cover them, today's `Song` has no genre field, only a single `songTag` enum, needed for theme matching
- [ ] Persist Wikidata's sitelinks count on `Song` (coordinate with story 23), the international-scope signal for difficulty-generated sets; decide and add the actual threshold once there's enough real catalog data to check it against, not guessed
- [ ] Add an `isPublic` flag (or equivalent) to `Playlist` (coordinate with story 15), and an endpoint to publish/unpublish one; a difficulty-generated set stays separate from this, it's assembled on the spot, not a stored playlist
- [ ] Add an endpoint listing playlists available to start a session from: owned, member-of, and published-public, alongside the on-the-spot difficulty/theme generation option
- [ ] Build the theme-matching flow: theme request → catalog search (story 14) → metadata pipeline calls to fill any gaps in genre/popularity data for candidate songs
- [ ] Add the on-the-spot generation endpoint: given a group, an optional theme, an optional difficulty tier, and a target card count, score the full verified catalog for the group's actual players (blending personalized predictions where available with the aggregate baseline for first-time players), filter to whichever criteria were given, return enough songs with headroom above the win-condition card count so a session doesn't run out or repeat
- [ ] Train the personalized collaborative-filtering model on accumulated `Guess` data (story 10) once there's enough of it to evaluate
- [ ] Add a scheduled retraining job for the personalized model
- [ ] Add a monitoring check comparing the personalized model's prediction accuracy against the simple aggregate baseline; if the personalized model stops beating the baseline, that's the signal it's stale and needs retraining, not just a fixed schedule
- [ ] Add the frontend: a theme request field and a difficulty selector (easy/medium/hard), either or both, plus a review step to inspect and confirm the generated set before saving

Tests:
- [ ] Unit tests for the aggregate difficulty score calculation
- [ ] Unit tests for all three group-scoring strategies (worst-case-protected for easy, median for medium, average for hard), including groups with a mix of experienced and first-time players
- [ ] Unit tests for the personalized model's predictions against a held-out set of real guesses
- [ ] Integration test: on-the-spot generation for a full-sized group (up to 8 players) returns a scored, filtered card set in well under a second, for theme, difficulty, and both together
- [ ] Integration test: the retraining job runs and the monitoring check correctly flags a model that's stopped beating the baseline
- [ ] Integration test: publishing a playlist makes it selectable by a user who neither owns it nor is a member of it; unpublishing removes that access without affecting existing owners/members
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

Consolidated into story 40, which redefines bulk import as two separate paths (a slow admin backlog queue, and immediate on-the-spot resolution for any user) rather than one CSV/JSON endpoint. The `ADMIN` role and access-check prerequisite noted above still applies to story 40's admin-only backlog endpoints.

## Story 40: Catalog seeding queue and user-facing bulk import

Checked against real code: `Song` has a single `youtubeId` field and no lookup query for it, `SongRepository` has zero custom query methods. No `@Scheduled` usage or scheduling dependency exists anywhere in the backend (confirmed during story 32's audit), `@EnableScheduling` isn't declared. Absorbs story 19's admin bulk-import scope, redefined as two genuinely separate mechanisms, not one:

- **Admin catalog seeding**: the admin (today, the sole developer) submits large batches of YouTube playlists or IDs to grow the catalog proactively, especially popular songs. This is patient, a multi-day backlog is fine, its whole point is reducing how often a normal user's own request needs to resolve a new song at all.
- **User on-the-spot bulk import**: any user submitting their own YouTube playlist or a list of video IDs needs those songs resolved immediately, even if some of them happen to already be sitting in the admin's backlog awaiting their scheduled turn. The two never share a queue; a song in both places gets resolved twice if the timing lines up that way, that's fine, on-the-spot always wins on priority.

Both paths depend on the same cheap first step: checking submitted YouTube IDs against the database before anything else happens. Both also depend on story 20's LLM choice for the admin backlog's scheduled drain (needs a daily quota to pace against) and on the metadata-sourcing spike's real implementation (`musicbrainz.py`, `wikidata.py`) actually existing, not just validated in `ai/spikes/`.

Story 18's verification lock (`DECISIONS.md`) changes the shape of this story's LLM dependency significantly: the story 20 spike's adversarial matrix found unanimous three-source agreement on 12 of 21 songs, meaning the LLM reconciliation call is only needed for a minority of songs, not every one. This narrows, but doesn't remove, the undecided items below.

Since story 20's spike concluded, the on-the-spot path is now two tiers, not one, both decided and validated against a real 70-song set:
- **Fast tier**: answers immediately from exactly one of MusicBrainz or Wikipedia (never both for the same song), dispatched dynamically, whichever of the two is free grabs the next song, rather than a fixed split, so a slower lane doesn't leave part of a batch waiting on it. 90% accuracy (63/70) on real data. Every fast-tier answer is provisional; the song still gets queued for the full patient pipeline afterward.
- **Patient tier**: the full lock-or-Wikipedia-plus-reconciliation pipeline from story 18. 99% accuracy (69/70). This is what the admin backlog drain always uses (no latency pressure), and what every fast-tier answer eventually gets re-run through.

One thing intentionally left undecided here, not assumed:
- Exactly how the alternate-YouTube-ID-to-Song mapping interacts with story 16's pgvector near-duplicate detection: this story's YouTube-ID check is a cheap, exact, pre-pipeline step; story 16's embedding check is a fallback for when the ID is genuinely new but the song might not be, both still apply, the sequencing between them (ID check, then embedding check, then full pipeline) needs confirming once story 16 actually exists.

**Decided: rate-limit contention between the admin backlog drain and on-the-spot traffic, a real concern surfaced during story 20's spike, now settled with a priority-queue design.** On-the-spot requests, including a user's playlist import, are always high priority and take precedence over the admin backlog for the shared external rate-limit budgets (MusicBrainz, Discogs, Wikidata, Wikipedia all come from the same outbound IP). The backlog drain pauses while any on-the-spot traffic is active and resumes once it's clear. Every song the fast tier resolves provisionally gets added back to the admin backlog queue afterward, to be reprocessed through the patient pipeline at low priority like everything else, this is how the fast tier's 90%-vs-99% accuracy gap gets closed, not instantly, but automatically.

- [ ] Add a table mapping alternate YouTube video IDs to an existing `Song` (many YouTube IDs to one canonical song), separate from `Song`'s own primary `youtubeId`, so a different upload of an already-known track (a lyric video, a Topic-channel version, a re-upload) doesn't create a duplicate `Song` row or re-run the pipeline
- [ ] Add a batch YouTube-ID lookup (`SongRepository` needs its first custom query methods for this): given a list of IDs, returns which are already known, checking both `Song.youtubeId` and the new alternate-ID table, no external API calls, this is the shared first step both paths below depend on
- [ ] Add a `PendingImport` entity: a YouTube ID submitted by the admin for eventual processing, not yet resolved, with its own status (pending, processing, done, failed)
- [ ] Add an admin-only endpoint to bulk-enqueue YouTube IDs (from a playlist link or a raw ID list) into the backlog, running the batch lookup first so already-known songs never get enqueued at all
- [ ] Add a scheduled job that drains the backlog daily up to whatever the chosen LLM tier's daily free quota is (story 20), running the full metadata pipeline per item and persisting results; this is the first `@Scheduled` usage in the backend
- [ ] Add an admin view over backlog status: how many pending, how many processed today, quota remaining
- [ ] Add a bulk-import endpoint open to any user (not admin-only), accepting a YouTube playlist link or a list of video IDs
- [ ] Run the same batch YouTube-ID lookup first; only unresolved IDs proceed
- [ ] Any unresolved ID from this path is processed immediately, independent of the admin backlog's schedule, even when the same ID is also sitting in that backlog waiting its turn
- [ ] Query MusicBrainz and Wikidata first with the title/channel-derived artist (per the existing metadata-sourcing spike's design); if both return zero matches, that's the trigger for an LLM extraction pass over the raw title, channel, and description, not a subjective "does this channel look like an artist" judgment, then retry the same source queries with the corrected artist
- [ ] If the retry also comes up empty, route to manual review rather than guessing, title and artist need to be verified correct, never a confidence score the way the release year gets one
- [ ] Once title/artist are resolved, run the fast tier first (story 18/20's dynamic MusicBrainz-or-Wikipedia dispatch, `ai/spikes/run_fast_tier_dispatch.py`) to answer immediately, then enqueue the same song into the admin backlog at low priority so the patient pipeline (story 18's lock-evaluation logic) resolves it properly afterward
- [ ] Implement the priority queue itself: on-the-spot requests (including playlist imports) are always high priority against the shared external rate-limit budgets (MusicBrainz, Discogs, Wikidata, Wikipedia, one outbound IP); the scheduled backlog-drain job (above) pauses while any on-the-spot traffic is active and resumes once it clears, rather than the two paths contending for the same rate-limit budget in real time
- [ ] Depends on story 24: run the three structured sources' fetches concurrently rather than sequentially for the on-the-spot path specifically, where a user is waiting on the result; the admin backlog drain has no such latency pressure and can stay sequential if that's simpler to build first
- [ ] Coordinate with story 23: `metadataRaw` should persist the curated, actually-used subset of each source's response, not the full raw API response, Wikidata's own entity dumps alone ran into the tens of KB per song during this spike's testing; at that size the 500MB Supabase free-tier cap holds roughly 10,000-50,000 songs instead of 170,000+ with a curated version
- [ ] Raw YouTube API Data specifically (a video's title, description, channel name) has its own constraint on top of the size one above: YouTube's Developer Policies (Section III.E.4) require non-authorized API Data to be deleted or refreshed within 30 calendar days, it can't be persisted indefinitely as-is. If any raw YouTube fields end up inside `metadataRaw`, they need their own refresh/delete cycle on that schedule; the derived facts (artist, title, release year, sourced from MusicBrainz/Discogs/Wikidata/Wikipedia) aren't YouTube API Data and aren't subject to this

Tests:
- [ ] Unit tests for the batch YouTube-ID lookup, including a mix of known, alternate-mapped, and unknown IDs in one batch
- [ ] Unit tests for the alternate-YouTube-ID-to-`Song` mapping
- [ ] Integration test: admin backlog enqueue skips already-known songs, only genuinely new IDs get added
- [ ] Integration test: the scheduled drain job respects the daily quota and doesn't exceed it
- [ ] Integration test: a user's on-the-spot request resolves immediately even when the same YouTube ID is also sitting in the admin backlog
- [ ] Unit tests for the artist/title verification trigger: a zero-match escalates to LLM extraction, a successful retry clears verification, a failed retry routes to manual review rather than auto-approving
- [ ] Integration test: an on-the-spot song resolved by the fast tier gets re-enqueued into the admin backlog afterward, and later resolves through the patient pipeline without being skipped as already-done
- [ ] Integration test: the backlog-drain job pauses while on-the-spot traffic is active and resumes once it clears, doesn't contend with on-the-spot requests for the same external rate-limit budget

## Story 41: Submission content safety, non-music rejection and prompt-injection defense

Checked against real code: `_append_youtube_data` in `prompt.py` already delimits the video description and instructs the LLM to treat it as data, not instructions, the only defense that exists today, and only on the final synthesis call. Applies to every submission path, not just story 40's bulk import, story 40 just raises the exposure by opening submission to any user's arbitrary YouTube content instead of only what's manually added one at a time today.

Two things settled through discussion:
- Reject compilations outright, even genuinely musical ones, a compilation isn't a single song and has no one correct answer for a round.
- Precision over recall on the non-music gate: a false reject is a cheap, recoverable resubmit or manual override; a false accept puts non-music content in front of a player mid-game, a worse and more visible failure. This project already holds itself to a "professional-grade, not just working" bar (`DECISIONS.md`).

- [ ] Add a hard pre-pipeline filter, no LLM involved: reject when duration falls outside a generous song-length window (roughly 1-12 minutes) combined with YouTube's own `categoryId` not being Music (10), already-fetched data, no extra API cost
- [ ] For the ambiguous remainder, non-Music category but song-length duration, add an LLM classification pass (structured output: `is_song`, `is_compilation`, confidence, reasoning) reading title, channel, and description for song-like versus gameplay-like signals
- [ ] Use a match (or lack of one) against MusicBrainz/Discogs/Wikidata as a secondary signal for this same ambiguous tier, not a standalone gate: a real game-soundtrack track should resolve to an actual catalogued release, resolving to nothing across all three lowers confidence but doesn't reject outright on its own, this project explicitly wants niche/underground coverage, which also won't always resolve
- [ ] Still-uncertain cases after all of the above route to manual review, not a hard reject, the same "escalate, don't guess" principle already set for artist/title verification
- [ ] Add a dedicated structured-output prompt-injection check (`contains_injection_attempt`, plus reasoning) run over raw title/channel/description before any extraction or classification LLM call uses that text, separate from relying on the existing delimiting alone to both resist injection and do its actual job
- [ ] Apply this injection check everywhere untrusted YouTube text reaches an LLM: the existing synthesis call, story 40's artist-extraction fallback, and this story's own classification pass, not just one of the three
- [x] Decided: a flagged injection attempt writes an abuse-visibility event (story 34's scope, alongside rate-limit-exceeded and report-submitted events), an attempted injection is evidence of intent, not just an uncertain submission, so it's tracked, not silently handled the same as an honestly ambiguous song. Depends on story 34's event pipeline existing. Whether the submission itself is also outright rejected, versus routed to manual review, still needs a call, not yet made

Tests:
- [ ] Unit tests for the hard duration+category filter, including the boundary values of the song-length window
- [ ] Unit tests for compilation rejection
- [ ] Unit tests for the injection-detection check (mocked LLM call): flags known injection patterns, passes clean text through unaffected
- [ ] Integration test: a submission through any path, single-song or bulk, that fails classification never reaches the full metadata pipeline

## Story 17: Community song reports and confirmations

Depends on story 19 for the admin review surface. Resolution stays fully manual: an admin decides every report, nothing here auto-changes `verificationStatus` on its own, see `DECISIONS.md`. Every card is reportable, including `VERIFIED` ones.

- [ ] Add a `SongReport` entity (reporter, song, message, suggested correct year, sources, status)
- [ ] `POST` endpoint to submit a report, available to any authenticated user who can view the song
- [ ] Add a report button to the song detail page (`SongForm.tsx`), which has no report affordance today, available on every card regardless of `verificationStatus`
- [ ] Add a `SongConfirmation` entity (user, song, timestamp): the community thumbs-up, distinct from a report, shown only on `NEEDS_REVIEW`/`MANUAL_ENTRY` cards, one per user per song
- [ ] `POST` endpoint to submit a confirmation, same visibility rule as the thumbs-up button below
- [ ] Add a thumbs-up affordance to the song detail page, visible only for `NEEDS_REVIEW`/`MANUAL_ENTRY` cards, "is this correct?"
- [ ] Admin review surface (needs story 19's admin role) ordered by priority, not submission time:
  1. Converging reports: two or more independent reports on the same card suggesting the same year, ranked highest regardless of current `verificationStatus`, including `VERIFIED` cards
  2. Reported, no convergence (a single report, or several that disagree with each other): ranked below convergent reports, by `verificationStatus` (`MANUAL_ENTRY`/`NEEDS_REVIEW` before `VERIFIED`)
  3. Unreported `NEEDS_REVIEW`/`MANUAL_ENTRY` cards with at least one confirmation, ranked by confirmation count, a fast confirm rather than research
  4. Unreported `NEEDS_REVIEW`/`MANUAL_ENTRY` cards with no confirmations, ranked by `verificationStatus` alone (`MANUAL_ENTRY` before `NEEDS_REVIEW`)
  5. `VERIFIED` cards with no report never appear in the queue
- [ ] The review surface shows the admin every signal behind a card's ranking (report count and whether they converge, on what year, confirmation count) rather than a single opaque score, the admin makes the actual call

Tests:
- [ ] Unit tests for `SongReport` and `SongConfirmation` validation
- [ ] Unit tests for the queue-ranking logic covering all five priority tiers, including convergence overriding a `VERIFIED` card's default low priority
- [ ] Integration test: submitting a report end to end, visible on the admin review surface at the correct priority tier
- [ ] Integration test: two reports on the same card suggesting different years don't count as convergence, and rank below a genuinely convergent pair

## Story 18: Criteria for promoting a reported or newly submitted song to verified

Criteria decided, twice: an earlier `DECISIONS.md` entry ("Story 18: verification is a lock, not a score") was written without authorization and retracted; the actual criteria below are the ones later validated live against a real 70-song set (story 20's spike) and confirmed final. `TASKS.md` and `PROJECT_STATE.md` may still have stale references to the retracted entry's name, not yet cleaned up, don't trust a mention of that entry as meaning it exists. Checked against real code: `Song` (`backend/.../model/song/Song.java`) has no `verificationStatus`, `confidence`, or `metadataRaw` field today, all three are story 23's scope, still undone. This story's implementation is blocked on story 23 landing that schema first, tasks are drafted here so the decision isn't lost, not because they're startable yet.

Final lock rule, validated: **only exact agreement among MusicBrainz, Discogs, and Wikidata locks with zero LLM involvement.** Anything short of that (partial agreement, a missing source, three-way disagreement) routes through Wikipedia (fetched and extracted via a dedicated LLM reading-comprehension call, DeepSeek-V4-Flash) and a four-source reconciliation call (gpt-5-nano) instead of a "2 of 3 plus an LLM judge" shortcut; that shortcut was never validated and is not what got built. Confirmed on real data: 53% of a 70-song test set locked with no LLM call at all, and the full pipeline (locked plus reconciled) hit 99% (69/70) accuracy, the one miss being a song no source had any data on at all.

- [ ] Depends on story 23: `verificationStatus` field exists on `Song` before any of this can be implemented
- [ ] Add the lock-evaluation logic: given the release-year candidates from MusicBrainz, Discogs, and Wikidata, lock (set `verificationStatus` to verified, immutable from here) only when all three agree exactly; copy and adapt the validated logic in `ai/spikes/run_conditional_pipeline.py`
- [ ] When the three don't agree, fetch and extract Wikipedia (the dedicated extraction prompt, `ai/spikes/combo_prompts.py`'s `build_wikipedia_extraction_prompt`, DeepSeek-V4-Flash) and run four-source reconciliation (`build_four_sources_prompt`, gpt-5-nano) to produce the year, confidence, and reasoning, sets status to needs-review, never silently promoted to verified even when the LLM answers with high confidence, the lock is reserved for source agreement alone
- [ ] A genuine no-answer (no source, including Wikipedia, has anything at all) escalates to a human for manual entry of the release year, rather than guessing or leaving the song stuck. A manually-entered year gets its own status, below `needs-review`, the least-trusted tier the schema has, distinct from a year that at least one source or the LLM reconciliation actually produced
- [ ] Once locked, no code path may overwrite the year, including story 17's community reports, a report against a locked song surfaces for admin judgment but can't auto-apply
- [ ] Wire this into story 40's submission pipeline (both the admin backlog drain and the on-the-spot path) as the step that runs immediately after source gathering, before any LLM reconciliation call, so the LLM is only invoked for the fraction of songs the lock doesn't resolve

Tests:
- [ ] Unit tests for the lock-evaluation logic: exact 3-way agreement locks with no LLM call, every other combination (partial agreement, missing source, 3-way disagreement) routes to Wikipedia+reconciliation instead
- [ ] Unit test confirming a locked song's year is immutable even via story 17's report path
- [ ] Integration test: a submission with unanimous source agreement never triggers an LLM call at all
- [ ] Integration test: a submission with no data from any source, including Wikipedia, routes to manual review rather than erroring or silently failing

## Story 32: Dropped, redundant with the verification pipeline

Was: a periodic, scheduled pass over the existing catalog, calling the AI microservice with an LLM-as-judge prompt to flag likely duplicate or mislabeled entries. Dropped once story 18/40's two-tier pipeline was decided: every song already gets verified on submission, and a fast-tier answer gets re-verified through the patient tier afterward, so a separate scheduled audit pass over the whole catalog is redundant. A manual, admin-triggered version (run on demand, not on a schedule) isn't ruled out, but isn't a defined feature.

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

- [ ] Add `sources/discogs.py`, copy and adapt `ai/spikes/discogs_spike.py`'s validated implementation, including `masterless_release_years` (a release with no linked master still often carries its own correct `year` field directly in the search result, silently discarded without this), following `sources/youtube.py`'s pattern (the only currently-live source) for HTTP client usage, timeout, and broad-exception-to-`UNKNOWN_DEFAULTS` fallback
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
- [x] A video's title and channel name don't always carry the real artist at all, confirmed on three real anime openings across three different channels (Crunchyroll, a Western licensor; TOHO animation, a Japanese broadcaster; and, contrast case, MAPPA's own studio channel and One Piece's official channel, which do embed artist and song directly in the title, just Japanese-formatted, not "Artist - Title"). Each of the two failing cases had the real artist only in the description, in a different format each time (an English "OP 2 'X' by Y" sentence; a mixed Japanese/English broadcaster paragraph that also carried a release date). Decided: don't regex per-channel description formats, they vary too much to keep up with case by case; extract title/artist/featured-artists from the raw title+channel+description via a structured-output LLM call instead, when the channel doesn't look like a real artist. This runs once per submission, so it's exactly the kind of call where story 20's cheap/free-LLM spike matters, not just the final synthesis step
- [x] Decide how the pipeline should call and reconcile MusicBrainz, Wikidata, and Discogs results against each other, see `DECISIONS.md`'s 2026-09 "Metadata pipeline call/reconcile shape" entry
- [x] Decided, final: the full patient/fast two-tier pipeline shape, model choices, and a fourth source (Wikipedia), all validated with real data against a 70-song set, not guessed at. See story 20's spike section for the full results (99% patient, 90% fast) and story 18 for the lock-evaluation logic this unlocks. Not revisited again barring a real problem found during implementation
- [ ] Build `ai/app/metadata/sources/wikidata.py`, copy and adapt `ai/spikes/wikidata_spike.py`'s validated implementation (search/select/date-extraction logic, bot-password authentication, rate-limit pacing), following `youtube.py`'s pattern for HTTP client usage, timeout, and broad-exception-to-`UNKNOWN_DEFAULTS` fallback for anything the spike didn't need to handle
- [ ] Un-stub `ai/app/metadata/sources/musicbrainz.py`, copy and adapt `ai/spikes/musicbrainz_spike.py`'s validated implementation (track+album query, `select_best_release_group`'s earliest-candidate selection, adaptive rate limiter)
- [ ] Add `ai/app/metadata/sources/wikipedia.py`, a new source, doesn't exist yet: copy and adapt `ai/spikes/wikipedia_spike.py`'s validated implementation (full-text search, `select_best_page`'s track/album-aware disambiguation, lead-extract fetch). Unlike the other three sources, this one has no deterministic parsing step, `ai/spikes/combo_prompts.py`'s `build_wikipedia_extraction_prompt` is the validated LLM extraction prompt to carry over, reading comprehension over the fetched prose, kept as its own call, not folded into the final synthesis prompt
- [ ] Wire all three into `service.py`'s `_gather_all_metadata` and add matching `_append_musicbrainz_data`/`_append_wikidata_data`/`_append_wikipedia_data` sections in `prompt.py` (the MusicBrainz one already exists and may need adjusting for the decided shape)
- [ ] Add `ai/app/clients/discogs_client.py` or equivalent, copy and adapt `ai/spikes/discogs_spike.py`'s validated implementation into story 25's `discogs.py` task, including the `masterless_release_years` fallback (a real, live-confirmed bug: releases with no linked master were silently discarding a correct year sitting right in the search result)

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

## Spike: Local/cheap LLM option for bulk metadata processing

Handoff item 2. Its own branch, separate from the metadata-source spike, per the handoff's explicit instruction. Covers both hosted-API and locally-runnable options, any provider, closed or open-weight, the constraint is Pydantic-compatible structured output (`CLAUDE.md`'s non-negotiable rule against regex-parsing LLM output), not a specific deployment shape.

Survey complete, verified against each provider's own official docs across three research passes. Shortlist, all confirmed with a hard structured-output guarantee (constrained decoding or strict JSON-schema mode, not best-effort JSON):

- ~~Zhipu/Z.ai~~, dropped on two independent grounds. Free tier (GLM-4.5-Flash and GLM-4.7-Flash both tested): confirmed live that structured output doesn't hold, `response_format` with a JSON schema is silently ignored in favor of markdown-fenced prose, and forced tool-calling on GLM-4.7-Flash hung indefinitely rather than responding at all, see `ai/spikes/README.md`. Paid tier (GLM-5.3-Flash, the newest and cheapest paid option): not price-competitive even before testing whether it works, its list price ($0.15/$0.50 per million) and promo price ($0.075/$0.25 through 2026-09-09) both cost more on input than `gpt-5-nano`'s $0.05, which is already confirmed working. No remaining Zhipu tier is both cheaper and functional.
- Groq gpt-oss-20b and gpt-oss-120b, real recurring free tier (30 RPM / 1,000 RPD / 8,000 TPM / 200,000 TPD), cheap paid overflow beyond that. Live-confirmed: structured output holds.
- DeepInfra Llama-3.1-8B-Instruct-Turbo, cheapest confirmed paid hosted option. Live-confirmed once balance was added: `response_format`'s json_schema mode gets a 405 from this specific model, but forced tool-calling works, `openai_compatible_spike.py` now tries the former and falls back to the latter automatically.
- ~~AWS Bedrock Nova Micro~~, dropped. Structured output was live-confirmed to hold via forced tool use before this, but every real call hit `ThrottlingException: Too many tokens per day` on the very first request despite the account's own Service Quotas showing a 5.76 billion token/day allowance nowhere near exhausted. A known AWS provisioning bug on newly enabled accounts, the backend token counter for a specific model sometimes never initializes correctly, not something Service Quotas can fix; only an AWS Support case can, and that's not worth waiting on for this spike.
- llama.cpp run locally, 7-8B class model, zero marginal cost, see `hardware-local-llm` in project memory for why the laptop and not the desktop. Structured output live-confirmed via forced JSON schema. Runs on CPU only (~10-12 tokens/sec), never the Arc iGPU: the GPU driver has no Vulkan ICD registered (`HKLM\SOFTWARE\Khronos\Vulkan\Drivers` empty on both registry views). Confirmed this isn't an install-quality problem, a full driver reinstall via Intel Driver & Support Assistant (32.0.101.8331 to 32.0.101.8991) made no difference; the driver's own INF has no registry section writing that key at all, some separate Vulkan runtime component would need to supply it. The one remaining fix, manually registering the ICD via a registry edit, was declined. GPU offload is dropped for this spike, llama.cpp stays CPU-only; this affects its real bulk-throughput number, not the accuracy comparison, which already ran on CPU. Its memory-only accuracy also came in far below its own DeepInfra-hosted twin, 5/19 (26%) versus DeepInfra's 12/19 (63%) on the identical base model (Meta-Llama-3.1-8B-Instruct), the local build's 4-bit quantization (Q4_K_M) most likely the cause; notably zero wrong answers, 14 of 19 were the model cleanly declining to answer rather than guessing badly, consistent with quantization eroding recall confidence rather than corrupting it. Weakens the case for llama.cpp as a real candidate independent of the offload/speed question.

OpenAI's own cheap tier (`gpt-5-nano`, `gpt-5-mini`) and the existing `gpt-5.1` production baseline stay in as benchmarks, not shortlist candidates, since every option above already beats `gpt-5-nano` on price. They exist to answer "how much accuracy, if any, does the cheap/free tier give up." Both live-confirmed: structured output holds (`gpt-5-nano` rejects a non-default `temperature`, handled in the client, otherwise no surprises).

- [x] Survey current free/cheap hosted-API options and open-weight models runnable locally, for which ones support Pydantic-compatible structured output
- [x] Narrow to a shortlist of candidates that plausibly clear the structured-output bar
- [x] Set up a client for each shortlisted candidate in `ai/spikes/` (`openai_compatible_spike.py` covers OpenAI, Groq, DeepInfra, and llama.cpp, and kept the now-unused Zhipu config for reference; `bedrock_spike.py` covers Nova Micro), no production code
- [x] Expand the test set with adversarial cases, not just the agreement-heavy set already used in the metadata-source spike. `run_matrix.py`'s `SONGS` list pruned to 4 sanity-baseline entries plus 17 new ones across reissue/pressing disagreement, cover-attribution risk, thin/partial source coverage, title collisions, and multi-artist/remix credit strings. A separate `extraction_test_set.py` covers the extraction-from-a-messy-raw-submission dimension (typos, upload-artifact noise, reversed order, a deliberately unanswerable case), independent of source-API reconciliation since it tests LLM judgment directly against the rules already in `app/metadata/prompt.py`
- [x] Run a memory-only accuracy check (no source data, LLM guessing from training alone) across every live-confirmed candidate, to answer whether a zero-source-touch fast tier is viable at all: `memory_accuracy_test.py` against the 19 scoreable songs from `run_matrix.py`'s adversarial set. `gpt-5-mini` 18/19 (95%), Groq `gpt-oss-20b` 16/19 (84%), `gpt-5-nano` 15/19 (79%), DeepInfra Llama-3.1-8B 9/19 (47%), llama.cpp (local, same base model as the DeepInfra entry) result pending its first run, Bedrock Nova Micro invalid, see below. Confirms memory-only isn't reliable enough on its own, the fast tier needs at least one real source touch
- [x] Investigated Bedrock Nova Micro's `ThrottlingException: Too many tokens per day` on every call in the memory-only test: a known new-AWS-account issue, Bedrock model quotas can default to 0 or near-0 until a manual Service Quotas increase request, not a bug in the spike client. The IAM user built for this spike's $5 budget cap has no `servicequotas:*` permission (deliberately scoped tight), so checking or requesting the actual quota needs the AWS console directly, not something this spike can do for itself
- [x] Build a third MusicBrainz/Discogs-only test batch beyond the current 37-song set: `international_test_songs.py`, 12 Romanian pop, manele, and other regional/niche songs (Nigerian afrobeats, Brazilian reggaeton, Turkish Eurovision, Punjabi independent hip-hop, Balkan trap, Puerto Rican reggaeton, Filipino OPM). Manele coverage on Wikipedia turned out too thin to responsibly source a 13th ground-truth date, several otherwise-plausible titles couldn't be pinned down and were left out rather than guessed at, itself a data point that held up in every later result
- [x] Add a raw-response cache to the spike sourcing code (`response_cache.py`, one gitignored JSON file per source, keyed by song) so LLM-combination testing reuses fetched data instead of re-querying the live APIs
- [x] Run MusicBrainz, Discogs, and Wikidata across the combined 49-song set (`all_songs.py`) with the cache wired in, and every LLM-combination scenario (single-source, three-source, four-way rollup) described below, superseded by the corrected numbers further down once real bugs were found and fixed; see those entries for the final figures
- [x] Revisited Wikipedia as a fourth structured source, prompted by a live miss no other source caught: MusicBrainz, Discogs, and Wikidata all got "Hot" by Inna wrong or came up empty, while Wikipedia's own article plainly states the correct 2008 single date. Research confirmed Wikipedia and Wikidata are the same Wikimedia Foundation infrastructure, identical rate-limit tiers (10/min anonymous, 200/min authenticated) and User-Agent policy already implemented in `wikidata_spike.py`, reusable as-is; a bot password needed its own issuance per wiki though, the existing Wikidata one didn't carry over, a new one was issued for `hittiguess-spike-wp`. The real difference: Wikipedia returns article prose, not a structured claim like Wikidata's P577, so there's no field to parse directly, this needs a dedicated LLM extraction pass over the text (`combo_prompts.build_wikipedia_extraction_prompt`), a genuinely new step none of the other three sources required, and one deliberately kept separate from every reconciliation-style prompt since it tests reading comprehension, not judgment among candidates
- [x] Built `wikipedia_spike.py` and ran it against the full 49-song (later 70-song) set (`run_full_wikipedia.py`), caching results. Fixed two real disambiguation bugs live-testing this, not hypotheticals: the page-selection logic (`select_best_page`) originally preferred any result with "song"/"single" in its title without first checking the result was actually about the right song at all (picked "Together Forever (Rick Astley song)" for a "Never Gonna Give You Up" search), and once album lookups started reusing the same function, the same preference picked wrong result *types* for albums too (an unrelated "A Night at the Opera (film)" for the Queen album lookup); fixed by filtering to title-matching results first and giving `select_best_page` a `query_type` ("track" vs. "album") so the preferred/avoided keywords flip direction correctly
- [x] Extended Wikipedia to check both the track's own article and its parent album's, the same comparison MusicBrainz, Discogs, and Wikidata already did, Wikipedia was the one source missing it
- [x] Priced gpt-5-nano against comparable-tier models while researching whether anything undercuts it: no other OpenAI model does; Anthropic has no true nano-equivalent (Claude Haiku 4.5 is 20x nano's input price); Google's closest match (Gemini 2.5 Flash-Lite) is being retired 2026-10-16. Four DeepInfra-hosted candidates surfaced instead as genuinely comparable in price: gpt-5.6-luna (OpenAI's own newest budget tier), DeepSeek-V4-Flash, google/gemma-4-26B-A4B-it, and nvidia/NVIDIA-Nemotron-3-Super-120B-A12B, all structured-output-confirmed
- [x] ~~nvidia/NVIDIA-Nemotron-3-Super-120B-A12B~~, dropped. Hung indefinitely twice in a row on DeepInfra (zero progress, zero CPU growth, for 10+ minutes, even past a 90-second request timeout added specifically because of this), disqualifying on reliability alone regardless of accuracy potential
- [x] Fixed a real reliability bug the Nemotron hang surfaced: the OpenAI-compatible client had no request timeout at all; added a 90-second one to `openai_compatible_spike.py` so a hung provider-side request can't silently block an entire batch run again
- [x] Ran the three-source combo and the Wikipedia-extraction test across the remaining new candidates (gpt-5.6-luna, DeepSeek-V4-Flash, gemma-4-26B-A4B-it) alongside the original four: none beat gpt-5-nano on the three-source reconciliation task (DeepSeek-V4-Flash tied it exactly, the others scored lower), but on the separate Wikipedia-extraction task (reading comprehension, not reconciliation, a genuinely different skill) the ranking flipped: gpt-5-mini and DeepSeek-V4-Flash led, gpt-5-nano dropped well behind, and Groq collapsed to a near-total failure to extract from prose at all. Decided: gpt-5-nano stays the reconciliation model, DeepSeek-V4-Flash is the extraction model, two different models for two different steps, not one model for everything
- [x] Found and fixed a real ground-truth bug of this spike's own making: `international_test_songs.py` had "Migraine" (Moonstar88) marked as 2008 (the single's date) without checking it against the parent album "Todo Combo"'s 2007 date, the exact single-vs-album mistake this test set exists to catch other sources making. Corrected to 2007, this project's own "earliest of track/album" rule applied consistently
- [x] Found and fixed three real, live-confirmed bugs during four-source reconciliation debugging, none guessed at: (1) Discogs silently discarded every release with no linked master (`master_id: 0` is falsy, `find_master_ids`' truthy check dropped it), even when the release's own `year` field, sitting right in the search result, was correct; fixed with a fallback (`masterless_release_years`) that recovers those years, verified live on a niche single where 7 of 9 correct-year candidates had no master at all. (2) The Wikipedia-extraction prompt had no defense against cover-attribution confusion, an article covering both an original artist's earlier recording and a later cover in one paragraph could get the wrong artist's date extracted; added an explicit rule to use the date belonging to the specific artist asked about. (3) The reconciliation prompt let the model cherry-pick one plausible-looking candidate per source instead of taking the earliest across that source's own full candidate list, and separately carried an unstated bias toward trusting MusicBrainz as inherently more authoritative than the other sources; both are now explicit rules in `combo_prompts.py`
- [x] Dropped `STRUCTURED_OUTPUT_TEMPERATURE` from 0.1 to 0.0 in `openai_compatible_spike.py`: confirmed live that non-zero temperature produced real run-to-run answer variance on close reconciliation calls, not just wording differences
- [x] Reran MusicBrainz/Discogs/Wikidata/Wikipedia and every combination scenario with all of the above fixes in place: Discogs standalone rose from 71% to 80% (and 0 no-answers, down from 3); Wikipedia extraction (DeepSeek) rose from 76% to 86%, every candidate improved substantially (gemma-4-26B-A4B-it alone jumped from 61% to 86%); the three-source combo (gpt-5-nano) rose from 92% to 94%; the four-source combo rose from 90% to **96%** (47/49), reversing the earlier finding that adding Wikipedia made things worse, once the real bugs were fixed, it's a clear net win, exactly matching the complementary-error-coverage theory
- [x] Built a fourth batch, 21 more mainstream/well-known songs across eras and regions (`mainstream_test_songs.py`), pulling the combined set from 49 to 70, including one deliberate single-vs-album trap ("Smooth Criminal", single 1988, parent album "Bad" 1987, earlier)
- [x] Decided the patient pipeline's actual metadata-gathering shape: query MusicBrainz, Discogs, and Wikidata always (free, deterministic, zero LLM cost); if all three agree exactly, lock that as the answer with no LLM call at all; only when they don't agree, fetch and extract Wikipedia (DeepSeek-V4-Flash) and run four-source reconciliation (gpt-5-nano). Built and ran this (`run_conditional_pipeline.py`) across the full 70-song set: **69/70 correct (99%)**, the only miss being "Mor De Ochii Tai" (the manele track no source has ever had real data on). 37 of 70 songs (53%) locked with zero LLM calls; only 33 (47%) needed the Wikipedia+reconciliation path; 66 total LLM calls across all 70 songs, versus 140 if every song always used both LLM steps
- [ ] Decide what a genuine no-answer (no source, including Wikipedia, has anything, like "Mor De Ochii Tai") should do downstream: per story 40/41's existing "escalate, don't guess" principle, this should route to manual review rather than auto-approve or guess, but that routing isn't built or tested in this spike
- [x] Tested the fast/on-the-spot tier: MusicBrainz and Wikipedia are this spike's two strongest single sources (87% and 89% standalone across all 70 songs, see the source-comparison entries above), so the fast tier routes each song to exactly one of the two, never both, via dynamic work-stealing dispatch, whichever lane is free grabs the next song, rather than a fixed pre-assigned split, so a slower lane doesn't leave the batch half-finished while the faster one idles. Built and ran this (`run_fast_tier_dispatch.py`) against all 70 songs, real cached answers, modeled per-lane timing grounded in this session's own observed latencies (MusicBrainz's paced API calls vs. Wikipedia's fetch-plus-LLM-extraction): **63/70 correct (90%)**, MusicBrainz's lane handled 42 songs, Wikipedia's handled 28, in line with MusicBrainz being the faster lane. That's a real, known 9-point accuracy gap against the patient pipeline's 99%, the deliberate cost of answering immediately instead of waiting; every fast-tier answer is still meant to get queued for the full patient pipeline afterward (already the existing two-pipeline design), which is what closes that gap, just not instantly
- [ ] Decide whether any shortlisted LLM candidate, and this conditional-pipeline shape (patient and fast tiers both), is worth building into the real AI microservice (stories 18/40), or whether further validation is needed first
- [ ] Design the report/re-verification system floated during this spike, not yet decided in detail: a report button on every card; an admin review queue prioritized by lowest confidence first (a report on an already-3-source-locked card sinks to the bottom of the queue rather than triggering review); a community "is this correct?" thumbs-up specifically on low-confidence or manually-entered cards, as a lightweight verification signal distinct from a report. Explicitly not mandatory before shipping the pipeline itself
- [x] `docs/TASKS.md`'s own story 18 section and `docs/PROJECT_STATE.md`'s story 18 row used to reference a `DECISIONS.md` "verification is a lock, not a score" entry that was explicitly retracted earlier in this project (never actually authorized). Both cleaned up, no longer point at the retracted entry; the lock concept it described is the same shape this spike later validated with real data (three-source agreement = lock), so the underlying idea held up even though that specific entry never existed

## Story 23: Song schema reconciliation

Checked against real code and `ARCHITECTURE.md`'s target shape (line 36): `Song` today has a single `releaseYear` int, a single `songTag` enum, a single `artist` string, and no `verificationStatus`, `confidence`, or `metadataRaw` fields. No migration tool exists yet, schema changes today happen only through Hibernate's `ddl-auto=update`; this story introduces Flyway rather than add another layer of auto-DDL.

Release year is one mutable field plus `verificationStatus`, decided, not `submittedYear`/`verifiedYear` as two separate fields: once verified, the year doesn't change except through the same review process that verified it, matching story 18's lock rule and the report/re-verification design under discussion.

Multiple artists: decided. A song's artists are an ordered list, each entry tagged `MAIN` or `FEATURED`; a song can have more than one `MAIN` artist (a joint credit like "Queen & David Bowie" has two, neither featured), plus any number of `FEATURED` ones. The role tag is a display concern only, a physical card prints the main artist(s) then "featuring" the featured ones, guessing and scoring treat every artist on the list identically, naming any single one correctly is enough (story 10).

Title cleaning changes as a result, superseding the AI microservice's current `prompt.py` rule that keeps a `(feat. X)` clause in the title text: that clause is stripped out instead, its artist extracted into the structured list. Only a remix, cover, or mashup clause survives in the title text, and each of those creates its own separate `Song` row entirely, its own artist list and release year, not a variant of the original; story 16's pgvector duplicate check must not treat a remix/cover/mashup as a near-duplicate of the original it's based on.

- [ ] Introduce Flyway as the schema migration tool
- [ ] Add a `verificationStatus` field: `UNVERIFIED` (default), `VERIFIED` (locked, matches story 18's lock rule), `NEEDS_REVIEW` (an LLM-reconciled year, not locked), and `MANUAL_ENTRY` (a human-entered year for a song no source had any data on, the least-trusted tier, distinct from the other three)
- [ ] Replace the single `artist` string with an ordered `SongArtist` list (song, artist name, role: `MAIN`/`FEATURED`), supporting more than one `MAIN` entry
- [ ] Update the AI microservice's title-cleaning prompt (`prompt.py`): stop keeping `(feat. X)`/`(ft. X)` clauses in the title, extract them into the artist list instead; keep remix/cover/mashup clauses in the title, and treat each as its own distinct submission rather than a variant of the original song
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
- [ ] `proxy.ts` gates every protected-route navigation on the presence of the `access_token` cookie alone, a 15-minute lifetime (`jwt.expiration=900000` in `application.properties`), instead of the 7-day `refresh_token` cookie. An idle user whose access token has expired gets redirected straight to `/login` on their next navigation, before `axios-instance.ts`'s response interceptor ever gets a chance to run and silently reissue a new access token through `/auth/refresh`, even though a valid refresh token still exists. Gate the middleware check on `refresh_token`'s presence instead, and let the client-side interceptor perform the actual reissue.

## Chore: Flutter DJ-model compliance

Flutter is kept, not dropped, deprioritized behind the web app per the existing 2026-06 `DECISIONS.md` entry. In the meantime it must follow the same non-negotiable rule as the rest of the product: the DJ is never shown an embedded YouTube player, playback happens on the real YouTube app.

- [ ] Check the current Flutter code for any embedded or hidden YouTube playback (an in-app WebView or player widget); not yet confirmed against the real Flutter codebase
- [ ] If one exists, replace it with a real link-out to the YouTube app, matching the 2026-07 `DECISIONS.md` entry's mechanism for the web DJ view

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
- [ ] Load-test every rate-limited entry point (both services) under concurrent traffic past the configured limit, confirming the limiter holds under real concurrency rather than only the single-threaded unit tests below

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

Scope decided: exactly two dedicated legal/static pages, Privacy Policy and Terms of Service. No separate cookie-consent page is needed yet, it's already gated below on story 34 shipping, and no other legal or static page (About, Contact) is planned.

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

## Story 28: UI redesign

Checked against real code: the frontend today covers auth (login, register, forgot-password, OAuth2 redirect), a landing page, and the dashboard's playlist/song CRUD (playlist list, playlist detail, song list, song detail, add song, join-by-invite). Everything `GAME_DESIGN.md` already specs for gameplay (drag-and-drop timeline, guess box, betting, voice sidebar, chat overlay, DJ link-out, turn notifications, results/leaderboards) has no frontend code yet, since stories 10/11/12/13/39 haven't been implemented.

Scope decided: one unified redesign pass covering both the existing pages and the not-yet-built gameplay screens, not two separate efforts. A fresh visual direction, not constrained to the current shadcn/Tailwind theme tokens, though the underlying component library stays unless a specific component doesn't hold up under the new direction. Mockups are built as a multi-artboard canvas via the `design` skill, reviewed before any implementation code is written.

- [ ] Design phase: establish the fresh visual direction (color, type, spacing, component style) and apply it across every existing page: landing, login, register, forgot-password, dashboard/playlist list, playlist detail, song detail, add song, join-by-invite
- [ ] Design phase: extend the same visual system to the gameplay screens `GAME_DESIGN.md` specs but that don't exist as code yet: group lobby (member list, admin crown, join code/link, settings), game session/timeline (drag-and-drop cards, guess box, token count, betting window), DJ view (open-in-YouTube link-out), voice sidebar, text chat overlay, turn notification banner, the minimized "playing while away" widget state, and the results/leaderboard screen
- [ ] Review pass against every mockup with the project owner before implementation starts, checking each gameplay screen against `GAME_DESIGN.md`'s spec for anything the design missed
- [ ] Implementation: apply the new visual system to the existing pages/components in `frontend/app` and `frontend/components`, replacing the current shadcn theme tokens with the new ones
- [ ] Implementation: build the new gameplay screens as real Next.js components/routes; wire to stories 10/11/39's actual backend once those land, using representative mock state in the meantime so this doesn't block on their implementation timing
- [ ] Decide and document the actual component/token boundary: shadcn stays as the underlying primitive library with new theme tokens, versus specific components getting replaced outright, per what the mockups actually need

Tests:
- [ ] Frontend test: each redesigned existing page renders without regression (a smoke test per route)
- [ ] Frontend test: the new gameplay screens render correctly against representative mock state (empty, mid-game, varying player counts)
- [ ] Frontend test: the drag-and-drop timeline placement and the guess box's animated feedback behave per `GAME_DESIGN.md`'s Interaction and animation section
- [ ] Accessibility check: color contrast and keyboard navigation for the new visual direction, specifically the semi-transparent chat overlay and the voice sidebar

## Story 42: Explicit database split

Formalizes the boundary between the core transactional database and story 33's separate analytics/event store as its own architectural decision, rather than leaving it implicit in story 33's provisioning task alone. Story 33 still owns picking the actual analytics store; this defines which data belongs on which side of the line, and why.

- [ ] Document, in `ARCHITECTURE.md`'s Database section, the explicit domain boundary: every entity either service reads or writes today, users, groups, sessions, rounds, guesses, songs, playlists, and pgvector embeddings, stays in the transactional Postgres+pgvector instance; only story 33's append-heavy usage/event data goes in the separate analytics store
- [ ] Confirm no entity currently planned for either service needs to live in both places or move between them; note it here if one turns up during story 33 or 34's implementation
- [ ] Cross-reference this story from stories 33 and 34 so the boundary isn't restated inconsistently in three places

## Story 43: Metadata minimization

A cross-cutting principle rather than a single implementation: curb `metadataRaw`'s growth so it doesn't bloat the database. Story 40 already flags this as a real constraint (Wikidata's own entity dumps ran tens of KB per song during the sourcing spike; at that size the 500MB Supabase free-tier cap holds roughly 10,000-50,000 songs instead of 170,000+ with a curated version), and story 23 already decides the fix (`metadataRaw` persists the curated, actually-used subset of each source's response, not the full raw API response). This story applies that same rule everywhere raw pipeline output gets persisted, not just at those two stories' specific call sites.

- [ ] Audit every place raw source or pipeline output is persisted (`metadataRaw` on `Song`, any raw YouTube API Data fields) against the curated-subset rule already decided in stories 23 and 40, confirm nothing outside those two stories ends up persisting an uncurated raw response
- [ ] Document the curation rule in `ARCHITECTURE.md` as a standing constraint on any future field that persists external API output, not just `metadataRaw`
- [ ] Coordinate with story 40's YouTube-API-Data 30-day refresh/delete requirement: both are limits on the same field, one on size, one on retention

## Story 44: Test user infrastructure (dev only)

A dedicated `Role` for automated test/QA agents, separate from `USER` and story 19/40's `ADMIN`. Exists so automated agents, this project's own AI-assisted development workflow included, reuse one seeded test account's credentials across runs instead of registering a fresh throwaway account every time. Coordinates with story 22 (test coverage): this is test infrastructure, not test coverage itself.

- [ ] Add a `TEST` value to `User.role`, alongside the existing `USER` and (once story 19/40 lands) `ADMIN`
- [ ] Add a fixture or seed mechanism that creates one reusable test account with known credentials in local/dev environments, rather than a new account per test run
- [ ] Document, in `CONTRIBUTING.md` (story 36) or a dev-setup doc, that agents and contributors running tests locally reuse the seeded test account's credentials instead of registering new ones
- [ ] Add an environment guardrail: any `TEST`-role account, and any endpoint or behavior gated on that role, is a no-op or outright rejected when running against a Production environment, even if a `TEST`-role row somehow exists there
- [ ] Add a startup or CI check that fails loudly if a `TEST`-role row is ever found in a Production database, rather than silently ignoring it

Tests:
- [ ] Unit test confirming `TEST`-role behavior is disabled under a Production environment flag, including the case where a `TEST` row actually exists
- [ ] Unit test for the seed/fixture mechanism producing the same reusable credentials across repeated runs
