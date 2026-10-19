package org.dariusturcu.backend.pipeline;

/** Duration and due-queue wait use recent DONE jobs; throughput uses a shorter completion window. */
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
