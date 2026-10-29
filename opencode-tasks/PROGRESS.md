# Story 54 progress

The agent working through `opencode-tasks/` keeps this file current. Update it after every finished step
(not only at the end of a task) and commit it with that step's work, so a fresh context can resume from
here. Never rely on memory of earlier steps: after a restart or a context compaction, read this file,
then `opencode-tasks/README.md`, then the brief named under "Current task".

## Current task

None. Every story 54 task is merged into main.

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
| 12 | fix/admin-catalog-origin | [#334](https://github.com/dariusturcu22/hittiguess/pull/334) | Songs with no pipeline evidence show "Before tracking" instead of "Unknown" |
| 13 | fix/musicbrainz-recording-search | [#335](https://github.com/dariusturcu22/hittiguess/pull/335) | MusicBrainz uses the first main artist, keeps the top-scored original track group, and finds parent albums through the recording search |
| 18 | fix/placement-preview-gate | [#336](https://github.com/dariusturcu22/hittiguess/pull/336) | Live drag previews validate against in-memory round state in `PlacementPreviewGate` and only touch Postgres on a cache miss |
| 19 | fix/replace-group-atomically | [#337](https://github.com/dariusturcu22/hittiguess/pull/337) | Replacing a group runs in one server transaction, locking both groups in id order |
| 09 | fix/lobby-design-proportions | [#338](https://github.com/dariusturcu22/hittiguess/pull/338) | The lobby matches GroupLobbyDark with theme tokens for shadows and rings |
| 10 | fix/gameplay-design-proportions | [#339](https://github.com/dariusturcu22/hittiguess/pull/339) | Gameplay matches its artboards; the footer is absolute so the timeline stays centered |
| 14 | fix/source-matching | [#340](https://github.com/dariusturcu22/hittiguess/pull/340) | Wikidata and Discogs match main artists as whole words and drop compilations and unofficial releases |
| 15 | fix/verification-lock-rule | [#341](https://github.com/dariusturcu22/hittiguess/pull/341) | A one-year cluster takes its earliest year only when two sources report it |
| 16 | chore/recheck-catalog | [#342](https://github.com/dariusturcu22/hittiguess/pull/342) | Reruns propose year changes on verified songs; the duplicate check skips the song that owns the video |
| 17 | fix/catalog-duplicates | [#343](https://github.com/dariusturcu22/hittiguess/pull/343) | Completions match an existing song by an indexed normalized key; admins can merge duplicate songs |

## Blocked or skipped

- Task 16 and 17 rollout is the owner's step in production: merge the 12 duplicate pairs listed in PR 343 first, then recheck songs 1 to 50, then the rest of the catalog, and accept or dismiss the proposals against `opencode-tasks/data/year-audit.md`.
