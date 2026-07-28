package com.renan.taskmanager.common.audit.domain;

import com.renan.taskmanager.common.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AuditEvent}.
 *
 * <p>Pure unit test: the entity has no collaborators, so we just exercise its
 * factories and invariants. The invariants are the security boundary —
 * especially the anti-enumeration rule that forbids an actor id on
 * {@link AuditAction#USER_LOGIN_FAILED}.</p>
 */
class AuditEventTest {

    private static final Instant NOW = Instant.parse("2026-07-28T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final UserId ACTOR = UserId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final UUID ENTITY = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String CORRELATION = "req-123";

    @Nested
    @DisplayName("record() — application-facing factory")
    class RecordFactory {

        @Test
        @DisplayName("Should generate an id and stamp occurredAt from the clock")
        void shouldGenerateIdAndTimestamp() {
            AuditEvent event = AuditEvent.record(
                    ACTOR, AuditAction.PROJECT_CREATED, AuditableEntityType.PROJECT,
                    ENTITY, CLOCK, CORRELATION, Map.of());

            assertThat(event.id()).isNotNull();
            assertThat(event.occurredAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("Should expose all fields unchanged")
        void shouldExposeFields() {
            AuditEvent event = AuditEvent.record(
                    ACTOR, AuditAction.TASK_CREATED, AuditableEntityType.TASK,
                    ENTITY, CLOCK, CORRELATION, Map.of("priority", "HIGH"));

            assertThat(event.actorId()).contains(ACTOR);
            assertThat(event.action()).isEqualTo(AuditAction.TASK_CREATED);
            assertThat(event.entityType()).isEqualTo(AuditableEntityType.TASK);
            assertThat(event.entityId()).contains(ENTITY);
            assertThat(event.correlationId()).contains(CORRELATION);
            assertThat(event.metadata()).containsEntry("priority", "HIGH");
        }

        @Test
        @DisplayName("Should allow null entityId for actions without a target entity")
        void shouldAllowNullEntityId() {
            AuditEvent event = AuditEvent.record(
                    ACTOR, AuditAction.USER_LOGOUT, AuditableEntityType.USER,
                    null, CLOCK, CORRELATION, Map.of());

            assertThat(event.entityId()).isEmpty();
        }

        @Test
        @DisplayName("Should allow null correlationId for non-HTTP callers")
        void shouldAllowNullCorrelationId() {
            AuditEvent event = AuditEvent.record(
                    ACTOR, AuditAction.USER_LOGOUT, AuditableEntityType.USER,
                    null, CLOCK, null, Map.of());

            assertThat(event.correlationId()).isEmpty();
        }

        @Test
        @DisplayName("Should reject null actor on a non-LOGIN_FAILED action")
        void shouldRequireActorExceptForLoginFailed() {
            assertThatThrownBy(() -> AuditEvent.record(
                    null, AuditAction.PROJECT_CREATED, AuditableEntityType.PROJECT,
                    ENTITY, CLOCK, CORRELATION, Map.of()))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Anti-enumeration invariant (USER_LOGIN_FAILED)")
    class AntiEnumeration {

        @Test
        @DisplayName("record(USER_LOGIN_FAILED) with null actor should succeed")
        void shouldAcceptNullActorForLoginFailed() {
            AuditEvent event = AuditEvent.record(
                    null, AuditAction.USER_LOGIN_FAILED, AuditableEntityType.USER,
                    null, CLOCK, CORRELATION, Map.of());

            assertThat(event.actorId()).isEmpty();
            assertThat(event.action()).isEqualTo(AuditAction.USER_LOGIN_FAILED);
        }

        @Test
        @DisplayName("record(USER_LOGIN_FAILED) with a known actor should be rejected")
        void shouldRejectActorForLoginFailed() {
            // Even when the app KNOWS the userId (wrong password on a valid email),
            // recording it would leak which emails exist. The entity refuses it.
            assertThatThrownBy(() -> AuditEvent.record(
                    ACTOR, AuditAction.USER_LOGIN_FAILED, AuditableEntityType.USER,
                    null, CLOCK, CORRELATION, Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("enumeration");
        }

        @Test
        @DisplayName("reconstitute(USER_LOGIN_FAILED) with a known actor should be rejected too")
        void shouldRejectActorForLoginFailedOnReconstitute() {
            // The invariant holds on both factories — the DB layer cannot smuggle
            // an actor id in either.
            assertThatThrownBy(() -> AuditEvent.reconstitute(
                    AuditEventId.generate(), ACTOR, AuditAction.USER_LOGIN_FAILED,
                    AuditableEntityType.USER, null, NOW, CORRELATION, Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Null-argument invariants (essentials)")
    class NullArgs {

        @Test
        @DisplayName("Null id → NullPointerException")
        void shouldRejectNullId() {
            assertThatThrownBy(() -> AuditEvent.reconstitute(
                    null, ACTOR, AuditAction.PROJECT_CREATED, AuditableEntityType.PROJECT,
                    ENTITY, NOW, CORRELATION, Map.of()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Null action → NullPointerException")
        void shouldRejectNullAction() {
            assertThatThrownBy(() -> AuditEvent.reconstitute(
                    AuditEventId.generate(), ACTOR, null, AuditableEntityType.PROJECT,
                    ENTITY, NOW, CORRELATION, Map.of()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Null entityType → NullPointerException")
        void shouldRejectNullEntityType() {
            assertThatThrownBy(() -> AuditEvent.reconstitute(
                    AuditEventId.generate(), ACTOR, AuditAction.PROJECT_CREATED, null,
                    ENTITY, NOW, CORRELATION, Map.of()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Null occurredAt → NullPointerException")
        void shouldRejectNullOccurredAt() {
            assertThatThrownBy(() -> AuditEvent.reconstitute(
                    AuditEventId.generate(), ACTOR, AuditAction.PROJECT_CREATED,
                    AuditableEntityType.PROJECT, ENTITY, null, CORRELATION, Map.of()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Null metadata → NullPointerException (empty map is the contract)")
        void shouldRejectNullMetadata() {
            assertThatThrownBy(() -> AuditEvent.reconstitute(
                    AuditEventId.generate(), ACTOR, AuditAction.PROJECT_CREATED,
                    AuditableEntityType.PROJECT, ENTITY, NOW, CORRELATION, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Metadata immutability (defensive copy)")
    class MetadataImmutability {

        @Test
        @DisplayName("Returned metadata should be unmodifiable")
        void shouldReturnUnmodifiableMetadata() {
            AuditEvent event = AuditEvent.record(
                    ACTOR, AuditAction.TASK_CREATED, AuditableEntityType.TASK,
                    ENTITY, CLOCK, CORRELATION, Map.of("priority", "HIGH"));

            assertThatThrownBy(() -> event.metadata().put("x", "y"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("Mutating the source map after construction should not affect the event")
        void shouldNotBeAffectedBySourceMutation() {
            var source = new java.util.HashMap<String, String>();
            source.put("priority", "HIGH");

            AuditEvent event = AuditEvent.record(
                    ACTOR, AuditAction.TASK_CREATED, AuditableEntityType.TASK,
                    ENTITY, CLOCK, CORRELATION, source);

            source.put("priority", "LOW"); // mutate after construction

            assertThat(event.metadata()).containsEntry("priority", "HIGH");
        }

        @Test
        @DisplayName("metadata() should never be null even when constructed with empty map")
        void shouldNeverReturnNullMetadata() {
            AuditEvent event = AuditEvent.record(
                    ACTOR, AuditAction.PROJECT_CREATED, AuditableEntityType.PROJECT,
                    ENTITY, CLOCK, CORRELATION, Map.of());

            assertThat(event.metadata()).isNotNull().isEmpty();
        }
    }

    @Nested
    @DisplayName("Entity equality (by id)")
    class Equality {

        @Test
        @DisplayName("Two events with the same id are equal; differing id are not")
        void shouldBeEqualById() {
            AuditEventId sharedId = AuditEventId.generate();
            AuditEvent a = AuditEvent.reconstitute(sharedId, ACTOR,
                    AuditAction.PROJECT_CREATED, AuditableEntityType.PROJECT,
                    ENTITY, NOW, CORRELATION, Map.of());
            AuditEvent b = AuditEvent.reconstitute(sharedId, ACTOR,
                    AuditAction.PROJECT_DELETED, AuditableEntityType.PROJECT, // different action!
                    ENTITY, NOW, CORRELATION, Map.of());
            AuditEvent c = AuditEvent.reconstitute(AuditEventId.generate(), ACTOR,
                    AuditAction.PROJECT_CREATED, AuditableEntityType.PROJECT,
                    ENTITY, NOW, CORRELATION, Map.of());

            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
            assertThat(a).isNotEqualTo(c);
            assertThat(a).isNotEqualTo(null);
            assertThat(a).isNotEqualTo("not-an-audit-event");
            assertThat(a).isEqualTo(a);
        }
    }

    @Nested
    @DisplayName("reconstitute() — repository-facing factory")
    class ReconstituteFactory {

        @Test
        @DisplayName("Should preserve every supplied field exactly")
        void shouldPreserveAllFields() {
            AuditEventId id = AuditEventId.generate();

            AuditEvent event = AuditEvent.reconstitute(
                    id, ACTOR, AuditAction.TASK_STATUS_CHANGED, AuditableEntityType.TASK,
                    ENTITY, NOW, CORRELATION, Map.of("from", "TODO", "to", "IN_PROGRESS"));

            assertThat(event.id()).isEqualTo(id);
            assertThat(event.actorId()).contains(ACTOR);
            assertThat(event.action()).isEqualTo(AuditAction.TASK_STATUS_CHANGED);
            assertThat(event.entityType()).isEqualTo(AuditableEntityType.TASK);
            assertThat(event.entityId()).contains(ENTITY);
            assertThat(event.occurredAt()).isEqualTo(NOW);
            assertThat(event.correlationId()).contains(CORRELATION);
            assertThat(event.metadata())
                    .containsEntry("from", "TODO")
                    .containsEntry("to", "IN_PROGRESS");
        }
    }
}
