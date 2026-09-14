package org.dariusturcu.backend.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnvironmentGuardTest {

    @Mock
    private Environment environment;

    @Test
    void isProductionReturnsTrueWhenProductionProfileIsActive() {
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);

        EnvironmentGuard environmentGuard = new EnvironmentGuard(environment);

        assertThat(environmentGuard.isProduction()).isTrue();
    }

    @Test
    void isProductionReturnsFalseWhenProductionProfileIsNotActive() {
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);

        EnvironmentGuard environmentGuard = new EnvironmentGuard(environment);

        assertThat(environmentGuard.isProduction()).isFalse();
    }
}
