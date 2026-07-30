package com.renan.taskmanager.users.application;

import com.renan.taskmanager.common.audit.application.AuditEventRecorder;
import com.renan.taskmanager.common.domain.UserId;
import com.renan.taskmanager.common.security.JwtService;
import com.renan.taskmanager.users.api.TokenResponse;
import com.renan.taskmanager.users.domain.InvalidCredentialsException;
import com.renan.taskmanager.users.domain.RevokedRefreshToken;
import com.renan.taskmanager.users.domain.RevokedRefreshTokenRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Application use case: exchange a valid refresh token for a new access +
 * refresh token pair (token rotation), with one-time-use enforcement.
 *
 * <p><b>Flow:</b></p>
 * <ol>
 *   <li>Parse + signature + expiration + iss/aud + type=refresh validation,
 *       all centralized in {@link JwtService#parseRefreshToken}. Any failure
 *       (bad signature, expired, wrong type, wrong issuer/audience, malformed)
 *       surfaces as {@link JwtException}.</li>
 *   <li>Atomically insert the token's {@code jti} in the revocation store.
 *       Only the request that acquires this one-time-use claim may continue.</li>
 *   <li>Re-issue a fresh access + refresh pair (new {@code jti}s).</li>
 * </ol>
 *
 * <p><b>One-time-use rotation:</b> the old refresh token is revoked in the same
 * transaction as the new pair's issuance. A replay of the old token returns
 * 401. See {@code DECISIONS.md} #17 for the design (PostgreSQL token store
 * over Redis: no new infra, same transaction atomicity). This closes the
 * previously-accepted stateless limitation.</p>
 *
 * <p><b>Why {@link InvalidCredentialsException} (→ 401)?</b>
 * Any failure (wrong type, tampered, expired, bad iss/aud, malformed, OR
 * revoked) is conceptually a credentials failure: the caller presented
 * something that is not a valid, usable refresh token. The original
 * JwtException details are intentionally collapsed to a single message —
 * callers must not learn whether the token was expired, tampered, revoked,
 * or the wrong type. 401 matches both the JWT spec expectation and the
 * existing login failure contract.</p>
 *
 * <p><b>Why a {@link Clock} dependency?</b> So the {@code revokedAt} timestamp
 * recorded on rotation is deterministic and injectable in tests. Production
 * uses {@link Clock#systemUTC()}; tests inject a fixed clock.</p>
 */
@Service
public class RefreshTokenUseCase {

    private final JwtService jwtService;
    private final RevokedRefreshTokenRepository revokedTokenRepository;
    private final Clock clock;
    private final AuditEventRecorder auditRecorder;
    private final long accessTtlMs;
    private final long refreshTtlMs;

    public RefreshTokenUseCase(
            JwtService jwtService,
            RevokedRefreshTokenRepository revokedTokenRepository,
            Clock clock,
            AuditEventRecorder auditRecorder,
            @Value("${app.jwt.access-token-expiration-ms:900000}") long accessTtlMs,
            @Value("${app.jwt.refresh-token-expiration-ms:604800000}") long refreshTtlMs
    ) {
        this.jwtService = jwtService;
        this.revokedTokenRepository = revokedTokenRepository;
        this.clock = clock;
        this.auditRecorder = auditRecorder;
        this.accessTtlMs = accessTtlMs;
        this.refreshTtlMs = refreshTtlMs;
    }

    @Transactional
    public TokenResponse execute(String refreshToken) {
        Claims claims = parseToken(refreshToken);
        UUID userId = jwtService.extractUserId(claims);
        acquireOneTimeUse(claims, userId);

        String email = jwtService.extractEmail(claims);
        String accessToken = jwtService.generateAccessToken(userId, email);
        String newRefreshToken = jwtService.generateRefreshToken(userId, email);
        auditRecorder.recordRefreshRotated(UserId.of(userId));
        return TokenResponse.of(accessToken, newRefreshToken, accessTtlMs, refreshTtlMs);
    }

    private Claims parseToken(String refreshToken) {
        Claims claims;
        try {
            claims = jwtService.parseRefreshToken(refreshToken);
        } catch (JwtException e) {
            throw new InvalidCredentialsException();
        }
        return claims;
    }

    private void acquireOneTimeUse(Claims claims, UUID userId) {
        UUID jti = jwtService.extractJti(claims);
        Instant now = clock.instant();
        Instant oldExpiry = claims.getExpiration().toInstant();
        if (!revokedTokenRepository.revokeIfAbsent(RevokedRefreshToken.reconstitute(
                jti, UserId.of(userId), now, oldExpiry))) {
            throw new InvalidCredentialsException();
        }
    }
}
