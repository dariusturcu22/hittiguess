package org.dariusturcu.backend.model.user;

import java.time.Instant;

public record GroupMembershipExportDTO(
        Long groupId,
        boolean isAdmin,
        String displayName,
        String avatarUrl,
        Instant joinedAt
) {
}
