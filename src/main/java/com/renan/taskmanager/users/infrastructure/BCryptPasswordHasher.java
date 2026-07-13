package com.renan.taskmanager.users.infrastructure;

import com.renan.taskmanager.users.domain.Password;
import com.renan.taskmanager.users.domain.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt implementation of the domain {@link PasswordHasher} port.
 *
 * <p><b>Why BCrypt?</b>
 * - Adaptive: cost factor can be increased as hardware improves
 * - Salt is built into the hash (no separate column needed)
 * - Industry standard, well-audited, available in Spring Security</p>
 *
 * <p><b>Cost factor (10):</b> balances security and performance. Each increase
 * doubles compute time. 10 is the library default and a reasonable choice for
 * most apps in 2026. For high-security contexts, consider 12+.</p>
 *
 * <p>The {@link BCryptPasswordEncoder} is created once per instance (not per
 * call) to avoid repeated reflection cost.</p>
 */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder;

    public BCryptPasswordHasher() {
        this.encoder = new BCryptPasswordEncoder(10);
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
