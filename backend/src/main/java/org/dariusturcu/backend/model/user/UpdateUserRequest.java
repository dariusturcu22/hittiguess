package org.dariusturcu.backend.model.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(min = 3, max = 20, message = "Username must be between 3 and 20 characters long")
        String username,

        @Email(message = "Email must be valid")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email,

        @Size(max = 128, message = "Password must be at most 128 characters long")
        String currentPassword
) {
}
