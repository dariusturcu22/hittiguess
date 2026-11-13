package org.dariusturcu.backend.model.song;

import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A user's on-the-spot bulk-import submission: a YouTube playlist link, a list of
 * video IDs or links, or both. A playlist link is expanded into its video IDs through
 * the AI microservice's YouTube Data API access before resolution runs; any IDs or
 * links submitted alongside it are merged in, deduplicated. When targetPlaylistId is
 * present, every song resolved or already known from this submission is also linked
 * into that playlist; when absent, resolution only affects the shared catalog.
 */
public record BulkImportRequest(
        @Size(max = 2000, message = "Playlist link must be at most 2000 characters")
        String playlistLink,

        @Size(max = 200, message = "At most 200 video IDs or links can be submitted")
        List<@Size(max = 2000, message = "Video ID or link must be at most 2000 characters") String> videoIdsOrLinks,

        Long targetPlaylistId,

        @Size(max = 255, message = "Import job ID must be at most 255 characters")
        String importJobId) {
}
