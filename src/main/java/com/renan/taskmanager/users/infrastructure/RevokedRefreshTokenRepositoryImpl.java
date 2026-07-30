package com.renan.taskmanager.users.infrastructure;

import com.renan.taskmanager.users.domain.RevokedRefreshToken;
import com.renan.taskmanager.users.domain.RevokedRefreshTokenRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/**
 * Adapter: implements the domain {@link RevokedRefreshTokenRepository} port
 * using JPA against the {@code revoked_refresh_tokens} table.
 *
 * <p>Mirrors {@link UserRepositoryImpl}: holds the Spring Data JPA interface and
 * the {@link RevokedRefreshTokenMapper}, and is the only class in this context
 * that knows about JPA.</p>
 */
@Repository
public class RevokedRefreshTokenRepositoryImpl implements RevokedRefreshTokenRepository {

    private final RevokedRefreshTokenJpaRepository jpaRepository;

    public RevokedRefreshTokenRepositoryImpl(RevokedRefreshTokenJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public boolean revokeIfAbsent(RevokedRefreshToken token) {
        // Idempotency: revoking the same jti twice is a no-op. We check first
        // rather than relying on a caught PK violation — exception flow is
        // harder to reason about and obscures real integrity errors.
        return jpaRepository.revokeIfAbsent(
                token.jti(),
                token.userId().value(),
                token.revokedAt(),
                token.expiresAt()
        ) == 1;
    }

    @Override
    public int deleteExpired(Instant now) {
        return jpaRepository.deleteExpired(now);
    }

    @Override
    public void deleteAll() {
        jpaRepository.deleteAll();
    }
}
