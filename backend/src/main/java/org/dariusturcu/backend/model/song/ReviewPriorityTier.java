package org.dariusturcu.backend.model.song;

/**
 * The admin review queue's priority tiers. Declaration order is the ranking order, highest
 * priority first. VERIFIED cards with no report never enter the queue, so they have no tier
 * here.
 */
public enum ReviewPriorityTier {
    CONVERGING_REPORTS,
    REPORTED_NO_CONVERGENCE,
    CONFIRMED_UNREPORTED,
    UNCONFIRMED_UNREPORTED
}
