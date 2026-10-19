package org.dariusturcu.backend.model.group;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Exactly one of inviteCode or joinCode must be supplied; the service rejects
// requests that give both or neither. displayName and avatarUrl are optional,
// defaulting to the joining user's account values.
public record JoinGroupRequest(
        String inviteCode,
        String joinCode,
        @Size(max = MemberIdentityConstraints.DISPLAY_NAME_MAX_LENGTH, message = MemberIdentityConstraints.DISPLAY_NAME_MESSAGE)
        @Pattern(regexp = MemberIdentityConstraints.DISPLAY_NAME_PATTERN, message = MemberIdentityConstraints.DISPLAY_NAME_MESSAGE)
        String displayName,
        @Size(max = MemberIdentityConstraints.AVATAR_URL_MAX_LENGTH, message = MemberIdentityConstraints.AVATAR_URL_MESSAGE)
        @Pattern(regexp = MemberIdentityConstraints.AVATAR_URL_PATTERN, message = MemberIdentityConstraints.AVATAR_URL_MESSAGE)
        String avatarUrl) {
}
