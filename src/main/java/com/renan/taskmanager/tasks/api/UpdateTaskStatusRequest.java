package com.renan.taskmanager.tasks.api;

import com.renan.taskmanager.tasks.domain.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for {@code PATCH /api/v1/tasks/{id}/status}.
 */
@Schema(name = "UpdateTaskStatusRequest", description = "Payload to transition a task to a new status")
public record UpdateTaskStatusRequest(
        @Schema(description = "Target status. Not every transition is allowed (e.g. TODO -> DONE is rejected).",
                example = "IN_PROGRESS",
                allowableValues = {"TODO", "IN_PROGRESS", "DONE"})
        @NotNull TaskStatus status
) {}
