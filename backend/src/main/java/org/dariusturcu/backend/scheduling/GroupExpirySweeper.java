package org.dariusturcu.backend.scheduling;

import org.dariusturcu.backend.service.GroupService;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// A scheduled sweep rather than a per-group Timer or ScheduledExecutorService task:
// a sweep is testable by setting a Group's expiresAt to a past instant and calling
// GroupService.deleteExpiredGroups() directly, a per-instance timer isn't without
// actually waiting out its delay.
@Component
@RequiredArgsConstructor
public class GroupExpirySweeper {

    private static final long SWEEP_INTERVAL_MILLIS = 60_000;

    private final GroupService groupService;

    @Scheduled(fixedRate = SWEEP_INTERVAL_MILLIS)
    public void sweep() {
        groupService.deleteExpiredGroups();
    }
}
