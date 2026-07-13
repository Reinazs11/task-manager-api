package com.renan.taskmanager.users.domain;

/**
 * Domain exception: a registration attempt used an email already in use.
 *
 * <p>Translated to HTTP 409 Conflict by the global exception handler.</p>
 */
public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(Email email) {
        super("A user with email '" + email.value() + "' already exists");
    }
}
