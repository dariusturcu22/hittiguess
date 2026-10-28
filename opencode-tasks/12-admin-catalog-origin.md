# 12. Admin catalog shows "Pipeline: Unknown" and "Added by: Unknown"

Branch: `fix/admin-catalog-origin`. Backend and frontend (small).

## What is wrong (production data)

Of 370 catalog songs, 320 show pipeline `FULL` and origin `ADMIN_SEED`, and exactly 50 show `UNKNOWN`
for both: song ids 1 to 50. These are the oldest songs, created before the stage pipeline and before
backlog tracking existed. They have no `pending_imports` rows, no pipeline runs or source findings, and
`added_by` is null. They also never went through the current year rules, and several have wrong years
(for example "Hey Mama" stored as 2015).

The labels come from `backend/.../service/CatalogSongRules.java`:
`pipeline(...)` returns `UNKNOWN` when there is no recheck and the latest queue origin is not
`ADMIN_SEED`, and `addedByKind(...)` returns `UNKNOWN` when `addedBy` is null and there is no admin-seed
row. Both are correct for missing data, but "Unknown" reads like a bug to the owner.

## What to build

1. Add `LEGACY` to `CatalogPipeline` and to `CatalogAddedByKind`: a song with no queue rows and no
   pipeline runs at all is `LEGACY` ("Added before pipeline tracking"). Keep `UNKNOWN` only for a song
   that has rows but none that explain it.
   - Check `AdminCatalogRow` and the catalog query in the admin catalog repository for how runs and queue
     rows are counted; add a `hasPipelineRun` flag if the row does not carry one yet.
2. Frontend admin songs table (`frontend/app/(app)/admin/songs/page.tsx` and the detail drawer): show
   `LEGACY` as "Before tracking" in both columns, in the muted style.
3. Regenerate the Orval client for the enum change (pinned Orval version, spec from a locally running
   core, revert line-ending-only diffs).
4. Do not invent an `added_by` value for these songs.

Task 16 reruns these 50 songs through the fixed pipeline, which gives them source findings and a
proper origin.

## Tests

- `CatalogSongRulesTest`: no rows and no runs gives `LEGACY`; rows with an admin seed give `FULL` and
  `ADMIN_SEED` as before.
- Admin songs page test: `LEGACY` renders "Before tracking".
