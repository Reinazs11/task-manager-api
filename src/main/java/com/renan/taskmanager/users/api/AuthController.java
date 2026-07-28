package com.renan.taskmanager.users.api;

import com.renan.taskmanager.users.application.LoginUseCase;
import com.renan.taskmanager.users.application.LogoutUseCase;
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
    private final LogoutUseCase logoutUseCase;

    public AuthController(RegisterUserUseCase registerUserUseCase,
                          LoginUseCase loginUseCase,
                          RefreshTokenUseCase refreshTokenUseCase,
                          LogoutUseCase logoutUseCase) {
        this.registerUserUseCase = registerUserUseCase;
        this.loginUseCase = loginUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.logoutUseCase = logoutUseCase;
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
     * <p>Exchanges a valid, non-revoked refresh token for a new access + refresh
     * pair (one-time-use rotation). The supplied refresh token is revoked
     * server-side in the same transaction, so a replay of it returns 401.
     * Returns 200 on success, 401 on any token failure (wrong type, tampered,
     * expired, OR already revoked/rotated), 400 on a missing/blank field.</p>
     */
    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new token pair (one-time-use)",
            description = "Validates the supplied refresh token and returns a fresh access "
                    + "token (15 min) and a fresh refresh token (7 days). The new tokens have "
                    + "new jti claims. One-time-use rotation: the supplied refresh token is "
                    + "revoked server-side in the same transaction, so reusing it returns 401. "
                    + "A revoked token is indistinguishable from an invalid one in the response "
                    + "(anti-enumeration).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Token pair rotated",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Validation failure (missing/blank refreshToken)",
                    content = @Content(schema = @Schema(implementation = com.renan.taskmanager.common.api.ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Invalid refresh token (wrong type, tampered, expired, revoked, or not a JWT)",
                    content = @Content(schema = @Schema(implementation = com.renan.taskmanager.common.api.ErrorResponse.class)))
    })
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        TokenResponse response = refreshTokenUseCase.execute(request.refreshToken());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/auth/logout
     *
     * <p>Revokes the supplied refresh token server-side. After logout, the
     * token cannot be used to mint new pairs via {@code /auth/refresh}
     * (returns 401). Returns 204 on success (including a second logout of the
     * same token — idempotent), 401 on any token failure (wrong type, tampered,
     * expired), 400 on a missing/blank field.</p>
     *
     * <p>Access tokens are short-lived (15 min) and not revocable server-side;
     * the client should drop them locally. This endpoint revokes the long-lived
     * refresh token, which is the one that matters.</p>
     */
    @PostMapping("/logout")
    @Operation(summary = "Revoke a refresh token (logout)",
            description = "Records the supplied refresh token as revoked server-side, so it "
                    + "can no longer be exchanged at /auth/refresh. Idempotent: logging out "
                    + "the same token twice returns 204 both times. Access tokens are not "
                    + "revoked (short-lived); the client drops them locally.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204",
                    description = "Refresh token revoked (or was already revoked)",
                    content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Validation failure (missing/blank refreshToken)",
                    content = @Content(schema = @Schema(implementation = com.renan.taskmanager.common.api.ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Invalid refresh token (wrong type, tampered, expired, or not a JWT)",
                    content = @Content(schema = @Schema(implementation = com.renan.taskmanager.common.api.ErrorResponse.class)))
    })
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        logoutUseCase.execute(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
