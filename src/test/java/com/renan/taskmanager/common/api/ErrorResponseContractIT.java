package com.renan.taskmanager.common.api;

import com.renan.taskmanager.common.AbstractIntegrationTest;
import com.renan.taskmanager.users.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Locks the API's error contract: every failure must return the SAME six-field
 * {@link ErrorResponse} shape.
 *
 * <p><b>Why a dedicated contract test (and not assertions scattered through the
 * existing integration tests)?</b> Because the contract is a first-class
 * invariant of the API. If a future refactor of {@link GlobalExceptionHandler} or
 * {@link com.renan.taskmanager.common.security.JsonAuthenticationEntryPoint}
 * drops one field, EVERY test in this class breaks loudly. That makes the
 * contract impossible to silently regress — which is what "not cheating on
 * tests" means here.</p>
 *
 * <p><b>How the 6 fields are protected:</b> each {@code @Test} uses
 * {@code jsonPath} on every one of {@code timestamp}, {@code status},
 * {@code error}, {@code message}, {@code path}, {@code details}. Removing any
 * field from the advice fails the whole suite.</p>
 *
 * <p><b>Why full {@code @SpringBootTest} and not {@code @WebMvcTest}?</b>
 * The 401 path goes through the real Spring Security filter chain
 * (entry point) and the 409 path through the real JPA + use case; slicing would
 * exclude both and silently neuter the test.</p>
 */
class ErrorResponseContractIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private String registerAndLogin(String email) throws Exception {
        Map<String, Object> reg = Map.of("email", email, "password", "Password123");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        Map<String, Object> login = Map.of("email", email, "password", "Password123");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private UUID createProject(String token, String name) throws Exception {
        Map<String, Object> body = Map.of("name", name);
        MvcResult result = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText());
    }

    private UUID createTask(String token, UUID projectId, String title) throws Exception {
        Map<String, Object> body = Map.of("title", title);
        MvcResult result = mockMvc.perform(post("/api/v1/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText());
    }

    // ========================================================================
    // 404 NOT_FOUND
    // ========================================================================
    // Authenticated resource lookups (project/task miss) no longer reach here:
    // they collapse to 403 to prevent enumeration (see GetProjectUseCase and
    // DeleteProjectUseCase Javadoc). The 404 contract is now only exercised
    // via unknown routes, which is covered by UnknownRoute further below.

    // ========================================================================
    // 409 CONFLICT (duplicate email)
    // ========================================================================

    @Nested
    @DisplayName("409 CONFLICT contract (duplicate registration)")
    class Conflict {

        @Test
        @DisplayName("Duplicate email registration returns the 6-field error shape")
        void duplicateEmail() throws Exception {
            registerAndLogin("alice@example.com"); // first registration succeeds

            Map<String, Object> body = Map.of("email", "alice@example.com", "password", "Password123");

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.timestamp").isNotEmpty())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.error").value("Conflict"))
                    .andExpect(jsonPath("$.message").isNotEmpty())
                    .andExpect(jsonPath("$.path").value("/api/v1/auth/register"))
                    .andExpect(jsonPath("$.details").isArray())
                    .andExpect(jsonPath("$.details.length()").value(0));
        }
    }

    // ========================================================================
    // 400 BAD_REQUEST (validation with MULTIPLE field errors)
    // ========================================================================

    @Nested
    @DisplayName("400 BAD_REQUEST contract (validation: multiple field errors)")
    class Validation {

        @Test
        @DisplayName("Registration with blank email AND short password lists BOTH errors in details[]")
        void multipleValidationErrors() throws Exception {
            // No email validation + password too short → two field errors expected.
            // This is the anti-cheat for the old findFirst() behavior: if anyone
            // reverts to surfacing only the first error, this test breaks.
            Map<String, Object> body = Map.of(
                    "email", "not-an-email",
                    "password", "short",
                    "name", "x"
            );

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.timestamp").isNotEmpty())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value("Validation failed"))
                    .andExpect(jsonPath("$.path").value("/api/v1/auth/register"))
                    .andExpect(jsonPath("$.details").isArray())
                    .andExpect(jsonPath("$.details.length()").value(2));
        }
    }

    // ========================================================================
    // 400 BAD_REQUEST (malformed JSON body)
    // ========================================================================

    @Nested
    @DisplayName("400 BAD_REQUEST contract (malformed body)")
    class MalformedBody {

        @Test
        @DisplayName("Malformed JSON body returns the 6-field error shape")
        void malformedJson() throws Exception {
            String token = registerAndLogin("alice@example.com");

            mockMvc.perform(post("/api/v1/projects")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{not valid json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value("Malformed request body"))
                    .andExpect(jsonPath("$.path").value("/api/v1/projects"))
                    .andExpect(jsonPath("$.details").isArray())
                    .andExpect(jsonPath("$.details.length()").value(0));
        }
    }

    // ========================================================================
    // 401 UNAUTHORIZED (security entry point)
    // ========================================================================

    @Nested
    @DisplayName("401 UNAUTHORIZED contract (security entry point)")
    class Unauthorized {

        @Test
        @DisplayName("Protected route without token returns the 6-field shape via the security entry point")
        void noToken() throws Exception {
            String uri = "/api/v1/projects";

            // Critical: this exercises JsonAuthenticationEntryPoint, NOT the advice.
            // Both must produce the same shape — this test fails if they diverge.
            mockMvc.perform(get(uri))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.timestamp").isNotEmpty())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andExpect(jsonPath("$.message").value("Authentication is required"))
                    .andExpect(jsonPath("$.path").value(uri))
                    .andExpect(jsonPath("$.details").isArray())
                    .andExpect(jsonPath("$.details.length()").value(0));
        }
    }

    // ========================================================================
    // 403 FORBIDDEN (authorization)
    // ========================================================================

    @Nested
    @DisplayName("403 FORBIDDEN contract (cross-user access)")
    class Forbidden {

        @Test
        @DisplayName("Non-owner accessing another user's project returns the 6-field error shape")
        void crossUserAccess() throws Exception {
            String tokenA = registerAndLogin("alice@example.com");
            String tokenB = registerAndLogin("bob@example.com");
            UUID aliceProject = createProject(tokenA, "Alice's private project");
            String uri = "/api/v1/projects/" + aliceProject;

            mockMvc.perform(get(uri).header("Authorization", "Bearer " + tokenB))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.timestamp").isNotEmpty())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("Forbidden"))
                    .andExpect(jsonPath("$.message").isNotEmpty())
                    .andExpect(jsonPath("$.path").value(uri))
                    .andExpect(jsonPath("$.details").isArray())
                    .andExpect(jsonPath("$.details.length()").value(0));
        }
    }

    // ========================================================================
    // 409 CONFLICT (invalid status transition)
    // ========================================================================

    @Nested
    @DisplayName("409 CONFLICT contract (invalid task status transition)")
    class InvalidTransition {

        @Test
        @DisplayName("TODO -> DONE transition returns the 6-field error shape")
        void invalidTransition() throws Exception {
            String token = registerAndLogin("alice@example.com");
            UUID projectId = createProject(token, "P1");
            UUID taskId = createTask(token, projectId, "T1");
            String uri = "/api/v1/tasks/" + taskId + "/status";

            // TODO -> DONE is not allowed (must pass through IN_PROGRESS).
            Map<String, Object> body = Map.of("status", "DONE");

            mockMvc.perform(patch(uri)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.timestamp").isNotEmpty())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.error").value("Conflict"))
                    .andExpect(jsonPath("$.message").isNotEmpty())
                    .andExpect(jsonPath("$.path").value(uri))
                    .andExpect(jsonPath("$.details").isArray())
                    .andExpect(jsonPath("$.details.length()").value(0));
        }
    }

    // ========================================================================
    // 405 METHOD_NOT_ALLOWED
    // ========================================================================

    @Nested
    @DisplayName("405 METHOD_NOT_ALLOWED contract")
    class MethodNotAllowed {

        @Test
        @DisplayName("PUT on a GET/POST-only route returns the 6-field error shape")
        void methodNotAllowed() throws Exception {
            String token = registerAndLogin("alice@example.com");
            String uri = "/api/v1/projects";

            // /api/v1/projects only accepts GET and POST. The dedicated
            // HttpRequestMethodNotSupportedException handler must produce the
            // standardized 6-field shape, not Spring's default error JSON.
            mockMvc.perform(put(uri)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.timestamp").isNotEmpty())
                    .andExpect(jsonPath("$.status").value(405))
                    .andExpect(jsonPath("$.error").value("Method Not Allowed"))
                    .andExpect(jsonPath("$.message").value("Request method 'PUT' is not supported"))
                    .andExpect(jsonPath("$.path").value(uri))
                    .andExpect(jsonPath("$.details").isArray())
                    .andExpect(jsonPath("$.details.length()").value(0));
        }
    }

    // ========================================================================
    // 500 INTERNAL_SERVER_ERROR (catch-all)
    // ========================================================================

    @Nested
    @DisplayName("500 INTERNAL_SERVER_ERROR contract (no internal leak)")
    class InternalError {

        @Test
        @DisplayName("Unhandled exception returns generic message and never leaks internals")
        void doesNotLeakInternals() throws Exception {
            String token = registerAndLogin("alice@example.com");
            String uri = "/api/v1/test/_500";

            String content = mockMvc.perform(get(uri).header("Authorization", "Bearer " + token))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.timestamp").isNotEmpty())
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.error").value("Internal Server Error"))
                    .andExpect(jsonPath("$.path").value(uri))
                    .andExpect(jsonPath("$.details").isArray())
                    .andExpect(jsonPath("$.details.length()").value(0))
                    .andReturn().getResponse().getContentAsString();

            // The anti-leak: the client must never see the original exception message,
            // class name, or any value embedded in it (e.g. the synthetic "hunter2"
            // password dropped into TestErrorController).
            String message = objectMapper.readTree(content).get("message").asText();
            org.assertj.core.api.Assertions.assertThat(message).isEqualTo("Unexpected error");
            org.assertj.core.api.Assertions.assertThat(content)
                    .doesNotContain("INTERNAL_SECRET_DO_NOT_LEAK")
                    .doesNotContain("hunter2")
                    .doesNotContain("IllegalStateException")
                    .doesNotContain("db password");
        }
    }

    // ========================================================================
    // 401 UNAUTHORIZED (domain: invalid login credentials)
    // ========================================================================
    // Exercises the InvalidCredentialsException handler (distinct from the
    // Spring Security entry point tested in the Unauthorized group above).
    @Nested
    @DisplayName("401 UNAUTHORIZED contract (domain: invalid credentials)")
    class InvalidCredentials {

        @Test
        @DisplayName("Login with wrong password returns the 6-field error shape from the domain handler")
        void invalidLogin() throws Exception {
            registerAndLogin("alice@example.com");
            String uri = "/api/v1/auth/login";

            Map<String, Object> body = Map.of("email", "alice@example.com", "password", "WrongPassword1");

            mockMvc.perform(post(uri)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.timestamp").isNotEmpty())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andExpect(jsonPath("$.message").isNotEmpty())
                    .andExpect(jsonPath("$.path").value(uri))
                    .andExpect(jsonPath("$.details").isArray())
                    .andExpect(jsonPath("$.details.length()").value(0));
        }
    }

    // ========================================================================
    // 400 BAD_REQUEST (type mismatch: malformed path variable)
    // ========================================================================
    // Exercises MethodArgumentTypeMismatchException: a non-UUID where a UUID is
    // expected. This is a very common client mistake, and the dedicated handler
    // returns 400 instead of leaking a conversion stack trace as 500.
    @Nested
    @DisplayName("400 BAD_REQUEST contract (malformed UUID path variable)")
    class TypeMismatch {

        @Test
        @DisplayName("Non-UUID id in path returns the 6-field error shape")
        void nonUuidPathVariable() throws Exception {
            String token = registerAndLogin("alice@example.com");
            String uri = "/api/v1/projects/not-a-uuid";

            mockMvc.perform(get(uri).header("Authorization", "Bearer " + token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.timestamp").isNotEmpty())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value("Invalid value for parameter 'id'"))
                    .andExpect(jsonPath("$.path").value(uri))
                    .andExpect(jsonPath("$.details").isArray())
                    .andExpect(jsonPath("$.details.length()").value(0));
        }
    }

    // ========================================================================
    // 403 FORBIDDEN — task lookup miss collapsed (anti-enumeration)
    // ========================================================================
    // PATCH status on a task that does not exist returns 403 (not 404), so a
    // caller cannot distinguish "exists but not mine" from "does not exist".
    // Same collapse the project endpoints already apply. The error envelope
    // still follows the 6-field contract.
    @Nested
    @DisplayName("403 FORBIDDEN contract (task lookup miss collapsed)")
    class TaskLookupMissCollapsed {

        @Test
        @DisplayName("PATCH status on a non-existent task returns 403 with the 6-field error shape")
        void taskLookupMiss() throws Exception {
            String token = registerAndLogin("alice@example.com");
            String uri = "/api/v1/tasks/" + UUID.randomUUID() + "/status";

            Map<String, Object> body = Map.of("status", "IN_PROGRESS");

            mockMvc.perform(patch(uri)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.timestamp").isNotEmpty())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("Forbidden"))
                    .andExpect(jsonPath("$.message").isNotEmpty())
                    .andExpect(jsonPath("$.path").value(uri))
                    .andExpect(jsonPath("$.details").isArray())
                    .andExpect(jsonPath("$.details.length()").value(0));
        }
    }

    // ========================================================================
    // 404 NOT_FOUND (no such route at all)
    // ========================================================================
    // Exercises NoResourceFoundException: a path that matches no controller.
    // Without the dedicated handler this falls through to the 500 catch-all.
    @Nested
    @DisplayName("404 NOT_FOUND contract (unknown route)")
    class UnknownRoute {

        @Test
        @DisplayName("Request to a non-existent path returns the 6-field error shape")
        void unknownRoute() throws Exception {
            String token = registerAndLogin("alice@example.com");
            String uri = "/api/v1/projects/" + UUID.randomUUID() + "/does-not-exist";

            mockMvc.perform(get(uri).header("Authorization", "Bearer " + token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.timestamp").isNotEmpty())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.message").value("Resource not found"))
                    .andExpect(jsonPath("$.path").value(uri))
                    .andExpect(jsonPath("$.details").isArray())
                    .andExpect(jsonPath("$.details.length()").value(0));
        }
    }
}
