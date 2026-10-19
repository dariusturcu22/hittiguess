package org.dariusturcu.backend.model.auth;

import jakarta.validation.constraints.NotBlank;

public record TwoFactorVerifyRequest(
        @NotBlank(message = "Pending token is required")
        String pendingToken,

        @NotBlank(message = "Code is required")
        String code
) {
}
