package com.renan.taskmanager.common.api;

import com.renan.taskmanager.common.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test for issue #17: the Swagger UI entry-point redirect
 * {@code /swagger-ui.html} returned 401 in dev because the security matcher
 * {@code /swagger-ui/**} matches assets inside the folder but NOT the redirect
 * springdoc publishes at the root.
 *
 * <p>This test locks two contracts:</p>
 * <ol>
 *   <li>The canonical docs URL is reachable <b>without</b> a JWT (it is public
 *       documentation in dev), and</li>
 *   <li>It does not 401 — the exact symptom of the bug. Either a 200 (served)
 *       or a 3xx redirect to {@code /swagger-ui/index.html} is acceptable; both
 *       prove the security gap is closed.</li>
 * </ol>
 *
 * <p><b>Prod is out of scope here:</b> docs are disabled entirely in prod via
 * {@code springdoc.*.enabled=false}, asserted separately in
 * {@code OpenApiProdProfileIT}.</p>
 */
class SwaggerEntryPointIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("GET /swagger-ui.html without token does not return 401 (regression for #17)")
    void swaggerEntryPointShouldNotRequireAuth() throws Exception {
        int status = mockMvc.perform(get("/swagger-ui.html"))
                .andReturn()
                .getResponse()
                .getStatus();

        // The bug: this returned 401 because /swagger-ui.html fell through to
        // anyRequest().authenticated(). After the fix it is permitAll, so it is
        // either served (200) or redirected to /swagger-ui/index.html (3xx) —
        // never 401/403. Asserting the exact code would be brittle across
        // springdoc versions; the regression is specifically the 401.
        org.assertj.core.api.Assertions.assertThat(status)
                .as("/swagger-ui.html must be public (regression for issue #17)")
                .isNotEqualTo(401)
                .isNotEqualTo(403);
    }

    @Test
    @DisplayName("GET /swagger-ui.html without token redirects to the UI assets")
    void swaggerEntryPointShouldRedirectToUi() throws Exception {
        // Springdoc's /swagger-ui.html is a redirect to /swagger-ui/index.html.
        // Asserting the Location pins the public-entry-point → assets-folder hop
        // that issue #17 broke: the redirect itself was unreachable behind auth.
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/swagger-ui/index.html"));
    }
}
