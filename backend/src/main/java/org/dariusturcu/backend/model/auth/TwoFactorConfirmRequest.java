package org.dariusturcu.backend.model.auth;

import jakarta.validation.constraints.NotBlank;

public record TwoFactorConfirmRequest(
        @NotBlank(message = "Code is required")
        String code
) {
}
