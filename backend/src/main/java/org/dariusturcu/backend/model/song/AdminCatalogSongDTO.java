package org.dariusturcu.backend.model.song;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/**
 * One row of the admin song catalog: a song, or a backlog item with no song yet. The id is a
 * stable key such as {@code song:12} or {@code import:34}. A null addedBy with addedByKind
 * ADMIN_SEED is an admin catalog seed; the frontend owns that wording.
 */
public record AdminCatalogSongDTO(
        @NotNull String id,
        Long songId,
        @NotNull String youtubeId,
        String title,
        @NotNull List<String> artists,
        Integer releaseYear,
        Integer proposedReleaseYear,
        @NotNull CatalogSongStatus status,
        @NotNull CatalogPipeline pipeline,
        Instant createdAt,
        String addedBy,
        @NotNull CatalogAddedByKind addedByKind) {
    public AdminCatalogSongDTO(
            String id, Long songId, String youtubeId, String title, List<String> artists,
            Integer releaseYear, CatalogSongStatus status, CatalogPipeline pipeline,
            Instant createdAt, String addedBy, CatalogAddedByKind addedByKind) {
        this(id, songId, youtubeId, title, artists, releaseYear, null, status, pipeline, createdAt, addedBy, addedByKind);
    }
}
