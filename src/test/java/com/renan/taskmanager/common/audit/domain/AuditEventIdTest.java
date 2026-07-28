package com.renan.taskmanager.common.audit.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AuditEventId}. Mirrors {@code ProjectIdTest}, since
 * the value objects share the same shape (limitation [2]: triplicated ID
 * classes).
 */
class AuditEventIdTest {

    @Test
    @DisplayName("generate() should produce two distinct ids")
    void shouldGenerateRandom() {
        AuditEventId a = AuditEventId.generate();
        AuditEventId b = AuditEventId.generate();

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("of(uuid) should expose the wrapped value")
    void shouldCreateFromUUID() {
        UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        AuditEventId id = AuditEventId.of(uuid);

        assertThat(id.value()).isEqualTo(uuid);
    }

    @Test
    @DisplayName("of(null) should reject with a message naming AuditEventId")
    void shouldRejectNullUUID() {
        assertThatThrownBy(() -> AuditEventId.of(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("AuditEventId");
    }

    @Test
    @DisplayName("toString should render the wrapped UUID")
    void shouldRenderUUID() {
        UUID uuid = UUID.randomUUID();
        AuditEventId id = AuditEventId.of(uuid);

        assertThat(id).hasToString(uuid.toString());
    }

    @Nested
    @DisplayName("Equality contract")
    class Equality {

        @Test
        @DisplayName("Should be equal when wrapping the same UUID")
        void shouldBeEqualWithSameUUID() {
            UUID uuid = UUID.randomUUID();
            AuditEventId a = AuditEventId.of(uuid);
            AuditEventId b = AuditEventId.of(uuid);

            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }

        @Test
        @DisplayName("Should differ across distinct UUIDs")
        void shouldDifferAcrossUUIDs() {
            AuditEventId a = AuditEventId.of(UUID.randomUUID());
            AuditEventId b = AuditEventId.of(UUID.randomUUID());

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("Should not equal null, foreign types, or self-equal trivially")
        void shouldRespectEqualsContract() {
            AuditEventId id = AuditEventId.generate();

            assertThat(id).isNotEqualTo(null);
            assertThat(id).isNotEqualTo("not-an-id");
            assertThat(id).isEqualTo(id);
        }
    }
}
