package com.renan.taskmanager.common.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Asserts the {@code prod} profile disables OpenAPI documentation endpoints.
 *
 * <p><b>Why a separate top-level class (and not a {@code @Nested} inside
 * {@link OpenApiDocumentationTest})?</b> Spring TestContext caches an
 * {@code ApplicationContext} per unique configuration. A different active profile
 * is a different configuration, so it needs its own context, and the framework
 * requires a top-level class (not an inner class) to host it. Splitting the
 * classes keeps each context's lifecycle clean.</p>
 *
 * <p><b>Why this matters:</b> documentation endpoints expose the entire API
 * surface. Leaving them enabled in production is a real leak. This test fails
 * loudly if anyone removes the {@code springdoc.*.enabled=false} override from
 * {@code application-prod.yml}.</p>
 *
 * <p><b>Why override {@code ddl-auto} here?</b> The {@code prod} profile sets
 * {@code ddl-auto=validate}, which is correct in real production where
 * Flyway/Liquibase has already created the schema (coming in Step 7). In this
 * test the container starts empty, so we let Hibernate create the tables. This
 * isolation does not affect what we are actually verifying: that springdoc is
 * disabled in prod. The ddl strategy is orthogonal.</p>
 */
@SpringBootTest(properties = {
        "spring.profiles.active=prod",
        "spring.jpa.hibernate.ddl-auto=update"
})
@AutoConfigureMockMvc
@Testcontainers
class OpenApiProdProfileTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("taskmanager_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should return 404 for /v3/api-docs when prod profile is active")
    void shouldDisableApiDocsInProd() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound());
    }
}
