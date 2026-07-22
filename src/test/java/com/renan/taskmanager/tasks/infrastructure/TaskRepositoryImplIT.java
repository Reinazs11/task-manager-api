package com.renan.taskmanager.tasks.infrastructure;

import com.renan.taskmanager.common.TestContainersConfig;
import com.renan.taskmanager.common.domain.UserId;
import com.renan.taskmanager.tasks.application.TaskMapperImpl;
import com.renan.taskmanager.tasks.domain.Priority;
import com.renan.taskmanager.tasks.domain.Project;
import com.renan.taskmanager.tasks.domain.Task;
import com.renan.taskmanager.tasks.domain.TaskId;
import com.renan.taskmanager.tasks.domain.TaskStatus;
import com.renan.taskmanager.tasks.domain.TaskTitle;
import com.renan.taskmanager.users.infrastructure.UserEntity;
import com.renan.taskmanager.users.infrastructure.UserJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link TaskRepositoryImpl} against real PostgreSQL.
 *
 * <p><b>Why this exists:</b> the {@code findByProjectId} query carries an
 * optional status-filter parameter passed into a JPQL {@code :statusFilter IS
 * NULL OR ...} clause. That shape only proves itself against a real database —
 * an in-memory H2 would not catch dialect-specific null-handling differences.
 * Also exercises {@code existsByIdAndOwnerId} on the task side (the
 * authorization primitive used by {@code UpdateTaskStatusUseCase}).</p>
 *
 * <p><b>Why we seed a {@link UserEntity} before each test:</b> both
 * {@code projects} and {@code tasks} carry a foreign key
 * {@code owner_id → users(id)}. Real PostgreSQL enforces it; H2 might not.</p>
 */
@DataJpaTest
@Import({TaskRepositoryImpl.class, ProjectRepositoryImpl.class, TaskMapperImpl.class, TestContainersConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TaskRepositoryImplIT {

    @Autowired
    private TaskRepositoryImpl taskRepository;

    @Autowired
    private ProjectRepositoryImpl projectRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    /**
     * Inserts a user row and returns its id, so {@code projects.owner_id} and
     * {@code tasks.owner_id} satisfy their FK constraints.
     */
    private UserId seedOwner() {
        UserEntity owner = UserEntity.builder()
                .email(UUID.randomUUID() + "@it.example.com")
                .passwordHash("$2a$12$placeholderhashforitrequiredtobevalidformat00")
                .name("IT Owner")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return UserId.of(userJpaRepository.save(owner).getId());
    }

    @Test
    @DisplayName("Should persist a task through a project and retrieve it by id")
    void shouldPersistAndRetrieveTask() {
        UserId owner = seedOwner();
        Project project = projectRepository.save(Project.create(owner, "P1"));
        Task task = project.addTask(new TaskTitle("Write tests"));
        Task saved = taskRepository.save(task);

        Optional<Task> found = taskRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo(new TaskTitle("Write tests"));
        assertThat(found.get().getProjectId()).isEqualTo(project.getId());
        assertThat(found.get().getOwnerId()).isEqualTo(owner);
        assertThat(found.get().getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(found.get().getPriority()).isEqualTo(Priority.DEFAULT);
    }

    @Test
    @DisplayName("Should preserve status transition across save/findById")
    void shouldPreserveStatusTransition() {
        UserId owner = seedOwner();
        Project project = projectRepository.save(Project.create(owner, "P1"));
        Task task = taskRepository.save(project.addTask(new TaskTitle("T")));

        task.transitionTo(TaskStatus.IN_PROGRESS);
        Task updated = taskRepository.save(task);

        Optional<Task> reloaded = taskRepository.findById(updated.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("findByProjectId without filter returns all tasks of the project")
    void shouldReturnAllTasksWhenNoFilter() {
        UserId owner = seedOwner();
        Project project = projectRepository.save(Project.create(owner, "P1"));
        taskRepository.save(project.addTask(new TaskTitle("T1")));
        Task t2 = taskRepository.save(project.addTask(new TaskTitle("T2")));
        t2.transitionTo(TaskStatus.IN_PROGRESS);
        taskRepository.save(t2);

        Page<Task> result = taskRepository.findByProjectId(
                project.getId(), null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("findByProjectId with status filter returns only matching tasks")
    void shouldReturnOnlyMatchingStatusWhenFilterProvided() {
        UserId owner = seedOwner();
        Project project = projectRepository.save(Project.create(owner, "P1"));
        taskRepository.save(project.addTask(new TaskTitle("TODO task")));
        Task inProgress = project.addTask(new TaskTitle("In-progress task"));
        inProgress.transitionTo(TaskStatus.IN_PROGRESS);
        taskRepository.save(inProgress);

        Page<Task> result = taskRepository.findByProjectId(
                project.getId(), TaskStatus.IN_PROGRESS, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).allSatisfy(t ->
                assertThat(t.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS));
    }

    @Test
    @DisplayName("existsByIdAndOwnerId should return true for the owner")
    void shouldReturnTrueForOwner() {
        UserId owner = seedOwner();
        Project project = projectRepository.save(Project.create(owner, "P1"));
        Task saved = taskRepository.save(project.addTask(new TaskTitle("T")));

        boolean exists = taskRepository.existsByIdAndOwnerId(saved.getId(), owner);

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsByIdAndOwnerId should return false for a non-owner")
    void shouldReturnFalseForNonOwner() {
        UserId owner = seedOwner();
        Project project = projectRepository.save(Project.create(owner, "P1"));
        Task saved = taskRepository.save(project.addTask(new TaskTitle("T")));

        boolean exists = taskRepository.existsByIdAndOwnerId(saved.getId(), UserId.generate());

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("existsByIdAndOwnerId should return false for a non-existent task")
    void shouldReturnFalseForNonExistentTask() {
        boolean exists = taskRepository.existsByIdAndOwnerId(TaskId.generate(), UserId.generate());

        assertThat(exists).isFalse();
    }
}
