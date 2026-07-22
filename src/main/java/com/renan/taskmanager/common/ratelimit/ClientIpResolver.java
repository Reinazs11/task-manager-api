package com.renan.taskmanager.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Extracts the originating client IP from a request, for use as the
 * rate-limit bucket key.
 *
 * <p><b>Why prefer {@code X-Forwarded-For}?</b> When the API runs behind a
 * reverse proxy or load balancer (nginx, Fly.io, Railway, AWS ALB),
 * {@link HttpServletRequest#getRemoteAddr()} returns the proxy's IP, not the
 * client's. Those proxies append the real client chain to the
 * {@code X-Forwarded-For} header, left-to-right: {@code client, proxy1, proxy2}.
 * The first entry is the originating client — that's the one we throttle.</p>
 *
 * <p><b>Security note:</b> we trust this header as-is. A spoofed XFF would let
 * an attacker rotate the apparent source IP and dodge the limit. For the
 * portfolio single-instance deployment this is acceptable and documented in
 * {@code DECISIONS.md}; a hardened deployment would configure the proxy to
 * overwrite (not append to) XFF, or validate it against a trusted proxy list.</p>
 */
public final class ClientIpResolver {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    /**
     * Resolves the client IP for the given request.
     *
     * @param request the incoming servlet request
     * @return the first hop of {@code X-Forwarded-For} when present and non-blank,
     *         otherwise the raw socket remote address
     */
    public String resolve(HttpServletRequest request) {
        String header = request.getHeader(FORWARDED_FOR_HEADER);
        if (header != null && !header.isBlank()) {
            String firstHop = header.split(",")[0].trim();
            if (!firstHop.isEmpty()) {
                return firstHop;
            }
        }
        return request.getRemoteAddr();
    }
}
