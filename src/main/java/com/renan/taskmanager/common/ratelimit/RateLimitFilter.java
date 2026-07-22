package com.renan.taskmanager.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.renan.taskmanager.common.api.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Rate-limits the public auth endpoints ({@code /api/v1/auth/**}) per source IP.
 *
 * <p><b>Why only auth?</b> Login and refresh are the brute-force surface: an
 * attacker can hammer them to guess passwords or rotate refresh tokens. The
 * rest of the API is already JWT-gated and token-bound to a user, so the
 * marginal value of throttling there is low. See {@code DECISIONS.md}.</p>
 *
 * <p><b>Why a shared bucket for /login and /refresh?</b> Both are auth paths
 * equally sensitive to brute-force; splitting them would double an attacker's
 * budget. A single per-IP bucket keeps the throttling intent intact.</p>
 *
 * <p><b>Why write the response directly instead of throwing for the advice?</b>
 * A servlet filter runs before the {@code DispatcherServlet}, so an exception
 * thrown here is not caught by {@code @RestControllerAdvice} — it would bubble
 * up as a generic 500. Mirroring {@code JsonAuthenticationEntryPoint} (which
 * handles 401 the same way), the filter serializes the same six-field
 * {@link ErrorResponse} itself, keeping the contract identical across every
 * status code regardless of which layer produced it.</p>
 *
 * <p>Registered in {@code SecurityConfig} ahead of the JWT filter so an
 * over-budget request is rejected before any BCrypt/JWT work happens.</p>
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final String AUTH_PATH_PREFIX = "/api/v1/auth/";
    private static final String RATE_LIMIT_MESSAGE = "Rate limit exceeded. Try again later.";

    private final RateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver = new ClientIpResolver();
    private final ObjectMapper objectMapper;

    /**
     * @param objectMapper Jackson serializer used to write the error body
     * @param rateLimiter   the shared per-IP token-bucket limiter (see {@link RateLimitConfig})
     */
    public RateLimitFilter(ObjectMapper objectMapper, RateLimiter rateLimiter) {
        this.objectMapper = objectMapper;
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (appliesTo(request)) {
            String clientIp = clientIpResolver.resolve(request);
            RateLimiter.Result result = rateLimiter.tryConsume(clientIp);
            if (!result.allowed()) {
                writeTooManyRequests(request, response, result.retryAfterSeconds());
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * Serializes the standardized error body for a throttled request and stops
     * the chain so no further processing happens.
     */
    private void writeTooManyRequests(HttpServletRequest request,
                                     HttpServletResponse response,
                                     long retryAfterSeconds) throws IOException {
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.TOO_MANY_REQUESTS,
                RATE_LIMIT_MESSAGE,
                request.getRequestURI(),
                List.of()
        );
        log.warn("Client error 429: {} ({})", RATE_LIMIT_MESSAGE, request.getRequestURI());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    /**
     * The filter is registered in the Spring Security chain, so it sees every
     * request; only the auth path prefix is actually throttled.
     */
    private static boolean appliesTo(HttpServletRequest request) {
        return request.getRequestURI().startsWith(AUTH_PATH_PREFIX);
    }
}
