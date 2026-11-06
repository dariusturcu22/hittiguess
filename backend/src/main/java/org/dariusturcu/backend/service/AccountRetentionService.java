package org.dariusturcu.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.EmailVerificationTokenRepository;
import org.dariusturcu.backend.repository.PasswordResetTokenRepository;
import org.dariusturcu.backend.repository.RefreshTokenRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Slf4j
public class AccountRetentionService {

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final int unverifiedAccountRetentionDays;

    public AccountRetentionService(
            UserRepository userRepository,
            EmailVerificationTokenRepository emailVerificationTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            RefreshTokenRepository refreshTokenRepository,
            @Value("${retention.unverified-accounts-days:7}") int unverifiedAccountRetentionDays
    ) {
        this.userRepository = userRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.unverifiedAccountRetentionDays = unverifiedAccountRetentionDays;
    }

    @Transactional
    public int purgeUnverifiedAccounts() {
        Instant cutoff = Instant.now().minus(unverifiedAccountRetentionDays, ChronoUnit.DAYS);
        List<User> staleUnverifiedUsers = userRepository.findByEmailVerifiedFalseAndCreatedAtBefore(cutoff);
        for (User staleUser : staleUnverifiedUsers) {
            emailVerificationTokenRepository.deleteEmailVerificationTokenByUser(staleUser);
            passwordResetTokenRepository.deletePasswordResetTokenByUser(staleUser);
            refreshTokenRepository.deleteRefreshTokenByUser(staleUser);
            userRepository.delete(staleUser);
        }
        if (!staleUnverifiedUsers.isEmpty()) {
            log.info("Purged {} unverified accounts older than {} days",
                    staleUnverifiedUsers.size(), unverifiedAccountRetentionDays);
        }
        return staleUnverifiedUsers.size();
    }
}
