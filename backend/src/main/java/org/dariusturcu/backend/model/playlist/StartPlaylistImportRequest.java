package org.dariusturcu.backend.model.playlist;

import java.util.List;

// Starts a background import from a playlist page: explicit video ids or links,
// plus an optional playlist link whose videos merge in, deduplicated.
public record StartPlaylistImportRequest(
        List<String> videoIdsOrLinks,
        String playlistLink
) {
}
