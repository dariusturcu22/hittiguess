package org.dariusturcu.backend.service;

import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.exception.ConflictException;
import org.dariusturcu.backend.model.EmailVerificationToken;
import org.dariusturcu.backend.model.user.AuthProvider;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.EmailVerificationTokenRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.security.util.SecurityUtils;
import org.dariusturcu.backend.security.util.TokenHasher;
import org.dariusturcu.backend.util.EmailAddresses;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

// An email change never takes effect on request. The new address is stored on the account's
// verification token and only replaces the current one when the link sent to that address is
// opened (AuthService.verifyEmail), so nobody can claim an address they don't control.
@Service
@RequiredArgsConstructor
@Transactional
public class EmailChangeService {

    private static final String EMAIL_IN_USE_MESSAGE = "Username or email already in use";
    private static final String PASSWORD_REQUIRED_MESSAGE = "Your current password is required to change your email";
    private static final String PROVIDER_MANAGED_MESSAGE =
            "The email of an account that signs in with Google is managed by Google";

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${email.verification-token-expiration}")
    private long emailVerificationTokenExpirationMillis;

    @Value("${frontend.url}")
    private String frontendUrl;

    public void requestEmailChange(String requestedEmail, String currentPassword) {
        User user = userRepository.findById(SecurityUtils.getCurrentUser().getId()).orElseThrow();
        String newEmail = EmailAddresses.normalize(requestedEmail);

        if (newEmail.equalsIgnoreCase(user.getEmail())) {
            return;
        }
        if (user.getAuthProvider() != AuthProvider.LOCAL) {
            throw new ConflictException(PROVIDER_MANAGED_MESSAGE);
        }
        if (user.getPassword() != null
                && (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword()))) {
            throw new IllegalArgumentException(PASSWORD_REQUIRED_MESSAGE);
        }
        if (userRepository.existsUserByEmail(newEmail)) {
            throw new ConflictException(EMAIL_IN_USE_MESSAGE);
        }

        emailVerificationTokenRepository.deleteEmailVerificationTokenByUser(user);
        emailVerificationTokenRepository.flush();

        String rawToken = UUID.randomUUID().toString();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setToken(TokenHasher.hash(rawToken));
        token.setUser(user);
        token.setPendingEmail(newEmail);
        token.setExpiresAt(Instant.now().plusMillis(emailVerificationTokenExpirationMillis));
        emailVerificationTokenRepository.save(token);

        emailService.sendVerificationEmail(newEmail, frontendUrl + "/verify-email?token=" + rawToken);
    }
}
