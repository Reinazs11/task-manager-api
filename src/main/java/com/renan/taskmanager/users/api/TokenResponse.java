package com.renan.taskmanager.users.api;

/**
 * Response payload for successful authentication flows (login, refresh).
 *
 * <p>Clients send the {@code accessToken} on every subsequent request as
 * {@code Authorization: Bearer <accessToken>}. When it expires, they use the
 * {@code refreshToken} to obtain a new pair (refresh endpoint, future work).</p>
 *
 * <p><b>Expires-in fields</b> let clients schedule proactive refresh before
 * expiry, avoiding mid-request 401s.</p>
 *
 * @param accessToken       short-lived JWT (15 min) for API access
 * @param refreshToken      long-lived JWT (7 days) to mint new access tokens
 * @param tokenType         always "Bearer" (RFC 6750)
 * @param expiresIn         access token TTL in seconds (15 min = 900)
 * @param refreshExpiresIn  refresh token TTL in seconds (7 days = 604800)
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        long refreshExpiresIn
) {
    public static TokenResponse of(String accessToken, String refreshToken,
                                   long accessTtlMs, long refreshTtlMs) {
        return new TokenResponse(
                accessToken,
                refreshToken,
                "Bearer",
                accessTtlMs / 1000,
                refreshTtlMs / 1000
        );
    }
}
