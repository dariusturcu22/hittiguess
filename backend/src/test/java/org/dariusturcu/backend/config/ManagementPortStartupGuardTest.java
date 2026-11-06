package org.dariusturcu.backend.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManagementPortStartupGuardTest {

    private static final int APPLICATION_PORT = 8080;
    private static final String DIFFERENT_MANAGEMENT_PORT = "8081";
    private static final String RANDOM_MANAGEMENT_PORT = "0";
    private static final String DISABLED_MANAGEMENT_PORT = "-1";

    @Test
    void startupFailsWhenTheManagementPortIsTheApplicationPort() {
        assertThatThrownBy(() -> new ManagementPortStartupGuard(APPLICATION_PORT, String.valueOf(APPLICATION_PORT)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startupFailsWhenTheManagementPortIsNotANumber() {
        assertThatThrownBy(() -> new ManagementPortStartupGuard(APPLICATION_PORT, "eighty"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startupSucceedsWhenThePortsDiffer() {
        assertThatCode(() -> new ManagementPortStartupGuard(APPLICATION_PORT, DIFFERENT_MANAGEMENT_PORT))
                .doesNotThrowAnyException();
    }

    @Test
    void startupSucceedsWhenManagementUsesARandomOrDisabledPort() {
        assertThatCode(() -> new ManagementPortStartupGuard(0, RANDOM_MANAGEMENT_PORT)).doesNotThrowAnyException();
        assertThatCode(() -> new ManagementPortStartupGuard(APPLICATION_PORT, DISABLED_MANAGEMENT_PORT))
                .doesNotThrowAnyException();
    }

    @Test
    void startupSucceedsWhenTheManagementPortIsUnset() {
        assertThatCode(() -> new ManagementPortStartupGuard(APPLICATION_PORT, ""))
                .doesNotThrowAnyException();
    }
}
