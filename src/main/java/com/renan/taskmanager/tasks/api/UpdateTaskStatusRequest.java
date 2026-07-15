package com.renan.taskmanager.tasks.api;

import com.renan.taskmanager.tasks.domain.TaskStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for {@code PATCH /api/v1/tasks/{id}/status}.
 */
public record UpdateTaskStatusRequest(
        @NotNull TaskStatus status
) {}
