package com.renan.taskmanager.tasks.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle states of a task and their allowed transitions.
 *
 * <p>Allowed transition graph:</p>
 * <pre>
 *   TODO --&gt; IN_PROGRESS --&gt; DONE
 *             ↑   ↓
 *             └───┘  (DONE may reopen back to IN_PROGRESS)
 *
 *   IN_PROGRESS --&gt; TODO  (allowed: correction)
 * </pre>
 *
 * <p><b>Notable restrictions:</b></p>
 * <ul>
 *   <li>{@code TODO -> DONE} is forbidden (must pass through IN_PROGRESS).</li>
 *   <li>{@code DONE -> TODO} is forbidden (reopen via IN_PROGRESS, or create a new task).</li>
 * </ul>
 */
public enum TaskStatus {

    TODO,
    IN_PROGRESS,
    DONE;

    /**
     * Allowed transitions from each status. Same-status is always allowed (idempotent).
     */
    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED = Map.of(
            TODO, EnumSet.of(TODO, IN_PROGRESS),
            IN_PROGRESS, EnumSet.of(TODO, IN_PROGRESS, DONE),
            DONE, EnumSet.of(DONE, IN_PROGRESS)
    );

    /**
     * Returns true if this status can transition to the target status.
     *
     * <p>Package-private: the only production caller is {@link #assertTransitionTo},
     * which lives in this same compilation unit. Tests in the same package can
     * still call it; callers outside the domain package should go through
     * {@code Task.transitionTo} instead of poking the transition graph directly.</p>
     */
    boolean canTransitionTo(TaskStatus target) {
        Set<TaskStatus> allowed = ALLOWED.get(this);
        return allowed != null && allowed.contains(target);
    }

    /**
     * Asserts that the transition is valid, throwing
     * {@link InvalidStatusTransitionException} otherwise.
     */
    public void assertTransitionTo(TaskStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(this, target);
        }
    }
}
