package com.renan.taskmanager.users.api;

import com.renan.taskmanager.users.application.LoginUseCase;
import com.renan.taskmanager.users.application.RegisterUserUseCase;
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
public class AuthController {

    private final RegisterUserUseCase registerUserUseCase;
    private final LoginUseCase loginUseCase;

    public AuthController(RegisterUserUseCase registerUserUseCase, LoginUseCase loginUseCase) {
        this.registerUserUseCase = registerUserUseCase;
        this.loginUseCase = loginUseCase;
    }

    /**
     * POST /api/v1/auth/register
     *
     * <p>Creates a new user account. Returns 201 Created on success,
     * 400 on validation failure, 409 on duplicate email.</p>
     */
    @PostMapping("/register")
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
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse response = loginUseCase.execute(request.email(), request.password());
        return ResponseEntity.ok(response);
    }
}
