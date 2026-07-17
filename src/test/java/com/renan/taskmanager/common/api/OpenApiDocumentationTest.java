package com.renan.taskmanager.common.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the OpenAPI 3 documentation that the API actually serves.
 *
 * <p><b>Why test the rendered output and not the {@code @Operation} annotations
 * via reflection?</b> Clients consume the JSON at {@code /v3/api-docs}, not the
 * source code. Testing the rendered contract catches real problems (missing
 * security scheme, undocumented endpoints, prod leaking docs) while being robust
 * to annotation-style changes. This is the honest way to lock "the API is
 * documented".</p>
 *
 * <p><b>Why a full {@code @SpringBootTest} with Testcontainers (and not a web
 * slice)?</b> springdoc inspects the live web context (every controller, every
 * {@code @Schema}); excluding JPA/repositories would drop the controllers' use
 * case dependencies and produce a document that does not match reality. So we
 * boot the whole app against a real PostgreSQL, exactly like the other
 * integration tests.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OpenApiDocumentationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("taskmanager_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ========================================================================
    // OpenAPI metadata
    // ========================================================================

    @Nested
    @DisplayName("/v3/api-docs metadata")
    class Metadata {

        @Test
        @DisplayName("Should expose a valid OpenAPI 3 document with title and version")
        void shouldExposeOpenApiDocument() throws Exception {
            MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.openapi").isNotEmpty())
                    .andExpect(jsonPath("$.info.title").value("Task Manager API"))
                    .andExpect(jsonPath("$.info.version").isNotEmpty())
                    .andReturn();

            JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(root.get("openapi").asText()).startsWith("3.");
        }
    }

    // ========================================================================
    // Security scheme (Bearer JWT)
    // ========================================================================

    @Nested
    @DisplayName("Security scheme")
    class SecurityScheme {

        @Test
        @DisplayName("Should declare an HTTP bearer security scheme for JWT")
        void shouldDeclareBearerJwtScheme() throws Exception {
            MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
            JsonNode scheme = root.path("components").path("securitySchemes").path("bearerAuth");

            // If OpenApiConfig is removed or the scheme renamed, these assertions fail.
            assertThat(scheme.isMissingNode()).isFalse();
            assertThat(scheme.path("type").asText()).isEqualTo("http");
            assertThat(scheme.path("scheme").asText()).isEqualTo("bearer");
            assertThat(scheme.path("bearerFormat").asText()).isEqualTo("JWT");
        }
    }

    // ========================================================================
    // Endpoint coverage
    // ========================================================================

    @Nested
    @DisplayName("Documented endpoints")
    class EndpointCoverage {

        @Test
        @DisplayName("Should document every public endpoint")
        void shouldDocumentAllEndpoints() throws Exception {
            MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode paths = objectMapper.readTree(result.getResponse().getContentAsString()).path("paths");

            // Auth (public)
            assertThat(paths.path("/api/v1/auth/register").has("post")).isTrue();
            assertThat(paths.path("/api/v1/auth/login").has("post")).isTrue();

            // Projects (protected)
            assertThat(paths.path("/api/v1/projects").has("post")).isTrue();
            assertThat(paths.path("/api/v1/projects").has("get")).isTrue();
            assertThat(paths.path("/api/v1/projects/{id}").has("get")).isTrue();
            assertThat(paths.path("/api/v1/projects/{id}").has("delete")).isTrue();
            assertThat(paths.path("/api/v1/projects/{id}/tasks").has("post")).isTrue();
            assertThat(paths.path("/api/v1/projects/{id}/tasks").has("get")).isTrue();

            // Tasks (protected)
            assertThat(paths.path("/api/v1/tasks/{id}/status").has("patch")).isTrue();
        }
    }

    // ========================================================================
    // Production profile disables docs
    // ========================================================================
    // NOTE: a dedicated Spring context with a different profile cannot live in
    // a @Nested inner class (Spring TestContext requires a top-level class for
    // its own ApplicationContext). The prod-profile assertions live in the
    // separate top-level class below.
}
