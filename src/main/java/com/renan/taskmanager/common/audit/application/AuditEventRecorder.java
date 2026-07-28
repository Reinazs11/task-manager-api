package com.renan.taskmanager.common.audit.application;

import com.renan.taskmanager.common.audit.domain.AuditAction;
import com.renan.taskmanager.common.audit.domain.AuditEvent;
import com.renan.taskmanager.common.audit.domain.AuditEventRepository;
import com.renan.taskmanager.common.audit.domain.AuditableEntityType;
import com.renan.taskmanager.common.domain.UserId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * The single entry point through which use cases record audit events.
 *
 * <p>Each state-changing use case calls exactly one of the {@code recordXxx}
 * methods after its business mutation has succeeded. The recorder builds the
 * {@link AuditEvent} (with the injected {@link Clock} for a deterministic
 * timestamp and the {@link CorrelationIdProvider} for the request id) and
 * persists it.</p>
 *
 * <h2>Transactional semantics (DECISIONS.md #21)</h2>
 * <ul>
 *   <li>Default propagation is {@link Propagation#REQUIRED}: when called from
 *       a {@code @Transactional} use case (the normal case), the audit insert
 *       <b>joins</b> the use case's transaction. If the use case rolls back,
 *       the audit row is discarded too — a failed action leaves no trail
 *       claiming it succeeded.</li>
 *   <li>The one exception is {@link #recordLoginFailed()}: login failure is
 *       the most valuable forensic signal, so the event must survive the
 *       {@code InvalidCredentialsException} that follows. That method runs in
 *       {@link Propagation#REQUIRES_NEW}, opening a fresh transaction that
 *       commits before the exception propagates. The caller is NOT
 *       transactional ({@code LoginUseCase} is not {@code @Transactional}),
 *       so {@code REQUIRES_NEW} commits immediately and independently.</li>
 * </ul>
 *
 * <h2>PII / anti-enumeration (issue #9, DECISIONS.md #6)</h2>
 * <ul>
 *   <li>Each {@code recordXxx} passes a fixed, allowlisted {@code metadata}
 *       map — never the request body. See method Javadocs for what each one
 *       records.</li>
 *   <li>{@link #recordLoginFailed()} always passes {@code null} for the actor,
 *       even when the caller could resolve one. The {@link AuditEvent}
 *     constructor enforces this invariant a second time (defense in depth).</li>
 * </ul>
 */
@Component
public class AuditEventRecorder {

    private final AuditEventRepository repository;
    private final Clock clock;
    private final CorrelationIdProvider correlationIdProvider;

    public AuditEventRecorder(AuditEventRepository repository,
                              Clock clock,
                              CorrelationIdProvider correlationIdProvider) {
        this.repository = repository;
        this.clock = clock;
        this.correlationIdProvider = correlationIdProvider;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordProjectCreated(UserId actor, UUID projectId) {
        repository.save(build(actor, AuditAction.PROJECT_CREATED,
                AuditableEntityType.PROJECT, projectId, Map.of()));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordProjectDeleted(UserId actor, UUID projectId) {
        repository.save(build(actor, AuditAction.PROJECT_DELETED,
                AuditableEntityType.PROJECT, projectId, Map.of()));
    }

    /**
     * @param priority the new task's priority ({@code LOW}/{@code MEDIUM}/{@code HIGH}).
     *                 Operational and non-sensitive; allowlisted by design.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordTaskCreated(UserId actor, UUID taskId, String priority) {
        repository.save(build(actor, AuditAction.TASK_CREATED,
                AuditableEntityType.TASK, taskId, Map.of("priority", priority)));
    }

    /**
     * @param from the task's status before the transition ({@code TODO}/...)
     * @param to   the task's status after the transition
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordTaskStatusChanged(UserId actor, UUID taskId, String from, String to) {
        repository.save(build(actor, AuditAction.TASK_STATUS_CHANGED,
                AuditableEntityType.TASK, taskId, Map.of("from", from, "to", to)));
    }

    /**
     * @param newUserId the id of the freshly created user; recorded as BOTH
     *                  actor and entity (the user created themselves).
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordUserRegistered(UserId newUserId) {
        repository.save(build(newUserId, AuditAction.USER_REGISTERED,
                AuditableEntityType.USER, newUserId.value(), Map.of()));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordLoginSucceeded(UserId actor) {
        repository.save(build(actor, AuditAction.USER_LOGIN_SUCCEEDED,
                AuditableEntityType.USER, null, Map.of()));
    }

    /**
     * Records a failed login attempt. <b>Anti-enumeration:</b> deliberately
     * takes NO actor id — the caller does not pass one, so the event cannot
     * leak whether the attempted email exists. Runs in a fresh transaction so
     * the row commits before {@code InvalidCredentialsException} is thrown.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLoginFailed() {
        repository.save(build(null, AuditAction.USER_LOGIN_FAILED,
                AuditableEntityType.USER, null, Map.of()));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordLogout(UserId actor) {
        repository.save(build(actor, AuditAction.USER_LOGOUT,
                AuditableEntityType.USER, null, Map.of()));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordRefreshRotated(UserId actor) {
        repository.save(build(actor, AuditAction.REFRESH_ROTATED,
                AuditableEntityType.USER, null, Map.of()));
    }

    private AuditEvent build(UserId actor, AuditAction action,
                             AuditableEntityType entityType, UUID entityId,
                             Map<String, String> metadata) {
        return AuditEvent.record(actor, action, entityType, entityId, clock,
                correlationIdProvider.current().orElse(null), metadata);
    }
}
