package org.dariusturcu.backend.scheduling;

import org.dariusturcu.backend.analytics.AnalyticsRetentionService;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnalyticsRetentionSweeper {

    private static final long SWEEP_INTERVAL_MILLIS = 86_400_000;

    private final AnalyticsRetentionService analyticsRetentionService;

    @Scheduled(fixedRate = SWEEP_INTERVAL_MILLIS)
    public void sweep() {
        analyticsRetentionService.purgeExpiredEvents();
    }
}
