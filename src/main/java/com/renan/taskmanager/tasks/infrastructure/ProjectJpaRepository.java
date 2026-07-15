package com.renan.taskmanager.tasks.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ProjectEntity}.
 */
public interface ProjectJpaRepository extends JpaRepository<ProjectEntity, UUID> {

    Page<ProjectEntity> findByOwnerId(UUID ownerId, Pageable pageable);

    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);
}
