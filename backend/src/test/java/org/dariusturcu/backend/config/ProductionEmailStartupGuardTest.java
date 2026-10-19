package org.dariusturcu.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionEmailStartupGuardTest {

    private static final String CONFIGURED_API_KEY = "re_configured_key";
    private static final String EMPTY_API_KEY = "";

    @Test
    void startupFailsWhenTheEmailApiKeyIsEmpty() {
        ProductionEmailStartupGuard startupGuard = new ProductionEmailStartupGuard(EMPTY_API_KEY);

        assertThatThrownBy(() -> startupGuard.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startupSucceedsWhenTheEmailApiKeyIsConfigured() {
        ProductionEmailStartupGuard startupGuard = new ProductionEmailStartupGuard(CONFIGURED_API_KEY);

        assertThatCode(() -> startupGuard.run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }
}
