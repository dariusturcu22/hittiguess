package org.dariusturcu.backend.model.admin;

import org.dariusturcu.backend.pipeline.PipelineStage;
import java.time.Instant;

/**
 * One stage queue. The exact meaning of every field is documented in docs/SYSTEM_REFERENCE.md
 * under the admin queue endpoints.
 *
 * @param averageResponseMs     mean duration from lease to finish of jobs that finished done in the
 *                              last 60 minutes; null without such a job.
 * @param estimatedDrainSeconds time to finish the jobs the stage holds now; null when unknown.
 * @param waitingDue            the part of waiting that may be leased now.
 * @param waitingScheduled      the part of waiting held back by a retry backoff or pace deferral.
 * @param averageQueueWaitMs    mean time those jobs spent due before their lease.
 * @param jobsPerMinute         jobs finished done per minute over the last 10 minutes.
 * @param sourceRequestMs       the AI service's moving average of the external source request alone.
 * @param concurrencyLimit      the most jobs of the stage in progress at once.
 * @param finishedLastHour      jobs finished done in the last 60 minutes, the sample behind averageResponseMs.
 */
public record QueueStageDTO(PipelineStage stage, QueueState state, long waiting, long inProgress,
        long retrying, Long doneToday, long failed, Integer currentDelayMs, Instant cooldownUntil,
        Integer averageResponseMs, Long estimatedDrainSeconds, String pausedReason,
        Long waitingDue, Long waitingScheduled, Integer averageQueueWaitMs, Double jobsPerMinute,
        Integer sourceRequestMs, Integer concurrencyLimit, Long finishedLastHour) {
}
