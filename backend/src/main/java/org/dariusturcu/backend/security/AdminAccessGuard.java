package org.dariusturcu.backend.security;

import org.dariusturcu.backend.exception.AdminAccessRequiredException;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.security.util.SecurityUtils;
import org.springframework.stereotype.Component;

/**
 * Gates every admin-only endpoint on the ADMIN role. Any admin-scoped operation
 * calls requireAdmin before acting, so a non-admin account can never reach
 * catalog-seeding or backlog-status behavior.
 */
@Component
public class AdminAccessGuard {

    public boolean isAdmin(User user) {
        return user != null && user.getRole() == Role.ADMIN;
    }

    public void requireAdmin() {
        User currentUser = SecurityUtils.getCurrentUser();
        if (!isAdmin(currentUser)) {
            throw new AdminAccessRequiredException();
        }
    }
}
