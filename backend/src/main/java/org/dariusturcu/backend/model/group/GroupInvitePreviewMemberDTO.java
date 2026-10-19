package org.dariusturcu.backend.model.group;

// The public invite preview carries only display identity. Internal member
// state (user id, presence, voice, join time) never leaves this endpoint.
public record GroupInvitePreviewMemberDTO(
        String displayName,
        String avatarUrl,
        boolean isAdmin) {
}
