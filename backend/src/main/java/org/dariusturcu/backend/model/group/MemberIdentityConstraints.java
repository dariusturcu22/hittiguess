package org.dariusturcu.backend.model.group;

// Bounds on the per-group identity a member picks when creating or joining a group. Every
// member's browser loads the avatar URL, so it's limited to Google profile images, the
// only avatar URLs the app itself produces.
public final class MemberIdentityConstraints {

    public static final int DISPLAY_NAME_MAX_LENGTH = 30;
    // \p{Cc} rather than Java's \p{Cntrl}, so the pattern published in the OpenAPI spec also
    // compiles as a JavaScript regular expression in the generated client schemas.
    public static final String DISPLAY_NAME_PATTERN = "[^\\p{Cc}]*\\S[^\\p{Cc}]*";
    public static final String DISPLAY_NAME_MESSAGE =
            "Display name must be 1 to 30 characters with no control characters";

    public static final int AVATAR_URL_MAX_LENGTH = 1024;
    public static final String AVATAR_URL_PATTERN = "https://[a-z0-9-]+\\.googleusercontent\\.com/\\S*";
    public static final String AVATAR_URL_MESSAGE = "Avatar URL must be an https Google profile image";

    private MemberIdentityConstraints() {
    }
}
