package com.renan.taskmanager.users.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for {@code POST /api/v1/auth/register}.
 *
 * <p>Bean Validation runs at the controller boundary, before the request
 * reaches any use case. This keeps domain code free of "missing field" checks.</p>
 *
 * <p><b>Note on password rules here vs {@code Password} value object:</b>
 * The {@code @Size(min=8)} on this DTO is a coarse pre-check for fast feedback.
 * The authoritative strength validation lives in the {@code Password} domain
 * class — that's where the uppercase/lowercase/digit rules live.</p>
 *
 * @param email    user email (validated format)
 * @param password plain password (coarse length pre-check; full strength in domain)
 * @param name     optional display name
 */
@Schema(name = "RegisterRequest", description = "Payload to create a new user account")
public record RegisterRequest(
        @Schema(description = "User email", example = "alice@example.com")
        @NotBlank @Email String email,

        @Schema(description = "Plain password (min 8 chars; full strength rules enforced server-side)",
                example = "Password123")
        @NotBlank @Size(min = 8) String password,

        @Schema(description = "Optional display name", example = "Alice", nullable = true)
        String name
) {}
