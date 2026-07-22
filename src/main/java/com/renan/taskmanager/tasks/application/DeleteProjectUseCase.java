package com.renan.taskmanager.tasks.application;

import com.renan.taskmanager.tasks.domain.ProjectId;
import com.renan.taskmanager.tasks.domain.ProjectRepository;
import com.renan.taskmanager.common.domain.UserId;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application use case: delete a project by id.
 *
 * <p>Authorization: throws {@link AccessDeniedException} if the requester is
 * not the owner.</p>
 */
@Service
public class DeleteProjectUseCase {

    private final ProjectRepository projectRepository;

    public DeleteProjectUseCase(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional
    public void execute(ProjectId projectId, UserId requesterId) {
        if (!projectRepository.existsByIdAndOwnerId(projectId, requesterId)) {
            // We check existence-and-ownership together to avoid revealing whether
            // the project exists to unauthorized users (same rationale as login:
            // no enumeration). If it doesn't belong to the requester, we can't
            // tell them "not found" vs "forbidden" without leaking existence.
            throw new AccessDeniedException("Cannot delete project");
        }

        // Existence check above only verifies ownership; if the id simply doesn't
        // exist at all, existsByIdAndOwnerId returns false too. To distinguish
        // 404 from 403 we'd need two queries — acceptable trade-off for the
        // security benefit of not leaking existence. We treat both as denied
        // here; if you want explicit 404, call findById first.
        projectRepository.deleteById(projectId);
    }
}
