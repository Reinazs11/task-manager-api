package com.renan.taskmanager.tasks.api;

import com.renan.taskmanager.tasks.domain.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for {@code POST /api/v1/projects/{projectId}/tasks}.
 *
 * @param title    task title (coarse length check; full validation in domain)
 * @param priority optional; defaults to MEDIUM when null
 */
public record CreateTaskRequest(
        @NotBlank @Size(max = 200) String title,
        Priority priority
) {}
