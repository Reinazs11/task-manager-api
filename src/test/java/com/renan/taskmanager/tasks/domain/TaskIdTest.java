package com.renan.taskmanager.tasks.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    @DisplayName("Should reject null UUID in of()")
    void shouldRejectNullUUID() {
        assertThatThrownBy(() -> TaskId.of(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("TaskId");
    }

    /**
     * Full equality contract covering every branch of {@code equals} so that
     * mutation testing cannot silently erode value-object semantics.
     */
    @Nested
    @DisplayName("Equality contract")
    class Equality {

        @Test
        @DisplayName("Should be equal when wrapping the same UUID")
        void shouldBeEqualWithSameUUID() {
            UUID uuid = UUID.randomUUID();
            TaskId a = TaskId.of(uuid);
            TaskId b = TaskId.of(uuid);

            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }

        @Test
        @DisplayName("Should be equal to itself (reflexive)")
        void shouldBeEqualToItself() {
            TaskId id = TaskId.generate();

            assertThat(id).isEqualTo(id);
        }

        @Test
        @DisplayName("Should not be equal to null")
        void shouldNotBeEqualToNull() {
            TaskId id = TaskId.generate();

            assertThat(id).isNotEqualTo(null);
        }

        @Test
        @DisplayName("Should not be equal to a different type")
        void shouldNotBeEqualToDifferentType() {
            TaskId id = TaskId.generate();

            assertThat(id).isNotEqualTo("not-a-taskid");
        }

        @Test
        @DisplayName("Should not be equal to a TaskId with a different UUID")
        void shouldNotBeEqualToDifferentUUID() {
            TaskId a = TaskId.generate();
            TaskId b = TaskId.generate();

            assertThat(a).isNotEqualTo(b);
        }
    }
}
