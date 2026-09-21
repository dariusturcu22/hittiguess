package org.dariusturcu.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.model.auth.AuthResponse;
import org.dariusturcu.backend.model.auth.AuthResult;
import org.dariusturcu.backend.model.auth.LoginOutcome;
import org.dariusturcu.backend.model.auth.LoginRequest;
import org.dariusturcu.backend.model.auth.PasswordResetConfirmRequest;
import org.dariusturcu.backend.model.auth.PasswordResetRequest;
import org.dariusturcu.backend.model.auth.RegisterRequest;
import org.dariusturcu.backend.model.auth.RegisterResult;
import org.dariusturcu.backend.model.auth.ResendVerificationRequest;
import org.dariusturcu.backend.model.auth.TwoFactorConfirmRequest;
import org.dariusturcu.backend.model.auth.TwoFactorConfirmResponse;
import org.dariusturcu.backend.model.auth.TwoFactorDisableRequest;
import org.dariusturcu.backend.model.auth.TwoFactorRequiredResponse;
import org.dariusturcu.backend.model.auth.TwoFactorSetupResponse;
import org.dariusturcu.backend.model.auth.TwoFactorVerifyRequest;
import org.dariusturcu.backend.model.auth.VerifyEmailRequest;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.security.util.JwtUtil;
import org.dariusturcu.backend.security.util.SecurityUtils;
import org.dariusturcu.backend.service.AuthService;
import org.dariusturcu.backend.service.TwoFactorService;
import org.dariusturcu.backend.util.CookieUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
@Tag(name = "Authentication Management")

public class AuthController {
    private final AuthService authService;
    private final TwoFactorService twoFactorService;
    private final CookieUtil cookieUtil;
    private final JwtUtil jwtUtil;

    @Operation(summary = "Register a new user")
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        RegisterResult result = authService.register(request);
        return ResponseEntity.ok(new AuthResponse(
                result.id(),
                result.username(),
                result.email(),
                true
        ));
    }

    @Operation(summary = "Login with username/email and password")
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        LoginOutcome outcome = authService.login(request);

        if (outcome instanceof LoginOutcome.TwoFactorRequired twoFactorRequired) {
            return ResponseEntity.ok(new TwoFactorRequiredResponse(twoFactorRequired.pendingToken()));
        }

        AuthResult result = ((LoginOutcome.Completed) outcome).authResult();
        setTokenCookies(response, result);
        return ResponseEntity.ok(new AuthResponse(
                result.id(),
                result.username(),
                result.email(),
                false
        ));
    }

    @Operation(summary = "Complete login for a two-factor-enabled account")
    @PostMapping("/2fa/verify")
    public ResponseEntity<AuthResponse> verifyTwoFactor(
            @Valid @RequestBody TwoFactorVerifyRequest request,
            HttpServletResponse response
    ) {
        AuthResult result = authService.verifyTwoFactor(request.pendingToken(), request.code());
        setTokenCookies(response, result);
        return ResponseEntity.ok(new AuthResponse(
                result.id(),
                result.username(),
                result.email(),
                false
        ));
    }

    @Operation(summary = "Verify an email address using a verification token")
    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Resend the email verification link, rate-limited")
    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerificationEmail(request.email());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Request a password reset email, always succeeds regardless of whether the email exists")
    @PostMapping("/password-reset/request")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        authService.requestPasswordReset(request.email());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Confirm a password reset using a reset token")
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.confirmPasswordReset(request.token(), request.newPassword());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Start TOTP two-factor setup for the current account")
    @PostMapping("/2fa/setup")
    public ResponseEntity<TwoFactorSetupResponse> setupTwoFactor() {
        User currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(twoFactorService.setup(currentUser));
    }

    @Operation(summary = "Confirm TOTP two-factor setup with a code from the authenticator app")
    @PostMapping("/2fa/confirm")
    public ResponseEntity<TwoFactorConfirmResponse> confirmTwoFactor(
            @Valid @RequestBody TwoFactorConfirmRequest request
    ) {
        User currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(new TwoFactorConfirmResponse(twoFactorService.confirm(currentUser, request.code())));
    }

    @Operation(summary = "Disable two-factor authentication, requires the current password or a valid code")
    @PostMapping("/2fa/disable")
    public ResponseEntity<Void> disableTwoFactor(@RequestBody TwoFactorDisableRequest request) {
        User currentUser = SecurityUtils.getCurrentUser();
        twoFactorService.disable(currentUser, request.currentPassword(), request.code());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Refresh access token using refresh token cookie")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String refreshToken = cookieUtil.extractFromCookies(request, "refresh_token")
                .orElseThrow(() -> new RuntimeException("No refresh token present"));

        AuthResult result = authService.refreshTokens(refreshToken);
        setTokenCookies(response, result);
        return ResponseEntity.ok(new AuthResponse(
                result.id(),
                result.username(),
                result.email(),
                false
        ));
    }

    @Operation(summary = "Logout and clear auth tokens")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        cookieUtil.extractFromCookies(request, "refresh_token")
                .ifPresent(token -> {
                    try {
                        authService.revokeRefreshToken(token);
                    } catch (Exception _) {
                    }
                });

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookieUtil.deleteCookie("access_token", "/")
                        .toString()
        );
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookieUtil.deleteCookie("refresh_token", "/auth/refresh")
                        .toString()
        );
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookieUtil.deleteCookie(CookieUtil.SESSION_HINT_COOKIE_NAME, "/")
                        .toString()
        );

        return ResponseEntity.ok().build();
    }

    private void setTokenCookies(HttpServletResponse response, AuthResult result) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieUtil.createAccessTokenCookie(
                        result.accessToken(),
                        jwtUtil.getExpirationSeconds()
                ).toString());
        long refreshLifetimeSeconds = result.rememberMe()
                ? jwtUtil.getRememberedRefreshExpirationSeconds()
                : jwtUtil.getRefreshExpirationSeconds();
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieUtil.createRefreshTokenCookie(
                        result.refreshToken(),
                        refreshLifetimeSeconds
                ).toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieUtil.createSessionHintCookie(
                        refreshLifetimeSeconds
                ).toString());
    }
}
