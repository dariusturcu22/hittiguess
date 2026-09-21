package org.dariusturcu.backend.model.auth;

public record AuthResult(
        String accessToken,
        String refreshToken,
        Long id,
        String username,
        String email,
        boolean rememberMe
) {
}
