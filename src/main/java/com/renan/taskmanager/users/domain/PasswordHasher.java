package com.renan.taskmanager.users.domain;

/**
 * Port (DDD): contract for password hashing.
 *
 * <p>The domain defines <b>what</b> it needs (hash and verify); the
 * infrastructure decides <b>how</b> (BCrypt, Argon2, etc.). This keeps crypto
 * details out of the domain so it stays testable and framework-agnostic.</p>
 *
 * <p><b>Why two methods and not just a Password method?</b>
 * Hashing is one-directional. We need a separate {@link #matches} to verify
 * a plain password against a stored hash.</p>
 */
public interface PasswordHasher {

    /**
     * Hashes a plain password using the configured algorithm.
     *
     * @param plainPassword validated plain password
     * @return the resulting hash (include salt/params so it's self-describing)
     */
    String hash(Password plainPassword);

    /**
     * Verifies that a plain password matches a stored hash.
     *
     * @param plainPassword password supplied at login
     * @param hash          previously stored hash
     * @return true if they match
     */
    boolean matches(Password plainPassword, String hash);
}
