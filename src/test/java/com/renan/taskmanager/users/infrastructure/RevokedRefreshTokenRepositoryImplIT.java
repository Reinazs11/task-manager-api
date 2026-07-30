package com.renan.taskmanager.users.infrastructure;

import com.renan.taskmanager.common.TestContainersConfig;
import com.renan.taskmanager.common.domain.UserId;
import com.renan.taskmanager.users.application.RevokedRefreshTokenMapperImpl;
import com.renan.taskmanager.users.domain.Email;
import com.renan.taskmanager.users.domain.Password;
import com.renan.taskmanager.users.domain.RevokedRefreshToken;
import com.renan.taskmanager.users.domain.RevokedRefreshTokenRepository;
import com.renan.taskmanager.users.domain.User;
import com.renan.taskmanager.users.domain.UserRepository;
import com.renan.taskmanager.users.application.UserMapperImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for {@link RevokedRefreshTokenRepositoryImpl} against a real
 * PostgreSQL 16 via Testcontainers.
 *
 * <p>Same slice-style setup as {@link UserRepositoryImplIT}: {@code @DataJpaTest}
 * with the embedded DB replaced by our Testcontainers PostgreSQL, and the
 * adapter + mapper + container explicitly {@code @Import}ed (the slice does
 * not pick them up on its own).</p>
 *
 * <p><b>Why do we seed a {@code users} row before each revoked-token row?</b>
 * The {@code user_id} FK on {@code revoked_refresh_tokens} references
 * {@code users(id)}. A real revocation always comes from an existing user;
 * the IT mirrors that, so the FK is satisfied and we exercise the constraint
 * the way production does.</p>
 */
@DataJpaTest
@Import({
        RevokedRefreshTokenRepositoryImpl.class,
        RevokedRefreshTokenMapperImpl.class,
        UserRepositoryImpl.class,
        UserMapperImpl.class,
        TestContainersConfig.class
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RevokedRefreshTokenRepositoryImplIT {

    @Autowired
    private RevokedRefreshTokenRepository repository;

    @Autowired
    private RevokedRefreshTokenJpaRepository jpaRepository;

    @Autowired
    private UserRepository userRepository;

    private UserId ownerId;

    @BeforeEach
    void seedUserAndClean() {
        userRepository.deleteAll();
        User saved = userRepository.save(User.create(
                new Email("owner@example.com"),
                Password.fromHash("$2a$10$abcdefghijklmnopqrstuvWXYZ1234567890abc")
        ));
        ownerId = saved.getId();
    }

    private RevokedRefreshToken token(UUID jti, Instant revokedAt, Instant expiresAt) {
        return RevokedRefreshToken.reconstitute(jti, ownerId, revokedAt, expiresAt);
    }

    @Nested
    @DisplayName("Atomic revocation")
    class AtomicRevocation {

        @Test
        @DisplayName("First revocation acquires the token")
        void shouldAcquireFreshToken() {
            UUID jti = UUID.randomUUID();
            boolean acquired = repository.revokeIfAbsent(token(jti,
                    Instant.parse("2026-07-27T10:00:00Z"),
                    Instant.parse("2026-08-03T10:00:00Z")));

            assertThat(acquired).isTrue();
            assertThat(jpaRepository.existsById(jti)).isTrue();
        }

        @Test
        @DisplayName("Second revocation does not acquire the token")
        void shouldRejectDuplicateAtomically() {
            UUID jti = UUID.randomUUID();
            RevokedRefreshToken revocation = token(jti,
                    Instant.parse("2026-07-27T10:00:00Z"),
                    Instant.parse("2026-08-03T10:00:00Z"));

            assertThat(repository.revokeIfAbsent(revocation)).isTrue();
            assertThat(repository.revokeIfAbsent(revocation)).isFalse();
            assertThat(jpaRepository.count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("deleteExpired")
    class Purge {

        @Test
        @DisplayName("Removes only rows whose expires_at has passed")
        void shouldRemoveOnlyExpiredRows() {
            UUID expiredJti = UUID.randomUUID();
            UUID activeJti = UUID.randomUUID();
            repository.revokeIfAbsent(token(expiredJti,
                    Instant.parse("2026-07-20T10:00:00Z"),
                    Instant.parse("2026-07-27T10:00:00Z")));
            repository.revokeIfAbsent(token(activeJti,
                    Instant.parse("2026-07-27T10:00:00Z"),
                    Instant.parse("2026-08-03T10:00:00Z")));

            int removed = repository.deleteExpired(Instant.parse("2026-07-28T00:00:00Z"));

            assertThat(removed).isEqualTo(1);
            assertThat(jpaRepository.existsById(expiredJti)).isFalse();
            assertThat(jpaRepository.existsById(activeJti)).isTrue();
        }
    }

    @Nested
    @DisplayName("Database-level constraints")
    class Constraints {

        @Test
        @DisplayName("expires_at <= revoked_at violates the CHECK constraint")
        void shouldRejectExpiresAtNotAfterRevokedAt() {
            // Even though the domain factory rejects this, the DB-level CHECK
            // is a defense-in-depth: a bug in the mapper or a manual insert
            // must not silently land an inconsistent row.
            RevokedRefreshTokenEntity bad = RevokedRefreshTokenEntity.builder()
                    .jti(UUID.randomUUID())
                    .userId(ownerId.value())
                    .revokedAt(Instant.parse("2026-07-27T10:00:00Z"))
                    .expiresAt(Instant.parse("2026-07-27T10:00:00Z"))  // equal → violates CHECK
                    .build();

            jpaRepository.save(bad);
            assertThatThrownBy(() -> jpaRepository.flush())
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("FK violation: user_id pointing at a missing user is rejected")
        void shouldRejectUnknownUserId() {
            RevokedRefreshTokenEntity row = RevokedRefreshTokenEntity.builder()
                    .jti(UUID.randomUUID())
                    .userId(UUID.randomUUID())  // no such user
                    .revokedAt(Instant.parse("2026-07-27T10:00:00Z"))
                    .expiresAt(Instant.parse("2026-08-03T10:00:00Z"))
                    .build();

            jpaRepository.save(row);
            assertThatThrownBy(() -> jpaRepository.flush())
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
    }
}
