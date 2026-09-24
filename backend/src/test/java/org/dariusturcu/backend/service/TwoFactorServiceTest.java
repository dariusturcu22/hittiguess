package org.dariusturcu.backend.service;

import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.dariusturcu.backend.exception.ConflictException;
import org.dariusturcu.backend.model.TwoFactorBackupCode;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.TwoFactorBackupCodeRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TwoFactorServiceTest {

    private static final long TOTP_PERIOD_SECONDS = 30;
    private static final long CURRENT_STEP = 0;
    private static final long NEXT_STEP = 1;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TwoFactorBackupCodeRepository backupCodeRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private TwoFactorService twoFactorService;
    private User user;

    @BeforeEach
    void setUp() {
        twoFactorService = new TwoFactorService(userRepository, backupCodeRepository, passwordEncoder);
        user = new User();
        user.setId(1L);
        user.setEmail("player@example.com");
        user.setPassword(passwordEncoder.encode("correct-password"));
    }

    @Test
    void setupStoresAPendingSecretWithoutEnablingTwoFactor() {
        var response = twoFactorService.setup(user);

        assertThat(user.getTotpSecret()).isEqualTo(response.secret());
        assertThat(user.isTwoFactorEnabled()).isFalse();
        assertThat(response.provisioningUri()).startsWith("otpauth://totp/");
        verify(userRepository).save(user);
    }

    @Test
    void confirmWithNoPendingSetupIsRejected() {
        assertThatThrownBy(() -> twoFactorService.confirm(user, "123456"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void confirmWithAWrongCodeDoesNotEnableTwoFactor() {
        twoFactorService.setup(user);

        assertThatThrownBy(() -> twoFactorService.confirm(user, "000000"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(user.isTwoFactorEnabled()).isFalse();
        verify(backupCodeRepository, never()).saveAll(any());
    }

    @Test
    void confirmWithTheRightCodeEnablesTwoFactorAndReturnsTenBackupCodes() throws Exception {
        twoFactorService.setup(user);
        String validCode = new DefaultCodeGenerator().generate(user.getTotpSecret(),
                new SystemTimeProvider().getTime() / 30);

        List<String> backupCodes = twoFactorService.confirm(user, validCode);

        assertThat(user.isTwoFactorEnabled()).isTrue();
        assertThat(backupCodes).hasSize(10);
        assertThat(backupCodes).doesNotHaveDuplicates();
        verify(backupCodeRepository).saveAll(any());
    }

    @Test
    void disableWithoutAPasswordOrCodeIsRejected() {
        user.setTwoFactorEnabled(true);
        user.setTotpSecret("SECRET");

        assertThatThrownBy(() -> twoFactorService.disable(user, null, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(user.isTwoFactorEnabled()).isTrue();
        verify(userRepository, never()).save(any());
    }

    @Test
    void disableWithAWrongPasswordAndNoCodeIsRejected() {
        user.setTwoFactorEnabled(true);
        user.setTotpSecret("SECRET");

        assertThatThrownBy(() -> twoFactorService.disable(user, "wrong-password", null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(user.isTwoFactorEnabled()).isTrue();
    }

    @Test
    void disableWithTheCorrectCurrentPasswordClearsTheSecretAndBackupCodes() {
        user.setTwoFactorEnabled(true);
        user.setTotpSecret("SECRET");

        twoFactorService.disable(user, "correct-password", null);

        assertThat(user.isTwoFactorEnabled()).isFalse();
        assertThat(user.getTotpSecret()).isNull();
        verify(backupCodeRepository).deleteByUser(user);
        verify(userRepository).save(user);
    }

    @Test
    void disableWithAValidBackupCodeInsteadOfAPasswordSucceeds() {
        user.setTwoFactorEnabled(true);
        user.setTotpSecret("SECRET");
        TwoFactorBackupCode storedCode = new TwoFactorBackupCode();
        storedCode.setUser(user);
        storedCode.setCodeHash(passwordEncoder.encode("BACKUP1234"));
        when(backupCodeRepository.findByUserAndUsedFalse(user)).thenReturn(List.of(storedCode));

        twoFactorService.disable(user, null, "BACKUP1234");

        assertThat(user.isTwoFactorEnabled()).isFalse();
        verify(backupCodeRepository).deleteByUser(user);
    }

    @Test
    void verifyAndConsumeBackupCodeMarksTheMatchingCodeUsedAndRejectsReuse() {
        TwoFactorBackupCode storedCode = new TwoFactorBackupCode();
        storedCode.setUser(user);
        storedCode.setCodeHash(passwordEncoder.encode("BACKUP1234"));
        when(backupCodeRepository.findByUserAndUsedFalse(user))
                .thenReturn(List.of(storedCode))
                .thenReturn(List.of());

        boolean firstAttempt = twoFactorService.verifyAndConsumeBackupCode(user, "BACKUP1234");
        boolean secondAttempt = twoFactorService.verifyAndConsumeBackupCode(user, "BACKUP1234");

        assertThat(firstAttempt).isTrue();
        assertThat(storedCode.isUsed()).isTrue();
        assertThat(secondAttempt).isFalse();
    }

    private String codeForStepOffset(String secret, long stepOffset) throws Exception {
        return new DefaultCodeGenerator().generate(secret,
                new SystemTimeProvider().getTime() / TOTP_PERIOD_SECONDS + stepOffset);
    }

    @Test
    void setupWhileTwoFactorIsOnIsRefusedAndLeavesItOn() {
        user.setTwoFactorEnabled(true);
        user.setTotpSecret("SECRET");

        assertThatThrownBy(() -> twoFactorService.setup(user))
                .isInstanceOf(ConflictException.class);

        assertThat(user.isTwoFactorEnabled()).isTrue();
        assertThat(user.getTotpSecret()).isEqualTo("SECRET");
        verify(userRepository, never()).save(any());
    }

    @Test
    void aLoginCodeIsAcceptedOnceAndRefusedWhenReplayed() throws Exception {
        twoFactorService.setup(user);
        twoFactorService.confirm(user, codeForStepOffset(user.getTotpSecret(), CURRENT_STEP));
        String nextCode = codeForStepOffset(user.getTotpSecret(), NEXT_STEP);

        assertThat(twoFactorService.verifyLoginCode(user, nextCode)).isTrue();
        assertThat(twoFactorService.verifyLoginCode(user, nextCode)).isFalse();
    }

    @Test
    void theCodeUsedToConfirmSetupCannotBeReusedToLogIn() throws Exception {
        twoFactorService.setup(user);
        String confirmationCode = codeForStepOffset(user.getTotpSecret(), CURRENT_STEP);
        twoFactorService.confirm(user, confirmationCode);

        assertThat(twoFactorService.verifyLoginCode(user, confirmationCode)).isFalse();
    }

    @Test
    void aCodeFromAnEarlierStepThanTheLastUsedOneIsRefused() throws Exception {
        twoFactorService.setup(user);
        twoFactorService.confirm(user, codeForStepOffset(user.getTotpSecret(), NEXT_STEP));

        assertThat(twoFactorService.verifyLoginCode(user, codeForStepOffset(user.getTotpSecret(), CURRENT_STEP))).isFalse();
    }

    @Test
    void verifyTotpCodeRejectsAnObviouslyWrongCode() {
        String secret = twoFactorService.setup(user).secret();

        assertThat(twoFactorService.verifyTotpCode(secret, "000000")).isFalse();
    }
}
