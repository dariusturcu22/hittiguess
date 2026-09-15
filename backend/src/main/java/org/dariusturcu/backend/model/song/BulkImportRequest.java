package org.dariusturcu.backend.model.song;

import java.util.List;

/**
 * A user's on-the-spot bulk-import submission: either a YouTube playlist link or a
 * list of video IDs or links. Playlist-link expansion (crawling the playlist to its
 * video IDs) depends on the YouTube Data API, not yet built on the backend; a list
 * of IDs or links is resolved directly.
 */
public record BulkImportRequest(
        String playlistLink,
        List<String> videoIdsOrLinks) {
}
