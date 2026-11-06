package org.dariusturcu.backend.config;

import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fails application startup if a TEST-role row exists while running against
 * Production. A TEST-role account is only ever supposed to exist in local or
 * dev environments; TestAccountSeeder is disabled in Production, so finding
 * one here means the row got into the Production database some other way,
 * and that is treated as fatal rather than silently ignored. An ADMIN account on a
 * .local address is the seeded development admin and is rejected the same way.
 */
@Component
@Profile(EnvironmentGuard.PRODUCTION_PROFILE)
public class ProductionTestAccountStartupGuard implements ApplicationRunner {

    static final String TEST_ACCOUNT_EMAIL_SUFFIX = ".local";

    private final UserRepository userRepository;

    public ProductionTestAccountStartupGuard(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsUserByRole(Role.TEST)) {
            throw new IllegalStateException(
                    "A TEST-role user exists in the Production database. Remove it before this application can start."
            );
        }
        if (userRepository.existsUserByRoleAndEmailEndingWithIgnoreCase(Role.ADMIN, TEST_ACCOUNT_EMAIL_SUFFIX)) {
            throw new IllegalStateException(
                    "An ADMIN user with a .local email address exists in the Production database. "
                            + "Remove it or give it a real address before this application can start."
            );
        }
    }
}
