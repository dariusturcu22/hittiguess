package org.dariusturcu.backend.model.auth;

// Neither field is individually required: disabling accepts the current password or a
// valid TOTP/backup code, checked in TwoFactorService.disable. Both blank is rejected there,
// not by bean validation, since the rule is "at least one of two", not "both present".
public record TwoFactorDisableRequest(
        String currentPassword,
        String code
) {
}
