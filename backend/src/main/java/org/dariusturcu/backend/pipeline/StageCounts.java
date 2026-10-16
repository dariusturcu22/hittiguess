package org.dariusturcu.backend.pipeline;

// Waiting and retrying count only jobs of active runs; retrying jobs are waiting jobs that
// have already failed at least once.
public record StageCounts(
        PipelineStage stage,
        boolean paused,
        long waiting,
        long inProgress,
        long doneToday,
        long failed,
        long retrying) {
}
