package com.renan.taskmanager.tasks.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Value Object that identifies a Project.
 *
 * <p>Type safety: a method accepting {@code ProjectId} cannot accidentally
 * receive a {@code TaskId} or {@code UserId}, even though all wrap a UUID.</p>
 */
public final class ProjectId {

    private final UUID value;

    private ProjectId(UUID value) {
        this.value = Objects.requireNonNull(value, "ProjectId cannot be null");
    }

    public static ProjectId generate() {
        return new ProjectId(UUID.randomUUID());
    }

    public static ProjectId of(UUID uuid) {
        return new ProjectId(uuid);
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProjectId projectId = (ProjectId) o;
        return Objects.equals(value, projectId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
