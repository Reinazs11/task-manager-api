package com.renan.taskmanager.tasks.application;

import com.renan.taskmanager.tasks.application.ports.ProjectQueryPort;
import com.renan.taskmanager.tasks.domain.Project;
import com.renan.taskmanager.users.domain.UserId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Application use case: list all projects belonging to a user, paginated.
 */
@Service
public class ListProjectsUseCase {

    private final ProjectQueryPort projectQueryPort;

    public ListProjectsUseCase(ProjectQueryPort projectQueryPort) {
        this.projectQueryPort = projectQueryPort;
    }

    public Page<Project> execute(UserId ownerId, Pageable pageable) {
        return projectQueryPort.findByOwnerId(ownerId, pageable);
    }
}
