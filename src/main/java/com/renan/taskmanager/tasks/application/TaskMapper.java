package com.renan.taskmanager.tasks.application;

import com.renan.taskmanager.tasks.domain.*;
import com.renan.taskmanager.tasks.infrastructure.ProjectEntity;
import com.renan.taskmanager.tasks.infrastructure.TaskEntity;
import com.renan.taskmanager.users.domain.UserId;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * MapStruct mapper for the tasks context.
 *
 * <p>Translates between domain objects (Project, Task and their value objects)
 * and JPA entities (ProjectEntity, TaskEntity). Hand-written because the domain
 * uses value objects and enums that don't auto-map to the entity's primitive
 * fields and JPA-specific enum mirrors.</p>
 */
@Mapper(componentModel = "spring")
public interface TaskMapper {

    // ===== Project =====

    default ProjectEntity toEntity(Project project) {
        return ProjectEntity.builder()
                .id(project.getId().value())
                .ownerId(project.getOwnerId().value())
                .name(project.getName().value())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }

    /**
     * Converts a ProjectEntity to a domain Project, WITHOUT loading its tasks.
     * Tasks are fetched separately (each Task carries its own projectId).
     * This avoids N+1 and keeps the aggregate boundaries clean.
     */
    default Project toDomain(ProjectEntity entity) {
        return Project.reconstitute(
                ProjectId.of(entity.getId()),
                UserId.of(entity.getOwnerId()),
                new ProjectName(entity.getName()),
                List.of(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    // ===== Task =====

    default TaskEntity toEntity(Task task) {
        return TaskEntity.builder()
                .id(task.getId().value())
                .projectId(task.getProjectId().value())
                .ownerId(task.getOwnerId().value())
                .title(task.getTitle().value())
                .status(TaskEntity.TaskStatusEntity.valueOf(task.getStatus().name()))
                .priority(TaskEntity.PriorityEntity.valueOf(task.getPriority().name()))
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    default Task toDomain(TaskEntity entity) {
        return Task.reconstitute(
                TaskId.of(entity.getId()),
                ProjectId.of(entity.getProjectId()),
                UserId.of(entity.getOwnerId()),
                new TaskTitle(entity.getTitle()),
                TaskStatus.valueOf(entity.getStatus().name()),
                Priority.valueOf(entity.getPriority().name()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
