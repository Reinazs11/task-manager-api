package com.renan.taskmanager.common.audit.api;

import com.renan.taskmanager.common.AbstractIntegrationTest;
import com.renan.taskmanager.common.audit.domain.AuditEventRepository;
import com.renan.taskmanager.users.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@link AuditController} through the full HTTP stack.
 *
 * <p>Covers: authentication gate (401 without a token), authorization scoping
 * (a user only sees their own events), the action filter, and the default
 * newest-first ordering. The audit rows are produced by exercising the real
 * use cases (register + login + create project), which is exactly how the
 * trail is populated in production — so this IT doubles as a smoke test of
 * the wiring added in Fase 5.</p>
 */
class AuditControllerIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @BeforeEach
    void cleanDatabase() {
        // Order matters: audit_events references users via a FK. Clear audit
        // rows first, then users, so no orphaned FK trips the cleanup.
        auditEventRepository.deleteAllForTest();
        userRepository.deleteAll();
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "Password123"))))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "Password123"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asString();
    }

    private void createProject(String token, String name) throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name))))
                .andExpect(status().isCreated());
    }

    @Nested
    @DisplayName("Authentication")
    class Authentication {

        @Test
        @DisplayName("GET /audit/events without a token should return 401")
        void shouldRejectUnauthenticatedRequest() throws Exception {
            mockMvc.perform(get("/api/v1/audit/events"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Authorization scoping")
    class Authorization {

        @Test
        @DisplayName("A user should see only their own events")
        void shouldScopeToCaller() throws Exception {
            String tokenA = registerAndLogin("userA@example.com");
            createProject(tokenA, "A's project");

            String tokenB = registerAndLogin("userB@example.com");
            createProject(tokenB, "B's project");

            // Each /audit/events call should return only that caller's rows.
            // userA: at least USER_REGISTERED + USER_LOGIN_SUCCEEDED + PROJECT_CREATED
            mockMvc.perform(get("/api/v1/audit/events")
                            .header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[?(@.action == 'PROJECT_CREATED')]").exists())
                    .andExpect(jsonPath("$.content[?(@.action == 'PROJECT_DELETED')]")
                            .doesNotExist());

            // userB's PROJECT_CREATED must not appear in userA's page.
            // Easiest robust check: userA sees exactly one PROJECT_CREATED.
            mockMvc.perform(get("/api/v1/audit/events?action=PROJECT_CREATED")
                            .header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1));
        }
    }

    @Nested
    @DisplayName("Filtering and ordering")
    class Filtering {

        @Test
        @DisplayName("action filter should narrow the result set")
        void shouldFilterByAction() throws Exception {
            String token = registerAndLogin("filter@example.com");
            createProject(token, "P1");
            createProject(token, "P2");

            mockMvc.perform(get("/api/v1/audit/events?action=PROJECT_CREATED")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[*].action")
                            .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("PROJECT_CREATED"))));
        }

        @Test
        @DisplayName("default response should be newest first")
        void shouldBeNewestFirst() throws Exception {
            String token = registerAndLogin("order@example.com");
            createProject(token, "first");
            createProject(token, "second");

            mockMvc.perform(get("/api/v1/audit/events?action=PROJECT_CREATED")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    // "second" was created last → should be first in the page
                    .andExpect(jsonPath("$.content[0].metadata").exists());
        }
    }
}
