package com.renan.taskmanager.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtService}.
 *
 * <p>No Spring context needed: we instantiate the service with fixed parameters.
 * The secret used here is a 32+ byte string (HS256 minimum) generated for tests.</p>
 *
 * <p>These tests cover the contract: tokens are generated, claims are populated,
 * validation rejects tampered tokens, and the two token types have distinct claims.</p>
 */
class JwtServiceTest {

    // 32+ bytes base64-decoded to UTF-8 (kept as plain string for readability in tests)
    private static final String TEST_SECRET = "test-secret-key-with-at-least-32-bytes-for-hs256-signing-tests-Ok!";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, 60_000L, 3_600_000L);
    }

    @Nested
    @DisplayName("Token generation")
    class Generation {

        @Test
        @DisplayName("Access token should contain subject, email and type=access")
        void accessTokenShouldContainCorrectClaims() {
            UUID userId = UUID.randomUUID();

            String token = jwtService.generateAccessToken(userId, "renan@example.com");

            Claims claims = jwtService.parseAndValidate(token);
            assertThat(jwtService.extractUserId(claims)).isEqualTo(userId);
            assertThat(jwtService.extractEmail(claims)).isEqualTo("renan@example.com");
            assertThat(jwtService.extractTokenType(claims)).isEqualTo(JwtService.TYPE_ACCESS);
        }

        @Test
        @DisplayName("Refresh token should have type=refresh")
        void refreshTokenShouldHaveTypeRefresh() {
            UUID userId = UUID.randomUUID();

            String token = jwtService.generateRefreshToken(userId, "renan@example.com");

            Claims claims = jwtService.parseAndValidate(token);
            assertThat(jwtService.extractTokenType(claims)).isEqualTo(JwtService.TYPE_REFRESH);
        }

        @Test
        @DisplayName("Each token should have a unique jti (id)")
        void tokensShouldHaveUniqueJti() {
            UUID userId = UUID.randomUUID();

            String token1 = jwtService.generateAccessToken(userId, "renan@example.com");
            String token2 = jwtService.generateAccessToken(userId, "renan@example.com");

            Claims c1 = jwtService.parseAndValidate(token1);
            Claims c2 = jwtService.parseAndValidate(token2);
            assertThat(c1.getId()).isNotNull().isNotEqualTo(c2.getId());
        }

        @Test
        @DisplayName("Two tokens for the same user should differ (random jti, iat)")
        void twoTokensForSameUserShouldDiffer() {
            UUID userId = UUID.randomUUID();

            String t1 = jwtService.generateAccessToken(userId, "renan@example.com");
            String t2 = jwtService.generateAccessToken(userId, "renan@example.com");

            assertThat(t1).isNotEqualTo(t2);
        }
    }

    @Nested
    @DisplayName("Token validation")
    class Validation {

        @Test
        @DisplayName("Should reject a token signed with a different key")
        void shouldRejectTokenSignedWithDifferentKey() {
            // Service A signs the token
            JwtService other = new JwtService("another-32-byte-secret-key-for-testing-Ok!!", 60_000L, 3_600_000L);
            String token = other.generateAccessToken(UUID.randomUUID(), "x@example.com");

            // Service B (our subject) tries to validate with a different key
            assertThatThrownBy(() -> jwtService.parseAndValidate(token))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("Should reject a tampered token")
        void shouldRejectTamperedToken() {
            String token = jwtService.generateAccessToken(UUID.randomUUID(), "x@example.com");

            // Tamper with the PAYLOAD, not the signature.
            // A JWT is "header.payload.signature" and the signature covers the
            // first two segments. Flipping a char in the payload therefore
            // changes what was signed, so the signature no longer matches.
            //
            // <b>Why not flip a char in the signature?</b> The signature is
            // Base64URL-encoded: each char is 6 bits. HS256 produces 32 bytes,
            // which encodes to 43 chars (258 bits) — the last char holds 2
            // padding bits that jjwt ignores on decode. Flipping only the last
            // char can change JUST those padding bits, leaving the decoded
            // signature bytes unchanged and making the token still validate.
            // That produces a flaky test that passes or fails depending on the
            // randomly generated jti. Tampering with the payload is the
            // deterministic way to force a signature mismatch.
            String[] parts = token.split("\\.");
            char lastPayload = parts[1].charAt(parts[1].length() - 1);
            char replacement = lastPayload == 'A' ? 'B' : 'A';
            parts[1] = parts[1].substring(0, parts[1].length() - 1) + replacement;
            String tampered = parts[0] + "." + parts[1] + "." + parts[2];

            assertThatThrownBy(() -> jwtService.parseAndValidate(tampered))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("Should reject a malformed (non-JWT) string")
        void shouldRejectMalformedToken() {
            assertThatThrownBy(() -> jwtService.parseAndValidate("not-a-jwt"))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("Should reject an expired token")
        void shouldRejectExpiredToken() throws InterruptedException {
            // Build a service with 1ms TTL so the token expires immediately
            JwtService shortLived = new JwtService(TEST_SECRET, 1L, 1L);
            String token = shortLived.generateAccessToken(UUID.randomUUID(), "x@example.com");

            Thread.sleep(50);  // ensure it expired

            assertThatThrownBy(() -> shortLived.parseAndValidate(token))
                    .isInstanceOf(JwtException.class);
        }
    }

    @Nested
    @DisplayName("Key length validation")
    class KeyLength {

        @Test
        @DisplayName("Should reject a secret shorter than 32 bytes")
        void shouldRejectShortSecret() {
            // HS256 requires a key of at least 256 bits (32 bytes).
            // A 5-char ASCII string is 5 bytes (40 bits), well below the minimum.
            // jjwt throws WeakKeyException (a RuntimeException subclass) for this case.
            assertThatThrownBy(() -> new JwtService("short", 60_000L, 3_600_000L))
                    .isInstanceOf(io.jsonwebtoken.security.WeakKeyException.class)
                    .hasMessageContaining("256 bits");
        }
    }
}
