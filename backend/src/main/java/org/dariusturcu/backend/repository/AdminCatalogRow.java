package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.song.CatalogSongStatus;
import org.dariusturcu.backend.model.song.PendingImportOrigin;

import java.time.Instant;
import java.util.List;

/**
 * One catalog row as the list query returns it: the row's fields and status, plus the backlog
 * facts the pipeline and added-by rules need.
 */
public record AdminCatalogRow(
        String rowKey,
        Long songId,
        String youtubeId,
        String title,
        List<String> artists,
        Integer releaseYear,
        Integer proposedReleaseYear,
        CatalogSongStatus status,
        boolean hasUnfinishedRecheck,
        boolean hasFinishedRecheck,
        PendingImportOrigin latestQueueOrigin,
        boolean hasPipelineRun,
        boolean hasAdminSeedRow,
        Instant createdAt,
        String addedByUsername) {
    public AdminCatalogRow(
            String rowKey, Long songId, String youtubeId, String title, List<String> artists,
            Integer releaseYear, CatalogSongStatus status, boolean hasUnfinishedRecheck,
            boolean hasFinishedRecheck, PendingImportOrigin latestQueueOrigin, boolean hasPipelineRun,
            boolean hasAdminSeedRow, Instant createdAt, String addedByUsername) {
        this(rowKey, songId, youtubeId, title, artists, releaseYear, null, status, hasUnfinishedRecheck,
                hasFinishedRecheck, latestQueueOrigin, hasPipelineRun, hasAdminSeedRow, createdAt, addedByUsername);
    }
}
