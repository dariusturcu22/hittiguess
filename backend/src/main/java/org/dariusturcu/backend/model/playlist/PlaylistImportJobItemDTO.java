package org.dariusturcu.backend.model.playlist;

public record PlaylistImportJobItemDTO(
        String youtubeId,
        PlaylistImportJobItemStatus status,
        Long songId
) {
}
