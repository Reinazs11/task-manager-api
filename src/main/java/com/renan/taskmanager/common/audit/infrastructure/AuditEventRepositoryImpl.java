package com.renan.taskmanager.common.audit.infrastructure;

import com.renan.taskmanager.common.audit.application.AuditEventMapper;
import com.renan.taskmanager.common.audit.application.AuditEventQueryPort;
import com.renan.taskmanager.common.audit.domain.AuditAction;
import com.renan.taskmanager.common.audit.domain.AuditEvent;
import com.renan.taskmanager.common.audit.domain.AuditEventRepository;
import com.renan.taskmanager.common.domain.UserId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter: implements the domain {@link AuditEventRepository} (write) and the
 * application {@link AuditEventQueryPort} (read) ports using JPA against the
 * {@code audit_events} table.
 *
 * <p>Mirrors {@code ProjectRepositoryImpl}: one infrastructure class may
 * implement both the write port and the read port for the same aggregate. It
 * holds the Spring Data interface and the {@link AuditEventMapper}, and is the
 * only class in this context that knows about JPA.</p>
 */
@Repository
public class AuditEventRepositoryImpl implements AuditEventRepository, AuditEventQueryPort {

    private final AuditEventJpaRepository jpaRepository;
    private final AuditEventMapper mapper;

    public AuditEventRepositoryImpl(AuditEventJpaRepository jpaRepository, AuditEventMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public void save(AuditEvent event) {
        jpaRepository.save(mapper.toEntity(event));
    }

    @Override
    public Optional<AuditEvent> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Page<AuditEvent> findByActor(UserId actorId, AuditAction action, Pageable pageable) {
        if (action == null) {
            return jpaRepository
                    .findByActorIdOrderByOccurredAtDesc(actorId.value(), pageable)
                    .map(mapper::toDomain);
        }
        return jpaRepository
                .findByActorIdAndActionOrderByOccurredAtDesc(
                        actorId.value(), AuditActionEntity.valueOf(action.name()), pageable)
                .map(mapper::toDomain);
    }
}
