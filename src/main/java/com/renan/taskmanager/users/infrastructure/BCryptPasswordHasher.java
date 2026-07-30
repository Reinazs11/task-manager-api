package com.renan.taskmanager.users.infrastructure;

import com.renan.taskmanager.users.domain.Password;
import com.renan.taskmanager.users.domain.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt implementation of the domain {@link PasswordHasher} port.
 *
 * <p><b>Why BCrypt?</b>
 * - Adaptive: cost factor can be increased as hardware improves
 * - Salt is built into the hash (no separate column needed)
 * - Industry standard, well-audited, available in Spring Security</p>
 *
 * <p><b>Single source of truth for the cost factor:</b>
 * The {@link PasswordEncoder} bean is defined exactly once in
 * {@code SecurityConfig} and injected here. Previously this class created its
 * own {@code BCryptPasswordEncoder} with a hardcoded cost, which meant two
 * encoders could drift apart if one was changed and the other was not. Now
 * the cost lives in one place ({@code SecurityConfig.passwordEncoder}).</p>
 *
 * <p><b>Cost 12:</b> retained for project compatibility. OWASP prefers
 * Argon2id for new systems; BCrypt remains an accepted fallback when its
 * 72-byte input limit is enforced.</p>
 */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private static final String DUMMY_PASSWORD = "dummy-password-never-used-for-login";

    private final PasswordEncoder encoder;
    private final String dummyHash;

    public BCryptPasswordHasher(PasswordEncoder encoder) {
        this.encoder = encoder;
        this.dummyHash = encoder.encode(DUMMY_PASSWORD);
    }

    @Override
    public String hash(Password plainPassword) {
        return encoder.encode(plainPassword.value());
    }

    @Override
    public boolean matches(String plainAttempt, String hash) {
        String hashToCheck = hash == null || hash.isBlank() ? dummyHash : hash;
        return encoder.matches(plainAttempt, hashToCheck);
    }
}
