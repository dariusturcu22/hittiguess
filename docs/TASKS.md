# TASKS.md: What To Actually Work On

This is the source of truth for day-to-day work. Consult PROJECT_STATE.md only when you need the bigger picture behind one of these.

Before starting any task, check it against the current code: some tasks may already be done, some may not apply the way they're written, and some may be missing. Once a story's tasks are confirmed accurate, update its status to Ready (or Implemented, once its own backend batch is actually done) in PROJECT_STATE.md.

Stories 9, 10, 11, 12, 13, 39, and most of the rest of Phase 1 and Phase 2 are now implemented (backend); see PROJECT_STATE.md for the current status of every story. Story 28 now implements the frontend surface for Batches A through F against those backends. Remaining visual, route, and representative-state checks stay listed under story 28.

"Next available task" means the earliest unchecked box under a Ready or In Progress story.

## Standing policy: all frontend work lives in story 28

Every story other than story 28 is backend-only. Any frontend task a story would otherwise carry (a page, a component, a WebRTC/browser-side piece, a frontend test) is tracked under story 28's implementation phase instead, not built in that story's own batch. Story 28 is the single place all frontend lands, wired against the real backends every prior batch shipped. Frontend tasks already written inline under other stories stay listed there marked "story 28" for traceability, but they are not part of that story's own batch completion; a backend story is done when its backend code and backend tests pass.

## Standing policy: story 34 abuse-visibility event writes are stubbed until story 34 ships

