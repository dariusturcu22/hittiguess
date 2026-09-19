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
        TestAccountSeeder.TestAccount testAccount = TestAccountSeeder.TEST_ACCOUNT_1;
        when(passwordEncoder.encode(ArgumentMatchers.anyString())).thenReturn("hashed-password");
        when(userRepository.existsUserByEmail(ArgumentMatchers.anyString())).thenReturn(true);

        when(userRepository.existsUserByEmail(testAccount.email())).thenReturn(false);
        testAccountSeeder.seedTestAccounts();

        ArgumentCaptor<User> savedUserCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(savedUserCaptor.capture());

        User savedUser = savedUserCaptor.getValue();
        assertThat(savedUser.getUsername()).isEqualTo(testAccount.username());
        assertThat(savedUser.getEmail()).isEqualTo(testAccount.email());
        assertThat(savedUser.getRole()).isEqualTo(Role.TEST);
        assertThat(savedUser.isEmailVerified()).isTrue();

        when(userRepository.existsUserByEmail(testAccount.email())).thenReturn(true);
        testAccountSeeder.seedTestAccounts();

        verify(userRepository, times(1)).save(ArgumentMatchers.any(User.class));
    }

    @Test
    void seedsAllThreeReusableTestAccounts() {
        TestAccountSeeder testAccountSeeder = new TestAccountSeeder(userRepository, passwordEncoder);
        when(passwordEncoder.encode(ArgumentMatchers.anyString())).thenReturn("hashed-password");
        when(userRepository.existsUserByEmail(ArgumentMatchers.anyString())).thenReturn(false);

        testAccountSeeder.seedTestAccounts();

        ArgumentCaptor<User> savedUserCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(3)).save(savedUserCaptor.capture());

        assertThat(savedUserCaptor.getAllValues())
                .extracting(User::getEmail)
                .containsExactlyInAnyOrder(
                        TestAccountSeeder.TEST_ACCOUNT_1.email(),
                        TestAccountSeeder.TEST_ACCOUNT_2.email(),
                        TestAccountSeeder.TEST_ACCOUNT_3.email());
    }
}
