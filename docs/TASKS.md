# TASKS.md: What To Actually Work On

This is the source of truth for day-to-day work. Consult PROJECT_STATE.md only when you need the bigger picture behind one of these.

The tasks below, under stories 7, 9, 10, 11, 12, and 39, are drafts and have not yet been confirmed against the real implementation, except where noted. Before starting any of them, check them against the current code: some tasks may already be done, some may not apply the way they're written, and some may be missing. Once a story's tasks are confirmed accurate, update its status to Ready in PROJECT_STATE.md.

Story 9 and story 12 were checked against the real code and confirmed blocked: both assume a group (story 39), a game session (story 10), and a WebSocket layer (story 11) that don't exist yet. Neither can move to Ready until 10, 11, and 39 do.

"Next available task" means the earliest unchecked box under a Ready or In Progress story.

## Story 7: Azure migration

- [ ] Confirm the current Postgres host (see story 23, may need the schema reconciliation done first)
- [ ] Provision an Azure Container Apps environment
- [ ] Deploy the core service container
- [ ] Deploy the AI microservice container, same environment, for internal networking
- [ ] Provision Azure Database for PostgreSQL Flexible Server, enable pgvector
- [ ] Migrate data to the new Postgres instance
- [ ] Verify both services and the frontend work end to end against Azure
- [ ] Point the hittiguess.com domain at the new deployment
- [ ] Update OAuth2 redirect URIs for the new domain
- [ ] Decommission Fly.io once verified

## Story 9: DJ real YouTube link-out

Confirmed against the real code: there's no DJ view, no group, no session concept, and no WebSocket layer today, so this is new work, not a removal. The QR code task was split out and done separately, see `ARCHIVE.md`'s Bug fixes entry. Blocked on story 39 (group), story 10 (game session), and story 11 (WebSocket sync).

- [ ] Build the DJ view: an "open in YouTube" link-out for remote sessions, opening a new browser tab, never an embedded player
- [ ] Wire WebRTC tab audio capture to that new tab and stream it to the other players
- [ ] Add deep-link handling for in-person sessions (Android intent, iOS universal link, fallback to a plain browser link)
- [ ] Wire the manual reveal trigger over WebSocket, any player can reveal once the song has played

## Story 10: Game session

Not yet checked against real code, there's nothing to check against, no session model exists. Draft, based on the `GameSession` shape and round flow in `ARCHITECTURE.md`, and the group/game-session split logged in `DECISIONS.md`.

- [ ] Implement `GameSession`, `Player`, `Round`, and `Guess` as ephemeral Postgres rows, purged when the session ends
- [ ] Initialize a session from the group's current settings when the admin starts it (playlist(s), DJ mode, win-condition card count), snapshotting the group's connected members as the roster
- [ ] Assign round 1's active player and DJ
- [ ] Round rotation: active player rotates each round, DJ stays fixed or rotates per the group's setting
- [ ] Guess placement and lock-in: before/after/between on the active player's timeline
- [ ] Betting: other token-holding players may bet after the guess locks, first come first served
- [ ] Scoring: apply the four outcome rules in `GAME_DESIGN.md` (correct placement keeps the card even on a tied release year; a correct guess beats any bet; a wrong guess with a correct bet gives the card to the bettor; a wrong guess with no bet discards it)
- [ ] Win condition: first player to reach the group's configured card count wins, bounded 5-20 for a 2-3 player group or 5-15 for a 4-8 player group
- [ ] Auto-abandon the session after 10 minutes with zero connected players, no results export in that case
- [ ] Downloadable results export when a session completes normally
- [ ] Purge all session state (roster, rounds, guesses) once the session ends or is abandoned, hand control back to the group

## Story 11: Real-time game sync over WebSocket

Not yet checked against real code, no WebSocket layer exists yet. Draft, based on the sync model in `ARCHITECTURE.md` (REST for group/session creation and join, WebSocket for state changes). Covers both the group and the game session, not just the session.

- [ ] Add the Spring WebSocket/STOMP dependency and base config to the core service
- [ ] Authenticate the WebSocket handshake against the existing JWT auth
- [ ] Define per-group STOMP destinations for broadcast (membership, settings changes, chat, voice signaling) and a client-to-server channel for admin actions
- [ ] Define per-session STOMP destinations for broadcast (round events) and a client-to-server channel for actions (guess, bet, reveal)
- [ ] Broadcast group events: member joined/left, settings changed, game session started
- [ ] Broadcast round events: round started, guess locked, bet placed, reveal triggered, round scored, next round
- [ ] Handle disconnect: mark the member's or player's `isConnected` flag false without ending the group or the session
- [ ] Wire group creation/join (story 39) to register the joining client on the group's topic, and game session start (story 10) to register on the session's topic

