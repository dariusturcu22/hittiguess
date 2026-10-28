# 08. Game screens: lay out at design size, scale evenly to the window

Branch: `fix/game-screen-scale`. Frontend only. Do this before tasks 09 and 10.

## What is wrong

Story 53 made the lobby and game screens fit the viewport without scrolling by adding height-step
variants to many elements (`short:` = viewport height 860px or less, `very-short:` = 700px or less,
defined in `frontend/app/globals.css`). There are 35 such classes in
`frontend/app/(app)/sessions/[sessionId]/page.tsx`, 24 in `frontend/app/(app)/groups/[groupId]/page.tsx`,
and more in `frontend/components/gameplay/*.tsx`. Each element shrinks on its own schedule, so on a
laptop some text and cards shrink while others do not, and the screen no longer looks like the design.

The designs (`docs/design/hittiguess-design.html`) are all 1440x900 artboards that include both 76px
rails, so the content area between the rails is 1288x900.

## What to build

1. A `GameStage` component (`frontend/components/gameplay/game-stage.tsx`) that renders its children in
   a fixed 1288x900 box and scales that box uniformly to fit the space it is given:
   `scale = min(availableWidth / 1288, availableHeight / 900)`, centered, never scrolling. Measure the
   available box with a `ResizeObserver`. Use `transform: scale()` with `transform-origin: top left` on
   an inner element sized 1288x900, inside an outer element sized to the scaled result. Cap the scale at
   1.25 so huge monitors do not blow it up. Expose the scale through a React context, because pointer
   math (drag and drop, task 07) must divide client coordinates by it.
2. Wrap the game routes in it: the group lobby page, and the session page with every round phase and the
   round intro. Not the results page, which scrolls normally.
3. Inside the stage, remove every `short:` and `very-short:` class from the lobby and session pages and
   from `frontend/components/gameplay/*.tsx`. Elements use their design size only (task 09 and 10 make
   those sizes match the design). Leave `short:` classes outside these screens (the left rail, admin
   pages) alone.
4. Drag and drop: placement and bet dragging in the session page use pointer coordinates; convert them
   with the stage scale so the card stays under the pointer at every scale. Check
   `DRAGGED_CARD_HALF_SIZE_PIXELS` and the other drag constants near the top of `page.tsx`.
5. Popups and dialogs that portal to `document.body` (shadcn `AlertDialog`, `Select` content) are outside
   the scaled box; that is fine, leave them unscaled.

## Tests

- `game-stage.test.tsx`: computes the scale from a mocked size; caps at 1.25; letterboxes on the
  narrower axis.
- Update the Playwright no-scroll checks in `frontend/e2e/batch-e-group-lobby.spec.ts` to keep passing,
  and add a screenshot of the lobby and one gameplay phase at 1366x768, 1440x900, and 1920x1080.
- Manual: drag a card at 1366x768 and confirm it stays under the pointer.
