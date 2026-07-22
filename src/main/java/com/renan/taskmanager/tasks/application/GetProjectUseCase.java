package com.renan.taskmanager.tasks.application;

import com.renan.taskmanager.tasks.domain.Project;
import com.renan.taskmanager.tasks.domain.ProjectId;
import com.renan.taskmanager.tasks.domain.ProjectRepository;
import com.renan.taskmanager.common.domain.UserId;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Application use case: fetch a single project by id.
 *
 * <p><b>Authorization (anti-enumeration):</b> ownership is checked before
 * the record is fetched. A caller that is not the owner — or that supplies
 * an id that does not exist at all — both receive {@link AccessDeniedException}
 * (→ HTTP 403). This prevents an attacker from distinguishing "exists but
 * not mine" from "does not exist". Same rationale as {@code DeleteProjectUseCase}
 * and {@code LoginUseCase}.</p>
 */
@Service
public class GetProjectUseCase {

    private final ProjectRepository projectRepository;

    public GetProjectUseCase(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public Project execute(ProjectId projectId, UserId requesterId) {
        if (!projectRepository.existsByIdAndOwnerId(projectId, requesterId)) {
            throw new AccessDeniedException("Cannot access project");
        }
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new AccessDeniedException("Cannot access project"));
    }
}
