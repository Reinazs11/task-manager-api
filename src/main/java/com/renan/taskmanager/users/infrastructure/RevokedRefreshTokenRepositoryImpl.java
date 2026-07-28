package com.renan.taskmanager.users.infrastructure;

import com.renan.taskmanager.users.application.RevokedRefreshTokenMapper;
import com.renan.taskmanager.users.domain.RevokedRefreshToken;
import com.renan.taskmanager.users.domain.RevokedRefreshTokenRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

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
    private final RevokedRefreshTokenMapper mapper;

    public RevokedRefreshTokenRepositoryImpl(RevokedRefreshTokenJpaRepository jpaRepository,
                                             RevokedRefreshTokenMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public void save(RevokedRefreshToken token) {
        // Idempotency: revoking the same jti twice is a no-op. We check first
        // rather than relying on a caught PK violation — exception flow is
        // harder to reason about and obscures real integrity errors.
        if (jpaRepository.existsById(token.jti())) {
            return;
        }
        jpaRepository.save(mapper.toEntity(token));
    }

    @Override
    public boolean isRevokedAndActive(UUID jti, Instant now) {
        return jpaRepository.existsByJtiAndExpiresAtAfter(jti, now);
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
