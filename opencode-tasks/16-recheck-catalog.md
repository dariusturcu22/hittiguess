# 16. Recheck the whole catalog with the fixed pipeline, and let the admin accept corrections

Branch: `chore/recheck-catalog`. Backend and frontend. Requires tasks 12 to 15 merged and deployed.

## What is wrong

- `AdminSongPipelineService.rerun(songId)` (`backend/.../pipeline/AdminSongPipelineService.java`, line
  66) queues one video again through `pipeline.runNow`. When the run completes,
  `PipelineCompletionService` calls `SongCatalogService.persist`, which for a song that is already
  `VERIFIED` only records the duration and returns: the new year is thrown away. So rerunning a wrong
  verified song cannot correct it.
- There is no way to rerun many songs at once.
- `data/year-audit.md` in this folder lists the production songs whose stored year breaks the rules.

## What to build

1. Keep the result of a rerun on a verified song instead of discarding it: when a completed run's year
   differs from the stored year of a `VERIFIED` song, store it as a proposal (a new nullable column
   `songs.proposed_release_year`, migration `V50__song_proposed_release_year.sql`, plus
   `proposed_at`), and leave the stored year unchanged. When the run agrees with the stored year, clear
   any proposal. Non-verified songs keep today's behavior (the run overwrites them).
2. Admin endpoint `POST /api/admin/songs/recheck` with body `{ "songIds": [...] }` or `{ "all": true }`:
   queues a low-priority run for each song (reuse the backfill lane; do not starve user imports), and
   returns how many were queued. Skip songs used by an active game (the existing delete path already has
   that check; reuse it).
3. Admin songs table: a "Proposed year" filter and column; the detail drawer shows the stored year, the
   proposed year, and the new source findings, with "Accept" (calls the existing
   `PUT /api/admin/songs/{id}` with the proposed year, then clears the proposal) and "Dismiss".
4. Document the endpoint in `docs/SYSTEM_REFERENCE.md` and the proposal behavior in
   `docs/ARCHITECTURE.md` (catalog section).
5. Regenerate the Orval client.

## Rollout (write these steps into the PR description for the owner)

1. Deploy.
2. Recheck the 50 legacy songs first (ids 1 to 50), then everything else.
3. Review proposals against `opencode-tasks/data/year-audit.md`.

## Tests

- Completion on a verified song with a different year sets a proposal and keeps the year; with the same
  year clears the proposal; on a `NEEDS_REVIEW` song overwrites as before.
- Recheck endpoint: admin only, skips songs in active games, queues the rest.
- Admin drawer: Accept and Dismiss call the right endpoints.
