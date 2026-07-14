package com.renan.taskmanager.tasks.domain;

/**
 * Domain exception: a task attempted an illegal status transition.
 *
 * <p>Translated to HTTP 409 Conflict (business rule violation) by the
 * global exception handler.</p>
 */
public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(TaskStatus from, TaskStatus to) {
        super("Cannot transition task from " + from + " to " + to);
    }
}
