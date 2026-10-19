package org.dariusturcu.backend.model.song;

import java.util.List;

/**
 * An admin's catalog-seeding backlog submission: a YouTube playlist link, a list of
 * video IDs or links, or both, mirroring BulkImportRequest's own shape. A playlist
 * link is expanded into its video IDs through the AI microservice before enqueueing;
 * any IDs or links submitted alongside it are merged in, deduplicated.
 */
public record AdminCatalogSeedingRequest(
        String playlistLink,
        List<String> youtubeIds) {
}
