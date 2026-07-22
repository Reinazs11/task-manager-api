package com.renan.taskmanager.tasks.domain;

/**
 * Priority levels for a task.
 *
 * <p>Unlike {@link TaskStatus}, priority has no transition rules: it can be
 * changed freely at any time via {@code Task.changePriority}.</p>
 *
 * <p><b>Why no numeric {@code weight()} method?</b>
 * The previous version exposed a {@code weight()} for sorting, but no query
 * ever used it — there is no ORDER BY priority in the project. Dead surface
 * area on a public API is a code smell a reviewer notices. If priority-based
 * sorting becomes a real requirement, the right place is a Spring Data
 * {@code OrderBy} clause (which compares the enum name or ordinal natively),
 * not a hand-rolled weight field.</p>
 */
public enum Priority {

    LOW,
    MEDIUM,
    HIGH;

    /** The default priority assigned to a new task when none is specified. */
    public static final Priority DEFAULT = MEDIUM;
}
