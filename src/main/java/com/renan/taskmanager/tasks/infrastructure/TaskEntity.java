package com.renan.taskmanager.tasks.infrastructure;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for Task persistence.
 *
 * <p>Stores the {@code project_id} as a FK column instead of a {@code @ManyToOne}
 * relation. This keeps the entity lean (no lazy-loading surprises, no N+1 risk)
 * and matches the domain model where a Task references a ProjectId by value.</p>
 *
 * <p>{@code owner_id} is denormalized here for fast authorization checks
 * (avoiding a join to projects just to verify ownership).</p>
 */
@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TaskStatusEntity status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private PriorityEntity priority;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * JPA-safe enum mirrors of the domain enums. We use separate enums here
     * (instead of reusing the domain ones) to keep JPA concerns out of the
     * domain layer. The mapper converts between them.
     */
    public enum TaskStatusEntity { TODO, IN_PROGRESS, DONE }
    public enum PriorityEntity { LOW, MEDIUM, HIGH }
}
