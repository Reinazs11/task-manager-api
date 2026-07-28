package com.renan.taskmanager.users.application;

import com.renan.taskmanager.common.security.JwtService;
import com.renan.taskmanager.users.api.TokenResponse;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RefreshTokenUseCase}.
 *
 * <p>Pure unit test: the {@link JwtService} is real (we need to actually sign
 * and parse JWTs to exercise the {@code jti} extraction path), while the
 * {@link RevokedRefreshTokenRepository} is mocked so we can drive the
 * one-time-use rotation and post-revocation rejection paths deterministically.</p>
 *
 * <p>A fixed {@link Clock} is injected so the {@code revokedAt} recorded on
 * rotation is deterministic and can be asserted against.</p>
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenUseCaseTest {

    // 32+ byte secret for HS256 in tests only.
    private static final String TEST_SECRET =
            "test-secret-key-with-at-least-32-bytes-for-hs256-signing-tests-Ok!";
    private static final String ISSUER = "task-manager-api";
    private static final String AUDIENCE = "task-manager-api-users";
    private static final long ACCESS_TTL_MS = 60_000L;
    private static final long REFRESH_TTL_MS = 3_600_000L;
    private static final Instant NOW = Instant.parse("2026-07-27T10:00:00Z");

    private JwtService jwtService;
    private Clock clock;

    @Mock
    private RevokedRefreshTokenRepository revokedTokenRepository;

    private RefreshTokenUseCase useCase;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, ACCESS_TTL_MS, REFRESH_TTL_MS, ISSUER, AUDIENCE);
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        useCase = new RefreshTokenUseCase(jwtService, revokedTokenRepository, clock,
                ACCESS_TTL_MS, REFRESH_TTL_MS);
    }

    private String mintRefresh(UUID userId, String email) {
        return jwtService.generateRefreshToken(userId, email);
    }

    @Nested
    @DisplayName("With a valid, non-revoked refresh token")
    class HappyPath {

        @Test
        @DisplayName("Should rotate tokens and revoke the old jti (one-time-use)")
        void shouldRotateAndRevokeOldJti() {
            // Arrange — a freshly minted refresh token that has not been revoked.
            UUID userId = UUID.randomUUID();
            String oldRefresh = mintRefresh(userId, "renan@example.com");
            Claims oldClaims = jwtService.parseRefreshToken(oldRefresh);
            when(revokedTokenRepository.isRevokedAndActive(eq(jwtService.extractJti(oldClaims)), eq(NOW)))
                    .thenReturn(false);

            // Act
            TokenResponse response = useCase.execute(oldRefresh);

            // Assert — a brand-new pair is issued.
            assertThat(response.accessToken()).isNotEmpty();
            assertThat(response.refreshToken()).isNotEmpty();
            assertThat(response.refreshToken()).isNotEqualTo(oldRefresh);

            // The OLD jti is recorded as revoked with the old token's own exp.
            ArgumentCaptor<RevokedRefreshToken> captor = ArgumentCaptor.forClass(RevokedRefreshToken.class);
            verify(revokedTokenRepository).save(captor.capture());
            RevokedRefreshToken recorded = captor.getValue();
            assertThat(recorded.jti()).isEqualTo(jwtService.extractJti(oldClaims));
            assertThat(recorded.userId().value()).isEqualTo(userId);
            assertThat(recorded.revokedAt()).isEqualTo(NOW);
            assertThat(recorded.expiresAt()).isEqualTo(oldClaims.getExpiration().toInstant());
        }
    }

    @Nested
    @DisplayName("When the refresh token has already been revoked")
    class AlreadyRevoked {

        @Test
        @DisplayName("Should throw InvalidCredentialsException and not save anything")
        void shouldRejectRevokedToken() {
            UUID userId = UUID.randomUUID();
            String refresh = mintRefresh(userId, "renan@example.com");
            UUID jti = jwtService.extractJti(jwtService.parseRefreshToken(refresh));
            when(revokedTokenRepository.isRevokedAndActive(eq(jti), eq(NOW))).thenReturn(true);

            assertThatThrownBy(() -> useCase.execute(refresh))
                    .isInstanceOf(InvalidCredentialsException.class);

            // CRITICAL: a revoked token must not be rotated nor recorded again.
            verify(revokedTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("Rejected-revoked message is identical to rejected-invalid (anti-enumeration)")
        void revokedMessageShouldMatchInvalidMessage() {
            // Anti-enumeration regression (DECISIONS #6): a caller must not be
            // able to tell "the token was revoked" from "the token is bogus".
            UUID userId = UUID.randomUUID();
            String refresh = mintRefresh(userId, "renan@example.com");
            UUID jti = jwtService.extractJti(jwtService.parseRefreshToken(refresh));
            when(revokedTokenRepository.isRevokedAndActive(eq(jti), eq(NOW))).thenReturn(true);

            String revokedMessage = catchMessage(() -> useCase.execute(refresh));
            String invalidMessage = catchMessage(() -> useCase.execute("not-a-jwt"));

            assertThat(revokedMessage).isEqualTo(invalidMessage);
        }
    }

    @Nested
    @DisplayName("When the refresh token is structurally invalid")
    class InvalidToken {

        @Test
        @DisplayName("Tampered/expired/wrong-type/non-JWT → InvalidCredentialsException, no save")
        void shouldCollapseAnyJwtFailureToInvalidCredentials() {
            assertThatThrownBy(() -> useCase.execute("this-is-not-a-jwt"))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(revokedTokenRepository, never()).save(any());
        }
    }

    /**
     * Runs an action that is expected to throw {@link InvalidCredentialsException}
     * and returns its message. Used by the anti-enumeration test to compare the
     * "revoked" and "invalid" responses field-by-field.
     */
    private static String catchMessage(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected InvalidCredentialsException");
        } catch (InvalidCredentialsException e) {
            return e.getMessage();
        }
    }
}
