package com.renan.taskmanager.tasks.domain;

import com.renan.taskmanager.common.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the {@link Task} entity.
 *
 * <p>A Task belongs to a Project and has a title, status, and priority.
 * Status transitions are enforced via {@link TaskStatus#assertTransitionTo}.</p>
 */
class TaskTest {

    private static final UserId OWNER = UserId.generate();
    private static final ProjectId PROJECT_ID = ProjectId.generate();
    private static final TaskTitle TITLE = new TaskTitle("Write integration tests");

    @Nested
    @DisplayName("When creating a task")
    class Creation {

        @Test
        @DisplayName("Should default to TODO status and MEDIUM priority")
        void shouldDefaultToTodoAndMedium() {
            Task task = Task.create(PROJECT_ID, OWNER, TITLE);

            assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
            assertThat(task.getPriority()).isEqualTo(Priority.MEDIUM);
        }

        @Test
        @DisplayName("Should accept explicit priority at creation")
        void shouldAcceptExplicitPriority() {
            Task task = Task.create(PROJECT_ID, OWNER, TITLE, Priority.HIGH);

            assertThat(task.getPriority()).isEqualTo(Priority.HIGH);
        }

        @Test
        @DisplayName("Should generate a unique TaskId")
        void shouldGenerateUniqueTaskId() {
            Task t1 = Task.create(PROJECT_ID, OWNER, TITLE);
            Task t2 = Task.create(PROJECT_ID, OWNER, TITLE);

            assertThat(t1.getId()).isNotEqualTo(t2.getId());
        }

        @Test
        @DisplayName("Should record creation timestamp")
        void shouldRecordCreationTimestamp() {
            Task task = Task.create(PROJECT_ID, OWNER, TITLE);

            assertThat(task.getCreatedAt()).isNotNull();
            assertThat(task.getUpdatedAt()).isEqualTo(task.getCreatedAt());
        }

        @Test
        @DisplayName("Should reject null priority when explicitly provided")
        void shouldRejectNullPriority() {
            assertThatThrownBy(() -> Task.create(PROJECT_ID, OWNER, TITLE, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Status transitions")
    class StatusTransitions {

        @Test
        @DisplayName("Should transition TODO -> IN_PROGRESS")
        void todoToInProgress() {
            Task task = Task.create(PROJECT_ID, OWNER, TITLE);

            task.transitionTo(TaskStatus.IN_PROGRESS);

            assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("Should reject TODO -> DONE (must pass through IN_PROGRESS)")
        void shouldRejectTodoToDone() {
            Task task = Task.create(PROJECT_ID, OWNER, TITLE);

            assertThatThrownBy(() -> task.transitionTo(TaskStatus.DONE))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        @DisplayName("Should reject DONE -> TODO (reopen via IN_PROGRESS instead)")
        void shouldRejectDoneToTodo() {
            Task task = Task.create(PROJECT_ID, OWNER, TITLE);
            task.transitionTo(TaskStatus.IN_PROGRESS);
            task.transitionTo(TaskStatus.DONE);

            assertThatThrownBy(() -> task.transitionTo(TaskStatus.TODO))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        @DisplayName("complete() should be a shortcut to DONE from IN_PROGRESS")
        void completeFromInProgress() {
            Task task = Task.create(PROJECT_ID, OWNER, TITLE);
            task.transitionTo(TaskStatus.IN_PROGRESS);

            task.complete();

            assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
        }

        @Test
        @DisplayName("Status change should update updatedAt")
        void shouldUpdateTimestampOnStatusChange() throws InterruptedException {
            Task task = Task.create(PROJECT_ID, OWNER, TITLE);
            var originalUpdatedAt = task.getUpdatedAt();

            Thread.sleep(10);
            task.transitionTo(TaskStatus.IN_PROGRESS);

            assertThat(task.getUpdatedAt()).isAfter(originalUpdatedAt);
        }
    }

    @Nested
    @DisplayName("Priority changes")
    class PriorityChanges {

        @Test
        @DisplayName("Should allow changing priority")
        void shouldAllowChangingPriority() {
            Task task = Task.create(PROJECT_ID, OWNER, TITLE, Priority.LOW);

            task.changePriority(Priority.HIGH);

            assertThat(task.getPriority()).isEqualTo(Priority.HIGH);
        }

        @Test
        @DisplayName("Should reject changing to null priority")
        void shouldRejectNullPriority() {
            Task task = Task.create(PROJECT_ID, OWNER, TITLE);

            assertThatThrownBy(() -> task.changePriority(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Priority");
        }
    }

    @Nested
    @DisplayName("Remaining transitions via transitionTo")
    class RemainingTransitions {

        @Test
        @DisplayName("Should allow DONE -> IN_PROGRESS (rework/reopen)")
        void doneToInProgress() {
            Task task = Task.create(PROJECT_ID, OWNER, TITLE);
            task.transitionTo(TaskStatus.IN_PROGRESS);
            task.transitionTo(TaskStatus.DONE);

            task.transitionTo(TaskStatus.IN_PROGRESS);

            assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("Should allow IN_PROGRESS -> TODO (correction)")
        void inProgressToTodo() {
            Task task = Task.create(PROJECT_ID, OWNER, TITLE);
            task.transitionTo(TaskStatus.IN_PROGRESS);

            task.transitionTo(TaskStatus.TODO);

            assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
        }
    }

    @Nested
    @DisplayName("complete() edge cases")
    class CompleteEdgeCases {

        @Test
        @DisplayName("complete() from TODO should throw (must pass through IN_PROGRESS)")
        void completeFromTodoShouldThrow() {
            Task task = Task.create(PROJECT_ID, OWNER, TITLE);

            assertThatThrownBy(task::complete)
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }
    }

    @Nested
    @DisplayName("Reconstitution from persisted data")
    class Reconstitution {

        @Test
        @DisplayName("Should preserve all fields when reconstituted")
        void shouldPreserveAllFields() {
            TaskId id = TaskId.generate();
            Instant createdAt = Instant.parse("2024-01-01T10:00:00Z");
            Instant updatedAt = Instant.parse("2024-06-15T14:30:00Z");

            Task task = Task.reconstitute(
                    id, PROJECT_ID, OWNER, TITLE,
                    TaskStatus.IN_PROGRESS, Priority.HIGH,
                    createdAt, updatedAt
            );

            assertThat(task.getId()).isEqualTo(id);
            assertThat(task.getProjectId()).isEqualTo(PROJECT_ID);
            assertThat(task.getOwnerId()).isEqualTo(OWNER);
            assertThat(task.getTitle()).isEqualTo(TITLE);
            assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
            assertThat(task.getPriority()).isEqualTo(Priority.HIGH);
            assertThat(task.getCreatedAt()).isEqualTo(createdAt);
            assertThat(task.getUpdatedAt()).isEqualTo(updatedAt);
        }

        @Test
        @DisplayName("Should allow createdAt and updatedAt to differ (unlike create)")
        void shouldAllowDistinctTimestamps() {
            Instant past = Instant.parse("2020-01-01T00:00:00Z");
            Instant recent = Instant.parse("2024-12-31T23:59:59Z");

            Task task = Task.reconstitute(
                    TaskId.generate(), PROJECT_ID, OWNER, TITLE,
                    TaskStatus.DONE, Priority.LOW, past, recent
            );

            assertThat(task.getCreatedAt()).isBefore(task.getUpdatedAt());
        }

        @Test
        @DisplayName("Should reject null id when reconstituting")
        void shouldRejectNullId() {
            assertThatThrownBy(() -> Task.reconstitute(
                    null, PROJECT_ID, OWNER, TITLE,
                    TaskStatus.TODO, Priority.MEDIUM,
                    Instant.now(), Instant.now()
            )).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Should reject null title when reconstituting")
        void shouldRejectNullTitle() {
            assertThatThrownBy(() -> Task.reconstitute(
                    TaskId.generate(), PROJECT_ID, OWNER, null,
                    TaskStatus.TODO, Priority.MEDIUM,
                    Instant.now(), Instant.now()
            )).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Entity equality (identity by TaskId)")
    class Equality {

        @Test
        @DisplayName("Two tasks with the same TaskId should be equal even if fields differ")
        void sameIdentityShouldBeEqual() {
            TaskId sharedId = TaskId.generate();

            Task t1 = Task.reconstitute(
                    sharedId, PROJECT_ID, OWNER, new TaskTitle("Original"),
                    TaskStatus.TODO, Priority.LOW,
                    Instant.parse("2024-01-01T00:00:00Z"),
                    Instant.parse("2024-01-01T00:00:00Z")
            );
            Task t2 = Task.reconstitute(
                    sharedId, PROJECT_ID, OWNER, new TaskTitle("Changed"),
                    TaskStatus.DONE, Priority.HIGH,
                    Instant.parse("2024-06-01T00:00:00Z"),
                    Instant.parse("2024-06-01T00:00:00Z")
            );

            // Same identity (TaskId) => equal, regardless of other fields
            assertThat(t1).isEqualTo(t2).hasSameHashCodeAs(t2);
        }

        @Test
        @DisplayName("Two tasks with different TaskIds should not be equal")
        void differentIdentityShouldNotBeEqual() {
            Task t1 = Task.create(PROJECT_ID, OWNER, TITLE);
            Task t2 = Task.create(PROJECT_ID, OWNER, TITLE);

            assertThat(t1).isNotEqualTo(t2);
        }
    }
}
