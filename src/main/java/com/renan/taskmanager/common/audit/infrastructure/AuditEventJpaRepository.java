package com.renan.taskmanager.common.audit.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AuditEventEntity}.
 *
 * <p>Infrastructure-layer interface — the domain never references it directly.
 * {@link AuditEventRepositoryImpl} adapts it to the domain
 * {@link com.renan.taskmanager.common.audit.domain.AuditEventRepository} and
 * {@link com.renan.taskmanager.common.audit.application.AuditEventQueryPort}
 * ports.</p>
 */
public interface AuditEventJpaRepository extends JpaRepository<AuditEventEntity, UUID> {

    /**
     * Derived query backing "show me MY recent activity". Filtered by actor and
     * newest-first via the {@code idx_audit_events_actor} index.
     */
    Page<AuditEventEntity> findByActorIdOrderByOccurredAtDesc(UUID actorId, Pageable pageable);

    /**
     * Derived query backing "my recent activity of a specific action" (e.g.
     * every project I deleted). Same index covers the actor + action filter.
     */
    Page<AuditEventEntity> findByActorIdAndActionOrderByOccurredAtDesc(
            UUID actorId, AuditActionEntity action, Pageable pageable);
}
