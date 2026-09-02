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

## Story 31: Dropped

Was: a "similar songs" feature using text embeddings over song title and artist. Dropped: the only version of "similar songs" worth building is audio-based (how a song actually sounds), not text-based (which mostly just catches same-artist or similarly-worded matches). See story 29 for why the audio-based version doesn't have a viable data source either.

## Story 29: Dropped, no viable audio-feature source found

Researched directly rather than left open: AcousticBrainz, the obvious free option, shut down its live API and submission pipeline in February 2022; only a frozen dataset remains, dated June 2022, with coverage skewed toward mainstream music already analyzed before the shutdown, exactly the opposite of the niche/underground coverage this project cares about. Self-hosting Essentia (the toolkit AcousticBrainz itself used) would work on any song, but needs the actual audio file, and the only way to get that for a YouTube-sourced song is unofficial downloading, which violates `CLAUDE.md`'s non-negotiable official-APIs-only rule and the DJ-link-out architecture built specifically to avoid touching YouTube's media stream. Paid catalog APIs (Apple Music at $99/year, various smaller commercial ones) are real ongoing cost for a nice-to-have feature and still don't reliably cover niche YouTube-only tracks. No option clears the bar. Dropped rather than left blocked indefinitely.

## Story 30: Collaborative filtering recommendations

Blocked on enough real usage data existing (`PROJECT_STATE.md`), and on story 10 (game session) actually shipping, since guess correctness and guess time, the likely implicit signals, only exist once real rounds are being played. Can't produce a real recommender without real interaction data, so only the shape is drafted here.

- [ ] Design the interaction-signal schema (guess correctness, guess time, or explicit ratings) once story 10 is live and producing real data
- [ ] Implement collaborative filtering (matrix factorization or item-item similarity) once enough interaction data has accumulated at the 100-200 user target scale

## Story 33: Analytics data store

- [ ] Choose and provision a separate append-heavy store for usage/event data, apart from the transactional Postgres database (a separate schema, or a dedicated event/time-series store)
- [ ] Define the event schema: game session start/end (with a compact per-game summary, group, players, win/loss, cards won, final score, for story 34's game history feature), login, playlist created, song submitted, rate-limit-exceeded (user, endpoint), report submitted, failed login attempt
- [ ] Decide a retention policy

Tests:
- [ ] Integration test: an event write to the new store doesn't touch or block the transactional database
- [ ] Unit test for the retention policy's cleanup logic

## Story 34: First-party usage analytics

Depends on story 33's store existing. Event scope is deliberately count/aggregate-based, not behavioral click-tracking: usage stats for the project's own understanding (games played, session length, playlists created, songs submitted, login activity), and abuse-visibility signals that turn existing enforcement into something reviewable (rate-limit-exceeded events from stories 13/27, report submissions from story 17, failed login attempts), not a new detection mechanism of its own.

- [ ] Instrument game session start/end (with the per-game summary), login, playlist creation, and song submission events to write to the analytics store
- [ ] Instrument rate-limit-exceeded, report-submitted, and failed-login-attempt events, for abuse visibility, not enforcement, stories 13/27/17 already enforce
- [ ] Build a simple internal dashboard or query surface over the collected events, including a simple way to flag a user who's crossed a rate-limit or report threshold repeatedly
- [ ] Build a per-user game history page in the frontend, querying the current user's own game-summary events from the analytics store; the transactional `GameSession`/`Round`/`Guess` rows still purge exactly as story 10 already specifies, this reads only from the separate analytics store
- [ ] No third-party trackers, matches this story's own scope and the "First-party usage analytics" framing

Tests:
- [ ] Integration test: each instrumented event type produces the expected record in the analytics store
- [ ] Integration test: the dashboard/query surface returns correct aggregates for known event data
- [ ] Integration test: a user's game history page returns only their own game summaries, not other users'
