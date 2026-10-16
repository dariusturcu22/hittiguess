package org.dariusturcu.backend.model.admin;

import org.dariusturcu.backend.pipeline.PipelineJobStatus;
import org.dariusturcu.backend.pipeline.PipelineLane;
import org.dariusturcu.backend.pipeline.PipelineStage;
import java.time.Instant;
import java.util.List;

public record QueueJobDTO(long id, Long runId, String youtubeId, String title, PipelineLane lane,
        PipelineStage stage, PipelineJobStatus status, boolean retrying, Instant queuedAt, int attempts,
        Integer maxAttempts, Instant nextAttemptAt, String lastError, PipelineStage failingStage, int priority, List<String> artists) {
}
