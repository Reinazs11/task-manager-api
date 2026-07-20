package com.renan.taskmanager.common.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Value Object representing a unique user identifier, shared across bounded
 * contexts.
 *
 * <p><b>Why does this live in {@code common.domain} (and not in
 * {@code users.domain})?</b>
 * Because the {@code tasks} context needs to reference "the user who owns a
 * project/task" without depending on the {@code users} context as a whole. The
 * shared-kernel pattern keeps the type-safety of a dedicated value object
 * (the compiler still rejects passing a {@code ProjectId} where a
 * {@code UserId} is expected) while letting two contexts collaborate on the
 * single concept they actually share: user identity.</p>
 *
 * <p><b>Why UUID instead of sequential Long?</b>
 * - Prevents enumeration attacks (attackers can't guess other IDs).
 * - Allows client-side / distributed generation without collisions.
 * - Friendly for distributed systems and microservices.</p>
 *
 * <p><b>Why a value object instead of raw UUID?</b>
 * Type safety. If a method accepts {@code UserId}, the compiler prevents
 * accidentally passing a {@code ProjectId}. With raw UUID, nothing distinguishes
 * the two.</p>
 */
public final class UserId {

    private final UUID value;

    private UserId(UUID value) {
        this.value = Objects.requireNonNull(value, "UserId cannot be null");
    }

    /**
     * Generates a new random UserId (UUIDv4).
     */
    public static UserId generate() {
        return new UserId(UUID.randomUUID());
    }

    /**
     * Creates a UserId from an existing UUID (e.g. when hydrating from the database).
     */
    public static UserId of(UUID uuid) {
        return new UserId(uuid);
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserId userId = (UserId) o;
        return Objects.equals(value, userId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
