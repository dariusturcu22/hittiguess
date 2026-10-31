package org.dariusturcu.backend.scheduling;

import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.service.TokenRetentionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TokenRetentionSweeper {

    private static final long SWEEP_INTERVAL_MILLIS = 3_600_000;

    private final TokenRetentionService tokenRetentionService;

    @Scheduled(fixedRate = SWEEP_INTERVAL_MILLIS)
    public void sweep() {
        tokenRetentionService.purgeExpiredTokens();
    }
}
