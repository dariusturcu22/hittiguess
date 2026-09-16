package org.dariusturcu.backend.service;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.model.TwoFactorBackupCode;
import org.dariusturcu.backend.model.auth.TwoFactorSetupResponse;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.TwoFactorBackupCodeRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class TwoFactorService {
    private static final String TOTP_ISSUER = "hittiguess";
    private static final int TOTP_DIGITS = 6;
    private static final int TOTP_PERIOD_SECONDS = 30;
    // Allows one 30-second step of clock drift either side of the server's own time,
    // matching this library's own documented usage for handling client/server clock skew.
    private static final int ALLOWED_TIME_PERIOD_DISCREPANCY = 1;
    private static final int BACKUP_CODE_COUNT = 10;
    private static final int BACKUP_CODE_LENGTH = 10;
    private static final String BACKUP_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final UserRepository userRepository;
    private final TwoFactorBackupCodeRepository backupCodeRepository;
    private final PasswordEncoder passwordEncoder;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator();
    private final TimeProvider timeProvider = new SystemTimeProvider();
    private final SecureRandom secureRandom = new SecureRandom();

    public TwoFactorSetupResponse setup(User user) {
        String secret = secretGenerator.generate();
        user.setTotpSecret(secret);
        user.setTwoFactorEnabled(false);
        userRepository.save(user);
        return new TwoFactorSetupResponse(secret, buildProvisioningUri(user, secret));
    }

    public List<String> confirm(User user, String code) {
        if (user.getTotpSecret() == null) {
            throw new IllegalStateException("Two-factor setup has not been started");
        }
        if (!verifyTotpCode(user.getTotpSecret(), code)) {
            throw new IllegalArgumentException("Invalid verification code");
        }

        user.setTwoFactorEnabled(true);
        userRepository.save(user);
        return generateBackupCodes(user);
    }

    public void disable(User user, String currentPassword, String code) {
        boolean passwordMatches = user.getPassword() != null
                && currentPassword != null
                && passwordEncoder.matches(currentPassword, user.getPassword());
        boolean codeMatches = code != null && verifyLoginCode(user, code);

        if (!passwordMatches && !codeMatches) {
            throw new IllegalArgumentException(
                    "Current password or a valid authentication code is required to disable two-factor authentication");
        }

        user.setTotpSecret(null);
        user.setTwoFactorEnabled(false);
        userRepository.save(user);
        backupCodeRepository.deleteByUser(user);
    }

    // Tries a TOTP code first, then falls back to an unused backup code. Used both by the
    // second step of a 2FA login and by /auth/2fa/disable's own proof-of-possession check.
    public boolean verifyLoginCode(User user, String code) {
        return verifyTotpCode(user.getTotpSecret(), code) || verifyAndConsumeBackupCode(user, code);
    }

    public boolean verifyTotpCode(String secret, String code) {
        if (secret == null || code == null) {
            return false;
        }
        DefaultCodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
        verifier.setAllowedTimePeriodDiscrepancy(ALLOWED_TIME_PERIOD_DISCREPANCY);
        return verifier.isValidCode(secret, code);
    }

    public boolean verifyAndConsumeBackupCode(User user, String code) {
        List<TwoFactorBackupCode> unusedCodes = backupCodeRepository.findByUserAndUsedFalse(user);
        for (TwoFactorBackupCode backupCode : unusedCodes) {
            if (passwordEncoder.matches(code, backupCode.getCodeHash())) {
                backupCode.setUsed(true);
                backupCodeRepository.save(backupCode);
                return true;
            }
        }
        return false;
    }

    private String buildProvisioningUri(User user, String secret) {
        QrData data = new QrData.Builder()
                .label(user.getEmail())
                .secret(secret)
                .issuer(TOTP_ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(TOTP_DIGITS)
                .period(TOTP_PERIOD_SECONDS)
                .build();
        return data.getUri();
    }

    private List<String> generateBackupCodes(User user) {
        backupCodeRepository.deleteByUser(user);

        List<String> rawCodes = new ArrayList<>();
        List<TwoFactorBackupCode> entities = new ArrayList<>();
        for (int codeIndex = 0; codeIndex < BACKUP_CODE_COUNT; codeIndex++) {
            String rawCode = generateRawBackupCode();
            rawCodes.add(rawCode);

            TwoFactorBackupCode entity = new TwoFactorBackupCode();
            entity.setUser(user);
            entity.setCodeHash(passwordEncoder.encode(rawCode));
            entities.add(entity);
        }
        backupCodeRepository.saveAll(entities);
        return rawCodes;
    }

    private String generateRawBackupCode() {
        StringBuilder code = new StringBuilder(BACKUP_CODE_LENGTH);
        for (int characterIndex = 0; characterIndex < BACKUP_CODE_LENGTH; characterIndex++) {
            int alphabetPosition = secureRandom.nextInt(BACKUP_CODE_ALPHABET.length());
            code.append(BACKUP_CODE_ALPHABET.charAt(alphabetPosition));
        }
        return code.toString();
    }
}
