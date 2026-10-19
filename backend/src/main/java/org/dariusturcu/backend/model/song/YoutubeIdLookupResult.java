package org.dariusturcu.backend.model.song;

import java.util.Set;

/**
 * The outcome of the batch YouTube-ID lookup: which submitted IDs are already
 * known to the catalog (matched either Song's own youtubeId or the alternate-ID
 * table) and which are genuinely new. This is the shared first step both the
 * admin backlog enqueue and the user on-the-spot import depend on, no external
 * API calls involved.
 */
public record YoutubeIdLookupResult(
        Set<String> knownYoutubeIds,
        Set<String> unknownYoutubeIds) {
}
