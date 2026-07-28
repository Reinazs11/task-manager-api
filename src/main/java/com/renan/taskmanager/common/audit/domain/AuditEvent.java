package com.renan.taskmanager.common.audit.domain;

import com.renan.taskmanager.common.domain.UserId;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable record that <b>some actor performed a state-changing operation
 * at a point in time</b>. This is the core of the audit trail (issue #9).
 *
 * <p><b>Entity semantics:</b> equality is by {@link AuditEventId} (the row
 * identity), not by field values — two events capturing "user X logged in" at
 * different instants are distinct events. Every other field is immutable after
 * construction.</p>
 *
 * <h2>Invariants enforced on construction</h2>
 * <ul>
 *   <li><b>Anti-enumeration (forensic):</b> {@link AuditAction#USER_LOGIN_FAILED}
 *       never carries an actor id. The application may know the userId (wrong
 *       password on a valid email) but must not record it, otherwise the audit
 *       trail itself becomes an enumeration oracle ("this email exists"). See
 *       {@code DECISIONS.md} #6 and #21.</li>
 *   <li><b>Metadata allowlist:</b> {@code metadata} is a flat {@code Map<String,
 *       String>} populated only with non-sensitive, operational fields chosen by
 *       the caller (e.g. {@code {from, to}} for status changes). It must never
 *       contain request bodies, passwords, emails, or free text — those are PII
 *       and secret leak vectors (issue #9 "PII" trade-off). The map is stored
 *       defensively copied and returned unmodifiable.</li>
 *   <li><b>Non-null essentials:</b> {@code action}, {@code entityType} and
 *       {@code occurredAt} are mandatory for every event.</li>
 * </ul>
 *
 * <h2>Two factories, one purpose</h2>
 * <ul>
 *   <li>{@link #record} — used by the application layer when a new event
 *       happens. Generates the id and stamps {@code occurredAt = clock.instant()}
 *       so every event has a trustworthy timestamp that is also deterministic
 *       under a fixed {@link Clock} in tests.</li>
 *   <li>{@link #reconstitute} — used by the repository to hydrate from the DB.
 *       All fields come from the row, nothing is generated.</li>
 * </ul>
 */
public final class AuditEvent {

    private final AuditEventId id;
    private final UserId actorId;
    private final AuditAction action;
    private final AuditableEntityType entityType;
    private final UserId entityId;
    private final Instant occurredAt;
    private final String correlationId;
    private final Map<String, String> metadata;

    private AuditEvent(AuditEventId id,
                       UserId actorId,
                       AuditAction action,
                       AuditableEntityType entityType,
                       UserId entityId,
                       Instant occurredAt,
                       String correlationId,
                       Map<String, String> metadata) {
        if (action == AuditAction.USER_LOGIN_FAILED && actorId != null) {
            throw new IllegalArgumentException(
                    "USER_LOGIN_FAILED must never carry an actorId — recording it would "
                            + "turn the audit trail into an enumeration oracle "
                            + "(see DECISIONS.md #6, #21).");
        }
        if (action != AuditAction.USER_LOGIN_FAILED) {
            // Every other action must know who performed it; an audit row with no
            // actor is useless. Only login-failure is deliberately anonymous.
            Objects.requireNonNull(actorId, "actorId is required for " + action);
        }
        this.id = Objects.requireNonNull(id, "id is required");
        this.actorId = actorId; // null ONLY for USER_LOGIN_FAILED
        this.action = Objects.requireNonNull(action, "action is required");
        this.entityType = Objects.requireNonNull(entityType, "entityType is required");
        this.entityId = entityId; // nullable: login/logout/refresh have no entity
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt is required");
        this.correlationId = correlationId; // nullable: non-HTTP callers
        // metadata is contractually non-null (callers pass Map.of() for "nothing").
        // Rejecting null here keeps the Javadoc and the code in sync — the worst
        // audit bug is a silent default that hides a caller bug.
        Objects.requireNonNull(metadata, "metadata is required (use Map.of() for none)");
        this.metadata = Collections.unmodifiableMap(new java.util.LinkedHashMap<>(metadata));
    }

    /**
     * Records a new event as it happens. Generates the id and timestamps the
     * event using {@code clock}, so tests can pin both by injecting a
     * {@link Clock#fixed fixed} clock.
     *
     * @param actorId       who performed the action; required for every action
     *                      except {@link AuditAction#USER_LOGIN_FAILED}, where it
     *                      must be {@code null} (anti-enumeration — see class
     *                      Javadoc). Passing {@code null} for any other action
     *                      throws {@link NullPointerException}.
     * @param action        what happened
     * @param entityType    the kind of entity the action concerns
     * @param entityId      the affected entity's id, or {@code null} when the
     *                      action has no single entity target
     *                      (login/logout/refresh)
     * @param clock         source of {@code occurredAt}; never null
     * @param correlationId the request correlation id for log join, or
     *                      {@code null} if the call is not HTTP-bound
     * @param metadata      flat, non-sensitive operational context; never null
     *                      (pass an empty map when nothing applies)
     */
    public static AuditEvent record(UserId actorId,
                                    AuditAction action,
                                    AuditableEntityType entityType,
                                    UserId entityId,
                                    Clock clock,
                                    String correlationId,
                                    Map<String, String> metadata) {
        return new AuditEvent(
                AuditEventId.generate(),
                actorId,
                action,
                entityType,
                entityId,
                clock.instant(),
                correlationId,
                metadata);
    }

    /**
     * Hydrates an event from already-persisted data. Used by the repository
     * when reading back rows. Nothing is generated — every field is taken as-is.
     */
    public static AuditEvent reconstitute(AuditEventId id,
                                          UserId actorId,
                                          AuditAction action,
                                          AuditableEntityType entityType,
                                          UserId entityId,
                                          Instant occurredAt,
                                          String correlationId,
                                          Map<String, String> metadata) {
        return new AuditEvent(id, actorId, action, entityType, entityId, occurredAt,
                correlationId, metadata);
    }

    public AuditEventId id() {
        return id;
    }

    public Optional<UserId> actorId() {
        return Optional.ofNullable(actorId);
    }

    public AuditAction action() {
        return action;
    }

    public AuditableEntityType entityType() {
        return entityType;
    }

    public Optional<UserId> entityId() {
        return Optional.ofNullable(entityId);
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public Optional<String> correlationId() {
        return Optional.ofNullable(correlationId);
    }

    /**
     * The metadata map, unmodifiable. Never null (empty map when nothing was
     * recorded).
     */
    public Map<String, String> metadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditEvent that = (AuditEvent) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
