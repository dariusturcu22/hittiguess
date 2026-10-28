# Story 54 progress

The agent working through `opencode-tasks/` keeps this file current. Update it after every finished step
(not only at the end of a task) and commit it with that step's work, so a fresh context can resume from
here. Never rely on memory of earlier steps: after a restart or a context compaction, read this file,
then `opencode-tasks/README.md`, then the brief named under "Current task".

## Current task

- Task: 11, `opencode-tasks/11-duration-refresh.md`
- Branch: `fix/duration-refresh`
- Step reached: code, tests, and docs finished; PR #333 open for review
- Next step: start 12 `opencode-tasks/12-admin-catalog-origin.md`

## Finished tasks

| Task | Branch | PR | Notes |
|---|---|---|---|
| 01 | fix/left-rail-spacing | [#325](https://github.com/dariusturcu22/hittiguess/pull/325) | Left rail icons spaced one height step wider |
| 02 | fix/voice-rail-join-slot | [#326](https://github.com/dariusturcu22/hittiguess/pull/326) | The voice rail's join slot sits under the people in the call |
| 03 | fix/waveform-five-bars | [#327](https://github.com/dariusturcu22/hittiguess/pull/327) | The waveform is five design-matched bars driven by frequency bands |
| 04 | fix/countdown-clock-sync | [#328](https://github.com/dariusturcu22/hittiguess/pull/328) | Countdowns run on the server clock; deadline responses carry server time |
| 05 | fix/start-session-from-playlist | [#329](https://github.com/dariusturcu22/hittiguess/pull/329) | Start session from a playlist creates a group or offers to leave the current one |
| 06 | fix/page-transitions | [#330](https://github.com/dariusturcu22/hittiguess/pull/330) | Logged-in `/` redirects in `proxy.ts`; loaders cover known redirects |
| 07 | feature/spectator-live-drag | [#331](https://github.com/dariusturcu22/hittiguess/pull/331) | Spectators see the active player's card move live during a drag |
| 08 | fix/game-screen-scale | [#332](https://github.com/dariusturcu22/hittiguess/pull/332) | Lobby and session screens render in a fixed 1288x900 stage scaled to the window |
| 11 | fix/duration-refresh | [#333](https://github.com/dariusturcu22/hittiguess/pull/333) | The refresh selects never-fetched songs, records missing videos, and stops on a zero-row batch |

## Blocked or skipped

- Tasks 09 and 10 build on the shared game stage from task 08, so they wait for #332 to merge.
- Tasks 14 to 17 build on the matching and lock rules from task 13, whose PR is not open yet.
