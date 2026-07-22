package com.renan.taskmanager.tasks.api;

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
 * Integration tests for Project and Task endpoints.
 *
 * <p>Covers the full HTTP stack with two registered users to test authorization.
 * userA owns resources; userB is the attacker. Per the anti-enumeration policy,
 * a non-owner (or an id that does not exist at all) always receives 403 —
 * never 404 — so the caller cannot distinguish the two cases.</p>
 */
class ProjectTaskIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    /**
     * Registers a user via API and returns their access token.
     */
    private String registerAndLogin(String email) throws Exception {
        Map<String, Object> regBody = Map.of("email", email, "password", "Password123");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regBody)))
                .andExpect(status().isCreated());

        Map<String, Object> loginBody = Map.of("email", email, "password", "Password123");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginBody)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    /**
     * Creates a project as the given user and returns its id.
     */
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

    /**
     * Creates a task in the given project as the given user and returns its id.
     */
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

    // ====================================================================
    // PROJECTS
    // ====================================================================

    @Nested
    @DisplayName("POST /api/v1/projects")
    class CreateProject {

        @Test
        @DisplayName("Should return 201 when authenticated")
        void shouldCreateProject() throws Exception {
            String token = registerAndLogin("alice@example.com");

            Map<String, Object> body = Map.of("name", "My Project");
            mockMvc.perform(post("/api/v1/projects")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.name").value("My Project"));
        }

        @Test
        @DisplayName("Should return 401 without token")
        void shouldRejectWithoutToken() throws Exception {
            Map<String, Object> body = Map.of("name", "X");
            mockMvc.perform(post("/api/v1/projects")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 400 when name is blank")
        void shouldRejectBlankName() throws Exception {
            String token = registerAndLogin("alice@example.com");
            Map<String, Object> body = Map.of("name", "");
            mockMvc.perform(post("/api/v1/projects")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/projects")
    class ListProjects {

        @Test
        @DisplayName("Should list only the requesting user's projects")
        void shouldListOnlyOwnProjects() throws Exception {
            String tokenA = registerAndLogin("alice@example.com");
            String tokenB = registerAndLogin("bob@example.com");

            createProject(tokenA, "Alice's Project");
            createProject(tokenB, "Bob's Project");

            // Alice should see only her project
            mockMvc.perform(get("/api/v1/projects")
                            .header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("Alice's Project"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/projects/{id}")
    class GetProject {

        @Test
        @DisplayName("Should return 200 when owner requests")
        void shouldReturnProjectToOwner() throws Exception {
            String token = registerAndLogin("alice@example.com");
            UUID projectId = createProject(token, "P1");

            mockMvc.perform(get("/api/v1/projects/" + projectId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(projectId.toString()));
        }

        @Test
        @DisplayName("Should return 403 when non-owner requests")
        void shouldReturn403ToNonOwner() throws Exception {
            String tokenA = registerAndLogin("alice@example.com");
            String tokenB = registerAndLogin("bob@example.com");
            UUID projectId = createProject(tokenA, "Alice's Secret");

            mockMvc.perform(get("/api/v1/projects/" + projectId)
                            .header("Authorization", "Bearer " + tokenB))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/projects/{id}")
    class DeleteProject {

        @Test
        @DisplayName("Should return 204 when owner deletes")
        void shouldDeleteWhenOwner() throws Exception {
            String token = registerAndLogin("alice@example.com");
            UUID projectId = createProject(token, "To Delete");

            mockMvc.perform(delete("/api/v1/projects/" + projectId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Should return 403 when non-owner tries to delete")
        void shouldRejectNonOwnerDelete() throws Exception {
            String tokenA = registerAndLogin("alice@example.com");
            String tokenB = registerAndLogin("bob@example.com");
            UUID projectId = createProject(tokenA, "Alice's");

            mockMvc.perform(delete("/api/v1/projects/" + projectId)
                            .header("Authorization", "Bearer " + tokenB))
                    .andExpect(status().isForbidden());
        }
    }

    // ====================================================================
    // TASKS
    // ====================================================================

    @Nested
    @DisplayName("POST /api/v1/projects/{id}/tasks")
    class CreateTaskEndpoint {

        @Test
        @DisplayName("Should return 201 when owner creates task")
        void shouldCreateTask() throws Exception {
            String token = registerAndLogin("alice@example.com");
            UUID projectId = createProject(token, "P1");

            Map<String, Object> body = Map.of("title", "Write tests");
            mockMvc.perform(post("/api/v1/projects/" + projectId + "/tasks")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.title").value("Write tests"))
                    .andExpect(jsonPath("$.status").value("TODO"))
                    .andExpect(jsonPath("$.priority").value("MEDIUM"));
        }

        @Test
        @DisplayName("Should return 403 when non-owner creates task")
        void shouldRejectNonOwnerTaskCreation() throws Exception {
            String tokenA = registerAndLogin("alice@example.com");
            String tokenB = registerAndLogin("bob@example.com");
            UUID projectId = createProject(tokenA, "Alice's");

            Map<String, Object> body = Map.of("title", "Hack");
            mockMvc.perform(post("/api/v1/projects/" + projectId + "/tasks")
                            .header("Authorization", "Bearer " + tokenB)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/projects/{id}/tasks")
    class ListTasksEndpoint {

        @Test
        @DisplayName("Should list tasks filtered by status")
        void shouldFilterByStatus() throws Exception {
            String token = registerAndLogin("alice@example.com");
            UUID projectId = createProject(token, "P1");

            // Create 3 tasks, advance one to IN_PROGRESS, one to DONE
            UUID t1 = createTask(token, projectId, "TODO Task");
            UUID t2 = createTask(token, projectId, "Active Task");
            UUID t3 = createTask(token, projectId, "Done Task");

            // Advance t2: IN_PROGRESS
            updateTaskStatus(token, t2, "IN_PROGRESS");
            // Advance t3: IN_PROGRESS then DONE
            updateTaskStatus(token, t3, "IN_PROGRESS");
            updateTaskStatus(token, t3, "DONE");

            // Filter: only DONE
            mockMvc.perform(get("/api/v1/projects/" + projectId + "/tasks")
                            .param("status", "DONE")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].title").value("Done Task"));
        }

        @Test
        @DisplayName("Should return 403 when non-owner lists tasks")
        void shouldRejectNonOwnerListing() throws Exception {
            String tokenA = registerAndLogin("alice@example.com");
            String tokenB = registerAndLogin("bob@example.com");
            UUID projectId = createProject(tokenA, "Alice's");

            mockMvc.perform(get("/api/v1/projects/" + projectId + "/tasks")
                            .header("Authorization", "Bearer " + tokenB))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/tasks/{id}/status")
    class UpdateTaskStatusEndpoint {

        @Test
        @DisplayName("Should return 200 on valid transition TODO -> IN_PROGRESS")
        void shouldAllowValidTransition() throws Exception {
            String token = registerAndLogin("alice@example.com");
            UUID projectId = createProject(token, "P1");
            UUID taskId = createTask(token, projectId, "T1");

            Map<String, Object> body = Map.of("status", "IN_PROGRESS");
            mockMvc.perform(patch("/api/v1/tasks/" + taskId + "/status")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        }

        @Test
        @DisplayName("Should return 409 on invalid transition TODO -> DONE")
        void shouldRejectInvalidTransition() throws Exception {
            String token = registerAndLogin("alice@example.com");
            UUID projectId = createProject(token, "P1");
            UUID taskId = createTask(token, projectId, "T1");

            Map<String, Object> body = Map.of("status", "DONE");
            mockMvc.perform(patch("/api/v1/tasks/" + taskId + "/status")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Should return 403 when non-owner updates task")
        void shouldRejectNonOwnerUpdate() throws Exception {
            String tokenA = registerAndLogin("alice@example.com");
            String tokenB = registerAndLogin("bob@example.com");
            UUID projectId = createProject(tokenA, "P1");
            UUID taskId = createTask(tokenA, projectId, "T1");

            Map<String, Object> body = Map.of("status", "IN_PROGRESS");
            mockMvc.perform(patch("/api/v1/tasks/" + taskId + "/status")
                            .header("Authorization", "Bearer " + tokenB)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isForbidden());
        }
    }

    /**
     * Helper: updates a task status via API.
     */
    private void updateTaskStatus(String token, UUID taskId, String status) throws Exception {
        Map<String, Object> body = Map.of("status", status);
        mockMvc.perform(patch("/api/v1/tasks/" + taskId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }
}
