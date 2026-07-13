package com.renan.taskmanager.users.api;

import java.util.UUID;

/**
 * Public representation of a user, returned on registration and profile reads.
 *
 * <p><b>Why a separate response DTO?</b>
 * Never expose the entity directly — it would leak the password hash and other
 * internal fields. A dedicated DTO makes the public contract explicit and stable
 * even if the internal schema changes.</p>
 *
 * @param id        user identifier
 * @param email     user email
 * @param name      optional display name (may be null)
 */
public record UserResponse(
        UUID id,
        String email,
        String name
) {}
