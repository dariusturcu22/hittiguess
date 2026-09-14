package org.dariusturcu.backend.config;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Reads the active Spring profile to determine whether the application is running
 * against Production. Every guardrail that must never activate in Production goes
 * through this class rather than checking a profile name directly, so there is one
 * place that defines what "Production" means.
 */
@Component
public class EnvironmentGuard {

    public static final String PRODUCTION_PROFILE = "prod";

    private final Environment environment;

    public EnvironmentGuard(Environment environment) {
        this.environment = environment;
    }

    public boolean isProduction() {
        return environment.acceptsProfiles(Profiles.of(PRODUCTION_PROFILE));
    }
}
