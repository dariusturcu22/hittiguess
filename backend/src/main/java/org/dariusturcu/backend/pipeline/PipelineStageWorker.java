package org.dariusturcu.backend.pipeline;

import org.dariusturcu.backend.service.ApiUsageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.Optional;
import java.util.UUID;

/**
 * Runs stage jobs in the calling thread: lease within the stage's concurrency limit, call the AI
 * stage, and report the outcome to the engine. {@link PipelineStageDispatcher} decides when and
 * on how many threads this runs.
 */
@Component
@ConditionalOnProperty(name = "pipeline.stages.enabled", havingValue = "true")
public class PipelineStageWorker {
    public static final int DEFAULT_BURST_LIMIT = 20;
    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineStageWorker.class);
    private static final Runnable NOTHING_AFTER_LEASE = () -> { };
    private final PipelineJobService engine;
    private final StagePayloads payloads;
    private final AiStageClient client;
    private final PipelineCompletionService completion;
    private final ApiUsageService usage;
    private final PipelineSettings settings;
    private final StageConcurrency concurrency;
    private final int burstLimit;
    private final String owner = UUID.randomUUID().toString();

    public PipelineStageWorker(PipelineJobService engine, StagePayloads payloads, AiStageClient client,
            PipelineCompletionService completion, ApiUsageService usage, PipelineSettings settings) {
        this(engine, payloads, client, completion, usage, settings, StageConcurrency.defaults(), DEFAULT_BURST_LIMIT);
    }

    @Autowired
    public PipelineStageWorker(PipelineJobService engine, StagePayloads payloads, AiStageClient client,
            PipelineCompletionService completion, ApiUsageService usage, PipelineSettings settings,
            StageConcurrency concurrency,
            @Value("${pipeline.stages.burst-limit:" + DEFAULT_BURST_LIMIT + "}") int burstLimit) {
        if (burstLimit < 1) {
            throw new IllegalArgumentException("The burst limit must be at least one job");
        }
        this.engine = engine;
        this.payloads = payloads;
        this.client = client;
        this.completion = completion;
        this.usage = usage;
        this.settings = settings;
        this.concurrency = concurrency;
        this.burstLimit = burstLimit;
    }

    /**
     * Checks cheaply for due work, then runs due jobs of the stage back to back. Returns how many
     * jobs it ran.
     */
    public int work(PipelineStage stage) {
        try {
            if (!engine.hasDueWork(stage)) {
                return 0;
            }
        } catch (RuntimeException unavailable) {
            LOGGER.warn("Pipeline stage {} postponed: {}", stage, unavailable.getClass().getSimpleName());
            return 0;
        }
        return drain(stage, NOTHING_AFTER_LEASE);
    }

    /**
     * Leases and runs jobs of the stage one after another, without waiting between them, until none
     * is due, the concurrency limit is reached, or the burst limit is used up, so one caller cannot
     * hold its thread forever. {@code afterLease} runs after each successful lease and before the
     * AI call, which lets the dispatcher start another slot while this one is busy.
     */
    public int drain(PipelineStage stage, Runnable afterLease) {
        int processed = 0;
        while (processed < burstLimit) {
            Optional<PipelineJob> leased;
            try {
                boolean allowBackfill = stage != PipelineStage.YOUTUBE_METADATA
                        || usage.youtubeUnitsRemaining() >= settings.youtubeReserveUnits();
                leased = engine.leaseWithinLimit(stage, owner, allowBackfill, concurrency.limit(stage));
            } catch (RuntimeException unavailable) {
                LOGGER.warn("Pipeline stage {} postponed: {}", stage, unavailable.getClass().getSimpleName());
                return processed;
            }
            if (leased.isEmpty()) {
                return processed;
            }
            processed++;
            afterLease.run();
            execute(leased.get());
        }
        return processed;
    }

    private void execute(PipelineJob job) {
        try {
            PipelineRun run = engine.findRun(job.runId()).orElseThrow();
            JsonNode request = payloads.request(run, job.stage(), engine.jobsOfRun(run.id()));
            JsonNode result = client.execute(job.stage(), request);
            completion.complete(job, owner, result);
        } catch (AiStageFailure failure) {
            report(job, failure);
        } catch (LeaseNotHeldException lostLease) {
            LOGGER.info("Pipeline job {} no longer belongs to this worker", job.id());
        } catch (RuntimeException failure) {
            failQuietly(job, "Stage processing failed: " + failure.getClass().getSimpleName());
        }
    }

    private void report(PipelineJob job, AiStageFailure failure) {
        try {
            if (failure.retryAfter() != null) {
                engine.reschedule(job.id(), owner, failure.retryAfter());
            } else if (failure.classification().kind() == FailureKind.PERMANENT) {
                engine.failPermanent(job.id(), owner, failure.getMessage());
            } else {
                engine.failTransient(job.id(), owner, failure.getMessage(), failure.classification().keyRejected());
            }
        } catch (RuntimeException reportFailure) {
            LOGGER.warn("Pipeline job {} outcome not recorded, its lease will expire: {}", job.id(),
                    reportFailure.getClass().getSimpleName());
        }
    }

    private void failQuietly(PipelineJob job, String reason) {
        try {
            engine.failTransient(job.id(), owner, reason, false);
        } catch (RuntimeException reportFailure) {
            LOGGER.warn("Pipeline job {} outcome not recorded, its lease will expire: {}", job.id(),
                    reportFailure.getClass().getSimpleName());
        }
    }
}
