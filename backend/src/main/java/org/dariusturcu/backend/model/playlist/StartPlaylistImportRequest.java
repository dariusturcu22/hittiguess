package org.dariusturcu.backend.model.playlist;

import jakarta.validation.constraints.Size;
import java.util.List;

// Starts a background import from a playlist page: explicit video ids or links,
// plus an optional playlist link whose videos merge in, deduplicated.
public record StartPlaylistImportRequest(
        @Size(max = 200, message = "At most 200 video IDs or links can be submitted")
        List<@Size(max = 2000, message = "Video ID or link must be at most 2000 characters") String> videoIdsOrLinks,

        @Size(max = 2000, message = "Playlist link must be at most 2000 characters")
        String playlistLink
) {
}
