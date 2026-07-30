package com.renan.taskmanager.users.domain;

/**
 * Raised when a plain password violates the registration policy.
 */
public class InvalidPasswordException extends RuntimeException {

    public InvalidPasswordException(String message) {
        super(message);
    }
}
