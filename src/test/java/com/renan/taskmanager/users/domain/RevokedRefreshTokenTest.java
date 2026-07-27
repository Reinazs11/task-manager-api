package com.renan.taskmanager.users.domain;

import com.renan.taskmanager.common.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RevokedRefreshToken}.
 *
 * <p>Pure unit test: the entity has no collaborators, so we just exercise its
 * factory and invariants directly. The invariants are the security boundary —
 * a revoked token with a swapped {@code revokedAt}/{@code expiresAt} pair could
 * silently skip the expiry check, so they are validated on construction.</p>
 */
class RevokedRefreshTokenTest {

    private static final UUID JTI = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UserId USER_ID = UserId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"));
    private static final Instant REVOKED_AT = Instant.parse("2026-07-27T10:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-03T10:00:00Z");  // 7 days later

    @Nested
    @DisplayName("reconstitute with valid data")
    class HappyPath {

        @Test
        @DisplayName("Should expose jti, userId, revokedAt, expiresAt unchanged")
        void shouldExposeFields() {
            RevokedRefreshToken token = RevokedRefreshToken.reconstitute(JTI, USER_ID, REVOKED_AT, EXPIRES_AT);

            assertThat(token.jti()).isEqualTo(JTI);
            assertThat(token.userId()).isEqualTo(USER_ID);
            assertThat(token.revokedAt()).isEqualTo(REVOKED_AT);
            assertThat(token.expiresAt()).isEqualTo(EXPIRES_AT);
        }
    }

    @Nested
    @DisplayName("Null-argument invariants")
    class NullArgs {

        @Test
        @DisplayName("Null jti → NullPointerException")
        void shouldRejectNullJti() {
            assertThatThrownBy(() -> RevokedRefreshToken.reconstitute(null, USER_ID, REVOKED_AT, EXPIRES_AT))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Null userId → NullPointerException")
        void shouldRejectNullUserId() {
            assertThatThrownBy(() -> RevokedRefreshToken.reconstitute(JTI, null, REVOKED_AT, EXPIRES_AT))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Null revokedAt → NullPointerException")
        void shouldRejectNullRevokedAt() {
            assertThatThrownBy(() -> RevokedRefreshToken.reconstitute(JTI, USER_ID, null, EXPIRES_AT))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Null expiresAt → NullPointerException")
        void shouldRejectNullExpiresAt() {
            assertThatThrownBy(() -> RevokedRefreshToken.reconstitute(JTI, USER_ID, REVOKED_AT, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Temporal invariant (expiresAt > revokedAt)")
    class TemporalInvariant {

        @Test
        @DisplayName("expiresAt == revokedAt → IllegalArgumentException")
        void shouldRejectEqualTimestamps() {
            assertThatThrownBy(() -> RevokedRefreshToken.reconstitute(JTI, USER_ID, REVOKED_AT, REVOKED_AT))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("expiresAt before revokedAt → IllegalArgumentException")
        void shouldRejectExpiresBeforeRevoked() {
            Instant earlier = REVOKED_AT.minusSeconds(1);

            assertThatThrownBy(() -> RevokedRefreshToken.reconstitute(JTI, USER_ID, REVOKED_AT, earlier))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Value-object equality (by jti)")
    class Equality {

        @Test
        @DisplayName("Two records with the same jti are equal; differing jti are not")
        void shouldBeEqualByJti() {
            RevokedRefreshToken a = RevokedRefreshToken.reconstitute(JTI, USER_ID, REVOKED_AT, EXPIRES_AT);
            RevokedRefreshToken b = RevokedRefreshToken.reconstitute(JTI, USER_ID, REVOKED_AT, EXPIRES_AT);
            RevokedRefreshToken c = RevokedRefreshToken.reconstitute(
                    UUID.fromString("99999999-9999-9999-9999-999999999999"),
                    USER_ID, REVOKED_AT, EXPIRES_AT);

            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
            assertThat(a).isNotEqualTo(c);
            assertThat(a).isNotEqualTo(null);
            assertThat(a).isNotEqualTo("not-a-revoked-token");
            assertThat(a).isEqualTo(a);  // self
        }
    }
}
