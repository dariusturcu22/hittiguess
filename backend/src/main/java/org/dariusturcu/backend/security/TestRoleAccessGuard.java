package org.dariusturcu.backend.security;

import org.dariusturcu.backend.config.EnvironmentGuard;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.springframework.stereotype.Component;

/**
 * Any endpoint or behavior gated on the TEST role must check this before acting,
 * so a TEST-role account, however it got there, can never do anything TEST-specific
 * while the application is running against Production.
 */
@Component
public class TestRoleAccessGuard {

    private final EnvironmentGuard environmentGuard;

    public TestRoleAccessGuard(EnvironmentGuard environmentGuard) {
        this.environmentGuard = environmentGuard;
    }

    public boolean isTestAccessAllowed(User user) {
        return user != null && user.getRole() == Role.TEST && !environmentGuard.isProduction();
    }
}
