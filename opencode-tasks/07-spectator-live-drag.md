# 07. Other players do not see the active player's card move

Branch: `feature/spectator-live-drag`. Backend and frontend. Read the design first: artboards
`GameSessionCardDraggingDark.dc.html` (active player) and `GameSessionCardDraggingSpectatorDark.dc.html`
(everyone else) in `docs/design/hittiguess-design.html` (task 10 explains how to extract one artboard).

## What is wrong

- The active player's drag publishes only the hovered gap: `previewPlacement(position)` in
  `frontend/hooks/use-game-session-realtime.ts` sends `{ position }` to `/app/sessions/{id}/preview`,
  handled by `GameActionController.previewPlacement` and `GameSessionService.previewPlacement`, which
  broadcasts a `PLACEMENT_PREVIEW` round event.
- In `frontend/app/(app)/sessions/[sessionId]/page.tsx`, spectators render a small static card in the
  hovered gap (around line 552: `<MysteryCard size="small" className="gameplay-wobble scale-90" />`,
  no `levels`, so no live waveform). Before the drag starts, spectators see no card at all; the
  floating "Your card" card (around line 625) is only rendered for the active player.
- The owner wants every player to see exactly what the active player sees: the floating card before the
  drag, the card following the drag, its lift and wobble, the hovered gap, and the same waveform.

## What to build

1. Message: extend the preview payload with the card's position relative to the timeline track, so it
   maps onto any screen size: `{ position, x, y, phase }` where `x` and `y` are fractions (0 to 1) of the
   timeline element's bounding box (y can go below 0 when the card is above the timeline), and `phase`
   is `IDLE` (card floating, not grabbed), `DRAGGING`, or `DROPPED`. Keep `position` as the hovered gap.
2. Backend: accept the new fields in `PlacementPreviewRequest`, validate ranges (clamp x to [0,1] and y to
   [-2,2]), keep the existing check that only the active player of the current round may preview, and
   relay them in the `PLACEMENT_PREVIEW` event. Do not persist them. Rate limit per player to about 30
   messages per second; drop extra ones silently.
3. Active player: publish on pointer move while dragging, throttled with `requestAnimationFrame` to at
   most about 20 per second, plus one message on grab, on drop, and when the round starts (`IDLE`).
4. Spectators: render the same `MysteryCard` the active player sees, with `levels={audioLevels}` (every
   player already receives the song stream, so their own `useAudioLevels` gives the same waveform), at
   the mirrored position: floating above the timeline in `IDLE`, following `x`/`y` in `DRAGGING` (animate
   between updates with a short CSS transition, about 60ms, so it does not stutter), and in the gap in
   `DROPPED`. Match the spectator artboard for size, rotation, lift, and the caption.
5. Remove the old static small spectator card.

## Tests

- Backend: a non-active player's preview is rejected; values are clamped; the event carries the fields.
- Frontend: the drag publishes throttled previews with fractions of the timeline box; a spectator
  renders the card at the mirrored position with levels; `IDLE` shows the floating card to spectators.
