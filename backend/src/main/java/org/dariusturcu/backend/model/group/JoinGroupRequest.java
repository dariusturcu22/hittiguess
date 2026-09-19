package org.dariusturcu.backend.model.group;

// Exactly one of inviteCode or joinCode must be supplied; the service rejects
// requests that give both or neither. displayName and avatarUrl are optional,
// defaulting to the joining user's account values.
public record JoinGroupRequest(
        String inviteCode,
        String joinCode,
        String displayName,
        String avatarUrl) {
}
