package org.dariusturcu.backend.model.playlist;

/**
 * The outcome of copying a source playlist's songs into a target playlist:
 * how many song links were added to the target this request, and how many
 * were skipped because the song was already present in the target.
 */
public record ImportFromPlaylistResultDTO(
        int importedCount,
        int skippedCount) {
}
