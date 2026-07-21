package com.renan.taskmanager.common.api;

import com.renan.taskmanager.common.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Asserts the {@code prod} profile disables OpenAPI documentation endpoints.
 *
 * <p><b>Why a separate top-level class (and not a {@code @Nested} inside
 * {@link OpenApiDocumentationIT})?</b> Spring TestContext caches an
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
 * <p><b>Why only override the profile (and not ddl-auto)?</b> The prod profile
 * sets {@code ddl-auto=validate}, which would have failed on the empty container
 * before Flyway existed. Now Flyway applies {@code V1__init_schema.sql} on
 * startup, so Hibernate validates successfully — no override needed.</p>
 */
@SpringBootTest(properties = "spring.profiles.active=prod")
class OpenApiProdProfileIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("Should return 404 for /v3/api-docs when prod profile is active")
    void shouldDisableApiDocsInProd() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound());
    }
}
