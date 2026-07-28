package com.renan.taskmanager.common.audit.application;

import com.renan.taskmanager.common.audit.domain.AuditAction;
import com.renan.taskmanager.common.audit.domain.AuditEvent;
import com.renan.taskmanager.common.audit.domain.AuditableEntityType;
import com.renan.taskmanager.common.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ListAuditEventsUseCase}.
 *
 * <p>Pure unit test: the query port is mocked. The interesting behavior is the
 * authorization scoping — the use case MUST forward the caller's id to the
 * port and never accept a different actor — so the test asserts the id passed
 * through unchanged.</p>
 */
@ExtendWith(MockitoExtension.class)
class ListAuditEventsUseCaseTest {

    @Mock
    private AuditEventQueryPort queryPort;

    @Test
    @DisplayName("execute should delegate to the port with the caller's id and the filter")
    void shouldDelegateWithCallerIdAndFilter() {
        ListAuditEventsUseCase useCase = new ListAuditEventsUseCase(queryPort);
        UserId caller = UserId.generate();
        Pageable pageable = PageRequest.of(0, 20);
        AuditEvent event = AuditEvent.record(caller, AuditAction.PROJECT_CREATED,
                AuditableEntityType.PROJECT, null,
                Clock.fixed(Instant.parse("2026-07-28T12:00:00Z"), ZoneOffset.UTC),
                null, java.util.Map.of());
        Page<AuditEvent> stub = new org.springframework.data.domain.PageImpl<>(List.of(event));
        when(queryPort.findByActor(eq(caller), eq(AuditAction.PROJECT_CREATED), eq(pageable)))
                .thenReturn(stub);

        Page<AuditEvent> result = useCase.execute(caller, AuditAction.PROJECT_CREATED, pageable);

        assertThat(result.getContent()).containsExactly(event);
    }
}
