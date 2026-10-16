package org.dariusturcu.backend.pipeline;

// User imports are leased before the backfill in every stage.
public enum PipelineLane {
    USER_IMPORT,
    BACKFILL
}
