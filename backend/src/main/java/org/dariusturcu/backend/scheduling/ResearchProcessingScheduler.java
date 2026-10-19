package org.dariusturcu.backend.scheduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dariusturcu.backend.difficulty.PreparedDifficultyService;
import org.dariusturcu.backend.research.ResearchDeliveryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class ResearchProcessingScheduler {
    private final ResearchDeliveryService delivery;
    private final PreparedDifficultyService difficulty;

    @Scheduled(fixedDelayString = "${research.delivery.interval-seconds:30}", timeUnit = TimeUnit.SECONDS)
    public void deliver() {
        try {
            delivery.purgeExpiredQueuedObservations();
            delivery.deliverBatch();
        } catch (RuntimeException failure) {
            log.warn("Research delivery postponed: {}", failure.getClass().getSimpleName());
        }
    }

    @Scheduled(fixedDelayString = "${difficulty.refresh.interval-hours:6}", timeUnit = TimeUnit.HOURS)
    public void refresh() {
        try {
            difficulty.refresh();
        } catch (RuntimeException failure) {
            log.warn("Difficulty refresh postponed; previous scores remain available: {}", failure.getClass().getSimpleName());
        }
    }
}
