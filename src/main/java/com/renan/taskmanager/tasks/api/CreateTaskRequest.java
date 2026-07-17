package com.renan.taskmanager.tasks.api;

import com.renan.taskmanager.tasks.domain.Priority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for {@code POST /api/v1/projects/{projectId}/tasks}.
 *
 * @param title    task title (coarse length check; full validation in domain)
 * @param priority optional; defaults to MEDIUM when null
 */
@Schema(name = "CreateTaskRequest", description = "Payload to create a new task inside a project")
public record CreateTaskRequest(
        @Schema(description = "Task title (1-200 chars)", example = "Write integration tests")
        @NotBlank @Size(max = 200) String title,

        @Schema(description = "Priority (LOW, MEDIUM, HIGH). Defaults to MEDIUM when omitted.",
                example = "HIGH", nullable = true,
                allowableValues = {"LOW", "MEDIUM", "HIGH"})
        Priority priority
) {}
