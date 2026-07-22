package com.renan.taskmanager.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT generation and validation service.
 *
 * <p>Issues two token types:</p>
 * <ul>
 *   <li><b>Access token</b> (15 min): short-lived, sent on every authenticated request.</li>
 *   <li><b>Refresh token</b> (7 days): long-lived, used to mint new access tokens
 *       without forcing the user to log in again.</li>
 * </ul>
 *
 * <p><b>Why HS256 (symmetric)?</b>
 * Single-service API: the same service signs and verifies. Simpler than RS256
 * (asymmetric), which only pays off when you have separate auth and resource
 * servers. For a portfolio project, HS256 is the right call.</p>
 *
 * <p><b>Why jjwt (and not auth0/java-jwt)?</b>
 * jjwt is the most widely-used Java JWT library, with a clean builder API and
 * active maintenance. The secret is configured via {@code app.jwt.secret}.</p>
 *
 * <p><b>Token claims:</b>
 * {@code sub} = user id (UUID string), {@code iss} = issuer, {@code aud} = audience,
 * {@code email} = user email, {@code type} = "access" or "refresh",
 * {@code iat}, {@code exp}, {@code jti}.</p>
 *
 * <p><b>Why {@code iss}/{@code aud} matter even in a single-service API:</b>
 * if another service ever shares this signing key, its tokens would be valid
 * here unless the audience is checked. {@code requireIssuer} +
 * {@code requireAudience} in the parser is cheap defense-in-depth and avoids
 * a silent cross-service token-reuse hole.</p>
 *
 * <p><b>Centralized type check:</b> use {@link #parseAccessToken} or
 * {@link #parseRefreshToken} to validate the {@code type} claim in one place.
 * Callers that only want raw signature/exp validation (e.g. to enforce the
 * type themselves with a richer error) can still use {@link #parseAndValidate}.</p>
 */
@Service
public class JwtService {

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_EMAIL = "email";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey signingKey;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;
    private final String issuer;
    private final String audience;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiration-ms:900000}") long accessTtlMs,
            @Value("${app.jwt.refresh-token-expiration-ms:604800000}") long refreshTtlMs,
            @Value("${app.jwt.issuer:task-manager-api}") String issuer,
            @Value("${app.jwt.audience:task-manager-api-users}") String audience
    ) {
        // HMAC-SHA key requires >= 256 bits (32 bytes). Validated by Keys.hmacShaKeyFor,
        // which throws a clear exception if the secret is too short.
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = Duration.ofMillis(accessTtlMs);
        this.refreshTokenTtl = Duration.ofMillis(refreshTtlMs);
        this.issuer = issuer;
        this.audience = audience;
    }

    /**
     * Generates a short-lived access token.
     */
    public String generateAccessToken(UUID userId, String email) {
        return buildToken(userId, email, accessTokenTtl, TYPE_ACCESS);
    }

    /**
     * Generates a long-lived refresh token.
     */
    public String generateRefreshToken(UUID userId, String email) {
        return buildToken(userId, email, refreshTokenTtl, TYPE_REFRESH);
    }

    private String buildToken(UUID userId, String email, Duration ttl, String type) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .issuer(issuer)
                .audience().add(audience).and()
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .id(UUID.randomUUID().toString())  // jti: unique token id
                .signWith(signingKey)
                .compact();
    }

    /**
     * Parses and validates a token, returning its claims. Enforces signature,
     * expiration, {@code iss}, and {@code aud}.
     *
     * <p>This method does NOT enforce the {@code type} (access/refresh) claim.
     * For type-aware validation use {@link #parseAccessToken} or
     * {@link #parseRefreshToken}.</p>
     *
     * @throws JwtException if the signature is invalid, the token is expired,
     *                      malformed, the issuer/audience does not match, or
     *                      otherwise untrusted
     */
    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Parses and validates a token, additionally enforcing that its
     * {@code type} claim equals {@link #TYPE_ACCESS}. Use this in the request
     * filter (and anywhere access tokens are the only acceptable type).
     *
     * @throws JwtException for the same reasons as {@link #parseAndValidate},
     *                      or if the token is not an access token
     */
    public Claims parseAccessToken(String token) {
        Claims claims = parseAndValidate(token);
        assertType(claims, TYPE_ACCESS);
        return claims;
    }

    /**
     * Parses and validates a token, additionally enforcing that its
     * {@code type} claim equals {@link #TYPE_REFRESH}. Use this in the refresh
     * endpoint.
     *
     * @throws JwtException for the same reasons as {@link #parseAndValidate},
     *                      or if the token is not a refresh token
     */
    public Claims parseRefreshToken(String token) {
        Claims claims = parseAndValidate(token);
        assertType(claims, TYPE_REFRESH);
        return claims;
    }

    private void assertType(Claims claims, String expected) {
        String actual = claims.get(CLAIM_TYPE, String.class);
        if (!expected.equals(actual)) {
            throw new JwtException(
                    "Token type mismatch (expected " + expected
                            + ", got " + actual + ")");
        }
    }

    /**
     * Extracts the user id (subject claim) from a valid token.
     */
    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    /**
     * Returns the value of the custom "type" claim ("access" or "refresh").
     */
    public String extractTokenType(Claims claims) {
        return claims.get(CLAIM_TYPE, String.class);
    }

    /**
     * Returns the value of the custom "email" claim.
     */
    public String extractEmail(Claims claims) {
        return claims.get(CLAIM_EMAIL, String.class);
    }
}
