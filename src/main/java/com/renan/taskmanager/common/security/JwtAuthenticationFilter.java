package com.renan.taskmanager.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT authentication filter: runs once per request to validate the JWT and
 * populate the {@link SecurityContextHolder} when a valid token is present.
 *
 * <p><b>Flow:</b></p>
 * <ol>
 *   <li>Extract {@code Authorization: Bearer <token>} header.</li>
 *   <li>If absent or malformed: continue the chain unauthenticated
 *       (let Spring Security deny the route later if it requires auth).</li>
 *   <li>If present: validate signature + expiration, extract claims,
 *       and set an authenticated principal in the context.</li>
 * </ol>
 *
 * <p><b>Why OncePerRequestFilter?</b>
 * Guarantees a single execution per request even inside forward/error includes.
 * Spring's recommended base class for custom security filters.</p>
 *
 * <p><b>Why not throw on invalid tokens?</b>
 * If we threw, a tampered token would cause a 500. Instead, we log and let the
 * request proceed unauthenticated — protected routes then return a clean 403.</p>
 *
 * <p><b>Why "Bearer "?</b>
 * RFC 6750 standard scheme for bearer tokens. We strip the prefix and use the
 * remainder as the raw JWT.</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());

        try {
            Claims claims = jwtService.parseAndValidate(token);
            String tokenType = jwtService.extractTokenType(claims);

            // Only access tokens grant API access. Refresh tokens are for
            // minting new access tokens, not for direct API calls.
            if (!JwtService.TYPE_ACCESS.equals(tokenType)) {
                log.debug("Rejected non-access token (type={})", tokenType);
                chain.doFilter(request, response);
                return;
            }

            // Build the authenticated principal. Spring stores it as a String
            // subject (user id) and grants a default "USER" role for now.
            // As roles/permissions are added to the domain, extend here.
            var auth = new UsernamePasswordAuthenticationToken(
                    claims.getSubject(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_USER"))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);

        } catch (JwtException e) {
            // Invalid/expired/tampered token: leave context unauthenticated.
            // Protected routes will return 403. Logging at debug to avoid noise.
            log.debug("Invalid JWT rejected: {}", e.getMessage());
        }

        chain.doFilter(request, response);
    }
}
