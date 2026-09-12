package org.dariusturcu.backend.service;

import org.dariusturcu.backend.exception.ConflictException;
import org.dariusturcu.backend.exception.ResourceNotFoundException;
import org.dariusturcu.backend.model.RefreshToken;
import org.dariusturcu.backend.model.auth.AuthResult;
import org.dariusturcu.backend.model.auth.LoginRequest;
import org.dariusturcu.backend.model.auth.RegisterRequest;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.RefreshTokenRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.security.util.JwtUtil;
import org.dariusturcu.backend.security.util.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private static final String USERNAME = "someuser";
    private static final String EMAIL = "someone@example.com";
    private static final String PASSWORD = "password123";
    private static final String REFRESH_TOKEN_VALUE = "raw-refresh-token";

    private User existingUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername(USERNAME);
        user.setEmail(EMAIL);
        user.setRole(Role.USER);
        return user;
    }

    @Test
    void registerRejectsAnAlreadyTakenUsername() {
        RegisterRequest request = new RegisterRequest(USERNAME, EMAIL, PASSWORD);
        when(userRepository.existsUserByUsername(USERNAME)).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request)).isInstanceOf(ConflictException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void registerRejectsAnAlreadyTakenEmail() {
        RegisterRequest request = new RegisterRequest(USERNAME, EMAIL, PASSWORD);
        when(userRepository.existsUserByUsername(USERNAME)).thenReturn(false);
        when(userRepository.existsUserByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request)).isInstanceOf(ConflictException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void registerCreatesALocalUserWithAnEncodedPasswordAndIssuesTokens() {
        RegisterRequest request = new RegisterRequest(USERNAME, EMAIL, PASSWORD);
        when(userRepository.existsUserByUsername(USERNAME)).thenReturn(false);
        when(userRepository.existsUserByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("encoded-password");
        User savedUser = existingUser();
        when(userRepository.save(any())).thenReturn(savedUser);
        when(jwtUtil.generateToken(savedUser)).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken()).thenReturn(REFRESH_TOKEN_VALUE);
        when(jwtUtil.getRefreshExpirationSeconds()).thenReturn(3600L);

        AuthResult result = authService.register(request);

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo(REFRESH_TOKEN_VALUE);
        verify(refreshTokenRepository).save(any());
    }

    @Test
    void loginRejectsAWrongPasswordForAnExistingAccountWithTheGenericMessage() {
        LoginRequest request = new LoginRequest(EMAIL, "wrong-password");
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Bad credentials");
    }

    @Test
    void loginRejectsAGoogleOnlyAccountAttemptingAPasswordLoginWithTheSameGenericMessageAsAWrongPassword() {
        // BCrypt throws IllegalArgumentException when comparing against a null password hash,
        // which is what a Google-only account has. The service must not let that surface as a
        // distinct error, or it becomes a way to tell which accounts exist and how they log in.
        LoginRequest request = new LoginRequest(EMAIL, PASSWORD);
        when(authenticationManager.authenticate(any())).thenThrow(new IllegalArgumentException("no password set"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid username or password");
    }

    @Test
    void loginIssuesFreshTokensForAValidCredentialPair() {
        LoginRequest request = new LoginRequest(EMAIL, PASSWORD);
        User user = existingUser();
        when(userRepository.findUserByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(user)).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken()).thenReturn(REFRESH_TOKEN_VALUE);
        when(jwtUtil.getRefreshExpirationSeconds()).thenReturn(3600L);

        AuthResult result = authService.login(request);

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.username()).isEqualTo(USERNAME);
    }

    @Test
    void loginFailsWhenTheAuthenticatedAccountCannotBeReloadedByEmail() {
        LoginRequest request = new LoginRequest(EMAIL, PASSWORD);
        when(userRepository.findUserByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void refreshTokensRejectsATokenThatIsNotOnRecord() {
        when(refreshTokenRepository.findByToken(TokenHasher.hash(REFRESH_TOKEN_VALUE))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refreshTokens(REFRESH_TOKEN_VALUE)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void refreshTokensRejectsAnExpiredTokenAndDeletesIt() {
        RefreshToken expiredToken = new RefreshToken();
        expiredToken.setExpiresAt(Instant.now().minusSeconds(60));
        when(refreshTokenRepository.findByToken(TokenHasher.hash(REFRESH_TOKEN_VALUE))).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> authService.refreshTokens(REFRESH_TOKEN_VALUE)).isInstanceOf(RuntimeException.class);

        verify(refreshTokenRepository).delete(expiredToken);
    }

    @Test
    void refreshTokensRotatesTheStoredTokenAndIssuesANewPair() {
        User user = existingUser();
        RefreshToken storedToken = new RefreshToken();
        storedToken.setUser(user);
        storedToken.setExpiresAt(Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findByToken(TokenHasher.hash(REFRESH_TOKEN_VALUE))).thenReturn(Optional.of(storedToken));
        when(jwtUtil.generateToken(user)).thenReturn("new-access-token");
        when(jwtUtil.generateRefreshToken()).thenReturn("new-refresh-token");
        when(jwtUtil.getRefreshExpirationSeconds()).thenReturn(3600L);

        AuthResult result = authService.refreshTokens(REFRESH_TOKEN_VALUE);

        verify(refreshTokenRepository).delete(storedToken);
        verify(refreshTokenRepository).save(any());
        assertThat(result.accessToken()).isEqualTo("new-access-token");
        assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
    }

    @Test
    void revokeRefreshTokenDeletesTheMatchingStoredTokenWhenItExists() {
        RefreshToken storedToken = new RefreshToken();
        when(refreshTokenRepository.findByToken(TokenHasher.hash(REFRESH_TOKEN_VALUE))).thenReturn(Optional.of(storedToken));

        authService.revokeRefreshToken(REFRESH_TOKEN_VALUE);

        verify(refreshTokenRepository).delete(storedToken);
    }

    @Test
    void revokeRefreshTokenDoesNothingWhenTheTokenIsNotOnRecord() {
        when(refreshTokenRepository.findByToken(TokenHasher.hash(REFRESH_TOKEN_VALUE))).thenReturn(Optional.empty());

        authService.revokeRefreshToken(REFRESH_TOKEN_VALUE);

        verify(refreshTokenRepository, never()).delete(any());
    }
}
