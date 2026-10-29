-- An admin rerun of a song already in the catalog gets its own origin, so the catalog can
-- show the full pipeline ran without claiming an admin seeded the song.
ALTER TABLE pipeline_runs DROP CONSTRAINT pipeline_runs_origin_known;
ALTER TABLE pipeline_runs ADD CONSTRAINT pipeline_runs_origin_known
    CHECK (origin IN ('ADMIN_SEED', 'ADMIN_RECHECK', 'FAST_TIER_RECHECK', 'USER_ADD_RECHECK'));
