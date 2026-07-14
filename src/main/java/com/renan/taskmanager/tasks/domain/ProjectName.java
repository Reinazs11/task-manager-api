package com.renan.taskmanager.tasks.domain;

import java.util.Objects;

/**
 * Value Object representing a validated project name.
 *
 * <p>Same rules as {@link TaskTitle} for consistency across the tasks context:
 * not blank, at most 200 characters, trimmed.</p>
 */
public final class ProjectName {

    private static final int MAX_LENGTH = 200;

    private final String value;

    public ProjectName(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Project name cannot be null");
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("Project name cannot be blank");
        }
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Project name must be at most " + MAX_LENGTH + " characters");
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
        ProjectName that = (ProjectName) o;
        return Objects.equals(value, that.value);
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
