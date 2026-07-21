package com.renan.taskmanager.users.api;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/auth/refresh}.
 *
 * @param refreshToken  the refresh JWT issued at login (or at the previous refresh)
 */
public record RefreshRequest(
        @NotBlank(message = "refreshToken is required")
        String refreshToken
) {
}
