package org.dariusturcu.backend.model.playlist;

public record PlaylistImportJobItemDTO(
        String youtubeId,
        PlaylistImportJobItemStatus status,
        Long songId,
        String rawTitle,
        String rawChannelTitle,
        String resolvedTitle,
        String resolvedArtists,
        Integer resolvedReleaseYear,
        String resolvedColor
) {
}
