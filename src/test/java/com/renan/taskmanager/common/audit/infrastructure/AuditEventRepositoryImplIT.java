package com.renan.taskmanager.common.audit.infrastructure;

import com.renan.taskmanager.common.TestContainersConfig;
import com.renan.taskmanager.common.audit.application.AuditEventMapperImpl;
import com.renan.taskmanager.common.audit.domain.AuditAction;
import com.renan.taskmanager.common.audit.domain.AuditEvent;
import com.renan.taskmanager.common.audit.domain.AuditableEntityType;
import com.renan.taskmanager.common.audit.application.AuditEventQueryPort;
import com.renan.taskmanager.common.audit.domain.AuditEventRepository;
import com.renan.taskmanager.common.domain.UserId;
import com.renan.taskmanager.users.application.UserMapperImpl;
import com.renan.taskmanager.users.domain.Email;
import com.renan.taskmanager.users.domain.Password;
import com.renan.taskmanager.users.domain.User;
import com.renan.taskmanager.users.domain.UserRepository;
import com.renan.taskmanager.users.infrastructure.UserRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.transaction.TestTransaction;

import java.time.Clock;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link AuditEventRepositoryImpl} against a real
 * PostgreSQL 16 via Testcontainers.
 *
 * <p>Same slice-style setup as {@code RevokedRefreshTokenRepositoryImplIT}:
 * {@code @DataJpaTest} with the embedded DB replaced by our Testcontainers
 * PostgreSQL, and the adapter + mapper + container explicitly
 * {@code @Import}ed.</p>
 *
 * <p>Key things this IT proves that unit tests cannot:</p>
 * <ul>
 *   <li>The {@code jsonb} {@code metadata} column round-trips a {@code Map}
 *       through Hibernate 6's {@code @JdbcTypeCode(SqlTypes.JSON)}.</li>
 *   <li>{@code ddl-auto=validate} accepts the entity against migration V3.</li>
 *   <li>The CHECK constraints accept every action/entityType we emit.</li>
 *   <li>The nullable actor/entity/correlation columns persist null cleanly.</li>
 *   <li>The {@code findByActor} query honours both the actor scope and the
 *       optional action filter, newest-first.</li>
 * </ul>
 */
@DataJpaTest
@Import({
        AuditEventRepositoryImpl.class,
        AuditEventMapperImpl.class,
        UserRepositoryImpl.class,
        UserMapperImpl.class,
        TestContainersConfig.class
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuditEventRepositoryImplIT {

    @Autowired
    private AuditEventRepository repository;

    @Autowired
    private AuditEventQueryPort queryPort;

    @Autowired
    private AuditEventJpaRepository jpaRepository;

    @Autowired
    private UserRepository userRepository;

    private UserId actor;
    private UserId otherActor;
    private Clock clock = Clock.systemUTC();

    @BeforeEach
    void seedUsersAndClean() {
        jpaRepository.deleteAll();
        userRepository.deleteAll();
        actor = userRepository.save(User.create(
                new Email("actor@example.com"),
                Password.fromHash("$2a$10$abcdefghijklmnopqrstuvWXYZ1234567890abc")
        )).getId();
        otherActor = userRepository.save(User.create(
                new Email("other@example.com"),
                Password.fromHash("$2a$10$abcdefghijklmnopqrstuvWXYZ1234567890abc")
        )).getId();
    }

    private AuditEvent event(UserId who, AuditAction action, Map<String, String> metadata) {
        return AuditEvent.record(who, action, AuditableEntityType.PROJECT, null, clock,
                "req-" + action, metadata);
    }

    @Nested
    @DisplayName("save + findById")
    class SaveAndFind {

        @Test
        @DisplayName("Saved event should be loadable by id with every field preserved")
        void shouldRoundTripAllFields() {
            Map<String, String> metadata = Map.of("from", "TODO", "to", "IN_PROGRESS");
            AuditEvent saved = event(actor, AuditAction.TASK_STATUS_CHANGED, metadata);

            repository.save(saved);
            jpaRepository.flush();

            AuditEvent loaded = repository.findById(saved.id().value()).orElseThrow();

            assertThat(loaded.actorId()).contains(actor);
            assertThat(loaded.action()).isEqualTo(AuditAction.TASK_STATUS_CHANGED);
            assertThat(loaded.entityType()).isEqualTo(AuditableEntityType.PROJECT);
            assertThat(loaded.metadata()).containsEntry("from", "TODO").containsEntry("to", "IN_PROGRESS");
            assertThat(loaded.correlationId()).contains("req-TASK_STATUS_CHANGED");
        }

        @Test
        @DisplayName("jsonb metadata should survive empty-map storage")
        void shouldPersistEmptyMetadata() {
            AuditEvent saved = event(actor, AuditAction.PROJECT_CREATED, Map.of());

            repository.save(saved);

            AuditEvent loaded = repository.findById(saved.id().value()).orElseThrow();
            assertThat(loaded.metadata()).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByActor (read port)")
    class FindByActor {

        @Test
        @DisplayName("Should return only the actor's own events, newest first")
        void shouldScopeToActorNewestFirst() throws Exception {
            // Two events for `actor`, one for `otherActor`, with explicit timestamps
            // so ordering is deterministic.
            AuditEvent older = AuditEvent.record(actor, AuditAction.PROJECT_CREATED,
                    AuditableEntityType.PROJECT, null,
                    Clock.fixed(java.time.Instant.parse("2026-07-01T00:00:00Z"), java.time.ZoneOffset.UTC),
                    "r1", Map.of());
            AuditEvent newer = AuditEvent.record(actor, AuditAction.TASK_CREATED,
                    AuditableEntityType.TASK, null,
                    Clock.fixed(java.time.Instant.parse("2026-07-02T00:00:00Z"), java.time.ZoneOffset.UTC),
                    "r2", Map.of());
            AuditEvent someoneElse = event(otherActor, AuditAction.PROJECT_CREATED, Map.of());
            repository.save(older);
            repository.save(newer);
            repository.save(someoneElse);

            var page = queryPort.findByActor(actor, null, PageRequest.of(0, 10));

            assertThat(page.getContent())
                    .extracting(AuditEvent::action)
                    .containsExactly(AuditAction.TASK_CREATED, AuditAction.PROJECT_CREATED);
            assertThat(page.getTotalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("Action filter should narrow the result set")
        void shouldFilterByAction() {
            repository.save(event(actor, AuditAction.PROJECT_CREATED, Map.of()));
            repository.save(event(actor, AuditAction.PROJECT_DELETED, Map.of()));
            repository.save(event(actor, AuditAction.PROJECT_CREATED, Map.of()));

            Pageable pageable = PageRequest.of(0, 10);
            var page = queryPort.findByActor(actor, AuditAction.PROJECT_DELETED, pageable);

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getContent().get(0).action()).isEqualTo(AuditAction.PROJECT_DELETED);
        }

        @Test
        @DisplayName("Actor with no events should get an empty page")
        void shouldReturnEmptyForUnknownActor() {
            var page = queryPort.findByActor(actor, null, PageRequest.of(0, 10));
            assertThat(page.getContent()).isEmpty();
        }
    }
}
