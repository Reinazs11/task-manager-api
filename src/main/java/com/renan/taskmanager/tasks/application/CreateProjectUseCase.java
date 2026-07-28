package com.renan.taskmanager.tasks.application;

import com.renan.taskmanager.common.audit.application.AuditEventRecorder;
import com.renan.taskmanager.tasks.domain.Project;
import com.renan.taskmanager.tasks.domain.ProjectRepository;
import com.renan.taskmanager.common.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application use case: create a new project for a user.
 */
@Service
public class CreateProjectUseCase {

    private final ProjectRepository projectRepository;
    private final AuditEventRecorder auditRecorder;

    public CreateProjectUseCase(ProjectRepository projectRepository, AuditEventRecorder auditRecorder) {
        this.projectRepository = projectRepository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public Project execute(UserId ownerId, String name) {
        Project project = Project.create(ownerId, name);
        Project saved = projectRepository.save(project);
        // Records after the save succeeds, inside the same transaction: if the
        // tx rolls back, the audit row is discarded too (DECISIONS.md #21).
        auditRecorder.recordProjectCreated(ownerId, saved.getId().value());
        return saved;
    }
}
