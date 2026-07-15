package com.renan.taskmanager.common.security;

import com.renan.taskmanager.users.domain.UserId;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Helper to extract the authenticated user's identity from the security context.
 *
 * <p>The {@link JwtAuthenticationFilter} stores the user id (as a String UUID)
 * as the principal. This class converts it into a {@link UserId} value object
 * for the application layer to consume.</p>
 */
public final class AuthenticatedUser {

    private AuthenticatedUser() {}

    /**
     * Returns the {@link UserId} of the currently authenticated user.
     *
     * @throws IllegalStateException if no authenticated user is present
     *                               (should not happen on protected routes)
     */
    public static UserId id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new IllegalStateException("No authenticated user in security context");
        }
        UUID uuid = UUID.fromString((String) auth.getPrincipal());
        return UserId.of(uuid);
    }
}
