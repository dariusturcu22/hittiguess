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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestAccountSeederTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private static User existingAccount(TestAccountSeeder.TestAccount testAccount, Role role) {
        User user = new User();
        user.setEmail(testAccount.email());
        user.setRole(role);
        return user;
    }

    @Test
    void seedingTwiceProducesTheSameReusableCredentialsWithoutCreatingADuplicate() {
        TestAccountSeeder testAccountSeeder = new TestAccountSeeder(userRepository, passwordEncoder);
        TestAccountSeeder.TestAccount testAccount = TestAccountSeeder.TEST_ACCOUNT_2;
        when(passwordEncoder.encode(ArgumentMatchers.anyString())).thenReturn("hashed-password");
        when(userRepository.findUserByEmail(TestAccountSeeder.TEST_ACCOUNT_1.email()))
                .thenReturn(Optional.of(existingAccount(TestAccountSeeder.TEST_ACCOUNT_1, Role.ADMIN)));
        when(userRepository.findUserByEmail(TestAccountSeeder.TEST_ACCOUNT_3.email()))
                .thenReturn(Optional.of(existingAccount(TestAccountSeeder.TEST_ACCOUNT_3, Role.TEST)));
        when(userRepository.findUserByEmail(testAccount.email())).thenReturn(Optional.empty());

        testAccountSeeder.seedTestAccounts();

        ArgumentCaptor<User> savedUserCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(savedUserCaptor.capture());
        User savedUser = savedUserCaptor.getValue();
        assertThat(savedUser.getUsername()).isEqualTo(testAccount.username());
        assertThat(savedUser.getEmail()).isEqualTo(testAccount.email());
        assertThat(savedUser.getRole()).isEqualTo(Role.TEST);
        assertThat(savedUser.isEmailVerified()).isTrue();

        when(userRepository.findUserByEmail(testAccount.email())).thenReturn(Optional.of(savedUser));
        testAccountSeeder.seedTestAccounts();

        verify(userRepository, times(1)).save(ArgumentMatchers.any(User.class));
    }

    @Test
    void seedsAllThreeReusableTestAccountsWithTheFirstAsAnAdmin() {
        TestAccountSeeder testAccountSeeder = new TestAccountSeeder(userRepository, passwordEncoder);
        when(passwordEncoder.encode(ArgumentMatchers.anyString())).thenReturn("hashed-password");
        when(userRepository.findUserByEmail(ArgumentMatchers.anyString())).thenReturn(Optional.empty());

        testAccountSeeder.seedTestAccounts();

        ArgumentCaptor<User> savedUserCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(3)).save(savedUserCaptor.capture());
        assertThat(savedUserCaptor.getAllValues())
                .extracting(User::getEmail, User::getRole)
                .containsExactlyInAnyOrder(
                        tuple(TestAccountSeeder.TEST_ACCOUNT_1.email(), Role.ADMIN),
                        tuple(TestAccountSeeder.TEST_ACCOUNT_2.email(), Role.TEST),
                        tuple(TestAccountSeeder.TEST_ACCOUNT_3.email(), Role.TEST));
    }

    @Test
    void anAccountSeededBeforeTheAdminRuleIsPromotedWithoutTouchingItsPassword() {
        TestAccountSeeder testAccountSeeder = new TestAccountSeeder(userRepository, passwordEncoder);
        User earlierFirstAccount = existingAccount(TestAccountSeeder.TEST_ACCOUNT_1, Role.TEST);
        when(userRepository.findUserByEmail(ArgumentMatchers.anyString())).thenAnswer(invocation ->
                TestAccountSeeder.TEST_ACCOUNT_1.email().equals(invocation.getArgument(0))
                        ? Optional.of(earlierFirstAccount)
                        : Optional.of(existingAccount(TestAccountSeeder.TEST_ACCOUNT_2, Role.TEST)));

        testAccountSeeder.seedTestAccounts();

        assertThat(earlierFirstAccount.getRole()).isEqualTo(Role.ADMIN);
        verify(userRepository).save(earlierFirstAccount);
        verify(passwordEncoder, never()).encode(ArgumentMatchers.anyString());
    }
}
