package org.dariusturcu.backend.model.song;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Expands a YouTube playlist link or id into its video ids for review before
// importing, so the importer shows what will run instead of starting blind.
public record ExpandPlaylistLinkRequest(
        @NotBlank(message = "Playlist link is required")
        @Size(max = 2000, message = "Playlist link must be at most 2000 characters")
        String playlistLink
) {
}
