package org.dariusturcu.backend.config;

import org.dariusturcu.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TestAccountSeederOptInTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(UserRepository.class, () -> mock(UserRepository.class))
            .withBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class))
            .withUserConfiguration(TestAccountSeeder.class);

    @Test
    void theSeederIsNotCreatedWhenTheOptInPropertyIsAbsent() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(TestAccountSeeder.class));
    }

    @Test
    void theSeederIsNotCreatedWhenTheOptInPropertyIsFalse() {
        contextRunner.withPropertyValues(TestAccountSeeder.SEED_PROPERTY + "=false")
                .run(context -> assertThat(context).doesNotHaveBean(TestAccountSeeder.class));
    }

    @Test
    void theSeederIsCreatedWhenTheOptInPropertyIsTrue() {
        contextRunner.withPropertyValues(TestAccountSeeder.SEED_PROPERTY + "=true")
                .run(context -> assertThat(context).hasSingleBean(TestAccountSeeder.class));
    }

    @Test
    void theSeederIsNotCreatedInProductionEvenWhenOptedIn() {
        contextRunner.withPropertyValues(TestAccountSeeder.SEED_PROPERTY + "=true", "spring.profiles.active=prod")
                .run(context -> assertThat(context).doesNotHaveBean(TestAccountSeeder.class));
    }
}
