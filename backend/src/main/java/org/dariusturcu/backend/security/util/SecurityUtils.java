package org.dariusturcu.backend.security.util;

import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class SecurityUtils {
    public static User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No authentication user found");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal) {
            return ((UserPrincipal) principal).getUser();
        }

        // Should never happen, the JWT filter always sets a UserPrincipal-backed
        // Authentication, this is a defensive check against a misconfiguration.
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid principal type");
    }

    public static Long getCurrentUserId() {
        return getCurrentUser().getId();
    }

    public static boolean isCurrentUser(Long userId) {
        return getCurrentUserId().equals(userId);
    }
}
