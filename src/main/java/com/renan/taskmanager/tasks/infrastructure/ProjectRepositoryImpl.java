package com.renan.taskmanager.tasks.infrastructure;

import com.renan.taskmanager.tasks.application.TaskMapper;
import com.renan.taskmanager.tasks.application.ports.ProjectQueryPort;
import com.renan.taskmanager.tasks.domain.Project;
import com.renan.taskmanager.tasks.domain.ProjectId;
import com.renan.taskmanager.tasks.domain.ProjectRepository;
import com.renan.taskmanager.common.domain.UserId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Adapter: implements both the domain {@link ProjectRepository} port and the
 * application {@link ProjectQueryPort} using JPA.
 */
@Repository
public class ProjectRepositoryImpl implements ProjectRepository, ProjectQueryPort {

    private final ProjectJpaRepository jpaRepository;
    private final TaskMapper mapper;

    public ProjectRepositoryImpl(ProjectJpaRepository jpaRepository, TaskMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Project save(Project project) {
        ProjectEntity entity = mapper.toEntity(project);
        ProjectEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Project> findById(ProjectId id) {
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Page<Project> findByOwnerId(UserId ownerId, Pageable pageable) {
        return jpaRepository.findByOwnerId(ownerId.value(), pageable).map(mapper::toDomain);
    }

    @Override
    public void deleteById(ProjectId id) {
        jpaRepository.deleteById(id.value());
    }

    @Override
    public boolean existsByIdAndOwnerId(ProjectId id, UserId ownerId) {
        return jpaRepository.existsByIdAndOwnerId(id.value(), ownerId.value());
    }
}
