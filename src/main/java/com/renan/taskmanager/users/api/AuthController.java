package com.renan.taskmanager.users.api;

import com.renan.taskmanager.users.application.LoginUseCase;
import com.renan.taskmanager.users.application.RefreshTokenUseCase;
import com.renan.taskmanager.users.application.RegisterUserUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints.
 *
 * <p>Routes under {@code /api/v1/auth/**} are public (see {@code SecurityConfig}):
 * no JWT required. After registering or logging in, clients receive tokens to
 * access protected routes.</p>
 *
 * <p><b>Why thin controllers?</b>
 * Controllers translate HTTP ↔ DTOs. All business logic lives in the use cases.
 * This keeps controllers easy to test and free of incidental complexity.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "User registration and login. Public endpoints — no JWT required.")
public class AuthController {

    private final RegisterUserUseCase registerUserUseCase;
    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;

    public AuthController(RegisterUserUseCase registerUserUseCase,
                          LoginUseCase loginUseCase,
                          RefreshTokenUseCase refreshTokenUseCase) {
        this.registerUserUseCase = registerUserUseCase;
        this.loginUseCase = loginUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
    }

    /**
     * POST /api/v1/auth/register
     *
     * <p>Creates a new user account. Returns 201 Created on success,
     * 400 on validation failure, 409 on duplicate email.</p>
     */
    @PostMapping("/register")
    @Operation(summary = "Register a new user",
            description = "Creates a user account. Email must be unique. Password strength rules "
                    + "are enforced server-side beyond the coarse @Size pre-check.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "User created",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Validation failure (blank/invalid email, short password)",
                    content = @Content(schema = @Schema(implementation = com.renan.taskmanager.common.api.ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Email already registered",
                    content = @Content(schema = @Schema(implementation = com.renan.taskmanager.common.api.ErrorResponse.class)))
    })
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = registerUserUseCase.execute(
                request.email(),
                request.password(),
                request.name()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/v1/auth/login
     *
     * <p>Authenticates credentials and returns a token pair. Returns 200 on
     * success, 401 on invalid credentials, 400 on validation failure.</p>
     */
    @PostMapping("/login")
    @Operation(summary = "Log in and obtain a JWT token pair",
            description = "Validates credentials and returns an access token (15 min) and a "
                    + "refresh token (7 days). On failure the response is identical whether "
                    + "the email is unknown or the password is wrong, to prevent user enumeration.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Authenticated",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Validation failure",
                    content = @Content(schema = @Schema(implementation = com.renan.taskmanager.common.api.ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Invalid credentials",
                    content = @Content(schema = @Schema(implementation = com.renan.taskmanager.common.api.ErrorResponse.class)))
    })
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse response = loginUseCase.execute(request.email(), request.password());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/auth/refresh
     *
     * <p>Exchanges a valid refresh token for a new access + refresh pair
     * (token rotation). Returns 200 on success, 401 on any token failure
     * (wrong type, tampered, expired), 400 on a missing/blank field.</p>
     */
    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new token pair",
            description = "Validates the supplied refresh token and returns a fresh access "
                    + "token (15 min) and a fresh refresh token (7 days). The new tokens have "
                    + "new jti claims, so callers can detect rotation. Note: stateless design — "
                    + "the old refresh token remains valid until its own expiry; one-time-use "
                    + "refresh would require a server-side token store.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Token pair rotated",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Validation failure (missing/blank refreshToken)",
                    content = @Content(schema = @Schema(implementation = com.renan.taskmanager.common.api.ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Invalid refresh token (wrong type, tampered, expired, or not a JWT)",
                    content = @Content(schema = @Schema(implementation = com.renan.taskmanager.common.api.ErrorResponse.class)))
    })
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        TokenResponse response = refreshTokenUseCase.execute(request.refreshToken());
        return ResponseEntity.ok(response);
    }
}
