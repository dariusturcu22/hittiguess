package org.dariusturcu.backend.model.song;

import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.dariusturcu.backend.model.admin.SourceFindingDTO;

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
        Integer proposedReleaseYear,
        Instant proposedAt,
        @NotNull CatalogSongStatus status,
        @NotNull CatalogPipeline pipeline,
        Instant createdAt,
        String addedBy,
        @NotNull CatalogAddedByKind addedByKind,
        @NotNull VerificationStatus verificationStatus,
        String confidence,
        Integer wikidataSitelinksCount,
        @NotNull Long openReportCount,
        @NotNull List<AdminQueueHistoryEntryDTO> queueHistory,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<SourceFindingDTO> sourceFindings,
        @JsonInclude(JsonInclude.Include.NON_NULL) String reviewReason) {
    public AdminSongDetailDTO(Long songId, String youtubeId, String title, List<String> artists, Integer releaseYear,
            CatalogSongStatus status, CatalogPipeline pipeline, Instant createdAt, String addedBy,
            CatalogAddedByKind addedByKind, VerificationStatus verificationStatus, String confidence,
            Integer wikidataSitelinksCount, Long openReportCount, List<AdminQueueHistoryEntryDTO> queueHistory) {
        this(songId, youtubeId, title, artists, releaseYear, null, null, status, pipeline, createdAt, addedBy, addedByKind,
                verificationStatus, confidence, wikidataSitelinksCount, openReportCount, queueHistory, null, null);
    }
}
