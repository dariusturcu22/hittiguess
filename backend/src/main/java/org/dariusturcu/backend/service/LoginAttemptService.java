package org.dariusturcu.backend.service;

import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.exception.RateLimitExceededException;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

// Locks an account for a cool-down after too many consecutive failed password or
// two-factor attempts, on top of the per-IP request limit, so a guess spread across many
// addresses still runs out. A failure is written in its own transaction: the login that
// records one always fails right after, and must not roll the count back with it.
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);
    private static final String LOCKED_MESSAGE = "Too many failed sign-in attempts. Try again in a few minutes.";

    private final UserRepository userRepository;

    public void requireNotLocked(User user) {
        Instant lockedUntil = user.getLoginLockedUntil();
        if (lockedUntil != null && lockedUntil.isAfter(Instant.now())) {
            throw new RateLimitExceededException(LOCKED_MESSAGE);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            int failedAttempts = user.getFailedLoginAttempts() + 1;
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                user.setLoginLockedUntil(Instant.now().plus(LOCKOUT_DURATION));
                failedAttempts = 0;
            }
            user.setFailedLoginAttempts(failedAttempts);
            userRepository.save(user);
        });
    }

    // Runs inside the successful login's own transaction, on the same managed user, so the
    // reset commits together with whatever else that login changed on the row.
    public void resetFailures(User user) {
        user.setFailedLoginAttempts(0);
        user.setLoginLockedUntil(null);
    }
}
