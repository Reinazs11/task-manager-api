package com.renan.taskmanager.users.api;

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
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {}
