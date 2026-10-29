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
 * verification status and the pipeline runs for its YouTube ID.
 *
 * <p>Status: an active backlog row wins, PROCESSING before PENDING (shown as IN_QUEUE). Without
 * one, the latest failed run means ERROR; otherwise a VERIFIED song is VERIFIED and every
 * other verification status is NEEDS_REVIEW. A
 * backlog item with no song is PROCESSING, IN_QUEUE, or, once it failed, ERROR.
 *
 * <p>Pipeline: FAST while a recheck row (FAST_TIER_RECHECK or USER_ADD_RECHECK) is not DONE;
 * FULL when the latest backlog row is an ADMIN_SEED or ADMIN_RECHECK or a recheck row is DONE; UNKNOWN when
 * queue evidence exists but none of it decides the pipeline; LEGACY when there is no queue
 * evidence at all.
 *
 * <p>Added by: USER when the song names an account, ADMIN_SEED when it does not and an admin
 * seed row exists for its YouTube ID, UNKNOWN when queue evidence exists but names no origin,
 * and LEGACY when there is no queue evidence at all. ADMIN_RECHECK rows are not evidence of
 * who added a song, so a rerun of a song added before tracking still shows LEGACY.
 *
 * <p>The catalog list query repeats the status rule in SQL so it can filter and count by status.
 */
public final class CatalogSongRules {

    private static final Set<PendingImportOrigin> RECHECK_ORIGINS =
            EnumSet.of(PendingImportOrigin.FAST_TIER_RECHECK, PendingImportOrigin.USER_ADD_RECHECK);

    private static final Set<PendingImportOrigin> FULL_PIPELINE_ORIGINS =
            EnumSet.of(PendingImportOrigin.ADMIN_SEED, PendingImportOrigin.ADMIN_RECHECK);

    private static final Comparator<PendingImport> ENQUEUE_ORDER = Comparator
            .comparing(PendingImport::getEnqueuedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(PendingImport::getId, Comparator.nullsFirst(Comparator.naturalOrder()));

    private CatalogSongRules() {
    }

    public static boolean isAdditionEvidence(PendingImportOrigin origin) {
        return origin != PendingImportOrigin.ADMIN_RECHECK;
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

    public static CatalogSongStatus songStatusFromRuns(VerificationStatus verificationStatus,
            Collection<PendingImport> rows) {
        Set<PendingImportStatus> statuses = EnumSet.noneOf(PendingImportStatus.class);
        rows.forEach(row -> statuses.add(row.getStatus()));
        PendingImportStatus activeStatus = activeQueueStatus(statuses);
        boolean latestFailed = rows.stream().max(ENQUEUE_ORDER)
                .map(row -> row.getStatus() == PendingImportStatus.FAILED).orElse(false);
        return activeStatus == null && latestFailed
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
                                           PendingImportOrigin latestQueueOrigin, boolean hasPipelineRun) {
        if (hasUnfinishedRecheck) {
            return CatalogPipeline.FAST;
        }
        if (FULL_PIPELINE_ORIGINS.contains(latestQueueOrigin) || hasFinishedRecheck) {
            return CatalogPipeline.FULL;
        }
        return hasPipelineRun ? CatalogPipeline.UNKNOWN : CatalogPipeline.LEGACY;
    }

    public static CatalogPipeline pipeline(Collection<PendingImport> queueRows, boolean hasPipelineRun) {
        boolean hasUnfinishedRecheck = queueRows.stream()
                .anyMatch(queueRow -> isRecheck(queueRow.getOrigin()) && queueRow.getStatus() != PendingImportStatus.DONE);
        boolean hasFinishedRecheck = queueRows.stream()
                .anyMatch(queueRow -> isRecheck(queueRow.getOrigin()) && queueRow.getStatus() == PendingImportStatus.DONE);
        PendingImportOrigin latestQueueOrigin = queueRows.stream()
                .max(ENQUEUE_ORDER)
                .map(PendingImport::getOrigin)
                .orElse(null);
        return pipeline(hasUnfinishedRecheck, hasFinishedRecheck, latestQueueOrigin, hasPipelineRun);
    }

    public static CatalogAddedByKind addedByKind(String addedByUsername, boolean hasAdminSeedRow, boolean hasPipelineRun) {
        if (addedByUsername != null) {
            return CatalogAddedByKind.USER;
        }
        if (hasAdminSeedRow) {
            return CatalogAddedByKind.ADMIN_SEED;
        }
        return hasPipelineRun ? CatalogAddedByKind.UNKNOWN : CatalogAddedByKind.LEGACY;
    }
}
