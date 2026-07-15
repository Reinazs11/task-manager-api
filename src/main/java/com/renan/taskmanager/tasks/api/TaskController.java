package com.renan.taskmanager.tasks.api;

import com.renan.taskmanager.common.security.AuthenticatedUser;
import com.renan.taskmanager.tasks.application.UpdateTaskStatusUseCase;
import com.renan.taskmanager.tasks.domain.Task;
import com.renan.taskmanager.tasks.domain.TaskId;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Task endpoints (status update only for now).
 * Task creation and listing live under {@link ProjectController} because tasks
 * are always created in the context of a project.
 */
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final UpdateTaskStatusUseCase updateTaskStatusUseCase;

    public TaskController(UpdateTaskStatusUseCase updateTaskStatusUseCase) {
        this.updateTaskStatusUseCase = updateTaskStatusUseCase;
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<TaskResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTaskStatusRequest request) {
        Task task = updateTaskStatusUseCase.execute(
                TaskId.of(id), AuthenticatedUser.id(), request.status());
        return ResponseEntity.ok(TaskResponse.from(task));
    }
}
