package org.dariusturcu.backend.model.auth;

import java.util.List;

public record TwoFactorConfirmResponse(
        List<String> backupCodes
) {
}