## Story 12: Voice chat

Blocked on story 11 (WebSocket layer) and story 39 (group): voice is scoped to the group's lifetime, not the game session's, and its signaling rides the WebSocket layer, neither exists yet.

- [ ] Implement WebRTC signaling over the WebSocket layer built in story 11
- [ ] Implement mesh peer connection setup between group members
- [ ] Enforce the 8-participant cap per group
- [ ] Integrate Cloudflare TURN, pay-as-you-go, as the ICE server fallback
- [ ] Add join/leave voice UI, joinable and leavable at any time, not tied to starting a call

## Story 39: Group

Not yet checked against real code, no group model exists. Draft, based on `ARCHITECTURE.md`'s Group shape and lifecycle, and `GAME_DESIGN.md`'s Groups section.

- [ ] Implement `Group` and `Member` as ephemeral Postgres rows
- [ ] `POST` endpoint to create a group; creator becomes admin
- [ ] Enforce one active group membership per user
- [ ] `POST` endpoint to join a group via invite link, only while the group hasn't started a game session yet
- [ ] Settings (playlist(s), DJ mode, win-condition card count), editable by the admin only, broadcast to all members in real time
- [ ] Chat available from group creation, stored for the life of the group
- [ ] Voice joinable and leavable at any time (see story 12 for the WebRTC mechanics)
- [ ] 30-minute timer from group creation to the admin starting a game session, delete the group if it fires
- [ ] Admin action to start a game session (see story 10), locks the group to new members
- [ ] 30-minute timer from a game session ending to the admin starting another, delete the group and remove every member if it fires
- [ ] Explicit leave vs. disconnect: disconnect only flips `isConnected`, explicit leave removes membership
- [ ] Admin explicitly leaves: promote the next-earliest-joined member to admin, or delete the group if none remain
- [ ] On app load, check the logged-in user's active group membership and prompt to return or leave, no link-based reconnect

## Story 15: Song/playlist relational fix

Checked against real code: `Song.playlist` is a required singular `@ManyToOne`, one song belongs to exactly one playlist today. Touches the same table as story 23; sequencing or combining the two migrations avoids two separate schema changes to `Song`.

- [ ] Introduce a join table between `Song` and `Playlist`, replacing the singular `@ManyToOne playlist` on `Song`
- [ ] Migrate existing data: each song's current single playlist link becomes one row in the new join table
- [ ] Update `PlaylistService`'s `checkPlaylistAccess` and `checkSongBelongsToPlaylist`, both currently assume one song belongs to exactly one playlist
- [ ] Decide song deletion semantics once a song isn't playlist-exclusive: does removing a song from one playlist delete it outright, or only unlink it? `PlaylistController`'s current delete-song endpoint assumes deletion
- [ ] Update `SongDTO`/`PlaylistDetailDTO` and the frontend to reflect a song appearing in multiple playlists
- [ ] Coordinate with story 23 (schema reconciliation), both touch `Song`'s shape

Tests:
- [ ] Unit tests for `checkPlaylistAccess` and `checkSongBelongsToPlaylist` against the new many-to-many relation
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
- [ ] Add a report button to the song view/edit UI, no report UI exists today
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
- [ ] Add a periodic pass over the catalog, calling the AI microservice with an LLM-as-judge prompt to flag likely duplicate or mislabeled entries
- [ ] Surface flagged results on a reviewable surface (needs story 19's admin role)

Tests:
- [ ] Unit tests for the flagging logic (mocked LLM call)
- [ ] Integration test: the scheduled job runs and produces flagged results on the review surface

## Story 24: Parallelize metadata pipeline fetches across sources

Checked against real code: `_gather_all_metadata` calls its sources sequentially with plain synchronous `httpx.get`, no `asyncio.gather`, no `httpx.AsyncClient`, no thread pool anywhere in the pipeline.

- [ ] Convert the source fetch functions to async, using `httpx.AsyncClient`
- [ ] Run the parallel-eligible sources concurrently with `asyncio.gather` in `_gather_all_metadata`
- [ ] Parallelizing today's stubbed MusicBrainz/Wikipedia/Genius calls is wasted work, since the resolved source set is MusicBrainz, Discogs, and Wikidata (`PROJECT_STATE.md`); wait until the real three sources exist (Discogs via story 25, MusicBrainz/Wikidata still undecided) before parallelizing, rather than the current four
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
