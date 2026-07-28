package com.renan.taskmanager.common.audit.api;

import com.renan.taskmanager.common.audit.domain.AuditEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Public representation of an audit event, returned by {@code GET /audit/events}.
 *
 * <p><b>Why expose actorId/entityId at all?</b> The query is already scoped to
 * the caller's own id (see {@code ListAuditEventsUseCase}), so the actor is
 * always "you" — there is no cross-user leakage. {@code entityId} lets a client
 * link an event back to the resource it affected (e.g. "which project did I
 * delete?"). {@code correlationId} is included so a support engineer can join
 * the event to the structured logs of the same request.</p>
 */
public record AuditEventResponse(
        UUID id,
        String action,
        String entityType,
        UUID entityId,
        Instant occurredAt,
        String correlationId,
        Map<String, String> metadata
) {

    public static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(
                event.id().value(),
                event.action().name(),
                event.entityType().name(),
                event.entityId().map(com.renan.taskmanager.common.domain.UserId::value).orElse(null),
                event.occurredAt(),
                event.correlationId().orElse(null),
                event.metadata()
        );
    }
}
