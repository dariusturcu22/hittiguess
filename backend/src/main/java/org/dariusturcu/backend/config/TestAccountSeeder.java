package org.dariusturcu.backend.config;

import org.dariusturcu.backend.model.user.AuthProvider;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates one reusable TEST-role account with known credentials on startup, so
 * agents and contributors running tests locally log in with the same account
 * every time instead of registering a fresh one per run. Idempotent: running
 * this against a database that already has the account is a no-op.
 *
 * Disabled outright in Production through the profile condition below, which
 * matches the profile EnvironmentGuard treats as Production. The account's
 * credentials are documented in docs/DEV_SETUP.md.
 */
@Component
@Profile("!" + EnvironmentGuard.PRODUCTION_PROFILE)
public class TestAccountSeeder implements ApplicationRunner {

    public static final String TEST_ACCOUNT_USERNAME = "hittiguess-test-agent";
    public static final String TEST_ACCOUNT_EMAIL = "test-agent@hittiguess.local";
    public static final String TEST_ACCOUNT_PASSWORD = "HittiguessTestAgent!2026";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public TestAccountSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedTestAccount();
    }

    public void seedTestAccount() {
        if (userRepository.existsUserByEmail(TEST_ACCOUNT_EMAIL)) {
            return;
        }

        User testAccount = new User();
        testAccount.setUsername(TEST_ACCOUNT_USERNAME);
        testAccount.setEmail(TEST_ACCOUNT_EMAIL);
        testAccount.setPassword(passwordEncoder.encode(TEST_ACCOUNT_PASSWORD));
        testAccount.setAuthProvider(AuthProvider.LOCAL);
        testAccount.setRole(Role.TEST);
        userRepository.save(testAccount);
    }
}
