package org.dariusturcu.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dariusturcu.backend.repository.EmailVerificationTokenRepository;
import org.dariusturcu.backend.repository.PasswordResetTokenRepository;
import org.dariusturcu.backend.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenRetentionService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Transactional
    public int purgeExpiredTokens() {
        Instant now = Instant.now();
        int expiredRefreshTokens = refreshTokenRepository.deleteByExpiresAtBefore(now);
        int expiredPasswordResetTokens = passwordResetTokenRepository.deleteByExpiresAtBeforeOrUsedTrue(now);
        int expiredEmailTokens = emailVerificationTokenRepository.deleteByExpiresAtBefore(now);
        int totalPurged = expiredRefreshTokens + expiredPasswordResetTokens + expiredEmailTokens;
        if (totalPurged > 0) {
            log.info("Purged expired tokens: {} refresh, {} password reset, {} email verification",
                    expiredRefreshTokens, expiredPasswordResetTokens, expiredEmailTokens);
        }
        return totalPurged;
    }
}
