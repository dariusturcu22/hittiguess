package org.dariusturcu.backend.service;

import org.dariusturcu.backend.pipeline.PipelineJobRepository;
import org.dariusturcu.backend.pipeline.PipelineRetentionService;
import org.dariusturcu.backend.repository.EmailVerificationTokenRepository;
import org.dariusturcu.backend.repository.PasswordResetTokenRepository;
import org.dariusturcu.backend.repository.RefreshTokenRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RetentionConfigurationValidationTest {

    private static final int SMALLEST_VALID_RETENTION_DAYS = 1;

    private AccountRetentionService accountRetentionServiceWith(int retentionDays) {
        return new AccountRetentionService(
                mock(UserRepository.class),
                mock(EmailVerificationTokenRepository.class),
                mock(PasswordResetTokenRepository.class),
                mock(RefreshTokenRepository.class),
                retentionDays);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -30})
    void unverifiedAccountRetentionBelowOneDayFailsAtStartup(int retentionDays) {
        assertThatThrownBy(() -> accountRetentionServiceWith(retentionDays))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RETENTION_UNVERIFIED_ACCOUNTS_DAYS");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void pipelineRetentionBelowOneDayFailsAtStartup(int retentionDays) {
        assertThatThrownBy(() -> new PipelineRetentionService(mock(PipelineJobRepository.class), retentionDays))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RETENTION_PIPELINE_JOBS_DAYS");
    }

    @Test
    void oneDayOfRetentionIsAccepted() {
        assertThat(accountRetentionServiceWith(SMALLEST_VALID_RETENTION_DAYS)).isNotNull();
        assertThat(new PipelineRetentionService(mock(PipelineJobRepository.class), SMALLEST_VALID_RETENTION_DAYS))
                .isNotNull();
    }
}
