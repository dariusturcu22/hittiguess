package org.dariusturcu.backend.pipeline;

/** Queue count definitions are shared with admin filters and documented in SYSTEM_REFERENCE.md. */
public record StageCounts(
        PipelineStage stage,
        boolean paused,
        long waiting,
        long inProgress,
        long doneToday,
        long failed,
        long retrying,
        long waitingDue) {
}
