package org.dariusturcu.backend.service;

import org.dariusturcu.backend.exception.RateLimitExceededException;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    private static final long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;

    private LoginAttemptService loginAttemptService;
    private User user;

    @BeforeEach
    void setUp() {
        loginAttemptService = new LoginAttemptService(userRepository);
        user = new User();
        user.setId(USER_ID);
    }

    @Test
    void anAccountWithNoLockIsAllowed() {
        assertThatCode(() -> loginAttemptService.requireNotLocked(user)).doesNotThrowAnyException();
    }

    @Test
    void anAccountLockedUntilALaterTimeIsRefused() {
        user.setLoginLockedUntil(Instant.now().plusSeconds(60));

        assertThatThrownBy(() -> loginAttemptService.requireNotLocked(user))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void anExpiredLockNoLongerRefuses() {
        user.setLoginLockedUntil(Instant.now().minusSeconds(1));

        assertThatCode(() -> loginAttemptService.requireNotLocked(user)).doesNotThrowAnyException();
    }

    @Test
    void failuresBelowTheLimitCountUpWithoutLocking() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        for (int attempt = 1; attempt < LoginAttemptService.MAX_FAILED_ATTEMPTS; attempt++) {
            loginAttemptService.recordFailure(USER_ID);
        }

        assertThat(user.getFailedLoginAttempts()).isEqualTo(LoginAttemptService.MAX_FAILED_ATTEMPTS - 1);
        assertThat(user.getLoginLockedUntil()).isNull();
    }

    @Test
    void reachingTheLimitLocksTheAccountForTheCoolDownAndStartsTheCountAgain() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        Instant beforeLock = Instant.now();

        for (int attempt = 0; attempt < LoginAttemptService.MAX_FAILED_ATTEMPTS; attempt++) {
            loginAttemptService.recordFailure(USER_ID);
        }

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLoginLockedUntil())
                .isAfterOrEqualTo(beforeLock.plus(LoginAttemptService.LOCKOUT_DURATION));
        assertThatThrownBy(() -> loginAttemptService.requireNotLocked(user))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void resetClearsTheCountAndTheLock() {
        user.setFailedLoginAttempts(LoginAttemptService.MAX_FAILED_ATTEMPTS - 1);
        user.setLoginLockedUntil(Instant.now().plusSeconds(60));

        loginAttemptService.resetFailures(user);

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLoginLockedUntil()).isNull();
    }
}
