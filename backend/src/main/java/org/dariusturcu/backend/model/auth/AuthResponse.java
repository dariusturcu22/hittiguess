package org.dariusturcu.backend.model.auth;

public record AuthResponse(
        Long id,
        String username,
        String email,
        boolean emailVerificationRequired
) {
}
