package org.dariusturcu.backend.model.group;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// displayName and avatarUrl are optional: the creator's per-group identity
// defaults to their account's own values when omitted, same as a joining member.
// When replaceCurrentGroup is true, an existing active membership is left as
// part of the same transaction rather than returning a 409 conflict.
public record CreateGroupRequest(
        @Size(max = MemberIdentityConstraints.DISPLAY_NAME_MAX_LENGTH, message = MemberIdentityConstraints.DISPLAY_NAME_MESSAGE)
        @Pattern(regexp = MemberIdentityConstraints.DISPLAY_NAME_PATTERN, message = MemberIdentityConstraints.DISPLAY_NAME_MESSAGE)
        String displayName,
        @Size(max = MemberIdentityConstraints.AVATAR_URL_MAX_LENGTH, message = MemberIdentityConstraints.AVATAR_URL_MESSAGE)
        @Pattern(regexp = MemberIdentityConstraints.AVATAR_URL_PATTERN, message = MemberIdentityConstraints.AVATAR_URL_MESSAGE)
        String avatarUrl,
        Boolean replaceCurrentGroup) {

    public CreateGroupRequest(String displayName, String avatarUrl) {
        this(displayName, avatarUrl, false);
    }

    public boolean shouldReplaceCurrentGroup() {
        return Boolean.TRUE.equals(replaceCurrentGroup);
    }
}
