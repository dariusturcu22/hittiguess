# 10. Gameplay screens do not match their design proportions

Branch: `fix/gameplay-design-proportions`. Frontend only. Requires task 08 (the session page renders
inside the 1288x900 `GameStage`). This is the largest visual task; split it into one commit per phase.

## Get the design

`python opencode-tasks/tools/extract_artboard.py --list` lists all artboards. The gameplay ones (dark
versions; light versions only differ in colors):

| Phase | Artboard |
|---|---|
| Round intro | GameSessionRoundIntroDark |
| Active player, card floating | GameSessionPlayerDark |
| DJ view | GameSessionDJDark |
| Dragging (active / spectator) | GameSessionCardDraggingDark, GameSessionCardDraggingSpectatorDark |
| Dropped, locked | GameSessionCardDroppedDark, GameSessionCardLockedDark |
| Betting countdown | GameSessionBettingPrepDark, GameSessionBettingPrepTokenHolderDark |
| Betting open | GameSessionBettingActiveDark, GameSessionBettingActiveTokenHolderDark |
| Reveal | GameSessionRevealDark |
| Guess feedback | GameSessionTokenEarnedDark, GameSessionIncorrectGuessDark |

Extract one or two at a time into `.design-artboards/` and read their CSS. Each is 1440x900 including
both 76px rails, so the game content is the 1288px between them.

## What is wrong

Text is too big or too small, timeline cards are the wrong size, and many elements are too close or
misaligned compared with the artboards. Task 08 removed the per-element shrinking; this task makes each
element match the design.

## What to build

Files: `frontend/app/(app)/sessions/[sessionId]/page.tsx`, `frontend/components/gameplay/game-pieces.tsx`
(`CARD_SIZE_CLASSES`, `SongCard`, `MysteryCard`, token pile, avatars), `gameplay-timeline.tsx`,
`round-intro.tsx`. For each phase, compare top to bottom:

1. Header: round number, the subtitle under it (must never be covered by a card), player strip with
   tokens and card counts.
2. Above the timeline: floating card, DJ panel, countdown ring, betting prompt.
3. Timeline: card size (the design's `.tcard` is 168x168 with a 5px border and 22px radius), gaps, the
   dashed drop gaps, year labels, edge fade.
4. Caption under the timeline: font size and distance.
5. Footer actions: guess inputs, Leave game, song volume.

Use the artboard's exact pixel values (Tailwind arbitrary values are fine). Keep colors and tokens.

## Tests

- Playwright: screenshot each phase at 1440x900 using the mocked-session approach already in
  `frontend/e2e/batch-e-group-lobby.spec.ts` ("gameplay session fits every desktop viewport"), and put
  them next to the artboard renders in the PR description.
- Update unit tests that assert class strings you changed.
