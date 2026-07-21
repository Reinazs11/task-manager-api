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
 *   <li>Parse + signature + expiration + iss/aud + type=refresh validation,
 *       all centralized in {@link JwtService#parseRefreshToken}. Any failure
 *       (bad signature, expired, wrong type, wrong issuer/audience, malformed)
 *       surfaces as {@link JwtException}.</li>
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
 * Any failure (wrong type, tampered, expired, bad iss/aud, malformed) is
 * conceptually a credentials failure: the caller presented something that
 * is not a valid refresh token. The original JwtException details are
 * intentionally collapsed to a single message — callers must not learn
 * why the token failed. 401 matches both the JWT spec expectation and the
 * existing login failure contract.</p>
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
            claims = jwtService.parseRefreshToken(refreshToken);
        } catch (JwtException e) {
            // Collapse every failure to the same 401 — anti-enumeration:
            // callers must not learn whether the token was expired, tampered,
            // or the wrong type.
            throw new InvalidCredentialsException();
        }

        UUID userId = jwtService.extractUserId(claims);
        String email = jwtService.extractEmail(claims);

        String newAccessToken = jwtService.generateAccessToken(userId, email);
        String newRefreshToken = jwtService.generateRefreshToken(userId, email);

        return TokenResponse.of(newAccessToken, newRefreshToken, accessTtlMs, refreshTtlMs);
    }
}
