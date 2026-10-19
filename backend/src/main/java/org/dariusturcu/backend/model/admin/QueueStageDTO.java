package org.dariusturcu.backend.model.admin;

import org.dariusturcu.backend.pipeline.PipelineStage;
import java.time.Instant;

/** A stage queue with job timings separate from upstream request timings. */
public record QueueStageDTO(PipelineStage stage, QueueState state, long waiting, long inProgress,
        long retrying, Long doneToday, long failed, Integer currentDelayMs, Instant cooldownUntil,
        Integer averageResponseMs, Long estimatedDrainSeconds, String pausedReason,
        Long waitingDue, Long waitingScheduled, Integer averageQueueWaitMs, Double jobsPerMinute,
        Integer sourceRequestMs, Integer concurrencyLimit, Long finishedLastHour) {
}
