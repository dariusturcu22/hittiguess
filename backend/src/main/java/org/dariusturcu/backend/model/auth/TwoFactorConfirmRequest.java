package org.dariusturcu.backend.model.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TwoFactorConfirmRequest(
        @NotBlank(message = "Code is required")
        @Size(max = 32, message = "Code must be at most 32 characters")
        String code
) {
}
