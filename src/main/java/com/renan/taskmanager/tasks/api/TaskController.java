package com.renan.taskmanager.tasks.api;

import com.renan.taskmanager.common.security.AuthenticatedUser;
import com.renan.taskmanager.tasks.application.UpdateTaskStatusUseCase;
import com.renan.taskmanager.tasks.domain.Task;
import com.renan.taskmanager.tasks.domain.TaskId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Tasks", description = "Task operations. Task creation and listing live under the Projects tag.")
public class TaskController {

    private final UpdateTaskStatusUseCase updateTaskStatusUseCase;

    public TaskController(UpdateTaskStatusUseCase updateTaskStatusUseCase) {
        this.updateTaskStatusUseCase = updateTaskStatusUseCase;
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Transition a task to a new status",
            description = "Applies the state-machine rule defined in TaskStatus. Invalid transitions (e.g. TODO -> DONE) return 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task transitioned",
                    content = @Content(schema = @Schema(implementation = TaskResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failure (missing/null status)",
                    content = @Content(schema = @Schema(implementation = com.renan.taskmanager.common.api.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not the task owner",
                    content = @Content(schema = @Schema(implementation = com.renan.taskmanager.common.api.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No such task",
                    content = @Content(schema = @Schema(implementation = com.renan.taskmanager.common.api.ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Invalid status transition",
                    content = @Content(schema = @Schema(implementation = com.renan.taskmanager.common.api.ErrorResponse.class)))
    })
    public ResponseEntity<TaskResponse> updateStatus(
            @Parameter(description = "Task id", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateTaskStatusRequest request) {
        Task task = updateTaskStatusUseCase.execute(
                TaskId.of(id), AuthenticatedUser.id(), request.status());
        return ResponseEntity.ok(TaskResponse.from(task));
    }
}
