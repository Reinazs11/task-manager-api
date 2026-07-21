package com.renan.taskmanager.users.application;

import com.renan.taskmanager.common.security.JwtService;
import com.renan.taskmanager.users.api.TokenResponse;
import com.renan.taskmanager.users.domain.InvalidCredentialsException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Application use case: exchange a valid refresh token for a new access +
 * refresh token pair (token rotation).
 *
 * <p><b>Flow:</b></p>
 * <ol>
 *   <li>Parse + signature/exp validation via {@link JwtService#parseAndValidate}.
 *       Any failure surfaces as {@link JwtException}, which the
 *       {@code JwtAuthenticationFilter} / {@code GlobalExceptionHandler}
 *       pipeline maps to HTTP 401.</li>
 *   <li>Reject tokens whose {@code type} claim is not {@code "refresh"}. This
 *       prevents an access token from being used as a refresh token.</li>
 *   <li>Re-issue a fresh access + refresh pair. The {@code jti} of the new
 *       tokens differs from the original, so callers can detect rotation.</li>
 * </ol>
 *
 * <p><b>Stateless rotation caveat (intentional):</b>
 * Without a server-side token store or blacklist we cannot revoke the
 * presented refresh token the moment it is exchanged. Both the old and the
 * new refresh token remain independently valid until each expires. This is
 * the accepted trade-off of the stateless design — same shape as access
 * tokens — and keeps the API horizontally scalable. If true one-time-use
 * refresh becomes a requirement, a token store (Redis or a DB table) is the
 * canonical next step.</p>
 *
 * <p><b>Why {@link InvalidCredentialsException} (→ 401)?</b>
 * The wrong-token-type case is conceptually a credentials failure: the caller
 * presented something that is not a valid refresh token. 401 matches both
 * the JWT spec expectation and the existing login failure contract.</p>
 */
@Service
public class RefreshTokenUseCase {

    private final JwtService jwtService;
    private final long accessTtlMs;
    private final long refreshTtlMs;

    public RefreshTokenUseCase(
            JwtService jwtService,
            @Value("${app.jwt.access-token-expiration-ms:900000}") long accessTtlMs,
            @Value("${app.jwt.refresh-token-expiration-ms:604800000}") long refreshTtlMs
    ) {
        this.jwtService = jwtService;
        this.accessTtlMs = accessTtlMs;
        this.refreshTtlMs = refreshTtlMs;
    }

    public TokenResponse execute(String refreshToken) {
        Claims claims;
        try {
            claims = jwtService.parseAndValidate(refreshToken);
        } catch (JwtException e) {
            // Re-throw as the domain credentials exception so GlobalExceptionHandler
            // maps it to 401 with the standard envelope. The JwtException details
            // (expired, tampered, malformed) are intentionally collapsed to a single
            // message — callers must not learn why the token failed.
            throw new InvalidCredentialsException();
        }

        String tokenType = jwtService.extractTokenType(claims);
        if (!JwtService.TYPE_REFRESH.equals(tokenType)) {
            // Access token (or anything else) used as refresh — reject as 401.
            throw new InvalidCredentialsException();
        }

        UUID userId = jwtService.extractUserId(claims);
        String email = jwtService.extractEmail(claims);

        String newAccessToken = jwtService.generateAccessToken(userId, email);
        String newRefreshToken = jwtService.generateRefreshToken(userId, email);

        return TokenResponse.of(newAccessToken, newRefreshToken, accessTtlMs, refreshTtlMs);
    }
}
