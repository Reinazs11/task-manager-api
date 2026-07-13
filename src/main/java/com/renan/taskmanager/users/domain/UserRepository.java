package com.renan.taskmanager.users.domain;

import java.util.Optional;

/**
 * Port (DDD): contract for persisting users.
 *
 * <p>Defined in the domain layer, implemented by infrastructure. The domain
 * stays pure (no JPA, no Spring Data) and testable in isolation by mocking
 * this interface.</p>
 *
 * <p><b>Why "Optional" instead of returning null?</b>
 * Optional makes the "might not exist" possibility explicit at the type level.
 * Callers cannot forget to handle the missing case.</p>
 */
public interface UserRepository {

    /**
     * Persists a user (insert or update based on existence).
     *
     * @return the saved user, with any generated fields refreshed
     */
    User save(User user);

    /**
     * Finds a user by email.
     *
     * @return the user, or empty if not found
     */
    Optional<User> findByEmail(Email email);

    /**
     * Checks if a user exists with the given email.
     *
     * <p>Useful for registration checks without loading the whole aggregate.</p>
     */
    boolean existsByEmail(Email email);

    /**
     * Removes all users. Used by integration tests to guarantee isolation.
     *
     * <p><b>Note:</b> this is a test-only operation. We expose it on the port
     * (rather than reaching into the JPA repo from tests) so the domain contract
     * is the single entry point for persistence, even in tests.</p>
     */
    void deleteAll();
}
