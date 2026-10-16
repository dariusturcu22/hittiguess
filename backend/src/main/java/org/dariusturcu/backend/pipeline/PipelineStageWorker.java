package org.dariusturcu.backend.pipeline;

import org.dariusturcu.backend.service.ApiUsageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "pipeline.stages.enabled", havingValue = "true")
public class PipelineStageWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineStageWorker.class);
    private final PipelineJobService engine;
    private final StagePayloads payloads;
    private final AiStageClient client;
    private final PipelineCompletionService completion;
    private final ApiUsageService usage;
    private final PipelineSettings settings;
    private final String owner = UUID.randomUUID().toString();

    public PipelineStageWorker(PipelineJobService engine, StagePayloads payloads, AiStageClient client,
            PipelineCompletionService completion, ApiUsageService usage, PipelineSettings settings) {
        this.engine = engine;
        this.payloads = payloads;
        this.client = client;
        this.completion = completion;
        this.usage = usage;
        this.settings = settings;
    }

    public void work(PipelineStage stage) {
        try {
            if (!engine.hasDueWork(stage)) {
                return;
            }
            boolean allowBackfill = stage != PipelineStage.YOUTUBE_METADATA
                    || usage.youtubeUnitsRemaining() >= settings.youtubeReserveUnits();
            engine.leaseExclusive(stage, owner, allowBackfill).ifPresent(job -> execute(job));
        } catch (RuntimeException unavailable) {
            LOGGER.warn("Pipeline stage {} postponed: {}", stage, unavailable.getClass().getSimpleName());
        }
    }

    private void execute(PipelineJob job) {
        try {
            PipelineRun run = engine.findRun(job.runId()).orElseThrow();
            JsonNode request = payloads.request(run, job.stage(), engine.jobsOfRun(run.id()));
            JsonNode result = client.execute(job.stage(), request);
            completion.complete(job, owner, result);
        } catch (AiStageFailure failure) {
            if (failure.retryAfter() != null) {
                engine.reschedule(job.id(), owner, failure.retryAfter());
            } else if (failure.classification().kind() == FailureKind.PERMANENT) {
                engine.failPermanent(job.id(), owner, failure.getMessage());
            } else {
                engine.failTransient(job.id(), owner, failure.getMessage(), failure.classification().keyRejected());
            }
        } catch (LeaseNotHeldException lostLease) {
            LOGGER.info("Pipeline job {} no longer belongs to this worker", job.id());
        } catch (RuntimeException failure) {
            engine.failTransient(job.id(), owner, "Stage processing failed: " + failure.getClass().getSimpleName(), false);
        }
    }

    @Scheduled(fixedDelayString = "${pipeline.stages.interval-seconds:5}", timeUnit = TimeUnit.SECONDS, scheduler = "pipelineStageScheduler")
    public void youtube() { work(PipelineStage.YOUTUBE_METADATA); }

    @Scheduled(fixedDelayString = "${pipeline.stages.interval-seconds:5}", timeUnit = TimeUnit.SECONDS, scheduler = "pipelineStageScheduler")
    public void musicbrainz() { work(PipelineStage.MUSICBRAINZ); }

    @Scheduled(fixedDelayString = "${pipeline.stages.interval-seconds:5}", timeUnit = TimeUnit.SECONDS, scheduler = "pipelineStageScheduler")
    public void discogs() { work(PipelineStage.DISCOGS); }

    @Scheduled(fixedDelayString = "${pipeline.stages.interval-seconds:5}", timeUnit = TimeUnit.SECONDS, scheduler = "pipelineStageScheduler")
    public void wikidata() { work(PipelineStage.WIKIDATA); }

    @Scheduled(fixedDelayString = "${pipeline.stages.interval-seconds:5}", timeUnit = TimeUnit.SECONDS, scheduler = "pipelineStageScheduler")
    public void wikipedia() { work(PipelineStage.WIKIPEDIA); }

    @Scheduled(fixedDelayString = "${pipeline.stages.interval-seconds:5}", timeUnit = TimeUnit.SECONDS, scheduler = "pipelineStageScheduler")
    public void reconcile() { work(PipelineStage.LLM_RECONCILIATION); }
}
