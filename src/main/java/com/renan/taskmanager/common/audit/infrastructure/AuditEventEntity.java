package com.renan.taskmanager.common.audit.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity for {@link com.renan.taskmanager.common.audit.domain.AuditEvent}
 * persistence against the {@code audit_events} table (migration V3).
 *
 * <p><b>Why a separate entity and not @Entity on the domain class?</b>
 * Same rationale as every other entity in this codebase: keep JPA annotations
 * off the domain so it stays pure and framework-agnostic. {@link
 * com.renan.taskmanager.common.audit.application.AuditEventMapper} translates
 * between the two.</p>
 *
 * <p><b>JSONB mapping:</b> {@code metadata} uses Hibernate 6's
 * {@link JdbcTypeCode} with {@link SqlTypes#JSON}, which maps a
 * {@code jsonb} column to a {@link Map} with no extra dependencies. Postgres
 * serialises/parses the JSON; the domain sees a plain {@code Map<String,
 * String>}.</p>
 *
 * <p><b>Nullability mirrors the migration:</b> {@code actor_id},
 * {@code entity_id} and {@code correlation_id} are nullable because some
 * events legitimately have no value (login failure, logout, non-HTTP caller).
 * Every other column is {@code nullable = false}.</p>
 */
@Entity
@Table(name = "audit_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEventEntity {

    /**
     * Primary key. Unlike {@code ProjectEntity}/{@code TaskEntity}, this entity
     * does NOT use {@code @GeneratedValue}: the id is always assigned by the
     * domain ({@link com.renan.taskmanager.common.audit.domain.AuditEvent#record}
     * calls {@code AuditEventId.generate()}). This matches the {@code
     * RevokedRefreshTokenEntity} precedent (PK assigned, not DB-generated) and
     * keeps the domain in full control of identity — important for an
     * append-only audit table where the id is meaningful the instant it is
     * created, before any flush.
     */
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Who performed the action. Null only for USER_LOGIN_FAILED. */
    @Column(name = "actor_id")
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private AuditActionEntity action;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false)
    private AuditableEntityTypeEntity entityType;

    /** The affected entity's id, or null when the action has no single target. */
    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /** Request correlation id for joining with structured logs. */
    @Column(name = "correlation_id")
    private String correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> metadata;
}
