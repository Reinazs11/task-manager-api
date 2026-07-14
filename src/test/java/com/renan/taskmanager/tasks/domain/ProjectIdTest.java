package com.renan.taskmanager.tasks.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the {@link ProjectId} value object.
 *
 * <p>ProjectId wraps UUID for type safety: a method accepting {@code ProjectId}
 * cannot accidentally receive a {@code TaskId} or {@code UserId}.</p>
 */
class ProjectIdTest {

    @Test
    @DisplayName("Should generate a new random ProjectId")
    void shouldGenerateRandom() {
        ProjectId id1 = ProjectId.generate();
        ProjectId id2 = ProjectId.generate();

        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    @DisplayName("Should create from existing UUID")
    void shouldCreateFromUUID() {
        UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        ProjectId id = ProjectId.of(uuid);

        assertThat(id.value()).isEqualTo(uuid);
    }

    @Test
    @DisplayName("Should be equal when wrapping the same UUID")
    void shouldBeEqualWithSameUUID() {
        UUID uuid = UUID.randomUUID();
        ProjectId a = ProjectId.of(uuid);
        ProjectId b = ProjectId.of(uuid);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    @DisplayName("Should reject null UUID in of()")
    void shouldRejectNullUUID() {
        assertThatThrownBy(() -> ProjectId.of(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ProjectId");
    }
}
