package org.dariusturcu.backend.model.auth;

public record TwoFactorSetupResponse(
        String secret,
        String provisioningUri
) {
}
