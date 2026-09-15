package org.dariusturcu.backend.scheduling;

import org.dariusturcu.backend.service.CatalogSeedingService;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// A scheduled sweep, the same shape as GroupExpirySweeper and AnalyticsRetentionSweeper:
// the drain logic lives in CatalogSeedingService.drainBacklog() so it is testable by
// calling it directly, and this component only wires it to the daily fixed-rate trigger.
@Component
@RequiredArgsConstructor
public class BacklogDrainSweeper {

    private static final long DRAIN_INTERVAL_MILLIS = 86_400_000;

    private final CatalogSeedingService catalogSeedingService;

    @Scheduled(fixedRate = DRAIN_INTERVAL_MILLIS)
    public void drain() {
        catalogSeedingService.drainBacklog();
    }
}
