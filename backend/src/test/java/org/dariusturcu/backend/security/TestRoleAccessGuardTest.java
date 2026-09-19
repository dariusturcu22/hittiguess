package org.dariusturcu.backend.security;

import org.dariusturcu.backend.config.EnvironmentGuard;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestRoleAccessGuardTest {

    @Mock
    private EnvironmentGuard environmentGuard;

    @Test
    void testRoleAccessIsDeniedInProductionEvenWhenTheAccountHasTheTestRole() {
        when(environmentGuard.isProduction()).thenReturn(true);
        TestRoleAccessGuard testRoleAccessGuard = new TestRoleAccessGuard(environmentGuard);

        User testRoleUser = new User();
        testRoleUser.setRole(Role.TEST);

        assertThat(testRoleAccessGuard.isTestAccessAllowed(testRoleUser)).isFalse();
    }

    @Test
    void testRoleAccessIsAllowedOutsideProductionForATestRoleAccount() {
        when(environmentGuard.isProduction()).thenReturn(false);
        TestRoleAccessGuard testRoleAccessGuard = new TestRoleAccessGuard(environmentGuard);

        User testRoleUser = new User();
        testRoleUser.setRole(Role.TEST);

        assertThat(testRoleAccessGuard.isTestAccessAllowed(testRoleUser)).isTrue();
    }

    @Test
    void testRoleAccessIsDeniedForANonTestRoleAccountEvenOutsideProduction() {
        TestRoleAccessGuard testRoleAccessGuard = new TestRoleAccessGuard(environmentGuard);

        User regularUser = new User();
        regularUser.setRole(Role.USER);

        assertThat(testRoleAccessGuard.isTestAccessAllowed(regularUser)).isFalse();
    }
}
