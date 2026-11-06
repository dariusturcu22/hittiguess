package org.dariusturcu.backend.scheduling;

import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.pipeline.PipelineRetentionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PipelineRetentionSweeper {

    private static final long SWEEP_INTERVAL_MILLIS = 86_400_000;

    private final PipelineRetentionService pipelineRetentionService;

    @Scheduled(fixedRate = SWEEP_INTERVAL_MILLIS)
    public void sweep() {
        pipelineRetentionService.purgeTerminalPipelineRunsAndJobs();
    }
}
