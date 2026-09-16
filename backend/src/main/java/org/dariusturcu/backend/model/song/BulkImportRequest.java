package org.dariusturcu.backend.model.song;

import java.util.List;

/**
 * A user's on-the-spot bulk-import submission: a YouTube playlist link, a list of
 * video IDs or links, or both. A playlist link is expanded into its video IDs through
 * the AI microservice's YouTube Data API access before resolution runs; any IDs or
 * links submitted alongside it are merged in, deduplicated.
 */
public record BulkImportRequest(
        String playlistLink,
        List<String> videoIdsOrLinks) {
}
