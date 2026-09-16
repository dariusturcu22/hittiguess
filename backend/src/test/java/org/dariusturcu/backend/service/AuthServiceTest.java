package org.dariusturcu.backend.service;

import org.dariusturcu.backend.exception.ConflictException;
import org.dariusturcu.backend.exception.EmailNotVerifiedException;
import org.dariusturcu.backend.exception.ResourceNotFoundException;
import org.dariusturcu.backend.model.EmailVerificationToken;
import org.dariusturcu.backend.model.PasswordResetToken;
import org.dariusturcu.backend.model.RefreshToken;
import org.dariusturcu.backend.model.auth.AuthResult;
import org.dariusturcu.backend.model.auth.LoginOutcome;
import org.dariusturcu.backend.model.auth.LoginRequest;
import org.dariusturcu.backend.model.auth.RegisterRequest;
import org.dariusturcu.backend.model.auth.RegisterResult;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.EmailVerificationTokenRepository;
import org.dariusturcu.backend.repository.PasswordResetTokenRepository;
import org.dariusturcu.backend.repository.RefreshTokenRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.security.util.JwtUtil;
import org.dariusturcu.backend.security.util.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final long TOKEN_EXPIRATION_MILLIS = 3_600_000L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private EmailService emailService;
    @Mock
    private TwoFactorService twoFactorService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, refreshTokenRepository, emailVerificationTokenRepository,
                passwordResetTokenRepository, passwordEncoder, jwtUtil, authenticationManager,
                emailService, twoFactorService);
        ReflectionTestUtils.setField(authService, "emailVerificationTokenExpirationMillis", TOKEN_EXPIRATION_MILLIS);
        ReflectionTestUtils.setField(authService, "passwordResetTokenExpirationMillis", TOKEN_EXPIRATION_MILLIS);
        ReflectionTestUtils.setField(authService, "frontendUrl", "http://localhost:3000");
    }

    private User buildUser(boolean emailVerified, boolean twoFactorEnabled) {
        User user = new User();
        user.setId(1L);
        user.setUsername("player");
        user.setEmail("player@example.com");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setRole(Role.USER);
        user.setEmailVerified(emailVerified);
        user.setTwoFactorEnabled(twoFactorEnabled);
        return user;
    }

    @Test
    void registerSendsAVerificationEmailAndDoesNotIssueTokens() {
        RegisterRequest request = new RegisterRequest("player", "player@example.com", "password123");
        when(userRepository.existsUserByUsername("player")).thenReturn(false);
        when(userRepository.existsUserByEmail("player@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        RegisterResult result = authService.register(request);

        assertThat(result.id()).isEqualTo(1L);
        verify(emailService).sendVerificationEmail(eq("player@example.com"), anyString());
        verify(jwtUtil, never()).generateToken(any(User.class));
    }

    @Test
    void registerRejectsAnAlreadyTakenUsernameOrEmail() {
        RegisterRequest request = new RegisterRequest("player", "player@example.com", "password123");
        when(userRepository.existsUserByUsername("player")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request)).isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginBlocksAnUnverifiedAccountWithADistinctError() {
        User user = buildUser(false, false);
        when(userRepository.findUserByEmail("player@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("player@example.com", "password123")))
                .isInstanceOf(EmailNotVerifiedException.class);

        verify(jwtUtil, never()).generateToken(any(User.class));
    }

    @Test
    void loginCompletesNormallyForAVerifiedAccountWithoutTwoFactor() {
        User user = buildUser(true, false);
        when(userRepository.findUserByEmail("player@example.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(user)).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken()).thenReturn("refresh-token");
        when(jwtUtil.getRefreshExpirationSeconds()).thenReturn(604800L);

        LoginOutcome outcome = authService.login(new LoginRequest("player@example.com", "password123"));

        assertThat(outcome).isInstanceOf(LoginOutcome.Completed.class);
        AuthResult result = ((LoginOutcome.Completed) outcome).authResult();
        assertThat(result.accessToken()).isEqualTo("access-token");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void loginForATwoFactorEnabledAccountReturnsAPendingTokenInsteadOfCompletingLogin() {
        User user = buildUser(true, true);
        when(userRepository.findUserByEmail("player@example.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateTwoFactorPendingToken(user)).thenReturn("pending-token");

        LoginOutcome outcome = authService.login(new LoginRequest("player@example.com", "password123"));

        assertThat(outcome).isInstanceOf(LoginOutcome.TwoFactorRequired.class);
        assertThat(((LoginOutcome.TwoFactorRequired) outcome).pendingToken()).isEqualTo("pending-token");
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void verifyTwoFactorRejectsAWrongCode() {
        User user = buildUser(true, true);
        when(jwtUtil.isTwoFactorPendingToken("pending-token")).thenReturn(true);
        when(jwtUtil.extractUserId("pending-token")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(twoFactorService.verifyLoginCode(user, "000000")).thenReturn(false);

        assertThatThrownBy(() -> authService.verifyTwoFactor("pending-token", "000000"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void verifyTwoFactorIssuesRealTokensOnACorrectCode() {
        User user = buildUser(true, true);
        when(jwtUtil.isTwoFactorPendingToken("pending-token")).thenReturn(true);
        when(jwtUtil.extractUserId("pending-token")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(twoFactorService.verifyLoginCode(user, "123456")).thenReturn(true);
        when(jwtUtil.generateToken(user)).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken()).thenReturn("refresh-token");
        when(jwtUtil.getRefreshExpirationSeconds()).thenReturn(604800L);

        AuthResult result = authService.verifyTwoFactor("pending-token", "123456");

        assertThat(result.accessToken()).isEqualTo("access-token");
    }

    @Test
    void verifyEmailWithAValidTokenMarksTheAccountVerifiedAndConsumesTheToken() {
        User user = buildUser(false, false);
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setToken(TokenHasher.hash("raw-token"));
        token.setExpiresAt(Instant.now().plusSeconds(60));
        when(emailVerificationTokenRepository.findByToken(TokenHasher.hash("raw-token")))
                .thenReturn(Optional.of(token));

        authService.verifyEmail("raw-token");

        assertThat(user.isEmailVerified()).isTrue();
        verify(emailVerificationTokenRepository).delete(token);
    }

    @Test
    void verifyEmailWithAnExpiredTokenIsRejectedAndTheTokenIsConsumed() {
        User user = buildUser(false, false);
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setToken(TokenHasher.hash("raw-token"));
        token.setExpiresAt(Instant.now().minusSeconds(60));
        when(emailVerificationTokenRepository.findByToken(TokenHasher.hash("raw-token")))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.verifyEmail("raw-token"))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(user.isEmailVerified()).isFalse();
        verify(emailVerificationTokenRepository).delete(token);
    }

    @Test
    void verifyEmailWithAnUnknownTokenIsRejected() {
        when(emailVerificationTokenRepository.findByToken(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail("unknown-token"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resendVerificationEmailDoesNothingForAnAlreadyVerifiedAccount() {
        User user = buildUser(true, false);
        when(userRepository.findUserByEmail("player@example.com")).thenReturn(Optional.of(user));

        authService.resendVerificationEmail("player@example.com");

        verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    void resendVerificationEmailDoesNothingForANonexistentAccount() {
        when(userRepository.findUserByEmail("nobody@example.com")).thenReturn(Optional.empty());

        authService.resendVerificationEmail("nobody@example.com");

        verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    void requestPasswordResetAlwaysReturnsNormallyForANonexistentEmail() {
        when(userRepository.findUserByEmail("nobody@example.com")).thenReturn(Optional.empty());

        authService.requestPasswordReset("nobody@example.com");

        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    void requestPasswordResetSendsAnEmailForARealAccount() {
        User user = buildUser(true, false);
        when(userRepository.findUserByEmail("player@example.com")).thenReturn(Optional.of(user));

        authService.requestPasswordReset("player@example.com");

        verify(emailService).sendPasswordResetEmail(eq("player@example.com"), anyString());
    }

    @Test
    void confirmPasswordResetWithAValidTokenChangesThePasswordAndRevokesSessions() {
        User user = buildUser(true, false);
        String originalHash = user.getPassword();
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setToken(TokenHasher.hash("raw-token"));
        token.setExpiresAt(Instant.now().plusSeconds(60));
        when(passwordResetTokenRepository.findByToken(TokenHasher.hash("raw-token")))
                .thenReturn(Optional.of(token));

        authService.confirmPasswordReset("raw-token", "new-password123");

        assertThat(user.getPassword()).isNotEqualTo(originalHash);
        assertThat(token.isUsed()).isTrue();
        verify(refreshTokenRepository).deleteRefreshTokenByUser(user);
    }

    @Test
    void confirmPasswordResetRejectsAnAlreadyUsedToken() {
        User user = buildUser(true, false);
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setToken(TokenHasher.hash("raw-token"));
        token.setExpiresAt(Instant.now().plusSeconds(60));
        token.setUsed(true);
        when(passwordResetTokenRepository.findByToken(TokenHasher.hash("raw-token")))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.confirmPasswordReset("raw-token", "new-password123"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void confirmPasswordResetRejectsAnExpiredToken() {
        User user = buildUser(true, false);
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setToken(TokenHasher.hash("raw-token"));
        token.setExpiresAt(Instant.now().minusSeconds(60));
        when(passwordResetTokenRepository.findByToken(TokenHasher.hash("raw-token")))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.confirmPasswordReset("raw-token", "new-password123"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
