package org.dariusturcu.backend.model.song;

import java.util.Set;

/**
 * The outcome of a user on-the-spot bulk import: which submitted IDs were already
 * known (skipped), which were resolved immediately this request, and which failed
 * to resolve. Every immediately-resolved ID is also re-enqueued into the admin
 * backlog for the patient pipeline afterward.
 */
public record BulkImportResultDTO(
        Set<String> alreadyKnownYoutubeIds,
        Set<String> resolvedYoutubeIds,
        Set<String> unresolvedYoutubeIds) {
}
