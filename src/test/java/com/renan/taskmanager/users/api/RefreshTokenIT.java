package com.renan.taskmanager.users.api;

import com.renan.taskmanager.common.AbstractIntegrationTest;
import com.renan.taskmanager.common.security.JwtService;
import com.renan.taskmanager.users.domain.UserRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code POST /api/v1/auth/refresh} — the endpoint that
 * trades a refresh token for a fresh access+refresh pair.
 *
 * <p><b>Why a dedicated IT?</b>
 * Token rotation crosses the same boundaries as login (HTTP → controller →
 * use case → {@code JwtService}), and the failure modes (wrong token type,
 * tampered token, missing token) only surface through the real JwtException
 * path. MockMvc with a full {@code @SpringBootTest} is the only honest way
 * to exercise this end-to-end.</p>
 *
 * <p><b>Why stateless rotation (no server-side blacklist)?</b>
 * Without a token store we cannot invalidate a refresh token the moment it
 * is exchanged; both the old and the new refresh remain valid until the
 * older one expires. This is an accepted trade-off of the stateless design
 * (same shape as access tokens) and is documented on {@code RefreshTokenUseCase}.</p>
 */
class RefreshTokenIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    private static final String VALID_EMAIL = "renan@example.com";
    private static final String VALID_PASSWORD = "Password123";
    private static final String REFRESH_URI = "/api/v1/auth/refresh";

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    @Nested
    @DisplayName("POST /api/v1/auth/refresh with a valid refresh token")
    class HappyPath {

        @Test
        @DisplayName("Should return 200 with a NEW access and refresh token")
        void shouldRotateTokens() throws Exception {
            Map<String, String> tokens = registerAndLogin(VALID_EMAIL, VALID_PASSWORD);

            Map<String, Object> body = Map.of("refreshToken", tokens.get("refreshToken"));

            MvcResult result = mockMvc.perform(post(REFRESH_URI)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(900))
                    .andExpect(jsonPath("$.refreshExpiresIn").value(604800))
                    .andReturn();

            String responseJson = result.getResponse().getContentAsString();
            String newAccess = objectMapper.readTree(responseJson).get("accessToken").asText();
            String newRefresh = objectMapper.readTree(responseJson).get("refreshToken").asText();

            // Rotation: the new refresh MUST differ from the old one (jti differs).
            assertThat(newRefresh).isNotEqualTo(tokens.get("refreshToken"));

            // The new access token must be valid and carry type=access.
            Claims claims = jwtService.parseAndValidate(newAccess);
            assertThat(jwtService.extractTokenType(claims)).isEqualTo(JwtService.TYPE_ACCESS);

            // The original refresh token's claims equal the new access's subject.
            Claims oldClaims = jwtService.parseAndValidate(tokens.get("refreshToken"));
            assertThat(claims.getSubject()).isEqualTo(oldClaims.getSubject());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/refresh with an invalid token")
    class InvalidTokens {

        @Test
        @DisplayName("Access token used as refresh → 401")
        void shouldRejectAccessTokenAsRefresh() throws Exception {
            Map<String, String> tokens = registerAndLogin(VALID_EMAIL, VALID_PASSWORD);

            Map<String, Object> body = Map.of("refreshToken", tokens.get("accessToken"));

            mockMvc.perform(post(REFRESH_URI)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Tampered refresh token → 401")
        void shouldRejectTamperedRefreshToken() throws Exception {
            Map<String, String> tokens = registerAndLogin(VALID_EMAIL, VALID_PASSWORD);
            String tampered = tamperPayload(tokens.get("refreshToken"));

            Map<String, Object> body = Map.of("refreshToken", tampered);

            mockMvc.perform(post(REFRESH_URI)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Random non-JWT string → 401")
        void shouldRejectNonJwtString() throws Exception {
            Map<String, Object> body = Map.of("refreshToken", "this-is-not-a-jwt");

            mockMvc.perform(post(REFRESH_URI)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Missing refreshToken field → 400")
        void shouldReturn400WhenRefreshTokenMissing() throws Exception {
            mockMvc.perform(post(REFRESH_URI)
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

    /**
     * Tamper the JWT payload (middle segment) by mutating one character.
     * The signature check will fail, producing a JwtException.
     */
    private static String tamperPayload(String jwt) {
        String[] parts = jwt.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Not a JWT: " + jwt);
        }
        char[] payload = parts[1].toCharArray();
        payload[0] = (payload[0] == 'A') ? 'B' : 'A';
        parts[1] = new String(payload);
        return parts[0] + "." + parts[1] + "." + parts[2];
    }
}
