package org.dariusturcu.backend.model.playlist;

import jakarta.validation.constraints.Pattern;

public record UpdatePlaylistRequest(
        String name,

        @Pattern(regexp = "(?i)^(cba6f7|fab387|a6e3a1|89b4fa|f5c2e7|f9e2af)$", message = "Must be an approved playlist color")
        String color
) {
}
