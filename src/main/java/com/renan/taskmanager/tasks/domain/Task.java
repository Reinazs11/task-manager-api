package com.renan.taskmanager.tasks.domain;

import com.renan.taskmanager.users.domain.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * Task entity belonging to a {@link Project}.
 *
 * <p>Tasks are created through {@link Project#addTask} so the aggregate root
 * can enforce invariants. Direct construction is allowed but discouraged —
 * it's here for reconstitution from persistence (Step 5).</p>
 *
 * <p><b>Identity:</b> equal by {@link TaskId}, not by fields.</p>
 */
public class Task {

    private final TaskId id;
    private final ProjectId projectId;
    private final UserId ownerId;
    private TaskTitle title;
    private TaskStatus status;
    private Priority priority;
    private final Instant createdAt;
    private Instant updatedAt;

    private Task(TaskId id, ProjectId projectId, UserId ownerId, TaskTitle title,
                 TaskStatus status, Priority priority, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "TaskId is required");
        this.projectId = Objects.requireNonNull(projectId, "ProjectId is required");
        this.ownerId = Objects.requireNonNull(ownerId, "OwnerId is required");
        this.title = Objects.requireNonNull(title, "Title is required");
        this.status = Objects.requireNonNull(status, "Status is required");
        this.priority = Objects.requireNonNull(priority, "Priority is required");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    /**
     * Factory for a new task with default status (TODO) and priority (MEDIUM).
     */
    public static Task create(ProjectId projectId, UserId ownerId, TaskTitle title) {
        return create(projectId, ownerId, title, Priority.DEFAULT);
    }

    /**
     * Factory for a new task with an explicit priority.
     */
    public static Task create(ProjectId projectId, UserId ownerId, TaskTitle title, Priority priority) {
        Instant now = Instant.now();
        return new Task(
                TaskId.generate(), projectId, ownerId, title,
                TaskStatus.TODO, Objects.requireNonNull(priority, "Priority is required"),
                now, now
        );
    }

    /**
     * Reconstitutes a task from persisted data. Used by the infrastructure layer.
     */
    public static Task reconstitute(TaskId id, ProjectId projectId, UserId ownerId, TaskTitle title,
                                     TaskStatus status, Priority priority,
                                     Instant createdAt, Instant updatedAt) {
        return new Task(id, projectId, ownerId, title, status, priority, createdAt, updatedAt);
    }

    /**
     * Transitions the task to a new status, enforcing the transition graph.
     *
     * @throws InvalidStatusTransitionException if the transition is not allowed
     */
    public void transitionTo(TaskStatus newStatus) {
        status.assertTransitionTo(newStatus);
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    /**
     * Convenience shortcut to mark a task as done. Requires IN_PROGRESS first
     * (TODO -> DONE is not allowed).
     */
    public void complete() {
        transitionTo(TaskStatus.DONE);
    }

    public void changePriority(Priority newPriority) {
        this.priority = Objects.requireNonNull(newPriority, "Priority is required");
        this.updatedAt = Instant.now();
    }

    public TaskId getId() {
        return id;
    }

    public ProjectId getProjectId() {
        return projectId;
    }

    public UserId getOwnerId() {
        return ownerId;
    }

    public TaskTitle getTitle() {
        return title;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public Priority getPriority() {
        return priority;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Task task = (Task) o;
        return Objects.equals(id, task.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
