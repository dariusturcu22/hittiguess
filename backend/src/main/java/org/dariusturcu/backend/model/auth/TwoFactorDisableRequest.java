package org.dariusturcu.backend.model.auth;

import jakarta.validation.constraints.Size;

// Neither field is individually required: disabling accepts the current password or a
// valid TOTP/backup code, checked in TwoFactorService.disable. Both blank is rejected there,
// not by bean validation, since the rule is "at least one of two", not "both present".
public record TwoFactorDisableRequest(
        @Size(max = 255, message = "Password must be at most 255 characters")
        String currentPassword,

        @Size(max = 32, message = "Code must be at most 32 characters")
        String code
) {
}
