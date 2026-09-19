package org.dariusturcu.backend.model.auth;

public record RegisterResult(
        Long id,
        String username,
        String email
) {
}
