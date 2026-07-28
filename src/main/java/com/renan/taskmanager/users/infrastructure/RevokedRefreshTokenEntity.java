package com.renan.taskmanager.users.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for {@link com.renan.taskmanager.users.domain.RevokedRefreshToken}
 * persistence.
 *
 * <p><b>Why a separate entity and not @Entity on the domain class?</b>
 * Same rationale as {@link UserEntity}: keep JPA annotations off the domain so
 * it stays pure and framework-agnostic. {@link RevokedRefreshTokenMapper}
 * translates between the two.</p>
 *
 * <p><b>Why no @GeneratedValue?</b>
 * The {@code jti} is the JWT ID emitted by {@code JwtService} when the refresh
 * token was created — the value is fixed the moment the token is signed. The
 * database does not assign it; we persist the {@code jti} verbatim as the
 * primary key. This makes the "is this token revoked?" check a PK lookup.</p>
 *
 * <p><b>Why is there no ORM relationship to UserEntity?</b>
 * A bare {@code user_id} FK column matches {@code V2__refresh_token_revocation.sql}
 * and keeps the row lightweight. The FK is enforced at the DB level (with
 * {@code ON DELETE CASCADE}); pulling a {@code @ManyToOne} here would add lazy
 * loading we never need.</p>
 */
@Entity
@Table(name = "revoked_refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevokedRefreshTokenEntity {

    @Id
    @Column(name = "jti", updatable = false, nullable = false)
    private UUID jti;

    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @Column(name = "revoked_at", updatable = false, nullable = false)
    private Instant revokedAt;

    @Column(name = "expires_at", updatable = false, nullable = false)
    private Instant expiresAt;
}
