package org.dariusturcu.backend.service;

import org.dariusturcu.backend.exception.ConflictException;
import org.dariusturcu.backend.model.EmailVerificationToken;
import org.dariusturcu.backend.model.user.AuthProvider;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.EmailVerificationTokenRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailChangeServiceTest {

    private static final Long USER_ID = 5L;
    private static final long TOKEN_EXPIRATION_MILLIS = 3_600_000L;
    private static final int TEST_BCRYPT_STRENGTH = 4;
    private static final String CURRENT_PASSWORD = "current-password-1";
    private static final String CURRENT_EMAIL = "player@example.com";
    private static final String NEW_EMAIL = "new.address@example.com";
    private static final String FRONTEND_URL = "http://localhost:3000";

    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock
    private EmailService emailService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(TEST_BCRYPT_STRENGTH);

    private EmailChangeService emailChangeService;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(USER_ID);
        user.setUsername("player");
        user.setEmail(CURRENT_EMAIL);
        user.setPassword(passwordEncoder.encode(CURRENT_PASSWORD));
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setEmailVerified(true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(user), null, null));

        emailChangeService = new EmailChangeService(
                userRepository, emailVerificationTokenRepository, passwordEncoder, emailService);
        ReflectionTestUtils.setField(emailChangeService, "emailVerificationTokenExpirationMillis", TOKEN_EXPIRATION_MILLIS);
        ReflectionTestUtils.setField(emailChangeService, "frontendUrl", FRONTEND_URL);
        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aValidRequestStoresThePendingAddressAndMailsTheLinkToTheNewAddressOnly() {
        when(userRepository.existsUserByEmail(NEW_EMAIL)).thenReturn(false);

        emailChangeService.requestEmailChange("New.Address@Example.com", CURRENT_PASSWORD);

        ArgumentCaptor<EmailVerificationToken> tokenCaptor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(emailVerificationTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getPendingEmail()).isEqualTo(NEW_EMAIL);
        assertThat(tokenCaptor.getValue().getUser()).isSameAs(user);
        verify(emailService).sendVerificationEmail(eq(NEW_EMAIL), startsWith(FRONTEND_URL + "/verify-email?token="));
        assertThat(user.getEmail()).isEqualTo(CURRENT_EMAIL);
        assertThat(user.isEmailVerified()).isTrue();
        verify(userRepository, never()).save(any());
    }

    @Test
    void aWrongPasswordIsRejectedWithoutSendingAnything() {
        assertThatThrownBy(() -> emailChangeService.requestEmailChange(NEW_EMAIL, "wrong-password"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(emailVerificationTokenRepository, never()).save(any());
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    void aMissingPasswordIsRejected() {
        assertThatThrownBy(() -> emailChangeService.requestEmailChange(NEW_EMAIL, null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(emailVerificationTokenRepository, never()).save(any());
    }

    @Test
    void anAddressAnotherAccountOwnsIsRejected() {
        when(userRepository.existsUserByEmail(NEW_EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> emailChangeService.requestEmailChange(NEW_EMAIL, CURRENT_PASSWORD))
                .isInstanceOf(ConflictException.class);

        verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    void resubmittingTheCurrentAddressInAnyCaseDoesNothing() {
        emailChangeService.requestEmailChange("PLAYER@example.com", null);

        verify(emailVerificationTokenRepository, never()).save(any());
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    void aGoogleAccountCannotChangeItsEmail() {
        user.setAuthProvider(AuthProvider.GOOGLE);
        user.setPassword(null);

        assertThatThrownBy(() -> emailChangeService.requestEmailChange(NEW_EMAIL, null))
                .isInstanceOf(ConflictException.class);

        verify(emailVerificationTokenRepository, never()).save(any());
    }
}
