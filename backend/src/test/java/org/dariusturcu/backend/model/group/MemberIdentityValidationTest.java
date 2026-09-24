package org.dariusturcu.backend.model.group;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class MemberIdentityValidationTest {

    private static final String JOIN_CODE = "WXYZ";
    private static final String GOOGLE_PROFILE_IMAGE = "https://lh3.googleusercontent.com/a/profile-photo=s96-c";

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean joinIsValid(String displayName, String avatarUrl) {
        return validator.validate(new JoinGroupRequest(null, JOIN_CODE, displayName, avatarUrl)).isEmpty();
    }

    private boolean createIsValid(String displayName, String avatarUrl) {
        return validator.validate(new CreateGroupRequest(displayName, avatarUrl)).isEmpty();
    }

    @Test
    void omittedIdentityFieldsAreAllowed() {
        assertThat(joinIsValid(null, null)).isTrue();
        assertThat(createIsValid(null, null)).isTrue();
    }

    @Test
    void aNormalDisplayNameAndAGoogleProfileImageAreAllowed() {
        assertThat(joinIsValid("Mara", GOOGLE_PROFILE_IMAGE)).isTrue();
        assertThat(createIsValid("Mara", GOOGLE_PROFILE_IMAGE)).isTrue();
    }

    @Test
    void aDisplayNameAtTheLengthLimitIsAllowedAndOneOverIsRefused() {
        String atLimit = "a".repeat(MemberIdentityConstraints.DISPLAY_NAME_MAX_LENGTH);
        String overLimit = "a".repeat(MemberIdentityConstraints.DISPLAY_NAME_MAX_LENGTH + 1);

        assertThat(joinIsValid(atLimit, null)).isTrue();
        assertThat(joinIsValid(overLimit, null)).isFalse();
        assertThat(createIsValid(overLimit, null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "tab\there", "line\nbreak"})
    void blankOrControlCharacterDisplayNamesAreRefused(String displayName) {
        assertThat(joinIsValid(displayName, null)).isFalse();
        assertThat(createIsValid(displayName, null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://lh3.googleusercontent.com/a/photo",
            "https://tracker.example.com/pixel.png",
            "https://googleusercontent.com.example.com/photo",
            "javascript:alert(1)",
            "data:image/png;base64,AAAA"
    })
    void avatarUrlsOutsideGoogleProfileImagesAreRefused(String avatarUrl) {
        assertThat(joinIsValid(null, avatarUrl)).isFalse();
        assertThat(createIsValid(null, avatarUrl)).isFalse();
    }
}
