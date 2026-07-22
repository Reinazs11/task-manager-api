package com.renan.taskmanager.tasks.application;

import com.renan.taskmanager.tasks.domain.Project;
import com.renan.taskmanager.tasks.domain.ProjectRepository;
import com.renan.taskmanager.common.domain.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateProjectUseCaseTest {

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private CreateProjectUseCase useCase;

    @Test
    void shouldCreateProjectForOwner() {
        UserId owner = UserId.generate();
        when(projectRepository.save(any(Project.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Project result = useCase.execute(owner, "My Project");

        assertThat(result.getOwnerId()).isEqualTo(owner);
        assertThat(result.getName().value()).isEqualTo("My Project");
    }

    /**
     * Validation lives in the {@code ProjectName} value object: a null name is
     * rejected before anything reaches the repository.
     */
    @Test
    void shouldRejectNullProjectName() {
        UserId owner = UserId.generate();

        assertThatThrownBy(() -> useCase.execute(owner, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Project name cannot be null");
        verify(projectRepository, never()).save(any());
    }

    @Test
    void shouldRejectBlankProjectName() {
        UserId owner = UserId.generate();

        assertThatThrownBy(() -> useCase.execute(owner, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Project name cannot be blank");
        verify(projectRepository, never()).save(any());
    }

    @Test
    void shouldRejectProjectNameLongerThanMaxLength() {
        UserId owner = UserId.generate();
        String tooLong = "x".repeat(201); // ProjectName.MAX_LENGTH is 200

        assertThatThrownBy(() -> useCase.execute(owner, tooLong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("200");
        verify(projectRepository, never()).save(any());
    }
}
