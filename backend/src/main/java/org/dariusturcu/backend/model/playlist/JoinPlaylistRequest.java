package org.dariusturcu.backend.model.playlist;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.dariusturcu.backend.model.group.MemberIdentityConstraints;

public record JoinPlaylistRequest(
        @Size(max = MemberIdentityConstraints.DISPLAY_NAME_MAX_LENGTH, message = MemberIdentityConstraints.DISPLAY_NAME_MESSAGE)
        @Pattern(regexp = MemberIdentityConstraints.DISPLAY_NAME_PATTERN, message = MemberIdentityConstraints.DISPLAY_NAME_MESSAGE)
        String displayName,

        @Size(max = MemberIdentityConstraints.AVATAR_URL_MAX_LENGTH, message = MemberIdentityConstraints.AVATAR_URL_MESSAGE)
        @Pattern(regexp = MemberIdentityConstraints.AVATAR_URL_PATTERN, message = MemberIdentityConstraints.AVATAR_URL_MESSAGE)
        String avatarUrl) {
}
