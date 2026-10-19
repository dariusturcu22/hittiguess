package org.dariusturcu.backend.service;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.exception.ConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
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
import org.dariusturcu.backend.model.user.AuthProvider;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.EmailVerificationTokenRepository;
import org.dariusturcu.backend.repository.PasswordResetTokenRepository;
import org.dariusturcu.backend.repository.RefreshTokenRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.security.util.JwtUtil;
import org.dariusturcu.backend.security.util.TokenHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {
    private static final String EMAIL_NOT_VERIFIED_MESSAGE =
            "Email not verified, check your inbox for a verification link";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final TwoFactorService twoFactorService;
    private final LoginAttemptService loginAttemptService;

    @Value("${email.verification-token-expiration}")
    private long emailVerificationTokenExpirationMillis;

    @Value("${email.password-reset-token-expiration}")
    private long passwordResetTokenExpirationMillis;

    @Value("${frontend.url}")
    private String frontendUrl;

    public RegisterResult register(RegisterRequest request) {
        boolean usernameOrEmailTaken = userRepository.existsUserByUsername(request.username())
                || userRepository.existsUserByEmail(request.email());

        if (usernameOrEmailTaken) {
            throw new ConflictException("Username or email already in use");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setRole(Role.USER);
        user.setEmailVerified(false);

        User savedUser = userRepository.save(user);
        sendVerificationEmail(savedUser);

        return new RegisterResult(savedUser.getId(), savedUser.getUsername(), savedUser.getEmail());
    }

    public LoginOutcome login(LoginRequest request) {
        // A locked account is refused before the password is checked, so a correct guess
        // during the lockout doesn't succeed or reveal itself.
        userRepository.findUserByEmail(request.email()).ifPresent(loginAttemptService::requireNotLocked);

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.email(),
                            request.password()
                    )
            );
        } catch (IllegalArgumentException passwordlessAccountException) {
            // BCrypt throws this for an account with no password set, a Google-only account
            // trying to log in with a password. Same response as any other wrong credentials,
            // so this doesn't become a second way to tell accounts apart.
            recordFailedLogin(request.email());
            throw new BadCredentialsException("Invalid username or password");
        } catch (BadCredentialsException wrongCredentialsException) {
            recordFailedLogin(request.email());
            throw wrongCredentialsException;
        }

        User user = userRepository.findUserByEmail(request.email())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException(EMAIL_NOT_VERIFIED_MESSAGE);
        }

        if (user.isTwoFactorEnabled()) {
            return new LoginOutcome.TwoFactorRequired(jwtUtil.generateTwoFactorPendingToken(user));
        }

        loginAttemptService.resetFailures(user);
        return new LoginOutcome.Completed(issueTokens(user, Boolean.TRUE.equals(request.rememberMe())));
    }

    private void recordFailedLogin(String email) {
        userRepository.findUserByEmail(email).map(User::getId).ifPresent(loginAttemptService::recordFailure);
    }

    public AuthResult verifyTwoFactor(String pendingToken, String code) {
        Long userId;
        try {
            if (!jwtUtil.isTwoFactorPendingToken(pendingToken)) {
                throw new BadCredentialsException("Invalid two-factor session");
            }
            userId = jwtUtil.extractUserId(pendingToken);
        } catch (JwtException exception) {
            throw new BadCredentialsException("Two-factor session expired, please log in again");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.isTwoFactorEnabled()) {
            throw new BadCredentialsException("Two-factor authentication is not enabled for this account");
        }

        loginAttemptService.requireNotLocked(user);
        if (!twoFactorService.verifyLoginCode(user, code)) {
            loginAttemptService.recordFailure(user.getId());
            throw new BadCredentialsException("Invalid authentication code");
        }

        loginAttemptService.resetFailures(user);
        return issueTokens(user, false);
    }

    public void verifyEmail(String rawToken) {
        EmailVerificationToken token = emailVerificationTokenRepository.findByToken(TokenHasher.hash(rawToken))
                .orElseThrow(() -> new ResourceNotFoundException("Invalid verification token"));

        if (token.isExpired()) {
            emailVerificationTokenRepository.delete(token);
            throw new ResourceNotFoundException("Verification token expired, request a new one");
        }

        User user = token.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);
        emailVerificationTokenRepository.delete(token);
    }

    public void resendVerificationEmail(String email) {
        userRepository.findUserByEmail(email)
                .filter(user -> !user.isEmailVerified())
                .ifPresent(this::sendVerificationEmail);
        // Same response whether the email doesn't exist or is already verified, so a caller
        // can't use this endpoint to tell either apart from an actual resend.
    }

    public void requestPasswordReset(String email) {
        userRepository.findUserByEmail(email).ifPresent(this::sendPasswordResetEmail);
        // Always succeeds from the caller's perspective, see requestPasswordReset's caller
        // in AuthController: a nonexistent email must not be distinguishable from a real one.
    }

    public void confirmPasswordReset(String rawToken, String newPassword) {
        PasswordResetToken token = passwordResetTokenRepository.findByToken(TokenHasher.hash(rawToken))
                .orElseThrow(() -> new ResourceNotFoundException("Invalid password reset token"));

        if (token.isUsed()) {
            throw new ConflictException("Password reset token already used");
        }
        if (token.isExpired()) {
            throw new ResourceNotFoundException("Password reset token expired, request a new one");
        }

        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        token.setUsed(true);
        passwordResetTokenRepository.save(token);

        // A password change invalidates any session started under the old password.
        refreshTokenRepository.deleteRefreshTokenByUser(user);
    }

    public AuthResult refreshTokens(String refreshToken) {
        RefreshToken storedToken = refreshTokenRepository.findByToken(TokenHasher.hash(refreshToken))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (storedToken.isExpired()) {
            refreshTokenRepository.delete(storedToken);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired, please log in again");
        }

        User user = storedToken.getUser();
        boolean rememberMe = storedToken.isRememberMe();
        refreshTokenRepository.delete(storedToken);

        return issueTokens(user, rememberMe);
    }

    public void revokeRefreshToken(String refreshToken) {
        refreshTokenRepository.findByToken(TokenHasher.hash(refreshToken))
                .ifPresent(refreshTokenRepository::delete);
    }

    private AuthResult issueTokens(User user, boolean rememberMe) {
        String newAccessToken = jwtUtil.generateToken(user);
        String newRefreshToken = createAndSaveRefreshToken(user, rememberMe);
        return new AuthResult(newAccessToken, newRefreshToken, user.getId(), user.getUsername(), user.getEmail(), rememberMe);
    }

    private void sendVerificationEmail(User user) {
        emailVerificationTokenRepository.deleteEmailVerificationTokenByUser(user);
        emailVerificationTokenRepository.flush();

        String rawToken = UUID.randomUUID().toString();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setToken(TokenHasher.hash(rawToken));
        token.setUser(user);
        token.setExpiresAt(Instant.now().plusMillis(emailVerificationTokenExpirationMillis));
        emailVerificationTokenRepository.save(token);

        String verificationLink = frontendUrl + "/verify-email?token=" + rawToken;
        emailService.sendVerificationEmail(user.getEmail(), verificationLink);
    }

    private void sendPasswordResetEmail(User user) {
        passwordResetTokenRepository.deletePasswordResetTokenByUser(user);
        passwordResetTokenRepository.flush();

        String rawToken = UUID.randomUUID().toString();
        PasswordResetToken token = new PasswordResetToken();
        token.setToken(TokenHasher.hash(rawToken));
        token.setUser(user);
        token.setExpiresAt(Instant.now().plusMillis(passwordResetTokenExpirationMillis));
        passwordResetTokenRepository.save(token);

        String resetLink = frontendUrl + "/reset-password?token=" + rawToken;
        emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
    }

    @Modifying
    public String createAndSaveRefreshToken(User user, boolean rememberMe) {
        refreshTokenRepository.deleteRefreshTokenByUser(user);
        refreshTokenRepository.flush();
        String tokenValue = jwtUtil.generateRefreshToken();
        RefreshToken newToken = new RefreshToken();
        newToken.setToken(TokenHasher.hash(tokenValue));
        newToken.setUser(user);
        newToken.setRememberMe(rememberMe);
        long lifetimeSeconds = rememberMe
                ? jwtUtil.getRememberedRefreshExpirationSeconds()
                : jwtUtil.getRefreshExpirationSeconds();
        newToken.setExpiresAt(Instant.now().plusSeconds(lifetimeSeconds));
        refreshTokenRepository.save(newToken);
        return tokenValue;
    }
}
