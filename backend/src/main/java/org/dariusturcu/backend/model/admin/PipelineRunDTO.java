package org.dariusturcu.backend.model.admin;

import org.dariusturcu.backend.pipeline.PipelineLane;
import org.dariusturcu.backend.pipeline.PipelineRunStatus;
import java.time.Instant;
import java.util.List;

public record PipelineRunDTO(long id, String youtubeId, String title, PipelineLane lane, PipelineRunStatus status,
        Integer provisionalYear, Instant queuedAt, Instant finishedAt, List<QueueJobDTO> jobs,
        Long batchId, String batchLabel, List<String> artists) {
}
