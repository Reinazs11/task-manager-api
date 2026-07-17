package com.renan.taskmanager.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Stamps every request with a correlation id and exposes it both downstream
 * (in the {@link MDC}, so every log line carries it) and to the client
 * (in the {@code X-Request-Id} response header).
 *
 * <p><b>Why a correlation id?</b>
 * In production a single user action may fan out across many log lines,
 * threads, and downstream services. Without a stable id, tracing which lines
 * belong to which request is guesswork. With {@code requestId} in the MDC, the
 * Logback pattern ({@code %X{requestId}}) prints it on every line, and support
 * can grep one id to see the full request lifecycle.</p>
 *
 * <p><b>Why accept a client-supplied id?</b>
 * Load balancers and upstream services (API gateway, front-end) often generate
 * their own id. Honoring an incoming {@code X-Request-Id} keeps the chain
 * continuous end-to-end. When absent, we mint one so the request is never
 * "anonymous".</p>
 *
 * <p><b>Why {@code MDC.remove} in finally?</b>
 * Servlet containers pool threads. If a request left a value in the MDC, the
 * next request reusing that thread would inherit it — cross-request log
 * contamination and potential information leakage. The {@code finally} block
 * guarantees cleanup even on exception.</p>
 *
 * <p><b>Filter ordering:</b> runs first ({@link Ordered#HIGHEST_PRECEDENCE}), so
 * every downstream filter and controller sees the MDC populated. The logging
 * filter runs after it (lower precedence) so it can include the id in its own
 * output.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    /** Header name shared between request (inbound) and response (outbound). */
    public static final String HEADER_NAME = "X-Request-Id";

    /** Key under which the id is stored in the {@link MDC}. */
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        try {
            MDC.put(MDC_KEY, requestId);
            response.setHeader(HEADER_NAME, requestId);
            chain.doFilter(request, response);
        } finally {
            // Thread pools reuse threads: never leak the previous request's id.
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * Uses the client-supplied id when present and non-blank; otherwise mints a
     * random UUID. Trims to avoid accidental whitespace injection from proxies.
     */
    private String resolveRequestId(HttpServletRequest request) {
        String inbound = request.getHeader(HEADER_NAME);
        if (inbound != null && !inbound.isBlank()) {
            return inbound.trim();
        }
        return UUID.randomUUID().toString();
    }
}
