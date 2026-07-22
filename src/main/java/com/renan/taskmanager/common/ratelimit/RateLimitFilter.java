package com.renan.taskmanager.common.ratelimit;

import com.renan.taskmanager.common.api.HttpErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Rate-limits the brute-force-sensitive auth endpoints ({@code /login} and
 * {@code /refresh}) per source IP.
 *
 * <p><b>Why only login + refresh (and not register)?</b> These are the only
 * endpoints where an attacker benefits from volume — guessing passwords or
 * rotating refresh tokens. Register is a creation, not a guess; throttling it
 * would also block legitimate onboarding from shared NATs (a whole office
 * signing up in the same minute). See {@code DECISIONS.md} #15.</p>
 *
 * <p><b>Why a shared bucket for /login and /refresh?</b> Both are equally
 * brute-force-sensitive auth paths; separate buckets would double an attacker's
 * budget. A single per-IP bucket keeps the throttling intent intact.</p>
 *
 * <p><b>Why write the response directly instead of throwing for the advice?</b>
 * A servlet filter runs before the {@code DispatcherServlet}, so an exception
 * thrown here is not caught by {@code @RestControllerAdvice} — it would bubble
 * up as a generic 500. The filter delegates the bytes to {@link HttpErrorWriter},
 * the same writer used by {@code JsonAuthenticationEntryPoint} (401), so the
 * six-field {@code ErrorResponse} contract holds regardless of which layer
 * rejects the request.</p>
 *
 * <p><b>Registration note:</b> this filter is both a {@code @Component} (so Spring
 * Boot auto-registers it in the servlet container) and added to the
 * {@code SecurityFilterChain} via {@code addFilterBefore}. Without
 * {@link OncePerRequestFilter}'s {@code alreadyFilteredAttributeName} guard it
 * would run twice per request and consume two tokens; that guard is what makes
 * the double registration safe. The same pattern is used by
 * {@code JwtAuthenticationFilter}.</p>
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /**
     * Only the brute-force-sensitive auth paths are throttled. Register is
     * intentionally excluded (see class Javadoc).
     */
    private static final Set<String> THROTTLED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh"
    );

    private static final String RATE_LIMIT_MESSAGE = "Rate limit exceeded. Try again later.";

    private final RateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final HttpErrorWriter httpErrorWriter;

    /**
     * @param httpErrorWriter      shared writer for the standardized error body
     * @param rateLimiter          the per-IP token-bucket limiter (see {@link RateLimitConfig})
     * @param trustForwardedFor    whether {@code X-Forwarded-For} is trusted; only
     *                             enable behind a proxy that overwrites the header
     */
    public RateLimitFilter(HttpErrorWriter httpErrorWriter,
                           RateLimiter rateLimiter,
                           @Value("${app.rate-limit.trust-forwarded-for:false}") boolean trustForwardedFor) {
        this.httpErrorWriter = httpErrorWriter;
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = new ClientIpResolver(trustForwardedFor);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (appliesTo(request)) {
            String clientIp = clientIpResolver.resolve(request);
            RateLimiter.Result result = rateLimiter.tryConsume(clientIp);
            if (!result.allowed()) {
                response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
                httpErrorWriter.write(response, HttpStatus.TOO_MANY_REQUESTS, RATE_LIMIT_MESSAGE, request.getRequestURI());
                log.warn("Client error 429: {} ({})", RATE_LIMIT_MESSAGE, request.getRequestURI());
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean appliesTo(HttpServletRequest request) {
        return THROTTLED_PATHS.contains(request.getRequestURI());
    }
}
