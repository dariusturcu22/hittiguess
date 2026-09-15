package org.dariusturcu.backend.model.group;

import java.time.Instant;

// The message payload broadcast over the group's chat topic and returned from history.
// Carries the sender's per-group display name rather than any account-profile field,
// the same identity boundary MemberDTO draws.
public record ChatMessageDTO(
        Long id,
        Long groupId,
        Long senderId,
        String senderDisplayName,
        String content,
        Instant createdAt) {
}
