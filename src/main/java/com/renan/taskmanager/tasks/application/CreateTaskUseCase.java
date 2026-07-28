package com.renan.taskmanager.tasks.application;

import com.renan.taskmanager.common.audit.application.AuditEventRecorder;
import com.renan.taskmanager.tasks.domain.*;
import com.renan.taskmanager.tasks.domain.Project;
import com.renan.taskmanager.tasks.domain.Task;
import com.renan.taskmanager.common.domain.UserId;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application use case: add a task to an existing project.
 *
 * <p><b>Authorization (anti-enumeration):</b> ownership is checked before
 * the project is fetched. A caller that is not the owner — or that supplies
 * a project id that does not exist — both receive {@link AccessDeniedException}
 * (→ HTTP 403). See {@code GetProjectUseCase} for the rationale.</p>
 */
@Service
public class CreateTaskUseCase {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final AuditEventRecorder auditRecorder;

    public CreateTaskUseCase(ProjectRepository projectRepository, TaskRepository taskRepository,
                             AuditEventRecorder auditRecorder) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public Task execute(ProjectId projectId, UserId requesterId, String title, Priority priority) {
        if (!projectRepository.existsByIdAndOwnerId(projectId, requesterId)) {
            throw new AccessDeniedException("Cannot add tasks to this project");
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AccessDeniedException("Cannot add tasks to this project"));

        Task task = project.addTask(new TaskTitle(title));

        // Override priority if explicitly provided; default comes from Task.create
        if (priority != null) {
            task.changePriority(priority);
        }

        Task saved = taskRepository.save(task);
        // Record after the save, inside the same tx. The priority in metadata
        // reflects what was actually persisted (the override above, or the
        // Task default). Allowlisted: operational, non-sensitive.
        auditRecorder.recordTaskCreated(requesterId, saved.getId().value(),
                saved.getPriority().name());
        return saved;
    }
}
