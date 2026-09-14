package org.dariusturcu.backend.config;

import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestAccountSeederTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void seedingTwiceProducesTheSameReusableCredentialsWithoutCreatingADuplicate() {
        TestAccountSeeder testAccountSeeder = new TestAccountSeeder(userRepository, passwordEncoder);
        when(passwordEncoder.encode(TestAccountSeeder.TEST_ACCOUNT_PASSWORD)).thenReturn("hashed-password");

        when(userRepository.existsUserByEmail(TestAccountSeeder.TEST_ACCOUNT_EMAIL)).thenReturn(false);
        testAccountSeeder.seedTestAccount();

        ArgumentCaptor<User> savedUserCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(savedUserCaptor.capture());

        User savedUser = savedUserCaptor.getValue();
        assertThat(savedUser.getUsername()).isEqualTo(TestAccountSeeder.TEST_ACCOUNT_USERNAME);
        assertThat(savedUser.getEmail()).isEqualTo(TestAccountSeeder.TEST_ACCOUNT_EMAIL);
        assertThat(savedUser.getRole()).isEqualTo(Role.TEST);

        when(userRepository.existsUserByEmail(TestAccountSeeder.TEST_ACCOUNT_EMAIL)).thenReturn(true);
        testAccountSeeder.seedTestAccount();

        verify(userRepository, times(1)).save(ArgumentMatchers.any(User.class));
    }
}
