package com.renan.taskmanager.tasks.application;

import com.renan.taskmanager.tasks.domain.Project;
import com.renan.taskmanager.tasks.domain.ProjectId;
import com.renan.taskmanager.tasks.domain.ProjectRepository;
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
        when(projectRepository.existsByIdAndOwnerId(pid, owner)).thenReturn(true);
        when(projectRepository.findById(pid)).thenReturn(Optional.of(project));

        Project result = useCase.execute(pid, owner);

        assertThat(result.getId()).isEqualTo(pid);
    }

    /**
     * Anti-enumeration: an attacker must not be able to distinguish "this
     * project exists but is not mine" from "this project does not exist".
     * Both branches of {@code existsByIdAndOwnerId=false} collapse to 403.
     */
    @Test
    void shouldThrow403WhenNonOwnerRequests() {
        UserId attacker = UserId.generate();
        ProjectId pid = ProjectId.generate();
        when(projectRepository.existsByIdAndOwnerId(pid, attacker)).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(pid, attacker))
                .isInstanceOf(AccessDeniedException.class);
        verify(projectRepository, never()).findById(any());
    }

    /**
     * Rare race window: project exists and belongs to the caller at the
     * ownership check, but disappears before findById. Still surfaces as 403
     * (consistent with the anti-enumeration posture) rather than 404.
     */
    @Test
    void shouldThrow403WhenProjectVanishesBetweenOwnershipCheckAndFetch() {
        UserId owner = UserId.generate();
        ProjectId pid = ProjectId.generate();
        when(projectRepository.existsByIdAndOwnerId(pid, owner)).thenReturn(true);
        when(projectRepository.findById(pid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(pid, owner))
                .isInstanceOf(AccessDeniedException.class);
    }
}
