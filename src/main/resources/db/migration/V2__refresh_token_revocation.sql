-- Refresh-token revocation store.
--
-- Closes the limitation that /auth/refresh was stateless: a refresh token stayed
-- valid until its own expiry even after being exchanged for a new pair. With this
-- table, RefreshTokenUseCase records the jti of every refresh token it rotates,
-- and LogoutUseCase records the jti of an explicitly revoked refresh token. The
-- store is then consulted on every refresh to enforce one-time-use rotation and
-- logout. See DECISIONS.md #17.
--
-- Conventions (mirrors V1__init_schema.sql):
--   * jti stored as PostgreSQL uuid — JwtService emits jti via
--     UUID.randomUUID().toString(), so it parses cleanly into a uuid column.
--     PK on jti makes the "is this token revoked?" check a primary-key lookup
--     and makes double-revocation (logout then rotate the same token) a no-op
--     via ON CONFLICT, not a constraint violation.
--   * user_id carries an FK to users(id) ON DELETE CASCADE so deleting a user
--     also drops their revoked tokens (no orphan growth, consistent with the
--     cascade style in V1).
--   * revoked_at/expires_at are timestamptz (java.time.Instant round-trips
--     without a timezone surprise).
--   * expires_at is the underlying refresh token's own expiration. After it
--     passes, the row is dead — the token would already be rejected by the
--     JwtService parser. deleteExpired(now) purges those rows in bulk; until a
--     scheduler is added (limitation [5]), dead rows accumulate harmlessly.
--   * CHECK (expires_at > revoked_at) catches a programming error: a token
--     cannot be revoked after it has already expired.

CREATE TABLE revoked_refresh_tokens (
    jti         uuid         NOT NULL,
    user_id     uuid         NOT NULL,
    revoked_at  timestamptz NOT NULL,
    expires_at  timestamptz NOT NULL,
    CONSTRAINT pk_revoked_refresh_tokens PRIMARY KEY (jti),
    CONSTRAINT fk_revoked_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_revoked_refresh_tokens_expires
        CHECK (expires_at > revoked_at)
);

-- Index by user_id so "revoke all of this user's tokens" (a future /auth/logout-all
-- endpoint) is an indexed lookup, not a sequential scan.
CREATE INDEX idx_revoked_refresh_tokens_user ON revoked_refresh_tokens (user_id);
