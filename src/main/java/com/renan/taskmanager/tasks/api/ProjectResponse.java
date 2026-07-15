package com.renan.taskmanager.tasks.api;

import com.renan.taskmanager.tasks.domain.Project;

import java.time.Instant;
import java.util.UUID;

/**
 * Public representation of a project.
 */
public record ProjectResponse(
        UUID id,
        String name,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId().value(),
                project.getName().value(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
