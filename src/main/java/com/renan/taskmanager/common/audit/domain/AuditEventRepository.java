package com.renan.taskmanager.common.audit.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Port (DDD): contract for the audit-event store (write side).
 *
 * <p>Defined in the domain layer, implemented by infrastructure (JPA against
 * the {@code audit_events} table). The domain stays pure (no JPA, no Spring
 * Data) and is mockable in unit tests of the recorder and use cases.</p>
 *
 * <p><b>Why this lives in {@code common.audit} (shared kernel)?</b> Both the
 * {@code tasks} and {@code users} contexts need to record audit events, and
 * the architecture rules forbid direct {@code tasks} ↔ {@code users}
 * dependencies. Placing the port in the shared kernel lets every context
 * depend on it without coupling to each other — the same pattern already used
 * by {@code common.domain.UserId}.</p>
 *
 * <p><b>Why no {@code save} return value?</b> An {@link AuditEvent} is
 * append-only; there is nothing the caller needs back. Callers that want the
 * persisted identity already supplied it via the domain object.</p>
 */
public interface AuditEventRepository {

    /**
     * Persists an audit event. The caller is responsible for transaction
     * semantics: under the standard {@code REQUIRED} propagation used by the
     * recorder, this insert joins the use case's transaction so that a business
     * rollback discards the event too (atomicity, {@code DECISIONS.md} #21).
     */
    void save(AuditEvent event);

    /**
     * Loads a single event by id. Read-side helper used by the query use case
     * and tests; returns empty when the id is unknown rather than throwing.
     */
    Optional<AuditEvent> findById(UUID id);

    /**
     * Removes every audit row. Test-only convenience (named explicitly to
     * signal intent) used by integration tests to start from a clean table.
     * Mirrors {@code RevokedRefreshTokenRepository.deleteAll}.
     */
    void deleteAllForTest();
}
