package org.dariusturcu.backend.model.admin;

import org.dariusturcu.backend.pipeline.PipelineJobStatus;
import org.dariusturcu.backend.pipeline.PipelineLane;
import org.dariusturcu.backend.pipeline.PipelineStage;
import java.time.Instant;
import java.util.List;

/**
 * One job of a stage queue.
 *
 * @param attempts      the number of failed tries; a successful try does not add to it, so a job that
 *                      failed once and then succeeded reports 1. Kept for compatibility, see failedTries.
 * @param maxAttempts   the most failed tries before the job fails for good.
 * @param lastError     the latest failure reason, only while the job is failed or waiting to retry after a
 *                      failed try; null on finished, skipped, and cancelled jobs.
 * @param failingStage  the stage of that failure, under the same rule as lastError.
 * @param failedTries   the same number as attempts, under a name that says what it counts.
 * @param maxTries      the same number as maxAttempts; null where the queue has no limit.
 */
public record QueueJobDTO(long id, Long runId, String youtubeId, String title, PipelineLane lane,
        PipelineStage stage, PipelineJobStatus status, boolean retrying, Instant queuedAt, int attempts,
        Integer maxAttempts, Instant nextAttemptAt, String lastError, PipelineStage failingStage, int priority, List<String> artists,
        int failedTries, Integer maxTries) {

    public QueueJobDTO(long id, Long runId, String youtubeId, String title, PipelineLane lane,
            PipelineStage stage, PipelineJobStatus status, boolean retrying, Instant queuedAt, int attempts,
            Integer maxAttempts, Instant nextAttemptAt, String lastError, PipelineStage failingStage, int priority,
            List<String> artists) {
        this(id, runId, youtubeId, title, lane, stage, status, retrying, queuedAt, attempts, maxAttempts, nextAttemptAt,
                lastError, failingStage, priority, artists, attempts, maxAttempts);
    }
}
