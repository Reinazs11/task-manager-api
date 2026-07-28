package com.renan.taskmanager.common.audit.domain;

/**
 * The set of state-changing operations captured by the audit trail.
 *
 * <p><b>Why an enum instead of free strings?</b> The CHECK constraint on
 * {@code audit_events.action} (migration {@code V3__audit_events.sql}) and this
 * enum are the two sides of the same allowlist: the DB rejects anything the
 * application did not intend to record, and the application can only emit
 * values the DB accepts. A drift between the two surfaces as a runtime error
 * rather than silent data corruption.</p>
 *
 * <p><b>Reads are deliberately absent.</b> Auditing GET/list operations would
 * flood the trail (issue #9: "reads are too noisy"). Only operations that
 * mutate state, plus the auth events that carry forensic value, are here.</p>
 *
 * <p><b>Anti-enumeration note:</b> {@link #USER_LOGIN_FAILED} never carries an
 * actor id, even when the application could resolve one — see
 * {@link AuditEvent} and {@code DECISIONS.md} #21.</p>
 */
public enum AuditAction {

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
