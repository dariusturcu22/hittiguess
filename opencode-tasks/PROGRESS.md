# Story 54 progress

The agent working through `opencode-tasks/` keeps this file current. Update it after every finished step
(not only at the end of a task) and commit it with that step's work, so a fresh context can resume from
here. Never rely on memory of earlier steps: after a restart or a context compaction, read this file,
then `opencode-tasks/README.md`, then the brief named under "Current task".

## Current task

- Task: 08, `opencode-tasks/08-game-screen-scale.md`
- Branch: `fix/game-screen-scale` (not created yet)
- Step reached: brief not read
- Next step: read the brief, then create the branch off an up-to-date `main`

## Finished tasks

| Task | Branch | PR | Notes |
|---|---|---|---|
| 01 | `fix/left-rail-spacing` | https://github.com/dariusturcu22/hittiguess/pull/325 | Rail gap and divider steps; 5-bar rail fits admin at 1366x768, 1366x650, 1440x900, 1920x1080 |
| 02 | `fix/voice-rail-join-slot` | https://github.com/dariusturcu22/hittiguess/pull/326 | `VoiceMemberList` has an optional `trailingSlot`; the join slot is first, or next after the members |
| 03 | `fix/waveform-five-bars` | https://github.com/dariusturcu22/hittiguess/pull/327 | `VISUALIZER_BAND_COUNT = 5`; 7px pill bars with 6px gaps; new `game-pieces.test.tsx` |
| 04 | `fix/countdown-clock-sync` | https://github.com/dariusturcu22/hittiguess/pull/328 | `RoundDTO.serverTime`; median offset module; session page, round intro, and away widget count on server time |
| 05 | `fix/start-session-from-playlist` | https://github.com/dariusturcu22/hittiguess/pull/329 | Shared `useCreateGroupAndOpen`; playlist start creates or chooses a group; locked groups cannot be left |
| 06 | `fix/page-transitions` | https://github.com/dariusturcu22/hittiguess/pull/330 | Proxy redirect for `/`; session loader while results redirect; lobby loader while locked; 491 frontend tests |
| 07 | `feature/spectator-live-drag` | https://github.com/dariusturcu22/hittiguess/pull/331 | Preview phases and timeline fractions; spectator card floats, follows the drag, and lands in the gap; 495 frontend tests and 1076 backend tests |

## Blocked or skipped

Tasks 09 and 10 depend on task 08, and tasks 14 to 17 depend on task 13. Each waits for that task's PR before its branch starts.
