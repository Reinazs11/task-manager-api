package com.renan.taskmanager.common.api;

import com.renan.taskmanager.common.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.profiles.active=prod,demo",
        "app.cors.allowed-origins=https://demo.example.com"
})
class OpenApiDemoProfileIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("Should expose Swagger only when demo is explicitly combined with prod")
    void shouldEnableApiDocsForDemo() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.description")
                        .value(org.hamcrest.Matchers.containsString("never use a real email")));
    }

    @Test
    @DisplayName("Should keep the standard validation error contract in demo")
    void shouldKeepValidationContractInDemo() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Demo","email":"demo@example.com","password":"weak"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.details").isArray());
    }
}
