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

import java.util.List;

/**
 * Creates three reusable TEST-role accounts with known credentials on startup, so
 * agents and contributors testing a real multiplayer round (one DJ, one active
 * player, one other player) log in as three genuinely separate accounts instead of
 * registering fresh ones per run. Idempotent per account: running this against a
 * database that already has a given account is a no-op for that one.
 *
 * Disabled outright in Production through the profile condition below, which
 * matches the profile EnvironmentGuard treats as Production. The accounts'
 * credentials are documented in docs/DEV_SETUP.md.
 */
@Component
@Profile("!" + EnvironmentGuard.PRODUCTION_PROFILE)
public class TestAccountSeeder implements ApplicationRunner {

    public record TestAccount(String username, String email, String password) {
    }

    public static final TestAccount TEST_ACCOUNT_1 =
            new TestAccount("hittiguess-test-agent-1", "test-agent-1@hittiguess.local", "HittiguessTestAgent1!2026");
    public static final TestAccount TEST_ACCOUNT_2 =
            new TestAccount("hittiguess-test-agent-2", "test-agent-2@hittiguess.local", "HittiguessTestAgent2!2026");
    public static final TestAccount TEST_ACCOUNT_3 =
            new TestAccount("hittiguess-test-agent-3", "test-agent-3@hittiguess.local", "HittiguessTestAgent3!2026");

    public static final List<TestAccount> TEST_ACCOUNTS = List.of(TEST_ACCOUNT_1, TEST_ACCOUNT_2, TEST_ACCOUNT_3);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public TestAccountSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedTestAccounts();
    }

    public void seedTestAccounts() {
        for (TestAccount testAccount : TEST_ACCOUNTS) {
            seedTestAccount(testAccount);
        }
    }

    private void seedTestAccount(TestAccount testAccount) {
        if (userRepository.existsUserByEmail(testAccount.email())) {
            return;
        }

        User user = new User();
        user.setUsername(testAccount.username());
        user.setEmail(testAccount.email());
        user.setPassword(passwordEncoder.encode(testAccount.password()));
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setRole(Role.TEST);
        userRepository.save(user);
    }
}
