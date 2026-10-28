# 11. Durations show "unavailable" and never come back

Branch: `fix/duration-refresh`. Backend only. Files:
`backend/src/main/java/org/dariusturcu/backend/service/SongDurationRefreshService.java`,
`backend/src/main/java/org/dariusturcu/backend/repository/SongRepository.java`,
`backend/src/main/java/org/dariusturcu/backend/model/song/Song.java` (`recordOfficialDuration`).

## What is wrong (production data)

- In production, songs 1 to 50 (created before durations were added on 2026-10-09) have
  `durationSeconds = null` and `durationFetchedAt = null`. Songs from id 51 on have durations.
- The refresh only selects songs whose duration is old:
  `select distinct song.youtubeId from Song song where song.durationFetchedAt <= :cutoff and song.youtubeId is not null`.
  `null <= cutoff` is false in SQL, so a song that never had a duration is never fetched.
- `Song.recordOfficialDuration` sets `durationFetchedAt = null` whenever the duration is missing, so any
  future song that arrives without a duration (a failed lookup at import) has the same problem.
- Production logs over 20 days show no call to the AI service's `/metadata/video-durations`: the job
  finds no candidates.

The documented behavior (docs/DECISIONS.md, "2026-10-09: Automatic YouTube duration refresh") stays:
batches of 50 through videos.list, failed lookups stay due, expired values are cleared after a failed
sweep (that is a YouTube data-retention requirement; keep it).

## What to build

1. Candidates: `where song.youtubeId is not null and (song.durationFetchedAt is null or song.durationFetchedAt <= :cutoff)`.
   Order never-fetched songs first. Match `updateRefreshedDuration`'s `where` clause to the same condition
   so the update applies to never-fetched rows.
2. A song whose video is gone (YouTube returns no item) gets `durationFetchedAt = now` with a null
   duration, so it is not retried every six hours forever (it becomes due again after 29 days).
3. Guard against an infinite loop: the `while` loop re-queries candidates after each batch; if a batch
   updates zero rows, stop the sweep and log a warning.
4. Run the sweep once shortly after startup (it already runs at startup because of `fixedDelay`; confirm
   with a log line "Duration refresh: N videos due") so the existing 50 songs get durations on the next
   deploy.

## Tests

- Repository/integration test (Testcontainers): a song with null `durationFetchedAt` is a candidate and
  gets updated; a fresh one is not; an expired one is.
- Service test: a missing video sets the fetch time with a null duration; a failed AI call leaves
  candidates due and still clears expired values.
