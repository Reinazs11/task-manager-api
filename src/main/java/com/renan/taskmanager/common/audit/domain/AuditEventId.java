package com.renan.taskmanager.common.audit.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Value Object that identifies an {@link AuditEvent}.
 *
 * <p>Type safety: a method accepting {@code AuditEventId} cannot accidentally
 * receive a {@code ProjectId}/{@code TaskId}/{@code UserId}, even though all
 * wrap a UUID. Follows the same convention as the other domain IDs (see
 * {@code DECISIONS.md} limitation [2] for the triplication debt).</p>
 */
public final class AuditEventId {

    private final UUID value;

    private AuditEventId(UUID value) {
        this.value = Objects.requireNonNull(value, "AuditEventId cannot be null");
    }

    public static AuditEventId generate() {
        return new AuditEventId(UUID.randomUUID());
    }

    public static AuditEventId of(UUID uuid) {
        return new AuditEventId(uuid);
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditEventId that = (AuditEventId) o;
        return Objects.equals(value, that.value);
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
