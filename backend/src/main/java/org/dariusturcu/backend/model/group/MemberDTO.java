package org.dariusturcu.backend.model.group;

import java.time.Instant;

// Carries only a member's per-group identity. A member's account profile
// (username, email, account avatar) is never reachable through this DTO or
// anything derived from it.
public record MemberDTO(
        Long id,
        String displayName,
        String avatarUrl,
        boolean isAdmin,
        boolean isConnected,
        boolean isInVoice,
        Instant joinedAt) {
}
