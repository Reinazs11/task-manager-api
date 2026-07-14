package com.renan.taskmanager.tasks.domain;

import com.renan.taskmanager.users.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the {@link Project} aggregate root.
 *
 * <p>Project owns its Tasks: tasks are created via {@link Project#addTask}, and
 * the project enforces invariants (every task carries the project's id and owner).</p>
 */
class ProjectTest {

    private static final UserId OWNER = UserId.generate();
    private static final TaskTitle TITLE = new TaskTitle("Setup CI");

    @Nested
    @DisplayName("When creating a project")
    class Creation {

        @Test
        @DisplayName("Should generate a ProjectId and record the owner")
        void shouldGenerateIdAndOwner() {
            Project project = Project.create(OWNER, "Backend Refactor");

            assertThat(project.getId()).isNotNull();
            assertThat(project.getOwnerId()).isEqualTo(OWNER);
            assertThat(project.getName().value()).isEqualTo("Backend Refactor");
        }

        @Test
        @DisplayName("Should start with no tasks")
        void shouldStartEmpty() {
            Project project = Project.create(OWNER, "Empty");

            assertThat(project.tasks()).isEmpty();
        }

        @Test
        @DisplayName("Should record timestamps")
        void shouldRecordTimestamps() {
            Project project = Project.create(OWNER, "Timestamped");

            assertThat(project.getCreatedAt()).isNotNull();
            assertThat(project.getUpdatedAt()).isEqualTo(project.getCreatedAt());
        }

        @Test
        @DisplayName("Should reject blank name")
        void shouldRejectBlankName() {
            assertThatThrownBy(() -> Project.create(OWNER, "   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("blank");
        }

        @Test
        @DisplayName("Should reject empty name")
        void shouldRejectEmptyName() {
            assertThatThrownBy(() -> Project.create(OWNER, ""))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should reject null name")
        void shouldRejectNullName() {
            assertThatThrownBy(() -> Project.create(OWNER, (String) null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Reconstitution from persisted data")
    class Reconstitution {

        @Test
        @DisplayName("Should preserve id, owner, name, tasks, and timestamps")
        void shouldPreserveAllFields() {
            ProjectId id = ProjectId.generate();
            Instant createdAt = Instant.parse("2024-01-01T10:00:00Z");
            Instant updatedAt = Instant.parse("2024-06-15T14:30:00Z");

            Project project = Project.reconstitute(
                    id, OWNER, "Persisted Project",
                    List.of(), createdAt, updatedAt
            );

            assertThat(project.getId()).isEqualTo(id);
            assertThat(project.getOwnerId()).isEqualTo(OWNER);
            assertThat(project.getName().value()).isEqualTo("Persisted Project");
            assertThat(project.getCreatedAt()).isEqualTo(createdAt);
            assertThat(project.getUpdatedAt()).isEqualTo(updatedAt);
        }

        @Test
        @DisplayName("Should expose pre-existing tasks via tasks()")
        void shouldExposePreExistingTasks() {
            Task existing = Task.create(ProjectId.generate(), OWNER, TITLE);

            Project project = Project.reconstitute(
                    ProjectId.generate(), OWNER, "With Tasks",
                    List.of(existing), Instant.now(), Instant.now()
            );

            assertThat(project.tasks()).hasSize(1);
            assertThat(project.tasks().get(0).getTitle()).isEqualTo(TITLE);
        }

        @Test
        @DisplayName("Should make a defensive copy of the input task list")
        void shouldMakeDefensiveCopy() {
            // Caller keeps a reference to the list they passed in.
            List<Task> mutableInput = new ArrayList<>();
            Project project = Project.reconstitute(
                    ProjectId.generate(), OWNER, "Defensive",
                    mutableInput, Instant.now(), Instant.now()
            );

            // Caller mutates their list AFTER construction.
            mutableInput.add(Task.create(ProjectId.generate(), OWNER, TITLE));

            // Project's internal list must NOT reflect the external mutation.
            assertThat(project.tasks()).isEmpty();
        }
    }

    @Nested
    @DisplayName("When adding tasks")
    class AddTasks {

        @Test
        @DisplayName("Should add a task and make it visible via tasks()")
        void shouldAddAndExposeTask() {
            Project project = Project.create(OWNER, "P1");

            Project.TaskAdded result = project.addTask(TITLE);

            assertThat(project.tasks()).hasSize(1);
            assertThat(project.tasks().get(0).getTitle()).isEqualTo(TITLE);
            assertThat(result.task()).isNotNull();
        }

        @Test
        @DisplayName("Added task should carry the project's id and owner")
        void addedTaskCarriesProjectContext() {
            Project project = Project.create(OWNER, "P1");

            Project.TaskAdded result = project.addTask(TITLE);

            assertThat(result.task().getProjectId()).isEqualTo(project.getId());
            assertThat(result.task().getOwnerId()).isEqualTo(OWNER);
        }

        @Test
        @DisplayName("Should generate unique TaskIds for multiple additions")
        void shouldGenerateUniqueTaskIds() {
            Project project = Project.create(OWNER, "P1");

            Project.TaskAdded r1 = project.addTask(new TaskTitle("First"));
            Project.TaskAdded r2 = project.addTask(new TaskTitle("Second"));

            assertThat(r1.task().getId()).isNotEqualTo(r2.task().getId());
        }

        @Test
        @DisplayName("Should maintain insertion order")
        void shouldMaintainInsertionOrder() {
            Project project = Project.create(OWNER, "P1");

            project.addTask(new TaskTitle("First"));
            project.addTask(new TaskTitle("Second"));
            project.addTask(new TaskTitle("Third"));

            assertThat(project.tasks()).extracting(t -> t.getTitle().value())
                    .containsExactly("First", "Second", "Third");
        }

        @Test
        @DisplayName("Should update project's updatedAt when a task is added")
        void shouldUpdateProjectTimestampOnAdd() throws InterruptedException {
            Project project = Project.create(OWNER, "P1");
            var originalUpdatedAt = project.getUpdatedAt();

            Thread.sleep(10);
            project.addTask(TITLE);

            assertThat(project.getUpdatedAt()).isAfter(originalUpdatedAt);
        }

        @Test
        @DisplayName("Should reject adding a task with null title")
        void shouldRejectNullTitle() {
            Project project = Project.create(OWNER, "P1");

            assertThatThrownBy(() -> project.addTask(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Task collection immutability")
    class TaskCollection {

        @Test
        @DisplayName("tasks() should return an immutable list")
        void shouldReturnImmutableList() {
            Project project = Project.create(OWNER, "P1");
            project.addTask(TITLE);

            assertThatThrownBy(() -> project.tasks().add(
                    Task.create(project.getId(), OWNER, TITLE)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("Should allow adding multiple tasks (5+)")
        void shouldAllowMultipleTasks() {
            Project project = Project.create(OWNER, "P1");

            for (int i = 0; i < 5; i++) {
                project.addTask(new TaskTitle("Task " + i));
            }

            assertThat(project.tasks()).hasSize(5);
        }
    }
}
