package com.renan.taskmanager.tasks.application;

import com.renan.taskmanager.tasks.domain.Task;
import com.renan.taskmanager.tasks.domain.TaskId;
import com.renan.taskmanager.tasks.domain.TaskRepository;
import com.renan.taskmanager.tasks.domain.TaskStatus;
import com.renan.taskmanager.common.domain.UserId;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application use case: update the status of a task.
 *
 * <p><b>Authorization (anti-enumeration):</b> ownership is checked before
 * the task is fetched. A caller that is not the owner — or that supplies a
 * task id that does not exist — both receive {@link AccessDeniedException}
 * (→ HTTP 403). See {@code GetProjectUseCase} for the rationale.</p>
 *
 * <p>Transition rules are enforced by {@link TaskStatus#assertTransitionTo}.</p>
 */
@Service
public class UpdateTaskStatusUseCase {

    private final TaskRepository taskRepository;

    public UpdateTaskStatusUseCase(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional
    public Task execute(TaskId taskId, UserId requesterId, TaskStatus newStatus) {
        if (!taskRepository.existsByIdAndOwnerId(taskId, requesterId)) {
            throw new AccessDeniedException("Cannot update this task");
        }

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new AccessDeniedException("Cannot update this task"));

        task.transitionTo(newStatus);
        return taskRepository.save(task);
    }
}
