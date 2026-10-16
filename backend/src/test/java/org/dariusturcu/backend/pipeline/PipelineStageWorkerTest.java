package org.dariusturcu.backend.pipeline;

import org.dariusturcu.backend.service.ApiUsageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PipelineStageWorkerTest {
    private static final long RESERVE = PipelineSettings.DEFAULT_YOUTUBE_RESERVE_UNITS;
    private static final long BELOW_RESERVE = RESERVE - 1;
    private final PipelineJobService engine = mock(PipelineJobService.class);
    private final StagePayloads payloads = mock(StagePayloads.class);
    private final AiStageClient client = mock(AiStageClient.class);
    private final PipelineCompletionService completion = mock(PipelineCompletionService.class);
    private final ApiUsageService usage = mock(ApiUsageService.class);
    private PipelineStageWorker worker;

    @BeforeEach
    void setup() {
        worker = new PipelineStageWorker(engine, payloads, client, completion, usage, new PipelineSettings(true, RESERVE));
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
        when(engine.leaseExclusive(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(Optional.empty());
        worker.work(PipelineStage.YOUTUBE_METADATA);
        verify(engine).leaseExclusive(org.mockito.ArgumentMatchers.eq(PipelineStage.YOUTUBE_METADATA),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(false));
    }

    @Test
    void otherSourcesDoNotReadYoutubeUsage() {
        when(engine.hasDueWork(PipelineStage.MUSICBRAINZ)).thenReturn(true);
        when(engine.leaseExclusive(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(Optional.empty());
        worker.work(PipelineStage.MUSICBRAINZ);
        verifyNoInteractions(usage);
        verify(engine).leaseExclusive(org.mockito.ArgumentMatchers.eq(PipelineStage.MUSICBRAINZ),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(true));
    }

    @Test
    void disabledFlagRegistersNoStageWorkersClientsOrSchedulers() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("test-flag", Map.of("pipeline.stages.enabled", "false")));
            context.register(PipelineStageWorker.class, PipelineCompletionService.class, HttpAiStageClient.class,
                    PipelineStageConfig.class, PipelineSettings.class);
            context.refresh();
            assertThat(context.getBeansOfType(PipelineStageWorker.class)).isEmpty();
            assertThat(context.getBeansOfType(AiStageClient.class)).isEmpty();
            assertThat(context.containsBean("pipelineStageScheduler")).isFalse();
            assertThat(context.getBean(PipelineSettings.class).enabled()).isFalse();
        }
    }
}
