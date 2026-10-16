package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.song.CatalogAddedByKind;
import org.dariusturcu.backend.model.song.CatalogPipeline;
import org.dariusturcu.backend.model.song.CatalogSongStatus;
import org.dariusturcu.backend.model.song.PendingImport;
import org.dariusturcu.backend.model.song.PendingImportOrigin;
import org.dariusturcu.backend.model.song.PendingImportStatus;
import org.dariusturcu.backend.model.song.VerificationStatus;

import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;

/**
 * How an admin catalog row's status, pipeline, and added-by kind follow from a song's stored
 * verification status and the backlog rows for its YouTube ID.
 *
 * <p>Status: an active backlog row wins, PROCESSING before PENDING (shown as IN_QUEUE). Without
 * one, a VERIFIED song is VERIFIED and every other verification status is NEEDS_REVIEW. A
 * backlog item with no song is PROCESSING, IN_QUEUE, or, once it failed, ERROR.
 *
 * <p>Pipeline: FAST while a recheck row (FAST_TIER_RECHECK or USER_ADD_RECHECK) is not DONE;
 * FULL when the latest backlog row is an ADMIN_SEED or a recheck row is DONE; UNKNOWN otherwise.
 *
 * <p>Added by: USER when the song names an account, ADMIN_SEED when it does not and an admin
 * seed row exists for its YouTube ID, UNKNOWN otherwise.
 *
 * <p>The catalog list query repeats the status rule in SQL so it can filter and count by status.
 */
public final class CatalogSongRules {

    private static final Set<PendingImportOrigin> RECHECK_ORIGINS =
            EnumSet.of(PendingImportOrigin.FAST_TIER_RECHECK, PendingImportOrigin.USER_ADD_RECHECK);

    private static final Comparator<PendingImport> ENQUEUE_ORDER = Comparator
            .comparing(PendingImport::getEnqueuedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(PendingImport::getId, Comparator.nullsFirst(Comparator.naturalOrder()));

    private CatalogSongRules() {
    }

    public static boolean isRecheck(PendingImportOrigin origin) {
        return RECHECK_ORIGINS.contains(origin);
    }

    public static PendingImportStatus activeQueueStatus(Collection<PendingImportStatus> queueStatuses) {
        if (queueStatuses.contains(PendingImportStatus.PROCESSING)) {
            return PendingImportStatus.PROCESSING;
        }
        if (queueStatuses.contains(PendingImportStatus.PENDING)) {
            return PendingImportStatus.PENDING;
        }
        return null;
    }

    public static CatalogSongStatus songStatus(VerificationStatus verificationStatus,
                                               PendingImportStatus activeQueueStatus) {
        if (activeQueueStatus == PendingImportStatus.PROCESSING) {
            return CatalogSongStatus.PROCESSING;
        }
        if (activeQueueStatus == PendingImportStatus.PENDING) {
            return CatalogSongStatus.IN_QUEUE;
        }
        return verificationStatus == VerificationStatus.VERIFIED
                ? CatalogSongStatus.VERIFIED
                : CatalogSongStatus.NEEDS_REVIEW;
    }

    public static CatalogSongStatus songStatus(VerificationStatus verificationStatus,
            Collection<PendingImport> rows, boolean stagePipeline) {
        Set<PendingImportStatus> statuses = EnumSet.noneOf(PendingImportStatus.class);
        rows.forEach(row -> statuses.add(row.getStatus()));
        PendingImportStatus activeStatus = activeQueueStatus(statuses);
        boolean latestFailed = rows.stream().max(ENQUEUE_ORDER)
                .map(row -> row.getStatus() == PendingImportStatus.FAILED).orElse(false);
        return stagePipeline && activeStatus == null && latestFailed
                ? CatalogSongStatus.ERROR : songStatus(verificationStatus, activeStatus);
    }

    public static CatalogSongStatus queueItemStatus(PendingImportStatus queueStatus) {
        return switch (queueStatus) {
            case PROCESSING -> CatalogSongStatus.PROCESSING;
            case PENDING -> CatalogSongStatus.IN_QUEUE;
            case DONE, FAILED -> CatalogSongStatus.ERROR;
        };
    }

    public static CatalogPipeline pipeline(boolean hasUnfinishedRecheck, boolean hasFinishedRecheck,
                                           PendingImportOrigin latestQueueOrigin) {
        if (hasUnfinishedRecheck) {
            return CatalogPipeline.FAST;
        }
        if (latestQueueOrigin == PendingImportOrigin.ADMIN_SEED || hasFinishedRecheck) {
            return CatalogPipeline.FULL;
        }
        return CatalogPipeline.UNKNOWN;
    }

    public static CatalogPipeline pipeline(Collection<PendingImport> queueRows) {
        boolean hasUnfinishedRecheck = queueRows.stream()
                .anyMatch(queueRow -> isRecheck(queueRow.getOrigin()) && queueRow.getStatus() != PendingImportStatus.DONE);
        boolean hasFinishedRecheck = queueRows.stream()
                .anyMatch(queueRow -> isRecheck(queueRow.getOrigin()) && queueRow.getStatus() == PendingImportStatus.DONE);
        PendingImportOrigin latestQueueOrigin = queueRows.stream()
                .max(ENQUEUE_ORDER)
                .map(PendingImport::getOrigin)
                .orElse(null);
        return pipeline(hasUnfinishedRecheck, hasFinishedRecheck, latestQueueOrigin);
    }

    public static CatalogAddedByKind addedByKind(String addedByUsername, boolean hasAdminSeedRow) {
        if (addedByUsername != null) {
            return CatalogAddedByKind.USER;
        }
        return hasAdminSeedRow ? CatalogAddedByKind.ADMIN_SEED : CatalogAddedByKind.UNKNOWN;
    }
}
