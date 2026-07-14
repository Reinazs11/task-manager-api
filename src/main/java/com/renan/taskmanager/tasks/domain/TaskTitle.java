package com.renan.taskmanager.tasks.domain;

import java.util.Objects;

/**
 * Value Object representing a validated task title.
 *
 * <p>Rules:</p>
 * <ul>
 *   <li>Not null, not blank</li>
 *   <li>At most 200 characters (after trimming)</li>
 * </ul>
 *
 * <p>Trimming is intentional: a title of spaces only is rejected, and
 * surrounding whitespace doesn't pollute the stored value.</p>
 */
public final class TaskTitle {

    private static final int MAX_LENGTH = 200;

    private final String value;

    public TaskTitle(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Task title cannot be null");
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("Task title cannot be blank");
        }
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Task title must be at most " + MAX_LENGTH + " characters");
        }
        this.value = trimmed;
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaskTitle taskTitle = (TaskTitle) o;
        return Objects.equals(value, taskTitle.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
