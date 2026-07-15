package com.renan.taskmanager.tasks.domain;

/**
 * Domain exception: a task lookup did not find the requested task.
 *
 * <p>Translated to HTTP 404 by the global exception handler.</p>
 */
public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException(java.util.UUID id) {
        super("Task not found with id: " + id);
    }
}
