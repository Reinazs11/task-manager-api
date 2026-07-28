package com.renan.taskmanager.common.audit.application;

import com.renan.taskmanager.common.audit.domain.AuditAction;
import com.renan.taskmanager.common.audit.domain.AuditEvent;
import com.renan.taskmanager.common.audit.domain.AuditEventId;
import com.renan.taskmanager.common.audit.domain.AuditableEntityType;
import com.renan.taskmanager.common.audit.infrastructure.AuditActionEntity;
import com.renan.taskmanager.common.audit.infrastructure.AuditEventEntity;
import com.renan.taskmanager.common.audit.infrastructure.AuditableEntityTypeEntity;
import com.renan.taskmanager.common.domain.UserId;
import org.mapstruct.Mapper;

import java.util.Map;
import java.util.Optional;

/**
 * MapStruct mapper: translates between the domain {@link AuditEvent} and the
 * JPA {@link AuditEventEntity}.
 *
 * <p>Same rationale as the other mappers ({@code TaskMapper}, {@code
 * RevokedRefreshTokenMapper}): keep JPA out of the domain, be explicit about
 * value-object and enum translation, and rely on compile-time code generation.
 * Methods are hand-written because the translation involves unwrapping
 * {@link UserId}/{@link AuditEventId} and converting between the domain and
 * infrastructure enums — MapStruct only supplies the Spring component glue.</p>
 */
@Mapper(componentModel = "spring")
public interface AuditEventMapper {

    /**
     * Domain → Entity. Used when persisting a new audit event.
     *
     * <p>{@code metadata} is defensively copied into a new map: the entity
     * becomes the single owner of what gets written, so a later mutation of
     * the source cannot leak into the persisted row.</p>
     */
    default AuditEventEntity toEntity(AuditEvent event) {
        return AuditEventEntity.builder()
                .id(event.id().value())
                .actorId(event.actorId().map(UserId::value).orElse(null))
                .action(AuditActionEntity.valueOf(event.action().name()))
                .entityType(AuditableEntityTypeEntity.valueOf(event.entityType().name()))
                .entityId(event.entityId().map(UserId::value).orElse(null))
                .occurredAt(event.occurredAt())
                .correlationId(event.correlationId().orElse(null))
                .metadata(Map.copyOf(event.metadata()))
                .build();
    }

    /**
     * Entity → Domain. Used when loading from the database (the audit read
     * endpoint and repository-level reads). {@code metadata} is wrapped in an
     * unmodifiable map by {@link AuditEvent} on reconstitution.
     */
    default AuditEvent toDomain(AuditEventEntity entity) {
        UserId actor = Optional.ofNullable(entity.getActorId()).map(UserId::of).orElse(null);
        UserId target = Optional.ofNullable(entity.getEntityId()).map(UserId::of).orElse(null);
        return AuditEvent.reconstitute(
                AuditEventId.of(entity.getId()),
                actor,
                AuditAction.valueOf(entity.getAction().name()),
                AuditableEntityType.valueOf(entity.getEntityType().name()),
                target,
                entity.getOccurredAt(),
                entity.getCorrelationId(),
                entity.getMetadata() == null ? Map.of() : entity.getMetadata()
        );
    }
}
