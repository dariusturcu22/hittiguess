package org.dariusturcu.backend.security;

import org.dariusturcu.backend.exception.AdminAccessRequiredException;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminAccessGuardTest {

    private final AdminAccessGuard adminAccessGuard = new AdminAccessGuard();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void isAdminIsTrueOnlyForTheAdminRole() {
        assertThat(adminAccessGuard.isAdmin(userWithRole(Role.ADMIN))).isTrue();
        assertThat(adminAccessGuard.isAdmin(userWithRole(Role.USER))).isFalse();
        assertThat(adminAccessGuard.isAdmin(userWithRole(Role.TEST))).isFalse();
        assertThat(adminAccessGuard.isAdmin(null)).isFalse();
    }

    @Test
    void requireAdminPassesForAnAdminUser() {
        authenticateAs(userWithRole(Role.ADMIN));
        assertThatCode(adminAccessGuard::requireAdmin).doesNotThrowAnyException();
    }

    @Test
    void requireAdminRejectsANonAdminUser() {
        authenticateAs(userWithRole(Role.USER));
        assertThatThrownBy(adminAccessGuard::requireAdmin)
                .isInstanceOf(AdminAccessRequiredException.class);
    }

    private User userWithRole(Role role) {
        User user = new User();
        user.setUsername("guard-test-" + role.name().toLowerCase());
        user.setRole(role);
        return user;
    }

    private void authenticateAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(user), null, null));
    }
}
