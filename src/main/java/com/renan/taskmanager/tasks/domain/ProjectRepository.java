package com.renan.taskmanager.tasks.domain;

import com.renan.taskmanager.users.domain.UserId;

import java.util.Optional;

/**
 * Port (DDD): command operations for projects, framework-agnostic.
 *
 * <p>Pagination queries live in {@link com.renan.taskmanager.tasks.application.ports.ProjectQueryPort}
 * because they need Spring Data types. This interface stays pure.</p>
 */
public interface ProjectRepository {

    Project save(Project project);

    Optional<Project> findById(ProjectId id);

    void deleteById(ProjectId id);

    boolean existsByIdAndOwnerId(ProjectId id, UserId ownerId);
}
