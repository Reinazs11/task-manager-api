package com.renan.taskmanager.tasks.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the {@link TaskId} value object.
 *
 * <p>Same rationale as {@link ProjectIdTest}: type safety for identifiers.</p>
 */
class TaskIdTest {

    @Test
    @DisplayName("Should generate a new random TaskId")
    void shouldGenerateRandom() {
        TaskId id1 = TaskId.generate();
        TaskId id2 = TaskId.generate();

        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    @DisplayName("Should create from existing UUID")
    void shouldCreateFromUUID() {
        UUID uuid = UUID.randomUUID();
        TaskId id = TaskId.of(uuid);

        assertThat(id.value()).isEqualTo(uuid);
    }

    @Test
    @DisplayName("Should be equal when wrapping the same UUID")
    void shouldBeEqualWithSameUUID() {
        UUID uuid = UUID.randomUUID();
        TaskId a = TaskId.of(uuid);
        TaskId b = TaskId.of(uuid);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }
}
