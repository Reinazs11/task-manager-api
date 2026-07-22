package com.renan.taskmanager.tasks.application;

import com.renan.taskmanager.common.domain.UserId;
import com.renan.taskmanager.tasks.domain.ProjectId;
import com.renan.taskmanager.tasks.domain.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DeleteProjectUseCase}.
 *
 * <p>The use case is the canonical anti-enumeration reference for the project:
 * ownership is checked via {@code existsByIdAndOwnerId}, and the same 403 is
 * returned whether the project does not exist at all or belongs to someone
 * else. The deletion itself only runs when ownership passes.</p>
 */
@ExtendWith(MockitoExtension.class)
class DeleteProjectUseCaseTest {

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private DeleteProjectUseCase useCase;

    @Test
    void shouldDeleteWhenOwnerRequests() {
        UserId owner = UserId.generate();
        ProjectId pid = ProjectId.generate();
        when(projectRepository.existsByIdAndOwnerId(pid, owner)).thenReturn(true);

        assertThatCode(() -> useCase.execute(pid, owner)).doesNotThrowAnyException();

        verify(projectRepository).deleteById(pid);
    }

    /**
     * Anti-enumeration: a non-owner gets 403 whether the project exists or not,
     * because existsByIdAndOwnerId collapses both cases into false.
     */
    @Test
    void shouldThrow403WhenNonOwnerRequests() {
        UserId attacker = UserId.generate();
        ProjectId pid = ProjectId.generate();
        when(projectRepository.existsByIdAndOwnerId(pid, attacker)).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(pid, attacker))
                .isInstanceOf(AccessDeniedException.class);
        verify(projectRepository, never()).deleteById(any());
    }
}
