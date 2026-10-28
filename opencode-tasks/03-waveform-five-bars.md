# 03. The song waveform shows 20 bars; it must show 5

Branch: `fix/waveform-five-bars`. Frontend only. Files: `frontend/hooks/use-audio-levels.ts`,
`frontend/components/gameplay/game-pieces.tsx`, and their tests.

## What is wrong

Story 53 rewrote the visualizer to follow the song's frequency spectrum, which the owner likes, but it
set `export const VISUALIZER_BAND_COUNT = 20;` in `use-audio-levels.ts`. `SoundBars` in
`game-pieces.tsx` draws that many bars on a canvas, so every mystery card shows 20 thin bars. The rule
for the whole app is 5 bars, matching the logo (`frontend/components/logo.tsx`, `BAR_COUNT = 5`) and
the design artboards.

## What to build

- Set the band count to 5. Keep the log-spaced bands from about 40 Hz to 16 kHz, the dB scaling, the
  tilt compensation, and the attack/release smoothing; only the number of bands changes, so each band
  covers a wider frequency range (average the bins inside a band; do not just sample one bin).
- Check that 5 bands still look lively for typical pop songs: with wide bands the lowest band can sit
  near maximum all the time. If it does, lower that band's weight or raise its floor so all five move.
- `SoundBars` draws on a canvas with `VISUALIZER_BAR_GAP_RATIO` and `VISUALIZER_BAR_RADIUS_RATIO`.
  With 5 bars, make bar width and gap match the design's mystery card (open
  `docs/design/hittiguess-design.html`; artboard `GameSessionPlayerDark.dc.html` holds the card. See
  task 10 for how to extract one artboard into its own file). The design bars are rounded pills, 7 to
  8px wide on a 128 to 168px card.
- `VISUALIZER_RESTING_PATTERN` already has 5 values; `restingLevels` can return it directly.
- Nothing else in the app may show a different bar count.

## Tests

- `use-audio-levels.test.ts`: 5 levels come back; band edges are log spaced across the range.
- `game-pieces` test (create one if missing): `SoundBars` with no levels draws 5 resting bars.
