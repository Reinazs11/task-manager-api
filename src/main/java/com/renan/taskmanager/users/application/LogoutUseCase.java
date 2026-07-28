package com.renan.taskmanager.users.application;

import com.renan.taskmanager.common.audit.application.AuditEventRecorder;
import com.renan.taskmanager.common.domain.UserId;
import com.renan.taskmanager.common.security.JwtService;
import com.renan.taskmanager.users.domain.InvalidCredentialsException;
import com.renan.taskmanager.users.domain.RevokedRefreshToken;
import com.renan.taskmanager.users.domain.RevokedRefreshTokenRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Application use case: revoke a refresh token server-side (logout).
 *
 * <p><b>Flow:</b></p>
 * <ol>
 *   <li>Parse + validate the supplied token as a refresh token via
 *       {@link JwtService#parseRefreshToken}. Any failure (wrong type,
 *       tampered, expired, bad iss/aud, malformed) is collapsed to
 *       {@link InvalidCredentialsException} → 401, identical to
 *       {@code RefreshTokenUseCase}. Anti-enumeration (DECISIONS.md #6): a
 *       caller cannot tell a forged token from a real one.</li>
 *   <li>Record the token's {@code jti} as revoked, with the token's own
 *       {@code exp} as the row's {@code expires_at}. The save is idempotent
 *       (see {@link RevokedRefreshTokenRepository#save}), so logging out the
 *       same token twice is a no-op.</li>
 * </ol>
 *
 * <p><b>What about access tokens?</b> They are short-lived (15 min) and not
 * revocable server-side without a check on every authenticated request. The
 * client should drop them locally; the server-side revocation here covers the
 * long-lived refresh token, which is the one that matters. See DECISIONS.md
 * #17 for the rationale.</p>
 *
 * <p><b>Expired refresh tokens:</b> {@code parseRefreshToken} rejects them
 * (→ 401). That is intentional and harmless — an expired refresh token is
 * already useless, so revoking it would record a row that
 * {@link RevokedRefreshTokenRepository#isRevokedAndActive} returns false for
 * immediately. Treating it as 401 keeps the contract simple and the message
 * indistinguishable from any other invalid token.</p>
 *
 * <p><b>Why is logout not authenticated (no access JWT required)?</b>
 * Consistent with {@code /auth/login} and {@code /auth/refresh}: the caller
 * proves ownership of the refresh token by presenting it. Requiring an access
 * JWT would block logout from a device whose access token has already
 * expired. See {@code SecurityConfig} — {@code /api/v1/auth/**} is public.</p>
 */
@Service
public class LogoutUseCase {

    private final JwtService jwtService;
    private final RevokedRefreshTokenRepository revokedTokenRepository;
    private final Clock clock;
    private final AuditEventRecorder auditRecorder;

    public LogoutUseCase(JwtService jwtService,
                         RevokedRefreshTokenRepository revokedTokenRepository,
                         Clock clock,
                         AuditEventRecorder auditRecorder) {
        this.jwtService = jwtService;
        this.revokedTokenRepository = revokedTokenRepository;
        this.clock = clock;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public void execute(String refreshToken) {
        Claims claims;
        try {
            claims = jwtService.parseRefreshToken(refreshToken);
        } catch (JwtException e) {
            // Same anti-enumeration collapse as RefreshTokenUseCase: the caller
            // must not learn why the token was rejected.
            throw new InvalidCredentialsException();
        }

        UUID jti = jwtService.extractJti(claims);
        UUID userId = jwtService.extractUserId(claims);
        Instant expiresAt = claims.getExpiration().toInstant();

        revokedTokenRepository.save(RevokedRefreshToken.reconstitute(
                jti, UserId.of(userId), clock.instant(), expiresAt));
        // Record after the revoke, inside the same tx. A logout is a security
        // signal worth keeping: "who explicitly ended their session, when".
        auditRecorder.recordLogout(UserId.of(userId));
    }
}
