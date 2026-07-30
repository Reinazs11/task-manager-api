package com.renan.taskmanager.users.application;

import com.renan.taskmanager.common.audit.application.AuditEventRecorder;
import com.renan.taskmanager.common.security.JwtService;
import com.renan.taskmanager.users.domain.InvalidCredentialsException;
import com.renan.taskmanager.users.domain.RevokedRefreshToken;
import com.renan.taskmanager.users.domain.RevokedRefreshTokenRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link LogoutUseCase}.
 *
 * <p>Pure unit test: real {@link JwtService} (we need real JWT signing/parsing
 * to extract {@code jti} from a supplied token), mocked
 * {@link RevokedRefreshTokenRepository} (so we can verify the recorded
 * revocation without a database).</p>
 */
@ExtendWith(MockitoExtension.class)
class LogoutUseCaseTest {

    private static final String TEST_SECRET =
            "test-secret-key-with-at-least-32-bytes-for-hs256-signing-tests-Ok!";
    private static final String ISSUER = "task-manager-api";
    private static final String AUDIENCE = "task-manager-api-users";
    private static final Instant NOW = Instant.parse("2026-07-27T10:00:00Z");

    private JwtService jwtService;

    @Mock
    private RevokedRefreshTokenRepository revokedTokenRepository;

    @Mock
    private AuditEventRecorder auditRecorder;

    private LogoutUseCase useCase;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, 60_000L, 3_600_000L, ISSUER, AUDIENCE);
        useCase = new LogoutUseCase(jwtService, revokedTokenRepository, Clock.fixed(NOW, ZoneOffset.UTC), auditRecorder);
    }

    @Nested
    @DisplayName("With a valid refresh token")
    class HappyPath {

        @Test
        @DisplayName("Should record the jti as revoked with the token's own expiry")
        void shouldRecordRevocation() {
            UUID userId = UUID.randomUUID();
            String refresh = jwtService.generateRefreshToken(userId, "renan@example.com");
            Claims claims = jwtService.parseRefreshToken(refresh);

            useCase.execute(refresh);

            ArgumentCaptor<RevokedRefreshToken> captor = ArgumentCaptor.forClass(RevokedRefreshToken.class);
            verify(revokedTokenRepository).revokeIfAbsent(captor.capture());
            RevokedRefreshToken recorded = captor.getValue();
            assertThat(recorded.jti()).isEqualTo(jwtService.extractJti(claims));
            assertThat(recorded.userId().value()).isEqualTo(userId);
            assertThat(recorded.revokedAt()).isEqualTo(NOW);
            assertThat(recorded.expiresAt()).isEqualTo(claims.getExpiration().toInstant());
            verify(auditRecorder).recordLogout(recorded.userId());
        }

        @Test
        @DisplayName("Should not throw on a valid token")
        void shouldNotThrow() {
            String refresh = jwtService.generateRefreshToken(UUID.randomUUID(), "renan@example.com");

            assertThatCode(() -> useCase.execute(refresh)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("With an invalid or wrong-type token")
    class InvalidToken {

        @Test
        @DisplayName("Non-JWT string → InvalidCredentialsException, nothing saved")
        void shouldRejectNonJwt() {
            assertThatThrownBy(() -> useCase.execute("not-a-jwt"))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(revokedTokenRepository, never()).revokeIfAbsent(any());
        }

        @Test
        @DisplayName("Access token used as refresh → InvalidCredentialsException (type check)")
        void shouldRejectAccessToken() {
            String access = jwtService.generateAccessToken(UUID.randomUUID(), "renan@example.com");

            assertThatThrownBy(() -> useCase.execute(access))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(revokedTokenRepository, never()).revokeIfAbsent(any());
        }
    }

    @Nested
    @DisplayName("Idempotency")
    class Idempotency {

        @Test
        @DisplayName("Logging out twice with the same token does not throw (delegated to repo)")
        void shouldBeIdempotentWhenAlreadyRevoked() {
            // The repository's save() is itself idempotent (see
            // RevokedRefreshTokenRepositoryImpl). The use case does not branch
            // on "already revoked" — it always hands the token to the repo,
            // which no-ops a duplicate. We only assert that the second call
            // does not throw; the no-op behavior itself is covered by
            // RevokedRefreshTokenRepositoryImplIT.
            String refresh = jwtService.generateRefreshToken(UUID.randomUUID(), "renan@example.com");

            useCase.execute(refresh);
            assertThatCode(() -> useCase.execute(refresh)).doesNotThrowAnyException();
        }
    }
}
