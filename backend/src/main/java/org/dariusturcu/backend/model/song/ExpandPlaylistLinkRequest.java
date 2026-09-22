package org.dariusturcu.backend.model.song;

// Expands a YouTube playlist link or id into its video ids for review before
// importing, so the importer shows what will run instead of starting blind.
public record ExpandPlaylistLinkRequest(
        String playlistLink
) {
}
