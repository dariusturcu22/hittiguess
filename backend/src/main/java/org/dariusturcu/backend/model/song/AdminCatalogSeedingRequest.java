package org.dariusturcu.backend.model.song;

import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * An admin's catalog-seeding backlog submission: a YouTube playlist link, a list of
 * video IDs or links, or both, mirroring BulkImportRequest's own shape. A playlist
 * link is expanded into its video IDs through the AI microservice before enqueueing;
 * any IDs or links submitted alongside it are merged in, deduplicated.
 */
public record AdminCatalogSeedingRequest(
        @Size(max = 2000, message = "Playlist link must be at most 2000 characters")
        String playlistLink,

        @Size(max = 1000, message = "At most 1000 video IDs can be submitted")
        List<@Size(max = 2000, message = "YouTube ID or link must be at most 2000 characters") String> youtubeIds) {
}
