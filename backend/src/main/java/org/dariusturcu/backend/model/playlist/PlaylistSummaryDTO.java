package org.dariusturcu.backend.model.playlist;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PlaylistSummaryDTO(
        @NotNull
        Long id,
        @NotNull
        String name,
        @NotNull
        String color,
        @NotNull
        int songCount,
        // The first four songs' YouTube IDs, for a default cover mosaic built from their
        // real thumbnails (i.ytimg.com/vi/{id}/hqdefault.jpg) rather than a placeholder
        // derived from the accent color above. Fewer than four songs means fewer entries,
        // not padding; the cover falls back to a placeholder tile for the remaining slots.
        @NotNull
        List<String> previewYoutubeIds
) {
}
