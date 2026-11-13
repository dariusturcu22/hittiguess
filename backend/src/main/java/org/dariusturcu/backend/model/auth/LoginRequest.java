package org.dariusturcu.backend.model.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Username and email are required")
        @Size(max = 255, message = "Email or username must be at most 255 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(max = 128, message = "Password must be at most 128 characters")
        String password,

        // Nullable wrapper, not primitive: older clients omit the field, and
        // this codebase's Jackson mapping rejects null into a primitive.
        // Absent means no preference, same as false.
        Boolean rememberMe
) {
}
