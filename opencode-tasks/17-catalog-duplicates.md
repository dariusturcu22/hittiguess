# 17. The same song is stored twice under different YouTube videos

Branch: `fix/catalog-duplicates`. Backend (and a small admin UI action). Do after task 16.

## What is wrong (production data)

12 songs exist twice in the catalog, each copy with its own YouTube video (song ids):
4 Minutes (10, 40), Say It Right (21, 348), Rhythm Is a Dancer (33, 337), Bleeding Love (41, 344),
Sun Is Up (57, 121), Stereo Love (58, 100), Pump It (103, 186), Judas (136, 322), LaLa Song (188, 191),
Sus pe toc (277, 295), 365 (316, 352), Kings & Queens (326, 358).

The pipeline's duplicate check (`ai/app/metadata/service.py`, `_check_for_duplicate`, called from
`identify_from_youtube_data`) only compares against songs that are already `VERIFIED` and already have
an embedding (`ai/app/dedup/repository.py`, `_FIND_BEST_VERIFIED_MATCH_QUERY`). Likely causes, check
each:
- Two videos of the same song processed in the same batch: the stage pipeline runs songs in parallel,
  so the second video is identified before the first is verified and embedded by the indexing job.
- The check embeds the regex-cleaned YouTube title and channel, which can be far from the stored clean
  "artist title" (for example "[Official Music Video]" suffixes), so the distance misses
  `HIGH_CONFIDENCE_COSINE_DISTANCE_THRESHOLD`.
- Any error in the check is swallowed with a warning and treated as no match.

## What to build

1. A deterministic guard in core at completion time (`backend/.../pipeline/PipelineCompletionService.java`
   before `catalog.persist`): normalize the final main artists and title (lowercase, accents and
   punctuation stripped, "feat." clauses removed; reuse the normalization from the story 53 game song
   list, `GameSongList`, if it fits) and look for an existing song with the same key in any status. If
   one exists, attach this video as an `AlternateYoutubeId` of that song instead of creating a new song
   (the alternate-id path in `SongCatalogService.persist` already does this for the AI's canonical match;
   reuse it). Hold the existing per-key advisory lock (`SongRepository.acquireTransactionLock`) so two
   parallel completions cannot both create the song.
2. Admin merge action for the existing duplicates: `POST /api/admin/songs/{keepId}/merge/{dropId}` moves
   playlist links and alternate ids from the dropped song to the kept one, adds the dropped song's
   video as an alternate id, and deletes the dropped song. Refuse while an active game uses either song.
   Add a "Merge into..." action in the admin song drawer.
3. List the 12 pairs in the PR description with which id to keep (keep the one that is `VERIFIED` with
   the correct year after task 16, otherwise the lower id).

## Tests

- Completion with a song whose normalized key already exists attaches an alternate id and creates no song.
- Two parallel completions of the same key create one song (integration test with two threads).
- Merge moves playlist links and alternate ids and refuses during an active game.
