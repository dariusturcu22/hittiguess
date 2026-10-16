package org.dariusturcu.backend.pipeline;

/**
 * Measured timing of one stage from its recently finished work. averageDurationMs is the mean
 * time from lease to finish of jobs that finished done in the duration window, and
 * averageQueueWaitMs the mean time those jobs spent due before their lease; both are null
 * without a sample. finishedInThroughputWindow counts jobs that finished done in the shorter
 * throughput window.
 */
public record StageTimings(
        PipelineStage stage,
        Long averageDurationMs,
        Long averageQueueWaitMs,
        long finishedInDurationWindow,
        long finishedInThroughputWindow) {

    public static StageTimings none(PipelineStage stage) {
        return new StageTimings(stage, null, null, 0, 0);
    }
}
