# TASKS.md: What To Actually Work On

This is the source of truth for day-to-day work. Consult PROJECT_STATE.md only when you need the bigger picture behind one of these.

The tasks below, under stories 7, 9, 10, 11, and 12, are drafts and have not yet been confirmed against the real implementation, except where noted. Before starting any of them, check them against the current code: some tasks may already be done, some may not apply the way they're written, and some may be missing. Once a story's tasks are confirmed accurate, update its status to Ready in PROJECT_STATE.md.

Story 9 and story 12 were checked against the real code and confirmed blocked: both assume a session (story 10) and a WebSocket layer (story 11) that don't exist yet. Neither can move to Ready until 10 and 11 do.

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

Confirmed against the real code: there's no DJ view, no session concept, and no WebSocket layer today, so this is new work, not a removal. The QR code task was split out and done separately, see `ARCHIVE.md`'s Bug fixes entry. Blocked on story 10 (session) and story 11 (WebSocket sync).

- [ ] Build the DJ view: an "open in YouTube" link-out for remote sessions, opening a new browser tab, never an embedded player
- [ ] Wire WebRTC tab audio capture to that new tab and stream it to the other players
- [ ] Add deep-link handling for in-person sessions (Android intent, iOS universal link, fallback to a plain browser link)
- [ ] Wire the manual reveal trigger over WebSocket, any player can reveal once the song has played

## Story 10: Ephemeral game session

Not yet checked against real code, there's nothing to check against, no session model exists. Draft, based on the `GameSession` shape and round flow already described in `ARCHITECTURE.md`.

Open question this breakdown doesn't resolve: whether session state lives entirely in memory or as ephemeral rows in Postgres, cleaned up on session end. `ARCHITECTURE.md` doesn't say, needs a decision before the entity work starts. Also open: the exact timeline-length-by-player-count scaling rule from `GAME_DESIGN.md`.

- [ ] Decide the session state persistence strategy (in-memory vs. ephemeral DB rows)
- [ ] Implement `GameSession`, `Player`, `Round`, and `Guess` per the shape in `ARCHITECTURE.md`
- [ ] `POST` endpoint to create a session: playlist(s), DJ setting (fixed or rotating)
- [ ] `POST` endpoint to join a session via invite link, before it starts only, full player, no spectating
- [ ] Session start: lock the roster, assign round 1's active player and DJ
- [ ] Round rotation: active player rotates each round, DJ stays fixed or rotates per the session setting
- [ ] Guess placement and lock-in: before/after/between on the active player's timeline
- [ ] Betting: other token-holding players may bet after the guess locks, first come first served
- [ ] Scoring: apply the four outcome rules in `GAME_DESIGN.md` (correct placement keeps the card even on a tied release year; a correct guess beats any bet; a wrong guess with a correct bet gives the card to the bettor; a wrong guess with no bet discards it)
- [ ] Pin the timeline-length-by-player-count scaling rule, then implement the win condition
- [ ] Downloadable results export at session end
- [ ] Purge all session state (roster, rounds, chat) once the session ends

## Story 11: Real-time game sync over WebSocket

Not yet checked against real code, no WebSocket layer exists yet. Draft, based on the sync model in `ARCHITECTURE.md` (REST for session creation/join, WebSocket for state changes).

- [ ] Add the Spring WebSocket/STOMP dependency and base config to the core service
- [ ] Authenticate the WebSocket handshake against the existing JWT auth
- [ ] Define per-session STOMP destinations for broadcast and a client-to-server channel for actions (guess, bet, reveal)
- [ ] Broadcast session lifecycle events: player joined, player disconnected/reconnected, session started
- [ ] Broadcast round events: round started, guess locked, bet placed, reveal triggered, round scored, next round
- [ ] Handle disconnect: mark the player's `isConnected` flag false without ending the session
- [ ] Wire session creation/join (story 10) to register the joining client on the session's topic

## Story 12: Voice chat

Blocked on story 11: voice signaling rides the existing WebSocket layer per `ARCHITECTURE.md`, which doesn't exist yet.

- [ ] Implement WebRTC signaling over the WebSocket layer built in story 11
- [ ] Implement mesh peer connection setup between session participants
- [ ] Enforce the 8-participant cap per session
- [ ] Integrate Cloudflare TURN, pay-as-you-go, as the ICE server fallback
- [ ] Add join/leave voice UI within a session
