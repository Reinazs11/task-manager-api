package com.renan.taskmanager.users.application;

import com.renan.taskmanager.common.audit.application.AuditEventRecorder;
import com.renan.taskmanager.common.domain.UserId;
import com.renan.taskmanager.common.security.JwtService;
import com.renan.taskmanager.users.api.TokenResponse;
import com.renan.taskmanager.users.domain.Email;
import com.renan.taskmanager.users.domain.InvalidCredentialsException;
import com.renan.taskmanager.users.domain.PasswordHasher;
import com.renan.taskmanager.users.domain.User;
import com.renan.taskmanager.users.domain.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
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
 *
 * <p><b>Metrics note:</b> every attempt increments {@code auth.login.attempts}
 * with a <b>binary</b> tag {@code result=success|failure}. The failure tag is
 * identical for "unknown email" and "wrong password" — by design, so the
 * metric cannot be used to enumerate valid emails (it would leak exactly what
 * the anti-enumeration above hides). See DECISIONS.md #6 and #19.</p>
 */
@Service
public class LoginUseCase {

    static final String LOGIN_ATTEMPTS_METRIC = "auth.login.attempts";
    static final String RESULT_TAG = "result";
    static final String RESULT_SUCCESS = "success";
    static final String RESULT_FAILURE = "failure";

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final JwtService jwtService;
    private final MeterRegistry meterRegistry;
    private final AuditEventRecorder auditRecorder;
    private final long accessTtlMs;
    private final long refreshTtlMs;

    public LoginUseCase(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            JwtService jwtService,
            MeterRegistry meterRegistry,
            AuditEventRecorder auditRecorder,
            @Value("${app.jwt.access-token-expiration-ms:900000}") long accessTtlMs,
            @Value("${app.jwt.refresh-token-expiration-ms:604800000}") long refreshTtlMs
    ) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
        this.meterRegistry = meterRegistry;
        this.auditRecorder = auditRecorder;
        this.accessTtlMs = accessTtlMs;
        this.refreshTtlMs = refreshTtlMs;
    }

    public TokenResponse execute(String email, String plainPassword) {
        Email emailVo = new Email(email);

        User user = userRepository.findByEmail(emailVo)
                .orElseThrow(this::recordFailureAndThrow);

        if (!passwordHasher.matches(plainPassword, user.getPassword().value())) {
            throw recordFailureAndThrow();
        }

        UUID userId = user.getId().value();
        String userEmail = user.getEmail().value();

        String accessToken = jwtService.generateAccessToken(userId, userEmail);
        String refreshToken = jwtService.generateRefreshToken(userId, userEmail);

        recordSuccess(user.getId());
        return TokenResponse.of(accessToken, refreshToken, accessTtlMs, refreshTtlMs);
    }

    private void recordSuccess(UserId actor) {
        meterRegistry.counter(LOGIN_ATTEMPTS_METRIC, RESULT_TAG, RESULT_SUCCESS).increment();
        auditRecorder.recordLoginSucceeded(actor);
    }

    private InvalidCredentialsException recordFailureAndThrow() {
        meterRegistry.counter(LOGIN_ATTEMPTS_METRIC, RESULT_TAG, RESULT_FAILURE).increment();
        // Audit BEFORE throwing: recordLoginFailed runs in its own tx
        // (REQUIRES_NEW) so the row commits before the exception propagates —
        // a failed login is the most valuable forensic signal and must survive.
        // Deliberately passes NO actor (anti-enumeration: even when we know the
        // userId from a wrong-password attempt, we do not record it).
        auditRecorder.recordLoginFailed();
        return new InvalidCredentialsException();
    }
}
