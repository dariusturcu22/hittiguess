package org.dariusturcu.backend.config;

import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionTestAccountStartupGuardTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void startupFailsWhenATestRoleRowExistsInProduction() {
        when(userRepository.existsUserByRole(Role.TEST)).thenReturn(true);
        ProductionTestAccountStartupGuard startupGuard = new ProductionTestAccountStartupGuard(userRepository);

        assertThatThrownBy(() -> startupGuard.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startupSucceedsWhenNoTestRoleRowExists() {
        when(userRepository.existsUserByRole(Role.TEST)).thenReturn(false);
        ProductionTestAccountStartupGuard startupGuard = new ProductionTestAccountStartupGuard(userRepository);

        assertThatCode(() -> startupGuard.run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }
}
