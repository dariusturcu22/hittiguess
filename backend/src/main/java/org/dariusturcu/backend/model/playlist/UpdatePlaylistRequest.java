package org.dariusturcu.backend.model.playlist;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdatePlaylistRequest(
        String name,

        @Pattern(regexp = "(?i)^(cba6f7|fab387|a6e3a1|89b4fa|f5c2e7|f9e2af)$", message = "Must be an approved playlist color")
        String color,

        @Size(max = Playlist.MAX_DESCRIPTION_LENGTH, message = "Description must be at most " + Playlist.MAX_DESCRIPTION_LENGTH + " characters")
        String description
) {
}
