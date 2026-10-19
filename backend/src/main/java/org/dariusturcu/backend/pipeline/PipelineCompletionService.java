package org.dariusturcu.backend.pipeline;

import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.service.SongCatalogService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.Instant;

@Service
public class PipelineCompletionService {
    private final PipelineJobService engine;
    private final PipelineJobRepository repository;
    private final StagePayloads payloads;
    private final SongCatalogService catalog;
    private final JdbcTemplate core;

    public PipelineCompletionService(PipelineJobService engine, PipelineJobRepository repository,
            StagePayloads payloads, SongCatalogService catalog, @Qualifier("coreJdbcTemplate") JdbcTemplate core) {
        this.engine = engine;
        this.repository = repository;
        this.payloads = payloads;
        this.catalog = catalog;
        this.core = core;
    }

    @Transactional
    public void complete(PipelineJob job, String owner, JsonNode result) {
        boolean youtubeFinal = job.stage() == PipelineStage.YOUTUBE_METADATA && payloads.youtubeFinished(result);
        if (youtubeFinal && payloads.rejected(result)) {
            engine.failPermanent(job.id(), owner, "Submission rejected by the precheck");
            return;
        }
        engine.complete(job.id(), owner, result.toString(), youtubeFinal);
        PipelineRun run = repository.lockRun(job.runId()).orElseThrow();
        if (run.status() != PipelineRunStatus.ACTIVE || run.readyToSaveAt() == null) {
            return;
        }
        Song song = catalog.persist(run.youtubeId(), payloads.finalMetadata(engine.jobsOfRun(run.id())), null);
        core.update("""
                UPDATE pending_imports SET status = 'DONE', patient_year = ?, processed_at = now() AT TIME ZONE 'UTC',
                    failure_reason = NULL
                WHERE youtube_id = ? AND origin = ? AND status IN ('PENDING', 'PROCESSING')
                """, song.getReleaseYear(), run.youtubeId(), run.origin().name());
        repository.finishRun(run.id(), PipelineRunStatus.DONE, Instant.now());
    }
}
