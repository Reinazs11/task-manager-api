package com.renan.taskmanager.tasks.application;

import com.renan.taskmanager.tasks.application.ports.ProjectQueryPort;
import com.renan.taskmanager.tasks.application.ports.TaskQueryPort;
import com.renan.taskmanager.tasks.domain.ProjectId;
import com.renan.taskmanager.tasks.domain.TaskStatus;
import com.renan.taskmanager.common.domain.UserId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Application use case: list tasks of a project with optional status filter.
 *
 * <p>Authorization: only the project owner can list tasks.</p>
 */
@Service
public class ListTasksUseCase {

    private final ProjectQueryPort projectQueryPort;
    private final TaskQueryPort taskQueryPort;

    public ListTasksUseCase(ProjectQueryPort projectQueryPort, TaskQueryPort taskQueryPort) {
        this.projectQueryPort = projectQueryPort;
        this.taskQueryPort = taskQueryPort;
    }

    public Page<com.renan.taskmanager.tasks.domain.Task> execute(
            ProjectId projectId, UserId requesterId, TaskStatus statusFilter, Pageable pageable) {
        boolean owned = projectQueryPort.existsByIdAndOwnerId(projectId, requesterId);
        if (!owned) {
            throw new AccessDeniedException("Cannot list tasks of a project you do not own");
        }
        return taskQueryPort.findByProjectId(projectId, statusFilter, pageable);
    }
}
