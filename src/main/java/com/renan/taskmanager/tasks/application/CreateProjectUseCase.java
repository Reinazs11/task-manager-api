package com.renan.taskmanager.tasks.application;

import com.renan.taskmanager.tasks.domain.Project;
import com.renan.taskmanager.tasks.domain.ProjectRepository;
import com.renan.taskmanager.common.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application use case: create a new project for a user.
 */
@Service
public class CreateProjectUseCase {

    private final ProjectRepository projectRepository;

    public CreateProjectUseCase(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional
    public Project execute(UserId ownerId, String name) {
        Project project = Project.create(ownerId, name);
        return projectRepository.save(project);
    }
}
