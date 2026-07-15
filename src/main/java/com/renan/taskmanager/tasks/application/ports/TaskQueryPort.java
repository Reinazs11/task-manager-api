package com.renan.taskmanager.tasks.application.ports;

import com.renan.taskmanager.tasks.domain.TaskId;
import com.renan.taskmanager.tasks.domain.TaskStatus;
import com.renan.taskmanager.tasks.domain.ProjectId;
import com.renan.taskmanager.users.domain.UserId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * Output port (application layer): query operations for tasks that need
 * Spring Data pagination types.
 *
 * @see ProjectQueryPort
 */
public interface TaskQueryPort {

    Page<com.renan.taskmanager.tasks.domain.Task> findByProjectId(
            ProjectId projectId, TaskStatus statusFilter, Pageable pageable);

    Optional<com.renan.taskmanager.tasks.domain.Task> findById(TaskId id);

    boolean existsByIdAndOwnerId(TaskId id, UserId ownerId);
}
