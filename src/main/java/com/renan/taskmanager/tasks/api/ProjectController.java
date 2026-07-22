package com.renan.taskmanager.tasks.api;

import com.renan.taskmanager.common.security.AuthenticatedUser;
import com.renan.taskmanager.tasks.application.*;
import com.renan.taskmanager.tasks.domain.Project;
import com.renan.taskmanager.tasks.domain.ProjectId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Project endpoints. All routes require authentication.
 */
@RestController
@RequestMapping("/api/v1/projects")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Projects", description = "Project CRUD and nested task management. All endpoints require a valid JWT.")
public class ProjectController {

    private final CreateProjectUseCase createProjectUseCase;
    private final ListProjectsUseCase listProjectsUseCase;
    private final GetProjectUseCase getProjectUseCase;
    private final DeleteProjectUseCase deleteProjectUseCase;
    private final CreateTaskUseCase createTaskUseCase;
    private final ListTasksUseCase listTasksUseCase;

    public ProjectController(CreateProjectUseCase createProjectUseCase,
                             ListProjectsUseCase listProjectsUseCase,
                             GetProjectUseCase getProjectUseCase,
                             DeleteProjectUseCase deleteProjectUseCase,
                             CreateTaskUseCase createTaskUseCase,
                             ListTasksUseCase listTasksUseCase) {
        this.createProjectUseCase = createProjectUseCase;
        this.listProjectsUseCase = listProjectsUseCase;
        this.getProjectUseCase = getProjectUseCase;
        this.deleteProjectUseCase = deleteProjectUseCase;
        this.createTaskUseCase = createTaskUseCase;
        this.listTasksUseCase = listTasksUseCase;
    }

    @PostMapping
    @Operation(summary = "Create a project", description = "Creates a project owned by the authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Project created",
                    content = @Content(schema = @Schema(implementation = ProjectResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failure",
                    content = @Content(schema = @Schema(implementation = com.renan.taskmanager.common.api.ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthenticated",
                    content = @Content(schema = @Schema(implementation = com.renan.taskmanager.common.api.ErrorResponse.class)))
    })
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest request) {
        Project project = createProjectUseCase.execute(AuthenticatedUser.id(), request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(ProjectResponse.from(project));
    }

    @GetMapping
    @Operation(summary = "List the authenticated user's projects",
            description = "Returns a paginated list of projects owned by the requester. Other users' projects are never returned.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of projects",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProjectResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Unauthenticated",
                    content = @Content(schema = @Schema(implementation = com.renan.taskmanager.common.api.ErrorResponse.class)))
    })
    public ResponseEntity<Page<ProjectResponse>> list(@PageableDefault(size = 20) Pageable pageable) {
        Page<Project> projects = listProjectsUseCase.execute(AuthenticatedUser.id(), pageable);
        return ResponseEntity.ok(projects.map(ProjectResponse::from));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single project by id",
            description = "Returns the project if the requester owns it. A non-owner or a non-existent id both return 403 (anti-enumeration: the caller cannot distinguish the two).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Project found",
                    content = @Content(schema = @Schema(implementation = ProjectResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not the owner, or no such project",
                    content = @Content(schema = @Schema(implementation = com.renan.taskmanager.common.api.ErrorResponse.class)))
    })
    public ResponseEntity<ProjectResponse> get(
            @Parameter(description = "Project id", required = true) @PathVariable UUID id) {
        Project project = getProjectUseCase.execute(ProjectId.of(id), AuthenticatedUser.id());
        return ResponseEntity.ok(ProjectResponse.from(project));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a project", description = "Deletes the project if the requester owns it. Non-owners receive 403 (not 404, intentionally collapsed).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted"),
            @ApiResponse(responseCode = "403", description = "Not the owner",
                    content = @Content(schema = @Schema(implementation = com.renan.taskmanager.common.api.ErrorResponse.class)))
    })
    public void delete(
            @Parameter(description = "Project id", required = true) @PathVariable UUID id) {
        deleteProjectUseCase.execute(ProjectId.of(id), AuthenticatedUser.id());
    }

    @PostMapping("/{id}/tasks")
    @Operation(summary = "Create a task inside a project", description = "Only the project owner can add tasks.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Task created",
                    content = @Content(schema = @Schema(implementation = TaskResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failure",
                    content = @Content(schema = @Schema(implementation = com.renan.taskmanager.common.api.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not the project owner",
                    content = @Content(schema = @Schema(implementation = com.renan.taskmanager.common.api.ErrorResponse.class)))
    })
    public ResponseEntity<TaskResponse> createTask(
            @Parameter(description = "Project id", required = true) @PathVariable UUID id,
            @Valid @RequestBody CreateTaskRequest request) {
        com.renan.taskmanager.tasks.domain.Task task = createTaskUseCase.execute(
                ProjectId.of(id), AuthenticatedUser.id(), request.title(), request.priority());
        return ResponseEntity.status(HttpStatus.CREATED).body(TaskResponse.from(task));
    }

    @GetMapping("/{id}/tasks")
    @Operation(summary = "List tasks of a project",
            description = "Returns a paginated list of tasks, optionally filtered by status. Only the project owner can call this.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of tasks",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = TaskResponse.class)))),
            @ApiResponse(responseCode = "403", description = "Not the project owner",
                    content = @Content(schema = @Schema(implementation = com.renan.taskmanager.common.api.ErrorResponse.class)))
    })
    public ResponseEntity<Page<TaskResponse>> listTasks(
            @Parameter(description = "Project id", required = true) @PathVariable UUID id,
            @Parameter(description = "Optional status filter") @RequestParam(name = "status", required = false)
            com.renan.taskmanager.tasks.domain.TaskStatus statusFilter,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<com.renan.taskmanager.tasks.domain.Task> tasks = listTasksUseCase.execute(
                ProjectId.of(id), AuthenticatedUser.id(), statusFilter, pageable);
        return ResponseEntity.ok(tasks.map(TaskResponse::from));
    }
}
