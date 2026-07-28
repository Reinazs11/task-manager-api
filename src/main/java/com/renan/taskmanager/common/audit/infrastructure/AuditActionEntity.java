package com.renan.taskmanager.common.audit.infrastructure;

/**
 * JPA-only mirror of {@link com.renan.taskmanager.common.audit.domain.AuditAction}.
 *
 * <p>Exists as a separate type so the {@code ..infrastructure..} package owns
 * the JPA mapping concerns and the domain enum stays free of persistence
 * annotations — the same pattern used by {@code TaskStatusEntity} vs
 * {@code TaskStatus}. The CHECK constraint on {@code audit_events.action}
 * (migration V3) must stay in sync with the constants here.</p>
 */
public enum AuditActionEntity {

    PROJECT_CREATED,
    PROJECT_DELETED,
    TASK_CREATED,
    TASK_STATUS_CHANGED,
    USER_REGISTERED,
    USER_LOGIN_SUCCEEDED,
    USER_LOGIN_FAILED,
    USER_LOGOUT,
    REFRESH_ROTATED
}
