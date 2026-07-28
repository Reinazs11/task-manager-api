package com.renan.taskmanager.common.api;

import com.renan.taskmanager.common.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the {@code /actuator/prometheus} metrics endpoint.
 *
 * <p><b>Why this test exists:</b> unlike {@code /actuator/health} (public —
 * Docker/K8s probe), Prometheus metrics are <b>JWT-protected</b> (see
 * DECISIONS.md #19). Two contracts must hold: (1) an unauthenticated request
 * is rejected with 401 in the standard six-field ErrorResponse shape, and
 * (2) an authenticated request gets back the Prometheus text exposition format
 * carrying the meters a reviewer expects to see. Both are asserted here.</p>
 */
class ActuatorPrometheusIT extends AbstractIntegrationTest {

    private static final String VALID_EMAIL = "metrics-test@example.com";
    private static final String VALID_PASSWORD = "Password123";

    @Nested
    @DisplayName("Without authentication")
    class Unauthenticated {

        @Test
        @DisplayName("GET /actuator/prometheus without token returns 401 in the standard ErrorResponse shape")
        void shouldRejectWithoutToken() throws Exception {
            mockMvc.perform(get("/actuator/prometheus"))
                    .andExpect(status().isUnauthorized())
                    // 401 must share the 6-field ErrorResponse shape produced by
                    // GlobalExceptionHandler, so clients can rely on one contract.
                    .andExpect(jsonPath("$.timestamp").isNotEmpty())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andExpect(jsonPath("$.message").value("Authentication is required"))
                    .andExpect(jsonPath("$.path").value("/actuator/prometheus"))
                    .andExpect(jsonPath("$.details").isArray())
                    .andExpect(jsonPath("$.details.length()").value(0));
        }
    }

    @Nested
    @DisplayName("With a valid access token")
    class Authenticated {

        @Test
        @DisplayName("GET /actuator/prometheus with token returns 200 and Prometheus text exposition")
        void shouldExposeMetricsWhenAuthenticated() throws Exception {
            String accessToken = registerAndLogin();

            String body = mockMvc.perform(get("/actuator/prometheus")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            // The HTTP server request timer is registered as soon as a request is
            // served by Spring MVC. The login above guarantees at least one served
            // request, so the meter MUST be present — this also proves the registry
            // is wired (a missing dependency yields an empty or 404 response).
            assertThatPrometheusBodyHasMeter(body, "http_server_requests_seconds");

            // The custom login counter (LoginUseCase) is incremented on the
            // registerAndLogin() above, so its success-tagged sample MUST appear.
            // Asserting the tag value binds the metric name + tag contract.
            assertThatPrometheusBodyHasMeter(body, "auth_login_attempts_total{result=\"success\"}");
        }
    }

    /**
     * Asserts the Prometheus exposition body contains at least one sample line
     * for the given meter name (help line or sample). Resilient to the
     * underscore-vs-dot naming Micrometer applies when bridging to Prometheus.
     */
    private static void assertThatPrometheusBodyHasMeter(String body, String meterName) {
        if (!body.contains(meterName)) {
            throw new AssertionError(
                    "Expected Prometheus exposition to contain meter '" + meterName
                            + "' but it was absent. Body was:\n" + body);
        }
    }

    /**
     * Registers a user via the API and logs in to obtain a valid access token.
     * Mirrors the helper in {@code JwtAuthorizationIT} so this test exercises
     * the real register → login → JWT path rather than minting a token directly.
     */
    private String registerAndLogin() throws Exception {
        registerUser();
        Map<String, Object> body = Map.of("email", VALID_EMAIL, "password", VALID_PASSWORD);
        var result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private void registerUser() throws Exception {
        Map<String, Object> body = Map.of(
                "email", VALID_EMAIL,
                "password", VALID_PASSWORD,
                "name", "Metrics Test User"
        );
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }
}
