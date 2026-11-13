package org.dariusturcu.backend.model.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RunNowRequest(
        @NotBlank(message = "YouTube ID is required")
        @Pattern(regexp = "^[a-zA-Z0-9_-]{11}$", message = "Must be a valid YouTube video ID")
        String youtubeId) {
}
