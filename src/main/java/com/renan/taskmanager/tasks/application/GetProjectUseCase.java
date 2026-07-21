package com.renan.taskmanager.tasks.application;

import com.renan.taskmanager.tasks.domain.Project;
import com.renan.taskmanager.tasks.domain.ProjectId;
import com.renan.taskmanager.tasks.domain.ProjectNotFoundException;
import com.renan.taskmanager.tasks.domain.ProjectRepository;
import com.renan.taskmanager.common.domain.UserId;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Application use case: fetch a single project by id.
 *
 * <p>Authorization: throws {@link AccessDeniedException} (→ 403) if the
 * requester is not the project's owner.</p>
 */
@Service
public class GetProjectUseCase {

    private final ProjectRepository projectRepository;

    public GetProjectUseCase(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public Project execute(ProjectId projectId, UserId requesterId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId.value()));

        if (!project.getOwnerId().equals(requesterId)) {
            throw new AccessDeniedException("Project does not belong to the requesting user");
        }
        return project;
    }
}
