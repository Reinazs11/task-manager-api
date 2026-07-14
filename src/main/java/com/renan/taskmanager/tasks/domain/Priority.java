package com.renan.taskmanager.tasks.domain;

/**
 * Priority levels for a task.
 *
 * <p>Unlike {@link TaskStatus}, priority has no transition rules: it can be
 * changed freely at any time. The {@link #weight()} method provides a stable
 * ordering (lower weight = lower priority).</p>
 */
public enum Priority {

    LOW(1),
    MEDIUM(5),
    HIGH(10);

    /** The default priority assigned to a new task when none is specified. */
    public static final Priority DEFAULT = MEDIUM;

    private final int weight;

    Priority(int weight) {
        this.weight = weight;
    }

    /**
     * Numeric weight used for sorting tasks by priority.
     * Higher value = higher priority.
     */
    public int weight() {
        return weight;
    }
}
