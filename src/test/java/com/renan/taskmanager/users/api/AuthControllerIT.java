package com.renan.taskmanager.users.api;

import com.renan.taskmanager.common.AbstractIntegrationTest;
import com.renan.taskmanager.common.security.JwtService;
import com.renan.taskmanager.users.domain.Email;
import com.renan.taskmanager.users.domain.UserRepository;
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
 * Integration tests for {@link AuthController} covering the full HTTP stack:
 * MockMvc → DispatcherServlet → Controller → UseCase → Repository → PostgreSQL.
 *
 * <p><b>Why @SpringBootTest and not @WebMvcTest?</b>
 * {@code @WebMvcTest} only loads the web slice; we need the whole context
 * (Spring Security, JPA, use cases, mappers). {@code @SpringBootTest} boots
 * everything, and {@code @AutoConfigureMockMvc} gives us MockMvc on top.</p>
 *
 * <p><b>Why Testcontainers here?</b>
 * The flow hits the database. We use real PostgreSQL so we catch dialect-
 * specific bugs (UUID columns, unique constraints) that H2 would hide.</p>
 */
class AuthControllerIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    private static final String VALID_EMAIL = "renan@example.com";
    private static final String VALID_PASSWORD = "Password123";

    /**
     * Wipes all users before each test to guarantee isolation.
     *
     * <p><b>Why not @Transactional on the test?</b>
     * MockMvc dispatches through the real servlet stack, so HTTP-driven writes
     * commit in their own transactions and aren't rolled back by the test's
     * transaction. A direct deleteAll() is deterministic and simple.</p>
     */
    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    @Nested
    @DisplayName("POST /api/v1/auth/register")
    class Register {

        @Test
        @DisplayName("Should return 201 and the created user on valid input")
        void shouldReturnCreatedOnValidInput() throws Exception {
            Map<String, Object> body = Map.of(
                    "email", VALID_EMAIL,
                    "password", VALID_PASSWORD,
                    "name", "Renan"
            );

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.email").value(VALID_EMAIL))
                    .andExpect(jsonPath("$.name").value("Renan"));

            // Side effect: user should be persisted
            assertThat(userRepository.findByEmail(new Email(VALID_EMAIL)))
                    .isPresent();
        }

        @Test
        @DisplayName("Should return 201 even without the optional name field")
        void shouldSucceedWithoutName() throws Exception {
            Map<String, Object> body = Map.of(
                    "email", "noname@example.com",
                    "password", VALID_PASSWORD
            );

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").doesNotExist());
        }

        @Test
        @DisplayName("Should return 400 when email is invalid")
        void shouldReturn400WhenEmailIsInvalid() throws Exception {
            Map<String, Object> body = Map.of(
                    "email", "not-an-email",
                    "password", VALID_PASSWORD
            );

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when password is too short")
        void shouldReturn400WhenPasswordIsTooShort() throws Exception {
            Map<String, Object> body = Map.of(
                    "email", "short@example.com",
                    "password", "Ab1"  // fails the DTO @Size(min=8)
            );

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 409 when email is already registered")
        void shouldReturn409WhenEmailAlreadyRegistered() throws Exception {
            // First registration succeeds
            registerUser(VALID_EMAIL, VALID_PASSWORD);

            // Second registration with same email should conflict
            Map<String, Object> body = Map.of(
                    "email", VALID_EMAIL,
                    "password", VALID_PASSWORD
            );

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class Login {

        @Test
        @DisplayName("Should return 200 with tokens on valid credentials")
        void shouldReturnTokensOnValidCredentials() throws Exception {
            registerUser(VALID_EMAIL, VALID_PASSWORD);

            Map<String, Object> body = Map.of(
                    "email", VALID_EMAIL,
                    "password", VALID_PASSWORD
            );

            MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(900))   // 15 min in seconds
                    .andExpect(jsonPath("$.refreshExpiresIn").value(604800))  // 7 days
                    .andReturn();

            // The access token should be valid per JwtService
            String accessToken = objectMapper.readTree(result.getResponse().getContentAsString())
                    .get("accessToken").asText();
            var claims = jwtService.parseAndValidate(accessToken);
            assertThat(jwtService.extractTokenType(claims)).isEqualTo(JwtService.TYPE_ACCESS);
        }

        @Test
        @DisplayName("Should return 401 on wrong password")
        void shouldReturn401OnWrongPassword() throws Exception {
            registerUser(VALID_EMAIL, VALID_PASSWORD);

            Map<String, Object> body = Map.of(
                    "email", VALID_EMAIL,
                    "password", "WrongPassword123"
            );

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 401 when email does not exist")
        void shouldReturn401WhenEmailDoesNotExist() throws Exception {
            Map<String, Object> body = Map.of(
                    "email", "ghost@example.com",
                    "password", VALID_PASSWORD
            );

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isUnauthorized());
        }
    }

    /**
     * Helper: registers a user via the API, used to seed state for login tests.
     */
    private void registerUser(String email, String password) throws Exception {
        Map<String, Object> body = Map.of("email", email, "password", password);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }
}
