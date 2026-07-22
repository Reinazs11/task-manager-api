package com.renan.taskmanager.tasks.application;

import com.renan.taskmanager.common.domain.UserId;
import com.renan.taskmanager.tasks.application.ports.ProjectQueryPort;
import com.renan.taskmanager.tasks.application.ports.TaskQueryPort;
import com.renan.taskmanager.tasks.domain.ProjectId;
import com.renan.taskmanager.tasks.domain.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ListTasksUseCase}.
 *
 * <p>The use case has two responsibilities: enforce owner authorization (the
 * project must belong to the requester) and delegate to {@link TaskQueryPort}
 * with the optional status filter. These tests cover both branches.</p>
 */
@ExtendWith(MockitoExtension.class)
class ListTasksUseCaseTest {

    @Mock
    private ProjectQueryPort projectQueryPort;

    @Mock
    private TaskQueryPort taskQueryPort;

    @InjectMocks
    private ListTasksUseCase useCase;

    @Test
    void shouldListTasksWhenOwnerRequests() {
        UserId owner = UserId.generate();
        ProjectId pid = ProjectId.generate();
        Pageable pageable = PageRequest.of(0, 20);
        Page<com.renan.taskmanager.tasks.domain.Task> empty = new PageImpl<>(List.of());
        when(projectQueryPort.existsByIdAndOwnerId(pid, owner)).thenReturn(true);
        when(taskQueryPort.findByProjectId(eq(pid), eq(null), eq(pageable))).thenReturn(empty);

        Page<com.renan.taskmanager.tasks.domain.Task> result =
                useCase.execute(pid, owner, null, pageable);

        assertThat(result).isSameAs(empty);
        verify(taskQueryPort).findByProjectId(pid, null, pageable);
    }

    @Test
    void shouldForwardStatusFilterWhenProvided() {
        UserId owner = UserId.generate();
        ProjectId pid = ProjectId.generate();
        Pageable pageable = PageRequest.of(0, 20);
        Page<com.renan.taskmanager.tasks.domain.Task> empty = new PageImpl<>(List.of());
        when(projectQueryPort.existsByIdAndOwnerId(pid, owner)).thenReturn(true);
        when(taskQueryPort.findByProjectId(eq(pid), eq(TaskStatus.IN_PROGRESS), eq(pageable))).thenReturn(empty);

        useCase.execute(pid, owner, TaskStatus.IN_PROGRESS, pageable);

        verify(taskQueryPort).findByProjectId(pid, TaskStatus.IN_PROGRESS, pageable);
    }

    /**
     * Anti-enumeration: "project exists but not yours" and "project does not
     * exist" are indistinguishable to the caller — both collapse to 403.
     */
    @Test
    void shouldThrow403WhenNonOwnerRequests() {
        UserId attacker = UserId.generate();
        ProjectId pid = ProjectId.generate();
        when(projectQueryPort.existsByIdAndOwnerId(pid, attacker)).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(pid, attacker, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
        verify(taskQueryPort, never()).findByProjectId(any(), any(), any());
    }
}
