package com.renan.taskmanager.users.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for {@code POST /api/v1/auth/login}.
 *
 * <p>{@code @Email} + {@code @NotBlank} enforce basic shape. The actual
 * credential verification happens in {@code LoginUseCase}.</p>
 *
 * @param email    registered user email
 * @param password plain password attempt
 */
@Schema(name = "LoginRequest", description = "Credentials to obtain a JWT token pair")
public record LoginRequest(
        @Schema(description = "Registered user email", example = "alice@example.com")
        @NotBlank @Email String email,

        @Schema(description = "Plain password attempt", example = "Password123")
        @NotBlank String password
) {}
