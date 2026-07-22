package com.renan.taskmanager.users.api;

import com.renan.taskmanager.common.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the auth-endpoint rate limiter ({@code RateLimitFilter}).
 *
 * <p>Verifies the security control end-to-end: a per-IP token bucket on the auth
 * endpoints returns 429 with the standard six-field {@code ErrorResponse} once
 * the configured capacity is exceeded, and leaves other paths untouched.</p>
 *
 * <p><b>Isolation:</b> each test sends a distinct {@code X-Forwarded-For} so its
 * bucket is independent. {@code @TestPropertySource} enables XFF trust for this
 * class only (default is false) — that creates a separate cached ApplicationContext
 * from the rest of the suite, which is exactly what we want here.</p>
 *
 * <p><b>Capacity assumption:</b> the dev profile configures 10 requests per
 * minute per IP (see {@code application.yml}). These tests depend on that
 * default; if it changes, the burst counts here must change too.</p>
 */
@TestPropertySource(properties = "app.rate-limit.trust-forwarded-for=true")
class AuthRateLimitIT extends AbstractIntegrationTest {

    private static final int AUTH_CAPACITY = 10;

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String REFRESH_PATH = "/api/v1/auth/refresh";

    /**
     * Body that always fails validation by the use case (wrong password) but
     * passes Bean Validation, so the request still reaches the filter+controller
     * and consumes a token. We are testing the limiter, not the credentials.
     */
    private static final Map<String, Object> LOGIN_BODY =
            Map.of("email", "anyone@example.com", "password", "WrongPassword123");

    /**
     * Trivial refresh body. The token is invalid, so the use case will reject
     * it; what matters is that the request was counted by the limiter.
     */
    private static final Map<String, Object> REFRESH_BODY =
            Map.of("refreshToken", "not.a.real.token");

    private String loginJson() throws Exception {
        return objectMapper.writeValueAsString(LOGIN_BODY);
    }

    private String refreshJson() throws Exception {
        return objectMapper.writeValueAsString(REFRESH_BODY);
    }

    @Nested
    @DisplayName("Rate limit on /auth/login")
    class LoginRateLimit {

        @Test
        @DisplayName("Should return 429 once the per-IP capacity is exceeded")
        void shouldReturn429AfterCapacityExceeded() throws Exception {
            String ip = "203.0.113.10";

            // First AUTH_CAPACITY requests are admitted (here they fail login → 401,
            // but the limiter let them through).
            for (int i = 0; i < AUTH_CAPACITY; i++) {
                mockMvc.perform(post(LOGIN_PATH)
                                .header("X-Forwarded-For", ip)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginJson()))
                        .andExpect(status().isUnauthorized());
            }

            // The next request is rejected by the limiter before reaching the controller.
            mockMvc.perform(post(LOGIN_PATH)
                            .header("X-Forwarded-For", ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson()))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(429))
                    .andExpect(jsonPath("$.error").value("Too Many Requests"))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.path").value(LOGIN_PATH))
                    .andExpect(jsonPath("$.details").isArray())
                    .andExpect(header().exists("Retry-After"));
        }

        @Test
        @DisplayName("Should not interfere with a different source IP")
        void shouldNotInterfereWithDifferentIp() throws Exception {
            String exhaustedIp = "203.0.113.20";
            String freshIp = "198.51.100.20";

            for (int i = 0; i < AUTH_CAPACITY; i++) {
                mockMvc.perform(post(LOGIN_PATH)
                        .header("X-Forwarded-For", exhaustedIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson()));
            }
            mockMvc.perform(post(LOGIN_PATH)
                            .header("X-Forwarded-For", exhaustedIp)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson()))
                    .andExpect(status().isTooManyRequests());

            // A different IP still gets its full bucket.
            mockMvc.perform(post(LOGIN_PATH)
                            .header("X-Forwarded-For", freshIp)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Shared bucket across /auth/login and /auth/refresh")
    class SharedBucket {

        @Test
        @DisplayName("Should count /login and /refresh against the same per-IP bucket")
        void shouldShareBucketBetweenLoginAndRefresh() throws Exception {
            String ip = "203.0.113.30";

            // Split the burst across both endpoints.
            int loginCalls = 5;
            for (int i = 0; i < loginCalls; i++) {
                mockMvc.perform(post(LOGIN_PATH)
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson()));
            }
            int refreshCalls = AUTH_CAPACITY - loginCalls; // 5
            for (int i = 0; i < refreshCalls; i++) {
                mockMvc.perform(post(REFRESH_PATH)
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson()));
            }

            // Bucket is now exhausted regardless of which endpoint the next hit uses.
            mockMvc.perform(post(REFRESH_PATH)
                            .header("X-Forwarded-For", ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(refreshJson()))
                    .andExpect(status().isTooManyRequests());
        }
    }

    @Nested
    @DisplayName("Non-auth paths are not rate-limited")
    class NonAuthPaths {

        @Test
        @DisplayName("Should not apply the limiter to endpoints outside /auth/**")
        void shouldNotRateLimitNonAuthPaths() throws Exception {
            String ip = "203.0.113.40";

            // Exhaust the auth bucket for this IP — non-auth traffic must be unaffected.
            for (int i = 0; i < AUTH_CAPACITY + 2; i++) {
                mockMvc.perform(post(LOGIN_PATH)
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson()));
            }

            // A protected endpoint (requires auth) returns 401/403 from Spring Security,
            // never 429 — the limiter did not engage.
            mockMvc.perform(get("/api/v1/projects")
                            .header("X-Forwarded-For", ip))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").exists());
        }
    }
}
