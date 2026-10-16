package org.dariusturcu.backend.pipeline;

import org.dariusturcu.backend.model.song.PendingImportOrigin;

import java.time.Instant;

public record PipelineRun(
        long id,
        String youtubeId,
        Long batchId,
        PipelineLane lane,
        PipelineRunStatus status,
        PendingImportOrigin origin,
        Integer provisionalYear,
        Instant createdAt,
        Instant readyToSaveAt,
        Instant finishedAt) {
}
