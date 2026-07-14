package com.renan.taskmanager.tasks.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the {@link TaskStatus} enum and its transition rules.
 *
 * <p>Status transitions are a business rule: not all moves are allowed.
 * These tests document the allowed graph:</p>
 *
 * <pre>
 *   TODO --&gt; IN_PROGRESS --&gt; DONE
 *              ↑   ↓
 *              └───┘  (DONE may reopen back to IN_PROGRESS)
 * </pre>
 */
class TaskStatusTest {

    @Nested
    @DisplayName("Valid transitions")
    class ValidTransitions {

        @Test
        @DisplayName("TODO should transition to IN_PROGRESS")
        void todoToInProgress() {
            assertThat(TaskStatus.TODO.canTransitionTo(TaskStatus.IN_PROGRESS)).isTrue();
        }

        @Test
        @DisplayName("IN_PROGRESS should transition to DONE")
        void inProgressToDone() {
            assertThat(TaskStatus.IN_PROGRESS.canTransitionTo(TaskStatus.DONE)).isTrue();
        }

        @Test
        @DisplayName("IN_PROGRESS should transition back to TODO (correction)")
        void inProgressToTodo() {
            assertThat(TaskStatus.IN_PROGRESS.canTransitionTo(TaskStatus.TODO)).isTrue();
        }

        @Test
        @DisplayName("DONE should reopen back to IN_PROGRESS (rework)")
        void doneToInProgress() {
            assertThat(TaskStatus.DONE.canTransitionTo(TaskStatus.IN_PROGRESS)).isTrue();
        }

        @Test
        @DisplayName("Same-status transition should be allowed (idempotent)")
        void sameStatus() {
            assertThat(TaskStatus.TODO.canTransitionTo(TaskStatus.TODO)).isTrue();
            assertThat(TaskStatus.IN_PROGRESS.canTransitionTo(TaskStatus.IN_PROGRESS)).isTrue();
            assertThat(TaskStatus.DONE.canTransitionTo(TaskStatus.DONE)).isTrue();
        }
    }

    @Nested
    @DisplayName("Invalid transitions")
    class InvalidTransitions {

        @Test
        @DisplayName("TODO should NOT skip directly to DONE")
        void todoToDone() {
            assertThat(TaskStatus.TODO.canTransitionTo(TaskStatus.DONE)).isFalse();
        }

        @Test
        @DisplayName("DONE should NOT go back to TODO (create a new task instead)")
        void doneToTodo() {
            assertThat(TaskStatus.DONE.canTransitionTo(TaskStatus.TODO)).isFalse();
        }
    }

    @Nested
    @DisplayName("assertTransition (throws on invalid)")
    class AssertTransition {

        @Test
        @DisplayName("Valid transition should not throw")
        void validDoesNotThrow() {
            TaskStatus.TODO.assertTransitionTo(TaskStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("Invalid transition should throw InvalidStatusTransitionException")
        void invalidThrows() {
            assertThatThrownBy(() -> TaskStatus.DONE.assertTransitionTo(TaskStatus.TODO))
                    .isInstanceOf(InvalidStatusTransitionException.class)
                    .hasMessageContaining("DONE")
                    .hasMessageContaining("TODO");
        }
    }
}
