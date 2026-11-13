package org.dariusturcu.backend.model.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyEmailRequest(
        @NotBlank(message = "Token is required")
        @Size(max = 512, message = "Token must be at most 512 characters")
        String token
) {
}
