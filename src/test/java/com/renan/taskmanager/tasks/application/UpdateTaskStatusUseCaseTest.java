package com.renan.taskmanager.tasks.application;

import com.renan.taskmanager.tasks.domain.*;
import com.renan.taskmanager.common.domain.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateTaskStatusUseCaseTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private UpdateTaskStatusUseCase useCase;

    @Test
    void shouldTransitionWhenOwnerRequests() {
        UserId owner = UserId.generate();
        TaskId tid = TaskId.generate();
        Task task = Task.reconstitute(tid, ProjectId.generate(), owner,
                new TaskTitle("T"), TaskStatus.TODO, Priority.MEDIUM,
                Instant.now(), Instant.now());
        when(taskRepository.findById(tid)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        Task result = useCase.execute(tid, owner, TaskStatus.IN_PROGRESS);

        assertThat(result.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void shouldThrow404WhenTaskNotFound() {
        TaskId tid = TaskId.generate();
        when(taskRepository.findById(tid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(tid, UserId.generate(), TaskStatus.DONE))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void shouldThrow403WhenNonOwnerRequests() {
        UserId owner = UserId.generate();
        UserId attacker = UserId.generate();
        TaskId tid = TaskId.generate();
        Task task = Task.reconstitute(tid, ProjectId.generate(), owner,
                new TaskTitle("T"), TaskStatus.TODO, Priority.MEDIUM,
                Instant.now(), Instant.now());
        when(taskRepository.findById(tid)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> useCase.execute(tid, attacker, TaskStatus.IN_PROGRESS))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldThrow409WhenTransitionInvalid() {
        UserId owner = UserId.generate();
        TaskId tid = TaskId.generate();
        Task task = Task.reconstitute(tid, ProjectId.generate(), owner,
                new TaskTitle("T"), TaskStatus.TODO, Priority.MEDIUM,
                Instant.now(), Instant.now());
        when(taskRepository.findById(tid)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> useCase.execute(tid, owner, TaskStatus.DONE))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }
}
