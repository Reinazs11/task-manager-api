package com.renan.taskmanager.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.renan.taskmanager.users.api.LoginRequest;
import com.renan.taskmanager.users.api.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization integration tests: verify that the JWT security infrastructure
 * actually enforces authentication on protected endpoints.
 *
 * <p>This complements {@code AuthControllerIntegrationTest} (which covers the
 * auth flows themselves) by testing the OTHER side: what happens when a
 * protected resource is accessed with various token states.</p>
 *
 * <p><b>Why this matters:</b>
 * Without these tests, a bug in {@link JwtAuthenticationFilter} or
 * {@link SecurityConfig} (e.g. a misconfigured route, a swallowed exception)
 * could go unnoticed until production. Security tests are first-class.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class JwtAuthorizationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("taskmanager_test")
            .withUsername("test")
            .withPassword("test");

    private static final String SECURE_URL = "/api/v1/test/secure";
    private static final String VALID_EMAIL = "auth-test@example.com";
    private static final String VALID_PASSWORD = "Password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private com.renan.taskmanager.users.domain.UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    @Nested
    @DisplayName("Protected endpoint access")
    class ProtectedEndpoint {

        @Test
        @DisplayName("Should return 401-403 when no token is provided")
        void shouldRejectWithoutToken() throws Exception {
            mockMvc.perform(get(SECURE_URL))
                    .andExpect(status().isUnauthorized())
                    // 401 must share the 6-field ErrorResponse shape produced by
                    // GlobalExceptionHandler, so clients can rely on one contract.
                    .andExpect(jsonPath("$.timestamp").isNotEmpty())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andExpect(jsonPath("$.message").value("Authentication is required"))
                    .andExpect(jsonPath("$.path").value(SECURE_URL))
                    .andExpect(jsonPath("$.details").isArray())
                    .andExpect(jsonPath("$.details.length()").value(0));
        }

        @Test
        @DisplayName("Should return 200 when a valid access token is provided")
        void shouldAcceptValidAccessToken() throws Exception {
            String accessToken = registerAndLogin();

            mockMvc.perform(get(SECURE_URL)
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(content().string("authenticated"));
        }

        @Test
        @DisplayName("Should reject when token has no Bearer prefix")
        void shouldRejectWithoutBearerPrefix() throws Exception {
            String accessToken = registerAndLogin();

            mockMvc.perform(get(SECURE_URL)
                            .header("Authorization", accessToken))  // no "Bearer "
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should reject when a refresh token is used for access")
        void shouldRejectRefreshTokenForAccess() throws Exception {
            // Register a user and issue a refresh token directly
            registerUser();
            String refreshToken = jwtService.generateRefreshToken(
                    java.util.UUID.randomUUID(), VALID_EMAIL);

            mockMvc.perform(get(SECURE_URL)
                            .header("Authorization", "Bearer " + refreshToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should reject a tampered token")
        void shouldRejectTamperedToken() throws Exception {
            String accessToken = registerAndLogin();
            // Flip the last character of the signature
            char last = accessToken.charAt(accessToken.length() - 1);
            char replacement = (last == 'A') ? 'B' : 'A';
            String tampered = accessToken.substring(0, accessToken.length() - 1) + replacement;

            mockMvc.perform(get(SECURE_URL)
                            .header("Authorization", "Bearer " + tampered))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should reject a malformed (non-JWT) string")
        void shouldRejectMalformedToken() throws Exception {
            mockMvc.perform(get(SECURE_URL)
                            .header("Authorization", "Bearer not-a-jwt"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should reject a token signed with a different key")
        void shouldRejectTokenFromDifferentIssuer() throws Exception {
            // A separate JwtService with a different secret signs the token
            JwtService rogue = new JwtService(
                    "another-secret-with-at-least-32-bytes-for-hs256-Ok!!!",
                    60_000L, 3_600_000L);
            String foreignToken = rogue.generateAccessToken(
                    java.util.UUID.randomUUID(), "attacker@example.com");

            mockMvc.perform(get(SECURE_URL)
                            .header("Authorization", "Bearer " + foreignToken))
                    .andExpect(status().isUnauthorized());
        }
    }

    /**
     * Registers a user via the API and logs in to obtain a valid access token.
     */
    private String registerAndLogin() throws Exception {
        registerUser();
        Map<String, Object> body = Map.of("email", VALID_EMAIL, "password", VALID_PASSWORD);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
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
                "name", "Test User"
        );
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }
}
