package org.dariusturcu.backend.security.oauth2;

import org.dariusturcu.backend.model.user.AuthProvider;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    private static final String GOOGLE_SUBJECT = "google-subject-1";
    private static final String GOOGLE_NAME = "Google Player";
    private static final String GOOGLE_EMAIL = "google.player@example.com";
    private static final String GOOGLE_PICTURE = "https://example.com/picture.png";

    @Mock
    private UserRepository userRepository;

    private CustomOAuth2UserService service() {
        return new CustomOAuth2UserService(userRepository);
    }

    private GoogleOAuth2UserInfo userInfo() {
        return new GoogleOAuth2UserInfo(Map.of(
                "sub", GOOGLE_SUBJECT,
                "name", GOOGLE_NAME,
                "email", GOOGLE_EMAIL,
                "picture", GOOGLE_PICTURE));
    }

    private User processOAuth2User(String registrationId, OAuth2UserInfo info) {
        return ReflectionTestUtils.invokeMethod(service(), "processOAuth2User", registrationId, info);
    }

    private User existingGoogleUser() {
        User user = new User();
        user.setId(7L);
        user.setUsername("Old Name");
        user.setEmail(GOOGLE_EMAIL);
        user.setAuthProvider(AuthProvider.GOOGLE);
        user.setAuthProviderId(GOOGLE_SUBJECT);
        user.setRole(Role.USER);
        user.setEmailVerified(false);
        return user;
    }

    @Test
    void firstGoogleLoginProvisionsAVerifiedUser() {
        when(userRepository.findUserByAuthProviderAndAuthProviderId(AuthProvider.GOOGLE, GOOGLE_SUBJECT))
                .thenReturn(Optional.empty());
        when(userRepository.existsUserByEmail(GOOGLE_EMAIL)).thenReturn(false);
        when(userRepository.existsUserByUsername(GOOGLE_NAME)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = processOAuth2User("google", userInfo());

        assertThat(user.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(user.getAuthProviderId()).isEqualTo(GOOGLE_SUBJECT);
        assertThat(user.getUsername()).isEqualTo(GOOGLE_NAME);
        assertThat(user.getEmail()).isEqualTo(GOOGLE_EMAIL);
        assertThat(user.isEmailVerified()).isTrue();
    }

    @Test
    void returningGoogleLoginRefreshesProfileAndKeepsVerification() {
        User user = existingGoogleUser();
        when(userRepository.findUserByAuthProviderAndAuthProviderId(AuthProvider.GOOGLE, GOOGLE_SUBJECT))
                .thenReturn(Optional.of(user));
        when(userRepository.existsUserByUsername(GOOGLE_NAME)).thenReturn(false);
        when(userRepository.save(user)).thenReturn(user);

        User result = processOAuth2User("google", userInfo());

        assertThat(result.getUsername()).isEqualTo(GOOGLE_NAME);
        assertThat(result.getImageUrl()).isEqualTo(GOOGLE_PICTURE);
        assertThat(result.isEmailVerified()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void googleLoginAgainstAnExistingLocalEmailIsRejected() {
        when(userRepository.findUserByAuthProviderAndAuthProviderId(AuthProvider.GOOGLE, GOOGLE_SUBJECT))
                .thenReturn(Optional.empty());
        when(userRepository.existsUserByEmail(GOOGLE_EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> processOAuth2User("google", userInfo()))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void googleUserInfoMapsTheStandardClaims() {
        GoogleOAuth2UserInfo info = userInfo();

        assertThat(info.getId()).isEqualTo(GOOGLE_SUBJECT);
        assertThat(info.getName()).isEqualTo(GOOGLE_NAME);
        assertThat(info.getEmail()).isEqualTo(GOOGLE_EMAIL);
        assertThat(info.getImageUrl()).isEqualTo(GOOGLE_PICTURE);
    }

    @Test
    void googleUserInfoWithoutAPictureMapsToNull() {
        GoogleOAuth2UserInfo info = new GoogleOAuth2UserInfo(Map.of(
                "sub", GOOGLE_SUBJECT,
                "name", GOOGLE_NAME,
                "email", GOOGLE_EMAIL));

        assertThat(info.getImageUrl()).isNull();
    }
}
