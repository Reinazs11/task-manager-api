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
import static org.mockito.ArgumentMatchers.any;
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
}
