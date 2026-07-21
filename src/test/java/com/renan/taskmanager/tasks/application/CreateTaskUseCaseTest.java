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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateTaskUseCaseTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private CreateTaskUseCase useCase;

    @Test
    void shouldCreateTaskWhenOwnerRequests() {
        UserId owner = UserId.generate();
        ProjectId pid = ProjectId.generate();
        Project project = Project.reconstitute(pid, owner, "P1",
                List.of(), Instant.now(), Instant.now());
        when(projectRepository.existsByIdAndOwnerId(pid, owner)).thenReturn(true);
        when(projectRepository.findById(pid)).thenReturn(Optional.of(project));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        Task result = useCase.execute(pid, owner, "New Task", Priority.HIGH);

        assertThat(result.getTitle()).isEqualTo(new TaskTitle("New Task"));
        assertThat(result.getOwnerId()).isEqualTo(owner);
        assertThat(result.getPriority()).isEqualTo(Priority.HIGH);
    }

    /**
     * Anti-enumeration: "project exists but not yours" and "project does not
     * exist" are indistinguishable. Both collapse to 403.
     */
    @Test
    void shouldThrow403WhenNonOwnerRequests() {
        UserId attacker = UserId.generate();
        ProjectId pid = ProjectId.generate();
        when(projectRepository.existsByIdAndOwnerId(pid, attacker)).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(pid, attacker, "T", null))
                .isInstanceOf(AccessDeniedException.class);
        verify(taskRepository, never()).save(any());
    }
}
