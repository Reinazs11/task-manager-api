package com.renan.taskmanager.users.domain;

import com.renan.taskmanager.common.domain.UserId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A revoked refresh token — the server-side record that a given refresh-token
 * {@code jti} is no longer acceptable.
 *
 * <p>This is the state that closes the stateless-rotation gap documented in
 * {@code DECISIONS.md} limitation [1] and issue #11. Two paths record a row
 * here:</p>
 * <ul>
 *   <li><b>One-time-use rotation:</b> {@code RefreshTokenUseCase} records the
 *       {@code jti} of the refresh token it just exchanged, so reusing the old
 *       token is rejected on the next {@code /auth/refresh}.</li>
 *   <li><b>Explicit logout:</b> {@code LogoutUseCase} records the {@code jti}
 *       of the refresh token supplied by the client, so further refresh
 *       attempts with that token are rejected.</li>
 * </ul>
 *
 * <p><b>Entity vs Value Object:</b> this is a Value Object — its identity is
 * the {@code jti} itself, and every other field is immutable. There is no
 * lifecycle past "recorded". It is {@code final} and constructed only through
 * {@link #reconstitute} since the application layer always arrives with all
 * four fields in hand (parsed from the JWT).</p>
 *
 * <p><b>Temporal invariant:</b> {@code expiresAt} must be strictly after
 * {@code revokedAt}. A refresh token cannot be revoked after it has already
 * expired — that is a logic error, not a valid state. The CHECK constraint on
 * {@code revoked_refresh_tokens} enforces the same rule at the DB level.</p>
 */
public final class RevokedRefreshToken {

    private final UUID jti;
    private final UserId userId;
    private final Instant revokedAt;
    private final Instant expiresAt;

    private RevokedRefreshToken(UUID jti, UserId userId, Instant revokedAt, Instant expiresAt) {
        this.jti = Objects.requireNonNull(jti, "jti is required");
        this.userId = Objects.requireNonNull(userId, "userId is required");
        this.revokedAt = Objects.requireNonNull(revokedAt, "revokedAt is required");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt is required");
        if (!expiresAt.isAfter(revokedAt)) {
            throw new IllegalArgumentException(
                    "expiresAt must be strictly after revokedAt (got revokedAt="
                            + revokedAt + ", expiresAt=" + expiresAt + ")");
        }
    }

    /**
     * Reconstitutes a revoked-token record from data already in hand.
     *
     * <p>The application layer parses the JWT, extracts the {@code jti},
     * subject, and {@code exp}, and hands them to the repository. There is no
     * {@code create} factory because nothing is generated — every field comes
     * from the token being revoked.</p>
     */
    public static RevokedRefreshToken reconstitute(UUID jti, UserId userId,
                                                   Instant revokedAt, Instant expiresAt) {
        return new RevokedRefreshToken(jti, userId, revokedAt, expiresAt);
    }

    public UUID jti() {
        return jti;
    }

    public UserId userId() {
        return userId;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RevokedRefreshToken that = (RevokedRefreshToken) o;
        return Objects.equals(jti, that.jti);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jti);
    }
}
