package com.renan.taskmanager.tasks.domain;

import com.renan.taskmanager.common.domain.UserId;

import java.util.Optional;

/**
 * Port (DDD): command operations for tasks, framework-agnostic.
 *
 * <p>Pagination queries live in {@link com.renan.taskmanager.tasks.application.ports.TaskQueryPort}
 * because they need Spring Data types. This interface stays pure.</p>
 */
public interface TaskRepository {

    Task save(Task task);

    Optional<Task> findById(TaskId id);

    boolean existsByIdAndOwnerId(TaskId id, UserId ownerId);
}
