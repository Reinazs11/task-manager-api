package com.renan.taskmanager.tasks.domain;

import com.renan.taskmanager.users.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
    }
}
