package com.renan.taskmanager.users.domain;

import java.time.Instant;

/**
 * Port (DDD): contract for the refresh-token revocation store.
 *
 * <p>Defined in the domain layer, implemented by infrastructure (JPA against
 * the {@code revoked_refresh_tokens} table). The domain stays pure (no JPA,
 * no Spring Data) and is mockable in unit tests.</p>
 *
 * <p><b>Why this lives in the {@code users} context (and not {@code common})?</b>
 * Token revocation is an auth/user concern that follows {@code RefreshTokenUseCase}
 * and {@code LogoutUseCase} — both in {@code users.application}. Keeping it here
 * preserves context isolation: {@code tasks} never references it.</p>
 *
 * <p><b>Why does the insert return a boolean?</b> A revoked-token record is
 * immutable after insert — there is nothing to "refresh" on the caller side.
 * The result tells the caller whether it atomically acquired this token.</p>
 */
public interface RevokedRefreshTokenRepository {

    /**
     * Atomically records a refresh-token revocation. Revoking the same
     * {@code jti} twice MUST NOT throw — the second call is a no-op (the row
     * already exists). This lets {@code LogoutUseCase} handle "already revoked"
     * transparently instead of branching on it.
     *
     * @return true only when this call inserted the revocation
     */
    boolean revokeIfAbsent(RevokedRefreshToken token);

    /**
     * Returns {@code true} if a refresh token with the given {@code jti} has
     * been revoked AND its own expiry is still in the future (so it would
     * otherwise still be valid). Once the token's natural expiry passes, the
     * row is dead either way — this method returns {@code false} and
     * {@link #deleteExpired(Instant)} can purge it.
     *
     * <p>Used by {@code RefreshTokenUseCase} to enforce one-time-use rotation
     * and post-logout rejection.</p>
     */
    /**
     * Bulk-purges rows whose {@code expires_at} has passed. Returns the number
     * of rows removed so a future scheduler (limitation [5]) can log it.
     *
     * <p>Hook only today — no caller invokes it in production yet. Kept on the
     * port so the future scheduler does not have to reach past the domain.</p>
     */
    int deleteExpired(Instant now);

    /**
     * Removes all revoked-token records. Used by integration tests to guarantee
     * isolation between runs.
     *
     * <p><b>Note:</b> test-only, mirroring {@link UserRepository#deleteAll()}.
     * Exposed on the port so the domain contract is the single entry point for
     * persistence, even in tests.</p>
     */
    void deleteAll();
}
