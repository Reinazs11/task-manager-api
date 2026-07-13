package com.renan.taskmanager.users.domain;

/**
 * Domain exception: credentials provided at login do not match any user.
 *
 * <p>Translated to HTTP 401 Unauthorized by the global exception handler.</p>
 *
 * <p><b>Why a single "invalid credentials" and not separate "user not found"
 * vs "wrong password"?</b>
 * Security: distinct error messages would let attackers enumerate accounts
 * ("this email exists, just wrong password"). One generic message avoids that.</p>
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
