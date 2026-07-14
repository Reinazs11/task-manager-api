package com.renan.taskmanager.tasks.domain;

import com.renan.taskmanager.users.domain.UserId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Project aggregate root.
 *
 * <p>A Project owns its tasks: tasks are added via {@link #addTask}, and the
 * project guarantees that every task carries the project's id and owner.</p>
 *
 * <p><b>Why is this an aggregate root?</b>
 * It's the consistency boundary: any operation that creates or modifies a task
 * must go through the owning Project. This prevents orphan tasks and ensures
 * the project's invariants (owner identity, task count, etc.) always hold.</p>
 */
public class Project {

    private final ProjectId id;
    private final UserId ownerId;
    private String name;
    private final List<Task> tasks;
    private final Instant createdAt;
    private Instant updatedAt;

    private Project(ProjectId id, UserId ownerId, String name,
                    List<Task> tasks, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "ProjectId is required");
        this.ownerId = Objects.requireNonNull(ownerId, "OwnerId is required");
        this.name = Objects.requireNonNull(name, "Name is required");
        this.tasks = new ArrayList<>(tasks);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    /**
     * Factory for a new empty project.
     */
    public static Project create(UserId ownerId, String name) {
        Instant now = Instant.now();
        return new Project(ProjectId.generate(), ownerId, name, new ArrayList<>(), now, now);
    }

    /**
     * Reconstitutes a project from persisted data.
     */
    public static Project reconstitute(ProjectId id, UserId ownerId, String name,
                                        List<Task> tasks, Instant createdAt, Instant updatedAt) {
        return new Project(id, ownerId, name, tasks, createdAt, updatedAt);
    }

    /**
     * Adds a new task to this project. The task inherits the project's id and owner.
     *
     * @return a {@link TaskAdded} record carrying the created task
     * @throws IllegalArgumentException if the title is null or invalid
     */
    public TaskAdded addTask(TaskTitle title) {
        if (title == null) {
            throw new IllegalArgumentException("Title cannot be null");
        }
        Task task = Task.create(id, ownerId, title);
        tasks.add(task);
        this.updatedAt = Instant.now();
        return new TaskAdded(task);
    }

    /**
     * Returns an unmodifiable view of the project's tasks.
     * Modifications must go through {@link #addTask} to preserve invariants.
     */
    public List<Task> tasks() {
        return Collections.unmodifiableList(tasks);
    }

    public ProjectId getId() {
        return id;
    }

    public UserId getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Return type of {@link #addTask}: carries the newly created task.
     * Using a named record (instead of bare Task) leaves room to extend the
     * result with metadata (events, flags) without breaking callers.
     */
    public record TaskAdded(Task task) {
        public TaskAdded {
            java.util.Objects.requireNonNull(task, "task cannot be null");
        }
    }
}
