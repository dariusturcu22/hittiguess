# 04. Countdowns differ between machines (3 seconds on one, 30 on another)

Branch: `fix/countdown-clock-sync`. Backend and frontend.

## What is wrong

The server publishes absolute deadlines on every round (`RoundDTO`: `placementEndsAt`,
`countdownEndsAt`, `bettingWindowEndsAt`, `nextRoundStartsAt`). Every client turns them into "seconds
left" by subtracting its own clock:

- `frontend/lib/gameplay-round.ts`: `secondsUntil(deadline, now)` and `fractionRemaining(...)`, called
  with `now` from `Date.now()` ticking in `frontend/app/(app)/sessions/[sessionId]/page.tsx`
  (around lines 265 and 342).
- `frontend/components/gameplay/round-intro.tsx` line 28 uses `Date.now()` directly.
- `frontend/components/active-session-widget.tsx` uses `secondsUntil` too.
- `page.tsx` around line 462 derives the intro end from `placementEndsAt` minus constants.

A computer whose system clock is 27 seconds behind shows 30 when the server means 3, which is exactly
what the playtest saw. Nothing corrects for clock offset.

## What to build

1. Backend: add `Instant serverTime` to `RoundDTO` (or to the session DTO that contains the round,
   whichever every round payload passes through; check `SessionMapper` and the realtime round event
   payloads in `GameSessionService`/the websocket broadcast). Set it to `Instant.now()` at the moment the
   DTO is built. Document it in `docs/SYSTEM_REFERENCE.md` next to the deadline fields.
2. Regenerate the Orval client (`frontend/hooks/models`) for the new field. Use the pinned Orval
   version from `frontend/package.json`; generate from a spec saved from a locally running core (start
   it in WSL), and revert line-ending-only diffs in unrelated generated files.
3. Frontend: a small module `frontend/lib/server-clock.ts` holding the current offset
   (`serverTime - Date.now()` at the moment a payload arrives), smoothed (median of the last 5
   samples) and exposed through `serverNow()` and a `useServerNow(tickMs)` hook. Every place listed above
   uses server time instead of `Date.now()`. Record a sample wherever session or round payloads arrive
   (the session query's `select`/`onSuccess`, and the realtime round event handler in
   `frontend/hooks/use-game-session-realtime.ts`).
4. Network delay makes the offset slightly early (by half the round trip, usually under 100ms). That
   is acceptable; do not add a time-sync protocol.

## Tests

- Backend: the mapper sets `serverTime`; a DTO serialization test includes it.
- Frontend: `server-clock` unit test (offset from samples, median smoothing); `secondsUntil` with a
  client clock 30 seconds behind still returns the server-relative value; round intro and active
  session widget use server time (mock `Date.now`).
