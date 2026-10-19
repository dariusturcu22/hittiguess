package org.dariusturcu.backend.model.playlist;

import java.time.Instant;

public record PlaylistMemberDTO(
        Long userId,
        String username,
        String displayName,
        String avatarUrl,
        boolean owner,
        boolean canRead,
        boolean canWrite,
        boolean canDelete,
        Instant joinedAt) {
}
