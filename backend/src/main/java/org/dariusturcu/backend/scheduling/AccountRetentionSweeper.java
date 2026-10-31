package org.dariusturcu.backend.scheduling;

import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.service.AccountRetentionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountRetentionSweeper {

    private static final long SWEEP_INTERVAL_MILLIS = 86_400_000;

    private final AccountRetentionService accountRetentionService;

    @Scheduled(fixedRate = SWEEP_INTERVAL_MILLIS)
    public void sweep() {
        accountRetentionService.purgeUnverifiedAccounts();
    }
}
