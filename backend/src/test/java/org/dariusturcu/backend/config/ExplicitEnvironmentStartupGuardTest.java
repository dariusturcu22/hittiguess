package org.dariusturcu.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExplicitEnvironmentStartupGuardTest {

    @Test
    void startupFailsWhenTheEnvironmentIsRequiredAndNotSet() {
        MockEnvironment environment = new MockEnvironment();

        assertThatThrownBy(() -> new ExplicitEnvironmentStartupGuard(environment, true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startupFailsWhenTheEnvironmentIsRequiredAndBlank() {
        MockEnvironment environment = new MockEnvironment().withProperty("APP_ENV", " ");

        assertThatThrownBy(() -> new ExplicitEnvironmentStartupGuard(environment, true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startupSucceedsWhenTheEnvironmentIsRequiredAndSet() {
        MockEnvironment environment = new MockEnvironment().withProperty("APP_ENV", "prod");

        assertThatCode(() -> new ExplicitEnvironmentStartupGuard(environment, true)).doesNotThrowAnyException();
    }

    @Test
    void startupSucceedsForLocalRunsThatDoNotRequireAnExplicitEnvironment() {
        MockEnvironment environment = new MockEnvironment();

        assertThatCode(() -> new ExplicitEnvironmentStartupGuard(environment, false)).doesNotThrowAnyException();
    }
}
