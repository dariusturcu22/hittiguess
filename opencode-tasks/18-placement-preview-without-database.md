# 18. Every live drag frame loads the whole game session from the database

Branch: `fix/placement-preview-gate`. Backend only. Files:
`backend/src/main/java/org/dariusturcu/backend/service/GameSessionService.java` (`previewPlacement`,
around line 476, and the places that start, close, and end rounds), a new class next to it, and
`backend/src/test/java/org/dariusturcu/backend/service/GameSessionServiceTest.java`.

## What is wrong

Task 07 made the active player's browser send the dragged card's position up to 20 times a second
(`frontend/lib/placement-preview.ts`, `PLACEMENT_PREVIEW_MINIMUM_INTERVAL_MILLISECONDS = 50`). The
messages travel over the STOMP WebSocket to `GameActionController.previewPlacement`, which calls
`GameSessionService.previewPlacement`. For every single frame that method:

1. opens a transaction (`@Transactional(readOnly = true)`),
2. loads the session with `gameSessionRepository.findById`, then lazily loads its players, its rounds,
   and the active player's timeline (`findPlayerByUserId`, `requireCurrentRound`,
   `player.getTimeline().size()`),
3. uses all of that only to check that the sender is the active player, that the round is still
   `AWAITING_PLACEMENT`, and that the gap index is inside the timeline,
4. then publishes the frame to the other players.

The database is Neon, a remote Postgres, so a drag costs about 20 round trips of several queries per
second, adds database latency to every relayed frame, and holds connections from the shared pool that
real game actions need. None of the checked facts change during a drag; they only change when a round
starts, locks in, is scored, or the session ends.

## What to build

1. A new Spring component, for example `PlacementPreviewGate`, holding one in-memory entry per session
   in a `ConcurrentHashMap<Long, Entry>`. An entry has the round id, the active player's user id, the
   active player id, and the active player's timeline size. The core runs as one replica and already
   keeps other session state in memory (docs/DECISIONS.md, "2026-10-17 | Core and AI run one always-on
   replica on Azure", and `SessionResultsStore`), so a process-local map is acceptable.
2. Keep the gate current from the events `GameSessionService` already publishes, after the transaction
   commits: listen to `SessionBroadcastEvent` with `@TransactionalEventListener(fallbackExecution = true)`,
   like `websocket/SessionBroadcastListener`. A round event whose `RoundDTO.status` is
   `AWAITING_PLACEMENT` opens or replaces the entry. Any other round status removes it. `SESSION_ENDED`
   removes it. Read the publish sites first (`publishRoundEvent`, the `SESSION_ENDED` publishes around
   lines 399 and 413, and every `round.setStatus(...)` call) and make sure each one that changes the
   active player or the round status produces an event the gate sees. If an event does not carry the
   timeline size or the active user id, add what is missing to the gate's update call instead of
   reading the database per frame.
3. Rewrite `previewPlacement` so the normal path never touches the database: rate limit, read the
   entry, compare the sender's user id with the entry's active user id, check the gap index against
   the entry's timeline size, publish. Remove `@Transactional` from it. A frame for a session with no
   entry while the round is not awaiting placement is dropped silently, as today.
4. Cache miss: after a restart the map is empty while a round is in progress. On a miss, load the
   session once (in its own read-only transaction, through a separate bean method so the proxy
   applies), fill the entry if the round is awaiting placement, and use it. Later frames hit the cache.
   Guard the miss so many frames arriving together cause one load, not one each
   (`computeIfAbsent` or a per-session lock).
5. Keep the existing behavior for a sender who is not the active player (`AccessDeniedException`) and
   for an out-of-range gap (`IllegalArgumentException`), and keep the x/y clamping and the rate limit.
6. Append a dated entry to `docs/DECISIONS.md`: drag previews are validated from in-memory round state
   kept current by the round events, and the database is read only on a cache miss. Update the live drag
   paragraph in `docs/SYSTEM_REFERENCE.md`.

## Tests

- `GameSessionServiceTest` (or a new `PlacementPreviewGateTest`): after a round starts, a burst of 50
  preview frames publishes them without calling `gameSessionRepository.findById` (verify with Mockito
  `never()` after the first load).
- A frame after `ROUND_SCORED` or `SESSION_ENDED` is dropped and loads nothing.
- A cache miss loads once for a burst of frames, then serves from the cache.
- A non-active sender is still rejected, and an out-of-range gap still throws.
- A new round with a different active player replaces the entry, so the previous player is rejected.
