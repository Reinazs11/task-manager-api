package com.renan.taskmanager.tasks.domain;

/**
 * Domain exception: a project lookup did not find the requested project.
 *
 * <p>Translated to HTTP 404 by the global exception handler.</p>
 */
public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException(java.util.UUID id) {
        super("Project not found with id: " + id);
    }
}
