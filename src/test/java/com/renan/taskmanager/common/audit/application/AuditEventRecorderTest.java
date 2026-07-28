package com.renan.taskmanager.common.audit.application;

import com.renan.taskmanager.common.audit.domain.AuditAction;
import com.renan.taskmanager.common.audit.domain.AuditEvent;
import com.renan.taskmanager.common.audit.domain.AuditEventRepository;
import com.renan.taskmanager.common.audit.domain.AuditableEntityType;
import com.renan.taskmanager.common.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link AuditEventRecorder}.
 *
 * <p>Pure unit test: the repository is mocked so we can verify the exact
 * {@link AuditEvent} each {@code recordXxx} produces, and the
 * {@link CorrelationIdProvider} is mocked so the recorder does not depend on a
 * populated MDC (which would be empty in a unit test). A fixed {@link Clock}
 * makes the timestamp deterministic and assertable.</p>
 *
 * <p>What these tests are really protecting:</p>
 * <ul>
 *   <li><b>Anti-enumeration:</b> {@code recordLoginFailed()} never records an
 *       actor, even though the recorder has no compile-time guard forcing
 *       that — the assertion is the guard.</li>
 *   <li><b>PII allowlist:</b> each method records only the fields documented
 *       in its Javadoc and nothing else.</li>
 *   <li><b>Correct action/entityType:</b> a wrong constant here would write a
 *       misleading audit trail, and the CHECK constraint would not catch it
 *       (every constant is valid).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AuditEventRecorderTest {

    private static final Instant NOW = Instant.parse("2026-07-28T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String CORRELATION = "req-abc";

    @Mock
    private AuditEventRepository repository;

    @Mock
    private CorrelationIdProvider correlationIdProvider;

    private AuditEventRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new AuditEventRecorder(repository, CLOCK, correlationIdProvider);
        org.mockito.Mockito.when(correlationIdProvider.current())
                .thenReturn(Optional.of(CORRELATION));
    }

    private AuditEvent captureSaved() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("Project events")
    class ProjectEvents {

        @Test
        @DisplayName("recordProjectCreated → PROJECT_CREATED on PROJECT with the project id")
        void shouldRecordProjectCreated() {
            UserId actor = UserId.generate();
            UserId projectId = UserId.generate();

            recorder.recordProjectCreated(actor, projectId);

            AuditEvent event = captureSaved();
            assertThat(event.action()).isEqualTo(AuditAction.PROJECT_CREATED);
            assertThat(event.entityType()).isEqualTo(AuditableEntityType.PROJECT);
            assertThat(event.actorId()).contains(actor);
            assertThat(event.entityId()).contains(projectId);
            assertThat(event.metadata()).isEmpty();
            assertThat(event.occurredAt()).isEqualTo(NOW);
            assertThat(event.correlationId()).contains(CORRELATION);
        }

        @Test
        @DisplayName("recordProjectDeleted → PROJECT_DELETED with the project id")
        void shouldRecordProjectDeleted() {
            UserId actor = UserId.generate();
            UserId projectId = UserId.generate();

            recorder.recordProjectDeleted(actor, projectId);

            AuditEvent event = captureSaved();
            assertThat(event.action()).isEqualTo(AuditAction.PROJECT_DELETED);
            assertThat(event.entityId()).contains(projectId);
        }
    }

    @Nested
    @DisplayName("Task events")
    class TaskEvents {

        @Test
        @DisplayName("recordTaskCreated → TASK_CREATED with priority in metadata only")
        void shouldRecordTaskCreated() {
            UserId actor = UserId.generate();
            UserId taskId = UserId.generate();

            recorder.recordTaskCreated(actor, taskId, "HIGH");

            AuditEvent event = captureSaved();
            assertThat(event.action()).isEqualTo(AuditAction.TASK_CREATED);
            assertThat(event.entityType()).isEqualTo(AuditableEntityType.TASK);
            assertThat(event.metadata()).containsEntry("priority", "HIGH").hasSize(1);
        }

        @Test
        @DisplayName("recordTaskStatusChanged → metadata has exactly {from, to}")
        void shouldRecordTaskStatusChanged() {
            UserId actor = UserId.generate();
            UserId taskId = UserId.generate();

            recorder.recordTaskStatusChanged(actor, taskId, "TODO", "IN_PROGRESS");

            AuditEvent event = captureSaved();
            assertThat(event.action()).isEqualTo(AuditAction.TASK_STATUS_CHANGED);
            assertThat(event.metadata())
                    .containsEntry("from", "TODO")
                    .containsEntry("to", "IN_PROGRESS")
                    .hasSize(2);
        }
    }

    @Nested
    @DisplayName("User / auth events")
    class AuthEvents {

        @Test
        @DisplayName("recordUserRegistered → actor == entity == newUserId")
        void shouldRecordUserRegistered() {
            UserId newUserId = UserId.generate();

            recorder.recordUserRegistered(newUserId);

            AuditEvent event = captureSaved();
            assertThat(event.action()).isEqualTo(AuditAction.USER_REGISTERED);
            assertThat(event.actorId()).contains(newUserId);
            assertThat(event.entityId()).contains(newUserId);
        }

        @Test
        @DisplayName("recordLoginSucceeded → USER_LOGIN_SUCCEEDED with actor")
        void shouldRecordLoginSucceeded() {
            UserId actor = UserId.generate();

            recorder.recordLoginSucceeded(actor);

            AuditEvent event = captureSaved();
            assertThat(event.action()).isEqualTo(AuditAction.USER_LOGIN_SUCCEEDED);
            assertThat(event.actorId()).contains(actor);
            assertThat(event.entityId()).isEmpty();
        }

        @Test
        @DisplayName("recordLoginFailed → actor is null (anti-enumeration)")
        void shouldRecordLoginFailedWithoutActor() {
            // The recorder exposes no actorId parameter on purpose. This test
            // documents the contract: if someone later adds one, this test
            // breaks loudly and the reviewer sees the enumeration leak.
            recorder.recordLoginFailed();

            AuditEvent event = captureSaved();
            assertThat(event.action()).isEqualTo(AuditAction.USER_LOGIN_FAILED);
            assertThat(event.actorId()).isEmpty();
            assertThat(event.metadata()).isEmpty();
        }

        @Test
        @DisplayName("recordLogout → USER_LOGOUT with actor, no entity")
        void shouldRecordLogout() {
            UserId actor = UserId.generate();

            recorder.recordLogout(actor);

            AuditEvent event = captureSaved();
            assertThat(event.action()).isEqualTo(AuditAction.USER_LOGOUT);
            assertThat(event.actorId()).contains(actor);
            assertThat(event.entityId()).isEmpty();
        }

        @Test
        @DisplayName("recordRefreshRotated → REFRESH_ROTATED with actor")
        void shouldRecordRefreshRotated() {
            UserId actor = UserId.generate();

            recorder.recordRefreshRotated(actor);

            AuditEvent event = captureSaved();
            assertThat(event.action()).isEqualTo(AuditAction.REFRESH_ROTATED);
            assertThat(event.actorId()).contains(actor);
        }
    }

    @Nested
    @DisplayName("Correlation id handling")
    class Correlation {

        @Test
        @DisplayName("When MDC has no correlation id, event carries null")
        void shouldHandleAbsentCorrelationId() {
            org.mockito.Mockito.when(correlationIdProvider.current())
                    .thenReturn(Optional.empty());

            recorder.recordLoginSucceeded(UserId.generate());

            AuditEvent event = captureSaved();
            assertThat(event.correlationId()).isEmpty();
        }
    }
}
