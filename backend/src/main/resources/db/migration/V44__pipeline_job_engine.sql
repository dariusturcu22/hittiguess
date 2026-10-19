-- The persisted stage pipeline. A batch groups the runs submitted together, a run is one
-- YouTube ID's journey through the stages, and a job is one stage of one run. Workers
-- lease waiting jobs per stage; stage_controls pauses a whole stage.
CREATE TABLE import_batches (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kind VARCHAR(32) NOT NULL,
    label TEXT NOT NULL,
    source_link TEXT,
    created_by BIGINT REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    cancelled_at TIMESTAMPTZ,
    CONSTRAINT import_batches_kind_known CHECK (kind IN ('ADMIN_PLAYLIST', 'ADMIN_IDS', 'USER_IMPORT'))
);

-- ready_to_save_at marks a run whose source and reconciliation work is finished, so the
-- song can be saved; the run is DONE once its embedding job is done too.
CREATE TABLE pipeline_runs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    youtube_id VARCHAR(255) NOT NULL,
    batch_id BIGINT REFERENCES import_batches (id) ON DELETE SET NULL,
    lane VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    origin VARCHAR(32) NOT NULL DEFAULT 'ADMIN_SEED',
    provisional_year INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ready_to_save_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    CONSTRAINT pipeline_runs_lane_known CHECK (lane IN ('USER_IMPORT', 'BACKFILL')),
    CONSTRAINT pipeline_runs_status_known CHECK (status IN ('ACTIVE', 'DONE', 'FAILED', 'CANCELLED')),
    CONSTRAINT pipeline_runs_origin_known
        CHECK (origin IN ('ADMIN_SEED', 'FAST_TIER_RECHECK', 'USER_ADD_RECHECK'))
);

CREATE UNIQUE INDEX pipeline_runs_one_active_per_video ON pipeline_runs (youtube_id) WHERE status = 'ACTIVE';
CREATE INDEX pipeline_runs_batch ON pipeline_runs (batch_id);

-- lane is copied from the run so the lease order is served by one index on this table.
CREATE TABLE pipeline_jobs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES pipeline_runs (id) ON DELETE CASCADE,
    stage VARCHAR(32) NOT NULL,
    lane VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'WAITING',
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL,
    priority INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    lease_owner TEXT,
    lease_expires_at TIMESTAMPTZ,
    result JSONB,
    failure_kind VARCHAR(32),
    failure_stage VARCHAR(32),
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pipeline_jobs_one_per_run_stage UNIQUE (run_id, stage),
    CONSTRAINT pipeline_jobs_stage_known CHECK (stage IN ('YOUTUBE_METADATA', 'MUSICBRAINZ', 'DISCOGS',
        'WIKIDATA', 'WIKIPEDIA', 'LLM_RECONCILIATION', 'EMBEDDING')),
    CONSTRAINT pipeline_jobs_lane_known CHECK (lane IN ('USER_IMPORT', 'BACKFILL')),
    CONSTRAINT pipeline_jobs_status_known
        CHECK (status IN ('WAITING', 'IN_PROGRESS', 'DONE', 'FAILED', 'SKIPPED', 'CANCELLED')),
    CONSTRAINT pipeline_jobs_failure_kind_known CHECK (failure_kind IN ('TRANSIENT', 'PERMANENT')),
    CONSTRAINT pipeline_jobs_failure_stage_known CHECK (failure_stage IN ('YOUTUBE_METADATA', 'MUSICBRAINZ',
        'DISCOGS', 'WIKIDATA', 'WIKIPEDIA', 'LLM_RECONCILIATION', 'EMBEDDING')),
    CONSTRAINT pipeline_jobs_attempts_not_negative CHECK (attempts >= 0),
    CONSTRAINT pipeline_jobs_max_attempts_positive CHECK (max_attempts > 0)
);

-- The lease order: user imports first, then priority, then oldest.
CREATE INDEX pipeline_jobs_waiting_lease_order ON pipeline_jobs
    (stage, (lane = 'USER_IMPORT') DESC, priority DESC, created_at, id) WHERE status = 'WAITING';
CREATE INDEX pipeline_jobs_in_progress_lease_expiry ON pipeline_jobs (stage, lease_expires_at)
    WHERE status = 'IN_PROGRESS';

CREATE TABLE stage_controls (
    stage VARCHAR(32) PRIMARY KEY,
    paused BOOLEAN NOT NULL DEFAULT false,
    paused_reason TEXT,
    paused_at TIMESTAMPTZ,
    CONSTRAINT stage_controls_stage_known CHECK (stage IN ('YOUTUBE_METADATA', 'MUSICBRAINZ', 'DISCOGS',
        'WIKIDATA', 'WIKIPEDIA', 'LLM_RECONCILIATION', 'EMBEDDING'))
);

INSERT INTO stage_controls (stage) VALUES
    ('YOUTUBE_METADATA'), ('MUSICBRAINZ'), ('DISCOGS'), ('WIKIDATA'), ('WIKIPEDIA'),
    ('LLM_RECONCILIATION'), ('EMBEDDING');
