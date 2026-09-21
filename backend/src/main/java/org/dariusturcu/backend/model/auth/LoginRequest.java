package org.dariusturcu.backend.model.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Username and email are required")
        String email,

        @NotBlank(message = "Password is required")
        String password,

        // Nullable wrapper, not primitive: older clients omit the field, and
        // this codebase's Jackson mapping rejects null into a primitive.
        // Absent means no preference, same as false.
        Boolean rememberMe
) {
}
