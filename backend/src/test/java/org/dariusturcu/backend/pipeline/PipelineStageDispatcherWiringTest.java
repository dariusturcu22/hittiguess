package org.dariusturcu.backend.pipeline;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PipelineStageDispatcherWiringTest {
    @Test
    void defaultIntervalsAndQualifiedExecutorsWireIntoTheDispatcher() {
        TaskScheduler scheduler = mock(TaskScheduler.class);
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(PipelineStageWorker.class, () -> mock(PipelineStageWorker.class));
            context.registerBean(PipelineJobService.class, () -> mock(PipelineJobService.class));
            context.registerBean(StageConcurrency.class, StageConcurrency::defaults);
            context.registerBean("pipelineStageScheduler", TaskScheduler.class, () -> scheduler);
            context.registerBean("pipelineStageExecutor", Executor.class, () -> Runnable::run);
            context.register(StageWakeUps.class, PipelineStageDispatcher.class);
            context.refresh();

            assertThat(context.getBean(PipelineStageDispatcher.class).isRunning()).isTrue();
            verify(scheduler).scheduleWithFixedDelay(any(Runnable.class),
                    eq(Duration.ofSeconds(PipelineStageDispatcher.DEFAULT_POLL_INTERVAL_SECONDS)));
        }
    }
}
