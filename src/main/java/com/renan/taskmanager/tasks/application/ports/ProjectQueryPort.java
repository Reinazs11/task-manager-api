package com.renan.taskmanager.tasks.application.ports;

import com.renan.taskmanager.tasks.domain.Project;
import com.renan.taskmanager.tasks.domain.ProjectId;
import com.renan.taskmanager.common.domain.UserId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * Output port (application layer): query operations for projects that need
 * Spring Data pagination types.
 *
 * <p>Lives in the application package (not domain) to keep the domain pure.
 * The infrastructure layer implements this; application use cases consume it.</p>
 */
public interface ProjectQueryPort {

    /**
     * Lists projects belonging to a specific owner, paginated.
     */
    Page<Project> findByOwnerId(UserId ownerId, Pageable pageable);

    Optional<Project> findById(ProjectId id);

    boolean existsByIdAndOwnerId(ProjectId id, UserId ownerId);
}
