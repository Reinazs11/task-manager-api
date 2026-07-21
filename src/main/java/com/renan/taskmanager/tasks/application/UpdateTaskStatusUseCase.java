package com.renan.taskmanager.tasks.application;

import com.renan.taskmanager.tasks.domain.Task;
import com.renan.taskmanager.tasks.domain.TaskId;
import com.renan.taskmanager.tasks.domain.TaskNotFoundException;
import com.renan.taskmanager.tasks.domain.TaskRepository;
import com.renan.taskmanager.tasks.domain.TaskStatus;
import com.renan.taskmanager.common.domain.UserId;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application use case: update the status of a task.
 *
 * <p>Authorization: only the task's owner (via project ownership) can update it.</p>
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
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId.value()));

        if (!task.getOwnerId().equals(requesterId)) {
            throw new AccessDeniedException("Cannot update a task you do not own");
        }

        task.transitionTo(newStatus);
        return taskRepository.save(task);
    }
}
