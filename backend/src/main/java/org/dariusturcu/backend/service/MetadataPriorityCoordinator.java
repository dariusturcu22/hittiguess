package org.dariusturcu.backend.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Arbitrates the shared external rate-limit budgets (MusicBrainz, Discogs, Wikidata,
 * Wikipedia all leave from one outbound IP) between the two catalog-seeding paths.
 * On-the-spot user traffic is always high priority. The admin backlog drain pauses
 * while any on-the-spot request is active and resumes once the last one clears,
 * rather than the two paths contending for the same budget in real time.
 *
 * On-the-spot work brackets itself with beginOnTheSpotWork / endOnTheSpotWork. The
 * drain calls mayDrainProceed between items and stops handing out new work while any
 * on-the-spot request is in flight; an item already in progress finishes rather than
 * being torn down.
 */
@Component
public class MetadataPriorityCoordinator {

    private final AtomicInteger activeOnTheSpotRequests = new AtomicInteger();

    public void beginOnTheSpotWork() {
        activeOnTheSpotRequests.incrementAndGet();
    }

    public void endOnTheSpotWork() {
        activeOnTheSpotRequests.updateAndGet(current -> current > 0 ? current - 1 : 0);
    }

    public boolean isOnTheSpotTrafficActive() {
        return activeOnTheSpotRequests.get() > 0;
    }

    public boolean mayDrainProceed() {
        return !isOnTheSpotTrafficActive();
    }
}
