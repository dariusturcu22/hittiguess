package org.dariusturcu.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile(EnvironmentGuard.PRODUCTION_PROFILE)
public class ProductionEmailStartupGuard implements ApplicationRunner {

    private static final String MISSING_EMAIL_KEY_MESSAGE = "RESEND_API_KEY must be configured in Production.";

    private final String apiKey;

    public ProductionEmailStartupGuard(@Value("${resend.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException(MISSING_EMAIL_KEY_MESSAGE);
        }
    }
}
