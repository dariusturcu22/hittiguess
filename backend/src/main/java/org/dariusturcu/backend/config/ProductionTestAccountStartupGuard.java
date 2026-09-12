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
 * and that is treated as fatal rather than silently ignored.
 */
@Component
@Profile(EnvironmentGuard.PRODUCTION_PROFILE)
public class ProductionTestAccountStartupGuard implements ApplicationRunner {

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
    }
}
