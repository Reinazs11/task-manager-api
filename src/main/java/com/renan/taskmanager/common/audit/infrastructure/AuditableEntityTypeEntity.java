package com.renan.taskmanager.common.audit.infrastructure;

/**
 * JPA-only mirror of {@link com.renan.taskmanager.common.audit.domain.AuditableEntityType}.
 *
 * <p>Same rationale as {@link AuditActionEntity}: keep JPA concerns out of the
 * domain enum. The CHECK constraint on {@code audit_events.entity_type}
 * (migration V3) must stay in sync with the constants here.</p>
 */
public enum AuditableEntityTypeEntity {

    PROJECT,
    TASK,
    USER
}
