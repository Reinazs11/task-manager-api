package com.renan.taskmanager.users.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link RevokedRefreshTokenEntity}.
 *
 * <p>Infrastructure-layer interface — the domain never references it directly.
 * {@link RevokedRefreshTokenRepositoryImpl} adapts it to the domain
 * {@link com.renan.taskmanager.users.domain.RevokedRefreshTokenRepository}
 * port.</p>
 */
public interface RevokedRefreshTokenJpaRepository extends JpaRepository<RevokedRefreshTokenEntity, UUID> {

    /**
     * Derived query: returns true if a revoked-token row exists for this
     * {@code jti} with an {@code expires_at} still in the future. This is the
     * one-time-use / post-logout check on every {@code /auth/refresh}.
     */
    boolean existsByJtiAndExpiresAtAfter(UUID jti, Instant now);

    /**
     * Bulk purge of rows whose underlying refresh token has already expired.
     * A {@code @Modifying} JPQL delete is cheaper than loading the entities
     * and calling {@code deleteAll}; the row count lets a future scheduler log.
     */
    @Modifying
    @Query("delete from RevokedRefreshTokenEntity r where r.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
