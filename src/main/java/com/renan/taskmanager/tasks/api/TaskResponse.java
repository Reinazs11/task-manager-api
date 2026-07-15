package com.renan.taskmanager.tasks.api;

import com.renan.taskmanager.tasks.domain.Priority;
import com.renan.taskmanager.tasks.domain.Task;
import com.renan.taskmanager.tasks.domain.TaskStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Public representation of a task.
 */
public record TaskResponse(
        UUID id,
        UUID projectId,
        String title,
        TaskStatus status,
        Priority priority,
        Instant createdAt,
        Instant updatedAt
) {
    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId().value(),
                task.getProjectId().value(),
                task.getTitle().value(),
                task.getStatus(),
                task.getPriority(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
