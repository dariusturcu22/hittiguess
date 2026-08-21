package org.dariusturcu.backend.model.playlist;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdatePlaylistRequest(
        @NotBlank(message = "Playlist name is required")
        String name,

        @NotBlank(message = "Color is required")
        @Pattern(regexp = "^[0-9a-fA-F]{6}$", message = "Must be a 6-character hex color without #")
        String color
) {
}