Stories that would write an abuse-visibility event (story 41's flagged-injection event, story 17's report-submitted event, story 27's rate-limit-exceeded event) write a stubbed no-op (a structured log line marked `TODO: story 34`) rather than a real event, since story 34's analytics event pipeline is Phase 3 and not built. Story 34 replaces these stubs with real writes. See `DECISIONS.md`.

## Story 9: DJ real YouTube link-out

Stories 10, 11, and 39 have all shipped (backend). Story 9's draft tasks confirmed accurate against the real code: no DJ view exists in the frontend, the backend session model tracks the DJ per round but no link-out, audio capture, or DJ-role enforcement is built. Ready.

The DJ's only in-app action is "Open YouTube Link"; playback, pausing, and closing the tab or app all happen on YouTube itself, never mirrored into the game. The round's own flow, the betting countdown and window, the reveal, and advancing to the next player, runs automatically off timers the game already has once the active player locks in a placement, with no manual trigger from the DJ or any player (see `GAME_DESIGN.md`'s Roles section and story 10's automatic-reveal task).

Backend slice (this batch). The DJ needs the current round's YouTube watch URL to link out, and the round DTO deliberately withholds a song's `youtubeId` until the reveal, so playback data reaches only the DJ and only during the playback window. `GET /api/sessions/{sessionId}/link-out` returns the current round's video id and canonical watch URL, restricted to that round's DJ and refused once the round is REVEALED or SCORED. The watch URL is built by `YoutubeLinkParser.buildWatchUrl` from the stored video id rather than assembled inline. Round flow stays driven by placement-lock-in timers, so "the DJ opened the link" needs no server-side state and belongs to story 28 along with the rest of the frontend. The audio-stream cutoff on guess lock-in is a client reaction to the existing `GUESS_LOCKED` broadcast, so it too is a story-28 concern with no new backend state.

- [x] Backend: expose the current round's YouTube link-out (video id plus canonical watch URL) to the round's DJ, gated so only that DJ can fetch it and only before the reveal (`GameSessionService.getCurrentRoundLinkOut`, `GameSessionController` `GET /api/sessions/{sessionId}/link-out`, `RoundLinkOutDTO`)
- [x] Backend: build the watch URL from the stored video id through `YoutubeLinkParser.buildWatchUrl` rather than a hardcoded URL string
- [ ] Build the DJ view: an "open in YouTube" link-out for remote sessions, opening a new browser tab, never an embedded player, behind an explicit "Open YouTube Link" action (story 28)
- [ ] Add a UI warning shown alongside that action, explicit that clicking it starts broadcasting the DJ's tab or system audio to the rest of the group (story 28)
- [ ] Wire WebRTC tab audio capture to that new tab and stream it to the other players, starting only once the DJ has actually opened the link, not before (story 28)
- [ ] Add deep-link handling for in-person sessions (Android intent, iOS universal link, fallback to a plain browser link) (story 28)
- [ ] Wire the active player's audio-stream cutoff over WebSocket: cuts off immediately on guess lock-in, regardless of what's still playing on the DJ's end (story 28, client reaction to the existing `GUESS_LOCKED` broadcast)
- [x] Backend: restrict the link-out to the DJ role specifically, a non-DJ player's or non-member's attempt is rejected

Tests:
- [x] Unit test: `YoutubeLinkParser.buildWatchUrl` produces the canonical watch URL and rejects a non-video-id input (`YoutubeLinkParserTest`)
- [x] Integration test: the round's DJ fetches the link-out and gets the correct watch URL for the current round's song; a non-DJ player is denied; a non-member is denied; the link-out is refused once the round is revealed (`GameSessionLinkOutIntegrationTest`)
- [ ] Unit test: the audio-stream cutoff fires on guess lock-in regardless of playback state, and only for the active player's stream (story 28)
- [ ] Frontend test: the "Open YouTube Link" action shows the audio-sharing warning before WebRTC tab capture starts (story 28)
- [ ] Integration test: deep-link handling falls back to a plain browser link when the YouTube app isn't installed (story 28)

## Story 10: Game session

Checked against real code: no session model exists, this is greenfield work. Based on the `GameSession` shape and round flow in `ARCHITECTURE.md`, and the round/token/reconnect rules in `GAME_DESIGN.md`.

Built on `feature/game-session`, stacked off `feature/websocket-sync`. The gameplay frontend is implemented in story 28; its visual matrix, representative-state tests, and accessibility checks remain open. See `DECISIONS.md` for the round-rotation, timer-scheduling, and betting-concurrency design choices this batch resolved.

- [x] Implement `GameSession`, `Player`, `Round`, and `Guess` as ephemeral Postgres rows, purged when the session ends
- [x] Initialize a session from the group's current settings when the admin starts it (playlist(s), DJ mode, win-condition card count), snapshotting the group's connected members as the roster
- [x] Assign round 1's active player and DJ
- [x] Round rotation: active player rotates each round, DJ stays fixed or rotates per the group's setting, skipping players marked `Left`
- [x] Guess placement and lock-in: before/after/between on the active player's timeline. Lock-in sound effect is a frontend concern, not built this batch (backend only)
- [x] 3-5 second countdown after lock-in, then a 15-second betting window; skip the window entirely if no player holds a token
- [x] Betting: every token-holding player may bet during the window, at the same time as each other, each on a different gap in the active player's own timeline than the one the active player just locked in, one bet per interval rather than one bet per round; concurrency-safe so only the first bet on a given gap is accepted and a losing attempt doesn't cost a token; a player still can't bet twice in the same round; a skip-betting action ends the window early
- [x] Automatic reveal once the betting window closes: broadcast the song's artist, title, and year to every player, off the same window timer, with no DJ or player action triggering it
- [x] Artist/title guess box, available to every player except the DJ for the whole turn, independent of timeline placement; only the active player's fully correct guess awards a token, matching normalizes both strings (lowercase, strip punctuation, strip diacritics, collapse whitespace) and compares them with Damerau-Levenshtein edit distance, a flat budget of 1 regardless of length (see `DECISIONS.md`). For a song with more than one artist (main or featured, story 23), naming any single one of them correctly is enough for the token, not all of them
- [x] Scoring: apply the four outcome rules in `GAME_DESIGN.md` (correct placement keeps the card even on a tied release year, and every bet is lost regardless; a wrong placement with a bet sitting on the gap that's objectively correct for the active player's timeline gives the card to that bettor's own timeline, inserted at wherever it objectively belongs there; a wrong placement with no bet on the correct gap, whether no one bet there or no one bet at all, discards the card)
- [x] Track two running per-player tallies for the session, fed by every player's guesses, active or not: total individual artists correctly named (every correct name, main or featured, from any song, adds one, regardless of how many total artists that song has) and total fully-correct title guesses. A non-active player's guess never earns a token or affects placement/betting, it only feeds these two tallies
- [x] Win condition: first player to reach the group's configured card count wins, bounded 5-20 for a 2-3 player group or 5-15 for a 4-8 player group (reuses `GroupService`'s existing validation, not re-implemented)
- [x] Player disconnect: mark `isConnected` false, leave timeline/tokens/turn order untouched
- [x] Player explicit leave: mark `Left`, exclude from future turns and DJ rotation, existing timeline cards still count toward the final results
- [x] Active-player turn timeout: if the active player is disconnected when their turn comes, or disconnects mid-turn, auto-skip after 90 seconds and mark them `Left`
- [x] Auto-abandon the session after 10 minutes with zero connected players, no results export in that case
- [x] Downloadable results export when a session completes normally, including the main card-count ranking and the two separate "Most Artists Guessed"/"Most Titles Guessed" leaderboards
- [x] Purge all session state (roster, rounds, guesses) once the session ends or is abandoned, hand control back to the group (`GroupService.recordGameSessionEnded` now also reopens the group, see `DECISIONS.md`)
- [ ] Frontend: drag-and-drop timeline placement, cards animate apart to open a gap with no overlap, animate back into place once placed. Deferred to story 28, backend only this phase
- [ ] Frontend: artist/title guess box gives immediate animated feedback, a correct guess animates a token dropping into the player's count, distinct animation for incorrect. Deferred to story 28, backend only this phase

Tests:
- [x] Unit tests for the guess-matching function: normalization (punctuation, diacritics, whitespace) and the flat edit-distance-1 budget, covering both a correct-typo case and a same-distance wrong-word case (`DECISIONS.md`'s worked examples), plus the multi-artist any-one-correct rule (`GuessMatcherTest`)
- [x] Unit tests for scoring: all four outcome rules, including the tied-release-year case (`GameSessionServiceTest`)
- [x] Unit tests for the two leaderboard tallies: a non-active player's guess updates them without touching tokens or placement; a multi-artist song credits a correct featured-artist name the same as a correct main-artist name (`GameSessionServiceTest`)
- [x] Unit tests for win-condition bounds: 5-20 (2-3 players) and 5-15 (4-8 players), including the boundary values (`GroupServiceTest`, since `GameSessionService` reuses that validation rather than re-implementing it)
- [x] Unit tests for round rotation, both fixed and rotating DJ settings, and rotation skipping `Left` players (`GameSessionServiceTest`)
- [x] Unit tests for the active-player turn timeout, including the boundary at 90 seconds (`GameSessionServiceTest`)
- [x] Unit test: reveal fires automatically once the betting window closes, with no DJ or player trigger required (`GameSessionServiceTest`)
- [x] Integration test: full session lifecycle, admin starts, roster snapshot, several rounds, win condition hit, results export generated, state purged (`GameSessionLifecycleIntegrationTest`)
- [x] Integration test: auto-abandon path, session torn down after 10 minutes with zero connected players, confirms no export is generated (`GameSessionLifecycleIntegrationTest`)
- [x] Integration test: betting concurrency, multiple simultaneous bet attempts on the same guess, exactly one accepted, no token lost by the others (`GameSessionBettingConcurrencyIntegrationTest`, real threads against a real Postgres instance)
- [x] Integration test: betting window skipped entirely when no player holds a token (`GameSessionLifecycleIntegrationTest`)
- [x] Integration test: player disconnects mid-turn, doesn't reconnect within 90 seconds, ends up `Left`, and a later reconnect attempt after that point doesn't restore active status (`GameSessionLifecycleIntegrationTest`)

## Story 11: Real-time game sync over WebSocket

Checked against real code: no WebSocket layer exists, this is greenfield work. Based on the sync model in `ARCHITECTURE.md` (REST for group/session creation and join, WebSocket for state changes). Covers both the group and the game session, not just the session.

This batch built the general STOMP infrastructure and the full group-side half. Story 10's batch (`feature/game-session`) then built the session-side half on top of it in full: destinations, broadcast, disconnect handling, and presence registration. The two frontend tasks stay deferred project-wide for this phase of batches, backend only. See `DECISIONS.md`'s corresponding entries for the destination-naming convention, the messaging pattern chosen, and the full list of deferrals.

- [x] Add the Spring WebSocket/STOMP dependency and base config to the core service (`WebSocketConfig`, `spring-boot-starter-websocket`)
- [x] Authenticate the WebSocket handshake against the existing JWT auth (`StompAuthenticationChannelInterceptor`, validated on the STOMP CONNECT frame)
- [x] Define per-group STOMP destinations for broadcast (membership, settings changes, chat, voice signaling) and a client-to-server channel for admin actions (`GroupDestinations`); chat and voice are naming-convention placeholders only, no send/receive logic, stories 13 and 12 still own that
- [x] Define per-session STOMP destinations for broadcast (round events) and a client-to-server channel for actions (guess, bet, reveal). Built in story 10's batch (`SessionDestinations`), following the parallel convention `GroupDestinations`' javadoc had already reserved
- [x] Broadcast group events: member joined/left, settings changed, game session started (`GroupService` publishes a `GroupBroadcastEvent` via `ApplicationEventPublisher`, `GroupBroadcastListener` forwards it to the right STOMP topic); member connection changes (disconnect/reconnect) and admin transfer broadcast the same way, a natural extension of "membership changes" beyond the story's literal four events, see `DECISIONS.md`
- [x] Broadcast round events: round started, guess locked, bet placed, reveal triggered, round scored, next round. Built in story 10's batch (`GameSessionService` publishes a `SessionBroadcastEvent`, `SessionBroadcastListener` forwards it to the session's round or ended topic)
- [x] Handle disconnect, group-member half: on WebSocket disconnect, mark the member's `isConnected` flag false without ending the group (`GroupSessionEventListener` reacting to `SessionDisconnectEvent`, calling `GroupService.disconnectMember`)
- [x] Handle disconnect, in-session-player half: built in story 10's batch (`SessionSessionEventListener` reacting to `SessionDisconnectEvent`, calling `GameSessionService.disconnectPlayer`, which also starts the 90-second turn-timeout clock when the disconnecting player is mid-turn)
- [x] Wire group creation/join to register the joining client on the group's topic: the client subscribing to its group's membership topic is what registers presence in `GroupPresenceRegistry` (`GroupSessionEventListener` reacting to the subscription)
- [x] Wire game session start to register the joining client on the session's topic. Built in story 10's batch: a client subscribes to the session's round topic once `GAME_SESSION_STARTED` fires, which `SessionSessionEventListener` reacts to the same way `GroupSessionEventListener` reacts to the membership-topic subscription
- [x] Log the group-side state events this story broadcasts (membership, settings changes) on a per-group timeline: a structured `groupEvent type=... groupId=...` line (`GroupBroadcastListener`), a shape a later addition can log alongside using the same two fields
- [x] Log round events alongside the WebRTC connection lifecycle events story 12 adds. Built in story 10's batch on the same two-field shape (`sessionEvent type=... sessionId=...`, `SessionBroadcastListener`); story 12's WebRTC events, not yet built, can log alongside it later
- [ ] Frontend: keep the group/session WebSocket connection alive while navigating to other parts of the app, minimize the game to a small persistent widget instead of requiring the player stay on the game screen. Deferred: this batch is backend only, per standing instruction; revisit alongside story 28's redesign or whenever story 10's frontend lands
- [ ] Frontend: turn notification, a sound plus a clickable visual banner when it's the player's turn and the game screen isn't focused, clicking either returns them to the game. Deferred alongside the item above, and also depends on story 10's turn concept existing

Tests:
- [ ] Integration test: WebSocket connection and session state survive navigating away from the game route and back. This describes frontend routing behavior with no backend-only analog; deferred alongside the two frontend tasks above. Replaced for this batch by a real backend-testable equivalent proving the same underlying guarantee at the protocol level, see below
- [x] Integration test: a client can disconnect and reconnect to a group's topic and keep receiving broadcasts correctly, membership state isn't lost or duplicated across the reconnect (`GroupWebSocketIntegrationTest`, asserted against the real STOMP simple broker's own subscription registry)
- [ ] Integration test: turn notification fires when the player's turn starts while they're on a different route, and doesn't fire when they're already on the game screen. Deferred alongside the frontend turn-notification task, entirely frontend/game-session behavior that doesn't exist on either side yet
- [x] Unit tests for the disconnect handler, group-member half: the flag flips without ending the group (`GroupServiceTest`, `GroupSessionEventListenerTest`)
- [x] Unit tests for the disconnect handler, in-session-player half: built in story 10's batch (`GameSessionServiceTest`'s and `GameSessionLifecycleIntegrationTest`'s disconnect/turn-timeout tests)
- [x] Unit tests for JWT handshake authentication: a valid token connects (sets the STOMP session's user), an invalid or missing one is rejected (`StompAuthenticationChannelInterceptorTest`)
- [x] Tests that the right group events actually get broadcast when `GroupService`'s methods run: member joins/leaves, settings change, session starts, disconnect/reconnect, admin transfer (`GroupServiceTest`'s event-publishing tests, `GroupBroadcastListenerTest`'s destination-routing tests)

## Story 12: Voice chat

Blocked on story 11 (WebSocket layer) and story 39 (group): voice is scoped to the group's lifetime, not the game session's, and its signaling rides the WebSocket layer. Both have now shipped (backend). Confirmed against real code: `Member.isInVoice` and the member-gated `/api/groups/{groupId}/voice/join` and `/api/groups/{groupId}/voice/leave` endpoints already exist from story 39, flipping the presence flag but not broadcasting it. No WebRTC signaling relay, no TURN-credentials endpoint. The WebRTC mesh itself, `RTCPeerConnection`, `getUserMedia`, ICE handling, audio elements, and every piece of join/leave/mute UI live in the browser and belong to story 28's gameplay pass, per this file's frontend-consolidation preamble. Voice is inherently browser-side, so the backend slice is only the parts that must run server-side: relaying signaling between members, broadcasting presence, and minting TURN credentials.

Backend slice:
- [x] STOMP signaling relay: a member-gated `@MessageMapping` on `/app/groups/{groupId}/voice/signal` that forwards an SDP offer, SDP answer, or ICE candidate from one member to the group's voice topic, tagged with the sender and the intended recipient so peers route it client-side. Reuses the story 11 STOMP config, the CONNECT-frame JWT principal, and the group membership check, matching the `GameActionController` pattern
- [x] Broadcast voice presence on join and leave so other members see the roster change live, reusing story 11's group broadcast-event and listener indirection rather than a bespoke channel
- [x] TURN credentials endpoint: member-gated `GET /api/groups/{groupId}/voice/turn-credentials` returning the ICE server list a client feeds `RTCPeerConnection`. Reads the Cloudflare TURN key from a config property; when the key is absent, returns STUN-only ICE servers and omits TURN, deferred until the real Cloudflare key is provisioned in this environment
- Deferred to story 28: WebRTC mesh peer setup, `RTCPeerConnection` lifecycle, `getUserMedia`/microphone, ICE negotiation, audio elements, the join/leave/mute/deafen UI, the speaking-indicator sidebar, and its animations
- The 8-participant cap is enforced structurally: a group is capped at `Group.MAX_MEMBERS` (8), and only members can join voice, so a voice room can never exceed the group size. No separate voice-participant counter exists server-side; the mesh's own per-peer connection cap is a story 28 client concern

Tests:
- [x] Signaling relay forwards a member's offer, answer, and candidate to the voice topic, and rejects a non-member
- [x] TURN credentials endpoint is member-gated and returns STUN-only ICE servers when the Cloudflare key is absent
- [ ] Frontend: WebRTC mesh, TURN fallback engaging on a failed direct connection, and join/leave-at-arbitrary-times behavior verified against a real browser (story 28)

## Story 13: Group-scoped text chat

Checked against real code: no chat model or endpoint exists. Blocked on story 11 (WebSocket layer) and story 39 (group): chat is scoped to the group's lifetime, not the game session's, and rides the WebSocket layer. Both have now shipped (backend). Draft tasks confirmed accurate: no `ChatMessage` entity or send/receive endpoint exists. Ready.

- [x] Implement `ChatMessage` as an ephemeral Postgres row (sender, group, body, timestamp). Built as `ChatMessage` with a `group_id`, `sender_id`, `content`, and `createdAt`, table `chat_messages` in migration `V14`, scoped to the group's lifetime rather than a game session's.
- [x] Client-to-server STOMP channel to send a message, riding the WebSocket layer built in story 11. `GroupChatController` maps `/app/groups/{groupId}/chat` (destination `GroupDestinations.chatDestination`), resolving the sender from the socket's authenticated principal the same way `GameActionController` does.
- [x] Broadcast new messages to the group's STOMP topic. `ChatService` sends the persisted message to `GroupDestinations.chatTopic` through `SimpMessagingTemplate`, serializing with the application `ObjectMapper` for the same reason `GroupBroadcastListener` does.
- [x] Load message history when a client joins or reconnects to a group. `GET /api/groups/{groupId}/chat/messages` returns the most recent messages, member only, bounded by a named page-size constant.
- [x] Purge chat history when the group is deleted, matching the group's ephemeral lifecycle. The `chat_messages` group foreign key is `ON DELETE CASCADE`, so both the expiry sweep and an admin's final leave clear a group's messages with the group.
- [x] Message length limit (500 characters) and a per-user send rate limit (5 messages per 10 seconds) to prevent spam within a group. Enforced in `ChatService` against named constants, reusing `RateLimiterRegistry`; a breach rejects the send and writes the stubbed `TODO: story 34` rate-limit event through `AbuseVisibilityEvents`.
- [ ] Frontend: semi-transparent bottom-left overlay, toggled by a keybind or a clickable button, rather than a persistent input field, plain username-and-message lines, no threading (story 28, see `GAME_DESIGN.md`'s Interaction and animation section)

Tests:
- [x] Unit tests for the message length limit and the per-user send rate limit, including the boundary values (`ChatServiceTest`: at-limit passes, over-limit rejects, blank rejected, under the rate limit passes, over it rejects and fires the stubbed event, and the limit is per user)
- [x] Integration test: message history loads correctly on join and on reconnect (`GroupChatIntegrationTest`: a member reads history most-recent-first, a non-member is denied)
- [x] Integration test: chat history is gone once the group is deleted (`GroupChatIntegrationTest`: the expiry sweep removes the group and its chat rows)

## Story 39: Group

Checked against real code: no group model exists, this is greenfield work. Based on `ARCHITECTURE.md`'s Group shape and lifecycle, and `GAME_DESIGN.md`'s Groups section.

- [x] Implement `Group` and `Member` as ephemeral Postgres rows; `Member` carries a per-group display name and avatar, separate from the user's account profile
- [x] `POST` endpoint to create a group; creator becomes admin
- [x] Enforce one active group membership per user
- [x] Generate a unique 4-letter join code alongside the existing invite link when a group is created
- [x] `POST` endpoint to join a group via invite link or join code, only while the group hasn't started a game session yet
- [x] On join, prompt for a per-group display name and avatar, defaulting to the user's account values but editable; other members only ever see this per-group identity, never the account profile
- [x] Settings (playlist(s), DJ mode, win-condition card count), editable by the admin only, broadcast to all members in real time. The data model and the admin-only update endpoint (`GroupService.updateGroupSettings`) persist correctly; `updateGroupSettings` now publishes a `SETTINGS_CHANGED` event story 11's `GroupBroadcastListener` forwards to the group's settings STOMP topic, see `DECISIONS.md`.
- [x] Chat available from group creation, stored for the life of the group. Backend built under story 13, which owns `ChatMessage` and send/receive/persistence; the chat overlay UI stays with story 28. `Group` uses a plain `Long` primary key, which is all `ChatMessage` needs to attach to it. See `DECISIONS.md`.
- [x] Voice joinable and leavable at any time (see story 12 for the WebRTC mechanics). Built as a plain `isInVoice` presence flag on `Member` plus join/leave-voice endpoints that flip it; the actual WebRTC mesh/signaling belongs to story 12, blocked on this story and story 11 both shipping. See `DECISIONS.md`.
- [x] 30-minute timer from group creation to the admin starting a game session, delete the group if it fires
- [x] Admin action to start a game session (see story 10), locks the group to new members. Only the group-side state transition (`GroupService.startGameSession`) exists; story 10's own session model doesn't, so nothing calls this yet outside tests.
- [x] 30-minute timer from a game session ending to the admin starting another, delete the group and remove every member if it fires. Both timers share one `expiresAt` column swept by a scheduled job (`GroupExpirySweeper`); `GroupService.recordGameSessionEnded` is the extension point story 10 calls once a real session ends, restarting this timer. See `DECISIONS.md` for the sweep-versus-per-instance-timer design choice.
- [x] Explicit leave vs. disconnect: disconnect only flips `isConnected`, explicit leave removes membership
- [x] Admin explicitly leaves: promote the next-earliest-joined member to admin, or delete the group if none remain
- [x] Admin action to voluntarily promote another member to admin at any time, independent of leaving
- [x] On app load, check the logged-in user's active group membership and prompt to return or leave, no link-based reconnect
- [ ] Frontend: visually mark the admin, a crown icon, distinct from regular members. Deferred: this batch is backend-only, the frontend gets a full redesign under story 28.

Tests:
- [x] Unit tests for join-code generation: uniqueness, and the 4-letter format
- [x] Unit tests for the one-active-group-per-user constraint
- [x] Unit tests for per-group profile isolation: a member's account profile is never exposed through group-scoped endpoints, only their per-group identity
- [x] Unit tests for admin transfer: both the explicit-promote action and the auto-promote-on-leave path, including the no-members-remain deletion case
- [x] Integration test: full group lifecycle, create, join via both invite link and join code, admin-only settings update persists, admin starts a session, group locks to new members. The live-broadcast half of the settings update isn't tested, there's no WebSocket layer yet to broadcast over, see the settings task note above.
- [x] Integration test: both 30-minute timers, pre-session and between-sessions, including that they don't fire early or fail to fire
- [x] Integration test: explicit leave removes membership while disconnect only flips the connection flag

## Story 30: Difficulty-tuned game session generation

Restructured to exactly two top-level modes, decided: Difficulty-Based (Auto-Generated), a card set assembled on the spot for the actual players in the group, and Custom, the player starts a session from an accessible playlist or pastes a playlist link directly. "Accessible" still covers three cases: a playlist the player owns, one they're a member of, or one someone has published for anyone to use; publishing a playlist publicly stays a real capability, not a distinction that only mattered for a dropped third mode. Theme-request generation, originally absorbed from story 21, is dropped along with story 21 itself, no on-the-spot themed generation is planned; see `PROJECT_STATE.md`.

A country/language filter dimension (a "Romanian songs only" mode alongside difficulty) is also dropped, decided against separately. A difficulty-generated set defaults to international scope instead, a song counts as international if its Wikidata sitelinks count (the number of language-edition Wikipedia articles covering it) clears some threshold, a signal already validated during the metadata-sourcing spike, not new testing.

That same sitelinks count also feeds difficulty tiering directly, not just the international-scope gate, decided: sitelinks is a real popularity proxy, a widely-covered song is a widely-recognized one, and it's available the moment a song is verified, unlike the aggregate guess-correctness score below, which needs real plays to exist first. Easy weights song selection toward higher-sitelink, more widely-recognized songs; hard carries no such weighting and can pull from low-sitelink, niche/obscure catalog entries same as any other song; medium sits between the two. This also covers the cold-start case, a newly-added song with too few real guesses for the aggregate score to mean anything yet still has a sitelinks count to place it provisionally.

Three tiers of signal feed difficulty, so this works from day one rather than waiting months for enough data:
- Sitelinks-based popularity (above) is available immediately for every verified song, no play history needed.
- A per-song aggregate difficulty score (percentage of all guesses on that song that were correct, across everyone) works as soon as a song has a handful of plays, and covers first-time players with no personal history.
- A personalized layer (collaborative filtering: for a given player and song, predict correct-or-not and roughly how fast, learned from patterns across all players and songs, same technique Netflix-style recommenders use, applied to interaction outcomes instead of ratings) only adds value once there's enough per-player history to beat the aggregate baseline. Depends on story 10 shipping and real rounds accumulating; realistically months of casual play before the personalized layer clearly outperforms the simple aggregate at this project's 100-200 user scale, see `PROJECT_STATE.md`.

Inference is cheap and local: scoring the whole catalog against a specific group's players is a small numeric comparison per song, no external API call, runs in well under a second even for a full catalog, unlike the metadata pipeline which costs money per call. The only real cost is periodic retraining, a scheduled batch job, cheap at this data scale.

Backend slice built on `feature/difficulty-generation`: the difficulty model lives in the core service (`org.dariusturcu.backend.difficulty`), computed on the fly from existing `Song` and scored `Round` data with no schema change. The play-derived signal, the three group-scoring strategies, and difficulty-tuned selection are built and unit-tested. The personalized collaborative-filtering layer is scaffolded behind an interface and deferred until real `Guess` volume exists. The sitelinks count is persisted, the generation and Custom-mode endpoints are built with the lobby frontend against them, and the `Playlist` public flag shipped narrowly ahead; see the per-task notes and `DECISIONS.md`.

- [x] Add a `SongDifficulty` aggregate view or table: per-song correct-guess percentage across all historical guesses, updated as new rounds complete (built as `RoundRepository.aggregatePlacementStatsBySong`, a grouped aggregate query over scored rounds rather than a stored table or view, so it stays current with no separate refresh and needs no migration; `SYSTEM_REFERENCE.md`'s planned `SongDifficulty` table is not needed for the on-the-fly model)
- [x] Add group-level difficulty scoring for "easy": the lowest individual predicted score among the group's actual players, not the average, so the least experienced player is protected rather than left behind by a group average that looks easy on paper
- [x] Add group-level difficulty scoring for "hard": a plain average across the group's players, no floor to protect, opt-in past the easy default
- [x] Add group-level difficulty scoring for "medium": the median of the group's individual predicted scores, a middle ground between easy's worst-case protection and hard's plain average, with no extra weighting factor to tune
- [x] Persist Wikidata's sitelinks count on `Song`, both the international-scope signal and the popularity signal for difficulty-generated sets (built as a nullable `wikidataSitelinksCount` column, `V21`, written by the AI metadata pipeline through both song persistence paths; the international-scope cutoff is 5 sitelinks and the easy/medium/hard popularity weighting rides the same count, both initial heuristics per `DECISIONS.md`, to tune once real catalog data exists)
- [x] Add the sitelinks-based popularity weighting to song selection: easy weights toward higher-sitelink songs, hard applies no such weighting, medium sits between; blends with, doesn't replace, the aggregate/personalized scoring below, and is what a newly-verified song with no real guesses yet falls back on (the weighting is built into `SongDifficultyScorer` as the cold-start fallback and blends with the play-derived signal by history weight; it produces the neutral default until the sitelinks column above lands, at which point real values flow in with no scorer change)
- [x] Add the Difficulty-Based generation endpoint: given a group, a difficulty tier, and a target card count, score the full verified catalog for the group's actual players (blending the aggregate baseline for first-time players with the sitelinks-based popularity weighting, personalized predictions plug in once trained), filter to international scope, return enough songs with headroom above the win-condition card count so a session doesn't run out or repeat (built as preview-then-confirm `POST /api/groups/{groupId}/session/generate` plus `POST .../start-with-songs` through a staged pool consumed once, covered by service and session-lifecycle integration tests)
- [x] Add an `isPublic` flag (or equivalent) to `Playlist` (coordinate with story 15), and an endpoint to publish/unpublish one (built as a `boolean isPublic` field on `Playlist`, default false, with owner-only `POST /api/playlists/{playlistId}/publish` and `POST /api/playlists/{playlistId}/unpublish` endpoints on `PlaylistController`/`PlaylistService`, enforced through the existing `PlaylistAccessService.requireOwner` check; `V15__add_public_playlists_and_saved_playlists` adds the column. `PlaylistAccessService.requireRead` now also passes for any playlist with `isPublic` true regardless of ownership or membership, which is what makes a published playlist readable and importable by anyone, see story 45. Built narrowly as its own slice ahead of the rest of this story, see `PROJECT_STATE.md` and `DECISIONS.md`)
- [x] Add a public-browse endpoint (`GET /api/playlists/public`) returning every playlist with `isPublic` true as a `PublicPlaylistSummaryDTO` (id, name, color, songCount, owner), open to any authenticated user, not owner/member-restricted
- [x] Add a "Save" capability distinct from membership: a `SavedPlaylist` entity (user, playlist, savedAt) with a unique constraint on (user, playlist), `POST`/`DELETE /api/playlists/{playlistId}/save`, and `GET /api/users/me/saved-playlists`; rejects saving a non-public playlist, the caller's own playlist, or an already-saved playlist
- [x] Add the Custom-mode endpoint: start a session from a playlist the player owns, is a member of, or that's published publicly, or from a playlist link or ID pasted directly (built as `POST /api/groups/{groupId}/session/start-custom` taking exactly one of a playlist id or a pasted link, pasted links expanding to video ids and skipping videos with no catalog song, covered by service and session-lifecycle integration tests including the pasted-link flow)
- [ ] Train the personalized collaborative-filtering model on accumulated `Guess` data (story 10) once there's enough of it to evaluate (scaffolded: `PersonalizedDifficultyPredictor` is the plug point, `AggregateBaselinePredictor` is the shipped baseline; blocked until real play accumulates enough guesses to train and beat the baseline, likely months of casual play at the target scale)
- [ ] Add a scheduled retraining job for the personalized model
- [ ] Add a monitoring check comparing the personalized model's prediction accuracy against the simple aggregate baseline; if the personalized model stops beating the baseline, that's the signal it's stale and needs retraining, not just a fixed schedule
- [x] Add the frontend: a top-level choice between Difficulty-Based (Auto-Generated) and Custom; the former shows a difficulty selector (easy/medium/hard) and a review step to inspect and confirm the generated set before saving, the latter a playlist picker plus a paste-a-link field (built in the group lobby against the preview and start endpoints, covered by lobby review-and-confirm tests)

Tests:
- [x] Unit tests for the aggregate difficulty score calculation
- [x] Unit tests for all three group-scoring strategies (worst-case-protected for easy, median for medium, average for hard), including groups with a mix of experienced and first-time players
- [x] Unit test for the sitelinks-based popularity weighting: easy-tier selection biased toward higher-sitelink songs, hard-tier selection unweighted and able to draw low-sitelink songs, using a synthetic catalog with a controlled sitelinks spread (covered by the scorer tests over a controlled sitelinks spread and the selector tests over a synthetic verified catalog; the on-disk sitelinks column is not persisted yet, so the values are supplied directly through the scorer's `Optional` seam)
- [x] Unit test: a song with zero recorded guesses still gets a usable difficulty placement from its sitelinks count alone (covered against the neutral fallback while the sitelinks column is absent, and against a supplied sitelinks value through the scorer seam)
- [x] Unit tests for publish/unpublish: a non-owner is rejected the same way other owner-only playlist actions are rejected, an owner succeeds and the playlist's `isPublic` flag flips
- [x] Unit test for the public-browse endpoint: only playlists with `isPublic` true are returned, an unpublished playlist does not appear
- [x] Unit tests for the save/unsave flow: saving a public playlist succeeds, saving a non-public playlist is rejected, saving the caller's own playlist is rejected, saving an already-saved playlist is rejected as a conflict, unsaving a playlist that was never saved is rejected
- [x] Unit test: `PlaylistAccessService.requireRead` allows a non-member when the playlist is public
- [x] Migration test verifying `is_public` is not-null/default-false on `playlists` and `saved_playlists` carries a unique constraint on (user, playlist)
- [ ] Unit tests for the personalized model's predictions against a held-out set of real guesses (deferred with the model itself; no trained model or real held-out guesses exist yet)
- [x] Integration test: Difficulty-Based generation for a full-sized group (up to 8 players) returns a scored card set in well under a second (covered for an 8-player group with a 1-second budget in `DifficultySessionStartIntegrationTest`)
- [ ] Integration test: the retraining job runs and the monitoring check correctly flags a model that's stopped beating the baseline
- [x] Integration test: Custom mode starts a session from a pasted playlist link the player neither owns nor is a member of (covered in `DifficultySessionStartIntegrationTest`, expansion stubbed at the service boundary)
- [x] Integration test: publishing a playlist makes it selectable by a user who neither owns it nor is a member of it; unpublishing removes that access without affecting existing owners/members (covered by the public-browse, requireRead, and publish/unpublish tests rather than a dedicated integration test)
- [x] Frontend test: the review UI lets a user inspect and confirm the generated set before saving (covered by the lobby generate-for-review-and-confirm test)

## Story 34: First-party usage analytics

Story 42 owns the explicit domain boundary this story reads and writes against: the transactional `GameSession`/`Round`/`Guess` rows this story's game-history task reads a summary from stay in the core database and purge exactly as story 10 specifies; only the compact event/summary data this story writes goes in story 33's separate analytics store. Depends on story 33's store existing, and also on the events it instruments actually existing: story 10 (game session, no `GameSession` model exists yet), story 17 (reports, no `SongReport` entity exists yet), and story 27 (rate limiting, only a narrow one-in-flight-request-per-user concurrency gate exists today on `/api/metadata/song`, not the general per-user/per-IP time-window limiter this depends on for login/register or other endpoints). Login and playlist-creation events can be instrumented once story 33 lands, independent of the others. Event scope is deliberately count/aggregate-based, not behavioral click-tracking: usage stats for the project's own understanding (games played, session length, playlists created, songs submitted, login activity), and abuse-visibility signals that turn existing enforcement into something reviewable (rate-limit-exceeded events from stories 13/27, report submissions from story 17, failed login attempts), not a new detection mechanism of its own.

- [ ] Instrument game session start/end (with the per-game summary), login, playlist creation, and song submission events to write to the analytics store; the game-session half depends on story 10, the rest can start once story 33 lands
- [ ] Instrument rate-limit-exceeded, report-submitted, and failed-login-attempt events, for abuse visibility, not enforcement; depends on stories 13/27/17 actually shipping their enforcement first, none of which exist yet
- [ ] Build a simple internal dashboard or query surface over the collected events, including a simple way to flag a user who's crossed a rate-limit or report threshold repeatedly
- [ ] Build a per-user game history page in the frontend, querying the current user's own game-summary events from the analytics store; the transactional `GameSession`/`Round`/`Guess` rows still purge exactly as story 10 already specifies, this reads only from the separate analytics store
- [ ] No third-party trackers, matches this story's own scope and the "First-party usage analytics" framing

Tests:
- [ ] Integration test: each instrumented event type produces the expected record in the analytics store
- [ ] Integration test: the dashboard/query surface returns correct aggregates for known event data
- [ ] Integration test: a user's game history page returns only their own game summaries, not other users'

## Story 46: Playlist membership: owner/admin, granular permissions, kick and ban, per-playlist identity

Surfaced during story 28's design pass on the Edit playlist and Join by invite screens, not part of the original backlog mapping. Backend built on `feature/playlist-membership`; the Edit playlist member list is implemented in story 28, while the Join by invite identity step remains open. See `DECISIONS.md`'s 2026-09 "Playlist membership" entry for the decided shape.

- [x] Add an owner/admin concept to `Playlist`: an `ownerId` (or equivalent), set to the creator on creation; only the owner can rename, change cover/color/description, toggle `isPublic` (story 30), delete the playlist, or manage other members
- [x] Replace the plain `Playlist.users` many-to-many with a `PlaylistMembership` entity (playlist, user, `canRead`/`canWrite`/`canDelete` booleans, joined-at, per-playlist display name and avatar), coordinate with story 15 since both touch `Playlist`'s relations
- [x] Migration for existing data: no pre-story-46 playlist or song has a real owner to assign, so the migration clears every existing playlist and song rather than backfilling a guessed one; users are untouched
- [x] The owner can leave a playlist at any time; if other members remain, leadership passes automatically to whichever member joined earliest, a playlist with other members left is never without an owner
- [x] Add an owner-only endpoint to transfer ownership to any current member at any time; the previous owner stays on as a regular member with their existing grants
- [x] Gate `PlaylistService`'s existing access checks by the new grants: reading requires `canRead`, adding a song requires `canWrite`, removing a song requires `canDelete`; an owner always has all three implicitly
- [x] Add owner-only endpoints to update a member's `canRead`/`canWrite`/`canDelete` grants independently, each revocable without affecting the others
- [x] Add a kick endpoint (owner only): ends the membership, the existing invite link or code still lets the kicked user rejoin
- [x] Add a `PlaylistBan` entity (playlist, user, banned-at) and a ban endpoint (owner only): ends the membership and blocks that user's future join attempts against this playlist
- [x] Update the join-by-invite endpoint to reject a banned user's join attempt, and to accept the per-playlist display name/avatar submitted with the join request, defaulting to the account's own when not overridden
- [x] Frontend: Edit playlist's member list (per-member read/write/delete toggles, kick and ban actions), owner-only, already designed
- [ ] Frontend: Join by invite's identity step (avatar and display name, pre-filled from the account, editable before joining), already designed

Tests:
- [x] Unit tests for the owner-only gate: every owner-only action (rename, cover/color/description, public toggle, delete, kick, ban, grant changes) rejects a non-owner member
- [x] Unit tests for each of the three grants enforced independently: a member with `canRead` false can't view, `canWrite` false can't add a song, `canDelete` false can't remove one, and combinations of the three don't interfere with each other
- [x] Unit tests for kick versus ban: a kicked user's subsequent join-by-invite succeeds, a banned user's is rejected
- [x] Integration test: full lifecycle, join with a custom per-playlist identity, owner revokes a grant, the affected action is blocked, owner kicks the member, the member rejoins successfully, owner bans a different member, that member's rejoin attempt is rejected
- [x] Integration test: the migration clears every pre-existing playlist and song and leaves users untouched
- [x] Unit tests: the owner leaving a playlist with other members promotes the earliest-joined remaining member; the owner leaving alone deletes the playlist; a non-owner leaving never changes ownership
- [x] Unit tests: ownership transfer moves the owner to the chosen member and the previous owner stays a member; a non-owner can't transfer ownership; transferring to a non-member is rejected

## Story 14: Song search by link or keyword before submission

Checked against real code: `SongRepository` has zero custom query methods, no backend search capability exists. The only "search" today is `DataTable`'s client-side substring filter over an already-loaded playlist's songs, not a real query.

Backend-only for this batch, matching how the song-genre-and-print-redesign batch split its own backend/frontend work: the frontend is implemented under story 28 against the mockups in `docs/design/source/`. The remaining search wiring and broader state coverage stay tracked here for traceability and are completed or verified through story 28. See DECISIONS.md for the search-scope decision.

- [x] Add a backend search endpoint, `SongRepository` has no query methods to build on today (`GET /api/songs/search`, the first top-level `/api/songs/...` route)
- [x] Support search by artist/title keyword and by YouTube link/ID (the link-parsing logic already exists client-side as `extractYoutubeId` in `AddSongForm.tsx`; replicated server-side as `YoutubeLinkParser`)
- [x] Decide search scope: within one playlist, across the user's playlists, or catalog-wide, affects both the query and which of `PlaylistService`'s access checks apply (catalog-wide search would need one, since it isn't a per-playlist access check). Decided catalog-wide, see DECISIONS.md
- [x] Story 28: Wire `AddSongForm.tsx`'s submission flow to check search results first, so a song already in the catalog isn't resubmitted as a near-duplicate (distinct from story 16's pgvector-based similarity check; this is a plain keyword/link pre-check)
- [x] Story 28: Add the frontend search UI, replacing or extending the current client-side-only title filter in `DataTable`

Tests:
- [x] Unit tests for the search query: keyword matching and YouTube link/ID matching
- [x] Integration test: search results respect the chosen scope's access checks (catalog-wide plus plain authentication: any authenticated user can search the whole catalog, an unauthenticated request is rejected)
- [ ] Deferred to story 28: Frontend test: the search UI returns and displays results correctly

## Story 40: Catalog seeding queue and user-facing bulk import

Checked against real code: `Song` has a single `youtubeId` field and no lookup query for it, `SongRepository` has zero custom query methods. `@Scheduled` and `@EnableScheduling` are already in use in the backend (`GroupExpirySweeper`, `AnalyticsRetentionSweeper`, and the backlog drain's own sweeper), so the seeding drain is not the first scheduled job. Absorbs story 19's admin bulk-import scope, redefined as two genuinely separate mechanisms, not one:

- **Admin catalog seeding**: the admin (today, the sole developer) submits large batches of YouTube playlists or IDs to grow the catalog proactively, especially popular songs. This is patient, a multi-day backlog is fine, its whole point is reducing how often a normal user's own request needs to resolve a new song at all.
- **User on-the-spot bulk import**: any user submitting their own YouTube playlist or a list of video IDs needs those songs resolved immediately, even if some of them happen to already be sitting in the admin's backlog awaiting their scheduled turn. The two never share a queue; a song in both places gets resolved twice if the timing lines up that way, that's fine, on-the-spot always wins on priority.

Both paths depend on the same cheap first step: checking submitted YouTube IDs against the database before anything else happens. Both also depend on story 20's LLM choice for the admin backlog's scheduled drain (needs a daily quota to pace against) and on the metadata-sourcing spike's real implementation (`musicbrainz.py`, `wikidata.py`) actually existing, not just validated in `ai/spikes/`.

Story 18's verification lock (`DECISIONS.md`) changes the shape of this story's LLM dependency significantly: the story 20 spike's adversarial matrix found unanimous three-source agreement on 12 of 21 songs, meaning the LLM reconciliation call is only needed for a minority of songs, not every one. This narrows, but doesn't remove, the undecided items below.

Since story 20's spike concluded, the on-the-spot path is now two tiers, not one, both decided and validated against a real 70-song set:
- **Fast tier**: answers immediately from exactly one of MusicBrainz or Wikipedia (never both for the same song), dispatched dynamically, whichever of the two is free grabs the next song, rather than a fixed split, so a slower lane doesn't leave part of a batch waiting on it. 90% accuracy (63/70) on real data. Every fast-tier answer is provisional; the song still gets queued for the full patient pipeline afterward.
- **Patient tier**: the full lock-or-Wikipedia-plus-reconciliation pipeline from story 18. 99% accuracy (69/70). This is what the admin backlog drain always uses (no latency pressure), and what every fast-tier answer eventually gets re-run through.

Decided: how the alternate-YouTube-ID-to-`Song` mapping sequences against story 16's pgvector near-duplicate detection. This story's YouTube-ID check runs first, cheap, exact, no external calls; only a genuinely new ID reaches story 16's embedding check next. A high-confidence pgvector match against an existing `Song` means the same song under a different YouTube upload, not a new song: link the new ID into this story's alternate-ID table against that existing `Song` and stop there, no full pipeline run. The full metadata pipeline only runs once both checks come up empty.

**Decided: rate-limit contention between the admin backlog drain and on-the-spot traffic, a real concern surfaced during story 20's spike, now settled with a priority-queue design.** On-the-spot requests, including a user's playlist import, are always high priority and take precedence over the admin backlog for the shared external rate-limit budgets (MusicBrainz, Discogs, Wikidata, Wikipedia all come from the same outbound IP). The backlog drain pauses while any on-the-spot traffic is active and resumes once it's clear. Every song the fast tier resolves provisionally gets added back to the admin backlog queue afterward, to be reprocessed through the patient pipeline at low priority like everything else, this is how the fast tier's 90%-vs-99% accuracy gap gets closed, not instantly, but automatically.

- [x] Add an `ADMIN` value to `User.role` and an admin-only access check, neither exists today, absorbed from story 19; gates every admin-only endpoint below
- [x] Add a table mapping alternate YouTube video IDs to an existing `Song` (many YouTube IDs to one canonical song), separate from `Song`'s own primary `youtubeId`, so a different upload of an already-known track (a lyric video, a Topic-channel version, a re-upload) doesn't create a duplicate `Song` row or re-run the pipeline
- [x] Add a batch YouTube-ID lookup (`SongRepository` needs its first custom query methods for this): given a list of IDs, returns which are already known, checking both `Song.youtubeId` and the new alternate-ID table, no external API calls, this is the shared first step both paths below depend on
- [ ] When story 16's pgvector check returns a high-confidence match for a YouTube ID that passed this story's own exact-ID check as new, link that ID into the alternate-ID table against the matched `Song` instead of creating a new one, and stop there, skipping the full pipeline for it (the alternate-ID linking primitive exists; the pgvector match that triggers it depends on story 16, not yet built)
- [x] Add a `PendingImport` entity: a YouTube ID submitted by the admin for eventual processing, not yet resolved, with its own status (pending, processing, done, failed)
- [x] Add an admin-only endpoint to bulk-enqueue YouTube IDs (from a playlist link, a raw ID list, or both) into the backlog, running the batch lookup first so already-known songs never get enqueued at all. The playlist-link crawl runs through the AI microservice's `playlistItems.list` access (`fetch_playlist_video_ids`, `/metadata/playlist-video-ids`), the same access the on-the-spot path below now uses
- [x] Add a scheduled job that drains the backlog daily up to whatever the chosen LLM tier's daily free quota is (story 20), running the metadata pipeline per item and persisting results; follows the existing `GroupExpirySweeper`/`AnalyticsRetentionSweeper` sweep pattern (`@Scheduled` already exists in the backend, so this is not its first usage; the earlier task note claiming otherwise was stale). Per-item resolution delegates to the AI microservice through `SongMetadataService`; the fast/patient tier dispatch inside that service is validated-but-unbuilt in `ai/spikes/` and lands in stories 18/20/24
- [x] Add an admin view over backlog status: how many pending, how many processed today, quota remaining
- [x] Add a bulk-import endpoint open to any user (not admin-only), accepting a YouTube playlist link, a list of video IDs or links, or both
- [x] Playlist-link expansion: crawl the playlist to its video IDs through the AI microservice's YouTube Data API access (`fetch_playlist_video_ids` in `ai/app/metadata/sources/youtube.py`, paginating on `nextPageToken`, exposed as the internal `POST /metadata/playlist-video-ids` endpoint), called from the backend through `PlaylistExpansionService`; a submitted playlist link is expanded and merged (deduplicated) with any IDs or links submitted alongside it, on both the admin and on-the-spot paths. An invalid link or an upstream API failure surfaces as a `PlaylistImportException` (400) rather than importing nothing silently
- [x] Run the same batch YouTube-ID lookup first; only unresolved IDs proceed
- [x] Any unresolved ID from this path is processed immediately, independent of the admin backlog's schedule, even when the same ID is also sitting in that backlog waiting its turn
- [ ] Query MusicBrainz and Wikidata first with the title/channel-derived artist (per the existing metadata-sourcing spike's design); if both return zero matches, that's the trigger for an LLM extraction pass over the raw title, channel, and description, not a subjective "does this channel look like an artist" judgment, then retry the same source queries with the corrected artist
- [ ] If the retry also comes up empty, route to manual review rather than guessing, title and artist need to be verified correct, never a confidence score the way the release year gets one
- [x] Once title/artist are resolved, run the fast tier first (story 18/20's dynamic MusicBrainz-or-Wikipedia dispatch, `ai/spikes/run_fast_tier_dispatch.py`) to answer immediately, then enqueue the same song into the admin backlog at low priority so the patient pipeline (story 18's lock-evaluation logic) resolves it properly afterward (the backend orchestration is in place: on-the-spot resolves immediately and re-enqueues for patient reprocessing; the fast/patient tier dispatch itself lives in the AI microservice and is unbuilt, stories 18/20)
- [x] Implement the priority queue itself: on-the-spot requests (including playlist imports) are always high priority against the shared external rate-limit budgets (MusicBrainz, Discogs, Wikidata, Wikipedia, one outbound IP); the scheduled backlog-drain job (above) pauses while any on-the-spot traffic is active and resumes once it clears, rather than the two paths contending for the same rate-limit budget in real time
- [ ] Depends on story 24: run the three structured sources' fetches concurrently rather than sequentially for the on-the-spot path specifically, where a user is waiting on the result; the admin backlog drain has no such latency pressure and can stay sequential if that's simpler to build first
- [ ] Coordinate with story 23: `metadataRaw` should persist the curated, actually-used subset of each source's response, not the full raw API response, Wikidata's own entity dumps alone ran into the tens of KB per song during this spike's testing; at that size the 500MB Supabase free-tier cap holds roughly 10,000-50,000 songs instead of 170,000+ with a curated version
- [ ] Raw YouTube API Data specifically (a video's title, description, channel name) has its own constraint on top of the size one above, coordinate with story 43's metadata-minimization rule, one limit on size, this one on retention: YouTube's Developer Policies (Section III.E.4) require non-authorized API Data to be deleted or refreshed within 30 calendar days, it can't be persisted indefinitely as-is. If any raw YouTube fields end up inside `metadataRaw`, they need their own refresh/delete cycle on that schedule; the derived facts (artist, title, release year, sourced from MusicBrainz/Discogs/Wikidata/Wikipedia) aren't YouTube API Data and aren't subject to this
- [x] Backend: live per-song progress for the on-the-spot bulk-import path over a new per-user STOMP destination, `/user/queue/bulk-import-progress`, rather than only the request's final response. `BulkImportService` publishes a `BulkImportProgressEvent` (video id plus outcome: already-known, resolved, or unresolved) for every submitted id as its loop reaches it; `BulkImportProgressListener` forwards each one through `SimpMessagingTemplate#convertAndSendToUser`, the per-user counterpart to `GroupBroadcastListener`/`SessionBroadcastListener`. `WebSocketConfig` registers `/queue` as a broker prefix alongside `/topic`, required for per-user delivery to route at all. The admin catalog-seeding backlog does not get this: `AdminCatalogBacklogDark.dc.html` shows a backlog-count and quota summary, not live per-item progress, so that endpoint stays synchronous
- [ ] Frontend, user-facing path only: the playlist-link crawl runs in the background rather than blocking the import screen. Leaving the screen doesn't cancel it: a temporary icon appears in the left sidebar (below Group lobby) while an import is active, and a toast appears once and fades after a few seconds; both reopen the import screen showing live per-song progress (raw YouTube title/channel updating in place to the resolved title, artist, and year as each one finishes), subscribing to the `/user/queue/bulk-import-progress` destination above. Surfaced during story 28's design pass. Story 45's from-an-existing-playlist path is synchronous and needs none of this

Tests:
- [x] Unit tests for the admin-only access check, including a non-admin request rejected
- [x] Unit tests for the batch YouTube-ID lookup, including a mix of known, alternate-mapped, and unknown IDs in one batch
- [x] Unit tests for the alternate-YouTube-ID-to-`Song` mapping
- [ ] Integration test: a new YouTube ID that pgvector matches with high confidence links into the alternate-ID table against the existing `Song` and never triggers the full pipeline
- [x] Integration test: admin backlog enqueue skips already-known songs, only genuinely new IDs get added
- [x] Integration test: the scheduled drain job respects the daily quota and doesn't exceed it
- [x] Integration test: a user's on-the-spot request resolves immediately even when the same YouTube ID is also sitting in the admin backlog
- [ ] Unit tests for the artist/title verification trigger: a zero-match escalates to LLM extraction, a successful retry clears verification, a failed retry routes to manual review rather than auto-approving
- [x] Integration test: an on-the-spot song resolved by the fast tier gets re-enqueued into the admin backlog afterward, and later resolves through the patient pipeline without being skipped as already-done
- [x] Integration test: the backlog-drain job pauses while on-the-spot traffic is active and resumes once it clears, doesn't contend with on-the-spot requests for the same external rate-limit budget
- [x] AI microservice unit tests for `fetch_playlist_video_ids`: pagination across multiple pages, a playlist with zero items, the first page failing (raises `PlaylistFetchError`), a later page failing (stops and returns the partial result)
- [x] AI microservice unit tests for `extract_youtube_playlist_id`: a bare playlist id, a playlist URL's `list=` param, a watch URL's trailing `list=` param, and a plain video URL with no `list=` param rejected
- [x] AI microservice router tests for `POST /metadata/playlist-video-ids`: a successful expansion, an invalid playlist link (400), an upstream fetch failure (502), and the internal-API-key gate
- [x] Backend unit tests for `PlaylistExpansionService`: a successful expansion, an AI-service call failure, a null `video_ids` response, and `expandAndMerge`'s dedupe behavior with and without a playlist link
- [x] Backend unit tests for `BulkImportService`'s playlist-expansion-and-merge behavior, its re-enqueue-on-resolve behavior, one progress event published per submitted id with the right outcome, and a playlist-expansion failure propagating rather than importing silently
- [x] Backend unit tests for `CatalogSeedingService`'s equivalent playlist-expansion-and-merge behavior on the admin enqueue path
- [x] Backend unit test for `BulkImportProgressListener` routing an event to the right user's progress queue
- [x] Backend integration test proving the per-user STOMP wiring actually works against the real STOMP auth setup: subscribing to `/user/queue/bulk-import-progress` registers the session against the authenticated user in the real `SimpUserRegistry`, and publishing a `BulkImportProgressEvent` routes through the real broker without error
- [ ] Frontend test: leaving and reopening the import screen mid-crawl (via the sidebar icon or the toast) shows the same live progress, not a reset state

## Story 41: Submission content safety, non-music rejection and prompt-injection defense

Checked against real code: `_append_youtube_data` in `prompt.py` already delimits the video description and instructs the LLM to treat it as data, not instructions, the only defense that exists today, and only on the final synthesis call. Applies to every submission path, not just story 40's bulk import, story 40 just raises the exposure by opening submission to any user's arbitrary YouTube content instead of only what's manually added one at a time today.

Two things settled through discussion:
- Reject compilations outright, even genuinely musical ones, a compilation isn't a single song and has no one correct answer for a round.
- Precision over recall on the non-music gate: a false reject is a cheap, recoverable resubmit or manual override; a false accept puts non-music content in front of a player mid-game, a worse and more visible failure. This project already holds itself to a "professional-grade, not just working" bar (`DECISIONS.md`).

- [x] Add a hard pre-pipeline filter, no LLM involved: reject when duration falls outside a generous song-length window (roughly 1-12 minutes) combined with YouTube's own `categoryId` not being Music (10), already-fetched data, no extra API cost
- [x] For the ambiguous remainder, non-Music category but song-length duration, add an LLM classification pass (structured output: `is_song`, `is_compilation`, confidence, reasoning) reading title, channel, and description for song-like versus gameplay-like signals
- [ ] Use a match (or lack of one) against MusicBrainz/Discogs/Wikidata as a secondary signal for this same ambiguous tier, not a standalone gate: a real game-soundtrack track should resolve to an actual catalogued release, resolving to nothing across all three lowers confidence but doesn't reject outright on its own, this project explicitly wants niche/underground coverage, which also won't always resolve
  - Deferred: tuning how a source non-match lowers confidence needs real submission data to set the weighting without over-rejecting niche tracks, the same data-tuning dependency story 30 carries; the classifier ships without it rather than guessing a threshold
- [ ] Still-uncertain cases after all of the above route to manual review, not a hard reject, the same "escalate, don't guess" principle already set for artist/title verification
  - Deferred: this manual-review tier depends on the source-match secondary signal above to define "still uncertain" without a threshold; deferred with it. A confident non-music or compilation verdict rejects, and a genuine no-answer song still reaches story 18's MANUAL_ENTRY route downstream
- [x] Add a dedicated structured-output prompt-injection check (`contains_injection_attempt`, plus reasoning) run over raw title/channel/description before any extraction or classification LLM call uses that text, separate from relying on the existing delimiting alone to both resist injection and do its actual job
- [x] Apply this injection check everywhere untrusted YouTube text reaches an LLM: the existing synthesis call, story 40's artist-extraction fallback, and this story's own classification pass, not just one of the three
  - The gate runs before both LLM calls that exist today (this story's classification pass and the synthesis call). Story 40's artist-extraction fallback is not built yet; the injection check attaches to it when story 40 adds it
- [x] Decided: a flagged injection attempt writes an abuse-visibility event (story 34's scope, alongside rate-limit-exceeded and report-submitted events), an attempted injection is evidence of intent, not just an uncertain submission, so it's tracked, not silently handled the same as an honestly ambiguous song. Depends on story 34's event pipeline existing. Whether the submission itself is also outright rejected, versus routed to manual review, still needs a call, not yet made
  - Settled and built: the submission is rejected outright (`DECISIONS.md`, 2026-09). The event write is a stubbed structured log line marked `TODO: story 34` until story 34's event pipeline ships

Tests:
- [x] Unit tests for the hard duration+category filter, including the boundary values of the song-length window
- [x] Unit tests for compilation rejection
- [x] Unit tests for the injection-detection check (mocked LLM call): flags known injection patterns, passes clean text through unaffected
- [x] Integration test: a submission through any path, single-song or bulk, that fails classification never reaches the full metadata pipeline
  - Covered for the resolve path: a rejected submission is asserted never to reach source gathering or the synthesis call. Story 40's bulk-import path has since landed but doesn't have this same integration test coverage yet

## Story 17: Community song reports and confirmations

Depends on story 40 for the admin review surface, which now owns the `ADMIN` role and access check absorbed from story 19. Resolution stays fully manual: an admin decides every report, nothing here auto-changes `verificationStatus` on its own, see `DECISIONS.md`. Every card is reportable, including `VERIFIED` ones. Story 40's `ADMIN` role, `AdminAccessGuard`, and `AdminAccessRequiredException`-to-403 mapping have landed on `dev`; the admin endpoints below reuse them. All frontend affordances (report button, thumbs-up, review surface UI) belong to story 28 per this file's preamble policy. The report-submitted abuse-visibility event is a stubbed structured log line marked `TODO: story 34` until story 34's event pipeline ships, per the standing policy above.

- [x] Add a `SongReport` entity (reporter, song, message, suggested correct year, sources, status), unique per reporter per song
- [x] `POST /api/songs/{songId}/reports` to submit a report, available to any authenticated user, on any card regardless of `verificationStatus`; fires the stubbed report-submitted event
- [x] Add a report button to the song detail page (`SongReadOnlyView.tsx`), available on every card regardless of `verificationStatus` (story 28)
- [x] Add a `SongConfirmation` entity (user, song, timestamp): the community thumbs-up, distinct from a report, one per user per song
- [x] `POST /api/songs/{songId}/confirmations` to submit a confirmation, accepted only on `NEEDS_REVIEW`/`MANUAL_ENTRY` cards
- [x] Add a thumbs-up affordance to the song detail page (`SongReadOnlyView.tsx`), visible only for `NEEDS_REVIEW` cards, "is this correct?" (story 28). `MANUAL_ENTRY` cards route to the editable `SongForm.tsx` instead of the read-only view, so the thumbs-up doesn't appear there; the report button and confirmation entity remain reachable through the API regardless
- [x] Admin review endpoint `GET /api/admin/song-reports/queue` (reuses `AdminAccessGuard`, non-admin gets 403) ordered by priority, not submission time:
  1. Converging reports: two or more independent reports on the same card suggesting the same year, ranked highest regardless of current `verificationStatus`, including `VERIFIED` cards
  2. Reported, no convergence (a single report, or several that disagree with each other): ranked below convergent reports, by `verificationStatus` (`MANUAL_ENTRY`/`NEEDS_REVIEW` before `VERIFIED`)
  3. Unreported `NEEDS_REVIEW`/`MANUAL_ENTRY` cards with at least one confirmation, ranked by confirmation count, a fast confirm rather than research
  4. Unreported `NEEDS_REVIEW`/`MANUAL_ENTRY` cards with no confirmations, ranked by `verificationStatus` alone (`MANUAL_ENTRY` before `NEEDS_REVIEW`)
  5. `VERIFIED` cards with no report never appear in the queue
- [x] Admin action endpoints (reuse `AdminAccessGuard`): `POST /api/admin/song-reports/{songId}/resolve` marks the open reports upheld and applies the admin's chosen corrected year (optional) and verification status (required, one of `VERIFIED`/`NEEDS_REVIEW`/`MANUAL_ENTRY`) unconditionally, overriding even a `VERIFIED` song's locked year and status. `POST /api/admin/song-reports/{songId}/dismiss` clears the open reports
- [x] The review endpoint exposes every signal behind a card's ranking (open report count, whether they converge and on what year, confirmation count) rather than a single opaque score, the admin makes the actual call
- [ ] Review surface UI over the endpoint above (story 28)

Tests:
- [x] Unit tests for `SongReport` and `SongConfirmation` behavior: duplicate report/confirm prevention, confirmation rejected on a `VERIFIED` card
- [x] Unit tests for the queue-ranking logic covering all five priority tiers, including convergence overriding a `VERIFIED` card's default low priority
- [x] Unit test confirming the stubbed report-submitted event fires on submission
- [x] Unit tests confirming a resolve on a `VERIFIED` song overrides its locked year and status, a resolve with no corrected year leaves the year untouched, and a resolve with no open reports throws
- [x] Integration test: submitting a report end to end, visible on the admin review surface at the correct priority tier
- [x] Integration test: two reports on the same card suggesting different years don't count as convergence, and rank below a genuinely convergent pair
- [x] Integration tests: admin-only access enforced via `AdminAccessGuard` (non-admin gets 403 on the queue and on resolve), unauthenticated submission gets 401

## Story 18: Criteria for promoting a reported or newly submitted song to verified

Criteria decided, twice: an earlier `DECISIONS.md` entry ("Story 18: verification is a lock, not a score") was written without authorization and retracted; the actual criteria below are the ones later validated live against a real 70-song set (story 20's spike) and confirmed final. `TASKS.md` and `PROJECT_STATE.md` may still have stale references to the retracted entry's name, not yet cleaned up, don't trust a mention of that entry as meaning it exists. Story 23 landed `Song`'s `verificationStatus`, `confidence`, and `metadataRaw` fields, previously this story's blocker. The lock-evaluation logic and its wiring into story 40's submission pipeline are built and merged to `dev` (batch 20, `feature/verification-promotion`); the checked boxes below reflect that.

Final lock rule, validated: **only exact agreement among MusicBrainz, Discogs, and Wikidata locks with zero LLM involvement.** Anything short of that (partial agreement, a missing source, three-way disagreement) routes through Wikipedia (fetched and extracted via a dedicated LLM reading-comprehension call, DeepSeek-V4-Flash) and a four-source reconciliation call (gpt-5-nano) instead of a "2 of 3 plus an LLM judge" shortcut; that shortcut was never validated and is not what got built. Confirmed on real data: 53% of a 70-song test set locked with no LLM call at all, and the full pipeline (locked plus reconciled) hit 99% (69/70) accuracy, the one miss being a song no source had any data on at all.

- [x] Depends on story 23: `verificationStatus` field exists on `Song` before any of this can be implemented. It now does (`UNVERIFIED`/`VERIFIED`/`NEEDS_REVIEW`/`MANUAL_ENTRY`), matching this story's own state machine exactly
- [x] Add the lock-evaluation logic: given the release-year candidates from MusicBrainz, Discogs, and Wikidata, lock (set `verificationStatus` to verified, immutable from here) only when all three agree exactly; copy and adapt the validated logic in `ai/spikes/run_conditional_pipeline.py`
- [x] When the three don't agree, fetch and extract Wikipedia (the dedicated extraction prompt, `ai/spikes/combo_prompts.py`'s `build_wikipedia_extraction_prompt`, DeepSeek-V4-Flash) and run four-source reconciliation (`build_four_sources_prompt`, gpt-5-nano) to produce the year, confidence, and reasoning, sets status to needs-review, never silently promoted to verified even when the LLM answers with high confidence, the lock is reserved for source agreement alone
- [x] A genuine no-answer (no source, including Wikipedia, has anything at all) escalates to a human for manual entry of the release year, rather than guessing or leaving the song stuck. A manually-entered year gets its own status, below `needs-review`, the least-trusted tier the schema has, distinct from a year that at least one source or the LLM reconciliation actually produced
- [x] Once locked, no code path may overwrite the year, including story 17's community reports, a report against a locked song surfaces for admin judgment but can't auto-apply; enforced by `PlaylistService.EDITABLE_VERIFICATION_STATUSES` which already excludes `VERIFIED`
- [ ] Wire this into story 40's submission pipeline (both the admin backlog drain and the on-the-spot path) as the step that runs immediately after source gathering, before any LLM reconciliation call, so the LLM is only invoked for the fraction of songs the lock doesn't resolve

Tests:
- [x] Unit tests for the lock-evaluation logic: exact 3-way agreement locks with no LLM call, every other combination (partial agreement, missing source, 3-way disagreement) routes to Wikipedia+reconciliation instead
- [ ] Unit test confirming a locked song's year is immutable even via story 17's report path
- [ ] Integration test: a submission with unanimous source agreement never triggers an LLM call at all
- [ ] Integration test: a submission with no data from any source, including Wikipedia, routes to manual review rather than erroring or silently failing

## Story 24: Parallelize metadata pipeline fetches across sources

Checked against current code: story 18 rewrote `resolve_metadata` (`ai/app/metadata/service.py`) into a conditional pipeline. The three structured sources (`musicbrainz.py`, `discogs.py`, `wikidata.py`) are gathered first, a lock is evaluated (`verification.py`, `evaluate_lock`), and only when the three do not agree is Wikipedia (`wikipedia.py`) fetched and reconciliation run. Each source is a synchronous function using synchronous `httpx` through `get_with_backoff`. Parallelization applies to the first gather only: the three structured sources take the same title and artist, share nothing, and are always fetched together before the lock check, so they run concurrently against each other. The conditional Wikipedia fetch and reconciliation stay after the gather, in story 18's order, and Wikipedia is still fetched only when the lock is not met. Each source keeps its own real, already-validated rate limiter (MusicBrainz's and Discogs' adaptive limiters, Wikidata's documented per-minute limit): parallelizing runs the three concurrently against each other, per-source pacing stays in effect underneath, this story only removes the artificial serialization between the structured sources.

Since the source functions are synchronous and `resolve_metadata` is a synchronous function that Starlette already runs in a worker thread, the concurrency primitive is a `ThreadPoolExecutor`, not an async rewrite: the three calls are network-bound, so one worker per source bounds the gather's wall-clock cost by the slowest single source instead of their sum, with no change to the source modules, their rate limiters, or their error handling. An async conversion would mean rewriting the source modules, `get_with_backoff`, and both adaptive rate limiters to async with no functional gain here, so it is not done.

- [x] Run the three structured-source fetches concurrently in the first gather (`_gather_structured_sources`) with a `ThreadPoolExecutor`, one worker per source, bounded by a named worker-count constant; each source's own adaptive or documented rate limiter stays in effect underneath the concurrency
- [x] Keep the conditional boundary intact: the lock check, the conditional Wikipedia fetch, and reconciliation stay strictly after the three-source gather completes, in story 18's order, and Wikipedia is fetched only when the lock is not met
- [x] Preserve each source's failure isolation: one source raising or timing out does not sink the others, each future is resolved through a per-source guard that logs and yields an empty result on failure, on top of the sources' own internal try/except
- [x] Preserve the synthesis ordering: the gather completes before the prompt is built and the synthesis call runs, unchanged from before
- [ ] Add a per-source hard timeout at the gather boundary (a cap on how long the whole gather waits on any one source, distinct from each source's own request timeout): deferred, each source already carries its own request-level timeout through `get_with_backoff`, a gather-level cap is only worth adding alongside story 40's on-the-spot latency budget
- [ ] Wire the concurrent gather into story 40's on-the-spot path specifically and keep the admin backlog drain sequential: story 40 has since landed with both paths built; confirm whether they already exercise this story's concurrent gather or still need wiring into it
- [ ] Confirm the priority-queue rate-limit design (story 40, `DECISIONS.md`'s "Rate-limit contention" entry) still holds once fetches run concurrently: story 40's pause/resume mechanism has since been built; this is to verify the interaction, not to build either piece from scratch

Tests:
- [x] Unit test confirming the three structured sources are fetched concurrently, not sequentially (controlled per-source delay, asserts total elapsed is bounded well under the sequential sum)
- [x] Unit test confirming one structured source raising does not prevent the others' results from reaching synthesis
- [x] Unit test confirming the three structured sources are gathered and passed through to the synthesis step
- [x] Unit test confirming Wikipedia is not fetched when the three structured sources lock, and is fetched after the gather when they disagree, so parallelization did not break the conditional ordering
- [ ] Unit test for the gather-level per-source timeout: deferred with that task above

## Spike: MusicBrainz and Wikidata sourcing

Handoff item 1. Story 25 (Discogs) is separate and already `Ready`. MusicBrainz, Wikidata, and Wikipedia are now implemented and wired into the metadata pipeline (`ai/app/metadata/sources/`), validated first through real experimentation against real submitted songs rather than guessed at.

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
- [x] Build `ai/app/metadata/sources/wikidata.py`, copy and adapt `ai/spikes/wikidata_spike.py`'s validated implementation (search/select/date-extraction logic, bot-password authentication, rate-limit pacing), following `youtube.py`'s pattern for HTTP client usage, timeout, and broad-exception-to-`UNKNOWN_DEFAULTS` fallback for anything the spike didn't need to handle. The track+album comparison uses the resolved track entity's own `P361` claim to find its parent album directly, rather than a separately-guessed album title
- [x] Un-stub `ai/app/metadata/sources/musicbrainz.py`, copy and adapt `ai/spikes/musicbrainz_spike.py`'s validated implementation (track+album query, `select_best_release_group`'s earliest-candidate selection, adaptive rate limiter)
- [x] Add `ai/app/metadata/sources/wikipedia.py`, a new source, doesn't exist yet: copy and adapt `ai/spikes/wikipedia_spike.py`'s validated implementation (full-text search, `select_best_page`'s track/album-aware disambiguation, lead-extract fetch). Unlike the other three sources, this one has no deterministic parsing step: it returns each matched article's lead-section prose as-is, the dedicated LLM extraction pass (`ai/spikes/combo_prompts.py`'s `build_wikipedia_extraction_prompt`) is story 18's scope, once story 23's schema exists for it to write a result to
- [x] Wire all three into `service.py`'s `_gather_all_metadata` and add matching `_append_musicbrainz_data`/`_append_wikidata_data`/`_append_wikipedia_data` sections in `prompt.py`, all three always show their section header even with no candidates, matching `ai/spikes/combo_prompts.py`'s validated reconciliation-prompt shape rather than omitting the section
- [ ] Add `ai/app/clients/discogs_client.py` or equivalent, copy and adapt `ai/spikes/discogs_spike.py`'s validated implementation into story 25's `discogs.py` task, including the `masterless_release_years` fallback (a real, live-confirmed bug: releases with no linked master were silently discarding a correct year sitting right in the search result)

Tests:
- [x] Unit tests for `musicbrainz.py`, `wikidata.py`, and `wikipedia.py`'s request building, response parsing, and fallback behavior, mirroring `youtube.py`'s existing test pattern

## Story 26: Cache metadata pipeline results by artist/title or YouTube ID

Scope review: two other mechanisms already cover a chunk of what a cache would. Story 40's batch YouTube-ID lookup catches an already-known exact ID before the pipeline runs at all, no external calls, no LLM. Story 16's pgvector similarity check catches a near-duplicate submission (different wording, same song), which a plain artist/title or YouTube-ID cache key would miss anyway since it isn't an exact-key match. What a cache layer adds on top of both: avoiding a second full pipeline run for the same exact YouTube ID submitted twice in quick succession, before story 40's alternate-ID mapping exists to catch it structurally, or during a burst where both submissions arrive before the first is persisted. That's a narrower case than the story's original framing suggested.

- [ ] Decide, before building anything else here, whether this narrower case is worth its own caching layer at this project's scale (100-200 users), versus relying on story 40's batch lookup and story 16's similarity check once both exist, and accepting the rare double-run in the meantime
- [ ] If still worth building: add a cache layer in front of `resolve_metadata`, no cache exists today, every call re-runs the full source-fetch and LLM pipeline
- [ ] Decide cache backend: in-memory (simple, doesn't survive restarts or share across multiple AI service workers) vs. Redis/Postgres-backed
- [ ] Set a TTL or invalidation policy, metadata for a given YouTube ID rarely changes, but upstream source data can be corrected
- [ ] Coordinate with story 16: a pgvector similarity hit and a plain cache hit solve overlapping but different problems (near-duplicate vs. exact-repeat lookups), avoid building two redundant caching layers

Tests:
- [ ] Unit tests for cache hit/miss behavior
- [ ] Unit test for TTL expiration

## Spike: Local/cheap LLM option for bulk metadata processing

Handoff item 2. Its own branch, separate from the metadata-source spike, per the handoff's explicit instruction. Covers both hosted-API and locally-runnable options, any provider, closed or open-weight, the constraint is Pydantic-compatible structured output (`AGENTS.md`'s non-negotiable rule against regex-parsing LLM output), not a specific deployment shape.

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
- [x] Decide whether any shortlisted LLM candidate, and this conditional-pipeline shape (patient and fast tiers both), is worth building into the real AI microservice (stories 18/40), or whether further validation is needed first. Greenlit, see `DECISIONS.md` and story 20's own task list for the narrow remaining client-infrastructure scope
- [x] Design the report/re-verification system floated during this spike: settled in full detail since, not just the lowest-confidence-first sketch this line originally described, see story 17's five-tier priority queue and the 2026-09 "Report and confirmation resolution" `DECISIONS.md` entry
- [x] `docs/TASKS.md`'s own story 18 section and `docs/PROJECT_STATE.md`'s story 18 row used to reference a `DECISIONS.md` "verification is a lock, not a score" entry that was explicitly retracted earlier in this project (never actually authorized). Both cleaned up, no longer point at the retracted entry; the lock concept it described is the same shape this spike later validated with real data (three-source agreement = lock), so the underlying idea held up even though that specific entry never existed

## Chore: Flutter DJ-model compliance

Flutter is kept, not dropped, deprioritized behind the web app per the existing 2026-06 `DECISIONS.md` entry. In the meantime it must follow the same non-negotiable rule as the rest of the product: the DJ is never shown an embedded YouTube player, playback happens on the real YouTube app.

- [x] `GroupLobbyLight.dc.html` still reflected an older lobby layout: a sticker-card player list with stat rows and pill badges, where `GroupLobbyDark.dc.html` uses an orbiting-avatar layout with drift-animated positions and lobby-btn controls. Rebuilt to match; its `GroupLobbyChat`/`GroupLobbyEightPlayers`/`GroupLobbySettings`/`GroupLobbyTwoPlayers` variants were already in sync. Every `*Light.dc.html`/`*Dark.dc.html` pair in `docs/design/source/` is now structurally in sync, colors translated to this project's light palette rather than copied from dark.
- [ ] Check the current Flutter code for any embedded or hidden YouTube playback (an in-app WebView or player widget); not yet confirmed against the real Flutter codebase
- [ ] If one exists, replace it with a real link-out to the YouTube app, matching the 2026-07 `DECISIONS.md` entry's mechanism for the web DJ view

## Story 37: Privacy policy, terms of service, and GDPR compliance

Checked against real code: `DELETE /me` (`UserController` → `UserService.deleteUser()`) already does a real hard delete of the `User` row, not a deactivation. It's not just unaudited for `Song.addedBy` references and shared playlists, both are confirmed live bugs, see this file's Bug fixes section. No analytics exist yet (story 34), so there's nothing to disclose there until it ships.

Scope decided: exactly two dedicated legal/static pages, Privacy Policy and Terms of Service. No separate cookie-consent page is needed yet, it's already gated below on story 34 shipping, and no other legal or static page (About, Contact) is planned.

- [x] Draft a privacy policy covering what's actually collected today: auth data (username, email, OAuth provider ID), playlist/song data. Lives at `docs/legal/privacy-policy.md`, see `DECISIONS.md`
- [x] Draft terms of service. Lives at `docs/legal/terms-of-service.md`, see `DECISIONS.md`
- [x] Add a GDPR data-export endpoint: a logged-in user can download their own account, playlist, and song data; `ExportController`/`ExportService` exist today but export playlist/song content, not a full personal-data dump, don't assume they already cover this. Built as `GET /api/users/me/export` and `PersonalDataExportService`, kept separate from `ExportController`/`ExportService`
- [x] Fix the `DELETE /me` bug in this file's Bug fixes section (FK violation on `Song.addedBy`, orphaned playlists on last-member deletion), then confirm no other edge case leaves orphaned references or unexpectedly deletes other members' shared playlists. Fixed; also found and fixed a related real bug surfaced while testing the shared-playlist ownership-transfer path against real Postgres, see `DECISIONS.md`
- [ ] Add a cookie/consent notice, only needed once story 34 (first-party analytics) ships; skip until then since no third-party trackers are planned. Deliberately deferred, not a gap: there's nothing to consent to yet

Tests:
- [x] Integration test: GDPR export endpoint returns the user's complete account, playlist, and song data
- [x] Integration test: `DELETE /me` with existing `Song.addedBy` references and shared-playlist memberships behaves per the decided handling, no orphaned references, no other member's playlist unexpectedly deleted

## Story 38: Observability

Checked against real code: no Spring Boot Actuator dependency exists in `pom.xml`, no health-check endpoint exists today. The backend already uses SLF4J logging (from the story-6-era audit fixes), but there's no request-id/correlation-id to trace one user action across both services. The AI microservice swallows every pipeline and OpenAI failure into a generic `status="ERROR"` response with no alerting.

Goes deeper than a minimal setup, deliberately: metrics, logs, and traces together (Prometheus, Loki, Tempo), not just health checks and error tracking. All consumed through Grafana Cloud's free tier rather than self-hosted, self-hosting any of these means an always-on VM that doesn't fit the project's whole-deployment cost ceiling (see `DECISIONS.md`), while the free tier covers this project's scale at $0. Instrumentation itself is OpenTelemetry, the vendor-neutral standard, so nothing here locks the project into Grafana Cloud specifically.

- [x] Add Spring Boot Actuator to the core service for health/metrics endpoints, expose `/actuator/prometheus`
- [x] Add an equivalent health endpoint to the AI microservice (FastAPI has none today), expose metrics via `prometheus-fastapi-instrumentator`
- [x] Set up a Grafana Cloud free-tier account, point both services' Prometheus metrics at it — a Grafana Alloy container (`observability/alloy/config.alloy`, wired into `backend/docker-compose.yml`) scrapes both services' local endpoints (`/actuator/prometheus`, `/metrics`) via `host.docker.internal` and remote-writes to Grafana Cloud's hosted Prometheus, since neither service runs as its own container for Alloy to scrape directly
- [x] Add OpenTelemetry auto-instrumentation to both services for distributed tracing, viewable in Grafana Cloud's Tempo — a real trace, sent with `OTEL_SDK_DISABLED=false` and a real Grafana Cloud OTLP endpoint, was confirmed to land in Tempo. The AI microservice's exporter had a real bug fixed here: passing `endpoint` directly to `OTLPSpanExporter` skips the SDK's own per-signal path resolution, so every export 404'd silently until the `/v1/traces` suffix was added explicitly; confirmed via a direct HTTP check against Grafana Cloud's gateway before and after the fix
- [x] Ship both services' structured logs to Grafana Cloud's Loki — logs ship through the same unified OTLP gateway as traces instead of a separate Loki-specific push path: the backend gets a second Logback appender (`OpenTelemetryAppender`), the AI microservice gets an OTLP `LoggingHandler` attached to the `app` logger specifically, not the root logger, since attaching to root captures the exporter's own HTTP transport logs and re-exports them, an unbounded feedback loop confirmed by reproducing it before fixing it. A real log record was confirmed to land in Grafana Cloud (`204` from the OTLP gateway) after the fix
- [x] Add error tracking (Sentry, free tier) to both services — SDK wiring is in place in both services (`sentry-spring-boot-4`, `sentry-sdk`); real Sentry projects now exist for both services and real DSNs are configured
- [x] Add a request-id/correlation-id filter so one user action can be traced across both services' logs, and correlates with the OpenTelemetry trace for the same request
- [x] Build a basic Grafana dashboard: request rate, error rate, latency percentiles for both services — written as dashboard-as-code at `observability/grafana/hittiguess-overview-dashboard.json`, not provisioned into a live Grafana instance since no account exists yet
- [ ] Add uptime monitoring for the production deployment — not done, there is no production deployment yet (stories 7 and 8, hosting and database migration, are both still undecided), uptime monitoring is meaningless without one
- [x] Surface the AI microservice's per-source fetch failures and OpenAI call failures as visible alerts, rather than only the generic swallowed `status="ERROR"` response — each metadata source and the OpenAI synthesis call now logs a structured error and calls the Sentry SDK's capture path distinctly, instead of disappearing into the pipeline's generic error response
- [x] Add a periodic check against Grafana Cloud's and Sentry's free-tier usage limits, so approaching them is noticed before either starts silently dropping data or asking for payment (a daily scheduled check reads Sentry accepted errors and Grafana active series against configurable quotas and warns at 80%; each half stays silent until its credentials exist; log and trace gigabyte billing has no stable code endpoint and stays a console billing alert)

Tests:
- [x] Integration test: Actuator health endpoint reports correctly both when healthy and when a dependency (the database) is down
- [x] Integration test: a request-id set on an incoming request propagates through a core-service-to-AI-service call, appears in both services' logs, and correlates with a single OpenTelemetry trace — the backend side (filter, MDC, span attribute, RestClient interceptor) is proven against the real production code path; the AI microservice side (reusing an incoming id, echoing it, logging it) is proven independently in its own suite. This sandbox's JDK cannot open real loopback sockets between processes (the same limitation `BackendApplicationTests` is excluded for), so a literal cross-process HTTP call between the two real running services could not be executed here; `MockRestServiceServer` stands in for the AI service's HTTP boundary while exercising every other real component.
- [ ] Integration test: a metrics scrape and a log line both actually reach Grafana Cloud in a real (non-mocked) call — confirmed manually with real credentials (a trace, a log record, and Alloy's own metrics scrape all verified against Grafana Cloud's actual response), but not committed as a permanent automated test, since that needs real Grafana Cloud credentials available in CI, not configured yet

## Story 35: Public ground-truth data API

The final YouTube-terms confirmation read stays an open question (`PROJECT_STATE.md`), kept open deliberately; the build itself isn't blocked on it since the story's actual output data doesn't include anything YouTube-sourced, so it's placed as the last task before shipping rather than before starting.

- [x] Add a public read-only endpoint exposing verified `(artist, title, release_year)` triples only, no YouTube-sourced fields (built as `GET /api/ground-truth/songs`, joint MAIN-artist display string plus title plus locked year)
- [x] Filter to verified songs only, depends on story 23's `verificationStatus` field existing (the query filters on `VERIFIED`; covered by service and data integration tests)
- [x] Add pagination and rate limiting for public consumption (coordinate with story 27) (Spring page parameters with an explicit envelope, plus the general 60-per-minute anonymous bucket, covered by page-boundary and rate-limit tests)
- [ ] Final confirmation read of YouTube's terms before shipping, since the catalog's overall provenance mixes sources even though this endpoint's own data doesn't include anything YouTube-sourced

Tests:
- [x] Integration test: the endpoint returns only verified songs, unverified songs never appear
- [x] Integration test: no YouTube-sourced field (`youtubeId` or anything derived from it) appears in the response shape
- [x] Unit tests for pagination and the rate limit, including boundary values

## Story 28: UI redesign

Checked against real code: the frontend covers auth, landing, playlist/song CRUD, imports, admin views, group lobby, game session, chat/voice shell, away widget, DJ link-out, and results export. Batches A through E are implemented and wired to generated hooks and realtime clients. The remaining unchecked items below are open import states, visual/state coverage, accessibility, and broader route smoke coverage.

Scope decided: one unified redesign pass covering both the existing pages and the gameplay screens, not two separate efforts. A fresh visual direction, not constrained to the current shadcn/Tailwind theme tokens, though the underlying component library stays unless a specific component doesn't hold up under the new direction. Mockups were built as a multi-artboard canvas via the `design` skill and reviewed before implementation.

Design phase complete: `docs/design/hittiguess-design.html` covers all 53 screens across auth, playlist management, song review, import, and gameplay, plus the landing page, in both light and dark themes, iterated and reviewed directly by the project owner. Implementation through Batch E is present; the remaining tasks below are explicit verification gates and open states.

- [x] Design phase: establish the fresh visual direction (color, type, spacing, component style) and apply it across every existing page: landing, login, register, forgot-password, dashboard/playlist list, playlist detail, song detail, add song, join-by-invite. See `docs/design/hittiguess-design.html`
- [x] Design phase: extend the same visual system to the gameplay screens `GAME_DESIGN.md` specs but that don't exist as code yet: group lobby (member list, admin crown, join code/link, settings), game session/timeline (drag-and-drop cards, guess box, token count, betting window), DJ view (open-in-YouTube link-out), voice sidebar, text chat overlay, turn notification banner, the minimized "playing while away" widget state, and the results/leaderboard screen. See `docs/design/hittiguess-design.html`
- [x] Review pass against every mockup with the project owner before implementation starts, checking each gameplay screen against `GAME_DESIGN.md`'s spec for anything the design missed
- [x] Implementation: rebuild the existing pages' actual layouts to match their mockups across Batches A through D, not just their color/font tokens. The remaining route smoke and rendered comparison checks are listed below. See `docs/FRONTEND_IMPLEMENTATION_GUIDE.md` for the required per-page workflow and verification step
- [x] Implementation: build the new gameplay screens as real Next.js components/routes and wire them to stories 9/10/11/12/13/39's actual backends. Representative-state and rendered verification remain open
- [x] Component/token boundary: no longer retheme-only where a mockup's layout differs from the existing page's layout; `docs/FRONTEND_IMPLEMENTATION_GUIDE.md` supersedes the retheme-only rule for those pages. shadcn primitives (`components/shadcn/*`) are still used wherever they're the natural fit for a control (button, input, dialog, table), never replaced with hand-built markup for a form control or anything interactive; but a page's overall layout is rebuilt to match its mockup rather than kept as-is (decided in `DECISIONS.md`: Batch F keeps the primitives, layout is rebuilt per mockup)

### Batch E: Gameplay screens

- [x] Build the group lobby route and shell from the `GroupLobby*` mockups, including member presence, admin indicators, join link, group settings, and the empty, two-player, and eight-player layouts
- [x] Wire the group lobby to the generated group-management hooks and persistent group WebSocket events, with loading, forbidden, missing, and connection-error states
- [x] Bridge the browser's HTTP-only access-token cookie into the STOMP authentication flow, so the gameplay client can connect without exposing the token to JavaScript
- [x] Build the game-session route and shared round shell from the `GameSession*` mockups, including player, DJ, and spectator layouts, current-song card, timeline, token count, and persistent session connection
- [x] Implement timeline card placement and guess submission against the game-session API, including dragging, dropped, locked, and animated reveal states
- [x] Implement betting-window preparation and active states, including token-holder variants and round progression from session broadcasts
- [x] Build the DJ link-out action and audio-sharing warning, always opening the real YouTube page or app rather than embedding playback
- [x] Add the group text-chat overlay, turn notification, away widget, and voice sidebar to the gameplay shell, wired to the group-chat, WebSocket signaling, and TURN-credentials APIs
- [x] Build the results and leaderboard route from the `Results*` mockups for two-player and eight-player sessions, including the session export action
- [ ] Render every Batch E route and state at each mockup's desktop and mobile breakpoint in both themes, comparing directly against its matching source mockup
- [x] Add unit coverage for each new interactive component and Playwright coverage for lobby join, session start, placement, betting, link-out warning, chat, and results export

Live Playwright validation of the group and gameplay flows requires the backend services to be started from this same checkout.

Tests:
- [x] Frontend test: each redesigned existing page renders without regression (a smoke test per route) (every one of the 24 routes has colocated tests, verified by audit)
- [x] Frontend test: the new gameplay screens render correctly against representative mock state (empty, mid-game, varying player counts) (session tests cover active-player, DJ, spectator, and pre-round states plus guess submission)
- [x] Frontend test: the drag-and-drop timeline placement and the guess box's animated feedback behave per `GAME_DESIGN.md`'s Interaction and animation section (keyboard placement, placement feedback, feedback clearing, and guess submission are covered)
- [x] Accessibility check: color contrast and keyboard navigation for the new visual direction, specifically the semi-transparent chat overlay and the voice sidebar (chat focus and Escape handling plus sidebar labeled controls and toggle states are covered, contrast audited under Batch F)

### Batch F: component boundary and accessibility

- [x] Keep shadcn primitives for interactive controls and add focused primitives only where the current set has a gap
- [x] Improve keyboard-visible focus, controls, and readable opaque surfaces for the chat overlay and voice sidebar
- [x] Audit the affected light and dark theme colors against WCAG AA text contrast

Tests:

- [x] Run frontend lint and production build

### Visual fidelity remediation for Batches A through D

The rendered Story 28 audit compares every existing page with its authoritative Dark mockup in `docs/design/source`, with Light and mobile variants where supplied. These tasks close the concrete layout and state gaps found by that audit.

- [x] Rebuild the shared app shell states from `AppShellDark`: active session, profile panel with statistics, and voice participant rail
- [x] Match the landing desktop and mobile structure, CTA copy, card fan, feature sections, final CTA, and footer
- [x] Match login and register control placement, card sizing, spacing, and Google button treatment
- [x] Add a deterministic capture state for the transient OAuth2 redirect screen
- [x] Match Your Playlists grid placement and remove the extra top-level join action
- [x] Match Playlist Detail's permanent member panel, header proportions, toolbar, banner, and song rows
- [x] Match Edit Playlist's description, cover edit affordance, explicit save and cancel actions, invite copy action, delete panel, and member metadata
- [x] Match Join by Invite's playlist summary, cover, member avatars, and join identity controls
- [x] Match verified, needs-review, and editable song detail card dimensions, actions, cover treatment, and footer structure
- [x] Match Add Song result density and selected-song panel, with deterministic editable and locked review states
- [x] Match Explore Public Playlists card mosaics and populated grid
- [x] Match the YouTube import link step and implement the processing list, progress bar, temporary sidebar progress icon, and progress toast states
- [x] Preserve the matching choose-source and existing-playlist import layouts while adding designed loading, empty, and error states (both steps render loading, retryable error, and empty states, covered by import page tests)
- [x] Expose and render the catalog backlog's per-item queue required by `AdminCatalogBacklogDark`
- [x] Match the report queue's artist metadata, convergence count, tier badges, and designed loading, empty, and error states

Tests:
- [x] Frontend tests cover every new or changed interactive state in this remediation (colocated tests per route carry the states; the full suite passes)
- [x] Route smoke tests cover every Batch A through D route (every one of the 24 routes has colocated tests, verified by audit)
- [ ] Render every affected page at 1440x900 in Dark and Light and compare it directly with its mockup
- [ ] Render the landing page at 390x844 in Dark and Light and compare it directly with its mobile mockups

### Marketing recovery and design-source synchronization

- [x] Recover the landing and auth mockup-fidelity implementation from the preserved 2026-09-17 checkpoint, without importing unrelated Batch A changes
- [x] Render the recovered landing and login pages against their Dark desktop and mobile mockups and correct any remaining layout differences
- [x] Reconcile the structurally divergent Light mockups with their authoritative Dark counterparts: `GameSessionDJ`, `JoinInvite`, `PlaylistDetail`, and `SongDetail`
- [x] Synchronize `docs/design/hittiguess-design.html`'s embedded source files with `docs/design/source`

Tests:
- [x] Verify every embedded mockup entry matches its source file after synchronization
- [x] Capture the recovered landing and login at their mockup viewport sizes in both themes

### Marketing interaction polish

- [x] Remove the colored underlines from the three landing feature paragraphs
- [x] Make the landing and auth theme slider animate its thumb and page colors, with direct Light and Dark selection
- [x] Add the shared theme slider and a landing-page link to every authentication screen
- [x] Match the auth primary and Google button dimensions to the mockup controls
- [x] Make the wordmark waveform smoother, slower, and less repetitive

Tests:
- [x] Verify both theme choices on landing and authentication pages and capture the animated slider states
- [x] Render login and register in both themes and compare their control proportions with the mockups

### Marketing theme-switch correction

- [x] Replace the two-choice theme control with a single full-track toggle that switches theme from any click target
- [x] Match the switch track and moving thumb proportions without clipping on landing or auth surfaces

Tests:
- [x] Verify a click on both ends and the center of the switch toggles the theme once

### Marketing theme-switch animation

- [x] Animate the compact toggle thumb between its Light and Dark positions independently of the page color transition

Tests:
- [x] Verify the thumb visibly slides in both directions

### Global scrollbar styling

- [x] Add a theme-aware custom scrollbar used by every page and reserve its layout gutter to prevent content shifts

Tests:
- [x] Verify the scrollbar track and thumb in both themes on a scrollable page

### Global scrollbar refinement

- [x] Remove the scrollbar gutter from non-scrollable pages and make the scrollbar track transparent

Tests:
- [x] Verify scrollable and non-scrollable pages retain their intended layout in both themes

### Marketing hydration and CI correction

- [x] Render the saved theme only after hydration so the server and client switch markup match
- [x] Initialize waveform variation after mounting to keep component render pure

Tests:
- [x] Run frontend lint and production build, then confirm the browser has no hydration errors

### Global overlay scrollbar

- [x] Replace browser-owned scrollbars with a compact global overlay thumb that does not consume content width

Tests:
- [x] Verify the overlay appears only on scrollable pages and remains over content in both themes

## Story 47: Product ground-truth pass

Source: the owner's full feature review, which is the binding spec for everything below. Where this section conflicts with an older task, a mockup, or existing code, this section wins and the mockup gets updated to match (`docs/design` updates are tracked here, not as a separate effort). Status Needs Definition: the boxes below are captured from the review, not yet confirmed against the real code. Confirm each cluster against the code before building it.

Library (`Your playlists`):

- [x] Add an All filter beside Owned, Joined, and Saved
- [x] Give each tab its own end tile: Owned keeps New playlist, Joined gets Join playlist, Saved gets Explore public playlists
- [x] Add a Join playlist button beside Create playlist

Explore:

- [x] Add All, Saved, and Not saved filters
- [x] Make each playlist card open its playlist detail; the Save/Saved action stays on the card

Playlist titles and covers:

- [x] Enforce the six predetermined title colors everywhere a color is set, no other values
- [x] Build the four-tile mosaic cover: squared YouTube thumbnails with no black bars, placeholders filling empty tiles, thumbnails filling in progressively as songs are added

Playlist detail:

- [x] Show member circles only, opening a centered full member list popup on click
- [x] Show the ghost empty state with no songs, no in-list search box, and no redundant call to action
- [x] Scope the song search to songs inside the playlist
- [x] Start session opens gameplay with that playlist already selected
- [x] Confirm before leaving a playlist
- [x] Add the export options UI (content choice, paper size, download or print; duplex not built)
- [x] Offer invite by code and invite by link, each copying a ready message; invite URLs respect localhost versus production

Edit playlist:

- [x] Implement cover change, title, title color, description, public toggle, and invite-link copy
- [x] Confirm delete works behind its warning, and the members tab grants, kick, and ban all work
- [x] Decide the pixel-art cover rule (upload pixelized for direct database storage, same rule for profile pictures); pixelize and store won

App-wide and imports:

- [x] Audit every clickable control for the pointer hand cursor
- [x] Link songs from an existing playlist instantly
- [x] Run YouTube imports in the background with a sidebar progress indicator, hover progress, greyed pending songs in the detail view, and a return path to the live progress screen
- [x] Show catalog recommendations by default with fetch-more, and keep the add-tray contents across navigation until committed
- [x] Show continuous staged progress on single-song fetch: submitted title and channel plus the sources being consulted

Lobby, voice, and gameplay:

- [x] Animate lobby members floating per the design; keep Start game, Chat, and Settings
- [x] Make the voice sidebar collapsible everywhere, mandatory only while in a call
- [x] Order sidebar participants top to bottom with the join control after the last participant
- [x] Add the voice settings popup: speaker and microphone selection plus a test control (shipped without a prior design)
- [x] Narrow lobby settings to DJ mode (with a player picker for a fixed DJ) and cards to win; move playlist choice to a multi-select popup that merges duplicates into a temporary playlist
- [x] Close lobby popups on outside click
- [x] Block starting alone with an explanatory message (two players minimum)
- [x] Confirm before leaving the lobby; drop the stray Live label
- [x] Fix away-status reliability
- [x] Show the first-time countdown only, enforce DJ, turn, guessing, token, betting, skip, and leaderboard rules per `GAME_DESIGN.md`
- [x] Animate the unrevealed card as an audio-reactive visualizer
- [x] Offer results download options (PDF print, copy as text, CSV download; shipped without a prior design)
- [x] Verify admin pages update live with processing state

Design:

- [ ] Update the `docs/design` mockups to this section wherever they disagree (the canvas file stores its artboards in an editor-internal script block, so this needs the canvas editor, not raw text edits)

Tests:

- [x] Confirm each cluster above against the real code before building it (the gate to Ready)
- [ ] Frontend tests for every new or changed interactive state, following the story 28 verification pattern
- [x] Playwright multi-user coverage for join, lobby, full rounds, results, and the import background flow
- [ ] Voice delivery check with the synthetic-tone method, plus the contrast and motion spot checks from story 28

## Story 50: Auth hardening

Checked against real code: `User` has no `emailVerified` field, `AuthController`/`AuthService` have no password-reset endpoints at all, and there's no TOTP secret, backup codes, or any second factor anywhere in `security/`. `docs/ARCHIVE.md`'s batch 5 entry made the frontend forgot-password form honestly say "not implemented yet" rather than silently failing; this story is what actually builds it. No email-sending capability exists anywhere in the backend (`pom.xml` has no `spring-boot-starter-mail`, no third-party email SDK). Gates Beta, not Local: friends playing with real accounts need working password recovery and a real signup verification step; the project owner playing alone or with one other person locally doesn't.

Email provider: Resend, chosen for its free tier (3,000 emails/month) and simple REST API, matching this project's existing pattern of picking the smallest free-tier service that does the job (Grafana Cloud, Sentry). Needs a real account and API key from the project owner, the same account-creation pattern story 38 (observability) used.

Two-factor authentication: TOTP (an authenticator app, e.g. Google Authenticator or Authy generating a 6-digit code from a shared secret), not SMS. No third-party SMS provider, no per-message cost, and it's the standard low-cost second factor for a project at this scale.

- [x] Add the Resend Java SDK (or a plain `RestClient` call to its REST API, whichever this project's existing HTTP-client conventions favor once checked against `AiServiceConfig`'s pattern) and an `EmailService` wrapping it; document `RESEND_API_KEY` in `backend/.env.example`
- [x] Add `emailVerified` (boolean, default false) to `User`; a Google OAuth2 signup sets it `true` immediately, since Google has already verified that email, only a local username/password signup starts unverified
- [x] Add an `EmailVerificationToken` entity (user, token, expiresAt), issued on registration and re-sendable; `POST /auth/register` sends a verification email with a link/token instead of (or alongside) completing signup, and a new `POST /auth/verify-email` endpoint marks the account verified when a valid, unexpired token is submitted
- [x] Decide and enforce what an unverified account can and can't do: block login entirely until verified (the simpler rule, avoids gating every downstream endpoint individually) versus allowing login but restricting real actions; document whichever is chosen in `DECISIONS.md`
- [x] Add a resend-verification-email endpoint, rate-limited the same way other auth endpoints are (see story 27, `RateLimitingFilter`'s existing `/auth/*` bucket)
- [x] Add a `PasswordResetToken` entity (user, token, expiresAt, used), `POST /auth/password-reset/request` (accepts an email, always returns success regardless of whether the email exists, to avoid leaking which emails are registered, and emails a reset link/token only if it does), and `POST /auth/password-reset/confirm` (token plus new password, single-use, expires after a short window)
- [ ] Wire the frontend's existing forgot-password form (currently an honest "not implemented" stub, see `ARCHIVE.md`) to the new request/confirm endpoints; the route is present, but this auth flow is not part of the implemented Batch A surface yet
- [x] Add `totpSecret` (nullable, encrypted at rest or at minimum never returned by any DTO once set) and `twoFactorEnabled` (boolean, default false) to `User`
- [x] Add `POST /auth/2fa/setup` (admin/self, authenticated): generates a TOTP secret and a provisioning URI/QR code, not yet enabled until confirmed
- [x] Add `POST /auth/2fa/confirm`: the user submits one valid code generated from the new secret to prove they've actually added it to an authenticator app before `twoFactorEnabled` flips true
- [x] Add backup/recovery codes: a set of one-time-use codes generated alongside 2FA setup, shown once, each usable exactly once in place of a TOTP code if the authenticator app is unavailable
- [x] Add `POST /auth/2fa/disable` (requires the current password or a valid code, not just being logged in, to prevent a hijacked session from silently turning it off)
- [x] Change the login flow for a `twoFactorEnabled` account: `POST /auth/login` with a correct password but 2FA enabled returns a short-lived, narrowly-scoped intermediate token (not a real access/refresh pair) instead of completing login; a new `POST /auth/2fa/verify` endpoint accepts that intermediate token plus a TOTP or backup code and only then issues the real access/refresh cookies
- [ ] Frontend: the 2FA setup screen (QR code, confirmation step, backup codes display), and the login flow's second step when 2FA is required; these auth states remain open

Tests:
- [x] Unit tests for `EmailService` (mocked HTTP call to Resend, not a real send in any test)
- [x] Unit and integration tests for the email-verification flow: an unverified account can't log in (or is restricted, per whichever rule was chosen), a valid token verifies the account, an expired or already-used token is rejected, a resend request is rate-limited
- [x] Unit and integration tests for password reset: a request for a nonexistent email still returns success and sends no real error signal, a valid token resets the password and is then rejected on reuse, an expired token is rejected
- [x] Unit tests for TOTP setup/confirm/disable: an unconfirmed secret doesn't enable 2FA, a wrong code during confirm doesn't enable it either, disable requires the extra proof and a bare authenticated request alone is rejected
- [x] Integration test for the two-step login flow: a 2FA-enabled account's login with just a password doesn't issue real tokens, a correct second-factor code completes it, a wrong or reused backup code is rejected
- [x] Unit test confirming no DTO or API response ever includes `totpSecret` or an unused backup code in plain form after initial generation
- [x] Wire the frontend's existing forgot-password form to the new request/confirm endpoints (already wired: the request page calls `useRequestPasswordReset`, the confirm page calls `useConfirmPasswordReset` with a mismatch guard, both covered by colocated tests)

## LAN playtest readiness

Chore, no story: opening the local stack to other devices on the LAN for
multi-device testing before deployment. Confirmed against the code that no
behavior change is needed: all API and WebSocket URLs derive from
`NEXT_PUBLIC_API_URL`, invite links use `window.location.origin`, cookies
carry no `Domain`, and CORS plus email-link hosts are env-driven. Google
sign-in is unavailable over LAN; testers use local accounts.

- [x] Document the three LAN env values in both `.env.example` files
- [x] Add a local-network playtest section to `docs/DEV_SETUP.md`
- [x] Allow the dev server's LAN origin: Next.js blocks non-localhost origins on dev-only assets (`/_next/*` chunks return 403 with no client JS at all), so `next.config.ts` derives `allowedDevOrigins` from `NEXT_PUBLIC_API_URL` instead of hardcoding a machine-specific IP
- [ ] Manual multi-device playtest: full game plus voice, recorded here once played
- [x] Fix the pre-existing session page test type error blocking `npm run build` (found during LAN verification, untouched by the LAN branch; the mock session type now allows a null round, build passes)
- [x] Hydration warning on the login page over LAN: caused by the Dark Reader extension rewriting SVG attributes before React hydrates, not by app code; extension-free browsers hydrate cleanly. No code change; disable Dark Reader for the site since it also fights the app's own theme toggle
- [x] Lobby start flow rework from playtest feedback: playlist chip opens a tier popup (easy/medium/hard/custom) instead of a separate modal; custom opens a fullscreen multi-playlist picker with a chosen list and a back path; the standalone custom-start entry point is gone; the two-player minimum shows only as a popup on Start instead of a persistent note; settings save reports success or failure by toast

## LAN playtest findings (PC client vs laptop server)

Reported during the first multi-device playtest. Items marked reproduce-first may be stale-bundle symptoms from before the `allowedDevOrigins` fix; verify each against the fixed stack before changing code.

Confirmed bugs (verified in code, fix directly):
- [x] Clipboard copies assume `navigator.clipboard`, which is undefined over plain-HTTP LAN, so every invite and results copy throws. Add a shared copy helper with a non-Clipboard fallback and use it at all five call sites
- [x] Export renders as an inline section, not a dialog, with no preview and only info/QR plus A4/Letter. Convert to a dialog (done); combined info+QR output and extra paper sizes need backend support and land separately (see the Fix: Export paper sizes section below)
- [x] Library tabs run Owned-first with All last. Move All first and default it; put Join left of Create playlist with a code/link popup below it
- [x] Edit-playlist save has no toast and fails silently; cancel gives no feedback. Add success toast with detail redirect (already redirects) and an error message
- [x] Playlist description has no backend support at all (no column, no update field), so the edit-page description field silently drops input. Needs an entity/migration/endpoint slice before the field can work (see the Fix: Export paper sizes section below)

Reproduce-first on the fixed stack:
- [x] YouTube playlist import stuck at connecting (real bug, not stale-bundle: the page never opens the socket it gates on. Fixed by dropping the gate since expansion is REST)
- [ ] Add-by-YouTube-link metadata fetch failures
- [ ] Export download and print doing nothing
- [x] Edit-playlist save and cancel reported dead (covered by the save fix above; cancel resets the name draft, description waits on backend support)

Lobby redesign (own batch):
- [ ] Join-call button top-aligned, not bottom-aligned
- [ ] No sidebar collapse control; hide the right sidebar outside calls except on the group lobby page; match the left sidebar width
- [ ] Playlist selection as a near-fullscreen overlay with bottom lobby actions still visible
- [ ] Difficulty options (easy/medium/hard/custom) directly in playlist selection; custom opens the fullscreen multi-playlist picker with a back path; remove the standalone custom start
- [ ] Two-player minimum only as a popup on Start, never persistent
- [ ] Lobby content shrinks on zoom instead of pushing bottom actions off screen; avatar and name animate as one unit

Library and shell redesign (own batch):
- [ ] Joined tab copy and explore call-to-action icon (blocked on mockup direction for new profile/settings pages)
- [ ] Sidebar logo animation genuinely random per sound-wave input, not a fixed loop
- [ ] Group nav button only when in a group, stronger highlight on the group page, Play routes into the existing group
- [ ] Playlist member stack matches the uploaded reference (overlapping avatars plus overflow count)

Settings and chat (own batch):
- [ ] Restyle settings dropdowns off the native control look, verified by screenshot
- [ ] Chat panel floats above its button like the settings panel does

Feedback polish (cross-cutting, own batch):

Audited against the real code: every page-level `useGet*` query already renders a spinner, `animate-pulse` block, or placeholder text while loading except the two gaps below; most mutations show a pending state on their triggering control but skip the toast half; there's no exit-animation tooling (no `framer-motion` or equivalent) anywhere in the repo, so this batch adds entrance motion via the already-used `tw-animate-css` `animate-in`/`fade-in-0` utilities (the same primitive every shadcn overlay already uses), not new exit-animation infrastructure.

- [x] Loading: the playlist edit page renders `useGetPlaylist`/`useGetMembers` with no `isLoading` handling at all, showing blank name/color/description fields and an empty member list on first paint. Add a loading skeleton for the whole page
- [x] Loading: the admin catalog backlog's stat tiles swap to a bare `"..."` string while loading; use the existing shadcn `Skeleton` component instead
- [x] Loading: the explore and library pages showed bare "Loading..." text; swapped both for a `Skeleton` card grid matching the loaded layout's shape
- [x] Toasts: playlist edit page's `uploadCoverImage`, `selectColor`, `togglePublish`, and the invite-link copy button; `MemberRow`'s `toggleGrant`/`kickMember`/`banMember`
- [x] Toasts: explore page's save mutation (no unsave action exists on this page, a saved card just shows disabled)
- [x] Toasts: group lobby's `startSession` and `leaveGroup` mutations had no error feedback at all (button just stopped spinning on failure); `generateSet`/`startWithSongs`/`startCustom` already show errors inline via `startError` and their successes navigate or open a visible review step, so those were left as-is rather than adding a redundant toast
- [x] Toasts: admin catalog backlog's enqueue mutation; admin report queue's resolve/dismiss mutations
- [x] Toasts: copy-from-playlist import mutation gets a success toast naming the count added (`isError` already renders inline and the YouTube-link expand mutation's failure already renders inline too, so neither needed a toast on closer look, just the copy-from-playlist success case was genuinely silent)
- [x] Toasts: group and playlist join-by-invite mutations already render their failure inline (`setError`) and their success navigates, so no toast was actually missing here on closer look, corrects the earlier audit
- [x] Motion: apply `animate-in fade-in-0` (with `slide-in-from-top-1` where a list is involved) to the playlist edit page's loaded content, the admin backlog and report queue lists, and the explore/library grid, matching the existing Radix overlay convention rather than inventing a new one
- [ ] Motion: exit transitions for a removed list item (kicked member, deleted song, dismissed report) are out of scope: they need delayed-removal state management no component in this codebase has today, not a one-line class addition. Left for a dedicated pass if wanted later

Tests:
- [x] Frontend lint and the two password-flow page tests stay green
- [x] Frontend tests for the new toast coverage on the playlist edit page, the explore save action, and the admin backlog/report queue actions
- [x] Frontend test for the playlist edit page's loading skeleton rendering before `useGetPlaylist` resolves

## LAN playtest findings, batch 4 (library, shell, settings, chat)

- [x] Playlist member stack shows the overlap plus overflow count per the reference
- [x] Explore call-to-action carries an icon
- [x] Sidebar logo bars randomize per mount instead of a fixed loop
- [x] Group nav button exists only in a group, stronger highlight on the group page
- [x] Settings dropdowns use the shadcn Select instead of native controls
- [x] Lobby chat floats above its button
- [ ] Profile and settings pages need mockups before building (blocked on owner direction)

Tests:
- [x] Stack overflow, Radix settings selects, group button rules, floating chat variant, plus screenshot verification of settings and chat

## Security and bug audit (2026-09)

A full-app audit of the core service, the AI microservice, and the frontend, against `dev` plus the open gameplay fix branches. The two two-factor findings are confirmed against a running stack; the rest are confirmed by reading the code unless marked unverified. Batches are ordered by severity; each is its own `fix/*` branch.

Batch 1, authentication (critical):
- [x] The two-factor pending token issued after a correct password is accepted as a full access token: `JwtUtil.validateToken` checks only the subject and expiry, and neither `JwtAuthenticationFilter` nor `StompAuthenticationChannelInterceptor` rejects the `two_factor_pending` token type. A password alone reaches every authenticated REST endpoint and WebSocket. Reject any token carrying a non-access token type everywhere a real access token is expected
- [x] `POST /auth/2fa/setup` sets `twoFactorEnabled` to false on an account that already has two-factor on, with no password or code, which bypasses the password-or-code rule on `/auth/2fa/disable`. Refuse setup while two-factor is enabled, or require the same proof `disable` does
- [x] `/auth/**` is exempt from CSRF while production cookies are `SameSite=None`, so the cookie-authenticated `/auth/2fa/setup`, `/auth/2fa/confirm`, `/auth/2fa/disable`, and `/auth/logout` accept cross-site requests. Require the CSRF token on every `/auth` endpoint that acts on an existing session
- [x] Two-factor codes can be replayed within their time step. Record the last accepted time step per user and reject a repeat
- [x] Login and two-factor verification are limited per IP only. Add a per-account failure limit with a cool-down

Batch 2, WebSocket authorization (critical):
- [x] STOMP authenticates only CONNECT; SUBSCRIBE is never authorized, so any logged-in user can subscribe to another group's chat, voice, settings, and membership topics and another session's round and ended topics by id. Authorize every SUBSCRIBE against group membership or session player membership
- [x] `VoiceSignalingController` relays every offer, answer, and ICE candidate (which carry players' IP addresses) to the whole group voice topic and relies on the client to filter by target. Deliver each signal only to its target member through a user destination
- [x] Subscribing to a session's round topic registers presence in `SessionPresenceRegistry` without checking the subscriber is a player in that session. Register only players

Batch 3, dependencies (critical):
- [x] `next` 16.3.2 is inside the range of published unauthenticated remote code execution advisories (16.0.0 to 16.3.2, including the Image Optimization API). Upgrade to a patched release
- [x] `npm audit` also reports high-severity `sharp` (libheif) and `fast-uri` advisories and a moderate `baseline-browser-mapping` one. Upgrade them
- [x] `npm audit` now also reports a critical `orval` advisory (fixed in 8.37.0, which also clears the high `js-yaml` one it pulls in), high `brace-expansion` and `browserslist` advisories, moderate `@humanfs/node`, `hono`, and `qs` ones, and a low `postcss-selector-parser` one. Upgrade them, and regenerate the API hooks with the upgraded `orval` so the generated code matches it
- [x] The AI microservice's dependencies are unpinned `>=` ranges with no lockfile. Pin them with a lockfile so builds are reproducible and auditable

Batch 4, game session reliability (critical and high):
- [x] `GameSessionService.reconnectPlayer` has no caller, so a player whose socket closes once (a reload, navigating away and back) stays `isConnected: false` with `disconnectedAt` set for good. An active player who reloads mid-turn is marked `Left` 90 seconds later while playing, and once every player has closed a socket at any point `zeroConnectedSince` sticks and the session is abandoned 10 minutes later mid-game. Call it when a player's socket subscribes to the round topic, and only mark a player disconnected once their last socket for that session closes, since each client holds several
- [x] A session starts with as few songs as players plus one, and when the queue empties `popNextSongId` throws inside the scheduled round advance: the session is never completed or abandoned and every client stays on the reveal. End the session with the current standings when no song remains, and raise the start threshold so a typical game can reach the win condition
- [x] Round timers, group lifecycle timers, player presence, and session results are held only in memory, and nothing recovers them on startup. A restart strands in-flight rounds in `COUNTDOWN`, `BETTING`, or `SCORED` and loses results. Reschedule pending round and group effects from persisted state on startup
- [x] An active player who stays connected but never places a card holds the round forever; the turn timeout only starts on disconnect. Add an idle placement timeout
- [x] `skipBetting` accepts any player, including the active player and the DJ, so one player can end everyone else's betting window. Limit it to eligible bettors, and decide whether it takes every eligible bettor or one
- [x] Every repeated correct artist or title submission increments the session-long leaderboard tallies again. Count each artist and title at most once per player per round
- [x] Guess results are never sent back, so neither the guesser's correct/incorrect animation nor the "guessed the artist" toast from the mockups can render. Deliver each guess result to its guesser, and broadcast a correct guess without the answer

Batch 5, data access and integrity (high):
- [ ] `GET /api/sessions/groups/{groupId}/results` has no membership check, and group ids are sequential, so any logged-in user can read any group's results. Restrict it to the session's players
- [ ] `PlaylistService.updateSong` edits the shared catalog `Song` row. Adding an existing YouTube id to any playlist links that row, so a user with write on their own playlist can change the year or title of an unverified song in every other playlist and game using it. Stop direct edits of a song linked to playlists the editor can't write, or route them through the report flow
- [ ] `GET /api/playlists/{playlistId}/cover` is public, so a private playlist's cover is readable by anyone with its id. Apply the playlist read check, or confirm covers are meant to be public
- [ ] Group display name and avatar URL on create and join have no length or format validation. Add bounds, and restrict avatar URLs to the schemes and hosts the app serves

Batch 6, resource abuse (high):
- [ ] `PixelArtImageService` decodes an upload in full before checking its dimensions, so a small image declaring huge dimensions can exhaust memory. Read the dimensions through an `ImageReader` before decoding
- [ ] `POST /api/bulk-import` takes an unbounded list of ids plus an expanded playlist and resolves every unknown one synchronously through paid OpenAI and YouTube calls, and checks write access on the target playlist only after that work. Cap the batch size, add a per-user daily quota, and check target access first
- [ ] The group join code is four letters with no join-attempt limit beyond the general request limit, and groups have no kick, so strangers can guess into open lobbies and can't be removed. Add a join-attempt limit and an admin kick

Batch 7, error handling and redirects (high and medium):
- [ ] `GlobalExceptionHandler` maps every `RuntimeException` to 400 with its raw message, so Spring's `AccessDeniedException` returns 400 instead of 403, `ResponseStatusException` loses its status, and internal messages (database constraint names, PDF failures) reach clients. Map the specific exceptions to their statuses and return a generic message for everything else
- [ ] The `returnTo` checks in `ReturnToOAuth2AuthorizationRequestResolver`, `OAuth2AuthenticationSuccessHandler`, and `frontend/lib/return-to.ts` reject `//` but accept `/\`, which browsers treat the same way, so a crafted login link can likely redirect off-site after sign-in (unverified). Reject backslashes and any value that resolves to another origin
- [ ] Without a Resend key, verification and password-reset links are written to the application log, so anyone with log access can take over an account. Refuse to start in production without an email key, and never log the link

Batch 8, hardening (low):
- [ ] The frontend sends no Content-Security-Policy, `frame-ancestors`, or `Referrer-Policy`. Add them in `next.config.ts`
- [ ] The results CSV export writes display names without neutralizing leading `=`, `+`, `-`, or `@`, so a name can run as a spreadsheet formula. Prefix such cells
- [ ] Swagger UI and `/v3/api-docs` are public in production. Disable them outside development
- [ ] The AI microservice compares the internal API key with `!=` and would accept an empty header if the key were left blank, and exposes `/metrics` and the FastAPI docs unauthenticated. Use a constant-time compare, refuse to start with an empty key, and restrict or disable those routes
- [ ] `GroupService.joinGroup` checks the 8-member cap without a lock, so concurrent joins can exceed it. Enforce the cap atomically

Existing test failures:
- [x] `frontend/app/(app)/groups/[groupId]/page.test.tsx` never finishes and pins a worker, which stalls `npm run test`
- [x] `CatalogSeedingIntegrationTest` fails two cases on `dev` with a `TransientPropertyValueException` (a `Song` referencing an unsaved `User`)

Tests:
- [x] Batch 1: integration tests that a pending token is refused on REST and STOMP, that setup can't disable two-factor, that cross-site `/auth` requests without a CSRF token are refused, that a replayed code is refused, and that repeated failures lock the account temporarily
- [x] Batch 2: integration tests that a non-member's SUBSCRIBE to group and session topics is refused and that a voice signal reaches only its target
- [x] Batch 3: the frontend build, lint, and unit and end-to-end suites pass on the upgraded dependencies, and `npm audit` reports no high or critical advisory
- [ ] The e2e login helpers wait for the `/playlists` load event with `page.waitForURL`, and in a full local run one or two login-dependent specs (the two-player round, and sometimes core flows) time out there even though the page has already reached `/playlists`. The same spec fails the same way on `dev` before the batch 3 upgrades. Find what holds the load event open and make the login wait on the rendered page instead
- [x] Batch 4: service tests for reconnect on subscribe, disconnect only after the last socket, graceful completion on an empty queue, startup rescheduling, the idle placement timeout, skip-betting eligibility, once-per-round tallies, and guess result delivery
- [ ] Batch 5: integration tests that non-members can't read results, that a shared song can't be edited through another user's playlist, and that invalid display names and avatar URLs are refused
- [ ] Batch 6: unit tests that an oversized-dimension image is refused before decoding, and integration tests for the bulk import cap, quota, and up-front access check
- [ ] Batch 7: handler tests for each mapped status and the generic message, and unit tests for `returnTo` rejecting `/\` and other-origin values on both sides
- [ ] Batch 8: tests for CSV cell neutralizing, the AI key compare and empty-key startup refusal, and the atomic group cap
