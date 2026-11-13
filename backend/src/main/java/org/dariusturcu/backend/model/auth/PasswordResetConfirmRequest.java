package org.dariusturcu.backend.model.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank(message = "Token is required")
        @Size(max = 512, message = "Token must be at most 512 characters")
        String token,

        @NotBlank(message = "New password is required")
        @Size(min = 6, max = 128, message = "Password must be between 6 and 128 characters")
        String newPassword
) {
}
