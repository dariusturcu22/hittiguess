package org.dariusturcu.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to start when app.env.required is true (the container image sets it) and APP_ENV
 * was not supplied. app.env defaults to dev for local runs; without this check a deployment
 * that forgot APP_ENV would silently run with every Production guard switched off.
 */
@Component
public class ExplicitEnvironmentStartupGuard {

    static final String ENVIRONMENT_VARIABLE = "APP_ENV";

    public ExplicitEnvironmentStartupGuard(
            Environment environment,
            @Value("${app.env.required:false}") boolean environmentRequired) {
        String explicitEnvironment = environment.getProperty(ENVIRONMENT_VARIABLE);
        if (environmentRequired && (explicitEnvironment == null || explicitEnvironment.isBlank())) {
            throw new IllegalStateException(
                    ENVIRONMENT_VARIABLE + " must be set explicitly (for example prod) when running the container image."
            );
        }
    }
}
