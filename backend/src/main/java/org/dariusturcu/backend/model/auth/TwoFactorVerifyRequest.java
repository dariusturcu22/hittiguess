package org.dariusturcu.backend.model.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TwoFactorVerifyRequest(
        @NotBlank(message = "Pending token is required")
        @Size(max = 512, message = "Pending token must be at most 512 characters")
        String pendingToken,

        @NotBlank(message = "Code is required")
        @Size(max = 32, message = "Code must be at most 32 characters")
        String code
) {
}
