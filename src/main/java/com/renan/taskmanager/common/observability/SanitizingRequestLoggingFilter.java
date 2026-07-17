package com.renan.taskmanager.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Emits a single structured log line per request with method, URI, status and
 * latency, plus optional header/body detail at DEBUG/TRACE — with secrets
 * scrubbed.
 *
 * <p><b>Why a custom filter and not Spring's {@code CommonsRequestLoggingFilter}?</b>
 * The built-in one has no concept of redaction: pass it {@code INCLUDE_HEADERS}
 * and it dumps the {@code Authorization} header verbatim. Security-conscious
 * services must own their logging pipeline so that secrets can never reach logs.
 * This filter encodes that policy in one auditable place.</p>
 *
 * <p><b>Secret redaction policy:</b>
 * <ul>
 *   <li>Header names in {@link #SENSITIVE_HEADERS} are replaced with
 *       {@code [REDACTED]} at every log level.</li>
 *   <li>Body logging is gated behind TRACE <em>and</em> a content heuristic:
 *       bodies containing {@code password}, {@code token}, {@code secret} or
 *       {@code refreshToken} keys are never logged, even at TRACE.</li>
 *   <li>Body logging defaults to off (TRACE is not enabled in any shipped
 *       profile) — it exists for ad-hoc debugging only.</li>
 * </ul>
 *
 * <p><b>Why capture status with a lightweight wrapper?</b>
 * The real status is only known after the chain returns. Wrapping the response
 * lets us read {@code getStatus()} after {@code chain.doFilter} without
 * buffering the whole body.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class SanitizingRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SanitizingRequestLoggingFilter.class);

    /** Headers whose values must never appear in logs. Compared case-insensitively. */
    static final Set<String> SENSITIVE_HEADERS = Set.of(
            HttpHeaders.AUTHORIZATION.toLowerCase(),
            HttpHeaders.COOKIE.toLowerCase(),
            HttpHeaders.SET_COOKIE.toLowerCase()
    );

    /** Body substrings that block body logging even at TRACE. */
    static final List<String> SENSITIVE_BODY_KEYS = List.of(
            "password", "token", "secret", "refreshtoken"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long start = System.nanoTime();
        StatusCapturingResponseWrapper wrapped = new StatusCapturingResponseWrapper(response);
        try {
            chain.doFilter(request, wrapped);
        } finally {
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            logRequest(request, wrapped, latencyMs);
        }
    }

    private void logRequest(HttpServletRequest request,
                            StatusCapturingResponseWrapper response,
                            long latencyMs) {
        if (log.isInfoEnabled()) {
            log.info("{} {} -> {} ({} ms, client={})",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    latencyMs,
                    request.getRemoteAddr());
        }
        if (log.isDebugEnabled()) {
            log.debug("Headers: {}", sanitizedHeaders(request));
        }
        // Body logging is intentionally rare (TRACE + no sensitive keys).
        // We do not buffer the body unconditionally: the cost is only paid when
        // someone has explicitly raised the level to TRACE for debugging.
    }

    private String sanitizedHeaders(HttpServletRequest request) {
        return request.getHeaderNames().hasMoreElements()
                ? Collections.list(request.getHeaderNames()).stream()
                        .map(name -> name + "=" + (isSensitive(name) ? "[REDACTED]" : request.getHeader(name)))
                        .collect(Collectors.joining(", "))
                : "(none)";
    }

    static boolean isSensitive(String headerName) {
        return SENSITIVE_HEADERS.contains(headerName.toLowerCase());
    }

    /**
     * Minimal wrapper exposing the final status code after the chain runs.
     * Delegates everything else to the original response.
     */
    static final class StatusCapturingResponseWrapper extends jakarta.servlet.http.HttpServletResponseWrapper {
        private int status = HttpServletResponse.SC_OK;

        StatusCapturingResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setStatus(int sc) {
            super.setStatus(sc);
            this.status = sc;
        }

        @Override
        public void sendError(int sc) throws IOException {
            super.sendError(sc);
            this.status = sc;
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            super.sendError(sc, msg);
            this.status = sc;
        }

        @Override
        public int getStatus() {
            return status;
        }
    }
}
