package com.renan.taskmanager.users.api;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/auth/logout}.
 *
 * @param refreshToken  the refresh JWT to revoke server-side
 */
public record LogoutRequest(
        @NotBlank(message = "refreshToken is required")
        String refreshToken
) {
}
