package org.dariusturcu.backend.pipeline;

import org.dariusturcu.backend.service.ApiUsageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PipelineStageWorkerTest {
    private static final long RESERVE = PipelineSettings.DEFAULT_YOUTUBE_RESERVE_UNITS;
    private static final long BELOW_RESERVE = RESERVE - 1;
    private static final int SMALL_BURST = 3;
    private static final long RUN_ID = 7;
    private static final int CONFIGURED_LIMIT = 5;
    private static final int ZERO_LIMIT = 0;
    // MusicBrainz, Discogs, Wikidata, and Wikipedia share the source default.
    private static final int SOURCE_STAGE_COUNT = 4;
    private final PipelineJobService engine = mock(PipelineJobService.class);
    private final StagePayloads payloads = mock(StagePayloads.class);
    private final AiStageClient client = mock(AiStageClient.class);
    private final PipelineCompletionService completion = mock(PipelineCompletionService.class);
    private final ApiUsageService usage = mock(ApiUsageService.class);
    private PipelineStageWorker worker;

    @BeforeEach
    void setup() {
        worker = new PipelineStageWorker(engine, payloads, client, completion, usage, new PipelineSettings(RESERVE),
                StageConcurrency.defaults(), SMALL_BURST);
    }

    private static PipelineJob job(long jobId) {
        Instant now = Instant.now();
        return new PipelineJob(jobId, RUN_ID, PipelineStage.MUSICBRAINZ, PipelineLane.BACKFILL,
                PipelineJobStatus.IN_PROGRESS, 0, PipelineJobService.DEFAULT_MAX_ATTEMPTS, 0, now, "owner", now,
                null, null, null, null, now, now, null, now);
    }

    @Test
    void anIdleTickReadsOnlyTheDueWorkCheck() {
        worker.work(PipelineStage.YOUTUBE_METADATA);
        verify(engine).hasDueWork(PipelineStage.YOUTUBE_METADATA);
        verifyNoInteractions(usage, client, payloads, completion);
    }

    @Test
    void reserveSuppressesOnlyTheYoutubeBackfillLane() {
        when(engine.hasDueWork(PipelineStage.YOUTUBE_METADATA)).thenReturn(true);
        when(usage.youtubeUnitsRemaining()).thenReturn(BELOW_RESERVE);
        when(engine.leaseWithinLimit(any(), anyString(), anyBoolean(), anyInt())).thenReturn(Optional.empty());
        worker.work(PipelineStage.YOUTUBE_METADATA);
        verify(engine).leaseWithinLimit(eq(PipelineStage.YOUTUBE_METADATA), anyString(), eq(false),
                eq(StageConcurrency.DEFAULT_YOUTUBE_METADATA_LIMIT));
    }

    @Test
    void otherSourcesDoNotReadYoutubeUsage() {
        when(engine.hasDueWork(PipelineStage.MUSICBRAINZ)).thenReturn(true);
        when(engine.leaseWithinLimit(any(), anyString(), anyBoolean(), anyInt())).thenReturn(Optional.empty());
        worker.work(PipelineStage.MUSICBRAINZ);
        verifyNoInteractions(usage);
        verify(engine).leaseWithinLimit(eq(PipelineStage.MUSICBRAINZ), anyString(), eq(true),
                eq(StageConcurrency.DEFAULT_SOURCE_LIMIT));
    }

    @Test
    void oneTickRunsDueJobsBackToBackUntilNoneIsLeft() {
        when(engine.hasDueWork(PipelineStage.MUSICBRAINZ)).thenReturn(true);
        when(engine.leaseWithinLimit(any(), anyString(), anyBoolean(), anyInt()))
                .thenReturn(Optional.of(job(1)), Optional.of(job(2)), Optional.empty());
        when(engine.findRun(RUN_ID)).thenReturn(Optional.empty());

        assertThat(worker.work(PipelineStage.MUSICBRAINZ)).isEqualTo(2);
        verify(engine, times(3)).leaseWithinLimit(any(), anyString(), anyBoolean(), anyInt());
    }

    @Test
    void theBurstLimitEndsATickEvenWhileJobsRemain() {
        when(engine.hasDueWork(PipelineStage.MUSICBRAINZ)).thenReturn(true);
        when(engine.leaseWithinLimit(any(), anyString(), anyBoolean(), anyInt())).thenReturn(Optional.of(job(1)));
        when(engine.findRun(RUN_ID)).thenReturn(Optional.empty());

        assertThat(worker.work(PipelineStage.MUSICBRAINZ)).isEqualTo(SMALL_BURST);
        verify(engine, times(SMALL_BURST)).leaseWithinLimit(any(), anyString(), anyBoolean(), anyInt());
    }

    @Test
    void configuredLimitsOverrideTheDefaultsAndRejectZero() {
        StageConcurrency configured = new StageConcurrency(Map.of(PipelineStage.DISCOGS, CONFIGURED_LIMIT));
        assertThat(configured.limit(PipelineStage.DISCOGS)).isEqualTo(CONFIGURED_LIMIT);
        assertThat(configured.limit(PipelineStage.YOUTUBE_METADATA)).isEqualTo(StageConcurrency.DEFAULT_YOUTUBE_METADATA_LIMIT);
        assertThat(StageConcurrency.defaults().totalMetadataLimit()).isEqualTo(StageConcurrency.DEFAULT_YOUTUBE_METADATA_LIMIT
                + StageConcurrency.DEFAULT_LLM_RECONCILIATION_LIMIT + SOURCE_STAGE_COUNT * StageConcurrency.DEFAULT_SOURCE_LIMIT);
        assertThat(StageConcurrency.propertyName(PipelineStage.LLM_RECONCILIATION))
                .isEqualTo("pipeline.stages.concurrency.llm-reconciliation");
        assertThatThrownBy(() -> new StageConcurrency(Map.of(PipelineStage.WIKIDATA, ZERO_LIMIT)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void limitsAreReadFromTheEnvironment() {
        var environment = new MockEnvironment().withProperty(
                StageConcurrency.propertyName(PipelineStage.YOUTUBE_METADATA), String.valueOf(CONFIGURED_LIMIT));
        assertThat(new StageConcurrency(environment).limit(PipelineStage.YOUTUBE_METADATA)).isEqualTo(CONFIGURED_LIMIT);
    }

    @Test
    void stageBeansWireWithoutTheRemovedFlag() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(new MockEnvironment()
                    .withProperty("ai.service.base-url", "http://localhost")
                    .withProperty("ai.service.internal-api-key", "test-key"));
            context.registerBean(PipelineJobService.class, () -> engine);
            context.registerBean(PipelineJobRepository.class, () -> mock(PipelineJobRepository.class));
            context.registerBean(StagePayloads.class, () -> payloads);
            context.registerBean(ApiUsageService.class, () -> usage);
            context.registerBean(org.dariusturcu.backend.service.SongCatalogService.class,
                    () -> mock(org.dariusturcu.backend.service.SongCatalogService.class));
            context.registerBean("coreJdbcTemplate", org.springframework.jdbc.core.JdbcTemplate.class,
                    () -> mock(org.springframework.jdbc.core.JdbcTemplate.class));
            context.registerBean(tools.jackson.databind.ObjectMapper.class, () -> new tools.jackson.databind.ObjectMapper());
            context.registerBean(org.dariusturcu.backend.observability.CorrelationIdPropagatingInterceptor.class);
            context.registerBean(StageConcurrency.class, StageConcurrency::defaults);
            context.register(PipelineStageWorker.class, PipelineCompletionService.class,
                    HttpAiStageClient.class, PipelineStageConfig.class, PipelineSettings.class);
            context.refresh();
            assertThat(context.getBean(PipelineStageWorker.class)).isNotNull();
            assertThat(context.getBean(PipelineCompletionService.class)).isNotNull();
            assertThat(context.getBean(AiStageClient.class)).isInstanceOf(HttpAiStageClient.class);
            assertThat(context.containsBean("pipelineStageScheduler")).isTrue();
            assertThat(context.containsBean("pipelineStageExecutor")).isTrue();
            assertThat(context.getBean(PipelineSettings.class).youtubeReserveUnits()).isEqualTo(RESERVE);
        }
    }
}
