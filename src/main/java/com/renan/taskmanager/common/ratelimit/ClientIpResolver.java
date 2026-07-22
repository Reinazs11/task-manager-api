package com.renan.taskmanager.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Extracts the originating client IP from a request, for use as the
 * rate-limit bucket key.
 *
 * <p><b>Why conditionally trust {@code X-Forwarded-For}?</b> When the API runs
 * behind a reverse proxy or load balancer (nginx, Fly.io, Railway, AWS ALB),
 * {@link HttpServletRequest#getRemoteAddr()} returns the proxy's IP, not the
 * client's. Those proxies append the real client chain to the
 * {@code X-Forwarded-For} header, left-to-right: {@code client, proxy1, proxy2}.
 * The first entry is the originating client.</p>
 *
 * <p><b>But the header is trivially spoofable</b> unless the fronting proxy is
 * configured to <em>overwrite</em> (not append to) it. So trusting XFF is opt-in
 * via {@code app.rate-limit.trust-forwarded-for}. When disabled (the default),
 * the resolver uses the raw socket {@code getRemoteAddr()} — which a direct
 * attacker cannot fake — and rate limiting stays effective even without a
 * proxy. Enable it only behind a proxy known to sanitize the header.</p>
 */
public final class ClientIpResolver {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private final boolean trustForwardedFor;

    /**
     * @param trustForwardedFor whether to honor {@code X-Forwarded-For}. Only
     *                          enable behind a proxy that overwrites this header.
     */
    public ClientIpResolver(boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }

    /**
     * Resolves the client IP for the given request.
     *
     * @param request the incoming servlet request
     * @return when XFF trust is enabled, the first hop of
     *         {@code X-Forwarded-For} if present and non-blank; otherwise (and
     *         always when trust is disabled) the raw socket remote address
     */
    public String resolve(HttpServletRequest request) {
        if (trustForwardedFor) {
            String header = request.getHeader(FORWARDED_FOR_HEADER);
            if (header != null && !header.isBlank()) {
                String firstHop = header.split(",")[0].trim();
                if (!firstHop.isEmpty()) {
                    return firstHop;
                }
            }
        }
        return request.getRemoteAddr();
    }
}
