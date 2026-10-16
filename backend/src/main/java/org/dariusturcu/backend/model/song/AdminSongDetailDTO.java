package org.dariusturcu.backend.model.song;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/**
 * The read-only admin view of one song: its catalog row fields, the verification status,
 * confidence, and Wikidata sitelinks as stored, its open report count, and every backlog row
 * for its YouTube ID, oldest first.
 */
public record AdminSongDetailDTO(
        @NotNull Long songId,
        @NotNull String youtubeId,
        String title,
        @NotNull List<String> artists,
        Integer releaseYear,
        @NotNull CatalogSongStatus status,
        @NotNull CatalogPipeline pipeline,
        Instant createdAt,
        String addedBy,
        @NotNull CatalogAddedByKind addedByKind,
        @NotNull VerificationStatus verificationStatus,
        String confidence,
        Integer wikidataSitelinksCount,
        @NotNull Long openReportCount,
        @NotNull List<AdminQueueHistoryEntryDTO> queueHistory) {
}
