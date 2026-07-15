package com.renan.taskmanager.tasks.infrastructure;

import com.renan.taskmanager.tasks.application.TaskMapper;
import com.renan.taskmanager.tasks.application.ports.TaskQueryPort;
import com.renan.taskmanager.tasks.domain.ProjectId;
import com.renan.taskmanager.tasks.domain.TaskId;
import com.renan.taskmanager.tasks.domain.TaskRepository;
import com.renan.taskmanager.tasks.domain.TaskStatus;
import com.renan.taskmanager.users.domain.UserId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Adapter: implements both the domain {@link TaskRepository} port and the
 * application {@link TaskQueryPort} using JPA.
 */
@Repository
public class TaskRepositoryImpl implements TaskRepository, TaskQueryPort {

    private final TaskJpaRepository jpaRepository;
    private final TaskMapper mapper;

    public TaskRepositoryImpl(TaskJpaRepository jpaRepository, TaskMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public com.renan.taskmanager.tasks.domain.Task save(com.renan.taskmanager.tasks.domain.Task task) {
        TaskEntity entity = mapper.toEntity(task);
        TaskEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<com.renan.taskmanager.tasks.domain.Task> findById(TaskId id) {
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Page<com.renan.taskmanager.tasks.domain.Task> findByProjectId(
            ProjectId projectId, TaskStatus statusFilter, Pageable pageable) {
        TaskEntity.TaskStatusEntity filter = statusFilter != null
                ? TaskEntity.TaskStatusEntity.valueOf(statusFilter.name())
                : null;
        return jpaRepository.findByProjectId(projectId.value(), filter, pageable)
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsByIdAndOwnerId(TaskId id, UserId ownerId) {
        return jpaRepository.existsByIdAndOwnerId(id.value(), ownerId.value());
    }
}
