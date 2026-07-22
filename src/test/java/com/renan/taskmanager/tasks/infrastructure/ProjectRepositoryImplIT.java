package com.renan.taskmanager.tasks.infrastructure;

import com.renan.taskmanager.common.TestContainersConfig;
import com.renan.taskmanager.common.domain.UserId;
import com.renan.taskmanager.tasks.application.TaskMapperImpl;
import com.renan.taskmanager.tasks.domain.Project;
import com.renan.taskmanager.tasks.domain.ProjectId;
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
 * Integration test for {@link ProjectRepositoryImpl} against real PostgreSQL.
 *
 * <p><b>Why this exists alongside {@code UserRepositoryImplIT}:</b>
 * the {@code existsByIdAndOwnerId} query is the load-bearing piece of the
 * project's anti-enumeration policy — it's how every authenticated use case
 * decides 403 vs 200. It must work against real PostgreSQL, not just be
 * inferred from a passing IT through the controller stack.</p>
 *
 * <p><b>Why we seed a {@link UserEntity} before each project:</b> the
 * {@code projects} table has a foreign key {@code owner_id → users(id)}. An
 * in-memory DB might not enforce this; real PostgreSQL does, and the FK
 * violation is the kind of bug only a Testcontainers run surfaces.</p>
 *
 * <p>Uses {@code @DataJpaTest} slice + Testcontainers (same setup as
 * {@code UserRepositoryImplIT}).</p>
 */
@DataJpaTest
@Import({ProjectRepositoryImpl.class, TaskMapperImpl.class, TestContainersConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProjectRepositoryImplIT {

    @Autowired
    private ProjectRepositoryImpl projectRepository;

    @Autowired
    private ProjectJpaRepository projectJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    /**
     * Inserts a user row and returns its id, so {@code projects.owner_id}
     * satisfies the foreign key constraint.
     */
    private UserId seedOwner() {
        UserEntity owner = UserEntity.builder()
                .email(java.util.UUID.randomUUID() + "@it.example.com")
                .passwordHash("$2a$12$placeholderhashforitrequiredtobevalidformat00")
                .name("IT Owner")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        UUID id = userJpaRepository.save(owner).getId();
        return UserId.of(id);
    }

    @Test
    @DisplayName("Should persist and retrieve a project by id")
    void shouldPersistAndRetrieveById() {
        UserId owner = seedOwner();
        Project saved = projectRepository.save(Project.create(owner, "My Project"));

        Optional<Project> found = projectRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName().value()).isEqualTo("My Project");
        assertThat(found.get().getOwnerId()).isEqualTo(owner);
    }

    @Test
    @DisplayName("Should return empty when the project id does not exist")
    void shouldReturnEmptyWhenIdNotFound() {
        Optional<Project> found = projectRepository.findById(ProjectId.generate());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("existsByIdAndOwnerId should return true for the owner")
    void shouldReturnTrueForOwner() {
        UserId owner = seedOwner();
        Project saved = projectRepository.save(Project.create(owner, "P1"));

        boolean exists = projectRepository.existsByIdAndOwnerId(saved.getId(), owner);

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsByIdAndOwnerId should return false for a different owner")
    void shouldReturnFalseForDifferentOwner() {
        UserId owner = seedOwner();
        Project saved = projectRepository.save(Project.create(owner, "P1"));

        // Random non-owner UUID — should not match (whether or not it happens
        // to exist as a user row is irrelevant to the exists check).
        boolean exists = projectRepository.existsByIdAndOwnerId(saved.getId(), UserId.generate());

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("existsByIdAndOwnerId should return false for a non-existent id")
    void shouldReturnFalseForNonExistentId() {
        boolean exists = projectRepository.existsByIdAndOwnerId(ProjectId.generate(), UserId.generate());

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("findByOwnerId should return only the projects owned by that user")
    void shouldReturnOnlyOwnerProjects() {
        UserId alice = seedOwner();
        UserId bob = seedOwner();
        projectRepository.save(Project.create(alice, "Alice 1"));
        projectRepository.save(Project.create(alice, "Alice 2"));
        projectRepository.save(Project.create(bob, "Bob 1"));

        Page<Project> result = projectRepository.findByOwnerId(alice, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).allSatisfy(p ->
                assertThat(p.getOwnerId()).isEqualTo(alice));
    }

    @Test
    @DisplayName("deleteById should remove the project")
    void shouldDeleteProjectById() {
        UserId owner = seedOwner();
        Project saved = projectRepository.save(Project.create(owner, "To Delete"));

        projectRepository.deleteById(saved.getId());
        projectJpaRepository.flush();

        assertThat(projectRepository.findById(saved.getId())).isEmpty();
    }
}
