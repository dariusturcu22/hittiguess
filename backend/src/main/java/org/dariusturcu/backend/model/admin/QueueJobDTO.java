package org.dariusturcu.backend.model.admin;

import org.dariusturcu.backend.pipeline.PipelineJobStatus;
import org.dariusturcu.backend.pipeline.PipelineLane;
import org.dariusturcu.backend.pipeline.PipelineStage;
import java.time.Instant;
import java.util.List;

/** Metadata attempts count failed tries; embedding attempts retain the legacy claim counter. */
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
