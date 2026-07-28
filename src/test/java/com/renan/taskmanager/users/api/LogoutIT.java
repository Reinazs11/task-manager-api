package com.renan.taskmanager.users.api;

import com.renan.taskmanager.common.AbstractIntegrationTest;
import com.renan.taskmanager.common.security.JwtService;
import com.renan.taskmanager.users.domain.RevokedRefreshTokenRepository;
import com.renan.taskmanager.users.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code POST /api/v1/auth/logout} — the endpoint that
 * revokes a refresh token server-side.
 *
 * <p><b>Why a dedicated IT?</b>
 * Logout touches HTTP → controller → use case → JwtService →
 * {@link RevokedRefreshTokenRepository}, and the security-relevant behavior
 * (post-logout refresh is rejected) only proves itself through the full stack.
 * The same reasoning as {@link RefreshTokenIT} applies: MockMvc with a real
 * {@code @SpringBootTest} is the only honest way to exercise this.</p>
 *
 * <p><b>Anti-enumeration regression:</b> a forged token and a real-but-revoked
 * token must both return the same 401 shape. That contract is checked here at
 * the HTTP level, complementing the unit-level check in
 * {@code RefreshTokenUseCaseTest}.</p>
 */
class LogoutIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RevokedRefreshTokenRepository revokedTokenRepository;

    @Autowired
    private JwtService jwtService;

    private static final String VALID_EMAIL = "renan@example.com";
    private static final String VALID_PASSWORD = "Password123";
    private static final String LOGOUT_URI = "/api/v1/auth/logout";

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();  // FK CASCADE clears revoked_refresh_tokens too
        revokedTokenRepository.deleteAll();
    }

    @Nested
    @DisplayName("POST /api/v1/auth/logout with a valid refresh token")
    class HappyPath {

        @Test
        @DisplayName("Should return 204 No Content")
        void shouldReturn204() throws Exception {
            String refresh = registerAndLogin(VALID_EMAIL, VALID_PASSWORD).get("refreshToken");

            mockMvc.perform(post(LOGOUT_URI)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("refreshToken", refresh))))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("After logout, the revoked refresh token returns 401 on /auth/refresh")
        void revokedRefreshTokenShouldFailAfterLogout() throws Exception {
            // Regression for the security contract: logout must make the
            // refresh token unusable, not just locally forgotten.
            String refresh = registerAndLogin(VALID_EMAIL, VALID_PASSWORD).get("refreshToken");

            mockMvc.perform(post(LOGOUT_URI)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("refreshToken", refresh))))
                    .andExpect(status().isNoContent());

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("refreshToken", refresh))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.path").exists());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/logout idempotency")
    class Idempotency {

        @Test
        @DisplayName("Calling logout twice with the same token returns 204 both times")
        void shouldBeIdempotent() throws Exception {
            String refresh = registerAndLogin(VALID_EMAIL, VALID_PASSWORD).get("refreshToken");
            Map<String, Object> body = Map.of("refreshToken", refresh);

            mockMvc.perform(post(LOGOUT_URI)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isNoContent());

            mockMvc.perform(post(LOGOUT_URI)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/logout with an invalid token")
    class InvalidToken {

        @Test
        @DisplayName("Refresh token never issued (signed with a different key) → 401")
        void shouldRejectForgedRefreshToken() throws Exception {
            // Minted with a different secret, so the JwtService parser rejects
            // it. The 401 shape must be identical to a revoked token — callers
            // must not learn "this token is forged" vs "this token was valid
            // once but is now revoked".
            JwtService rogue = new JwtService(
                    "another-32-byte-secret-key-for-testing-Ok!!!",
                    60_000L, 3_600_000L, "task-manager-api", "task-manager-api-users");
            String forged = rogue.generateRefreshToken(java.util.UUID.randomUUID(), VALID_EMAIL);

            mockMvc.perform(post(LOGOUT_URI)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("refreshToken", forged))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        @DisplayName("Access token used as refresh → 401 (type check)")
        void shouldRejectAccessToken() throws Exception {
            String access = registerAndLogin(VALID_EMAIL, VALID_PASSWORD).get("accessToken");

            mockMvc.perform(post(LOGOUT_URI)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("refreshToken", access))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Random non-JWT string → 401")
        void shouldRejectNonJwtString() throws Exception {
            mockMvc.perform(post(LOGOUT_URI)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("refreshToken", "this-is-not-a-jwt"))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Missing refreshToken field → 400")
        void shouldReturn400WhenRefreshTokenMissing() throws Exception {
            mockMvc.perform(post(LOGOUT_URI)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private Map<String, String> registerAndLogin(String email, String password) throws Exception {
        Map<String, Object> reg = Map.of("email", email, "password", password);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isOk())
                .andReturn();

        String json = loginResult.getResponse().getContentAsString();
        return Map.of(
                "accessToken", objectMapper.readTree(json).get("accessToken").asText(),
                "refreshToken", objectMapper.readTree(json).get("refreshToken").asText()
        );
    }
}
