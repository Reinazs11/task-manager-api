package com.renan.taskmanager.tasks.application;

import com.renan.taskmanager.tasks.domain.Project;
import com.renan.taskmanager.tasks.domain.ProjectId;
import com.renan.taskmanager.tasks.domain.ProjectNotFoundException;
import com.renan.taskmanager.tasks.domain.ProjectRepository;
import com.renan.taskmanager.users.domain.UserId;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetProjectUseCaseTest {

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private GetProjectUseCase useCase;

    @Test
    void shouldReturnProjectWhenOwnerRequests() {
        UserId owner = UserId.generate();
        ProjectId pid = ProjectId.generate();
        Project project = Project.reconstitute(pid, owner, "P1",
                List.of(), Instant.now(), Instant.now());
        when(projectRepository.findById(pid)).thenReturn(Optional.of(project));

        Project result = useCase.execute(pid, owner);

        assertThat(result.getId()).isEqualTo(pid);
    }

    @Test
    void shouldThrow404WhenProjectNotFound() {
        ProjectId pid = ProjectId.generate();
        when(projectRepository.findById(pid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(pid, UserId.generate()))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void shouldThrow403WhenNonOwnerRequests() {
        UserId owner = UserId.generate();
        UserId attacker = UserId.generate();
        ProjectId pid = ProjectId.generate();
        Project project = Project.reconstitute(pid, owner, "P1",
                List.of(), Instant.now(), Instant.now());
        when(projectRepository.findById(pid)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> useCase.execute(pid, attacker))
                .isInstanceOf(AccessDeniedException.class);
    }
}
