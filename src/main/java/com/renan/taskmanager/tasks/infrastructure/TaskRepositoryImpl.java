package com.renan.taskmanager.tasks.infrastructure;

import com.renan.taskmanager.tasks.application.TaskMapper;
import com.renan.taskmanager.tasks.application.ports.TaskQueryPort;
import com.renan.taskmanager.tasks.domain.ProjectId;
import com.renan.taskmanager.tasks.domain.TaskId;
import com.renan.taskmanager.tasks.domain.TaskRepository;
import com.renan.taskmanager.tasks.domain.TaskStatus;
import com.renan.taskmanager.common.domain.UserId;
import jakarta.persistence.EntityManager;
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
    private final EntityManager entityManager;

    public TaskRepositoryImpl(TaskJpaRepository jpaRepository, TaskMapper mapper,
                              EntityManager entityManager) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Override
    public com.renan.taskmanager.tasks.domain.Task save(com.renan.taskmanager.tasks.domain.Task task) {
        TaskEntity entity = mapper.toEntity(task);
        if (jpaRepository.existsById(task.getId().value())) {
            return mapper.toDomain(jpaRepository.saveAndFlush(entity));
        }
        entityManager.persist(entity);
        return mapper.toDomain(entity);
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
