package com.renan.taskmanager.tasks.application;

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

    public CreateTaskUseCase(ProjectRepository projectRepository, TaskRepository taskRepository) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional
    public Task execute(ProjectId projectId, UserId requesterId, String title, Priority priority) {
        if (!projectRepository.existsByIdAndOwnerId(projectId, requesterId)) {
            throw new AccessDeniedException("Cannot add tasks to this project");
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AccessDeniedException("Cannot add tasks to this project"));

        Project.TaskAdded result = project.addTask(new TaskTitle(title));
        Task taskToPersist = result.task();

        // Override priority if explicitly provided; default comes from Task.create
        if (priority != null) {
            taskToPersist.changePriority(priority);
        }

        return taskRepository.save(taskToPersist);
    }
}
