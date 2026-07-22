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
 * <p><b>Cost 12:</b> aligns with OWASP 2026 recommendations. Each cost point
 * doubles compute time — 12 is the floor for new deployments, with headroom
 * to raise as hardware improves.</p>
 */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final PasswordEncoder encoder;

    public BCryptPasswordHasher(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    public String hash(Password plainPassword) {
        return encoder.encode(plainPassword.value());
    }

    @Override
    public boolean matches(String plainAttempt, String hash) {
        if (hash == null || hash.isBlank()) {
            return false;
        }
        return encoder.matches(plainAttempt, hash);
    }
}
