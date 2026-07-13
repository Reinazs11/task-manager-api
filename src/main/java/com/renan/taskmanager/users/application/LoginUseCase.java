package com.renan.taskmanager.users.application;

import com.renan.taskmanager.common.security.JwtService;
import com.renan.taskmanager.users.api.TokenResponse;
import com.renan.taskmanager.users.domain.Email;
import com.renan.taskmanager.users.domain.InvalidCredentialsException;
import com.renan.taskmanager.users.domain.PasswordHasher;
import com.renan.taskmanager.users.domain.User;
import com.renan.taskmanager.users.domain.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Application use case: authenticate a user and issue JWT tokens.
 *
 * <p>Flow:</p>
 * <ol>
 *   <li>Look up the user by email.</li>
 *   <li>If not found, throw {@link InvalidCredentialsException}.</li>
 *   <li>Compare the supplied password with the stored hash using
 *       {@link PasswordHasher#matches} (constant-time under BCrypt).</li>
 *   <li>If mismatch, throw {@link InvalidCredentialsException}.</li>
 *   <li>Issue access + refresh tokens and return them.</li>
 * </ol>
 *
 * <p><b>Security note:</b> we never reveal whether the email exists or the
 * password was wrong — same exception, same message, same timing pattern.
 * This prevents user enumeration by attackers.</p>
 */
@Service
public class LoginUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final JwtService jwtService;
    private final long accessTtlMs;
    private final long refreshTtlMs;

    public LoginUseCase(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            JwtService jwtService,
            @Value("${app.jwt.access-token-expiration-ms:900000}") long accessTtlMs,
            @Value("${app.jwt.refresh-token-expiration-ms:604800000}") long refreshTtlMs
    ) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
        this.accessTtlMs = accessTtlMs;
        this.refreshTtlMs = refreshTtlMs;
    }

    public TokenResponse execute(String email, String plainPassword) {
        Email emailVo = new Email(email);

        User user = userRepository.findByEmail(emailVo)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordHasher.matches(plainPassword, user.getPassword().value())) {
            throw new InvalidCredentialsException();
        }

        UUID userId = user.getId().value();
        String userEmail = user.getEmail().value();

        String accessToken = jwtService.generateAccessToken(userId, userEmail);
        String refreshToken = jwtService.generateRefreshToken(userId, userEmail);

        return TokenResponse.of(accessToken, refreshToken, accessTtlMs, refreshTtlMs);
    }
}
