package org.dariusturcu.backend.model.user;

import java.time.Instant;

public record PlaylistMembershipExportDTO(
        Long playlistId,
        String playlistName,
        boolean owner,
        boolean canRead,
        boolean canWrite,
        boolean canDelete,
        String displayName,
        String avatarUrl,
        Instant joinedAt
) {
}
