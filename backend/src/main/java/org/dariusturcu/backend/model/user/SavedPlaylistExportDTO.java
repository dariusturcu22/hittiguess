package org.dariusturcu.backend.model.user;

import java.time.Instant;

public record SavedPlaylistExportDTO(
        Long playlistId,
        String playlistName,
        Instant savedAt
) {
}
