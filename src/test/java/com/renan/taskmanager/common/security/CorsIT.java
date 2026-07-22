package com.renan.taskmanager.common.security;

import com.renan.taskmanager.common.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the CORS configuration.
 *
 * <p><b>Why a dedicated IT?</b>
 * CORS is enforced by Spring Security's filter chain before any controller
 * runs. Slicing with {@code @WebMvcTest} would skip the chain and miss the
 * real behavior. Only a full {@code @SpringBootTest} exercises the
 * {@code CorsConfigurationSource} bean plus the {@code .cors(...)} wiring.</p>
 *
 * <p><b>What we assert:</b>
 * <ul>
 *   <li>Preflight ({@code OPTIONS}) from an allowed origin returns 200 with
 *       the expected {@code Access-Control-Allow-*} headers.</li>
 *   <li>A request from a non-allowed origin receives no
 *       {@code Access-Control-Allow-Origin} header (Spring rejects CORS).</li>
 * </ul>
 */
class CorsIT extends AbstractIntegrationTest {

    private static final String REGISTER_URI = "/api/v1/auth/register";

    @Nested
    @DisplayName("CORS preflight from an allowed origin")
    class AllowedOrigin {

        @Test
        @DisplayName("OPTIONS from http://localhost:3000 returns 200 with CORS headers")
        void shouldAllowPreflightFromAllowedOrigin() throws Exception {
            mockMvc.perform(options(REGISTER_URI)
                            .header("Origin", "http://localhost:3000")
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "Content-Type"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                    .andExpect(header().exists("Access-Control-Allow-Methods"))
                    .andExpect(header().exists("Access-Control-Allow-Headers"));
        }
    }

    @Nested
    @DisplayName("CORS preflight from a non-allowed origin")
    class DisallowedOrigin {

        @Test
        @DisplayName("OPTIONS from https://evil.example returns no Access-Control-Allow-Origin")
        void shouldRejectPreflightFromDisallowedOrigin() throws Exception {
            mockMvc.perform(options(REGISTER_URI)
                            .header("Origin", "https://evil.example")
                            .header("Access-Control-Request-Method", "POST"))
                    .andExpect(result -> {
                        String allowOrigin = result.getResponse().getHeader("Access-Control-Allow-Origin");
                        if (allowOrigin != null) {
                            throw new AssertionError(
                                    "Expected no Access-Control-Allow-Origin for disallowed origin, got: "
                                            + allowOrigin);
                        }
                    });
        }
    }

    @Nested
    @DisplayName("CORS headers on actual (non-preflight) requests")
    class ActualRequest {

        @Test
        @DisplayName("POST from allowed origin echoes Access-Control-Allow-Origin")
        void shouldEchoAllowOriginOnActualRequest() throws Exception {
            mockMvc.perform(post(REGISTER_URI)
                            .header("Origin", "http://localhost:3000")
                            .contentType("application/json")
                            .content("{\"email\":\"cors@example.com\",\"password\":\"Password123\",\"name\":\"CORS\"}"))
                    .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
        }
    }
}
