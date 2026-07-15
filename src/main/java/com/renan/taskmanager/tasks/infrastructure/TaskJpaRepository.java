package com.renan.taskmanager.tasks.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link TaskEntity}.
 */
public interface TaskJpaRepository extends JpaRepository<TaskEntity, UUID> {

    /**
     * Lists tasks of a project, optionally filtered by status.
     * When statusFilter is null, returns all tasks of the project.
     */
    @Query("""
            SELECT t FROM TaskEntity t
            WHERE t.projectId = :projectId
            AND (:statusFilter IS NULL OR t.status = :statusFilter)
            """)
    Page<TaskEntity> findByProjectId(UUID projectId, TaskEntity.TaskStatusEntity statusFilter, Pageable pageable);

    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);
}
