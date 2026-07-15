package com.renan.taskmanager.tasks.api;

import com.renan.taskmanager.common.security.AuthenticatedUser;
import com.renan.taskmanager.tasks.application.*;
import com.renan.taskmanager.tasks.domain.Project;
import com.renan.taskmanager.tasks.domain.ProjectId;
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
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest request) {
        Project project = createProjectUseCase.execute(AuthenticatedUser.id(), request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(ProjectResponse.from(project));
    }

    @GetMapping
    public ResponseEntity<Page<ProjectResponse>> list(@PageableDefault(size = 20) Pageable pageable) {
        Page<Project> projects = listProjectsUseCase.execute(AuthenticatedUser.id(), pageable);
        return ResponseEntity.ok(projects.map(ProjectResponse::from));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> get(@PathVariable UUID id) {
        Project project = getProjectUseCase.execute(ProjectId.of(id), AuthenticatedUser.id());
        return ResponseEntity.ok(ProjectResponse.from(project));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        deleteProjectUseCase.execute(ProjectId.of(id), AuthenticatedUser.id());
    }

    @PostMapping("/{id}/tasks")
    public ResponseEntity<TaskResponse> createTask(
            @PathVariable UUID id,
            @Valid @RequestBody CreateTaskRequest request) {
        com.renan.taskmanager.tasks.domain.Task task = createTaskUseCase.execute(
                ProjectId.of(id), AuthenticatedUser.id(), request.title(), request.priority());
        return ResponseEntity.status(HttpStatus.CREATED).body(TaskResponse.from(task));
    }

    @GetMapping("/{id}/tasks")
    public ResponseEntity<Page<TaskResponse>> listTasks(
            @PathVariable UUID id,
            @RequestParam(name = "status", required = false)
            com.renan.taskmanager.tasks.domain.TaskStatus statusFilter,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<com.renan.taskmanager.tasks.domain.Task> tasks = listTasksUseCase.execute(
                ProjectId.of(id), AuthenticatedUser.id(), statusFilter, pageable);
        return ResponseEntity.ok(tasks.map(TaskResponse::from));
    }
}
