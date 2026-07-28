package com.renan.taskmanager.common.audit.domain;

/**
 * The kind of entity a given {@link AuditEvent} concerns.
 *
 * <p>{@link AuditAction} already implies the entity type for most events, but
 * storing it explicitly lets the audit table be indexed and queried by entity
 * type without parsing action names — and the CHECK constraint on
 * {@code audit_events.entity_type} (migration {@code V3__audit_events.sql})
 * mirrors this enum at the DB level.</p>
 */
public enum AuditableEntityType {

    PROJECT,
    TASK,
    USER
}
