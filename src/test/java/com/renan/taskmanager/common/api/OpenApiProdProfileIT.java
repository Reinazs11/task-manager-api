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
 * <p><b>Why override {@code @SpringBootTest(properties = ...)} here (instead of
 * inheriting the base class unchanged)?</b> The {@code prod} profile sets
 * {@code ddl-auto=validate}, which would fail on the empty container because no
 * migration has run yet (Flyway integration comes in Step 7b). We restate the
 * annotations with the prod profile and a one-off {@code ddl-auto=update} so the
 * context boots; this does NOT weaken what we are verifying — that springdoc is
 * disabled in prod. The ddl strategy is orthogonal to the docs leak we test.</p>
 */
@SpringBootTest(properties = {
        "spring.profiles.active=prod",
        "spring.jpa.hibernate.ddl-auto=update"
})
class OpenApiProdProfileIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("Should return 404 for /v3/api-docs when prod profile is active")
    void shouldDisableApiDocsInProd() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound());
    }
}
